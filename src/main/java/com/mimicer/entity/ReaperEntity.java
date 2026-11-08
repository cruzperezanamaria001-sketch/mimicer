package com.mimicer.entity;

import com.mimicer.blood.BloodSystem;
import com.mimicer.config.ReaperConfig;
import com.mimicer.entity.scene.ClosetBrain;
import com.mimicer.entity.scene.ClosetGoal;
import com.mimicer.entity.scene.FeedGoal;
import com.mimicer.entity.scene.SceneHost;
import com.mimicer.entity.scene.SceneTiming;
import com.mimicer.hide.HideManager;
import com.mimicer.limb.LimbSystem;
import com.mimicer.util.Barricades;
import com.mimicer.util.Ducking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * The Reaper: cosechador espectral con guadaña. Huele la sangre (persigue más rápido cuanto más herida está la
 * presa), se queda inmóvil si lo miras mientras te acecha sano, señala a su presa antes de cazarla, ejecuta con un
 * golpe de guadaña a los que están a punto de caer, se alimenta de los cuerpos y abre armarios con la punta de la
 * hoja. Animado con GeckoLib 4.8.4.
 */
public class ReaperEntity extends Monster implements GeoEntity, SceneHost {
    public static final int ACT_NONE = 0, ACT_DETECT = 1, ACT_ATTACK = 2, ACT_REAP = 3;
    public static final int MODE_WANDER = 0, MODE_STALK = 1, MODE_HUNT = 2, MODE_FROZEN = 3;

    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DUCK =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FEED_START =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXTRACT_START =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXTRACT_MODE =
            SynchedEntityData.defineId(ReaperEntity.class, EntityDataSerializers.INT);

    private static final EntityDimensions STAND_DIMS = EntityDimensions.scalable(0.7F, 2.7F);
    private static final EntityDimensions DUCK_DIMS = EntityDimensions.scalable(0.7F, 1.8F);

    private static final String P = "animation.reaper.";
    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation A_STARE = RawAnimation.begin().thenLoop(P + "stare");
    private static final RawAnimation A_WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation A_STALK = RawAnimation.begin().thenLoop(P + "stalk");
    private static final RawAnimation A_RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation A_DETECT = RawAnimation.begin().thenPlayAndHold(P + "detect");
    private static final RawAnimation A_ATTACK = RawAnimation.begin().thenPlay(P + "attack");
    private static final RawAnimation A_REAP = RawAnimation.begin().thenPlayAndHold(P + "reap");
    private static final RawAnimation A_CROUCH = RawAnimation.begin().thenLoop(P + "crouch");
    private static final RawAnimation A_CROUCH_WALK = RawAnimation.begin().thenLoop(P + "crouch_walk");
    private static final RawAnimation A_FEED = RawAnimation.begin().thenPlayAndHold(P + "feed_kneel").thenLoop(P + "feed");
    private static final RawAnimation A_C_LISTEN = RawAnimation.begin().thenLoop(P + "c_listen");
    private static final RawAnimation A_C_OPEN = RawAnimation.begin().thenPlayAndHold(P + "c_open");
    private static final RawAnimation A_C_PEEK = RawAnimation.begin().thenLoop(P + "c_peek");
    private static final RawAnimation A_C_REACH = RawAnimation.begin().thenLoop(P + "c_reach");
    private static final RawAnimation A_C_GRAB = RawAnimation.begin().thenLoop(P + "c_grab");
    private static final RawAnimation A_DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");
    private static final RawAnimation A_WISPS = RawAnimation.begin().thenLoop(P + "wisps");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private FeedGoal feedGoal;
    private ClosetGoal closetGoal;
    private ClosetBrain closetBrain;

    private int actionTicks;
    private int actionTotal;
    private int attackCooldown;
    private int detectCooldown;
    private int stuckTicks;
    private int duckTicks;
    private int duckCooldown;
    private int heartbeatTimer;
    private int huntTicks;
    private LivingEntity pendingTarget;
    private BlockPos breakPos;
    private boolean hitDone;

    public ReaperEntity(EntityType<? extends ReaperEntity> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        setMaxUpStep(1.0F);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static boolean checkReaperSpawn(EntityType<ReaperEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                           BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND) {
            return true;
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random)
                && random.nextInt(ReaperConfig.SPAWN_RARITY.get()) == 0
                && level.getEntitiesOfClass(ReaperEntity.class, new AABB(pos).inflate(ReaperConfig.SPAWN_EXCLUSION_RADIUS.get()))
                .isEmpty();
    }

    // ------------------------------------------------------------------ datos

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, ACT_NONE);
        this.entityData.define(DATA_MODE, MODE_WANDER);
        this.entityData.define(DATA_DUCK, false);
        this.entityData.define(DATA_FEED_START, -1);
        this.entityData.define(DATA_EXTRACT_START, -1);
        this.entityData.define(DATA_EXTRACT_MODE, 0);
    }

    public int getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    public int getMode() {
        return this.entityData.get(DATA_MODE);
    }

    private void setMode(int mode) {
        if (getMode() != mode) {
            this.entityData.set(DATA_MODE, mode);
        }
    }

    public boolean isDucking() {
        return this.entityData.get(DATA_DUCK);
    }

    private void setDucking(boolean duck) {
        if (isDucking() != duck) {
            this.entityData.set(DATA_DUCK, duck);
            duckTicks = 0;
            refreshDimensions();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DUCK.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isDucking() ? DUCK_DIMS : STAND_DIMS;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return isDucking() ? 1.5F : 2.4F;
    }

    // ------------------------------------------------------------------ objetivos

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.feedGoal = new FeedGoal(this);
        this.closetGoal = new ClosetGoal(this, SceneTiming.Kind.REAPER);
        this.closetBrain = new ClosetBrain(this);
        this.goalSelector.addGoal(0, this.closetGoal);
        this.goalSelector.addGoal(0, this.feedGoal);
        this.goalSelector.addGoal(1, new HuntGoal(this));
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Persigue al objetivo; se queda quieto cuando está "congelado" porque lo miran. */
    private static final class HuntGoal extends Goal {
        private final ReaperEntity reaper;

        HuntGoal(ReaperEntity reaper) {
            this.reaper = reaper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = reaper.getTarget();
            return t != null && t.isAlive() && reaper.actionTicks == 0 && !reaper.isBusyWithScene();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = reaper.getTarget();
            if (t == null) {
                return;
            }
            reaper.getLookControl().setLookAt(t, 40.0F, 40.0F);
            if (reaper.getMode() == MODE_FROZEN) {
                reaper.getNavigation().stop();
            } else if (reaper.tickCount % 5 == 0 || reaper.getNavigation().isDone()) {
                reaper.getNavigation().moveTo(t, 1.0D);
            }
        }
    }

    /** Presas: jugadores y aldeanos visibles, y cualquier criatura herida que huela a sangre. */
    private boolean isPrey(LivingEntity e) {
        if (e == this || !e.isAlive()) {
            return false;
        }
        if (e instanceof Player p) {
            return !p.isCreative() && !p.isSpectator() && !HideManager.isHidden(p);
        }
        if (e instanceof AbstractVillager) {
            return !(e instanceof Villager v && HideManager.isVillagerHidden(v));
        }
        return e instanceof Animal && isBloody(e);
    }

    private static boolean isBloody(LivingEntity e) {
        return e.getHealth() < e.getMaxHealth() * 0.6F || BloodSystem.getWound(e) > 0.5F;
    }

    private void scanForPrey() {
        if (tickCount % 10 != 0 || detectCooldown > 0) {
            return;
        }
        double sight = ReaperConfig.SIGHT_RANGE.get();
        double smell = ReaperConfig.BLOOD_SENSE_RANGE.get();
        double range = Math.max(sight, smell);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, 12.0, range),
                this::isPrey)) {
            double d = distanceTo(e);
            boolean seen = d < sight && hasLineOfSight(e);
            boolean smelled = d < smell && isBloody(e);
            if (!seen && !smelled) {
                continue;
            }
            double score = smelled ? d * 0.5 : d;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            pendingTarget = best;
            startAction(ACT_DETECT, 36);
            level().playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.6F, 0.5F);
            level().playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.9F, 0.45F);
        }
    }

    // ------------------------------------------------------------------ ciclo

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    private void clientTick() {
        if (random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SOUL, getRandomX(0.6), getY() + random.nextDouble() * 2.2, getRandomZ(0.6),
                    0.0, 0.03, 0.0);
        }
    }

    private void serverTick() {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (detectCooldown > 0) {
            detectCooldown--;
        }
        if (actionTicks > 0) {
            tickAction();
            return;
        }
        tickClosetSearch();
        if (isBusyWithScene()) {
            setMode(MODE_WANDER);
            return;
        }
        LivingEntity t = getTarget();
        if (t != null && (!isPrey(t) || distanceToSqr(t) > 60.0 * 60.0)) {
            setTarget(null);
            t = null;
        }
        if (t == null) {
            huntTicks = 0;
            setMode(MODE_WANDER);
            applySpeed(0.22D);
            if (isDucking() && tickCount % 5 == 0 && duckTicks++ > 8 && Ducking.fits(this, STAND_DIMS)) {
                setDucking(false);
            }
            scanForPrey();
            return;
        }
        huntTicks++;
        tickHunt(t);
    }

    private void applySpeed(double speed) {
        var attr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && Math.abs(attr.getBaseValue() - speed) > 1.0E-4) {
            attr.setBaseValue(speed);
        }
    }

    private void tickHunt(LivingEntity t) {
        float missing = 1.0F - Mth.clamp(t.getHealth() / t.getMaxHealth(), 0.0F, 1.0F);
        boolean wounded = missing > 0.35F || BloodSystem.getWound(t) > 0.5F;
        double dist = distanceTo(t);
        boolean stalking = t instanceof Player && !wounded && dist > 5.5;
        if (stalking) {
            boolean watched = ReaperConfig.FREEZE_WHEN_WATCHED.get() && t instanceof Player p && isWatchedBy(p);
            setMode(watched ? MODE_FROZEN : MODE_STALK);
            applySpeed(watched ? 0.0D : 0.27D);
        } else {
            setMode(MODE_HUNT);
            // Huele la sangre: cuanto más herida, más rápido (y se acelera algo con el tiempo de caza).
            applySpeed(0.34D + 0.12D * missing + Math.min(0.04D, huntTicks / 4000.0D));
        }
        updateDuck(t);
        tickHeartbeat(t, dist);
        tickObstacles(t, dist);

        if (attackCooldown <= 0 && dist < 3.0 && hasLineOfSight(t)) {
            boolean reap = t.getHealth() <= Math.max(6.0F, t.getMaxHealth() * 0.35F);
            startAction(reap ? ACT_REAP : ACT_ATTACK, reap ? 38 : 18);
            level().playSound(null, blockPosition(), reap ? SoundEvents.WITHER_SKELETON_AMBIENT : SoundEvents.WITHER_AMBIENT,
                    SoundSource.HOSTILE, 1.4F, reap ? 0.3F : 0.5F);
        }
    }

    private boolean isWatchedBy(Player p) {
        Vec3 view = p.getViewVector(1.0F);
        Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
        return view.dot(to) > 0.93 && p.hasLineOfSight(this);
    }

    private void tickHeartbeat(LivingEntity t, double dist) {
        if (!ReaperConfig.HEARTBEAT.get() || !(t instanceof Player p) || dist > 26.0 || tickCount % 2 != 0) {
            return;
        }
        if (--heartbeatTimer <= 0) {
            heartbeatTimer = (int) Mth.clamp(dist * 1.6, 9.0, 40.0);
            level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
                    (float) Mth.clamp(1.6 - dist / 20.0, 0.4, 1.4), 0.9F);
        }
    }

    /** Se agacha para huecos de 2 bloques y rompe las barricadas con la guadaña si no hay otro camino. */
    private void tickObstacles(LivingEntity t, double dist) {
        if (getNavigation().isDone() && dist > 3.0 && getMode() != MODE_FROZEN) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (ReaperConfig.BREAK_BARRICADES.get() && stuckTicks > 10 && attackCooldown <= 0) {
            BlockPos b = Barricades.find(this, new Vec3(t.getX() - getX(), 0.0, t.getZ() - getZ()), 2);
            if (b != null) {
                breakPos = b;
                stuckTicks = 0;
                startAction(ACT_ATTACK, 18);
                return;
            }
        }
        if (stuckTicks > 25 && !isDucking() && duckCooldown <= 0) {
            setDucking(true);
            duckCooldown = 100;
            stuckTicks = 0;
            getNavigation().stop();
        }
    }

    private void updateDuck(LivingEntity t) {
        if (tickCount % 5 != 0) {
            return;
        }
        if (duckCooldown > 0) {
            duckCooldown -= 5;
        }
        if (isDucking()) {
            duckTicks += 5;
            if (duckTicks > 40 && !scenePosture && Ducking.fits(this, STAND_DIMS)) {
                setDucking(false);
            }
            return;
        }
        if (duckCooldown <= 0 && Ducking.gapAhead(this, STAND_DIMS, DUCK_DIMS,
                new Vec3(t.getX() - getX(), 0.0, t.getZ() - getZ()))) {
            setDucking(true);
            duckCooldown = 100;
        }
    }

    // ------------------------------------------------------------------ acciones (detectar, tajo, ejecución)

    private void startAction(int action, int ticks) {
        this.entityData.set(DATA_ACTION, action);
        actionTicks = ticks;
        actionTotal = ticks;
        hitDone = false;
        getNavigation().stop();
    }

    private void tickAction() {
        int elapsed = actionTotal - actionTicks;
        actionTicks--;
        getNavigation().stop();
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        int action = getAction();
        LivingEntity focus = action == ACT_DETECT ? pendingTarget : getTarget();
        if (focus != null) {
            getLookControl().setLookAt(focus, 60.0F, 60.0F);
            double dx = focus.getX() - getX(), dz = focus.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            setYHeadRot(yaw);
        }
        if (action == ACT_ATTACK && !hitDone && elapsed >= 11) {
            hitDone = true;
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.5F);
            if (breakPos != null) {
                if (level().getBlockState(breakPos).getDestroySpeed(level(), breakPos) >= 0.0F) {
                    level().destroyBlock(breakPos, true, this);
                }
                breakPos = null;
            } else {
                scytheHit(false);
            }
        } else if (action == ACT_REAP && !hitDone && elapsed >= 27) {
            hitDone = true;
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.6F, 0.4F);
            scytheHit(true);
        } else if (action == ACT_DETECT && elapsed == 20) {
            playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.6F, 0.35F);
        }
        if (actionTicks <= 0) {
            this.entityData.set(DATA_ACTION, ACT_NONE);
            if (action == ACT_DETECT) {
                if (pendingTarget != null && pendingTarget.isAlive() && isPrey(pendingTarget)) {
                    setTarget(pendingTarget);
                }
                pendingTarget = null;
                detectCooldown = 40;
            } else {
                attackCooldown = action == ACT_REAP ? 40 : 22;
            }
        }
    }

    /** Tajo de guadaña en arco frontal; la ejecución mata de un golpe a su objetivo (con probabilidad de decapitar). */
    private void scytheHit(boolean reap) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0.0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        double dmg = ReaperConfig.ATTACK_DAMAGE.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.2, 1.2, 3.2),
                x -> isPrey(x) || (x instanceof Player p && !p.isCreative() && !p.isSpectator() && !HideManager.isHidden(p)))) {
            if (reap && e != getTarget()) {
                continue;
            }
            Vec3 to = e.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            if (flat.length() > 3.7 || (flat.length() > 0.8 && look.dot(flat.normalize()) < 0.35)) {
                continue;
            }
            BloodSystem.heavyHit(level, e, flat.lengthSqr() < 1.0E-4 ? look : flat, reap ? 4.0 : 2.5);
            if (reap) {
                e.hurt(damageSources().mobAttack(this), Float.MAX_VALUE);
                continue;
            }
            boolean hurt = e.hurt(damageSources().mobAttack(this), (float) dmg);
            if (hurt && e.isAlive()) {
                e.knockback(0.9, -look.x, -look.z);
                if (e.getHealth() < e.getMaxHealth() * 0.5F && random.nextFloat() < 0.3F && LimbSystem.canLoseLimbs(e)) {
                    int limb = LimbSystem.pickIntactLimb(e, random);
                    if (limb != 0) {
                        LimbSystem.rip(level, e, limb);
                    }
                }
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return false;       // todo el daño viene de la guadaña (tajo y ejecución)
    }

    // ------------------------------------------------------------------ SceneHost (cuerpos y armarios)

    private boolean scenePosture;

    @Override
    public PathfinderMob mob() {
        return this;
    }

    @Override
    public int getFeedStart() {
        return this.entityData.get(DATA_FEED_START);
    }

    @Override
    public void setFeedStart(int t) {
        this.entityData.set(DATA_FEED_START, t);
    }

    @Override
    public int getExtractStart() {
        return this.entityData.get(DATA_EXTRACT_START);
    }

    @Override
    public void setExtractStart(int t) {
        this.entityData.set(DATA_EXTRACT_START, t);
    }

    @Override
    public int getExtractMode() {
        return this.entityData.get(DATA_EXTRACT_MODE);
    }

    @Override
    public void setExtractMode(int mode) {
        this.entityData.set(DATA_EXTRACT_MODE, mode);
    }

    @Override
    public void scenePosture(boolean crouched) {
        this.scenePosture = crouched;
        if (crouched) {
            setDucking(true);
        } else if (isDucking() && Ducking.fits(this, STAND_DIMS)) {
            setDucking(false);
        }
    }

    @Override
    public boolean sceneAllowed() {
        return isAlive() && actionTicks == 0;
    }

    @Override
    public void beginFeeding(CorpseEntity corpse) {
        if (sceneAllowed() && feedGoal != null && !feedGoal.isActive()) {
            feedGoal.assign(corpse);
        }
    }

    /** The Reaper no saca a nadie de debajo de las camas: solo de armarios. */
    @Override
    public boolean requestExtraction(LivingEntity victim, BlockPos bed) {
        return false;
    }

    @Override
    public boolean requestClosetCheck(BlockPos closetLower) {
        return closetGoal != null && closetGoal.request(closetLower);
    }

    @Override
    public void tickClosetSearch() {
        if (closetBrain != null) {
            closetBrain.tick();
        }
    }

    @Override
    public void onExtracted(LivingEntity victim) {
        if (victim.isAlive()) {
            setTarget(victim);
            setMode(MODE_HUNT);
        }
    }

    @Override
    public void onExtractFailed(LivingEntity victim) {}

    @Override
    public boolean isBusyWithScene() {
        return (feedGoal != null && feedGoal.isActive()) || (closetGoal != null && closetGoal.isActive());
    }

    // ------------------------------------------------------------------ sonidos y vida

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.0F, 0.4F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.CHAIN_STEP, getMode() == MODE_HUNT ? 0.7F : 0.35F, 0.6F);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return !hasCustomName() && distanceSqr > 128.0 * 128.0;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void die(DamageSource source) {
        if (feedGoal != null || closetGoal != null) {
            // Las metas liberan sus reservas al detenerse (stop()), pero por si acaso soltamos el objetivo.
            setTarget(null);
        }
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 40, 0.5, 1.0, 0.5, 0.05);
        }
    }

    // ------------------------------------------------------------------ animación (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "wisps", 0, s -> s.setAndContinue(A_WISPS)));
    }

    private PlayState mainPredicate(AnimationState<ReaperEntity> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(A_DEATH);
        }
        switch (getAction()) {
            case ACT_DETECT:
                return state.setAndContinue(A_DETECT);
            case ACT_ATTACK:
                return state.setAndContinue(A_ATTACK);
            case ACT_REAP:
                return state.setAndContinue(A_REAP);
            default:
                break;
        }
        float partial = state.getPartialTick();
        if (isFeeding()) {
            return state.setAndContinue(A_FEED);
        }
        if (isExtracting() && getExtractMode() > 0) {
            float t = extractTime(partial);
            SceneTiming.Kind k = SceneTiming.Kind.REAPER;
            if (t < k.cOpen) {
                return state.setAndContinue(A_C_LISTEN);
            }
            if (t < k.cOpen + k.cOpenTicks) {
                return state.setAndContinue(A_C_OPEN);
            }
            if (getExtractMode() == SceneTiming.MODE_CLOSET_EXTRACT) {
                return state.setAndContinue(t < k.cGrab ? A_C_REACH : A_C_GRAB);
            }
            return state.setAndContinue(A_C_PEEK);
        }
        boolean moving = state.isMoving();
        if (isDucking()) {
            return state.setAndContinue(moving ? A_CROUCH_WALK : A_CROUCH);
        }
        if (getMode() == MODE_FROZEN) {
            return state.setAndContinue(A_STARE);
        }
        if (moving) {
            return state.setAndContinue(getMode() == MODE_HUNT ? A_RUN : getMode() == MODE_STALK ? A_STALK : A_WALK);
        }
        return state.setAndContinue(A_IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
