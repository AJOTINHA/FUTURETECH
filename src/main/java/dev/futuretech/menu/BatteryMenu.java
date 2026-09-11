package dev.futuretech.menu;

import static dev.futuretech.block.entity.BatteryBlockEntity.*;

import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class BatteryMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable BatteryBlockEntity battery;

    public BatteryMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public BatteryMenu(int id, Inventory inventory, @Nullable BatteryBlockEntity battery, ContainerData data) {
        super(ModMenus.BATTERY.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.battery = battery;
        addStandardInventorySlots(inventory, 8, 102);
        addDataSlots(data);
    }

    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    public int inputRate() { return data.get(DATA_INPUT); }

    public int outputRate() { return data.get(DATA_OUTPUT); }

    @Override
    public boolean stillValid(Player player) {
        // The client-side menu has no block entity and is closed by the server when needed.
        return battery == null || Container.stillValidBlockEntity(battery, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        // No machine slots yet: shift-click only swaps between the main inventory and the hotbar.
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
