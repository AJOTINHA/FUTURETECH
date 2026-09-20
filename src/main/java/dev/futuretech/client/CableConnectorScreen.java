package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.FutureTech;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.redstone.client.RedstoneModeIcons;
import dev.futuretech.block.CableKind;
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

import java.util.List;

/**
 * One connector's two directions, on the same grey panel and dark blue header the machines use.
 * The buttons borrow the side configuration's toggle look, in the palette the mod already uses
 * for energy: blue on one direction, orange on the other. Cables whose connectors carry a
 * priority get a third row under them, with the value between a minus and a plus; cables whose
 * connectors carry a colour get a channel row like it, and a row with a swatch of the colour that
 * unfolds a picker of the sixteen dyes below. Every kind gets a redstone row, under the channel
 * where there is one, with the mode's icon unfolding a picker of the three modes the same way.
 * Cables whose connectors take a filter card get a row with the card's slot, a gear that opens
 * the card once one is in, and the player's inventory below to take the card from.
 *
 * <p>The rows stack in that order, each kind keeping only the ones it has, so a kind with a
 * colour and no priority — the redstone cable — starts its channel where the priority would be.
 * A kind that carries a signal gets a row of two switches right under the directions: the
 * sensor, which reads the block as a comparator would, and strong, which powers the block
 * through instead of only waking it; they light in the cable's own colour when on.
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
    private static final int STEP_SIZE = 18;
    private static final int VALUE_WIDTH = 26;
    private static final int STEP_GAP = 4;
    private static final int SWATCH_SIZE = 18;
    private static final int SWITCH_HEIGHT = 18;
    private static final int REDSTONE_TILE = RedstoneModeIcons.SIZE;
    private static final int REDSTONE_PITCH = REDSTONE_TILE + 4;
    private static final int REDSTONE_PADDING = 3;
    private static final int REDSTONE_PICKER_WIDTH = REDSTONE_PADDING * 2 + RedstoneMode.values().length * REDSTONE_PITCH - 4;
    private static final int REDSTONE_PICKER_HEIGHT = REDSTONE_PADDING * 2 + REDSTONE_TILE;
    private static final int REDSTONE_SELECTED = 0xFFEC761C;
    private static final int REDSTONE_UNSELECTED = 0xFF56616D;
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
    /** The stepper's columns, shared by the priority row and the channel row. */
    private static final int RAISE_X = WIDTH - MARGIN - STEP_SIZE;
    private static final int VALUE_X = RAISE_X - STEP_GAP - VALUE_WIDTH;
    private static final int LOWER_X = VALUE_X - STEP_GAP - STEP_SIZE;
    /** The swatches sit in the same column as the filter slot, drawn like a slot. */
    private static final int SWATCH_X = CableConnectorMenu.FILTER_SLOT_X - 1;
    private static final Box UPGRADE = new Box(CableConnectorMenu.UPGRADE_SLOT_X - 1, CableConnectorMenu.UPGRADE_SLOT_Y - 1, 18, 18);
    private static final Box FILTER = new Box(CableConnectorMenu.FILTER_SLOT_X - 1, CableConnectorMenu.FILTER_SLOT_Y - 1, 18, 18);
    private static final Box GEAR_BOX = new Box(FILTER.x() + FILTER.width() + GAP, CableConnectorMenu.FILTER_SLOT_Y, GEAR_SIZE, GEAR_SIZE);

    /**
     * Where each row a kind has starts, from the top of the panel; a row the kind lacks is left
     * out and the ones below it move up. The bottom is where the panel ends when nothing else
     * follows the last row.
     */
    private record Rows(int switches, int priority, int channel, int redstone, int color, int bottom) {
        static Rows of(CableKind kind) {
            int next = BUTTON_TOP + BUTTON_HEIGHT + GAP;
            int switches = -1;
            int priority = -1;
            int channel = -1;
            int color = -1;
            if (kind.signalled()) {
                switches = next;
                next += SWITCH_HEIGHT + GAP;
            }
            if (kind.prioritised()) {
                priority = next;
                next += STEP_SIZE + GAP;
            }
            if (kind.coloured()) {
                channel = next;
                next += STEP_SIZE + GAP;
            }
            int redstone = next;
            next += SWATCH_SIZE + GAP;
            if (kind.coloured()) {
                color = next;
                next += SWATCH_SIZE + GAP;
            }
            return new Rows(switches, priority, channel, redstone, color, next - GAP + MARGIN);
        }
    }

    private boolean pickerOpen;
    private boolean redstonePickerOpen;
    /** The sensor and strong switches, under the directions; only a signalled kind has them. */
    private final Box sensor;
    private final Box strong;
    /** The priority row: a minus, the value and a plus; only a prioritised kind has it. */
    private final Box raise;
    private final Box value;
    private final Box lower;
    /** The channel row repeats the priority row's controls; only a coloured kind has it. */
    private final Box raiseChannel;
    private final Box channelValue;
    private final Box lowerChannel;
    /** The colour swatch; only a coloured kind has it. */
    private final Box color;
    /** The picker hangs off the swatch's bottom edge and covers whatever is under it while open. */
    private final Box picker;
    /** The redstone swatch, in the colour's column; where it sits depends on the kind's rows. */
    private final Box redstone;
    /** The redstone picker hangs off its swatch's bottom edge, like the colour's. */
    private final Box redstonePicker;

    public CableConnectorScreen(CableConnectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, height(menu));
        titleLabelX = MARGIN;
        Rows rows = Rows.of(menu.kind());
        sensor = new Box(INSERT.x(), rows.switches(), BUTTON_WIDTH, SWITCH_HEIGHT);
        strong = new Box(EXTRACT.x(), rows.switches(), BUTTON_WIDTH, SWITCH_HEIGHT);
        raise = new Box(RAISE_X, rows.priority(), STEP_SIZE, STEP_SIZE);
        value = new Box(VALUE_X, rows.priority(), VALUE_WIDTH, STEP_SIZE);
        lower = new Box(LOWER_X, rows.priority(), STEP_SIZE, STEP_SIZE);
        raiseChannel = new Box(RAISE_X, rows.channel(), STEP_SIZE, STEP_SIZE);
        channelValue = new Box(VALUE_X, rows.channel(), VALUE_WIDTH, STEP_SIZE);
        lowerChannel = new Box(LOWER_X, rows.channel(), STEP_SIZE, STEP_SIZE);
        color = new Box(SWATCH_X, rows.color(), SWATCH_SIZE, SWATCH_SIZE);
        picker = new Box(color.x(), color.y() + color.height() + 1, PICKER_SIZE, PICKER_SIZE);
        redstone = new Box(SWATCH_X, rows.redstone(), SWATCH_SIZE, SWATCH_SIZE);
        redstonePicker = new Box(redstone.x(), redstone.y() + redstone.height() + 1, REDSTONE_PICKER_WIDTH, REDSTONE_PICKER_HEIGHT);
        // Without a filter row there are no slots and no player inventory, so no inventory label either.
        inventoryLabelY = menu.kind().filtered() ? CableConnectorMenu.INVENTORY_TOP - 12 : Integer.MIN_VALUE;
    }

    /** Each row a kind has adds to the panel; a filtered kind ends with the player's inventory. */
    private static int height(CableConnectorMenu menu) {
        if (menu.kind().filtered()) return CableConnectorMenu.INVENTORY_TOP + 82;
        return Rows.of(menu.kind()).bottom();
    }

    private boolean isOver(Box box, int mouseX, int mouseY) {
        return box.contains(mouseX - leftPos, mouseY - topPos);
    }

    private Box pickerSwatch(DyeColor color) {
        return new Box(picker.x() + PICKER_PADDING + color.getId() % PICKER_COLUMNS * PICKER_PITCH,
                picker.y() + PICKER_PADDING + color.getId() / PICKER_COLUMNS * PICKER_PITCH, PICKER_SWATCH, PICKER_SWATCH);
    }

    /** The dye under the pointer while the picker is open, if any. */
    private @Nullable DyeColor pickerAt(int mouseX, int mouseY) {
        if (!pickerOpen) return null;
        for (DyeColor color : DyeColor.values()) {
            if (isOver(pickerSwatch(color), mouseX, mouseY)) return color;
        }
        return null;
    }

    private Box redstoneTile(RedstoneMode mode) {
        return new Box(redstonePicker.x() + REDSTONE_PADDING + mode.ordinal() * REDSTONE_PITCH,
                redstonePicker.y() + REDSTONE_PADDING, REDSTONE_TILE, REDSTONE_TILE);
    }

    /** The redstone mode under the pointer while its picker is open, if any. */
    private @Nullable RedstoneMode redstoneAt(int mouseX, int mouseY) {
        if (!redstonePickerOpen) return null;
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (isOver(redstoneTile(mode), mouseX, mouseY)) return mode;
        }
        return null;
    }

    /** The picker is drawn after the slots, on its own stratum, so it sits over the rows beneath it. */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        if (redstonePickerOpen) {
            graphics.nextStratum();
            int x = leftPos + redstonePicker.x();
            int y = topPos + redstonePicker.y();
            graphics.fill(x - 1, y - 1, x + redstonePicker.width() + 1, y + redstonePicker.height() + 1, 0xFF111820);
            graphics.fill(x, y, x + redstonePicker.width(), y + redstonePicker.height(), 0xFF65717D);
            for (RedstoneMode mode : RedstoneMode.values()) {
                Box tile = redstoneTile(mode);
                int tx = leftPos + tile.x();
                int ty = topPos + tile.y();
                boolean selected = mode == menu.redstone();
                graphics.fill(tx - 1, ty - 1, tx + REDSTONE_TILE + 1, ty + REDSTONE_TILE + 1, selected ? REDSTONE_SELECTED : REDSTONE_UNSELECTED);
                graphics.fill(tx, ty, tx + REDSTONE_TILE, ty + REDSTONE_TILE, 0xFF8B959F);
                RedstoneModeIcons.draw(graphics, mode, selected, tx, ty);
                if (isOver(tile, mouseX, mouseY)) graphics.fill(tx, ty, tx + REDSTONE_TILE, ty + REDSTONE_TILE, 0x40FFFFFF);
            }
            return;
        }
        if (!pickerOpen) return;
        graphics.nextStratum();
        int x = leftPos + picker.x();
        int y = topPos + picker.y();
        graphics.fill(x - 1, y - 1, x + picker.width() + 1, y + picker.height() + 1, 0xFF111820);
        graphics.fill(x, y, x + picker.width(), y + picker.height(), 0xFF65717D);
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
        if (menu.kind().signalled()) {
            int accent = menu.kind().accent();
            drawButton(graphics, sensor, menu.sensor() ? accent : OFF_COLOR, mouseX, mouseY);
            drawButton(graphics, strong, menu.strong() ? accent : OFF_COLOR, mouseX, mouseY);
        }
        // A slot frame holding the mode's icon, like the colour swatch, so it reads as one more setting.
        int rx = leftPos + redstone.x() + 1;
        int ry = topPos + redstone.y() + 1;
        drawSlot(graphics, rx, ry);
        RedstoneModeIcons.draw(graphics, menu.redstone(), true, rx, ry);
        if (isOver(redstone, mouseX, mouseY)) graphics.fill(rx, ry, rx + 16, ry + 16, 0x40FFFFFF);
        if (menu.kind().prioritised()) drawStepper(graphics, lower, value, raise, mouseX, mouseY);
        if (menu.kind().coloured()) {
            drawStepper(graphics, lowerChannel, channelValue, raiseChannel, mouseX, mouseY);
            // A slot frame holding the dye, so it reads as one more thing set on this row.
            int sx = leftPos + color.x() + 1;
            int sy = topPos + color.y() + 1;
            drawSlot(graphics, sx, sy);
            graphics.fill(sx + 1, sy + 1, sx + 15, sy + 15, menu.color().getTextureDiffuseColor());
            if (isOver(color, mouseX, mouseY)) graphics.fill(sx, sy, sx + 16, sy + 16, 0x40FFFFFF);
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
        if (menu.kind().signalled()) {
            String keys = menu.kind().translationKey();
            drawCentred(graphics, sensor, Component.translatable(keys + ".sensor"), menu.sensor() ? TITLE : 0xFFC6CED6);
            drawCentred(graphics, strong, Component.translatable(keys + ".strong"), menu.strong() ? TITLE : 0xFFC6CED6);
        }
        graphics.text(font, Component.translatable("gui.futuretech.redstone"), MARGIN,
                redstone.y() + (redstone.height() - font.lineHeight) / 2, TITLE, false);
        String keys = menu.kind().translationKey();
        if (menu.kind().prioritised()) {
            graphics.text(font, Component.translatable(keys + ".priority"), MARGIN,
                    value.y() + (STEP_SIZE - font.lineHeight) / 2, TITLE, false);
            drawStepSymbol(graphics, lower, false);
            drawStepSymbol(graphics, raise, true);
            int priority = menu.priority();
            drawCentred(graphics, value, Component.literal(priority > 0 ? "+" + priority : Integer.toString(priority)), TITLE);
        }
        if (menu.kind().coloured()) {
            graphics.text(font, Component.translatable(keys + ".channel"), MARGIN,
                    channelValue.y() + (STEP_SIZE - font.lineHeight) / 2, TITLE, false);
            drawStepSymbol(graphics, lowerChannel, false);
            drawStepSymbol(graphics, raiseChannel, true);
            drawCentred(graphics, channelValue, Component.literal(Integer.toString(menu.channel())), TITLE);
            graphics.text(font, Component.translatable(keys + ".color"), MARGIN,
                    color.y() + (color.height() - font.lineHeight) / 2, TITLE, false);
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
        // While a picker is open only its tiles answer; nothing under it does.
        if (redstonePickerOpen) {
            RedstoneMode mode = redstoneAt(mouseX, mouseY);
            if (mode != null) graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(mode.translationKey()).getVisualOrderText(),
                    Component.translatable(mode.descriptionKey()).getVisualOrderText()), mouseX, mouseY);
            return;
        }
        if (pickerOpen) {
            DyeColor color = pickerAt(mouseX, mouseY);
            if (color != null) graphics.setTooltipForNextFrame(colorName(color), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isOver(redstone, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(menu.redstone().translationKey()).getVisualOrderText(),
                    Component.translatable(menu.isPowered() ? "gui.futuretech.redstone.powered" : "gui.futuretech.redstone.unpowered").getVisualOrderText()),
                    mouseX, mouseY);
            return;
        }
        if (menu.kind().coloured() && isOver(color, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(colorName(menu.color()), mouseX, mouseY);
            return;
        }
        if (!menu.kind().signalled()) return;
        String keys = menu.kind().translationKey();
        if (isOver(sensor, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.translatable(keys + ".sensor.tooltip"), mouseX, mouseY);
        } else if (isOver(strong, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.translatable(keys + ".strong.tooltip"), mouseX, mouseY);
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
        if (redstonePickerOpen) {
            // A tile picks and closes; anything else just closes, without reaching what is underneath.
            RedstoneMode mode = redstoneAt(mouseX, mouseY);
            redstonePickerOpen = false;
            if (mode != null) send(CableConnectorMenu.SET_REDSTONE + mode.ordinal());
            return true;
        }
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
        else if (menu.kind().signalled() && isOver(sensor, mouseX, mouseY)) button = CableConnectorMenu.TOGGLE_SENSOR;
        else if (menu.kind().signalled() && isOver(strong, mouseX, mouseY)) button = CableConnectorMenu.TOGGLE_STRONG;
        else if (isOver(redstone, mouseX, mouseY)) {
            redstonePickerOpen = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        } else {
            // Shift takes the bigger step, so reaching the ends of the range is a few clicks.
            boolean fast = event.hasShiftDown();
            if (menu.kind().prioritised() && isOver(raise, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.RAISE_PRIORITY_FAST : CableConnectorMenu.RAISE_PRIORITY;
            } else if (menu.kind().prioritised() && isOver(lower, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.LOWER_PRIORITY_FAST : CableConnectorMenu.LOWER_PRIORITY;
            } else if (menu.kind().filtered() && menu.hasFilter() && isOver(GEAR_BOX, mouseX, mouseY)) {
                button = CableConnectorMenu.OPEN_FILTER;
            } else if (menu.kind().coloured() && isOver(raiseChannel, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.RAISE_CHANNEL_FAST : CableConnectorMenu.RAISE_CHANNEL;
            } else if (menu.kind().coloured() && isOver(lowerChannel, mouseX, mouseY)) {
                button = fast ? CableConnectorMenu.LOWER_CHANNEL_FAST : CableConnectorMenu.LOWER_CHANNEL;
            } else if (menu.kind().coloured() && isOver(color, mouseX, mouseY)) {
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
