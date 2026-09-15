package dev.futuretech.block.entity;

import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.item.StoredTankFluid;
import dev.futuretech.menu.FluidTankMenu;
import dev.futuretech.block.FluidTankBlock;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SideConfigVisuals;
import net.neoforged.neoforge.model.data.ModelData;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

public final class FluidTankBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 16_000;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int DATA_FRONT = SideConfig.DATA_COUNT;
    public static final int DATA_REDSTONE = DATA_FRONT + 1;
    public static final int DATA_COUNT = DATA_REDSTONE + RedstoneControl.DATA_COUNT;
    private final SideConfig sides;
    private final RedstoneControl redstone = new RedstoneControl();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> 1, this::setChanged);
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < SideConfig.DATA_COUNT) return sides.data(index);
            if (index == DATA_FRONT) return front().ordinal();
            return redstone.data(index - DATA_REDSTONE);
        }
        @Override
        public void set(int index, int value) {}
        @Override
        public int getCount() { return DATA_COUNT; }
    };
    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            FluidTankBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) { return slot == INPUT && acceptsContainer(stack); }
    };
    private final FluidStacksResourceHandler fluids = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            needsSync = true;
        }
    };
    private boolean needsSync;
    /**
     * A filling tank changes every tick; the clients get a fresh level every {@value} ticks and the
     * comparators only when the signal moves, with a change of fluid, an empty or a full tank sent
     * at once. The client eases the drawn level over the same span, so the picture stays smooth.
     */
    public static final int SYNC_TICKS = 5;
    private long lastSyncTick = Long.MIN_VALUE;
    private int lastSyncSignal = -1;
    private FluidResource lastSyncFluid = FluidResource.EMPTY;
    private FluidStack visualFluid = FluidStack.EMPTY;
    private float visualFrom;
    private float visualTarget;
    private long visualTime;
    private boolean visualInitialized;
    private FluidStack syncedContents = FluidStack.EMPTY;

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK.get(), pos, state);
        sides = ((FluidTankBlock)state.getBlock()).createSideConfig(state);
    }

    @Override
    public SideConfig sideConfig() { return sides; }
    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }
    @Override
    public Direction front() { return getBlockState().getValue(FluidTankBlock.FACING); }
    @Override
    public UpgradeInventory upgrades() { return upgrades; }
    @Override
    public RedstoneControl redstoneControl() { return redstone; }
    @Override
    public void redstoneControlChanged() { setChanged(); }
    public ContainerData menuData() { return menuData; }

    @Override
    public void sideConfigChanged() {
        setChanged();
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        needsSync = true;
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    /** Live checks also keep previously cached handlers from bypassing newly closed sides. */
    public @Nullable ResourceHandler<FluidResource> handler(@Nullable Direction side) {
        if (side == null) return fluids;
        if (sides.mode(side) == SideMode.NONE) return null;
        return new ResourceHandler<>() {
            @Override public int size() { return fluids.size(); }
            @Override public FluidResource getResource(int index) { return fluids.getResource(index); }
            @Override public long getAmountAsLong(int index) { return fluids.getAmountAsLong(index); }
            @Override public long getCapacityAsLong(int index, FluidResource fluid) { return fluids.getCapacityAsLong(index, fluid); }
            @Override public boolean isValid(int index, FluidResource fluid) { return fluids.isValid(index, fluid); }
            @Override
            public int insert(int index, FluidResource fluid, int amount, TransactionContext tx) {
                return sides.mode(side).allowsInput() && redstone.allowsRunning() ? fluids.insert(index, fluid, amount, tx) : 0;
            }
            @Override
            public int extract(int index, FluidResource fluid, int amount, TransactionContext tx) {
                return sides.mode(side).allowsOutput() && redstone.allowsRunning() ? fluids.extract(index, fluid, amount, tx) : 0;
            }
        };
    }

    public ResourceHandler<FluidResource> fluids() { return fluids; }
    public FluidStack contents() { return FluidUtil.getStack(fluids, 0); }
    public SimpleContainer inventory() { return inventory; }

    public FluidStack displayContents() {
        return level != null && level.isClientSide() ? syncedContents.copy() : contents();
    }

    public static boolean acceptsContainer(ItemStack stack) {
        return !stack.isEmpty() && ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    /** Converts one input container. A blocked output rolls back the fluid transfer as well. */
    public boolean processContainer() {
        if (!redstone.allowsRunning()) return false;
        ItemStack input = inventory.getItem(INPUT);
        if (!acceptsContainer(input)) return false;
        var working = new ItemStacksResourceHandler(1);
        working.set(0, ItemResource.of(input), 1);
        var container = ItemAccess.forHandlerIndex(working, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        boolean filled = false;
        for (int i = 0; i < container.size(); i++) filled |= container.getAmountAsLong(i) > 0;
        ItemStack result;
        try (var transaction = Transaction.openRoot()) {
            int moved = filled
                    ? ResourceHandlerUtil.move(container, fluids, fluid -> true, Integer.MAX_VALUE, transaction)
                    : ResourceHandlerUtil.move(fluids, container, fluid -> true, Integer.MAX_VALUE, transaction);
            if (moved == 0) return false;
            result = working.getResource(0).toStack(working.getAmountAsInt(0));
            if (result.isEmpty() || !canAcceptOutput(result)) return false;
            transaction.commit();
        }
        // Server ticks and menu clicks run on one thread; the checked output cannot change here.
        inventory.removeItem(INPUT, 1);
        ItemStack output = inventory.getItem(OUTPUT);
        if (output.isEmpty()) inventory.setItem(OUTPUT, result);
        else {
            output.grow(result.getCount());
            inventory.setChanged();
        }
        return true;
    }

    private boolean canAcceptOutput(ItemStack result) {
        ItemStack output = inventory.getItem(OUTPUT);
        int limit = Math.min(inventory.getMaxStackSize(), result.getMaxStackSize());
        return output.isEmpty() ? result.getCount() <= limit
                : ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= limit;
    }

    public Component contentsMessage() {
        var stack = contents();
        return Component.translatable("gui.futuretech.tank.contents",
                stack.isEmpty() ? Component.translatable("gui.futuretech.empty") : stack.getHoverName(),
                stack.getAmount(), CAPACITY);
    }

    public int comparatorSignal() {
        int amount = fluids.getAmountAsInt(0);
        return amount == 0 ? 0 : 1 + (int)(14L * amount / CAPACITY);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidTankBlockEntity tank) {
        tank.redstone.update(level, pos);
        tank.processContainer();
        if (!tank.needsSync) return;
        int amount = tank.fluids.getAmountAsInt(0);
        FluidResource fluid = tank.fluids.getResource(0);
        long now = level.getGameTime();
        boolean urgent = !fluid.equals(tank.lastSyncFluid) || amount == 0 || amount == CAPACITY;
        if (!urgent && tank.lastSyncTick != Long.MIN_VALUE && now - tank.lastSyncTick < SYNC_TICKS) return;
        tank.needsSync = false;
        tank.lastSyncTick = now;
        tank.lastSyncFluid = fluid;
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        int signal = tank.comparatorSignal();
        if (signal != tank.lastSyncSignal) {
            tank.lastSyncSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private void restore(FluidStack stack) {
        int amount = Math.clamp(stack.getAmount(), 0, CAPACITY);
        fluids.set(0, amount == 0 ? FluidResource.EMPTY : FluidResource.of(stack), amount);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        restore(input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY));
        var loaded = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, loaded);
        for (int slot = 0; slot < 2; slot++) inventory.setItem(slot, loaded.get(slot));
        sides.load(input);
        redstone.load(input);
        upgrades.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, contents());
        var items = NonNullList.withSize(2, ItemStack.EMPTY);
        for (int slot = 0; slot < 2; slot++) items.set(slot, inventory.getItem(slot));
        ContainerHelper.saveAllItems(output, items);
        sides.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, contents());
        sides.save(output);
        return output.buildResult();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        int previousModes = SideConfigVisuals.faceModes(sides);
        sides.load(input);
        if (previousModes != SideConfigVisuals.faceModes(sides)) SideConfigVisuals.refresh(this);
        var incoming = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        syncedContents = incoming.copyWithAmount(Math.clamp(incoming.getAmount(), 0, CAPACITY));
        float target = Math.clamp(incoming.getAmount(), 0, CAPACITY) / (float)CAPACITY;
        float current = visualFill(0);
        boolean sameFluid = incoming.isEmpty() || FluidStack.isSameFluidSameComponents(visualFluid, incoming);
        visualFrom = !visualInitialized ? target : sameFluid ? current : 0;
        visualTarget = target;
        visualTime = level == null ? 0 : level.getGameTime();
        visualInitialized = true;
        // Keep the previous texture while the last fluid drains away.
        if (!incoming.isEmpty()) visualFluid = incoming.copy();
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    public FluidStack visualFluid() { return visualFluid.copy(); }

    public float visualFill(float partialTick) {
        if (level == null) return visualTarget;
        float progress = Math.clamp((level.getGameTime() - visualTime + partialTick) / (float) SYNC_TICKS, 0, 1);
        return visualFrom + (visualTarget - visualFrom) * progress;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        restore(components.getOrDefault(ModDataComponents.TANK_FLUID.get(), StoredTankFluid.EMPTY).toStack());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        var stack = contents();
        if (!stack.isEmpty()) components.set(ModDataComponents.TANK_FLUID.get(), StoredTankFluid.of(stack));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) { output.discard("Fluid"); }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, inventory);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FluidTankMenu(id, playerInventory, this);
    }
}
