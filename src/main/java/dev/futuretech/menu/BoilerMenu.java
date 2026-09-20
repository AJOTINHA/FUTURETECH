package dev.futuretech.menu;

import static dev.futuretech.block.entity.BoilerBlockEntity.*;

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
import dev.futuretech.block.entity.BoilerBlockEntity;
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

public final class BoilerMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu {
    private final Container buckets;
    private final ContainerData data;

    /** Width of the boiler screen. */
    public static final int IMAGE_WIDTH = 176;
    public static final int SLOT_X = 8;
    public static final int INPUT_Y = 31;
    public static final int FUEL_X = 80, FUEL_Y = 43;
    public static final int OUTPUT_Y = 61;
    private static final int PLAYER_START = INVENTORY_SIZE;
    private static final int HOTBAR_START = PLAYER_START + 27;
    private static final int PLAYER_END = HOTBAR_START + 9;

    public BoilerMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Client menu receives the machine state from the server. */
    private BoilerMenu(int id, Inventory inventory, ContainerData data) {
        this(id, inventory, new SimpleContainer(INVENTORY_SIZE),
                new UpgradeInventory(() -> Math.clamp(data.get(DATA_MK), 1, 4), () -> {}), data);
    }

    public BoilerMenu(int id, Inventory inventory, Container buckets, UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.BOILER.get(), id);
        checkContainerSize(buckets, INVENTORY_SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.buckets = buckets;
        this.data = data;
        addSlot(new Slot(buckets,SLOT_FUEL,FUEL_X,FUEL_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return BoilerBlockEntity.acceptsFuel(fuelMode(), stack); }
            /** A lava tank or an energy column stands where the slot was while an upgrade heats the water. */
            @Override public boolean isActive() { return fuelMode() == SOLID; }
        });
        addSlot(new Slot(buckets, SLOT_INPUT, SLOT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return BoilerBlockEntity.isWaterContainer(stack); }
        });
        addSlot(new Slot(buckets, SLOT_OUTPUT, SLOT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, 102);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (buckets instanceof BoilerBlockEntity) markSynced();
    }

    public int mk() { return Math.clamp(data.get(DATA_MK),1,4); }
    /** {@link BoilerBlockEntity#SOLID}, {@link BoilerBlockEntity#LAVA} or {@link BoilerBlockEntity#ENERGY}. */
    public int fuelMode() { return Math.clamp(data.get(DATA_MODE), SOLID, ENERGY); }
    /** Lava in the tank or FE in the buffer, whichever the mode burns; zero on solid fuel. */
    public int reserve() { return data.get(DATA_RESERVE); }
    public int heatPercent() { return data.get(DATA_HEAT_PERCENT); }
    /** mB of water a tick at full steam, speed upgrades included. */
    public int maxWaterPerTick() { return Math.max(1, data.get(DATA_MAX_WATER)); }
    public int waterStored() { return data.get(DATA_WATER); }
    public int steamStored() { return EnergySync.unpack(data.get(DATA_STEAM_LOW),data.get(DATA_STEAM_HIGH)); }
    public int burnRemaining() { return data.get(DATA_BURN); }
    public int burnTotal() { return Math.max(1,data.get(DATA_BURN_TOTAL)); }
    public int productionRate() { return data.get(DATA_RATE); }
    public int status() { return Math.clamp(data.get(DATA_STATUS),ACTIVE,NO_ENERGY); }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.BOILER.get().displayState(front()).setValue(MachineLevel.MK, mk()); }

    @Override
    public Set<SideMode> allowedModes() {
        return ((SideConfigurableBlock) ModBlocks.BOILER.get()).allowedSideModes();
    }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.BOILER.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.BOILER.get().supportsAutoPush(); }

    @Override
    public boolean isAutoPulling() { return data.get(DATA_AUTO_BASE) != 0; }

    @Override
    public boolean isAutoPushing() { return data.get(DATA_AUTO_BASE + 1) != 0; }

    @Override
    public SlotRole slotRole(int index) { return index == SLOT_FUEL || index == SLOT_INPUT ? SlotRole.INPUT : index == SLOT_OUTPUT ? SlotRole.OUTPUT : SlotRole.NONE; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(buckets instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(buckets instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return buckets.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START || index >= PLAYER_END) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, PLAYER_END, PLAYER_END + UpgradeInventory.SLOTS, false)) return ItemStack.EMPTY;
        } else if (BoilerBlockEntity.acceptsFuel(fuelMode(), stack)) {
            if (!moveItemStackTo(stack,SLOT_FUEL,SLOT_FUEL+1,false)) return ItemStack.EMPTY;
        } else if (BoilerBlockEntity.isWaterContainer(stack)) {
            if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)) return ItemStack.EMPTY;
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
