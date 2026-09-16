package dev.futuretech.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tells comparators about a machine only when its signal changed, and not more often than every
 * {@value #INTERVAL_TICKS} ticks. Vanilla's {@code BlockEntity.setChanged()} broadcasts to the
 * neighbours on every call, which costs about a dozen block reads (~25 µs here); a machine that
 * changes every tick, twice, spent most of its tick on that. The mod's machines mark the chunk
 * dirty with {@link #markChanged} instead and report their signal through {@link #update} at the
 * end of the tick. A signal that flickers across a threshold inside one interval is not reported,
 * which comparators, with their own two-tick delay, would not have shown anyway.
 */
final class ComparatorNotifier {
    static final int INTERVAL_TICKS = 4;
    private int lastSignal = -1;

    /** What {@code setChanged()} should do every tick: mark the chunk for saving and nothing else. */
    static void markChanged(BlockEntity entity) {
        Level level = entity.getLevel();
        if (level != null) level.blockEntityChanged(entity.getBlockPos());
    }

    /** Reports {@code signal} to the neighbours if it differs from the last one reported and the interval allows. */
    void update(Level level, BlockPos pos, BlockState state, int signal) {
        if (signal == lastSignal) return;
        if (lastSignal >= 0 && level.getGameTime() % INTERVAL_TICKS != 0) return;
        lastSignal = signal;
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
}
