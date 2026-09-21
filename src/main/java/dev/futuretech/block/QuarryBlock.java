package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.QuarryBlockEntity;
import dev.futuretech.perf.TickProfiler;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The quarry: it digs the box its {@link MiningMarkerBlock area markers} mark, down to the bottom
 * of the world. Energy comes in through every face without being configured, and a face only
 * decides whether what was dug may leave through it, so the modes are output or nothing.
 */
public final class QuarryBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<QuarryBlock> CODEC = simpleCodec(QuarryBlock::new);
    private static final Set<SideMode> ALLOWED_SIDE_MODES = Set.of(SideMode.OUTPUT, SideMode.NONE);

    public QuarryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MachineLevel.MK);
    }

    @Override
    public Set<SideMode> allowedSideModes() { return ALLOWED_SIDE_MODES; }

    @Override
    public SideConfig createSideConfig(BlockState state) {
        // New machines start closed; the player opens the faces they want from the screen.
        return new SideConfig(ALLOWED_SIDE_MODES, side -> SideMode.NONE);
    }

    /** Nothing useful is ever pushed into a quarry; what it digs is pushed out. */
    @Override
    public boolean supportsAutoPull() { return false; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<QuarryBlock> codec() { return CODEC; }

    /** Redstone is sampled on change rather than polled every tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        RedstoneControl.sample(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.QUARRY.get(), TickProfiler.wrap(QuarryBlockEntity::serverTick));
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
            player.openMenu(quarry);
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(quarry.energy()) : 0;
    }
}
