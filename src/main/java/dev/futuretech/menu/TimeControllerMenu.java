package dev.futuretech.menu;

import static dev.futuretech.block.entity.TimeControllerBlockEntity.*;

import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.entity.TimeControllerBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The time controller's menu: the moment the block is set to, its redstone mode and the energy
 * it pays with, all as data slots, and the player's inventory under them. Picking a moment is a
 * menu button, one per {@link DayMoment}, past the redstone mode's buttons; the block has no
 * slots of its own.
 */
public final class TimeControllerMenu extends MachineMenu implements RedstoneControlMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int INVENTORY_Y = 112;
    /** {@code SELECT_BASE + ordinal} sets that moment; past the redstone buttons. */
    public static final int SELECT_BASE = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;

    private final ContainerData data;
    private final @Nullable TimeControllerBlockEntity controller;

    public TimeControllerMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainerData(DATA_COUNT));
    }

    public TimeControllerMenu(int id, Inventory inventory, @Nullable TimeControllerBlockEntity controller, ContainerData data) {
        super(ModMenus.TIME_CONTROLLER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.controller = controller;
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
        if (controller != null) markSynced();
    }

    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    public int energyCapacity() { return CAPACITY; }

    public DayMoment moment() { return DayMoment.byOrdinal(data.get(DATA_MOMENT)); }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (RedstoneControlMenu.handleButton(controller, buttonId)) return true;
        int pick = buttonId - SELECT_BASE;
        if (controller == null || pick < 0 || pick >= DayMoment.values().length) return false;
        controller.setMoment(DayMoment.values()[pick]);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        // The client-side menu has no block entity and is closed by the server when needed.
        return controller == null || Container.stillValidBlockEntity(controller, player);
    }

    /** Only the player's own slots are here, so shift-clicking moves between the inventory and the hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
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
