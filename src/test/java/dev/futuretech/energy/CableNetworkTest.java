package dev.futuretech.energy;

import dev.futuretech.block.CableTier;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CableNetworkTest {
    private static final int THROUGHPUT = CableTier.MK1.throughput();
    private static final BlockPos CABLE = BlockPos.ZERO;

    /**
     * Stand-in for a block next to the cable; {@code handler} may be null for blocks without energy.
     * These connectors all deliver, which is what a cable face does until the player narrows it.
     */
    private record FakeEndpoint(Direction side, @Nullable EnergyHandler handler) implements CableNetwork.Endpoint {
        @Override
        public CableNetwork.EndpointKey key() { return new CableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return true; }

        @Override
        public boolean pulls() { return false; }
    }

    /** A connector the player set to extract only: energy may enter the cable, never leave through it. */
    private record ExtractOnlyEndpoint(Direction side, @Nullable EnergyHandler handler)
            implements CableNetwork.Endpoint {
        @Override
        public CableNetwork.EndpointKey key() { return new CableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return false; }

        @Override
        public boolean pulls() { return true; }
    }

    private static CableNetwork network(CableNetwork.Endpoint... endpoints) {
        return new CableNetwork(THROUGHPUT, Set.of(CABLE), List.of(endpoints));
    }

    private static int insert(CableNetwork network, Direction side, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = network.handlerFor(new CableNetwork.EndpointKey(CABLE, side)).insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Test
    void distributionIsFairAndRefillsSinksThatStillHaveRoom(MinecraftServer server) {
        var source = new SimpleEnergyHandler(1_000, 0, 1_000, 300);
        var small = new SimpleEnergyHandler(40);
        var big = new SimpleEnergyHandler(1_000);
        var full = new SimpleEnergyHandler(10, 10, 10, 10);
        assertEquals(300, CableNetwork.distribute(source, List.of(small, big, full)));
        // The small sink takes its 40 and the remainder of its share flows on to the big one.
        assertEquals(40, small.getAmountAsInt());
        assertEquals(260, big.getAmountAsInt());
        assertEquals(10, full.getAmountAsInt());
        assertEquals(0, source.getAmountAsInt());
        assertEquals(0, CableNetwork.distribute(source, List.of(big)));
    }

    @Test
    void aConnectorSetToExtractOnlyNeverReceivesTheDistribution(MinecraftServer server) {
        var machine = new SimpleEnergyHandler(10_000);
        var closed = new SimpleEnergyHandler(10_000);
        var network = network(new FakeEndpoint(Direction.NORTH, machine),
                new ExtractOnlyEndpoint(Direction.SOUTH, closed));
        assertEquals(THROUGHPUT, insert(network, Direction.UP, THROUGHPUT));
        network.tick(1);
        // Everything the network held went out through the one connector that still delivers.
        assertEquals(THROUGHPUT, machine.getAmountAsInt());
        assertEquals(0, closed.getAmountAsInt());
        assertEquals(THROUGHPUT, network.lastMoved());
    }

    @Test
    void aConnectorSetToExtractOnlyPullsFromABlockThatNeverPushes(MinecraftServer server) {
        // A battery only pushes through its own output faces. Set to input, it just sits on its
        // charge, so the connector has to do the pulling or nothing ever comes out.
        var hoarder = new SimpleEnergyHandler(50_000, 0, 50_000, 50_000);
        var machine = new SimpleEnergyHandler(10_000);
        var network = network(new ExtractOnlyEndpoint(Direction.NORTH, hoarder),
                new FakeEndpoint(Direction.SOUTH, machine));
        network.tick(1);
        assertEquals(THROUGHPUT, machine.getAmountAsInt());
        assertEquals(50_000 - THROUGHPUT, hoarder.getAmountAsInt());
    }

    @Test
    void anOrdinaryConnectorNeverDrainsTheBlockItIsFeeding(MinecraftServer server) {
        // The default leaves both directions open, and a cable touching a furnace must not start
        // siphoning it. Only a connector narrowed to extract alone pumps.
        var machine = new SimpleEnergyHandler(10_000, 10_000, 10_000, 4_000);
        var network = network(new FakeEndpoint(Direction.NORTH, machine));
        network.tick(1);
        assertEquals(4_000, machine.getAmountAsInt());
        assertEquals(0, network.stored());
    }

    @Test
    void networkAcceptsOneTickOfThroughputAndRejectsTheRest(MinecraftServer server) {
        var network = network();
        assertEquals(THROUGHPUT, insert(network, Direction.NORTH, 10_000));
        assertEquals(0, insert(network, Direction.SOUTH, 1));
        assertEquals(THROUGHPUT, network.stored());
        // With nowhere to send it, the buffer stays full and keeps refusing inserts on later ticks.
        network.tick(1);
        assertEquals(0, insert(network, Direction.NORTH, 1));
        assertEquals(THROUGHPUT, network.stored());
    }

    @Test
    void energyIsNotHandedBackToTheBlockThatJustInsertedIt(MinecraftServer server) {
        var producerSide = new SimpleEnergyHandler(1_000);
        var consumer = new SimpleEnergyHandler(1_000);
        var network = network(new FakeEndpoint(Direction.WEST, producerSide), new FakeEndpoint(Direction.EAST, consumer),
                new FakeEndpoint(Direction.UP, null));
        insert(network, Direction.WEST, 100);
        network.tick(1);
        assertEquals(0, producerSide.getAmountAsInt());
        assertEquals(100, consumer.getAmountAsInt());
        assertEquals(100, network.lastMoved());
        assertEquals(0, network.stored());
        // The exclusion lasts one distribution: on a quiet tick the west block is a sink again.
        insert(network, Direction.EAST, 60);
        network.tick(2);
        assertEquals(60, producerSide.getAmountAsInt());
        assertEquals(100, consumer.getAmountAsInt());
    }

    @Test
    void fullBufferNeverBouncesEnergyBackToTheFaceStillPushingIn(MinecraftServer server) {
        // Regression: a battery on a dead-end cable used to get its own energy handed back once the
        // buffer filled up and its inserts were rejected, draining and refilling every other tick.
        var battery = new SimpleEnergyHandler(100_000, 200, 200, 50_000);
        var network = network(new FakeEndpoint(Direction.WEST, battery));
        var face = network.handlerFor(new CableNetwork.EndpointKey(CABLE, Direction.WEST));
        for (int tick = 1; tick <= 20; tick++) {
            EnergyHandlerUtil.move(battery, face, 200, null);
            network.tick(tick);
            assertEquals(0, network.lastMoved(), "tick " + tick);
        }
        assertEquals(THROUGHPUT, network.stored());
        assertEquals(50_000 - THROUGHPUT, battery.getAmountAsInt());
    }

    /** Endpoint on an explicit cable face, for a network that touches one block from two sides. */
    private record FakeEndpointAt(CableNetwork.EndpointKey key, @Nullable EnergyHandler handler)
            implements CableNetwork.Endpoint {
        @Override
        public boolean delivers() { return true; }

        @Override
        public boolean pulls() { return false; }
    }

    @Test
    void energyIsNotHandedBackThroughASecondFaceOfTheSameBlock(MinecraftServer server) {
        // Regression: a battery with an output face and an input face on the same network used to get
        // its own energy back through the other face, draining and refilling every tick.
        BlockPos battery = BlockPos.ZERO;
        BlockPos westCable = battery.west();
        BlockPos eastCable = battery.east();
        var batteryHandler = new SimpleEnergyHandler(100_000, 200, 200, 50_000);
        var consumer = new SimpleEnergyHandler(1_000);
        var outputFace = new CableNetwork.EndpointKey(westCable, Direction.EAST);
        var inputFace = new CableNetwork.EndpointKey(eastCable, Direction.WEST);
        assertEquals(battery, outputFace.cablePos().relative(outputFace.side()));
        assertEquals(battery, inputFace.cablePos().relative(inputFace.side()));
        var network = new CableNetwork(THROUGHPUT, Set.of(westCable, eastCable),
                List.of(new FakeEndpointAt(outputFace, batteryHandler),
                        new FakeEndpointAt(inputFace, batteryHandler),
                        new FakeEndpointAt(new CableNetwork.EndpointKey(westCable, Direction.UP), consumer)));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(100, network.handlerFor(outputFace).insert(100, transaction));
            transaction.commit();
        }
        network.tick(1);
        assertEquals(100, consumer.getAmountAsInt(), "the consumer should get all of it");
        assertEquals(50_000, batteryHandler.getAmountAsInt(), "the battery must not receive its own energy back");
    }

    @Test
    void tickRunsOnceEvenWhenEveryCableCallsIt(MinecraftServer server) {
        var consumer = new SimpleEnergyHandler(50, 50, 0);
        var network = network(new FakeEndpoint(Direction.DOWN, consumer));
        insert(network, Direction.NORTH, 300);
        network.tick(7);
        network.tick(7);
        network.tick(7);
        assertEquals(50, consumer.getAmountAsInt());
        assertEquals(50, network.lastMoved());
        assertEquals(250, network.stored());
        // Output is capped per tick too: a second tick delivers at most the throughput.
        var hungry = new SimpleEnergyHandler(10_000);
        var wide = network(new FakeEndpoint(Direction.DOWN, hungry));
        insert(wide, Direction.NORTH, THROUGHPUT);
        wide.tick(8);
        assertEquals(THROUGHPUT, hungry.getAmountAsInt());
    }

    @Test
    void producerPushesIntoTheNetworkAndTheBatteryFillsFromIt(MinecraftServer server) {
        // A generator-like source: 20 FE appear each tick and at most 80 FE/t may leave it.
        var producer = new TickLimitedEnergyHandler(20_000, 0, 80, () -> {});
        var battery = new SimpleEnergyHandler(100_000);
        var network = network(new FakeEndpoint(Direction.EAST, battery));
        var producerFace = network.handlerFor(new CableNetwork.EndpointKey(CABLE, Direction.WEST));
        for (int tick = 1; tick <= 50; tick++) {
            producer.beginTick();
            producer.set(producer.getAmountAsInt() + 20);
            EnergyHandlerUtil.move(producer, producerFace, 80, null);
            network.tick(tick);
        }
        assertEquals(1_000, battery.getAmountAsInt());
        assertEquals(0, producer.getAmountAsInt());
        assertEquals(0, network.stored());
    }

    @Test
    void cableRecipeIsLoadedByTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath("futuretech", "cable_mk1"))).isPresent());
        assertEquals("cable_mk1", ModBlocks.CABLE_MK1.getId().getPath());
        assertEquals(CableTier.MK1, ModBlocks.CABLE_MK1.get().tier());
    }
}
