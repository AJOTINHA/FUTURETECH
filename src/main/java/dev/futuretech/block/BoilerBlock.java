package dev.futuretech.block;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.perf.TickProfiler;
import com.mojang.serialization.MapCodec;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.BoilerBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import java.util.Set;

public final class BoilerBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);
    // Input faces take water and fuel; output faces provide steam and empty buckets.
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public BoilerBlock(Properties properties) {
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
        // New machines start closed; the player opens the faces they want from the screen. The
        // faces govern energy too: with the energy upgrade, FE comes in through the input faces only.
        return new SideConfig(ALLOWED_SIDE_MODES, true, side -> SideMode.NONE);
    }

    /** A generator pulls full buckets in and hands the empty ones back. */
    @Override
    public boolean supportsAutoPull() { return true; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<BoilerBlock> codec() {
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
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.BOILER.get(), TickProfiler.wrap(BoilerBlockEntity::serverTick));
    }

    /** A bucket clicked on the block goes straight into the tank, like on the fluid tank. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BoilerBlockEntity generator) {
            FluidUtil.interactWithFluidHandler(player, hand, pos, generator.handler(null), null);
        }
        // Consume unsuccessful bucket interactions too, so a full tank cannot spill water beside it.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity generator) {
            player.openMenu(generator);
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BoilerBlockEntity generator
                ? generator.comparatorSignal() : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Embers and smoke leave the firebox while the boiler produces steam.
        if (!state.getValue(LIT) || random.nextInt(4) != 0) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.53 + facing.getStepZ() * (random.nextDouble() * 0.5 - 0.25);
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.53 + facing.getStepX() * (random.nextDouble() * 0.5 - 0.25);
        level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
    }
}
