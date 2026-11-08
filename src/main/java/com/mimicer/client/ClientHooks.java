package com.mimicer.client;

import com.mimicer.hide.HideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Puntos de entrada de código exclusivo del cliente llamados desde paquetes. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void setHidden(boolean hidden, BlockPos bed, boolean closet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HideManager.clientSet(player, hidden, bed, closet);
        if (hidden) {
            player.setDeltaMovement(Vec3.ZERO);
            player.setPos(bed.getX() + 0.5, bed.getY() + (closet ? 0.07 : 0.02), bed.getZ() + 0.5);
        }
    }
}
