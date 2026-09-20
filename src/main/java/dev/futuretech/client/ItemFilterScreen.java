package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.block.CableKind;
import dev.futuretech.item.ItemFilterMode;
import dev.futuretech.menu.ItemFilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The filter card's editor: the entries in a grid, the player's inventory to pick items from, and
 * — on an MK1 — the mode button beside the grid. The cards above it hold too long a list to keep
 * a button beside it, so their mode moves into the gear tab on the panel's right edge along with
 * how closely the card reads what it compares and, on the cards that count, the level they keep;
 * see {@link FilterSettingsTab}.
 */
public final class ItemFilterScreen extends AbstractContainerScreen<ItemFilterMenu> implements TabbedScreen {
    private static final int WIDTH = 176;
    private static final int MODE_X = 120;
    private static final int MODE_WIDTH = 48;
    private static final int MODE_HEIGHT = 36;
    /** Centred on the MK1's three rows. */
    private static final int MODE_Y = ItemFilterMenu.GRID_Y + (3 * 18 - MODE_HEIGHT) / 2;
    private static final int WHITELIST_COLOR = CableKind.ITEMS.accent();
    private static final int BLACKLIST_COLOR = 0xFFEC761C;

    /** The gear tab, on the cards that have anything to set behind it; empty on an MK1. */
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }

    public ItemFilterScreen(ItemFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, height(menu));
        titleLabelX = 8;
        inventoryLabelY = menu.inventoryTop() - 12;
        tabs = menu.tier().configurable() ? new TabStrip(new FilterSettingsTab(menu, font)) : new TabStrip();
    }

    private static int height(ItemFilterMenu menu) {
        return menu.inventoryTop() + 3 * 18 + 4 + 18 + 8;
    }

    private boolean hasGear() { return menu.tier().configurable(); }

    private boolean over(int mouseX, int mouseY, int x, int y, int width, int height) {
        return overButton(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(graphics, leftPos, topPos, menu.slots);
        if (!hasGear()) drawMode(graphics, mouseX, mouseY);
        tabs.render(graphics, leftPos, topPos, imageWidth, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    private void drawMode(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos + MODE_X;
        int y = topPos + MODE_Y;
        int right = x + MODE_WIDTH;
        int bottom = y + MODE_HEIGHT;
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1,
                menu.mode() == ItemFilterMode.WHITELIST ? WHITELIST_COLOR : BLACKLIST_COLOR);
        graphics.fill(x, y, right, bottom, 0xFF65717D);
        graphics.fill(x, y, right, y + 1, 0xFFB5C0CA);
        graphics.fill(x, y + 1, x + 1, bottom, 0xFF9AA7B3);
        graphics.fill(x + 1, bottom - 1, right, bottom, 0xFF394651);
        graphics.fill(right - 1, y + 1, right, bottom, 0xFF394651);
        if (over(mouseX, mouseY, MODE_X, MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
            graphics.fill(x, y, right, bottom, 0x40FFFFFF);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (hasGear()) return;
        Component label = Component.translatable(menu.mode().translationKey());
        graphics.text(font, label, MODE_X + (MODE_WIDTH - font.width(label)) / 2,
                MODE_Y + (MODE_HEIGHT - font.lineHeight) / 2, TITLE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        int x = (int) event.x();
        int y = (int) event.y();
        if (!hasGear() && over(x, y, MODE_X, MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, ItemFilterMenu.TOGGLE_MODE);
            click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** While the level box in the tab is being typed into, it takes the keys, the inventory key included. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        return tabs.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return tabs.charTyped(event) || super.charTyped(event);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
