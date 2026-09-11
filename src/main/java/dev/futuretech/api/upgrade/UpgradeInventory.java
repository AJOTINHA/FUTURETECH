package dev.futuretech.api.upgrade;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The upgrade slots of one machine. Only items in the {@code futuretech:upgrades} tag fit, one per
 * slot. Upgrades have no effect yet; machines will read them once upgrade items exist.
 */
public final class UpgradeInventory extends SimpleContainer {
    public static final int SLOTS = 4;
    public static final TagKey<Item> UPGRADES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("futuretech", "upgrades"));
    private static final String TAG = "Upgrades";

    private final Runnable onChanged;

    public UpgradeInventory(Runnable onChanged) {
        super(SLOTS);
        this.onChanged = onChanged;
    }

    public static boolean isUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UPGRADES);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return isUpgrade(stack); }

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
        NonNullList<ItemStack> loaded = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(TAG), loaded);
        for (int slot = 0; slot < SLOTS; slot++) setItem(slot, loaded.get(slot));
    }

    private NonNullList<ItemStack> items() {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) list.set(slot, getItem(slot));
        return list;
    }
}
