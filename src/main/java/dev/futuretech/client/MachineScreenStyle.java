package dev.futuretech.client;

import dev.futuretech.api.upgrade.UpgradeSlot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2f;

/** Shared look of the mod's machine screens: flat grey panel, dark blue header, simple slots and gradient bars. */
public final class MachineScreenStyle {
    static final int TEXT = 0xFF283541;
    static final int TITLE = 0xFFFFFFFF;
    static final int BAR_BACK = 0xFF283541;
    static final int ENERGY_START = 0xFF1676C4;
    public static final int ENERGY_END = 0xFF55E7ED;

    /** Append the synchronized level and keep long translated/custom names inside the header. */
    static void drawMachineTitle(GuiGraphicsExtractor graphics, Font font, Component name, int mk,
                                 int x, int y, int maxWidth) {
        Component title = mk > 1 ? Component.translatable("item.futuretech.machine_level", name, mk) : name;
        float scale = Math.min(1.0F, (float) maxWidth / Math.max(1, font.width(title)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + font.lineHeight * (1 - scale) / 2);
        graphics.pose().scale(scale, scale);
        graphics.text(font, title, 0, 0, TITLE, false);
        graphics.pose().popMatrix();
    }

    public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        // Small pixel steps reproduce vanilla-style corners without rounded panels.
        drawCutCornerRect(graphics, x - 3, y + 1, width + 6, height + 4, 3, 0x08000000);
        drawCutCornerRect(graphics, x - 2, y + 1, width + 4, height + 3, 3, 0x0C000000);
        drawCutCornerRect(graphics, x - 1, y + 1, width + 2, height + 2, 3, 0x14000000);
        drawCutCornerRect(graphics, x, y, width, height, 3, 0xFF111820);
        drawCutCornerRect(graphics, x + 2, y + 2, width - 4, height - 4, 1, 0xFFBBC3CC);
        graphics.fill(x + 3, y + 2, x + width - 3, y + 3, 0xFF293747);
        graphics.fill(x + 2, y + 3, x + width - 2, y + 18, 0xFF293747);
        graphics.fill(x + 3, y + 2, x + width - 3, y + 3, 0x12FFFFFF);
        graphics.fillGradient(x + 2, y + 18, x + width - 2, y + 20, 0x18000000, 0x00000000);
    }

    /**
     * A button in the rows' style: the row's border and face, its label centred in white, and the
     * face lit while the pointer is on it. The screens that use it do their own hit-testing with
     * {@link #overButton}, so a button is a rectangle and a label, not a widget.
     */
    static void drawButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           Component label, boolean hovered) {
        drawButton(graphics, font, x, y, width, height, label, hovered, true);
    }

    /** The same button, greyed out and deaf while {@code enabled} is false. */
    static void drawButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           Component label, boolean hovered, boolean enabled) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF56616D);
        graphics.fill(x, y, x + width, y + height, enabled ? 0xFF65717D : 0xFF5B6672);
        if (hovered && enabled) graphics.fill(x, y, x + width, y + height, 0x40FFFFFF);
        graphics.text(font, label, x + (width - font.width(label)) / 2, y + (height - font.lineHeight) / 2 + 1,
                enabled ? TITLE : 0xFF8B959F, false);
    }

    static boolean overButton(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** The pencil button beside a card row: a slot-sized square in the row's colours. */
    public static final int PENCIL_WIDTH = 16;
    private static final int PENCIL_ROW_BACK = 0xFF56616D;
    private static final int PENCIL_ROW_FACE = 0xFF65717D;
    private static final int PENCIL_MUTED = 0xFF8B959F;
    private static final int PENCIL_TIP = 0xFFC9A66B;
    /**
     * The pencil, drawn pixel by pixel in the 16 by 16 of the button: the eraser at the top right,
     * a three-pixel body down to the tip, and the graphite at the bottom left.
     */
    private static final String[] PENCIL = {
            "................",
            "................",
            "...........EE...",
            "..........EEE...",
            ".........BBBE...",
            "........BBBB....",
            ".......BBBB.....",
            "......BBBB......",
            ".....BBBB.......",
            "....TBBB........",
            "...TTBB.........",
            "..GTT...........",
            "..GG............",
            "................",
            "................",
            "................",
    };

    /** A slot-sized button in the card rows' style, with the pencil on it, at the row's {@code top}. */
    static void drawPencil(GuiGraphicsExtractor graphics, int x, int top, boolean lit) {
        graphics.fill(x - 1, top - 1, x + PENCIL_WIDTH + 1, top + PENCIL_WIDTH + 1, PENCIL_ROW_BACK);
        graphics.fill(x, top, x + PENCIL_WIDTH, top + PENCIL_WIDTH, PENCIL_ROW_FACE);
        if (lit) graphics.fill(x, top, x + PENCIL_WIDTH, top + PENCIL_WIDTH, 0x40FFFFFF);
        for (int row = 0; row < PENCIL.length; row++) {
            String line = PENCIL[row];
            for (int col = 0; col < line.length(); col++) {
                int colour = switch (line.charAt(col)) {
                    case 'B' -> TITLE;
                    case 'E' -> PENCIL_MUTED;
                    case 'T' -> PENCIL_TIP;
                    case 'G' -> TEXT;
                    default -> 0;
                };
                if (colour != 0) graphics.fill(x + col, top + row, x + col + 1, top + row + 1, colour);
            }
        }
    }

    /**
     * Vanilla's furnace flame, traced off its {@code lit_progress} sprite. That sprite is opaque and
     * carries vanilla's own panel grey behind the flames, so blitting it would stamp a grey box onto
     * our panel; drawing it a run at a time lets the panel show through. It also buys us an unlit
     * state, which vanilla bakes into its background texture instead of shipping as a sprite.
     * A dot is see-through, {@code o} the flames' shadow, the rest their fire colours.
     */
    private static final String[] FLAME = {
        ".r.........r..",
        ".#r...r...r#o.",
        "..#...#...#.o.",
        ".ryo..yr..yr..",
        ".#yo...#..y#..",
        ".yWo..r#o.Wyo.",
        "ry#o..y#o.#yr.",
        "#Wro.rWyo.ry#o",
        "yWo..#yro..Wyo",
        "WW#..#Woo.#Wyo",
        "rWyo.yWo..yWro",
        ".WWo.yWy..WWo.",
        "#W#o.#WWo.#W#.",
        ".ooo..ooo..ooo",
    };
    /**
     * Unlit, the flames drop to one flat grey and shed their shadow. Vanilla's own unlit flame is
     * 139 grey on a 198 background, so it barely lifts off the panel; matching that ratio against
     * ours lands on the slot grey, and keeping the shadow would only thicken the silhouette.
     */
    private static final int FLAME_OFF = 0xFF8B959F;
    private static final int FLAME_SHADOW = 0xFF56616D;
    /** Draws the flame a horizontal run at a time; unlit, every run takes the same flat grey. */
    static void drawFurnaceFlame(GuiGraphicsExtractor graphics, int x, int y, boolean lit) {
        for (int row = 0; row < FLAME.length; row++) {
            String line = FLAME[row];
            int runStart = 0;
            int runColour = 0;
            for (int column = 0; column <= line.length(); column++) {
                int colour = column < line.length() ? flameColour(line.charAt(column), lit) : 0;
                if (colour == runColour) continue;
                if (runColour != 0) graphics.fill(x + runStart, y + row, x + column, y + row + 1, runColour);
                runStart = column;
                runColour = colour;
            }
        }
    }

    /** Zero means the panel shows through. */
    private static int flameColour(char pixel, boolean lit) {
        if (pixel == '.') return 0;
        if (!lit) return pixel == 'o' ? 0 : FLAME_OFF;
        return switch (pixel) {
            case 'r' -> 0xFFD84C45;
            case '#' -> 0xFFFFB600;
            case 'y' -> 0xFFFFFF1F;
            case 'W' -> 0xFFFFFFFF;
            // The sprite's own shadow, restated in our palette rather than vanilla's grey.
            default -> FLAME_SHADOW;
        };
    }

    /**
     * Draws the arrow a column at a time, so the fill follows the head's taper instead of stopping
     * at a straight edge. The last column is scaled to the leftover fraction, keeping the animation
     * off whole-pixel steps the way the gradient bars do.
     */
    /** Draws the panel's own slots; upgrade slots are drawn by their tab instead. */
    static void drawSlots(GuiGraphicsExtractor graphics, int x, int y, Iterable<Slot> slots) {
        for (Slot slot : slots) {
            if (slot instanceof UpgradeSlot || !slot.isActive()) continue;
            drawSlot(graphics, x + slot.x, y + slot.y);
        }
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, int sx, int sy) {
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF56616D);
        graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B959F);
        // One-pixel shading inside each slot leaves its border and size unchanged.
        graphics.fill(sx, sy, sx + 16, sy + 1, 0x14000000);
        graphics.fill(sx, sy + 1, sx + 1, sy + 15, 0x0A000000);
        graphics.fill(sx, sy + 15, sx + 16, sy + 16, 0x16FFFFFF);
    }

    public static void drawCutCornerRect(GuiGraphicsExtractor graphics, int x, int y,
                                  int width, int height, int cornerSize, int color) {
        graphics.fill(x, y + cornerSize, x + width, y + height - cornerSize, color);
        for (int row = 0; row < cornerSize; row++) {
            int inset = cornerSize - row;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    static void drawGradientBar(GuiGraphicsExtractor graphics, int x, int y, float width,
                                int height, int startColor, int endColor) {
        if (width <= 0) return;
        // Local Y becomes screen X. Fractional scaling avoids whole GUI-pixel jumps.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().mul(new Matrix3x2f(0, -1, 1, 0, 0, 0));
        graphics.pose().scale(1.0F, width);
        graphics.fillGradient(-height, 0, 0, 1, startColor, endColor);
        graphics.pose().popMatrix();
        // A faint vertical sheen adds depth without changing the approved gradient.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(width, 1.0F);
        graphics.fillGradient(0, 0, 1, Math.max(1, height / 2), 0x18FFFFFF, 0x00FFFFFF);
        graphics.pose().popMatrix();
    }

    /**
     * The same bar stood on end: it grows upward from {@code bottom}, so {@code startColor} sits at
     * the foot of the column and {@code endColor} at its tip.
     */
    static void drawVerticalGradientBar(GuiGraphicsExtractor graphics, int x, int bottom, int width,
                                        float height, int startColor, int endColor) {
        if (height <= 0) return;
        // Fractional scaling of a one-unit rect avoids whole GUI-pixel jumps as the bar fills.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, bottom);
        graphics.pose().scale(1.0F, height);
        graphics.fillGradient(0, -1, width, 0, endColor, startColor);
        graphics.pose().popMatrix();
        // Sheen down the leading edge, mirroring the one the horizontal bar carries along its top.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, bottom);
        graphics.pose().mul(new Matrix3x2f(0, -1, 1, 0, 0, 0));
        graphics.pose().scale(height, 1.0F);
        graphics.fillGradient(0, 0, 1, Math.max(1, width / 2), 0x18FFFFFF, 0x00FFFFFF);
        graphics.pose().popMatrix();
    }

    /**
     * Shows the stored-against-capacity readout while the pointer is inside an energy bar. The box
     * is the bar's outer one, so a half-empty or flat bar still answers.
     *
     * @param x , y, width, height the bar's outer box in screen coordinates
     */
    static void energyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              int x, int y, int width, int height, int stored, int capacity) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return;
        graphics.setTooltipForNextFrame(
                Component.translatable("gui.futuretech.stored", stored, capacity), mouseX, mouseY);
    }

    static final class AnimatedBar {
        private double displayedWidth = Double.NaN;
        private long lastFrameNanos;

        /**
         * @param ready false while the menu has not received the server's values yet; the bar then shows
         *              the raw target and starts animating only from the first real value
         */
        float width(int amount, int capacity, int maxWidth, boolean ready) {
            double target = Math.clamp(amount * (double) maxWidth / Math.max(1, capacity), 0.0, (double) maxWidth);
            if (!ready) return (float) target;
            long now = System.nanoTime();
            if (Double.isNaN(displayedWidth)) {
                displayedWidth = target;
            } else {
                // A 120 ms response time keeps animation consistent at different frame rates.
                double elapsedSeconds = Math.max(0.0, (now - lastFrameNanos) / 1_000_000_000.0);
                double blend = -Math.expm1(-elapsedSeconds / 0.12);
                displayedWidth += (target - displayedWidth) * blend;
                if (Math.abs(target - displayedWidth) < 0.001) displayedWidth = target;
            }
            lastFrameNanos = now;
            return (float) displayedWidth;
        }
    }

    private MachineScreenStyle() {}
}
