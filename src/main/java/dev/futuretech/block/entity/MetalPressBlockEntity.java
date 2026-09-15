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
import dev.futuretech.block.MetalPressBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.MetalPressMenu;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import dev.futuretech.recipe.PressingRecipe;
import dev.futuretech.registry.ModRecipes;
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
 * Presses plates or gears with energy using the dedicated pressing recipe type. The machine has up to
 * {@link #LANES} lanes, each an input slot paired with an output slot and its own job; the MK
 * level says how many are open, so an MK4 presses four things at once and draws energy for each.
 */
public final class MetalPressBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress on one lane; one generator running flat out feeds one lane. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Five seconds of powered work per item at 20 ticks per second. */
    public static final int PRESS_TICKS = 100;
    /** Ingots a gear recipe takes; the auto-input keeps batches of this size together on one lane. */
    public static final int GEAR_INGOTS = 2;
    /** Input slots come first, then the outputs, so lane {@code n} is slots {@code n} and {@code LANES + n}. */
    public static final int LANES = 4;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = LANES;
    public static final int SLOT_MOLD = 2 * LANES;
    public static final int INVENTORY_SIZE = SLOT_MOLD + 1;
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
     * How the metal_press finds the recipe for a stack. The pressing book is all it really needs, so this
     * is a parameter rather than a {@link ServerLevel} field: the world only exists to reach the book.
     */
    @FunctionalInterface
    public interface PressingLookup {
        @Nullable RecipeHolder<PressingRecipe> find(PressingRecipe.Input input);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final int[] progress = new int[LANES];
    private final int[] progressTotal = new int[LANES];
    private final ItemStack[] workInput = new ItemStack[LANES];
    private final ItemStack[] workResult = new ItemStack[LANES];
    /** The recipe behind {@link #workResult}; not saved, so a reloaded lane looks it up once more. */
    @SuppressWarnings("unchecked")
    private final @Nullable RecipeHolder<PressingRecipe>[] workRecipe = new RecipeHolder[LANES];
    /** Bit {@code n} set while lane {@code n} advanced this tick. */
    private int workingLanes;
    private final int[] workCount = new int[LANES];
    private final RecipeManager.CachedCheck<PressingRecipe.Input, PressingRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipes.PRESSING.get());
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

    public MetalPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METAL_PRESS.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.METAL_PRESS.get()).createSideConfig(state);
        Arrays.fill(progressTotal, PRESS_TICKS);
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
        return getBlockState().hasProperty(MetalPressBlock.FACING)
                ? getBlockState().getValue(MetalPressBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void sideConfigChanged() {
        setChanged();
        // Cables and capability caches next to us must see the new face modes.
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public static boolean isMold(ItemStack stack) {
        return stack.is(ModItems.PLATE_MOLD.get()) || stack.is(ModItems.GEAR_MOLD.get());
    }

    public boolean gearMode() { return getItem(SLOT_MOLD).is(ModItems.GEAR_MOLD.get()); }

    /** Changing or removing the reusable mold cancels unfinished work without consuming ingots. */
    private void moldChanged() {
        for (int lane = 0; lane < LANES; lane++) resetWork(lane);
        workingLanes = 0;
        setChanged();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean changed = slot == SLOT_MOLD && !ItemStack.isSameItemSameComponents(getItem(slot), stack);
        super.setItem(slot, stack);
        if (changed) moldChanged();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = super.removeItem(slot, count);
        if (slot == SLOT_MOLD && !removed.isEmpty()) moldChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        if (slot == SLOT_MOLD && !removed.isEmpty()) moldChanged();
        return removed;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        moldChanged();
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, MetalPressBlockEntity metal_press) {
        metal_press.beginTick();
        if (metal_press.auto.isPulling()) metal_press.transfer.pullFromNeighbours(level, pos, metal_press, metal_press.sides);
        if (metal_press.auto.isPushing()) metal_press.transfer.pushToNeighbours(level, pos, metal_press, metal_press.sides);
        int wasWorking = metal_press.workingLanes;
        metal_press.workingLanes = 0;
        boolean working = metal_press.redstone.allowsRunning() && level instanceof ServerLevel server
                && metal_press.press(input -> metal_press.quickCheck.getRecipeFor(input, server).orElse(null));
        // Pausing preserves the current jobs.
        boolean lit = metal_press.litHold.update(working);
        if (state.getValue(MetalPressBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(MetalPressBlock.LIT, lit), 3);
        }
        if (wasWorking != metal_press.workingLanes) metal_press.setChanged();
        metal_press.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(metal_press.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Advances one tick on every open lane; false when none had anything to do or energy to do it with. */
    boolean press(PressingLookup recipes) {
        int mk = MachineLevel.of(getBlockState());
        int perTick = MachineLevel.consumption(ENERGY_PER_TICK, mk);
        workingLanes = 0;
        if (!isMold(getItem(SLOT_MOLD))) {
            for (int lane = 0; lane < LANES; lane++) resetWork(lane);
            return false;
        }
        for (int lane = 0; lane < lanes(); lane++) {
            if (pressLane(lane, recipes, perTick)) workingLanes |= 1 << lane;
        }
        return workingLanes != 0;
    }

    private boolean pressLane(int lane, PressingLookup recipes, int perTick) {
        ItemStack ingredient = items.get(SLOT_INPUT + lane);
        if (ingredient.isEmpty()) { resetWork(lane); return false; }
        // Without energy nothing below matters; pausing keeps the job, so the recipe is not touched.
        if (energy.getAmountAsInt() < perTick) return false;
        // The mold change resets the job, so a kept job always belongs to the current mold.
        boolean sameJob = workRecipe[lane] != null && ItemStack.isSameItemSameComponents(workInput[lane], ingredient)
                && ingredient.getCount() >= workCount[lane] && (now() + lane) % REVALIDATE_TICKS != 0;
        if (!sameJob) {
            // The mold is part of the lookup, so a miss is remembered for the mold it was found with.
            int mold = gearMode() ? 1 : 0;
            if (misses.known(lane, ingredient, ItemStack.EMPTY, mold, now())) { resetWork(lane); return false; }
            var input = new PressingRecipe.Input(ingredient, gearMode());
            RecipeHolder<PressingRecipe> recipe = recipes.find(input);
            if (recipe == null || !recipe.value().matches(input, null)) {
                misses.remember(lane, ingredient, ItemStack.EMPTY, mold, now());
                resetWork(lane);
                return false;
            }
            ItemStack result = recipe.value().assemble(input);
            if (!ItemStack.isSameItemSameComponents(workInput[lane], ingredient)
                    || !ItemStack.matches(workResult[lane], result) || workCount[lane] != recipe.value().count()) {
                resetWork(lane);
                workInput[lane] = ingredient.copyWithCount(1);
                workResult[lane] = result.copy();
                workCount[lane] = recipe.value().count();
            }
            workRecipe[lane] = recipe;
        }
        ItemStack result = workResult[lane];
        if (result.isEmpty() || !canAccept(lane, result)) return false;
        progressTotal[lane] = MachineLevel.duration(workRecipe[lane].value().duration(), MachineLevel.of(getBlockState()));
        energy.set(energy.getAmountAsInt() - perTick);
        progress[lane]++;
        if (progress[lane] >= progressTotal[lane]) {
            progress[lane] = 0;
            ItemStack output = items.get(SLOT_OUTPUT + lane);
            if (output.isEmpty()) items.set(SLOT_OUTPUT + lane, result.copy());
            else output.grow(result.getCount());
            ingredient.shrink(workCount[lane]);
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
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        for (int lane = 0; lane < LANES; lane++) {
            String suffix = lane == 0 ? "" : String.valueOf(lane);
            progressTotal[lane] = Math.max(1, input.getIntOr("ProgressTotal" + suffix, PRESS_TICKS));
            progress[lane] = Math.clamp(input.getIntOr("Progress" + suffix, 0), 0, progressTotal[lane]);
            workInput[lane] = input.read("WorkInput" + suffix, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            workCount[lane] = input.getIntOr("WorkCount" + suffix, 0);
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
            output.putInt("WorkCount" + suffix, workCount[lane]);
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
        if (slot < SLOT_INPUT || slot >= SLOT_INPUT + lanes() || isMold(stack) || !sides.allowsItemInput(side)) return false;
        return side == null || slot == preferredLane(stack);
    }

    private int preferredLane(ItemStack stack) {
        int best = -1;
        int partial = -1;
        for (int lane = 0; lane < lanes(); lane++) {
            ItemStack held = items.get(SLOT_INPUT + lane);
            if (!held.isEmpty() && (!ItemStack.isSameItemSameComponents(held, stack) || held.getCount() >= held.getMaxStackSize())) continue;
            // Finish a batch before spreading ingots to the next lane, even when a hopper feeds one at a time.
            if (gearMode() && held.getCount() % GEAR_INGOTS != 0
                    && (partial < 0 || held.getCount() % GEAR_INGOTS > items.get(SLOT_INPUT + partial).getCount() % GEAR_INGOTS)) partial = lane;
            if (best < 0 || held.getCount() < items.get(SLOT_INPUT + best).getCount()) best = lane;
        }
        return partial >= 0 ? partial : best;
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
        if (slot == SLOT_MOLD) return isMold(stack);
        return slot >= SLOT_INPUT && slot < SLOT_INPUT + lanes() && !isMold(stack);
    }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.metal_press"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MetalPressMenu(id, inventory, this, upgrades, data);
    }
}
