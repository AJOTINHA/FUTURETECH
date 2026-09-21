package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A girder of the scaffold a quarry stands up around its pit. The machine builds it out of these
 * one block at a time and takes it down when it leaves, so they are not crafted and they drop
 * nothing: break one and the quarry puts it back on its next pass.
 *
 * <p>Girders join like the mod's cables do — a stub towards every neighbouring girder — so a
 * corner or a T reads as one continuous beam without the machine having to say which is which.
 */
public final class QuarryFrameBlock extends Block {
    public static final MapCodec<QuarryFrameBlock> CODEC = simpleCodec(QuarryFrameBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final Map<Direction, BooleanProperty> BY_SIDE = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    /**
     * The joint in the middle of the block and the stub an arm reaches out with, both the same
     * six pixels across: a run of girders then reads as one straight beam rather than as beads.
     */
    private static final VoxelShape NODE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5),
            Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11),
            Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11),
            Direction.UP, Block.box(5, 11, 5, 11, 16, 11)));
    /**
     * One shape per combination of arms, built once and handed out by identity: the mesher asks
     * for these per quad, so they must never be made on the fly.
     */
    private static final VoxelShape[] SHAPES = buildShapes();

    public QuarryFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<QuarryFrameBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private static VoxelShape[] buildShapes() {
        var shapes = new VoxelShape[1 << 6];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = NODE;
            for (Direction side : Direction.values()) {
                if ((mask & 1 << side.ordinal()) != 0) shape = Shapes.or(shape, ARMS.get(side));
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    private static int mask(BlockState state) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (state.getValue(BY_SIDE.get(side))) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    /** The state a girder at {@code pos} should have: a stub towards every girder beside it. */
    public static BlockState connected(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(BY_SIDE.get(side), level.getBlockState(pos.relative(side)).is(state.getBlock()));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return state.setValue(BY_SIDE.get(directionToNeighbour), neighbourState.is(this));
    }
}
