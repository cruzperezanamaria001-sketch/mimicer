package com.mimicer.net;

import com.mimicer.MimicerMod;
import com.mimicer.client.ClientHooks;
import com.mimicer.hide.HideManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MimicerMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private NetworkHandler() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, HideStatePacket.class, HideStatePacket::encode,
                HideStatePacket::decode, HideStatePacket::handle);
        CHANNEL.registerMessage(id++, GlitchScarePacket.class, GlitchScarePacket::encode,
                GlitchScarePacket::decode, GlitchScarePacket::handle);
        CHANNEL.registerMessage(id++, StopHidePacket.class, StopHidePacket::encode,
                StopHidePacket::decode, StopHidePacket::handle);
    }

    /** Servidor a cliente: el jugador entra o sale del escondite. */
    public static class HideStatePacket {
        private final boolean hidden;
        private final BlockPos bed;

        public HideStatePacket(boolean hidden, BlockPos bed) {
            this.hidden = hidden;
            this.bed = bed;
        }

        public static void encode(HideStatePacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.hidden);
            buf.writeBlockPos(msg.bed);
        }

        public static HideStatePacket decode(FriendlyByteBuf buf) {
            return new HideStatePacket(buf.readBoolean(), buf.readBlockPos());
        }

        public static void handle(HideStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.setHidden(msg.hidden, msg.bed)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente a servidor: el jugador quiere salir del escondite (tecla de salto). */
    public static class StopHidePacket {
        public static void encode(StopHidePacket msg, FriendlyByteBuf buf) {}

        public static StopHidePacket decode(FriendlyByteBuf buf) {
            return new StopHidePacket();
        }

        public static void handle(StopHidePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    HideManager.stopHiding(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor a cliente: distorsión de pantalla del jumpscare de The Glitch. */
    public static class GlitchScarePacket {
        private final int ticks;

        public GlitchScarePacket(int ticks) {
            this.ticks = ticks;
        }

        public static void encode(GlitchScarePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.ticks);
        }

        public static GlitchScarePacket decode(FriendlyByteBuf buf) {
            return new GlitchScarePacket(buf.readVarInt());
        }

        public static void handle(GlitchScarePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.mimicer.client.GlitchOverlay.start(msg.ticks)));
            ctx.get().setPacketHandled(true);
        }
    }
}
