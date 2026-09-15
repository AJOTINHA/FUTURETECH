package dev.futuretech.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Measures how long each of the mod's block entities spends in its server tick, so the cost can
 * be shown above the blocks in the world. Off by default: every ticker is wrapped, but a wrapped
 * ticker that is not profiling costs one boolean read. Switched on with {@code /futuretech perf}.
 *
 * <p>What a tick measures is what the block does in its own ticker: a machine pushing energy or
 * items into its neighbours pays for that push, and a cable network's whole tick lands on the
 * first cable of the network that happens to tick. Server thread only.
 */
public final class TickProfiler {
    /** Ticks a block may go unmeasured before its sample is dropped; a broken block stops ticking. */
    private static final int STALE_TICKS = 40;
    /** Weight of the newest tick in the running average: settles in about a second. */
    private static final double SMOOTHING = 0.1;

    /** One measured block: where it is and its running average, in microseconds per tick. */
    public record Entry(BlockPos pos, int micros) {}

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class Sample {
        double micros;
        long lastTick;
        boolean seeded;
    }

    private static volatile boolean enabled;
    private static final Map<Key, Sample> samples = new HashMap<>();

    private TickProfiler() {}

    public static boolean enabled() { return enabled; }

    /** Turning it off forgets every sample, so the next session starts clean. */
    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) samples.clear();
    }

    /** Wraps a ticker so its time is recorded while profiling; the block's ticker otherwise runs untouched. */
    public static <T extends BlockEntity> BlockEntityTicker<T> wrap(BlockEntityTicker<T> ticker) {
        return (level, pos, state, entity) -> {
            if (!enabled) {
                ticker.tick(level, pos, state, entity);
                return;
            }
            long start = System.nanoTime();
            ticker.tick(level, pos, state, entity);
            record(level, pos, System.nanoTime() - start);
        };
    }

    private static void record(Level level, BlockPos pos, long nanos) {
        Sample sample = samples.computeIfAbsent(new Key(level.dimension(), pos.immutable()), key -> new Sample());
        double micros = nanos / 1000.0;
        sample.micros = sample.seeded ? sample.micros + (micros - sample.micros) * SMOOTHING : micros;
        sample.seeded = true;
        sample.lastTick = level.getGameTime();
    }

    /** Drops blocks that stopped ticking; called once per server tick while profiling. */
    public static void prune(long gameTime) {
        for (Iterator<Sample> it = samples.values().iterator(); it.hasNext(); ) {
            if (gameTime - it.next().lastTick > STALE_TICKS) it.remove();
        }
    }

    /** The measured blocks of {@code level} within {@code radius} of {@code centre}. */
    public static List<Entry> near(ServerLevel level, Vec3 centre, double radius) {
        List<Entry> entries = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Map.Entry<Key, Sample> entry : samples.entrySet()) {
            if (entry.getKey().dimension() != level.dimension()) continue;
            BlockPos pos = entry.getKey().pos();
            if (centre.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radiusSq) continue;
            entries.add(new Entry(pos, (int) Math.round(entry.getValue().micros)));
        }
        return entries;
    }

    /** Every measured block of {@code level} added up: what the mod costs that dimension per tick. */
    public static int totalMicros(ServerLevel level) {
        double total = 0;
        for (Map.Entry<Key, Sample> entry : samples.entrySet()) {
            if (entry.getKey().dimension() == level.dimension()) total += entry.getValue().micros;
        }
        return (int) Math.round(total);
    }

    /** How many blocks of {@code level} are being measured. */
    public static int count(ServerLevel level) {
        int count = 0;
        for (Key key : samples.keySet()) if (key.dimension() == level.dimension()) count++;
        return count;
    }
}
