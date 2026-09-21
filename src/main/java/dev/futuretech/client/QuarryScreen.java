package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.menu.QuarryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

public final class QuarryScreen extends AbstractContainerScreen<QuarryMenu> implements TabbedScreen {
    /** The button that reads the markers around the quarry into a box again. */
    private static final int READ_X = 101;
    private static final int READ_Y = 63;
    private static final int READ_WIDTH = 64;
    private static final int READ_HEIGHT = 14;
    /** The column beside the buffer: the status, which may take two lines, then the box and the layer. */
    private static final int INFO_X = 101;
    private static final int INFO_WIDTH = QuarryMenu.IMAGE_WIDTH - INFO_X - 4;
    private static final int STATUS_Y = 24;
    private static final int STATUS_LINES = 2;
    private static final int AREA_Y = 44;
    private static final int LAYER_Y = 54;
    private static final int ENERGY_LEFT = 7;
    private static final int ENERGY_TOP = 90;
    private static final int ENERGY_WIDTH = 162;
    private static final int ENERGY_HEIGHT = 14;
    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }

    public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, QuarryMenu.IMAGE_WIDTH, QuarryMenu.IMAGE_HEIGHT);
        titleLabelX = menu.slots.getFirst().x - 1;
        inventoryLabelX = titleLabelX;
        inventoryLabelY = QuarryMenu.INVENTORY_Y - 10;
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
        drawButton(graphics, font, x + READ_X, y + READ_Y, READ_WIDTH, READ_HEIGHT,
                Component.translatable("gui.futuretech.quarry.read_area"),
                overButton(mouseX, mouseY, x + READ_X, y + READ_Y, READ_WIDTH, READ_HEIGHT));
        graphics.fill(x + ENERGY_LEFT, y + ENERGY_TOP, x + ENERGY_LEFT + ENERGY_WIDTH,
                y + ENERGY_TOP + ENERGY_HEIGHT, BAR_BACK);
        float width = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_WIDTH - 2, menu.isSynced());
        drawGradientBar(graphics, x + ENERGY_LEFT + 1, y + ENERGY_TOP + 1, width,
                ENERGY_HEIGHT - 2, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_LEFT, topPos + ENERGY_TOP,
                ENERGY_WIDTH, ENERGY_HEIGHT, menu.energyStored(), menu.energyCapacity());
        if (overButton(mouseX, mouseY, leftPos + READ_X, topPos + READ_Y, READ_WIDTH, READ_HEIGHT)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.quarry.read_area.tip",
                    menu.maxSide()), mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        if (event.button() == 0 && overButton(event.x(), event.y(),
                leftPos + READ_X, topPos + READ_Y, READ_WIDTH, READ_HEIGHT)) {
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, QuarryMenu.BUTTON_READ_AREA);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var status = menu.status();
        // Wrapped to the column: "Parada pela redstone" is wider than the space beside the buffer.
        var statusLines = font.split(Component.translatable(status.key()), INFO_WIDTH);
        for (int line = 0; line < Math.min(STATUS_LINES, statusLines.size()); line++) {
            graphics.text(font, statusLines.get(line), INFO_X, STATUS_Y + line * font.lineHeight,
                    status.isProblem() ? 0xFFB03A2E : TEXT, false);
        }
        // The box and the layer being dug, or what the level would accept while there is no box.
        Component area = menu.hasArea()
                ? Component.translatable("gui.futuretech.quarry.area", menu.areaWidth(), menu.areaDepth())
                : Component.translatable("gui.futuretech.quarry.area_max", menu.maxSide(), menu.maxSide());
        graphics.text(font, area, INFO_X, AREA_Y, TEXT, false);
        if (menu.hasArea()) {
            graphics.text(font, Component.translatable("gui.futuretech.quarry.layer", menu.layer()), INFO_X, LAYER_Y, TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 8, 80, TEXT, false);
        Component rate = Component.translatable("gui.futuretech.rate", menu.isWorking() ? menu.energyRatePerTick() : 0);
        graphics.text(font, rate, 169 - font.width(rate), 80, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                menu.energyCapacity()), 8, 108, TEXT, false);
        // How far along the block under the drill is, on the right of the stored line.
        Component progress = Component.translatable("gui.futuretech.quarry.progress",
                Math.round(menu.diggingProgress() * 100));
        graphics.text(font, progress, 169 - font.width(progress), 108, TEXT, false);
    }
}
