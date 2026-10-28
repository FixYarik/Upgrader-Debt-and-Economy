package dev.ude.client;

import dev.ude.UpgraderDebtEconomy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=UpgraderDebtEconomy.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents(){}
    @SubscribeEvent public static void onItem(PlayerInteractEvent.RightClickItem e){
        if(e.getHand()!=InteractionHand.MAIN_HAND)return;
        if(!e.getItemStack().is(UpgraderDebtEconomy.SMARTPHONE.get()))return;
        if(e.getEntity()!=Minecraft.getInstance().player)return;
        Minecraft.getInstance().setScreen(new UdePhoneScreen());
    }
}
