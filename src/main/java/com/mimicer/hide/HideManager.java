package com.mimicer.hide;

import com.mimicer.entity.MimicEntity;
import com.mimicer.net.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de los jugadores escondidos bajo una cama. Es la única fuente de verdad en el servidor;
 * el cliente solo recibe un paquete para su propio jugador.
 */
public final class HideManager {
    public static final float REVEAL_THRESHOLD = 100.0F;

    private static final class HideData {
        final BlockPos bed;
        float suspicion;
        /** Mímicos que te vieron meterte bajo la cama: solo ellos pueden descubrirte o romper la cama. */
        final java.util.Set<UUID> seenBy = new java.util.HashSet<>();

        HideData(BlockPos bed) {
            this.bed = bed;
        }
    }

    private static final Map<UUID, HideData> HIDDEN = new ConcurrentHashMap<>();

    private HideManager() {}

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

    // ------------------------------------------------------------ servidor

    public static void startHiding(ServerPlayer player, BlockPos bed) {
        HideData data = new HideData(bed);
        // Quién lo vio esconderse: los que lo persiguen o tienen línea de visión justo ahora (antes de moverlo).
        for (MimicEntity m : player.level().getEntitiesOfClass(MimicEntity.class,
                player.getBoundingBox().inflate(32.0, 10.0, 32.0), x -> x.isAlive() && x.isMonsterForm())) {
            if (m.getTarget() == player || m.hasLineOfSight(player)) {
                data.seenBy.add(m.getUUID());
            }
        }
        HIDDEN.put(player.getUUID(), data);
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(bed.getX() + 0.5, bed.getY() + 0.02, bed.getZ() + 0.5);
        player.refreshDimensions();
        player.level().playSound(null, bed, SoundEvents.WOOL_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.7F);
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
        if (player.distanceToSqr(bx, by, bz) > 0.09) {
            player.teleportTo(bx, by, bz);
        }
        data.suspicion = Math.max(0.0F, data.suspicion - 0.05F);
        // Respirar y moverse bajo la cama hace un ruido pequeño de vez en cuando.
        if (player.tickCount % 80 == 0 && player.getRandom().nextFloat() < 0.3F) {
            NoiseManager.emit(player.level(), player.position(), 6.0F);
        }
    }

    /** Llamado desde el Mímico en forma monstruosa: acumula sospecha si está cerca de una cama ocupada. */
    public static void mimicSense(MimicEntity mimic) {
        if (mimic.tickCount % 2 != 0 || HIDDEN.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, HideData> entry : HIDDEN.entrySet()) {
            Player p = mimic.level().getPlayerByUUID(entry.getKey());
            if (!(p instanceof ServerPlayer player) || player.level() != mimic.level()) {
                continue;
            }
            HideData data = entry.getValue();
            // Un Mímico que no te vio esconderte no sabe que estás ahí: ni te descubre ni rompe la cama.
            if (!data.seenBy.contains(mimic.getUUID())) {
                continue;
            }
            // Solo cuenta si el Mímico está en la misma habitación que la cama: cerca de alguna de sus dos mitades
            // y con una línea directa hasta ella. Estar al otro lado de una pared no sirve de nada.
            BlockPos other = otherHalf(mimic.level(), data.bed);
            double dist = Math.min(mimic.position().distanceTo(Vec3.atCenterOf(data.bed)),
                    mimic.position().distanceTo(Vec3.atCenterOf(other)));
            if (dist > 4.0 || !canSeeBed(mimic, data.bed, other)) {
                continue;
            }
            boolean adjacent = dist < 2.2;
            float add = adjacent ? 2.5F : 0.6F;
            if (NoiseManager.recentNoiseNear(mimic.level(), player.position(), 8.0, 60)) {
                add += adjacent ? 6.0F : 1.5F;
            }
            data.suspicion += add;
            if (data.suspicion >= REVEAL_THRESHOLD) {
                reveal(player, mimic);
                return;
            }
            // Si sospecha pero no se acerca, rompe la cama para sacarlo.
            if (!adjacent && data.suspicion >= REVEAL_THRESHOLD * 0.5F && mimic.getRandom().nextInt(20) == 0) {
                mimic.swing(InteractionHand.MAIN_HAND);
                mimic.level().destroyBlock(data.bed, true, mimic);
                reveal(player, mimic);
                return;
            }
        }
    }

    /** La otra mitad de la cama (la cabecera o los pies), tanto si se pulsó una como la otra. */
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

    public static void reveal(ServerPlayer player, MimicEntity mimic) {
        stopHiding(player);
        mimic.setTarget(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.3F);
    }

    public static void forget(UUID id) {
        HIDDEN.remove(id);
    }
}
