package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
import dev.futuretech.menu.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar progressBar = new AnimatedBar();
    private final TabStrip tabs;

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ElectricFurnaceMenu.IMAGE_WIDTH, 184);
        titleLabelX = menu.slots.getFirst().x - 1;
        inventoryLabelX = titleLabelX;
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
        // The energy box matches the generator's, centred on the slot row's vertical span.
        graphics.fill(x + 69, y + 46, x + 163, y + 60, BAR_BACK);
        float energyWidth = energyBar.width(menu.energyStored(), ElectricFurnaceBlockEntity.CAPACITY, 92, menu.isSynced());
        drawGradientBar(graphics, x + 70, y + 47, energyWidth, 12, ENERGY_START, ENERGY_END);
        // Smelting progress runs between the input and output slots.
        graphics.fill(x + 7, y + 65, x + 52, y + 69, 0xFF56616D);
        float smeltWidth = progressBar.width(menu.progress(), menu.progressTotal(), 45, menu.isSynced());
        drawGradientBar(graphics, x + 7, y + 65, smeltWidth, 4, 0xFFEC761C, 0xFFFFD76A);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var inputStack = menu.slots.getFirst().getItem();
        Component inputName = inputStack.isEmpty()
                ? Component.translatable("gui.futuretech.empty") : inputStack.getHoverName();
        // Keep translated names inside the item column, above the slots.
        var nameLines = font.split(inputName, 58);
        for (int line = 0; line < Math.min(2, nameLines.size()); line++) {
            graphics.text(font, nameLines.get(line), inventoryLabelX, 26 + line * font.lineHeight, TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 70, 26, TEXT, false);
        Component usageRate = Component.translatable("gui.futuretech.rate",
                menu.isWorking() ? ElectricFurnaceBlockEntity.ENERGY_PER_TICK : 0);
        graphics.text(font, usageRate, 163 - font.width(usageRate), 26, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                ElectricFurnaceBlockEntity.CAPACITY), 70, 63, TEXT, false);
    }
}
