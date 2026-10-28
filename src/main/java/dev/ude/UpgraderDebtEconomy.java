package dev.ude;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.MinecraftForge;
import dev.ude.events.UdeEvents;

@Mod(UpgraderDebtEconomy.MOD_ID)
public final class UpgraderDebtEconomy {
    public static final String MOD_ID="upgrader_debt_economy";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,MOD_ID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,MOD_ID);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<dev.ude.npc.CivicNpc>> CIVIC_NPC=ENTITIES.register("civic_npc",()->
        net.minecraft.world.entity.EntityType.Builder.<dev.ude.npc.CivicNpc>of(dev.ude.npc.CivicNpc::new,net.minecraft.world.entity.MobCategory.CREATURE)
        .sized(0.6f,1.95f).clientTrackingRange(10).build(MOD_ID+":civic_npc"));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<dev.ude.menu.CivicMenu>> CIVIC_MENU=MENUS.register("civic",()->
        net.minecraftforge.common.extensions.IForgeMenuType.create(dev.ude.menu.CivicMenu::new));
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,MOD_ID);

    public static final RegistryObject<Item> CREDIT_COIN=ITEMS.register("credit_coin",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> SMARTPHONE=ITEMS.register("smartphone",()->new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BANK_CARD=ITEMS.register("bank_card",()->new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BUSINESS_LICENCE=ITEMS.register("business_licence",()->new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> VAULT_DOOR=BLOCKS.register("vault_door",dev.ude.block.VaultDoorBlock::new);
    public static final RegistryObject<Block> ATM=BLOCKS.register("atm",()->new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f)));
    public static final RegistryObject<Block> HOME_MARKER=BLOCKS.register("home_marker",()->new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)));
    static {
        ITEMS.register("vault_door",()->new BlockItem(VAULT_DOOR.get(),new Item.Properties()));
        ITEMS.register("atm",()->new BlockItem(ATM.get(),new Item.Properties()));
        ITEMS.register("home_marker",()->new BlockItem(HOME_MARKER.get(),new Item.Properties()));
    }

    public UpgraderDebtEconomy(){
        IEventBus modBus=FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);BLOCKS.register(modBus);ENTITIES.register(modBus);MENUS.register(modBus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,dev.ude.world.CityHallConfig.SPEC);
        modBus.addListener(this::attributes);
        modBus.addListener(this::onTab);
        MinecraftForge.EVENT_BUS.register(UdeEvents.class);
    }
    private void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent e){e.put(CIVIC_NPC.get(),dev.ude.npc.CivicNpc.civicAttributes().build());}
    private void onTab(BuildCreativeModeTabContentsEvent e){
        if(e.getTabKey()==CreativeModeTabs.INGREDIENTS){
            e.accept(CREDIT_COIN);e.accept(SMARTPHONE);e.accept(BANK_CARD);
            e.accept(BUSINESS_LICENCE);e.accept(ATM.get());e.accept(HOME_MARKER.get());
        }
    }
}
