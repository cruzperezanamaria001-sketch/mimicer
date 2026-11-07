package com.mimicer.entity;

import com.mimicer.entity.goal.MimicBreakBlocksGoal;
import com.mimicer.entity.goal.MimicCorpseGoal;
import com.mimicer.entity.goal.MimicInvestigateGoal;
import com.mimicer.entity.goal.MimicStalkWindowGoal;
import com.mimicer.hide.HideManager;
import com.mimicer.limb.LimbSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * El Mímico: de día es indistinguible de un aldeano; de noche se transforma en un monstruo.
 */
public class MimicEntity extends Monster {
    public static final int TRANSFORM_TICKS = 40;

    private static final UUID NIGHT_SPEED_ID = UUID.fromString("5f6a0f4e-3a52-4c55-8d2c-6d1a1b0a7e11");
    private static final EntityDimensions MONSTER_DIMENSIONS = EntityDimensions.scalable(0.7F, 2.3F);

    private static final EntityDataAccessor<Integer> DATA_TRANSFORM =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LURKING =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CARRYING =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LIFT =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_THROW =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);

    private float transformProgress;
    private float transformProgressO;
    private boolean speedApplied;
    private boolean lastMonster;
    private int revealTicks;
    private int ripCooldown;
    @Nullable
    private BlockPos origin;
    @Nullable
    private CorpseEntity carried;

    public MimicEntity(EntityType<? extends MimicEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TRANSFORM, 0);
        this.entityData.define(DATA_LURKING, false);
        this.entityData.define(DATA_CARRYING, false);
        this.entityData.define(DATA_LIFT, 0);
        this.entityData.define(DATA_THROW, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MimicCorpseGoal(this));
        this.goalSelector.addGoal(2, new MimicBreakBlocksGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(4, new MimicInvestigateGoal(this));
        this.goalSelector.addGoal(5, new MimicStalkWindowGoal(this));
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Villager.class, 6.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> isMonsterForm() && !HideManager.isHidden((Player) e)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false,
                e -> isMonsterForm()));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Animal.class, 20, true, false,
                e -> isMonsterForm()));
    }

    // ------------------------------------------------------------------ estado

    public float getTransformProgress(float partialTick) {
        return Mth.lerp(partialTick, transformProgressO, transformProgress);
    }

    public int getTransformTicks() {
        return this.entityData.get(DATA_TRANSFORM);
    }

    /** Forma monstruosa completa. */
    public boolean isMonsterForm() {
        return getTransformTicks() >= TRANSFORM_TICKS;
    }

    public boolean isTransforming() {
        int t = getTransformTicks();
        return t > 0 && t < TRANSFORM_TICKS;
    }

    public boolean isLurking() {
        return this.entityData.get(DATA_LURKING);
    }

    public void setLurking(boolean lurking) {
        this.entityData.set(DATA_LURKING, lurking);
    }

    public boolean isCarrying() {
        return this.entityData.get(DATA_CARRYING);
    }

    public int getLiftTicks() {
        return this.entityData.get(DATA_LIFT);
    }

    public int getThrowTicks() {
        return this.entityData.get(DATA_THROW);
    }

    public void startLift() {
        this.entityData.set(DATA_LIFT, 20);
    }

    public void startThrow() {
        this.entityData.set(DATA_THROW, 10);
    }

    @Nullable
    public CorpseEntity getCarried() {
        return carried;
    }

    public void setCarried(@Nullable CorpseEntity corpse) {
        this.carried = corpse;
        this.entityData.set(DATA_CARRYING, corpse != null);
    }

    /** Un comportamiento que los aldeanos consideran sospechoso. */
    public boolean isStrange() {
        return isMonsterForm() || isTransforming() || isLurking() || isCarrying();
    }

    // ------------------------------------------------------------------ ciclo

    @Override
    public void tick() {
        transformProgressO = transformProgress;
        transformProgress = getTransformTicks() / (float) TRANSFORM_TICKS;
        super.tick();
        if (!level().isClientSide) {
            serverTick();
        }
    }

    private void serverTick() {
        if (origin == null) {
            origin = blockPosition();
        }
        if (revealTicks > 0) {
            revealTicks--;
        }
        if (ripCooldown > 0) {
            ripCooldown--;
        }
        if (this.entityData.get(DATA_LIFT) > 0) {
            this.entityData.set(DATA_LIFT, this.entityData.get(DATA_LIFT) - 1);
        }
        if (this.entityData.get(DATA_THROW) > 0) {
            this.entityData.set(DATA_THROW, this.entityData.get(DATA_THROW) - 1);
        }

        boolean wantMonster = !level().isDay() || revealTicks > 0;
        int t = getTransformTicks();
        if (wantMonster) {
            if (t < TRANSFORM_TICKS && (t > 0 || revealTicks > 0 || random.nextInt(60) == 0)) {
                setTransformTicks(t + 1);
            }
        } else if (t > 0 && (t < TRANSFORM_TICKS || random.nextInt(60) == 0)) {
            setTransformTicks(t - 1);
        }

        boolean monster = isMonsterForm();
        if (monster != lastMonster || (monster && !speedApplied)) {
            onFormChanged(monster);
        }

        if (isTransforming()) {
            getNavigation().stop();
            setTarget(null);
            setLurking(false);
            if (getTransformTicks() % 8 == 0) {
                playSound(SoundEvents.SKELETON_HURT, 0.8F, 0.4F + random.nextFloat() * 0.3F);
            }
        }

        if (monster) {
            HideManager.mimicSense(this);
            if (tickCount % 80 == 0 && random.nextInt(3) == 0) {
                playSound(SoundEvents.ZOMBIE_VILLAGER_AMBIENT, 1.0F, 0.4F);
            }
        }
    }

    private void setTransformTicks(int ticks) {
        this.entityData.set(DATA_TRANSFORM, Mth.clamp(ticks, 0, TRANSFORM_TICKS));
    }

    private void onFormChanged(boolean monster) {
        lastMonster = monster;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(NIGHT_SPEED_ID);
            if (monster) {
                speed.addTransientModifier(new AttributeModifier(NIGHT_SPEED_ID, "mimic night speed",
                        0.45D, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        speedApplied = monster;
        if (monster) {
            clearRestriction();
            playSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.2F, 0.5F);
        } else {
            setTarget(null);
            setLurking(false);
            if (carried != null) {
                carried.drop();
                setCarried(null);
            }
            if (origin != null) {
                restrictTo(origin, 48);
            }
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TRANSFORM.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float p = getTransformTicks() / (float) TRANSFORM_TICKS;
        if (p <= 0.0F) {
            return super.getDimensions(pose);
        }
        return p >= 1.0F ? MONSTER_DIMENSIONS : EntityDimensions.scalable(
                Mth.lerp(p, 0.6F, 0.7F), Mth.lerp(p, 1.95F, 2.3F));
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.9F;
    }

    // ------------------------------------------------------------------ combate

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity victim && level() instanceof ServerLevel sl) {
            LimbSystem.spawnBlood(sl, victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0), 10);
            boolean protectedPlayer = victim instanceof Player p && (p.isCreative() || p.isSpectator());
            if (isMonsterForm() && !protectedPlayer && victim.isAlive() && LimbSystem.canLoseLimbs(victim)
                    && ripCooldown <= 0 && random.nextFloat() < 0.35F) {
                int limb = LimbSystem.pickIntactLimb(victim, random);
                if (limb != 0) {
                    LimbSystem.rip(sl, victim, limb);
                    ripCooldown = 60;
                }
            }
        }
        return ok;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && !(attacker instanceof Player)) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && attacker != null && !isMonsterForm()) {
            revealTicks = 600;
        }
        return result;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != this.getType() && super.canAttackType(type);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (carried != null) {
            carried.drop();
            carried = null;
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    protected SoundEvent getAmbientSound() {
        return isMonsterForm() ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isMonsterForm() ? SoundEvents.ZOMBIE_VILLAGER_HURT : SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isMonsterForm() ? SoundEvents.ZOMBIE_VILLAGER_DEATH : SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return isMonsterForm() ? 0.5F : super.getVoicePitch();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (isMonsterForm()) {
            playSound(SoundEvents.ZOMBIE_VILLAGER_STEP, 0.15F, 0.6F);
        } else {
            super.playStepSound(pos, state);
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Transform", getTransformTicks());
        if (origin != null) {
            tag.put("Origin", NbtUtils.writeBlockPos(origin));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTransformTicks(tag.getInt("Transform"));
        if (tag.contains("Origin")) {
            origin = NbtUtils.readBlockPos(tag.getCompound("Origin"));
            if (!isMonsterForm()) {
                restrictTo(origin, 48);
            }
        }
        transformProgress = getTransformTicks() / (float) TRANSFORM_TICKS;
        transformProgressO = transformProgress;
    }
}
