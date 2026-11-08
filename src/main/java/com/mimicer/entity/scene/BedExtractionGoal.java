package com.mimicer.entity.scene;

import com.mimicer.hide.HideManager;
import com.mimicer.hide.NoiseManager;
import com.mimicer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Saca a una víctima escondida bajo una cama, con escena propia de cada monstruo: se acerca por un lado de la cama,
 * se agacha, busca con el brazo debajo, la agarra y la arrastra poco a poco hasta dejarla delante de sí. Solo
 * entonces empieza el ataque. Una víctima solo puede estar reservada por un monstruo a la vez.
 */
public class BedExtractionGoal extends Goal {
    private enum Phase { WALK, SCENE, DONE }

    private final SceneHost host;
    private final PathfinderMob mob;
    private final SceneTiming.Kind kind;

    private LivingEntity pendingVictim;
    private BlockPos pendingBed;
    private LivingEntity victim;
    private BlockPos bed;
    private Vec3 spot;
    private Vec3 bedCenter;
    private Vec3 dragEnd;
    private Phase phase = Phase.DONE;
    private int walkTicks;
    private int sceneStart;
    private boolean grabbed;
    private boolean finished;

    public BedExtractionGoal(SceneHost host, SceneTiming.Kind kind) {
        this.host = host;
        this.mob = host.mob();
        this.kind = kind;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public boolean isActive() {
        return pendingVictim != null || phase != Phase.DONE;
    }

    public boolean request(LivingEntity v, BlockPos bedPos) {
        if ((pendingVictim == v) || (victim == v && phase != Phase.DONE)) {
            return true;
        }
        if (isActive() || host.isBusyWithScene() || !host.sceneAllowed()
                || !HideManager.claimExtraction(v.getUUID(), mob.getUUID())) {
            return false;
        }
        pendingVictim = v;
        pendingBed = bedPos;
        return true;
    }

    @Override
    public boolean canUse() {
        if (pendingVictim == null) {
            return false;
        }
        if (!host.sceneAllowed() || !pendingVictim.isAlive() || !HideManager.isHiddenVictim(pendingVictim)) {
            HideManager.releaseExtraction(pendingVictim.getUUID(), mob.getUUID());
            pendingVictim = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && host.sceneAllowed() && victim != null && victim.isAlive();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        victim = pendingVictim;
        bed = pendingBed;
        pendingVictim = null;
        pendingBed = null;
        grabbed = false;
        finished = false;
        walkTicks = 0;
        mob.setTarget(null);
        bedCenter = new Vec3(bed.getX() + 0.5, bed.getY() + 0.02, bed.getZ() + 0.5);
        spot = findSpot();
        if (spot == null) {
            phase = Phase.DONE;
            host.onExtractFailed(victim);
            return;
        }
        Vec3 dir = bedCenter.subtract(spot);
        dir = new Vec3(dir.x, 0.0, dir.z).normalize();
        dragEnd = new Vec3(spot.x + dir.x * 0.45, bed.getY() + 0.02, spot.z + dir.z * 0.45);
        phase = Phase.WALK;
    }

    /** Casilla libre junto a la cama (no la otra mitad) más cercana al monstruo. */
    private Vec3 findSpot() {
        BlockPos other = HideManager.otherBedHalf(mob.level(), bed);
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos half : new BlockPos[]{bed, other}) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos cell = half.relative(d);
                if (cell.equals(bed) || cell.equals(other)) continue;
                if (!mob.level().getBlockState(cell).getCollisionShape(mob.level(), cell).isEmpty()
                        || !mob.level().getBlockState(cell.above()).getCollisionShape(mob.level(), cell.above()).isEmpty()
                        || mob.level().getBlockState(cell.below()).getCollisionShape(mob.level(), cell.below()).isEmpty()
                        || mob.level().getBlockState(cell).getBlock() instanceof BedBlock) continue;
                Vec3 p = new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
                double dist = p.distanceToSqr(mob.position());
                if (dist < bestD) {
                    bestD = dist;
                    best = p;
                }
            }
        }
        return best;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || phase == Phase.DONE) {
            return;
        }
        if (!HideManager.isHiddenVictim(victim)) {
            // La cama desapareció o la víctima salió: si ya la tenía agarrada, termina; si no, abandona.
            if (grabbed && !finished) {
                finish(level);
            } else {
                phase = Phase.DONE;
                host.onExtractFailed(victim);
            }
            return;
        }
        if (phase == Phase.WALK) {
            tickWalk(level);
            return;
        }
        tickScene(level);
    }

    private void tickWalk(ServerLevel level) {
        walkTicks++;
        double d2 = (mob.getX() - spot.x) * (mob.getX() - spot.x) + (mob.getZ() - spot.z) * (mob.getZ() - spot.z);
        if (d2 < 0.5) {
            phase = Phase.SCENE;
            sceneStart = (int) level.getGameTime();
            host.setExtractStart(sceneStart);
            host.scenePosture(true);
            mob.getNavigation().stop();
            return;
        }
        if (walkTicks % 10 == 1) {
            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1D);
        }
        if (walkTicks == 40) {
            host.scenePosture(true);      // las criaturas altas se agachan para entrar por puertas bajas
        }
        if (walkTicks > 240 || (walkTicks > 60 && mob.getNavigation().isDone() && d2 > 4.0)) {
            phase = Phase.DONE;
            host.onExtractFailed(victim);
        }
    }

    private void tickScene(ServerLevel level) {
        int t = (int) (level.getGameTime() - sceneStart);
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
        float yaw = (float) (Mth.atan2(bedCenter.z - mob.getZ(), bedCenter.x - mob.getX()) * 180.0 / Math.PI) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);

        if (t < kind.grab) {
            // Busca a tientas bajo la cama: un roce de tela cada pocos ticks.
            if (t > kind.kneel && t % 12 == 6) {
                level.playSound(null, bed, SoundEvents.WOOL_HIT, SoundSource.HOSTILE, 0.6F, 0.5F + mob.getRandom().nextFloat() * 0.2F);
            }
            return;
        }
        if (!grabbed) {
            grabbed = true;
            grab(level);
        }
        float p = Mth.clamp((t - kind.grab) / (float) (kind.end - kind.grab), 0.0F, 1.0F);
        moveVictim(level, p);
        if ((t - kind.grab) % 16 == 0) {
            level.playSound(null, victim.blockPosition(), SoundEvents.WOOL_STEP, SoundSource.HOSTILE, 0.8F, 0.6F);
            if (victim instanceof Villager) {
                level.playSound(null, victim.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.9F, 0.9F);
            }
            NoiseManager.emit(level, victim.position(), 12.0F);
        }
        if (t >= kind.end) {
            finish(level);
        }
    }

    private void grab(ServerLevel level) {
        if (kind == SceneTiming.Kind.MIMIC) {
            level.playSound(null, bed, ModSounds.MIMIC_SCREAM.get(), SoundSource.HOSTILE, 1.5F, 1.1F);
        } else {
            level.playSound(null, bed, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5F, 0.4F);
        }
        if (victim instanceof Villager v) {
            v.setInvisible(false);
            v.setPose(Pose.SLEEPING);       // tumbado mientras lo arrastran
        }
    }

    private void moveVictim(ServerLevel level, float p) {
        float e = p * p * (3.0F - 2.0F * p);
        Vec3 pos = bedCenter.lerp(dragEnd, e);
        float yaw = (float) (Mth.atan2(spot.z - pos.z, spot.x - pos.x) * 180.0 / Math.PI) - 90.0F;
        if (victim instanceof ServerPlayer sp) {
            sp.setDeltaMovement(Vec3.ZERO);
            sp.teleportTo(level, pos.x, pos.y, pos.z, yaw, -25.0F);     // la cámara mira hacia el monstruo
        } else {
            victim.setDeltaMovement(Vec3.ZERO);
            victim.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        }
    }

    private void finish(ServerLevel level) {
        finished = true;
        LivingEntity v = victim;
        if (v instanceof ServerPlayer sp) {
            HideManager.stopHiding(sp);
        } else if (v instanceof Villager vil) {
            HideManager.unhideVillager(vil);
            vil.setPose(Pose.STANDING);
        }
        phase = Phase.DONE;
        host.onExtracted(v);
    }

    @Override
    public void stop() {
        if (victim != null) {
            HideManager.releaseExtraction(victim.getUUID(), mob.getUUID());
            if (victim instanceof Villager vil && vil.getPose() == Pose.SLEEPING && !vil.isSleeping()) {
                vil.setPose(Pose.STANDING);
            }
        }
        if (pendingVictim != null) {
            HideManager.releaseExtraction(pendingVictim.getUUID(), mob.getUUID());
            pendingVictim = null;
        }
        victim = null;
        phase = Phase.DONE;
        host.setExtractStart(-1);
        host.scenePosture(false);
        mob.getNavigation().stop();
    }
}
