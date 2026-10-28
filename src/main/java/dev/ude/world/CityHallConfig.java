package dev.ude.world;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CityHallConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue VILLAGE_CHANCE, BLOCKS_PER_TICK, COLUMNS_PER_TICK, MAX_HALLS;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("cityHall");
        ENABLED = b.comment("Place a city hall beside suitable loaded vanilla villages. Never force-load chunks.").define("enabled", true);
        VILLAGE_CHANCE = b.comment("Deterministic percentage of villages eligible for a city hall.").defineInRange("villageChancePercent", 100, 0, 100);
        BLOCKS_PER_TICK = b.comment("Maximum template blocks processed per server tick, across all halls.").defineInRange("blocksPerTick", 512, 32, 2048);
        COLUMNS_PER_TICK = b.comment("Maximum terrain columns checked per server tick.").defineInRange("columnsPerTick", 64, 1, 128);
        MAX_HALLS = b.comment("Bound world-wide generation and pending discovery records.").defineInRange("maxVillageRecords", 256, 1, 4096);
        b.pop(); SPEC = b.build();
    }
    private CityHallConfig() {}
}
