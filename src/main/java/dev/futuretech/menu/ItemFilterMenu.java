package dev.futuretech.menu;

import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.item.ItemFilterMode;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Editor for the filter card in one connector's slot. The nine entries are ghost slots: clicking
 * one with an item in hand lists that item without spending it, clicking with an empty hand
 * clears it, and shift-clicking an item in the inventory lists it in the first free entry. Every
 * change is written straight back to the card, so taking the card out afterwards keeps the list.
 */
public final class ItemFilterMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 1;
    public static final int TOGGLE_MODE = 0;
    /** Top-left of the three-by-three grid of entries, laid out like a dispenser's but clear of the header. */
    public static final int GRID_X = 62;
    public static final int GRID_Y = 26;
    public static final int INVENTORY_TOP = 94;
    private static final int MODE = 0;
    private static final int ENTRIES = ItemFilterItem.SLOTS;
    private static final int INVENTORY_START = ENTRIES;
    private static final int HOTBAR_START = INVENTORY_START + 27;
    private static final int INVENTORY_END = HOTBAR_START + 9;
    private static final double REACH_SQUARED = 64.0;

    /** An entry of the list: shown like an item, but never picked up or filled by vanilla's own logic. */
    private static final class GhostSlot extends Slot {
        GhostSlot(SimpleContainer entries, int index, int x, int y) {
            super(entries, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        @Override
        public boolean mayPickup(Player player) { return false; }

        @Override
        public boolean isFake() { return true; }
    }

    private final ContainerData data;
    private final SimpleContainer entries;
    private final @Nullable AbstractCableBlockEntity cable;
    private final Direction side;

    /** Client side: the opening packet says which connector's card this is. */
    public ItemFilterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, null, extra.readEnum(Direction.class), new SimpleContainer(ENTRIES),
                new SimpleContainerData(DATA_COUNT));
    }

    private ItemFilterMenu(int id, Inventory inventory, @Nullable AbstractCableBlockEntity cable, Direction side,
                           SimpleContainer entries, ContainerData data) {
        super(ModMenus.ITEM_FILTER.get(), id);
        checkContainerSize(entries, ENTRIES);
        checkContainerDataCount(data, DATA_COUNT);
        this.cable = cable;
        this.side = side;
        this.entries = entries;
        this.data = data;
        for (int index = 0; index < ENTRIES; index++) {
            addSlot(new GhostSlot(entries, index, GRID_X + index % 3 * 18, GRID_Y + index / 3 * 18));
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_TOP);
        addDataSlots(data);
    }

    /** Server side: the entries start as the card's list and every edit goes back onto the card. */
    public static ItemFilterMenu opening(int id, Inventory inventory, AbstractCableBlockEntity cable, Direction side) {
        var entries = new SimpleContainer(ENTRIES);
        NonNullList<ItemStack> listed = ItemFilterItem.entries(cable.connectorFilters().getItem(side.ordinal()));
        for (int index = 0; index < ENTRIES; index++) entries.setItem(index, listed.get(index));
        return new ItemFilterMenu(id, inventory, cable, side, entries, new ContainerData() {
            @Override
            public int get(int index) { return ItemFilterItem.mode(card(cable, side)).ordinal(); }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        });
    }

    private static ItemStack card(AbstractCableBlockEntity cable, Direction side) {
        return cable.connectorFilters().getItem(side.ordinal());
    }

    public ItemFilterMode mode() { return ItemFilterMode.byOrdinal(data.get(MODE)); }

    /** Writes the current entries onto the card; the container's own change hook saves the cable. */
    private void store() {
        if (cable == null) return;
        ItemStack filter = card(cable, side);
        if (!ItemFilterItem.isFilter(filter)) return;
        NonNullList<ItemStack> list = NonNullList.withSize(ENTRIES, ItemStack.EMPTY);
        for (int index = 0; index < ENTRIES; index++) list.set(index, entries.getItem(index));
        ItemFilterItem.setEntries(filter, list);
        cable.connectorFilters().setChanged();
    }

    private void setEntry(int index, ItemStack stack) {
        entries.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        store();
    }

    private boolean listed(ItemStack stack) {
        for (int index = 0; index < ENTRIES; index++) {
            if (ItemStack.isSameItem(entries.getItem(index), stack)) return true;
        }
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cable == null || id != TOGGLE_MODE) return false;
        ItemStack filter = card(cable, side);
        if (!ItemFilterItem.isFilter(filter)) return false;
        ItemFilterItem.setMode(filter, ItemFilterItem.mode(filter).other());
        cable.connectorFilters().setChanged();
        return true;
    }

    /** Ghost entries take the carried item's kind, or clear; everything else is vanilla's business. */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < ENTRIES) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                setEntry(slotIndex, button == 1 ? ItemStack.EMPTY : getCarried());
            }
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }

    /** Shift-clicking an inventory item lists it in the first free entry; the item itself stays put. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < INVENTORY_START || index >= INVENTORY_END) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty() || listed(stack)) return ItemStack.EMPTY;
        for (int entry = 0; entry < ENTRIES; entry++) {
            if (entries.getItem(entry).isEmpty()) {
                setEntry(entry, stack);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (cable == null) return true;
        return !cable.isRemoved() && ItemFilterItem.isFilter(card(cable, side))
                && player.distanceToSqr(Vec3.atCenterOf(cable.getBlockPos())) <= REACH_SQUARED;
    }
}
