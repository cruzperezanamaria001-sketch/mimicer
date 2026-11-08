package com.mimicer.entity.scene;

import com.mimicer.blood.BloodSystem;
import com.mimicer.entity.CorpseEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Tras matar, el monstruo va hasta el cadáver, se agacha y se lo come mordisco a mordisco: cada mordisco arranca
 * una extremidad del cuerpo (la misma máscara que usa el sistema de mutilaciones) justo cuando la animación
 * levanta la cabeza con ella en la boca. Mientras tanto no ataca. Si lo hieren, deja de comer.
 */
public class FeedGoal extends Goal {
    private enum Phase { WALK, EAT, DONE }

    private final SceneHost host;
    private final PathfinderMob mob;
    private CorpseEntity pending;
    private CorpseEntity corpse;
    private Phase phase = Phase.DONE;
    private int walkTicks;
    private int startTime;
    private int startedTick;
    private int lastBiteT;
    private boolean noMore;

    public FeedGoal(SceneHost host) {
        this.host = host;
        this.mob = host.mob();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public boolean isActive() {
        return pending != null || phase != Phase.DONE;
    }

    /** El cadáver acaba de crearse: reserva el cuerpo para este monstruo (nadie más puede comérselo). */
    public void assign(CorpseEntity c) {
        if (isActive() || host.isBusyWithScene() || !c.claimForEating(mob.getUUID())) {
            return;
        }
        pending = c;
    }

    @Override
    public boolean canUse() {
        if (pending == null) {
            return false;
        }
        if (!pending.isAlive() || !host.sceneAllowed() || host.isExtracting()) {
            pending.releaseEater(mob.getUUID());
            pending = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && corpse != null && corpse.isAlive() && host.sceneAllowed()
                && mob.getLastHurtByMobTimestamp() <= startedTick;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        corpse = pending;
        pending = null;
        phase = Phase.WALK;
        walkTicks = 0;
        noMore = false;
        startedTick = mob.tickCount;
        mob.setTarget(null);
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        double dx = corpse.getX() - mob.getX(), dz = corpse.getZ() - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (phase == Phase.WALK) {
            walkTicks++;
            if (dist < 1.45) {
                beginEating(level);
            } else {
                if (walkTicks % 10 == 1) {
                    Vec3 away = new Vec3(-dx, 0.0, -dz);
                    away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
                    Vec3 stand = corpse.position().add(away.scale(0.95));
                    mob.getNavigation().moveTo(stand.x, stand.y, stand.z, 1.15D);
                }
                if (walkTicks > 160 || (walkTicks > 30 && mob.getNavigation().isDone() && dist > 3.0)) {
                    phase = Phase.DONE;       // no llega: abandona el cuerpo
                }
                return;
            }
        }
        if (phase != Phase.EAT) {
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(corpse.getX(), corpse.getY(), corpse.getZ());
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);

        int t = (int) (level.getGameTime() - startTime);
        int c = t - SceneTiming.FEED_KNEEL;
        if (c >= SceneTiming.FEED_BITE_AT && (c - SceneTiming.FEED_BITE_AT) % SceneTiming.FEED_CYCLE == 0) {
            bite(level, t);
        } else if (c >= 30 && c % SceneTiming.FEED_CYCLE >= 30 && t % 12 == 0) {
            level.playSound(null, corpse.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.7F, 0.5F);
        }
        if ((noMore && t >= lastBiteT + 60) || t > 1200) {
            phase = Phase.DONE;
        }
    }

    private void beginEating(ServerLevel level) {
        phase = Phase.EAT;
        startTime = (int) level.getGameTime();
        host.setFeedStart(startTime);
        host.scenePosture(true);
        mob.getNavigation().stop();
    }

    private void bite(ServerLevel level, int t) {
        lastBiteT = t;
        int limb = corpse.nextEdibleLimb();
        if (limb == 0) {
            noMore = true;
            return;
        }
        corpse.eatLimb(limb);
        level.playSound(null, corpse.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.0F, 0.4F);
        level.playSound(null, corpse.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.5F);
        BloodSystem.biteBurst(level, corpse.position(), corpse);
        if (corpse.nextEdibleLimb() == 0) {
            noMore = true;
        }
    }

    @Override
    public void stop() {
        if (corpse != null) {
            corpse.releaseEater(mob.getUUID());
        }
        if (pending != null) {
            pending.releaseEater(mob.getUUID());
            pending = null;
        }
        corpse = null;
        phase = Phase.DONE;
        host.setFeedStart(-1);
        host.scenePosture(false);
        mob.getNavigation().stop();
    }
}
