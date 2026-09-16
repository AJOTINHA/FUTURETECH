package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.FluidCableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * One block class for every {@link FluidCableTier}. Fluids are moved by the
 * {@link dev.futuretech.transfer.FluidCableNetwork}.
 */
public final class FluidCableBlock extends AbstractCableBlock {
    public static final MapCodec<FluidCableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FluidCableTier.CODEC.fieldOf("tier").forGetter(FluidCableBlock::tier), propertiesCodec()
    ).apply(i, FluidCableBlock::new));

    private final FluidCableTier tier;

    public FluidCableBlock(FluidCableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public FluidCableTier tier() { return tier; }

    @Override
    protected MapCodec<FluidCableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.FLUID; }

    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof FluidCableBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return level.getCapability(Capabilities.Fluid.BLOCK, neighbour, face) != null;
    }

    /**
     * A new cable beside one with fluid in it starts cut off: joining a run that is carrying
     * something is a choice made with the wrench, never a side effect of placing a block.
     */
    @Override
    protected boolean placesCut(LevelReader level, BlockPos neighbour) {
        return level.getBlockEntity(neighbour) instanceof FluidCableBlockEntity cable && !cable.shown().isEmpty();
    }

    @Override
    protected BlockEntityType<FluidCableBlockEntity> blockEntityType() {
        return ModBlockEntities.FLUID_CABLE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidCableBlockEntity(pos, state);
    }
}
