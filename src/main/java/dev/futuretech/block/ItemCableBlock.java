package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * One block class for every {@link ItemCableTier}. Items are moved by the
 * {@link dev.futuretech.transfer.ItemCableNetwork}.
 */
public final class ItemCableBlock extends AbstractCableBlock {
    public static final MapCodec<ItemCableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCableTier.CODEC.fieldOf("tier").forGetter(ItemCableBlock::tier), propertiesCodec()
    ).apply(i, ItemCableBlock::new));

    private final ItemCableTier tier;

    public ItemCableBlock(ItemCableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ItemCableTier tier() { return tier; }

    @Override
    protected MapCodec<ItemCableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.ITEMS; }

    /** Only its own tier: an opaque cable and a see-through one are separate lines, not one run. */
    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof ItemCableBlock other && other.tier == tier;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return level.getCapability(Capabilities.Item.BLOCK, neighbour, face) != null;
    }

    @Override
    protected BlockEntityType<ItemCableBlockEntity> blockEntityType() {
        return ModBlockEntities.ITEM_CABLE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemCableBlockEntity(pos, state);
    }
}
