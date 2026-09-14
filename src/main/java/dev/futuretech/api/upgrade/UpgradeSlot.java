package dev.futuretech.api.upgrade;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * A menu slot inside the upgrade tab. It only shows and reacts on the client while the tab is
 * fully open; the screen supplies that state through {@link #setActive}. A slot the machine's MK
 * has not unlocked yet takes nothing, though whatever it holds can still be taken out.
 */
public final class UpgradeSlot extends Slot {
    private final UpgradeInventory upgrades;
    private BooleanSupplier active = () -> true;

    public UpgradeSlot(UpgradeInventory upgrades, int index, int x, int y) {
        super(upgrades, index, x, y);
        this.upgrades = upgrades;
    }

    public boolean isUnlocked() { return upgrades.isUnlocked(getContainerSlot()); }

    public void setActive(BooleanSupplier active) { this.active = active; }

    @Override
    public boolean isActive() { return active.getAsBoolean(); }

    @Override
    public boolean mayPlace(ItemStack stack) { return isUnlocked() && UpgradeInventory.isUpgrade(stack); }

    @Override
    public int getMaxStackSize() { return 1; }
}
