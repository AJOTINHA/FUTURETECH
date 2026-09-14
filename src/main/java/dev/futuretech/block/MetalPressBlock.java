package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.MetalPressBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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

public final class MetalPressBlock extends AbstractFurnaceBlock implements SideConfigurableBlock {
    public static final MapCodec<MetalPressBlock> CODEC = simpleCodec(MetalPressBlock::new);
    // Items go both ways: in as ingredient, out as result, or both on one face. Energy always comes in.
    private static final Set<SideMode> ALLOWED_SIDE_MODES =
            Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);

    public MetalPressBlock(Properties properties) {
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

    /** A metal press both takes ingredients in and hands results out. */
    @Override
    public boolean supportsAutoPull() { return true; }

    @Override
    public boolean supportsAutoPush() { return true; }

    @Override
    public BlockState displayState(Direction front) {
        return defaultBlockState().setValue(FACING, front.getAxis().isHorizontal() ? front : Direction.NORTH);
    }

    @Override
    public MapCodec<MetalPressBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalPressBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type, ModBlockEntities.METAL_PRESS.get(), MetalPressBlockEntity::serverTick);
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MetalPressBlockEntity metal_press) {
            player.openMenu(metal_press, buffer -> dev.futuretech.menu.MetalPressMenu.writeOpeningData(buffer, metal_press));
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MetalPressBlockEntity metal_press
                ? EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(metal_press.energy()) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Small sparks at the intake while the press is working.
        if (!state.getValue(LIT) || random.nextInt(3) != 0) return;
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.53;
        double y = pos.getY() + 0.35;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.53;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
    }
}
