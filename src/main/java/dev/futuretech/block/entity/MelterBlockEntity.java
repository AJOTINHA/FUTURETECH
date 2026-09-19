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
import dev.futuretech.block.MelterBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.MelterMenu;
import dev.futuretech.recipe.MeltingRecipe;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModRecipes;
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
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Melts an item into a fluid, with energy: a stone into lava, by the melting recipes. One item at
 * a time goes in the slot; what comes out fills the tank, and the tank leaves through fluid cables
 * on the output faces or into a bucket clicked on the block. A tank holding one fluid takes no
 * recipe that makes another until it is empty. Every level melts faster and cheaper, and doubles
 * the tank.
 */
public final class MelterBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress; one generator running flat out feeds one melter. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Used until a recipe is known. */
    public static final int MELT_TICKS = 100;
    /** The MK1 tank; every level above doubles it, see {@link #tankCapacity(int)}. */
    public static final int TANK_CAPACITY = 8_000;
    public static final int SLOT_INPUT = 0;
    public static final int INVENTORY_SIZE = 1;
    // Energy and the tank's amount are synced as two 16-bit halves each; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_FLUID_LOW = 2;
    public static final int DATA_FLUID_HIGH = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_PROGRESS_TOTAL = 5;
    public static final int DATA_WORKING = 6;
    public static final int DATA_SIDE_BASE = 7;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    /** A resolved recipe is trusted while the same item sits there, and looked up again this often to catch a recipe reload. */
    private static final int REVALIDATE_TICKS = 100;

    /**
     * How the melter finds the recipe for a stack. The melting book is all it really needs, so this
     * is a parameter rather than a {@link ServerLevel} field: the world only exists to reach the book.
     */
    @FunctionalInterface
    public interface MeltingLookup {
        @Nullable RecipeHolder<MeltingRecipe> find(SingleRecipeInput input);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int progress;
    private int progressTotal = MELT_TICKS;
    private boolean working;
    private final RecipeManager.CachedCheck<SingleRecipeInput, MeltingRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipes.MELTING.get());
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::markChanged);
    private final Tank tank = new Tank();
    /** What the client was last told the tank holds, for the screen; the server reads the tank itself. */
    private FluidStack syncedContents = FluidStack.EMPTY;

    /** The tank: any fluid a recipe makes, one at a time, and a capacity that follows the level. */
    private final class Tank extends FluidStacksResourceHandler {
        Tank() { super(1, TANK_CAPACITY); }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            markChanged();
            // The screen draws the fluid by its kind, which only the update packet carries.
            if (previousContents.isEmpty() != getResource(0).isEmpty()) sync();
        }

        /** The tank grows with the level; the field is what {@code getCapacity} answers. */
        void resize(int capacity) { this.capacity = capacity; }
    }

    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final RecipeMissMemo misses = new RecipeMissMemo(1);
    /** The recipe resolved for the item in the slot, kept while that item stays; not saved. */
    private @Nullable RecipeHolder<MeltingRecipe> jobRecipe;
    private ItemStack jobInput = ItemStack.EMPTY;
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::markChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_FLUID_LOW -> EnergySync.low(tank.getAmountAsInt(0));
                case DATA_FLUID_HIGH -> EnergySync.high(tank.getAmountAsInt(0));
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> progressTotal;
                case DATA_WORKING -> working ? 1 : 0;
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

    public MelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MELTER.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.MELTER.get()).createSideConfig(state);
        resize(state);
    }

    /** Millibuckets the tank holds at level {@code mk}: the MK1 tank doubled for every level above. */
    public static int tankCapacity(int mk) { return TANK_CAPACITY << (Math.clamp(mk, 1, MachineLevel.MAX) - 1); }

    /** The tank this melter has now. */
    public int tankCapacity() { return tankCapacity(MachineLevel.of(getBlockState())); }

    private void resize(BlockState state) {
        int mk = MachineLevel.of(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, mk));
        tank.resize(tankCapacity(mk));
    }

    /** An upgrade kit swaps the block state under us; the buffer and the tank grow with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resize(state);
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    /** The faces and the tank's fluid reach the client with the chunk and with every change. */
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
        sides.load(input);
        SideConfigVisuals.refresh(this);
        syncedContents = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    /** Sends the client what the tank holds now, for the screen. */
    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The tank's contents as the server has them. */
    public FluidStack contents() {
        FluidResource resource = tank.getResource(0);
        return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(tank.getAmountAsInt(0));
    }

    /** What the screen shows: the kind the client was last told, at the amount the menu syncs. */
    public FluidStack displayContents(int amount) {
        if (level == null || !level.isClientSide()) return contents();
        return syncedContents.isEmpty() ? FluidStack.EMPTY : syncedContents.copyWithAmount(amount);
    }

    @Override
    public AutoTransfer autoTransfer() { return auto; }

    @Override
    public void autoTransferChanged() { markChanged(); }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public void redstoneControlChanged() { markChanged(); }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    @Override
    public Direction front() {
        return getBlockState().hasProperty(MelterBlock.FACING)
                ? getBlockState().getValue(MelterBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void sideConfigChanged() {
        markChanged();
        // Cables and capability caches next to us must see the new face modes.
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public EnergyHandler energy() { return energy; }

    /** The tank itself: the machine's own access, and what a bucket clicked on the block talks to. */
    public ResourceHandler<FluidResource> tank() { return tank; }

    public ContainerData menuData() { return data; }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    @Override
    public void setChanged() { markChanged(); }

    private void markChanged() { ComparatorNotifier.markChanged(this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MelterBlockEntity melter) {
        melter.beginTick();
        if (melter.auto.isPulling()) melter.transfer.pullFromNeighbours(level, pos, melter, melter.sides);
        boolean wasWorking = melter.working;
        melter.working = melter.redstone.allowsRunning() && level instanceof ServerLevel server
                && melter.melt(input -> melter.quickCheck.getRecipeFor(input, server).orElse(null));
        // Losing power mid-item keeps most of the progress, the way a cooling furnace does.
        if (!melter.working) melter.progress = Mth.clamp(melter.progress - 2, 0, melter.progressTotal);
        boolean lit = melter.litHold.update(melter.working);
        if (state.getValue(MelterBlock.LIT) != lit) level.setBlock(pos, state.setValue(MelterBlock.LIT, lit), 3);
        if (wasWorking != melter.working) melter.markChanged();
        melter.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(melter.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** The clock the miss memo runs on; tests drive a machine with no level. */
    private long now() { return level == null ? 0 : level.getGameTime(); }

    /** Advances one tick on the item in the slot; false with nothing to do, no energy, or no room in the tank. */
    boolean melt(MeltingLookup recipes) {
        ItemStack ingredient = items.get(SLOT_INPUT);
        if (ingredient.isEmpty()) return false;
        int mk = MachineLevel.of(getBlockState());
        int perTick = upgrades.consumption(ENERGY_PER_TICK, mk);
        // Without energy nothing below matters, so the recipe book is not touched.
        if (energy.getAmountAsInt() < perTick) return false;
        boolean sameJob = jobRecipe != null && ItemStack.isSameItemSameComponents(jobInput, ingredient)
                && now() % REVALIDATE_TICKS != 0;
        if (!sameJob) {
            if (misses.known(0, ingredient, ItemStack.EMPTY, 0, now())) return false;
            var input = new SingleRecipeInput(ingredient);
            RecipeHolder<MeltingRecipe> found = recipes.find(input);
            if (found == null) { misses.remember(0, ingredient, ItemStack.EMPTY, 0, now()); return false; }
            jobRecipe = found;
            jobInput = ingredient.copyWithCount(1);
        }
        MeltingRecipe recipe = jobRecipe.value();
        FluidStack made = recipe.made();
        if (!hasRoomFor(made)) return false;
        progressTotal = upgrades.duration(recipe.duration(), mk);
        energy.set(energy.getAmountAsInt() - perTick);
        progress++;
        if (progress >= progressTotal) {
            progress = 0;
            tank.set(0, FluidResource.of(made), tank.getAmountAsInt(0) + made.getAmount());
            ingredient.shrink(1);
        }
        markChanged();
        return true;
    }

    /** Whether the tank can take the whole result: empty, or the same fluid with room for all of it. */
    private boolean hasRoomFor(FluidStack made) {
        FluidResource held = tank.getResource(0);
        if (!held.isEmpty() && !held.matches(made)) return false;
        return tank.getAmountAsInt(0) + made.getAmount() <= tankCapacity();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        resize(getBlockState());
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        FluidStack fluid = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        int amount = Math.clamp(fluid.getAmount(), 0, tankCapacity());
        tank.set(0, fluid.isEmpty() || amount == 0 ? FluidResource.EMPTY : FluidResource.of(fluid), amount);
        progressTotal = Math.max(1, input.getIntOr("ProgressTotal", MELT_TICKS));
        progress = Math.clamp(input.getIntOr("Progress", 0), 0, progressTotal);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        working = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, contents());
        output.putInt("Progress", progress);
        output.putInt("ProgressTotal", progressTotal);
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    /** Hoppers and the item capability both read the face's resource mode from here: only the input slot, only on input faces. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return sides.allowsItemInput(side) ? INPUT_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == SLOT_INPUT && sides.allowsItemInput(side);
    }

    /** Nothing leaves as an item: what the melter makes is in the tank. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    /**
     * Anything may go in the slot, as in a vanilla furnace: the recipe lookup needs a server
     * level, and a client-side slot that rejected everything would stop players filling it by hand.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == SLOT_INPUT; }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.melter"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MelterMenu(id, inventory, this, upgrades, data);
    }
}
