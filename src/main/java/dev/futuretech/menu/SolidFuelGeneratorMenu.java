package dev.futuretech.menu;

import static dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity.*;

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
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
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

public final class SolidFuelGeneratorMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    private final Container fuel;
    private final ContainerData data;

    /** Width of the generator screen; upgrade slots sit in the tab beside it. */
    public static final int IMAGE_WIDTH = 176;

    public SolidFuelGeneratorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Client side: the upgrade slots lock by the synced MK, so the tab draws them like the server has them. */
    private SolidFuelGeneratorMenu(int id, Inventory inventory, ContainerData data) {
        this(id, inventory, new SimpleContainer(1), new UpgradeInventory(() -> Math.clamp(data.get(DATA_MK), 1, 4), () -> {}), data);
    }

    public SolidFuelGeneratorMenu(int id, Inventory inventory, Container fuel, UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.SOLID_FUEL_GENERATOR.get(), id);
        checkContainerSize(fuel, 1);
        checkContainerDataCount(data, DATA_COUNT);
        this.fuel = fuel;
        this.data = data;
        addSlot(new Slot(fuel, 0, 8, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) { return SolidFuelGeneratorBlockEntity.isFuel(stack); }
        });
        addStandardInventorySlots(inventory, 8, 102);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (fuel instanceof SolidFuelGeneratorBlockEntity) markSynced();
    }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    public int mk() { return Math.clamp(data.get(DATA_MK), 1, 4); }

    @Override
    public int energyRatePerTick() { return GENERATION_PER_TICK; }

    @Override
    public int energyOutputPerTick() { return OUTPUT_PER_TICK; }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.GENERATOR; }

    @Override
    public boolean isWorking() { return isGenerating(); }

    public int burnRemaining() { return data.get(DATA_BURN_REMAINING); }

    public int burnTotal() { return Math.max(1, data.get(DATA_BURN_TOTAL)); }

    public boolean isGenerating() { return data.get(DATA_GENERATING) != 0; }

    public boolean isFull() { return energyCapacity() - energyStored() < GENERATION_PER_TICK; }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.SOLID_FUEL_GENERATOR.get().displayState(front()).setValue(MachineLevel.MK, Math.clamp(data.get(DATA_MK), 1, 4)); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.SOLID_FUEL_GENERATOR.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.SOLID_FUEL_GENERATOR.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.SOLID_FUEL_GENERATOR.get().supportsAutoPush(); }

    @Override
    public boolean isAutoPulling() { return data.get(DATA_AUTO_BASE) != 0; }

    @Override
    public boolean isAutoPushing() { return data.get(DATA_AUTO_BASE + 1) != 0; }

    @Override
    public SlotRole slotRole(int index) { return index == 0 ? SlotRole.INPUT : SlotRole.NONE; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(fuel instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(fuel instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return fuel.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0 || index >= 37) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, 37, 37 + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (SolidFuelGeneratorBlockEntity.isFuel(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 1, 28, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
