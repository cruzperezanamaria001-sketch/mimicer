package com.mimicer.entity;

import com.mimicer.config.GlitchConfig;
import com.mimicer.net.NetworkHandler;
import com.mimicer.registry.ModBlocks;
import com.mimicer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Glitch: anomalía digital que observa desde casas y ventanas de aldeas, asusta si la miras, y cuando algo la
 * persigue camina corrompiendo el terreno (bloques Glitch), rompe obstáculos limitados y dispara un rayo morado.
 */
public class GlitchEntity extends Monster implements GeoEntity {
    // acciones (animaciones exclusivas, sincronizadas con el cliente)
    public static final int ACT_NONE = 0, ACT_SPAWN = 1, ACT_JUMPSCARE = 2, ACT_CHARGE = 3, ACT_SHOOT = 4, ACT_BREAK = 5;
    // modos de IA
    public static final int MODE_OBSERVE = 0, MODE_APPROACH = 1, MODE_CORRUPT = 2, MODE_REST = 3;

    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(GlitchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(GlitchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WATCHING =
            SynchedEntityData.defineId(GlitchEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String P = "animation.glitch.";
    private static final RawAnimation A_DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");
    private static final RawAnimation A_SPAWN = RawAnimation.begin().thenPlay(P + "spawn");
    private static final RawAnimation A_JUMPSCARE = RawAnimation.begin().thenPlay(P + "jumpscare");
    private static final RawAnimation A_CHARGE = RawAnimation.begin().thenLoop(P + "charge");
    private static final RawAnimation A_SHOOT = RawAnimation.begin().thenPlay(P + "shoot");
    private static final RawAnimation A_BREAK = RawAnimation.begin().thenLoop(P + "break");
    private static final RawAnimation A_WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation A_CHASE = RawAnimation.begin().thenLoop(P + "chase");
    private static final RawAnimation A_CORRUPT = RawAnimation.begin().thenLoop(P + "corrupt");
    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation A_WATCH = RawAnimation.begin().thenLoop(P + "watch");
    private static final RawAnimation A_HEAD_TURN = RawAnimation.begin().thenLoop(P + "head_turn");
    private static final RawAnimation A_SHARDS = RawAnimation.begin().thenLoop(P + "shards");

    private static final EntityDataAccessor<Boolean> DATA_DUCK =
            SynchedEntityData.defineId(GlitchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.world.entity.EntityDimensions STAND_DIMS =
            net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 2.0F);
    private static final net.minecraft.world.entity.EntityDimensions DUCK_DIMS =
            net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 1.4F);
    private static final RawAnimation A_CROUCH = RawAnimation.begin().thenLoop(P + "crouch");
    private static final RawAnimation A_CROUCH_WALK = RawAnimation.begin().thenLoop(P + "crouch_walk");

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.7F, 0.05F, 1.0F), 1.3F);
    private static final double BEAM_SPEED = 4.0D;
    private static final double BEAM_RANGE = 36.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---- estado de servidor
    private BlockPos origin;
    private int actionTicks;
    private int modeTicks;
    private int relocateCooldown = 100;
    private int scareCooldown = 200;
    private int beamCooldown;
    private int stuckTicks;
    private int duckTicks;
    private int duckCooldown;
    private int corruptedCount;
    private int corruptBudget;
    private int lastCorruptSound;
    private Vec3 lastPos = Vec3.ZERO;
    private LivingEntity chaser;
    private LivingEntity beamTarget;
    private Vec3 beamStart = Vec3.ZERO;
    private Vec3 beamDir = Vec3.ZERO;
    private double beamDist;
    private final Set<Integer> beamHit = new HashSet<>();
    private BlockPos breakPos;
    private Map<Integer, Double> lastDist = new HashMap<>();
    private final Map<UUID, Long> playerScare = new HashMap<>();

    public GlitchEntity(EntityType<? extends GlitchEntity> type, Level level) {
        super(type, level);
        this.xpReward = 25;
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    // ---- agacharse para pasar por huecos bajos

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
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return isDucking() ? DUCK_DIMS : STAND_DIMS;
    }

    /** Se agacha si delante hay un hueco que solo cabe agachado; se levanta cuando hay sitio. */
    private void updateDuck() {
        if (tickCount % 5 != 0) {
            return;
        }
        if (duckCooldown > 0) {
            duckCooldown -= 5;
        }
        if (isDucking()) {
            duckTicks += 5;
            if (duckTicks > 40 && com.mimicer.util.Ducking.fits(this, STAND_DIMS)) {
                setDucking(false);
            }
            return;
        }
        Vec3 mv = getDeltaMovement();
        if (duckCooldown <= 0 && getNavigation().isInProgress()
                && com.mimicer.util.Ducking.gapAhead(this, STAND_DIMS, DUCK_DIMS, new Vec3(mv.x, 0.0, mv.z))) {
            setDucking(true);
            duckCooldown = 100;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    /** Solo aparece de forma natural cerca de aldeanos (aldeas). */
    public static boolean checkGlitchSpawn(EntityType<GlitchEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                           BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND) {
            return true;
        }
        return Mob.checkMobSpawnRules(type, level, reason, pos, random)
                && !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(48.0)).isEmpty();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, ACT_NONE);
        this.entityData.define(DATA_MODE, MODE_OBSERVE);
        this.entityData.define(DATA_WATCHING, false);
        this.entityData.define(DATA_DUCK, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, false));
    }

    public int getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    public int getMode() {
        return this.entityData.get(DATA_MODE);
    }

    private void setAction(int action, int ticks) {
        this.entityData.set(DATA_ACTION, action);
        this.actionTicks = ticks;
    }

    private void setMode(int mode, int ticks) {
        this.entityData.set(DATA_MODE, mode);
        this.modeTicks = ticks;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "shards", 0, s -> s.setAndContinue(A_SHARDS)));
    }

    private PlayState mainPredicate(AnimationState<GlitchEntity> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(A_DEATH);
        }
        switch (getAction()) {
            case ACT_SPAWN: return state.setAndContinue(A_SPAWN);
            case ACT_JUMPSCARE: return state.setAndContinue(A_JUMPSCARE);
            case ACT_CHARGE: return state.setAndContinue(A_CHARGE);
            case ACT_SHOOT: return state.setAndContinue(A_SHOOT);
            case ACT_BREAK: return state.setAndContinue(A_BREAK);
            default: break;
        }
        if (isDucking()) {
            return state.setAndContinue(state.isMoving() ? A_CROUCH_WALK : A_CROUCH);
        }
        if (state.isMoving()) {
            int mode = getMode();
            return state.setAndContinue(mode == MODE_CORRUPT ? A_CORRUPT : mode == MODE_APPROACH ? A_CHASE : A_WALK);
        }
        if (this.entityData.get(DATA_WATCHING)) {
            return state.setAndContinue(tickCount % 240 < 40 ? A_HEAD_TURN : A_WATCH);
        }
        return state.setAndContinue(A_IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel sl && !isDeadOrDying()) {
            serverTick(sl);
        }
    }

    private void serverTick(ServerLevel level) {
        if (origin == null) {
            origin = blockPosition();
        }
        if (tickCount == 1) {
            setAction(ACT_SPAWN, 20);
            playSound(ModSounds.GLITCH_APPEAR.get(), 1.5F, 1.0F);
        }
        if (scareCooldown > 0) scareCooldown--;
        if (beamCooldown > 0) beamCooldown--;
        if (relocateCooldown > 0) relocateCooldown--;
        if (tickCount % 20 == 0) corruptBudget = 0;

        switch (getAction()) {
            case ACT_SPAWN -> { if (--actionTicks <= 0) setAction(ACT_NONE, 0); return; }
            case ACT_JUMPSCARE -> {
                getNavigation().stop();
                if (--actionTicks <= 0) {
                    setAction(ACT_NONE, 0);
                    relocateCooldown = 0;       // tras asustar se aparta enseguida
                    setMode(MODE_OBSERVE, 0);
                }
                return;
            }
            case ACT_CHARGE -> { tickCharge(level); return; }
            case ACT_SHOOT -> { tickShoot(level); return; }
            case ACT_BREAK -> { tickBreak(level); return; }
            default -> { }
        }

        if (tickCount % 10 == 0) {
            scanChasers(level);
        }
        if (getMode() != MODE_CORRUPT && chaser != null) {
            startCorrupt(chaser);
        }

        updateDuck();
        if (isDucking() && getMode() == MODE_OBSERVE && !getNavigation().isInProgress() && tickCount % 5 == 0
                && duckTicks > 20 && com.mimicer.util.Ducking.fits(this, STAND_DIMS)) {
            setDucking(false);
        }
        switch (getMode()) {
            case MODE_OBSERVE -> tickObserve(level);
            case MODE_APPROACH -> tickApproach(level);
            case MODE_CORRUPT -> tickCorrupt(level);
            default -> tickRest();
        }

        if (getNavigation().isInProgress() && tickCount % 14 == 0) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.GLITCH_STEP.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ observar / aproximarse

    private ServerPlayer nearestPlayer(ServerLevel level, double range) {
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private void tickObserve(ServerLevel level) {
        ServerPlayer p = nearestPlayer(level, 40.0);
        boolean watching = p != null && hasLineOfSight(p);
        this.entityData.set(DATA_WATCHING, watching);
        if (p == null) {
            if (relocateCooldown <= 0) { relocateCooldown = 200; }
            return;
        }
        getNavigation().stop();
        if (watching) {
            getLookControl().setLookAt(p, 40.0F, 40.0F);
            faceTowards(p);
        }
        if (tickCount > 40 && tickCount % 2 == 0 && scareCooldown <= 0) {
            for (ServerPlayer target : level.players()) {
                if (shouldScare(level, target)) {
                    jumpscare(level, target);
                    return;
                }
            }
        }
        if (relocateCooldown <= 0) {
            if (random.nextInt(5) == 0 && p.distanceTo(this) > 12.0F) {
                setMode(MODE_APPROACH, 120);
            } else {
                relocate(level, p);
            }
            relocateCooldown = 300 + random.nextInt(500);
        }
    }

    private void tickApproach(ServerLevel level) {
        ServerPlayer p = nearestPlayer(level, 40.0);
        if (p == null || --modeTicks <= 0 || distanceTo(p) < 10.0F) {
            setMode(MODE_OBSERVE, 0);
            return;
        }
        if (tickCount % 10 == 0) {
            getNavigation().moveTo(p, 0.8D);
        }
        if (tickCount % 2 == 0 && scareCooldown <= 0) {
            for (ServerPlayer target : level.players()) {
                if (shouldScare(level, target)) {
                    jumpscare(level, target);
                    return;
                }
            }
        }
    }

    private void tickRest() {
        getNavigation().stop();
        if (--modeTicks <= 0) {
            if (chaser != null && chaser.isAlive()) {
                startCorrupt(chaser);
            } else {
                setMode(MODE_OBSERVE, 0);
            }
        }
    }

    // ------------------------------------------------------------------ jumpscare

    private boolean shouldScare(ServerLevel level, ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(this) > 24.0 * 24.0) return false;
        Long last = playerScare.get(p.getUUID());
        if (last != null && level.getGameTime() - last < GlitchConfig.JUMPSCARE_COOLDOWN.get()) return false;
        Vec3 look = p.getViewVector(1.0F);
        Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
        return look.dot(to) > 0.985 && p.hasLineOfSight(this);
    }

    private void jumpscare(ServerLevel level, ServerPlayer p) {
        playerScare.put(p.getUUID(), level.getGameTime());
        scareCooldown = 600;
        // Se abalanza hasta quedar delante del jugador.
        Vec3 look = p.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        for (double dist : new double[]{2.6, 3.4, 4.2}) {
            double x = p.getX() + flat.x * dist, z = p.getZ() + flat.z * dist, y = p.getY();
            AABB box = getDimensions(getPose()).makeBoundingBox(x, y, z);
            if (level.noCollision(this, box)) {
                moveTo(x, y, z, getYRot(), 0.0F);
                break;
            }
        }
        getNavigation().stop();
        faceTowards(p);
        setAction(ACT_JUMPSCARE, 14);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.GLITCH_JUMPSCARE.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 0, false, false));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new NetworkHandler.GlitchScarePacket(16));
        level.sendParticles(PURPLE, getX(), getY() + 1.0, getZ(), 25, 0.5, 0.9, 0.5, 0.05);
    }

    // ------------------------------------------------------------------ reubicación (apariciones)

    private static boolean isHouseBlock(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(Tags.Blocks.COBBLESTONE) || s.is(BlockTags.WOOL);
    }

    /** Busca una ventana (mirando desde dentro) o un lateral de casa a 14-30 bloques del jugador. */
    private void relocate(ServerLevel level, ServerPlayer p) {
        BlockPos center = p.blockPosition();
        Vec3 look = p.getViewVector(1.0F);
        BlockPos window = null, wall = null;
        for (int i = 0; i < 60 && window == null; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double rad = 14.0 + random.nextDouble() * 16.0;
            int x = center.getX() + (int) Math.round(Math.cos(ang) * rad);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * rad);
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) continue;
            // de vez en cuando evita aparecer justo delante de la mirada del jugador
            double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0 && (look.x * dx + look.z * dz) / len > 0.85 && random.nextInt(10) < 7) continue;
            for (int dy = -2; dy <= 3; dy++) {
                BlockPos spot = new BlockPos(x, center.getY() + dy, z);
                BlockPos floor = spot.below();
                if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (d.getStepX() * dx + d.getStepZ() * dz <= 0.0) continue;   // el lado que mira al jugador
                    BlockState n = level.getBlockState(spot.relative(d));
                    BlockState n2 = level.getBlockState(spot.relative(d).above());
                    boolean glass = n.is(Tags.Blocks.GLASS_PANES) || n.is(Tags.Blocks.GLASS)
                            || n2.is(Tags.Blocks.GLASS_PANES) || n2.is(Tags.Blocks.GLASS);
                    if (glass && roofed(level, spot)) { window = spot; break; }
                    if (wall == null && isHouseBlock(n) && isHouseBlock(n2)) wall = spot;
                }
                if (window != null) break;
            }
        }
        BlockPos dest = window != null ? window : wall;
        if (dest == null) {
            return;
        }
        blink(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        faceTowards(p);
        setAction(ACT_SPAWN, 20);
        playSound(ModSounds.GLITCH_APPEAR.get(), 1.2F, 1.0F);
        origin = dest;
    }

    private static boolean roofed(ServerLevel level, BlockPos spot) {
        for (int dy = 3; dy <= 5; dy++) {
            if (!level.getBlockState(spot.above(dy)).isAir()) return true;
        }
        return false;
    }

    private void blink(ServerLevel level, double x, double y, double z) {
        level.sendParticles(PURPLE, getX(), getY() + 1.0, getZ(), 18, 0.4, 0.9, 0.4, 0.05);
        getNavigation().stop();
        moveTo(x, y, z, getYRot(), 0.0F);
        level.sendParticles(PURPLE, x, y + 1.0, z, 18, 0.4, 0.9, 0.4, 0.05);
    }

    private void faceTowards(Entity e) {
        float yaw = (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * 180.0 / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    // ------------------------------------------------------------------ detección de perseguidores

    private void scanChasers(ServerLevel level) {
        Map<Integer, Double> now = new HashMap<>();
        LivingEntity found = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14.0, 6.0, 14.0),
                x -> x != this && x.isAlive())) {
            boolean candidate = e instanceof Player || e instanceof Villager || e instanceof Animal || e instanceof Mob;
            if (!candidate || e instanceof GlitchEntity || e instanceof MimicEntity || e instanceof VoidEntity
                    || (e instanceof Player pl && pl.isSpectator())) continue;
            double dist = e.distanceTo(this);
            now.put(e.getId(), dist);
            boolean targeting = e instanceof Mob m && m.getTarget() == this;
            Double before = lastDist.get(e.getId());
            boolean closing = before != null && before - dist >= 1.2 && dist < 10.0;
            if ((targeting || closing) && (found == null || dist < found.distanceTo(this))) {
                found = e;
            }
        }
        lastDist = now;
        if (found != null) {
            chaser = found;
        } else if (chaser != null && (!chaser.isAlive() || chaser.distanceToSqr(this) > 40.0 * 40.0)) {
            chaser = null;
        }
    }

    // ------------------------------------------------------------------ corrupción

    private void startCorrupt(LivingEntity by) {
        chaser = by;
        setMode(MODE_CORRUPT, 200 + random.nextInt(200));
        beamCooldown = Math.max(beamCooldown, 40 + random.nextInt(60));
        stuckTicks = 0;
    }

    private void tickCorrupt(ServerLevel level) {
        this.entityData.set(DATA_WATCHING, false);
        if (--modeTicks <= 0) {
            chaser = null;
            setMode(MODE_OBSERVE, 0);
            ServerPlayer p = nearestPlayer(level, 64.0);
            if (p != null) {
                relocate(level, p);
            }
            relocateCooldown = 200 + random.nextInt(300);
            return;
        }
        Vec3 threat = chaser != null && chaser.isAlive() ? chaser.position() : null;
        if (threat == null) {
            ServerPlayer p = nearestPlayer(level, 24.0);
            threat = p != null ? p.position() : null;
        }
        if (tickCount % 10 == 0 && threat != null) {
            Vec3 away = DefaultRandomPos.getPosAway(this, 16, 7, threat);
            if (away != null) {
                getNavigation().moveTo(away.x, away.y, away.z, 1.0D);
            }
            checkStuck(level);
        }
        if (tickCount % 4 == 0) {
            corruptBelow(level);
        }
        if (beamCooldown <= 0 && getAction() == ACT_NONE) {
            LivingEntity target = pickBeamTarget(level);
            if (target != null) {
                startCharge(level, target);
            }
        }
    }

    private LivingEntity pickBeamTarget(ServerLevel level) {
        if (chaser != null && chaser.isAlive() && chaser.distanceTo(this) < 28.0F && hasLineOfSight(chaser)) {
            return chaser;
        }
        ServerPlayer p = nearestPlayer(level, 24.0);
        return p != null && hasLineOfSight(p) ? p : null;
    }

    private boolean canCorrupt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.is(ModBlocks.GLITCH_BLOCK.get()) || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > 20.0F) return false;
        if (s.is(BlockTags.WITHER_IMMUNE) || s.is(BlockTags.BEACON_BASE_BLOCKS)) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        return s.isSolidRender(level, pos);
    }

    /** Corrompe el suelo bajo sus pies (y un bloque vecino al azar) respetando los límites configurados. */
    private void corruptBelow(ServerLevel level) {
        if (!GlitchConfig.CORRUPTION_ENABLED.get() || !onGround()
                || corruptedCount >= GlitchConfig.MAX_BLOCKS_PER_ENTITY.get()
                || corruptBudget >= GlitchConfig.MAX_PER_SECOND.get()) return;
        int radius = GlitchConfig.MAX_RADIUS.get();
        BlockPos below = blockPosition().below();
        for (int i = 0; i < 2; i++) {
            BlockPos pos = i == 0 ? below : below.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            if (pos.distSqr(origin) > (double) radius * radius || !canCorrupt(level, pos)) continue;
            level.setBlock(pos, ModBlocks.GLITCH_BLOCK.get().defaultBlockState(), 3);
            corruptedCount++;
            corruptBudget++;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.05);
            if (tickCount - lastCorruptSound >= 10) {
                lastCorruptSound = tickCount;
                level.playSound(null, pos, ModSounds.GLITCH_CORRUPT.get(), SoundSource.HOSTILE, 0.8F, 0.9F + random.nextFloat() * 0.3F);
            }
        }
    }

    // ------------------------------------------------------------------ romper obstáculos

    private boolean canBreak(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.is(BlockTags.WITHER_IMMUNE)) return false;
        float h = s.getDestroySpeed(level, pos);
        return h >= 0.0F && h <= GlitchConfig.MAX_BREAK_HARDNESS.get();
    }

    private void checkStuck(ServerLevel level) {
        if (!GlitchConfig.BREAKING_ENABLED.get()) return;
        if (position().distanceToSqr(lastPos) < 0.25 && getNavigation().isInProgress()) {
            stuckTicks += 10;
        } else {
            stuckTicks = 0;
        }
        lastPos = position();
        if (stuckTicks >= 20 && !isDucking() && duckCooldown <= 0) {
            setDucking(true);       // antes de romper nada, prueba agachado
            duckCooldown = 100;
            stuckTicks = 0;
            getNavigation().stop();
            return;
        }
        if (stuckTicks < 30) return;
        stuckTicks = 0;
        Direction d = getDirection();
        BlockPos front = blockPosition().relative(d);
        for (BlockPos pos : new BlockPos[]{front, front.above(), front.above(2)}) {
            if (canBreak(level, pos)) {
                breakPos = pos;
                getNavigation().stop();
                setAction(ACT_BREAK, GlitchConfig.BREAK_TICKS.get());
                return;
            }
        }
    }

    private void tickBreak(ServerLevel level) {
        getNavigation().stop();
        if (breakPos == null || !canBreak(level, breakPos)) {
            if (breakPos != null) level.destroyBlockProgress(getId(), breakPos, -1);
            breakPos = null;
            setAction(ACT_NONE, 0);
            return;
        }
        int total = GlitchConfig.BREAK_TICKS.get();
        int done = total - actionTicks;
        level.destroyBlockProgress(getId(), breakPos, Math.min(9, done * 10 / total));
        if (done % 6 == 0) {
            level.playSound(null, breakPos, SoundEvents.STONE_HIT, SoundSource.HOSTILE, 0.8F, 0.7F);
        }
        if (--actionTicks <= 0) {
            level.destroyBlockProgress(getId(), breakPos, -1);
            level.destroyBlock(breakPos, false, this);
            breakPos = null;
            setAction(ACT_NONE, 0);
        }
    }

    // ------------------------------------------------------------------ rayo morado

    private void startCharge(ServerLevel level, LivingEntity target) {
        beamTarget = target;
        getNavigation().stop();
        setAction(ACT_CHARGE, 40);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.GLITCH_CHARGE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    private Vec3 handPos() {
        Vec3 f = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(f.x * 0.7, 1.45, f.z * 0.7);
    }

    private void tickCharge(ServerLevel level) {
        getNavigation().stop();
        if (beamTarget == null || !beamTarget.isAlive()) {
            setAction(ACT_NONE, 0);
            beamCooldown = 60;
            return;
        }
        faceTowards(beamTarget);
        getLookControl().setLookAt(beamTarget, 60.0F, 60.0F);
        Vec3 h = handPos();
        if (actionTicks % 2 == 0) {
            double r = 0.3 + actionTicks * 0.04;
            level.sendParticles(PURPLE, h.x + (random.nextDouble() - 0.5) * r * 2.5, h.y + (random.nextDouble() - 0.5) * r * 2.5,
                    h.z + (random.nextDouble() - 0.5) * r * 2.5, 2, 0.0, 0.0, 0.0, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 1, 0.5, 0.8, 0.5, 0.2);
        }
        if (--actionTicks <= 0) {
            beamStart = h;
            Vec3 to = beamTarget.position().add(0.0, beamTarget.getBbHeight() * 0.6, 0.0).subtract(h);
            beamDir = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, yBodyRot) : to.normalize();
            beamDist = 0.0;
            beamHit.clear();
            setAction(ACT_SHOOT, 14);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.GLITCH_BEAM.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        }
    }

    private void tickShoot(ServerLevel level) {
        getNavigation().stop();
        if (beamDist < BEAM_RANGE) {
            for (int i = 0; i < (int) BEAM_SPEED && beamDist < BEAM_RANGE; i++, beamDist += 1.0) {
                Vec3 pos = beamStart.add(beamDir.scale(beamDist));
                if (level.getBlockState(BlockPos.containing(pos)).isSolidRender(level, BlockPos.containing(pos))) {
                    beamDist = BEAM_RANGE;      // el rayo se detiene contra los bloques
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.2);
                    break;
                }
                level.sendParticles(PURPLE, pos.x, pos.y, pos.z, 2, 0.08, 0.08, 0.08, 0.0);
                if (i % 2 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0.15, 0.15, 0.15, 0.1);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(0.9),
                        x -> x != this && !(x instanceof GlitchEntity) && x.isAlive())) {
                    if (e instanceof Player pl && (pl.isSpectator() || pl.isCreative())) continue;
                    if (beamHit.add(e.getId())) {
                        double min = GlitchConfig.BEAM_MIN_DAMAGE.get(), max = Math.max(min, GlitchConfig.BEAM_MAX_DAMAGE.get());
                        e.hurt(damageSources().indirectMagic(this, this), (float) (min + random.nextDouble() * (max - min)));
                        if (com.mimicer.blood.BloodSystem.canBleed(e)) {
                            com.mimicer.blood.BloodSystem.heavyHit(level, e, beamDir, 1.5);
                        }
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
                    }
                }
            }
        }
        if (--actionTicks <= 0) {
            setAction(ACT_NONE, 0);
            beamTarget = null;
            beamCooldown = 160 + random.nextInt(160);
            setMode(MODE_REST, 30);
        }
    }

    // ------------------------------------------------------------------ daño y varios

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof GlitchEntity) && getAction() == ACT_NONE) {
            chaser = attacker;
            if (getMode() != MODE_CORRUPT) {
                startCorrupt(attacker);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && breakPos != null) {
            level().destroyBlockProgress(getId(), breakPos, -1);
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return distanceSqr > 96.0 * 96.0;
    }

    @Override
    protected void tickDeath() {
        // la animación de muerte dura 1 s (20 ticks): coincide con el tiempo estándar
        super.tickDeath();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("corrupted", corruptedCount);
        if (origin != null) tag.putLong("origin", origin.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        corruptedCount = tag.getInt("corrupted");
        if (tag.contains("origin")) origin = BlockPos.of(tag.getLong("origin"));
    }
}
