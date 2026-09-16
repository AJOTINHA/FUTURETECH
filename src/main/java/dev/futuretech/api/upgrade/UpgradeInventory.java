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

import java.util.function.IntSupplier;

/**
 * The upgrade slots of one machine. Only items in the {@code futuretech:upgrades} tag fit, one per
 * slot, and only the first {@link #unlocked()} slots take anything: a machine unlocks as many as
 * its MK level, so a plain MK1 has one and an MK4 all four. Upgrade items can be installed and
 * saved; what they do is not implemented yet. The level's own gains are in {@link MachineLevel}.
 */
public final class UpgradeInventory extends SimpleContainer {
    public static final int SLOTS = 4;
    public static final TagKey<Item> UPGRADES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("futuretech", "upgrades"));
    private static final String TAG = "Upgrades";

    private final IntSupplier unlocked;
    private final Runnable onChanged;

    /** Every slot unlocked; for menus on the client and tests. */
    public UpgradeInventory(Runnable onChanged) {
        this(() -> SLOTS, onChanged);
    }

    public UpgradeInventory(IntSupplier unlocked, Runnable onChanged) {
        super(SLOTS);
        this.unlocked = unlocked;
        this.onChanged = onChanged;
    }

    /** How many slots, counted from the first, take upgrades right now. */
    public int unlocked() { return Math.clamp(unlocked.getAsInt(), 1, SLOTS); }

    public boolean isUnlocked(int slot) { return slot >= 0 && slot < unlocked(); }

    /** Upgrades sitting in unlocked slots. */
    public int installed() {
        int count = 0;
        for (int slot = 0; slot < unlocked(); slot++) if (isUpgrade(getItem(slot))) count++;
        return count;
    }

    public static boolean isUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UPGRADES);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return isUnlocked(slot) && isUpgrade(stack); }

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
