package dev.futuretech.redstone;

import dev.futuretech.block.WirelessRedstoneBlock;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;

/**
 * The two ends over a frequency, in a world: what the transmitter reads is what the receiver gives
 * out, however far apart they stand, and only while both are on the same number. A real level is
 * the only place to check it — the plates read the redstone around them and hand power back to it.
 *
 * <p>Redstone only comes and goes by the front of a plate, which is the side its lamp is on, so
 * every case here wires the plates from there; {@link #redstoneOnlyComesAndGoesByTheFront} is the
 * one that walks all the way round to make sure no other side answers.
 *
 * <p>Each case works on a frequency of its own, so the cases the framework runs one after another
 * in the same world never hear each other.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class WirelessRedstoneGameTests {
    /** A plate laid on the floor looks south, and that is the side its redstone uses. */
    private static final Direction FRONT = Direction.SOUTH;

    private final ServerLevel level;
    /** Where the transmitter goes; the receiver stands a few blocks away, which it need not. */
    private final BlockPos sending;
    private final BlockPos listening;

    private WirelessRedstoneGameTests(ServerLevel level, BlockPos centre) {
        this.level = level;
        this.sending = centre;
        this.listening = centre.offset(3, 0, 3);
    }

    /** Mounts a plate on the block below and signs it in, the way the level does a tick after the chunk loads. */
    private WirelessRedstoneBlockEntity place(BlockPos pos, WirelessRedstoneBlock block, int frequency) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, block.defaultBlockState().setValue(WirelessRedstoneBlock.FACING, Direction.UP), Block.UPDATE_ALL);
        var plate = (WirelessRedstoneBlockEntity) level.getBlockEntity(pos);
        assertTrue(plate != null, "the plate needs its block entity");
        assertTrue(WirelessRedstoneBlock.front(level.getBlockState(pos)) == FRONT, "a plate on the floor looks south");
        plate.connect();
        plate.setFrequency(frequency);
        return plate;
    }

    /** A patch of floor for dust to lie on, with nothing on it yet. */
    private void clearFor(BlockPos... places) {
        for (BlockPos pos : places) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Takes both plates down again, and everything wired to them, so the next case starts clean. */
    private void clear() {
        for (BlockPos pos : new BlockPos[]{sending, listening}) {
            for (Direction side : Direction.values()) {
                if (side == Direction.DOWN) continue;
                level.setBlock(pos.relative(side), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos.relative(side).relative(side), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** A block of redstone against the front of the transmitter: fifteen, from the side that counts. */
    private void power() {
        level.setBlock(sending.relative(FRONT), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void unpower() {
        level.setBlock(sending.relative(FRONT), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * A wire of a chosen strength on the transmitter's front, which is what a length of redstone
     * coming from far away looks like to the plate. A wire works its own strength out the moment it
     * is laid; setting the number on a wire that is already there leaves it alone, so the feed
     * stays where the test put it.
     */
    private void feed(int power) {
        BlockPos front = sending.relative(FRONT);
        if (!level.getBlockState(front).is(Blocks.REDSTONE_WIRE)) {
            clearFor(front);
            level.setBlock(front, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
        // The wire as it lies, with only its strength changed: a fresh one would come with no
        // connections, and a wire joined to nothing hands its power to nobody.
        var wire = level.getBlockState(front);
        level.setBlock(front, wire.setValue(RedStoneWireBlock.POWER, power), Block.UPDATE_CLIENTS);
    }

    /** Whether the dust at {@code pos} has joined itself to what lies that way. */
    private boolean connected(BlockPos pos, Direction towards) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_WIRE)
                && state.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(towards)).isConnected();
    }

    void whatTheTransmitterReadsIsWhatTheReceiverGivesOut() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 11);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 11);
        assertEquals(0, receiver.power(), "nothing is being sent yet");

        // Against the front of the transmitter: the neighbour change is what makes it read.
        power();
        assertEquals(15, transmitter.power());
        assertEquals(15, receiver.power());
        assertTrue(level.getBlockState(listening).getValue(WirelessRedstoneBlock.LIT), "the lamp shows it");
        // Asked the way the block on the receiver's front asks: from there, back at the plate.
        assertEquals(15, level.getSignal(listening, FRONT.getOpposite()), "weak power out of the front");
        assertEquals(15, level.getDirectSignal(listening, FRONT.getOpposite()), "and strong power with it");
        assertEquals(0, level.getSignal(listening, FRONT), "nothing out of the back");
        assertEquals(15, level.getBestNeighborSignal(listening.relative(FRONT)), "the block on the front is powered");

        unpower();
        assertEquals(0, transmitter.power());
        assertEquals(0, receiver.power());
        assertFalse(level.getBlockState(listening).getValue(WirelessRedstoneBlock.LIT));
        assertEquals(0, level.getSignal(listening, FRONT.getOpposite()));
        clear();
    }

    void theStrengthTravelsRatherThanJustOnAndOff() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 22);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 22);

        feed(7);
        transmitter.sample();
        assertEquals(7, transmitter.power());
        assertEquals(7, receiver.power(), "a seven comes out of the other end as a seven");
        assertEquals(7, level.getSignal(listening, FRONT.getOpposite()));

        feed(3);
        transmitter.sample();
        assertEquals(3, receiver.power());
        clear();
    }

    void onlyThePlatesOnTheSameFrequencyHearEachOther() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 33);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 33);
        power();
        assertEquals(15, receiver.power());
        assertEquals(2, WirelessRedstone.count(33));

        // Off to another frequency: there is nobody sending on that one.
        receiver.setFrequency(3300);
        assertEquals(0, receiver.power());
        assertEquals(1, WirelessRedstone.count(33));
        assertEquals(1, WirelessRedstone.count(3300));

        // The transmitter follows it there, and the signal is back.
        transmitter.setFrequency(3300);
        assertEquals(15, receiver.power());
        assertEquals(2, WirelessRedstone.count(3300));
        assertEquals(0, WirelessRedstone.count(33), "neither is on the frequency they left");

        // Four digits is as far as a frequency goes.
        receiver.setFrequency(123456);
        assertEquals(WirelessRedstoneBlockEntity.MAX_FREQUENCY, receiver.frequency());
        assertEquals(0, receiver.power());
        clear();
    }

    void aTransmitterTakenDownTakesItsSignalWithIt() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 44);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 44);
        power();
        assertEquals(15, receiver.power());

        level.setBlock(sending, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertTrue(transmitter.isRemoved(), "the block entity went with the block");
        assertEquals(0, receiver.power(), "nobody is sending any more");
        assertEquals(0, WirelessRedstone.strength(44));
        assertEquals(1, WirelessRedstone.count(44), "only the receiver is left on the frequency");
        assertEquals(0, level.getSignal(listening, FRONT.getOpposite()));
        clear();
    }

    /**
     * The way a player actually wires one: a line of dust up to the front of the plate, and another
     * leaving the front of the other. Dust only hands power to a block it has joined itself to, so
     * this is the case a block of redstone against the plate never proved.
     */
    void aLineOfDustFeedsOnePlateAndLeavesTheOther() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 55);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 55);
        BlockPos feed = sending.relative(FRONT);
        BlockPos source = feed.relative(FRONT);
        BlockPos tap = listening.relative(FRONT);
        clearFor(feed, source, tap);
        level.setBlock(tap, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(feed, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        assertTrue(connected(feed, FRONT.getOpposite()), "the dust has to join itself to the transmitter");
        assertEquals(15, transmitter.power(), "the transmitter reads the dust running into it");
        assertEquals(15, receiver.power());
        assertTrue(connected(tap, FRONT.getOpposite()), "and to the receiver");
        assertEquals(15, level.getBlockState(tap).getValue(RedStoneWireBlock.POWER),
                "the receiver lights the dust leaving it");

        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertEquals(0, transmitter.power(), "the dust went dark, so the transmitter did");
        assertEquals(0, level.getBlockState(tap).getValue(RedStoneWireBlock.POWER));
        clear();
    }

    /**
     * All the way round both plates: dust run into each of the four sides in turn, then a block of
     * redstone on the one it is mounted on and in the space over it. Only the front may answer —
     * the back and the sides belong to whatever else the player is wiring past the plate.
     */
    void redstoneOnlyComesAndGoesByTheFront() {
        var transmitter = place(sending, ModBlocks.WIRELESS_TRANSMITTER.get(), 66);
        var receiver = place(listening, ModBlocks.WIRELESS_RECEIVER.get(), 66);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean isFront = side == FRONT;
            int expected = isFront ? 15 : 0;
            BlockPos feed = sending.relative(side);
            BlockPos tap = listening.relative(side);
            // Each line of dust is fed from beside it rather than from behind it. Dust with
            // nothing either side of it draws itself straight on into whatever it runs up against
            // — a wall of stone as much as a plate — so dust fed from behind would look joined to
            // the plate whatever the plate said. Fed from the side it has no such reason, and what
            // it draws is the plate's own answer.
            BlockPos source = feed.relative(side.getClockWise());
            BlockPos beside = tap.relative(side.getClockWise());
            clearFor(feed, source, tap, beside);
            level.setBlock(tap, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(beside, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(feed, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

            String where = "dust on the " + side + " side";
            assertTrue(connected(feed, side.getOpposite()) == isFront,
                    where + " joins the transmitter: " + level.getBlockState(feed));
            assertEquals(expected, transmitter.power(), where + " is read");
            assertEquals(expected, receiver.power(), where + " reaches the other end");
            assertTrue(connected(tap, side.getOpposite()) == isFront,
                    where + " joins the receiver: " + level.getBlockState(tap));
            assertEquals(expected, level.getBlockState(tap).getValue(RedStoneWireBlock.POWER),
                    where + " is lit by the receiver");

            for (BlockPos pos : new BlockPos[]{feed, source, tap, beside}) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            assertEquals(0, transmitter.power(), where + " went and the reading stayed");
        }
        // The block it hangs on is its back, and the space over it is neither: both stay quiet.
        level.setBlock(sending.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        assertEquals(0, transmitter.power(), "the block it is mounted on is behind it");
        level.setBlock(sending.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(sending.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        assertEquals(0, transmitter.power(), "and the space over it is not its front either");
        clear();
    }

    private static void assertTrue(boolean value) { assertTrue(value, "Expected true"); }
    private static void assertTrue(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void assertFalse(boolean value) { assertTrue(!value, "Expected false"); }
    private static void assertEquals(long expected, long actual) { assertTrue(expected == actual, expected + " != " + actual); }
    private static void assertEquals(long expected, long actual, String message) {
        assertTrue(expected == actual, message + ": " + expected + " != " + actual);
    }

    private static final java.util.Map<String, java.util.function.Consumer<WirelessRedstoneGameTests>> CASES = java.util.Map.of(
            "signal", WirelessRedstoneGameTests::whatTheTransmitterReadsIsWhatTheReceiverGivesOut,
            "analog", WirelessRedstoneGameTests::theStrengthTravelsRatherThanJustOnAndOff,
            "frequency", WirelessRedstoneGameTests::onlyThePlatesOnTheSameFrequencyHearEachOther,
            "removed", WirelessRedstoneGameTests::aTransmitterTakenDownTakesItsSignalWithIt,
            "wired", WirelessRedstoneGameTests::aLineOfDustFeedsOnePlateAndLeavesTheOther,
            "front", WirelessRedstoneGameTests::redstoneOnlyComesAndGoesByTheFront);

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "wireless_" + name);
    }

    /** Plain test functions, the way the area tools' cases are registered. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> CASES.forEach((name, test) ->
                registry.register(id(name), (java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper>) helper -> {
                    test.accept(new WirelessRedstoneGameTests(helper.getLevel(), helper.absolutePos(new BlockPos(5, 5, 5))));
                    helper.succeed();
                })));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "wireless"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "wireless_empty"), 200, 0, true);
        for (String name : CASES.keySet()) {
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
