package dev.futuretech.teleport;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.item.PortableTeleporterItem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The network panel's link slots, shared by its menu (which makes the slots) and its link tab
 * (which draws around them): a portable teleporter put in the top one comes out of the bottom
 * one linked to the panel. Menu slots cannot move, so the tab is the first on the strip and
 * pins its width, the way the upgrade tab does.
 */
public final class LinkSlots {
    public static final int TAB_INDEX = 0;
    public static final int IN = 0;
    public static final int OUT = 1;
    public static final int SLOTS = 2;
    public static final int PADDING = 6;
    /** Wide enough for the title on every supported language; the slots sit in the middle of it. */
    public static final int CONTENT_WIDTH = 60;
    public static final int TAB_WIDTH = PADDING * 2 + CONTENT_WIDTH;
    /** Between the two slots: a gap, the arrow, a gap. */
    public static final int ARROW_HEIGHT = 8;
    public static final int GAP = 3;
    public static final int CONTENT_HEIGHT = 18 + GAP + ARROW_HEIGHT + GAP + 18;

    private LinkSlots() {}

    /** Slot x relative to the screen's left edge, for a screen {@code imageWidth} wide: centred in the tab. */
    public static int slotX(int imageWidth) {
        return imageWidth - 2 + PADDING + (CONTENT_WIDTH - 16) / 2;
    }

    /** Slot y relative to the screen's top edge, for the in slot (0) or the out slot (1). */
    public static int slotY(int index) {
        int top = TabStrip.TOP_OFFSET + TAB_INDEX * (MachineTab.SIZE + TabStrip.SPACING) + MachineTab.SIZE + PADDING + 1;
        return index == IN ? top : top + 18 + GAP + ARROW_HEIGHT + GAP;
    }

    public static void addSlots(Container link, int imageWidth, Consumer<LinkSlot> add) {
        add.accept(new LinkSlot(link, IN, slotX(imageWidth), slotY(IN)));
        add.accept(new LinkSlot(link, OUT, slotX(imageWidth), slotY(OUT)));
    }

    /** Points every link slot of the menu at the tab's open state. */
    public static void bind(AbstractContainerMenu menu, BooleanSupplier active) {
        for (var slot : menu.slots) {
            if (slot instanceof LinkSlot linkSlot) linkSlot.setActive(active);
        }
    }

    /** A link slot: the in slot takes a portable teleporter, the out slot only gives. Both show only while the tab is open. */
    public static final class LinkSlot extends Slot {
        private BooleanSupplier active = () -> true;

        LinkSlot(Container link, int index, int x, int y) {
            super(link, index, x, y);
        }

        public void setActive(BooleanSupplier active) { this.active = active; }

        @Override
        public boolean isActive() { return active.getAsBoolean(); }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() == IN && stack.getItem() instanceof PortableTeleporterItem;
        }

        @Override
        public int getMaxStackSize() { return 1; }
    }
}
