package dev.futuretech.menu;

import static dev.futuretech.block.entity.ExtruderBlockEntity.*;

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
import dev.futuretech.block.entity.ExtruderBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The extruder's menu: the output slot, and the numbers the screen shows - energy, progress and how
 * much each tank holds - through data slots. Which fluids those are comes off the block entity the
 * client has, found by the position the opening packet carries, since a fluid is not a number.
 */
public final class ExtruderMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    public static final int IMAGE_WIDTH = 176;
    /** Picking the product, right after the ids the tabs already own: forwards, and backwards. */
    public static final int BUTTON_NEXT_CHOICE = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;
    public static final int BUTTON_PREVIOUS_CHOICE = BUTTON_NEXT_CHOICE + 1;
    /** The slot sits in the middle of the panel, with a tank and an arrow on either side of it. */
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 45;
    public static final int INVENTORY_Y = 102;
    private static final int PLAYER_START = INVENTORY_SIZE;
    private static final int HOTBAR_START = PLAYER_START + 27;
    private static final int PLAYER_END = HOTBAR_START + 9;

    private final Container contents;
    private final ContainerData data;
    private final @Nullable ExtruderBlockEntity extruder;

    public ExtruderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    /** Client side: the upgrade slots lock by the synced MK, so the tab draws them like the server has them. */
    private ExtruderMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
        this(id, inventory, new SimpleContainer(INVENTORY_SIZE),
                new UpgradeInventory(() -> Math.clamp(data.get(DATA_MK), 1, 4), () -> {}), data, findExtruder(inventory, pos));
    }

    public ExtruderMenu(int id, Inventory inventory, ExtruderBlockEntity extruder, UpgradeInventory upgrades, ContainerData data) {
        this(id, inventory, extruder, upgrades, data, extruder);
    }

    private ExtruderMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data,
                      @Nullable ExtruderBlockEntity extruder) {
        super(ModMenus.EXTRUDER.get(), id);
        checkContainerSize(contents, INVENTORY_SIZE);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.data = data;
        this.extruder = extruder;
        addSlot(new Slot(contents, SLOT_OUTPUT, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof ExtruderBlockEntity) markSynced();
    }

    private static @Nullable ExtruderBlockEntity findExtruder(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ExtruderBlockEntity extruder ? extruder : null;
    }

    public int mk() { return Math.clamp(data.get(DATA_MK), 1, 4); }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    @Override
    public int energyRatePerTick() { return MachineLevel.consumption(ENERGY_PER_TICK, mk()); }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.CONSUMER; }

    @Override
    public boolean isWorking() { return data.get(DATA_WORKING) != 0; }

    public int progress() { return data.get(DATA_PROGRESS); }

    public int progressTotal() { return Math.max(1, data.get(DATA_PROGRESS_TOTAL)); }

    /** The chosen product's place in the ordered extrusion recipes, or -1 while none is known. */
    public int choiceIndex() { return data.get(DATA_CHOICE); }

    /** Millibuckets in one tank, unpacked from its pair of data slots. */
    public int fluidStored(int tank) {
        return EnergySync.unpack(data.get(DATA_FLUID_BASE + 2 * tank), data.get(DATA_FLUID_BASE + 2 * tank + 1));
    }

    /** Each tank at this level. */
    public int tankCapacity() { return ExtruderBlockEntity.tankCapacity(mk()); }

    /** What one tank holds, kind and amount, for the screen. */
    public FluidStack fluid(int tank) {
        return extruder == null ? FluidStack.EMPTY : extruder.displayContents(tank, fluidStored(tank));
    }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.EXTRUDER.get().displayState(front()).setValue(MachineLevel.MK, mk()); }

    @Override
    public Set<SideMode> allowedModes() { return ((SideConfigurableBlock) ModBlocks.EXTRUDER.get()).allowedSideModes(); }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.EXTRUDER.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.EXTRUDER.get().supportsAutoPush(); }

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
        if (buttonId == BUTTON_NEXT_CHOICE || buttonId == BUTTON_PREVIOUS_CHOICE) {
            // A crafted packet can reach a client-side menu, which has no machine to tell.
            if (!(contents instanceof ExtruderBlockEntity extruder)) return false;
            extruder.cycleChoice(buttonId == BUTTON_PREVIOUS_CHOICE);
            return true;
        }
        return SideConfigMenu.handleButton(contents instanceof SideConfigurable target ? target : null, buttonId)
                || RedstoneControlMenu.handleButton(contents instanceof RedstoneControllable target ? target : null, buttonId);
    }

    @Override
    public boolean stillValid(Player player) { return contents.stillValid(player); }

    /** Nothing may be moved into the machine but an upgrade: what it drinks arrives through the tanks. */
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
