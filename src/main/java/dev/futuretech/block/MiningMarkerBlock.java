package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.block.entity.MiningMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A corner of the frame a quarry digs inside of: a blue torch, stood where a corner of the pit
 * should be. Markers that share an axis link up, and the line between two of them is an edge the
 * renderer draws; give a marker a redstone signal and it also shows its empty directions, as a
 * straight line saying where the next corner may stand. What the quarry digs is the inside of the
 * frame, so the blocks the markers stand on are never broken.
 */
public final class MiningMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<MiningMarkerBlock> CODEC = simpleCodec(MiningMarkerBlock::new);
    /** Set while a redstone signal reaches the marker; that is when it draws its guide lines. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** A torch's own box: two pixels across and ten tall, with nothing to walk into. */
    private static final VoxelShape SHAPE = Block.column(2, 0, 10);

    public MiningMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<MiningMarkerBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    /** The lines are drawn by the block entity's renderer; the torch itself is an ordinary model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiningMarkerBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A torch is walked through, not around. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) { return true; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED,
                context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** A marker placed or broken changes the lines of every marker its axes reach. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) MiningMarkerBlockEntity.refreshAround(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MiningMarkerBlockEntity.refreshAround(level, pos);
    }

    /** Redstone only decides whether the guide lines show; the links stand either way. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }
}
