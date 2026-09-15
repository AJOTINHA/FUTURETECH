package dev.futuretech.transfer;

import dev.futuretech.api.side.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Moves items between a machine and whatever is against its configured faces, one instance per
 * machine. The machine's own face rules decide which slots a side may touch, so a face set to
 * input only ever receives and a face set to output only ever gives.
 *
 * <p>A round runs every {@value #INTERVAL} ticks rather than every tick, with a budget sized so
 * the pace is unchanged; between rounds the neighbours are not even looked at. The six neighbour
 * handlers and the machine's own face wrappers are cached, so a round costs no capability queries
 * and no allocation.
 */
public final class ItemTransferUtil {
    /** Ticks between rounds. */
    public static final int INTERVAL = 4;
    /** Items a machine moves per tick in each direction, shared across all of its faces. */
    public static final int PER_TICK = 4;
    /** Items one round may move in each direction: the per-tick pace over the interval. */
    public static final int PER_ROUND = PER_TICK * INTERVAL;
    private static final Predicate<ItemResource> ANY = resource -> true;

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] neighbours =
            new BlockCapabilityCache[Direction.values().length];
    private final WorldlyContainerWrapper[] faces = new WorldlyContainerWrapper[Direction.values().length];
    private @Nullable ServerLevel cachedLevel;
    private @Nullable BlockPos cachedPos;

    /** Takes items from neighbours into the faces configured as input. */
    public void pullFromNeighbours(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides) {
        transfer(level, pos, container, sides, true);
    }

    /** Hands items from the faces configured as output to their neighbours. */
    public void pushToNeighbours(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides) {
        transfer(level, pos, container, sides, false);
    }

    private void transfer(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides, boolean pulling) {
        if (!(level instanceof ServerLevel server) || level.getGameTime() % INTERVAL != 0) return;
        if (server != cachedLevel || !pos.equals(cachedPos)) {
            java.util.Arrays.fill(neighbours, null);
            java.util.Arrays.fill(faces, null);
            cachedLevel = server;
            cachedPos = pos.immutable();
        }
        Direction[] all = Direction.values();
        // The first face rotates each round so one busy neighbour cannot starve the others.
        int first = (int) (level.getGameTime() / INTERVAL % all.length);
        int budget = PER_ROUND;
        for (int step = 0; step < all.length && budget > 0; step++) {
            Direction side = all[(first + step) % all.length];
            boolean open = pulling ? sides.allowsItemInput(side) : sides.allowsItemOutput(side);
            if (!open) continue;
            var cache = neighbours[side.ordinal()];
            if (cache == null) {
                // An unloaded neighbour answers null until its chunk loads, which invalidates the cache.
                cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, server, pos.relative(side), side.getOpposite());
                neighbours[side.ordinal()] = cache;
            }
            ResourceHandler<ItemResource> neighbour = cache.getCapability();
            if (neighbour == null) continue;
            // Going through the face wrapper keeps the machine's own slot rules in charge.
            var own = faces[side.ordinal()];
            if (own == null) faces[side.ordinal()] = own = new WorldlyContainerWrapper(container, side);
            budget -= pulling
                    ? ResourceHandlerUtil.moveStacking(neighbour, own, ANY, budget, null)
                    : ResourceHandlerUtil.moveStacking(own, neighbour, ANY, budget, null);
        }
    }
}
