package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.CableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * One block class for every {@link CableTier}. Energy flow is handled by the
 * {@link dev.futuretech.energy.CableNetwork}.
 */
public final class CableBlock extends AbstractCableBlock {
    public static final MapCodec<CableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CableTier.CODEC.fieldOf("tier").forGetter(CableBlock::tier), propertiesCodec()
    ).apply(i, CableBlock::new));

    private final CableTier tier;

    public CableBlock(CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CableTier tier() { return tier; }

    @Override
    protected MapCodec<CableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.ENERGY; }

    /** Each tier's core has its colour, and the plug in the collar wears the same one. */
    @Override
    public String contactTexture() { return "block/" + tier.blockName() + "/cable_contact"; }

    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof CableBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return level.getCapability(Capabilities.Energy.BLOCK, neighbour, face) != null;
    }

    @Override
    protected BlockEntityType<CableBlockEntity> blockEntityType() {
        return ModBlockEntities.CABLE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }
}
