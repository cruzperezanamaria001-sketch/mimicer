package com.mimicer.util;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Utilidades para criaturas altas que se agachan para pasar por huecos de 2 bloques o menos. */
public final class Ducking {
    private Ducking() {}

    /** ¿Cabe la criatura con esas dimensiones en su posición actual? */
    public static boolean fits(Mob mob, EntityDimensions dims) {
        return mob.level().noCollision(mob, dims.makeBoundingBox(mob.getX(), mob.getY(), mob.getZ()));
    }

    /**
     * ¿Hay delante (en la dirección {@code dir}) un hueco por el que cabe agachada pero no de pie? Comprueba a
     * 0,8 / 1,3 / 1,9 bloques y a la altura del suelo y un bloque más arriba (escalones).
     */
    public static boolean gapAhead(Mob mob, EntityDimensions stand, EntityDimensions duck, Vec3 dir) {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0E-3) {
            return false;
        }
        double dx = dir.x / len, dz = dir.z / len;
        for (double dist : new double[]{0.8, 1.3, 1.9}) {
            double x = mob.getX() + dx * dist, z = mob.getZ() + dz * dist;
            for (double dy : new double[]{0.0, 1.0}) {
                boolean duckFits = mob.level().noCollision(mob, duck.makeBoundingBox(x, mob.getY() + dy, z));
                boolean standFits = mob.level().noCollision(mob, stand.makeBoundingBox(x, mob.getY() + dy, z));
                if (duckFits && !standFits) {
                    return true;
                }
            }
        }
        return false;
    }
}
