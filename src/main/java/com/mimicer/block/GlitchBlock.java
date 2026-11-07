package com.mimicer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Bloque corrompido: textura animada con interferencias y partículas moradas ocasionales. */
public class GlitchBlock extends Block {
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.65F, 0.0F, 1.0F), 0.9F);

    public GlitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 1.02;
        double z = pos.getZ() + random.nextDouble();
        if (level.getBlockState(pos.above()).isAir()) {
            level.addParticle(random.nextBoolean() ? PURPLE : ParticleTypes.REVERSE_PORTAL, x, y, z,
                    0.0, 0.03, 0.0);
        }
    }
}
