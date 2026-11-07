package com.mimicer.entity.goal;

import com.mimicer.entity.MimicEntity;
import com.mimicer.hide.NoiseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Va a donde oyó un ruido, mira alrededor y revisa las camas cercanas en busca de escondites. */
public class MimicInvestigateGoal extends Goal {
    private enum Phase { WALK, LOOK_AROUND, CHECK_BEDS, DONE }

    private final MimicEntity mimic;
    private Phase phase = Phase.DONE;
    private Vec3 dest;
    private long lastHandledNoise = -1;
    private long currentNoise;
    private int timer;
    private int walkTicks;
    private final List<BlockPos> beds = new ArrayList<>();
    private int bedIndex;
    private int cooldown;

    public MimicInvestigateGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mimic.isMonsterForm() || mimic.getTarget() != null || mimic.isLurking() || mimic.getCarried() != null) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 10;
        NoiseManager.Noise noise = NoiseManager.findHeard(mimic.level(), mimic.position());
        if (noise == null || noise.time() <= lastHandledNoise) {
            return false;
        }
        dest = noise.pos();
        currentNoise = noise.time();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return mimic.isMonsterForm() && mimic.getTarget() == null && phase != Phase.DONE;
    }

    @Override
    public void start() {
        phase = Phase.WALK;
        walkTicks = 0;
        lastHandledNoise = currentNoise;
    }

    @Override
    public void tick() {
        switch (phase) {
            case WALK -> {
                walkTicks++;
                mimic.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0D);
                if (mimic.position().distanceToSqr(dest) < 9.0 || walkTicks > 300
                        || (mimic.getNavigation().isDone() && walkTicks > 20)) {
                    mimic.getNavigation().stop();
                    phase = Phase.LOOK_AROUND;
                    timer = 50;
                }
            }
            case LOOK_AROUND -> {
                if (timer % 15 == 0) {
                    double angle = mimic.getRandom().nextDouble() * Math.PI * 2.0;
                    mimic.getLookControl().setLookAt(mimic.getX() + Math.cos(angle) * 4.0,
                            mimic.getEyeY() - 0.3, mimic.getZ() + Math.sin(angle) * 4.0);
                }
                if (--timer <= 0) {
                    collectBeds();
                    phase = beds.isEmpty() ? Phase.DONE : Phase.CHECK_BEDS;
                    bedIndex = 0;
                    timer = 0;
                }
            }
            case CHECK_BEDS -> tickCheckBeds();
            default -> { }
        }
    }

    private void tickCheckBeds() {
        if (bedIndex >= beds.size()) {
            phase = Phase.DONE;
            return;
        }
        BlockPos bed = beds.get(bedIndex);
        Vec3 center = Vec3.atCenterOf(bed);
        if (mimic.position().distanceToSqr(center) > 4.0 && timer == 0) {
            mimic.getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
            walkTicks++;
            if (walkTicks > 200) {
                bedIndex++;
                walkTicks = 0;
            }
            return;
        }
        // Se agacha junto a la cama y mira debajo.
        mimic.getNavigation().stop();
        mimic.getLookControl().setLookAt(center.x, center.y - 0.2, center.z);
        if (timer == 0) {
            timer = 45;
        }
        if (--timer <= 0) {
            bedIndex++;
            walkTicks = 0;
        }
    }

    private void collectBeds() {
        beds.clear();
        BlockPos origin = mimic.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8))) {
            BlockState state = mimic.level().getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                beds.add(pos.immutable());
            }
        }
        beds.sort(Comparator.comparingDouble(p -> p.distSqr(mimic.blockPosition())));
        if (beds.size() > 4) {
            beds.subList(4, beds.size()).clear();
        }
    }

    @Override
    public void stop() {
        phase = Phase.DONE;
        cooldown = 40;
        mimic.getNavigation().stop();
    }
}
