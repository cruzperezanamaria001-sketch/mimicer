package com.mimicer.util;

import com.mimicer.block.BoardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Qué cuenta como barricada (puertas cerradas y tablas) y cómo localizarla delante de una criatura. */
public final class Barricades {
    private Barricades() {}

    public static boolean isBarricade(BlockState s) {
        if (s.getBlock() instanceof DoorBlock) {
            return !s.getValue(DoorBlock.OPEN);
        }
        return s.getBlock() instanceof BoardBlock;
    }

    /** Primera barricada a 0,9 / 1,6 / 2,4 bloques en la dirección dada, a alturas 0..maxHeight. */
    public static BlockPos find(Mob mob, Vec3 dir, int maxHeight) {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0E-3) {
            return null;
        }
        double dx = dir.x / len, dz = dir.z / len;
        for (double dist : new double[]{0.9, 1.6, 2.4}) {
            for (int dy = 0; dy <= maxHeight; dy++) {
                BlockPos pos = BlockPos.containing(mob.getX() + dx * dist, mob.getY() + dy + 0.2, mob.getZ() + dz * dist);
                if (isBarricade(mob.level().getBlockState(pos))) {
                    return pos;
                }
            }
        }
        return null;
    }
}
