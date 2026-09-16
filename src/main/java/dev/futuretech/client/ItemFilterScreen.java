package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.block.CableKind;
import dev.futuretech.item.ItemFilterMode;
import dev.futuretech.menu.ItemFilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The filter card's editor: the nine entries in a dispenser-style grid, the mode button beside
 * them, and the player's inventory to pick items from. Same panel and colours as the machines.
 */
public final class ItemFilterScreen extends AbstractContainerScreen<ItemFilterMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = ItemFilterMenu.INVENTORY_TOP + 3 * 18 + 4 + 18 + 8;
    private static final int MODE_X = 120;
    private static final int MODE_WIDTH = 48;
    private static final int MODE_HEIGHT = 36;
    /** Centred on the grid's three rows. */
    private static final int MODE_Y = ItemFilterMenu.GRID_Y + (3 * 18 - MODE_HEIGHT) / 2;
    private static final int WHITELIST_COLOR = CableKind.ITEMS.accent();
    private static final int BLACKLIST_COLOR = 0xFFEC761C;

    public ItemFilterScreen(ItemFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        titleLabelX = 8;
        inventoryLabelY = INVENTORY_LABEL;
    }

    private static final int INVENTORY_LABEL = ItemFilterMenu.INVENTORY_TOP - 12;

    private boolean overMode(int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        return x >= MODE_X && x < MODE_X + MODE_WIDTH && y >= MODE_Y && y < MODE_Y + MODE_HEIGHT;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(graphics, leftPos, topPos, menu.slots);
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
        if (overMode(mouseX, mouseY)) graphics.fill(x, y, right, bottom, 0x40FFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        Component label = Component.translatable(menu.mode().translationKey());
        graphics.text(font, label, MODE_X + (MODE_WIDTH - font.width(label)) / 2,
                MODE_Y + (MODE_HEIGHT - font.lineHeight) / 2, TITLE, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overMode((int) event.x(), (int) event.y())) {
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, ItemFilterMenu.TOGGLE_MODE);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
