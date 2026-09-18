package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> implements TabbedScreen {
    /** The energy column down the left edge, centred on the slot rows: outer box, with the fill inset by a pixel. */
    private static final int ENERGY_X = 7;
    private static final int ENERGY_HEIGHT = 49;
    private static final int ENERGY_WIDTH = 14;
    /** The progress arrow sits in the gap between a lane's two slots, centred on their row; offsets from the row's top. */
    private static final int ARROW_X = 76;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_TOP = 2;
    private static final int ARROW_BOTTOM = 15;
    private static final int ARROW_SHAFT_TOP = 6;
    private static final int ARROW_SHAFT_BOTTOM = 11;
    /** Columns of the head; it loses a row off each side per step, ending in a single pixel. */
    private static final int ARROW_HEAD = 7;
    /** Centred under the last input slot, a couple of rows below its border, however many lanes there are. */
    private static final int FLAME_X = 57;
    private static final int FLAME_BELOW_ROW = 19;
    private static final int ARROW_BACK = 0xFF56616D;
    private static final int ARROW_START = 0xFFEC761C;
    private static final int ARROW_END = 0xFFFFD76A;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar[] progressBars;
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ElectricFurnaceMenu.IMAGE_WIDTH, 184 + menu.extraHeight());
        // Title and inventory label stay against the left edge; the slots no longer sit under them.
        titleLabelX = ENERGY_X;
        inventoryLabelX = ENERGY_X;
        inventoryLabelY = 90 + menu.extraHeight();
        progressBars = new AnimatedBar[menu.lanes()];
        for (int lane = 0; lane < progressBars.length; lane++) progressBars[lane] = new AnimatedBar();
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
        // Energy stands on end down the left edge and fills from the bottom up.
        int energyTop = y + energyTop();
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        int energyHeight = ENERGY_HEIGHT - 2;
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(),
                energyHeight, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        // Each lane's smelting progress runs from its input slot towards its output one.
        // Electric furnaces burn nothing, so the flame only says whether the machine is smelting.
        for (int lane = 0; lane < menu.lanes(); lane++) {
            float smeltWidth = progressBars[lane].width(menu.progress(lane), menu.progressTotal(lane), ARROW_WIDTH, menu.isSynced());
            drawProgressArrow(graphics, x, y + menu.rowY(lane), smeltWidth);
        }
        drawFurnaceFlame(graphics, x + FLAME_X, y + menu.rowY(menu.lanes() - 1) + FLAME_BELOW_ROW, menu.isWorking());
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
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

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /** The column's top, relative to the panel: centred on the block of slot rows (29 on a single lane). */
    private int energyTop() {
        int rowsMiddle = menu.rowY(0) + menu.lanes() * ElectricFurnaceMenu.ROW_SPACING / 2;
        return rowsMiddle - (ENERGY_HEIGHT + 1) / 2;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // The column carries the readout that used to be printed under it.
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + energyTop(),
                ENERGY_WIDTH, ENERGY_HEIGHT,
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
