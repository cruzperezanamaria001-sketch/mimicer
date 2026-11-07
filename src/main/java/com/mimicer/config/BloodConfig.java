package com.mimicer.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración del Dynamic Blood System (archivo mimicer-blood.toml). */
public final class BloodConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue INTENSITY;
    public static final ForgeConfigSpec.IntValue STAIN_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_STAINS_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue MAX_STAINS_PER_EVENT;
    public static final ForgeConfigSpec.IntValue MAX_PARTICLES_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue PARTICLES;
    public static final ForgeConfigSpec.BooleanValue TRAILS;
    public static final ForgeConfigSpec.DoubleValue TRAIL_FREQUENCY;
    public static final ForgeConfigSpec.IntValue WOUND_DECAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue WALL_SPLATTER;
    public static final ForgeConfigSpec.BooleanValue SPREAD;
    public static final ForgeConfigSpec.BooleanValue DEATH_POOLS;
    public static final ForgeConfigSpec.BooleanValue CORPSE_SEEPAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("blood");
        ENABLED = b.comment("Activa el sistema de sangre dinámica").define("enabled", true);
        INTENSITY = b.comment("Multiplicador general de cantidad de sangre")
                .defineInRange("intensity", 1.0D, 0.1D, 3.0D);
        STAIN_LIFETIME_SECONDS = b.comment("Segundos que dura una mancha antes de desaparecer (se oscurece en 3 fases)")
                .defineInRange("stainLifetimeSeconds", 180, 10, 7200);
        MAX_STAINS_PER_MINUTE = b.comment("Límite de manchas nuevas por minuto en todo el servidor")
                .defineInRange("maxStainsPerMinute", 800, 10, 20000);
        MAX_STAINS_PER_EVENT = b.comment("Máximo de manchas generadas por un solo golpe")
                .defineInRange("maxStainsPerEvent", 14, 1, 100);
        MAX_PARTICLES_PER_TICK = b.comment("Máximo de emisores de partículas de sangre por tick")
                .defineInRange("maxParticlesPerTick", 40, 0, 1000);
        PARTICLES = b.comment("Salpicaduras de partículas además de manchas").define("particles", true);
        TRAILS = b.comment("Rastros de sangre de heridos al caminar").define("trails", true);
        TRAIL_FREQUENCY = b.comment("Frecuencia de gotas de los heridos (1.0 = normal)")
                .defineInRange("trailFrequency", 1.0D, 0.0D, 5.0D);
        WOUND_DECAY_SECONDS = b.comment("Segundos que tarda una herida grave en dejar de sangrar")
                .defineInRange("woundDecaySeconds", 120, 5, 3600);
        WALL_SPLATTER = b.comment("Permite que la sangre manche paredes, puertas y ventanas")
                .define("wallSplatter", true);
        SPREAD = b.comment("Las manchas grandes se extienden poco a poco").define("spread", true);
        DEATH_POOLS = b.comment("Charcos y salpicaduras al morir").define("deathPools", true);
        CORPSE_SEEPAGE = b.comment("Los cadáveres con heridas siguen sangrando un tiempo").define("corpseSeepage", true);
        b.pop();
        SPEC = b.build();
    }

    private BloodConfig() {}
}
