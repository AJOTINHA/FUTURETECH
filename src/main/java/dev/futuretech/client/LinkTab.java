package dev.futuretech.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.teleport.LinkSlots;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The network panel's link tab: a slot, an arrow down, a slot. A portable teleporter dropped in
 * the top one comes out of the bottom one linked to this panel. The slots themselves belong to
 * the menu (see {@link LinkSlots}); this tab draws their frames and switches them on while it
 * is fully open, the way the upgrade tab does.
 */
public final class LinkTab extends MachineTab {
    private static final int ARROW_COLOR = 0xFF8B959F;

    public LinkTab(AbstractContainerMenu menu, Font font) {
        super(font);
        LinkSlots.bind(menu, this::isFullyOpen);
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.link"); }

    @Override
    protected int contentWidth() { return LinkSlots.CONTENT_WIDTH; }

    @Override
    protected int contentHeight() { return LinkSlots.CONTENT_HEIGHT; }

    /** Pinned so the slots the menu placed stay under the frames this tab draws. */
    @Override
    protected int fullWidth() { return LinkSlots.TAB_WIDTH; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // Two links of a chain, one over the other, centred in the 20 px square.
        graphics.fill(x + 5, y + 6, x + 12, y + 7, TITLE_COLOR);
        graphics.fill(x + 5, y + 7, x + 6, y + 10, TITLE_COLOR);
        graphics.fill(x + 11, y + 7, x + 12, y + 10, TITLE_COLOR);
        graphics.fill(x + 5, y + 10, x + 12, y + 11, TITLE_COLOR);
        graphics.fill(x + 8, y + 9, x + 15, y + 10, 0xFFEC761C);
        graphics.fill(x + 8, y + 10, x + 9, y + 13, 0xFFEC761C);
        graphics.fill(x + 14, y + 10, x + 15, y + 13, 0xFFEC761C);
        graphics.fill(x + 8, y + 13, x + 15, y + 14, 0xFFEC761C);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        int slotX = contentX + (LinkSlots.CONTENT_WIDTH - 16) / 2;
        MachineScreenStyle.drawSlot(graphics, slotX, contentY + 1);
        MachineScreenStyle.drawSlot(graphics, slotX, contentY + 1 + 18 + LinkSlots.GAP + LinkSlots.ARROW_HEIGHT + LinkSlots.GAP);
        // The arrow: a two pixel shaft into a head three pixels tall, pointing at the out slot.
        int arrowX = slotX + 8;
        int arrowY = contentY + 1 + 18 + LinkSlots.GAP;
        graphics.fill(arrowX - 1, arrowY, arrowX + 1, arrowY + 5, ARROW_COLOR);
        graphics.fill(arrowX - 3, arrowY + 5, arrowX + 3, arrowY + 6, ARROW_COLOR);
        graphics.fill(arrowX - 2, arrowY + 6, arrowX + 2, arrowY + 7, ARROW_COLOR);
        graphics.fill(arrowX - 1, arrowY + 7, arrowX + 1, arrowY + 8, ARROW_COLOR);
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        // Clicks on the slots are handled by the container screen itself.
        return false;
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}
}
