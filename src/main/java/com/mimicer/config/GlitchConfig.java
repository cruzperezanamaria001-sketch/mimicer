package com.mimicer.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de The Glitch (archivo mimicer-common.toml). */
public final class GlitchConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CORRUPTION_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_ENTITY;
    public static final ForgeConfigSpec.IntValue MAX_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_PER_SECOND;
    public static final ForgeConfigSpec.BooleanValue BREAKING_ENABLED;
    public static final ForgeConfigSpec.IntValue BREAK_TICKS;
    public static final ForgeConfigSpec.DoubleValue MAX_BREAK_HARDNESS;
    public static final ForgeConfigSpec.IntValue JUMPSCARE_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue BEAM_MIN_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BEAM_MAX_DAMAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("glitch");
        CORRUPTION_ENABLED = b.comment("Si The Glitch convierte bloques en bloques Glitch al caminar")
                .define("corruptionEnabled", true);
        MAX_BLOCKS_PER_ENTITY = b.comment("Máximo de bloques que puede corromper cada Glitch durante su vida")
                .defineInRange("maxCorruptedBlocks", 300, 0, 100000);
        MAX_RADIUS = b.comment("Radio máximo (bloques) desde su punto de aparición donde puede corromper")
                .defineInRange("maxCorruptionRadius", 48, 1, 512);
        MAX_PER_SECOND = b.comment("Máximo de bloques corrompidos por segundo")
                .defineInRange("maxBlocksPerSecond", 8, 1, 200);
        BREAKING_ENABLED = b.comment("Si puede romper obstáculos cuando se queda atascado")
                .define("breakingEnabled", true);
        BREAK_TICKS = b.comment("Ticks que tarda en romper un bloque")
                .defineInRange("breakTicks", 20, 1, 400);
        MAX_BREAK_HARDNESS = b.comment("Dureza máxima de los bloques que puede romper (piedra=1.5, obsidiana=50)")
                .defineInRange("maxBreakHardness", 3.0D, 0.0D, 100.0D);
        JUMPSCARE_COOLDOWN = b.comment("Ticks de enfriamiento entre sustos al mismo jugador")
                .defineInRange("jumpscareCooldown", 1800, 100, 72000);
        BEAM_MIN_DAMAGE = b.comment("Daño mínimo del rayo morado (2 corazones = 4)")
                .defineInRange("beamMinDamage", 4.0D, 0.0D, 100.0D);
        BEAM_MAX_DAMAGE = b.comment("Daño máximo del rayo morado (3 corazones = 6)")
                .defineInRange("beamMaxDamage", 6.0D, 0.0D, 100.0D);
        b.pop();
        SPEC = b.build();
    }

    private GlitchConfig() {}
}
