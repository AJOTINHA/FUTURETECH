package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.EnergyCableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * One block class for every {@link EnergyCableTier}. Energy flow is handled by the
 * {@link dev.futuretech.energy.EnergyCableNetwork}.
 */
public final class EnergyCableBlock extends AbstractCableBlock {
    public static final MapCodec<EnergyCableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EnergyCableTier.CODEC.fieldOf("tier").forGetter(EnergyCableBlock::tier), propertiesCodec()
    ).apply(i, EnergyCableBlock::new));

    private final EnergyCableTier tier;

    public EnergyCableBlock(EnergyCableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public EnergyCableTier tier() { return tier; }

    @Override
    protected MapCodec<EnergyCableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.ENERGY; }

    /** Each tier's core has its colour, and the plug in the collar wears the same one. */
    @Override
    public String contactTexture() { return "block/" + tier.blockName() + "/energy_cable_contact"; }

    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof EnergyCableBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return level.getCapability(Capabilities.Energy.BLOCK, neighbour, face) != null;
    }

    @Override
    protected BlockEntityType<EnergyCableBlockEntity> blockEntityType() {
        return ModBlockEntities.CABLE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCableBlockEntity(pos, state);
    }
}
