package dev.futuretech.api.gui;

import dev.futuretech.client.MachineScreenStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Read-only energy readout for the left edge of a machine screen: what the machine is drawing or
 * making, the most it can move and what is left in its buffer. Nothing here is clickable.
 */
public final class EnergyInfoTab extends MachineTab {
    /** Drop from a label to its value. */
    private static final int VALUE_DROP = 10;
    /** How far a value is indented under its label. */
    private static final int INDENT = 6;
    /** Blank rows between one label/value pair and the next. */
    private static final int PAIR_GAP = 3;
    private static final int LABEL_COLOR = 0xFF56616D;

    /**
     * @param widest the longest this row's value can ever be, so the panel is sized once instead of
     *               resizing itself as the numbers move
     */
    private record Row(Component label, Component widest, Supplier<Component> value) {}

    private final List<Row> rows;

    public EnergyInfoTab(EnergyInfoMenu menu, Font font) {
        super(font, Side.LEFT);
        // The rate is read every frame: the upgrade slots that lower it fill in only after the
        // screen opens. What it is now, before they arrive, is the most it can ever be.
        boolean generator = menu.kind() == EnergyInfoMenu.Kind.GENERATOR;
        int rate = menu.energyRatePerTick();
        int output = menu.energyOutputPerTick();
        rows = new ArrayList<>(4);
        rows.add(new Row(label(generator ? "generation" : "usage"), rate(rate),
                // Idle machines move nothing, so this line drops to zero while the maximum stays put.
                () -> rate(menu.energyUsagePerTick())));
        rows.add(new Row(label(generator ? "maximum_generation" : "maximum"), rate(rate), () -> rate(menu.energyRatePerTick())));
        if (output > 0) rows.add(new Row(label("maximum_output"), rate(output), () -> rate(output)));
        rows.add(new Row(label("stored"), amount(menu.energyCapacity()), () -> amount(menu.energyStored())));
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.info"); }

    @Override
    protected int contentWidth() {
        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, Math.max(font.width(row.label()), INDENT + font.width(row.widest())));
        }
        return widest;
    }

    @Override
    protected int contentHeight() { return pairY(rows.size() - 1) + VALUE_DROP + font.lineHeight; }

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
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = contentY + pairY(index);
            graphics.text(font, row.label(), contentX, y, LABEL_COLOR, false);
            graphics.text(font, row.value().get(), contentX + INDENT, y + VALUE_DROP, TEXT_COLOR, false);
        }
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
