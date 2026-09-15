package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.api.side.AutoTransfer;
import dev.futuretech.api.side.AutoTransferable;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.block.SolidFuelGeneratorBlock;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.transfer.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.neoforged.neoforge.model.data.ModelData;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

public final class SolidFuelGeneratorBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    public static final int GENERATION_PER_TICK = 20;
    public static final int OUTPUT_PER_TICK = 80;
    public static final int BURN_TICKS = 1_600;
    // Energy is synced as two 16-bit halves; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_BURN_REMAINING = 2;
    public static final int DATA_GENERATING = 3;
    public static final int DATA_BURN_TOTAL = 4;
    public static final int DATA_SIDE_BASE = 5;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;
    public static final TagKey<Item> WOODEN_FUELS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("futuretech", "generator_wooden_fuels"));

    private static final int[] NO_SLOTS = {};
    private static final int[] FUEL_SLOT = {0};

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int burnRemaining;
    private int burnTotal = BURN_TICKS;
    private boolean generating;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, 0, OUTPUT_PER_TICK, this::setChanged);
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final EnergyExporter exporter = new EnergyExporter();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::setChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_BURN_REMAINING -> burnRemaining;
                case DATA_GENERATING -> generating ? 1 : 0;
                case DATA_BURN_TOTAL -> burnTotal;
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

    public SolidFuelGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLID_FUEL_GENERATOR.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.SOLID_FUEL_GENERATOR.get()).createSideConfig(state);
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
        return getBlockState().hasProperty(SolidFuelGeneratorBlock.FACING)
                ? getBlockState().getValue(SolidFuelGeneratorBlock.FACING) : Direction.NORTH;
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

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(WOODEN_FUELS));
    }

    public static int burnDuration(ItemStack stack, FuelValues fuelValues) {
        if (!isFuel(stack)) return 0;
        int duration = stack.getBurnTime(RecipeType.SMELTING, fuelValues);
        if (duration > 0) return duration;
        // This generator also burns Nether wood and wooden items without a furnace fuel value.
        if (stack.is(ItemTags.HANGING_SIGNS)) return 800;
        if (stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS)) return 1200;
        if (stack.is(ItemTags.WOODEN_SLABS)) return 150;
        if (stack.is(ItemTags.WOODEN_BUTTONS) || stack.is(ItemTags.SAPLINGS)) return 100;
        if (stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.SIGNS)) return 200;
        return 300;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolidFuelGeneratorBlockEntity generator) {
        int previousSignal = EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy);
        generator.beginTick();
        generator.exportEnergy(level, pos);
        if (generator.auto.isPulling()) generator.transfer.pullFromNeighbours(level, pos, generator, generator.sides);
        // Redstone only gates generation; stored energy still leaves through the output faces.
        generator.redstone.update(level, pos);
        if (generator.redstone.allowsRunning()) generator.generateEnergy(level.fuelValues());
        else generator.generating = false;
        boolean lit = generator.litHold.update(generator.generating);
        if (state.getValue(SolidFuelGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(SolidFuelGeneratorBlock.LIT, lit), 3);
        }
        if (previousSignal != EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy)) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** Opens a new tick's output budget; neighbours pulling energy share it with {@link #exportEnergy}. */
    void beginTick() { energy.beginTick(); }

    private void exportEnergy(Level level, BlockPos pos) {
        exporter.pushToNeighbours(level, pos, energy, sides::allowsEnergyOutput);
    }

    void generateEnergy(FuelValues fuelValues) {
        generating = false;
        // Reserve a whole tick's output before using fuel, including the last few FE.
        if (energy.getCapacityAsInt() - energy.getAmountAsInt() < GENERATION_PER_TICK) return;
        if (burnRemaining == 0) {
            ItemStack fuel = items.getFirst();
            int duration = burnDuration(fuel, fuelValues);
            if (duration == 0) return;
            var remainder = fuel.getCraftingRemainder();
            fuel.shrink(1);
            if (fuel.isEmpty()) items.set(0, remainder == null ? ItemStack.EMPTY : remainder.create());
            burnRemaining = duration;
            burnTotal = duration;
        }
        energy.set(energy.getAmountAsInt() + GENERATION_PER_TICK);
        burnRemaining--;
        generating = true;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        burnTotal = Math.max(1, input.getIntOr("BurnTotal", BURN_TICKS));
        burnRemaining = Math.clamp(input.getIntOr("BurnRemaining", 0), 0, burnTotal);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        generating = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("BurnRemaining", burnRemaining);
        output.putInt("BurnTotal", burnTotal);
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && isFuel(stack); }

    /** Hoppers and the item capability both read the face's resource mode from here. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return sides.allowsItemInput(side) ? FUEL_SLOT : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack) && sides.allowsItemInput(side);
    }

    /** Fuel goes in and is burnt; nothing is ever pulled back out of a generator. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.solid_fuel_generator"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new SolidFuelGeneratorMenu(id, inventory, this, upgrades, data);
    }
}
