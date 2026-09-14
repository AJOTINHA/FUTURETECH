package dev.futuretech.transfer;

import dev.futuretech.block.FluidCableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FluidCableNetworkTest {
    private static final int THROUGHPUT = FluidCableTier.STANDARD.throughput();
    private static final BlockPos CABLE = BlockPos.ZERO;
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private static final FluidResource LAVA = FluidResource.of(Fluids.LAVA);

    /** A connector with every setting; the builders below fill in the usual defaults. */
    private record Fake(Direction side, boolean delivers, boolean pulls, int priority, DyeColor color, int channel,
                        @Nullable ResourceHandler<FluidResource> handler) implements FluidCableNetwork.Endpoint {
        @Override
        public FluidCableNetwork.EndpointKey key() { return new FluidCableNetwork.EndpointKey(CABLE, side); }
    }

    private static Fake sink(Direction side, @Nullable ResourceHandler<FluidResource> tank) {
        return new Fake(side, true, false, 0, DyeColor.WHITE, 0, tank);
    }

    private static Fake pump(Direction side, @Nullable ResourceHandler<FluidResource> tank) {
        return new Fake(side, false, true, 0, DyeColor.WHITE, 0, tank);
    }

    private static FluidCableNetwork network(FluidCableNetwork.Endpoint... endpoints) {
        return new FluidCableNetwork(THROUGHPUT, Set.of(CABLE), List.of(endpoints));
    }

    private static FluidStacksResourceHandler tank(int capacity, FluidResource fluid, int amount) {
        var tank = new FluidStacksResourceHandler(1, capacity);
        if (amount > 0) tank.set(0, fluid, amount);
        return tank;
    }

    private static int amount(ResourceHandler<FluidResource> tank) { return tank.getAmountAsInt(0); }

    private static int push(FluidCableNetwork network, Direction side, FluidResource fluid, int amount) {
        return ResourceHandlerUtil.insertStacking(network.handlerFor(new FluidCableNetwork.EndpointKey(CABLE, side)), fluid, amount, null);
    }

    @Test
    void networkTakesOneTickOfThroughputAndSpreadsItOverTheTanks(MinecraftServer server) {
        var small = tank(100, WATER, 0);
        var big = tank(10_000, WATER, 0);
        var network = network(sink(Direction.NORTH, small), sink(Direction.SOUTH, big), sink(Direction.UP, null));
        assertEquals(THROUGHPUT, push(network, Direction.WEST, WATER, 5_000));
        assertEquals(0, push(network, Direction.WEST, WATER, 1), "The buffer holds one tick only");
        network.tick(1);
        // The small tank takes its 100 and the rest of its share flows on to the big one.
        assertEquals(100, amount(small));
        assertEquals(THROUGHPUT - 100, amount(big));
        assertEquals(THROUGHPUT, network.lastMoved());
    }

    @Test
    void anExtractOnlyConnectorPumpsFromATankThatNeverPushes(MinecraftServer server) {
        var source = tank(10_000, WATER, 10_000);
        var target = tank(10_000, WATER, 0);
        var network = network(pump(Direction.NORTH, source), sink(Direction.SOUTH, target));
        network.tick(1);
        assertEquals(10_000 - THROUGHPUT, amount(source));
        assertEquals(THROUGHPUT, amount(target));
        network.tick(2);
        assertEquals(2 * THROUGHPUT, amount(target));
    }

    @Test
    void anOrdinaryConnectorNeverDrainsTheTankItTouches(MinecraftServer server) {
        var full = tank(10_000, WATER, 10_000);
        var empty = tank(10_000, WATER, 0);
        var network = network(sink(Direction.NORTH, full), sink(Direction.SOUTH, empty));
        network.tick(1);
        assertEquals(10_000, amount(full));
        assertEquals(0, amount(empty));
    }

    @Test
    void fluidIsNotHandedBackToTheBlockThatJustPushedIt(MinecraftServer server) {
        var producer = tank(10_000, WATER, 0);
        var consumer = tank(10_000, WATER, 0);
        var network = network(sink(Direction.WEST, producer), sink(Direction.EAST, consumer));
        push(network, Direction.WEST, WATER, 200);
        network.tick(1);
        assertEquals(0, amount(producer));
        assertEquals(200, amount(consumer));
        // A dead end with only the pusher to give to keeps the fluid in the buffer and refuses more.
        var deadEnd = network(sink(Direction.WEST, producer));
        assertEquals(THROUGHPUT, push(deadEnd, Direction.WEST, WATER, THROUGHPUT));
        deadEnd.tick(1);
        assertEquals(0, amount(producer));
        assertEquals(0, push(deadEnd, Direction.WEST, WATER, 1));
    }

    @Test
    void higherPriorityIsFilledFirstAndTheRestGetsTheOverflow(MinecraftServer server) {
        var favoured = tank(300, WATER, 0);
        var other = tank(10_000, WATER, 0);
        var network = network(sink(Direction.NORTH, other),
                new Fake(Direction.SOUTH, true, false, 5, DyeColor.WHITE, 0, favoured));
        push(network, Direction.WEST, WATER, THROUGHPUT);
        network.tick(1);
        assertEquals(300, amount(favoured));
        assertEquals(THROUGHPUT - 300, amount(other));
    }

    @Test
    void linesKeepColoursAndChannelsApartWithABufferEach(MinecraftServer server) {
        var redSource = tank(10_000, WATER, 10_000);
        var redSink = tank(10_000, WATER, 0);
        var blueSink = tank(10_000, WATER, 0);
        var otherChannel = tank(10_000, WATER, 0);
        var network = network(
                new Fake(Direction.NORTH, false, true, 0, DyeColor.RED, 3, redSource),
                new Fake(Direction.SOUTH, true, false, 0, DyeColor.RED, 3, redSink),
                new Fake(Direction.EAST, true, false, 0, DyeColor.BLUE, 3, blueSink),
                new Fake(Direction.WEST, true, false, 0, DyeColor.RED, 4, otherChannel));
        network.tick(1);
        assertEquals(THROUGHPUT, amount(redSink));
        assertEquals(0, amount(blueSink));
        assertEquals(0, amount(otherChannel));
        // Two lines carry two fluids at once without mixing.
        var lavaSource = tank(10_000, LAVA, 10_000);
        var lavaSink = tank(10_000, LAVA, 0);
        var twoLines = network(
                new Fake(Direction.NORTH, false, true, 0, DyeColor.RED, 0, redSource),
                new Fake(Direction.SOUTH, true, false, 0, DyeColor.RED, 0, redSink),
                new Fake(Direction.EAST, false, true, 0, DyeColor.ORANGE, 0, lavaSource),
                new Fake(Direction.WEST, true, false, 0, DyeColor.ORANGE, 0, lavaSink));
        twoLines.tick(1);
        assertEquals(THROUGHPUT, amount(lavaSink));
        assertEquals(2 * THROUGHPUT, amount(redSink));
    }

    /** A connector on an explicit cable face, for runs of more than one cable. */
    private record FakeAt(FluidCableNetwork.EndpointKey key, boolean delivers, boolean pulls,
                          @Nullable ResourceHandler<FluidResource> handler) implements FluidCableNetwork.Endpoint {
        @Override
        public int priority() { return 0; }
    }

    @Test
    void everyCableOnARouteLearnsWhichWayTheFluidRuns(MinecraftServer server) {
        // A straight run of three cables: tank on the west end, sink on the east end.
        BlockPos a = CABLE;
        BlockPos b = a.east();
        BlockPos c = b.east();
        var source = tank(10_000, WATER, 10_000);
        var target = tank(10_000, WATER, 0);
        var network = new FluidCableNetwork(THROUGHPUT, Set.of(a, b, c), List.of(
                new FakeAt(new FluidCableNetwork.EndpointKey(a, Direction.WEST), false, true, source),
                new FakeAt(new FluidCableNetwork.EndpointKey(c, Direction.EAST), true, false, target)));
        assertNull(network.flowAt(b));
        network.tick(1);
        assertEquals(THROUGHPUT, amount(target));
        assertEquals(Direction.EAST, network.flowAt(a));
        assertEquals(Direction.EAST, network.flowAt(b));
        assertEquals(Direction.EAST, network.flowAt(c), "The last cable points at the tank it feeds");
        // Once the picture fades, so do the directions.
        source.set(0, WATER, 0);
        for (long tick = 2; tick <= 3 + FluidCableNetwork.SHOW_TICKS; tick++) network.tick(tick);
        assertTrue(network.shown().isEmpty());
        assertNull(network.flowAt(b));
    }

    @Test
    void theNetworkShowsWhatItCarriesAndForgetsItAMomentAfterTheFlowStops(MinecraftServer server) {
        var source = tank(10_000, WATER, THROUGHPUT);
        var target = tank(10_000, WATER, 0);
        var network = network(pump(Direction.NORTH, source), sink(Direction.SOUTH, target));
        assertTrue(network.shown().isEmpty());
        network.tick(1);
        assertTrue(network.shown().is(Fluids.WATER));
        for (long tick = 2; tick <= 1 + FluidCableNetwork.SHOW_TICKS; tick++) network.tick(tick);
        assertTrue(network.shown().is(Fluids.WATER), "Still shown while the pause is short");
        network.tick(2 + FluidCableNetwork.SHOW_TICKS);
        assertTrue(network.shown().isEmpty());
    }
}
