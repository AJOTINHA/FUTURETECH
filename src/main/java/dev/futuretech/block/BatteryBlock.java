package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.BatteryBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.Set;

/**
 * One block class for every {@link BatteryTier}; the tier supplies capacity and transfer rate.
 * Every face starts closed; players open them from the screen. A battery face is never input and
 * output at once, because that would let a cable hand the battery its own energy back.
 */
public final class BatteryBlock extends BaseEntityBlock implements SideConfigurableBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Set<SideMode> ALLOWED_SIDE_MODES = Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE);
    public static final MapCodec<BatteryBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BatteryTier.CODEC.fieldOf("tier").forGetter(BatteryBlock::tier), propertiesCodec()
    ).apply(i, BatteryBlock::new));

    private final BatteryTier tier;
    private static final VoxelShape FRAME_SHAPE = createFrameShape();

    private static VoxelShape createFrameShape() {
        VoxelShape shape = Shapes.empty();
        for (int x : new int[]{0, 13}) for (int y : new int[]{0, 13}) for (int z : new int[]{0, 13}) {
            shape = Shapes.or(shape, box(x, y, z, x + 3, y + 3, z + 3));
        }
        for (double a : new double[]{0.25, 13.25}) for (double b : new double[]{0.25, 13.25}) {
            shape = Shapes.or(shape, box(3, a, b, 13, a + 2.5, b + 2.5),
                    box(a, 3, b, a + 2.5, 13, b + 2.5), box(a, b, 3, a + 2.5, b + 2.5, 13));
        }
        return shape.optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Catch targeting rays across the open faces so clicks cannot reach blocks behind the battery.
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FRAME_SHAPE;
    }

    public BatteryBlock(BatteryTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The output face points at the player, like the front of a furnace. */
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
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    public BatteryTier tier() { return tier; }

    @Override
    public Set<SideMode> allowedSideModes() { return ALLOWED_SIDE_MODES; }

    @Override
    public SideConfig createSideConfig(BlockState state) {
        // New machines start closed; the player opens the faces they want from the screen.
        // A battery moves energy for a living, so here the face modes do govern energy.
        return new SideConfig(ALLOWED_SIDE_MODES, true, side -> SideMode.NONE);
    }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    protected MapCodec<BatteryBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.BATTERY.get(), BatteryBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            player.openMenu(battery);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BatteryBlockEntity battery
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(battery.energy()) : 0;
    }
}
