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
 * The teleporter's menu: the pad's one card slot, a destination the player can pick, the pad's
 * name to edit, and the energy the trips are paid from. Where the pad is and what it is called
 * ride in the opening packet, so the screen can show the distance and the name at once; picking
 * the destination is a menu button, renaming a small packet of its own. The rest of the pad's
 * destinations are on the network, picked from a network panel.
 */
public final class TeleporterMenu extends MachineMenu implements RedstoneControlMenu, EnergyInfoMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int CARD_X = 8;
    public static final int CARD_SPACING = 18;
    /** The area under the name box the window keeps, for the energy column and the tabs beside it. */
    public static final int LIST_TOP = 36;
    public static final int LIST_HEIGHT = 4 * CARD_SPACING;
    /** The one card row sits in the middle of that area. */
    public static final int CARD_Y = LIST_TOP + (LIST_HEIGHT - 16) / 2;
    public static final int INVENTORY_Y = LIST_TOP + LIST_HEIGHT + 14;
    public static final int NAME_LENGTH = 24;
    /** {@code SELECT_BASE + slot} picks that card as the destination; past the redstone buttons. */
    public static final int SELECT_BASE = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length + 10;

    private final Container contents;
    private final UpgradeInventory upgrades;
    private final ContainerData data;
    private final int mk;
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
        this(id, inventory, new SimpleContainer(1), new UpgradeInventory(() -> mk, () -> {}),
                new SimpleContainerData(DATA_COUNT), mk, pos, name);
    }

    public TeleporterMenu(int id, Inventory inventory, TeleporterBlockEntity teleporter, UpgradeInventory upgrades, ContainerData data) {
        this(id, inventory, teleporter, upgrades, data, data.get(DATA_MK), teleporter.globalPos(), teleporter.name());
    }

    private TeleporterMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data,
                           int mk, GlobalPos pos, String name) {
        super(ModMenus.TELEPORTER.get(), id);
        checkContainerSize(contents, 1);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.upgrades = upgrades;
        this.data = data;
        this.mk = Math.clamp(mk, 1, MachineLevel.MAX);
        this.pos = pos;
        this.name = name;
        this.inventoryStart = 1;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        addSlot(new CardSlot(contents));
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

    public GlobalPos pos() { return pos; }

    /** The name as last known here; the screen keeps it while the player types. */
    public String name() { return name; }

    public void setName(String name) { this.name = name; }

    /** The card in the pad's slot, or null with none there. */
    public @Nullable TeleportTarget card() { return TeleportCardItem.target(contents.getItem(CARD_SLOT)); }

    /** What a trip to the pad's own card would cost from here, at this level. */
    public int cost() {
        TeleportTarget target = card();
        return target == null ? 0 : upgrades.cost(TeleporterBlockEntity.cost(pos, target, mk));
    }

    /** Whether this level can go where the pad's own card points. */
    public boolean reaches() {
        TeleportTarget target = card();
        return target != null && TeleporterBlockEntity.reaches(pos, target, mk);
    }

    /** The pad's own slot when it is the chosen destination, or -1: nothing, or a card on the network. */
    public int selected() { return data.get(DATA_SELECTED); }

    public ResourceKey<Level> dimension() { return pos.dimension(); }

    public BlockPos blockPos() { return pos.pos(); }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk); }

    /** No steady draw: the rate shown is what the chosen trip costs, wherever its card is. */
    @Override
    public int energyRatePerTick() { return data.get(DATA_COST); }

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
        if (buttonId == SELECT_BASE + CARD_SLOT) {
            if (contents instanceof TeleporterBlockEntity teleporter) teleporter.select(CARD_SLOT);
            return true;
        }
        return RedstoneControlMenu.handleButton(contents instanceof RedstoneControllable target ? target : null, buttonId);
    }

    /** The server's side of a rename: only from a player who has this menu open, which is who sends it. */
    public void rename(String name) {
        if (contents instanceof TeleporterBlockEntity teleporter) teleporter.setName(name);
        this.name = name;
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
        if (index < inventoryStart || index >= inventoryEnd) {
            // The card slot and the upgrade slots empty into the player's inventory.
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, inventoryEnd, inventoryEnd + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (!TeleportCardItem.isWritten(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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

    /** The pad's one card slot: a written card, and only one. */
    private static final class CardSlot extends Slot {
        private CardSlot(Container contents) {
            super(contents, CARD_SLOT, CARD_X, CARD_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return TeleportCardItem.isWritten(stack); }

        @Override
        public int getMaxStackSize() { return 1; }
    }
}
