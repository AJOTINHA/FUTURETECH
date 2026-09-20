package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;
import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.menu.WindGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WindGeneratorScreen extends AbstractContainerScreen<WindGeneratorMenu> implements TabbedScreen {
    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;
    private static final String[] STATUS = {"active", "calm", "obstructed", "disabled", "full", "no_sky"};
    @Override public TabStrip tabs() { return tabs; }

    public WindGeneratorScreen(WindGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WindGeneratorMenu.IMAGE_WIDTH, 184);
        titleLabelX = inventoryLabelX = 8;
        inventoryLabelY = 90;
        tabs = new TabStrip(new SideConfigTab<>(menu, font), new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        int blade = menu.windStrength() > 0 ? 0xFF8EDBDD : 0xFF657080;
        graphics.fill(x+33, y+39, x+37, y+59, 0xFF778B98);
        graphics.fill(x+30, y+58, x+40, y+60, 0xFF485B68);
        graphics.fill(x+34, y+24, x+39, y+36, blade);
        graphics.fill(x+38, y+35, x+50, y+40, blade);
        graphics.fill(x+31, y+39, x+36, y+51, blade);
        graphics.fill(x+20, y+32, x+32, y+37, blade);
        graphics.fill(x+31, y+34, x+39, y+41, 0xFFCEDDE4);
        graphics.fill(x+69, y+46, x+163, y+60, BAR_BACK);
        float width = energyBar.width(menu.energyStored(), menu.energyCapacity(), 92, menu.isSynced());
        drawGradientBar(graphics, x+70, y+47, width, 12, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth-15);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 70, 26, TEXT, false);
        Component rate = Component.translatable("gui.futuretech.rate", menu.energyUsagePerTick());
        graphics.text(font, rate, 163-font.width(rate), 26, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(), menu.energyCapacity()), 70, 63, TEXT, false);
        Component wind = Component.literal(menu.windStrength()+"%");
        graphics.text(font, wind, 35-font.width(wind)/2, 63, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.wind."+STATUS[menu.status()]), 8, 78, TEXT, false);
    }

    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos+69, topPos+46, 94, 14, menu.energyStored(), menu.energyCapacity());
        if (mouseX >= leftPos+19 && mouseX < leftPos+52 && mouseY >= topPos+24 && mouseY < topPos+73) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.wind.windStrength", menu.windStrength()), mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }
}
