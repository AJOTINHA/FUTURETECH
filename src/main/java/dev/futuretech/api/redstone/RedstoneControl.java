package dev.futuretech.api.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The redstone mode of one machine plus the signal it last saw; saved with the block entity. */
public final class RedstoneControl {
    /** Menu data slots {@link #data} exposes: the mode and whether the block is powered. */
    public static final int DATA_COUNT = 2;
    private static final String TAG = "RedstoneMode";

    private RedstoneMode mode = RedstoneMode.IGNORED;
    private boolean powered;

    public RedstoneMode mode() { return mode; }

    public void setMode(RedstoneMode mode) { this.mode = mode; }

    public boolean isPowered() { return powered; }

    /** For a client-side copy, which cannot sample: the signal as the server last said it was. */
    public void setPowered(boolean powered) { this.powered = powered; }

    /**
     * Samples the signal at {@code pos}. Reading the six neighbours (and, behind each solid one,
     * six more) is too much to do every tick, so this runs when the block entity loads and when a
     * neighbour changes, the way the vanilla furnace's redstone does; see {@link #sample}.
     */
    public void update(Level level, BlockPos pos) {
        powered = level.hasNeighborSignal(pos);
    }

    /** For a block's {@code neighborChanged}: resamples the signal of the controllable block entity at {@code pos}, if any. */
    public static void sample(Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstoneControllable target) {
            target.redstoneControl().update(level, pos);
        }
    }

    public boolean allowsRunning() { return mode.allows(powered); }

    public int data(int index) {
        return index == 0 ? mode.ordinal() : powered ? 1 : 0;
    }

    public void save(ValueOutput output) {
        output.store(TAG, RedstoneMode.CODEC, mode);
    }

    public void load(ValueInput input) {
        mode = input.read(TAG, RedstoneMode.CODEC).orElse(RedstoneMode.IGNORED);
    }
}
