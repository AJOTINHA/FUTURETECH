package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SolidFuelGeneratorScreen extends AbstractContainerScreen<SolidFuelGeneratorMenu> {
    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar fuelBar = new AnimatedBar();
    private final TabStrip tabs;

    public SolidFuelGeneratorScreen(SolidFuelGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 184);
        titleLabelX = menu.slots.getFirst().x - 1;
        inventoryLabelX = titleLabelX;
        inventoryLabelY = 90;
        tabs = new TabStrip(new SideConfigTab<>(menu, font), new RedstoneControlTab<>(menu, font));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        // The energy box is centred on the fuel slot's vertical span (44..62) without changing its size.
        graphics.fill(x + 69, y + 46, x + 163, y + 60, BAR_BACK);
        float energyWidth = energyBar.width(menu.energyStored(), SolidFuelGeneratorBlockEntity.CAPACITY, 92, menu.isSynced());
        drawGradientBar(graphics, x + 70, y + 47, energyWidth, 12, ENERGY_START, ENERGY_END);
        graphics.fill(x + 7, y + 65, x + 25, y + 69, 0xFF56616D);
        float burnWidth = fuelBar.width(menu.burnRemaining(), menu.burnTotal(), 18, menu.isSynced());
        drawGradientBar(graphics, x + 7, y + 65, burnWidth, 4, 0xFFEC761C, 0xFFFFD76A);
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
        var fuelStack = menu.slots.getFirst().getItem();
        Component fuelName = fuelStack.isEmpty()
                ? Component.translatable("gui.futuretech.empty") : fuelStack.getHoverName();
        // Keep translated names inside the fuel column, above the slot.
        var nameLines = font.split(fuelName, 58);
        for (int line = 0; line < Math.min(2, nameLines.size()); line++) {
            graphics.text(font, nameLines.get(line), inventoryLabelX, 26 + line * font.lineHeight, TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 70, 26, TEXT, false);
        Component generationRate = Component.translatable("gui.futuretech.rate",
                menu.isGenerating() ? SolidFuelGeneratorBlockEntity.GENERATION_PER_TICK : 0);
        graphics.text(font, generationRate, 163 - font.width(generationRate), 26, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                SolidFuelGeneratorBlockEntity.CAPACITY), 70, 63, TEXT, false);
    }
}
