package dev.futuretech.menu;

import static dev.futuretech.block.entity.LavaGeneratorBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SlotRole;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.entity.LavaGeneratorBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class LavaGeneratorMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    private final Container buckets;
    private final UpgradeInventory upgrades;
    private final ContainerData data;

    /** Width of the generator screen; upgrade slots sit in the tab beside it. */
    public static final int IMAGE_WIDTH = 176;
    public static final int SLOT_X = 8;
    public static final int INPUT_Y = 26;
    public static final int OUTPUT_Y = 62;
    private static final int PLAYER_START = INVENTORY_SIZE;
    private static final int HOTBAR_START = PLAYER_START + 27;
    private static final int PLAYER_END = HOTBAR_START + 9;

    public LavaGeneratorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Client side: the upgrade slots lock by the synced MK, so the tab draws them like the server has them. */
    private LavaGeneratorMenu(int id, Inventory inventory, ContainerData data) {
        this(id, inventory, new SimpleContainer(INVENTORY_SIZE),
                new UpgradeInventory(() -> Math.clamp(data.get(DATA_MK), 1, 4), () -> {}), data);
    }

    public LavaGeneratorMenu(int id, Inventory inventory, Container buckets, UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.LAVA_GENERATOR.get(), id);
        checkContainerSize(buckets, INVENTORY_SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.buckets = buckets;
        this.upgrades = upgrades;
        this.data = data;
        addSlot(new Slot(buckets, SLOT_INPUT, SLOT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return LavaGeneratorBlockEntity.isLavaContainer(stack); }
        });
        addSlot(new Slot(buckets, SLOT_OUTPUT, SLOT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, 102);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (buckets instanceof LavaGeneratorBlockEntity) markSynced();
    }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    public int mk() { return Math.clamp(data.get(DATA_MK), 1, 4); }

    @Override
    public int energyRatePerTick() { return upgrades.generation(GENERATION_PER_TICK, mk()); }

    @Override
    public int energyOutputPerTick() { return upgrades.generation(OUTPUT_PER_TICK, mk()); }

    /** FE one millibucket of lava is worth: what the efficiency and speed upgrades move. */
    public int fePerMb() { return upgrades.yield(GENERATION_PER_TICK / LAVA_PER_TICK); }

    /** Millibuckets a second at full rate, the unit the boiler's readout already uses. */
    public int lavaPerSecond() { return Math.round(energyRatePerTick() * 20.0F / fePerMb()); }

    /** What the info tab says about the lava: how fast it goes, and what a millibucket is worth. */
    @Override
    public java.util.List<InfoRow> extraInfo() {
        return java.util.List.of(
                new InfoRow(label("lava_usage"), value("lava_rate", 9_999), () -> value("lava_rate", lavaPerSecond())),
                new InfoRow(label("fe_per_mb"), value("fe_per_mb_value", 999),
                        () -> value("fe_per_mb_value", fePerMb())));
    }

    private static Component label(String key) { return Component.translatable("gui.futuretech.info." + key); }

    private static Component value(String key, int amount) {
        return Component.translatable("gui.futuretech.info." + key, amount);
    }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.GENERATOR; }

    @Override
    public boolean isWorking() { return isGenerating(); }

    public int lavaStored() { return EnergySync.unpack(data.get(DATA_LAVA_LOW), data.get(DATA_LAVA_HIGH)); }

    public boolean isGenerating() { return data.get(DATA_GENERATING) != 0; }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.LAVA_GENERATOR.get().displayState(front()).setValue(MachineLevel.MK, mk()); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.LAVA_GENERATOR.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.LAVA_GENERATOR.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.LAVA_GENERATOR.get().supportsAutoPush(); }

    @Override
    public boolean isAutoPulling() { return data.get(DATA_AUTO_BASE) != 0; }

    @Override
    public boolean isAutoPushing() { return data.get(DATA_AUTO_BASE + 1) != 0; }

    @Override
    public SlotRole slotRole(int index) { return index == 0 ? SlotRole.INPUT : index == 1 ? SlotRole.OUTPUT : SlotRole.NONE; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(buckets instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(buckets instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return buckets.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START || index >= PLAYER_END) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, PLAYER_END, PLAYER_END + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (LavaGeneratorBlockEntity.isLavaContainer(stack)) {
            if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
