package dev.futuretech.energy;

import dev.futuretech.perf.TickProfiler;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.CableBlock;
import dev.futuretech.block.entity.CableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A group of touching cables that behaves as one energy buffer. Neighbours push energy into any
 * cable of the group; once per tick the group spreads what it holds over every adjacent block
 * that accepts energy, never handing it straight back to a block that is pushing into it.
 */
public final class CableNetwork {
    /** A cable face that borders a non-cable block. */
    public record EndpointKey(BlockPos cablePos, Direction side) {
        /** The bordering block; a network wrapping around one machine reaches it from several faces. */
        public BlockPos neighbour() { return cablePos.relative(side); }
    }

    /** Resolves the energy handler of the block beyond an endpoint; may change as blocks are placed. */
    public interface Endpoint {
        EndpointKey key();

        /** Whether this connector hands energy to its neighbour; a connector set to extract only does not. */
        boolean delivers();

        /**
         * Whether this connector pulls energy out of its neighbour itself, instead of waiting to be
         * pushed. Only a connector narrowed to extract alone does: one that still inserts is an
         * ordinary junction, and pulling there would drain every machine a cable happens to touch.
         */
        boolean pulls();

        @Nullable EnergyHandler handler();
    }

    private final int throughput;
    private final TickLimitedEnergyHandler buffer;
    private final Set<BlockPos> cables;
    private final List<Endpoint> endpoints;
    private final Set<BlockPos> fedSinceLastDistribution = new HashSet<>();
    /** Whether any connector pumps; a network without pumps and without energy has nothing to do in a tick. */
    private final boolean pumps;
    /** The connectors that hand energy out, so a tick does not walk the ones that never do. */
    private final List<Endpoint> deliverers;
    /** The connectors that pump, walked only on ticks the pumps are awake. */
    private final List<Endpoint> pumpers;
    /** Pumps that found nothing look again only every this many ticks; one that finds energy wakes every tick. */
    static final int PUMP_IDLE_TICKS = 5;
    private boolean pumpsAsleep;
    /** Reused every tick so an idle network allocates nothing. */
    private final List<EnergyHandler> sinks = new ArrayList<>();
    private long lastTick = Long.MIN_VALUE;
    /** How much the delivering connectors could take, added up once per tick; see {@link #sinkRoom}. */
    private long sinkRoom;
    private long sinkRoomTick = Long.MAX_VALUE;
    private int lastMoved;
    private boolean valid = true;

    public CableNetwork(int throughput, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this.throughput = throughput;
        // The buffer only ever holds one tick of throughput, so a congested network rejects inserts
        // and the pushing block keeps its energy.
        this.buffer = new TickLimitedEnergyHandler(throughput, throughput, throughput, () -> {});
        this.cables = Set.copyOf(cables);
        this.endpoints = List.copyOf(endpoints);
        this.pumps = this.endpoints.stream().anyMatch(Endpoint::pulls);
        this.deliverers = this.endpoints.stream().filter(Endpoint::delivers).toList();
        this.pumpers = this.endpoints.stream().filter(Endpoint::pulls).toList();
    }

    /** Flood-fills the cables touching {@code start} and gives every one of them this network. */
    public static CableNetwork discover(ServerLevel level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<CableBlockEntity> members = new ArrayList<>();
        int throughput = Integer.MAX_VALUE;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        cables.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable)) continue;
            members.add(cable);
            throughput = Math.min(throughput, cable.tier().throughput());
            for (Direction side : Direction.values()) {
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                if (level.getBlockState(neighbour).getBlock() instanceof CableBlock) {
                    if (cables.add(neighbour)) queue.add(neighbour);
                } else {
                    // Captured here rather than read per tick: changing a connector invalidates the
                    // network, so a rebuilt one always carries current modes.
                    SideMode mode = cable.connectors().mode(side);
                    // A face on "none" neither delivers nor pulls: most faces border air or the
                    // ground, and keeping them would have every tick walk a list of nothing.
                    if (mode == SideMode.NONE) continue;
                    endpoints.add(new CachedEndpoint(new EndpointKey(pos, side),
                            mode.allowsOutput(), mode.allowsInput() && !mode.allowsOutput(),
                            BlockCapabilityCache.create(
                                    Capabilities.Energy.BLOCK, level, neighbour, side.getOpposite())));
                }
            }
        }
        var network = new CableNetwork(throughput == Integer.MAX_VALUE ? 0 : throughput, cables, endpoints);
        members.forEach(member -> member.setNetwork(network));
        return network;
    }

    public boolean isValid() { return valid; }

    public int throughput() { return throughput; }

    public int size() { return cables.size(); }

    public int stored() { return buffer.getAmountAsInt(); }

    /** Energy delivered to endpoints during the last distribution. */
    public int lastMoved() { return lastMoved; }

    /** Drops every member's reference so the next tick rebuilds the network from what is left. */
    public void invalidate(ServerLevel level) {
        valid = false;
        for (BlockPos pos : cables) {
            if (level.hasChunkAt(pos.getX(), pos.getZ())
                    && level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cable.clearNetwork(this);
            }
        }
    }

    /**
     * The handler a neighbour beyond the given cable face sees. Any insert attempt through it, accepted
     * or not, keeps that whole block out of the next distribution: a full buffer must not start returning
     * energy to the block that is trying to push more in, not even through another face it touches.
     */
    public EnergyHandler handlerFor(@Nullable EndpointKey key) {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() { return buffer.getAmountAsLong(); }

            /** Room is what the machines behind the delivering connectors can still take, capped at one tick of throughput. */
            @Override
            public long getCapacityAsLong() { return Math.min(buffer.getCapacityAsLong(), sinkRoom()); }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                // The attempt alone keeps the pusher out of the distribution, accepted or not.
                if (amount > 0 && key != null) fedSinceLastDistribution.add(key.neighbour());
                int accepted = (int) Math.clamp(getCapacityAsLong() - buffer.getAmountAsLong(), 0, amount);
                return accepted <= 0 ? 0 : buffer.insert(accepted, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return buffer.extract(amount, transaction);
            }
        };
    }

    /**
     * The network never holds energy that has nowhere to go: a tick's worth at most, and only as
     * much as the machines behind the delivering connectors can still take. Nothing is swallowed
     * into a buffer that is not saved, so a generator does not burn to refill it on every load.
     * Added up once per tick from cheap reads; no transaction is opened.
     */
    private long sinkRoom() {
        if (sinkRoomTick == lastTick) return sinkRoom;
        sinkRoomTick = lastTick;
        long room = 0;
        for (Endpoint endpoint : deliverers) {
            EnergyHandler handler = endpoint.handler();
            if (handler == null) continue;
            room += Math.max(0, handler.getCapacityAsLong() - handler.getAmountAsLong());
            if (room >= throughput) break;
        }
        return sinkRoom = Math.min(room, throughput);
    }

    /** Runs the once-per-tick distribution; every member cable calls this, only the first one acts. */
    public void tick(long gameTime) {
        if (gameTime == lastTick) return;
        lastTick = gameTime;
        TickProfiler.networkTicked(cables.size());
        buffer.beginTick();
        // Extract-only connectors act as pumps: nothing else in the mod pulls, so a block that
        // merely holds energy without pushing would otherwise never be drained.
        // A pump with nothing to pull, or nowhere to send it, opens no transaction: a move of zero
        // still costs the transaction machinery, every tick, for every pump.
        // Pumps that found nothing doze: they look again every few ticks, and wake for good on a find.
        if (pumps && !deliverers.isEmpty() && buffer.getAmountAsInt() < buffer.getCapacityAsInt()
                && (!pumpsAsleep || gameTime % PUMP_IDLE_TICKS == 0)) {
            boolean found = false;
            for (Endpoint endpoint : pumpers) {
                if (buffer.inputRemaining() <= 0) break;
                EnergyHandler source = endpoint.handler();
                if (source == null || source.getAmountAsLong() <= 0) continue;
                found = true;
                EnergyHandlerUtil.move(source, buffer, buffer.inputRemaining(), null);
            }
            pumpsAsleep = !found;
        }
        // Nothing arrived and nothing was pumped: no sinks to look up this tick.
        if (buffer.getAmountAsInt() <= 0) {
            fedSinceLastDistribution.clear();
            lastMoved = 0;
            return;
        }
        sinks.clear();
        for (Endpoint endpoint : deliverers) {
            if (fedSinceLastDistribution.contains(endpoint.key().neighbour())) continue;
            EnergyHandler handler = endpoint.handler();
            // A full machine is skipped here, before a transaction is opened for it.
            if (handler != null && handler.getAmountAsLong() < handler.getCapacityAsLong()) sinks.add(handler);
        }
        fedSinceLastDistribution.clear();
        lastMoved = distribute(buffer, sinks, (int) Math.floorMod(gameTime, Integer.MAX_VALUE));
    }

    /**
     * Below this a share is not split further: a trickle goes to one sink at a time, in turn, instead
     * of opening a transaction per sink to hand each a few FE. One generator's tick is 20 FE.
     */
    static final int MIN_SHARE = 20;

    static int distribute(EnergyHandler source, List<EnergyHandler> sinks) { return distribute(source, sinks, 0); }

    /**
     * Spreads the source over the sinks in rounds so early sinks cannot starve later ones. The
     * first sink served rotates with {@code round}, so when the whole buffer fits one share the
     * sinks take turns across ticks.
     */
    static int distribute(EnergyHandler source, List<EnergyHandler> sinks, int round) {
        if (sinks.isEmpty() || source.getAmountAsInt() <= 0) return 0;
        List<EnergyHandler> open = new ArrayList<>(sinks.size());
        int first = round % sinks.size();
        for (int i = 0; i < sinks.size(); i++) open.add(sinks.get((first + i) % sinks.size()));
        int moved = 0;
        boolean progress = true;
        while (progress && source.getAmountAsInt() > 0 && !open.isEmpty()) {
            progress = false;
            int share = Math.max(MIN_SHARE, source.getAmountAsInt() / open.size());
            for (Iterator<EnergyHandler> it = open.iterator(); it.hasNext() && source.getAmountAsInt() > 0; ) {
                int accepted = EnergyHandlerUtil.move(source, it.next(), share, null);
                if (accepted == 0) it.remove();
                else {
                    moved += accepted;
                    progress = true;
                }
            }
        }
        return moved;
    }

    private record CachedEndpoint(EndpointKey key, boolean delivers, boolean pulls,
                                 BlockCapabilityCache<EnergyHandler, Direction> cache) implements Endpoint {
        @Override
        public @Nullable EnergyHandler handler() { return cache.getCapability(); }
    }
}
