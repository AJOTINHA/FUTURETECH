package dev.futuretech.menu;

import static dev.futuretech.block.entity.ControllerBlockEntity.*;

import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.block.ControllerKind;
import dev.futuretech.block.entity.ControllerBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A controller's menu: the choice the block is set to, its redstone mode and the energy it pays
 * with, all as data slots, and the player's inventory under them. Which controller it is rides
 * in the opening packet, so the screen knows its choices before any sync. Picking one is a menu
 * button, past the redstone mode's buttons; the block has no slots of its own.
 */
public final class ControllerMenu extends MachineMenu implements RedstoneControlMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int INVENTORY_Y = 112;
    /** {@code SELECT_BASE + choice} sets that choice; past the redstone buttons. */
    public static final int SELECT_BASE = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;

    private final ControllerKind kind;
    private final ContainerData data;
    private final @Nullable ControllerBlockEntity controller;

    public ControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readEnum(ControllerKind.class), null, new SimpleContainerData(DATA_COUNT));
    }

    public ControllerMenu(int id, Inventory inventory, ControllerBlockEntity controller, ContainerData data) {
        this(id, inventory, controller.kind(), controller, data);
    }

    private ControllerMenu(int id, Inventory inventory, ControllerKind kind, @Nullable ControllerBlockEntity controller,
                           ContainerData data) {
        super(ModMenus.CONTROLLER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.kind = kind;
        this.data = data;
        this.controller = controller;
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
        if (controller != null) markSynced();
    }

    public ControllerKind kind() { return kind; }

    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    public int energyCapacity() { return kind.capacity(); }

    public int choice() { return kind.clampChoice(data.get(DATA_CHOICE)); }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (RedstoneControlMenu.handleButton(controller, buttonId)) return true;
        int pick = buttonId - SELECT_BASE;
        if (controller == null || pick < 0 || pick >= kind.choices()) return false;
        controller.setChoice(pick);
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
