package dev.futuretech.menu;

import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.item.ItemFilterMatch;
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
 * Editor for the filter card in one connector's slot. The entries are ghost slots: clicking one
 * with an item in hand lists that item without spending it, clicking with an empty hand clears
 * it, and shift-clicking an item in the inventory lists it in the first free entry. Every change
 * is written straight back to the card, so taking the card out afterwards keeps the list.
 *
 * <p>How many entries there are is the card's own business, so the grid is built from the tier
 * the opening packet carries. An MK1 keeps its dispenser-sized square and its mode button; the
 * cards above it lay nine to a row and put the mode behind the gear, together with how closely
 * they read what they compare.
 */
public final class ItemFilterMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 3;
    public static final int TOGGLE_MODE = 0;
    /** One button per switch behind the gear, in {@link ItemFilterMatch.Option} order. */
    public static final int TOGGLE_MATCH_BASE = 1;
    /**
     * The level the card keeps rides on the button's own number: this plus the level. A button
     * carries one number, and the level is one, so the packet the switches use serves for it too.
     */
    public static final int SET_COUNT_BASE = TOGGLE_MATCH_BASE + ItemFilterMatch.Option.values().length;
    /** Top of the grid of entries, clear of the header. */
    public static final int GRID_Y = 26;
    /** Rows of panel between the last entry and the inventory, which the label sits in. */
    public static final int INVENTORY_GAP = 14;
    private static final int MODE = 0;
    private static final int MATCH = 1;
    private static final int COUNT = 2;
    private static final int PLAYER_SLOTS = 36;
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
    private final ItemFilterItem.Tier tier;

    /** Client side: the opening packet says which connector's card this is, and which card it is. */
    public ItemFilterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, null, extra.readEnum(Direction.class), extra.readEnum(ItemFilterItem.Tier.class),
                null, new SimpleContainerData(DATA_COUNT));
    }

    private ItemFilterMenu(int id, Inventory inventory, @Nullable AbstractCableBlockEntity cable, Direction side,
                           ItemFilterItem.Tier tier, @Nullable SimpleContainer listed, ContainerData data) {
        super(ModMenus.ITEM_FILTER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.cable = cable;
        this.side = side;
        this.tier = tier;
        this.entries = listed == null ? new SimpleContainer(tier.slots) : listed;
        this.data = data;
        checkContainerSize(entries, tier.slots);
        for (int index = 0; index < tier.slots; index++) {
            addSlot(new GhostSlot(entries, index, gridX() + index % tier.columns() * 18,
                    GRID_Y + index / tier.columns() * 18));
        }
        addStandardInventorySlots(inventory, 8, inventoryTop());
        addDataSlots(data);
    }

    public ItemFilterItem.Tier tier() { return tier; }

    /** The MK1's small square is centred; the wider cards start where the inventory does. */
    public int gridX() { return tier == ItemFilterItem.Tier.MK1 ? 62 : 8; }

    public int inventoryTop() { return GRID_Y + tier.rows() * 18 + INVENTORY_GAP; }

    /** Server side: the entries start as the card's list and every edit goes back onto the card. */
    public static ItemFilterMenu opening(int id, Inventory inventory, AbstractCableBlockEntity cable, Direction side) {
        ItemFilterItem.Tier tier = ItemFilterItem.tier(card(cable, side));
        var entries = new SimpleContainer(tier.slots);
        fill(entries, card(cable, side), tier);
        return new ItemFilterMenu(id, inventory, cable, side, tier, entries, new ContainerData() {
            @Override
            public int get(int index) {
                ItemStack filter = card(cable, side);
                return switch (index) {
                    case MATCH -> bits(ItemFilterItem.match(filter));
                    case COUNT -> ItemFilterItem.count(filter);
                    default -> ItemFilterItem.mode(filter).ordinal();
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        });
    }

    private static ItemStack card(AbstractCableBlockEntity cable, Direction side) {
        return cable.connectorFilters().getItem(side.ordinal());
    }

    /** Lays the card's list into the ghost slots, one of each, the way a single listed item always has. */
    private static void fill(SimpleContainer entries, ItemStack filter, ItemFilterItem.Tier tier) {
        NonNullList<ItemStack> listed = ItemFilterItem.entries(filter);
        for (int index = 0; index < tier.slots; index++) entries.setItem(index, listed.get(index));
    }

    public ItemFilterMode mode() { return ItemFilterMode.byOrdinal(data.get(MODE)); }

    /** The level the card keeps, as the data slot has it. */
    public int count() { return data.get(COUNT); }

    /** How closely the card reads what it compares, as the data slot has it. */
    public ItemFilterMatch match() {
        ItemFilterMatch match = ItemFilterMatch.ITEM_ONLY;
        int bits = data.get(MATCH);
        for (ItemFilterMatch.Option option : ItemFilterMatch.Option.values()) {
            match = match.with(option, (bits & 1 << option.ordinal()) != 0);
        }
        return match;
    }

    /** The match as the one number a data slot carries, a bit per switch. */
    private static int bits(ItemFilterMatch match) {
        int bits = 0;
        for (ItemFilterMatch.Option option : ItemFilterMatch.Option.values()) {
            if (match.follows(option)) bits |= 1 << option.ordinal();
        }
        return bits;
    }

    /** Writes the current entries onto the card; the container's own change hook saves the cable. */
    private void store() {
        if (cable == null) return;
        ItemStack filter = card(cable, side);
        if (!ItemFilterItem.isFilter(filter)) return;
        NonNullList<ItemStack> list = NonNullList.withSize(tier.slots, ItemStack.EMPTY);
        for (int index = 0; index < tier.slots; index++) list.set(index, entries.getItem(index));
        ItemFilterItem.setEntries(filter, list);
        cable.connectorFilters().setChanged();
    }

    private void setEntry(int index, ItemStack stack) {
        entries.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        store();
    }

    private boolean listed(ItemStack stack) {
        for (int index = 0; index < tier.slots; index++) {
            if (ItemStack.isSameItem(entries.getItem(index), stack)) return true;
        }
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cable == null) return false;
        ItemStack filter = card(cable, side);
        if (!ItemFilterItem.isFilter(filter)) return false;
        if (id == TOGGLE_MODE) {
            ItemFilterItem.setMode(filter, ItemFilterItem.mode(filter).other());
        } else if (id >= SET_COUNT_BASE) {
            // Only a card that is keeping a level has one to set.
            if (!ItemFilterItem.counting(filter)) return false;
            ItemFilterItem.setCount(filter, Math.min(id - SET_COUNT_BASE, ItemFilterItem.MAX_COUNT));
        } else {
            int option = id - TOGGLE_MATCH_BASE;
            ItemFilterMatch.Option[] options = ItemFilterMatch.Option.values();
            // Only a card that offers a switch has anything to set for it.
            if (option < 0 || option >= options.length || !tier.offers(options[option])) return false;
            ItemFilterItem.setMatch(filter, ItemFilterItem.match(filter).toggle(options[option]));
        }
        cable.connectorFilters().setChanged();
        return true;
    }

    /** Ghost entries take the carried item's kind, or clear; everything else is vanilla's business. */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < tier.slots) {
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
        if (index < tier.slots || index >= tier.slots + PLAYER_SLOTS) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty() || listed(stack)) return ItemStack.EMPTY;
        for (int entry = 0; entry < tier.slots; entry++) {
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
