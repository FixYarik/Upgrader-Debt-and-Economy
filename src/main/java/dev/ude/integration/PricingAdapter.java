package dev.ude.integration;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Map;

/**
 * Explicit fallback values, NOT an Upgrader Items API bridge.
 * Replace with a verified version-specific adapter once its mod-id/API is inspected.
 */
public final class PricingAdapter {
    private static final Map<String,Long> VALUE=Map.of(
        "minecraft:iron_ingot",40L,"minecraft:gold_ingot",75L,
        "minecraft:diamond",250L,"minecraft:emerald",125L,
        "minecraft:wheat",7L,"minecraft:coal",12L,
        "minecraft:copper_ingot",25L,"minecraft:netherite_ingot",1500L
    );
    private PricingAdapter(){}
    public static long unitValue(ItemStack stack){
        return VALUE.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),0L);
    }
}
