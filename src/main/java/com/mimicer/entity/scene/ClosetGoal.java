package com.mimicer.entity.scene;

import com.mimicer.block.WardrobeBlock;
import com.mimicer.config.ClosetConfig;
import com.mimicer.hide.ClosetIndex;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Revisa un armario: va hasta delante, escucha, abre las puertas con su propia animación y, si hay alguien dentro,
 * lo agarra y lo saca poco a poco hasta dejarlo delante de él. Solo entonces empieza el ataque. Si está vacío,
 * mira dentro, cierra y sigue. Cada armario y cada víctima solo los puede reservar un monstruo a la vez.
 */
public class ClosetGoal extends Goal {
    private enum Phase { WALK, SCENE, DONE }

    private final SceneHost host;
    private final PathfinderMob mob;
    private final SceneTiming.Kind kind;

    private BlockPos pending;
    private BlockPos closet;
    private LivingEntity victim;
    private Vec3 stand;
    private Vec3 inside;
    private Vec3 dragEnd;
    private Phase phase = Phase.DONE;
    private int walkTicks;
    private int sceneStart;
    private boolean opened;
    private boolean decided;
    private boolean grabbed;
    private boolean finished;
    private boolean closedAgain;

    public ClosetGoal(SceneHost host, SceneTiming.Kind kind) {
        this.host = host;
        this.mob = host.mob();
        this.kind = kind;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public boolean isActive() {
        return pending != null || phase != Phase.DONE;
    }

    public boolean request(BlockPos lower) {
        if (isActive() || host.isBusyWithScene() || !host.sceneAllowed() || !ClosetConfig.ENABLED.get()
                || !WardrobeBlock.isWardrobe(mob.level(), lower) || !ClosetIndex.claim(lower, mob.getUUID())) {
            return false;
        }
        pending = lower;
        return true;
    }

    @Override
    public boolean canUse() {
        if (pending == null) {
            return false;
        }
        if (!host.sceneAllowed() || !WardrobeBlock.isWardrobe(mob.level(), pending)) {
            ClosetIndex.release(pending, mob.getUUID());
            pending = null;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && host.sceneAllowed() && closet != null && WardrobeBlock.isWardrobe(mob.level(), closet);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        closet = pending;
        pending = null;
        victim = null;
        opened = decided = grabbed = finished = closedAgain = false;
        walkTicks = 0;
        mob.setTarget(null);
        Direction f = WardrobeBlock.facing(mob.level(), closet);
        inside = WardrobeBlock.insidePos(closet);
        BlockPos c1 = closet.relative(f);
        BlockPos c2 = c1.relative(f);
        if (free(c1) && free(c2)) {
            stand = WardrobeBlock.frontPoint(mob.level(), closet, 1.6);
            dragEnd = WardrobeBlock.frontPoint(mob.level(), closet, 0.95);
        } else if (free(c1)) {
            stand = WardrobeBlock.frontPoint(mob.level(), closet, 1.0);
            dragEnd = WardrobeBlock.frontPoint(mob.level(), closet, 0.62);
        } else {
            phase = Phase.DONE;
            return;
        }
        phase = Phase.WALK;
    }

    private boolean free(BlockPos cell) {
        var level = mob.level();
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                && !level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || phase == Phase.DONE) {
            return;
        }
        if (phase == Phase.WALK) {
            tickWalk(level);
        } else {
            tickScene(level);
        }
    }

    private void tickWalk(ServerLevel level) {
        walkTicks++;
        double d2 = (mob.getX() - stand.x) * (mob.getX() - stand.x) + (mob.getZ() - stand.z) * (mob.getZ() - stand.z);
        if (d2 < 0.45) {
            phase = Phase.SCENE;
            sceneStart = (int) level.getGameTime();
            host.setExtractMode(SceneTiming.MODE_CLOSET_CHECK);
            host.setExtractStart(sceneStart);
            mob.getNavigation().stop();
            return;
        }
        if (walkTicks % 10 == 1) {
            mob.getNavigation().moveTo(stand.x, stand.y, stand.z, 1.1D);
        }
        if (walkTicks > 500 || (walkTicks > 60 && mob.getNavigation().isDone() && d2 > 4.0)) {
            phase = Phase.DONE;         // no llega hasta el armario
        }
    }

    private void faceCloset() {
        float yaw = (float) (Mth.atan2(inside.z - mob.getZ(), inside.x - mob.getX()) * 180.0 / Math.PI) - 90.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);
    }

    private void tickScene(ServerLevel level) {
        int t = (int) (level.getGameTime() - sceneStart);
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
        faceCloset();

        if (t < kind.cOpen) {
            // Se queda frente a las puertas, escuchando; de vez en cuando roza la madera.
            if (t % 14 == 8) {
                level.playSound(null, closet, SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 0.5F, 0.5F);
            }
            return;
        }
        if (!opened) {
            opened = true;
            WardrobeBlock.setOpen(level, closet, true, kind.cOpenTicks);
            NoiseManager.emit(level, Vec3.atCenterOf(closet), 14.0F);
        }
        if (!decided && t >= kind.cOpen + kind.cOpenTicks / 2) {
            decided = true;
            HideManager.Hidden h = HideManager.hiddenAt(level, closet);
            if (h != null && h.victim().isAlive() && HideManager.claimExtraction(h.victim().getUUID(), mob.getUUID())) {
                victim = h.victim();
                host.setExtractMode(SceneTiming.MODE_CLOSET_EXTRACT);
            }
        }
        if (victim != null) {
            tickExtract(level, t);
        } else if (decided) {
            tickEmpty(level, t);
        }
    }

    private void tickEmpty(ServerLevel level, int t) {
        if (!closedAgain && t >= kind.cEmptyEnd - 14) {
            closedAgain = true;
            WardrobeBlock.setOpen(level, closet, false, 12);
        }
        if (t >= kind.cEmptyEnd) {
            phase = Phase.DONE;
        }
    }

    private void tickExtract(ServerLevel level, int t) {
        if (!victim.isAlive() || !HideManager.isHiddenVictim(victim)) {
            if (grabbed && !finished) {
                finish(level);
            } else {
                phase = Phase.DONE;
            }
            return;
        }
        if (t < kind.cGrab) {
            return;
        }
        if (!grabbed) {
            grabbed = true;
            if (kind == SceneTiming.Kind.MIMIC) {
                level.playSound(null, closet, ModSounds.MIMIC_SCREAM.get(), SoundSource.HOSTILE, 1.5F, 1.1F);
            } else if (kind == SceneTiming.Kind.VOID) {
                level.playSound(null, closet, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5F, 0.4F);
            } else {
                level.playSound(null, closet, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.3F);
            }
            victim.setInvisible(false);             // ahora se ve cómo la arrastran
        }
        float p = Mth.clamp((t - kind.cGrab) / (float) (kind.cEnd - kind.cGrab), 0.0F, 1.0F);
        moveVictim(level, p);
        if ((t - kind.cGrab) % 14 == 0) {
            level.playSound(null, victim.blockPosition(), SoundEvents.WOOD_STEP, SoundSource.HOSTILE, 0.9F, 0.5F);
            if (victim instanceof Villager) {
                level.playSound(null, victim.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.9F, 0.9F);
            }
            NoiseManager.emit(level, victim.position(), 12.0F);
        }
        if (t >= kind.cEnd) {
            finish(level);
        }
    }

    private void moveVictim(ServerLevel level, float p) {
        float e = p * p * (3.0F - 2.0F * p);
        Vec3 pos = inside.lerp(new Vec3(dragEnd.x, closet.getY() + 0.02, dragEnd.z), e);
        float yaw = (float) (Mth.atan2(stand.z - pos.z, stand.x - pos.x) * 180.0 / Math.PI) - 90.0F;
        if (victim instanceof ServerPlayer sp) {
            sp.setDeltaMovement(Vec3.ZERO);
            sp.teleportTo(level, pos.x, pos.y, pos.z, yaw, -15.0F);
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
        }
        level.scheduleTick(closet, level.getBlockState(closet).getBlock(), 60);   // el armario se cierra solo después
        phase = Phase.DONE;
        host.onExtracted(v);
    }

    @Override
    public void stop() {
        if (victim != null) {
            if (grabbed && !finished && HideManager.isHiddenVictim(victim)) {
                if (victim instanceof ServerPlayer sp) {
                    HideManager.stopHiding(sp);
                } else if (victim instanceof Villager vil) {
                    HideManager.unhideVillager(vil);
                }
            }
            HideManager.releaseExtraction(victim.getUUID(), mob.getUUID());
        }
        if (closet != null) {
            ClosetIndex.release(closet, mob.getUUID());
            if (mob.level() instanceof ServerLevel level && opened && !finished && WardrobeBlock.isWardrobe(level, closet)) {
                level.scheduleTick(closet, level.getBlockState(closet).getBlock(), 20);
            }
        }
        if (pending != null) {
            ClosetIndex.release(pending, mob.getUUID());
            pending = null;
        }
        victim = null;
        closet = null;
        phase = Phase.DONE;
        host.setExtractStart(-1);
        host.setExtractMode(SceneTiming.MODE_BED);
        mob.getNavigation().stop();
    }
}
