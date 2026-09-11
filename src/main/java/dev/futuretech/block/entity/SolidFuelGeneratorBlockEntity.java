package dev.futuretech.block.entity;

import dev.futuretech.block.SolidFuelGeneratorBlock;
import dev.futuretech.energy.GeneratorEnergyHandler;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

public final class SolidFuelGeneratorBlockEntity extends BaseContainerBlockEntity {
    public static final int CAPACITY = 20_000;
    public static final int GENERATION_PER_TICK = 20;
    public static final int OUTPUT_PER_TICK = 80;
    public static final int BURN_TICKS = 1_600;
    // Menu data is synced as 16-bit values, so the energy amount travels as two halves.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_BURN_REMAINING = 2;
    public static final int DATA_GENERATING = 3;
    public static final int DATA_BURN_TOTAL = 4;
    public static final int DATA_COUNT = 5;
    public static final TagKey<Item> WOODEN_FUELS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("futuretech", "generator_wooden_fuels"));

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int burnRemaining;
    private int burnTotal = BURN_TICKS;
    private boolean generating;
    private final GeneratorEnergyHandler energy = new GeneratorEnergyHandler(CAPACITY, OUTPUT_PER_TICK, this::setChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> energy.getAmountAsInt() & 0xFFFF;
                case DATA_ENERGY_HIGH -> energy.getAmountAsInt() >>> 16;
                case DATA_BURN_REMAINING -> burnRemaining;
                case DATA_GENERATING -> generating ? 1 : 0;
                case DATA_BURN_TOTAL -> burnTotal;
                default -> 0;
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
    }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    /** Rebuilds the energy amount from the two synced halves, tolerating sign extension of the low half. */
    public static int unpackEnergy(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

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
        generator.generateEnergy(level.fuelValues());
        if (state.getValue(SolidFuelGeneratorBlock.LIT) != generator.generating) {
            level.setBlock(pos, state.setValue(SolidFuelGeneratorBlock.LIT, generator.generating), 3);
        }
        if (previousSignal != EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy)) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** Opens a new tick's output budget; neighbours pulling energy share it with {@link #exportEnergy}. */
    void beginTick() { energy.beginTick(); }

    private void exportEnergy(Level level, BlockPos pos) {
        // Rotate the first output face so several consumers can share a small supply.
        Direction[] sides = Direction.values();
        int first = (int) (level.getGameTime() % sides.length);
        for (int i = 0; i < sides.length && energy.outputRemaining() > 0; i++) {
            Direction side = sides[(first + i) % sides.length];
            BlockPos neighbor = pos.relative(side);
            if (!level.hasChunkAt(neighbor.getX(), neighbor.getZ())) continue;
            EnergyHandler receiver = level.getCapability(Capabilities.Energy.BLOCK, neighbor, side.getOpposite());
            EnergyHandlerUtil.move(energy, receiver, energy.outputRemaining(), null);
        }
    }

    void generateEnergy(FuelValues fuelValues) {
        generating = false;
        // Reserve a whole tick's output before using fuel, including the last few FE.
        if (CAPACITY - energy.getAmountAsInt() < GENERATION_PER_TICK) return;
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
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, CAPACITY));
        burnTotal = Math.max(1, input.getIntOr("BurnTotal", BURN_TICKS));
        burnRemaining = Math.clamp(input.getIntOr("BurnRemaining", 0), 0, burnTotal);
        generating = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("BurnRemaining", burnRemaining);
        output.putInt("BurnTotal", burnTotal);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && isFuel(stack); }

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
        return new SolidFuelGeneratorMenu(id, inventory, this, data);
    }
}
