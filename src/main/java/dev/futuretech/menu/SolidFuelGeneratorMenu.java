package dev.futuretech.menu;

import static dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity.*;

import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SolidFuelGeneratorMenu extends AbstractContainerMenu {
    private final Container fuel;
    private final ContainerData data;

    public SolidFuelGeneratorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT));
    }

    public SolidFuelGeneratorMenu(int id, Inventory inventory, Container fuel, ContainerData data) {
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
        addDataSlots(data);
    }

    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }
    public int burnRemaining() { return data.get(DATA_BURN_REMAINING); }
    public int burnTotal() { return Math.max(1, data.get(DATA_BURN_TOTAL)); }
    public boolean isGenerating() { return data.get(DATA_GENERATING) != 0; }
    public boolean isFull() { return CAPACITY - energyStored() < GENERATION_PER_TICK; }

    @Override
    public boolean stillValid(Player player) { return fuel.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
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
