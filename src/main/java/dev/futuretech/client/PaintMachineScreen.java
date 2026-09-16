package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.PaintMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PaintMachineScreen extends AbstractContainerScreen<PaintMachineMenu> {
    /** The energy column down the left edge, centred on the slot rows: outer box, with the fill inset by a pixel. */
    private static final int ENERGY_X = 7;
    private static final int ENERGY_HEIGHT = 49;
    private static final int ENERGY_WIDTH = 14;
    /** The progress arrow sits in the gap between the plates and the panels, centred on the row. */
    private static final int ARROW_X = 76;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_TOP = 2;
    private static final int ARROW_BOTTOM = 15;
    private static final int ARROW_SHAFT_TOP = 6;
    private static final int ARROW_SHAFT_BOTTOM = 11;
    /** The nozzle sits under the block and plate pair, however many lanes there are. */
    private static final int NOZZLE_X = 49;
    private static final int NOZZLE_BELOW_ROW = 20;
    /** Columns of the head; it loses a row off each side per step, ending in a single pixel. */
    private static final int ARROW_HEAD = 7;
    private static final int ARROW_BACK = 0xFF56616D;
    private static final int ARROW_START = 0xFF1676C4;
    private static final int ARROW_END = 0xFF55E7ED;
    private static final int NOZZLE_IDLE = 0xFF8B959F;
    private static final int NOZZLE_METAL = 0xFF56616D;
    /** The paints the front's nozzle cycles through, in the same order, so the panel agrees with the block. */
    private static final int[] PAINTS = {0xFFD64034, 0xFFE8BA30, 0xFF4EC878, 0xFF1676C4};
    /** Ticks each paint stays before the next, matching the front's two frames of three ticks each. */
    private static final int PAINT_TICKS = 6;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar[] progressBars;
    private final TabStrip tabs;

    public PaintMachineScreen(PaintMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PaintMachineMenu.IMAGE_WIDTH, 184 + menu.extraHeight());
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
        // Each lane's paint job runs from its block and plates towards the panel slot.
        for (int lane = 0; lane < menu.lanes(); lane++) {
            float meltWidth = progressBars[lane].width(menu.progress(lane), menu.progressTotal(lane), ARROW_WIDTH, menu.isSynced());
            drawProgressArrow(graphics, x, y + menu.rowY(lane), meltWidth);
        }
        drawNozzle(graphics, x + NOZZLE_X, y + menu.rowY(menu.lanes() - 1) + NOZZLE_BELOW_ROW, menu.isWorking());
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /**
     * A nozzle in the furnace status indicator position: grey when idle; at work, a spray beneath
     * it and a panel taking the colour, cycling through the same paints as the block's front.
     */
    private void drawNozzle(GuiGraphicsExtractor graphics, int x, int y, boolean working) {
        int metal = working ? NOZZLE_METAL : NOZZLE_IDLE;
        // The body hanging from a rail, and the tip below it.
        graphics.fill(x, y, x + 14, y + 1, metal);
        graphics.fill(x + 5, y + 1, x + 9, y + 5, metal);
        graphics.fill(x + 6, y + 5, x + 8, y + 6, metal);
        // The panel on the tray.
        graphics.fill(x + 2, y + 12, x + 12, y + 13, metal);
        graphics.fill(x + 3, y + 9, x + 11, y + 12, working ? NOZZLE_METAL : NOZZLE_IDLE);
        if (!working) return;
        long time = minecraft == null || minecraft.level == null ? 0 : minecraft.level.getGameTime();
        int paint = PAINTS[(int) (time / PAINT_TICKS % PAINTS.length)];
        graphics.fill(x + 3, y + 9, x + 11, y + 12, paint);
        // Three drops of spray between the tip and the panel, spreading as they fall.
        graphics.fill(x + 6, y + 6, x + 8, y + 7, paint);
        graphics.fill(x + 5, y + 7, x + 6, y + 8, paint);
        graphics.fill(x + 8, y + 7, x + 9, y + 8, paint);
    }

    /**
     * Draws the arrow a column at a time, so the fill follows the head's taper instead of stopping
     * at a straight edge. The last column is scaled to the leftover fraction, keeping the animation
     * off whole-pixel steps the way the gradient bars do.
     */
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
        int rowsMiddle = menu.rowY(0) + menu.lanes() * PaintMachineMenu.ROW_SPACING / 2;
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
