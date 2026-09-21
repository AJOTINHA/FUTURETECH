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
import dev.futuretech.block.LavaPumpBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.LavaPumpMenu;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drains the lava under it into an internal tank, with energy. A pipe drops out of the pump's
 * underside, through air and lava, down to the first solid block; every lava block the pipe passes
 * seeds a search through the pool connected to it, and the pump takes the pool's sources one at a
 * time, the farthest first, so the near ones keep the pool joined to the pipe until the end.
 *
 * <p>A pumped source does not just vanish: stone goes in its place, so the lava around it never
 * starts flowing into the gap and the world has nothing to recalculate. The pool is lost for good,
 * as a lava lake would be, and it sits in the tank as whole buckets. The tank leaves through
 * fluid cables on the faces in an output mode, or fills empty buckets dropped in the input slot,
 * which come out full from the output slot. Every level above MK1 pumps proportionally faster for
 * proportionally more energy a tick, so a bucket costs the same on every level, and doubles the tank.
 */
public final class LavaPumpBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of pumping; higher levels draw proportionally more. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 400;
    /** Ticks a source takes on a plain MK1: a bucket every 2.5 seconds, and about 1 000 FE a bucket at every level. */
    public static final int WORK_TICKS = 50;
    /** Millibuckets one source is: the bucket it would fill. */
    public static final int SOURCE_VOLUME = 1_000;
    /** The MK1 tank; every level above doubles it, see {@link #tankCapacity(int)}. */
    public static final int TANK_CAPACITY = 8_000;
    /** How far from the pump, sideways, the search through the pool reaches. */
    public static final int RANGE = 32;
    /** Lava blocks one search walks at most; a Nether lake is far bigger, and is taken in slices. */
    public static final int SCAN_LIMIT = 8_192;
    /** How often an empty search is tried again: the pool refills, or somebody pours more in. */
    public static final int RESCAN_TICKS = 100;
    /** Ticks per block of pipe going down, or coming back up when the floor rises. */
    public static final int PIPE_TICKS = 3;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int INVENTORY_SIZE = 2;
    // Energy and lava are synced as two 16-bit halves each; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_LAVA_LOW = 2;
    public static final int DATA_LAVA_HIGH = 3;
    public static final int DATA_STATUS = 4;
    /** Sources the last search found and the pump has not taken yet. */
    public static final int DATA_POOL = 5;
    public static final int DATA_SIDE_BASE = 6;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    /**
     * A bucket that cannot be filled yet (output slot full, not enough lava) is retried when the
     * slot or the tank changes and every {@value} ticks, not every tick; see the fluid tank.
     */
    public static final int CONTAINER_RETRY_TICKS = 20;

    private static final String TAG_PIPE = "Pipe";
    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    private static final int[] ALL_SLOTS = {SLOT_INPUT, SLOT_OUTPUT};

    /** What the pump is doing, for the screen; the order is what travels in the data slot. */
    public enum Status {
        OFF("off", true), NO_LAVA("no_lava", true), NO_ENERGY("no_energy", true),
        FULL("full", true), PUMPING("pumping", false);

        private final String key;
        private final boolean problem;

        Status(String key, boolean problem) {
            this.key = "gui.futuretech.lava_pump.status." + key;
            this.problem = problem;
        }

        public String key() { return key; }

        /** Whether the screen shows it in red: anything but pumping. */
        public boolean isProblem() { return problem; }

        public static Status byOrdinal(int ordinal) { return values()[Math.clamp(ordinal, 0, values().length - 1)]; }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private Status status = Status.NO_LAVA;
    private boolean containerDirty = true;
    /** Blocks of pipe hanging under the pump right now; it grows and shrinks a block at a time. */
    private int pipe;
    /** The sources the last search found, nearest first, so the far end is taken off first. */
    private final List<BlockPos> pool = new ArrayList<>();
    /** Ticks already spent on the source at the end of {@link #pool}. */
    private int progress;
    private long nextScan;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::markChanged);
    private final Tank lava = new Tank();

    /** The lava tank: only lava, and a capacity that follows the level. */
    private final class Tank extends FluidStacksResourceHandler {
        Tank() { super(1, TANK_CAPACITY); }

        @Override
        public boolean isValid(int index, FluidResource resource) { return isLava(resource); }

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
                case DATA_LAVA_LOW -> EnergySync.low(lavaAmount());
                case DATA_LAVA_HIGH -> EnergySync.high(lavaAmount());
                case DATA_STATUS -> status.ordinal();
                case DATA_POOL -> Math.min(pool.size(), Short.MAX_VALUE);
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

    public LavaPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAVA_PUMP.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.LAVA_PUMP.get()).createSideConfig(state);
        resize(state);
    }

    /** Millibuckets the tank holds at level {@code mk}: the MK1 tank doubled for every level above. */
    public static int tankCapacity(int mk) { return TANK_CAPACITY << (Math.clamp(mk, 1, MachineLevel.MAX) - 1); }

    /** The tank this pump has now. */
    public int tankCapacity() { return tankCapacity(MachineLevel.of(getBlockState())); }

    private void resize(BlockState state) {
        int mk = MachineLevel.of(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, mk));
        lava.resize(tankCapacity(mk));
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // The faces and the pipe: what the renderer needs and nothing more.
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        sides.save(output);
        output.putInt(TAG_PIPE, pipe);
        return output.buildResult();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        sides.load(input);
        pipe = input.getIntOr(TAG_PIPE, 0);
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
        return getBlockState().hasProperty(LavaPumpBlock.FACING)
                ? getBlockState().getValue(LavaPumpBlock.FACING) : Direction.NORTH;
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

    public Status status() { return status; }

    /** Blocks of pipe under the pump, for the renderer. */
    public int pipe() { return pipe; }

    /** Sources the last search found and the pump has not taken yet. */
    public int poolSize() { return pool.size(); }

    public ContainerData menuData() { return data; }

    public static boolean isLava(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().isSame(Fluids.LAVA);
    }

    /** Whether the stack is a bucket (or any fluid container) with room for lava: empty, or already holding some. */
    public static boolean isFillableContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var container = ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        for (int i = 0; i < container.size(); i++) {
            FluidResource held = container.getResource(i);
            if (held.isEmpty() || isLava(held)) return true;
        }
        return false;
    }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    private void markChanged() { ComparatorNotifier.markChanged(this); }

    private void sendToClients() {
        if (level == null || level.isClientSide()) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * The container contract's hook: a slot changed under us (hopper, cable, menu click), so the
     * bucket in the input slot is worth trying again. Energy and lava changes go through
     * {@link #markChanged} instead, or a running pump would retry a stuck bucket every tick.
     */
    @Override
    public void setChanged() {
        containerDirty = true;
        markChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LavaPumpBlockEntity pump) {
        pump.beginTick();
        if (pump.auto.isPulling()) pump.transfer.pullFromNeighbours(level, pos, pump, pump.sides);
        if (pump.auto.isPushing()) pump.transfer.pushToNeighbours(level, pos, pump, pump.sides);
        if (pump.containerDirty || level.getGameTime() % CONTAINER_RETRY_TICKS == 0) {
            pump.containerDirty = false;
            pump.fillContainer();
        }
        if (level instanceof ServerLevel server) {
            if (level.getGameTime() % PIPE_TICKS == 0) pump.lowerPipe(server);
            // Redstone only gates the pumping; stored lava still leaves through the output faces.
            if (pump.redstone.allowsRunning()) pump.pump(server);
            else pump.status = Status.OFF;
        }
        boolean lit = pump.litHold.update(pump.status == Status.PUMPING);
        if (state.getValue(LavaPumpBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LavaPumpBlock.LIT, lit), Block.UPDATE_ALL);
        }
        pump.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(pump.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Ticks a source takes at this level with the upgrades installed. */
    public int workTicks() { return upgrades.duration(WORK_TICKS, MachineLevel.of(getBlockState())); }

    /** Energy a tick of pumping draws at this level with the upgrades installed. */
    public int consumptionPerTick() { return upgrades.consumption(ENERGY_PER_TICK, MachineLevel.of(getBlockState())); }

    /** Millibuckets a tick the pump averages while it has lava: a source's volume over the ticks it takes. */
    public int ratePerTick() { return SOURCE_VOLUME / workTicks(); }

    /** The same, for the screen: at level {@code mk} with no upgrades installed. */
    public static int ratePerTick(int mk) { return SOURCE_VOLUME / MachineLevel.duration(WORK_TICKS, mk); }

    /**
     * Blocks of pipe there is room for: from the pump's underside, through air and lava, to the
     * first block that is neither. The pipe goes through lava rather than stopping at its surface,
     * so it stands on the floor of the pool.
     */
    int pipeRoom(ServerLevel level) {
        int room = 0;
        var cursor = worldPosition.mutable();
        while (true) {
            cursor.move(Direction.DOWN);
            if (cursor.getY() < level.getMinY() || !level.hasChunkAt(cursor)) return room;
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && !state.getFluidState().is(Fluids.LAVA)) return room;
            room++;
        }
    }

    /** One block of pipe goes down, or comes up when stone rose under it; the clients see each step. */
    void lowerPipe(ServerLevel level) {
        int room = pipeRoom(level);
        if (room == pipe) return;
        pipe += room > pipe ? 1 : -1;
        setChanged();
        sendToClients();
    }

    /**
     * Walks the pool from every lava block the pipe passes: through lava, six ways, out to
     * {@link #RANGE} sideways and never above the pump. The sources are kept nearest first, so
     * the far end of the pool is taken before the lava that joins the rest of it to the pipe.
     */
    void scan(ServerLevel level) {
        pool.clear();
        progress = 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        int room = pipeRoom(level);
        for (int depth = 1; depth <= room; depth++) {
            BlockPos pos = worldPosition.below(depth);
            if (level.getFluidState(pos).is(Fluids.LAVA) && seen.add(pos)) queue.add(pos);
        }
        while (!queue.isEmpty() && seen.size() < SCAN_LIMIT) {
            BlockPos pos = queue.poll();
            if (level.getFluidState(pos).isSourceOfType(Fluids.LAVA)) pool.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (next.getY() >= worldPosition.getY() || next.getY() < level.getMinY()) continue;
                if (Math.abs(next.getX() - worldPosition.getX()) > RANGE
                        || Math.abs(next.getZ() - worldPosition.getZ()) > RANGE) continue;
                if (!level.hasChunkAt(next) || !level.getFluidState(next).is(Fluids.LAVA)) continue;
                if (seen.add(next)) queue.add(next);
            }
        }
        nextScan = level.getGameTime() + RESCAN_TICKS;
    }

    /** The source being worked on: the last of the pool still standing, dropping any that are gone. */
    private @Nullable BlockPos nextSource(ServerLevel level) {
        while (!pool.isEmpty()) {
            BlockPos pos = pool.getLast();
            if (level.getFluidState(pos).isSourceOfType(Fluids.LAVA)) return pos;
            pool.removeLast();
            progress = 0;
        }
        return null;
    }

    /**
     * One tick of pumping: works on the far end of the pool and, when the ticks are paid, turns
     * that source to stone and puts its bucket in the tank. Waits while there is no lava, no
     * energy or no room for a whole source.
     */
    void pump(ServerLevel level) {
        if (pool.isEmpty() && level.getGameTime() >= nextScan) scan(level);
        BlockPos source = nextSource(level);
        if (source == null) {
            status = Status.NO_LAVA;
            return;
        }
        if (lavaAmount() + SOURCE_VOLUME > tankCapacity()) {
            status = Status.FULL;
            return;
        }
        int perTick = consumptionPerTick();
        if (energy.getAmountAsInt() < perTick) {
            status = Status.NO_ENERGY;
            return;
        }
        energy.set(energy.getAmountAsInt() - perTick);
        progress++;
        status = Status.PUMPING;
        if (progress >= workTicks()) {
            // Stone in the gap: the lava beside it never gets a hole to flow into.
            level.setBlock(source, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            pool.removeLast();
            progress = 0;
            int amount = lavaAmount();
            // Our own tank is set directly: a transaction every source would cost more than the pumping.
            lava.set(0, FluidResource.of(Fluids.LAVA), amount + SOURCE_VOLUME);
            // A bucket waits for a whole bucket's worth; the tick that gets there is the one to retry it on.
            if (amount < SOURCE_VOLUME) containerDirty = true;
        }
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
            int moved = ResourceHandlerUtil.move(lava, container, LavaPumpBlockEntity::isLava, Integer.MAX_VALUE, transaction);
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
        int amount = Math.clamp(input.getIntOr("Lava", 0), 0, tankCapacity());
        lava.set(0, amount == 0 ? FluidResource.EMPTY : FluidResource.of(Fluids.LAVA), amount);
        pipe = Math.max(0, input.getIntOr(TAG_PIPE, 0));
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        // The pool is searched again; only whole sources are ever paid for.
        pool.clear();
        progress = 0;
        nextScan = 0;
        status = Status.NO_LAVA;
        containerDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("Lava", lavaAmount());
        output.putInt(TAG_PIPE, pipe);
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
    protected Component getDefaultName() { return Component.translatable("block.futuretech.lava_pump"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new LavaPumpMenu(id, inventory, this, upgrades, data);
    }
}
