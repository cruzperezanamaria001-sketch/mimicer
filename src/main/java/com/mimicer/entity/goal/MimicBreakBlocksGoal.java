package com.mimicer.entity.goal;

import com.mimicer.block.BoardBlock;
import com.mimicer.entity.MimicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.EnumSet;

/**
 * Rompe tablas, puertas de madera, cristales y cristaleras que le cierran el paso hacia su objetivo.
 */
public class MimicBreakBlocksGoal extends Goal {
    private static final double[] DISTANCES = {0.9, 1.5, 2.1, 2.7};
    private static final double[] HEIGHTS = {0.4, 1.3, 2.0};

    private final MimicEntity mimic;
    private BlockPos breakPos;
    private int progress;
    private int needed;
    private int cooldown;
    private Vec3 lastPos;
    private int lastCheckTick;
    private boolean stuck;

    public MimicBreakBlocksGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mimic.isMonsterForm() || --cooldown > 0) {
            return false;
        }
        LivingEntity target = mimic.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        cooldown = 8;
        updateStuck();
        double dist = mimic.distanceTo(target);
        // "Atascado": lleva un rato sin avanzar y aún no está junto a su presa (p. ej. abriendo y cerrando una puerta).
        boolean blocked = mimic.getNavigation().isDone() || mimic.horizontalCollision
                || !mimic.getSensing().hasLineOfSight(target) || (stuck && dist > 2.5);
        if (!blocked || dist > 30.0 || dist < 1.0) {
            return false;
        }
        BlockPos found = findObstacle(target);
        if (found == null) {
            return false;
        }
        breakPos = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return breakPos != null && mimic.isMonsterForm() && mimic.getTarget() != null
                && isBreakable(mimic.level().getBlockState(breakPos))
                && progress < needed
                && mimic.distanceToSqr(Vec3.atCenterOf(breakPos)) < 16.0;
    }

    @Override
    public void start() {
        progress = 0;
        BlockState state = mimic.level().getBlockState(breakPos);
        float hardness = Math.max(0.2F, state.getDestroySpeed(mimic.level(), breakPos));
        needed = Mth.clamp((int) (hardness * 14.0F), 8, 70);
        mimic.getNavigation().stop();
    }

    @Override
    public void tick() {
        Level level = mimic.level();
        mimic.getLookControl().setLookAt(Vec3.atCenterOf(breakPos));
        progress++;
        if (progress % 6 == 0) {
            mimic.swing(InteractionHand.MAIN_HAND);
            BlockState state = level.getBlockState(breakPos);
            level.playSound(null, breakPos, state.getSoundType().getHitSound(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        level.destroyBlockProgress(mimic.getId(), breakPos, Math.min(9, progress * 10 / needed));
        if (progress >= needed) {
            level.destroyBlock(breakPos, true, mimic);
            level.destroyBlockProgress(mimic.getId(), breakPos, -1);
        }
    }

    @Override
    public void stop() {
        if (breakPos != null) {
            mimic.level().destroyBlockProgress(mimic.getId(), breakPos, -1);
        }
        breakPos = null;
        progress = 0;
        cooldown = 5;
    }

    private void updateStuck() {
        int now = mimic.tickCount;
        if (lastPos == null || now - lastCheckTick >= 30) {
            Vec3 cur = mimic.position();
            stuck = lastPos != null && cur.distanceToSqr(lastPos) < 0.36;
            lastPos = cur;
            lastCheckTick = now;
        }
    }

    /** Tabla delante del Mímico (hasta 2,5 bloques), la más cercana. Tienen prioridad sobre puertas y cristales. */
    private BlockPos findNearbyBoard(Vec3 dir) {
        BlockPos base = mimic.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 2, 2))) {
            if (!(mimic.level().getBlockState(pos).getBlock() instanceof BoardBlock)) {
                continue;
            }
            Vec3 c = Vec3.atCenterOf(pos);
            double dx = c.x - mimic.getX();
            double dz = c.z - mimic.getZ();
            double dist = dx * dx + dz * dz;
            if (dist > 6.25 || dx * dir.x + dz * dir.z < 0.0) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private BlockPos findObstacle(LivingEntity target) {
        Vec3 dir = new Vec3(target.getX() - mimic.getX(), 0.0, target.getZ() - mimic.getZ());
        if (dir.lengthSqr() < 1.0E-4) {
            return null;
        }
        dir = dir.normalize();
        BlockPos board = findNearbyBoard(dir);
        if (board != null) {
            return board;
        }
        for (double dist : DISTANCES) {
            for (double h : HEIGHTS) {
                BlockPos pos = BlockPos.containing(mimic.getX() + dir.x * dist, mimic.getY() + h, mimic.getZ() + dir.z * dist);
                if (isBreakable(mimic.level().getBlockState(pos))) {
                    return pos;
                }
            }
        }
        return null;
    }

    public static boolean isBreakable(BlockState state) {
        return state.getBlock() instanceof BoardBlock
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(Tags.Blocks.GLASS)
                || state.is(Tags.Blocks.GLASS_PANES);
    }
}
