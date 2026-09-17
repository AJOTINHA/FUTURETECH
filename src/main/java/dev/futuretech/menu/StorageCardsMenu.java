package dev.futuretech.menu;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.entity.StorageCardsBlockEntity;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.teleport.StorageCardsEditPayload;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The card storage's menu: a column of card slots, each a destination the network's pads can
 * send to, shown four at a time in a window that scrolls through the shelf. The window is the
 * teleporter's old one, moved here when the pads went down to a single card: the rows are the
 * fixed thing and the cards behind them change as the window scrolls, and the level says how
 * many there are to scroll through.
 */
public final class StorageCardsMenu extends MachineMenu {
    /** Wider than the machines by a slot: the pencil past each row is one, and the margin stays the rows' own. */
    public static final int IMAGE_WIDTH = 188;
    /** The player's inventory, centred in the wider window. */
    public static final int INVENTORY_X = (IMAGE_WIDTH - 9 * 18) / 2;
    public static final int CARD_X = 8;
    /** Under the header and its shadow, the way the panel's rows sit. */
    public static final int CARD_Y = 22;
    public static final int CARD_SPACING = 18;
    /** Rows on screen at once: the shelf laid out a row per card would be far taller than the screen. */
    public static final int VISIBLE = 4;
    public static final int INVENTORY_Y = CARD_Y + VISIBLE * CARD_SPACING + 14;
    /** {@code SCROLL_BASE + row} puts that card at the top of the window. */
    public static final int SCROLL_BASE = 100;

    private final Container contents;
    private final int mk;
    /** Card slots this level opened; what the window scrolls through. */
    private final int cards;
    /** The card showing in the window's top row. Both sides keep it, and clamp it the same way. */
    private int scrollRow;
    private final int inventoryStart;
    private final int inventoryEnd;
    private final int hotbarStart;

    public StorageCardsMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt());
    }

    /** Client side: the upgrade slots lock by the same MK, so the tab draws them right before any sync. */
    private StorageCardsMenu(int id, Inventory inventory, int mk) {
        this(id, inventory, new SimpleContainer(StorageCardsBlockEntity.SLOTS), new UpgradeInventory(() -> mk, () -> {}), mk);
    }

    public StorageCardsMenu(int id, Inventory inventory, StorageCardsBlockEntity storage, UpgradeInventory upgrades) {
        this(id, inventory, storage, upgrades, MachineLevel.of(storage.getBlockState()));
    }

    private StorageCardsMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, int mk) {
        super(ModMenus.STORAGE_CARDS.get(), id);
        checkContainerSize(contents, StorageCardsBlockEntity.SLOTS);
        this.contents = contents;
        this.mk = Math.clamp(mk, 1, MachineLevel.MAX);
        this.cards = StorageCardsBlockEntity.cards(this.mk);
        // One Slot per visible row, never per card: the row is the fixed thing and the card behind
        // it changes as the window scrolls.
        this.inventoryStart = VISIBLE;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        for (int row = 0; row < VISIBLE; row++) addSlot(new CardSlot(contents, row));
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        if (contents instanceof StorageCardsBlockEntity) markSynced();
    }

    /** What to write when opening: the MK, which is how many rows there are. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, StorageCardsBlockEntity storage) {
        buffer.writeVarInt(MachineLevel.of(storage.getBlockState()));
    }

    public int mk() { return mk; }

    /** How many cards this storage holds: four at MK1, doubling each level. */
    public int cards() { return cards; }

    /** Whether there are more cards than rows, which is when the scrollbar has anything to do. */
    public boolean scrollable() { return cards > VISIBLE; }

    public int maxScroll() { return Math.max(0, cards() - VISIBLE); }

    public int scrollRow() { return scrollRow; }

    /** The card showing in {@code row} of the window right now. */
    public int cardAt(int row) { return scrollRow + row; }

    /**
     * Moves the window. Both sides clamp the same way, so a scroll the server trims lands where
     * the client put it; the click that follows travels behind the scroll on the same connection,
     * so the server has already moved the window by the time it resolves which card was hit.
     */
    public void scrollTo(int row) {
        scrollRow = Math.clamp(row, 0, maxScroll());
    }

    /**
     * The card showing in {@code row} of the window, read through the row's own slot rather than
     * by index: the slot is what the server fills, so it is right even on the tick a scroll lands.
     */
    public @Nullable TeleportTarget rowCard(int row) {
        return TeleportCardItem.target(slots.get(row).getItem());
    }

    /** The server's side of a card's edit: only from a player who has this menu open, which is who sends it. */
    public void edit(int slot, String name, int colour) {
        if (contents instanceof StorageCardsBlockEntity storage) storage.editCard(slot, name, colour);
    }

    /** The client's side: name and colour go up once, when the player is done with them. */
    public static void sendEdit(int slot, String name, int colour) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new StorageCardsEditPayload(slot, name, colour));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= SCROLL_BASE && buttonId <= SCROLL_BASE + maxScroll()) {
            scrollTo(buttonId - SCROLL_BASE);
            // The rows now show other cards; the client is holding what the old ones had.
            broadcastFullState();
            return true;
        }
        return false;
    }

    /**
     * Also closes when the storage's level changes underneath: its slot count is its MK, so a
     * screen opened on an MK1 and upgraded by someone else would be rows short of the server's.
     * Closing it means the next open lays the rows out again.
     */
    @Override
    public boolean stillValid(Player player) {
        if (contents instanceof StorageCardsBlockEntity storage
                && MachineLevel.of(storage.getBlockState()) != mk) {
            return false;
        }
        return contents.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < inventoryStart || index >= inventoryEnd) {
            // Card and upgrade slots empty into the player's inventory.
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, inventoryEnd, inventoryEnd + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (!TeleportCardItem.isWritten(stack) || !moveItemStackTo(stack, 0, VISIBLE, false)) {
            if (index < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, inventoryEnd, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, inventoryStart, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    /**
     * A row of the window, bound to whichever card is scrolled under it. The slot's own position
     * and container index are final in vanilla, so the index is the thing that moves: every read
     * and write goes through {@link #card()}.
     */
    private final class CardSlot extends Slot {
        private final int row;

        private CardSlot(Container contents, int row) {
            super(contents, row, CARD_X, CARD_Y + row * CARD_SPACING);
            this.row = row;
        }

        /** The card this row is showing right now. */
        private int card() { return scrollRow + row; }

        @Override
        public int getSlotIndex() { return card(); }

        @Override
        public ItemStack getItem() { return contents.getItem(card()); }

        @Override
        public void set(ItemStack stack) {
            contents.setItem(card(), stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) { return contents.removeItem(card(), amount); }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return card() < cards && TeleportCardItem.isWritten(stack);
        }

        /** The last window of a level whose count is not a multiple of four can run off the end. */
        @Override
        public boolean isActive() { return card() < cards; }

        @Override
        public int getMaxStackSize() { return 1; }
    }
}
