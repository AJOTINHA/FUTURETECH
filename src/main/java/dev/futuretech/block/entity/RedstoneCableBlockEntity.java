package dev.futuretech.block.entity;

import dev.futuretech.block.CableKind;
import dev.futuretech.redstone.RedstoneCableNetwork;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Holds this cable's share of a {@link RedstoneCableNetwork}; the network carries the lines.
 * Nothing ticks on its own: the cable reads its neighbours again when one of them changes, and
 * answers from the network when a neighbour asks what it gives out.
 *
 * <p>Networks are walked on a scheduled block tick, the way a repeater takes its step, never
 * from inside the block change that called for it: a cable keeps answering from a retired
 * network until that tick puts it on a new one, so a lamp on a line never blinks while the line
 * is being walked again, and a cable that has no network yet — just placed, or its chunk just
 * loaded — books the same tick to get one.
 */
public final class RedstoneCableBlockEntity extends AbstractCableBlockEntity {
    private static final String SENSOR_TAG = "Sensor";
    private static final String WEAK_TAG = "Weak";

    private @Nullable RedstoneCableNetwork network;
    /** Connectors reading the block beside them the way a comparator does, one bit per {@code Direction.ordinal()}. */
    private int sensorSides;
    /**
     * Connectors giving weak power, which wakes the block beside them without powering it
     * through; the rest give strong power, like a repeater. Kept as the exception so an
     * untouched cable saves nothing extra.
     */
    private int weakSides;

    public RedstoneCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_CABLE.get(), pos, state, CableKind.REDSTONE);
    }

    @Override
    public CableKind kind() { return CableKind.REDSTONE; }

    /** The network this cable is on, retired or not, or null while it has none yet. */
    public @Nullable RedstoneCableNetwork network() { return network; }

    public void setNetwork(RedstoneCableNetwork network) { this.network = network; }

    /**
     * Retires the network, whose cables are all booked for a walk next tick; a cable that has
     * none yet books itself instead, so a cable placed with no cable beside it still gets one.
     */
    @Override
    public void invalidateNetwork() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (network != null) network.invalidate(serverLevel);
        else RedstoneCableNetwork.book(serverLevel, worldPosition);
    }

    /**
     * The booked walk, from the block's tick: nothing to do if a neighbour's walk already put
     * this cable on a live network, else this cable walks its own and the retired network tells
     * the blocks it used to give into to ask again.
     */
    public void rebuild() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) return;
        if (network != null && network.isValid()) return;
        RedstoneCableNetwork retired = network;
        RedstoneCableNetwork.discover(serverLevel, worldPosition);
        if (retired != null) retired.announceLeftovers(serverLevel);
    }

    /** What the connector on {@code side} gives out right now; nothing until the cable has a network. */
    public int emitted(Direction side) {
        return network == null ? 0 : network.emitted(worldPosition, side);
    }

    /** The same, as strong power: only a connector set to strong powers the block through. */
    public int emittedStrongly(Direction side) {
        return isStrong(side) ? emitted(side) : 0;
    }

    /** Whether the connector on {@code side} reads the block beside it as a comparator would. */
    public boolean isSensor(Direction side) { return (sensorSides & (1 << side.ordinal())) != 0; }

    /** The network captures what each connector reads, so it is rebuilt on a change. */
    public void setSensor(Direction side, boolean sensor) {
        int bit = 1 << side.ordinal();
        int updated = sensor ? sensorSides | bit : sensorSides & ~bit;
        if (updated == sensorSides) return;
        sensorSides = updated;
        setChanged();
        invalidateNetwork();
    }

    /** Whether the connector on {@code side} gives strong power; every connector does until switched. */
    public boolean isStrong(Direction side) { return (weakSides & (1 << side.ordinal())) == 0; }

    /** No rebuild: the block is asked as it goes; the blocks given into are told to ask again. */
    public void setStrong(Direction side, boolean strong) {
        int bit = 1 << side.ordinal();
        int updated = strong ? weakSides & ~bit : weakSides | bit;
        if (updated == weakSides) return;
        weakSides = updated;
        setChanged();
        if (network != null) network.announce(worldPosition);
    }

    /**
     * A neighbour changed: the cable's own signal is read again, with the network's connectors
     * quiet so the cable does not power itself through a block it gives into, and then the
     * network reads this cable's connectors. If the cable's signal moved, the connectors that
     * answer to it may have started or stopped, so the blocks they give into are told.
     */
    @Override
    public void samplePower(Level level) {
        boolean was = isPowered();
        if (network == null) {
            super.samplePower(level);
            return;
        }
        network.silently(() -> super.samplePower(level));
        if (!(level instanceof ServerLevel)) return;
        network.sample(worldPosition);
        if (was != isPowered()) network.announce(worldPosition);
    }

    /** Never called: the block asks for no ticker. */
    @Override
    public void serverTick() {}

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sensorSides = input.getIntOr(SENSOR_TAG, 0);
        weakSides = input.getIntOr(WEAK_TAG, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (sensorSides != 0) output.putInt(SENSOR_TAG, sensorSides);
        if (weakSides != 0) output.putInt(WEAK_TAG, weakSides);
    }

    /** Beside the networks of the cables around it, this cable books a walk of its own. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && network == null) RedstoneCableNetwork.book(serverLevel, worldPosition);
    }
}
