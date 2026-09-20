package dev.futuretech.client;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.block.entity.BoilerBlockEntity;
import dev.futuretech.menu.BoilerMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The boiler's readout on the left edge, laid out like {@link EnergyInfoTab}: steam made now and
 * at most, what a mB of water costs in heat, and then whatever is heating the water this moment —
 * the heat left in the lit fuel, the lava burnt per second and held, or the FE drawn and held.
 * The rows follow the installed upgrade, so the panel changes as one goes in or out.
 */
public final class BoilerInfoTab extends MachineTab {
    private static final int VALUE_DROP = 10;
    private static final int INDENT = 6;
    private static final int PAIR_GAP = 3;
    private static final int LABEL_COLOR = 0xFF56616D;

    /** {@code widest} is the longest the value can get, so the panel does not resize as the numbers move. */
    private record Row(Component label, Component widest, Supplier<Component> value) {}

    private final BoilerMenu menu;

    public BoilerInfoTab(BoilerMenu menu, Font font) {
        super(font, Side.LEFT);
        this.menu = menu;
    }

    private List<Row> rows() {
        // The widest a value gets: an MK4 with every slot holding a speed upgrade.
        int maxWater = MachineLevel.consumption(BoilerBlockEntity.WATER_PER_TICK, MachineLevel.MAX) * (1 + UpgradeInventory.SLOTS);
        List<Row> rows = new ArrayList<>(5);
        rows.add(new Row(label("steam"), steam(maxWater), () -> steam(menu.productionRate() / BoilerBlockEntity.STEAM_PER_WATER)));
        rows.add(new Row(label("maximum_steam"), steam(maxWater),
                () -> steam(menu.maxWaterPerTick())));
        rows.add(new Row(label("heat_percent"), value("percent", 140), () -> value("percent", menu.heatPercent())));
        switch (menu.fuelMode()) {
            case BoilerBlockEntity.LAVA -> {
                // Heat is bought a mB at a time, so the draw reads best per second: 6 heat/t at 100% is 24 mB/s.
                rows.add(new Row(label("lava_usage"), value("lava_rate", lavaPerSecond(maxWater, 140)),
                        () -> value("lava_rate", lavaPerSecond(menu.productionRate() / BoilerBlockEntity.STEAM_PER_WATER, menu.heatPercent()))));
                rows.add(new Row(label("lava_stored"), tank(BoilerBlockEntity.LAVA_CAPACITY, BoilerBlockEntity.LAVA_CAPACITY),
                        () -> tank(menu.reserve(), BoilerBlockEntity.LAVA_CAPACITY)));
            }
            case BoilerBlockEntity.ENERGY -> {
                rows.add(new Row(label("usage"), rate(energyPerTick(maxWater, 140)),
                        () -> rate(energyPerTick(menu.productionRate() / BoilerBlockEntity.STEAM_PER_WATER, menu.heatPercent()))));
                rows.add(new Row(label("stored"), stored(BoilerBlockEntity.ENERGY_CAPACITY, BoilerBlockEntity.ENERGY_CAPACITY),
                        () -> stored(menu.reserve(), BoilerBlockEntity.ENERGY_CAPACITY)));
            }
            default -> rows.add(new Row(label("heat_left"), heat(32767, 32767), () -> heat(menu.burnRemaining(), menu.burnTotal())));
        }
        return rows;
    }

    /** mB of lava a second for {@code water} mB of water a tick at {@code percent} heat per mB. */
    private static int lavaPerSecond(int water, int percent) {
        return water * percent * 20 / (100 * BoilerBlockEntity.HEAT_PER_LAVA_MB);
    }

    private static int energyPerTick(int water, int percent) {
        return water * percent * BoilerBlockEntity.FE_PER_HEAT / 100;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.info"); }

    @Override
    protected int contentWidth() {
        int widest = 0;
        for (Row row : rows()) {
            widest = Math.max(widest, Math.max(font.width(row.label()), INDENT + font.width(row.widest())));
        }
        return widest;
    }

    @Override
    protected int contentHeight() { return pairY(rows().size() - 1) + VALUE_DROP + font.lineHeight; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) { EnergyInfoTab.drawBolt(graphics, x, y); }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        List<Row> rows = rows();
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = contentY + pairY(index);
            graphics.text(font, row.label(), contentX, y, LABEL_COLOR, false);
            graphics.text(font, row.value().get(), contentX + INDENT, y + VALUE_DROP, TEXT_COLOR, false);
        }
    }

    private int pairY(int index) { return index * (VALUE_DROP + font.lineHeight + PAIR_GAP); }

    private static Component label(String key) { return Component.translatable("gui.futuretech.info." + key); }

    private static Component value(String key, Object... arguments) { return Component.translatable("gui.futuretech.info." + key, arguments); }

    private static Component steam(int water) { return value("steam_rate", water * BoilerBlockEntity.STEAM_PER_WATER); }

    private static Component rate(int perTick) { return Component.translatable("gui.futuretech.rate", perTick); }

    private static Component heat(int left, int total) { return value("heat", left, total); }

    private static Component tank(int amount, int capacity) { return Component.translatable("gui.futuretech.tank.amount", amount, capacity); }

    private static Component stored(int amount, int capacity) { return Component.translatable("gui.futuretech.stored", amount, capacity); }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) { return false; }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}
}
