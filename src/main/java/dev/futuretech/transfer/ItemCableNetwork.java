package dev.futuretech.transfer;

import dev.futuretech.perf.TickProfiler;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.TesseractBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.item.ItemFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A group of touching item cables. An item pushed into any cable of the group, or pumped in by an
 * extract-only connector, leaves its source at once, is given a destination among the inventories
 * on its colour and channel, and then travels the cables to it at a fixed pace; only on arrival
 * does it enter the destination. If the destination will not take it by then it looks for another
 * one from where it is, and failing that waits at the end of the cable and tries again. Cables
 * hold what is inside them: flights survive saving, and a broken cable drops its items.
 *
 * <p>Every connector an item can enter through has its own budget, renewed every {@code interval}
 * ticks: the tier's batch plus {@value #PER_UPGRADE} per speed upgrade on that connector, up to a
 * stack. What is not used in one interval does not carry over. The same upgrades also make what
 * enters there travel faster, see {@link #ticksPerBlock(int)}.
 *
 * <p>Connectors carry a colour, white unless the player picked another, and a channel number from
 * 0 to 100. An item entering through a connector only leaves through connectors of the same colour
 * on the same channel, so one network can carry many separate lines.
 */
public final class ItemCableNetwork {
    /** A cable face that borders a non-cable block. */
    public record EndpointKey(BlockPos cablePos, Direction side) {
        /** The bordering block; a network wrapping around one inventory reaches it from several faces. */
        public BlockPos neighbour() { return cablePos.relative(side); }
    }

    /** Resolves the item handler of the block beyond an endpoint; may change as blocks are placed. */
    public interface Endpoint {
        EndpointKey key();

        /** Whether this connector hands items to its neighbour; a connector set to extract only does not. */
        boolean delivers();

        /**
         * Whether this connector pulls items out of its neighbour itself, instead of waiting to be
         * pushed. Only a connector narrowed to extract alone does: one that still inserts is an
         * ordinary junction, and pulling there would empty every chest a cable happens to touch.
         */
        boolean pulls();

        /** Connectors with a higher priority are served, and pumped, before lower ones. */
        int priority();

        /** Whether the connector's filter card lets a resource cross it, in either direction. */
        default boolean accepts(ItemResource resource) { return true; }

        /** Whether the card on this connector keeps levels, which caps what crosses it either way. */
        default boolean counting() { return false; }

        /**
         * How much of a resource may still cross before the level the card keeps is reached: what
         * the neighbour is short of when inserting, what it has over when extracting. Everything,
         * where no level is kept for that resource.
         */
        default int allowance(ItemResource resource, boolean inserting) { return Integer.MAX_VALUE; }

        /** The connector's colour; items only cross between connectors of the same colour and channel. */
        default DyeColor color() { return DyeColor.WHITE; }

        /** The connector's channel number. */
        default int channel() { return 0; }

        /** Speed upgrades on the connector; each one widens the budget of what enters through it. */
        default int upgrades() { return 0; }

        /**
         * Whether the block beyond hands over without a pace of its own — a tesseract, whose
         * channel brings whatever was put in at the other end all at once. What enters through
         * such a connector is not trickled in one at a time: the budget is the widest a connector
         * can have. The speed along the cables stays the connector's own.
         */
        default boolean unbounded() { return false; }

        @Nullable ResourceHandler<ItemResource> handler();
    }

    /** Items each speed upgrade adds to a connector's budget per interval. */
    public static final int PER_UPGRADE = 2;
    /** No connector moves more than a stack per interval, however many upgrades it carries. */
    public static final int MAX_PER_INTERVAL = 64;
    /** Speed upgrades one connector takes. */
    public static final int MAX_UPGRADES = 32;
    /** How long an item takes to cross one cable when it entered through a connector without upgrades. */
    public static final int TRAVEL_TICKS_PER_BLOCK = 20;

    /**
     * How long an item entering through a connector with that many speed upgrades takes to cross
     * one cable, starting from {@code base} ticks without any: the full set brings it down to a
     * cable per tick.
     */
    public static int ticksPerBlock(int base, int upgrades) {
        int bonus = Math.clamp(upgrades, 0, MAX_UPGRADES) * (base - 1) / MAX_UPGRADES;
        return base - bonus;
    }

    private record PathKey(BlockPos from, BlockPos to) {}

    private final @Nullable ServerLevel level;
    private final int batch;
    private final int interval;
    /** Ticks per cable for items that enter through a connector without upgrades. */
    private final int travelTicks;
    private final Set<BlockPos> cables;
    /** Highest priority first; connectors of equal priority stay in discovery order. */
    private final List<Endpoint> endpoints;
    /** Runs of equal priority within {@link #endpoints}, so a round can rotate inside each. */
    private final List<List<Endpoint>> ranks;
    private final Map<EndpointKey, Integer> indexByKey = new HashMap<>();
    /**
     * What each connector may still let in this interval, indexed like {@link #endpoints}; the
     * last entry serves inserts that do not come through a known connector.
     */
    private final int[] budgets;
    // Journaled so an aborted transaction gives its share of the interval's budgets back.
    private final SnapshotJournal<int[]> budgetJournal = new SnapshotJournal<>() {
        @Override
        protected int[] createSnapshot() { return budgets.clone(); }

        @Override
        protected void revertToSnapshot(int[] snapshot) { System.arraycopy(snapshot, 0, budgets, 0, budgets.length); }
    };
    /**
     * The list belonging to each cable, by position. The lists are the cables' own: a network is
     * built and thrown away as cables change and chunks come and go, so it indexes the load
     * instead of holding it. Without a world - as in the routing tests - it keeps its own.
     */
    private final Map<BlockPos, List<ItemFlight>> byCable = new LinkedHashMap<>();
    private final List<ItemFlight> departing = new ArrayList<>();
    // An item only sets off once the transaction that took it from its source is final.
    private final SnapshotJournal<Integer> departureJournal = new SnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() { return departing.size(); }

        @Override
        protected void revertToSnapshot(Integer snapshot) { departing.subList(snapshot, departing.size()).clear(); }

        @Override
        protected void onRootCommit(Integer originalState) {
            long now = now();
            for (ItemFlight flight : departing) {
                stagger(flight, now);
                BlockPos born = flight.current();
                in(born).add(flight);
                touch(born);
                announce(flight);
            }
            departing.clear();
        }
    };
    /** Where the last item to enter through each connector was, so the next one keeps behind it; see {@link #stagger}. */
    private final Map<EndpointKey, Departure> departures = new HashMap<>();
    private record Departure(long tick, int travelled) {}
    private final Map<PathKey, List<BlockPos>> paths = new HashMap<>();
    /**
     * Connectors that had no room for a resource earlier this tick. Nothing the network does within
     * a tick makes room (it only ever inserts), so once a resource is refused every later look for
     * the same pair is answered from here: a clogged network with hundreds of waiting items asks
     * each full chest once per tick instead of once per item. Cleared when the tick changes.
     */
    private final Set<NoRoomKey> noRoom = new HashSet<>();
    private long noRoomTick = Long.MIN_VALUE;
    private record NoRoomKey(EndpointKey key, ItemResource resource) {}
    private long lastTick = Long.MIN_VALUE;
    private int round;
    private boolean valid = true;

    /** A network with no level never talks to clients; tests build these, with a travel pace of their own. */
    public ItemCableNetwork(int batch, int interval, int travelTicks, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this(null, batch, interval, travelTicks, cables, endpoints);
    }

    public ItemCableNetwork(@Nullable ServerLevel level, int batch, int interval, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this(level, batch, interval, TRAVEL_TICKS_PER_BLOCK, cables, endpoints);
    }

    public ItemCableNetwork(@Nullable ServerLevel level, int batch, int interval, int travelTicks,
                            Set<BlockPos> cables, List<Endpoint> endpoints) {
        this.level = level;
        this.batch = batch;
        this.interval = Math.max(1, interval);
        this.travelTicks = Math.max(1, travelTicks);
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
        for (int index = 0; index < this.endpoints.size(); index++) indexByKey.put(this.endpoints.get(index).key(), index);
        budgets = new int[this.endpoints.size() + 1];
        renewBudgets();
    }

    /** What the connector at {@code index} may let in per interval; the shared entry gets the bare batch. */
    private int allowance(int index) {
        if (index == endpoints.size()) return batch;
        Endpoint endpoint = endpoints.get(index);
        if (endpoint.unbounded()) return MAX_PER_INTERVAL;
        return Math.min(MAX_PER_INTERVAL, batch + PER_UPGRADE * endpoint.upgrades());
    }

    private void renewBudgets() {
        for (int index = 0; index < budgets.length; index++) budgets[index] = allowance(index);
    }

    /**
     * Flood-fills the cables touching {@code start}, gives every one of them this network and takes
     * over the flights they were keeping while there was none.
     */

    public static ItemCableNetwork discover(ServerLevel level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<ItemCableBlockEntity> members = new ArrayList<>();
        ItemCableTier slowest = null;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        cables.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof ItemCableBlockEntity cable)) continue;
            members.add(cable);
            if (slowest == null || cable.tier().compareTo(slowest) < 0) slowest = cable.tier();
            for (Direction side : Direction.values()) {
                // A side with no link, cut by the wrench or bare, joins no cable and reaches no machine.
                if (!cable.getBlockState().getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                // Its own tier only: a link left over from before the tiers were kept apart still ends here.
                if (cable.getBlockState().getBlock() instanceof ItemCableBlock own && own.joins(level.getBlockState(neighbour))) {
                    if (cables.add(neighbour)) queue.add(neighbour);
                } else {
                    // Captured here rather than read per tick: changing a connector invalidates the
                    // network, so a rebuilt one always carries current modes.
                    SideMode mode = cable.connectors().mode(side);
                    // A face on "none" never moves anything; most faces border air or the ground.
                    if (mode == SideMode.NONE) continue;
                    // Redstone is the one thing read live: a signal flips too often to rebuild for.
                    endpoints.add(new CachedEndpoint(new EndpointKey(pos, side),
                            mode.allowsOutput(), mode.allowsInput() && !mode.allowsOutput(),
                            () -> cable.connectorActive(side),
                            cable.connectorPriority(side), cable.connectorAccepts(side), cable.connectorFilter(side),
                            cable.connectorColor(side), cable.connectorChannel(side), cable.connectorSpeedUpgrades(side),
                            level.getBlockState(neighbour).getBlock() instanceof TesseractBlock,
                            BlockCapabilityCache.create(
                                    Capabilities.Item.BLOCK, level, neighbour, side.getOpposite())));
                }
            }
        }
        var network = slowest == null ? new ItemCableNetwork(level, 0, 1, cables, endpoints)
                : new ItemCableNetwork(level, slowest.batch(), slowest.interval(), cables, endpoints);
        for (ItemCableBlockEntity member : members) member.setNetwork(network);
        network.adopt();
        return network;
    }

    public boolean isValid() { return valid; }

    public int batch() { return batch; }

    public int interval() { return interval; }

    public int size() { return cables.size(); }

    /** Items moved since the current interval began, through every connector together. */
    public int moved() {
        int moved = 0;
        for (int index = 0; index < budgets.length; index++) moved += allowance(index) - budgets[index];
        return moved;
    }

    /** The list the cable at {@code pos} keeps; its own when there is a world to ask. */
    private List<ItemFlight> in(BlockPos pos) {
        // Looked up every time, never remembered. A cable's block entity is replaced whenever its
        // chunk comes back, and a network that held on to the list of the one before would leave
        // whatever came back with the cable sitting there, moved by nobody.
        if (level != null && level.hasChunkAt(pos.getX(), pos.getZ())
                && level.getBlockEntity(pos) instanceof ItemCableBlockEntity cable) {
            return cable.flights();
        }
        // Without a world - the routing tests - the network keeps the lists itself.
        return byCable.computeIfAbsent(pos, key -> new ArrayList<>());
    }

    /** Notes that what is inside the cable at {@code pos} changed, so its chunk is written again. */
    private void touch(BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof ItemCableBlockEntity cable) cable.setChanged();
    }

    /** Every item currently travelling or waiting in the network. */
    public List<ItemFlight> flights() {
        List<ItemFlight> all = new ArrayList<>();
        for (BlockPos pos : cables) all.addAll(in(pos));
        return List.copyOf(all);
    }

    /** The flights inside the cable at {@code pos}; what that cable saves or drops. */
    public List<ItemFlight> flightsIn(BlockPos pos) { return List.copyOf(in(pos)); }

    /** Takes the flights inside the cable at {@code pos} out of the network, for dropping. */
    public List<ItemFlight> removeFlightsIn(BlockPos pos) {
        List<ItemFlight> removed = flightsIn(pos);
        in(pos).clear();
        touch(pos);
        for (ItemFlight flight : removed) announceEnd(flight);
        return removed;
    }

    /**
     * Drops every member's reference so the next tick rebuilds the network from what is left, and
     * leaves each flight with the cable it is inside, to be picked up by the next network.
     */
    public void invalidate(ServerLevel level) {
        valid = false;
        // Nothing to hand back: what is inside each cable stays with that cable, and is written
        // out with it. This is why a chunk going away no longer has to be pulled back in.
        for (BlockPos pos : cables) {
            if (level.hasChunkAt(pos.getX(), pos.getZ())
                    && level.getBlockEntity(pos) instanceof ItemCableBlockEntity cable) {
                cable.clearNetwork(this);
            }
        }
    }

    /**
     * Looks over what the cables already had inside them when this network was built - items
     * that were travelling before a cable changed, or that came back with their chunk. A flight
     * whose road is not part of this network is given a new destination from where it stands,
     * and waits in its cable if there is none. It never changes hands.
     */
    /** Puts flights inside the cables they stand in and looks their routes over, as when a
     *  network is built over cables that already had load in them. */
    public void adopt(List<ItemFlight> found) {
        for (ItemFlight flight : found) {
            BlockPos pos = flight.current();
            in(pos).add(flight);
            repair(flight, pos);
        }
    }

    /**
     * Looks over what the cables already had inside them when this network was built - items
     * that were travelling before a cable changed, or that came back with their chunk.
     */
    private void adopt() {
        for (BlockPos pos : cables) for (ItemFlight flight : in(pos)) repair(flight, pos);
    }

    /**
     * A flight whose road is not part of this network is given a new destination from where it
     * stands, and waits in its cable if there is none. It never changes hands.
     */
    private void repair(ItemFlight flight, BlockPos pos) {
        if (cables.containsAll(flight.path) && indexByKey.containsKey(flight.destination())) {
            // Its road is part of this network, so whatever made it wait is over.
            flight.waiting = false;
        } else if (!reroute(flight, pos)) {
            // No other destination for now, so it waits - but it keeps the road it was on.
            flight.waiting = true;
        }
        announce(flight);
    }

    /**
     * The handler a neighbour beyond the given cable face sees: one always-empty slot whose inserts
     * set items travelling to the other inventories on the network. Nothing can ever be extracted
     * from it, it only takes what that face's filter card lets in and what some inventory on that
     * face's colour and channel has room for right now, and it never sends an item back to the
     * block that is pushing it in, not even through another face that block touches.
     */
    public ResourceHandler<ItemResource> handlerFor(@Nullable EndpointKey key) {
        Integer known = key == null ? null : indexByKey.get(key);
        int slot = known == null ? endpoints.size() : known;
        Endpoint entry = known == null ? null : endpoints.get(known);
        return new ResourceHandler<>() {
            @Override
            public int size() { return 1; }

            @Override
            public ItemResource getResource(int index) { return ItemResource.EMPTY; }

            @Override
            public long getAmountAsLong(int index) { return 0; }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) { return allowance(slot); }

            @Override
            public boolean isValid(int index, ItemResource resource) {
                return entry == null || entry.accepts(resource);
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                if (index != 0 || (entry != null && !entry.accepts(resource))) return 0;
                return dispatch(resource, amount, key, entry, slot, transaction);
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }


    /**
     * Sets up to {@code amount} of {@code resource} travelling from the face {@code key} towards the
     * delivering endpoints on {@code entry}'s colour and channel, skipping the block beyond that
     * face and spending the budget of the connector at {@code slot}. Each candidate is asked,
     * without keeping anything, how much it would take right now, and the item leaves for the first
     * ones that have room: higher priorities first, and within one priority starting somewhere else
     * each interval so one inventory cannot hog everything that comes through. Without a face there
     * is no cable to travel through, so the item is handed straight over.
     */
    private int dispatch(ItemResource resource, int amount, @Nullable EndpointKey key, @Nullable Endpoint entry,
                         int slot, TransactionContext transaction) {
        long t = System.nanoTime();
        try {
            return route(resource, amount, key, entry, slot, transaction);
        } finally {
            nsDispatch += System.nanoTime() - t;
        }
    }

    private int route(ItemResource resource, int amount, @Nullable EndpointKey key, @Nullable Endpoint entry,
                      int slot, TransactionContext transaction) {
        int allowed = Math.min(amount, budgets[slot]);
        if (allowed <= 0 || resource.isEmpty() || endpoints.isEmpty()) return 0;
        BlockPos origin = key == null ? null : key.neighbour();
        DyeColor color = entry == null ? DyeColor.WHITE : entry.color();
        int channel = entry == null ? 0 : entry.channel();
        int moved = 0;
        for (List<Endpoint> rank : ranks) {
            int count = rank.size();
            for (int step = 0; step < count && moved < allowed; step++) {
                Endpoint endpoint = rank.get((round + step) % count);
                if (!serves(endpoint, color, channel, resource) || endpoint.key().neighbour().equals(origin)) continue;
                // A counting connector takes only what its neighbour is short of the level it keeps.
                int wanted = Math.min(allowed - moved, endpoint.allowance(resource, true));
                if (wanted <= 0) continue;
                int room;
                if (key == null) {
                    room = ResourceHandlerUtil.insertStacking(endpoint.handler(), resource, wanted, transaction);
                } else {
                    room = roomFor(endpoint, resource, wanted, transaction);
                    if (room > 0) {
                        departureJournal.updateSnapshots(transaction);
                        departing.add(new ItemFlight(ThreadLocalRandom.current().nextLong(), resource.toStack(room),
                                path(key.cablePos(), endpoint.key().cablePos()), key.side(),
                                endpoint.key().side(), color, channel, 0,
                                ticksPerBlock(travelTicks, entry == null ? 0 : entry.upgrades()), false));
                    }
                }
                moved += room;
            }
        }
        if (moved > 0) {
            budgetJournal.updateSnapshots(transaction);
            budgets[slot] -= moved;
        }
        return moved;
    }

    private static boolean serves(Endpoint endpoint, DyeColor color, int channel, ItemResource resource) {
        return endpoint.delivers() && endpoint.color() == color && endpoint.channel() == channel && endpoint.accepts(resource);
    }

    /**
     * How much of {@code resource} the endpoint would still take once everything already on its
     * way there has arrived, without leaving anything there. Counting the flights keeps two
     * sources from both sending to the last free slot, and keeps a lower priority from taking the
     * room a higher one was just promised.
     */
    private int roomFor(Endpoint endpoint, ItemResource resource, int amount, @Nullable TransactionContext transaction) {
        ResourceHandler<ItemResource> handler = endpoint.handler();
        if (handler == null) return 0;
        long now = level == null ? lastTick : level.getGameTime();
        if (now != noRoomTick) {
            noRoomTick = now;
            noRoom.clear();
        }
        NoRoomKey refused = new NoRoomKey(endpoint.key(), resource);
        if (noRoom.contains(refused)) return 0;
        // What is already bound there is reserved once per kind of item, not once per flight:
        // a dozen identical items in the air are one simulated insert, not twelve.
        Map<ItemResource, Integer> bound = new HashMap<>();
        reserve(flights(), endpoint.key(), bound);
        reserve(departing, endpoint.key(), bound);
        int room;
        Container container = level == null ? null : ContainerDelivery.blockContainer(level, endpoint.key().neighbour());
        if (container != null) {
            // A vanilla inventory is read directly. Other kinds of items on their way there are
            // counted as taking whole empty slots, which errs on the side of waiting a moment.
            ItemStack stack = resource.toStack(1);
            int maxStack = container.getMaxStackSize(stack);
            int taken = 0;
            for (Map.Entry<ItemResource, Integer> entry : bound.entrySet()) {
                taken += entry.getKey().equals(resource) ? entry.getValue() : Math.ceilDiv(entry.getValue(), maxStack) * maxStack;
            }
            room = Math.max(0, ContainerDelivery.room(container, stack, endpoint.key().side().getOpposite(), amount + taken) - taken);
        } else {
            try (Transaction probe = Transaction.open(transaction)) {
                for (Map.Entry<ItemResource, Integer> entry : bound.entrySet()) {
                    ResourceHandlerUtil.insertStacking(handler, entry.getKey(), entry.getValue(), probe);
                }
                room = ResourceHandlerUtil.insertStacking(handler, resource, amount, probe);
            }
        }
        if (room <= 0) noRoom.add(refused);
        return room;
    }

    /** Adds up, by kind of item, everything among {@code bound} that is heading for {@code key}. */
    private static void reserve(List<ItemFlight> bound, EndpointKey key, Map<ItemResource, Integer> totals) {
        for (ItemFlight flight : bound) {
            if (flight.to != key.side() || !flight.path.getLast().equals(key.cablePos())) continue;
            totals.merge(ItemResource.of(flight.stack), flight.stack.getCount(), Integer::sum);
        }
    }

    /**
     * Every tick the flights move on and the ones that reached their exit are handed over; once per
     * interval the budgets are renewed and the extract-only connectors pump. Every member cable
     * calls this every tick, only the first call acts.
     */
    /**
     * While {@code /futuretech perf} is on, where this network's tick goes is logged every 100
     * ticks: moving flights, pumping, deciding destinations, inserting on arrival, and the packets
     * that tell clients about departures and arrivals. Off, none of this runs.
     */
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private long nsAdvance, nsPump, nsDispatch, nsInsert, nsAnnounce, nsEnd;
    private int logTicks, inserts, announces, ends, containerInserts;

    public void tick(long gameTime) {
        if (gameTime == lastTick) return;
        lastTick = gameTime;
        TickProfiler.networkTicked(cables.size());
        boolean newInterval = Math.floorMod(gameTime, interval) == 0;
        boolean profiling = TickProfiler.enabled();
        long t0 = profiling ? System.nanoTime() : 0;
        advanceFlights(newInterval);
        if (profiling) {
            nsAdvance += System.nanoTime() - t0;
            if (++logTicks >= 100) {
                int waiting = 0;
                List<ItemFlight> all = flights();
                for (ItemFlight flight : all) if (flight.waiting) waiting++;
                LOG.info("[perf] item network {} cables: flights={} waiting={} | us per 100 ticks: advance={} pump={} dispatch={} insert={} ({} direct of {}) announce={} ({}) end={} ({})",
                        cables.size(), all.size(), waiting, nsAdvance / 1000, nsPump / 1000, nsDispatch / 1000, nsInsert / 1000,
                        containerInserts, inserts, nsAnnounce / 1000, announces, nsEnd / 1000, ends);
                nsAdvance = nsPump = nsDispatch = nsInsert = nsAnnounce = nsEnd = 0;
                logTicks = inserts = announces = ends = containerInserts = 0;
            }
        }
        if (!newInterval) return;
        long t1 = profiling ? System.nanoTime() : 0;
        pump(gameTime);
        if (profiling) nsPump += System.nanoTime() - t1;
    }

    private void pump(long gameTime) {
        renewBudgets();
        round = (int) Math.floorMod(gameTime / interval, Integer.MAX_VALUE);
        // Extract-only connectors act as pumps: a chest never pushes, so a connector on one has to
        // do the pulling or nothing ever comes out. What they pull goes through the same entrance
        // as a push, so it sets off for another inventory or stays where it was. Pumps take turns
        // the same way sinks do: by priority, and round-robin within one priority.
        for (List<Endpoint> rank : ranks) {
            int count = rank.size();
            for (int step = 0; step < count; step++) {
                Endpoint endpoint = rank.get((round + step) % count);
                if (!endpoint.pulls()) continue;
                int budget = budgets[indexByKey.get(endpoint.key())];
                ResourceHandler<ItemResource> source = endpoint.handler();
                if (source == null || budget <= 0) continue;
                if (endpoint.counting()) drain(endpoint, source, budget);
                else ResourceHandlerUtil.moveStacking(source, handlerFor(endpoint.key()), endpoint::accepts, budget, null);
            }
        }
    }

    /**
     * Pulls from a connector whose card keeps levels, one kind at a time: each is taken only down
     * to the level the card keeps of it, and never past the connector's budget for the interval.
     * The plain pump moves whatever it finds, which is why this one exists.
     */
    private void drain(Endpoint endpoint, ResourceHandler<ItemResource> source, int budget) {
        ResourceHandler<ItemResource> destination = handlerFor(endpoint.key());
        int moved = 0;
        for (int index = 0; index < source.size() && moved < budget; index++) {
            ItemResource resource = source.getResource(index);
            if (resource.isEmpty() || !endpoint.accepts(resource)) continue;
            int over = endpoint.allowance(resource, false);
            if (over <= 0) continue;
            moved += ResourceHandlerUtil.moveStacking(source, destination, resource::equals,
                    Math.min(budget - moved, over), null);
        }
    }

    /** Moves every flight one tick along; arrivals are delivered, and waiting ones retry each interval. */
    private void advanceFlights(boolean retryWaiting) {
        List<ItemFlight> moved = new ArrayList<>();
        for (BlockPos pos : cables) {
            List<ItemFlight> here = in(pos);
            if (here.isEmpty()) continue;
            boolean changed = false;
            for (Iterator<ItemFlight> it = here.iterator(); it.hasNext(); ) {
                ItemFlight flight = it.next();
                if (flight.waiting) {
                    if (retryWaiting && arrive(flight)) { it.remove(); changed = true; continue; }
                } else {
                    flight.travelled++;
                    changed = true;
                    if (flight.arrived() && arrive(flight)) { it.remove(); continue; }
                }
                // Crossing into the next cable is a move between two cables' own lists.
                if (!flight.current().equals(pos)) { it.remove(); changed = true; moved.add(flight); }
            }
            if (changed) touch(pos);
        }
        for (ItemFlight flight : moved) {
            BlockPos pos = flight.current();
            in(pos).add(flight);
            touch(pos);
        }
    }

    /**
     * Hands the flight to its destination. What does not fit looks for another inventory from the
     * cable it is in; if there is none it waits at the exit face. Returns true once nothing is left.
     */
    private boolean arrive(ItemFlight flight) {
        Integer index = indexByKey.get(flight.destination());
        Endpoint destination = index == null ? null : endpoints.get(index);
        ItemResource resource = ItemResource.of(flight.stack);
        if (destination != null && serves(destination, flight.color, flight.channel, resource)) {
            Container container = level == null ? null : ContainerDelivery.blockContainer(level, destination.key().neighbour());
            long t = System.nanoTime();
            int inserted = container != null
                    ? ContainerDelivery.insert(container, flight.stack, destination.key().side().getOpposite())
                    : ResourceHandlerUtil.insertStacking(destination.handler(), resource, flight.stack.getCount(), null);
            nsInsert += System.nanoTime() - t;
            inserts++;
            if (container != null) containerInserts++;
            flight.stack.shrink(inserted);
            if (flight.stack.isEmpty()) {
                announceEnd(flight);
                return true;
            }
        }
        if (reroute(flight, flight.path.getLast())) return false;
        if (!flight.waiting) {
            flight.waiting = true;
            announce(flight);
        }
        return false;
    }

    /**
     * Gives the flight a new destination reachable from {@code cable}, avoiding the one it was
     * heading for; the item sets off again from that cable's centre. False when nothing on its
     * colour and channel has room.
     */
    private boolean reroute(ItemFlight flight, BlockPos cable) {
        EndpointKey previous = flight.destination();
        ItemResource resource = ItemResource.of(flight.stack);
        for (List<Endpoint> rank : ranks) {
            int count = rank.size();
            for (int step = 0; step < count; step++) {
                Endpoint endpoint = rank.get((round + step) % count);
                if (endpoint.key().equals(previous) || !serves(endpoint, flight.color, flight.channel, resource)) continue;
                if (roomFor(endpoint, resource, flight.stack.getCount(), null) <= 0) continue;
                // A flight at its exit face turns back from there; one caught mid-cable restarts
                // from that cable's centre.
                flight.travelled = flight.arrived() ? 0 : flight.ticksPerBlock / 2;
                flight.path = path(cable, endpoint.key().cablePos());
                flight.from = flight.to;
                flight.to = endpoint.key().side();
                flight.waiting = false;
                // Several may turn back from the same face in one tick, when a chest beyond it opens up.
                stagger(flight, now());
                announce(flight);
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps a flight half a cable behind the one that entered through the same connector before
     * it. A tesseract, or a chest that was waiting on a full line, hands over several items in one
     * tick; set off together they would travel as one heap, drawn on top of each other. So each
     * starts that much further back, at a negative count of ticks: still inside the block it came
     * from, and drawn only once it crosses the entry face.
     */
    /** The tick being played; a network without a level, as the tests build, counts its own ticks from zero. */
    private long now() { return level == null ? Math.max(lastTick, 0) : level.getGameTime(); }

    private void stagger(ItemFlight flight, long now) {
        EndpointKey entry = new EndpointKey(flight.path.getFirst(), flight.from);
        Departure last = departures.get(entry);
        if (last != null) {
            long ahead = last.travelled + (now - last.tick);
            int gap = Math.max(1, flight.ticksPerBlock / 2);
            flight.travelled = (int) Math.min(flight.travelled, ahead - gap);
        }
        departures.put(entry, new Departure(now, flight.travelled));
    }

    /** Tells the players watching the flight's cable where it is and where it is going. */
    private void announce(ItemFlight flight) {
        if (level == null) return;
        long t = System.nanoTime();
        var payload = new ItemJourneyPayload(flight.id, flight.path, flight.from, flight.to, flight.stack.copy(),
                flight.ticksPerBlock, flight.travelled, flight.waiting);
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(flight.current()), payload);
        nsAnnounce += System.nanoTime() - t;
        announces++;
    }

    public void announceEnd(ItemFlight flight) {
        if (level == null) return;
        long t = System.nanoTime();
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(flight.current()),
                new ItemJourneyEndPayload(flight.id));
        nsEnd += System.nanoTime() - t;
        ends++;
    }

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
                        ArrayDeque<BlockPos> path = new ArrayDeque<>();
                        for (BlockPos step = to; !step.equals(from); step = previous.get(step)) path.addFirst(step);
                        path.addFirst(from);
                        return List.copyOf(path);
                    }
                    queue.add(next);
                }
            }
            return List.of(from);
        });
    }

    /** {@code mayDeliver} and {@code mayPull} are the connector's settings; {@code active} is whether redstone lets it work now. */
    private record CachedEndpoint(EndpointKey key, boolean mayDeliver, boolean mayPull, BooleanSupplier active, int priority,
                                 Predicate<ItemResource> filter, ItemStack card, DyeColor color, int channel, int upgrades,
                                 boolean unbounded,
                                 BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> cache) implements Endpoint {
        @Override
        public boolean delivers() { return mayDeliver && active.getAsBoolean(); }

        @Override
        public boolean pulls() { return mayPull && active.getAsBoolean(); }

        @Override
        public boolean accepts(ItemResource resource) { return filter.test(resource); }

        // The card is the connector's own stack, so a level changed on the screen is read at once.
        @Override
        public boolean counting() { return ItemFilterItem.counting(card); }

        @Override
        public int allowance(ItemResource resource, boolean inserting) {
            int level = ItemFilterItem.level(card, resource);
            if (level == ItemFilterItem.NO_LEVEL) return Integer.MAX_VALUE;
            int held = held(resource);
            return Math.max(0, inserting ? level - held : held - level);
        }

        /** How much of a resource the block beyond is holding right now. */
        private int held(ItemResource resource) {
            ResourceHandler<ItemResource> handler = handler();
            if (handler == null) return 0;
            int total = 0;
            for (int index = 0; index < handler.size(); index++) {
                if (resource.equals(handler.getResource(index))) total += handler.getAmountAsInt(index);
            }
            return total;
        }

        @Override
        public @Nullable ResourceHandler<ItemResource> handler() { return cache.getCapability(); }
    }
}
