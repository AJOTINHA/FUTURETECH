package dev.futuretech.api.gui;

import dev.futuretech.client.MachineScreenStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * A tab glued to one side edge of a machine screen. Collapsed it is a square with an icon; clicked
 * it grows in place into a panel with a title and whatever content the subclass draws. Tabs are
 * stacked and driven by a {@link TabStrip}.
 */
public abstract class MachineTab {
    public static final int SIZE = 20;
    protected static final int PADDING = 6;
    protected static final int TITLE_COLOR = 0xFFFFFFFF;
    protected static final int TEXT_COLOR = 0xFF283541;
    /** Opening and closing slide over this many milliseconds. */
    private static final double SLIDE_MILLIS = 150.0;

    /** Which edge of the screen the tab hangs off; it grows away from that edge. */
    public enum Side { LEFT, RIGHT }

    protected final Font font;
    private final Side side;
    private boolean open;
    // 0 = closed, 1 = fully open; moves towards the target every frame for the slide.
    private double slide;
    private long lastFrameNanos;
    private int x;
    private int y;
    private int width = SIZE;
    private int height = SIZE;

    protected MachineTab(Font font) {
        this(font, Side.RIGHT);
    }

    protected MachineTab(Font font, Side side) {
        this.font = font;
        this.side = side;
    }

    protected abstract Component title();

    /** Width of the content area, without the tab padding. */
    protected abstract int contentWidth();

    /** Height of the content area, without the tab padding. */
    protected abstract int contentHeight();

    /** Draws the collapsed icon inside the square whose top-left corner is {@code (x, y)}. */
    protected abstract void drawIcon(GuiGraphicsExtractor graphics, int x, int y);

    protected abstract void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY);

    /** Handles a click on the content while fully open; return true when it was consumed. */
    protected abstract boolean clickContent(MouseButtonEvent event, int contentX, int contentY);

    /** Tabs opt in to additional buttons without changing header or other tab behaviour. */
    protected boolean acceptsContentButton(int button) { return button == 0; }

    protected abstract void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY);

    public Side side() { return side; }

    public boolean isOpen() { return open; }

    public void setOpen(boolean open) { this.open = open; }

    public boolean isFullyOpen() { return open && slide >= 1; }

    /** Drawn over the screen's panel every frame, before the tabs themselves; for marks a tab puts on the panel while open. */
    public void drawPanelOverlay(GuiGraphicsExtractor graphics, int leftPos, int topPos) {}

    /** Current height, including the part still sliding; the strip stacks the next tab under it. */
    public int height() { return height; }

    /** Where the tab is on screen as of the last frame, however far it has slid; what JEI keeps clear of. */
    public Rect2i bounds() { return new Rect2i(x, y, width, height); }

    /** Width when fully open; tabs whose content has a fixed position (menu slots) pin this. */
    protected int fullWidth() {
        return Math.max(PADDING * 2 + contentWidth(), SIZE + font.width(title()) + PADDING + 2);
    }

    private int fullHeight() { return SIZE + PADDING + contentHeight() + PADDING; }

    private int contentX() { return x + (width - contentWidth()) / 2; }

    private int contentY() { return y + SIZE + PADDING; }

    /** The icon square stays against the screen, so on a left tab it sits at the tab's far end. */
    private int iconX() { return side == Side.LEFT ? x + width - SIZE : x; }

    /**
     * @param anchorX the screen edge the tab hangs off: its left corner for a right tab, its right
     *                corner for a left tab, which therefore grows away from the screen
     */
    public void render(GuiGraphicsExtractor graphics, int anchorX, int y, int mouseX, int mouseY) {
        advanceSlide();
        width = SIZE + (int) Math.round((fullWidth() - SIZE) * slide);
        height = SIZE + (int) Math.round((fullHeight() - SIZE) * slide);
        this.x = side == Side.LEFT ? anchorX - width : anchorX;
        this.y = y;
        int iconX = iconX();
        boolean hovered = slide <= 0 && isOver(mouseX, mouseY, iconX, y, SIZE, SIZE);
        // Same cut corners, shadow and header band as the machine screens; collapsed, the band fills the tab.
        MachineScreenStyle.drawPanel(graphics, x, y, width, height);
        if (hovered) graphics.fill(x + 2, y + 3, x + width - 2, y + SIZE - 2, 0x1AFFFFFF);
        drawIcon(graphics, iconX, y);
        if (slide <= 0) return;

        // Content only shows inside the part of the tab that has grown so far.
        if (side == Side.LEFT) graphics.enableScissor(x + 2, y, x + width, y + height - 2);
        else graphics.enableScissor(x, y, x + width - 2, y + height - 2);
        int titleX = side == Side.LEFT ? iconX - font.width(title()) - 2 : x + SIZE;
        graphics.text(font, title(), titleX, y + 6, TITLE_COLOR, false);
        drawContent(graphics, contentX(), contentY(), mouseX, mouseY);
        graphics.disableScissor();
    }

    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, iconX(), y, SIZE, SIZE)) {
            graphics.setTooltipForNextFrame(title(), mouseX, mouseY);
            return;
        }
        if (isFullyOpen()) contentTooltip(graphics, contentX(), contentY(), mouseX, mouseY);
    }

    /** Returns true when the click landed on this tab. */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (isOver(event.x(), event.y(), iconX(), y, SIZE, SIZE)) {
            if (event.button() != 0) return false;
            open = !open;
            return true;
        }
        return acceptsContentButton(event.button()) && isFullyOpen() && isOver(event.x(), event.y(), x, y, width, height)
                && clickContent(event, contentX(), contentY());
    }

    /** A key while the screen is up; a tab with something to type into takes what is its. */
    public boolean keyPressed(KeyEvent event) { return false; }

    public boolean charTyped(CharacterEvent event) { return false; }

    private void advanceSlide() {
        long now = System.nanoTime();
        double elapsedMillis = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;
        double step = elapsedMillis / SLIDE_MILLIS;
        slide = open ? Math.min(1, slide + step) : Math.max(0, slide - step);
    }

    protected static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
