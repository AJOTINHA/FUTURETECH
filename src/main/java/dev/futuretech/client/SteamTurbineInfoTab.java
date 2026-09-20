package dev.futuretech.client;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.block.entity.SteamTurbineBlockEntity;
import dev.futuretech.menu.SteamTurbineMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The turbine's readout on the left edge, laid out like {@link EnergyInfoTab}: the energy it makes
 * now and at most and what it holds, then the steam side of the same work - what it drinks now and
 * at most, what a mB is worth, and what is in the tank. The rates follow the level and the speed
 * upgrades, so the panel changes as one goes in or out.
 */
public final class SteamTurbineInfoTab extends MachineTab {
    private static final int VALUE_DROP = 10;
    private static final int INDENT = 6;
    private static final int PAIR_GAP = 3;
    private static final int LABEL_COLOR = 0xFF56616D;

    /** {@code widest} is the longest the value can get, so the panel does not resize as the numbers move. */
    private record Row(Component label, Component widest, Supplier<Component> value) {}

    private final List<Row> rows;

    public SteamTurbineInfoTab(SteamTurbineMenu menu, Font font) {
        super(font, Side.LEFT);
        // The widest a value gets: an MK4 with every slot holding a speed upgrade.
        int maxSteam = MachineLevel.consumption(SteamTurbineBlockEntity.STEAM_PER_TICK, MachineLevel.MAX) * (1 + UpgradeInventory.SLOTS);
        int maxRate = maxSteam * SteamTurbineBlockEntity.FE_PER_MB;
        rows = List.of(
                new Row(label("generation"), rate(maxRate), () -> rate(menu.energyUsagePerTick())),
                new Row(label("maximum_generation"), rate(maxRate), () -> rate(menu.energyRatePerTick())),
                new Row(label("stored"), amount(menu.energyCapacity()), () -> amount(menu.energyStored())),
                new Row(label("steam_usage"), steam(maxSteam), () -> steam(menu.steamUsagePerTick())),
                new Row(label("maximum_steam_usage"), steam(maxSteam), () -> steam(menu.steamPerTick())),
                new Row(label("fe_per_mb"), perMb(SteamTurbineBlockEntity.FE_PER_MB), () -> perMb(menu.fePerMb())),
                new Row(label("steam_stored"), tank(SteamTurbineBlockEntity.STEAM_CAPACITY, SteamTurbineBlockEntity.STEAM_CAPACITY),
                        () -> tank(menu.steamStored(), SteamTurbineBlockEntity.STEAM_CAPACITY)));
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
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) { EnergyInfoTab.drawBolt(graphics, x, y); }

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

    private static Component steam(int perTick) { return Component.translatable("gui.futuretech.info.steam_rate", perTick); }

    private static Component perMb(int fe) { return Component.translatable("gui.futuretech.info.fe_per_mb_value", fe); }

    private static Component tank(int amount, int capacity) { return Component.translatable("gui.futuretech.tank.amount", amount, capacity); }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) { return false; }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {}
}
