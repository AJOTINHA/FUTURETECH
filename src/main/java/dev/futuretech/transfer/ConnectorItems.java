package dev.futuretech.transfer;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Predicate;

/**
 * One item per connector of a cable, slot {@code side.ordinal()} for the connector on {@code side}:
 * a filter card, a stack of upgrade modules. Only items the predicate accepts fit.
 */
public final class ConnectorItems extends SimpleContainer {
    private final String tag;
    private final Predicate<ItemStack> fits;
    private final int maxPerSlot;
    private final Runnable onChanged;

    /**
     * @param tag        the name the six slots are saved under
     * @param fits       which items a slot takes
     * @param maxPerSlot how many of them one connector holds
     */
    public ConnectorItems(String tag, Predicate<ItemStack> fits, int maxPerSlot, Runnable onChanged) {
        super(Direction.values().length);
        this.tag = tag;
        this.fits = fits;
        this.maxPerSlot = maxPerSlot;
        this.onChanged = onChanged;
    }

    /** The item on {@code side}'s connector, or an empty stack when there is none. */
    public ItemStack on(Direction side) { return getItem(side.ordinal()); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return fits.test(stack); }

    @Override
    public int getMaxStackSize() { return maxPerSlot; }

    @Override
    public void setChanged() {
        super.setChanged();
        onChanged.run();
    }

    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child(tag), items());
    }

    public void load(ValueInput input) {
        NonNullList<ItemStack> loaded = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(tag), loaded);
        for (int slot = 0; slot < loaded.size(); slot++) setItem(slot, loaded.get(slot));
    }

    private NonNullList<ItemStack> items() {
        NonNullList<ItemStack> list = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < list.size(); slot++) list.set(slot, getItem(slot));
        return list;
    }
}
