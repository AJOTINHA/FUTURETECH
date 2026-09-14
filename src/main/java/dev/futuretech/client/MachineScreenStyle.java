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
