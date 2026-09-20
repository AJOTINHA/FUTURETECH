package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.block.entity.RainSensorBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The daylight detector's cousin for rain: a slab that gives out a redstone signal while it is
 * raining on it, stronger as the rain thickens and full in a storm. It reads the rain the way
 * the world does — under the open sky, in a biome where it rains — so a roof or a desert keeps
 * it quiet. Right-clicking inverts it, like the detector: it then signals while it is dry.
 * Unlike the detector it has a front — the drop on its face reads one way up — and it is placed
 * with that front towards whoever placed it.
 */
public final class RainSensorBlock extends BaseEntityBlock {
    public static final MapCodec<RainSensorBlock> CODEC = simpleCodec(RainSensorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    /** The side the drop's foot is on: the face turned to the player who placed it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Six pixels high, the detector's own slab. */
    private static final VoxelShape SHAPE = Block.column(16, 0, 6);
    /** The detector reads once a second; so does this. */
    private static final long READ_EVERY = 20;

    public RainSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(INVERTED, false).setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<RainSensorBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, INVERTED, FACING);
    }

    /** The front faces the player, the way a furnace's does. */
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) { return true; }

    /**
     * The signal for the sky as it is: nothing dry, the rain's own strength in fifteenths while it
     * rains — at least one, so a drizzle still counts — and full in a storm. Inverted, the same
     * taken from fifteen.
     */
    public static int signal(boolean raining, boolean thundering, float rainLevel, boolean inverted) {
        int power = 0;
        if (thundering) power = 15;
        else if (raining) power = Math.clamp(Math.round(rainLevel * 15), 1, 15);
        return inverted ? 15 - power : power;
    }

    /**
     * Runs on the server. The rain is read as the world reads it, since a sensor should agree
     * with the sky the player sees; the storm is read as set, since thunder is a flag the world
     * flips at once and only shows by degrees.
     */
    private static void updateSignal(BlockState state, Level level, BlockPos pos) {
        boolean raining = level.isRainingAt(pos.above());
        boolean thundering = level instanceof ServerLevel server ? server.getWeatherData().isThundering() : level.isThundering();
        int power = signal(raining, raining && thundering, level.getRainLevel(1.0F), state.getValue(INVERTED));
        // The neighbours are told, the way the detector tells them: dust beside it re-reads the signal.
        if (state.getValue(POWER) != power) level.setBlock(pos, state.setValue(POWER, power), UPDATE_ALL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide()) {
            BlockState inverted = state.cycle(INVERTED);
            level.setBlock(pos, inverted, UPDATE_ALL);
            updateSignal(inverted, level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(POWER); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RainSensorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.RAIN_SENSOR.get(),
                (tickLevel, pos, tickState, sensor) -> {
                    if (tickLevel.getGameTime() % READ_EVERY == 0) updateSignal(tickState, tickLevel, pos);
                });
    }
}
