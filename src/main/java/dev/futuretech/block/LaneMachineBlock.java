package dev.futuretech.block;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.perf.TickProfiler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.Set;

/** A machine with one input and one output slot per open lane; {@link LaneMachineKind} says which one. */
public final class LaneMachineBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<LaneMachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(block -> block.kind.name()),
            propertiesCodec()
    ).apply(i, (kind, properties) -> new LaneMachineBlock(LaneMachineKind.valueOf(kind), properties)));
    // Items go both ways: in as ingredient, out as result, or both on one face. Energy always comes in.
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public final LaneMachineKind kind;

    public LaneMachineBlock(LaneMachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
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

    /** The machine both takes ingredients in and hands results out. */
    @Override
    public boolean supportsAutoPull() { return true; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<LaneMachineBlock> codec() {
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
        return new LaneMachineBlockEntity(kind, pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, kind.blockEntityType(), TickProfiler.wrap(LaneMachineBlockEntity::serverTick));
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof LaneMachineBlockEntity machine) {
            player.openMenu(machine, buffer -> dev.futuretech.menu.LaneMachineMenu.writeOpeningData(buffer, machine));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof LaneMachineBlockEntity machine
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(machine.energy()) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A little something at the intake while the machine is working.
        if (!state.getValue(LIT) || random.nextInt(3) != 0) return;
        kind.addWorkingParticle(level, pos, state.getValue(FACING), random);
    }
}
