package com.mimicer.blood;

import com.mimicer.block.BloodStainBlock;
import com.mimicer.config.BloodConfig;
import com.mimicer.entity.GlitchEntity;
import com.mimicer.entity.VoidEntity;
import com.mimicer.limb.LimbSystem;
import com.mimicer.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Dynamic Blood System. Las manchas son bloques finos ({@link BloodStainBlock}): se sincronizan solas en
 * multijugador, caducan por ticks programados y se limitan con un presupuesto por minuto. Las salpicaduras se
 * calculan con trazados de rayos desde el punto de impacto en la dirección del golpe, con dispersión aleatoria,
 * de modo que cada impacto produce un patrón distinto.
 */
public final class BloodSystem {
    public static final String KEY_WOUND = "mimicer_wound";

    // variantes de textura de mancha
    public static final int V_BIG_A = 0, V_BIG_B = 1, V_MEDIUM = 2, V_SPRAY = 3, V_DROPS = 4, V_SMEAR = 5;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.03F), 1.0F);
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.32F, 0.0F, 0.0F), 1.3F);

    // presupuesto de manchas: ventana deslizante de 60 s
    private static long curSec = -1;
    private static final int[] BUCKET = new int[60];
    private static int bucketTotal;
    // presupuesto de partículas por tick
    private static int particleTick = -1;
    private static int particlesLeft;

    private BloodSystem() {}

    // ------------------------------------------------------------------ quién sangra

    public static boolean canBleed(LivingEntity e) {
        if (e instanceof Player p) {
            return !p.isSpectator() && !p.isCreative();
        }
        if (e instanceof GlitchEntity || e instanceof VoidEntity || e instanceof com.mimicer.entity.ReaperEntity
                || e instanceof AbstractGolem
                || e instanceof AbstractSkeleton || e instanceof Slime || e instanceof Blaze) {
            return false;
        }
        return e instanceof AbstractVillager || e instanceof Animal || e instanceof Monster
                || e instanceof com.mimicer.entity.MimicEntity;
    }

    /** Solo los golpes, proyectiles, explosiones y caídas salpican; el fuego, el hambre o el ahogamiento no. */
    public static boolean bloodySource(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        return src.getEntity() != null || src.getDirectEntity() != null || src.is(DamageTypeTags.IS_PROJECTILE)
                || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FALL);
    }

    // ------------------------------------------------------------------ presupuestos

    private static boolean reserveStain(long gameTime) {
        long sec = gameTime / 20;
        if (sec < curSec || sec - curSec >= 60) {
            java.util.Arrays.fill(BUCKET, 0);
            bucketTotal = 0;
            curSec = sec;
        } else {
            for (long s = curSec + 1; s <= sec; s++) {
                int i = (int) (s % 60);
                bucketTotal -= BUCKET[i];
                BUCKET[i] = 0;
            }
            curSec = sec;
        }
        if (bucketTotal >= BloodConfig.MAX_STAINS_PER_MINUTE.get()) {
            return false;
        }
        BUCKET[(int) (sec % 60)]++;
        bucketTotal++;
        return true;
    }

    private static boolean takeParticles(ServerLevel level, int n) {
        if (!BloodConfig.PARTICLES.get() || level.getServer() == null) return false;
        int tick = level.getServer().getTickCount();
        if (tick != particleTick) {
            particleTick = tick;
            particlesLeft = BloodConfig.MAX_PARTICLES_PER_TICK.get();
        }
        if (particlesLeft < n) return false;
        particlesLeft -= n;
        return true;
    }

    public static int phaseTicks(RandomSource r) {
        int total = BloodConfig.STAIN_LIFETIME_SECONDS.get() * 20 / 3;
        return Math.max(20, (int) (total * (0.8 + r.nextDouble() * 0.4)));
    }

    // ------------------------------------------------------------------ colocación de manchas

    /** Coloca (o completa) una mancha en la celda de aire {@code pos}, pegada a su cara {@code attach}. */
    public static boolean placeStain(ServerLevel level, BlockPos pos, Direction attach, int variant, int age) {
        if (!level.isLoaded(pos) || !BloodConfig.ENABLED.get()) return false;
        if (attach.getAxis().isHorizontal() && !BloodConfig.WALL_SPLATTER.get()) return false;
        if (!BloodStainBlock.canAttach(level, pos, attach)) return false;
        BlockState cur = level.getBlockState(pos);
        BlockState next;
        boolean fresh = false;
        if (cur.is(ModBlocks.BLOOD_STAIN.get())) {
            if (cur.getValue(BloodStainBlock.face(attach))) return false;
            next = cur.setValue(BloodStainBlock.face(attach), true);
        } else if (cur.isAir()) {
            next = ModBlocks.BLOOD_STAIN.get().defaultBlockState()
                    .setValue(BloodStainBlock.face(attach), true)
                    .setValue(BloodStainBlock.VARIANT, variant)
                    .setValue(BloodStainBlock.AGE, age);
            fresh = true;
        } else {
            return false;
        }
        if (!reserveStain(level.getGameTime())) return false;
        level.setBlock(pos, next, 2);
        if (fresh) {
            level.scheduleTick(pos, ModBlocks.BLOOD_STAIN.get(), phaseTicks(level.random));
        }
        return true;
    }

    /** Las manchas grandes se expanden: por el suelo hacia un lado, o goteando por la pared hacia abajo. */
    public static void spreadFrom(ServerLevel level, BlockPos pos, BlockState state, RandomSource r) {
        for (Direction f : Direction.values()) {
            if (!state.getValue(BloodStainBlock.face(f))) continue;
            if (f == Direction.DOWN) {
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                placeStain(level, pos.relative(side), Direction.DOWN, V_MEDIUM, 1);
            } else if (f.getAxis().isHorizontal()) {
                placeStain(level, pos.below(), f, V_SPRAY, 1);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ salpicadura direccional

    private static Vec3 jitter(Vec3 dir, double cone, RandomSource r) {
        Vec3 v = dir.add((r.nextDouble() - 0.5) * 2 * cone, (r.nextDouble() - 0.5) * 2 * cone, (r.nextDouble() - 0.5) * 2 * cone);
        return v.lengthSqr() < 1.0E-6 ? dir : v.normalize();
    }

    /**
     * Lanza {@code rays} rayos desde {@code origin} con dispersión {@code cone} y mancha donde chocan. Los que no
     * chocan caen como gotas al suelo. Devuelve cuántas manchas se colocaron.
     */
    public static int splatter(ServerLevel level, Entity ctx, Vec3 origin, Vec3 dir, int rays, double cone,
                               double reach, boolean wallsOnly) {
        RandomSource r = level.random;
        int placed = 0;
        int cap = BloodConfig.MAX_STAINS_PER_EVENT.get();
        for (int i = 0; i < rays && placed < cap; i++) {
            Vec3 d = jitter(dir, cone, r);
            if (r.nextInt(100) < 30) {
                d = new Vec3(d.x, d.y - 0.5 - r.nextDouble() * 0.6, d.z).normalize();   // gotas hacia el suelo
            }
            double range = 0.8 + r.nextDouble() * reach;
            Vec3 end = origin.add(d.scale(range));
            BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, ctx));
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos at = hit.getBlockPos().relative(hit.getDirection());
                double dist = hit.getLocation().distanceTo(origin);
                int variant = dist < 1.6 ? (r.nextBoolean() ? V_BIG_A : V_BIG_B)
                        : dist < 3.0 ? V_MEDIUM : (r.nextBoolean() ? V_SPRAY : V_DROPS);
                if (hit.getDirection().getAxis().isHorizontal() && variant == V_DROPS) variant = V_SPRAY;
                if (placeStain(level, at, hit.getDirection().getOpposite(), variant, 0)) {
                    placed++;
                    // mancha vecina en el mismo plano: las salpicaduras fuertes son más anchas
                    if (reach > 3.0 && r.nextInt(100) < 40 && placed < cap) {
                        Direction t = tangent(hit.getDirection(), r);
                        if (placeStain(level, at.relative(t), hit.getDirection().getOpposite(), V_MEDIUM, 0)) placed++;
                    }
                }
            } else if (!wallsOnly) {
                // trayectoria caída: busca el suelo bajo el punto final
                BlockHitResult floor = level.clip(new ClipContext(end, end.add(0.0, -4.0, 0.0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
                if (floor.getType() == HitResult.Type.BLOCK && floor.getDirection() == Direction.UP
                        && placeStain(level, floor.getBlockPos().above(), Direction.DOWN,
                        r.nextInt(100) < 60 ? V_DROPS : V_SPRAY, 0)) {
                    placed++;
                }
            }
        }
        return placed;
    }

    private static Direction tangent(Direction normal, RandomSource r) {
        Direction[] all = Direction.values();
        for (int tries = 0; tries < 8; tries++) {
            Direction d = all[r.nextInt(6)];
            if (d.getAxis() != normal.getAxis()) return d;
        }
        return Direction.UP;
    }

    /** Charco circular en el suelo, más denso en el centro. */
    public static int pool(ServerLevel level, Vec3 center, double radius, int maxCells, Entity ctx) {
        RandomSource r = level.random;
        int placed = 0;
        int r0 = (int) Math.ceil(radius);
        for (int dx = -r0; dx <= r0 && placed < maxCells; dx++) {
            for (int dz = -r0; dz <= r0 && placed < maxCells; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius || r.nextDouble() > 1.1 - dist / (radius + 0.5)) continue;
                int x = Mth.floor(center.x) + dx, z = Mth.floor(center.z) + dz;
                BlockPos top = new BlockPos(x, Mth.floor(center.y) + 1, z);
                for (int k = 0; k < 4; k++) {            // baja hasta encontrar suelo
                    BlockPos cell = top.below(k);
                    if (!level.isLoaded(cell)) break;
                    if (level.getBlockState(cell).isAir() || level.getBlockState(cell).is(ModBlocks.BLOOD_STAIN.get())) {
                        if (BloodStainBlock.canAttach(level, cell, Direction.DOWN)) {
                            if (placeStain(level, cell, Direction.DOWN, dist < radius * 0.5 ? (r.nextBoolean() ? V_BIG_A : V_BIG_B) : V_MEDIUM, 0)) {
                                placed++;
                            }
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return placed;
    }

    private static void spray(ServerLevel level, Vec3 origin, Vec3 dir, int emitters, double speed) {
        if (emitters <= 0 || !takeParticles(level, emitters)) return;
        RandomSource r = level.random;
        for (int i = 0; i < emitters; i++) {
            Vec3 d = jitter(dir, 0.45, r);
            level.sendParticles(i % 3 == 0 ? BLOOD_DARK : BLOOD, origin.x, origin.y, origin.z, 0,
                    d.x, d.y, d.z, speed * (0.5 + r.nextDouble()));
        }
    }

    // ------------------------------------------------------------------ dirección del impacto

    public static Vec3 impactDir(LivingEntity victim, DamageSource src) {
        Entity direct = src.getDirectEntity();
        Vec3 d = null;
        if (direct instanceof Projectile && direct.getDeltaMovement().lengthSqr() > 1.0E-4) {
            d = direct.getDeltaMovement().normalize();
        } else if (direct != null) {
            d = victim.position().subtract(direct.position());
        } else if (src.getSourcePosition() != null) {
            d = victim.position().subtract(src.getSourcePosition());
        }
        if (d == null || d.lengthSqr() < 1.0E-4) {
            double a = victim.getRandom().nextDouble() * Math.PI * 2;
            return new Vec3(Math.cos(a), 0.2, Math.sin(a));
        }
        return new Vec3(d.x, d.y * 0.3 + 0.15, d.z).normalize();
    }

    // ------------------------------------------------------------------ golpe

    public static void onHit(ServerLevel level, LivingEntity victim, DamageSource src, float amount) {
        if (src.getDirectEntity() instanceof GlitchEntity) {
            return;     // el rayo morado salpica por su cuenta (dirección del rayo), ver impactBurst
        }
        double mult = BloodConfig.INTENSITY.get();
        double intensity = Mth.clamp(amount / 4.0, 0.25, 4.0) * mult;
        Vec3 origin = victim.position().add(0.0, victim.getBbHeight() * 0.65, 0.0);
        Vec3 dir = impactDir(victim, src);
        hitSplash(level, victim, origin, dir, intensity);
        addWound(victim, (float) Math.min(6.0, amount / Math.max(4.0F, victim.getMaxHealth()) * 10.0));
    }

    /** Golpe muy violento (p. ej. ataques de The Reaper): salpicadura mayor que el golpe normal. */
    public static void heavyHit(ServerLevel level, LivingEntity victim, Vec3 dir, double strength) {
        Vec3 origin = victim.position().add(0.0, victim.getBbHeight() * 0.65, 0.0);
        hitSplash(level, victim, origin, dir.normalize(), Math.max(2.0, strength) * BloodConfig.INTENSITY.get());
        addWound(victim, 5.0F);
    }

    /**
     * Estampido contra el suelo (p. ej. el golpe final de The Void): gran charco, salpicadura radial baja que
     * alcanza paredes cercanas y chorro de partículas.
     */
    public static void impactBurst(ServerLevel level, LivingEntity victim, Vec3 pos, double strength) {
        if (!BloodConfig.ENABLED.get()) return;
        double s = strength * BloodConfig.INTENSITY.get();
        pool(level, pos, 1.4 + s * 0.7, BloodConfig.MAX_STAINS_PER_EVENT.get() * 2, victim);
        RandomSource r = level.random;
        Vec3 low = pos.add(0.0, 0.3, 0.0);
        int rays = Mth.clamp((int) Math.round(6 + s * 3), 6, 16);
        for (int i = 0; i < rays; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            splatter(level, victim, low, new Vec3(Math.cos(a), 0.15 + r.nextDouble() * 0.4, Math.sin(a)), 1, 0.15,
                    2.5 + s * 1.2, false);
        }
        spray(level, low, new Vec3(0.0, 1.0, 0.0), 10, 0.3);
    }

    /** Mordisco de un monstruo sobre un cadáver: pequeño charco y salpicadura hacia arriba y los lados. */
    public static void biteBurst(ServerLevel level, Vec3 pos, Entity ctx) {
        if (!BloodConfig.ENABLED.get()) return;
        double mult = BloodConfig.INTENSITY.get();
        Vec3 origin = pos.add(0.0, 0.35, 0.0);
        splatter(level, ctx, origin, new Vec3(0.0, 1.0, 0.0), Math.max(3, (int) Math.round(6 * mult)), 0.9, 2.4, false);
        pool(level, pos, 0.9 + 0.3 * mult, 4, ctx);
        spray(level, origin, new Vec3(0.0, 1.0, 0.0), 6, 0.25);
    }

    private static void hitSplash(ServerLevel level, LivingEntity victim, Vec3 origin, Vec3 dir, double intensity) {
        int rays = Mth.clamp((int) Math.round(2 + intensity * 3), 2, 16);
        int forward = Math.max(1, (int) (rays * 0.7));
        double reach = 1.5 + intensity * 1.4;
        splatter(level, victim, origin, dir, forward, 0.35, reach, false);
        splatter(level, victim, origin, dir.scale(-1.0).add(0.0, 0.5, 0.0).normalize(), rays - forward, 0.7, reach * 0.6, false);
        spray(level, origin, dir, Math.min(10, 2 + (int) (intensity * 2)), 0.25);
    }

    // ------------------------------------------------------------------ muerte

    public static void onDeath(ServerLevel level, LivingEntity victim) {
        if (!BloodConfig.ENABLED.get() || !BloodConfig.DEATH_POOLS.get()) return;
        int lost = Integer.bitCount(LimbSystem.get(victim) & (LimbSystem.NON_VITAL | LimbSystem.HEAD));
        double wound = victim.getPersistentData().getFloat(KEY_WOUND);
        double strength = (1.0 + lost * 0.6 + Math.min(4.0, wound) * 0.2) * BloodConfig.INTENSITY.get();
        Vec3 pos = victim.position();
        pool(level, pos, 0.9 + strength * 0.5, Math.min(BloodConfig.MAX_STAINS_PER_EVENT.get() * 2, 6 + (int) (strength * 5)), victim);
        // salpicadura horizontal para manchar las paredes cercanas
        Vec3 chest = pos.add(0.0, Math.max(0.3, victim.getBbHeight() * 0.35), 0.0);
        RandomSource r = level.random;
        int rays = Mth.clamp((int) Math.round(4 + strength * 3), 4, 14);
        for (int i = 0; i < rays; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            splatter(level, victim, chest, new Vec3(Math.cos(a), 0.1 + r.nextDouble() * 0.5, Math.sin(a)), 1, 0.15,
                    2.2 + strength, true);
        }
        spray(level, chest, new Vec3(0, 1, 0), 4, 0.15);
    }

    /** Un cadáver con heridas sigue manando un rato: el charco crece despacio. */
    public static void corpseSeep(ServerLevel level, Entity corpse, int limbs, int age) {
        if (!BloodConfig.ENABLED.get() || !BloodConfig.CORPSE_SEEPAGE.get()) return;
        int lost = Integer.bitCount(limbs & (LimbSystem.NON_VITAL | LimbSystem.HEAD));
        if (lost == 0 && level.random.nextInt(4) != 0) return;
        double radius = 0.8 + Math.min(1.8, age / 600.0) + lost * 0.25;
        pool(level, corpse.position(), radius, 3 + lost, corpse);
    }

    // ------------------------------------------------------------------ mutilaciones

    /** Arrancar una extremidad: salpicadura mucho más intensa y distinta según la parte afectada. */
    public static void mutilation(ServerLevel level, LivingEntity victim, int limb) {
        if (!BloodConfig.ENABLED.get()) return;
        double mult = BloodConfig.INTENSITY.get();
        RandomSource r = level.random;
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        double h = victim.getBbHeight();
        Vec3 pos = victim.position();
        Vec3 origin;
        Vec3 dir;
        int rays;
        double reach;
        double poolRadius;
        if (LimbSystem.has(limb, LimbSystem.HEAD)) {
            origin = pos.add(0.0, h * 0.9, 0.0);
            dir = new Vec3(0.0, 1.0, 0.0);              // chorro hacia arriba que cae alrededor
            rays = 16; reach = 4.5; poolRadius = 2.0;
        } else if (LimbSystem.has(limb, LimbSystem.LEFT_ARM | LimbSystem.RIGHT_ARM)) {
            origin = pos.add(0.0, h * 0.7, 0.0);
            dir = LimbSystem.has(limb, LimbSystem.LEFT_ARM) ? right.scale(-1.0) : right;
            dir = dir.add(0.0, 0.15, 0.0).normalize();   // abanico lateral
            rays = 12; reach = 4.0; poolRadius = 1.3;
        } else {
            origin = pos.add(0.0, h * 0.35, 0.0);
            dir = (LimbSystem.has(limb, LimbSystem.LEFT_LEG) ? right.scale(-1.0) : right).add(fwd.scale(0.4)).normalize();
            rays = 10; reach = 3.2; poolRadius = 2.0;    // bajo y con gran charco
        }
        rays = (int) Math.min(18, Math.round(rays * Math.max(0.5, mult)));
        splatter(level, victim, origin, dir, rays, 0.55, reach * Math.max(0.6, mult), false);
        // chorro secundario aleatorio en otra dirección para que no se repita
        double a = r.nextDouble() * Math.PI * 2;
        splatter(level, victim, origin, new Vec3(Math.cos(a), 0.3, Math.sin(a)), Math.max(2, rays / 3), 0.5, reach, false);
        pool(level, pos, poolRadius * Math.max(0.6, mult), 12, victim);
        spray(level, origin, dir, 10, 0.4);
        addWound(victim, 4.0F);
    }

    // ------------------------------------------------------------------ heridas y rastros

    public static float getWound(LivingEntity e) {
        return e.getPersistentData().getFloat(KEY_WOUND);
    }

    public static void addWound(LivingEntity e, float sev) {
        if (!canBleed(e) || sev <= 0) return;
        CompoundTag tag = e.getPersistentData();
        tag.putFloat(KEY_WOUND, Math.min(10.0F, tag.getFloat(KEY_WOUND) + sev));
    }

    /** Cada 10 ticks: las víctimas heridas gotean; moviéndose dejan rastro. */
    public static void tickWound(ServerLevel level, LivingEntity e) {
        if (!BloodConfig.ENABLED.get() || !canBleed(e) || !e.isAlive()) return;
        CompoundTag tag = e.getPersistentData();
        float wound = tag.getFloat(KEY_WOUND);
        int lost = Integer.bitCount(LimbSystem.get(e) & (LimbSystem.NON_VITAL | LimbSystem.HEAD));
        if (wound <= 0.0F && lost == 0) return;
        if (wound > 0.0F) {
            wound = Math.max(0.0F, wound - 10.0F / (BloodConfig.WOUND_DECAY_SECONDS.get() * 2.0F));
            tag.putFloat(KEY_WOUND, wound);
        }
        if (!BloodConfig.TRAILS.get() || !e.onGround() || e.isInWater()) return;
        double eff = wound + lost * 1.5;
        double hp = e.getHealth() / Math.max(1.0F, e.getMaxHealth());
        eff *= 1.5 - 0.5 * hp;                       // cuanto menos vida, más sangra
        if (eff < 0.5) return;
        Vec3 mv = e.getDeltaMovement();
        boolean moving = mv.x * mv.x + mv.z * mv.z > 0.0004;
        double p = Math.min(0.95, eff * 0.09 * BloodConfig.TRAIL_FREQUENCY.get() * (moving ? 1.0 : 0.4));
        RandomSource r = level.random;
        if (r.nextDouble() > p) return;
        BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.05, e.getZ());
        int variant = (moving && eff >= 3.0 && r.nextInt(100) < 35) ? V_SMEAR : V_DROPS;
        if (eff >= 6.0 && r.nextInt(100) < 40) variant = V_MEDIUM;
        placeStain(level, feet, Direction.DOWN, variant, 0);
        if (moving && eff >= 4.0 && r.nextBoolean()) {
            Vec3 back = new Vec3(-mv.x, 0.0, -mv.z).normalize().scale(1.0);
            placeStain(level, BlockPos.containing(e.getX() + back.x, e.getY() + 0.05, e.getZ() + back.z),
                    Direction.DOWN, V_DROPS, 0);
        }
        if (takeParticles(level, 1)) {
            level.sendParticles(BLOOD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 0, 0.0, -0.4, 0.0, 0.2);
        }
    }
}
