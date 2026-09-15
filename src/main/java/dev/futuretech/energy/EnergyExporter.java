package dev.futuretech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Pushes a block's energy into its neighbours, one instance per pushing block. The six neighbour
 * handlers are looked up through {@link BlockCapabilityCache}s, so a tick costs six field reads
 * instead of six capability queries (each of which had a cable allocating a fresh handler view).
 * The caches are created lazily and reset when the pushing block moves to another level.
 */
public final class EnergyExporter {
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<EnergyHandler, @Nullable Direction>[] neighbours = new BlockCapabilityCache[Direction.values().length];
    private @Nullable ServerLevel cachedLevel;
    private @Nullable BlockPos cachedPos;

    /**
     * Pushes the source's remaining output budget for this tick into adjacent energy receivers.
     * The first face rotates each tick so several consumers can share a small supply. An empty
     * source does not touch its neighbours at all.
     *
     * @param throughSide which faces may push; lets a machine honour its side configuration and
     *                    skip neighbours that would bounce energy back
     */
    public void pushToNeighbours(Level level, BlockPos pos, TickLimitedEnergyHandler source,
                                 Predicate<Direction> throughSide) {
        if (source.getAmountAsInt() <= 0 || source.outputRemaining() <= 0 || !(level instanceof ServerLevel server)) return;
        if (server != cachedLevel || !pos.equals(cachedPos)) {
            java.util.Arrays.fill(neighbours, null);
            cachedLevel = server;
            cachedPos = pos.immutable();
        }
        Direction[] sides = Direction.values();
        int first = (int) (level.getGameTime() % sides.length);
        for (int i = 0; i < sides.length && source.outputRemaining() > 0 && source.getAmountAsInt() > 0; i++) {
            Direction side = sides[(first + i) % sides.length];
            if (!throughSide.test(side)) continue;
            var cache = neighbours[side.ordinal()];
            if (cache == null) {
                // An unloaded neighbour answers null until its chunk loads, which invalidates the cache.
                cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, server, pos.relative(side), side.getOpposite());
                neighbours[side.ordinal()] = cache;
            }
            EnergyHandler receiver = cache.getCapability();
            if (receiver != null) EnergyHandlerUtil.move(source, receiver, source.outputRemaining(), null);
        }
    }
}
