package com.mimicer.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de The Reaper (archivo mimicer-reaper.toml). */
public final class ReaperConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SPAWN_RARITY;
    public static final ForgeConfigSpec.IntValue SPAWN_EXCLUSION_RADIUS;
    public static final ForgeConfigSpec.IntValue SIGHT_RANGE;
    public static final ForgeConfigSpec.IntValue BLOOD_SENSE_RANGE;
    public static final ForgeConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue BREAK_BARRICADES;
    public static final ForgeConfigSpec.BooleanValue HEARTBEAT;
    public static final ForgeConfigSpec.BooleanValue FREEZE_WHEN_WATCHED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("reaper");
        SPAWN_RARITY = b.comment("Cuanto mayor, más raro: solo 1 de cada N intentos de generación válidos se acepta")
                .defineInRange("spawnRarity", 3, 1, 100);
        SPAWN_EXCLUSION_RADIUS = b.comment("No se genera otro Reaper a menos de esta distancia de uno existente")
                .defineInRange("spawnExclusionRadius", 96, 16, 512);
        SIGHT_RANGE = b.comment("Distancia a la que detecta presas con línea de visión")
                .defineInRange("sightRange", 32, 8, 64);
        BLOOD_SENSE_RANGE = b.comment("Distancia a la que huele a presas heridas o sangrando, aunque no las vea")
                .defineInRange("bloodSenseRange", 24, 0, 64);
        ATTACK_DAMAGE = b.comment("Daño del tajo de guadaña (corazones x2)")
                .defineInRange("attackDamage", 9.0D, 1.0D, 100.0D);
        BREAK_BARRICADES = b.comment("Rompe barricadas (tablas y puertas atrancadas) con la guadaña si le cierran el paso")
                .define("breakBarricades", true);
        HEARTBEAT = b.comment("Suena un latido que se acelera cuando persigue a un jugador cerca")
                .define("heartbeat", true);
        FREEZE_WHEN_WATCHED = b.comment("Se queda inmóvil mientras el jugador sano lo mira directamente")
                .define("freezeWhenWatched", true);
        b.pop();
        SPEC = b.build();
    }

    private ReaperConfig() {}
}
