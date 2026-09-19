package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.AutoTransfer;
import dev.futuretech.api.side.AutoTransferable;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.WaterPumpBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.WaterPumpMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.transfer.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Draws water out of the sources around it into an internal tank, with energy. The pump needs
 * at least {@link #MIN_SOURCES} water sources touching it, the way a pool needs two to refill,
 * and pumps faster the more it has; the sources are never used up. The tank leaves through
 * fluid cables on the faces in an output mode, or fills empty buckets dropped in the input
 * slot, which come out full from the output slot. Every level above MK1 doubles the flow and
 * the tank, for a little more energy a tick.
 */
public final class WaterPumpBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of pumping, whatever the number of sources; higher levels draw a little more. */
    public static final int ENERGY_PER_TICK = 10;
    public static final int INPUT_PER_TICK = 200;
    /** Millibuckets a tick per source touching the pump: with all six, one bucket every 34 ticks. */
    public static final int WATER_PER_SOURCE = 5;
    /** Fewer sources than this and the pump waits; one source alone would drain, as a pool would. */
    public static final int MIN_SOURCES = 2;
    /** The MK1 tank; every level above doubles it, see {@link #tankCapacity(int)}. */
    public static final int TANK_CAPACITY = 8_000;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int INVENTORY_SIZE = 2;
    // Energy and water are synced as two 16-bit halves each; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_WATER_LOW = 2;
    public static final int DATA_WATER_HIGH = 3;
    public static final int DATA_PUMPING = 4;
    public static final int DATA_SOURCES = 5;
    public static final int DATA_SIDE_BASE = 6;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    /**
     * A bucket that cannot be filled yet (output slot full, not enough water) is retried when the
     * slot or the tank changes and every {@value} ticks, not every tick; see the fluid tank.
     */
    public static final int CONTAINER_RETRY_TICKS = 20;
    /** The sources are counted on load and when a neighbour changes; this is the safety net between. */
    public static final int SOURCE_RETRY_TICKS = 100;

    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    private static final int[] ALL_SLOTS = {SLOT_INPUT, SLOT_OUTPUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private boolean pumping;
    private boolean containerDirty = true;
    /** Water sources touching the pump, as last counted. */
    private int sources;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::markChanged);
    private final Tank water = new Tank();

    /** The water tank: only water, and a capacity that follows the level. */
    private final class Tank extends FluidStacksResourceHandler {
        Tank() { super(1, TANK_CAPACITY); }

        @Override
        public boolean isValid(int index, FluidResource resource) { return isWater(resource); }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) { markChanged(); }

        /** The tank grows with the level; the field is what {@code getCapacity} answers. */
        void resize(int capacity) { this.capacity = capacity; }
    }
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::markChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_WATER_LOW -> EnergySync.low(waterAmount());
                case DATA_WATER_HIGH -> EnergySync.high(waterAmount());
                case DATA_PUMPING -> pumping ? 1 : 0;
                case DATA_SOURCES -> sources;
                case DATA_FRONT -> front().ordinal();
                case DATA_MK -> MachineLevel.of(getBlockState());
                default -> {
                    if (index >= DATA_SIDE_BASE && index < DATA_FRONT) yield sides.data(index - DATA_SIDE_BASE);
                    if (index >= DATA_AUTO_BASE && index < DATA_REDSTONE_BASE) yield auto.data(index - DATA_AUTO_BASE);
                    if (index >= DATA_REDSTONE_BASE && index < DATA_MK) yield redstone.data(index - DATA_REDSTONE_BASE);
                    yield 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // The authoritative data is read-only; clients use SimpleContainerData.
        }

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PUMP.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.WATER_PUMP.get()).createSideConfig(state);
        resize(state);
    }

    /** Millibuckets the tank holds at level {@code mk}: the MK1 tank doubled for every level above. */
    public static int tankCapacity(int mk) { return TANK_CAPACITY << (Math.clamp(mk, 1, MachineLevel.MAX) - 1); }

    /** The tank this pump has now. */
    public int tankCapacity() { return tankCapacity(MachineLevel.of(getBlockState())); }

    private void resize(BlockState state) {
        int mk = MachineLevel.of(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, mk));
        water.resize(tankCapacity(mk));
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return SideConfigVisuals.updateTag(sides); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        sides.load(input);
        SideConfigVisuals.refresh(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override
    public AutoTransfer autoTransfer() { return auto; }

    @Override
    public void autoTransferChanged() { setChanged(); }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    /** An upgrade kit swaps the block state under us; the buffer and the tank grow with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resize(state);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    @Override
    public Direction front() {
        return getBlockState().hasProperty(WaterPumpBlock.FACING)
                ? getBlockState().getValue(WaterPumpBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void sideConfigChanged() {
        setChanged();
        // Cables and capability caches next to us must see the new face modes.
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public EnergyHandler energy() { return energy; }

    /** The tank itself: the machine's own access, and what a bucket clicked on the block talks to. */
    public ResourceHandler<FluidResource> water() { return water; }

    public int waterAmount() { return water.getAmountAsInt(0); }

    /** Water sources touching the pump, as last counted. */
    public int sources() { return sources; }

    public ContainerData menuData() { return data; }

    public static boolean isWater(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().isSame(Fluids.WATER);
    }

    /** Whether the stack is a bucket (or any fluid container) with room for water: empty, or already holding some. */
    public static boolean isFillableContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var container = ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        for (int i = 0; i < container.size(); i++) {
            FluidResource held = container.getResource(i);
            if (held.isEmpty() || isWater(held)) return true;
        }
        return false;
    }

    /** Counts the water sources on the six faces; called on load, on neighbour changes and now and then between. */
    public void sampleSources(Level level) {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (level.getFluidState(worldPosition.relative(side)).isSourceOfType(Fluids.WATER)) count++;
        }
        setSources(count);
    }

    /** The count the world gave, or what a test says the world would give. */
    public void setSources(int count) {
        if (count == sources) return;
        sources = count;
        markChanged();
    }

    /** The redstone signal and the sources are sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        RedstoneControl.sample(level, worldPosition);
        if (!level.isClientSide()) sampleSources(level);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    private void markChanged() { ComparatorNotifier.markChanged(this); }

    /**
     * The container contract's hook: a slot changed under us (hopper, cable, menu click), so the
     * bucket in the input slot is worth trying again. Energy and water changes go through
     * {@link #markChanged} instead, or a running pump would retry a stuck bucket every tick.
     */
    @Override
    public void setChanged() {
        containerDirty = true;
        markChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterPumpBlockEntity pump) {
        pump.beginTick();
        if (pump.auto.isPulling()) pump.transfer.pullFromNeighbours(level, pos, pump, pump.sides);
        if (pump.auto.isPushing()) pump.transfer.pushToNeighbours(level, pos, pump, pump.sides);
        if (level.getGameTime() % SOURCE_RETRY_TICKS == 0) pump.sampleSources(level);
        if (pump.containerDirty || level.getGameTime() % CONTAINER_RETRY_TICKS == 0) {
            pump.containerDirty = false;
            pump.fillContainer();
        }
        // Redstone only gates the pumping; stored water still leaves through the output faces.
        if (pump.redstone.allowsRunning()) pump.pump();
        else pump.pumping = false;
        boolean lit = pump.litHold.update(pump.pumping);
        if (state.getValue(WaterPumpBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(WaterPumpBlock.LIT, lit), 3);
        }
        pump.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(pump.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Millibuckets one tick of pumping yields at this level with the sources counted. */
    public int ratePerTick() {
        return sources < MIN_SOURCES ? 0 : ratePerTick(sources, MachineLevel.of(getBlockState()));
    }

    /** The rate for {@code sources} at level {@code mk}: every level above MK1 doubles the flow, as it doubles the tank. */
    public static int ratePerTick(int sources, int mk) {
        return (WATER_PER_SOURCE * sources) << (Math.clamp(mk, 1, MachineLevel.MAX) - 1);
    }

    /** Pumps one tick's water into the tank; waits while the sources are too few, the tank full or the energy short. */
    void pump() {
        pumping = false;
        int rate = ratePerTick();
        if (rate == 0) return;
        int perTick = upgrades.consumption(ENERGY_PER_TICK, MachineLevel.of(getBlockState()));
        if (energy.getAmountAsInt() < perTick) return;
        int amount = waterAmount();
        int capacity = tankCapacity();
        if (amount >= capacity) return;
        // Our own tank is set directly: a transaction every tick would cost more than the pumping.
        int filled = Math.min(capacity, amount + rate);
        water.set(0, FluidResource.of(Fluids.WATER), filled);
        energy.set(energy.getAmountAsInt() - perTick);
        pumping = true;
        // A bucket waits for a whole bucket's worth; the tick that gets there is the one to retry it on.
        if (amount < 1_000 && filled >= 1_000) containerDirty = true;
        markChanged();
    }

    /**
     * Fills the container in the input slot from the tank and moves the filled item to the output
     * slot. Nothing moves unless the container fills whole and the output slot can take it, so a
     * half-filled bucket never appears.
     */
    boolean fillContainer() {
        ItemStack input = items.get(SLOT_INPUT);
        if (!isFillableContainer(input)) return false;
        var working = new ItemStacksResourceHandler(1);
        working.set(0, ItemResource.of(input), 1);
        var container = ItemAccess.forHandlerIndex(working, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        ItemStack filled;
        try (var transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(water, container, WaterPumpBlockEntity::isWater, Integer.MAX_VALUE, transaction);
            if (moved == 0) return false;
            filled = working.getResource(0).toStack(working.getAmountAsInt(0));
            if (filled.isEmpty() || !canAcceptOutput(filled)) return false;
            transaction.commit();
        }
        input.shrink(1);
        if (input.isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) items.set(SLOT_OUTPUT, filled);
        else output.grow(filled.getCount());
        setChanged();
        return true;
    }

    private boolean canAcceptOutput(ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT);
        int limit = Math.min(getMaxStackSize(), result.getMaxStackSize());
        return output.isEmpty() ? result.getCount() <= limit
                : ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= limit;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        resize(getBlockState());
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        int amount = Math.clamp(input.getIntOr("Water", 0), 0, tankCapacity());
        water.set(0, amount == 0 ? FluidResource.EMPTY : FluidResource.of(Fluids.WATER), amount);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        pumping = false;
        containerDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("Water", waterAmount());
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == SLOT_INPUT && isFillableContainer(stack); }

    /** Hoppers and the item capability both read the face's resource mode from here. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (sides.mode(side)) {
            case NONE -> NO_SLOTS;
            case INPUT -> INPUT_SLOTS;
            case OUTPUT -> OUTPUT_SLOTS;
            case BOTH -> ALL_SLOTS;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack) && sides.allowsItemInput(side);
    }

    /** Only the filled bucket ever leaves; empty buckets waiting in the input slot stay put. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT && sides.allowsItemOutput(side);
    }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.water_pump"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new WaterPumpMenu(id, inventory, this, upgrades, data);
    }
}
