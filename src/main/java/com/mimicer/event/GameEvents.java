package com.mimicer.event;

import com.mimicer.MimicerMod;
import com.mimicer.entity.CorpseEntity;
import com.mimicer.entity.MimicEntity;
import com.mimicer.hide.HideManager;
import com.mimicer.hide.NoiseManager;
import com.mimicer.limb.LimbSystem;
import com.mimicer.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameEvents {
    public static final String TAG_CONVERT = "mimicer_convert";
    private static final float VILLAGE_MIMIC_CHANCE = 0.05F;

    private GameEvents() {}

    // ------------------------------------------------------------ inmunidad ante monstruos

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof MimicEntity mimic)) {
            return;
        }
        LivingEntity attacker = event.getEntity();
        if (attacker instanceof MimicEntity) {
            return;
        }
        // Ningún monstruo hostil ataca al Mímico; los golems tampoco mientras va disfrazado.
        if (attacker instanceof Enemy || (attacker instanceof IronGolem && !mimic.isMonsterForm())) {
            event.setCanceled(true);
        }
    }

    /** Los monstruos no le hacen nada a The Void. */
    @SubscribeEvent
    public static void onVoidTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof com.mimicer.entity.VoidEntity && event.getEntity() instanceof Enemy
                && !(event.getEntity() instanceof com.mimicer.entity.VoidEntity)) {
            event.setCanceled(true);
        }
    }

    /** Mientras The Void agarra a alguien, no puede bajarse (ni con shift). */
    @SubscribeEvent
    public static void onMount(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof com.mimicer.entity.VoidEntity v
                && v.isGrabbing() && event.getEntityMounting().isAlive()) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------ extremidades y sangrado

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 20 == 0
                && LimbSystem.canLoseLimbs(entity)) {
            LimbSystem.tickSecond(level, entity);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !LimbSystem.canLoseLimbs(victim)) {
            return;
        }
        boolean direct = event.getSource().getEntity() instanceof MimicEntity
                || event.getSource().getEntity() instanceof com.mimicer.entity.VoidEntity;
        boolean glitch = event.getSource().getEntity() instanceof com.mimicer.entity.GlitchEntity
                || victim.getLastHurtByMob() instanceof com.mimicer.entity.GlitchEntity;
        if (!direct && !glitch && !(victim.getLastHurtByMob() instanceof MimicEntity)) {
            return;
        }
        int limbs = LimbSystem.get(victim);
        if (direct && level.random.nextFloat() < 0.25F) {
            limbs |= LimbSystem.HEAD;
        }
        UUID playerId = victim instanceof Player ? victim.getUUID() : null;
        CorpseEntity corpse = CorpseEntity.create(level, victim, limbs, playerId);
        level.addFreshEntity(corpse);
        // El asesino (Mímico o The Void) se acerca al cadáver y se alimenta de él.
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        if (!(killer instanceof com.mimicer.entity.scene.SceneHost)) {
            killer = victim.getLastHurtByMob();
        }
        if (killer instanceof com.mimicer.entity.scene.SceneHost host && killer.isAlive()) {
            host.beginFeeding(corpse);
        }
        LimbSystem.spawnBlood(level, victim.position().add(0.0, 0.4, 0.0), 60);
        LimbSystem.clear(victim);
    }

    // ------------------------------------------------------------ escondites

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());

        if (HideManager.isHidden(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (state.getBlock() instanceof BedBlock && player.isShiftKeyDown()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!level.isClientSide && player instanceof ServerPlayer sp && !state.getValue(BedBlock.OCCUPIED)
                    && !sp.isSpectator() && !HideManager.isBedTaken(level, event.getPos())) {
                HideManager.startHiding(sp, event.getPos());
            }
            return;
        }
        if (!level.isClientSide && state.is(BlockTags.DOORS)) {
            NoiseManager.emit(level, Vec3.atCenterOf(event.getPos()), 16.0F);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (HideManager.isHidden(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (HideManager.isHidden(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        HideManager.serverTick(player);
        if (player.isSprinting() && player.onGround() && player.tickCount % 10 == 0 && !HideManager.isHidden(player)) {
            NoiseManager.emit(player.level(), player.position(), 10.0F);
        }
    }

    /** Escondido bajo la cama el jugador es bajito: la cámara queda a ras del suelo. */
    @SubscribeEvent
    public static void onEntitySize(net.minecraftforge.event.entity.EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && HideManager.isHidden(player)) {
            event.setNewSize(net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 0.3F));
            event.setNewEyeHeight(0.15F);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HideManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            HideManager.stopHiding(sp);
        }
    }

    // ------------------------------------------------------------ ruidos

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            NoiseManager.emit(level, Vec3.atCenterOf(event.getPos()), 20.0F);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && event.getEntity() instanceof Player) {
            NoiseManager.emit(level, Vec3.atCenterOf(event.getPos()), 12.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            NoiseManager.emit(player.level(), player.position(), 24.0F);
        }
    }

    // ------------------------------------------------------------ mímicos en las aldeas

    /** Al generarse una aldea, una pequeña parte de los aldeanos son en realidad Mímicos. */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Villager villager && event.getSpawnType() == MobSpawnType.STRUCTURE
                && !villager.isBaby() && villager.getRandom().nextFloat() < VILLAGE_MIMIC_CHANCE) {
            villager.getPersistentData().putBoolean(TAG_CONVERT, true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Villager villager)
                || !villager.getPersistentData().getBoolean(TAG_CONVERT)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.setCanceled(true);
        double x = villager.getX();
        double y = villager.getY();
        double z = villager.getZ();
        float yaw = villager.getYRot();
        level.getServer().execute(() -> {
            MimicEntity mimic = ModEntities.MIMIC.get().create(level);
            if (mimic != null) {
                mimic.moveTo(x, y, z, yaw, 0.0F);
                level.addFreshEntity(mimic);
            }
        });
    }
}
