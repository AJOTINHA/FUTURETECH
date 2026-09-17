package dev.futuretech.menu;

import static dev.futuretech.block.entity.TeleporterBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The teleporter's menu: a column of card slots, each a destination the player can pick, the
 * pad's name to edit, and the energy the trips are paid from. Where the pad is and what it is
 * called ride in the opening packet, so the screen can show distances and the name at once;
 * picking a destination is a menu button, renaming a small packet of its own.
 */
public final class TeleporterMenu extends MachineMenu implements RedstoneControlMenu, EnergyInfoMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int CARD_X = 8;
    public static final int CARD_Y = 36;
    public static final int CARD_SPACING = 18;
    /**
     * Rows on screen at once, whatever the level holds. The window stays the size it has always
     * been and the rows scroll through it, rather than the window growing with the pad: an MK4
     * laid out a row per card would be seven hundred pixels tall.
     */
    public static final int VISIBLE = 4;
    public static final int INVENTORY_Y = CARD_Y + VISIBLE * CARD_SPACING + 14;
    public static final int NAME_LENGTH = 24;
    /** {@code SELECT_BASE + slot} picks that card as the destination; past the redstone buttons. */
    public static final int SELECT_BASE = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length + 10;
    /** {@code SCROLL_BASE + row} puts that card at the top of the window; past every select button. */
    public static final int SCROLL_BASE = SELECT_BASE + MAX_CARDS + 1;

    private final Container contents;
    private final ContainerData data;
    private final int mk;
    /** Card slots this level opened; what the window scrolls through. */
    private final int cards;
    /** The card showing in the window's top row. Both sides keep it, and clamp it the same way. */
    private int scrollRow;
    private final GlobalPos pos;
    private String name;
    private final int inventoryStart;
    private final int inventoryEnd;
    private final int hotbarStart;

    public TeleporterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt(), GlobalPos.of(buffer.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION),
                buffer.readBlockPos()), buffer.readUtf(NAME_LENGTH));
    }

    /** Client side: the upgrade slots lock by the same MK, so the tab draws them right before any sync. */
    private TeleporterMenu(int id, Inventory inventory, int mk, GlobalPos pos, String name) {
        this(id, inventory, new SimpleContainer(MAX_CARDS), new UpgradeInventory(() -> mk, () -> {}),
                new SimpleContainerData(DATA_COUNT), mk, pos, name);
    }

    public TeleporterMenu(int id, Inventory inventory, TeleporterBlockEntity teleporter, UpgradeInventory upgrades, ContainerData data) {
        this(id, inventory, teleporter, upgrades, data, data.get(DATA_MK), teleporter.globalPos(), teleporter.name());
    }

    private TeleporterMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data,
                           int mk, GlobalPos pos, String name) {
        super(ModMenus.TELEPORTER.get(), id);
        checkContainerSize(contents, MAX_CARDS);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.data = data;
        this.mk = Math.clamp(mk, 1, MachineLevel.MAX);
        this.cards = TeleporterBlockEntity.cards(this.mk);
        this.pos = pos;
        this.name = name;
        // One Slot per visible row, never per card: the row is the fixed thing and the card behind
        // it changes as the window scrolls.
        this.inventoryStart = VISIBLE;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        for (int row = 0; row < VISIBLE; row++) addSlot(new CardSlot(contents, row));
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof TeleporterBlockEntity) markSynced();
    }

    /** What to write when opening: the MK, where the pad is and what it is called. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, TeleporterBlockEntity teleporter) {
        buffer.writeVarInt(MachineLevel.of(teleporter.getBlockState()));
        GlobalPos pos = teleporter.globalPos();
        buffer.writeResourceKey(pos.dimension());
        buffer.writeBlockPos(pos.pos());
        buffer.writeUtf(teleporter.name(), NAME_LENGTH);
    }

    public int mk() { return mk; }

    /** How many cards this pad holds: four at MK1, doubling each level. */
    public int cards() { return cards; }

    /** Whether there are more cards than rows, which is when the scrollbar has anything to do. */
    public boolean scrollable() { return cards > VISIBLE; }

    /** The furthest the window can be scrolled down; zero when everything already fits. */
    public int maxScroll() { return Math.max(0, cards - VISIBLE); }

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

    public GlobalPos pos() { return pos; }

    /** The name as last known here; the screen keeps it while the player types. */
    public String name() { return name; }

    public void setName(String name) { this.name = name; }

    /** The card in a slot, or null with none there. */
    public @Nullable TeleportTarget card(int slot) { return TeleportCardItem.target(contents.getItem(slot)); }

    /**
     * The card showing in {@code row} of the window, read through the row's own slot rather than
     * by index: the slot is what the server fills, so it is right even on the tick a scroll lands.
     */
    public @Nullable TeleportTarget rowCard(int row) {
        return TeleportCardItem.target(slots.get(row).getItem());
    }

    /** What a trip to the card showing in {@code row} costs from here, at this level. */
    public int rowCost(int row) {
        TeleportTarget target = rowCard(row);
        return target == null ? 0 : TeleporterBlockEntity.cost(pos, target, mk);
    }

    /** Whether this level can go where the card showing in {@code row} points. */
    public boolean rowReaches(int row) {
        TeleportTarget target = rowCard(row);
        return target != null && TeleporterBlockEntity.reaches(pos, target, mk);
    }

    public int selected() { return data.get(DATA_SELECTED); }

    /** What a trip to the card in {@code slot} would cost from here, at this level. */
    public int cost(int slot) {
        TeleportTarget target = card(slot);
        return target == null ? 0 : TeleporterBlockEntity.cost(pos, target, mk);
    }

    /** Whether this level can go where the card in {@code slot} points. */
    public boolean reaches(int slot) {
        TeleportTarget target = card(slot);
        return target != null && TeleporterBlockEntity.reaches(pos, target, mk);
    }

    public ResourceKey<Level> dimension() { return pos.dimension(); }

    public BlockPos blockPos() { return pos.pos(); }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk); }

    /** No steady draw: the rate shown is what the chosen trip costs. */
    @Override
    public int energyRatePerTick() { return selected() < 0 ? 0 : cost(selected()); }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.CONSUMER; }

    @Override
    public boolean isWorking() { return data.get(DATA_CHARGING) != 0; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= SELECT_BASE && buttonId < SELECT_BASE + cards) {
            if (contents instanceof TeleporterBlockEntity teleporter) teleporter.select(buttonId - SELECT_BASE);
            return true;
        }
        if (buttonId >= SCROLL_BASE && buttonId <= SCROLL_BASE + maxScroll()) {
            scrollTo(buttonId - SCROLL_BASE);
            // The rows now show other cards; the client is holding what the old ones had.
            broadcastFullState();
            return true;
        }
        return RedstoneControlMenu.handleButton(contents instanceof RedstoneControllable target ? target : null, buttonId);
    }

    /** The server's side of a rename: only from a player who has this menu open, which is who sends it. */
    public void rename(String name) {
        if (contents instanceof TeleporterBlockEntity teleporter) teleporter.setName(name);
        this.name = name;
    }

    /**
      * Also closes when the pad's level changes underneath: this is the one machine whose slot
      * count is its MK, so a screen opened on an MK1 and upgraded by someone else would be rows
      * short of the server's. Closing it means the next open lays the rows out again.
      */
    @Override
    public boolean stillValid(Player player) {
        if (contents instanceof TeleporterBlockEntity teleporter
                && MachineLevel.of(teleporter.getBlockState()) != mk) {
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
     * and write goes through {@link #card()}, and a row past the end of this level's cards is
     * simply not there.
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
