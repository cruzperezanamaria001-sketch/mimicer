package com.mimicer.client;

import com.mimicer.MimicerMod;
import com.mimicer.hide.HideManager;
import com.mimicer.net.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Control del jugador mientras está escondido bajo una cama. */
@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static int exitCooldown;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !HideManager.isHidden(player)) {
            return;
        }
        var input = event.getInput();
        // Saltar sirve para salir; el resto del movimiento queda bloqueado.
        if (input.jumping && exitCooldown <= 0) {
            exitCooldown = 10;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.StopHidePacket());
        }
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && HideManager.isHidden(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (exitCooldown > 0) {
            exitCooldown--;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && HideManager.isHidden(player)) {
            player.setDeltaMovement(0.0, 0.0, 0.0);
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(Component.translatable("message.mimicer.hiding"), true);
            }
        }
    }
}
