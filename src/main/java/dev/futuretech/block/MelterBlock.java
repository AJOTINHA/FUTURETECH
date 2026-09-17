package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.MelterBlockEntity;
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
 * The melter: a machine that turns an item into a fluid, with energy — a stone into a splash of
 * lava. What it makes stays in its own tank until a fluid cable on an output face draws it, or a
 * bucket clicked on the block takes it. Energy comes in through every face without being
 * configured; a face only decides items in and fluid out.
 */
public final class MelterBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<MelterBlock> CODEC = simpleCodec(MelterBlock::new);
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public MelterBlock(Properties properties) {
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

    /** The melter pulls what it melts in; its fluid leaves by being drawn, so there is nothing to push. */
    @Override
    public boolean supportsAutoPull() { return true; }

    @Override
    public boolean supportsAutoPush() { return false; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<MelterBlock> codec() { return CODEC; }

    /** Redstone is sampled on change rather than polled every tick. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        RedstoneControl.sample(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MelterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.MELTER.get(), TickProfiler.wrap(MelterBlockEntity::serverTick));
    }

    /** A bucket clicked on the block fills from the tank, like on the fluid tank. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MelterBlockEntity melter) {
            FluidUtil.interactWithFluidHandler(player, hand, pos, melter.tank(), null);
        }
        // Consume unsuccessful bucket interactions too, so a full bucket cannot spill beside it.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MelterBlockEntity melter) {
            player.openMenu(melter, buffer -> buffer.writeBlockPos(pos));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MelterBlockEntity melter
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(melter.energy()) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // An ember now and then at the door while something is melting.
        if (!state.getValue(LIT) || random.nextInt(5) != 0) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.53 + facing.getStepZ() * (random.nextDouble() * 0.5 - 0.25);
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.53 + facing.getStepX() * (random.nextDouble() * 0.5 - 0.25);
        level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
    }
}
