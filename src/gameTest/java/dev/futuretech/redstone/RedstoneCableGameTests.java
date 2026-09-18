package dev.futuretech.redstone;

import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.RedstoneCableBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The redstone cable in a world: what a connector set to insert reads is what the connectors
 * set to extract on its line give out, at the strength it was read, to the block beside them and
 * on through a block of stone. A real level is the only place to check it — the cable reads the
 * redstone around it and hands power back to it, and the lamp at the end is what a player wires.
 *
 * <p>A cable walks its network on the tick after it was placed or set up, so every case sets its
 * cables up, waits for that, and only then looks; a lamp takes four ticks to go dark, so every
 * case that turns one off waits those too.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class RedstoneCableGameTests {
    /** The tick after a change, and one to spare for where in the tick the change fell. */
    private static final int WALK = 2;
    /** A lamp goes out four ticks after its signal does, on top of the walk. */
    private static final int LAMP_OFF = WALK + 4;

    private final GameTestHelper helper;
    private final ServerLevel level;
    /** The west end of the line; the cables run east from here. */
    private final BlockPos start;

    private RedstoneCableGameTests(GameTestHelper helper, BlockPos start) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.start = start;
    }

    private BlockPos cable(int index) { return start.east(index); }

    /** Lays cables from {@code start} eastward, {@code count} of them, on nothing. */
    private void lay(int count) {
        for (int index = 0; index < count; index++) {
            level.setBlock(cable(index), ModBlocks.REDSTONE_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private RedstoneCableBlockEntity at(BlockPos pos) {
        var cable = (RedstoneCableBlockEntity) level.getBlockEntity(pos);
        assertTrue(cable != null, "a cable at " + pos);
        return cable;
    }

    /** Sets a connector the way the screen does; the network is walked on the next tick. */
    private void set(BlockPos pos, Direction side, SideMode mode) {
        assertTrue(level.getBlockState(pos).getValue(dev.futuretech.block.AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side)),
                "the cable at " + pos + " links " + side.getName());
        at(pos).setConnectorMode(side, mode);
    }

    private void set(BlockPos pos, Direction side, SideMode mode, DyeColor color) {
        set(pos, side, mode);
        at(pos).setConnectorColor(side, color);
    }

    private void place(BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void clear(BlockPos pos) { place(pos, Blocks.AIR); }

    /**
     * Dust of a chosen strength, which is what a length of redstone coming from far away looks
     * like: laid first, then set, because setting the number on dust that is already there
     * leaves it alone.
     */
    private void dust(BlockPos pos, int power) {
        if (!level.getBlockState(pos).is(Blocks.REDSTONE_WIRE)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(pos, level.getBlockState(pos).setValue(RedStoneWireBlock.POWER, power), Block.UPDATE_ALL);
    }

    /**
     * The wrench on the arm of the cable at {@code pos} that points {@code side}: the hit lands
     * a little short of that face, which is what tells the click which arm it meant.
     */
    private net.minecraft.world.InteractionResult wrench(BlockPos pos, Direction side) {
        Vec3 hit = Vec3.atCenterOf(pos).add(side.getStepX() * 0.4, side.getStepY() * 0.4, side.getStepZ() * 0.4);
        return ModBlocks.REDSTONE_CABLE.get().toggleLink(level, pos, level.getBlockState(pos), hit, side);
    }

    private boolean linked(BlockPos pos, Direction side) {
        return level.getBlockState(pos).getValue(dev.futuretech.block.AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    private boolean lit(BlockPos lamp) {
        var state = level.getBlockState(lamp);
        assertTrue(state.is(Blocks.REDSTONE_LAMP), "a lamp at " + lamp);
        return state.getValue(RedstoneLampBlock.LIT);
    }

    /** A block of redstone read at one end lights the lamp at the other, and taking it away puts the lamp out. */
    private void aSignalReadAtOneEndLightsTheLampAtTheOther(GameTestHelper helper) {
        BlockPos source = start.west();
        BlockPos lamp = cable(3);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(source, Blocks.REDSTONE_BLOCK);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            set(cable(2), Direction.EAST, SideMode.INPUT);
            assertFalse(lit(lamp), "nothing has been walked yet");
        }).thenIdle(WALK).thenExecute(() -> {
            assertTrue(lit(lamp), "the lamp lights once the line is walked");
            assertEquals(15, level.getSignal(cable(2), Direction.WEST), "asked from the lamp's side, fifteen");
            assertEquals(15, level.getDirectSignal(cable(2), Direction.WEST), "and strong");
            assertEquals(0, level.getSignal(cable(2), Direction.EAST), "nothing out of the other end");
            assertEquals(0, level.getSignal(cable(1), Direction.NORTH), "nor out of a bare cable");
            clear(source);
            assertEquals(0, level.getSignal(cable(2), Direction.WEST), "gone the moment the source is");
        }).thenIdle(LAMP_OFF).thenExecute(() -> assertFalse(lit(lamp), "and the lamp goes out")).thenSucceed();
    }

    /** A seven read is a seven given out, and dust laid beside the connector lights to seven. */
    private void theStrengthTravelsRatherThanJustOnAndOff(GameTestHelper helper) {
        BlockPos feed = start.west();
        BlockPos tap = cable(3);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            dust(feed, 7);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            dust(tap, 0);
            set(cable(2), Direction.EAST, SideMode.INPUT);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(7, level.getSignal(cable(2), Direction.WEST));
            assertEquals(7, level.getBlockState(tap).getValue(RedStoneWireBlock.POWER), "the dust leaving it reads seven");
            dust(feed, 3);
            assertEquals(3, level.getSignal(cable(2), Direction.WEST), "a three comes out as a three");
            assertEquals(3, level.getBlockState(tap).getValue(RedStoneWireBlock.POWER));
        }).thenSucceed();
    }

    /**
     * Two lamps on two colours: each hears the source on its own colour and not the other's, and
     * a connector left on none beside a source reads nothing at all. The lamps stand apart: a lit
     * lamp is strongly powered, and would light a lamp against it whatever colour that one is on.
     */
    private void eachColourIsAWireOfItsOwn(GameTestHelper helper) {
        BlockPos whiteSource = cable(1).north();
        BlockPos redSource = cable(2).north();
        BlockPos whiteLamp = cable(0).south();
        BlockPos redLamp = cable(3).south();
        helper.startSequence().thenExecute(() -> {
            lay(4);
            place(whiteLamp, Blocks.REDSTONE_LAMP);
            place(redLamp, Blocks.REDSTONE_LAMP);
            place(whiteSource, Blocks.REDSTONE_BLOCK);
            place(redSource, Blocks.REDSTONE_BLOCK);
            set(cable(1), Direction.NORTH, SideMode.OUTPUT, DyeColor.WHITE);
            set(cable(0), Direction.SOUTH, SideMode.INPUT, DyeColor.WHITE);
            set(cable(3), Direction.SOUTH, SideMode.INPUT, DyeColor.RED);
        }).thenIdle(WALK).thenExecute(() -> {
            assertTrue(lit(whiteLamp), "white hears the white source");
            assertFalse(lit(redLamp), "nobody reads red yet: the connector beside its source is on none");
            set(cable(2), Direction.NORTH, SideMode.OUTPUT, DyeColor.RED);
        }).thenIdle(WALK).thenExecute(() -> {
            assertTrue(lit(redLamp), "now red hears its own");
            assertTrue(lit(whiteLamp), "and white is none the wiser");
            clear(whiteSource);
            assertEquals(0, level.getSignal(cable(0), Direction.NORTH), "white went quiet");
            assertEquals(15, level.getSignal(cable(3), Direction.NORTH), "red did not");
            at(cable(3)).setConnectorChannel(Direction.SOUTH, 4);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(0, level.getSignal(cable(3), Direction.NORTH), "red on channel four is another wire");
        }).thenSucceed();
    }

    /**
     * The block a connector gives into is strongly powered, the way a repeater's is, so a lamp
     * carries the signal on to the lamp beyond it; and the cable does not take its own signal in
     * that block for redstone at its door, so a connector told to work only without a signal
     * keeps working instead of switching itself off and on for ever.
     */
    private void theBlockGivenIntoPassesItOnAndTheCableDoesNotHearItself(GameTestHelper helper) {
        BlockPos source = start.west();
        BlockPos lamp = cable(3);
        BlockPos beyond = cable(4);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(beyond, Blocks.REDSTONE_LAMP);
            place(source, Blocks.REDSTONE_BLOCK);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            set(cable(2), Direction.EAST, SideMode.INPUT);
            at(cable(2)).setConnectorRedstone(Direction.EAST, RedstoneMode.LOW);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(15, level.getDirectSignal(cable(2), Direction.WEST), "strong into the lamp");
            assertEquals(15, level.getDirectSignalTo(lamp), "the lamp is strongly powered");
            assertTrue(lit(lamp));
            assertTrue(lit(beyond), "and passes it on to the lamp beyond it");
            assertFalse(at(cable(2)).isPowered(), "the cable does not hear its own signal in the lamp");
        }).thenSucceed();
    }

    /** A cable taken out of the middle of the line leaves the lamp with nothing, and putting it back lights it again. */
    private void aCableTakenOutCutsTheLine(GameTestHelper helper) {
        BlockPos source = start.west();
        BlockPos lamp = cable(3);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(source, Blocks.REDSTONE_BLOCK);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            set(cable(2), Direction.EAST, SideMode.INPUT);
        }).thenIdle(WALK).thenExecute(() -> {
            assertTrue(lit(lamp));
            clear(cable(1));
        }).thenIdle(LAMP_OFF).thenExecute(() -> {
            assertFalse(lit(lamp), "the line is cut");
            assertEquals(0, level.getSignal(cable(2), Direction.WEST));
            place(cable(1), ModBlocks.REDSTONE_CABLE.get());
        }).thenIdle(WALK).thenExecute(() -> {
            assertTrue(lit(lamp), "joined again");
        }).thenSucceed();
    }

    /** A connector told to work only with a signal at the cable waits for one, and the cable's redstone is its own. */
    private void aConnectorAnswersToTheRedstoneAtItsCable(GameTestHelper helper) {
        BlockPos source = start.west();
        BlockPos lamp = cable(3);
        BlockPos gate = cable(2).north();
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(source, Blocks.REDSTONE_BLOCK);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            set(cable(2), Direction.EAST, SideMode.INPUT);
            at(cable(2)).setConnectorRedstone(Direction.EAST, RedstoneMode.HIGH);
        }).thenIdle(WALK).thenExecute(() -> {
            assertFalse(lit(lamp), "no signal at the cable, so the connector waits");
            // A lever would do; a block of redstone against the cable is the same to it, and links
            // there as any source does, with its connector left on none.
            place(gate, Blocks.REDSTONE_BLOCK);
            assertTrue(at(cable(2)).isPowered());
            assertTrue(lit(lamp), "signalled, the connector works");
            clear(gate);
            assertFalse(at(cable(2)).isPowered());
            assertEquals(0, level.getSignal(cable(2), Direction.WEST));
        }).thenIdle(LAMP_OFF).thenExecute(() -> assertFalse(lit(lamp))).thenSucceed();
    }

    /** A step of a sequence fails its test by the framework's own exception; anything else would stop the server. */
    private void assertTrue(boolean value, String message) {
        if (!value) throw helper.assertionException(net.minecraft.network.chat.Component.literal(message));
    }

    private void assertTrue(boolean value) { assertTrue(value, "Expected true"); }
    private void assertFalse(boolean value, String message) { assertTrue(!value, message); }
    private void assertFalse(boolean value) { assertTrue(!value, "Expected false"); }
    private void assertEquals(long expected, long actual) { assertTrue(expected == actual, expected + " != " + actual); }
    private void assertEquals(long expected, long actual, String message) {
        assertTrue(expected == actual, message + ": " + expected + " != " + actual);
    }

    /**
     * The wrench joins the cable to a block it would not link to on its own — a wall of stone —
     * and takes the joint off again; on air it does nothing, and a forced joint waits out the
     * air when the block goes, taking hold again when one is put back.
     */
    private void theWrenchForcesALinkToAnyBlockAndTakesItOffAgain(GameTestHelper helper) {
        BlockPos stone = start.west();
        helper.startSequence().thenExecute(() -> {
            lay(1);
            place(stone, Blocks.STONE);
            assertFalse(linked(cable(0), Direction.WEST), "stone is a wall");
            assertTrue(wrench(cable(0), Direction.EAST) == net.minecraft.world.InteractionResult.PASS, "nothing to join to on the air side");
            assertTrue(wrench(cable(0), Direction.WEST) == net.minecraft.world.InteractionResult.SUCCESS);
            assertTrue(linked(cable(0), Direction.WEST), "joined by hand");
            assertTrue(at(cable(0)).isForced(Direction.WEST));
            clear(stone);
            assertFalse(linked(cable(0), Direction.WEST), "no block, no joint");
            assertTrue(at(cable(0)).isForced(Direction.WEST), "but the wish is kept");
            place(stone, Blocks.COBBLESTONE);
            assertTrue(linked(cable(0), Direction.WEST), "and holds to whatever comes back");
            assertTrue(wrench(cable(0), Direction.WEST) == net.minecraft.world.InteractionResult.SUCCESS);
            assertFalse(linked(cable(0), Direction.WEST), "taken off again");
            assertFalse(at(cable(0)).isForced(Direction.WEST));
        }).thenSucceed();
    }

    /**
     * A connector with its sensor on reads a chest the way a comparator does — how full it is —
     * and follows what goes in and out of it; the chest is joined by the wrench, since a chest
     * says nothing about redstone on its own.
     */
    private void aSensorReadsAChestLikeAComparator(GameTestHelper helper) {
        BlockPos chest = start.west();
        BlockPos lamp = cable(3);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(chest, Blocks.CHEST);
            assertTrue(wrench(cable(0), Direction.WEST) == net.minecraft.world.InteractionResult.SUCCESS, "joined to the chest by hand");
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            at(cable(0)).setSensor(Direction.WEST, true);
            set(cable(2), Direction.EAST, SideMode.INPUT);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(0, level.getSignal(cable(2), Direction.WEST), "an empty chest reads nothing");
            var container = (ChestBlockEntity) level.getBlockEntity(chest);
            assertTrue(container != null, "a chest");
            // Nine full stacks of twenty-seven slots: a third full, which a comparator calls five.
            for (int slot = 0; slot < 9; slot++) container.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
            assertEquals(5, level.getSignal(cable(2), Direction.WEST), "read the moment the chest changed");
            assertTrue(lit(lamp));
            for (int slot = 9; slot < 27; slot++) container.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
            assertEquals(15, level.getSignal(cable(2), Direction.WEST), "full");
            container.clearContent();
            container.setChanged();
            assertEquals(0, level.getSignal(cable(2), Direction.WEST), "empty again");
            at(cable(0)).setSensor(Direction.WEST, false);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(0, level.getSignal(cable(2), Direction.WEST), "with the sensor off, a chest gives no signal at all");
        }).thenSucceed();
    }

    /** A connector switched to weak wakes the lamp beside it and nothing beyond it. */
    private void aWeakConnectorWakesTheBlockWithoutPoweringItThrough(GameTestHelper helper) {
        BlockPos source = start.west();
        BlockPos lamp = cable(3);
        BlockPos beyond = cable(4);
        helper.startSequence().thenExecute(() -> {
            lay(3);
            place(lamp, Blocks.REDSTONE_LAMP);
            place(beyond, Blocks.REDSTONE_LAMP);
            place(source, Blocks.REDSTONE_BLOCK);
            set(cable(0), Direction.WEST, SideMode.OUTPUT);
            set(cable(2), Direction.EAST, SideMode.INPUT);
            at(cable(2)).setStrong(Direction.EAST, false);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(15, level.getSignal(cable(2), Direction.WEST), "the signal is there");
            assertEquals(0, level.getDirectSignal(cable(2), Direction.WEST), "but not as strong power");
            assertTrue(lit(lamp), "the lamp beside it wakes");
            assertFalse(lit(beyond), "the lamp beyond it does not");
            at(cable(2)).setStrong(Direction.EAST, true);
            assertEquals(15, level.getDirectSignal(cable(2), Direction.WEST), "strong again, at once");
            assertTrue(lit(beyond), "and the lamp beyond hears it");
        }).thenSucceed();
    }

    private static final java.util.Map<String, java.util.function.BiConsumer<RedstoneCableGameTests, GameTestHelper>> CASES = java.util.Map.of(
            "lamp", RedstoneCableGameTests::aSignalReadAtOneEndLightsTheLampAtTheOther,
            "analog", RedstoneCableGameTests::theStrengthTravelsRatherThanJustOnAndOff,
            "colours", RedstoneCableGameTests::eachColourIsAWireOfItsOwn,
            "through", RedstoneCableGameTests::theBlockGivenIntoPassesItOnAndTheCableDoesNotHearItself,
            "cut", RedstoneCableGameTests::aCableTakenOutCutsTheLine,
            "gated", RedstoneCableGameTests::aConnectorAnswersToTheRedstoneAtItsCable,
            "forced", RedstoneCableGameTests::theWrenchForcesALinkToAnyBlockAndTakesItOffAgain,
            "sensor", RedstoneCableGameTests::aSensorReadsAChestLikeAComparator,
            "weak", RedstoneCableGameTests::aWeakConnectorWakesTheBlockWithoutPoweringItThrough);

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "redstone_cable_" + name);
    }

    /** Plain test functions, the way the wireless cases are registered; each one runs as a sequence of ticks. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> CASES.forEach((name, test) ->
                registry.register(id(name), (java.util.function.Consumer<GameTestHelper>) helper ->
                        test.accept(new RedstoneCableGameTests(helper, helper.absolutePos(new BlockPos(2, 5, 5))), helper))));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "redstone_cable"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "redstone_cable_empty"), 200, 0, true);
        for (String name : CASES.keySet()) {
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
