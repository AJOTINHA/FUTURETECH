package dev.futuretech.redstone;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.RedstoneCableBlock;
import dev.futuretech.block.entity.RedstoneCableBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.PipeBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A group of touching redstone cables. Nothing is buffered and nothing ticks: the network is a
 * wire. Every connector set to insert reads the signal of the block beside it into the cable,
 * every connector set to extract gives a signal out of it, and what it gives is the strongest
 * signal read on its line — the colour and channel it shares with the readers. A line nobody
 * reads gives out nothing.
 *
 * <p>It works the way vanilla redstone does, by being told. A cable hears its neighbours change
 * and asks the network to read that cable's connectors again; when a line's strength moves, the
 * blocks beside the connectors giving that line out are told in turn, and read the new strength
 * from the cable when they ask. While the network reads, its own connectors answer nothing, so a
 * block of stone a connector gives into is not read back as a signal of its own — a wire that
 * heard its own echo would never go quiet.
 *
 * <p>The network keeps positions rather than block entities and reaches the world through a
 * {@link Wiring}, so it can be tried with a made-up world; {@link #discover} builds the real one.
 */
public final class RedstoneCableNetwork {
    /** A colour and channel; every pair is a wire of its own. */
    public record Line(DyeColor color, int channel) {}

    /**
     * One connector of the network: the cable face it is on, its line, and what it does there.
     * A connector may read, give out, or both; a reader with the sensor on reads the block the
     * way a comparator does; {@code active} is whether redstone lets it work right now, asked
     * as it goes because a signal flips too often to rebuild for.
     */
    public record Endpoint(BlockPos cablePos, Direction side, Line line, boolean reads, boolean emits,
                           boolean sensor, BooleanSupplier active) {
        public Endpoint(BlockPos cablePos, Direction side, Line line, boolean reads, boolean emits, BooleanSupplier active) {
            this(cablePos, side, line, reads, emits, false, active);
        }

        /** The block beside the connector. */
        public BlockPos neighbour() { return cablePos.relative(side); }
    }

    /** What the network asks of the world: a neighbour's signal, and telling one it changed. */
    public interface Wiring {
        /**
         * The signal the block at {@code neighbour} gives towards the cable, which lies {@code side}
         * from it; with {@code sensor}, what a comparator would read off the block instead — how
         * full it is — when the block has such a reading.
         */
        int read(BlockPos neighbour, Direction side, boolean sensor);

        /** Tells the block beside the connector on {@code side} of the cable at {@code cablePos} to ask again. */
        void changed(BlockPos cablePos, Direction side);
    }

    private final Wiring wiring;
    private final Set<BlockPos> cables;
    private final List<Endpoint> endpoints;
    /** What every reader last read, by its index in {@link #endpoints}; an inactive reader reads 0. */
    private final int[] readings;
    private final Map<Line, List<Integer>> readersByLine = new HashMap<>();
    private final Map<Line, List<Endpoint>> emittersByLine = new HashMap<>();
    private final Map<BlockPos, List<Integer>> readersByCable = new HashMap<>();
    /** The emitter on each face of each cable, by {@code Direction.ordinal()}; asked on every redstone query. */
    private final Map<BlockPos, Endpoint[]> emittersByCable = new HashMap<>();
    private final Map<Line, Integer> strengths = new HashMap<>();
    /** While the network reads, its own connectors give out nothing; see the class comment. */
    private boolean silent;
    private boolean valid = true;
    /** Whether a retired network has told the blocks it gave into; once is enough. */
    private boolean leftoversAnnounced;

    public RedstoneCableNetwork(Wiring wiring, Set<BlockPos> cables, List<Endpoint> endpoints) {
        this.wiring = wiring;
        this.cables = Set.copyOf(cables);
        this.endpoints = List.copyOf(endpoints);
        this.readings = new int[this.endpoints.size()];
        for (int index = 0; index < this.endpoints.size(); index++) {
            Endpoint endpoint = this.endpoints.get(index);
            if (endpoint.reads()) {
                readersByLine.computeIfAbsent(endpoint.line(), line -> new ArrayList<>()).add(index);
                readersByCable.computeIfAbsent(endpoint.cablePos(), pos -> new ArrayList<>()).add(index);
            }
            if (endpoint.emits()) {
                emittersByLine.computeIfAbsent(endpoint.line(), line -> new ArrayList<>()).add(endpoint);
                emittersByCable.computeIfAbsent(endpoint.cablePos(), pos -> new Endpoint[Direction.values().length])
                        [endpoint.side().ordinal()] = endpoint;
            }
        }
    }

    /**
     * Flood-fills the cables touching {@code start}, gives every one of them this network, reads
     * every connector and tells every block a connector gives into, so the blocks around a line
     * that was just laid, joined, cut or set up read what the line carries now.
     */
    public static RedstoneCableNetwork discover(ServerLevel level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<RedstoneCableBlockEntity> members = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        cables.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable)) continue;
            members.add(cable);
            for (Direction side : Direction.values()) {
                // A side with no link, cut by the wrench or bare, joins no cable and reaches no block.
                if (!cable.getBlockState().getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                if (level.getBlockState(neighbour).getBlock() instanceof RedstoneCableBlock) {
                    if (cables.add(neighbour)) queue.add(neighbour);
                } else {
                    // Captured here rather than read per change: setting a connector rebuilds the
                    // network, so a rebuilt one always carries current modes and lines.
                    SideMode mode = cable.connectors().mode(side);
                    // A face on "none" neither reads nor gives; most faces border air or the ground.
                    if (mode == SideMode.NONE) continue;
                    // The words are the cable's here, not the neighbour's: on every other cable
                    // "insert" puts the resource into the block beside it, but a signal lives in
                    // the wire, so a lever inserts its signal into the cable and a lamp extracts
                    // it. Insert is the mode that allows output, and that connector reads.
                    endpoints.add(new Endpoint(pos, side,
                            new Line(cable.connectorColor(side), cable.connectorChannel(side)),
                            mode.allowsOutput(), mode.allowsInput(), cable.isSensor(side), () -> cable.connectorActive(side)));
                }
            }
        }
        var network = new RedstoneCableNetwork(new LevelWiring(level), cables, endpoints);
        for (RedstoneCableBlockEntity member : members) {
            // A member still on a live network that did not reach here — its chunk loaded after
            // this one's, say — retires that network, whose leftover members find their own.
            RedstoneCableNetwork previous = member.network();
            if (previous != null && previous != network && previous.isValid()) previous.invalidate(level);
            member.setNetwork(network);
        }
        network.readAll();
        network.announce();
        return network;
    }

    public boolean isValid() { return valid; }

    /** The cables of this network. */
    public Set<BlockPos> cables() { return cables; }

    /**
     * Retires the network and books every one of its cables for a walk next tick. Its cables keep
     * answering from it until then, when the first of them to tick walks what is still joined
     * and the rest find themselves on a live network, or walk what was cut off from it: a run cut
     * in two comes back as two. Booked rather than walked here because a network is retired from
     * inside block changes and chunk loads, where the blocks around it cannot be told anything
     * yet; a burst of changes books the same tick once.
     */
    public void invalidate(ServerLevel level) {
        if (!valid) return;
        valid = false;
        for (BlockPos pos : cables) book(level, pos);
    }

    /** Books the cable at {@code pos} for a walk next tick, if its chunk is loaded to take the booking. */
    public static void book(ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos)) level.scheduleTick(pos, ModBlocks.REDSTONE_CABLE.get(), 1);
    }

    /**
     * Once its cables are on new networks, a retired network tells every block a connector of
     * its gave into to ask again: the ones still given into read the new strength, and the ones
     * whose cable or connector went read nothing, the way they should.
     */
    public void announceLeftovers(ServerLevel level) {
        if (leftoversAnnounced) return;
        leftoversAnnounced = true;
        for (Endpoint endpoint : endpoints) {
            if (endpoint.emits() && level.hasChunkAt(endpoint.neighbour())) wiring.changed(endpoint.cablePos(), endpoint.side());
        }
    }

    /** The strongest signal read on {@code line}, 0 when nothing reads on it. */
    public int strength(Line line) { return strengths.getOrDefault(line, 0); }

    /**
     * What the connector on {@code side} of the cable at {@code cablePos} gives out: its line's
     * strength if it is set to insert and redstone lets it, else nothing; and nothing at all while
     * the network is reading.
     */
    public int emitted(BlockPos cablePos, Direction side) {
        if (silent) return 0;
        Endpoint[] emitters = emittersByCable.get(cablePos);
        Endpoint emitter = emitters == null ? null : emitters[side.ordinal()];
        if (emitter == null || !emitter.active().getAsBoolean()) return 0;
        return strength(emitter.line());
    }

    /** Runs {@code reading} with the network's own connectors answering nothing, the way its own reads go. */
    public void silently(Runnable reading) {
        boolean was = silent;
        silent = true;
        try {
            reading.run();
        } finally {
            silent = was;
        }
    }

    /** Reads every reader and works every line out, telling nobody: the first picture of a new network. */
    private void readAll() {
        silently(() -> {
            for (int index = 0; index < endpoints.size(); index++) {
                if (endpoints.get(index).reads()) readings[index] = read(index);
            }
        });
        strengths.clear();
        readersByLine.keySet().forEach(this::recompute);
    }

    /**
     * Reads the connectors of the cable at {@code cablePos} again, after a block beside it
     * changed, and tells the blocks beside every connector giving out a line whose strength
     * moved. Whether anything moved.
     */
    public boolean sample(BlockPos cablePos) {
        List<Integer> readers = readersByCable.get(cablePos);
        if (readers == null) return false;
        Set<Line> touched = new HashSet<>();
        silently(() -> {
            for (int index : readers) {
                int read = read(index);
                if (read == readings[index]) continue;
                readings[index] = read;
                touched.add(endpoints.get(index).line());
            }
        });
        boolean moved = false;
        for (Line line : touched) {
            if (recompute(line)) {
                moved = true;
                emittersByLine.getOrDefault(line, List.of()).forEach(emitter -> wiring.changed(emitter.cablePos(), emitter.side()));
            }
        }
        return moved;
    }

    /** Tells every block a connector gives into to ask again. */
    public void announce() {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.emits()) wiring.changed(endpoint.cablePos(), endpoint.side());
        }
    }

    /**
     * Tells the blocks beside the connectors of one cable that give out to ask again: the cable's
     * own signal changed, and with it whether redstone lets those connectors work.
     */
    public void announce(BlockPos cablePos) {
        Endpoint[] emitters = emittersByCable.get(cablePos);
        if (emitters == null) return;
        for (Endpoint emitter : emitters) {
            if (emitter != null) wiring.changed(emitter.cablePos(), emitter.side());
        }
    }

    private int read(int index) {
        Endpoint endpoint = endpoints.get(index);
        if (!endpoint.active().getAsBoolean()) return 0;
        return Math.clamp(wiring.read(endpoint.neighbour(), endpoint.side(), endpoint.sensor()), 0, 15);
    }

    /** Works {@code line} out from its readers again; whether its strength moved. */
    private boolean recompute(Line line) {
        int strongest = 0;
        for (int index : readersByLine.getOrDefault(line, List.of())) strongest = Math.max(strongest, readings[index]);
        Integer previous = strongest == 0 ? strengths.remove(line) : strengths.put(line, strongest);
        return (previous == null ? 0 : previous) != strongest;
    }

    /**
     * The real world: a neighbour's signal is asked the way a mechanism asks — from the cable,
     * looking that way, so a block of stone given into by something else counts — or, with the
     * sensor on, the way a comparator asks, which is how full the block is when it can say; and
     * a block given into is told the way a repeater tells the block on its front, and the blocks
     * around that block with it, so a block of stone passes the strength on.
     */
    private record LevelWiring(ServerLevel level) implements Wiring {
        @Override
        public int read(BlockPos neighbour, Direction side, boolean sensor) {
            if (sensor) {
                var state = level.getBlockState(neighbour);
                // The comparator passes the face of the block it looks at; the cable's is the same one.
                if (state.hasAnalogOutputSignal()) return state.getAnalogOutputSignal(level, neighbour, side.getOpposite());
            }
            return level.getSignal(neighbour, side);
        }

        @Override
        public void changed(BlockPos cablePos, Direction side) {
            BlockPos neighbour = cablePos.relative(side);
            level.neighborChanged(neighbour, ModBlocks.REDSTONE_CABLE.get(), null);
            level.updateNeighborsAt(neighbour, ModBlocks.REDSTONE_CABLE.get());
        }
    }
}
