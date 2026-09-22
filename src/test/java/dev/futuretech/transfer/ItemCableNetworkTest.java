package dev.futuretech.transfer;

import dev.futuretech.block.ItemCableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    /**
     * Ticks an item needs to cross the one cable of most networks here: half a cable in, half out.
     * The tests set their own pace so a trip always fits inside one interval, whatever the game uses.
     */
    private static final int TRIP = 4;
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

    /** Endpoint on an explicit cable face, for networks of more than one cable. */
    private record FakeEndpointAt(ItemCableNetwork.EndpointKey key, boolean delivers, boolean pulls,
                                  @Nullable ResourceHandler<ItemResource> handler) implements ItemCableNetwork.Endpoint {
        @Override
        public int priority() { return 0; }
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

    private static final Predicate<ItemResource> COBBLESTONE_ONLY = resource -> resource.is(Items.COBBLESTONE);

    private static ItemCableNetwork network(ItemCableNetwork.Endpoint... endpoints) {
        return new ItemCableNetwork(BATCH, INTERVAL, TRIP, Set.of(CABLE), List.of(endpoints));
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
        return push(network, side, Items.COBBLESTONE, amount);
    }

    private static int push(ItemCableNetwork network, Direction side, Item item, int amount) {
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, side));
        return ResourceHandlerUtil.insertStacking(face, ItemResource.of(item), amount, null);
    }

    /** Runs every tick after {@code from} up to and including {@code to}. */
    private static void run(ItemCableNetwork network, long from, long to) {
        for (long tick = from + 1; tick <= to; tick++) network.tick(tick);
    }

    /** Lets everything in flight across one cable arrive, starting right after {@code after}. */
    private static void fly(ItemCableNetwork network, long after) {
        assertTrue(TRIP < INTERVAL, "the trip must fit inside an interval for these tests");
        run(network, after, after + TRIP);
    }

    /** A connector with speed upgrades on it, wide enough to let several items in within one tick. */
    private record UpgradedEndpoint(Direction side, int upgrades, @Nullable ResourceHandler<ItemResource> handler)
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

    @Test
    void itemsEnteringTogetherSetOffHalfACableApart(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest), new UpgradedEndpoint(Direction.SOUTH, 4, null));
        network.tick(0);
        for (int item = 0; item < 3; item++) assertEquals(1, push(network, Direction.SOUTH, 1));
        var flights = network.flights();
        assertEquals(3, flights.size());
        int gap = TRIP / 2;
        assertEquals(List.of(0, -gap, -2 * gap), flights.stream().map(flight -> flight.travelled).toList(),
                "Each one starts half a cable behind the one before it, not on top of it");
        run(network, 0, TRIP);
        assertEquals(1, count(chest), "The first arrives on its own");
        run(network, TRIP, TRIP + gap);
        assertEquals(2, count(chest), "The second half a cable later");
        run(network, TRIP + gap, TRIP + 2 * gap);
        assertEquals(3, count(chest));
        assertTrue(network.flights().isEmpty());
    }

    @Test
    void pushedItemsSetOffAtOnceAndArriveAfterCrossingTheCable(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest), new FakeEndpoint(Direction.UP, null));
        assertEquals(BATCH, push(network, Direction.SOUTH, BATCH));
        assertEquals(BATCH, network.moved());
        assertEquals(1, network.flights().size(), "The item is on its way, not in the chest yet");
        assertEquals(0, count(chest));
        run(network, 0, TRIP - 1);
        assertEquals(0, count(chest), "Still travelling one tick before arrival");
        network.tick(TRIP);
        assertEquals(BATCH, count(chest));
        assertTrue(network.flights().isEmpty());
    }

    @Test
    void itemsCrossEveryCableOnTheWayAndArriveOnlyAtTheEnd(MinecraftServer server) {
        // A straight run of three cables: chest on the west end, machine on the east end. The chest
        // holds one batch only, so no second item sets off while the first is still on its way.
        BlockPos a = CABLE;
        BlockPos b = a.east();
        BlockPos c = b.east();
        var chest = chest(BATCH);
        var machine = chest(0);
        var network = new ItemCableNetwork(BATCH, INTERVAL, TRIP, Set.of(a, b, c), List.of(
                new FakeEndpointAt(new ItemCableNetwork.EndpointKey(a, Direction.WEST), false, true, chest),
                new FakeEndpointAt(new ItemCableNetwork.EndpointKey(c, Direction.EAST), true, false, machine)));
        network.tick(INTERVAL);
        assertEquals(0, count(chest), "Pumped out at once");
        var flight = network.flights().getFirst();
        assertEquals(List.of(a, b, c), flight.path);
        assertEquals(a, flight.current());
        run(network, INTERVAL, INTERVAL + TRIP);
        assertEquals(b, flight.current(), "Half a cable in, one cable on: now in the middle cable");
        run(network, INTERVAL + TRIP, INTERVAL + 3 * TRIP - 1);
        assertEquals(0, count(machine));
        network.tick(INTERVAL + 3 * TRIP);
        assertEquals(BATCH, count(machine));
        assertTrue(network.flights().isEmpty());
    }

    @Test
    void throughputCapsEachIntervalAndWhatDoesNotFitStaysWithThePusher(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        assertEquals(BATCH, push(network, Direction.SOUTH, 10));
        assertEquals(0, push(network, Direction.SOUTH, 1));
        // Ticks inside the interval renew nothing; the first tick of the next interval does.
        run(network, 0, INTERVAL - 1);
        assertEquals(0, push(network, Direction.SOUTH, 1));
        network.tick(INTERVAL);
        assertEquals(BATCH, push(network, Direction.SOUTH, 10));
        fly(network, INTERVAL);
        assertEquals(2 * BATCH, count(chest));
    }

    @Test
    void nothingIsSentBackToTheBlockThatPushedIt(MinecraftServer server) {
        var producer = chest(0);
        var consumer = chest(0);
        var network = network(new FakeEndpoint(Direction.WEST, producer), new FakeEndpoint(Direction.EAST, consumer));
        assertEquals(BATCH, push(network, Direction.WEST, BATCH));
        fly(network, 0);
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
        assertEquals(64 - BATCH, count(chest));
        assertEquals(BATCH, network.moved());
        fly(network, INTERVAL);
        assertEquals(BATCH, count(machine));
        // The next interval pumps again; the same tick a second time does nothing.
        network.tick(INTERVAL + TRIP);
        run(network, INTERVAL + TRIP, 2 * INTERVAL);
        fly(network, 2 * INTERVAL);
        assertEquals(2 * BATCH, count(machine));
    }

    @Test
    void anOrdinaryConnectorNeverDrainsTheChestItTouches(MinecraftServer server) {
        // The default leaves both directions open, and a cable touching a chest must not start
        // emptying it. Only a connector narrowed to extract alone pumps.
        var chest = chest(64);
        var other = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest), new FakeEndpoint(Direction.SOUTH, other));
        run(network, 0, 2 * INTERVAL);
        assertEquals(64, count(chest));
        assertEquals(0, count(other));
    }

    @Test
    void aConnectorSetToExtractOnlyNeverReceivesADelivery(MinecraftServer server) {
        var machine = chest(0);
        var closed = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, machine), new ExtractOnlyEndpoint(Direction.SOUTH, closed));
        assertEquals(BATCH, push(network, Direction.UP, BATCH));
        fly(network, 0);
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
        var network = new ItemCableNetwork(BATCH, INTERVAL, TRIP, Set.of(CABLE, otherFace.cablePos()), List.of(
                new ExtractOnlyEndpoint(Direction.NORTH, chest), new FakeEndpointAt(otherFace, true, false, chest)));
        network.tick(INTERVAL);
        assertEquals(64, count(chest));
        assertEquals(0, network.moved());
        assertTrue(network.flights().isEmpty());
    }

    @Test
    void aRolledBackTransactionGivesTheBudgetBackAndSendsNothing(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.SOUTH));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(BATCH, face.insert(0, ItemResource.of(Items.COBBLESTONE), BATCH, transaction));
            assertEquals(BATCH, network.moved());
            // Closed without committing: the budget comes back and the item never sets off.
        }
        assertEquals(0, network.moved());
        assertTrue(network.flights().isEmpty());
        assertEquals(BATCH, push(network, Direction.SOUTH, BATCH));
        fly(network, 0);
        assertEquals(BATCH, count(chest));
    }

    @Test
    void deliveryStartsAtADifferentInventoryEachInterval(MinecraftServer server) {
        var first = chest(0);
        var second = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, first), new FakeEndpoint(Direction.SOUTH, second));
        run(network, 0, 2 * INTERVAL);
        push(network, Direction.UP, 1);
        run(network, 2 * INTERVAL, 3 * INTERVAL);
        push(network, Direction.UP, 1);
        run(network, 3 * INTERVAL, 4 * INTERVAL);
        assertEquals(1, count(first));
        assertEquals(1, count(second));
    }

    @Test
    void theCableHoldsNothingForOthersAndNothingCanBePulledOutOfIt(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.SOUTH));
        push(network, Direction.SOUTH, 2);
        assertTrue(face.getResource(0).isEmpty());
        assertEquals(0, face.getAmountAsInt(0));
        // A machine's input face pulling from the cable, or a hopper under it, finds nothing.
        assertEquals(0, ResourceHandlerUtil.moveStacking(face, chest(0), resource -> true, 64, null));
    }

    @Test
    void higherPriorityIsServedFirstAndTheRestOnlyGetsTheOverflow(MinecraftServer server) {
        // The favoured chest has room for one stack of cobblestone in its single slot; only once it
        // is full, counting what is already on its way, does anything go to the others.
        var favoured = new ItemStacksResourceHandler(1);
        var first = chest(0);
        var last = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new FakeEndpoint(Direction.NORTH, first),
                new PrioritisedEndpoint(Direction.SOUTH, 3, favoured),
                new PrioritisedEndpoint(Direction.EAST, -2, last)));
        var face = network.handlerFor(new ItemCableNetwork.EndpointKey(CABLE, Direction.UP));
        assertEquals(64, ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 64, null));
        run(network, 0, INTERVAL);
        assertEquals(64, count(favoured));
        assertEquals(0, count(first));
        assertEquals(10, ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 10, null));
        fly(network, INTERVAL);
        assertEquals(10, count(first));
        assertEquals(0, count(last));
        long tick = INTERVAL;
        for (int round = 2; round <= 20; round++) {
            run(network, tick, round * INTERVAL);
            tick = round * INTERVAL;
            ResourceHandlerUtil.insertStacking(face, ItemResource.of(Items.COBBLESTONE), 64, null);
        }
        run(network, tick, tick + INTERVAL);
        // Three slots of 64 fill the middle chest; only then does the lowest priority see anything.
        assertEquals(64, count(favoured));
        assertEquals(3 * 64, count(first));
        assertTrue(count(last) > 0);
        assertTrue(network.flights().isEmpty(), "Nothing is sent that has nowhere to go");
    }

    @Test
    void pumpsWithHigherPriorityGetTheRoomFirst(MinecraftServer server) {
        // Each pump has its own budget, so priority only matters when the room runs out: a sink
        // with space for one item takes it from the favoured pump, and the other stays full
        // because the room is already spoken for by the item on its way.
        var urgent = chest(64);
        var later = chest(64);
        var sink = new ItemStacksResourceHandler(1);
        sink.set(0, ItemResource.of(Items.COBBLESTONE), 64 - BATCH);
        var network = network(new ExtractOnlyEndpoint(Direction.NORTH, later),
                new PrioritisedPump(Direction.SOUTH, 5, urgent), new FakeEndpoint(Direction.EAST, sink));
        run(network, 0, 3 * INTERVAL + TRIP);
        assertEquals(64 - BATCH, count(urgent));
        assertEquals(64, count(later));
        assertEquals(64, count(sink));
    }

    @Test
    void aFlightWhoseDestinationFilledUpGoesElsewhereOrWaitsForRoom(MinecraftServer server) {
        var nearlyFull = new ItemStacksResourceHandler(1);
        nearlyFull.set(0, ItemResource.of(Items.COBBLESTONE), 63);
        var spare = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, nearlyFull), new FakeEndpoint(Direction.EAST, spare));
        assertEquals(1, push(network, Direction.SOUTH, 1));
        // Someone else takes the last slot while the item is on its way.
        nearlyFull.set(0, ItemResource.of(Items.COBBLESTONE), 64);
        fly(network, 0);
        assertEquals(0, count(spare), "It turns back and heads for the spare chest");
        assertEquals(1, network.flights().size());
        fly(network, TRIP);
        assertEquals(1, count(spare));
        assertTrue(network.flights().isEmpty());

        // With nowhere else to go it waits at the exit and tries again each interval.
        var lonely = network(new FakeEndpoint(Direction.NORTH, nearlyFull));
        nearlyFull.set(0, ItemResource.of(Items.COBBLESTONE), 63);
        assertEquals(1, push(lonely, Direction.SOUTH, 1));
        nearlyFull.set(0, ItemResource.of(Items.COBBLESTONE), 64);
        run(lonely, 0, INTERVAL);
        assertTrue(lonely.flights().getFirst().waiting);
        nearlyFull.set(0, ItemResource.of(Items.COBBLESTONE), 32);
        run(lonely, INTERVAL, 2 * INTERVAL);
        assertEquals(33, count(nearlyFull));
        assertTrue(lonely.flights().isEmpty());
    }

    @Test
    void flightsKeptByCablesAreTakenOverByTheNextNetwork(MinecraftServer server) {
        var chest = chest(0);
        var network = network(new FakeEndpoint(Direction.NORTH, chest));
        var flight = new ItemFlight(7, new ItemStack(Items.COBBLESTONE, 3), List.of(CABLE), Direction.SOUTH, Direction.NORTH,
                DyeColor.WHITE, 0, 1, TRIP, false);
        network.adopt(List.of(flight));
        assertEquals(List.of(flight), network.flights());
        assertEquals(CABLE, flight.current());
        fly(network, 0);
        assertEquals(3, count(chest));
        // A kept flight whose destination is gone waits where it is until a route turns up.
        var stranded = new ItemFlight(8, new ItemStack(Items.DIRT), List.of(CABLE.east()), Direction.WEST, Direction.EAST,
                DyeColor.RED, 0, 0, TRIP, false);
        network.adopt(List.of(stranded));
        assertTrue(stranded.waiting);
        assertEquals(List.of(CABLE.east()), stranded.path);
    }

    @Test
    void journeyPathFollowsTheCablesFromEntryToExit(MinecraftServer server) {
        // An L of cables with a dead-end stub: the path takes the corner and ignores the stub.
        BlockPos a = BlockPos.ZERO;
        BlockPos b = a.east();
        BlockPos c = b.east();
        BlockPos d = c.north();
        BlockPos stub = a.north();
        var network = new ItemCableNetwork(BATCH, INTERVAL, TRIP, Set.of(a, b, c, d, stub), List.of());
        assertEquals(List.of(a, b, c, d), network.path(a, d));
        assertEquals(List.of(d, c, b, a), network.path(d, a));
        assertEquals(List.of(b), network.path(b, b));
        assertEquals(List.of(a), network.path(a, a.south()), "An unreachable exit leaves the item at the entry");
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

    @Test
    void speedUpgradesMakeWhatEntersThereTravelFaster(MinecraftServer server) {
        assertEquals(TRIP, ItemCableNetwork.ticksPerBlock(TRIP, 0));
        assertEquals(1, ItemCableNetwork.ticksPerBlock(TRIP, ItemCableNetwork.MAX_UPGRADES));
        assertEquals(1, ItemCableNetwork.ticksPerBlock(ItemCableNetwork.TRAVEL_TICKS_PER_BLOCK, ItemCableNetwork.MAX_UPGRADES));
        assertTrue(ItemCableNetwork.ticksPerBlock(ItemCableNetwork.TRAVEL_TICKS_PER_BLOCK, 16) < ItemCableNetwork.TRAVEL_TICKS_PER_BLOCK);
        // A fully upgraded pump on a three-cable run gets its item across in three ticks, not twelve.
        BlockPos a = CABLE;
        BlockPos b = a.east();
        BlockPos c = b.east();
        var chest = chest(64);
        var machine = new ItemStacksResourceHandler(9);
        var network = new ItemCableNetwork(BATCH, INTERVAL, TRIP, Set.of(a, b, c), List.of(
                new UpgradedPump(Direction.WEST, ItemCableNetwork.MAX_UPGRADES, chest),
                new FakeEndpointAt(new ItemCableNetwork.EndpointKey(c, Direction.EAST), true, false, machine)));
        network.tick(INTERVAL);
        assertEquals(1, network.flights().getFirst().ticksPerBlock);
        run(network, INTERVAL, INTERVAL + 2);
        assertEquals(0, count(machine));
        network.tick(INTERVAL + 3);
        assertEquals(64, count(machine));
    }

    @Test
    void aFilteredConnectorOnlyReceivesWhatItsCardAllows(MinecraftServer server) {
        var stone = chest(0);
        var anything = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, true, false, COBBLESTONE_ONLY, stone),
                new FakeEndpoint(Direction.SOUTH, anything)));
        // Dirt has one place to go; cobblestone is offered to the filtered chest first in this round.
        assertEquals(4, push(network, Direction.UP, Items.DIRT, 4));
        fly(network, 0);
        assertEquals(0, count(stone));
        assertEquals(4, count(anything));
        var stoneOnly = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, true, false, COBBLESTONE_ONLY, chest(0))));
        assertEquals(0, push(stoneOnly, Direction.UP, Items.DIRT, 4));
        assertEquals(4, push(stoneOnly, Direction.UP, Items.COBBLESTONE, 4));
    }

    @Test
    void aFilteredConnectorRefusesWhatItsCardKeepsOutAtTheEntrance(MinecraftServer server) {
        var sink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.WEST, true, false, COBBLESTONE_ONLY, null),
                new FakeEndpoint(Direction.EAST, sink)));
        assertEquals(0, push(network, Direction.WEST, Items.DIRT, 4));
        assertEquals(4, push(network, Direction.WEST, Items.COBBLESTONE, 4));
        fly(network, 0);
        assertEquals(4, count(sink));
    }

    @Test
    void aFilteredPumpLeavesWhatItsCardKeepsOutInTheChest(MinecraftServer server) {
        var mixed = new ItemStacksResourceHandler(3);
        mixed.set(0, ItemResource.of(Items.DIRT), 8);
        mixed.set(1, ItemResource.of(Items.COBBLESTONE), 8);
        var sink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new FilteredEndpoint(Direction.NORTH, false, true, COBBLESTONE_ONLY, mixed),
                new FakeEndpoint(Direction.SOUTH, sink)));
        network.tick(INTERVAL);
        assertEquals(8, mixed.getAmountAsInt(0), "Dirt stays");
        assertEquals(0, mixed.getAmountAsInt(1), "Cobblestone is pumped");
        fly(network, INTERVAL);
        assertEquals(8, count(sink));
    }

    @Test
    void itemsOnlyTravelBetweenConnectorsOfTheSameColour(MinecraftServer server) {
        var redChest = chest(64);
        var redSink = chest(0);
        var blueSink = chest(0);
        var whiteSink = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.NORTH, false, true, DyeColor.RED, redChest),
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.BLUE, blueSink),
                new FakeEndpoint(Direction.UP, whiteSink),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, redSink)));
        network.tick(INTERVAL);
        fly(network, INTERVAL);
        assertEquals(64, count(redSink));
        assertEquals(0, count(blueSink));
        assertEquals(0, count(whiteSink), "Connectors left white are their own channel");
        // A push through a blue face only reaches blue; with none listening it is refused.
        var onlyRed = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.BLUE, null),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, chest(0))));
        assertEquals(0, push(onlyRed, Direction.EAST, Items.DIRT, 4));
    }

    @Test
    void itemsOnlyTravelBetweenConnectorsOnTheSameChannelNumber(MinecraftServer server) {
        var source = chest(64);
        var sameLine = chest(0);
        var otherLine = chest(0);
        var otherColour = chest(0);
        var network = new ItemCableNetwork(64, INTERVAL, TRIP, Set.of(CABLE), List.of(
                new ColouredEndpoint(Direction.NORTH, false, true, DyeColor.RED, 7, source),
                new ColouredEndpoint(Direction.EAST, true, false, DyeColor.RED, 8, otherLine),
                new ColouredEndpoint(Direction.WEST, true, false, DyeColor.BLUE, 7, otherColour),
                new ColouredEndpoint(Direction.SOUTH, true, false, DyeColor.RED, 7, sameLine)));
        network.tick(INTERVAL);
        fly(network, INTERVAL);
        assertEquals(64, count(sameLine));
        assertEquals(0, count(otherLine), "Same colour, other channel");
        assertEquals(0, count(otherColour), "Same channel, other colour");
    }
}
