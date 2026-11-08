package com.mimicer.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración del sistema de armarios (archivo mimicer-closets.toml). */
public final class ClosetConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue RANDOM_CHECK_CHANCE;
    public static final ForgeConfigSpec.IntValue CHECK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue SEARCH_RANGE;
    public static final ForgeConfigSpec.IntValue LOST_TARGET_SEARCH_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_CHECKS_PER_SEARCH;
    public static final ForgeConfigSpec.BooleanValue VILLAGERS_HIDE;
    public static final ForgeConfigSpec.DoubleValue VILLAGER_CLOSET_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("closets");
        ENABLED = b.comment("Los monstruos pueden buscar y abrir armarios").define("monstersSearchClosets", true);
        RANDOM_CHECK_CHANCE = b.comment("Probabilidad (0-1) de que un monstruo libre revise un armario cercano "
                        + "en cada intervalo, aunque no haya visto entrar a nadie")
                .defineInRange("randomCheckChance", 0.12D, 0.0D, 1.0D);
        CHECK_INTERVAL_SECONDS = b.comment("Segundos entre tiradas de revisión aleatoria")
                .defineInRange("checkIntervalSeconds", 25, 5, 600);
        SEARCH_RANGE = b.comment("Radio (bloques) en el que un monstruo busca armarios que revisar")
                .defineInRange("searchRange", 18, 4, 48);
        LOST_TARGET_SEARCH_SECONDS = b.comment("Segundos que un monstruo que perdió de vista a su presa revisa armarios cercanos")
                .defineInRange("lostTargetSearchSeconds", 30, 0, 300);
        MAX_CHECKS_PER_SEARCH = b.comment("Armarios máximos que revisa en una misma búsqueda")
                .defineInRange("maxChecksPerSearch", 4, 1, 16);
        VILLAGERS_HIDE = b.comment("Los aldeanos alarmados pueden esconderse en armarios").define("villagersHide", true);
        VILLAGER_CLOSET_CHANCE = b.comment("Probabilidad de que un aldeano prefiera un armario cercano a su cama")
                .defineInRange("villagerClosetChance", 0.5D, 0.0D, 1.0D);
        b.pop();
        SPEC = b.build();
    }

    private ClosetConfig() {}
}
