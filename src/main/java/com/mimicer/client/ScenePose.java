package com.mimicer.client;

import com.mimicer.entity.scene.SceneTiming;
import net.minecraft.util.Mth;

/** Fases de las escenas de alimentación y extracción, calculadas a partir del tiempo de escena (en ticks). */
public final class ScenePose {
    private ScenePose() {}

    public static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** kneel: agachado 0..1; down: cabeza hacia el cuerpo; jaw: mandíbula; pull: tirón hacia arriba; chew: masticar. */
    public record Feed(float kneel, float down, float jaw, float pull, float chew, float reach) {}

    public static Feed feed(float t) {
        float kneel = smooth(t / SceneTiming.FEED_KNEEL);
        if (t < SceneTiming.FEED_KNEEL) {
            return new Feed(kneel, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        }
        float c = (t - SceneTiming.FEED_KNEEL) % SceneTiming.FEED_CYCLE;
        float down;
        float jaw;
        float pull = 0.0F;
        float chew = 0.0F;
        float reach;
        if (c < 16.0F) {                       // baja la cabeza y abre la mandíbula
            float k = smooth(c / 16.0F);
            down = k;
            jaw = k;
            reach = k;
        } else if (c < 22.0F) {                // mordisco: la mandíbula se cierra de golpe
            float k = (c - 16.0F) / 6.0F;
            down = 1.0F;
            jaw = 1.0F - k * k;
            reach = 1.0F;
        } else if (c < 30.0F) {                // tira hacia arriba sacudiendo la cabeza
            float k = smooth((c - 22.0F) / 8.0F);
            down = 1.0F - k * 0.6F;
            jaw = 0.0F;
            pull = k;
            reach = 1.0F - k;
        } else {                               // mastica con la cabeza alzada
            float k = (c - 30.0F) / 26.0F;
            down = 0.4F * (1.0F - smooth(k * 1.5F));
            jaw = 0.0F;
            chew = Mth.sin(c * 1.1F) * (1.0F - k * 0.4F);
            reach = 0.0F;
            pull = Math.max(0.0F, 1.0F - k * 2.0F);
        }
        return new Feed(kneel, down, jaw, pull, chew, reach);
    }

    /** kneel: agachado; search: brazo bajo la cama; grab: ya agarra; drag: avance del arrastre; rock: tirón rítmico. */
    public record Extract(float kneel, float search, float grab, float drag, float rock) {}

    public static Extract extract(float t, SceneTiming.Kind kind) {
        float kneel = smooth(t / kind.kneel);
        float search = smooth((t - kind.kneel) / (float) (kind.grab - kind.kneel));
        float grab = t >= kind.grab ? 1.0F : 0.0F;
        float drag = Mth.clamp((t - kind.grab) / (float) (kind.end - kind.grab), 0.0F, 1.0F);
        float rock = t >= kind.grab ? Mth.sin((t - kind.grab) * 0.35F) : 0.0F;
        return new Extract(kneel, t >= kind.grab ? 1.0F : search, grab, drag, rock);
    }
}
