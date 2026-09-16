package dev.futuretech.menu;

import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.UpgradeSlots;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

public final class FluidTankMenu extends AbstractContainerMenu implements SideConfigMenu, RedstoneControlMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 210;
    public static final int INPUT_X = 34;
    public static final int OUTPUT_X = 126;
    public static final int SLOT_Y = 61;
    public static final int INVENTORY_Y = 128;
    private static final int PLAYER_START = 2;
    private static final int HOTBAR_START = 29;
    private static final int PLAYER_END = 38;
    private final @Nullable FluidTankBlockEntity tank;
    private final ContainerData data;

    public FluidTankMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, findTank(inventory, extra.readBlockPos()), new SimpleContainer(2),
                new UpgradeInventory(() -> 1, () -> {}), new SimpleContainerData(FluidTankBlockEntity.DATA_COUNT));
    }

    public FluidTankMenu(int id, Inventory inventory, FluidTankBlockEntity tank) {
        this(id, inventory, tank, tank.inventory(), tank.upgrades(), tank.menuData());
    }

    private FluidTankMenu(int id, Inventory inventory, @Nullable FluidTankBlockEntity tank, Container contents,
                          UpgradeInventory upgrades, ContainerData data) {
        super(ModMenus.FLUID_TANK.get(), id);
        this.tank = tank;
        this.data = data;
        addSlot(new Slot(contents, FluidTankBlockEntity.INPUT, INPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return FluidTankBlockEntity.acceptsContainer(stack); }
        });
        addSlot(new Slot(contents, FluidTankBlockEntity.OUTPUT, OUTPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        UpgradeSlots.addSlots(upgrades, WIDTH, this::addSlot);
        addDataSlots(data);
    }

    private static @Nullable FluidTankBlockEntity findTank(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof FluidTankBlockEntity tank ? tank : null;
    }

    public FluidStack fluid() { return tank == null ? FluidStack.EMPTY : tank.displayContents(); }
    public FluidStack visualFluid() { return tank == null ? FluidStack.EMPTY : tank.visualFluid(); }
    public float visualFill(float partialTick) { return tank == null ? 0 : tank.visualFill(partialTick); }

    @Override
    public SideMode sideMode(Direction side) { return SideMode.byOrdinal(data.get(side.ordinal())); }
    @Override
    public Direction front() { return Direction.values()[Math.clamp(data.get(FluidTankBlockEntity.DATA_FRONT), 0, 5)]; }
    @Override
    public BlockState displayState() { return ModBlocks.FLUID_TANK.get().displayState(front()); }
    @Override
    public Set<SideMode> allowedModes() { return ModBlocks.FLUID_TANK.get().allowedSideModes(); }
    @Override
    public boolean supportsAutoPull() { return false; }
    @Override
    public boolean supportsAutoPush() { return false; }
    @Override
    public boolean isAutoPulling() { return false; }
    @Override
    public boolean isAutoPushing() { return false; }
    @Override
    public RedstoneMode redstoneMode() { return RedstoneMode.byOrdinal(data.get(FluidTankBlockEntity.DATA_REDSTONE)); }
    @Override
    public boolean isPowered() { return data.get(FluidTankBlockEntity.DATA_REDSTONE + 1) != 0; }
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return SideConfigMenu.handleButton(tank, buttonId) || RedstoneControlMenu.handleButton(tank, buttonId);
    }

    @Override
    public boolean stillValid(Player player) {
        return tank != null && Container.stillValidBlockEntity(tank, player);
    }

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
        } else if (FluidTankBlockEntity.acceptsContainer(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
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
