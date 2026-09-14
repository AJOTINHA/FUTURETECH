package dev.futuretech.transfer;

import dev.futuretech.item.ItemFilterItem;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The filter card of each of one cable's six connectors, slot {@code side.ordinal()} for the
 * connector on {@code side}. Only an {@link ItemFilterItem} fits, one per slot.
 */
public final class ConnectorFilters extends SimpleContainer {
    private static final String TAG = "Filters";

    private final Runnable onChanged;

    public ConnectorFilters(Runnable onChanged) {
        super(Direction.values().length);
        this.onChanged = onChanged;
    }

    /** The card on {@code side}'s connector, or an empty stack when there is none. */
    public ItemStack on(Direction side) { return getItem(side.ordinal()); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return ItemFilterItem.isFilter(stack); }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public void setChanged() {
        super.setChanged();
        onChanged.run();
    }

    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child(TAG), items());
    }

    public void load(ValueInput input) {
        NonNullList<ItemStack> loaded = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(TAG), loaded);
        for (int slot = 0; slot < loaded.size(); slot++) setItem(slot, loaded.get(slot));
    }

    private NonNullList<ItemStack> items() {
        NonNullList<ItemStack> list = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < list.size(); slot++) list.set(slot, getItem(slot));
        return list;
    }
}
