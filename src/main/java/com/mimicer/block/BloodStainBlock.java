package com.mimicer.block;

import com.mimicer.blood.BloodSystem;
import com.mimicer.config.BloodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mancha de sangre: una capa fina pegada a hasta seis caras de un bloque de aire. No tiene colisión ni se puede
 * seleccionar. Se oscurece en tres fases (AGE) y desaparece sola con ticks programados (duración configurable).
 * Al ser un bloque, se sincroniza con todos los jugadores sin entidades ni paquetes propios.
 */
public class BloodStainBlock extends Block {
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 5);

    public BloodStainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(DOWN, false).setValue(UP, false).setValue(NORTH, false)
                .setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false)
                .setValue(AGE, 0).setValue(VARIANT, 0));
    }

    public static BooleanProperty face(Direction d) {
        return switch (d) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, SOUTH, EAST, WEST, AGE, VARIANT);
    }

    /** ¿Puede una mancha agarrarse a la cara {@code d} de la celda {@code pos}? */
    public static boolean canAttach(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        BlockState s = level.getBlockState(n);
        if (s.is(Blocks.WATER) || s.is(Blocks.LAVA)) return false;
        Block b = s.getBlock();
        return s.isFaceSturdy(level, n, d.getOpposite())
                || b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
                || b instanceof IronBarsBlock || b instanceof BoardBlock;
    }

    public static boolean hasAnyFace(BlockState s) {
        for (Direction d : Direction.values()) {
            if (s.getValue(face(d))) return true;
        }
        return false;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        BlockState out = state;
        if (state.getValue(face(dir)) && !canAttach(level, pos, dir)) {
            out = out.setValue(face(dir), false);
        }
        return hasAnyFace(out) ? out : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= 2) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), 2);
        if (age == 0 && BloodConfig.SPREAD.get() && state.getValue(VARIANT) <= 1 && random.nextInt(3) == 0) {
            BloodSystem.spreadFrom(level, pos, state, random);
        }
        level.scheduleTick(pos, this, BloodSystem.phaseTicks(random));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
    }
}
