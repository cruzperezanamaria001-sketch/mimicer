package com.mimicer.entity.goal;

import com.mimicer.entity.CorpseEntity;
import com.mimicer.entity.MimicEntity;
import com.mimicer.hide.HideManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Recoge un cuerpo del suelo, lo carga y lo lanza contra jugadores o aldeanos cercanos. */
public class MimicCorpseGoal extends Goal {
    private enum Phase { APPROACH, LIFT, CARRY, WINDUP, DONE }

    private final MimicEntity mimic;
    private CorpseEntity corpse;
    private Phase phase = Phase.DONE;
    private int timer;
    private int carryTicks;
    private int cooldown;
    private Vec3 randomDir;

    public MimicCorpseGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mimic.isMonsterForm()) {
            return false;
        }
        if (mimic.getCarried() != null) {
            return true;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (mimic.getTarget() != null || mimic.getRandom().nextInt(40) != 0) {
            return false;
        }
        corpse = findCorpse();
        return corpse != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mimic.isMonsterForm() && phase != Phase.DONE;
    }

    @Override
    public void start() {
        if (mimic.getCarried() != null) {
            corpse = mimic.getCarried();
            phase = Phase.CARRY;
            carryTicks = 0;
        } else {
            phase = Phase.APPROACH;
            timer = 0;
        }
    }

    @Override
    public void tick() {
        switch (phase) {
            case APPROACH -> tickApproach();
            case LIFT -> {
                mimic.getNavigation().stop();
                if (--timer <= 0) {
                    phase = Phase.CARRY;
                    carryTicks = 0;
                }
            }
            case CARRY -> tickCarry();
            case WINDUP -> tickWindup();
            default -> { }
        }
    }

    private void tickApproach() {
        timer++;
        if (corpse == null || !corpse.isAlive() || !corpse.isFree() || timer > 400) {
            phase = Phase.DONE;
            return;
        }
        mimic.getNavigation().moveTo(corpse, 1.1D);
        mimic.getLookControl().setLookAt(corpse);
        if (mimic.distanceTo(corpse) < 1.8F) {
            corpse.pickUp(mimic);
            mimic.setCarried(corpse);
            mimic.startLift();
            mimic.getNavigation().stop();
            phase = Phase.LIFT;
            timer = 20;
        }
    }

    private void tickCarry() {
        carryTicks++;
        if (corpse == null || !corpse.isAlive() || mimic.getCarried() != corpse) {
            phase = Phase.DONE;
            return;
        }
        LivingEntity victim = findVictim();
        if (victim != null) {
            mimic.getNavigation().moveTo(victim, 1.0D);
            mimic.getLookControl().setLookAt(victim);
            if (mimic.distanceTo(victim) < 10.0F && mimic.getSensing().hasLineOfSight(victim)) {
                beginThrow(victim.getEyePosition());
            }
        } else if (carryTicks > 200) {
            double angle = mimic.getRandom().nextDouble() * Math.PI * 2.0;
            beginThrow(mimic.position().add(Math.cos(angle) * 8.0, 1.0, Math.sin(angle) * 8.0));
        }
    }

    private Vec3 throwTarget;

    private void beginThrow(Vec3 target) {
        throwTarget = target;
        mimic.getNavigation().stop();
        mimic.startThrow();
        phase = Phase.WINDUP;
        timer = 8;
    }

    private void tickWindup() {
        mimic.getNavigation().stop();
        mimic.getLookControl().setLookAt(throwTarget);
        if (--timer > 0) {
            return;
        }
        if (corpse != null && corpse.isAlive() && throwTarget != null) {
            Vec3 delta = throwTarget.subtract(corpse.position());
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            Vec3 dir = horizontal < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(delta.x / horizontal, 0, delta.z / horizontal);
            double speed = Math.min(1.3, 0.35 + horizontal * 0.09);
            corpse.throwBody(new Vec3(dir.x * speed, 0.25 + horizontal * 0.02, dir.z * speed));
            mimic.level().playSound(null, mimic.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.5F);
        }
        mimic.setCarried(null);
        cooldown = 200;
        phase = Phase.DONE;
    }

    @Override
    public void stop() {
        if (phase != Phase.DONE && mimic.getCarried() != null && !mimic.isMonsterForm()) {
            mimic.getCarried().drop();
            mimic.setCarried(null);
        }
        phase = Phase.DONE;
        corpse = null;
        throwTarget = null;
    }

    private LivingEntity findVictim() {
        LivingEntity target = mimic.getTarget();
        if (target != null && target.isAlive()) {
            return target;
        }
        Player player = mimic.level().getNearestPlayer(mimic.getX(), mimic.getY(), mimic.getZ(), 24.0,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && !HideManager.isHidden(p));
        if (player != null) {
            return player;
        }
        List<AbstractVillager> villagers = mimic.level().getEntitiesOfClass(AbstractVillager.class,
                mimic.getBoundingBox().inflate(24.0, 6.0, 24.0));
        return villagers.stream()
                .filter(v -> !(v instanceof Villager vv && HideManager.isVillagerHidden(vv)))
                .min(Comparator.comparingDouble(mimic::distanceToSqr)).orElse(null);
    }

    private CorpseEntity findCorpse() {
        List<CorpseEntity> corpses = mimic.level().getEntitiesOfClass(CorpseEntity.class,
                mimic.getBoundingBox().inflate(16.0, 6.0, 16.0), CorpseEntity::isFree);
        return corpses.stream().min(Comparator.comparingDouble(mimic::distanceToSqr)).orElse(null);
    }
}
