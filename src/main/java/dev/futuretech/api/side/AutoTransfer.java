package dev.futuretech.api.side;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * Whether a machine moves items on its own through the faces it already has configured. Pulling
 * takes from whatever sits against an input face; pushing hands results to whatever sits against an
 * output face. Sorting spreads what is in the input slots over every open lane, so a stack dropped
 * in one lane keeps the others busy too. All three start off, so a freshly placed machine never
 * touches a neighbour, or a player's stack, by surprise.
 */
public final class AutoTransfer {
    /** Menu data slots {@link #data} exposes: one flag each. */
    public static final int DATA_COUNT = 3;
    private static final String TAG_PULL = "AutoPull";
    private static final String TAG_PUSH = "AutoPush";
    private static final String TAG_SORT = "AutoSort";

    private boolean pulling;
    private boolean pushing;
    private boolean sorting;

    public boolean isPulling() { return pulling; }

    public boolean isPushing() { return pushing; }

    public boolean isSorting() { return sorting; }

    public void setPulling(boolean pulling) { this.pulling = pulling; }

    public void setPushing(boolean pushing) { this.pushing = pushing; }

    public void setSorting(boolean sorting) { this.sorting = sorting; }

    public int data(int index) {
        return switch (index) {
            case 0 -> pulling ? 1 : 0;
            case 1 -> pushing ? 1 : 0;
            case 2 -> sorting ? 1 : 0;
            default -> 0;
        };
    }

    /**
     * Evens out the {@code lanes} slots from {@code base}: every stack spills half of what it holds
     * over an empty slot, or over a shorter stack of the same item, into that slot. A stack never
     * empties itself and never changes what a lane holds, so the jobs under way carry on; a big
     * stack settles over a few ticks. True when anything moved.
     */
    public static boolean balance(List<ItemStack> items, int base, int lanes) {
        boolean moved = false;
        for (int from = 0; from < lanes; from++) {
            ItemStack stack = items.get(base + from);
            for (int to = 0; to < lanes && stack.getCount() > 1; to++) {
                if (to == from) continue;
                ItemStack target = items.get(base + to);
                if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, stack)) continue;
                int share = (stack.getCount() - target.getCount()) / 2;
                if (share < 1) continue;
                if (target.isEmpty()) items.set(base + to, stack.split(share));
                else {
                    target.grow(share);
                    stack.shrink(share);
                }
                moved = true;
            }
        }
        return moved;
    }

    public void save(ValueOutput output) {
        output.putBoolean(TAG_PULL, pulling);
        output.putBoolean(TAG_PUSH, pushing);
        output.putBoolean(TAG_SORT, sorting);
    }

    public void load(ValueInput input) {
        pulling = input.getBooleanOr(TAG_PULL, false);
        pushing = input.getBooleanOr(TAG_PUSH, false);
        sorting = input.getBooleanOr(TAG_SORT, false);
    }
}
