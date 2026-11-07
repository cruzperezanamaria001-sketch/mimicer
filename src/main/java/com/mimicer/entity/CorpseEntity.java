package com.mimicer.entity;

import com.mimicer.registry.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuerpo de una víctima del Mímico. Conserva visualmente las extremidades perdidas,
 * y en el caso de un jugador, su skin. Puede ser recogido y lanzado por el Mímico.
 */
public class CorpseEntity extends Entity {
    public static final int MAX_AGE = 24000;

    private static final EntityDataAccessor<String> VICTIM_TYPE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> PLAYER_UUID =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> LIMBS =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BABY =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CARRIED =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    /** Ropa de un aldeano: "tipo|profesión|nivel" (p. ej. "plains|farmer|2"); vacío si no aplica. */
    private static final EntityDataAccessor<String> CLOTHES =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);

    private UUID carrierId;
    private boolean thrown;
    private boolean hitSomething;
    private int age;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static CorpseEntity create(Level level, LivingEntity victim, int limbs, UUID playerUuid) {
        CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE.get(), level);
        corpse.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        corpse.entityData.set(VICTIM_TYPE, key.toString());
        corpse.entityData.set(PLAYER_UUID, Optional.ofNullable(playerUuid));
        corpse.entityData.set(LIMBS, limbs);
        corpse.entityData.set(BABY, victim.isBaby());
        if (victim instanceof Villager villager) {
            VillagerData data = villager.getVillagerData();
            String type = BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType()).getPath();
            String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath();
            corpse.entityData.set(CLOTHES, type + "|" + profession + "|" + data.getLevel());
        } else if (victim instanceof MimicEntity mimic) {
            corpse.entityData.set(CLOTHES, mimic.getVillagerTypeName() + "|" + mimic.getProfessionName() + "|1");
        }
        return corpse;
    }

    public String getClothes() {
        return this.entityData.get(CLOTHES);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(VICTIM_TYPE, "minecraft:villager");
        this.entityData.define(PLAYER_UUID, Optional.empty());
        this.entityData.define(LIMBS, 0);
        this.entityData.define(BABY, false);
        this.entityData.define(CARRIED, false);
        this.entityData.define(CLOTHES, "");
    }

    public String getVictimType() {
        return this.entityData.get(VICTIM_TYPE);
    }

    public Optional<UUID> getPlayerUuid() {
        return this.entityData.get(PLAYER_UUID);
    }

    public int getLimbs() {
        return this.entityData.get(LIMBS);
    }

    public boolean isBabyVictim() {
        return this.entityData.get(BABY);
    }

    public boolean isCarried() {
        return this.entityData.get(CARRIED);
    }

    public boolean isThrown() {
        return thrown;
    }

    public boolean isFree() {
        return carrierId == null && !thrown;
    }

    /** El Mímico levanta el cuerpo. */
    public void pickUp(MimicEntity carrier) {
        this.carrierId = carrier.getUUID();
        this.thrown = false;
        this.hitSomething = false;
        this.entityData.set(CARRIED, true);
    }

    /** El Mímico lo lanza con una velocidad concreta. */
    public void throwBody(Vec3 velocity) {
        this.carrierId = null;
        this.thrown = true;
        this.hitSomething = false;
        this.entityData.set(CARRIED, false);
        this.setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    public void drop() {
        this.carrierId = null;
        this.entityData.set(CARRIED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (++age > MAX_AGE) {
            discard();
            return;
        }

        if (carrierId != null) {
            Entity carrier = ((ServerLevel) level()).getEntity(carrierId);
            if (carrier instanceof MimicEntity mimic && mimic.isAlive() && mimic.getCarried() == this) {
                Vec3 look = Vec3.directionFromRotation(0.0F, mimic.yBodyRot);
                setPos(mimic.getX() + look.x * 0.5, mimic.getY() + mimic.getBbHeight() * 0.95, mimic.getZ() + look.z * 0.5);
                setYRot(mimic.yBodyRot + 90.0F);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                return;
            }
            drop();
        }

        // Física simple: gravedad, colisión con el suelo y rozamiento.
        Vec3 motion = getDeltaMovement();
        if (!onGround()) {
            motion = motion.add(0.0, -0.04, 0.0);
        }
        move(MoverType.SELF, motion);
        Vec3 after = getDeltaMovement();
        double friction = onGround() ? 0.6 : 0.98;
        setDeltaMovement(after.x * friction, onGround() ? 0.0 : after.y * 0.98, after.z * friction);
        if (horizontalCollision) {
            setDeltaMovement(getDeltaMovement().multiply(-0.3, 1.0, -0.3));
        }

        if (thrown) {
            if (!hitSomething) {
                List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(0.4), e -> !(e instanceof MimicEntity) && e.isAlive());
                for (LivingEntity hit : hits) {
                    hit.hurt(damageSources().generic(), 5.0F);
                    hit.knockback(0.8, -getDeltaMovement().x, -getDeltaMovement().z);
                    hitSomething = true;
                    break;
                }
            }
            if (onGround() && getDeltaMovement().horizontalDistanceSqr() < 0.0025) {
                thrown = false;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(VICTIM_TYPE, tag.getString("VictimType"));
        entityData.set(LIMBS, tag.getInt("Limbs"));
        entityData.set(BABY, tag.getBoolean("Baby"));
        entityData.set(CLOTHES, tag.getString("Clothes"));
        entityData.set(PLAYER_UUID, tag.hasUUID("PlayerUuid") ? Optional.of(tag.getUUID("PlayerUuid")) : Optional.empty());
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("VictimType", getVictimType());
        tag.putInt("Limbs", getLimbs());
        tag.putBoolean("Baby", isBabyVictim());
        tag.putString("Clothes", getClothes());
        getPlayerUuid().ifPresent(id -> tag.putUUID("PlayerUuid", id));
        tag.putInt("Age", age);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
