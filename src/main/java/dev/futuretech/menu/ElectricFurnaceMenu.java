package dev.futuretech.menu;

import static dev.futuretech.block.entity.ElectricFurnaceBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
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

public final class ElectricFurnaceMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    private final Container contents;
    private final ContainerData data;

    /** Width of the furnace screen; upgrade slots sit in the tab beside it. */
    public static final int IMAGE_WIDTH = 176;
    /** Index of the first player-inventory slot, after the two machine slots. */
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = INVENTORY_START + 36;
    private static final int HOTBAR_START = INVENTORY_END - 9;

    public ElectricFurnaceMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(2), new UpgradeInventory(() -> {}), new SimpleContainerData(DATA_COUNT));
    }

    public ElectricFurnaceMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.ELECTRIC_FURNACE.get(), id);
        checkContainerSize(contents, 2);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.data = data;
        addSlot(new Slot(contents, SLOT_INPUT, 8, 45));
        addSlot(new Slot(contents, SLOT_OUTPUT, 35, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, 102);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof ElectricFurnaceBlockEntity) markSynced();
    }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return CAPACITY; }

    @Override
    public int energyUsagePerTick() { return ENERGY_PER_TICK; }

    public int progress() { return data.get(DATA_PROGRESS); }

    public int progressTotal() { return Math.max(1, data.get(DATA_PROGRESS_TOTAL)); }

    @Override
    public boolean isWorking() { return data.get(DATA_WORKING) != 0; }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.ELECTRIC_FURNACE.get().displayState(front()); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.ELECTRIC_FURNACE.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.ELECTRIC_FURNACE.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.ELECTRIC_FURNACE.get().supportsAutoPush(); }

    @Override
    public boolean isAutoPulling() { return data.get(DATA_AUTO_BASE) != 0; }

    @Override
    public boolean isAutoPushing() { return data.get(DATA_AUTO_BASE + 1) != 0; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(contents instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(contents instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return contents.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START || index >= INVENTORY_END) {
            // Machine and upgrade slots empty into the player's inventory.
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, INVENTORY_END, INVENTORY_END + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)) {
            // Not smeltable-checked here: the recipe lookup needs a server level, as in a vanilla furnace.
            if (index < HOTBAR_START) {
                if (!moveItemStackTo(stack, HOTBAR_START, INVENTORY_END, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
