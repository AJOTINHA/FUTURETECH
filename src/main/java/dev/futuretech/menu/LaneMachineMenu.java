package dev.futuretech.menu;

import static dev.futuretech.block.entity.LaneMachineBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.energy.EnergySync;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

/**
 * The crusher's and the sawmill's menu lays out one row of slots per open lane, centred on the
 * panel, so the kind and the MK travel in the opening packet: the client has to know which machine
 * and how many rows to make before any data slot arrives.
 */
public final class LaneMachineMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    public final LaneMachineKind kind;
    private final Container contents;
    private final ContainerData data;
    private final int lanes;
    private final int inventoryStart;
    private final int inventoryEnd;
    private final int hotbarStart;

    /** Width of the screen; upgrade slots sit in the tab beside it. */
    public static final int IMAGE_WIDTH = 176;
    public static final int INPUT_X = 56;
    public static final int OUTPUT_X = 104;
    /** Top of the single lane's slot row; further lanes follow 18 px apart. */
    public static final int ROW_Y = 45;
    public static final int ROW_SPACING = 18;
    /** With more lanes the rows start higher, level with the top of the energy column, before the panel grows. */
    public static final int ROWS_TOP = 29;
    /** Below the last input slot: a gap, then the 14 px working indicator; the single-lane panel ends there at 78. */
    public static final int BELOW_ROWS = 15;
    public static final int CONTENT_BOTTOM = ROW_Y + ROW_SPACING + BELOW_ROWS;
    /** Top of the player inventory on a single-lane panel. */
    public static final int INVENTORY_Y = 102;

    public LaneMachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(LaneMachineKind.values()[buffer.readVarInt()], id, inventory, buffer.readVarInt());
    }

    /** Client side: the upgrade slots lock by the same MK, so the tab draws them right before any sync. */
    private LaneMachineMenu(LaneMachineKind kind, int id, Inventory inventory, int mk) {
        this(kind, id, inventory, new SimpleContainer(2 * LANES), new UpgradeInventory(() -> mk, () -> {}), new SimpleContainerData(DATA_COUNT), mk);
    }

    public LaneMachineMenu(LaneMachineKind kind, int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data) {
        this(kind, id, inventory, contents, upgrades, data, data.get(DATA_MK));
    }

    private LaneMachineMenu(LaneMachineKind kind, int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data, int mk) {
        super(kind.menuType(), id);
        this.kind = kind;
        checkContainerSize(contents, 2 * LANES);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.data = data;
        this.lanes = Math.clamp(mk, 1, LANES);
        this.inventoryStart = 2 * lanes;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        // Each pair straddles the panel's centre line, leaving room for progress between them.
        for (int lane = 0; lane < lanes; lane++) addSlot(new Slot(contents, SLOT_INPUT + lane, INPUT_X, rowY(lane)));
        for (int lane = 0; lane < lanes; lane++) {
            addSlot(new Slot(contents, SLOT_OUTPUT + lane, OUTPUT_X, rowY(lane)) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y + extraHeight());
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof LaneMachineBlockEntity) markSynced();
    }

    /** What to write when opening: the kind, and the MK, which fixes the number of rows. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, LaneMachineBlockEntity machine) {
        buffer.writeVarInt(machine.kind.ordinal());
        buffer.writeVarInt(machine.lanes());
    }

    /** Open lanes, each an input row paired with an output row. */
    public int lanes() { return lanes; }

    /** The level from the opening packet; the data slot only confirms it later. */
    public int mk() { return lanes; }

    /** Top of lane {@code lane}'s slot row, relative to the screen. */
    public int rowY(int lane) { return (lanes == 1 ? ROW_Y : ROWS_TOP) + ROW_SPACING * lane; }

    /** How much taller than a single-lane panel this one is: what the rows and the indicator need past the usual bottom. */
    public int extraHeight() { return Math.max(0, rowY(lanes - 1) + ROW_SPACING + BELOW_ROWS - CONTENT_BOTTOM); }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    @Override
    public int energyRatePerTick() { return MachineLevel.consumption(ENERGY_PER_TICK, mk()) * lanes; }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.CONSUMER; }

    public int progress(int lane) { return data.get(DATA_PROGRESS_BASE + lane); }

    public int progressTotal(int lane) { return Math.max(1, data.get(DATA_PROGRESS_TOTAL_BASE + lane)); }

    public boolean isWorking(int lane) { return (data.get(DATA_WORKING) & 1 << lane) != 0; }

    @Override
    public boolean isWorking() { return data.get(DATA_WORKING) != 0; }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return kind.block().displayState(front()).setValue(MachineLevel.MK, Math.clamp(data.get(DATA_MK), 1, 4)); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) kind.block()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return kind.block().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return kind.block().supportsAutoPush(); }

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
        if (index < inventoryStart || index >= inventoryEnd) {
            // Machine and upgrade slots empty into the player's inventory.
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, inventoryEnd, inventoryEnd + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, lanes, false)) {
            // Recipe validity is checked by the server when processing the input.
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
}
