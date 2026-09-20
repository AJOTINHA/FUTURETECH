package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.item.PortableTeleporterItem;
import dev.futuretech.teleport.PanelView;
import dev.futuretech.teleport.PortableTeleporterPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The portable teleporter's screen: the destinations of the panel it is linked to, priced from
 * where the player stands, in the rows the network panel uses. A click goes; a card whose pad is
 * gone is red and takes no click. There is no menu under it — the list came whole from the
 * server, and the trip is one packet back — so it is a plain screen over the world.
 */
public final class PortableTeleporterScreen extends Screen {
    private static final int WIDTH = 176;
    private static final int LABEL_X = 7;
    private static final int ROW_X = 8;
    private static final int ROW_Y = 22;
    private static final int ROW_WIDTH = WIDTH - 2 * ROW_X;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_SPACING = 18;
    private static final int VISIBLE = 5;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_GUTTER = SCROLL_WIDTH + 2;
    private static final int ENERGY_Y = ROW_Y + VISIBLE * ROW_SPACING + 2;
    private static final int HEIGHT = ENERGY_Y + 16;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int UNREACHABLE = 0xFFB03A2E;
    private static final int MUTED = 0xFF8B959F;
    private static final int SCROLL_TRACK = 0xFF3E4752;
    private static final int SCROLL_KNOB = 0xFF8B959F;

    private final InteractionHand hand;
    private final List<PanelView.Card> cards;
    private final int energy;
    private int scrollRow;
    private int left;
    private int top;

    private PortableTeleporterScreen(InteractionHand hand, List<PanelView.Card> cards, int energy) {
        super(Component.translatable("item.futuretech.portable_teleporter"));
        this.hand = hand;
        this.cards = cards;
        this.energy = energy;
    }

    public static void open(PortableTeleporterPayloads.Open payload) {
        Minecraft.getInstance().gui.setScreen(new PortableTeleporterScreen(payload.hand(), payload.cards(), payload.energy()));
    }

    @Override
    protected void init() {
        super.init();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    private int maxScroll() { return Math.max(0, cards.size() - VISIBLE); }

    private int rowWidth() { return maxScroll() > 0 ? ROW_WIDTH - SCROLL_GUTTER : ROW_WIDTH; }

    private static int rowY(int row) { return ROW_Y + row * ROW_SPACING; }

    /** The card under the pointer, already scrolled, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - left;
        int localY = (int) mouseY - top;
        if (localX < ROW_X || localX >= ROW_X + rowWidth()) return -1;
        for (int row = 0; row < VISIBLE; row++) {
            int y = rowY(row);
            if (localY >= y && localY < y + ROW_HEIGHT) {
                int index = scrollRow + row;
                return index < cards.size() ? index : -1;
            }
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, left, top, WIDTH, HEIGHT);
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
        int width = rowWidth();
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= cards.size()) break;
            int rowX = left + ROW_X;
            int rowTop = top + rowY(row);
            graphics.fill(rowX - 1, rowTop - 1, rowX + width + 1, rowTop + ROW_HEIGHT + 1, ROW_BACK);
            graphics.fill(rowX, rowTop, rowX + width, rowTop + ROW_HEIGHT, ROW_FACE);
            if (index == hovered && cards.get(index).present()) graphics.fill(rowX, rowTop, rowX + width, rowTop + ROW_HEIGHT, 0x40FFFFFF);
        }
        if (maxScroll() > 0) {
            int barX = left + ROW_X + ROW_WIDTH - SCROLL_WIDTH;
            int barTop = top + ROW_Y - 1;
            int barHeight = VISIBLE * ROW_SPACING;
            graphics.fill(barX, barTop, barX + SCROLL_WIDTH, barTop + barHeight, SCROLL_TRACK);
            int knob = Math.max(12, barHeight * VISIBLE / Math.max(1, cards.size()));
            int knobY = barTop + (barHeight - knob) * scrollRow / Math.max(1, maxScroll());
            graphics.fill(barX, knobY, barX + SCROLL_WIDTH, knobY + knob, SCROLL_KNOB);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, left + LABEL_X, top + 6, TITLE, false);
        if (cards.isEmpty()) {
            graphics.text(font, Component.translatable("item.futuretech.portable_teleporter.nowhere"), left + ROW_X + 4, top + rowY(0) + 5, MUTED, false);
        }
        int width = rowWidth();
        for (int row = 0; row < VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= cards.size()) break;
            PanelView.Card card = cards.get(index);
            int textY = top + rowY(row) + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            String cost = String.format("%,d FE", card.cost());
            int costWidth = font.width(cost);
            // Red for a pad that is gone, and for a fare the buffer does not cover.
            int colour = card.present() && card.cost() <= energy ? TITLE : UNREACHABLE;
            graphics.text(font, font.plainSubstrByWidth(card.name(), width - costWidth - 12), left + ROW_X + 4, textY, colour, false);
            graphics.text(font, cost, left + ROW_X + width - 4 - costWidth, textY, colour, false);
        }
        graphics.text(font, Component.translatable("gui.futuretech.stored", String.format("%,d", energy),
                String.format("%,d", PortableTeleporterItem.CAPACITY)), left + LABEL_X, top + ENERGY_Y, TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            PanelView.Card card = cards.get(index);
            if (!card.present() || card.cost() > energy) return true;
            ClientPacketDistributor.sendToServer(new PortableTeleporterPayloads.Go(hand, card.source(), card.slot()));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
