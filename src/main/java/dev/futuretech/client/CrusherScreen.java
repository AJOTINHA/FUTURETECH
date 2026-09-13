package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.CrusherBlockEntity;
import dev.futuretech.menu.CrusherMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CrusherScreen extends AbstractContainerScreen<CrusherMenu> {
    /** The energy column down the left edge: outer box, with the fill inset by a pixel. */
    private static final int ENERGY_X = 7;
    private static final int ENERGY_TOP = 29;
    private static final int ENERGY_BOTTOM = 78;
    private static final int ENERGY_WIDTH = 14;
    /** The progress arrow sits in the gap between the two slots, centred on their row. */
    private static final int ARROW_X = 76;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_TOP = 47;
    private static final int ARROW_BOTTOM = 60;
    private static final int ARROW_SHAFT_TOP = 51;
    private static final int ARROW_SHAFT_BOTTOM = 56;
    /** Columns of the head; it loses a row off each side per step, ending in a single pixel. */
    private static final int ARROW_HEAD = 7;
    private static final int ARROW_BACK = 0xFF56616D;
    private static final int ARROW_START = 0xFF1676C4;
    private static final int ARROW_END = 0xFF55E7ED;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar progressBar = new AnimatedBar();
    private final TabStrip tabs;

    public CrusherScreen(CrusherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CrusherMenu.IMAGE_WIDTH, 184);
        // Title and inventory label stay against the left edge; the slots no longer sit under them.
        titleLabelX = ENERGY_X;
        inventoryLabelX = ENERGY_X;
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
        // Energy stands on end down the left edge and fills from the bottom up.
        graphics.fill(x + ENERGY_X, y + ENERGY_TOP, x + ENERGY_X + ENERGY_WIDTH, y + ENERGY_BOTTOM, BAR_BACK);
        int energyHeight = ENERGY_BOTTOM - ENERGY_TOP - 2;
        float energyFill = energyBar.width(menu.energyStored(), CrusherBlockEntity.CAPACITY,
                energyHeight, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, y + ENERGY_BOTTOM - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        // Crushing progress runs from the input slot towards the output one.
        float crushWidth = progressBar.width(menu.progress(), menu.progressTotal(), ARROW_WIDTH, menu.isSynced());
        drawProgressArrow(graphics, x, y, crushWidth);
        drawRollers(graphics, x + 57, y + 65, menu.isWorking());
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** Opposing teeth use the furnace status indicator position. */
    private static void drawRollers(GuiGraphicsExtractor graphics, int x, int y, boolean working) {
        int color = working ? ENERGY_END : 0xFF8B959F;
        graphics.fill(x, y, x + 14, y + 3, color);
        graphics.fill(x, y + 9, x + 14, y + 12, color);
        for (int tooth = 0; tooth < 3; tooth++) {
            graphics.fill(x + tooth * 5, y + 3, x + tooth * 5 + 3, y + 5, color);
            graphics.fill(x + tooth * 5 + 1, y + 7, x + tooth * 5 + 4, y + 9, color);
        }
    }
    /**
     * Draws the arrow a column at a time, so the fill follows the head's taper instead of stopping
     * at a straight edge. The last column is scaled to the leftover fraction, keeping the animation
     * off whole-pixel steps the way the gradient bars do.
     */
    private void drawProgressArrow(GuiGraphicsExtractor graphics, int x, int y, float filled) {
        for (int column = 0; column < ARROW_WIDTH; column++) {
            int left = x + ARROW_X + column;
            int top = y + arrowTop(column);
            int bottom = y + arrowBottom(column);
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

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // The column carries the readout that used to be printed under it.
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP,
                ENERGY_WIDTH, ENERGY_BOTTOM - ENERGY_TOP,
                menu.energyStored(), CrusherBlockEntity.CAPACITY);
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
    }
}
