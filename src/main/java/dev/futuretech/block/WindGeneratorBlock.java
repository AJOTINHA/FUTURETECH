package dev.futuretech.block;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.perf.TickProfiler;
import com.mojang.serialization.MapCodec;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.WindGeneratorBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.Set;

/** A five-block turbine with a tall mast, three blades and configurable energy outputs. */
public final class WindGeneratorBlock extends BaseEntityBlock implements SideConfigurableBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Set<SideMode> ALLOWED_SIDE_MODES = Set.of(SideMode.OUTPUT, SideMode.NONE);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<WindGeneratorBlock> CODEC = simpleCodec(WindGeneratorBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 8, 15),
            box(4.5, 8, 4.5, 11.5, 11, 11.5), box(5.5, 11, 5.5, 10.5, 16, 10.5));

    public WindGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(MachineLevel.MK, 1).setValue(LIT, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, MachineLevel.MK, LIT);
    }

    /** The status display points at the player; energy defaults to the underside. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return WindTurbineStructure.fits(context.getLevel(), context.getClickedPos(), state.getValue(FACING)) ? state : null;
    }

    @Override protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return WindTurbineStructure.fits(level, pos, state.getValue(FACING));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (level.isClientSide()) return;
        if (oldState.is(this) && oldState.getValue(FACING) == state.getValue(FACING)) return;
        if (!WindTurbineStructure.fits(level, pos, state.getValue(FACING))) return;
        if (oldState.is(this)) WindTurbineStructure.remove(level, pos, oldState.getValue(FACING));
        WindTurbineStructure.place(level, pos, state.getValue(FACING));
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
        WindTurbineStructure.remove(level, pos, state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Set<SideMode> allowedSideModes() { return ALLOWED_SIDE_MODES; }

    @Override
    public SideConfig createSideConfig(BlockState state) {
        // New machines start closed; the player opens the faces they want from the screen.
        return new SideConfig(ALLOWED_SIDE_MODES, true, side -> SideMode.NONE);
    }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    protected MapCodec<WindGeneratorBlock> codec() {
        return CODEC;
    }

    /** Redstone is sampled on change rather than polled every tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        RedstoneControl.sample(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, ModBlockEntities.WIND_GENERATOR.get(),
                WindGeneratorBlockEntity::clientTick) : createTickerHelper(
                type, ModBlockEntities.WIND_GENERATOR.get(), TickProfiler.wrap(WindGeneratorBlockEntity::serverTick));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WindGeneratorBlockEntity generator) {
            player.openMenu(generator);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof WindGeneratorBlockEntity generator
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy()) : 0;
    }
}
