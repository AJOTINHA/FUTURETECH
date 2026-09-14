package dev.futuretech.transfer;

import dev.futuretech.block.ItemCableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ItemCableNetworkTest {
    private static final int BATCH = ItemCableTier.STANDARD.batch();
    private static final int INTERVAL = ItemCableTier.STANDARD.interval();
    private static final BlockPos CABLE = BlockPos.ZERO;

    /**
     * Stand-in for a block next to the cable; {@code handler} may be null for blocks without items.
     * These connectors all deliver, which is what a cable face does until the player narrows it.
     */
    private record FakeEndpoint(Direction side, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return true; }

        @Override
        public boolean pulls() { return false; }

        @Override
        public int priority() { return 0; }
    }

    /** A connector the player set to extract only: items may enter the cable, never leave through it. */
    private record ExtractOnlyEndpoint(Direction side, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return false; }

        @Override
        public boolean pulls() { return true; }

        @Override
        public int priority() { return 0; }
    }

    /** An ordinary connector the player moved off priority 0. */
    private record PrioritisedEndpoint(Direction side, int priority, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return true; }

        @Override
        public boolean pulls() { return false; }
    }

    private static ItemCableNetwork network(ItemCableNetwork.Endpoint... endpoints) {
        return new ItemCableNetwork(BATCH, INTERVAL, Set.of(CABLE), List.of(endpoints));
    }

    private static ItemStacksResourceHandler chest(int cobblestone) {
        var chest = new ItemStacksResourceHandler(3);
        if (cobblestone > 0) chest.set(0, ItemResource.of(Items.COBBLESTONE), cobblestone);
        return chest;
    }

    private static int count(ResourceHandler<ItemResource> handler) {
        int total = 0;
        for (int index = 0; index < handler.size(); index++) total += handler.getAmountAsInt(index);
        return total;
    }

    /** Pushes cobblestone into the cable face on {@code side}, the way a machine's output face does. */
    private static int push(ItemCableNetwork network, Direction side, int amount) {
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, side));
        return ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), amount, null);
    }

    @Test
    void pushedItemsGoStraightThroughToADeliveringInventory(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest), new FakeEndpoint(Direction.UP, null));
        assertEquals(BATCH, push(network, Direction.SOUTH, BATCH));
        assertEquals(BATCH, count(chest));
        assertEquals(BATCH, network.moved());
    }

    @Test
    void throughputCapsEachTickAndWhatDoesNotFitStaysWithThePusher(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        assertEquals(BATCH, push(network, Direction.SOUTH, 10));
        assertEquals(0, push(network, Direction.SOUTH, 1));
        // Ticks inside the interval renew nothing; the first tick of the next interval does.
        for (int tick = 1; tick < INTERVAL; tick++) network.tick(tick);
        assertEquals(0, push(network, Direction.SOUTH, 1));
        network.tick(INTERVAL);
        assertEquals(BATCH, push(network, Direction.SOUTH, 10));
        assertEquals(2 * BATCH, count(chest));
    }

    @Test
    void nothingIsHandedBackToTheBlockThatPushedIt(MinecraftServer server) {
        var producer = chest(0);
        var consumer = chest(0);
        var network = network(new FakeEndpoint(Direction.WEST, producer), new FakeEndpoint(Direction.EAST, consumer));
        assertEquals(BATCH, push(network, Direction.WEST, BATCH));
        assertEquals(0, count(producer));
        assertEquals(BATCH, count(consumer));
        // With no one else to give to, the push is refused rather than bounced back.
        var deadEnd = network(new FakeEndpoint(Direction.WEST, producer));
        assertEquals(0, push(deadEnd, Direction.WEST, BATCH));
        assertEquals(0, count(producer));
    }

    @Test
    void anExtractOnlyConnectorPumpsFromAChestThatNeverPushes(MinecraftServer server) {
        var chest = chest(64);
        var machine = chest(0);
        var network = network(new ExtractOnlyEndpoint(Direction.NORTH, chest), new FakeEndpoint(Direction.SOUTH, machine));
        network.tick(INTERVAL);
        assertEquals(BATCH, count(machine));
        assertEquals(64 - BATCH, count(chest));
        assertEquals(BATCH, network.moved());
        // The next interval pumps again; the same tick a second time, or a tick inside the
        // interval, does nothing.
        network.tick(INTERVAL);
        network.tick(INTERVAL + 1);
        network.tick(2 * INTERVAL);
        assertEquals(2 * BATCH, count(machine));
    }

    @Test
    void anOrdinaryConnectorNeverDrainsTheChestItTouches(MinecraftServer server) {
        // The default leaves both directions open, and a cable touching a chest must not start
        // emptying it. Only a connector narrowed to extract alone pumps.
        var chest = chest(64);
        var other = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest), new FakeEndpoint(Direction.SOUTH, other));
        network.tick(INTERVAL);
        assertEquals(64, count(chest));
        assertEquals(0, count(other));
    }

    @Test
    void aConnectorSetToExtractOnlyNeverReceivesADelivery(MinecraftServer server) {
        var machine = chest(0);
        var closed = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, machine), new ExtractOnlyEndpoint(Direction.SOUTH, closed));
        assertEquals(BATCH, push(network, Direction.UP, BATCH));
        assertEquals(BATCH, count(machine));
        assertEquals(0, count(closed));
    }

    @Test
    void pumpingSkipsTheChestItIsPumpingFrom(MinecraftServer server) {
        // A chest reached by an extract-only connector and, from another cable, an ordinary one must
        // not have its items pulled out and dropped straight back in.
        var chest = chest(64);
        BlockPos chestPos = CABLE.north();
        var otherFace = new ItemCableNetwork.EndpointKey(chestPos.east(), Direction.WEST);
        assertEquals(chestPos, otherFace.neighbour());
        var network = new ItemCableNetwork(BATCH, INTERVAL, Set.of(CABLE, otherFace.cablePos()), List.of(
                new ExtractOnlyEndpoint(Direction.NORTH, chest), new FakeEndpointAt(otherFace, chest)));
        network.tick(INTERVAL);
        assertEquals(64, count(chest));
        assertEquals(0, network.moved());
    }

    /** Endpoint on an explicit cable face, for a network that touches one block from two sides. */
    private record FakeEndpointAt(ItemCableNetwork.EndpointKey key, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public boolean delivers() { return true; }

        @Override
        public boolean pulls() { return false; }

        @Override
        public int priority() { return 0; }
    }

    @Test
    void higherPriorityIsServedFirstAndTheRestOnlyGetsTheOverflow(MinecraftServer server) {
        // The favoured chest has room for one stack of cobblestone in its single slot; only once it
        // is full does anything reach the others, however they were listed.
        var favoured = new ItemStacksResourceHandler(1);
        var first = chest(0);
        var last = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new FakeEndpoint(Direction.NORTH, first),
                new PrioritisedEndpoint(Direction.SOUTH, 3, favoured),
                new PrioritisedEndpoint(Direction.EAST, -2, last)));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.UP));
        assertEquals(64, ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 64, null));
        assertEquals(64, count(favoured));
        assertEquals(0, count(first));
        network.tick(INTERVAL);
        assertEquals(10, ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 10, null));
        assertEquals(10, count(first));
        assertEquals(0, count(last));
        for (int round = 2; round <= 20; round++) {
            network.tick(round * INTERVAL);
            ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 64, null);
        }
        // Three slots of 64 fill the middle chest; only then does the lowest priority see anything.
        assertEquals(64, count(favoured));
        assertEquals(3 * 64, count(first));
        assertTrue(count(last) > 0);
    }

    @Test
    void pumpsWithHigherPriorityGetTheRoomFirst(MinecraftServer server) {
        // Each pump has its own budget, so priority only matters when the room runs out: a sink
        // with space for one item takes it from the favoured pump, and the other stays full.
        var urgent = chest(64);
        var later = chest(64);
        var sink = new ItemStacksResourceHandler(1);
        sink.set(0, ItemResource.of(Items.COBBLESTONE), 64 - BATCH);
        var network = network(new ExtractOnlyEndpoint(Direction.NORTH, later),
                new PrioritisedPump(Direction.SOUTH, 5, urgent), new FakeEndpoint(Direction.EAST, sink));
        for (int round = 1; round <= 3; round++) network.tick(round * INTERVAL);
        assertEquals(64 - BATCH, count(urgent));
        assertEquals(64, count(later));
        assertEquals(64, count(sink));
    }

    private record PrioritisedPump(Direction side, int priority, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return false; }

        @Override
        public boolean pulls() { return true; }
    }

    /** A connector with a filter card: {@code filter} says what may cross it either way. */
    private record FilteredEndpoint(Direction side, boolean delivers, boolean pulls, Predicate<ItemResource> filter,
                                    @Nullable ResourceHandler<ItemResource> handler) implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public int priority() { return 0; }

        @Override
        public boolean accepts(ItemResource resource) { return filter.test(resource); }
    }

    private static final Predicate<ItemResource> COBBLESTONE_ONLY = resource -> resource.is(Items.COBBLESTONE);

    /** An extract-only connector carrying speed upgrades. */
    private record UpgradedPump(Direction side, int upgrades, @Nullable ResourceHandler<ItemResource> handler)
            implements ItemCableNetwork.Endpoint {
        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public boolean delivers() { return false; }

        @Override
        public boolean pulls() { return true; }

        @Override
        public int priority() { return 0; }
    }

    @Test
    void speedUpgradesWidenTheBudgetOfTheConnectorTheyAreOn(MinecraftServer server) {
        var fast = chest(64);
        var slow = chest(64);
        var sink = new ItemStacksResourceHandler(9);
        var network = network(new UpgradedPump(Direction.NORTH, 3, fast), new ExtractOnlyEndpoint(Direction.SOUTH, slow),
                new FakeEndpoint(Direction.EAST, sink));
        network.tick(INTERVAL);
        // Three upgrades add six to the batch of one; the plain pump keeps its one.
        assertEquals(64 - (BATCH + 3 * ItemCableNetwork.PER_UPGRADE), count(fast));
        assertEquals(64 - BATCH, count(slow));
        assertEquals(2 * BATCH + 3 * ItemCableNetwork.PER_UPGRADE, network.moved());
        // A full load of upgrades moves a whole stack per interval, never more.
        var loaded = chest(64);
        var stacked = network(new UpgradedPump(Direction.NORTH, 32, loaded), new FakeEndpoint(Direction.EAST, new ItemStacksResourceHandler(9)));
        stacked.tick(INTERVAL);
        assertEquals(0, count(loaded));
        assertEquals(ItemCableNetwork.MAX_PER_INTERVAL, stacked.moved());
    }

    /** A connector on a colour, and optionally a numbered channel. */
    private record ColouredEndpoint(Direction side, boolean delivers, boolean pulls, DyeColor color, int channel,
                                    @Nullable ResourceHandler<ItemResource> handler) implements ItemCableNetwork.Endpoint {
        ColouredEndpoint(Direction side, boolean delivers, boolean pulls, DyeColor color,
                         @Nullable ResourceHandler<ItemResource> handler) {
            this(side, delivers, pulls, color, 0, handler);
        }

        @Override
        public ItemCableNetwork.EndpointKey key() { return new ItemCableNetwork.EndpointKey(CABLE, side); }

        @Override
        public int priority() { return 0; }
    }

    @Test
    void itemsOnlyTravelBetweenConnectorsOnTheSameChannelNumber(MinecraftServer server) {
        var source = chest(64);
        var sameLine = chest(0);
        var otherLine = chest(0);
        var otherColour = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.NORTH, false, true, DyeColor.RED, 7, source),
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.RED, 8, otherLine),
                new ColouredEndpoint(Direction.WEST, true, false, DyeColor.BLUE, 7, otherColour),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, 7, sameLine)));
        network.tick(INTERVAL);
        assertEquals(64, count(sameLine));
        assertEquals(0, count(otherLine), "Same colour, other channel");
        assertEquals(0, count(otherColour), "Same channel, other colour");
    }

    @Test
    void itemsOnlyTravelBetweenConnectorsOfTheSameColour(MinecraftServer server) {
        var redChest = chest(64);
        var redSink = chest(0);
        var blueSink = chest(0);
        var whiteSink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.NORTH, false, true, DyeColor.RED, redChest),
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.BLUE, blueSink),
                new FakeEndpoint(Direction.UP, whiteSink),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, redSink)));
        network.tick(INTERVAL);
        assertEquals(64, count(redSink));
        assertEquals(0, count(blueSink));
        assertEquals(0, count(whiteSink), "Connectors left white are their own channel");
        // A push through a blue face only reaches blue; with none listening it is refused.
        var blueEntrance = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.EAST));
        var onlyRed = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.BLUE, null),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, chest(0))));
        assertEquals(0, ResourceHandlerUtil.insertStacking(
                onlyRed.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.EAST)), ItemResource.of(Items.DIRT), 4, null));
        assertNotNull(blueEntrance);
    }

    @Test
    void aFilteredConnectorOnlyReceivesWhatItsCardAllows(MinecraftServer server) {
        var stone = chest(0);
        var anything = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, true, false, COBBLESTONE_ONLY, stone),
                new FakeEndpoint(Direction.SOUTH, anything)));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.UP));
        // Dirt has one place to go; cobblestone is offered to the filtered chest first in this round.
        assertEquals(4, ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.DIRT), 4, null));
        assertEquals(0, count(stone));
        assertEquals(4, count(anything));
        var stoneOnly = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, true, false, COBBLESTONE_ONLY, chest(0))));
        var lonely = stoneOnly.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.UP));
        assertEquals(0, ResourceHandlerUtil.insertStacking(lonely, ItemResource.of(Items.DIRT), 4, null));
        assertEquals(4, ResourceHandlerUtil.insertStacking(lonely, ItemResource.of(Items.COBBLESTONE), 4, null));
    }

    @Test
    void aFilteredConnectorRefusesWhatItsCardKeepsOutAtTheEntrance(MinecraftServer server) {
        var sink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.WEST, true, false, COBBLESTONE_ONLY, null),
                new FakeEndpoint(Direction.EAST, sink)));
        var entrance = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.WEST));
        assertEquals(0, ResourceHandlerUtil.insertStacking(entrance, ItemResource.of(Items.DIRT), 4, null));
        assertEquals(4, ResourceHandlerUtil.insertStacking(entrance, ItemResource.of(Items.COBBLESTONE), 4, null));
        assertEquals(4, count(sink));
    }

    @Test
    void aFilteredPumpLeavesWhatItsCardKeepsOutInTheChest(MinecraftServer server) {
        var mixed = new ItemStacksResourceHandler(3);
        mixed.set(0, ItemResource.of(Items.DIRT), 8);
        mixed.set(1, ItemResource.of(Items.COBBLESTONE), 8);
        var sink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, false, true, COBBLESTONE_ONLY, mixed),
                new FakeEndpoint(Direction.SOUTH, sink)));
        network.tick(INTERVAL);
        assertEquals(8, mixed.getAmountAsInt(0), "Dirt stays");
        assertEquals(0, mixed.getAmountAsInt(1), "Cobblestone is pumped");
        assertEquals(8, count(sink));
    }

    @Test
    void aRolledBackTransactionGivesTheBudgetBack(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.SOUTH));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(BATCH, face.insert(0, ItemResource.of(Items.COBBLESTONE), BATCH, transaction));
            assertEquals(BATCH, network.moved());
            // Closed without committing: the simulation is undone in the chest and in the budget.
        }
        assertEquals(0, count(chest));
        assertEquals(0, network.moved());
        assertEquals(BATCH, push(network, Direction.SOUTH, BATCH));
    }

    @Test
    void deliveryStartsAtADifferentInventoryEachInterval(MinecraftServer server) {
        var first = chest(0);
        var second = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, first), new FakeEndpoint(Direction.SOUTH, second));
        network.tick(2 * INTERVAL);
        push(network, Direction.UP, 1);
        network.tick(3 * INTERVAL);
        push(network, Direction.UP, 1);
        assertEquals(1, count(first));
        assertEquals(1, count(second));
    }

    @Test
    void theCableHoldsNothingAndNothingCanBePulledOutOfIt(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.SOUTH));
        push(network, Direction.SOUTH, 2);
        assertTrue(face.getResource(0).isEmpty());
        assertEquals(0, face.getAmountAsInt(0));
        // A machine's input face pulling from the cable, or a hopper under it, finds nothing.
        assertEquals(0, ResourceHandlerUtil.moveStacking(face, chest(0), resource -> true, 64, null));
    }
}
