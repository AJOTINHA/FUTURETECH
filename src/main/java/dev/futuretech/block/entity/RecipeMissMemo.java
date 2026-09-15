package dev.futuretech.block.entity;

import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Remembers, per lane, the last input that had no recipe. {@code RecipeManager.CachedCheck} only
 * caches hits: a stack with no recipe left in a lane would otherwise be looked up against the
 * whole book every tick — all of vanilla's smelting recipes, in the electric furnace's case. A
 * miss is forgotten after {@value #TICKS} ticks, so a datapack reload that adds the recipe is
 * picked up within seconds. Bigger stacks of the same item are looked up again, since a recipe may
 * need more than one.
 */
final class RecipeMissMemo {
    static final int TICKS = 100;
    private final ItemStack[] first;
    private final ItemStack[] second;
    private final int[] variant;
    private final long[] until;

    RecipeMissMemo(int lanes) {
        first = new ItemStack[lanes];
        second = new ItemStack[lanes];
        variant = new int[lanes];
        until = new long[lanes];
        Arrays.fill(first, ItemStack.EMPTY);
        Arrays.fill(second, ItemStack.EMPTY);
    }

    /** Whether the lane's input is known to have no recipe; {@code b} is empty for single-input machines. */
    boolean known(int lane, ItemStack a, ItemStack b, int variant, long now) {
        return now < until[lane] && this.variant[lane] == variant && covers(first[lane], a) && covers(second[lane], b);
    }

    void remember(int lane, ItemStack a, ItemStack b, int variant, long now) {
        first[lane] = a.copy();
        second[lane] = b.copy();
        this.variant[lane] = variant;
        until[lane] = now + TICKS;
    }

    /** A remembered stack covers a stack of the same item that is no bigger. */
    private static boolean covers(ItemStack remembered, ItemStack stack) {
        if (remembered.isEmpty() || stack.isEmpty()) return remembered.isEmpty() && stack.isEmpty();
        return ItemStack.isSameItemSameComponents(remembered, stack) && stack.getCount() <= remembered.getCount();
    }
}
