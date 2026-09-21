package dev.futuretech.transfer;

import dev.futuretech.api.side.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Hands a machine's tank to whatever sits against its output faces: a tank, another machine's
 * intake, anything with a fluid handler on that side. Cables draw from the same faces on their
 * own; this is for the block placed right up against the pump. A round every {@link #INTERVAL}
 * ticks moves up to {@link #PER_ROUND} across all faces, and the neighbour handlers are cached,
 * so a round costs no capability queries.
 */
public final class FluidTransferUtil {
    /** Ticks between rounds. */
    public static final int INTERVAL = 4;
    /** Millibuckets a machine hands out per tick, shared across all of its output faces. */
    public static final int PER_TICK = 250;
    public static final int PER_ROUND = PER_TICK * INTERVAL;
    private static final Predicate<FluidResource> ANY = resource -> true;

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>[] neighbours =
            new BlockCapabilityCache[Direction.values().length];
    private @Nullable ServerLevel cachedLevel;
    private @Nullable BlockPos cachedPos;

    /** Moves from {@code tank} into the neighbours on the faces in an output mode: the same faces fluid cables draw from. */
    public void pushToNeighbours(Level level, BlockPos pos, ResourceHandler<FluidResource> tank, SideConfig sides) {
        if (!(level instanceof ServerLevel server) || level.getGameTime() % INTERVAL != 0) return;
        if (server != cachedLevel || !pos.equals(cachedPos)) {
            java.util.Arrays.fill(neighbours, null);
            cachedLevel = server;
            cachedPos = pos.immutable();
        }
        Direction[] all = Direction.values();
        // The first face rotates each round so one thirsty neighbour cannot starve the others.
        int first = (int) (level.getGameTime() / INTERVAL % all.length);
        int budget = PER_ROUND;
        for (int step = 0; step < all.length && budget > 0; step++) {
            Direction side = all[(first + step) % all.length];
            if (!sides.allowsItemOutput(side)) continue;
            var cache = neighbours[side.ordinal()];
            if (cache == null) {
                cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, server, pos.relative(side), side.getOpposite());
                neighbours[side.ordinal()] = cache;
            }
            ResourceHandler<FluidResource> neighbour = cache.getCapability();
            if (neighbour == null) continue;
            budget -= ResourceHandlerUtil.move(tank, neighbour, ANY, budget, null);
        }
    }
}
