package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.StorageCardsMenu;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The card storage's screen: one row per card slot in the window, the slot on the left and the
 * card's name and where it points beside it, with a scrollbar down the right for the rest of the
 * shelf and a pencil past each row to rename the card and colour the beam for it. Nothing here is
 * picked — the pads and the panel do the picking; this is where the cards are put.
 */
public final class StorageCardsScreen extends AbstractContainerScreen<StorageCardsMenu> implements TabbedScreen {
    private static final int LABEL_X = 7;
    /** Each card's row beside its slot: from the slot's right edge to the scrollbar. */
    private static final int ROW_X = StorageCardsMenu.CARD_X + 22;
    private static final int ROW_WIDTH = 122;
    private static final int ROW_HEIGHT = 16;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_X = ROW_X + ROW_WIDTH + 2;
    /** The pencil: a slot-sized button past the scrollbar, with the rows' own margin to the edge. */
    private static final int PENCIL_X = SCROLL_X + SCROLL_WIDTH + 4;
    private static final int SCROLL_TOP = StorageCardsMenu.CARD_Y - 1;
    private static final int SCROLL_HEIGHT = StorageCardsMenu.VISIBLE * StorageCardsMenu.CARD_SPACING;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int MUTED = 0xFF8B959F;
    private static final int SCROLL_TRACK = 0xFF3E4752;
    private static final int SCROLL_KNOB = 0xFF8B959F;
    private static final int SCROLL_KNOB_HELD = 0xFFB8C2CC;
    private static final int MIN_KNOB = 12;

    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }
    private boolean draggingKnob;

    public StorageCardsScreen(StorageCardsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StorageCardsMenu.IMAGE_WIDTH, StorageCardsMenu.INVENTORY_Y + 82);
        titleLabelX = LABEL_X;
        inventoryLabelX = StorageCardsMenu.INVENTORY_X - 1;
        inventoryLabelY = StorageCardsMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new UpgradeTab(menu, font));
    }

    private static int rowY(int row) { return StorageCardsMenu.CARD_Y + row * StorageCardsMenu.CARD_SPACING; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        for (int row = 0; row < StorageCardsMenu.VISIBLE; row++) {
            // The last window of a level whose count is not a multiple of four runs off the end.
            if (menu.cardAt(row) >= menu.cards()) continue;
            int rowX = x + ROW_X;
            int top = y + rowY(row);
            graphics.fill(rowX - 1, top - 1, rowX + ROW_WIDTH + 1, top + ROW_HEIGHT + 1, ROW_BACK);
            graphics.fill(rowX, top, rowX + ROW_WIDTH, top + ROW_HEIGHT, ROW_FACE);
            drawPencil(graphics, x + PENCIL_X, top, isOverPencil(row, mouseX, mouseY));
        }
        if (menu.scrollable()) drawScrollbar(graphics, x, y);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** The knob's height: the share of the cards on screen, never too small to grab. */
    private int knobHeight() {
        return Math.max(MIN_KNOB, SCROLL_HEIGHT * StorageCardsMenu.VISIBLE / menu.cards());
    }

    private int knobY() {
        int travel = SCROLL_HEIGHT - knobHeight();
        return SCROLL_TOP + (menu.maxScroll() == 0 ? 0 : travel * menu.scrollRow() / menu.maxScroll());
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        int barX = x + SCROLL_X;
        int top = y + SCROLL_TOP;
        graphics.fill(barX, top, barX + SCROLL_WIDTH, top + SCROLL_HEIGHT, SCROLL_TRACK);
        int knobY = y + knobY();
        graphics.fill(barX, knobY, barX + SCROLL_WIDTH, knobY + knobHeight(),
                draggingKnob ? SCROLL_KNOB_HELD : SCROLL_KNOB);
    }

    private boolean isOverRow(int row, int mouseX, int mouseY) {
        int rowX = leftPos + ROW_X;
        int top = topPos + rowY(row);
        return mouseX >= rowX && mouseX < rowX + ROW_WIDTH && mouseY >= top && mouseY < top + ROW_HEIGHT;
    }

    private boolean isOverPencil(int row, double mouseX, double mouseY) {
        int x = leftPos + PENCIL_X;
        int top = topPos + rowY(row);
        return mouseX >= x && mouseX < x + PENCIL_WIDTH && mouseY >= top && mouseY < top + ROW_HEIGHT;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (!menu.scrollable()) return false;
        int barX = leftPos + SCROLL_X;
        int top = topPos + SCROLL_TOP;
        return mouseX >= barX && mouseX < barX + SCROLL_WIDTH && mouseY >= top && mouseY < top + SCROLL_HEIGHT;
    }

    /** Scrolls so the knob's middle sits under the pointer. */
    private void dragTo(double mouseY) {
        int travel = SCROLL_HEIGHT - knobHeight();
        if (travel <= 0) return;
        double offset = mouseY - topPos - SCROLL_TOP - knobHeight() / 2.0;
        scrollTo((int) Math.round(offset * menu.maxScroll() / travel));
    }

    /**
     * Moves the window on both sides. The button goes out before whatever click follows it, on the
     * same connection, so the server has already moved the rows by the time it resolves that click.
     */
    private void scrollTo(int row) {
        int clamped = Math.clamp(row, 0, menu.maxScroll());
        if (clamped == menu.scrollRow()) return;
        menu.scrollTo(clamped);
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) {
            gameMode.handleInventoryButtonClick(menu.containerId, StorageCardsMenu.SCROLL_BASE + clamped);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        for (int row = 0; row < StorageCardsMenu.VISIBLE; row++) {
            if (menu.cardAt(row) >= menu.cards()) continue;
            TeleportTarget target = menu.rowCard(row);
            int textY = rowY(row) + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            if (target == null) {
                graphics.text(font, Component.translatable("gui.futuretech.teleporter.empty"), ROW_X + 4, textY, MUTED, false);
                continue;
            }
            // The name, and where it points on the right: no cost here, that is a pad's sum.
            var pos = target.pos().pos();
            String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            int whereWidth = font.width(where);
            graphics.text(font, font.plainSubstrByWidth(target.name(), ROW_WIDTH - whereWidth - 12), ROW_X + 4, textY, TITLE, false);
            graphics.text(font, where, ROW_X + ROW_WIDTH - 4 - whereWidth, textY, MUTED, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        tabs.extractTooltip(graphics, mouseX, mouseY);
        for (int row = 0; row < StorageCardsMenu.VISIBLE; row++) {
            TeleportTarget target = menu.rowCard(row);
            if (target == null || !isOverRow(row, mouseX, mouseY)) continue;
            var pos = target.pos().pos();
            graphics.setTooltipForNextFrame(List.of(
                    Component.literal(target.name()).getVisualOrderText(),
                    Component.translatable("item.futuretech.teleport_card.target", pos.getX(), pos.getY(), pos.getZ(),
                            target.dimensionName()).getVisualOrderText()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        if (isOverScrollbar(event.x(), event.y())) {
            draggingKnob = true;
            dragTo(event.y());
            return true;
        }
        for (int row = 0; row < StorageCardsMenu.VISIBLE; row++) {
            TeleportTarget target = menu.rowCard(row);
            if (target == null || menu.cardAt(row) >= menu.cards() || !isOverPencil(row, event.x(), event.y())) continue;
            // The slot is pinned now: the window may scroll while the screen above is open.
            int slot = menu.cardAt(row);
            Minecraft.getInstance().gui.pushScreenLayer(new CardEditScreen(target.name(), target.colour(),
                    (name, colour) -> StorageCardsMenu.sendEdit(slot, name, colour)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingKnob) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingKnob = false;
        return super.mouseReleased(event);
    }

    /** The wheel scrolls anywhere over the window, not only on the bar. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.scrollable() && scrollY != 0) {
            scrollTo(menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
