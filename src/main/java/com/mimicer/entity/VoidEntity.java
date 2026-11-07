package com.mimicer.entity;

import com.mimicer.hide.HideManager;
import com.mimicer.limb.LimbSystem;
import com.mimicer.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

/**
 * The Void: alto y negro, como un Enderman. Se teletransporta a árboles y casas desde donde se asoma a mirar.
 * Si te acercas te avisa ("No te acerques", en rojo y con voz); si te acercas más te persigue, te agarra,
 * te levanta y te estampa contra el suelo. Solo ataca a jugadores y aldeanos; los monstruos no le hacen nada.
 */
public class VoidEntity extends Monster {
    public static final int GRAB_TICKS = 32;

    private static final EntityDataAccessor<Boolean> DATA_GRABBING =
            SynchedEntityData.defineId(VoidEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_DUCK =
            SynchedEntityData.defineId(VoidEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDimensions STAND_DIMS = EntityDimensions.scalable(0.6F, 2.9F);
    private static final EntityDimensions DUCK_DIMS = EntityDimensions.scalable(0.6F, 1.9F);

    private net.minecraft.core.BlockPos throwPos;
    private int throwTicks;
    private int duckTicks;
    private int duckCooldown;
    private LivingEntity grabbed;
    private int grabTicks;
    private int slamCooldown;
    private int teleportCooldown = 60;
    private int blinkCooldown;
    private int stuckTicks;
    private int lostSight;
    private int warnedAt = -100000;

    public VoidEntity(EntityType<? extends VoidEntity> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    // ---- agacharse para pasar por huecos de 2 bloques

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

    private void updateDuck(LivingEntity target) {
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
        if (target != null && duckCooldown <= 0
                && com.mimicer.util.Ducking.gapAhead(this, STAND_DIMS, DUCK_DIMS,
                new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ()))) {
            setDucking(true);
            duckCooldown = 100;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_GRABBING, false);
        this.entityData.define(DATA_DUCK, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, false));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 48.0F));
    }

    public boolean isGrabbing() {
        return this.entityData.get(DATA_GRABBING);
    }

    /** Solo jugadores y aldeanos (los escondidos bajo una cama no cuentan). */
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
        return false;
    }

    // ------------------------------------------------------------------ ciclo

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            serverTick();
        }
    }

    private void serverTick() {
        if (slamCooldown > 0) {
            slamCooldown--;
        }
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
        if (throwPos != null) {
            tickThrow();
            return;
        }
        if (grabbed != null) {
            tickGrab();
            return;
        }
        if (getTarget() != null) {
            tickChase();
        } else {
            if (isDucking() && tickCount % 5 == 0 && duckTicks++ > 8 && com.mimicer.util.Ducking.fits(this, STAND_DIMS)) {
                setDucking(false);
            }
            scanForPrey();
            tickTeleport();
        }
    }

    // ------------------------------------------------------------------ aviso y persecución

    private void scanForPrey() {
        if (tickCount % 5 != 0) {
            return;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(20.0, 8.0, 20.0), this::isPrey)) {
            double d = distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        double dist = Math.sqrt(bestDist);
        if (best instanceof Player player) {
            boolean warned = tickCount - warnedAt < 1200;
            if (!warned && dist < 14.0 && hasLineOfSight(player)) {
                warn(player);
                return;
            }
            // Tras el aviso: si sigues acercándote (o te quedas mirando) te persigue.
            if (warned && tickCount - warnedAt > 30
                    && (dist < 6.5 || (dist < 9.0 && tickCount - warnedAt > 100))) {
                startChase(player);
            }
        } else if (dist < 8.0) {
            startChase(best);
        }
    }

    private void warn(Player player) {
        warnedAt = tickCount;
        getLookControl().setLookAt(player, 60.0F, 60.0F);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.VOID_WARNING.get(), SoundSource.HOSTILE, 4.0F, 0.9F);
        MutableComponent text = Component.literal("No te acerques").withStyle(ChatFormatting.RED);
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("<The Void> ").withStyle(ChatFormatting.DARK_RED).append(text));
            sp.displayClientMessage(text.copy().withStyle(ChatFormatting.BOLD), true);
        }
    }

    private void startChase(LivingEntity target) {
        setTarget(target);
        setAggressive(true);
        stuckTicks = 0;
        lostSight = 0;
        playSound(SoundEvents.ENDERMAN_STARE, 2.0F, 0.5F);
    }

    private void stopChase() {
        setTarget(null);
        setAggressive(false);
        getNavigation().stop();
        warnedAt = -100000;
        teleportCooldown = 40;
    }

    private void tickChase() {
        LivingEntity t = getTarget();
        if (t == null || !isPrey(t) || distanceToSqr(t) > 40.0 * 40.0) {
            stopChase();
            return;
        }
        lostSight = hasLineOfSight(t) ? 0 : lostSight + 1;
        if (lostSight > 300) {
            stopChase();
            return;
        }
        updateDuck(t);
        // Si no encuentra camino, primero se agacha (cabe en huecos de 2 bloques); si aun así no llega, se teletransporta.
        if (getNavigation().isDone() && distanceTo(t) > 3.5F) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > 8 && throwPos == null) {
            net.minecraft.core.BlockPos b = com.mimicer.util.Barricades.find(this,
                    new Vec3(t.getX() - getX(), 0.0, t.getZ() - getZ()), 2);
            if (b != null) {
                startThrow(b);
                return;
            }
        }
        if (stuckTicks > 20 && !isDucking() && duckCooldown <= 0) {
            setDucking(true);
            duckCooldown = 100;
            duckTicks = 0;
            stuckTicks = 0;
            getNavigation().stop();
        }
        if (stuckTicks > 40 && blinkCooldown <= 0) {
            stuckTicks = 0;
            if (tryBlinkNear(t)) {
                blinkCooldown = 100;
            }
        }
    }

    // ------------------------------------------------------------------ barricadas: las agarra y las lanza

    private void startThrow(net.minecraft.core.BlockPos pos) {
        throwPos = pos;
        throwTicks = 0;
        this.entityData.set(DATA_GRABBING, true);       // misma pose de brazos en alto que al agarrar
        this.goalSelector.disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
        getNavigation().stop();
        playSound(SoundEvents.ENDERMAN_STARE, 2.0F, 0.4F);
    }

    private void endThrow() {
        throwPos = null;
        throwTicks = 0;
        stuckTicks = 0;
        this.entityData.set(DATA_GRABBING, false);
        this.goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
    }

    private void tickThrow() {
        getNavigation().stop();
        BlockState st = level().getBlockState(throwPos);
        if (!com.mimicer.util.Barricades.isBarricade(st)) {
            endThrow();
            return;
        }
        Vec3 c = throwPos.getCenter();
        getLookControl().setLookAt(c.x, c.y, c.z);
        float yaw = (float) (Mth.atan2(c.z - getZ(), c.x - getX()) * 180.0 / Math.PI) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
        throwTicks++;
        if (throwTicks == 10) {
            playSound(SoundEvents.WOOD_BREAK, 1.5F, 0.5F);
        }
        if (throwTicks >= 24 && level() instanceof ServerLevel sl) {
            doThrow(sl, st);
        }
    }

    /** Arranca la puerta o la tabla del marco y la lanza hacia delante; el camino queda libre. */
    private void doThrow(ServerLevel level, BlockState st) {
        net.minecraft.core.BlockPos main = throwPos;
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
            if (st.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                main = main.below();
                st = level.getBlockState(main);
            }
            level.setBlock(main.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
        }
        LivingEntity t = getTarget();
        Vec3 c = main.getCenter();
        Vec3 dir = t != null ? new Vec3(t.getX() - c.x, 0.0, t.getZ() - c.z)
                : Vec3.directionFromRotation(0.0F, yBodyRot);
        dir = new Vec3(dir.x, 0.0, dir.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, yBodyRot) : dir.normalize();
        level.levelEvent(2001, main, net.minecraft.world.level.block.Block.getId(st));
        net.minecraft.world.entity.item.FallingBlockEntity fb =
                net.minecraft.world.entity.item.FallingBlockEntity.fall(level, main, st);
        fb.disableDrop();
        fb.setHurtsEntities(2.0F, 40);
        fb.setDeltaMovement(dir.x * 1.1, 0.4, dir.z * 1.1);
        fb.hurtMarked = true;
        level.playSound(null, main, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
        endThrow();
    }

    // ------------------------------------------------------------------ agarre y estampido

    @Override
    public boolean doHurtTarget(Entity target) {
        if (grabbed == null && slamCooldown <= 0 && target instanceof LivingEntity victim && isPrey(victim)) {
            startGrab(victim);
            return true;
        }
        return false;
    }

    private void startGrab(LivingEntity victim) {
        if (!victim.startRiding(this, true)) {
            return;
        }
        grabbed = victim;
        grabTicks = 0;
        this.entityData.set(DATA_GRABBING, true);
        setTarget(null);
        getNavigation().stop();
        playSound(SoundEvents.ENDERMAN_SCREAM, 2.0F, 0.4F);
    }

    private void tickGrab() {
        if (grabbed == null || !grabbed.isAlive() || grabbed.getVehicle() != this) {
            releaseGrab();
            return;
        }
        grabTicks++;
        getNavigation().stop();
        if (grabTicks % 8 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.4, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
            playSound(SoundEvents.ENDERMAN_STARE, 1.5F, 0.4F);
        }
        if (grabTicks >= GRAB_TICKS) {
            slam();
        }
    }

    /** Lo estampa contra el suelo delante de él y lo mata. */
    private void slam() {
        LivingEntity victim = grabbed;
        releaseGrab();
        if (victim == null || !victim.isAlive() || !(level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        double sx = getX() + forward.x * 1.3;
        double sz = getZ() + forward.z * 1.3;
        if (victim instanceof ServerPlayer sp) {
            sp.teleportTo(sx, getY(), sz);
        } else {
            victim.moveTo(sx, getY(), sz, victim.getYRot(), 0.0F);
        }
        victim.setDeltaMovement(0.0, -3.0, 0.0);
        victim.hurtMarked = true;

        Vec3 impact = new Vec3(sx, getY() + 0.2, sz);
        sl.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 2, 0.3, 0.1, 0.3, 0.0);
        LimbSystem.spawnBlood(sl, impact, 60);
        com.mimicer.blood.BloodSystem.impactBurst(sl, victim, new Vec3(sx, getY(), sz), 2.5);
        sl.playSound(null, sx, getY(), sz, SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 2.0F, 0.5F);
        sl.playSound(null, sx, getY(), sz, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);

        if (LimbSystem.canLoseLimbs(victim)) {
            int limb = LimbSystem.pickIntactLimb(victim, random);
            if (limb != 0) {
                LimbSystem.rip(sl, victim, limb);
            }
        }
        victim.hurt(damageSources().mobAttack(this), Float.MAX_VALUE);
        slamCooldown = 60;
        warnedAt = -100000;
        teleportCooldown = 30;
        setAggressive(false);
    }

    /** Suelta a quien tenga agarrado. Primero se desmarca el agarre, o el evento de bajarse quedaría cancelado. */
    private void releaseGrab() {
        this.entityData.set(DATA_GRABBING, false);
        if (grabbed != null) {
            grabbed.stopRiding();
            grabbed = null;
        }
        grabTicks = 0;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            releaseGrab();
            if (throwPos != null) {
                endThrow();
            }
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ teletransporte

    /** Cada cierto tiempo salta a un árbol o una casa cercanos al jugador, tras el tronco o la pared, a asomarse. */
    private void tickTeleport() {
        if (--teleportCooldown > 0) {
            return;
        }
        teleportCooldown = 100 + random.nextInt(160);
        Player player = level().getNearestPlayer(getX(), getY(), getZ(), 56.0,
                e -> e instanceof Player p && isPrey(p));
        if (player == null) {
            return;
        }
        BlockPos spot = findPeekSpot(player);
        if (spot != null) {
            blink(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player);
        }
    }

    private static boolean isCover(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(Tags.Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS);
    }

    /**
     * Busca un hueco de pie, a 12-28 bloques del jugador, con un tronco o una pared (dos bloques de alto)
     * justo entre él y el jugador: así queda medio oculto y solo se asoma.
     */
    private BlockPos findPeekSpot(Player player) {
        Level level = level();
        BlockPos center = player.blockPosition();
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 12.0 + random.nextDouble() * 16.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spot = new BlockPos(x, y, z);
            BlockPos floor = spot.below();
            if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()
                    || !level.getBlockState(spot.above(2)).isAir()
                    || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                continue;
            }
            double dx = player.getX() - (spot.getX() + 0.5);
            double dz = player.getZ() - (spot.getZ() + 0.5);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d.getStepX() * dx + d.getStepZ() * dz <= 0.0) {
                    continue; // el tapón tiene que quedar del lado del jugador
                }
                BlockPos cover = spot.relative(d);
                if (isCover(level.getBlockState(cover)) && isCover(level.getBlockState(cover.above()))) {
                    return spot;
                }
            }
        }
        return null;
    }

    /** Aparece junto a su presa cuando no puede llegar andando (p. ej. dentro de una casa). */
    private boolean tryBlinkNear(LivingEntity target) {
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + random.nextDouble() * 1.5;
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            double y = target.getY();
            AABB box = getDimensions(getPose()).makeBoundingBox(x, y, z);
            if (level().noCollision(this, box) && !level().getBlockState(BlockPos.containing(x, y - 0.5, z)).isAir()) {
                blink(x, y, z, target);
                return true;
            }
        }
        return false;
    }

    private void blink(double x, double y, double z, LivingEntity lookAt) {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 12, 0.3, 1.0, 0.3, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
        getNavigation().stop();
        moveTo(x, y, z, getYRot(), 0.0F);
        if (lookAt != null) {
            float yaw = (float) (Mth.atan2(lookAt.getZ() - z, lookAt.getX() - x) * 180.0 / Math.PI) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            setYHeadRot(yaw);
        }
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.5, z, 12, 0.3, 1.0, 0.3, 0.02);
        sl.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
    }

    // ------------------------------------------------------------------ daño y sonidos

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        // Los monstruos no le hacen nada.
        if (attacker instanceof Enemy) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && attacker instanceof LivingEntity le && isPrey(le) && getTarget() == null && grabbed == null) {
            warnedAt = tickCount - 100;
            startChase(le);
        }
        return result;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return isDucking() ? 1.6F : 2.55F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.45F;
    }
}
