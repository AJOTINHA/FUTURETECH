package dev.futuretech.transfer;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A group of touching item cables. Unlike the energy network it holds nothing: an item pushed into
 * any cable of the group goes straight through to an adjacent inventory in the same transaction,
 * so nothing is ever lost in a cable and a full network simply refuses the push. Once per tick
 * the group also pumps from the inventories behind its extract-only connectors. Every connector an
 * item can enter through has its own budget, renewed every {@code interval} ticks: the tier's
 * batch plus {@value #PER_UPGRADE} per speed upgrade on that connector, up to a stack. What is not
 * used in one interval does not carry over.
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

        /** The connector's colour; items only cross between connectors of the same colour and channel. */
        default DyeColor color() { return DyeColor.WHITE; }

        /** The connector's channel number. */
        default int channel() { return 0; }

        /** Speed upgrades on the connector; each one widens the budget of what enters through it. */
        default int upgrades() { return 0; }

        @Nullable ResourceHandler<ItemResource> handler();
    }

    /** Items each speed upgrade adds to a connector's budget per interval. */
    public static final int PER_UPGRADE = 2;
    /** No connector moves more than a stack per interval, however many upgrades it carries. */
    public static final int MAX_PER_INTERVAL = 64;

    private final int batch;
    private final int interval;
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
    private long lastTick = Long.MIN_VALUE;
    private int round;
    private boolean valid = true;

    public ItemCableNetwork(int batch, int interval, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this.batch = batch;
        this.interval = Math.max(1, interval);
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
        return Math.min(MAX_PER_INTERVAL, batch + PER_UPGRADE * endpoints.get(index).upgrades());
    }

    private void renewBudgets() {
        for (int index = 0; index < budgets.length; index++) budgets[index] = allowance(index);
    }

    /** Flood-fills the cables touching {@code start} and gives every one of them this network. */
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
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                if (level.getBlockState(neighbour).getBlock() instanceof ItemCableBlock) {
                    if (cables.add(neighbour)) queue.add(neighbour);
                } else {
                    // Captured here rather than read per tick: changing a connector invalidates the
                    // network, so a rebuilt one always carries current modes.
                    SideMode mode = cable.connectors().mode(side);
                    endpoints.add(new CachedEndpoint(new EndpointKey(pos, side),
                            mode.allowsOutput(), mode.allowsInput() && !mode.allowsOutput(),
                            cable.connectorPriority(side), cable.connectorAccepts(side),
                            cable.connectorColor(side), cable.connectorChannel(side), cable.connectorSpeedUpgrades(side),
                            BlockCapabilityCache.create(
                                    Capabilities.Item.BLOCK, level, neighbour, side.getOpposite())));
                }
            }
        }
        var network = slowest == null ? new ItemCableNetwork(0, 1, cables, endpoints)
                : new ItemCableNetwork(slowest.batch(), slowest.interval(), cables, endpoints);
        members.forEach(member -> member.setNetwork(network));
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

    /** Drops every member's reference so the next tick rebuilds the network from what is left. */
    public void invalidate(ServerLevel level) {
        valid = false;
        for (BlockPos pos : cables) {
            if (level.hasChunkAt(pos.getX(), pos.getZ())
                    && level.getBlockEntity(pos) instanceof ItemCableBlockEntity cable) {
                cable.clearNetwork(this);
            }
        }
    }

    /**
     * The handler a neighbour beyond the given cable face sees: one always-empty slot whose inserts
     * pass straight through to the other inventories on the network. Nothing can ever be extracted
     * from it, it only takes what that face's filter card lets in, what comes in only goes out on
     * that face's colour and channel, and it never hands an item back to the block that is pushing
     * it in, not even through another face that block touches.
     */
    public ResourceHandler<ItemResource> handlerFor(@Nullable EndpointKey key) {
        BlockPos origin = key == null ? null : key.neighbour();
        Integer known = key == null ? null : indexByKey.get(key);
        int slot = known == null ? endpoints.size() : known;
        Endpoint entry = known == null ? null : endpoints.get(known);
        DyeColor color = entry == null ? DyeColor.WHITE : entry.color();
        int channel = entry == null ? 0 : entry.channel();
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
                return deliver(resource, amount, origin, color, channel, slot, transaction);
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /**
     * Hands up to {@code amount} of {@code resource} to the delivering endpoints of {@code color} on
     * {@code channel}, skipping the block at {@code origin} and spending the budget of the connector
     * at {@code slot}. Higher priorities are offered everything first; within one priority the round
     * starts at a different endpoint each interval so one inventory cannot hog everything that
     * comes through.
     */
    private int deliver(ItemResource resource, int amount, @Nullable BlockPos origin, DyeColor color, int channel,
                        int slot, TransactionContext transaction) {
        int allowed = Math.min(amount, budgets[slot]);
        if (allowed <= 0 || resource.isEmpty() || endpoints.isEmpty()) return 0;
        int moved = 0;
        for (List<Endpoint> rank : ranks) {
            int count = rank.size();
            for (int step = 0; step < count && moved < allowed; step++) {
                Endpoint endpoint = rank.get((round + step) % count);
                if (!endpoint.delivers() || endpoint.color() != color || endpoint.channel() != channel
                        || endpoint.key().neighbour().equals(origin) || !endpoint.accepts(resource)) continue;
                moved += ResourceHandlerUtil.insertStacking(endpoint.handler(), resource, allowed - moved, transaction);
            }
        }
        if (moved > 0) {
            budgetJournal.updateSnapshots(transaction);
            budgets[slot] -= moved;
        }
        return moved;
    }

    /**
     * Renews the budget and pumps once per interval; every member cable calls this every tick,
     * and only the first call on the interval's first tick acts.
     */
    public void tick(long gameTime) {
        if (gameTime == lastTick || Math.floorMod(gameTime, interval) != 0) return;
        lastTick = gameTime;
        renewBudgets();
        round = (int) Math.floorMod(gameTime / interval, Integer.MAX_VALUE);
        // Extract-only connectors act as pumps: a chest never pushes, so a connector on one has to
        // do the pulling or nothing ever comes out. What they pull goes through the same pass-through
        // as a push, so it lands in another inventory or stays where it was. Pumps take turns the
        // same way sinks do: by priority, and round-robin within one priority.
        for (List<Endpoint> rank : ranks) {
            int count = rank.size();
            for (int step = 0; step < count; step++) {
                Endpoint endpoint = rank.get((round + step) % count);
                if (!endpoint.pulls()) continue;
                int budget = budgets[indexByKey.get(endpoint.key())];
                ResourceHandler<ItemResource> source = endpoint.handler();
                if (source == null || budget <= 0) continue;
                ResourceHandlerUtil.moveStacking(source, handlerFor(endpoint.key()), endpoint::accepts, budget, null);
            }
        }
    }

    private record CachedEndpoint(EndpointKey key, boolean delivers, boolean pulls, int priority,
                                 Predicate<ItemResource> filter, DyeColor color, int channel, int upgrades,
                                 BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> cache) implements Endpoint {
        @Override
        public boolean accepts(ItemResource resource) { return filter.test(resource); }

        @Override
        public @Nullable ResourceHandler<ItemResource> handler() { return cache.getCapability(); }
    }
}
