package com.mimicer.block;

import com.mimicer.hide.ClosetIndex;
import com.mimicer.hide.HideManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Armario de dos bloques de alto con puertas animadas. El interior es hueco (se puede estar de pie dentro):
 * un jugador o aldeano puede esconderse y los monstruos pueden abrirlo y sacarlo. La posición del armario
 * (identificador del escondite) es siempre la de su mitad inferior.
 */
public class WardrobeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape[] LOWER = new VoxelShape[4];
    private static final VoxelShape[] UPPER = new VoxelShape[4];

    static {
        // Cuerpo hueco orientado al NORTE (frente en z=0): suelo, fondo, laterales, puerta cerrada y, arriba, techo.
        double[][] lower = {{0, 0, 0, 16, 1, 16}, {0, 0, 14, 16, 16, 16}, {0, 0, 0, 2, 16, 16}, {14, 0, 0, 16, 16, 16},
                {0, 0, 0, 16, 16, 1}};
        double[][] upper = {{0, 0, 14, 16, 16, 16}, {0, 0, 0, 2, 16, 16}, {14, 0, 0, 16, 16, 16}, {0, 0, 0, 16, 16, 1},
                {0, 14, 0, 16, 16, 16}};
        for (int i = 0; i < 4; i++) {
            Direction d = Direction.from2DDataValue(i);
            LOWER[i] = build(lower, d);
            UPPER[i] = build(upper, d);
        }
    }

    private static VoxelShape build(double[][] boxes, Direction d) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
            double nx1, nz1, nx2, nz2;
            switch (d) {
                case SOUTH -> { nx1 = 16 - x2; nz1 = 16 - z2; nx2 = 16 - x1; nz2 = 16 - z1; }
                case EAST -> { nx1 = 16 - z2; nz1 = x1; nx2 = 16 - z1; nz2 = x2; }
                case WEST -> { nx1 = z1; nz1 = 16 - x2; nx2 = z2; nz2 = 16 - x1; }
                default -> { nx1 = x1; nz1 = z1; nx2 = x2; nz2 = z2; }
            }
            shape = Shapes.or(shape, Block.box(nx1, b[1], nz1, nx2, b[4], nz2));
        }
        return shape;
    }

    public WardrobeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    // ------------------------------------------------------------ colocación

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= ctx.getLevel().getMaxBuildHeight() - 1
                || !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (player.isCreative()) {
                    level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);
                } else {
                    level.destroyBlock(lowerPos, true, player);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------ formas

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = state.getValue(FACING).get2DDataValue();
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER[i] : UPPER[i];
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ------------------------------------------------------------ interacción

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        BlockPos lower = lowerPos(state, pos);
        if (!level.isClientSide) {
            WardrobeBlockEntity be = blockEntity(level, lower);
            if (be != null && !HideManager.isSpotTaken(level, lower)) {
                setOpen(level, lower, !be.isOpen(), 12);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Cierre diferido tras entrar o salir alguien.
        WardrobeBlockEntity be = blockEntity(level, pos);
        if (be != null && be.isOpen() && !ClosetIndex.isBusy(level, pos)) {
            setOpen(level, pos, false, 12);
        }
    }

    // ------------------------------------------------------------ utilidades

    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Nullable
    public static WardrobeBlockEntity blockEntity(Level level, BlockPos lower) {
        return level.getBlockEntity(lower) instanceof WardrobeBlockEntity be ? be : null;
    }

    public static boolean isWardrobe(Level level, BlockPos lower) {
        BlockState s = level.getBlockState(lower);
        return s.getBlock() instanceof WardrobeBlock && s.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    public static Direction facing(Level level, BlockPos lower) {
        BlockState s = level.getBlockState(lower);
        return s.getBlock() instanceof WardrobeBlock ? s.getValue(FACING) : Direction.NORTH;
    }

    /** Dónde se queda quien está dentro: centro del hueco, sobre el suelo. */
    public static Vec3 insidePos(BlockPos lower) {
        return new Vec3(lower.getX() + 0.5, lower.getY() + 0.07, lower.getZ() + 0.5);
    }

    /** Punto delante del armario a {@code dist} bloques del centro, en la dirección del frente. */
    public static Vec3 frontPoint(Level level, BlockPos lower, double dist) {
        Direction f = facing(level, lower);
        return new Vec3(lower.getX() + 0.5 + f.getStepX() * dist, lower.getY(), lower.getZ() + 0.5 + f.getStepZ() * dist);
    }

    /** Abre o cierra las puertas con animación de {@code ticks} ticks y su sonido (sin sonidos nuevos). */
    public static void setOpen(Level level, BlockPos lower, boolean open, int ticks) {
        WardrobeBlockEntity be = blockEntity(level, lower);
        if (be == null || be.isOpen() == open) {
            return;
        }
        be.setOpen(open, ticks);
        level.playSound(null, lower, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 0.9F, ticks > 20 ? 0.55F : 0.8F);
    }

    // ------------------------------------------------------------ bloque con entidad (solo la mitad inferior)

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new WardrobeBlockEntity(com.mimicer.registry.ModBlockEntities.WARDROBE.get(), pos, state) : null;
    }
}
