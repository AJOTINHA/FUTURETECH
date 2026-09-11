package dev.futuretech.client;

import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Matrix3x2f;

public final class SolidFuelGeneratorScreen extends AbstractContainerScreen<SolidFuelGeneratorMenu> {
    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar fuelBar = new AnimatedBar();

    public SolidFuelGeneratorScreen(SolidFuelGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 184);
        titleLabelX = menu.slots.getFirst().x - 1;
        inventoryLabelX = titleLabelX;
        inventoryLabelY = 90;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        // Small pixel steps reproduce vanilla-style corners without rounded panels.
        drawCutCornerRect(graphics, x - 3, y + 1, imageWidth + 6, imageHeight + 4, 3, 0x08000000);
        drawCutCornerRect(graphics, x - 2, y + 1, imageWidth + 4, imageHeight + 3, 3, 0x0C000000);
        drawCutCornerRect(graphics, x - 1, y + 1, imageWidth + 2, imageHeight + 2, 3, 0x14000000);
        drawCutCornerRect(graphics, x, y, imageWidth, imageHeight, 3, 0xFF111820);
        drawCutCornerRect(graphics, x + 2, y + 2, imageWidth - 4, imageHeight - 4, 1, 0xFFBBC3CC);
        graphics.fill(x + 3, y + 2, x + imageWidth - 3, y + 3, 0xFF293747);
        graphics.fill(x + 2, y + 3, x + imageWidth - 2, y + 18, 0xFF293747);
        graphics.fill(x + 3, y + 2, x + imageWidth - 3, y + 3, 0x12FFFFFF);
        graphics.fillGradient(x + 2, y + 18, x + imageWidth - 2, y + 20, 0x18000000, 0x00000000);
        for (var slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF56616D);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B959F);
            // One-pixel shading inside each slot leaves its border and size unchanged.
            graphics.fill(sx, sy, sx + 16, sy + 1, 0x14000000);
            graphics.fill(sx, sy + 1, sx + 1, sy + 15, 0x0A000000);
            graphics.fill(sx, sy + 15, sx + 16, sy + 16, 0x16FFFFFF);
        }
        // The energy box is centred on the fuel slot's vertical span (44..62) without changing its size.
        graphics.fill(x + 69, y + 46, x + 163, y + 60, 0xFF283541);
        float energyWidth = energyBar.width(menu.energyStored(), SolidFuelGeneratorBlockEntity.CAPACITY, 92);
        drawGradientBar(graphics, x + 70, y + 47, energyWidth, 12, 0xFF1676C4, 0xFF55E7ED);
        graphics.fill(x + 7, y + 65, x + 25, y + 69, 0xFF56616D);
        float burnWidth = fuelBar.width(menu.burnRemaining(), menu.burnTotal(), 18);
        drawGradientBar(graphics, x + 7, y + 65, burnWidth, 4, 0xFFEC761C, 0xFFFFD76A);
    }

    private static void drawCutCornerRect(GuiGraphicsExtractor graphics, int x, int y,
                                          int width, int height, int cornerSize, int color) {
        graphics.fill(x, y + cornerSize, x + width, y + height - cornerSize, color);
        for (int row = 0; row < cornerSize; row++) {
            int inset = cornerSize - row;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    private static void drawGradientBar(GuiGraphicsExtractor graphics, int x, int y, float width,
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

    private static final class AnimatedBar {
        private double displayedWidth = Double.NaN;
        private long lastFrameNanos;

        float width(int amount, int capacity, int maxWidth) {
            double target = Math.clamp(amount * (double) maxWidth / Math.max(1, capacity), 0.0, (double) maxWidth);
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFFFFFFF, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF283541, false);
        var fuelStack = menu.slots.getFirst().getItem();
        Component fuelName = fuelStack.isEmpty()
                ? Component.translatable("gui.futuretech.empty") : fuelStack.getHoverName();
        // Keep translated names inside the fuel column, above the slot.
        var nameLines = font.split(fuelName, 58);
        for (int line = 0; line < Math.min(2, nameLines.size()); line++) {
            graphics.text(font, nameLines.get(line), inventoryLabelX, 26 + line * font.lineHeight, 0xFF283541, false);
        }
        graphics.text(font, Component.translatable("gui.futuretech.energy"), 70, 26, 0xFF283541, false);
        Component generationRate = Component.translatable("gui.futuretech.rate",
                menu.isGenerating() ? SolidFuelGeneratorBlockEntity.GENERATION_PER_TICK : 0);
        graphics.text(font, generationRate, 163 - font.width(generationRate), 26, 0xFF283541, false);
        graphics.text(font, Component.translatable("gui.futuretech.stored", menu.energyStored(),
                SolidFuelGeneratorBlockEntity.CAPACITY), 70, 63, 0xFF283541, false);
    }
}
