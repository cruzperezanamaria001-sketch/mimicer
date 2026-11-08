package com.mimicer.entity;

import com.mimicer.entity.goal.MimicBreakBlocksGoal;
import com.mimicer.entity.goal.MimicCorpseGoal;
import com.mimicer.entity.goal.MimicInvestigateGoal;
import com.mimicer.entity.goal.MimicStalkWindowGoal;
import com.mimicer.hide.HideManager;
import com.mimicer.limb.LimbSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
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
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * El Mímico: de día es indistinguible de un aldeano; de noche se transforma en un monstruo.
 */
public class MimicEntity extends Monster implements com.mimicer.entity.scene.SceneHost {
    public static final int TRANSFORM_TICKS = 40;

    private static final UUID NIGHT_SPEED_ID = UUID.fromString("5f6a0f4e-3a52-4c55-8d2c-6d1a1b0a7e11");
    // Cabe por puertas de 2 bloques; para ventanas de 1 bloque se arrastra (CRAWL).
    private static final EntityDimensions MONSTER_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.9F);
    private static final EntityDimensions CRAWL_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.9F);

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
    /** Apariencia de la forma monstruosa (0..VARIANT_COUNT-1). */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SIZE =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    public static final int VARIANT_COUNT = 5;
    private static final EntityDataAccessor<Integer> DATA_FEED_START =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXTRACT_START =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CRAWL =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tipo de aldeano (bioma) y profesión que imita; vacío hasta que se asigna en el servidor. */
    private static final EntityDataAccessor<String> DATA_VTYPE =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PROFESSION =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.STRING);

    private static final String[] PROFESSIONS = {"none", "nitwit", "armorer", "butcher", "cartographer", "cleric",
            "farmer", "fisherman", "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith",
            "weaponsmith"};

    private float transformProgress;
    private float transformProgressO;
    private float crawlProgress;
    private float crawlProgressO;
    private int crawlTicks;
    private boolean scenePosture;
    private com.mimicer.entity.scene.FeedGoal feedGoal;
    private com.mimicer.entity.scene.BedExtractionGoal extractGoal;
    private int crawlCooldown;
    private int noPathTicks;
    private double holeFloorY = Double.NaN;
    private int holeTick;
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
        this.entityData.define(DATA_VTYPE, "");
        this.entityData.define(DATA_PROFESSION, "");
        this.entityData.define(DATA_CRAWL, false);
        this.entityData.define(DATA_FEED_START, -1);
        this.entityData.define(DATA_EXTRACT_START, -1);
        this.entityData.define(DATA_VARIANT, -1);
        this.entityData.define(DATA_SIZE, 100);
    }

    // ------------------------------------------------------------------ habilidades por variante

    public static final int V_CLASSIC = 0;
    public static final int V_ASH = 1;
    public static final int V_BONE = 2;
    public static final int V_MOSS = 3;
    public static final int V_FLAYED = 4;

    private static final UUID FRENZY_ID = UUID.fromString("0b6c1f0e-7d8a-4f3b-9a55-2f4d8c1e6a22");

    private int dashCooldown;
    private int frenzyStacks;
    private int frenzyTimer;

    /**
     * Clásico: embestida. Ceniza: inmune al fuego y prende a sus víctimas. Hueso: aura de frío que ralentiza.
     * Musgo: veneno al golpear y se regenera. Desollado: frenesí, cada golpe lo acelera y le cura.
     * Solo en forma monstruosa.
     */
    private void tickAbilities() {
        if (!(level() instanceof ServerLevel sl) || isTransforming()) {
            return;
        }
        switch (getVariant()) {
            case V_CLASSIC -> tickCharge();
            case V_ASH -> {
                if (isOnFire()) {
                    setRemainingFireTicks(0);
                }
                if (tickCount % 5 == 0) {
                    sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 2, 0.3, 0.5, 0.3, 0.01);
                }
                if (tickCount % 15 == 0) {
                    sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.5, 0.3, 0.01);
                }
            }
            case V_BONE -> tickFrost(sl);
            case V_MOSS -> {
                if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
                    heal(2.0F);
                }
                if (tickCount % 10 == 0) {
                    sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 1.2, getZ(), 3, 0.4, 0.6, 0.4, 0.0);
                }
            }
            case V_FLAYED -> tickFrenzy(sl);
            default -> { }
        }
    }

    /** Embestida: se lanza en línea recta contra su presa cuando está a media distancia. */
    private void tickCharge() {
        if (dashCooldown > 0) {
            dashCooldown--;
            return;
        }
        LivingEntity t = getTarget();
        if (t == null || !onGround() || isCrawling() || getCarried() != null) {
            return;
        }
        double d = distanceTo(t);
        if (d < 6.0 || d > 16.0 || !hasLineOfSight(t)) {
            return;
        }
        Vec3 dir = new Vec3(t.getX() - getX(), 0.0, t.getZ() - getZ()).normalize();
        setDeltaMovement(dir.x * 1.0, 0.25, dir.z * 1.0);
        this.hasImpulse = true;
        dashCooldown = 140;
        playSound(SoundEvents.ZOMBIE_VILLAGER_AMBIENT, 1.4F, 0.3F);
        playSound(com.mimicer.registry.ModSounds.MIMIC_SCREAM.get(), 1.5F, 1.25F);
    }

    /** Aura de frío: ralentiza y congela poco a poco a jugadores y aldeanos cercanos. */
    private void tickFrost(ServerLevel sl) {
        if (tickCount % 10 != 0) {
            return;
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(9.0, 3.0, 9.0),
                x -> x instanceof Player || x instanceof AbstractVillager)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 12));
        }
        sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.0, getZ(), 6, 1.2, 0.8, 1.2, 0.02);
    }

    private void tickFrenzy(ServerLevel sl) {
        if (frenzyStacks <= 0) {
            return;
        }
        if (--frenzyTimer <= 0) {
            frenzyStacks--;
            frenzyTimer = 60;
            applyFrenzy();
        }
        if (frenzyStacks >= 3 && tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private void addFrenzy() {
        if (frenzyStacks < 5) {
            frenzyStacks++;
        }
        frenzyTimer = 160;
        applyFrenzy();
    }

    private void applyFrenzy() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(FRENZY_ID);
        if (frenzyStacks > 0) {
            speed.addTransientModifier(new AttributeModifier(FRENZY_ID, "mimic frenzy",
                    0.07D * frenzyStacks, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void removeFrenzy() {
        frenzyStacks = 0;
        applyFrenzy();
    }

    private void onHitAbility(LivingEntity victim) {
        switch (getVariant()) {
            case V_ASH -> victim.setSecondsOnFire(5);
            case V_BONE -> {
                victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 60);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
            case V_MOSS -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            case V_FLAYED -> {
                addFrenzy();
                heal(2.0F);
            }
            default -> victim.knockback(0.8, getX() - victim.getX(), getZ() - victim.getZ());
        }
    }

    /** Variante visual de la forma monstruosa; 0 mientras no se haya asignado. */
    public int getVariant() {
        return Math.max(0, Math.min(VARIANT_COUNT - 1, this.entityData.get(DATA_VARIANT)));
    }

    /** Factor de tamaño visual (0,92 a 1,1). Solo cosmético: no cambia la hitbox. */
    public float getSizeScale() {
        return this.entityData.get(DATA_SIZE) / 100.0F;
    }

    // ------------------------------------------------------------------ arrastrarse

    public boolean isCrawling() {
        return this.entityData.get(DATA_CRAWL);
    }

    public float getCrawlProgress(float partialTick) {
        return Mth.lerp(partialTick, crawlProgressO, crawlProgress);
    }

    private void setCrawling(boolean crawling) {
        if (isCrawling() != crawling) {
            this.entityData.set(DATA_CRAWL, crawling);
            crawlTicks = 0;
            refreshDimensions();
        }
    }

    private boolean canStandUp() {
        return level().noCollision(this, MONSTER_DIMENSIONS.makeBoundingBox(getX(), getY(), getZ()));
    }

    /** Agachado cabe donde de pie no: hay un hueco bajo (p. ej. una ventana rota) hacia el objetivo. */
    private boolean lowGapAhead(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            return false;
        }
        dx /= len;
        dz /= len;
        for (double dist : new double[]{0.8, 1.3, 1.9}) {
            double x = getX() + dx * dist;
            double z = getZ() + dz * dist;
            for (double dy : new double[]{0.0, 1.0}) {
                boolean fitsCrawling = level().noCollision(this, CRAWL_DIMENSIONS.makeBoundingBox(x, getY() + dy, z));
                boolean fitsStanding = level().noCollision(this, MONSTER_DIMENSIONS.makeBoundingBox(x, getY() + dy, z));
                if (fitsCrawling && !fitsStanding) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateCrawl() {
        if (!isMonsterForm()) {
            setCrawling(false);
            return;
        }
        if (scenePosture) {
            setCrawling(true);       // agachado durante la comida y la extracción
            return;
        }
        if (tickCount % 5 != 0) {
            return;
        }
        if (isCrawling()) {
            crawlTicks += 5;
            // Se levanta cuando ya hay sitio y pasó un rato desde que se agachó.
            if (crawlTicks > 60 && canStandUp()) {
                setCrawling(false);
            }
            return;
        }
        if (crawlCooldown > 0) {
            crawlCooldown -= 5;
        }
        LivingEntity target = getTarget();
        if (target == null) {
            noPathTicks = 0;
            return;
        }
        boolean idle = getNavigation().isDone();
        noPathTicks = idle && distanceTo(target) > 3.0F ? noPathTicks + 5 : 0;
        boolean gapAhead = (idle || horizontalCollision) && lowGapAhead(target);
        // Si lleva un rato sin poder llegar, prueba agachado: quizá el camino es un túnel o una ventana bajos.
        if (crawlCooldown <= 0 && (gapAhead || noPathTicks >= 40)) {
            setCrawling(true);
            crawlCooldown = 200;
            noPathTicks = 0;
            getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ hoyos

    /** De noche se atreve a saltar a pozos profundos para llegar a su presa. */
    @Override
    public int getMaxFallDistance() {
        return isMonsterForm() ? 14 : super.getMaxFallDistance();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (isMonsterForm()) {
            if (distance >= 2.0F) {
                holeFloorY = getY();
                holeTick = tickCount;
            }
            return false;
        }
        return super.causeFallDamage(distance, multiplier, source);
    }

    /** Solo trepa para salir de un hoyo en el que acaba de caer, no por cualquier pared. */
    @Override
    public boolean onClimbable() {
        return isMonsterForm() && horizontalCollision && !Double.isNaN(holeFloorY) && getY() < holeFloorY + 10.0;
    }

    /** Nombre del tipo de aldeano que imita (plains, desert, ...). */
    public String getVillagerTypeName() {
        String s = this.entityData.get(DATA_VTYPE);
        return s.isEmpty() ? "plains" : s;
    }

    /** Profesión que imita ("none" si no lleva ropa de profesión). */
    public String getProfessionName() {
        String s = this.entityData.get(DATA_PROFESSION);
        return s.isEmpty() ? "none" : s;
    }

    private void assignDisguise() {
        if (this.entityData.get(DATA_VARIANT) < 0) {
            this.entityData.set(DATA_VARIANT, random.nextInt(VARIANT_COUNT));
            this.entityData.set(DATA_SIZE, 92 + random.nextInt(19));
        }
        if (this.entityData.get(DATA_VTYPE).isEmpty()) {
            String type = "plains";
            try {
                type = BuiltInRegistries.VILLAGER_TYPE
                        .getKey(VillagerType.byBiome(level().getBiome(blockPosition()))).getPath();
            } catch (RuntimeException ignored) {
                // se queda con plains
            }
            this.entityData.set(DATA_VTYPE, type);
        }
        if (this.entityData.get(DATA_PROFESSION).isEmpty()) {
            this.entityData.set(DATA_PROFESSION, PROFESSIONS[random.nextInt(PROFESSIONS.length)]);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.feedGoal = new com.mimicer.entity.scene.FeedGoal(this);
        this.extractGoal = new com.mimicer.entity.scene.BedExtractionGoal(this, com.mimicer.entity.scene.SceneTiming.Kind.MIMIC);
        this.goalSelector.addGoal(0, this.extractGoal);
        this.goalSelector.addGoal(0, this.feedGoal);
        this.goalSelector.addGoal(1, new MimicCorpseGoal(this));
        this.goalSelector.addGoal(2, new MimicBreakBlocksGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(4, new MimicInvestigateGoal(this));
        this.goalSelector.addGoal(5, new MimicStalkWindowGoal(this));
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Villager.class, 6.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        // Un solo selector para jugadores, aldeanos y animales: así elige siempre a la presa MÁS CERCANA,
        // sin dar prioridad a los jugadores sobre el resto.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, false, false,
                this::isPrey));
    }

    /**
     * Presas válidas en forma monstruosa. Jugadores y animales exigen verlos (los jugadores en casa los gestiona
     * el acecho por ventanas); los aldeanos se atrincheran, así que se les puede perseguir sin verlos
     * y romper las barricadas.
     */
    private boolean isPrey(LivingEntity e) {
        if (!isMonsterForm() || e == this) {
            return false;
        }
        if (e instanceof Player player) {
            return !HideManager.isHidden(player) && hasLineOfSight(player);
        }
        if (e instanceof AbstractVillager) {
            // Un aldeano escondido bajo una cama no es presa hasta que lo descubran.
            return !(e instanceof Villager v && HideManager.isVillagerHidden(v));
        }
        return e instanceof Animal && hasLineOfSight(e);
    }

    // ------------------------------------------------------------------ escenas (comida / extracción)

    @Override
    public net.minecraft.world.entity.PathfinderMob mob() {
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
    public void scenePosture(boolean crouched) {
        this.scenePosture = crouched;
        if (!crouched && isCrawling() && canStandUp()) {
            setCrawling(false);
        }
    }

    @Override
    public boolean sceneAllowed() {
        return isMonsterForm() && !isTransforming();
    }

    @Override
    public void beginFeeding(CorpseEntity corpse) {
        if (sceneAllowed() && feedGoal != null && !feedGoal.isActive()) {
            feedGoal.assign(corpse);
        }
    }

    @Override
    public boolean requestExtraction(LivingEntity victim, @Nullable BlockPos bed) {
        return bed != null && extractGoal != null && extractGoal.request(victim, bed);
    }

    @Override
    public void onExtracted(LivingEntity victim) {
        setTarget(victim);
    }

    @Override
    public void onExtractFailed(LivingEntity victim) {
        if (victim instanceof net.minecraft.server.level.ServerPlayer sp && HideManager.isHidden(sp)) {
            HideManager.reveal(sp, this);
        } else if (victim instanceof Villager v && HideManager.isVillagerHidden(v)) {
            HideManager.revealVillager(v, this);
        }
    }

    @Override
    public boolean isBusyWithScene() {
        return (feedGoal != null && feedGoal.isActive()) || (extractGoal != null && extractGoal.isActive());
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
        crawlProgressO = crawlProgress;
        crawlProgress = Mth.clamp(crawlProgress + (isCrawling() ? 0.2F : -0.2F), 0.0F, 1.0F);
        super.tick();
        if (!level().isClientSide) {
            serverTick();
        }
    }

    private void serverTick() {
        if (origin == null) {
            origin = blockPosition();
        }
        assignDisguise();
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

        updateCrawl();
        // Ya salió del hoyo (o pasó demasiado tiempo): deja de poder trepar.
        if (!Double.isNaN(holeFloorY)
                && ((onGround() && getY() > holeFloorY + 1.2) || tickCount - holeTick > 600)) {
            holeFloorY = Double.NaN;
        }
        // Dentro de un hoyo no hay camino de salida para el navegador: empuja en línea recta hacia la presa y trepa.
        if (monster && !Double.isNaN(holeFloorY) && getTarget() != null && getNavigation().isDone()) {
            LivingEntity prey = getTarget();
            getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), 1.0D);
        }

        if (monster) {
            HideManager.mimicSense(this);
            if (!isBusyWithScene()) {
                tickAbilities();
            }
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
        removeFrenzy();
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
            // Grito al transformarse; el tono cambia un poco según la apariencia.
            playSound(com.mimicer.registry.ModSounds.MIMIC_SCREAM.get(), 2.0F, 0.85F + 0.06F * getVariant());
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
        if (DATA_TRANSFORM.equals(key) || DATA_CRAWL.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float p = getTransformTicks() / (float) TRANSFORM_TICKS;
        if (p <= 0.0F) {
            return super.getDimensions(pose);
        }
        if (p >= 1.0F) {
            return isCrawling() ? CRAWL_DIMENSIONS : MONSTER_DIMENSIONS;
        }
        return EntityDimensions.scalable(0.6F, Mth.lerp(p, 1.95F, 1.9F));
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
            if (isMonsterForm()) {
                onHitAbility(victim);
            }
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
        if (getVariant() == V_ASH && isMonsterForm() && source.is(DamageTypeTags.IS_FIRE)) {
            setRemainingFireTicks(0);
            return false;
        }
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

    /** Disfrazado de aldeano no elige objetivos, salvo que lo hayan descubierto golpeándolo. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !isMonsterForm() && !isTransforming() && revealTicks <= 0) {
            return;
        }
        super.setTarget(target);
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
        tag.putString("VillagerType", this.entityData.get(DATA_VTYPE));
        tag.putString("Profession", this.entityData.get(DATA_PROFESSION));
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
        tag.putInt("SizePct", this.entityData.get(DATA_SIZE));
        if (origin != null) {
            tag.put("Origin", NbtUtils.writeBlockPos(origin));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTransformTicks(tag.getInt("Transform"));
        this.entityData.set(DATA_VTYPE, tag.getString("VillagerType"));
        this.entityData.set(DATA_PROFESSION, tag.getString("Profession"));
        if (tag.contains("Variant")) {
            this.entityData.set(DATA_VARIANT, tag.getInt("Variant"));
            this.entityData.set(DATA_SIZE, tag.getInt("SizePct"));
        }
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
