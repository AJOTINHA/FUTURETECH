package dev.futuretech.block.entity;

import dev.futuretech.api.facade.CableFacades;
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
import dev.futuretech.block.PaintMachineBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.item.FacadeItem;
import dev.futuretech.menu.PaintMachineMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Makes facades with energy, and is the only thing that does: one block and {@link #PLATES_PER_JOB}
 * steel plates become {@link #YIELD} panels wearing that block, so a cable run can be dressed from
 * a hopper. The machine has up to
 * {@link #LANES} lanes, each a block slot and a plate slot with an output slot and its own job;
 * the MK level says how many are open. There is no recipe book: any block a facade may wear is
 * accepted, which is what {@link CableFacades#isValid} decides.
 */
public final class PaintMachineBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress on one lane; one generator running flat out feeds one lane. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Five seconds of powered work per block at 20 ticks per second. */
    public static final int PAINT_TICKS = 100;
    /** How many panels one block makes: the four sides a cable run usually shows. */
    public static final int YIELD = 4;
    /** Steel plates one job takes: one per panel it gives back. */
    public static final int PLATES_PER_JOB = YIELD;
    /**
     * Block slots come first, then the plate slots, then the outputs, so lane {@code n} is slots
     * {@code n}, {@code LANES + n} and {@code 2 * LANES + n}.
     */
    public static final int LANES = 4;
    public static final int SLOT_BLOCK = 0;
    public static final int SLOT_PLATES = LANES;
    public static final int SLOT_OUTPUT = 2 * LANES;
    public static final int INVENTORY_SIZE = 3 * LANES;
    // Energy is synced as two 16-bit halves; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    /** One progress and one total per lane, then a bit mask of the lanes at work. */
    public static final int DATA_PROGRESS_BASE = 2;
    public static final int DATA_PROGRESS_TOTAL_BASE = DATA_PROGRESS_BASE + LANES;
    public static final int DATA_WORKING = DATA_PROGRESS_TOTAL_BASE + LANES;
    public static final int DATA_SIDE_BASE = DATA_WORKING + 1;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    private static final int[] NO_SLOTS = {};

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final int[] progress = new int[LANES];
    private final int[] progressTotal = new int[LANES];
    /** The block each lane is painting, so a swap mid-job starts the job over rather than finishing the other block. */
    private final ItemStack[] workBlock = new ItemStack[LANES];
    /** Bit {@code n} set while lane {@code n} advanced this tick. */
    private int workingLanes;
    private final TickLimitedEnergyHandler energy =
            new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::setChanged);
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::setChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_WORKING -> workingLanes;
                case DATA_FRONT -> front().ordinal();
                case DATA_MK -> MachineLevel.of(getBlockState());
                default -> {
                    if (index >= DATA_PROGRESS_BASE && index < DATA_PROGRESS_TOTAL_BASE) yield progress[index - DATA_PROGRESS_BASE];
                    if (index >= DATA_PROGRESS_TOTAL_BASE && index < DATA_WORKING) yield progressTotal[index - DATA_PROGRESS_TOTAL_BASE];
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

    public PaintMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PAINT_MACHINE.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.PAINT_MACHINE.get()).createSideConfig(state);
        Arrays.fill(progressTotal, PAINT_TICKS);
        Arrays.fill(workBlock, ItemStack.EMPTY);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    /** The block a stack would put on a facade, or null when the stack is not a block a facade may wear. */
    public static @Nullable BlockState facadeOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem block)) return null;
        BlockState state = block.getBlock().defaultBlockState();
        return CableFacades.isValid(state) ? state : null;
    }

    public static boolean isPlate(ItemStack stack) { return stack.is(ModItems.STEEL_PLATE.get()); }

    /** Lanes the MK level has opened: the first {@code n} slot pairs and their outputs. */
    public int lanes() { return Math.clamp(MachineLevel.of(getBlockState()), 1, LANES); }

    /** An upgrade kit swaps the block state under us; the buffer grows with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    @Override
    public Direction front() {
        return getBlockState().hasProperty(PaintMachineBlock.FACING)
                ? getBlockState().getValue(PaintMachineBlock.FACING) : Direction.NORTH;
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

    public ContainerData menuData() { return data; }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    @Override
    public void setChanged() { ComparatorNotifier.markChanged(this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PaintMachineBlockEntity painter) {
        painter.beginTick();
        if (painter.auto.isPulling()) painter.transfer.pullFromNeighbours(level, pos, painter, painter.sides);
        if (painter.auto.isPushing()) painter.transfer.pushToNeighbours(level, pos, painter, painter.sides);
        if (painter.auto.isSorting() && (AutoTransfer.balance(painter.items, SLOT_BLOCK, painter.lanes())
                | AutoTransfer.balance(painter.items, SLOT_PLATES, painter.lanes()))) painter.setChanged();
        int wasWorking = painter.workingLanes;
        boolean working = painter.redstone.allowsRunning() && painter.paint();
        // Pausing preserves the current jobs.
        boolean lit = painter.litHold.update(working);
        if (state.getValue(PaintMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(PaintMachineBlock.LIT, lit), 3);
        }
        if (wasWorking != painter.workingLanes) painter.setChanged();
        painter.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(painter.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Advances one tick on every open lane; false when none had anything to do or energy to do it with. */
    boolean paint() {
        int mk = MachineLevel.of(getBlockState());
        int perTick = upgrades.consumption(ENERGY_PER_TICK, mk);
        workingLanes = 0;
        for (int lane = 0; lane < lanes(); lane++) {
            if (paintLane(lane, perTick)) workingLanes |= 1 << lane;
        }
        return workingLanes != 0;
    }

    private boolean paintLane(int lane, int perTick) {
        ItemStack block = items.get(SLOT_BLOCK + lane);
        ItemStack plates = items.get(SLOT_PLATES + lane);
        BlockState facade = facadeOf(block);
        if (facade == null || !isPlate(plates) || plates.getCount() < PLATES_PER_JOB) { resetWork(lane); return false; }
        // Without energy nothing below matters; pausing keeps the job.
        if (energy.getAmountAsInt() < perTick) return false;
        // A different block starts over; more of the same keeps the progress.
        if (!ItemStack.isSameItemSameComponents(workBlock[lane], block)) {
            resetWork(lane);
            workBlock[lane] = block.copyWithCount(1);
        }
        ItemStack result = FacadeItem.of(facade);
        result.setCount(YIELD);
        if (!canAccept(lane, result)) return false;
        progressTotal[lane] = upgrades.duration(PAINT_TICKS, MachineLevel.of(getBlockState()));
        energy.set(energy.getAmountAsInt() - perTick);
        progress[lane]++;
        if (progress[lane] >= progressTotal[lane]) {
            progress[lane] = 0;
            ItemStack output = items.get(SLOT_OUTPUT + lane);
            if (output.isEmpty()) items.set(SLOT_OUTPUT + lane, result);
            else output.grow(result.getCount());
            block.shrink(1);
            plates.shrink(PLATES_PER_JOB);
        }
        setChanged();
        return true;
    }

    private void resetWork(int lane) {
        if (progress[lane] != 0 || !workBlock[lane].isEmpty()) setChanged();
        progress[lane] = 0;
        workBlock[lane] = ItemStack.EMPTY;
    }

    /** Whether the lane's output slot has room for one more batch of panels. */
    private boolean canAccept(int lane, ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT + lane);
        if (output.isEmpty()) return result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        for (int lane = 0; lane < LANES; lane++) {
            String suffix = String.valueOf(lane);
            progressTotal[lane] = Math.max(1, input.getIntOr("ProgressTotal" + suffix, PAINT_TICKS));
            progress[lane] = Math.clamp(input.getIntOr("Progress" + suffix, 0), 0, progressTotal[lane]);
            workBlock[lane] = input.read("WorkBlock" + suffix, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        workingLanes = 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        for (int lane = 0; lane < LANES; lane++) {
            String suffix = String.valueOf(lane);
            output.putInt("Progress" + suffix, progress[lane]);
            output.putInt("ProgressTotal" + suffix, progressTotal[lane]);
            if (!workBlock[lane].isEmpty()) output.store("WorkBlock" + suffix, ItemStack.CODEC, workBlock[lane]);
        }
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    /** Hoppers and the item capability both read the face's resource mode from here. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        int lanes = lanes();
        return switch (sides.mode(side)) {
            case NONE -> NO_SLOTS;
            case INPUT -> inputSlots(lanes);
            case OUTPUT -> laneSlots(SLOT_OUTPUT, lanes);
            case BOTH -> {
                int[] all = Arrays.copyOf(inputSlots(lanes), 3 * lanes);
                System.arraycopy(laneSlots(SLOT_OUTPUT, lanes), 0, all, 2 * lanes, lanes);
                yield all;
            }
        };
    }

    private static int[] inputSlots(int lanes) {
        int[] slots = Arrays.copyOf(laneSlots(SLOT_BLOCK, lanes), 2 * lanes);
        System.arraycopy(laneSlots(SLOT_PLATES, lanes), 0, slots, lanes, lanes);
        return slots;
    }

    private static int[] laneSlots(int first, int lanes) {
        int[] slots = new int[lanes];
        for (int lane = 0; lane < lanes; lane++) slots[lane] = first + lane;
        return slots;
    }

    private boolean isBlockSlot(int slot) { return slot >= SLOT_BLOCK && slot < SLOT_BLOCK + lanes(); }

    private boolean isPlateSlot(int slot) { return slot >= SLOT_PLATES && slot < SLOT_PLATES + lanes(); }

    /**
     * Incoming items are steered to one slot of their kind: plates to the plate slot holding the
     * fewest, blocks to the block slot holding the fewest of that block, so a hopper feeding both
     * fills the lanes evenly. The machine's own access (a null side) is not steered.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (!canPlaceItem(slot, stack) || !sides.allowsItemInput(side)) return false;
        return side == null || slot == preferredSlot(stack);
    }

    /** The open slot of the stack's kind holding the least of it, empties included; negative when none is open. */
    public int preferredSlot(ItemStack stack) {
        int first = isPlate(stack) ? SLOT_PLATES : facadeOf(stack) != null ? SLOT_BLOCK : -1;
        if (first < 0) return -1;
        int best = -1;
        for (int slot = first; slot < first + lanes(); slot++) {
            ItemStack held = items.get(slot);
            if (!held.isEmpty() && (!ItemStack.isSameItemSameComponents(held, stack) || held.getCount() >= held.getMaxStackSize())) continue;
            if (best < 0 || held.getCount() < items.get(best).getCount()) best = slot;
        }
        return best;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= SLOT_OUTPUT && slot < SLOT_OUTPUT + lanes() && sides.allowsItemOutput(side);
    }

    /** Only what the job can use goes in: a block a facade may wear on one side, steel plates on the other. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (isBlockSlot(slot)) return facadeOf(stack) != null;
        return isPlateSlot(slot) && isPlate(stack);
    }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.paint_machine"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new PaintMachineMenu(id, inventory, this, upgrades, data);
    }
}
