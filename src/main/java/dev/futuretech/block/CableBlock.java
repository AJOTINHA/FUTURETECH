package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.CableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * One block class for every {@link CableTier}. The six connection properties only drive the model
 * and shape; energy flow is handled by the {@link dev.futuretech.energy.CableNetwork}.
 */
public final class CableBlock extends PipeBlock implements EntityBlock {
    public static final MapCodec<CableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CableTier.CODEC.fieldOf("tier").forGetter(CableBlock::tier), propertiesCodec()
    ).apply(i, CableBlock::new));
    private static final float SIZE = 6.0F;

    private final CableTier tier;

    public CableBlock(CableTier tier, Properties properties) {
        super(SIZE, properties);
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    public CableTier tier() { return tier; }

    @Override
    protected MapCodec<CableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Cables join other cables and any block that offers energy on the touching face. */
    private static boolean connectsTo(LevelReader level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        if (level.getBlockState(neighbour).getBlock() instanceof CableBlock) return true;
        return level instanceof Level realLevel
                && realLevel.getCapability(Capabilities.Energy.BLOCK, neighbour, side.getOpposite()) != null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side),
                    connectsTo(context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour),
                connectsTo(level, pos, directionToNeighbour));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || oldState.is(this)) return;
        // Neighbouring networks must absorb the new cable, so they rebuild on their next tick.
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side)) instanceof CableBlockEntity cable) cable.invalidateNetwork();
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CABLE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CableBlockEntity>) CableBlockEntity::serverTick;
    }
}
