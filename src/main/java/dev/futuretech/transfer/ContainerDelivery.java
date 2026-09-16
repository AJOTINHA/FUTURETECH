package dev.futuretech.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Hands items to a vanilla inventory the way the hopper does: straight into the {@link Container},
 * one {@code setChanged} at the end, no transaction. Measured through the transfer API, one item
 * into a chest cost 60–230 µs (snapshots, copies and commit journals over 27 slots); this path
 * costs a few. Blocks that only offer an item handler, such as other mods' machines, keep going
 * through the API.
 */
final class ContainerDelivery {
    private ContainerDelivery() {}

    /** The inventory of the block at {@code pos}, joined across a double chest, or null when the block has none. */
    static @Nullable Container blockContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof WorldlyContainerHolder holder) return holder.getContainer(state, level, pos);
        if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container) {
            if (container instanceof ChestBlockEntity && block instanceof ChestBlock chest) {
                return ChestBlock.getContainer(chest, state, level, pos, true);
            }
            return container;
        }
        return null;
    }

    /** Inserts as much of {@code stack} as fits through {@code side}; returns how much went in. */
    static int insert(Container container, ItemStack stack, @Nullable Direction side) {
        ItemStack rest = HopperBlockEntity.addItem(null, container, stack.copy(), side);
        return stack.getCount() - rest.getCount();
    }

    /**
     * How many of {@code stack} would fit through {@code side} right now, up to {@code wanted}:
     * what the partial stacks of the same item can take, plus a full stack per empty slot the
     * item may go in.
     */
    static int room(Container container, ItemStack stack, @Nullable Direction side, int wanted) {
        int room = 0;
        if (container instanceof WorldlyContainer worldly && side != null) {
            for (int slot : worldly.getSlotsForFace(side)) {
                if (!worldly.canPlaceItemThroughFace(slot, stack, side)) continue;
                room += slotRoom(container, slot, stack);
                if (room >= wanted) return wanted;
            }
        } else {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                room += slotRoom(container, slot, stack);
                if (room >= wanted) return wanted;
            }
        }
        return room;
    }

    private static int slotRoom(Container container, int slot, ItemStack stack) {
        if (!container.canPlaceItem(slot, stack)) return 0;
        ItemStack held = container.getItem(slot);
        if (held.isEmpty()) return container.getMaxStackSize(stack);
        if (!ItemStack.isSameItemSameComponents(held, stack)) return 0;
        return Math.max(0, container.getMaxStackSize(held) - held.getCount());
    }
}
