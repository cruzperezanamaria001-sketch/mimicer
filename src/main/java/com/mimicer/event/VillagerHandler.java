package com.mimicer.event;

import com.mimicer.MimicerMod;
import com.mimicer.block.BoardBlock;
import com.mimicer.entity.MimicEntity;
import com.mimicer.hide.HideManager;
import com.mimicer.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * Aldeanos inteligentes: sospechan del Mímico, alertan a los demás, corren a su cama, cierran puertas
 * y tapian puertas y ventanas con Tablas. La huida está acotada a los límites de la aldea.
 */
@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerHandler {
    private static final String TAG_ALARM = "mimicer_alarm_until";
    private static final String TAG_ORIGIN = "mimicer_origin";
    private static final String TAG_PANIC = "mimicer_panic_ticks";
    private static final String TAG_BOARDS = "mimicer_boards";
    private static final String TAG_THREAT = "mimicer_threat_time";
    private static final String TAG_UNBOARD = "mimicer_unboard_time";

    /** Ticks sin ver amenazas antes de abrir las barricadas. */
    private static final int CALM_TICKS = 200;
    /** Una amenaza solo justifica tapiar si se vio hace menos de esto. */
    private static final int THREAT_RECENT_TICKS = 600;

    private static final int ALARM_TICKS = 1200;
    private static final int MAX_PANIC_TICKS = 300;
    private static final double MAX_ORIGIN_DISTANCE = 40.0;
    private static final int MAX_BOARDS = 8;

    private VillagerHandler() {}

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if ((villager.tickCount + villager.getId()) % 10 != 0) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(TAG_ORIGIN)) {
            data.putLong(TAG_ORIGIN, villager.blockPosition().asLong());
        }
        long now = level.getGameTime();

        detectMimic(level, villager, now);

        if (HideManager.isVillagerHidden(villager)) {
            tickHiddenVillager(level, villager, data, now);
            return;
        }
        if (data.getBoolean(HideManager.TAG_VILLAGER_HIDDEN)) {
            // Resto de una partida anterior: el estado en memoria se perdió y seguiría invisible y sin IA.
            HideManager.unhideVillager(villager);
        }

        boolean alarmed = now < data.getLong(TAG_ALARM);
        Brain<Villager> brain = villager.getBrain();
        BlockPos origin = BlockPos.of(data.getLong(TAG_ORIGIN));

        fixPanic(villager, brain, data, origin);

        if (alarmed) {
            seekShelter(level, villager, brain, data, origin);
        } else if ((villager.tickCount + villager.getId()) % 20 == 0
                && now - data.getLong(TAG_THREAT) > CALM_TICKS) {
            // Pasó el peligro: abren ellos mismos las barricadas para poder salir (también de día).
            if (removeBoards(level, villager)) {
                data.putLong(TAG_UNBOARD, now);
            }
            data.putInt(TAG_BOARDS, 0);
        }
    }

    // ------------------------------------------------------------ sospecha y alerta

    private static void detectMimic(ServerLevel level, Villager villager, long now) {
        if ((villager.tickCount + villager.getId()) % 20 != 0) {
            return;
        }
        CompoundTag data = villager.getPersistentData();
        List<MimicEntity> mimics = level.getEntitiesOfClass(MimicEntity.class,
                villager.getBoundingBox().inflate(40.0, 12.0, 40.0), m -> m.isAlive() && m.isStrange());
        for (com.mimicer.entity.VoidEntity v : level.getEntitiesOfClass(com.mimicer.entity.VoidEntity.class,
                villager.getBoundingBox().inflate(20.0, 8.0, 20.0), e -> e.isAlive() && e.isAggressive())) {
            data.putLong(TAG_THREAT, now);
            raiseAlarm(level, villager, now);
            return;
        }
        if (mimics.isEmpty()) {
            // Sin amenaza cerca y de día la alerta se acaba: no se quedan encerrados ni vuelven a atrincherarse.
            if (level.isDay()) {
                data.putLong(TAG_ALARM, 0L);
            }
            return;
        }
        data.putLong(TAG_THREAT, now);
        for (MimicEntity mimic : mimics) {
            if (villager.distanceToSqr(mimic) < 16.0 * 16.0 && villager.hasLineOfSight(mimic)) {
                raiseAlarm(level, villager, now);
                return;
            }
        }
    }

    private static void raiseAlarm(ServerLevel level, Villager source, long now) {
        boolean fresh = now >= source.getPersistentData().getLong(TAG_ALARM);
        List<Villager> neighbours = level.getEntitiesOfClass(Villager.class,
                source.getBoundingBox().inflate(32.0, 10.0, 32.0));
        for (Villager v : neighbours) {
            long until = now + ALARM_TICKS;
            if (v.getPersistentData().getLong(TAG_ALARM) < until) {
                v.getPersistentData().putLong(TAG_ALARM, until);
            }
        }
        if (fresh) {
            level.playSound(null, source.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, 0.8F);
        }
    }

    // ------------------------------------------------------------ huida acotada

    /** Evita que el pánico de vainilla los haga correr indefinidamente o alejarse de la aldea. */
    private static void fixPanic(Villager villager, Brain<Villager> brain, CompoundTag data, BlockPos origin) {
        int panic = data.getInt(TAG_PANIC);
        boolean panicking = brain.isActive(Activity.PANIC);
        double distOrigin = Math.sqrt(villager.blockPosition().distSqr(origin));
        if (panicking) {
            panic += 10;
        } else {
            panic = 0;
        }
        data.putInt(TAG_PANIC, panic);
        if (panicking && (panic > MAX_PANIC_TICKS || distOrigin > MAX_ORIGIN_DISTANCE)) {
            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            data.putInt(TAG_PANIC, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(origin, 0.8F, 2));
        } else if (distOrigin > MAX_ORIGIN_DISTANCE * 1.5) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(origin, 0.8F, 2));
        }
    }

    // ------------------------------------------------------------ escondido bajo la cama

    /** Mientras está escondido: quieto bajo la cama hasta que pase el peligro, rompan la cama o lo hieran. */
    private static void tickHiddenVillager(ServerLevel level, Villager villager, CompoundTag data, long now) {
        if (HideManager.isExtracting(villager.getUUID())) {
            return;       // un monstruo lo está sacando de la cama: la escena controla su posición
        }
        BlockPos bed = HideManager.villagerBed(villager);
        boolean closet = HideManager.villagerHiddenInCloset(villager);
        boolean bedOk = bed != null && HideManager.isSpotValid(level, bed, closet);
        boolean calm = now - data.getLong(TAG_THREAT) > CALM_TICKS;
        if (!bedOk || calm || villager.hurtTime > 0) {
            HideManager.unhideVillager(villager);
            return;
        }
        double bx = bed.getX() + 0.5;
        double by = bed.getY() + (closet ? 0.07 : 0.02);
        double bz = bed.getZ() + 0.5;
        if (villager.distanceToSqr(bx, by, bz) > 0.25) {
            villager.moveTo(bx, by, bz, villager.getYRot(), 0.0F);
        }
    }

    // ------------------------------------------------------------ refugio y barricadas

    private static void seekShelter(ServerLevel level, Villager villager, Brain<Villager> brain,
                                    CompoundTag data, BlockPos origin) {
        // Un aldeano dormido ya está a salvo en su cama: no se le toca el cerebro ni cierra puertas.
        if (villager.isSleeping()) {
            return;
        }
        if (seekCloset(level, villager, brain, data)) {
            return;
        }
        BlockPos shelter = findShelter(level, villager, brain, origin);
        double dist = Math.sqrt(villager.blockPosition().distSqr(shelter));
        if (dist > 2.5) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(shelter, 0.9F, 1));
            return;
        }
        // Ya está a salvo: se queda quieto, cierra puertas y se atrinchera.
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(shelter.above()));
        long now = level.getGameTime();
        boolean recentThreat = now - data.getLong(TAG_THREAT) < THREAT_RECENT_TICKS;
        // Solo cierran puertas con una amenaza reciente; si no, la puerta se queda como esté.
        if (recentThreat) {
            closeDoors(level, villager);
        }
        boolean justUnboarded = now - data.getLong(TAG_UNBOARD) < CALM_TICKS * 2L;
        if (recentThreat && !justUnboarded && data.getInt(TAG_BOARDS) < MAX_BOARDS
                && villager.getRandom().nextFloat() < 0.6F) {
            if (placeBoard(level, villager)) {
                data.putInt(TAG_BOARDS, data.getInt(TAG_BOARDS) + 1);
            }
        }
        // Tras atrincherarse (o si no hay nada que tapiar) se mete bajo su cama, si no la ocupa ya alguien.
        if (recentThreat && level.getBlockState(shelter).getBlock() instanceof BedBlock
                && (data.getInt(TAG_BOARDS) >= 2 || villager.getRandom().nextFloat() < 0.15F)) {
            HideManager.startHidingVillager(villager, shelter);
        }
    }

    private static final String TAG_CLOSET = "mimicer_closet";
    private static final String TAG_CLOSET_ALARM = "mimicer_closet_alarm";

    /** Con una amenaza reciente, algunos aldeanos corren a un armario cercano y se esconden dentro. */
    private static boolean seekCloset(ServerLevel level, Villager villager, Brain<Villager> brain, CompoundTag data) {
        if (!com.mimicer.config.ClosetConfig.VILLAGERS_HIDE.get() || !com.mimicer.config.ClosetConfig.ENABLED.get()) {
            return false;
        }
        long now = level.getGameTime();
        if (now - data.getLong(TAG_THREAT) >= THREAT_RECENT_TICKS) {
            return false;
        }
        long alarm = now / 1200L;
        BlockPos closet = null;
        if (data.contains(TAG_CLOSET) && data.getLong(TAG_CLOSET_ALARM) == alarm) {
            closet = BlockPos.of(data.getLong(TAG_CLOSET));
        } else if (data.getLong(TAG_CLOSET_ALARM) != alarm) {
            data.putLong(TAG_CLOSET_ALARM, alarm);
            data.remove(TAG_CLOSET);
            if (villager.getRandom().nextFloat() < com.mimicer.config.ClosetConfig.VILLAGER_CLOSET_CHANCE.get()) {
                for (BlockPos c : com.mimicer.hide.ClosetIndex.near(level, villager.blockPosition(), 10, 3)) {
                    if (!HideManager.isSpotTaken(level, c) && !com.mimicer.hide.ClosetIndex.isBusy(level, c)) {
                        closet = c;
                        data.putLong(TAG_CLOSET, c.asLong());
                        break;
                    }
                }
            }
        }
        if (closet == null || !com.mimicer.block.WardrobeBlock.isWardrobe(level, closet)
                || HideManager.isSpotTaken(level, closet)) {
            data.remove(TAG_CLOSET);
            return false;
        }
        net.minecraft.world.phys.Vec3 front = com.mimicer.block.WardrobeBlock.frontPoint(level, closet, 1.4);
        if (villager.distanceToSqr(front) > 4.0) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(front, 0.95F, 1));
            return true;
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        if (HideManager.startHidingVillagerCloset(villager, closet)) {
            data.remove(TAG_CLOSET);
        } else {
            data.remove(TAG_CLOSET);
            return false;
        }
        return true;
    }

    private static BlockPos findShelter(ServerLevel level, Villager villager, Brain<Villager> brain, BlockPos origin) {
        Optional<GlobalPos> home = brain.getMemory(MemoryModuleType.HOME);
        if (home.isPresent() && home.get().dimension() == level.dimension()
                && home.get().pos().distSqr(villager.blockPosition()) < 64.0 * 64.0) {
            return home.get().pos();
        }
        BlockPos from = villager.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-16, -4, -16), from.offset(16, 4, 16))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
                    && !level.canSeeSky(pos)) {
                double d = pos.distSqr(from);
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.immutable();
                }
            }
        }
        return best != null ? best : origin;
    }

    /** Hay un aldeano alarmado fuera (a la vista del cielo) intentando refugiarse cerca: no hay que cerrarle la puerta. */
    private static boolean outsiderNear(ServerLevel level, BlockPos center, double radius, Villager self) {
        long now = level.getGameTime();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(radius), x -> x != self)) {
            if (v.isSleeping() || !level.canSeeSky(v.blockPosition())) {
                continue;
            }
            if (now < v.getPersistentData().getLong(TAG_ALARM)) {
                return true;
            }
        }
        return false;
    }

    private static void closeDoors(ServerLevel level, Villager villager) {
        if (outsiderNear(level, villager.blockPosition(), 9.0, villager)) {
            return;
        }
        BlockPos from = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-4, -1, -4), from.offset(4, 2, 4))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && state.is(BlockTags.WOODEN_DOORS)
                    && state.getValue(DoorBlock.OPEN) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                door.setOpen(villager, level, state, pos, false);
            }
        }
    }

    /** Coloca una Tabla en el lado interior de una puerta o ventana cercana. Devuelve si colocó alguna. */
    private static boolean placeBoard(ServerLevel level, Villager villager) {
        if (outsiderNear(level, villager.blockPosition(), 12.0, villager)) {
            return false;
        }
        BlockPos from = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-5, -1, -5), from.offset(5, 3, 5))) {
            BlockState state = level.getBlockState(pos);
            boolean door = state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            boolean window = state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES);
            if (!door && !window) {
                continue;
            }
            Direction inward = Direction.getNearest(from.getX() - pos.getX(), 0.0, from.getZ() - pos.getZ());
            if (!inward.getAxis().isHorizontal()) {
                continue;
            }
            BlockPos cell = pos.relative(inward);
            BlockPos outer = pos.relative(inward.getOpposite());
            if (!level.getBlockState(cell).isAir() || level.canSeeSky(cell)
                    || !(level.getBlockState(outer).isAir() || level.canSeeSky(outer))) {
                continue;
            }
            BlockState board = ModBlocks.BOARD.get().defaultBlockState().setValue(BoardBlock.FACING, inward);
            level.setBlock(cell, board, 3);
            if (door && level.getBlockState(cell.above()).isAir()) {
                level.setBlock(cell.above(), board, 3);
            }
            level.playSound(null, cell, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            return true;
        }
        return false;
    }

    private static boolean removeBoards(ServerLevel level, Villager villager) {
        boolean removed = false;
        BlockPos from = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-6, -1, -6), from.offset(6, 3, 6))) {
            if (level.getBlockState(pos).getBlock() instanceof BoardBlock) {
                level.destroyBlock(pos.immutable(), true);
                removed = true;
            }
        }
        return removed;
    }
}
