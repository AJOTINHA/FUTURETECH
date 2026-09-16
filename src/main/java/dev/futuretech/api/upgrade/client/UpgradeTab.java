package dev.futuretech.api.upgrade.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlot;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.client.MachineScreenStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Upgrade tab: a row of slots for upgrade items. The slots themselves belong to the menu (see
 * {@link UpgradeSlots}); this tab draws their frames and switches them on while it is fully open.
 */
public final class UpgradeTab extends MachineTab {
    private static final int LOCK_COLOR = 0xFF56616D;
    private static final int LOCK_SHADE = 0x90232B33;

    private final AbstractContainerMenu menu;

    public UpgradeTab(AbstractContainerMenu menu, Font font) {
        super(font);
        this.menu = menu;
        UpgradeSlots.bind(menu, this::isFullyOpen);
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.upgrades"); }

    @Override
    protected int contentWidth() { return UpgradeSlots.ROW; }

    @Override
    protected int contentHeight() { return 18; }

    /** Pinned so the slots the menu placed stay under the frames this tab draws. */
    @Override
    protected int fullWidth() { return UpgradeSlots.TAB_WIDTH; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // An upward arrow: the shaft and a three-step head, centred in the 20 px square.
        graphics.fill(x + 9, y + 8, x + 11, y + 15, TITLE_COLOR);
        graphics.fill(x + 8, y + 7, x + 12, y + 8, TITLE_COLOR);
        graphics.fill(x + 7, y + 6, x + 13, y + 7, TITLE_COLOR);
        graphics.fill(x + 9, y + 5, x + 11, y + 6, 0xFFEC761C);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (int index = 0; index < UpgradeInventory.SLOTS; index++) {
            int x = contentX + 1 + index * UpgradeSlots.CELL;
            MachineScreenStyle.drawSlot(graphics, x, contentY + 1);
            if (!unlocked(index)) drawLock(graphics, x, contentY + 1);
        }
    }

    /** Whether the machine's MK has opened the slot at {@code index}; the menu's slots know. */
    private boolean unlocked(int index) {
        for (var slot : menu.slots) {
            if (slot instanceof UpgradeSlot upgradeSlot && upgradeSlot.getContainerSlot() == index) {
                return upgradeSlot.isUnlocked();
            }
        }
        return true;
    }

    /** Shades the slot and draws a small padlock over it: a shackle above a body, centred in the 16 px square. */
    private static void drawLock(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, LOCK_SHADE);
        graphics.fill(x + 5, y + 3, x + 11, y + 4, LOCK_COLOR);
        graphics.fill(x + 5, y + 4, x + 6, y + 7, LOCK_COLOR);
        graphics.fill(x + 10, y + 4, x + 11, y + 7, LOCK_COLOR);
        graphics.fill(x + 4, y + 7, x + 12, y + 13, LOCK_COLOR);
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        // Clicks on the slots are handled by the container screen itself.
        return false;
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}
}
