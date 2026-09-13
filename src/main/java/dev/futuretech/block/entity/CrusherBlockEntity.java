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
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.CrusherBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.CrusherMenu;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import dev.futuretech.recipe.CrushingRecipe;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/** Crushes ingredients with energy using the dedicated crushing recipe type. */
public final class CrusherBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of progress; one generator running flat out feeds one crusher. */
    public static final int ENERGY_PER_TICK = 20;
    public static final int INPUT_PER_TICK = 200;
    /** Five seconds of powered work per item at 20 ticks per second. */
    public static final int CRUSH_TICKS = 100;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    // Energy is synced as two 16-bit halves; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_PROGRESS_TOTAL = 3;
    public static final int DATA_WORKING = 4;
    public static final int DATA_SIDE_BASE = 5;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    private static final int[] ALL_SLOTS = {SLOT_INPUT, SLOT_OUTPUT};

    /**
     * How the crusher finds the recipe for a stack. The crushing book is all it really needs, so this
     * is a parameter rather than a {@link ServerLevel} field: the world only exists to reach the book.
     */
    @FunctionalInterface
    public interface CrushingLookup {
        @Nullable RecipeHolder<CrushingRecipe> find(SingleRecipeInput input);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;
    private int progressTotal = CRUSH_TICKS;
    private boolean working;
    private ItemStack workInput = ItemStack.EMPTY;
    private ItemStack workResult = ItemStack.EMPTY;
    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> quickCheck =
            RecipeManager.createCheck(ModRecipes.CRUSHING.get());
    private final TickLimitedEnergyHandler energy =
            new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::setChanged);
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final UpgradeInventory upgrades = new UpgradeInventory(this::setChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> progressTotal;
                case DATA_WORKING -> working ? 1 : 0;
                case DATA_FRONT -> front().ordinal();
                default -> {
                    if (index >= DATA_SIDE_BASE && index < DATA_FRONT) yield sides.data(index - DATA_SIDE_BASE);
                    if (index >= DATA_AUTO_BASE && index < DATA_REDSTONE_BASE) yield auto.data(index - DATA_AUTO_BASE);
                    if (index >= DATA_REDSTONE_BASE && index < DATA_COUNT) yield redstone.data(index - DATA_REDSTONE_BASE);
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

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.CRUSHER.get()).createSideConfig(state);
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
        return getBlockState().hasProperty(CrusherBlock.FACING)
                ? getBlockState().getValue(CrusherBlock.FACING) : Direction.NORTH;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity crusher) {
        crusher.beginTick();
        crusher.redstone.update(level, pos);
        if (crusher.auto.isPulling()) ItemTransferUtil.pullFromNeighbours(level, pos, crusher, crusher.sides);
        if (crusher.auto.isPushing()) ItemTransferUtil.pushToNeighbours(level, pos, crusher, crusher.sides);
        boolean wasWorking = crusher.working;
        crusher.working = crusher.redstone.allowsRunning() && level instanceof ServerLevel server
                && crusher.crush(input -> crusher.quickCheck.getRecipeFor(input, server).orElse(null));
        // Pausing preserves the current job.
        if (state.getValue(CrusherBlock.LIT) != crusher.working) {
            level.setBlock(pos, state.setValue(CrusherBlock.LIT, crusher.working), 3);
        }
        if (wasWorking != crusher.working) crusher.setChanged();
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** Advances one tick of crushing; false when there is nothing to do or no energy to do it with. */
    boolean crush(CrushingLookup recipes) {
        ItemStack ingredient = items.get(SLOT_INPUT);
        if (ingredient.isEmpty()) { resetWork(); return false; }
        var input = new SingleRecipeInput(ingredient);
        RecipeHolder<CrushingRecipe> recipe = recipes.find(input);
        if (recipe == null) { resetWork(); return false; }
        ItemStack result = recipe.value().assemble(input);
        if (!ItemStack.isSameItemSameComponents(workInput, ingredient)
                || !ItemStack.matches(workResult, result)) {
            resetWork();
            workInput = ingredient.copyWithCount(1);
            workResult = result.copy();
        }
        if (result.isEmpty() || !canAccept(result)) return false;
        if (energy.getAmountAsInt() < ENERGY_PER_TICK) return false;
        progressTotal = CRUSH_TICKS;
        energy.set(energy.getAmountAsInt() - ENERGY_PER_TICK);
        progress++;
        if (progress >= progressTotal) {
            progress = 0;
            ItemStack output = items.get(SLOT_OUTPUT);
            if (output.isEmpty()) items.set(SLOT_OUTPUT, result.copy());
            else output.grow(result.getCount());
            ingredient.shrink(1);
        }
        setChanged();
        return true;
    }

    private void resetWork() {
        if (progress != 0 || !workInput.isEmpty()) setChanged();
        progress = 0;
        workInput = ItemStack.EMPTY;
        workResult = ItemStack.EMPTY;
    }

    /** Whether the output slot has room for one more result. */
    private boolean canAccept(ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) return result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, CAPACITY));
        progressTotal = Math.max(1, input.getIntOr("ProgressTotal", CRUSH_TICKS));
        progress = Math.clamp(input.getIntOr("Progress", 0), 0, progressTotal);
        workInput = input.read("WorkInput", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        workResult = input.read("WorkResult", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        upgrades.load(input);
        working = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("Progress", progress);
        output.putInt("ProgressTotal", progressTotal);
        if (!workInput.isEmpty()) output.store("WorkInput", ItemStack.CODEC, workInput);
        if (!workResult.isEmpty()) output.store("WorkResult", ItemStack.CODEC, workResult);
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

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
        return slot == SLOT_INPUT && sides.allowsItemInput(side);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT && sides.allowsItemOutput(side);
    }

    /**
     * Anything may go in the input slot: the recipe lookup needs a server
     * level, and a client-side slot that rejected everything would stop players filling it by hand.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT;
    }

    @Override
    public int getContainerSize() { return 2; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.crusher"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new CrusherMenu(id, inventory, this, upgrades, data);
    }
}
