package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import net.minecraft.client.renderer.RenderPipelines;
import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.MelterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The melter's screen: the input slot, the progress arrow running from it into the tank, the
 * tank drawn with its fluid's own texture, and the energy column at the right; the machine tabs
 * beside the window.
 */
public final class MelterScreen extends AbstractContainerScreen<MelterMenu> {
    private static final int ARROW_X = MelterMenu.SLOT_X + 24;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_TOP = 2;
    private static final int ARROW_BOTTOM = 15;
    private static final int ARROW_SHAFT_TOP = 6;
    private static final int ARROW_SHAFT_BOTTOM = 11;
    /** Columns of the head; it loses a row off each side per step, ending in a single pixel. */
    private static final int ARROW_HEAD = 7;
    private static final int ARROW_BACK = 0xFF56616D;
    private static final int ARROW_START = 0xFFEC761C;
    private static final int ARROW_END = 0xFFFFD76A;
    /** The tank stands past the arrow, spanning the slot's row and the rows around it. */
    private static final int TANK_X = ARROW_X + ARROW_WIDTH + 8;
    private static final int TANK_Y = 24;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 48;
    private static final int ENERGY_X = 158;
    private static final int ENERGY_TOP = TANK_Y;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = TANK_HEIGHT;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar fluidBar = new AnimatedBar();
    private final AnimatedBar progressBar = new AnimatedBar();
    private final TabStrip tabs;

    public MelterScreen(MelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MelterMenu.IMAGE_WIDTH, MelterMenu.INVENTORY_Y + 82);
        titleLabelX = 7;
        inventoryLabelX = 7;
        inventoryLabelY = MelterMenu.INVENTORY_Y - 12;
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
        float filled = progressBar.width(menu.progress(), menu.progressTotal(), ARROW_WIDTH, menu.isSynced());
        drawProgressArrow(graphics, x, y + MelterMenu.SLOT_Y, filled);
        drawTank(graphics, x + TANK_X, y + TANK_Y);
        int energyTop = y + ENERGY_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** The tank column, drawn with the fluid's own texture and tint like the fluid tank's bar. */
    private void drawTank(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, 0xFF56616D);
        graphics.fill(x, y, x + TANK_WIDTH, y + TANK_HEIGHT, BAR_BACK);
        FluidStack fluid = menu.fluid();
        int height = Math.round(fluidBar.width(menu.fluidStored(), menu.tankCapacity(), TANK_HEIGHT, menu.isSynced()));
        if (height > 0 && !fluid.isEmpty()) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            var sprite = model.stillMaterial().sprite();
            int color = model.fluidTintSource() == null ? 0xFFFFFFFF : model.fluidTintSource().colorAsStack(fluid);
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

    private void drawProgressArrow(GuiGraphicsExtractor graphics, int x, int rowY, float filled) {
        for (int column = 0; column < ARROW_WIDTH; column++) {
            int left = x + ARROW_X + column;
            int top = rowY + arrowTop(column);
            int bottom = rowY + arrowBottom(column);
            float covered = Math.clamp(filled - column, 0.0F, 1.0F);
            graphics.fill(left, top, left + 1, bottom, ARROW_BACK);
            if (covered <= 0) continue;
            int colour = lerpColour(ARROW_START, ARROW_END, column / (float) (ARROW_WIDTH - 1));
            if (covered >= 1) {
                graphics.fill(left, top, left + 1, bottom, colour);
                continue;
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(left, 0);
            graphics.pose().scale(covered, 1.0F);
            graphics.fill(0, top, 1, bottom, colour);
            graphics.pose().popMatrix();
        }
    }

    /** Top of one arrow column: the shaft holds its height until the head starts tapering. */
    private static int arrowTop(int column) {
        int intoHead = column - (ARROW_WIDTH - ARROW_HEAD);
        return intoHead < 0 ? ARROW_SHAFT_TOP : ARROW_TOP + intoHead;
    }

    private static int arrowBottom(int column) {
        int intoHead = column - (ARROW_WIDTH - ARROW_HEAD);
        return intoHead < 0 ? ARROW_SHAFT_BOTTOM : ARROW_BOTTOM - intoHead;
    }

    /** The arrow is drawn per column, so its gradient has to be sampled rather than filled. */
    private static int lerpColour(int from, int to, float t) {
        int alpha = lerpChannel(from >>> 24, to >>> 24, t);
        int red = lerpChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
        int green = lerpChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
        int blue = lerpChannel(from & 0xFF, to & 0xFF, t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int lerpChannel(int from, int to, float t) { return Math.round(from + (to - from) * t); }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        if (mouseX >= leftPos + TANK_X - 1 && mouseX < leftPos + TANK_X + TANK_WIDTH + 1
                && mouseY >= topPos + TANK_Y - 1 && mouseY < topPos + TANK_Y + TANK_HEIGHT + 1) {
            FluidStack fluid = menu.fluid();
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",
                    fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName(),
                    menu.fluidStored(), menu.tankCapacity()), mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }
}
