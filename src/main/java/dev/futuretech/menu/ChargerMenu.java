package dev.futuretech.menu;

import static dev.futuretech.block.entity.ChargerBlockEntity.*;

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
import dev.futuretech.block.entity.ChargerBlockEntity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.util.Set;

/** The charger's menu: the item to charge on the left, the energy column in the middle, the charged item on the right. */
public final class ChargerMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, EnergyInfoMenu {
    private final Container contents;
    private final ContainerData data;
    private final int inventoryStart;
    private final int inventoryEnd;
    private final int hotbarStart;
    private final int mk;

    public static final int IMAGE_WIDTH = 176;
    public static final int INPUT_X = 44;
    public static final int OUTPUT_X = 116;
    public static final int SLOT_Y = 45;
    public static final int INVENTORY_Y = 102;

    public ChargerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt());
    }

    /** Client side: the upgrade slots lock by the same MK, so the tab draws them right before any sync. */
    private ChargerMenu(int id, Inventory inventory, int mk) {
        this(id, inventory, new SimpleContainer(SLOTS), new UpgradeInventory(() -> mk, () -> {}), new SimpleContainerData(DATA_COUNT), mk);
    }

    public ChargerMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data) {
        this(id, inventory, contents, upgrades, data, data.get(DATA_MK));
    }

    private ChargerMenu(int id, Inventory inventory, Container contents, UpgradeInventory upgrades, ContainerData data, int mk) {
        super(ModMenus.CHARGER.get(), id);
        checkContainerSize(contents, SLOTS);
        checkContainerDataCount(data, DATA_COUNT);
        this.contents = contents;
        this.data = data;
        this.mk = Math.clamp(mk, 1, 4);
        this.inventoryStart = SLOTS;
        this.inventoryEnd = inventoryStart + 36;
        this.hotbarStart = inventoryEnd - 9;
        addSlot(new Slot(contents, SLOT_INPUT, INPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM) != null; }
        });
        addSlot(new Slot(contents, SLOT_OUTPUT, OUTPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, IMAGE_WIDTH, this::addSlot);
        addDataSlots(data);
        if (contents instanceof ChargerBlockEntity) markSynced();
    }

    /** What to write when opening: the MK, which fixes the upgrade tab before any sync. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, ChargerBlockEntity charger) {
        buffer.writeVarInt(MachineLevel.of(charger.getBlockState()));
    }

    public int mk() { return mk; }

    @Override
    public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }

    @Override
    public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }

    @Override
    public int energyRatePerTick() { return MachineLevel.consumption(CHARGE_PER_TICK, mk()); }

    @Override
    public EnergyInfoMenu.Kind kind() { return EnergyInfoMenu.Kind.CONSUMER; }

    @Override
    public boolean isWorking() { return data.get(DATA_WORKING) != 0; }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() { return ModBlocks.CHARGER.get().displayState(front()).setValue(MachineLevel.MK, Math.clamp(data.get(DATA_MK), 1, 4)); }

    @Override
    public Set<SideMode> allowedModes() { return ((SideConfigurableBlock) ModBlocks.CHARGER.get()).allowedSideModes(); }

    @Override
    public boolean supportsAutoPull() { return ModBlocks.CHARGER.get().supportsAutoPull(); }

    @Override
    public boolean supportsAutoPush() { return ModBlocks.CHARGER.get().supportsAutoPush(); }

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
        } else if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)) {
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
