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
import dev.futuretech.block.LavaGeneratorBlock;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.LavaGeneratorMenu;
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
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
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
 * Burns lava from an internal tank into energy. The tank is filled by fluid cables through the
 * faces in an input mode, or by lava buckets dropped in the input slot; the emptied bucket comes
 * out of the output slot. Like the solid fuel generator it pushes energy out of every face and
 * the MK level only grows the energy buffer.
 */
public final class LavaGeneratorBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 40_000;
    public static final int GENERATION_PER_TICK = 50;
    public static final int OUTPUT_PER_TICK = 200;
    /** Millibuckets burnt for every tick of generation: one bucket lasts 1.000 ticks. */
    public static final int LAVA_PER_TICK = 1;
    public static final int TANK_CAPACITY = 8_000;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int INVENTORY_SIZE = 2;
    // Energy and lava are synced as two 16-bit halves each; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_LAVA_LOW = 2;
    public static final int DATA_LAVA_HIGH = 3;
    public static final int DATA_GENERATING = 4;
    public static final int DATA_SIDE_BASE = 5;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    /**
     * A bucket that cannot be drained yet (output slot full, no room in the tank) is retried when
     * the slot or the tank changes and every {@value} ticks, not every tick; see the fluid tank.
     */
    public static final int CONTAINER_RETRY_TICKS = 20;

    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    private static final int[] ALL_SLOTS = {SLOT_INPUT, SLOT_OUTPUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private boolean generating;
    private boolean containerDirty = true;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, 0, OUTPUT_PER_TICK, this::markChanged);
    private final FluidStacksResourceHandler lava = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) { return isLava(resource); }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) { markChanged(); }
    };
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final EnergyExporter exporter = new EnergyExporter();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::markChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_LAVA_LOW -> EnergySync.low(lavaAmount());
                case DATA_LAVA_HIGH -> EnergySync.high(lavaAmount());
                case DATA_GENERATING -> generating ? 1 : 0;
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

    public LavaGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAVA_GENERATOR.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.LAVA_GENERATOR.get()).createSideConfig(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
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

    /** An upgrade kit swaps the block state under us; the buffer grows with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
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
        return getBlockState().hasProperty(LavaGeneratorBlock.FACING)
                ? getBlockState().getValue(LavaGeneratorBlock.FACING) : Direction.NORTH;
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
    public ResourceHandler<FluidResource> lava() { return lava; }

    public int lavaAmount() { return lava.getAmountAsInt(0); }

    public ContainerData menuData() { return data; }

    public static boolean isLava(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().is(FluidTags.LAVA);
    }

    private static boolean isLava(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().is(FluidTags.LAVA);
    }

    /** Whether the stack is a bucket (or any fluid container) holding lava. */
    public static boolean isLavaContainer(ItemStack stack) {
        return !stack.isEmpty() && isLava(FluidUtil.getFirstStackContained(stack));
    }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    private void markChanged() { ComparatorNotifier.markChanged(this); }

    /**
     * The container contract's hook: a slot changed under us (hopper, cable, menu click), so the
     * bucket in the input slot is worth trying again. Energy and lava changes go through
     * {@link #markChanged} instead, or a burning generator would retry a stuck bucket every tick.
     */
    @Override
    public void setChanged() {
        containerDirty = true;
        markChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LavaGeneratorBlockEntity generator) {
        generator.beginTick();
        generator.exportEnergy(level, pos);
        if (generator.auto.isPulling()) generator.transfer.pullFromNeighbours(level, pos, generator, generator.sides);
        if (generator.auto.isPushing()) generator.transfer.pushToNeighbours(level, pos, generator, generator.sides);
        if (generator.containerDirty || level.getGameTime() % CONTAINER_RETRY_TICKS == 0) {
            generator.containerDirty = false;
            generator.drainContainer();
        }
        // Redstone only gates generation; stored energy still leaves through the output faces.
        if (generator.redstone.allowsRunning()) generator.generateEnergy();
        else generator.generating = false;
        boolean lit = generator.litHold.update(generator.generating);
        if (state.getValue(LavaGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LavaGeneratorBlock.LIT, lit), 3);
        }
        generator.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy));
    }

    /** Opens a new tick's output budget; neighbours pulling energy share it with {@link #exportEnergy}. */
    void beginTick() { energy.beginTick(); }

    private void exportEnergy(Level level, BlockPos pos) {
        exporter.pushToNeighbours(level, pos, energy, sides::allowsEnergyOutput);
    }

    /** Burns one tick's lava into one tick's energy; waits while the buffer cannot take a whole tick. */
    void generateEnergy() {
        generating = false;
        if (energy.getCapacityAsInt() - energy.getAmountAsInt() < GENERATION_PER_TICK) return;
        int amount = lavaAmount();
        if (amount < LAVA_PER_TICK) return;
        // Our own tank is set directly: a transaction every tick would cost more than the burn.
        FluidResource resource = lava.getResource(0);
        lava.set(0, amount == LAVA_PER_TICK ? FluidResource.EMPTY : resource, amount - LAVA_PER_TICK);
        // Burning one mB at a time, the room for a bucket opens on exactly one tick: retry it then.
        if (TANK_CAPACITY - amount < FluidType.BUCKET_VOLUME && TANK_CAPACITY - amount + LAVA_PER_TICK >= FluidType.BUCKET_VOLUME) {
            containerDirty = true;
        }
        energy.set(energy.getAmountAsInt() + GENERATION_PER_TICK);
        generating = true;
        markChanged();
    }

    /**
     * Empties the container in the input slot into the tank and moves what is left of it (an empty
     * bucket) to the output slot. Nothing moves unless the whole container fits and the output slot
     * can take the emptied item, so a half-drained bucket never appears.
     */
    boolean drainContainer() {
        ItemStack input = items.get(SLOT_INPUT);
        if (!isLavaContainer(input)) return false;
        var working = new ItemStacksResourceHandler(1);
        working.set(0, ItemResource.of(input), 1);
        var container = ItemAccess.forHandlerIndex(working, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        ItemStack emptied;
        try (var transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(container, lava, LavaGeneratorBlockEntity::isLava, Integer.MAX_VALUE, transaction);
            if (moved == 0) return false;
            // A bucket only hands over its whole 1.000 mB; lava left inside means the tank had no room.
            if (!FluidUtil.getFirstStackContained(working.getResource(0).toStack(1)).isEmpty()) return false;
            emptied = working.getResource(0).toStack(working.getAmountAsInt(0));
            if (!emptied.isEmpty() && !canAcceptOutput(emptied)) return false;
            transaction.commit();
        }
        input.shrink(1);
        if (input.isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);
        if (!emptied.isEmpty()) {
            ItemStack output = items.get(SLOT_OUTPUT);
            if (output.isEmpty()) items.set(SLOT_OUTPUT, emptied);
            else output.grow(emptied.getCount());
        }
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
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        var stored = input.read("Lava", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        int amount = isLava(stored) ? Math.clamp(stored.getAmount(), 0, TANK_CAPACITY) : 0;
        lava.set(0, amount == 0 ? FluidResource.EMPTY : FluidResource.of(stored), amount);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        generating = false;
        containerDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("Lava", FluidStack.OPTIONAL_CODEC, FluidUtil.getStack(lava, 0));
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == SLOT_INPUT && isLavaContainer(stack); }

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

    /** Only the emptied bucket ever leaves; lava buckets waiting in the input slot stay put. */
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
    protected Component getDefaultName() { return Component.translatable("block.futuretech.lava_generator"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new LavaGeneratorMenu(id, inventory, this, upgrades, data);
    }
}
