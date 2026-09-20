package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.AutoTransfer;
import dev.futuretech.api.side.AutoTransferable;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.LaneMachineBlock;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.LaneMachineMenu;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import dev.futuretech.recipe.LaneMachineRecipe;
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
 * Turns one ingredient into a result with energy, using the kind's recipe type: the crusher and the
 * sawmill. The machine has up to {@link #LANES} lanes, each an input slot paired with an output slot
 * and its own job; the MK level says how many are open, so an MK4 works four things at once and
 * draws energy for each.
 */
public final class LaneMachineBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress on one lane; one generator running flat out feeds one lane. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Five seconds of powered work per item at 20 ticks per second. */
    public static final int WORK_TICKS = 100;
    /** Crusher or sawmill: the recipe book, the block behind the menu and the name. */
    public final LaneMachineKind kind;
    /** Input slots come first, then the outputs, so lane {@code n} is slots {@code n} and {@code LANES + n}. */
    public static final int LANES = 4;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = LANES;
    // Energy is synced as two 16-bit halves; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    /** One progress and one total per lane, then a bit mask of the lanes at work. */
    public static final int DATA_PROGRESS_BASE = 2;
    public static final int DATA_PROGRESS_TOTAL_BASE = DATA_PROGRESS_BASE + LANES;
    public static final int DATA_WORKING = DATA_PROGRESS_TOTAL_BASE + LANES;
    public static final int DATA_PROGRESS = DATA_PROGRESS_BASE;
    public static final int DATA_PROGRESS_TOTAL = DATA_PROGRESS_TOTAL_BASE;
    public static final int DATA_SIDE_BASE = DATA_WORKING + 1;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    private static final int[] NO_SLOTS = {};

    /**
     * How the machine finds the recipe for a stack. The recipe book is all it really needs, so this
     * is a parameter rather than a {@link ServerLevel} field: the world only exists to reach the book.
     */
    @FunctionalInterface
    public interface RecipeLookup {
        @Nullable RecipeHolder<LaneMachineRecipe> find(SingleRecipeInput input);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(2 * LANES, ItemStack.EMPTY);
    private final int[] progress = new int[LANES];
    private final int[] progressTotal = new int[LANES];
    private final ItemStack[] workInput = new ItemStack[LANES];
    private final ItemStack[] workResult = new ItemStack[LANES];
    /** The recipe behind {@link #workResult}; not saved, so a reloaded lane looks it up once more. */
    @SuppressWarnings("unchecked")
    private final @Nullable RecipeHolder<LaneMachineRecipe>[] workRecipe = new RecipeHolder[LANES];
    /** Bit {@code n} set while lane {@code n} advanced this tick. */
    private int workingLanes;
    private final RecipeManager.CachedCheck<SingleRecipeInput, LaneMachineRecipe> quickCheck;
    private final TickLimitedEnergyHandler energy =
            new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::setChanged);
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final RecipeMissMemo misses = new RecipeMissMemo(LANES);
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

    public LaneMachineBlockEntity(LaneMachineKind kind, BlockPos pos, BlockState state) {
        super(kind.blockEntityType(), pos, state);
        this.kind = kind;
        this.quickCheck = RecipeManager.createCheck(kind.recipeType());
        this.sides = ((SideConfigurableBlock) kind.block()).createSideConfig(state);
        Arrays.fill(progressTotal, WORK_TICKS);
        Arrays.fill(workInput, ItemStack.EMPTY);
        Arrays.fill(workResult, ItemStack.EMPTY);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    /** Lanes the MK level has opened: the first {@code n} input slots and their outputs. */
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
        return getBlockState().hasProperty(LaneMachineBlock.FACING)
                ? getBlockState().getValue(LaneMachineBlock.FACING) : Direction.NORTH;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaneMachineBlockEntity machine) {
        machine.beginTick();
        if (machine.auto.isPulling()) machine.transfer.pullFromNeighbours(level, pos, machine, machine.sides);
        if (machine.auto.isPushing()) machine.transfer.pushToNeighbours(level, pos, machine, machine.sides);
        if (machine.auto.isSorting() && (AutoTransfer.balance(machine.items, SLOT_INPUT, machine.lanes()))) machine.setChanged();
        int wasWorking = machine.workingLanes;
        boolean working = machine.redstone.allowsRunning() && level instanceof ServerLevel server
                && machine.work(input -> machine.quickCheck.getRecipeFor(input, server).orElse(null));
        // Pausing preserves the current jobs.
        boolean lit = machine.litHold.update(working);
        if (state.getValue(LaneMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LaneMachineBlock.LIT, lit), 3);
        }
        if (wasWorking != machine.workingLanes) machine.setChanged();
        machine.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(machine.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Advances one tick on every open lane; false when none had anything to do or energy to do it with. */
    boolean work(RecipeLookup recipes) {
        int mk = MachineLevel.of(getBlockState());
        int perTick = upgrades.consumption(ENERGY_PER_TICK, mk);
        int total = upgrades.duration(WORK_TICKS, mk);
        workingLanes = 0;
        for (int lane = 0; lane < lanes(); lane++) {
            if (workLane(lane, recipes, perTick, total)) workingLanes |= 1 << lane;
        }
        return workingLanes != 0;
    }

    private boolean workLane(int lane, RecipeLookup recipes, int perTick, int total) {
        ItemStack ingredient = items.get(SLOT_INPUT + lane);
        if (ingredient.isEmpty()) { resetWork(lane); return false; }
        // Without energy nothing below matters; pausing keeps the job, so the recipe is not touched.
        if (energy.getAmountAsInt() < perTick) return false;
        boolean sameJob = workRecipe[lane] != null && ItemStack.isSameItemSameComponents(workInput[lane], ingredient)
                && (now() + lane) % REVALIDATE_TICKS != 0;
        if (!sameJob) {
            if (misses.known(lane, ingredient, ItemStack.EMPTY, 0, now())) { resetWork(lane); return false; }
            var input = new SingleRecipeInput(ingredient);
            RecipeHolder<LaneMachineRecipe> recipe = recipes.find(input);
            if (recipe == null) { misses.remember(lane, ingredient, ItemStack.EMPTY, 0, now()); resetWork(lane); return false; }
            ItemStack result = recipe.value().assemble(input);
            if (!ItemStack.isSameItemSameComponents(workInput[lane], ingredient)
                    || !ItemStack.matches(workResult[lane], result)) {
                resetWork(lane);
                workInput[lane] = ingredient.copyWithCount(1);
                workResult[lane] = result.copy();
            }
            workRecipe[lane] = recipe;
        }
        ItemStack result = workResult[lane];
        if (result.isEmpty() || !canAccept(lane, result)) return false;
        progressTotal[lane] = total;
        energy.set(energy.getAmountAsInt() - perTick);
        progress[lane]++;
        if (progress[lane] >= progressTotal[lane]) {
            progress[lane] = 0;
            ItemStack output = items.get(SLOT_OUTPUT + lane);
            if (output.isEmpty()) items.set(SLOT_OUTPUT + lane, result.copy());
            else output.grow(result.getCount());
            ingredient.shrink(1);
        }
        setChanged();
        return true;
    }


    /** A lane's resolved recipe is trusted while the same item sits there, and looked up again this often to catch a recipe reload. */
    private static final int REVALIDATE_TICKS = 100;

    /** The clock the miss memo runs on; tests drive a machine with no level. */
    private long now() { return level == null ? 0 : level.getGameTime(); }

    private void resetWork(int lane) {
        if (progress[lane] != 0 || !workInput[lane].isEmpty()) setChanged();
        progress[lane] = 0;
        workInput[lane] = ItemStack.EMPTY;
        workResult[lane] = ItemStack.EMPTY;
        workRecipe[lane] = null;
    }

    /** Whether the lane's output slot has room for one more result. */
    private boolean canAccept(int lane, ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT + lane);
        if (output.isEmpty()) return result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(2 * LANES, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        for (int lane = 0; lane < LANES; lane++) {
            String suffix = lane == 0 ? "" : String.valueOf(lane);
            progressTotal[lane] = Math.max(1, input.getIntOr("ProgressTotal" + suffix, WORK_TICKS));
            progress[lane] = Math.clamp(input.getIntOr("Progress" + suffix, 0), 0, progressTotal[lane]);
            workInput[lane] = input.read("WorkInput" + suffix, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            workResult[lane] = input.read("WorkResult" + suffix, ItemStack.CODEC).orElse(ItemStack.EMPTY);
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
        // Lane 0 keeps the unsuffixed keys older saves used, so their jobs carry over.
        for (int lane = 0; lane < LANES; lane++) {
            String suffix = lane == 0 ? "" : String.valueOf(lane);
            output.putInt("Progress" + suffix, progress[lane]);
            output.putInt("ProgressTotal" + suffix, progressTotal[lane]);
            if (!workInput[lane].isEmpty()) output.store("WorkInput" + suffix, ItemStack.CODEC, workInput[lane]);
            if (!workResult[lane].isEmpty()) output.store("WorkResult" + suffix, ItemStack.CODEC, workResult[lane]);
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
            case INPUT -> laneSlots(SLOT_INPUT, lanes);
            case OUTPUT -> laneSlots(SLOT_OUTPUT, lanes);
            case BOTH -> {
                int[] all = Arrays.copyOf(laneSlots(SLOT_INPUT, lanes), 2 * lanes);
                System.arraycopy(laneSlots(SLOT_OUTPUT, lanes), 0, all, lanes, lanes);
                yield all;
            }
        };
    }

    private static int[] laneSlots(int first, int lanes) {
        int[] slots = new int[lanes];
        for (int lane = 0; lane < lanes; lane++) slots[lane] = first + lane;
        return slots;
    }

    /**
     * Incoming items go to the open lane holding the least of that item, empty lanes included, so
     * a hopper feeding one stack spreads it over every lane instead of piling it onto the first.
     * The machine's own access (a null side) is not steered.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot < SLOT_INPUT || slot >= SLOT_INPUT + lanes() || !sides.allowsItemInput(side)) return false;
        return side == null || slot == preferredLane(stack);
    }

    private int preferredLane(ItemStack stack) {
        int best = -1;
        for (int lane = 0; lane < lanes(); lane++) {
            ItemStack held = items.get(SLOT_INPUT + lane);
            if (!held.isEmpty() && (!ItemStack.isSameItemSameComponents(held, stack) || held.getCount() >= held.getMaxStackSize())) continue;
            if (best < 0 || held.getCount() < items.get(SLOT_INPUT + best).getCount()) best = lane;
        }
        return best;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= SLOT_OUTPUT && slot < SLOT_OUTPUT + lanes() && sides.allowsItemOutput(side);
    }

    /**
     * Anything may go in an open input slot: the recipe lookup needs a server
     * level, and a client-side slot that rejected everything would stop players filling it by hand.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= SLOT_INPUT && slot < SLOT_INPUT + lanes();
    }

    @Override
    public int getContainerSize() { return 2 * LANES; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable(kind.nameKey); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new LaneMachineMenu(kind, id, inventory, this, upgrades, data);
    }
}
