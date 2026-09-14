package dev.futuretech.item;

import dev.futuretech.FutureTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Right-clicking any block with the wrench turns it: a facing walks the compass and then up and
 * down, an axis cycles, and blocks with neither fall back to their own rotation logic. Runs
 * before the block itself is used, so machines with a GUI rotate instead of opening. Sneaking
 * instead picks the block up, contents included.
 */
public final class WrenchItem extends Item {
    /** Around the compass first, then up and down. */
    private static final List<Direction> ORDER =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN);

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (context.isSecondaryUseActive()) return dismantle(state, level, pos, context.getPlayer());
        if (level.getBlockEntity(pos) instanceof dev.futuretech.block.entity.AssemblerBlockEntity arm
                && arm.kind() == dev.futuretech.block.AssemblerBlock.Kind.TRANSPORT) {
            if (!level.isClientSide() && context.getPlayer() != null) arm.switchMode(context.getPlayer());
            return InteractionResult.SUCCESS;
        }
        BlockState rotated = rotated(state, level, pos);
        if (rotated == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlock(pos, rotated, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Sneak-click picks up one of this mod's blocks as its item, through the normal drop path so
     * the loot table keeps whatever the block held (a tank's fluid, a battery's charge).
     */
    private static InteractionResult dismantle(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(FutureTech.MOD_ID)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) level.destroyBlock(pos, true, player);
        return InteractionResult.SUCCESS;
    }

    /** The state after one turn, or null when the block has nothing to rotate. */
    private static @Nullable BlockState rotated(BlockState state, Level level, BlockPos pos) {
        for (Property<?> property : state.getProperties()) {
            Class<?> type = property.getValueClass();
            if (type == Direction.class) {
                @SuppressWarnings("unchecked")
                Property<Direction> facing = (Property<Direction>) property;
                return turned(state, facing, level, pos);
            }
            if (type == Direction.Axis.class) {
                BlockState cycled = state.cycle(property);
                return cycled.canSurvive(level, pos) ? cycled : null;
            }
        }
        BlockState turned = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        return turned != state && turned.canSurvive(level, pos) ? turned : null;
    }

    /** Next facing in {@link #ORDER} the block accepts and survives with, wrapping around. */
    private static @Nullable BlockState turned(BlockState state, Property<Direction> facing, Level level, BlockPos pos) {
        int start = ORDER.indexOf(state.getValue(facing));
        for (int step = 1; step < ORDER.size(); step++) {
            Direction next = ORDER.get((start + step) % ORDER.size());
            if (!facing.getPossibleValues().contains(next)) continue;
            BlockState turned = state.setValue(facing, next);
            if (turned.canSurvive(level, pos)) return turned;
        }
        return null;
    }
}
