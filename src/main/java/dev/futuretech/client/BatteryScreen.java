package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.BatteryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BatteryScreen extends AbstractContainerScreen<BatteryMenu> {
    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;

    public BatteryScreen(BatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BatteryMenu.IMAGE_WIDTH, 184);
        titleLabelX = 7;
        inventoryLabelX = titleLabelX;
        inventoryLabelY = 90;
        tabs = new TabStrip(new UpgradeTab(menu, font), new SideConfigTab<>(menu, font), new RedstoneControlTab<>(menu, font));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        // One wide bar spans the panel, with the same height as the generator's energy bar.
        graphics.fill(x + 7, y + 38, x + 169, y + 52, BAR_BACK);
        float energyWidth = energyBar.width(menu.energyStored(), menu.tier().capacity(), 160, menu.isSynced());
        drawGradientBar(graphics, x + 8, y + 39, energyWidth, 12, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Same outer box the bar's backing plate fills.
        energyTooltip(graphics, mouseX, mouseY, leftPos + 7, topPos + 38, 162, 14,
                menu.energyStored(), menu.tier().capacity());
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component tierTitle = Component.translatable("block.futuretech." + menu.tier().blockName());
        graphics.text(font, tierTitle, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 7, 26, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                menu.tier().capacity()), 7, 55, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.input", menu.inputRate()), 7, 68, TEXT, false);
        Component output = Component.translatable("gui.futuretech.output", menu.outputRate());
        graphics.text(font, output, 169 - font.width(output), 68, TEXT, false);
    }
}
