package com.mimicer.hide;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Índice (solo servidor) de los armarios cargados y de quién los tiene reservados. Evita recorrer bloques para
 * buscar armarios y garantiza que dos monstruos no trabajan sobre el mismo armario a la vez.
 */
public final class ClosetIndex {
    private static final Map<ResourceKey<Level>, Set<Long>> CLOSETS = new ConcurrentHashMap<>();
    /** Armario (posición inferior, long) -> monstruo que lo está revisando. */
    private static final Map<Long, UUID> CLAIMS = new ConcurrentHashMap<>();

    private ClosetIndex() {}

    public static void add(Level level, BlockPos lower) {
        CLOSETS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(lower.asLong());
    }

    public static void remove(Level level, BlockPos lower) {
        Set<Long> set = CLOSETS.get(level.dimension());
        if (set != null) {
            set.remove(lower.asLong());
        }
        CLAIMS.remove(lower.asLong());
    }

    public static boolean claim(BlockPos lower, UUID monster) {
        return CLAIMS.putIfAbsent(lower.asLong(), monster) == null;
    }

    public static void release(BlockPos lower, UUID monster) {
        CLAIMS.remove(lower.asLong(), monster);
    }

    public static boolean isBusy(Level level, BlockPos lower) {
        return CLAIMS.containsKey(lower.asLong());
    }

    /** Armarios a menos de {@code range} bloques (horizontal) y {@code dy} en vertical, ordenados por cercanía. */
    public static List<BlockPos> near(Level level, BlockPos from, int range, int dy) {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> set = CLOSETS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return out;
        }
        long r2 = (long) range * range;
        for (long l : set) {
            BlockPos p = BlockPos.of(l);
            long dx = p.getX() - from.getX();
            long dz = p.getZ() - from.getZ();
            if (dx * dx + dz * dz <= r2 && Math.abs(p.getY() - from.getY()) <= dy) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from)));
        return out;
    }
}
