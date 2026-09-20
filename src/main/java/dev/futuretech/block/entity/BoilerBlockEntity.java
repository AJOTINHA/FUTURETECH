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
import dev.futuretech.block.BoilerBlock;
import dev.futuretech.api.side.SidedEnergy;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import dev.futuretech.menu.BoilerMenu;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Converts water and heat into steam without producing electricity. The heat comes from solid
 * fuel, or, with the matching upgrade installed, from lava or from FE. Each mB of water costs one
 * unit of heat at MK1; every level and every efficiency upgrade takes a slice off that bill.
 */
public final class BoilerBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int WATER_CAPACITY = 8_000, STEAM_CAPACITY = 16_000, LAVA_CAPACITY = 8_000;
    public static final int WATER_PER_TICK = 6, STEAM_PER_WATER = 10;
    /** One mB of lava carries this much heat: a bucket matches the lava generator's 50 000 FE, times the turbine's tenfold. */
    public static final int HEAT_PER_LAVA_MB = 5;
    /** FE per unit of heat: dearer than the turbine gives back per unit even with every upgrade, so steam never pays for itself. */
    public static final int FE_PER_HEAT = 300;
    /** Heat bought at a time from the energy buffer, so the heat bar has something to show. */
    public static final int ENERGY_HEAT_BATCH = 10;
    public static final int ENERGY_CAPACITY = 20_000, ENERGY_INPUT_PER_TICK = 2_000;
    /** Heat per mB of water at each level, in percent: every kit takes a tenth off the fuel bill. */
    public static final int[] MK_HEAT_PERCENT = {100, 90, 80, 70};
    /** Where the heat comes from: the fuel slot, the lava tank or the energy buffer. */
    public static final int SOLID = 0, LAVA = 1, ENERGY = 2;
    public static final int SLOT_FUEL = 0, SLOT_INPUT = 1, SLOT_OUTPUT = 2, INVENTORY_SIZE = 3;
    public static final int DATA_WATER = 0, DATA_STEAM_LOW = 1, DATA_STEAM_HIGH = 2,
            DATA_BURN = 3, DATA_BURN_TOTAL = 4, DATA_RATE = 5, DATA_STATUS = 6, DATA_SIDE_BASE = 7;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_MODE = DATA_MK + 1, DATA_RESERVE = DATA_MK + 2, DATA_HEAT_PERCENT = DATA_MK + 3,
            DATA_MAX_WATER = DATA_MK + 4;
    public static final int DATA_COUNT = DATA_MK + 5;
    public static final int ACTIVE = 0, NO_WATER = 1, NO_FUEL = 2, FULL = 3, DISABLED = 4, NO_LAVA = 5, NO_ENERGY = 6;
    public static final int CONTAINER_RETRY_TICKS = 20;

    private static final int[] NO_SLOTS = {};
    private static final int[] INPUT_SLOTS = {SLOT_FUEL, SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT};
    private static final int[] ALL_SLOTS = {SLOT_FUEL, SLOT_INPUT, SLOT_OUTPUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private boolean generating;
    private int burnRemaining, burnTotal = 1600, produced, status = NO_WATER;
    /** Hundredths of the current heat unit already spent, so a 70% bill still adds up exactly. */
    private int heatDebt;
    private boolean containerDirty = true;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(ENERGY_CAPACITY, ENERGY_INPUT_PER_TICK, 0, this::markChanged);
    private final FluidStacksResourceHandler tanks = new FluidStacksResourceHandler(3, STEAM_CAPACITY) {
        @Override public boolean isValid(int index, FluidResource resource) {
            return switch (index) {
                case 0 -> isWater(resource);
                case 1 -> isSteam(resource);
                default -> isLava(resource) && fuelMode() == LAVA;
            };
        }
        @Override protected int getCapacity(int index, FluidResource resource) {
            return switch (index) { case 0 -> WATER_CAPACITY; case 1 -> STEAM_CAPACITY; default -> LAVA_CAPACITY; };
        }
        @Override protected void onContentsChanged(int index, FluidStack previousContents) {
            containerDirty = true;
            markChanged();
        }
    };
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_WATER -> waterAmount();
                case DATA_STEAM_LOW -> EnergySync.low(steamAmount());
                case DATA_STEAM_HIGH -> EnergySync.high(steamAmount());
                case DATA_BURN -> burnRemaining;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_RATE -> produced;
                case DATA_STATUS -> status;
                case DATA_FRONT -> front().ordinal();
                case DATA_MK -> MachineLevel.of(getBlockState());
                case DATA_MODE -> fuelMode();
                case DATA_RESERVE -> fuelMode() == LAVA ? lavaAmount() : fuelMode() == ENERGY ? energy.getAmountAsInt() : 0;
                case DATA_HEAT_PERCENT -> heatPercent();
                case DATA_MAX_WATER -> waterPerTick();
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

    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::upgradesChanged);

    private int lastMode = SOLID;

    /**
     * A lava or energy upgrade going in or out changes what the faces offer: cached handlers must
     * be dropped, and the cables beside us must look again, as they would after a face changed.
     */
    private void upgradesChanged() {
        markChanged();
        int mode = fuelMode();
        if (mode == lastMode || level == null) return;
        lastMode = mode;
        invalidateCapabilities();
        getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    /** Lava wins over energy when both upgrades sit in the slots; without either, the fuel slot heats the water. */
    public int fuelMode() {
        if (upgrades.installed(UpgradeInventory.LAVA) > 0) return LAVA;
        return upgrades.installed(UpgradeInventory.ENERGY) > 0 ? ENERGY : SOLID;
    }

    /**
     * Heat per mB of water in percent: the level's share, a tenth more for every speed upgrade,
     * less what the efficiency upgrades take off.
     */
    public int heatPercent() {
        int level = MK_HEAT_PERCENT[Math.clamp(MachineLevel.of(getBlockState()), 1, MK_HEAT_PERCENT.length) - 1];
        int speed = 100 + UpgradeInventory.SPEED_ENERGY_PERCENT * upgrades.installed(UpgradeInventory.SPEED);
        int efficiency = Math.max(0, 100 - UpgradeInventory.EFFICIENCY_PERCENT * upgrades.installed(UpgradeInventory.EFFICIENCY));
        return Math.max(1, level * speed / 100 * efficiency / 100);
    }

    /** mB of water boiled a tick at full steam: the level's rate, once more for every speed upgrade. */
    public int waterPerTick() {
        return MachineLevel.consumption(WATER_PER_TICK, MachineLevel.of(getBlockState())) * (1 + upgrades.installed(UpgradeInventory.SPEED));
    }

    public EnergyHandler energy() { return energy; }

    /** The energy buffer is only on offer while the energy upgrade is installed, and then only to input faces. */
    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        if (fuelMode() != ENERGY) return null;
        return SidedEnergy.view(energy, sides, side);
    }

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.BOILER.get()).createSideConfig(state);
    }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
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
    public void redstoneControlChanged() { setChanged(); }

    @Override
    public Direction front() {
        return getBlockState().hasProperty(BoilerBlock.FACING)
                ? getBlockState().getValue(BoilerBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void sideConfigChanged() {
        setChanged();
        // Cables and capability caches next to us must see the new face modes.
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public ResourceHandler<FluidResource> tanks() { return tanks; }
    public int waterAmount() { return tanks.getAmountAsInt(0); }
    public int steamAmount() { return tanks.getAmountAsInt(1); }
    public int lavaAmount() { return tanks.getAmountAsInt(2); }
    public ContainerData menuData() { return data; }
    public int comparatorSignal() { return steamAmount() == 0 ? 0 : 1 + 14 * steamAmount() / STEAM_CAPACITY; }
    public static boolean isWater(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
    }
    public static boolean isSteam(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid() == dev.futuretech.registry.ModFluids.STEAM.get();
    }
    public static boolean isLava(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().isSame(net.minecraft.world.level.material.Fluids.LAVA);
    }
    public static boolean isWaterContainer(ItemStack stack) {
        return !stack.isEmpty() && isWater(FluidResource.of(FluidUtil.getFirstStackContained(stack)));
    }
    public static boolean isLavaContainer(ItemStack stack) {
        return !stack.isEmpty() && isLava(FluidResource.of(FluidUtil.getFirstStackContained(stack)));
    }
    /** The fuel slot only takes burnable items, and only while it is the heat source; lava and FE come through the faces. */
    public static boolean acceptsFuel(int mode, ItemStack stack) {
        return mode == SOLID && SolidFuelGeneratorBlockEntity.isFuel(stack);
    }
    /** Water enters tank 0 and lava tank 2; only steam leaves tank 1. Face changes also affect cached handlers. */
    public @Nullable ResourceHandler<FluidResource> handler(@Nullable Direction side) {
        if (side != null && sides.mode(side) == dev.futuretech.api.side.SideMode.NONE) return null;
        return new ResourceHandler<>() {
            @Override public int size() { return 3; }
            @Override public FluidResource getResource(int index) { return tanks.getResource(index); }
            @Override public long getAmountAsLong(int index) { return tanks.getAmountAsLong(index); }
            @Override public long getCapacityAsLong(int index, FluidResource resource) { return tanks.getCapacityAsLong(index,resource); }
            @Override public boolean isValid(int index, FluidResource resource) { return tanks.isValid(index,resource); }
            @Override public int insert(int index, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return index != 1 && sides.allowsItemInput(side) ? tanks.insert(index,resource,amount,tx) : 0;
            }
            @Override public int extract(int index, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                return index == 1 && sides.allowsItemOutput(side) ? tanks.extract(index,resource,amount,tx) : 0;
            }
        };
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity generator) {
        generator.energy.beginTick();
        if (level.getGameTime() % 4 == 0) generator.exportSteam(level, pos);
        if (generator.auto.isPulling()) generator.transfer.pullFromNeighbours(level, pos, generator, generator.sides);
        if (generator.auto.isPushing()) generator.transfer.pushToNeighbours(level, pos, generator, generator.sides);
        if (generator.containerDirty || level.getGameTime() % CONTAINER_RETRY_TICKS == 0) {
            generator.containerDirty = false;
            generator.drainContainer();
        }
        // Redstone pauses boiling; stored steam can still leave through output faces.
        generator.boil(level.fuelValues());
        boolean lit = generator.litHold.update(generator.generating);
        if (state.getValue(BoilerBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, lit), 3);
        }
        generator.comparator.update(level, pos, state, generator.comparatorSignal());
    }

    private void exportSteam(Level level, BlockPos pos) {
        if (steamAmount() == 0) return;
        int remaining = 1000;
        for (Direction side : Direction.values()) {
            if (!sides.mode(side).allowsOutput() || !level.hasChunkAt(pos.relative(side))) continue;
            var destination = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
            if (destination == null) continue;
            try (var tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.move(tanks,destination,BoilerBlockEntity::isSteam,remaining,tx);
                tx.commit();
                remaining -= moved;
            }
            if (remaining == 0 || steamAmount() == 0) break;
        }
    }

    /**
     * Turns water into steam at the level's rate, paying for the heat as it goes. The bill is kept
     * in hundredths so a 70% level and the efficiency upgrades add up exactly, and heat is only
     * bought (a fuel item lit, a mB of lava burnt, a batch of FE drawn) when the bill needs it.
     */
    void boil(net.minecraft.world.level.block.entity.FuelValues fuels) {
        generating = false;
        produced = 0;
        if (!redstone.allowsRunning()) { status = DISABLED; return; }
        if (waterAmount() == 0) { status = NO_WATER; return; }
        int room = (STEAM_CAPACITY - steamAmount()) / STEAM_PER_WATER;
        if (room == 0) { status = FULL; return; }
        int percent = heatPercent();
        int wanted = Math.min(Math.min(waterAmount(), room), waterPerTick());
        int cost = wanted * percent;
        if (heatAvailable() < cost) buyHeat(fuels, cost);
        int waterUsed = Math.min(wanted, heatAvailable() / percent);
        if (waterUsed == 0) {
            status = switch (fuelMode()) { case LAVA -> NO_LAVA; case ENERGY -> NO_ENERGY; default -> NO_FUEL; };
            return;
        }
        tanks.set(0, tanks.getResource(0), waterAmount() - waterUsed);
        produced = waterUsed * STEAM_PER_WATER;
        tanks.set(1, FluidResource.of(dev.futuretech.registry.ModFluids.STEAM.get()), steamAmount() + produced);
        int debt = heatDebt + waterUsed * percent;
        burnRemaining -= debt / 100;
        heatDebt = debt % 100;
        generating = true;
        status = ACTIVE;
        markChanged();
    }

    /** Heat still unspent, in hundredths of a unit. */
    private int heatAvailable() { return burnRemaining * 100 - heatDebt; }

    /** Buys heat from wherever the mode says until {@code cost} hundredths are covered or the source runs dry. */
    private void buyHeat(net.minecraft.world.level.block.entity.FuelValues fuels, int cost) {
        switch (fuelMode()) {
            case LAVA -> {
                while (heatAvailable() < cost && lavaAmount() > 0) {
                    tanks.set(2, tanks.getResource(2), lavaAmount() - 1);
                    burnRemaining += HEAT_PER_LAVA_MB;
                    burnTotal = HEAT_PER_LAVA_MB;
                }
            }
            case ENERGY -> {
                int units = Math.min(ENERGY_HEAT_BATCH, energy.getAmountAsInt() / FE_PER_HEAT);
                if (heatAvailable() >= cost || units == 0) return;
                energy.set(energy.getAmountAsInt() - units * FE_PER_HEAT);
                burnRemaining += units;
                burnTotal = ENERGY_HEAT_BATCH;
            }
            default -> {
                ItemStack fuel = items.get(SLOT_FUEL);
                int duration = SolidFuelGeneratorBlockEntity.burnDuration(fuel, fuels);
                if (duration == 0) return;
                var remainder = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty()) items.set(SLOT_FUEL, remainder == null ? ItemStack.EMPTY : remainder.create());
                burnRemaining += duration;
                burnTotal = duration;
            }
        }
    }

    /**
     * Empties the container in the input slot into the tank and moves what is left of it (an empty
     * bucket) to the output slot. Nothing moves unless the whole container fits and the output slot
     * can take the emptied item, so a half-drained bucket never appears.
     */
    boolean drainContainer() {
        ItemStack input = items.get(SLOT_INPUT);
        java.util.function.Predicate<FluidResource> fluid = BoilerBlockEntity::isWater;
        if (input.isEmpty() || !fluid.test(FluidResource.of(FluidUtil.getFirstStackContained(input)))) return false;
        var working = new ItemStacksResourceHandler(1);
        working.set(0, ItemResource.of(input), 1);
        var container = ItemAccess.forHandlerIndex(working, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) return false;
        ItemStack emptied;
        try (var transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(container, tanks, fluid, Integer.MAX_VALUE, transaction);
            if (moved == 0) return false;
            // A bucket only hands over its whole 1.000 mB; fluid left inside means the tank had no room.
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
        var water = input.read("Water",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        var steam = input.read("Steam",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        tanks.set(0,FluidResource.of(water),isWater(FluidResource.of(water)) ? Math.clamp(water.getAmount(),0,WATER_CAPACITY) : 0);
        tanks.set(1,FluidResource.of(steam),isSteam(FluidResource.of(steam)) ? Math.clamp(steam.getAmount(),0,STEAM_CAPACITY) : 0);
        var lava = input.read("Lava",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        tanks.set(2,FluidResource.of(lava),isLava(FluidResource.of(lava)) ? Math.clamp(lava.getAmount(),0,LAVA_CAPACITY) : 0);
        energy.set(Math.clamp(input.getIntOr("Energy",0),0,ENERGY_CAPACITY));
        burnTotal = Math.clamp(input.getIntOr("BurnTotal",1600),1,32767);
        burnRemaining = Math.clamp(input.getIntOr("BurnRemaining",0),0,32767);
        heatDebt = Math.clamp(input.getIntOr("HeatDebt",0),0,99);
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        upgrades.load(input);
        lastMode = fuelMode();
        generating = false;
        containerDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Water",FluidStack.OPTIONAL_CODEC,FluidUtil.getStack(tanks,0));
        output.store("Steam",FluidStack.OPTIONAL_CODEC,FluidUtil.getStack(tanks,1));
        output.store("Lava",FluidStack.OPTIONAL_CODEC,FluidUtil.getStack(tanks,2));
        output.putInt("Energy",energy.getAmountAsInt());
        output.putInt("BurnRemaining",burnRemaining);
        output.putInt("BurnTotal",burnTotal);
        output.putInt("HeatDebt",heatDebt);
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == SLOT_FUEL ? acceptsFuel(fuelMode(), stack) : slot == SLOT_INPUT && isWaterContainer(stack); }

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

    /** Only the emptied bucket ever leaves; buckets waiting in the input and fuel slots stay put. */
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
    protected Component getDefaultName() { return Component.translatable("block.futuretech.boiler"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BoilerMenu(id, inventory, this, upgrades, data);
    }
}
