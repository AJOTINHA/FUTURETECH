package dev.futuretech.block;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.perf.TickProfiler;
import com.mojang.serialization.MapCodec;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.PaintMachineBlockEntity;
import dev.futuretech.menu.PaintMachineMenu;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.Set;

public final class PaintMachineBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<PaintMachineBlock> CODEC = simpleCodec(PaintMachineBlock::new);
    // Items go both ways: in as block and plates, out as panels, or both on one face. Energy always comes in.
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public PaintMachineBlock(Properties properties) {
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

    /** A paint machine both takes its block and plates in and hands panels out. */
    @Override
    public boolean supportsAutoPull() { return true; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<PaintMachineBlock> codec() {
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
        return new PaintMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.PAINT_MACHINE.get(), TickProfiler.wrap(PaintMachineBlockEntity::serverTick));
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof PaintMachineBlockEntity painter) {
            player.openMenu(painter, buffer -> PaintMachineMenu.writeOpeningData(buffer, painter));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PaintMachineBlockEntity painter
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(painter.energy()) : 0;
    }

    /** The four paints the front cycles through, as the mist that escapes the window takes them in turn. */
    private static final int[] PAINTS = {0xD64034, 0xE8BA30, 0x4EC878, 0x1676C4};

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A puff of paint mist drifts out of the window while the nozzle is working.
        if (!state.getValue(LIT) || random.nextInt(4) != 0) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.53 + facing.getStepZ() * (random.nextDouble() * 0.5 - 0.25);
        double y = pos.getY() + 0.4 + random.nextDouble() * 0.25;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.53 + facing.getStepX() * (random.nextDouble() * 0.5 - 0.25);
        int paint = PAINTS[(int) (level.getGameTime() / 24 % PAINTS.length)];
        level.addParticle(new DustParticleOptions(paint, 0.6F), x, y, z, 0, 0.01, 0);
    }
}
