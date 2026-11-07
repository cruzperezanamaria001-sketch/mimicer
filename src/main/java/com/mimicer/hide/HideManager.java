package com.mimicer.hide;

import com.mimicer.entity.MimicEntity;
import com.mimicer.net.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
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
        HIDDEN.put(player.getUUID(), new HideData(bed));
        player.setInvisible(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(bed.getX() + 0.5, bed.getY() + 0.02, bed.getZ() + 0.5);
        player.refreshDimensions();
        player.level().playSound(null, bed, SoundEvents.WOOL_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.7F);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NetworkHandler.HideStatePacket(true, bed));
        NoiseManager.emit(player.level(), player.position(), 5.0F);
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
        if (!player.isAlive() || player.level().isDay()
                || !(player.level().getBlockState(data.bed).getBlock() instanceof BedBlock)) {
            stopHiding(player);
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(data.bed.getX() + 0.5, data.bed.getY() + 0.02, data.bed.getZ() + 0.5);
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
            double dist = mimic.position().distanceTo(Vec3.atCenterOf(data.bed));
            if (dist > 4.5) {
                continue;
            }
            float add = dist < 2.0 ? 2.5F : 1.2F;
            if (NoiseManager.recentNoiseNear(mimic.level(), player.position(), 8.0, 60)) {
                add += 6.0F;
            }
            data.suspicion += add;
            if (data.suspicion >= REVEAL_THRESHOLD) {
                reveal(player, mimic);
                return;
            }
        }
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
