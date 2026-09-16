package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.LavaGeneratorBlockEntity;
import dev.futuretech.menu.LavaGeneratorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class LavaGeneratorScreen extends AbstractContainerScreen<LavaGeneratorMenu> {
    /** The lava column stands between the bucket slots and the energy readout, spanning both slots. */
    private static final int TANK_X = 34;
    private static final int TANK_Y = LavaGeneratorMenu.INPUT_Y;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = LavaGeneratorMenu.OUTPUT_Y + 18 - LavaGeneratorMenu.INPUT_Y;
    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar lavaBar = new AnimatedBar();
    private final TabStrip tabs;

    public LavaGeneratorScreen(LavaGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LavaGeneratorMenu.IMAGE_WIDTH, 184);
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
        drawDownArrow(graphics, x + LavaGeneratorMenu.SLOT_X + 5, y + LavaGeneratorMenu.INPUT_Y + 21);
        drawTank(graphics, x + TANK_X, y + TANK_Y);
        // The energy box is centred on the input slot's vertical span, as on the solid fuel generator.
        graphics.fill(x + 69, y + 46, x + 163, y + 60, BAR_BACK);
        float energyWidth = energyBar.width(menu.energyStored(), menu.energyCapacity(), 92, menu.isSynced());
        drawGradientBar(graphics, x + 70, y + 47, energyWidth, 12, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** The lava column, drawn with lava's own texture and tint like the fluid tank's bar. */
    private void drawTank(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, 0xFF56616D);
        graphics.fill(x, y, x + TANK_WIDTH, y + TANK_HEIGHT, BAR_BACK);
        int height = Math.round(lavaBar.width(menu.lavaStored(), LavaGeneratorBlockEntity.TANK_CAPACITY, TANK_HEIGHT, menu.isSynced()));
        if (height > 0) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(Fluids.LAVA.defaultFluidState());
            var sprite = model.stillMaterial().sprite();
            int color = model.fluidTintSource() == null ? 0xFFFFFFFF
                    : model.fluidTintSource().colorAsStack(new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME));
            graphics.enableScissor(x, y + TANK_HEIGHT - height, x + TANK_WIDTH, y + TANK_HEIGHT);
            for (int row = 0; row < TANK_HEIGHT; row += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y + row, 16, 16, color);
            }
            graphics.disableScissor();
        }
        graphics.fill(x, y, x + 1, y + TANK_HEIGHT, 0x30FFFFFF);
        for (int step = 1; step < 4; step++) {
            int tickY = y + TANK_HEIGHT * step / 4;
            graphics.fill(x + TANK_WIDTH - 4, tickY, x + TANK_WIDTH, tickY + 1, 0xA0FFFFFF);
        }
    }

    /** A full bucket goes in at the top and comes back empty at the bottom. */
    private static void drawDownArrow(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 3, y, x + 4, y + 7, TEXT);
        graphics.fill(x + 1, y + 4, x + 6, y + 5, TEXT);
        graphics.fill(x + 2, y + 5, x + 5, y + 6, TEXT);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Same outer box the bar's backing plate fills.
        energyTooltip(graphics, mouseX, mouseY, leftPos + 69, topPos + 46, 94, 14,
                menu.energyStored(), menu.energyCapacity());
        if (mouseX >= leftPos + TANK_X - 1 && mouseX < leftPos + TANK_X + TANK_WIDTH + 1
                && mouseY >= topPos + TANK_Y - 1 && mouseY < topPos + TANK_Y + TANK_HEIGHT + 1) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",
                    Fluids.LAVA.getFluidType().getDescription(), menu.lavaStored(),
                    LavaGeneratorBlockEntity.TANK_CAPACITY), mouseX, mouseY);
        }
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
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 70, 26, TEXT, false);
        Component generationRate = Component.translatable("gui.futuretech.rate",
                menu.isGenerating() ? LavaGeneratorBlockEntity.GENERATION_PER_TICK : 0);
        graphics.text(font, generationRate, 163 - font.width(generationRate), 26, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                menu.energyCapacity()), 70, 63, TEXT, false);
        Component lava = Component.translatable("gui.futuretech.tank.amount", menu.lavaStored(),
                LavaGeneratorBlockEntity.TANK_CAPACITY);
        graphics.text(font, lava, 70, 73, TEXT, false);
    }
}
