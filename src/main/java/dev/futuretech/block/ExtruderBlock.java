package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.ExtruderBlockEntity;
import dev.futuretech.perf.TickProfiler;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The extruder: a machine that presses two fluids into an item, with energy - water and lava into
 * cobblestone, stone or obsidian, whichever its screen is set to. Its two tanks fill through fluid
 * cables on the input faces, or from a bucket clicked on the block, and the item it makes waits in
 * the output slot for an item cable on an output face. Energy comes in through every face without
 * being configured.
 */
public final class ExtruderBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<ExtruderBlock> CODEC = simpleCodec(ExtruderBlock::new);
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public ExtruderBlock(Properties properties) {
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

    /** What the extruder is fed is drawn in by the cables; what it makes is an item it can push out. */
    @Override
    public boolean supportsAutoPull() { return false; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<ExtruderBlock> codec() { return CODEC; }

    /** Redstone is sampled on change rather than polled every tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        RedstoneControl.sample(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExtruderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.EXTRUDER.get(), TickProfiler.wrap(ExtruderBlockEntity::serverTick));
    }

    /** A bucket clicked on the block pours into the first tank that will take it, as on the fluid tank. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExtruderBlockEntity extruder) {
            FluidUtil.interactWithFluidHandler(player, hand, pos, extruder.tanks(), null);
        }
        // Consume unsuccessful bucket interactions too, so a full bucket cannot spill beside it.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof ExtruderBlockEntity extruder) {
            player.openMenu(extruder, buffer -> buffer.writeBlockPos(pos));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ExtruderBlockEntity extruder
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(extruder.energy()) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Water meeting lava inside: a wisp of steam off the top now and then while it works.
        if (!state.getValue(LIT) || random.nextInt(4) != 0) return;
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double y = pos.getY() + 1.02;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.CLOUD, x, y, z, 0, 0.015, 0);
    }
}
