package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.entity.TimeControllerBlockEntity;
import dev.futuretech.menu.TimeControllerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The time controller's screen: the six moments of the day as buttons, two columns of three,
 * the chosen one outlined; the energy column beside them; under them what the next jump would
 * cost from the time it is now — in the warning colour while the buffer cannot pay it — and on
 * the edge the redstone control every machine has, which here says when the jump fires.
 */
public final class TimeControllerScreen extends AbstractContainerScreen<TimeControllerMenu> implements TabbedScreen {
    private static final int LABEL_X = 8;
    private static final int BUTTON_WIDTH = 68;
    private static final int BUTTON_HEIGHT = 16;
    private static final int ROW_TOP = 26;
    private static final int ROW_HEIGHT = 20;
    private static final int COLUMN_GAP = 4;
    private static final int ROWS = 3;
    private static final int SELECTED = 0xFFEC761C;
    private static final int ENERGY_X = 154;
    private static final int ENERGY_WIDTH = 14;
    /** The column stands the height of the three rows of buttons. */
    private static final int ENERGY_HEIGHT = ROWS * ROW_HEIGHT - (ROW_HEIGHT - BUTTON_HEIGHT);
    private static final int COST_Y = ROW_TOP + ROWS * ROW_HEIGHT + 2;
    private static final int HEIGHT = TimeControllerMenu.INVENTORY_Y + 3 * 18 + 4 + 18 + 8;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }

    public TimeControllerScreen(TimeControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TimeControllerMenu.IMAGE_WIDTH, HEIGHT);
        titleLabelX = LABEL_X;
        inventoryLabelX = LABEL_X;
        inventoryLabelY = TimeControllerMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new RedstoneControlTab<>(menu, font));
    }

    private static int buttonX(DayMoment moment) {
        return LABEL_X + moment.ordinal() / ROWS * (BUTTON_WIDTH + COLUMN_GAP);
    }

    private static int buttonY(DayMoment moment) {
        return ROW_TOP + moment.ordinal() % ROWS * ROW_HEIGHT;
    }

    /** What the next jump skips, priced, from the client's own clock; the server prices the real one. */
    private int cost() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : TimeControllerBlockEntity.cost(level.getOverworldClockTime(), menu.moment());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        for (DayMoment moment : DayMoment.values()) {
            int bx = x + buttonX(moment);
            int by = y + buttonY(moment);
            drawButton(graphics, font, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(moment.translationKey()),
                    overButton(mouseX, mouseY, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT));
            if (moment == menu.moment()) outline(graphics, bx, by);
        }
        int energyTop = y + ROW_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** The chosen moment wears the accent on its border, the way a chosen redstone mode does. */
    private static void outline(GuiGraphicsExtractor graphics, int x, int y) {
        int right = x + BUTTON_WIDTH;
        int bottom = y + BUTTON_HEIGHT;
        graphics.fill(x - 1, y - 1, right + 1, y, SELECTED);
        graphics.fill(x - 1, bottom, right + 1, bottom + 1, SELECTED);
        graphics.fill(x - 1, y, x, bottom, SELECTED);
        graphics.fill(right, y, right + 1, bottom, SELECTED);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        int cost = cost();
        Component line = Component.translatable("gui.futuretech.time_controller.next", String.format("%,d", cost));
        graphics.text(font, line, LABEL_X, COST_Y, cost > menu.energyStored() ? SELECTED : TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ROW_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        for (DayMoment moment : DayMoment.values()) {
            if (!overButton(event.x(), event.y(), leftPos + buttonX(moment), topPos + buttonY(moment), BUTTON_WIDTH, BUTTON_HEIGHT)) continue;
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, TimeControllerMenu.SELECT_BASE + moment.ordinal());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
