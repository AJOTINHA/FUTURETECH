package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.block.entity.ControllerBlockEntity;
import dev.futuretech.perf.TickProfiler;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

/**
 * The time and weather controllers: one machine block per {@link ControllerKind}, with the sky
 * in its window and lit for a moment after it fires. Energy comes in on every face, so it has
 * no side configuration; when it fires is its redstone control, like any machine's. See
 * {@link ControllerBlockEntity}.
 */
public final class ControllerBlock extends BaseEntityBlock {
    public static final MapCodec<ControllerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ControllerKind.CODEC.fieldOf("kind").forGetter(ControllerBlock::kind),
            propertiesCodec()
    ).apply(instance, ControllerBlock::new));
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final ControllerKind kind;

    public ControllerBlock(ControllerKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public ControllerKind kind() { return kind; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected MapCodec<ControllerBlock> codec() { return CODEC; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Redstone is sampled on change rather than polled every tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        RedstoneControl.sample(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.CONTROLLER.get(),
                TickProfiler.wrap(ControllerBlockEntity::serverTick));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            player.openMenu(controller, buffer -> buffer.writeEnum(controller.kind()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ControllerBlockEntity controller
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(controller.energy()) : 0;
    }
}
