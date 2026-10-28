package dev.ude.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ude.core.*;
import dev.ude.world.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.ude.integration.PricingAdapter;
import java.util.*;

/** Commands deliberately remain text-based until actual GUIs are implemented. */
public final class UdeCommands {
    private UdeCommands(){}
    private static String op(){return UUID.randomUUID().toString();}
    private static long owned(ServerPlayer p,Item item){
        long n=0;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            var stack=p.getInventory().getItem(i);if(stack.is(item))n+=stack.getCount();
        }
        return n;
    }
    private static void consume(ServerPlayer p,Item item,int count){
        if(owned(p,item)<count)throw new IllegalStateException("Недостаточно предметов");
        int remaining=count;
        for(int i=0;i<p.getInventory().getContainerSize()&&remaining>0;i++){
            ItemStack stack=p.getInventory().getItem(i);
            if(stack.is(item)){int n=Math.min(stack.getCount(),remaining);stack.shrink(n);remaining-=n;}
        }
        p.getInventory().setChanged();
    }
    private static void msg(CommandSourceStack source,String m){source.sendSuccess(()->Component.literal("§6[UDE] §f"+m),false);}
    private static int guard(CommandSourceStack src,Action task){
        try{return task.run();}catch(RuntimeException ex){src.sendFailure(Component.literal("UDE: "+ex.getMessage()));return 0;}
    }
    @FunctionalInterface private interface Action {int run() throws RuntimeException;}
    private static UdeSavedData data(CommandSourceStack s){return UdeSavedData.get(s.getServer());}
    private static String dPlot(UdeSavedData data,UUID id){var p=data.plot(id);return p==null?"нет":p.pos().toShortString();}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("ude")
            .then(Commands.literal("help").executes(ctx->{msg(ctx.getSource(),"/ude bank open|balance|deposit <count>; /ude credit take <type> <amount> <days>; /ude credit list|repay <uuid> <amount>; /ude home set; /ude business buy <type>|list; /ude cityhall build (OP)");return 1;}))
            .then(Commands.literal("bank")
                .then(Commands.literal("open").executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                    boolean added=d.engine().openAccount(p.getUUID());if(added)d.changed();
                    msg(c.getSource(),added?"Счёт открыт.":"Счёт уже существует.");return 1;
                })))
                .then(Commands.literal("balance").executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    msg(c.getSource(),"Баланс: "+data(c.getSource()).engine().balance(p.getUUID())+" ₡");return 1;
                })))
                .then(Commands.literal("deposit").then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                    int count=IntegerArgumentType.getInteger(c,"count");
                    if(!d.engine().hasAccount(p.getUUID()))throw new IllegalStateException("Сначала открой счёт");
                    // Consume actual physical currency first.
                    consume(p,dev.ude.UpgraderDebtEconomy.CREDIT_COIN.get(),count);
                    d.engine().grant(p.getUUID(),count*100L,op());d.changed();
                    msg(c.getSource(),"Зачислено "+(count*100)+" ₡");return 1;
                }))))
                .then(Commands.literal("withdraw").then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                    int count=IntegerArgumentType.getInteger(c,"count");
                    d.engine().debit(p.getUUID(),count*100L,op());d.changed();
                    ItemStack coin=new ItemStack(dev.ude.UpgraderDebtEconomy.CREDIT_COIN.get(),count);
                    if(!p.getInventory().add(coin) && !coin.isEmpty())p.drop(coin,false);
                    msg(c.getSource(),"Выдано "+count+" монет. Со счёта списано "+(count*100)+" ₡");return 1;
                }))))
                .then(Commands.literal("transfer")
                    .then(Commands.argument("player",EntityArgument.player())
                    .then(Commands.argument("amount",LongArgumentType.longArg(1))
                    .executes(c->guard(c.getSource(),()->{
                        ServerPlayer from=c.getSource().getPlayerOrException(),to=EntityArgument.getPlayer(c,"player");
                        long amount=LongArgumentType.getLong(c,"amount");UdeSavedData d=data(c.getSource());
                        d.engine().transfer(from.getUUID(),to.getUUID(),amount,op());d.changed();
                        msg(c.getSource(),"Отправлено "+amount+" ₡ игроку "+to.getName().getString());return 1;
                    })))))
            )
            .then(Commands.literal("market")
                .then(Commands.literal("prices").executes(c->{msg(c.getSource(),"Предметы: iron_ingot 40, gold_ingot 75, diamond 250, emerald 125, wheat 7, coal 12, copper_ingot 25, netherite_ingot 1500 ₡ за штуку");return 1;}))
                .then(Commands.literal("sell").then(Commands.argument("item",StringArgumentType.word())
                    .then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->guard(c.getSource(),()->{
                        ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                        String itemName=StringArgumentType.getString(c,"item");
                        ResourceLocation loc=new ResourceLocation("minecraft",itemName);
                        Item item=BuiltInRegistries.ITEM.get(loc);
                        ItemStack sample=new ItemStack(item);
                        long unit=PricingAdapter.unitValue(sample);
                        if(item==Items.AIR||unit<=0)throw new IllegalArgumentException("Предмет не принимается");
                        int count=IntegerArgumentType.getInteger(c,"count");
                        if(!d.engine().hasAccount(p.getUUID()))throw new IllegalStateException("Сначала открой счёт");
                        consume(p,item,count);
                        long paid=Math.multiplyExact(unit,count);
                        d.engine().grant(p.getUUID(),paid,op());d.changed();
                        msg(c.getSource(),"Продано "+count+" × "+itemName+" за "+paid+" ₡");return 1;
                    }))))))
            )
            .then(Commands.literal("credit")
                .then(Commands.literal("take")
                    .then(Commands.argument("type",StringArgumentType.word())
                    .then(Commands.argument("amount",LongArgumentType.longArg(1))
                    .then(Commands.argument("days",IntegerArgumentType.integer(1,120))
                    .executes(c->guard(c.getSource(),()->{
                        ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                        LoanType type=LoanType.valueOf(StringArgumentType.getString(c,"type").toUpperCase(Locale.ROOT));
                        var loan=d.engine().takeLoan(p.getUUID(),type,LongArgumentType.getLong(c,"amount"),
                            c.getSource().getServer().overworld().getGameTime(),IntegerArgumentType.getInteger(c,"days"),op());
                        d.changed();msg(c.getSource(),"Кредит выдан. ID "+loan.id+", к возврату "+loan.outstanding+" ₡");return 1;
                    })))))
                .then(Commands.literal("list").executes(c->guard(c.getSource(),()->{
                    UUID uid=c.getSource().getPlayerOrException().getUUID();
                    var own=data(c.getSource()).engine().loans().stream().filter(l->l.owner.equals(uid)).toList();
                    if(own.isEmpty())msg(c.getSource(),"Кредитов нет");
                    for(var l:own)msg(c.getSource(),l.type+" "+l.id+" долг "+l.outstanding+" ₡, срок тик "+l.dueAt);
                    return 1;
                })))
                .then(Commands.literal("repay").then(Commands.argument("loan",StringArgumentType.word())
                    .then(Commands.argument("amount",LongArgumentType.longArg(1))
                    .executes(c->guard(c.getSource(),()->{
                        ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                        long amount=d.engine().repay(p.getUUID(),UUID.fromString(StringArgumentType.getString(c,"loan")),LongArgumentType.getLong(c,"amount"),op());
                        d.changed();msg(c.getSource(),"Погашено "+amount+" ₡");return 1;
                    })))))
            )
            .then(Commands.literal("home").then(Commands.literal("set").executes(c->guard(c.getSource(),()->{
                ServerPlayer p=c.getSource().getPlayerOrException();BlockPos pos=p.blockPosition();UdeSavedData d=data(c.getSource());
                d.engine().setHome(p.getUUID(),new EconomyEngine.Home(p.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));
                d.changed();msg(c.getSource(),"Дом зарегистрирован по координатам "+pos.toShortString());return 1;
            }))))
            .then(Commands.literal("business")
                .then(Commands.literal("inspect").then(Commands.argument("id",StringArgumentType.word()).executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                    UUID id=UUID.fromString(StringArgumentType.getString(c,"id"));
                    var b=d.engine().getBusiness(id);
                    if(b==null || !b.owner.equals(p.getUUID()))throw new IllegalArgumentException("Чужое предприятие");
                    var plot=d.plot(id);
                    if(plot==null)throw new IllegalStateException("Участок не назначен");
                    ServerLevel lvl=plot.level(p.server);
                    if(lvl==null || !lvl.hasChunkAt(plot.pos()))throw new IllegalStateException("Участок не загружен");
                    int missing=BusinessSitePlanner.missing(lvl,plot.pos());
                    if(missing==0){d.engine().finishConstruction(p.getUUID(),id);d.changed();msg(c.getSource(),"Предприятие открыто! Постройка проверена.");}
                    else msg(c.getSource(),"Недостаёт "+missing+" блоков по чертежу: дубовые брёвна 4 опоры по 4; рамка из дубовых досок на уровне +4; верстак в (0,+0,+2), бочка в (+2,+0,+2)");
                    return 1;
                }))))
                .then(Commands.literal("buy").then(Commands.argument("type",StringArgumentType.word()).executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();UdeSavedData d=data(c.getSource());
                    BusinessType type=BusinessType.valueOf(StringArgumentType.getString(c,"type").toUpperCase(Locale.ROOT));
                    BlockPos site=BusinessSitePlanner.find(p.serverLevel(),p.blockPosition());
                    if(site==null)throw new IllegalStateException("Рядом не найден ровный свободный участок 15x15");
                    var b=d.engine().registerBusiness(p.getUUID(),type,op());
                    BusinessSitePlanner.placeFoundation(p.serverLevel(),site);
                    d.assignPlot(b.id,p.serverLevel(),site);d.changed();
                    p.getInventory().add(new net.minecraft.world.item.ItemStack(dev.ude.UpgraderDebtEconomy.BUSINESS_LICENCE.get()));
                    msg(c.getSource(),"Куплен "+type+" ("+b.id+"). Фундамент: "+site.toShortString()+". Построй 4 угловые опоры из дубовых брёвен высотой 4, рамку крыши из досок на высоте +4, поставь верстак и бочку. /ude business inspect "+b.id);return 1;
                }))))
                .then(Commands.literal("list").executes(c->guard(c.getSource(),()->{
                    UUID owner=c.getSource().getPlayerOrException().getUUID();
                    for(var b:data(c.getSource()).engine().businesses())if(b.owner.equals(owner))
                        msg(c.getSource(),b.type+" ID="+b.id+" построено="+b.completed+" налоги="+b.taxDebt+" участок="+dPlot(data(c.getSource()),b.id));
                    return 1;
                })))
            )
            .then(Commands.literal("money").then(Commands.literal("grant").requires(s->s.hasPermission(2))
                .then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("amount",LongArgumentType.longArg(1))
                .executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=EntityArgument.getPlayer(c,"player");UdeSavedData d=data(c.getSource());
                    d.engine().openAccount(p.getUUID());d.engine().grant(p.getUUID(),LongArgumentType.getLong(c,"amount"),op());d.changed();
                    msg(c.getSource(),"Средства зачислены игроку "+p.getName().getString());return 1;
                })))))
            .then(Commands.literal("cityhall").then(Commands.literal("build").requires(s->s.hasPermission(2))
                .executes(c->guard(c.getSource(),()->{
                    ServerPlayer p=c.getSource().getPlayerOrException();ServerLevel level=p.serverLevel();
                    BlockPos center=p.blockPosition().offset(0,0,45);
                    if(!CityHallBuilder.canBuild(level,center))throw new IllegalStateException("Неподходящий участок: найдите ровную свободную площадку 55x65 блоков");
                    CityHallBuilder.build(level,center);
                    msg(c.getSource(),"Мэрия построена около "+center.toShortString());return 1;
                })))
            )
        ));
    }
}
