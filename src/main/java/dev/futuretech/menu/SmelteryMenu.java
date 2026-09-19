package dev.futuretech.menu;

import static dev.futuretech.block.entity.SmelteryBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.entity.SmelteryBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
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
 * The smeltery's menu lays out one row per open lane — two ingredient slots, then the alloy — so
 * the MK travels in the opening packet: the client has to know how many rows to make before any
 * data slot arrives.
 */
public final class SmelteryMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    private final Container contents;
    private final UpgradeInventory upgrades;
    private final ContainerData data;
    private final int lanes;
    private final int inventoryStart;
    private final int inventoryEnd;
    private final int hotbarStart;

    /** Width of the smeltery screen; upgrade slots sit in the tab beside it. */
    public static final int IMAGE_WIDTH = 176;
    /** The two ingredients sit side by side, then the arrow, then the alloy. */
    public static final int INPUT_A_X = 38;
    public static final int INPUT_B_X = 56;
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

    public SmelteryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt());
    }

    /** Client side: the upgrade slots lock by the same MK, so the tab draws them right before any sync. */
    private SmelteryMenu(int id, Inventory inventory, int mk) {
        this(id, inventory, new SimpleContainer(INVENTORY_SIZE), new UpgradeInventory(() -> mk, () -> {}), new SimpleContainerData(DATA_COUNT), mk);
    }

    public SmelteryMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data) {
        this(id, inventory, contents, upgrades, data, data.get(DATA_MK));
    }

    private SmelteryMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data, int mk) {
        super(ModMenus.SMELTERY.get(), id);
        checkContainerSize(contents, INVENTORY_SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.upgrades = upgrades;
        this.data = data;
        this.lanes = Math.clamp(mk, 1, LANES);
        this.inventoryStart = 3 * lanes;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        // Menu slots go first inputs, second inputs, outputs, mirroring the block entity's order.
        for (int lane = 0; lane < lanes; lane++) addSlot(new Slot(contents, SLOT_INPUT_A + lane, INPUT_A_X, rowY(lane)));
        for (int lane = 0; lane < lanes; lane++) addSlot(new Slot(contents, SLOT_INPUT_B + lane, INPUT_B_X, rowY(lane)));
        for (int lane = 0; lane < lanes; lane++) {
            addSlot(new Slot(contents, SLOT_OUTPUT + lane, OUTPUT_X, rowY(lane)) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y + extraHeight());
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof SmelteryBlockEntity) markSynced();
    }

    /** What to write when opening: the MK, which fixes the number of rows. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, SmelteryBlockEntity smeltery) {
        buffer.writeVarInt(smeltery.lanes());
    }

    /** Open lanes, each a pair of input slots with an output slot. */
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
    public int energyRatePerTick() { return upgrades.consumption(ENERGY_PER_TICK, mk()) * lanes; }

    @Override
    public int energyUsagePerTick() {
        return upgrades.consumption(ENERGY_PER_TICK, mk()) * Integer.bitCount(data.get(DATA_WORKING));
    }

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
    public BlockState displayState() { return ModBlocks.SMELTERY.get().displayState(front()).setValue(MachineLevel.MK, Math.clamp(data.get(DATA_MK), 1, 4)); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.SMELTERY.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.SMELTERY.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.SMELTERY.get().supportsAutoPush(); }

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
        } else if (!moveToInputs(stack)) {
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

    /**
     * Shift-clicking an ingredient tops up slots already holding it, then completes a lane whose
     * partner slot is filled, then takes the first free slot: two shift-clicks make a pair on lane
     * one instead of spreading over two lanes' first slots.
     */
    private boolean moveToInputs(ItemStack stack) {
        boolean moved = false;
        for (int slotIndex = 0; slotIndex < 2 * lanes && !stack.isEmpty(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            if (slot.hasItem() && ItemStack.isSameItemSameComponents(slot.getItem(), stack)) {
                moved |= moveItemStackTo(stack, slotIndex, slotIndex + 1, false);
            }
        }
        for (int lane = 0; lane < lanes && !stack.isEmpty(); lane++) {
            boolean hasA = slots.get(lane).hasItem();
            boolean hasB = slots.get(lanes + lane).hasItem();
            if (hasA == hasB) continue;
            int target = hasA ? lanes + lane : lane;
            moved |= moveItemStackTo(stack, target, target + 1, false);
        }
        for (int lane = 0; lane < lanes && !stack.isEmpty(); lane++) {
            if (!slots.get(lane).hasItem()) moved |= moveItemStackTo(stack, lane, lane + 1, false);
        }
        return moved;
    }
}
