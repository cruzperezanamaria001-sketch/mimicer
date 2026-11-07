package com.mimicer.entity.goal;

import com.mimicer.entity.MimicEntity;
import com.mimicer.hide.HideManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Acecho nocturno: rodea la casa donde hay un jugador, se asoma a las ventanas, se queda inmóvil
 * mirándolo y, pasado un rato, lo ataca.
 */
public class MimicStalkWindowGoal extends Goal {
    private enum Phase { APPROACH, PEEK, DONE }

    private final MimicEntity mimic;
    private Phase phase = Phase.DONE;
    private Player victim;
    private BlockPos outside;
    private BlockPos window;
    private final Set<BlockPos> used = new HashSet<>();
    private int windowsPlanned;
    private int windowsVisited;
    private int timer;
    private int walkTicks;
    private int cooldown = 100;

    public MimicStalkWindowGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mimic.isMonsterForm() || mimic.getTarget() != null || mimic.getCarried() != null) {
            return false;
        }
        if (--cooldown > 0) {
            return false;
        }
        cooldown = 40;
        if (mimic.getRandom().nextInt(3) != 0) {
            return false;
        }
        Player player = mimic.level().getNearestPlayer(mimic.getX(), mimic.getY(), mimic.getZ(), 40.0,
                e -> e instanceof Player p && isStalkable(p));
        if (player == null) {
            return false;
        }
        if (!pickWindow(player)) {
            return false;
        }
        victim = player;
        return true;
    }

    private boolean isStalkable(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && !HideManager.isHidden(p)
                && !mimic.level().canSeeSky(p.blockPosition());
    }

    @Override
    public boolean canContinueToUse() {
        return mimic.isMonsterForm() && mimic.getTarget() == null && phase != Phase.DONE;
    }

    @Override
    public void start() {
        used.clear();
        windowsVisited = 0;
        windowsPlanned = 1 + mimic.getRandom().nextInt(3);
        beginApproach();
    }

    private void beginApproach() {
        phase = Phase.APPROACH;
        walkTicks = 0;
        used.add(window);
    }

    @Override
    public void tick() {
        if (victim == null || !isStalkable(victim) || mimic.distanceTo(victim) > 55.0F) {
            phase = Phase.DONE;
            return;
        }
        switch (phase) {
            case APPROACH -> {
                walkTicks++;
                Vec3 spot = Vec3.atBottomCenterOf(outside);
                mimic.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9D);
                if (mimic.position().distanceToSqr(spot) < 2.25 || walkTicks > 300) {
                    mimic.getNavigation().stop();
                    mimic.setLurking(true);
                    phase = Phase.PEEK;
                    timer = 80 + mimic.getRandom().nextInt(120);
                }
            }
            case PEEK -> {
                mimic.getNavigation().stop();
                mimic.getLookControl().setLookAt(victim, 30.0F, 30.0F);
                if (--timer <= 0) {
                    mimic.setLurking(false);
                    windowsVisited++;
                    if (windowsVisited >= windowsPlanned || !pickWindow(victim)) {
                        mimic.setTarget(victim);
                        phase = Phase.DONE;
                    } else {
                        beginApproach();
                    }
                }
            }
            default -> { }
        }
    }

    @Override
    public void stop() {
        mimic.setLurking(false);
        phase = Phase.DONE;
        victim = null;
        cooldown = 400;
    }

    /** Busca la ventana con zona exterior accesible más cercana al Mímico, alrededor del jugador. */
    private boolean pickWindow(Player player) {
        Level level = mimic.level();
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        BlockPos bestOutside = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-7, -1, -7), center.offset(7, 3, 7))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES)) || used.contains(pos)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos out = pos.relative(d);
                BlockPos in = pos.relative(d.getOpposite());
                BlockPos floor = out.below();
                if (level.getBlockState(out).isAir() && level.getBlockState(out.above()).isAir()
                        && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        && level.canSeeSky(out) && level.getBlockState(in).isAir() && !level.canSeeSky(in)) {
                    double dist = mimic.distanceToSqr(Vec3.atCenterOf(out));
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                        bestOutside = out.immutable();
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        window = best;
        outside = bestOutside;
        return true;
    }
}
