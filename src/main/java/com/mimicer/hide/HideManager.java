package com.mimicer.hide;

import com.mimicer.entity.MimicEntity;
import com.mimicer.net.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de jugadores y aldeanos escondidos bajo una cama. Es la única fuente de verdad en el servidor;
 * el cliente solo recibe un paquete para su propio jugador.
 */
public final class HideManager {
    public static final float REVEAL_THRESHOLD = 100.0F;
    /** Marca en el NBT del aldeano escondido, para limpiar restos si el mundo se recarga. */
    public static final String TAG_VILLAGER_HIDDEN = "mimicer_hidden";

    private static final class HideData {
        final BlockPos bed;
        float suspicion;
        /** Mímicos que te vieron meterte bajo la cama: solo ellos pueden descubrirte o romper la cama. */
        final Set<UUID> seenBy = new HashSet<>();

        HideData(BlockPos bed) {
            this.bed = bed;
        }
    }

    private static final Map<UUID, HideData> HIDDEN = new ConcurrentHashMap<>();
    private static final Map<UUID, HideData> V_HIDDEN = new ConcurrentHashMap<>();

    /** Víctimas que un monstruo está sacando de la cama ahora mismo: víctima -> monstruo. */
    private static final Map<UUID, UUID> EXTRACTING = new ConcurrentHashMap<>();

    public record Hidden(net.minecraft.world.entity.LivingEntity victim, BlockPos bed) {}

    private HideManager() {}

    public static boolean claimExtraction(UUID victim, UUID monster) {
        return EXTRACTING.putIfAbsent(victim, monster) == null;
    }

    public static void releaseExtraction(UUID victim, UUID monster) {
        EXTRACTING.remove(victim, monster);
    }

    public static boolean isExtracting(UUID victim) {
        return EXTRACTING.containsKey(victim);
    }

    public static boolean isHiddenVictim(net.minecraft.world.entity.LivingEntity e) {
        if (e instanceof Player p) {
            return isHidden(p);
        }
        return e instanceof Villager v && isVillagerHidden(v);
    }

    /** Escondido bajo una cama, visto por este monstruo y no reservado por otro. */
    public static Hidden findSeenBy(ServerLevel level, UUID monster, Vec3 pos, double range) {
        if (HIDDEN.isEmpty() && V_HIDDEN.isEmpty()) {
            return null;
        }
        for (Map.Entry<UUID, HideData> entry : HIDDEN.entrySet()) {
            Player p = level.getPlayerByUUID(entry.getKey());
            if (p != null && entry.getValue().seenBy.contains(monster) && !EXTRACTING.containsKey(entry.getKey())
                    && p.position().distanceToSqr(pos) < range * range) {
                return new Hidden(p, entry.getValue().bed);
            }
        }
        for (Map.Entry<UUID, HideData> entry : V_HIDDEN.entrySet()) {
            Entity e = level.getEntity(entry.getKey());
            if (e instanceof Villager v && v.isAlive() && entry.getValue().seenBy.contains(monster)
                    && !EXTRACTING.containsKey(entry.getKey()) && v.position().distanceToSqr(pos) < range * range) {
                return new Hidden(v, entry.getValue().bed);
            }
        }
        return null;
    }

    public static boolean isHidden(Player player) {
        return HIDDEN.containsKey(player.getUUID());
    }

    public static BlockPos getBed(Player player) {
        HideData data = HIDDEN.get(player.getUUID());
        return data == null ? null : data.bed;
    }

    // ------------------------------------------------------------ cliente

    public static void clientSet(Player player, boolean hidden, BlockPos bed) {
        if (hidden) {
            HIDDEN.put(player.getUUID(), new HideData(bed));
        } else {
            HIDDEN.remove(player.getUUID());
        }
        player.refreshDimensions();
    }

    // ------------------------------------------------------------ jugadores (servidor)

    public static void startHiding(ServerPlayer player, BlockPos bed) {
        HideData data = new HideData(bed);
        addWitnesses(data, player.level(), player);
        HIDDEN.put(player.getUUID(), data);
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(bed.getX() + 0.5, bed.getY() + 0.02, bed.getZ() + 0.5);
        player.refreshDimensions();
        player.level().playSound(null, bed, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.6F, 0.7F);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NetworkHandler.HideStatePacket(true, bed));
        // Si lo vieron, van derechos a la cama; si no, solo un ruido mínimo.
        NoiseManager.emit(player.level(), player.position(), data.seenBy.isEmpty() ? 5.0F : 40.0F);
    }

    public static void stopHiding(ServerPlayer player) {
        if (HIDDEN.remove(player.getUUID()) == null) {
            return;
        }
        player.setInvisible(false);
        player.refreshDimensions();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NetworkHandler.HideStatePacket(false, BlockPos.ZERO));
    }

    /** Cada tick de servidor mientras el jugador está escondido. */
    public static void serverTick(ServerPlayer player) {
        HideData data = HIDDEN.get(player.getUUID());
        if (data == null) {
            return;
        }
        if (!player.isAlive() || !(player.level().getBlockState(data.bed).getBlock() instanceof BedBlock)) {
            stopHiding(player);
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        double bx = data.bed.getX() + 0.5;
        double by = data.bed.getY() + 0.02;
        double bz = data.bed.getZ() + 0.5;
        // Solo recoloca si algo lo movió: teleportar cada tick provoca tirones en el cliente.
        if (!EXTRACTING.containsKey(player.getUUID()) && player.distanceToSqr(bx, by, bz) > 0.09) {
            player.teleportTo(bx, by, bz);
        }
        data.suspicion = Math.max(0.0F, data.suspicion - 0.05F);
        // Respirar y moverse bajo la cama hace un ruido pequeño de vez en cuando.
        if (player.tickCount % 80 == 0 && player.getRandom().nextFloat() < 0.3F) {
            NoiseManager.emit(player.level(), player.position(), 6.0F);
        }
    }

    public static void reveal(ServerPlayer player, MimicEntity mimic) {
        stopHiding(player);
        mimic.setTarget(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
                SoundSource.HOSTILE, 1.5F, 0.3F);
        player.level().playSound(null, player.blockPosition(), com.mimicer.registry.ModSounds.MIMIC_SCREAM.get(),
                SoundSource.HOSTILE, 1.5F, 1.1F);
    }

    public static void forget(UUID id) {
        HIDDEN.remove(id);
    }

    // ------------------------------------------------------------ aldeanos (servidor)

    public static boolean isVillagerHidden(Villager villager) {
        return V_HIDDEN.containsKey(villager.getUUID());
    }

    public static BlockPos villagerBed(Villager villager) {
        HideData data = V_HIDDEN.get(villager.getUUID());
        return data == null ? null : data.bed;
    }

    /** Mete al aldeano bajo la cama: invisible, quieto y sin IA hasta que pase el peligro o lo descubran. */
    public static boolean startHidingVillager(Villager villager, BlockPos bed) {
        Level level = villager.level();
        if (level.isClientSide || !(level.getBlockState(bed).getBlock() instanceof BedBlock)
                || isBedTaken(level, bed) || isVillagerHidden(villager)) {
            return false;
        }
        HideData data = new HideData(bed);
        addWitnesses(data, level, villager);
        V_HIDDEN.put(villager.getUUID(), data);
        villager.getPersistentData().putBoolean(TAG_VILLAGER_HIDDEN, true);
        villager.getNavigation().stop();
        villager.setNoAi(true);
        villager.setInvisible(true);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.moveTo(bed.getX() + 0.5, bed.getY() + 0.02, bed.getZ() + 0.5, villager.getYRot(), 0.0F);
        level.playSound(null, bed, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.6F, 0.8F);
        NoiseManager.emit(level, villager.position(), data.seenBy.isEmpty() ? 4.0F : 40.0F);
        return true;
    }

    /** Lo saca de debajo de la cama (peligro pasado, cama rota, herido o descubierto). */
    public static void unhideVillager(Villager villager) {
        V_HIDDEN.remove(villager.getUUID());
        villager.getPersistentData().remove(TAG_VILLAGER_HIDDEN);
        villager.setInvisible(false);
        villager.setNoAi(false);
    }

    public static void revealVillager(Villager villager, MimicEntity mimic) {
        unhideVillager(villager);
        mimic.setTarget(villager);
        villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_HURT,
                SoundSource.NEUTRAL, 1.2F, 0.8F);
    }

    // ------------------------------------------------------------ comunes

    /** Quién lo vio esconderse: los que lo persiguen o tienen línea de visión justo ahora (antes de moverlo). */
    private static void addWitnesses(HideData data, Level level, net.minecraft.world.entity.LivingEntity hider) {
        for (com.mimicer.entity.VoidEntity v : level.getEntitiesOfClass(com.mimicer.entity.VoidEntity.class,
                hider.getBoundingBox().inflate(32.0, 10.0, 32.0), x -> x.isAlive())) {
            if (v.getTarget() == hider || v.hasLineOfSight(hider)) {
                data.seenBy.add(v.getUUID());
            }
        }
        for (MimicEntity m : level.getEntitiesOfClass(MimicEntity.class,
                hider.getBoundingBox().inflate(32.0, 10.0, 32.0), x -> x.isAlive() && x.isMonsterForm())) {
            if (m.getTarget() == hider || m.hasLineOfSight(hider)) {
                data.seenBy.add(m.getUUID());
            }
        }
    }

    /** ¿Ya hay alguien (jugador o aldeano) escondido bajo esta cama? */
    public static boolean isBedTaken(Level level, BlockPos pos) {
        BlockPos other = otherHalf(level, pos);
        for (HideData d : HIDDEN.values()) {
            if (d.bed.equals(pos) || d.bed.equals(other)) {
                return true;
            }
        }
        for (HideData d : V_HIDDEN.values()) {
            if (d.bed.equals(pos) || d.bed.equals(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Llamado desde el Mímico en forma monstruosa: acumula sospecha sobre quien se escondió bajo una cama
     * (jugador o aldeano), pero solo si él lo vio esconderse.
     */
    public static void mimicSense(MimicEntity mimic) {
        if (mimic.tickCount % 2 != 0 || (HIDDEN.isEmpty() && V_HIDDEN.isEmpty())
                || (mimic.isBusyWithScene() && !isExtractingBy(mimic))) {
            return;
        }
        for (Map.Entry<UUID, HideData> entry : HIDDEN.entrySet()) {
            Player p = mimic.level().getPlayerByUUID(entry.getKey());
            if (!(p instanceof ServerPlayer player) || player.level() != mimic.level()) {
                continue;
            }
            HideData data = entry.getValue();
            int result = sense(mimic, data, player.position());
            if (result == 2) {
                breakBed(mimic, data);
                reveal(player, mimic);
                return;
            }
            if (result == 1) {
                if (!mimic.requestExtraction(player, data.bed)) {
                    reveal(player, mimic);
                }
                return;
            }
        }
        if (!(mimic.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Map.Entry<UUID, HideData> entry : V_HIDDEN.entrySet()) {
            Entity e = serverLevel.getEntity(entry.getKey());
            if (!(e instanceof Villager villager)) {
                continue;
            }
            if (!villager.isAlive()) {
                V_HIDDEN.remove(entry.getKey());
                continue;
            }
            HideData data = entry.getValue();
            int result = sense(mimic, data, villager.position());
            if (result == 2) {
                breakBed(mimic, data);
                revealVillager(villager, mimic);
                return;
            }
            if (result == 1) {
                if (!mimic.requestExtraction(villager, data.bed)) {
                    revealVillager(villager, mimic);
                }
                return;
            }
        }
    }

    /**
     * 0 = nada, 1 = lo descubre, 2 = rompe la cama para sacarlo.
     * Un Mímico que no lo vio esconderse no sabe que está ahí.
     */
    private static boolean isExtractingBy(MimicEntity mimic) {
        return EXTRACTING.containsValue(mimic.getUUID());
    }

    private static int sense(MimicEntity mimic, HideData data, Vec3 victimPos) {
        if (!data.seenBy.contains(mimic.getUUID())) {
            return 0;
        }
        // Solo cuenta si el Mímico está en la misma habitación que la cama: cerca de alguna de sus dos mitades
        // y con una línea directa hasta ella. Estar al otro lado de una pared no sirve de nada.
        BlockPos other = otherHalf(mimic.level(), data.bed);
        double dist = Math.min(mimic.position().distanceTo(Vec3.atCenterOf(data.bed)),
                mimic.position().distanceTo(Vec3.atCenterOf(other)));
        if (dist > 4.0 || !canSeeBed(mimic, data.bed, other)) {
            return 0;
        }
        boolean adjacent = dist < 2.2;
        float add = adjacent ? 2.5F : 0.6F;
        if (NoiseManager.recentNoiseNear(mimic.level(), victimPos, 8.0, 60)) {
            add += adjacent ? 6.0F : 1.5F;
        }
        data.suspicion += add;
        if (data.suspicion >= REVEAL_THRESHOLD) {
            return 1;
        }
        // Si sospecha pero no se acerca, rompe la cama para sacarlo.
        if (!adjacent && data.suspicion >= REVEAL_THRESHOLD * 0.5F && mimic.getRandom().nextInt(20) == 0) {
            return 2;
        }
        return 0;
    }

    private static void breakBed(MimicEntity mimic, HideData data) {
        mimic.swing(InteractionHand.MAIN_HAND);
        mimic.level().destroyBlock(data.bed, true, mimic);
    }

    /** La otra mitad de la cama (la cabecera o los pies), tanto si se pulsó una como la otra. */
    public static BlockPos otherBedHalf(Level level, BlockPos pos) {
        return otherHalf(level, pos);
    }

    private static BlockPos otherHalf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return pos;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos.relative(facing);
    }

    /** Línea de visión desde los ojos del Mímico hasta alguna mitad de la cama, sin paredes ni cristales de por medio. */
    private static boolean canSeeBed(MimicEntity mimic, BlockPos a, BlockPos b) {
        Vec3 eye = mimic.getEyePosition();
        for (BlockPos p : new BlockPos[]{a, b}) {
            Vec3 target = new Vec3(p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5);
            BlockHitResult hit = mimic.level().clip(new ClipContext(eye, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mimic));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(a) || hit.getBlockPos().equals(b)) {
                return true;
            }
        }
        return false;
    }
}
