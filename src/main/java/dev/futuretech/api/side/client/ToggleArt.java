package dev.futuretech.api.side.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How the small square toggles of the machine screens are drawn: a 16 px bevelled button with a
 * coloured border while it is on, a grey one while it is off, and a pixel glyph inset by two on
 * each side. The glyphs are strings, one per row, with {@code #} for a lit pixel.
 */
public final class ToggleArt {
    public static final int SIZE = 16;
    public static final int OFF_COLOR = 0xFF56616D;
    private static final int OUTLINE = 0xFF26333F;

    private ToggleArt() {}

    /** The button at {@code x, y} with its border, face and glyph; {@code hovered} brightens the face. */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int color, boolean on, String[] glyph, boolean hovered) {
        int tint = on ? color : OFF_COLOR;
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, tint);
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF65717D);
        graphics.fill(x, y, x + SIZE, y + 1, 0xFFB5C0CA);
        graphics.fill(x, y + 1, x + 1, y + SIZE, 0xFF9AA7B3);
        graphics.fill(x + 1, y + SIZE - 1, x + SIZE, y + SIZE, 0xFF394651);
        graphics.fill(x + SIZE - 1, y + 1, x + SIZE, y + SIZE, 0xFF394651);
        drawGlyph(graphics, x, y, tint, glyph);
        if (hovered) graphics.fill(x, y, x + SIZE, y + SIZE, 0x40FFFFFF);
    }

    /** Inset pixel glyph with a dark outline and a light edge for contrast at GUI scale. */
    public static void drawGlyph(GuiGraphicsExtractor graphics, int x, int y, int color, String[] glyph) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int highlight = 0xFF000000 | ((red * 3 + 255) / 4 << 16)
                | ((green * 3 + 255) / 4 << 8) | (blue * 3 + 255) / 4;
        // Paint the outline first so adjacent rows cannot cover the coloured face.
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < glyph[row].length(); column++) {
                if (!pixel(glyph, column, row)) continue;
                int px = x + 2 + column;
                int py = y + 2 + row;
                graphics.fill(px - 1, py, px + 2, py + 1, OUTLINE);
                graphics.fill(px, py - 1, px + 1, py + 2, OUTLINE);
            }
        }
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < glyph[row].length(); column++) {
                if (!pixel(glyph, column, row)) continue;
                boolean lightEdge = !pixel(glyph, column - 1, row) || !pixel(glyph, column, row - 1);
                int px = x + 2 + column;
                int py = y + 2 + row;
                graphics.fill(px, py, px + 1, py + 1, lightEdge ? highlight : color);
            }
        }
    }

    /** The glyph upside down. */
    public static String[] flipped(String[] glyph) {
        String[] result = new String[glyph.length];
        for (int row = 0; row < glyph.length; row++) result[row] = glyph[glyph.length - 1 - row];
        return result;
    }

    public static boolean isOver(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    private static boolean pixel(String[] glyph, int column, int row) {
        if (row < 0 || row >= glyph.length || column < 0 || column >= glyph[0].length()) return false;
        return glyph[row].charAt(column) == '#';
    }
}
