package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.menu.TeleporterMenu;
import dev.futuretech.teleport.TeleportTarget;
import dev.futuretech.teleport.TeleporterRenamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The pad's name to edit at the top, then the one card slot with its row: the card's name, what
 * the trip costs, and the row lit when it is the chosen destination. The energy column stands at
 * the right. The rest of the pad's destinations are on the network and picked from a panel, so
 * a pad sending to one of those shows no row lit here.
 */
public final class TeleporterScreen extends AbstractContainerScreen<TeleporterMenu> implements TabbedScreen {
    private static final int LABEL_X = 7;
    /** The card's row beside its slot: from the slot's right edge to the energy column. */
    private static final int ROW_X = TeleporterMenu.CARD_X + 24;
    private static final int ROW_WIDTH = 120;
    /**
     * The name box sits over the row, its frame on the row's border: the row's border is a pixel
     * outside its face and the box's frame is the outermost pixel of its own bounds, so the box
     * goes a pixel out on each side for the two edges to line up. Its label is in the slot's column.
     */
    private static final int NAME_LABEL_X = 5;
    private static final int NAME_X = ROW_X - 1;
    /** Under the header and its shadow, not on them. */
    private static final int NAME_Y = 21;
    private static final int NAME_WIDTH = ROW_WIDTH + 2;
    private static final int NAME_HEIGHT = 12;
    private static final int ROW_HEIGHT = 16;
    private static final int ENERGY_X = 156;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = TeleporterMenu.LIST_HEIGHT;
    /** Centred on the card row, which is itself centred in the window: the middle of the window. */
    private static final int ENERGY_TOP = TeleporterMenu.CARD_Y + ROW_HEIGHT / 2 - ENERGY_HEIGHT / 2;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int SELECTED = 0xFF55E7ED;
    private static final int UNREACHABLE = 0xFFB03A2E;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final TabStrip tabs;

    @Override
    public TabStrip tabs() { return tabs; }
    private EditBox nameBox;

    public TeleporterScreen(TeleporterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TeleporterMenu.IMAGE_WIDTH, TeleporterMenu.INVENTORY_Y + 82);
        titleLabelX = LABEL_X;
        inventoryLabelX = LABEL_X;
        inventoryLabelY = TeleporterMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new UpgradeTab(menu, font), new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + NAME_X, topPos + NAME_Y, NAME_WIDTH, NAME_HEIGHT, Component.translatable("gui.futuretech.teleporter.name"));
        nameBox.setMaxLength(TeleporterMenu.NAME_LENGTH);
        nameBox.setValue(menu.name());
        nameBox.setHint(Component.translatable("gui.futuretech.teleporter.name_hint"));
        // Every keystroke goes up: the server keeps the last one, and the name is short.
        nameBox.setResponder(name -> {
            if (name.equals(menu.name())) return;
            menu.setName(name);
            ClientPacketDistributor.sendToServer(new TeleporterRenamePayload(name));
        });
        addRenderableWidget(nameBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        int rowX = x + ROW_X;
        int rowY = y + TeleporterMenu.CARD_Y;
        boolean selected = menu.selected() == TeleporterBlockEntity.CARD_SLOT;
        graphics.fill(rowX - 1, rowY - 1, rowX + ROW_WIDTH + 1, rowY + ROW_HEIGHT + 1, selected ? SELECTED : ROW_BACK);
        graphics.fill(rowX, rowY, rowX + ROW_WIDTH, rowY + ROW_HEIGHT, ROW_FACE);
        if (menu.card() != null && isOverRow(mouseX, mouseY)) {
            graphics.fill(rowX, rowY, rowX + ROW_WIDTH, rowY + ROW_HEIGHT, 0x40FFFFFF);
        }
        int energyTop = y + ENERGY_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    private boolean isOverRow(int mouseX, int mouseY) {
        int rowX = leftPos + ROW_X;
        int rowY = topPos + TeleporterMenu.CARD_Y;
        return mouseX >= rowX && mouseX < rowX + ROW_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.teleporter.name"), NAME_LABEL_X, NAME_Y + 2, TEXT, false);
        TeleportTarget target = menu.card();
        int textY = TeleporterMenu.CARD_Y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
        if (target == null) {
            graphics.text(font, Component.translatable("gui.futuretech.teleporter.empty"), ROW_X + 4, textY, 0xFF8B959F, false);
            return;
        }
        String cost = String.format("%,d FE", menu.cost());
        int costWidth = font.width(cost);
        String name = font.plainSubstrByWidth(target.name(), ROW_WIDTH - costWidth - 12);
        int colour = menu.reaches() && !menu.cardMissing() ? TITLE : UNREACHABLE;
        graphics.text(font, name, ROW_X + 4, textY, colour, false);
        graphics.text(font, cost, ROW_X + ROW_WIDTH - 4 - costWidth, textY, colour, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        TeleportTarget target = menu.card();
        if (target != null && isOverRow(mouseX, mouseY)) {
            var pos = target.pos().pos();
            var lines = new java.util.ArrayList<>(List.of(
                    Component.literal(target.name()).getVisualOrderText(),
                    Component.translatable("item.futuretech.teleport_card.target", pos.getX(), pos.getY(), pos.getZ(),
                            target.dimensionName()).getVisualOrderText()));
            if (!menu.reaches()) {
                lines.add(Component.translatable("message.futuretech.teleporter.dimension", TeleporterBlockEntity.CROSS_DIMENSION_LEVEL).getVisualOrderText());
            }
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        if (menu.card() != null && isOverRow((int) event.x(), (int) event.y())) {
            if (menu.cardMissing()) return true;
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) {
                gameMode.handleInventoryButtonClick(menu.containerId, TeleporterMenu.SELECT_BASE + TeleporterBlockEntity.CARD_SLOT);
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** While the name box has the focus it takes the keys, the inventory key included; Escape still closes. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return nameBox.keyPressed(event) || nameBox.canConsumeInput() || super.keyPressed(event);
    }
}
