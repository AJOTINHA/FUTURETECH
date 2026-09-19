package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.menu.NetworkPanelMenu;
import dev.futuretech.teleport.PanelView;
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
 * The teleporter's destinations, seen from the panel: one row per written card the pad on the
 * panel's cables can send to — its own, then the storages' — empty slots left out. Clicking a
 * row sets that card as the pad's destination,
 * the same card again unpicking it, the way the pad's own screen does. The pencil beside a row
 * opens that card in its own screen, to rename it and to colour the pad's beam for it.
 *
 * <p>Nothing here is a slot, so the list scrolls freely: what the rows stand for lives in a block
 * far away and arrives as a view, not as container contents.
 */
public final class NetworkPanelScreen extends AbstractContainerScreen<NetworkPanelMenu> implements TabbedScreen {
    private static final int LABEL_X = 7;
    private static final int ROW_X = NetworkPanelMenu.ROW_X;
    private static final int ROW_WIDTH = 152;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_GUTTER = SCROLL_WIDTH + 2;
    private static final int ROW_HEIGHT = 16;
    /** The pencil: a slot-sized button past the row, clear of the scrollbar. */
    private static final int PENCIL_X = ROW_X + ROW_WIDTH + 4;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int SELECTED = 0xFF55E7ED;
    private static final int UNREACHABLE = 0xFFB03A2E;
    private static final int MUTED = 0xFF8B959F;
    private static final int SCROLL_TRACK = 0xFF3E4752;
    private static final int SCROLL_KNOB = 0xFF8B959F;
    private static final int MIN_KNOB = 12;

    private int scrollRow;
    private boolean draggingKnob;
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }

    public NetworkPanelScreen(NetworkPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, NetworkPanelMenu.IMAGE_WIDTH, NetworkPanelMenu.INVENTORY_Y + 82);
        titleLabelX = LABEL_X;
        titleLabelY = 6;
        inventoryLabelX = NetworkPanelMenu.INVENTORY_X - 1;
        inventoryLabelY = NetworkPanelMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new LinkTab(menu, font));
    }

    private PanelView.@org.jspecify.annotations.Nullable Pad pad() { return menu.view().padOrNull(); }

    private List<PanelView.Card> cards() {
        var pad = pad();
        return pad == null ? List.of() : pad.cards();
    }

    private int rows() { return cards().size(); }

    private int maxScroll() { return Math.max(0, rows() - NetworkPanelMenu.VISIBLE); }

    private int rowWidth() { return maxScroll() > 0 ? ROW_WIDTH - SCROLL_GUTTER : ROW_WIDTH; }

    private static int rowY(int row) { return NetworkPanelMenu.ROW_Y + row * NetworkPanelMenu.ROW_SPACING; }

    /** The row of the list under the pointer, already scrolled, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos;
        if (localX < ROW_X || localX >= ROW_X + rowWidth()) return -1;
        return rowIn(mouseY);
    }

    /** The pencil under the pointer, as the row it belongs to, or -1. */
    private int pencilAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos;
        if (localX < PENCIL_X || localX >= PENCIL_X + PENCIL_WIDTH) return -1;
        return rowIn(mouseY);
    }

    /** The list row whose band holds {@code mouseY}, already scrolled, or -1 past the list. */
    private int rowIn(double mouseY) {
        int localY = (int) mouseY - topPos;
        for (int row = 0; row < NetworkPanelMenu.VISIBLE; row++) {
            int y = rowY(row);
            if (localY >= y && localY < y + ROW_HEIGHT) {
                int index = scrollRow + row;
                return index < rows() ? index : -1;
            }
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
        int width = rowWidth();
        int hovered = rowAt(mouseX, mouseY);
        int hoveredPencil = pencilAt(mouseX, mouseY);
        var pad = pad();
        for (int row = 0; row < NetworkPanelMenu.VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= rows()) break;
            int rowX = x + ROW_X;
            int top = y + rowY(row);
            boolean chosen = pad != null && pad.chose(cards().get(index));
            graphics.fill(rowX - 1, top - 1, rowX + width + 1, top + ROW_HEIGHT + 1, chosen ? SELECTED : ROW_BACK);
            graphics.fill(rowX, top, rowX + width, top + ROW_HEIGHT, ROW_FACE);
            if (index == hovered) graphics.fill(rowX, top, rowX + width, top + ROW_HEIGHT, 0x40FFFFFF);
            drawPencil(graphics, x + PENCIL_X, top, index == hoveredPencil);
        }
        if (maxScroll() > 0) drawScrollbar(graphics, x, y);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        int barX = x + ROW_X + ROW_WIDTH - SCROLL_WIDTH;
        int top = y + NetworkPanelMenu.ROW_Y - 1;
        int height = NetworkPanelMenu.VISIBLE * NetworkPanelMenu.ROW_SPACING;
        graphics.fill(barX, top, barX + SCROLL_WIDTH, top + height, SCROLL_TRACK);
        int knob = Math.max(MIN_KNOB, height * NetworkPanelMenu.VISIBLE / Math.max(1, rows()));
        int knobY = top + (height - knob) * scrollRow / Math.max(1, maxScroll());
        graphics.fill(barX, knobY, barX + SCROLL_WIDTH, knobY + knob, SCROLL_KNOB);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var pad = pad();
        if (pad == null || pad.cards().isEmpty()) {
            // Three different things to go and fix, so they are three different messages: no cable
            // is touching the panel at all, the cables run but reach no pad, or the pad has no card.
            Component reason = menu.view().unplugged()
                    ? Component.translatable("gui.futuretech.network_panel.unplugged")
                    : pad == null
                    ? Component.translatable("gui.futuretech.network_panel.empty", menu.view().cables())
                    : Component.translatable("gui.futuretech.network_panel.no_cards", pad.name());
            graphics.text(font, reason, ROW_X + 4, rowY(0) + 5, MUTED, false);
            return;
        }
        int width = rowWidth();
        for (int row = 0; row < NetworkPanelMenu.VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= rows()) break;
            int textY = rowY(row) + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            drawCardRow(graphics, cards().get(index), textY, width);
        }
    }

    /** A card's row: its name and what that trip costs the pad, in the teleporter's own wording. */
    private void drawCardRow(GuiGraphicsExtractor graphics, PanelView.Card card, int textY, int width) {
        String cost = String.format("%,d FE", card.cost());
        int costWidth = font.width(cost);
        int colour = card.selectable() ? TITLE : UNREACHABLE;
        graphics.text(font, font.plainSubstrByWidth(card.name(), width - costWidth - 12), ROW_X + 4, textY, colour, false);
        graphics.text(font, cost, ROW_X + width - 4 - costWidth, textY, colour, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        if (maxScroll() > 0 && isOverScrollbar(event.x(), event.y())) {
            draggingKnob = true;
            dragTo(event.y());
            return true;
        }
        int pencil = pencilAt(event.x(), event.y());
        if (pencil >= 0) {
            var card = cards().get(pencil);
            Minecraft.getInstance().gui.pushScreenLayer(new CardEditScreen(card.name(), card.colour(),
                    (name, colour) -> NetworkPanelMenu.sendEdit(card, name, colour)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            if (!cards().get(index).present()) return true;
            NetworkPanelMenu.sendPick(cards().get(index));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int barX = leftPos + ROW_X + ROW_WIDTH - SCROLL_WIDTH;
        int top = topPos + NetworkPanelMenu.ROW_Y - 1;
        int height = NetworkPanelMenu.VISIBLE * NetworkPanelMenu.ROW_SPACING;
        return mouseX >= barX && mouseX < barX + SCROLL_WIDTH && mouseY >= top && mouseY < top + height;
    }

    private void dragTo(double mouseY) {
        int height = NetworkPanelMenu.VISIBLE * NetworkPanelMenu.ROW_SPACING;
        int knob = Math.max(MIN_KNOB, height * NetworkPanelMenu.VISIBLE / Math.max(1, rows()));
        int travel = height - knob;
        if (travel <= 0) return;
        double offset = mouseY - topPos - (NetworkPanelMenu.ROW_Y - 1) - knob / 2.0;
        scrollRow = Math.clamp((int) Math.round(offset * maxScroll() / travel), 0, maxScroll());
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
