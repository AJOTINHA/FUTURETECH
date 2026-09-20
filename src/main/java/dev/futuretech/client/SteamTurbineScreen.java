package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;
import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.SteamTurbineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SteamTurbineScreen extends AbstractContainerScreen<SteamTurbineMenu> implements TabbedScreen {
    private final AnimatedBar steamBar = new AnimatedBar();
    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;
    private static final String[] STATUS = {"active", "no_steam", "disabled", "full"};
    @Override public TabStrip tabs() { return tabs; }

    public SteamTurbineScreen(SteamTurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SteamTurbineMenu.IMAGE_WIDTH, 184);
        titleLabelX = inventoryLabelX = 8;
        inventoryLabelY = 90;
        tabs = new TabStrip(new UpgradeTab(menu, font), new SideConfigTab<>(menu, font), new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        graphics.fill(x+24,y+26,x+46,y+70,0xFF56616D);
        graphics.fill(x+25,y+27,x+45,y+69,BAR_BACK);
        int height = Math.round(steamBar.width(menu.steamStored(),dev.futuretech.block.entity.SteamTurbineBlockEntity.STEAM_CAPACITY,42,menu.isSynced()));
        graphics.fill(x+25,y+69-height,x+45,y+69,0xFFD2E5E8);
        for (int step=1;step<4;step++) graphics.fill(x+40,y+27+step*10,x+45,y+28+step*10,0xAAFFFFFF);
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
        Component steam = Component.translatable("fluid.futuretech.steam");
        graphics.text(font,steam,35-font.width(steam)/2,16,TEXT,false);
        graphics.text(font, Component.translatable("gui.futuretech.steam_turbine."+STATUS[menu.status()]), 8, 78, TEXT, false);
    }

    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos+69, topPos+46, 94, 14, menu.energyStored(), menu.energyCapacity());
        if (mouseX >= leftPos+19 && mouseX < leftPos+52 && mouseY >= topPos+24 && mouseY < topPos+73) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents", Component.translatable("fluid.futuretech.steam"), menu.steamStored(), dev.futuretech.block.entity.SteamTurbineBlockEntity.STEAM_CAPACITY), mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }
}
