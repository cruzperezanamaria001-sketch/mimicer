package com.mimicer.limb;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sistema de daño localizado. El estado se guarda en el NBT persistente de la víctima,
 * por lo que no modifica los modelos ni las clases originales de Minecraft.
 */
public final class LimbSystem {
    public static final int LEFT_ARM = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_LEG = 4;
    public static final int RIGHT_LEG = 8;
    public static final int HEAD = 16;
    public static final int NON_VITAL = LEFT_ARM | RIGHT_ARM | LEFT_LEG | RIGHT_LEG;

    private static final String KEY_LIMBS = "mimicer_limbs";
    private static final String KEY_BLEED = "mimicer_bleed";

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.02F), 1.1F);
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.3F, 0.0F, 0.0F), 1.4F);

    private LimbSystem() {}

    public static boolean canLoseLimbs(LivingEntity e) {
        return e instanceof Player || e instanceof AbstractVillager || e instanceof Animal;
    }

    public static int get(LivingEntity e) {
        return e.getPersistentData().getInt(KEY_LIMBS);
    }

    public static int getBleed(LivingEntity e) {
        return e.getPersistentData().getInt(KEY_BLEED);
    }

    public static void clear(LivingEntity e) {
        CompoundTag tag = e.getPersistentData();
        tag.remove(KEY_LIMBS);
        tag.remove(KEY_BLEED);
    }

    public static boolean has(int mask, int limb) {
        return (mask & limb) != 0;
    }

    /** Devuelve una extremidad no vital que aún conserva la víctima, o 0 si no le queda ninguna. */
    public static int pickIntactLimb(LivingEntity victim, RandomSource random) {
        int lost = get(victim);
        int[] options = {LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG};
        int start = random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            int limb = options[(start + i) % 4];
            if (!has(lost, limb)) {
                return limb;
            }
        }
        return 0;
    }

    /** Arranca una extremidad: la víctima puede sobrevivir, pero sangra y queda debilitada. */
    public static void rip(ServerLevel level, LivingEntity victim, int limb) {
        CompoundTag tag = victim.getPersistentData();
        tag.putInt(KEY_LIMBS, get(victim) | limb);
        tag.putInt(KEY_BLEED, 1200);
        Vec3 pos = limbPosition(victim, limb);
        spawnBlood(level, pos, 40);
        level.playSound(null, victim.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.0F, 0.5F);
        level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.6F);
        applyEffects(victim);
    }

    private static Vec3 limbPosition(LivingEntity victim, int limb) {
        double y = victim.getY() + (has(limb, LEFT_LEG | RIGHT_LEG) ? victim.getBbHeight() * 0.3 : victim.getBbHeight() * 0.65);
        return new Vec3(victim.getX(), y, victim.getZ());
    }

    public static void spawnBlood(ServerLevel level, Vec3 pos, int count) {
        level.sendParticles(BLOOD, pos.x, pos.y, pos.z, count, 0.25, 0.25, 0.25, 0.08);
        level.sendParticles(BLOOD_DARK, pos.x, pos.y, pos.z, count / 2, 0.15, 0.15, 0.15, 0.03);
    }

    /** Se llama cada segundo para entidades con extremidades perdidas o sangrado. */
    public static void tickSecond(ServerLevel level, LivingEntity e) {
        int lost = get(e);
        int bleed = getBleed(e);
        if (lost == 0 && bleed <= 0) {
            return;
        }
        applyEffects(e);
        if (bleed > 0) {
            if (e.hasEffect(MobEffects.REGENERATION)) {
                e.getPersistentData().putInt(KEY_BLEED, 0);
                return;
            }
            e.getPersistentData().putInt(KEY_BLEED, Math.max(0, bleed - 20));
            spawnBlood(level, new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ()), 6);
            if (e.getHealth() > 1.0F || e instanceof Player == false) {
                // El sangrado nunca baja de medio corazón a los jugadores: el golpe final lo da el Mímico.
                e.hurt(level.damageSources().generic(), 1.0F);
            }
        }
    }

    private static void applyEffects(LivingEntity e) {
        int lost = get(e);
        int arms = Integer.bitCount(lost & (LEFT_ARM | RIGHT_ARM));
        int legs = Integer.bitCount(lost & (LEFT_LEG | RIGHT_LEG));
        if (e instanceof Animal) {
            // Los animales usan las cuatro ranuras como patas.
            legs = Integer.bitCount(lost & NON_VITAL);
            arms = 0;
        }
        if (arms > 0) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, arms - 1, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, arms, false, false));
        }
        if (legs > 0) {
            int amp = legs >= 2 ? 3 + legs : 1;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, amp, false, false));
        }
    }
}
