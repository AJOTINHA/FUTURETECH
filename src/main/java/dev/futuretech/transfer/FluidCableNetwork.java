package dev.futuretech.transfer;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.FluidCableBlock;
import dev.futuretech.block.FluidCableTier;
import dev.futuretech.block.entity.FluidCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A group of touching fluid cables. Like the energy network it is a buffer: neighbours push fluid
 * into any cable, extract-only connectors pump from the tanks behind them, and once per tick the
 * group spreads what it holds over the tanks that accept it, never handing fluid straight back to
 * a block that is pushing into it. Each buffer holds one tick of the tier's throughput, so a
 * congested line refuses inserts and the pushing block keeps its fluid. A pump fills the line
 * even with nowhere to deliver, so a cable on a tank set to extract holds one tick of fluid until
 * a destination shows up.
 *
 * <p>When the network is torn down it empties its buffers first: into the line's sinks, and what
 * they refuse straight back into the tanks around it, so a rebuild swallows nothing.
 *
 * <p>Connectors carry a colour and a channel number, and every colour-channel pair is a line with
 * a buffer of its own: what comes in on one line only goes out on the same line. Within a line,
 * higher priorities are filled first; equal priorities share fairly.
 *
 * <p>The network also keeps track of what fluid it last carried and which way it went through
 * each cable, so the see-through cables can show it streaming: the picture lingers a moment after
 * the flow stops, since a buffer this small is empty more often than not even while fluid is
 * streaming through.
 */
public final class FluidCableNetwork {
    /** A cable face that borders a non-cable block. */
    public record EndpointKey(BlockPos cablePos, Direction side) {
        public BlockPos neighbour() { return cablePos.relative(side); }
    }

    /** A colour and channel; every line has its own buffer. */
    public record Line(DyeColor color, int channel) {}

    /** Resolves the fluid handler of the block beyond an endpoint; may change as blocks are placed. */
    public interface Endpoint {
        EndpointKey key();

        /** Whether this connector hands fluid to its neighbour; a connector set to extract only does not. */
        boolean delivers();

        /** Whether this connector pulls fluid out of its neighbour itself; only extract-only connectors do. */
        boolean pulls();

        /** Connectors with a higher priority are filled before lower ones. */
        int priority();

        default DyeColor color() { return DyeColor.WHITE; }

        default int channel() { return 0; }

        default Line line() { return new Line(color(), channel()); }

        @Nullable ResourceHandler<FluidResource> handler();
    }

    /** How long a see-through cable keeps showing a fluid after the last of it went through. */
    public static final int SHOW_TICKS = 20;
    private static final Predicate<FluidResource> ANY = resource -> true;

    private final @Nullable ServerLevel level;
    private final int throughput;
    private final Set<BlockPos> cables;
    /** Highest priority first; connectors of equal priority stay in discovery order. */
    private final List<Endpoint> endpoints;
    /** Runs of equal priority within {@link #endpoints}. */
    private final List<List<Endpoint>> ranks;
    private final Map<EndpointKey, Endpoint> byKey = new HashMap<>();
    private final Map<Line, FluidStacksResourceHandler> buffers = new HashMap<>();
    /** Blocks that pushed into a line since its last distribution, so nothing bounces back to them. */
    private final Map<Line, Set<BlockPos>> fedSinceLastDistribution = new HashMap<>();
    /** The connectors fluid came in through since the last distribution, per line. */
    private final Map<Line, Set<EndpointKey>> entriesSinceLastDistribution = new HashMap<>();
    private final Map<PathKey, List<BlockPos>> paths = new HashMap<>();
    /** Which way fluid last went through each cable; a cable off every route is not listed. */
    private final Map<BlockPos, Direction> flow = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    private int lastMoved;
    private FluidStack lastFluid = FluidStack.EMPTY;
    private long lastFlowTick = Long.MIN_VALUE;
    private FluidStack shown = FluidStack.EMPTY;
    private boolean valid = true;

    /** A network with no level never updates cables; tests build these. */
    public FluidCableNetwork(int throughput, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this(null, throughput, cables, endpoints);
    }

    public FluidCableNetwork(@Nullable ServerLevel level, int throughput, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this.level = level;
        this.throughput = throughput;
        this.cables = Set.copyOf(cables);
        List<Endpoint> sorted = new ArrayList<>(endpoints);
        sorted.sort(Comparator.comparingInt(Endpoint::priority).reversed());
        this.endpoints = List.copyOf(sorted);
        List<List<Endpoint>> ranks = new ArrayList<>();
        for (Endpoint endpoint : this.endpoints) {
            if (ranks.isEmpty() || ranks.getLast().getFirst().priority() != endpoint.priority()) ranks.add(new ArrayList<>());
            ranks.getLast().add(endpoint);
        }
        this.ranks = ranks.stream().map(List::copyOf).toList();
        for (Endpoint endpoint : this.endpoints) byKey.put(endpoint.key(), endpoint);
    }

    /** Flood-fills the cables touching {@code start} and gives every one of them this network. */
    public static FluidCableNetwork discover(ServerLevel level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<FluidCableBlockEntity> members = new ArrayList<>();
        FluidCableTier slowest = null;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        cables.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof FluidCableBlockEntity cable)) continue;
            members.add(cable);
            if (slowest == null || cable.tier().compareTo(slowest) < 0) slowest = cable.tier();
            for (Direction side : Direction.values()) {
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                if (level.getBlockState(neighbour).getBlock() instanceof FluidCableBlock) {
                    if (cables.add(neighbour)) queue.add(neighbour);
                } else {
                    // Captured here rather than read per tick: changing a connector invalidates the
                    // network, so a rebuilt one always carries current modes.
                    SideMode mode = cable.connectors().mode(side);
                    endpoints.add(new CachedEndpoint(new EndpointKey(pos, side),
                            mode.allowsOutput(), mode.allowsInput() && !mode.allowsOutput(),
                            cable.connectorPriority(side), cable.connectorColor(side), cable.connectorChannel(side),
                            BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, neighbour, side.getOpposite())));
                }
            }
        }
        var network = new FluidCableNetwork(level, slowest == null ? 0 : slowest.throughput(), cables, endpoints);
        // A member may still sit on a live network (its chunk loaded after this one's); retiring
        // it first empties that network's buffers instead of orphaning them.
        members.forEach(FluidCableBlockEntity::invalidateNetwork);
        members.forEach(member -> member.setNetwork(network));
        return network;
    }

    public boolean isValid() { return valid; }

    /** What every line's buffer holds right now, lines with nothing left out. */
    public Map<Line, FluidStack> buffered() {
        Map<Line, FluidStack> held = new HashMap<>();
        buffers.forEach((line, buffer) -> {
            int amount = buffer.getAmountAsInt(0);
            if (amount > 0) held.put(line, buffer.getResource(0).toStack(amount));
        });
        return held;
    }

    public int throughput() { return throughput; }

    public int size() { return cables.size(); }

    /** Millibuckets delivered to tanks during the last distribution, over every line. */
    public int lastMoved() { return lastMoved; }

    /** What the see-through cables of this network show right now. */
    public FluidStack shown() { return shown; }

    /** The buffer of a line, made on first use. */
    public FluidStacksResourceHandler buffer(Line line) {
        return buffers.computeIfAbsent(line, l -> new FluidStacksResourceHandler(1, Math.max(1, throughput)));
    }

    /**
     * Drops every member's reference so the next tick rebuilds the network from what is left,
     * after emptying the buffers into the tanks so the rebuilt one starts clean without loss.
     */
    public void invalidate(ServerLevel level) {
        if (!valid) return;
        valid = false;
        for (BlockPos pos : cables) {
            if (level.hasChunkAt(pos.getX(), pos.getZ())
                    && level.getBlockEntity(pos) instanceof FluidCableBlockEntity cable) {
                cable.clearNetwork(this);
            }
        }
        flush(level);
    }

    /**
     * Empties every line into its sinks and, failing that, back into any tank on the line, going
     * around the connector modes: the fluid came out of a tank and has nowhere else to be.
     */
    private void flush(ServerLevel level) {
        for (Map.Entry<Line, FluidStacksResourceHandler> entry : buffers.entrySet()) {
            Line line = entry.getKey();
            FluidStacksResourceHandler buffer = entry.getValue();
            for (Endpoint endpoint : endpoints) {
                if (buffer.getAmountAsInt(0) <= 0) break;
                if (endpoint.delivers() && endpoint.line().equals(line)) ResourceHandlerUtil.move(buffer, endpoint.handler(), ANY, Integer.MAX_VALUE, null);
            }
            for (Endpoint endpoint : endpoints) {
                if (buffer.getAmountAsInt(0) <= 0) break;
                if (!endpoint.line().equals(line)) continue;
                BlockPos tank = endpoint.key().neighbour();
                if (!level.hasChunkAt(tank.getX(), tank.getZ())) continue;
                ResourceHandlerUtil.move(buffer, level.getCapability(Capabilities.Fluid.BLOCK, tank, null), ANY, Integer.MAX_VALUE, null);
            }
        }
        buffers.clear();
    }

    /**
     * The handler a neighbour beyond the given cable face sees: the buffer of that face's line.
     * Any insert attempt through it, accepted or not, keeps that whole block out of the line's next
     * distribution, so a full buffer never starts returning fluid to the block that is trying to
     * push more in. Nothing can be extracted from it.
     */
    public ResourceHandler<FluidResource> handlerFor(@Nullable EndpointKey key) {
        Endpoint entry = key == null ? null : byKey.get(key);
        Line line = entry == null ? new Line(DyeColor.WHITE, 0) : entry.line();
        FluidStacksResourceHandler buffer = buffer(line);
        return new ResourceHandler<>() {
            @Override
            public int size() { return 1; }

            @Override
            public FluidResource getResource(int index) { return buffer.getResource(0); }

            @Override
            public long getAmountAsLong(int index) { return buffer.getAmountAsLong(0); }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) { return buffer.getCapacityAsLong(0, resource); }

            @Override
            public boolean isValid(int index, FluidResource resource) { return buffer.isValid(0, resource); }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                if (index != 0) return 0;
                if (amount > 0 && key != null) {
                    fedSinceLastDistribution.computeIfAbsent(line, l -> new HashSet<>()).add(key.neighbour());
                    entriesSinceLastDistribution.computeIfAbsent(line, l -> new HashSet<>()).add(key);
                }
                return buffer.insert(0, resource, amount, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /** Runs the once-per-tick pumping and distribution; every member cable calls this, only the first one acts. */
    public void tick(long gameTime) {
        if (gameTime == lastTick) return;
        lastTick = gameTime;
        // Extract-only connectors act as pumps: a tank never pushes, so a connector on one has to
        // do the pulling or nothing ever comes out. Higher priorities pump first.
        for (Endpoint endpoint : endpoints) {
            if (!endpoint.pulls()) continue;
            ResourceHandler<FluidResource> source = endpoint.handler();
            if (source == null) continue;
            FluidStacksResourceHandler buffer = buffer(endpoint.line());
            int room = buffer.getCapacityAsInt(0, buffer.getResource(0)) - buffer.getAmountAsInt(0);
            if (room > 0 && ResourceHandlerUtil.move(source, buffer, ANY, room, null) > 0) {
                entriesSinceLastDistribution.computeIfAbsent(endpoint.line(), l -> new HashSet<>()).add(endpoint.key());
            }
        }
        lastMoved = 0;
        boolean routesChanged = false;
        for (Map.Entry<Line, FluidStacksResourceHandler> entry : buffers.entrySet()) {
            Line line = entry.getKey();
            FluidStacksResourceHandler buffer = entry.getValue();
            if (buffer.getAmountAsInt(0) > 0) {
                lastFluid = buffer.getResource(0).toStack(buffer.getAmountAsInt(0));
                lastFlowTick = gameTime;
            }
            Set<BlockPos> fed = fedSinceLastDistribution.getOrDefault(line, Set.of());
            List<Endpoint> accepted = new ArrayList<>();
            for (List<Endpoint> rank : ranks) {
                if (buffer.getAmountAsInt(0) <= 0) break;
                List<Endpoint> sinks = new ArrayList<>();
                for (Endpoint endpoint : rank) {
                    if (!endpoint.delivers() || !endpoint.line().equals(line)) continue;
                    if (fed.contains(endpoint.key().neighbour())) continue;
                    if (endpoint.handler() != null) sinks.add(endpoint);
                }
                lastMoved += distribute(buffer, sinks, accepted);
            }
            // Every route fluid took this tick, from where it came in to where it went out,
            // leaves its direction on the cables along the way.
            for (EndpointKey from : entriesSinceLastDistribution.getOrDefault(line, Set.of())) {
                for (Endpoint to : accepted) routesChanged |= markRoute(from, to.key());
            }
        }
        fedSinceLastDistribution.clear();
        entriesSinceLastDistribution.clear();
        updateShown(gameTime, routesChanged);
    }

    /**
     * Spreads the buffer over the sinks in rounds so early sinks cannot starve later ones; the
     * ones that took anything are added to {@code accepted}.
     */
    static int distribute(FluidStacksResourceHandler source, List<Endpoint> sinks, List<Endpoint> accepted) {
        List<Endpoint> open = new ArrayList<>(sinks);
        int moved = 0;
        boolean progress = true;
        while (progress && source.getAmountAsInt(0) > 0 && !open.isEmpty()) {
            progress = false;
            int share = Math.max(1, source.getAmountAsInt(0) / open.size());
            for (Iterator<Endpoint> it = open.iterator(); it.hasNext() && source.getAmountAsInt(0) > 0; ) {
                Endpoint sink = it.next();
                int taken = ResourceHandlerUtil.move(source, sink.handler(), ANY, share, null);
                if (taken == 0) it.remove();
                else {
                    moved += taken;
                    progress = true;
                    if (!accepted.contains(sink)) accepted.add(sink);
                }
            }
        }
        return moved;
    }

    /** Sets the flow direction of every cable between an entry and an exit; true if any changed. */
    private boolean markRoute(EndpointKey from, EndpointKey to) {
        List<BlockPos> route = path(from.cablePos(), to.cablePos());
        boolean changed = false;
        for (int index = 0; index < route.size(); index++) {
            BlockPos cable = route.get(index);
            Direction direction;
            if (index + 1 < route.size()) {
                BlockPos next = route.get(index + 1);
                direction = Direction.getApproximateNearest(next.getX() - cable.getX(), next.getY() - cable.getY(), next.getZ() - cable.getZ());
            } else {
                direction = to.side();
            }
            if (flow.put(cable, direction) != direction) changed = true;
        }
        return changed;
    }

    /** Which way fluid last went through the cable at {@code pos}, if it was on a route at all. */
    public @Nullable Direction flowAt(BlockPos pos) { return flow.get(pos); }

    /**
     * The shortest run of cables from one to another, both included; cached, since the network
     * never changes once built. Falls back to the entry cable alone if the two are not joined.
     */
    List<BlockPos> path(BlockPos from, BlockPos to) {
        return paths.computeIfAbsent(new PathKey(from, to), key -> {
            if (from.equals(to)) return List.of(from);
            Map<BlockPos, BlockPos> previous = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            previous.put(from, from);
            queue.add(from);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                for (Direction side : Direction.values()) {
                    BlockPos next = pos.relative(side);
                    if (!cables.contains(next) || previous.containsKey(next)) continue;
                    previous.put(next, pos);
                    if (next.equals(to)) {
                        ArrayDeque<BlockPos> route = new ArrayDeque<>();
                        for (BlockPos step = to; !step.equals(from); step = previous.get(step)) route.addFirst(step);
                        route.addFirst(from);
                        return List.copyOf(route);
                    }
                    queue.add(next);
                }
            }
            return List.of(from);
        });
    }

    /**
     * Keeps the last fluid on show for a moment after the flow stops, and tells the cables when
     * that, or the way it runs through them, changes. Once the picture goes, so do the directions.
     */
    private void updateShown(long gameTime, boolean routesChanged) {
        FluidStack now = gameTime - lastFlowTick <= SHOW_TICKS ? lastFluid : FluidStack.EMPTY;
        boolean same = now.isEmpty() ? shown.isEmpty() : FluidStack.isSameFluidSameComponents(now, shown);
        if (same && !routesChanged) return;
        shown = now.copy();
        if (shown.isEmpty()) flow.clear();
        if (level == null) return;
        for (BlockPos pos : cables) {
            if (level.hasChunkAt(pos.getX(), pos.getZ())
                    && level.getBlockEntity(pos) instanceof FluidCableBlockEntity cable) {
                cable.show(shown, flow.get(pos));
            }
        }
    }

    private record PathKey(BlockPos from, BlockPos to) {}

    private record CachedEndpoint(EndpointKey key, boolean delivers, boolean pulls, int priority,
                                 DyeColor color, int channel,
                                 BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache) implements Endpoint {
        @Override
        public @Nullable ResourceHandler<FluidResource> handler() { return cache.getCapability(); }
    }
}
