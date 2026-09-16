package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.menu.FluidTankMenu;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FluidTankScreen extends AbstractContainerScreen<FluidTankMenu> {
    private static final int BAR_X = 76;
    private static final int BAR_Y = 38;
    private static final int BAR_WIDTH = 24;
    private static final int BAR_HEIGHT = 62;
    private final TabStrip tabs;

    public FluidTankScreen(FluidTankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FluidTankMenu.WIDTH, FluidTankMenu.HEIGHT);
        titleLabelX = 7;
        inventoryLabelX = 8;
        inventoryLabelY = 116;
        tabs = new TabStrip(new UpgradeTab(menu, font), new SideConfigTab<>(menu, font), new RedstoneControlTab<>(menu, font));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(graphics, leftPos, topPos, menu.slots);
        int x = leftPos + BAR_X, y = topPos + BAR_Y;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFF56616D);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BAR_BACK);
        var fluid = menu.visualFluid();
        int height = Math.clamp(Math.round(menu.visualFill(partialTick) * BAR_HEIGHT), 0, BAR_HEIGHT);
        if (!fluid.isEmpty() && height > 0) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            var sprite = model.stillMaterial().sprite();
            int color = model.fluidTintSource() == null ? 0xFFFFFFFF : model.fluidTintSource().colorAsStack(fluid);
            graphics.enableScissor(x, y + BAR_HEIGHT - height, x + BAR_WIDTH, y + BAR_HEIGHT);
            for (int row = 0; row < BAR_HEIGHT; row += 16) for (int column = 0; column < BAR_WIDTH; column += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + column, y + row, 16, 16, color);
            }
            graphics.disableScissor();
        }
        graphics.fill(x, y, x + 1, y + BAR_HEIGHT, 0x30FFFFFF);
        for (int step = 1; step < 4; step++) {
            int tickY = y + BAR_HEIGHT * step / 4;
            graphics.fill(x + BAR_WIDTH - 4, tickY, x + BAR_WIDTH, tickY + 1, 0xA0FFFFFF);
        }
        drawArrow(graphics, leftPos + 57, topPos + 67);
        drawArrow(graphics, leftPos + 108, topPos + 67);
        tabs.render(graphics, leftPos, topPos, imageWidth, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y + 1, x + 7, y + 2, TEXT);
        graphics.fill(x + 4, y - 1, x + 5, y + 4, TEXT);
        graphics.fill(x + 5, y, x + 6, y + 3, TEXT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var fluid = menu.fluid();
        Component name = fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName();
        String shortName = font.plainSubstrByWidth(name.getString(), imageWidth - 16);
        graphics.text(font, shortName, (imageWidth - font.width(shortName)) / 2, 25, TEXT, false);
        centred(graphics, Component.translatable("gui.futuretech.tank.input"), FluidTankMenu.INPUT_X + 8, 47);
        centred(graphics, Component.translatable("gui.futuretech.tank.output"), FluidTankMenu.OUTPUT_X + 8, 47);
        centred(graphics, Component.translatable("gui.futuretech.tank.amount", fluid.getAmount(), menu.capacity()),
                imageWidth / 2, 104);
    }

    private void centred(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        tabs.extractTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + BAR_WIDTH
                && mouseY >= topPos + BAR_Y && mouseY < topPos + BAR_Y + BAR_HEIGHT) {
            var fluid = menu.fluid();
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",
                    fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName(),
                    fluid.getAmount(), menu.capacity()), mouseX, mouseY);
        }
    }
}
