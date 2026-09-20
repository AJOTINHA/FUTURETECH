package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.drawButton;
import static dev.futuretech.client.MachineScreenStyle.overButton;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.FutureTech;
import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.item.ItemFilterMatch;
import dev.futuretech.menu.ItemFilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The gear on the edge of an MK2 card's screen and up, the same tab the machines hang their
 * redstone control off. Open, it holds whether the list is what passes or what is kept out, and
 * how closely the card reads what it compares: one row per switch, each with the state it is in
 * on its button. Clicking the button flips it and the card is written at once.
 *
 * <p>The three below "item data" are exceptions to it, so they grey out while the card is reading
 * the item alone: a card that ignores the data has no durability to ignore in particular.
 *
 * <p>While a card is keeping an amount, the row under that switch is the amount: a minus, the
 * number in a box, a plus. The buttons step it by one; clicking the box lets the player type it,
 * three digits at most, and Enter or a click anywhere else sets it, Escape lets it go.
 */
public final class FilterSettingsTab extends MachineTab {
    private static final Identifier GEAR = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "gear");
    private static final int GEAR_SIZE = 16;
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 16;
    /** Panel between the longest label and the buttons. */
    private static final int GAP = 6;
    /** The amount row: a step button, the box, a step button, in the connector's proportions. */
    private static final int STEP_SIZE = BUTTON_HEIGHT;
    private static final int VALUE_WIDTH = 26;
    private static final int STEP_GAP = 4;
    private static final int VALUE_BORDER = 0xFF394651;
    private static final int VALUE_BORDER_FOCUSED = 0xFFB5C0CA;
    private static final int VALUE_BACKGROUND = 0xFF2B333B;
    private static final int DIGITS = 3;

    private final ItemFilterMenu menu;
    /** The switches this card offers, in order; the list's own mode is the row above them. */
    private final List<ItemFilterMatch.Option> options = new ArrayList<>();
    private final int labelWidth;
    /** What the player has typed into the box so far; null while the box is not being typed into. */
    private String typed;
    /** Where the content was drawn last frame, for the click that lands before the next one. */
    private int contentX;
    private int contentY;

    public FilterSettingsTab(ItemFilterMenu menu, Font font) {
        super(font);
        this.menu = menu;
        int widest = font.width(Component.translatable("gui.futuretech.filter.list"));
        for (ItemFilterMatch.Option option : ItemFilterMatch.Option.values()) {
            if (!menu.tier().offers(option)) continue;
            options.add(option);
            widest = Math.max(widest, font.width(Component.translatable(option.translationKey())));
        }
        labelWidth = widest;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.filter.settings"); }

    @Override
    protected int contentWidth() { return labelWidth + GAP + BUTTON_WIDTH + 2; }

    @Override
    protected int contentHeight() { return rows() * ROW_HEIGHT - (ROW_HEIGHT - BUTTON_HEIGHT); }

    /** Whether the card is keeping an amount right now, which is when the amount row is there. */
    private boolean counting() { return menu.tier().counts() && menu.match().count(); }

    /** The mode row, the switches, and the amount row while there is one. */
    private int rows() { return 1 + options.size() + (counting() ? 1 : 0); }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GEAR, x + (SIZE - GEAR_SIZE) / 2, y + (SIZE - GEAR_SIZE) / 2,
                GEAR_SIZE, GEAR_SIZE);
    }

    /** Whether a row is the player's to click: the exceptions wait on the data switch above them. */
    private boolean enabled(ItemFilterMatch.Option option) {
        return option == ItemFilterMatch.Option.DATA || option == ItemFilterMatch.Option.COUNT
                || menu.match().data();
    }

    private int buttonX(int contentX) { return contentX + labelWidth + GAP + 1; }

    private int rowY(int contentY, int row) { return contentY + row * ROW_HEIGHT + 1; }

    // The amount row's three columns, right-aligned with the buttons above it.
    private int raiseX(int contentX) { return buttonX(contentX) + BUTTON_WIDTH - STEP_SIZE; }

    private int valueX(int contentX) { return raiseX(contentX) - STEP_GAP - VALUE_WIDTH; }

    private int lowerX(int contentX) { return valueX(contentX) - STEP_GAP - STEP_SIZE; }

    private int amountY(int contentY) { return rowY(contentY, 1 + options.size()); }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        this.contentX = contentX;
        this.contentY = contentY;
        // The list's own mode, which the MK1 has on its main screen and these cards have here.
        Component mode = Component.translatable(menu.mode().translationKey());
        drawRow(graphics, contentX, contentY, 0, Component.translatable("gui.futuretech.filter.list"), mode, true,
                mouseX, mouseY);
        for (int row = 0; row < options.size(); row++) {
            ItemFilterMatch.Option option = options.get(row);
            boolean follows = menu.match().follows(option);
            Component state = Component.translatable(follows
                    ? "gui.futuretech.filter.match.follow" : "gui.futuretech.filter.match.ignore");
            drawRow(graphics, contentX, contentY, row + 1, Component.translatable(option.translationKey()), state,
                    enabled(option), mouseX, mouseY);
        }
        if (counting()) drawAmount(graphics, contentX, contentY, mouseX, mouseY);
        else typed = null;
    }

    private void drawRow(GuiGraphicsExtractor graphics, int contentX, int contentY, int row, Component label,
                         Component state, boolean enabled, int mouseX, int mouseY) {
        int y = rowY(contentY, row);
        int x = buttonX(contentX);
        graphics.text(font, label, contentX, y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1,
                enabled ? TEXT_COLOR : 0xFF8B959F, false);
        drawButton(graphics, font, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, state,
                enabled && isFullyOpen() && overButton(mouseX, mouseY, x, y, BUTTON_WIDTH, BUTTON_HEIGHT), enabled);
    }

    private void drawAmount(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        int y = amountY(contentY);
        drawStep(graphics, lowerX(contentX), y, false, mouseX, mouseY);
        drawStep(graphics, raiseX(contentX), y, true, mouseX, mouseY);
        int x = valueX(contentX);
        graphics.fill(x, y, x + VALUE_WIDTH, y + STEP_SIZE, typed != null ? VALUE_BORDER_FOCUSED : VALUE_BORDER);
        graphics.fill(x + 1, y + 1, x + VALUE_WIDTH - 1, y + STEP_SIZE - 1, VALUE_BACKGROUND);
        // Typing shows what has been typed, with the cursor blinking after it; otherwise the level as set.
        String shown = typed != null ? typed + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "")
                : Integer.toString(menu.count());
        graphics.text(font, shown, x + (VALUE_WIDTH - font.width(shown)) / 2, y + (STEP_SIZE - font.lineHeight) / 2 + 1,
                TITLE_COLOR, false);
    }

    /** A step button with its sign drawn in the middle, five pixels across like the connector's. */
    private void drawStep(GuiGraphicsExtractor graphics, int x, int y, boolean plus, int mouseX, int mouseY) {
        drawButton(graphics, font, x, y, STEP_SIZE, STEP_SIZE, CommonComponents.EMPTY,
                isFullyOpen() && overButton(mouseX, mouseY, x, y, STEP_SIZE, STEP_SIZE));
        int sx = x + (STEP_SIZE - 5) / 2;
        int sy = y + (STEP_SIZE - 5) / 2;
        graphics.fill(sx, sy + 2, sx + 5, sy + 3, TITLE_COLOR);
        if (plus) graphics.fill(sx + 2, sy, sx + 3, sy + 5, TITLE_COLOR);
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}

    /** A click anywhere but in the box sets what was typed into it before anything else happens. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        if (typed != null && !overValue(event.x(), event.y())) commit();
        return super.mouseClicked(event);
    }

    private boolean overValue(double mouseX, double mouseY) {
        if (!isFullyOpen() || !counting()) return false;
        return overButton(mouseX, mouseY, valueX(contentX), amountY(contentY), VALUE_WIDTH, STEP_SIZE);
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        int x = buttonX(contentX);
        for (int row = 0; row <= options.size(); row++) {
            if (!overButton(event.x(), event.y(), x, rowY(contentY, row), BUTTON_WIDTH, BUTTON_HEIGHT)) continue;
            if (row == 0) {
                send(ItemFilterMenu.TOGGLE_MODE);
                return true;
            }
            ItemFilterMatch.Option option = options.get(row - 1);
            if (enabled(option)) send(ItemFilterMenu.TOGGLE_MATCH_BASE + option.ordinal());
            return true;
        }
        if (!counting()) return false;
        int y = amountY(contentY);
        if (overButton(event.x(), event.y(), lowerX(contentX), y, STEP_SIZE, STEP_SIZE)) {
            setCount(menu.count() - 1);
            return true;
        }
        if (overButton(event.x(), event.y(), raiseX(contentX), y, STEP_SIZE, STEP_SIZE)) {
            setCount(menu.count() + 1);
            return true;
        }
        if (overButton(event.x(), event.y(), valueX(contentX), y, VALUE_WIDTH, STEP_SIZE)) {
            typed = "";
            return true;
        }
        return false;
    }

    /** While the box is being typed into every key is its, so the inventory key does not close the screen. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (typed == null) return false;
        switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> typed = null;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> commit();
            case InputConstants.KEY_BACKSPACE -> {
                if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
            }
            default -> {}
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (typed == null) return false;
        int codepoint = event.codepoint();
        if (codepoint >= '0' && codepoint <= '9' && typed.length() < DIGITS) {
            // A leading zero is nothing typed yet, so "0" then "5" is five, not "05".
            typed = (typed.equals("0") ? "" : typed) + (char) codepoint;
        }
        return true;
    }

    /** What was typed becomes the level; an empty box leaves it as it was. */
    private void commit() {
        if (typed == null) return;
        if (!typed.isEmpty()) setCount(Integer.parseInt(typed));
        typed = null;
    }

    private void setCount(int count) {
        send(ItemFilterMenu.SET_COUNT_BASE + Math.clamp(count, 0, ItemFilterItem.MAX_COUNT));
    }

    private void send(int button) {
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, button);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
