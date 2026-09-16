package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.CableConnectorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * One connector's two directions, on the same grey panel and dark blue header the machines use.
 * The buttons borrow the side configuration's toggle look, in the palette the mod already uses
 * for energy: blue on one direction, orange on the other. Cables whose connectors carry a
 * priority get a third row under them, with the value between a minus and a plus; cables whose
 * connectors carry a colour get a row with a swatch of it that unfolds a picker of the sixteen
 * dyes below; cables
 * whose connectors take a filter card get a row with the card's slot, a gear that opens the card
 * once one is in, and the player's inventory below to take the card from.
 */
public final class CableConnectorScreen extends AbstractContainerScreen<CableConnectorMenu> {
    /** A rectangle in panel coordinates. */
    private record Box(int x, int y, int width, int height) {
        boolean contains(int panelX, int panelY) {
            return panelX >= x && panelX < x + width && panelY >= y && panelY < y + height;
        }
    }

    private static final int WIDTH = 176;
    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int BUTTON_WIDTH = (WIDTH - MARGIN * 2 - GAP) / 2;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTON_TOP = 26;
    private static final int PRIORITY_TOP = BUTTON_TOP + BUTTON_HEIGHT + GAP;
    private static final int STEP_SIZE = 18;
    private static final int VALUE_WIDTH = 26;
    private static final int STEP_GAP = 4;
    private static final int CHANNEL_TOP = PRIORITY_TOP + STEP_SIZE + GAP;
    private static final int COLOR_TOP = CHANNEL_TOP + STEP_SIZE + GAP;
    private static final int PICKER_COLUMNS = 4;
    private static final int PICKER_SWATCH = 12;
    private static final int PICKER_PITCH = PICKER_SWATCH + 2;
    private static final int PICKER_PADDING = 3;
    private static final int PICKER_SIZE = PICKER_PADDING * 2 + PICKER_COLUMNS * PICKER_PITCH - 2;
    private static final int GEAR_SIZE = 16;
    private static final Identifier GEAR = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "gear");
    private static final int INSERT_COLOR = 0xFF1676C4;
    private static final int EXTRACT_COLOR = 0xFFEC761C;
    private static final int OFF_COLOR = 0xFF56616D;
    private static final int VALUE_BACKGROUND = 0xFF2B333B;

    private static final Box INSERT = new Box(MARGIN, BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
    private static final Box EXTRACT = new Box(MARGIN + BUTTON_WIDTH + GAP, BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
    private static final Box RAISE = new Box(WIDTH - MARGIN - STEP_SIZE, PRIORITY_TOP, STEP_SIZE, STEP_SIZE);
    private static final Box VALUE = new Box(RAISE.x() - STEP_GAP - VALUE_WIDTH, PRIORITY_TOP, VALUE_WIDTH, STEP_SIZE);
    private static final Box LOWER = new Box(VALUE.x() - STEP_GAP - STEP_SIZE, PRIORITY_TOP, STEP_SIZE, STEP_SIZE);
    /** The channel row repeats the priority row's controls one row down. */
    private static final Box RAISE_CHANNEL = new Box(RAISE.x(), CHANNEL_TOP, STEP_SIZE, STEP_SIZE);
    private static final Box CHANNEL_VALUE = new Box(VALUE.x(), CHANNEL_TOP, VALUE_WIDTH, STEP_SIZE);
    private static final Box LOWER_CHANNEL = new Box(LOWER.x(), CHANNEL_TOP, STEP_SIZE, STEP_SIZE);
    /** The swatch sits in the same column as the filter slot, one row above it, drawn like a slot. */
    private static final Box COLOR = new Box(CableConnectorMenu.FILTER_SLOT_X - 1, COLOR_TOP, 18, 18);
    private static final Box UPGRADE = new Box(CableConnectorMenu.UPGRADE_SLOT_X - 1, CableConnectorMenu.UPGRADE_SLOT_Y - 1, 18, 18);
    private static final Box FILTER = new Box(CableConnectorMenu.FILTER_SLOT_X - 1, CableConnectorMenu.FILTER_SLOT_Y - 1, 18, 18);
    /** The picker hangs off the swatch's bottom edge and covers whatever is under it while open. */
    private static final Box PICKER = new Box(COLOR.x(), COLOR.y() + COLOR.height() + 1, PICKER_SIZE, PICKER_SIZE);

    private boolean pickerOpen;
    private static final Box GEAR_BOX = new Box(FILTER.x() + FILTER.width() + GAP, CableConnectorMenu.FILTER_SLOT_Y, GEAR_SIZE, GEAR_SIZE);

    public CableConnectorScreen(CableConnectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, height(menu));
        titleLabelX = MARGIN;
        // Without a filter row there are no slots and no player inventory, so no inventory label either.
        inventoryLabelY = menu.kind().filtered() ? CableConnectorMenu.INVENTORY_TOP - 12 : Integer.MIN_VALUE;
    }

    /** Each row a kind has adds to the panel; a filtered kind ends with the player's inventory. */
    private static int height(CableConnectorMenu menu) {
        if (menu.kind().filtered()) return CableConnectorMenu.INVENTORY_TOP + 82;
        if (menu.kind().coloured()) return COLOR_TOP + 18 + MARGIN;
        if (menu.kind().prioritised()) return PRIORITY_TOP + STEP_SIZE + MARGIN;
        return BUTTON_TOP + BUTTON_HEIGHT + MARGIN;
    }

    private boolean isOver(Box box, int mouseX, int mouseY) {
        return box.contains(mouseX - leftPos, mouseY - topPos);
    }

    private static Box pickerSwatch(DyeColor color) {
        return new Box(PICKER.x() + PICKER_PADDING + color.getId() % PICKER_COLUMNS * PICKER_PITCH,
                PICKER.y() + PICKER_PADDING + color.getId() / PICKER_COLUMNS * PICKER_PITCH, PICKER_SWATCH, PICKER_SWATCH);
    }

    /** The dye under the pointer while the picker is open, if any. */
    private @Nullable DyeColor pickerAt(int mouseX, int mouseY) {
        if (!pickerOpen) return null;
        for (DyeColor color : DyeColor.values()) {
            if (isOver(pickerSwatch(color), mouseX, mouseY)) return color;
        }
        return null;
    }

    /** The picker is drawn after the slots, on its own stratum, so it sits over the rows beneath it. */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        if (!pickerOpen) return;
        graphics.nextStratum();
        int x = leftPos + PICKER.x();
        int y = topPos + PICKER.y();
        graphics.fill(x - 1, y - 1, x + PICKER.width() + 1, y + PICKER.height() + 1, 0xFF111820);
        graphics.fill(x, y, x + PICKER.width(), y + PICKER.height(), 0xFF65717D);
        for (DyeColor color : DyeColor.values()) {
            Box box = pickerSwatch(color);
            int sx = leftPos + box.x();
            int sy = topPos + box.y();
            if (color == menu.color()) graphics.fill(sx - 1, sy - 1, sx + PICKER_SWATCH + 1, sy + PICKER_SWATCH + 1, 0xFFFFFFFF);
            graphics.fill(sx, sy, sx + PICKER_SWATCH, sy + PICKER_SWATCH, color.getTextureDiffuseColor());
            if (isOver(box, mouseX, mouseY)) graphics.fill(sx, sy, sx + PICKER_SWATCH, sy + PICKER_SWATCH, 0x40FFFFFF);
        }
    }


    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawButton(graphics, INSERT, menu.inserts() ? INSERT_COLOR : OFF_COLOR, mouseX, mouseY);
        drawButton(graphics, EXTRACT, menu.extracts() ? EXTRACT_COLOR : OFF_COLOR, mouseX, mouseY);
        if (!menu.kind().prioritised()) return;
        drawStepper(graphics, LOWER, VALUE, RAISE, mouseX, mouseY);
        if (menu.kind().coloured()) {
            drawStepper(graphics, LOWER_CHANNEL, CHANNEL_VALUE, RAISE_CHANNEL, mouseX, mouseY);
            // A slot frame holding the dye, so it reads as one more thing set on this row.
            int sx = leftPos + COLOR.x() + 1;
            int sy = topPos + COLOR.y() + 1;
            drawSlot(graphics, sx, sy);
            graphics.fill(sx + 1, sy + 1, sx + 15, sy + 15, menu.color().getTextureDiffuseColor());
            if (isOver(COLOR, mouseX, mouseY)) graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF);
        }
        if (!menu.kind().filtered()) return;
        drawSlots(graphics, leftPos, topPos, menu.slots);
        if (menu.hasFilter()) {
            int gearX = leftPos + GEAR_BOX.x();
            int gearY = topPos + GEAR_BOX.y();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GEAR, gearX, gearY, GEAR_SIZE, GEAR_SIZE);
            if (isOver(GEAR_BOX, mouseX, mouseY)) graphics.fill(gearX, gearY, gearX + GEAR_SIZE, gearY + GEAR_SIZE, 0x40FFFFFF);
        }
    }

    /** A minus button, a dark value box and a plus button on one row. */
    private void drawStepper(GuiGraphicsExtractor graphics, Box lower, Box value, Box raise, int mouseX, int mouseY) {
        drawButton(graphics, lower, OFF_COLOR, mouseX, mouseY);
        drawButton(graphics, raise, OFF_COLOR, mouseX, mouseY);
        int x = leftPos + value.x();
        int y = topPos + value.y();
        graphics.fill(x, y, x + value.width(), y + value.height(), 0xFF394651);
        graphics.fill(x + 1, y + 1, x + value.width() - 1, y + value.height() - 1, VALUE_BACKGROUND);
    }

    private void drawButton(GuiGraphicsExtractor graphics, Box box, int border, int mouseX, int mouseY) {
        int x = leftPos + box.x();
        int y = topPos + box.y();
        int right = x + box.width();
        int bottom = y + box.height();
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1, border);
        graphics.fill(x, y, right, bottom, 0xFF65717D);
        graphics.fill(x, y, right, y + 1, 0xFFB5C0CA);
        graphics.fill(x, y + 1, x + 1, bottom, 0xFF9AA7B3);
        graphics.fill(x + 1, bottom - 1, right, bottom, 0xFF394651);
        graphics.fill(right - 1, y + 1, right, bottom, 0xFF394651);
        if (isOver(box, mouseX, mouseY)) graphics.fill(x, y, right, bottom, 0x40FFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        drawCentred(graphics, INSERT, Component.translatable("gui.futuretech.cable.insert"), menu.inserts() ? TITLE : 0xFFC6CED6);
        drawCentred(graphics, EXTRACT, Component.translatable("gui.futuretech.cable.extract"), menu.extracts() ? TITLE : 0xFFC6CED6);
        if (!menu.kind().prioritised()) return;
        int baseline = PRIORITY_TOP + (STEP_SIZE - font.lineHeight) / 2;
        String keys = menu.kind().translationKey();
        graphics.text(font, Component.translatable(keys + ".priority"), MARGIN, baseline, TITLE, false);
        drawStepSymbol(graphics, LOWER, false);
        drawStepSymbol(graphics, RAISE, true);
        int priority = menu.priority();
        drawCentred(graphics, VALUE, Component.literal(priority > 0 ? "+" + priority : Integer.toString(priority)), TITLE);
        if (menu.kind().coloured()) {
            graphics.text(font, Component.translatable(keys + ".channel"), MARGIN,
                    CHANNEL_TOP + (STEP_SIZE - font.lineHeight) / 2, TITLE, false);
            drawStepSymbol(graphics, LOWER_CHANNEL, false);
            drawStepSymbol(graphics, RAISE_CHANNEL, true);
            drawCentred(graphics, CHANNEL_VALUE, Component.literal(Integer.toString(menu.channel())), TITLE);
            graphics.text(font, Component.translatable(keys + ".color"), MARGIN,
                    COLOR.y() + (COLOR.height() - font.lineHeight) / 2, TITLE, false);
        }
        if (menu.kind().upgradable()) {
            graphics.text(font, Component.translatable("gui.futuretech.item_cable.upgrade"), MARGIN,
                    UPGRADE.y() + (UPGRADE.height() - font.lineHeight) / 2, TITLE, false);
        }
        if (!menu.kind().filtered()) return;
        graphics.text(font, Component.translatable("gui.futuretech.item_cable.filter"), MARGIN,
                FILTER.y() + (FILTER.height() - font.lineHeight) / 2, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    /** Centre the visible strokes instead of the font's advance width and line height. */
    private void drawStepSymbol(GuiGraphicsExtractor graphics, Box box, boolean plus) {
        int x = box.x() + (box.width() - 5) / 2;
        int y = box.y() + (box.height() - 5) / 2;
        graphics.fill(x, y + 2, x + 5, y + 3, TITLE);
        if (plus) graphics.fill(x + 2, y, x + 3, y + 5, TITLE);
    }

    /** Labels are drawn in the label pass, whose origin is already the panel's corner. */
    private void drawCentred(GuiGraphicsExtractor graphics, Box box, Component label, int color) {
        int x = box.x() + (box.width() - font.width(label)) / 2;
        int baseline = box.y() + (box.height() - font.lineHeight) / 2;
        graphics.text(font, label, x, baseline, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // While the picker is open only its dyes answer, by name; nothing under it does.
        if (pickerOpen) {
            DyeColor color = pickerAt(mouseX, mouseY);
            if (color != null) graphics.setTooltipForNextFrame(colorName(color), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        if (menu.kind().coloured() && isOver(COLOR, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(colorName(menu.color()), mouseX, mouseY);
        }
    }

    /** The dye's own name, as the game already translates it. */
    private static Component colorName(DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName());
    }

    /** Sends a menu button and plays the same click the side configuration tab uses. */
    private void send(int buttonId) {
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (pickerOpen) {
            // A dye picks and closes; anything else just closes, without reaching what is underneath.
            DyeColor color = pickerAt(mouseX, mouseY);
            pickerOpen = false;
            if (color != null) send(CableConnectorMenu.SET_COLOR + color.getId());
            return true;
        }
        int button = -1;
        if (isOver(INSERT, mouseX, mouseY)) button = CableConnectorMenu.TOGGLE_INSERT;
        else if (isOver(EXTRACT, mouseX, mouseY)) button = CableConnectorMenu.TOGGLE_EXTRACT;
        else if (menu.kind().prioritised()) {
            // Shift takes the bigger step, so reaching the ends of the range is a few clicks.
            boolean fast = event.hasShiftDown();
            if (isOver(RAISE, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.RAISE_PRIORITY_FAST : CableConnectorMenu.RAISE_PRIORITY;
            } else if (isOver(LOWER, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.LOWER_PRIORITY_FAST : CableConnectorMenu.LOWER_PRIORITY;
            } else if (menu.kind().filtered() && menu.hasFilter() && isOver(GEAR_BOX, mouseX, mouseY)) {
                button = CableConnectorMenu.OPEN_FILTER;
            } else if (menu.kind().coloured() && isOver(RAISE_CHANNEL, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.RAISE_CHANNEL_FAST : CableConnectorMenu.RAISE_CHANNEL;
            } else if (menu.kind().coloured() && isOver(LOWER_CHANNEL, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.LOWER_CHANNEL_FAST : CableConnectorMenu.LOWER_CHANNEL;
            } else if (menu.kind().coloured() && isOver(COLOR, mouseX, mouseY)) {
                pickerOpen = true;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        if (button < 0) return super.mouseClicked(event, doubleClick);
        send(button);
        return true;
    }
}
