package com.mimicer.hide;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Registro ligero de ruidos recientes que el Mímico puede ir a investigar. */
public final class NoiseManager {
    public static final int MAX_AGE = 400;
    private static final int MAX_NOISES = 64;

    public record Noise(Level level, Vec3 pos, float radius, long time) {}

    private static final List<Noise> NOISES = new ArrayList<>();

    private NoiseManager() {}

    public static synchronized void emit(Level level, Vec3 pos, float radius) {
        if (level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        purge(now);
        if (NOISES.size() >= MAX_NOISES) {
            NOISES.remove(0);
        }
        NOISES.add(new Noise(level, pos, radius, now));
    }

    /** Ruido más reciente que llega hasta {@code from}, o null. */
    public static synchronized Noise findHeard(Level level, Vec3 from) {
        long now = level.getGameTime();
        purge(now);
        Noise best = null;
        for (Noise n : NOISES) {
            if (n.level() != level) {
                continue;
            }
            if (n.pos().distanceToSqr(from) <= (double) n.radius() * n.radius()) {
                if (best == null || n.time() > best.time()) {
                    best = n;
                }
            }
        }
        return best;
    }

    public static synchronized boolean recentNoiseNear(Level level, Vec3 pos, double radius, int maxAgeTicks) {
        long now = level.getGameTime();
        for (Noise n : NOISES) {
            if (n.level() == level && now - n.time() <= maxAgeTicks
                    && n.pos().distanceToSqr(pos) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static void purge(long now) {
        Iterator<Noise> it = NOISES.iterator();
        while (it.hasNext()) {
            Noise n = it.next();
            if (now - n.time() > MAX_AGE || now < n.time()) {
                it.remove();
            }
        }
    }
}
