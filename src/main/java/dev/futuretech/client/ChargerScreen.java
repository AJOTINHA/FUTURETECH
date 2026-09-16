package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.ChargerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The item to charge on the left, the energy column standing in the middle, the charged item on the right. */
public final class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
    private static final int LABEL_X = 7;
    /** The energy column between the two slots, centred on their row. */
    private static final int ENERGY_X = 81;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = 49;
    private static final int ENERGY_TOP = ChargerMenu.SLOT_Y + 8 - ENERGY_HEIGHT / 2;
    /** Short arrows from the input into the column and from the column into the output; lit while charging. */
    private static final int ARROW_Y = ChargerMenu.SLOT_Y + 6;
    private static final int ARROW_LENGTH = 12;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;

    public ChargerScreen(ChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ChargerMenu.IMAGE_WIDTH, 184);
        titleLabelX = LABEL_X;
        inventoryLabelX = LABEL_X;
        inventoryLabelY = 90;
        tabs = new TabStrip(new UpgradeTab(menu, font), new SideConfigTab<>(menu, font),
                new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        int energyTop = y + ENERGY_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        int arrow = menu.isWorking() ? ENERGY_END : 0xFF8B959F;
        drawArrow(graphics, x + ChargerMenu.INPUT_X + 18, y + ARROW_Y, arrow);
        drawArrow(graphics, x + ENERGY_X + ENERGY_WIDTH + 2, y + ARROW_Y, arrow);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** A small right-pointing arrow: a two-pixel shaft and a stepped head. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y + 1, x + ARROW_LENGTH - 3, y + 3, color);
        graphics.fill(x + ARROW_LENGTH - 4, y - 1, x + ARROW_LENGTH - 3, y + 5, color);
        graphics.fill(x + ARROW_LENGTH - 3, y, x + ARROW_LENGTH - 2, y + 4, color);
        graphics.fill(x + ARROW_LENGTH - 2, y + 1, x + ARROW_LENGTH - 1, y + 3, color);
        graphics.fill(x + ARROW_LENGTH - 1, y + 2, x + ARROW_LENGTH, y + 2, color);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }
}
