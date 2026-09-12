package dev.futuretech.api.gui;

import dev.futuretech.client.MachineScreenStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Read-only energy readout for the left edge of a machine screen: what the machine is drawing, the
 * most it can draw and what is left in its buffer. Nothing here is clickable.
 */
public final class EnergyInfoTab extends MachineTab {
    /** Drop from a label to its value. */
    private static final int VALUE_DROP = 10;
    /** How far a value is indented under its label. */
    private static final int INDENT = 6;
    /** Blank rows between one label/value pair and the next. */
    private static final int PAIR_GAP = 3;
    private static final int LABEL_COLOR = 0xFF56616D;

    private final EnergyInfoMenu menu;

    public EnergyInfoTab(EnergyInfoMenu menu, Font font) {
        super(font, Side.LEFT);
        this.menu = menu;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.info"); }

    /**
     * Sized for the widest text the tab can ever show — the maximum draw and a full buffer — so the
     * panel does not resize itself as the numbers move.
     */
    @Override
    protected int contentWidth() {
        int widest = 0;
        for (String key : new String[] {"usage", "maximum", "stored"}) {
            widest = Math.max(widest, font.width(label(key)));
        }
        widest = Math.max(widest, INDENT + font.width(rate(menu.energyUsagePerTick())));
        return Math.max(widest, INDENT + font.width(amount(menu.energyCapacity())));
    }

    @Override
    protected int contentHeight() { return pairY(2) + VALUE_DROP + font.lineHeight; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // A lightning bolt, one row per scanline: the upper wedge descends to the left, steps
        // sideways at the notch, and the tail picks up to the right of it. Traced from the
        // U+26A1 glyph, because hand-drawn strips of constant width just read as a slash.
        graphics.fill(x + 14, y + 3, x + 16, y + 4, TITLE_COLOR);
        graphics.fill(x + 13, y + 4, x + 15, y + 5, TITLE_COLOR);
        graphics.fill(x + 11, y + 5, x + 13, y + 6, TITLE_COLOR);
        graphics.fill(x + 10, y + 6, x + 12, y + 7, TITLE_COLOR);
        graphics.fill(x + 8, y + 7, x + 11, y + 8, TITLE_COLOR);
        graphics.fill(x + 7, y + 8, x + 10, y + 9, TITLE_COLOR);
        graphics.fill(x + 5, y + 9, x + 13, y + 10, TITLE_COLOR);
        graphics.fill(x + 8, y + 10, x + 15, y + 11, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 11, y + 11, x + 14, y + 12, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 9, y + 12, x + 12, y + 13, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 8, y + 13, x + 11, y + 14, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 7, y + 14, x + 9, y + 15, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 5, y + 15, x + 7, y + 16, MachineScreenStyle.ENERGY_END);
        graphics.fill(x + 4, y + 16, x + 6, y + 17, MachineScreenStyle.ENERGY_END);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        // Idle machines draw nothing, so the usage line drops to zero while the maximum stays put.
        drawPair(graphics, contentX, contentY + pairY(0), "usage",
                rate(menu.isWorking() ? menu.energyUsagePerTick() : 0));
        drawPair(graphics, contentX, contentY + pairY(1), "maximum", rate(menu.energyUsagePerTick()));
        drawPair(graphics, contentX, contentY + pairY(2), "stored", amount(menu.energyStored()));
    }

    private void drawPair(GuiGraphicsExtractor graphics, int x, int y, String key, Component value) {
        graphics.text(font, label(key), x, y, LABEL_COLOR, false);
        graphics.text(font, value, x + INDENT, y + VALUE_DROP, TEXT_COLOR, false);
    }

    private int pairY(int index) { return index * (VALUE_DROP + font.lineHeight + PAIR_GAP); }

    private static Component label(String key) { return Component.translatable("gui.futuretech.info." + key); }

    private static Component rate(int perTick) { return Component.translatable("gui.futuretech.rate", perTick); }

    private static Component amount(int energy) { return Component.translatable("gui.futuretech.info.amount", energy); }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) { return false; }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}
}
