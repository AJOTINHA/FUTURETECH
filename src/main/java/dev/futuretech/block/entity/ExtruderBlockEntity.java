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
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.ExtruderBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.ExtruderMenu;
import dev.futuretech.recipe.ExtrudingRecipe;
import dev.futuretech.recipe.ExtrudingRecipes;
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
import net.minecraft.resources.Identifier;
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

import java.util.List;

/**
 * Extrudes two fluids into one item, with energy: water and lava into cobblestone, stone or
 * obsidian. The two tanks fill through fluid cables on the input faces or from a bucket clicked on
 * the block, and what comes out of them lands in the single output slot, which item cables and
 * hoppers empty through the output faces. Every level extrudes faster and doubles both tanks.
 *
 * <p>The same two fluids make several things, so the machine does not search for a recipe: the
 * player picks one on the screen and it makes that until they pick another. The choice is kept by
 * recipe id, and falls back to the first recipe when it is unset or its data pack is gone.
 *
 * <p>Both tanks must hold their fluid whatever is being made, but what a batch drinks is the
 * recipe's business: cobblestone costs neither fluid, stone costs water alone, obsidian costs both.
 *
 * <p>The tanks refuse to hold the same fluid twice: a line of water that filled both of them would
 * leave no room for the other half of any recipe.
 */
public final class ExtruderBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Used until a recipe is known. */
    public static final int EXTRUDE_TICKS = 200;
    /** The MK1 tank; every level above doubles it, see {@link #tankCapacity(int)}. */
    public static final int TANK_CAPACITY = 8_000;
    /** The two halves of a batch; {@link Tanks} pairs them by index, so this is not a free number. */
    public static final int TANKS = 2;
    public static final int TANK_A = 0;
    public static final int TANK_B = 1;
    public static final int SLOT_OUTPUT = 0;
    public static final int INVENTORY_SIZE = 1;
    // Energy and each tank's amount are synced as two 16-bit halves each; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    /** Two slots per tank, low half then high half, in tank order. */
    public static final int DATA_FLUID_BASE = 2;
    public static final int DATA_PROGRESS = DATA_FLUID_BASE + 2 * TANKS;
    public static final int DATA_PROGRESS_TOTAL = DATA_PROGRESS + 1;
    public static final int DATA_WORKING = DATA_PROGRESS_TOTAL + 1;
    /** Which product is chosen, as its place in the ordered recipe list, or -1 with none loaded. */
    public static final int DATA_CHOICE = DATA_WORKING + 1;
    public static final int DATA_SIDE_BASE = DATA_CHOICE + 1;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    private static final int[] NO_SLOTS = {};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    /** How often the chosen recipe is looked up again, to catch a data pack reload. */
    private static final int REVALIDATE_TICKS = 100;

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int progress;
    private int progressTotal = EXTRUDE_TICKS;
    private boolean working;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::markChanged);
    private final Tanks tanks = new Tanks();
    /** What the client was last told each tank holds, for the screen; the server reads the tanks themselves. */
    private final FluidStack[] syncedContents = {FluidStack.EMPTY, FluidStack.EMPTY};

    /** The pair of tanks: any fluid a recipe wants, one per tank, at a capacity that follows the level. */
    private final class Tanks extends FluidStacksResourceHandler {
        Tanks() { super(TANKS, TANK_CAPACITY); }

        /**
         * A fluid already in the other tank is refused here: the two tanks are the two halves of a
         * batch, and one fluid filling both would leave the recipe's other half nowhere to go.
         */
        @Override
        public boolean isValid(int index, FluidResource fluid) {
            FluidResource other = getResource(TANKS - 1 - index);
            return other.isEmpty() || !other.equals(fluid);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            markChanged();
            // The screen draws each fluid by its kind, which only the update packet carries.
            if (previousContents.isEmpty() != getResource(index).isEmpty()) sync();
        }

        /** The tanks grow with the level; the field is what {@code getCapacity} answers. */
        void resize(int capacity) { this.capacity = capacity; }
    }

    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    /** The product the player picked, by recipe id; null until one is chosen or loaded. */
    private @Nullable Identifier choiceId;
    /** That choice resolved against the loaded recipes, with its place in them and what it makes. */
    private @Nullable RecipeHolder<ExtrudingRecipe> chosen;
    private int choiceIndex = -1;
    /** Never put in the slot itself, only copied: the slot must not share this instance. */
    private ItemStack chosenResult = ItemStack.EMPTY;
    private boolean choiceStale = true;
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::upgradesChanged);
    /** The upgrades that swap the product list; the first one installed wins. */
    private static final List<TagKey<Item>> PRODUCT_UPGRADES = List.of(UpgradeInventory.SAND);

    /** An upgrade going in or out may change which recipes are on offer, so the choice is looked at again. */
    private void upgradesChanged() {
        choiceStale = true;
        markChanged();
    }

    /** The product upgrade installed, if any: the tag the recipes on offer must name. */
    public Optional<TagKey<Item>> activeUpgrade() {
        for (TagKey<Item> tag : PRODUCT_UPGRADES) if (upgrades.installed(tag) > 0) return Optional.of(tag);
        return Optional.empty();
    }

    /** Whether the machine, as upgraded now, may make this recipe. */
    public boolean offers(ExtrudingRecipe recipe) { return recipe.upgrade().equals(activeUpgrade()); }
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> progressTotal;
                case DATA_WORKING -> working ? 1 : 0;
                case DATA_CHOICE -> choiceIndex;
                case DATA_FRONT -> front().ordinal();
                case DATA_MK -> MachineLevel.of(getBlockState());
                default -> {
                    if (index >= DATA_FLUID_BASE && index < DATA_PROGRESS) {
                        int amount = tanks.getAmountAsInt((index - DATA_FLUID_BASE) / 2);
                        yield (index - DATA_FLUID_BASE) % 2 == 0 ? EnergySync.low(amount) : EnergySync.high(amount);
                    }
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

    public ExtruderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXTRUDER.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.EXTRUDER.get()).createSideConfig(state);
        resize(state);
    }

    /** Millibuckets one tank holds at level {@code mk}: the MK1 tank doubled for every level above. */
    public static int tankCapacity(int mk) { return TANK_CAPACITY << (Math.clamp(mk, 1, MachineLevel.MAX) - 1); }

    /** The tanks this extruder has now. */
    public int tankCapacity() { return tankCapacity(MachineLevel.of(getBlockState())); }

    private void resize(BlockState state) {
        int mk = MachineLevel.of(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, mk));
        tanks.resize(tankCapacity(mk));
    }

    /** An upgrade kit swaps the block state under us; the buffer and the tanks grow with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resize(state);
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    /** The faces and both tanks' fluids reach the client with the chunk and with every change. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("FluidA", FluidStack.OPTIONAL_CODEC, contents(TANK_A));
        output.store("FluidB", FluidStack.OPTIONAL_CODEC, contents(TANK_B));
        sides.save(output);
        return output.buildResult();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        sides.load(input);
        SideConfigVisuals.refresh(this);
        syncedContents[TANK_A] = input.read("FluidA", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        syncedContents[TANK_B] = input.read("FluidB", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    /** Sends the client what the tanks hold now, for the screen. */
    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** One tank's contents as the server has them. */
    public FluidStack contents(int tank) {
        FluidResource resource = tanks.getResource(tank);
        return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(tanks.getAmountAsInt(tank));
    }

    /** What the screen shows for a tank: the kind the client was last told, at the amount the menu syncs. */
    public FluidStack displayContents(int tank, int amount) {
        if (level == null || !level.isClientSide()) return contents(tank);
        FluidStack synced = syncedContents[tank];
        return synced.isEmpty() ? FluidStack.EMPTY : synced.copyWithAmount(amount);
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
        return getBlockState().hasProperty(ExtruderBlock.FACING)
                ? getBlockState().getValue(ExtruderBlock.FACING) : Direction.NORTH;
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

    /** Both tanks: the machine's own access, and what a bucket clicked on the block talks to. */
    public ResourceHandler<FluidResource> tanks() { return tanks; }

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

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExtruderBlockEntity extruder) {
        extruder.beginTick();
        if (extruder.auto.isPushing()) extruder.transfer.pushToNeighbours(level, pos, extruder, extruder.sides);
        boolean wasWorking = extruder.working;
        extruder.working = extruder.redstone.allowsRunning() && extruder.extrude(recipes(level));
        // Losing power mid-batch keeps most of the progress, the way a cooling furnace does.
        if (!extruder.working) extruder.progress = Mth.clamp(extruder.progress - 2, 0, extruder.progressTotal);
        boolean lit = extruder.litHold.update(extruder.working);
        if (state.getValue(ExtruderBlock.LIT) != lit) level.setBlock(pos, state.setValue(ExtruderBlock.LIT, lit), 3);
        if (wasWorking != extruder.working) extruder.markChanged();
        extruder.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(extruder.energy));
    }

    /** The loaded extrusion recipes, or none at all on a level that has no server behind it. */
    private static List<RecipeHolder<ExtrudingRecipe>> recipes(Level level) {
        return level instanceof ServerLevel server
                ? ExtrudingRecipes.of(server.getServer().getRecipeManager()) : List.of();
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** The clock the choice is revalidated on; tests drive a machine with no level. */
    private long now() { return level == null ? 0 : level.getGameTime(); }

    /** What the screen shows as picked: the choice's place in the ordered recipes, or -1 with none. */
    public int choiceIndex() { return choiceIndex; }

    /**
     * Moves to the next product, or the previous one on a right click, and drops whatever batch was
     * under way - the progress made towards obsidian is not progress towards cobblestone.
     */
    public void cycleChoice(boolean backwards) {
        if (level == null || level.isClientSide() || level.getServer() == null) return;
        cycleChoice(ExtrudingRecipes.of(level.getServer().getRecipeManager()), backwards);
    }

    /** The same step over a given list, which is what a test drives it with; only recipes on offer are stepped through. */
    void cycleChoice(List<RecipeHolder<ExtrudingRecipe>> recipes, boolean backwards) {
        if (recipes.isEmpty()) return;
        resolveChoice(recipes);
        if (chosen == null) return;
        int index = choiceIndex;
        for (int step = 0; step < recipes.size(); step++) {
            index = Math.floorMod(index + (backwards ? -1 : 1), recipes.size());
            if (offers(recipes.get(index).value())) break;
        }
        choose(recipes, index);
        progress = 0;
        markChanged();
    }

    /** Takes the recipe at {@code index} as the chosen one, remembering what it makes. */
    private void choose(List<RecipeHolder<ExtrudingRecipe>> recipes, int index) {
        choiceIndex = index;
        chosen = recipes.get(index);
        choiceId = chosen.id().identifier();
        chosenResult = chosen.value().result().create();
        progressTotal = upgrades.duration(chosen.value().duration(), MachineLevel.of(getBlockState()));
    }

    /**
     * Finds the chosen recipe among the loaded ones; the first one on offer stands in for a choice
     * that is gone, or that the upgrades no longer allow. With nothing on offer at all the machine
     * makes nothing.
     */
    private void resolveChoice(List<RecipeHolder<ExtrudingRecipe>> recipes) {
        choiceStale = false;
        int first = -1;
        for (int index = 0; index < recipes.size(); index++) {
            ExtrudingRecipe recipe = recipes.get(index).value();
            if (!offers(recipe)) continue;
            if (first < 0) first = index;
            if (recipes.get(index).id().identifier().equals(choiceId)) {
                choose(recipes, index);
                return;
            }
        }
        if (first < 0) {
            chosen = null;
            chosenResult = ItemStack.EMPTY;
            choiceIndex = -1;
            return;
        }
        // Nothing picked yet, or the pick left with its data pack or its upgrade: the first on offer stands in.
        choose(recipes, first);
    }

    /** Advances one tick on the chosen product; false without the fluids, the energy or the room for it. */
    boolean extrude(List<RecipeHolder<ExtrudingRecipe>> recipes) {
        if (choiceStale || now() % REVALIDATE_TICKS == 0) resolveChoice(recipes);
        if (chosen == null) return false;
        ExtrudingRecipe recipe = chosen.value();
        FluidResource a = tanks.getResource(TANK_A);
        FluidResource b = tanks.getResource(TANK_B);
        int amountA = tanks.getAmountAsInt(TANK_A);
        int amountB = tanks.getAmountAsInt(TANK_B);
        // Which part each tank pours in: the recipe's own order, or the other way round.
        boolean ordered = recipe.matchesOrdered(a, amountA, b, amountB);
        if (!ordered && !recipe.matchesOrdered(b, amountB, a, amountA)) return false;
        int mk = MachineLevel.of(getBlockState());
        int perTick = upgrades.consumption(ENERGY_PER_TICK, mk);
        if (energy.getAmountAsInt() < perTick) return false;
        if (!hasRoomFor(chosenResult)) return false;
        progressTotal = upgrades.duration(recipe.duration(), mk);
        energy.set(energy.getAmountAsInt() - perTick);
        progress++;
        if (progress >= progressTotal) {
            progress = 0;
            drink(TANK_A, ordered ? recipe.first().consumes() : recipe.second().consumes());
            drink(TANK_B, ordered ? recipe.second().consumes() : recipe.first().consumes());
            ItemStack output = items.get(SLOT_OUTPUT);
            if (output.isEmpty()) items.set(SLOT_OUTPUT, chosenResult.copy());
            else output.grow(chosenResult.getCount());
        }
        markChanged();
        return true;
    }

    /** Takes a batch's worth out of one tank, emptying it when that was all of it. */
    private void drink(int tank, int amount) {
        // A part that costs nothing leaves its tank untouched, rather than rewriting the same amount.
        if (amount <= 0) return;
        int left = tanks.getAmountAsInt(tank) - amount;
        tanks.set(tank, left <= 0 ? FluidResource.EMPTY : tanks.getResource(tank), Math.max(0, left));
    }

    /** Whether the output slot can take the whole result: empty, or the same item with room for it. */
    private boolean hasRoomFor(ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        resize(getBlockState());
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        loadTank(input, "FluidA", TANK_A);
        loadTank(input, "FluidB", TANK_B);
        progressTotal = Math.max(1, input.getIntOr("ProgressTotal", EXTRUDE_TICKS));
        progress = Math.clamp(input.getIntOr("Progress", 0), 0, progressTotal);
        choiceId = Identifier.tryParse(input.getStringOr("Choice", ""));
        choiceStale = true;
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        working = false;
    }

    /** A saved tank, clamped to the capacity this level has in case the machine was downgraded. */
    private void loadTank(ValueInput input, String key, int tank) {
        FluidStack fluid = input.read(key, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        int amount = Math.clamp(fluid.getAmount(), 0, tankCapacity());
        tanks.set(tank, fluid.isEmpty() || amount == 0 ? FluidResource.EMPTY : FluidResource.of(fluid), amount);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("FluidA", FluidStack.OPTIONAL_CODEC, contents(TANK_A));
        output.store("FluidB", FluidStack.OPTIONAL_CODEC, contents(TANK_B));
        output.putInt("Progress", progress);
        output.putInt("ProgressTotal", progressTotal);
        // By id, not by index: the recipes may be numbered differently by the time this is read.
        if (choiceId != null) output.putString("Choice", choiceId.toString());
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    /** Hoppers and the item capability both read the face's mode from here: only the output slot, only on output faces. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return sides.allowsItemOutput(side) ? OUTPUT_SLOTS : NO_SLOTS;
    }

    /** Nothing goes in as an item: what the extruder drinks arrives through the tanks. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT && sides.allowsItemOutput(side);
    }

    /** The one slot is where a batch lands; nothing may be put there by hand either. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return false; }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.extruder"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ExtruderMenu(id, inventory, this, upgrades, data);
    }
}
