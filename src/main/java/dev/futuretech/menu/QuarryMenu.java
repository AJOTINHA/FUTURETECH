package dev.futuretech.menu;

import static dev.futuretech.block.entity.QuarryBlockEntity.*;

import dev.futuretech.api.gui.EnergyInfoMenu;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SlotRole;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import dev.futuretech.block.QuarryArea;
import dev.futuretech.block.QuarryStatus;
import dev.futuretech.block.entity.QuarryBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
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

public final class QuarryMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    /** Reads the markers around the quarry into a box again; the one button this screen has. */
    public static final int BUTTON_READ_AREA = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;

    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 212;
    /** The buffer: five across and three down, in the top left corner. */
    public static final int COLUMNS = 5;
    public static final int ROWS = 3;
    public static final int SLOT_X = 8;
    public static final int SLOT_Y = 22;
    public static final int INVENTORY_Y = 130;
    private static final int PLAYER_START = INVENTORY_SIZE;
    private static final int HOTBAR_START = PLAYER_START + 27;
    private static final int PLAYER_END = HOTBAR_START + 9;

    private final Container buffer;
    private final UpgradeInventory upgrades;
    private final ContainerData data;

    public QuarryMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Client side: the upgrade slots lock by the synced MK, so the tab draws them like the server has them. */
    private QuarryMenu(int id, Inventory inventory, ContainerData data) {
        this(id, inventory, new SimpleContainer(INVENTORY_SIZE),
                new UpgradeInventory(() -> Math.clamp(data.get(DATA_MK), 1, 4), () -> {}), data);
    }

    public QuarryMenu(int id, Inventory inventory, Container buffer, UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.QUARRY.get(), id);
        checkContainerSize(buffer, INVENTORY_SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.buffer = buffer;
        this.upgrades = upgrades;
        this.data = data;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            addSlot(new Slot(buffer, slot, SLOT_X + slot % COLUMNS * 18, SLOT_Y + slot / COLUMNS * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (buffer instanceof QuarryBlockEntity) markSynced();
    }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    public int mk() { return Math.clamp(data.get(DATA_MK), 1, 4); }

    @Override
    public int energyRatePerTick() { return upgrades.consumption(ENERGY_PER_TICK, mk()); }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.CONSUMER; }

    @Override
    public boolean isWorking() { return status() == QuarryStatus.MINING; }

    public QuarryStatus status() { return QuarryStatus.byOrdinal(data.get(DATA_STATUS)); }

    /** How far along the block being dug is, from zero to one. */
    public float diggingProgress() {
        int total = data.get(DATA_PROGRESS_TOTAL);
        return total <= 0 ? 0 : Math.clamp(data.get(DATA_PROGRESS) / (float) total, 0, 1);
    }

    public int areaWidth() { return data.get(DATA_WIDTH); }

    public int areaDepth() { return data.get(DATA_DEPTH); }

    /** The layer being dug, as a world y; only meaningful while there is an area. */
    public int layer() { return data.get(DATA_LAYER); }

    public boolean hasArea() { return areaWidth() > 0 && areaDepth() > 0; }

    /** Longest side this level accepts, for the line that says the box is too big. */
    public int maxSide() { return QuarryArea.maxSide(mk()); }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.QUARRY.get().displayState(front()).setValue(MachineLevel.MK, mk()); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.QUARRY.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.QUARRY.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.QUARRY.get().supportsAutoPush(); }

    @Override
    public boolean isAutoPulling() { return data.get(DATA_AUTO_BASE) != 0; }

    @Override
    public boolean isAutoPushing() { return data.get(DATA_AUTO_BASE + 1) != 0; }

    @Override
    public SlotRole slotRole(int index) { return index < INVENTORY_SIZE ? SlotRole.OUTPUT : SlotRole.NONE; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_READ_AREA) {
            if (buffer instanceof QuarryBlockEntity quarry) quarry.readMarkers();
            return true;
        }
        return SideConfigMenu.handleButton(buffer instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(buffer instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return buffer.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START || index >= PLAYER_END) {
            // Out of the buffer or an upgrade slot, into the player's inventory.
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, PLAYER_END, PLAYER_END + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
