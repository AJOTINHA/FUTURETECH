package dev.futuretech.menu;

import static dev.futuretech.block.entity.SolarGeneratorBlockEntity.*;

import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.SolarGeneratorBlockEntity;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public final class SolarGeneratorMenu extends MachineMenu implements SideConfigMenu, RedstoneControlMenu, dev.futuretech.api.gui.EnergyInfoMenu {
    private final ContainerData data;
    private final @Nullable SolarGeneratorBlockEntity generator;

    /** Width of the solar generator screen. */
    public static final int IMAGE_WIDTH = 176;

    public SolarGeneratorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(DATA_COUNT));
    }

    /** Client data is synchronized by the server menu. */
    private SolarGeneratorMenu(int id, Inventory inventory, ContainerData data) {
        this(id, inventory, null, data);
    }

    public SolarGeneratorMenu(int id, Inventory inventory, @Nullable SolarGeneratorBlockEntity generator, ContainerData data) {
        super(ModMenus.SOLAR_GENERATOR.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.generator = generator;
        addStandardInventorySlots(inventory, 8, 102);
        addDataSlots(data);
        if (generator != null) markSynced();
    }

    public int mk() { return Math.clamp(data.get(DATA_MK), 1, 4); }
    @Override public int energyStored() { return EnergySync.unpack(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH)); }
    @Override public int energyCapacity() { return MachineLevel.capacity(CAPACITY, mk()); }
    @Override public int energyRatePerTick() { return MachineLevel.consumption(GENERATION_PER_TICK, mk()); }
    @Override public int energyUsagePerTick() { return data.get(DATA_RATE); }
    @Override public int energyOutputPerTick() { return OUTPUT_PER_TICK; }
    @Override public boolean isWorking() { return energyUsagePerTick() > 0; }
    @Override public Kind kind() { return Kind.GENERATOR; }
    public int sunlight() { return data.get(DATA_SUN); }
    public int status() { return Math.clamp(data.get(DATA_STATUS), ACTIVE, NO_SKY); }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(DATA_SIDE_BASE + side.ordinal())); }

    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(DATA_FRONT), 0, 5)]; }

    @Override
    public BlockState displayState() {
        return ModBlocks.SOLAR_GENERATOR.get().displayState(front()).setValue(MachineLevel.MK, mk());
    }

    @Override
    public Set<SideMode> allowedModes() { return ModBlocks.SOLAR_GENERATOR.get().allowedSideModes(); }

    // A generator holds no items, so it offers neither toggle.
    @Override
    public boolean supportsAutoPull() { return false; }

    @Override
    public boolean supportsAutoPush() { return false; }

    @Override
    public boolean isAutoPulling() { return false; }

    @Override
    public boolean isAutoPushing() { return false; }

    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_BASE)); }

    @Override
    public boolean isPowered() { return data.get(DATA_REDSTONE_BASE + 1) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(generator, buttonId) || RedstoneControlMenu.handleButton(generator, buttonId);
    }

    @Override
    public boolean stillValid(Player player) {
        // The client-side menu has no block entity and is closed by the server when needed.
        return generator == null || Container.stillValidBlockEntity(generator, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
