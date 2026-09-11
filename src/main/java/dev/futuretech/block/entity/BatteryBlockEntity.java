package dev.futuretech.block.entity;

import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.energy.EnergyNetworkUtil;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.BatteryMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

public final class BatteryBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_INPUT = 2;
    public static final int DATA_OUTPUT = 3;
    public static final int DATA_TIER = 4;
    public static final int DATA_COUNT = 5;

    private final BatteryTier tier;
    private final TickLimitedEnergyHandler energy;
    // Transfer totals of the previous tick, shown in the menu as FE/t.
    private int lastInput;
    private int lastOutput;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_INPUT -> lastInput;
                case DATA_OUTPUT -> lastOutput;
                case DATA_TIER -> tier.ordinal();
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

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state);
        this.tier = state.getBlock() instanceof BatteryBlock block ? block.tier() : BatteryTier.MK1;
        this.energy = new TickLimitedEnergyHandler(
                tier.capacity(), tier.transferPerTick(), tier.transferPerTick(), this::setChanged);
    }

    public BatteryTier tier() { return tier; }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        int previousSignal = EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(battery.energy);
        battery.beginTick();
        battery.exportEnergy(level, pos);
        if (previousSignal != EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(battery.energy)) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** Records the previous tick's transfers and opens fresh budgets for this one. */
    void beginTick() {
        lastInput = energy.inputUsed();
        lastOutput = energy.outputUsed();
        energy.beginTick();
    }

    private void exportEnergy(Level level, BlockPos pos) {
        // Batteries never feed each other, which would bounce energy back and forth every tick.
        EnergyNetworkUtil.pushToNeighbours(level, pos, energy,
                neighbour -> level.getBlockEntity(neighbour) instanceof BatteryBlockEntity);
    }

    private void setEnergyClamped(int amount) {
        energy.set(Math.clamp(amount, 0, tier.capacity()));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setEnergyClamped(input.getIntOr("Energy", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
    }

    // The stored charge travels with the dropped item and returns when it is placed again.
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        setEnergyClamped(components.getOrDefault(ModDataComponents.ENERGY.get(), 0));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getAmountAsInt() > 0) {
            components.set(ModDataComponents.ENERGY.get(), energy.getAmountAsInt());
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Energy");
    }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BatteryMenu(id, inventory, this, data);
    }
}
