package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.SolarGeneratorBlock;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.SolarGeneratorMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

/** Fuel-free generation from the actual sky's sun angle, with weather attenuation. */
public final class SolarGeneratorBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable, RedstoneControllable {
    public static final int CAPACITY = 20_000;
    public static final int GENERATION_PER_TICK = 40;
    public static final int OUTPUT_PER_TICK = 200;
    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_RATE = 2, DATA_SUN = 3,
            DATA_MK = 4, DATA_STATUS = 5, DATA_SIDE_BASE = 6;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_REDSTONE_BASE = DATA_FRONT + 1;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int ACTIVE = 0, NIGHT = 1, COVERED = 2, DISABLED = 3, FULL = 4, NO_SKY = 5;

    private final TickLimitedEnergyHandler energy;
    private final SideConfig sides;
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final EnergyExporter exporter = new EnergyExporter();
    private int generated, sunlight, status = NIGHT;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_RATE -> generated;
                case DATA_SUN -> sunlight;
                case DATA_MK -> MachineLevel.of(getBlockState());
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

    public SolarGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_GENERATOR.get(), pos, state);
        energy = new TickLimitedEnergyHandler(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)), 0, OUTPUT_PER_TICK, this::setChanged);
        sides = ModBlocks.SOLAR_GENERATOR.get().createSideConfig(state);
    }

    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }
    @Override public SideConfig sideConfig() { return sides; }
    @Override public Direction front() { return getBlockState().getValue(SolarGeneratorBlock.FACING); }
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

    public static int sunlightPercent(boolean hasSky, boolean exposed, float angleDegrees, float rain, float thunder) {
        if (!hasSky || !exposed) return 0;
        double sun = Math.max(0, Math.cos(Math.toRadians(angleDegrees)));
        double weather = (1 - .5 * Math.clamp(rain, 0, 1)) * (1 - .6 * Math.clamp(thunder, 0, 1));
        // A pronounced noon peak, tapering smoothly towards dawn and dusk.
        return Math.clamp((int) Math.round(100 * sun * sun * weather), 0, 100);
    }

    /** Any non-air block above the panel counts as cover, even glass or a partial block.
     * WORLD_SURFACE is maintained on placement/removal, so no column scan is needed per tick. */
    public static boolean hasOpenSky(Level level, BlockPos pos) {
        return level.dimensionType().hasSkyLight()
                && level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) <= pos.getY() + 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarGeneratorBlockEntity generator) {
        generator.energy.beginTick();
        // Stored energy remains available at night and when redstone pauses generation.
        generator.exporter.pushToNeighbours(level, pos, generator.energy, generator.sides::allowsEnergyOutput);
        boolean hasSky = level.dimensionType().hasSkyLight();
        boolean exposed = hasSky && hasOpenSky(level, pos);
        float angle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos);
        generator.sunlight = sunlightPercent(hasSky, exposed, angle, level.getRainLevel(1), level.getThunderLevel(1));
        generator.generateEnergy(generator.sunlight);
        if (!hasSky) generator.status = NO_SKY;
        else if (!exposed) generator.status = COVERED;
        // The sun on the front is the sun on the panel: lit while there is light and redstone lets
        // it run, a full buffer included; dark at night, under cover, or switched off.
        boolean lit = generator.sunlight > 0 && generator.redstone.allowsRunning();
        if (state.getValue(SolarGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(SolarGeneratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        generator.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy));
    }

    void generateEnergy(int sunPercent) {
        generated = 0;
        if (!redstone.allowsRunning()) { status = DISABLED; return; }
        if (sunPercent <= 0) { status = NIGHT; return; }
        int room = energy.getCapacityAsInt() - energy.getAmountAsInt();
        if (room <= 0) { status = FULL; return; }
        int peak = MachineLevel.consumption(GENERATION_PER_TICK, MachineLevel.of(getBlockState()));
        generated = Math.min(room, peak * Math.clamp(sunPercent, 0, 100) / 100);
        status = generated > 0 ? ACTIVE : NIGHT;
        if (generated > 0) energy.set(energy.getAmountAsInt() + generated);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        sides.load(input);
        redstone.load(input);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
        sides.save(output);
        redstone.save(output);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.futuretech.solar_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SolarGeneratorMenu(id, inventory, this, data);
    }
}
