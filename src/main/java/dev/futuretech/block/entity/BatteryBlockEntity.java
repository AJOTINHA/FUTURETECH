package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.BatteryMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.neoforged.neoforge.model.data.ModelData;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

public final class BatteryBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_INPUT = 2;
    public static final int DATA_OUTPUT = 3;
    public static final int DATA_TIER = 4;
    public static final int DATA_SIDE_BASE = 5;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_REDSTONE_BASE = DATA_FRONT + 1;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;

    private final TickLimitedEnergyHandler energy;
    private final SideConfig sides;
    private final RedstoneControl redstone = new RedstoneControl();
    private final EnergyExporter exporter = new EnergyExporter();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> tier().ordinal() + 1, this::setChanged);
    // Transfer totals of the previous tick, shown in the menu as FE/t.
    private int lastInput;
    private int lastOutput;
    private int lastSentVisualCharge = -1;
    private float visualChargeFrom;
    private float visualChargeTarget;
    private double visualChargeTime;
    private boolean visualChargeInitialized;

    /** Normalized visual charge only; no inventory or energy-handler mutation on the client. */
    public float visualCharge(float partialTick) {
        double now = level == null ? visualChargeTime + 5 : level.getGameTime() + partialTick;
        float progress = (float)Math.clamp((now - visualChargeTime) / 5.0, 0.0, 1.0);
        return visualChargeFrom + (visualChargeTarget - visualChargeFrom) * progress;
    }

    private int visualChargeUnits() {
        return (int)((long)energy.getAmountAsInt() * 1000 / tier().capacity());
    }

    private void syncVisualCharge(Level level, BlockPos pos, BlockState state) {
        int charge = visualChargeUnits();
        if (charge != lastSentVisualCharge && (level.getGameTime() % 5 == 0
                || lastSentVisualCharge < 0 || charge == 0 || charge == 1000)) {
            lastSentVisualCharge = charge;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_INPUT -> lastInput;
                case DATA_OUTPUT -> lastOutput;
                case DATA_TIER -> tier().ordinal();
                case DATA_FRONT -> front().ordinal();
                default -> {
                    if (index >= DATA_SIDE_BASE && index < DATA_FRONT) yield sides.data(index - DATA_SIDE_BASE);
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

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state);
        BatteryTier tier = tier();
        this.energy = new TickLimitedEnergyHandler(
                tier.capacity(), tier.transferPerTick(), tier.transferPerTick(), this::setChanged);
        this.sides = state.getBlock() instanceof BatteryBlock block
                ? block.createSideConfig(state) : ModBlocks.BATTERY_MK1.get().createSideConfig(state);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        BatteryTier tier = tier();
        energy.setCapacity(tier.capacity());
        energy.setTransferLimits(tier.transferPerTick(), tier.transferPerTick());
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = SideConfigVisuals.updateTag(sides);
        tag.putInt("VisualCharge", visualChargeUnits());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        int previousModes = SideConfigVisuals.faceModes(sides);
        sides.load(input);
        if (previousModes != SideConfigVisuals.faceModes(sides)) SideConfigVisuals.refresh(this);
        input.getInt("VisualCharge").ifPresent(charge -> {
            float target = Math.clamp(charge, 0, 1000) / 1000.0F;
            visualChargeFrom = visualChargeInitialized ? visualCharge(0) : target;
            visualChargeTarget = target;
            visualChargeTime = level == null ? 0 : level.getGameTime();
            visualChargeInitialized = true;
        });
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

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
    public void sideConfigChanged() {
        setChanged();
        // Cables and capability caches next to us must see the new face modes.
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public BatteryTier tier() { return BatteryTier.byOrdinal(MachineLevel.of(getBlockState()) - 1); }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        int previousSignal = EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(battery.energy);
        battery.beginTick();
        // Redstone gates the output; charging through input faces is never blocked.
        if (battery.redstone.allowsRunning()) battery.exportEnergy(level, pos);
        battery.syncVisualCharge(level, pos, state);
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

    /** The face shown in the middle of the side panel, marked with copper on the model. */
    @Override
    public Direction front() {
        return getBlockState().hasProperty(BatteryBlock.FACING)
                ? getBlockState().getValue(BatteryBlock.FACING) : Direction.NORTH;
    }

    private void exportEnergy(Level level, BlockPos pos) {
        // Only output faces push, and never straight into another battery.
        exporter.pushToNeighbours(level, pos, energy, side -> sides.allowsEnergyOutput(side)
                && !(level.getBlockEntity(pos.relative(side)) instanceof BatteryBlockEntity));
    }

    private void setEnergyClamped(int amount) {
        energy.set(Math.clamp(amount, 0, tier().capacity()));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setEnergyClamped(input.getIntOr("Energy", 0));
        sides.load(input);
        redstone.load(input);
        upgrades.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
        sides.save(output);
        redstone.save(output);
        upgrades.save(output);
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
    public Component getDisplayName() { return Component.translatable("block.futuretech." + tier().blockName()); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BatteryMenu(id, inventory, this, upgrades, data);
    }
}
