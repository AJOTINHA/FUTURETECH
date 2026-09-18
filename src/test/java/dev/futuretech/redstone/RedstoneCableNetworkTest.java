package dev.futuretech.redstone;

import dev.futuretech.redstone.RedstoneCableNetwork.Endpoint;
import dev.futuretech.redstone.RedstoneCableNetwork.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The network over a made-up world: what the readers read is what the emitters on their line
 * give out, and nobody else's. The world here is a map of signals and a list of who was told.
 */
class RedstoneCableNetworkTest {
    private static final BlockPos CABLE = BlockPos.ZERO;
    private static final BlockPos OTHER = BlockPos.ZERO.east(2);
    private static final Line WHITE = new Line(DyeColor.WHITE, 0);
    private static final Line RED = new Line(DyeColor.RED, 0);
    private static final Line WHITE_ON_SEVEN = new Line(DyeColor.WHITE, 7);

    /** The blocks around the cables: a signal per position, and a note of every block told to ask again. */
    private static final class World implements RedstoneCableNetwork.Wiring {
        final Map<BlockPos, Integer> signals = new HashMap<>();
        final List<BlockPos> told = new ArrayList<>();
        /** What the block being read would see coming out of the cable at that moment, if it looked. */
        RedstoneCableNetwork network;
        int echoed = -1;

        /** What a comparator would read off the block, where the block has such a reading. */
        final Map<BlockPos, Integer> fill = new HashMap<>();

        @Override
        public int read(BlockPos neighbour, Direction side, boolean sensor) {
            if (network != null) echoed = Math.max(echoed, network.emitted(neighbour.relative(side.getOpposite()), side));
            if (sensor && fill.containsKey(neighbour)) return fill.get(neighbour);
            return signals.getOrDefault(neighbour, 0);
        }

        @Override
        public void changed(BlockPos cablePos, Direction side) { told.add(cablePos.relative(side)); }

        void set(BlockPos neighbour, int signal) { signals.put(neighbour, signal); }
    }

    private static Endpoint reader(BlockPos cable, Direction side, Line line) {
        return new Endpoint(cable, side, line, true, false, () -> true);
    }

    private static Endpoint emitter(BlockPos cable, Direction side, Line line) {
        return new Endpoint(cable, side, line, false, true, () -> true);
    }

    private static RedstoneCableNetwork network(World world, Endpoint... endpoints) {
        var network = new RedstoneCableNetwork(world, Set.of(CABLE, OTHER), List.of(endpoints));
        world.network = network;
        return network;
    }

    @Test
    void anEmitterGivesOutTheStrongestSignalReadOnItsLine() {
        var world = new World();
        var network = network(world,
                reader(CABLE, Direction.NORTH, WHITE), reader(CABLE, Direction.SOUTH, WHITE),
                emitter(OTHER, Direction.EAST, WHITE));
        assertEquals(0, network.emitted(OTHER, Direction.EAST), "nothing read yet");

        world.set(CABLE.north(), 7);
        assertTrue(network.sample(CABLE));
        assertEquals(7, network.strength(WHITE));
        assertEquals(7, network.emitted(OTHER, Direction.EAST));
        assertEquals(List.of(OTHER.east()), world.told, "the block the emitter gives into is told");

        world.set(CABLE.south(), 12);
        assertTrue(network.sample(CABLE));
        assertEquals(12, network.emitted(OTHER, Direction.EAST), "the stronger of the two");

        world.set(CABLE.south(), 3);
        assertTrue(network.sample(CABLE));
        assertEquals(7, network.emitted(OTHER, Direction.EAST), "the other one still reads seven");

        world.set(CABLE.north(), 0);
        world.set(CABLE.south(), 0);
        assertTrue(network.sample(CABLE));
        assertEquals(0, network.emitted(OTHER, Direction.EAST));
        assertEquals(4, world.told.size(), "told on every move, and only on a move");
    }

    @Test
    void nothingIsToldWhenNothingMoved() {
        var world = new World();
        var network = network(world, reader(CABLE, Direction.NORTH, WHITE), emitter(OTHER, Direction.EAST, WHITE));
        world.set(CABLE.north(), 5);
        assertTrue(network.sample(CABLE));
        world.told.clear();
        assertFalse(network.sample(CABLE), "read again, same five");
        assertFalse(network.sample(OTHER), "a cable with no reader has nothing to read");
        assertTrue(world.told.isEmpty());
    }

    @Test
    void linesKeepToThemselves() {
        var world = new World();
        var network = network(world,
                reader(CABLE, Direction.NORTH, WHITE), reader(CABLE, Direction.SOUTH, RED),
                reader(CABLE, Direction.UP, WHITE_ON_SEVEN),
                emitter(OTHER, Direction.EAST, WHITE), emitter(OTHER, Direction.WEST, RED),
                emitter(OTHER, Direction.UP, WHITE_ON_SEVEN));
        world.set(CABLE.north(), 15);
        network.sample(CABLE);
        assertEquals(15, network.emitted(OTHER, Direction.EAST), "white hears white");
        assertEquals(0, network.emitted(OTHER, Direction.WEST), "red hears nothing");
        assertEquals(0, network.emitted(OTHER, Direction.UP), "white on another channel hears nothing");
        assertEquals(List.of(OTHER.east()), world.told, "only the white emitter's block is told");

        world.set(CABLE.south(), 9);
        world.set(CABLE.above(), 4);
        network.sample(CABLE);
        assertEquals(9, network.emitted(OTHER, Direction.WEST));
        assertEquals(4, network.emitted(OTHER, Direction.UP));
        assertEquals(15, network.emitted(OTHER, Direction.EAST));
    }

    /** A block the network gives into must not be read back as a signal: the connectors answer nothing while it reads. */
    @Test
    void itsOwnConnectorsAreQuietWhileItReads() {
        var world = new World();
        var network = network(world, reader(CABLE, Direction.NORTH, WHITE), emitter(CABLE, Direction.SOUTH, WHITE));
        world.set(CABLE.north(), 15);
        network.sample(CABLE);
        assertEquals(15, network.emitted(CABLE, Direction.SOUTH), "loud once the read is over");
        assertEquals(0, world.echoed, "and nothing while it read");
        world.echoed = -1;
        world.set(CABLE.north(), 0);
        network.sample(CABLE);
        assertEquals(0, world.echoed, "the read of the fall heard no echo of the fifteen either");
        assertEquals(0, network.emitted(CABLE, Direction.SOUTH));
    }

    @Test
    void aConnectorRedstoneSwitchedOffNeitherReadsNorGives() {
        var world = new World();
        boolean[] readerOn = {true};
        boolean[] emitterOn = {true};
        var network = network(world,
                new Endpoint(CABLE, Direction.NORTH, WHITE, true, false, () -> readerOn[0]),
                new Endpoint(OTHER, Direction.EAST, WHITE, false, true, () -> emitterOn[0]));
        world.set(CABLE.north(), 15);
        network.sample(CABLE);
        assertEquals(15, network.emitted(OTHER, Direction.EAST));

        emitterOn[0] = false;
        assertEquals(0, network.emitted(OTHER, Direction.EAST), "asked as it goes, no rebuild");
        emitterOn[0] = true;

        readerOn[0] = false;
        assertTrue(network.sample(CABLE), "a reader switched off reads nothing, which is a move");
        assertEquals(0, network.emitted(OTHER, Direction.EAST));
    }

    @Test
    void aConnectorOnBothReadsAndGivesOnTheSameFace() {
        var world = new World();
        var network = network(world, new Endpoint(CABLE, Direction.NORTH, WHITE, true, true, () -> true),
                reader(OTHER, Direction.EAST, WHITE));
        world.set(OTHER.east(), 6);
        network.sample(OTHER);
        assertEquals(6, network.emitted(CABLE, Direction.NORTH), "gives out what the other reader read");
        assertEquals(List.of(CABLE.north()), world.told);
        world.set(CABLE.north(), 11);
        network.sample(CABLE);
        assertEquals(11, network.emitted(CABLE, Direction.NORTH), "and reads its own side too");
    }

    @Test
    void announcingTellsEveryBlockGivenInto() {
        var world = new World();
        var network = network(world, reader(CABLE, Direction.NORTH, WHITE),
                emitter(CABLE, Direction.SOUTH, WHITE), emitter(OTHER, Direction.EAST, RED));
        network.announce();
        assertEquals(List.of(CABLE.south(), OTHER.east()), world.told);
        world.told.clear();
        network.announce(OTHER);
        assertEquals(List.of(OTHER.east()), world.told, "one cable's connectors alone");
        world.told.clear();
        network.announce(CABLE.west());
        assertTrue(world.told.isEmpty(), "a cable with no emitter tells nobody");
    }

    /** A reader with its sensor on takes the comparator's reading where there is one, and the signal elsewhere. */
    @Test
    void aSensorReadsTheBlockAsAComparatorWould() {
        var world = new World();
        var network = network(world,
                new Endpoint(CABLE, Direction.NORTH, WHITE, true, false, true, () -> true),
                new Endpoint(CABLE, Direction.SOUTH, RED, true, false, true, () -> true),
                emitter(OTHER, Direction.EAST, WHITE), emitter(OTHER, Direction.WEST, RED));
        world.fill.put(CABLE.north(), 9);
        world.set(CABLE.north(), 15);
        world.set(CABLE.south(), 15);
        network.sample(CABLE);
        assertEquals(9, network.emitted(OTHER, Direction.EAST), "a chest nine parts full, whatever powers it");
        assertEquals(15, network.emitted(OTHER, Direction.WEST), "a lever has no fill to read, so its signal counts");
    }

    @Test
    void readingsAreClampedToASignal() {
        var world = new World();
        var network = network(world, reader(CABLE, Direction.NORTH, WHITE), emitter(OTHER, Direction.EAST, WHITE));
        world.set(CABLE.north(), 40);
        network.sample(CABLE);
        assertEquals(15, network.emitted(OTHER, Direction.EAST));
        world.set(CABLE.north(), -3);
        network.sample(CABLE);
        assertEquals(0, network.emitted(OTHER, Direction.EAST));
    }
}
