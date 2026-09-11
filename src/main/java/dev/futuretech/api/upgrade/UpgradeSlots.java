package dev.futuretech.api.upgrade;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.gui.TabStrip;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Consumer;

/**
 * Where the upgrade slots sit, shared by menus (which create the slots) and the upgrade tab (which
 * draws around them). Menu slots cannot move, so the tab must be the first one on the strip, where
 * nothing above it can push it down.
 */
public final class UpgradeSlots {
    /** Zero-based position of the upgrade tab on the strip. */
    public static final int TAB_INDEX = 0;
    public static final int CELL = 20;
    public static final int PADDING = 6;
    /** Width of the row of slots: four 16 px slots with 1 px borders and 2 px between them. */
    public static final int ROW = CELL * UpgradeInventory.SLOTS - (CELL - 16) + 2;
    /** Full tab width; wide enough for the title on every supported language. */
    public static final int TAB_WIDTH = PADDING * 2 + ROW;

    /** Slot x relative to the screen's left edge, for a screen {@code imageWidth} wide. */
    public static int slotX(int imageWidth, int index) {
        return imageWidth - 2 + PADDING + 1 + index * CELL;
    }

    /** Slot y relative to the screen's top edge. */
    public static int slotY() {
        return TabStrip.TOP_OFFSET + TAB_INDEX * (MachineTab.SIZE + TabStrip.SPACING) + MachineTab.SIZE + PADDING + 1;
    }

    /** Adds the four upgrade slots to a menu, in slot order. */
    public static void addSlots(UpgradeInventory upgrades, int imageWidth, Consumer<UpgradeSlot> add) {
        for (int index = 0; index < UpgradeInventory.SLOTS; index++) {
            add.accept(new UpgradeSlot(upgrades, index, slotX(imageWidth, index), slotY()));
        }
    }

    /** Points every upgrade slot of the menu at the tab's open state. */
    public static void bind(AbstractContainerMenu menu, java.util.function.BooleanSupplier active) {
        for (var slot : menu.slots) {
            if (slot instanceof UpgradeSlot upgradeSlot) upgradeSlot.setActive(active);
        }
    }

    private UpgradeSlots() {}
}
