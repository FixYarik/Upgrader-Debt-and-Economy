package dev.ude.menu;

import dev.ude.UpgraderDebtEconomy;
import dev.ude.core.*;
import dev.ude.npc.*;
import dev.ude.world.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Uses vanilla container button packets: the server owns NPC identity and allowed actions. */
public final class CivicMenu extends AbstractContainerMenu {
    private final CivicNpc npc;
    private final Player owner;
    private final ContainerData data;
    private long lastAction=Long.MIN_VALUE;
    public CivicMenu(int id,Inventory inv,FriendlyByteBuf buf){this(id,inv,inv.player.level().getEntity(buf.readInt()) instanceof CivicNpc n?n:null);}
    public CivicMenu(int id,Inventory inv,CivicNpc npc){
        super(UpgraderDebtEconomy.CIVIC_MENU.get(),id);this.npc=npc;owner=inv.player;
        data=inv.player.level().isClientSide?new SimpleContainerData(8):new ContainerData(){
            public int get(int i){
                if(!(owner instanceof ServerPlayer p))return 0;
                var e=UdeSavedData.get(p.server).engine();
                long value=i<4?e.balance(p.getUUID()):e.loans().stream().filter(l->l.owner.equals(p.getUUID())&&l.outstanding>0).mapToLong(l->l.outstanding).findFirst().orElse(0);
                return (int)(value>>>((i%4)*16))&65535;
            }
            public void set(int i,int v){}
            public int getCount(){return 8;}
        };
        addDataSlots(data);
    }
    public CivicRole role(){return npc==null?CivicRole.GUARD:npc.role();}
    public long balance(){return number(0);}
    public long debt(){return number(4);}
    private long number(int start){long n=0;for(int i=0;i<4;i++)n|=((long)data.get(start+i)&65535)<<(i*16);return n;}
    @Override public boolean stillValid(Player player){
        return player==owner && npc!=null && npc.isAlive() && npc.level()==player.level() && npc.distanceToSqr(player)<=64;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer p)||!stillValid(player))return false;
        long tick=p.server.overworld().getGameTime();
        if(lastAction!=Long.MIN_VALUE && tick-lastAction<10)return false;
        lastAction=tick;
        var saved=UdeSavedData.get(p.server);var engine=saved.engine();
        try {
            if(role().banker()) {
                switch(action) {
                    case 0 -> {engine.openAccount(p.getUUID());saved.changed();}
                    case 1 -> {
                        if(!engine.hasAccount(p.getUUID()))throw new IllegalStateException("Open an account first");
                        Math.addExact(engine.balance(p.getUUID()),100);
                        ItemStack coin=ItemStack.EMPTY;
                        for(ItemStack item:p.getInventory().items)if(item.is(UpgraderDebtEconomy.CREDIT_COIN.get())){coin=item;break;}
                        if(coin.isEmpty())throw new IllegalStateException("A credit coin is required");
                        engine.grant(p.getUUID(),100,UUID.randomUUID().toString());coin.shrink(1);p.getInventory().setChanged();saved.changed();
                    }
                    case 2 -> {
                        engine.debit(p.getUUID(),100,UUID.randomUUID().toString());saved.changed();
                        ItemStack coin=new ItemStack(UpgraderDebtEconomy.CREDIT_COIN.get());
                        if(!p.getInventory().add(coin))p.drop(coin,false);
                    }
                    case 3 -> {engine.takeLoan(p.getUUID(),LoanType.MICRO,500,p.server.overworld().getGameTime(),5,UUID.randomUUID().toString());saved.changed();}
                    case 4 -> {
                        var loan=engine.loans().stream().filter(l->l.owner.equals(p.getUUID())&&l.outstanding>0).findFirst().orElseThrow(()->new IllegalStateException("No active loan"));
                        engine.repay(p.getUUID(),loan.id,loan.outstanding,UUID.randomUUID().toString());saved.changed();
                    }
                    default -> {return false;}
                }
            }else if(role()==CivicRole.MAYOR && action==10){
                ItemStack book=MayorGuide.make();if(!p.getInventory().add(book))p.drop(book,false);
            }else if(role()==CivicRole.BUSINESS && action>=20 && action<25){
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ude business buy "+BusinessType.values()[action-20].name());
            }else if(role()==CivicRole.TAX && action==30){
                for(var b:engine.businesses())if(b.owner.equals(p.getUUID())&&b.taxDebt>0&&engine.balance(p.getUUID())>0){
                    engine.payTaxes(p.getUUID(),b.id,Math.min(b.taxDebt,engine.balance(p.getUUID())),UUID.randomUUID().toString());saved.changed();
                }
            }else return false;
            broadcastChanges();p.sendSystemMessage(Component.translatable("menu.upgrader_debt_economy.success"));return true;
        }catch(IllegalArgumentException|IllegalStateException|ArithmeticException ex){
            p.sendSystemMessage(Component.literal("UDE: "+ex.getMessage()));return false;
        }
    }
}
