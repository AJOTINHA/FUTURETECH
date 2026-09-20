package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.WindGeneratorBlock;
import dev.futuretech.block.WindTurbineStructure;
import dev.futuretech.energy.EnergyExporter;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.WindGeneratorMenu;
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

/** Wind power depends on altitude and clearance, independently of the time of day. */
public final class WindGeneratorBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable, RedstoneControllable {
    public static final int CAPACITY = 20_000;
    public static final int GENERATION_PER_TICK = 24;
    public static final int OUTPUT_PER_TICK = 200;
    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_RATE = 2, DATA_WIND = 3,
            DATA_MK = 4, DATA_STATUS = 5, DATA_SIDE_BASE = 6;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_REDSTONE_BASE = DATA_FRONT + 1;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int ACTIVE = 0, CALM = 1, OBSTRUCTED = 2, DISABLED = 3, FULL = 4, NO_SKY = 5;

    private final TickLimitedEnergyHandler energy;
    private final SideConfig sides;
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final EnergyExporter exporter = new EnergyExporter();
    private int generated, windStrength, status = CALM;
    private int sampleCountdown, obstructionStatus = CALM, rotorSpeed;
    private boolean legacyRotorPartsRemoved;
    private float rotorAngle, previousRotorAngle;

    public float rotorAngle(float partialTick) {
        return previousRotorAngle + (rotorAngle - previousRotorAngle) * Math.clamp(partialTick, 0, 1);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WindGeneratorBlockEntity generator) {
        if (generator.rotorAngle >= 360) generator.rotorAngle -= 360;
        generator.previousRotorAngle = generator.rotorAngle;
        generator.rotorAngle += generator.rotorSpeed * .06F;
    }
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_RATE -> generated;
                case DATA_WIND -> windStrength;
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

    public WindGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_GENERATOR.get(), pos, state);
        energy = new TickLimitedEnergyHandler(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)), 0, OUTPUT_PER_TICK, this::setChanged);
        sides = ModBlocks.WIND_GENERATOR.get().createSideConfig(state);
    }

    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }
    @Override public SideConfig sideConfig() { return sides; }
    @Override public Direction front() { return getBlockState().getValue(WindGeneratorBlock.FACING); }
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
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = SideConfigVisuals.updateTag(sides);
        tag.putInt("RotorSpeed", rotorSpeed);
        return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        rotorSpeed = Math.clamp(input.getIntOr("RotorSpeed", 0), 0, 100);
        int previous = SideConfigVisuals.faceModes(sides);
        sides.load(input);
        if (previous != SideConfigVisuals.faceModes(sides)) SideConfigVisuals.refresh(this);
    }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    /** Full exposure gives 25% at Y64, rising smoothly to 100% at Y192. */
    public static int windPercent(int height, int clear, int total) {
        if (total <= 0 || clear <= 0) return 0;
        int altitude = 25 + Math.clamp(height - 64, 0, 128) * 75 / 128;
        return altitude * Math.clamp(clear, 0, total) / total;
    }

    private static boolean clearAir(Level level, BlockPos pos, BlockPos base) {
        return level.hasChunkAt(pos) && (level.getBlockState(pos).isAir() || WindTurbineStructure.belongsTo(level, pos, base));
    }

    private void sampleWind(Level level, BlockPos pos) {
        windStrength = 0;
        if (!level.dimensionType().hasSkyLight()) { obstructionStatus = NO_SKY; return; }
        if (level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) > pos.getY() + WindTurbineStructure.HEIGHT
                || !clearAir(level, pos.above(3).relative(front()), pos) || !clearAir(level, pos.above(3).relative(front(), 2), pos)) {
            obstructionStatus = OBSTRUCTED;
            return;
        }
        int clear = 0;
        // Sixteen horizontal samples reward open sites without loading neighbouring chunks.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 4; distance++) {
                if (!clearAir(level, pos.above(3).relative(direction, distance), pos)) break;
                clear++;
            }
        }
        windStrength = windPercent(pos.getY() + 3, clear, 16);
        obstructionStatus = CALM;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WindGeneratorBlockEntity generator) {
        generator.energy.beginTick();
        generator.exporter.pushToNeighbours(level, pos, generator.energy, generator.sides::allowsEnergyOutput);
        if (generator.sampleCountdown-- <= 0) {
            generator.sampleCountdown = 19;
            if (!generator.legacyRotorPartsRemoved) {
                generator.legacyRotorPartsRemoved = WindTurbineStructure.removeLegacyRotorParts(level, pos, generator.front());
            }
            generator.sampleWind(level, pos);
        }
        generator.generateEnergy(generator.windStrength);
        if (generator.redstone.allowsRunning() && generator.windStrength == 0) {
            generator.status = generator.obstructionStatus;
        }
        int speed = generator.redstone.allowsRunning() ? generator.windStrength : 0;
        boolean lit = speed > 0;
        if (state.getValue(WindGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(WindGeneratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        if (generator.rotorSpeed != speed) {
            generator.rotorSpeed = speed;
            level.sendBlockUpdated(pos, generator.getBlockState(), generator.getBlockState(), Block.UPDATE_CLIENTS);
        }
        generator.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(generator.energy));
    }

    void generateEnergy(int windPercent) {
        generated = 0;
        if (!redstone.allowsRunning()) { status = DISABLED; return; }
        if (windPercent <= 0) { status = CALM; return; }
        int room = energy.getCapacityAsInt() - energy.getAmountAsInt();
        if (room <= 0) { status = FULL; return; }
        int peak = MachineLevel.consumption(GENERATION_PER_TICK, MachineLevel.of(getBlockState()));
        generated = Math.min(room, peak * Math.clamp(windPercent, 0, 100) / 100);
        status = generated > 0 ? ACTIVE : CALM;
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
    @Override public Component getDisplayName() { return Component.translatable("block.futuretech.wind_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WindGeneratorMenu(id, inventory, this, data);
    }
}
