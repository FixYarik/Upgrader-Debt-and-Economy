package dev.ude.events;

import dev.ude.UpgraderDebtEconomy;
import dev.ude.commands.UdeCommands;
import dev.ude.core.EconomyEngine;
import dev.ude.world.UdeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.world.item.ItemStack;
import dev.ude.world.MayorGuide;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.BlockPos;

/** Bridges Minecraft events to tested economy domain classes. */
public final class UdeEvents {
    private UdeEvents(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){UdeCommands.register(e.getDispatcher());}
    @SubscribeEvent public static void onBlock(PlayerInteractEvent.RightClickBlock e){
        if(e.getHand()!=InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p))return;
        var block=e.getLevel().getBlockState(e.getPos()).getBlock();
        if(block==UpgraderDebtEconomy.ATM.get()){
            p.sendSystemMessage(Component.literal("§6UDE BANK §fБаланс: "+UdeSavedData.get(p.server).engine().balance(p.getUUID())+" ₡. Используйте /ude bank ..."));
            e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);
        } else if(block==UpgraderDebtEconomy.HOME_MARKER.get()){
            var pos=e.getPos();UdeSavedData d=UdeSavedData.get(p.server);
            d.engine().setHome(p.getUUID(),new EconomyEngine.Home(p.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));
            d.changed();p.sendSystemMessage(Component.literal("§6UDE §fАдрес дома зарегистрирован: "+pos.toShortString()));
            e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);
        }
    }
    @SubscribeEvent public static void onItem(PlayerInteractEvent.RightClickItem e){
        if(e.getHand()!=InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p))return;
        if(e.getItemStack().is(UpgraderDebtEconomy.SMARTPHONE.get())){
            UdeSavedData data=UdeSavedData.get(p.server);
            p.sendSystemMessage(Component.literal("§6UDE Smartphone §fБанк: "+data.engine().balance(p.getUUID())+" ₡; /ude bank balance | /ude credit list | /ude business list"));
            e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);
        }
    }

    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var server=e.getServer();
        long now=server.overworld().getGameTime();
        for(ServerLevel level:server.getAllLevels()){
            dev.ude.world.CityHallWorldgen.tick(level);
            dev.ude.world.CityHallProtection.tick(level);
        }
        if(now%20!=0)return; // Server scans once per second.
        UdeSavedData data=UdeSavedData.get(server);
        var due=data.engine().markCollectorsDue(now,l->{
            ServerPlayer p=server.getPlayerList().getPlayer(l.owner);
            return p!=null && data.engine().getHome(l.owner)!=null;
        });
        if(due.isEmpty())return;
        data.changed();
        for(var l:due){
            ServerPlayer p=server.getPlayerList().getPlayer(l.owner);
            if(p==null)continue;
            p.sendSystemMessage(Component.literal("§c[UDE] 3 дня просрочки. Спортики выехали к вашему дому. Кредит: "+l.id));
            // Alpha: dispatch notification only. Real collector pathfinding/raid is not yet implemented.
        }
    }
}
