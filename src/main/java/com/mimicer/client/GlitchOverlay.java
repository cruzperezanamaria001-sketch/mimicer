package com.mimicer.client;

import com.mimicer.MimicerMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Distorsión de pantalla breve durante el jumpscare de The Glitch. */
@Mod.EventBusSubscriber(modid = MimicerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GlitchOverlay {
    private static int ticks;
    private static final Random RND = new Random();

    private GlitchOverlay() {}

    public static void start(int duration) {
        ticks = duration;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticks > 0) {
            ticks--;
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (ticks <= 0) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int strength = Math.min(ticks, 8);
        g.fill(0, 0, w, h, (0x10 + strength * 6) << 24 | 0x300050);
        for (int i = 0; i < 6 + strength * 2; i++) {
            int y = RND.nextInt(h);
            int sh = 2 + RND.nextInt(14);
            int x0 = RND.nextInt(w / 3);
            int x1 = w - RND.nextInt(w / 3);
            int color;
            switch (RND.nextInt(4)) {
                case 0 -> color = 0xA0B400FF;
                case 1 -> color = 0xC0000000;
                case 2 -> color = 0x70FF40FF;
                default -> color = 0x90200030;
            }
            g.fill(x0, y, x1, y + sh, color);
        }
    }
}
