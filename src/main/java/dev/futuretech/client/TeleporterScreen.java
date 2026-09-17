package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.menu.TeleporterMenu;
import dev.futuretech.teleport.TeleportTarget;
import dev.futuretech.teleport.TeleporterRenamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The pad's name to edit at the top, then one row per card slot: the card's name, what the trip
 * costs, and the row lit when it is the chosen destination. The energy column stands at the
 * right, spanning the rows.
 *
 * <p>The window is always four rows tall, whatever the pad's level holds. A level with more cards
 * than that scrolls, on the wheel or the bar beside the rows, and the rows give up eight pixels of
 * width to make room for it; a pad whose cards all fit shows no bar and keeps the full width.
 */
public final class TeleporterScreen extends AbstractContainerScreen<TeleporterMenu> {
    private static final int LABEL_X = 7;
    private static final int NAME_X = 42;
    private static final int NAME_Y = 17;
    private static final int NAME_WIDTH = 126;
    private static final int NAME_HEIGHT = 12;
    /** Each card's row beside its slot: from the slot's right edge to the energy column. */
    private static final int ROW_X = TeleporterMenu.CARD_X + 22;
    private static final int ROW_FULL_WIDTH = 120;
    private static final int ROW_HEIGHT = 16;
    private static final int SCROLL_WIDTH = 6;
    /** What the rows give up to the bar: its own width plus a pixel of air on either side. */
    private static final int SCROLL_GUTTER = SCROLL_WIDTH + 2;
    private static final int ENERGY_X = 158;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_TOP = TeleporterMenu.CARD_Y - 1;
    private static final int ENERGY_HEIGHT = TeleporterMenu.VISIBLE * TeleporterMenu.CARD_SPACING;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int SELECTED = 0xFF55E7ED;
    private static final int UNREACHABLE = 0xFFB03A2E;
    private static final int SCROLL_TRACK = 0xFF3E4752;
    private static final int SCROLL_KNOB = 0xFF8B959F;
    private static final int SCROLL_KNOB_HELD = 0xFFB8C2CC;
    private static final int MIN_KNOB = 12;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;
    private final int rowWidth;
    private EditBox nameBox;
    private boolean draggingKnob;

    public TeleporterScreen(TeleporterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TeleporterMenu.IMAGE_WIDTH, TeleporterMenu.INVENTORY_Y + 82);
        this.rowWidth = menu.scrollable() ? ROW_FULL_WIDTH - SCROLL_GUTTER : ROW_FULL_WIDTH;
        titleLabelX = LABEL_X;
        inventoryLabelX = LABEL_X;
        inventoryLabelY = TeleporterMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new UpgradeTab(menu, font), new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + NAME_X, topPos + NAME_Y, NAME_WIDTH, NAME_HEIGHT, Component.translatable("gui.futuretech.teleporter.name"));
        nameBox.setMaxLength(TeleporterMenu.NAME_LENGTH);
        nameBox.setValue(menu.name());
        nameBox.setHint(Component.translatable("gui.futuretech.teleporter.name_hint"));
        // Every keystroke goes up: the server keeps the last one, and the name is short.
        nameBox.setResponder(name -> {
            if (name.equals(menu.name())) return;
            menu.setName(name);
            ClientPacketDistributor.sendToServer(new TeleporterRenamePayload(name));
        });
        addRenderableWidget(nameBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        for (int row = 0; row < TeleporterMenu.VISIBLE; row++) {
            // The last window of a level whose count is not a multiple of four runs off the end.
            if (menu.cardAt(row) >= menu.cards()) continue;
            int rowX = x + ROW_X;
            int rowY = y + rowY(row);
            boolean selected = menu.selected() == menu.cardAt(row);
            graphics.fill(rowX - 1, rowY - 1, rowX + rowWidth + 1, rowY + ROW_HEIGHT + 1, selected ? SELECTED : ROW_BACK);
            graphics.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT, ROW_FACE);
            if (menu.rowCard(row) != null && isOverRow(row, mouseX, mouseY)) {
                graphics.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }
        }
        if (menu.scrollable()) drawScrollbar(graphics, x, y);
        int energyTop = y + ENERGY_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    private static int rowY(int row) { return TeleporterMenu.CARD_Y + row * TeleporterMenu.CARD_SPACING; }

    private static int scrollX() { return ROW_X + ROW_FULL_WIDTH - SCROLL_WIDTH; }

    private static int scrollTop() { return TeleporterMenu.CARD_Y - 1; }

    /** The knob's height: the share of the cards on screen, never too small to grab. */
    private int knobHeight() {
        return Math.max(MIN_KNOB, ENERGY_HEIGHT * TeleporterMenu.VISIBLE / menu.cards());
    }

    private int knobY() {
        int travel = ENERGY_HEIGHT - knobHeight();
        return scrollTop() + (menu.maxScroll() == 0 ? 0 : travel * menu.scrollRow() / menu.maxScroll());
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        int barX = x + scrollX();
        int top = y + scrollTop();
        graphics.fill(barX, top, barX + SCROLL_WIDTH, top + ENERGY_HEIGHT, SCROLL_TRACK);
        int knobY = y + knobY();
        graphics.fill(barX, knobY, barX + SCROLL_WIDTH, knobY + knobHeight(),
                draggingKnob ? SCROLL_KNOB_HELD : SCROLL_KNOB);
    }

    private boolean isOverRow(int row, int mouseX, int mouseY) {
        int rowX = leftPos + ROW_X;
        int rowY = topPos + rowY(row);
        return mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (!menu.scrollable()) return false;
        int barX = leftPos + scrollX();
        int top = topPos + scrollTop();
        return mouseX >= barX && mouseX < barX + SCROLL_WIDTH && mouseY >= top && mouseY < top + ENERGY_HEIGHT;
    }

    /** Scrolls so the knob's middle sits under the pointer. */
    private void dragTo(double mouseY) {
        int travel = ENERGY_HEIGHT - knobHeight();
        if (travel <= 0) return;
        double offset = mouseY - topPos - scrollTop() - knobHeight() / 2.0;
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
            gameMode.handleInventoryButtonClick(menu.containerId, TeleporterMenu.SCROLL_BASE + clamped);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.teleporter.name"), LABEL_X, NAME_Y + 2, TEXT, false);
        for (int row = 0; row < TeleporterMenu.VISIBLE; row++) {
            if (menu.cardAt(row) >= menu.cards()) continue;
            TeleportTarget target = menu.rowCard(row);
            int textY = rowY(row) + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            if (target == null) {
                graphics.text(font, Component.translatable("gui.futuretech.teleporter.empty"), ROW_X + 4, textY, 0xFF8B959F, false);
                continue;
            }
            boolean reaches = menu.rowReaches(row);
            String cost = String.format("%,d FE", menu.rowCost(row));
            int costWidth = font.width(cost);
            String name = font.plainSubstrByWidth(target.name(), rowWidth - costWidth - 12);
            int colour = reaches ? TITLE : UNREACHABLE;
            graphics.text(font, name, ROW_X + 4, textY, colour, false);
            graphics.text(font, cost, ROW_X + rowWidth - 4 - costWidth, textY, colour, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        for (int row = 0; row < TeleporterMenu.VISIBLE; row++) {
            TeleportTarget target = menu.rowCard(row);
            if (target == null || !isOverRow(row, mouseX, mouseY)) continue;
            var pos = target.pos().pos();
            var lines = new java.util.ArrayList<>(List.of(
                    Component.literal(target.name()).getVisualOrderText(),
                    Component.translatable("item.futuretech.teleport_card.target", pos.getX(), pos.getY(), pos.getZ(),
                            target.dimensionName()).getVisualOrderText()));
            if (!menu.rowReaches(row)) {
                lines.add(Component.translatable("message.futuretech.teleporter.dimension", TeleporterBlockEntity.CROSS_DIMENSION_LEVEL).getVisualOrderText());
            }
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (isOverScrollbar(event.x(), event.y())) {
            draggingKnob = true;
            dragTo(event.y());
            return true;
        }
        for (int row = 0; row < TeleporterMenu.VISIBLE; row++) {
            if (menu.rowCard(row) == null || !isOverRow(row, mouseX, mouseY)) continue;
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) {
                gameMode.handleInventoryButtonClick(menu.containerId, TeleporterMenu.SELECT_BASE + menu.cardAt(row));
            }
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

    /** While the name box has the focus it takes the keys, the inventory key included; Escape still closes. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return nameBox.keyPressed(event) || nameBox.canConsumeInput() || super.keyPressed(event);
    }
}
