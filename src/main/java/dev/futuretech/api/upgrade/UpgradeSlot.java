package dev.futuretech.api.upgrade;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * A menu slot inside the upgrade tab. It only shows and reacts on the client while the tab is
 * fully open; the screen supplies that state through {@link #setActive}.
 */
public final class UpgradeSlot extends Slot {
    private BooleanSupplier active = () -> true;

    public UpgradeSlot(UpgradeInventory upgrades, int index, int x, int y) {
        super(upgrades, index, x, y);
    }

    public void setActive(BooleanSupplier active) { this.active = active; }

    @Override
    public boolean isActive() { return active.getAsBoolean(); }

    @Override
    public boolean mayPlace(ItemStack stack) { return UpgradeInventory.isUpgrade(stack); }

    @Override
    public int getMaxStackSize() { return 1; }
}
