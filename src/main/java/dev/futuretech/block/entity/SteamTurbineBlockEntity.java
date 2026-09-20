package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.SteamTurbineBlock;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.SteamTurbineMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
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
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

/** A dedicated steam consumer; water and other fluids cannot generate power. */
public final class SteamTurbineBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 40_000;
    public static final int STEAM_CAPACITY = 16_000, STEAM_PER_TICK = 20, FE_PER_MB = 10;
    public static final int GENERATION_PER_TICK = STEAM_PER_TICK * FE_PER_MB;
    /** FE per mB lost for every speed upgrade, in percent: turning faster wastes a little of each mB. */
    public static final int SPEED_LOSS_PERCENT = 10;
    public static final int OUTPUT_PER_TICK = 1_200;
    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_RATE = 2, DATA_STEAM = 3,
            DATA_MK = 4, DATA_STATUS = 5, DATA_MAX_RATE = 6, DATA_SIDE_BASE = 7;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_REDSTONE_BASE = DATA_FRONT + 1;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int ACTIVE = 0, NO_STEAM = 1, DISABLED = 2, FULL = 3;

    private final TickLimitedEnergyHandler energy;
    private final SideConfig sides;
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final EnergyExporter exporter = new EnergyExporter();
    private int generated, status = NO_STEAM;
    private final FluidStacksResourceHandler steam = new FluidStacksResourceHandler(1,STEAM_CAPACITY) {
        @Override public boolean isValid(int index, FluidResource resource) { return BoilerBlockEntity.isSteam(resource); }
        @Override protected void onContentsChanged(int index, FluidStack previous) { setChanged(); }
    };
    public ResourceHandler<FluidResource> steam() { return steam; }
    public int steamAmount() { return steam.getAmountAsInt(0); }
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_RATE -> generated;
                case DATA_STEAM -> steamAmount();
                case DATA_MK -> MachineLevel.of(getBlockState());
                case DATA_MAX_RATE -> steamPerTick() * fePerMb();
                case DATA_STATUS -> status;
                case DATA_FRONT -> front().ordinal();
                default -> {
                    if (index >= DATA_SIDE_BASE && index < DATA_FRONT) yield sides.data(index - DATA_SIDE_BASE);
                    if (index >= DATA_REDSTONE_BASE && index < DATA_COUNT) yield redstone.data(index - DATA_REDSTONE_BASE);
                    yield 0;
                }
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::setChanged);

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_TURBINE.get(), pos, state);
        energy = new TickLimitedEnergyHandler(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)), 0, OUTPUT_PER_TICK, this::setChanged);
        sides = ModBlocks.STEAM_TURBINE.get().createSideConfig(state);
    }

    @Override public UpgradeInventory upgrades() { return upgrades; }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }
    @Override public SideConfig sideConfig() { return sides; }
    @Override public Direction front() { return getBlockState().getValue(SteamTurbineBlock.FACING); }
    @Override public RedstoneControl redstoneControl() { return redstone; }
    @Override public void redstoneControlChanged() { setChanged(); }
    public EnergyHandler energy() { return energy; }
    public ContainerData menuData() { return data; }
    @Override public void setChanged() { ComparatorNotifier.markChanged(this); }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }
    @Override public void sideConfigChanged() {
        setChanged();
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }
    @Override public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return SideConfigVisuals.updateTag(sides); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        int previous = SideConfigVisuals.faceModes(sides);
        sides.load(input);
        if (previous != SideConfigVisuals.faceModes(sides)) SideConfigVisuals.refresh(this);
    }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity generator) {
        generator.energy.beginTick();
        generator.exporter.pushToNeighbours(level,pos,generator.energy,generator.sides::allowsEnergyOutput);
        generator.generateEnergy();
        boolean lit = generator.generated > 0;
        if (state.getValue(SteamTurbineBlock.LIT) != lit) level.setBlock(pos,state.setValue(SteamTurbineBlock.LIT,lit),Block.UPDATE_CLIENTS);
        generator.comparator.update(level,pos,state,EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy));
    }

    /** mB of steam a tick at full speed: the level's rate, once more for every speed upgrade. */
    public int steamPerTick() {
        return MachineLevel.consumption(STEAM_PER_TICK, MachineLevel.of(getBlockState())) * (1 + upgrades.installed(UpgradeInventory.SPEED));
    }

    /** FE returned for each mB of steam: the base, less a tenth for every speed upgrade. */
    public int fePerMb() {
        return Math.max(1, FE_PER_MB * Math.max(0, 100 - SPEED_LOSS_PERCENT * upgrades.installed(UpgradeInventory.SPEED)) / 100);
    }

    void generateEnergy() {
        generated = 0;
        if (!redstone.allowsRunning()) { status = DISABLED; return; }
        if (steamAmount() == 0) { status = NO_STEAM; return; }
        int fePerMb = fePerMb();
        int room = (energy.getCapacityAsInt() - energy.getAmountAsInt()) / fePerMb;
        if (room == 0) { status = FULL; return; }
        int consumed = Math.min(Math.min(steamAmount(),room),steamPerTick());
        steam.set(0,steam.getResource(0),steamAmount()-consumed);
        generated = consumed * fePerMb;
        energy.set(energy.getAmountAsInt()+generated);
        status = ACTIVE;
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        var savedSteam = input.read("Steam",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        var resource = FluidResource.of(savedSteam);
        steam.set(0,resource,BoilerBlockEntity.isSteam(resource) ? Math.clamp(savedSteam.getAmount(),0,STEAM_CAPACITY) : 0);
        sides.load(input);
        redstone.load(input);
        upgrades.load(input);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("Steam",FluidStack.OPTIONAL_CODEC,FluidUtil.getStack(steam,0));
        sides.save(output);
        redstone.save(output);
        upgrades.save(output);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.futuretech.steam_turbine"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SteamTurbineMenu(id, inventory, this, upgrades, data);
    }
}
