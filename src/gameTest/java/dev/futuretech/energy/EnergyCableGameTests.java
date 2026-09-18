package dev.futuretech.energy;

import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.TimeControllerBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Energy on its way from a battery to a machine, in a real level: straight into the machine
 * beside it, and along an energy cable. The time controller is the machine, since it takes
 * energy on every face with nothing to configure; what these prove is the path, not the sink.
 * The last cases are the controller's own trigger, one per redstone mode: a signal arriving
 * moves the clock forward and takes the fare, and holding it does nothing more; a signal going
 * does the same on the other setting; and with the redstone ignored, picking a moment fires.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class EnergyCableGameTests {
    /** The tick after a change, and one to spare for where in the tick the change fell. */
    private static final int WALK = 2;

    private final GameTestHelper helper;
    private final ServerLevel level;
    /** Where the machine stands; the battery and the cable run west from it. */
    private final BlockPos machine;

    private EnergyCableGameTests(GameTestHelper helper, BlockPos machine) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.machine = machine;
    }

    private void place(BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private TimeControllerBlockEntity controller() {
        var entity = level.getBlockEntity(machine);
        assertTrue(entity instanceof TimeControllerBlockEntity, "a time controller at " + machine);
        return (TimeControllerBlockEntity) entity;
    }

    /** A full MK1 battery at {@code pos}, giving out through {@code side}. */
    private BatteryBlockEntity battery(BlockPos pos, Direction side) {
        place(pos, ModBlocks.BATTERY_MK1.get());
        var entity = level.getBlockEntity(pos);
        assertTrue(entity instanceof BatteryBlockEntity, "a battery at " + pos);
        var battery = (BatteryBlockEntity) entity;
        battery.sideConfig().set(side, SideMode.OUTPUT);
        battery.sideConfigChanged();
        ((TickLimitedEnergyHandler) battery.energy()).set(battery.tier().capacity());
        return battery;
    }

    private void aBatteryBesideAMachineFillsIt(GameTestHelper helper) {
        helper.startSequence().thenExecute(() -> {
            place(machine, ModBlocks.TIME_CONTROLLER.get());
            battery(machine.west(), Direction.EAST);
        }).thenIdle(WALK + 2).thenExecute(() -> {
            assertTrue(controller().energy().getAmountAsInt() > 0, "the machine has taken energy from the battery");
        }).thenSucceed();
    }

    private void aBatteryFillsAMachineAlongACable(GameTestHelper helper) {
        BlockPos cable = machine.west();
        helper.startSequence().thenExecute(() -> {
            place(cable, ModBlocks.ENERGY_CABLE_MK1.get());
            place(machine, ModBlocks.TIME_CONTROLLER.get());
            battery(cable.west(), Direction.EAST);
        }).thenIdle(WALK + 4).thenExecute(() -> {
            var state = level.getBlockState(cable);
            assertTrue(state.getValue(net.minecraft.world.level.block.PipeBlock.EAST), "the cable links to the machine");
            assertTrue(state.getValue(net.minecraft.world.level.block.PipeBlock.WEST), "and to the battery");
            assertTrue(controller().energy().getAmountAsInt() > 0, "the machine has taken energy along the cable");
        }).thenSucceed();
    }

    private long clock() { return level.getServer().clockManager().getTotalTicks(level.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD)); }

    /** A full controller set to noon, with the redstone read the given way. */
    private TimeControllerBlockEntity controller(RedstoneMode mode) {
        place(machine, ModBlocks.TIME_CONTROLLER.get());
        var controller = controller();
        ((TickLimitedEnergyHandler) controller.energy()).set(TimeControllerBlockEntity.CAPACITY);
        controller.redstoneControl().setMode(mode);
        controller.setMoment(DayMoment.NOON);
        return controller;
    }

    /** The clock is at the next noon from {@code before}, the day count did not fall, and the fare came off {@code energyBefore}. */
    private void assertJumped(long before, int energyBefore, String what) {
        long target = DayMoment.NOON.next(before);
        long now = clock();
        assertTrue(now >= target && now < target + 5, what + ": the clock is at the next noon, " + now + " for " + target);
        assertTrue(now / DayMoment.DAY_TICKS >= before / DayMoment.DAY_TICKS, what + ": the day count did not fall");
        // The world may have lived a tick or two between the reading and the jump, each one less to skip.
        long fare = energyBefore - controller().energy().getAmountAsInt();
        long expected = TimeControllerBlockEntity.cost(before, DayMoment.NOON);
        assertTrue(fare <= expected && fare >= expected - TimeControllerBlockEntity.COST_PER_TICK * (WALK + 1),
                what + ": the fare was taken, " + fare + " for " + expected);
    }

    private void aSignalArrivingMovesTheClockForwardAndTakesTheFareOnce(GameTestHelper helper) {
        BlockPos source = machine.west();
        long[] before = new long[1];
        int[] energy = new int[1];
        helper.startSequence().thenExecute(() -> {
            controller(RedstoneMode.HIGH);
        }).thenIdle(WALK).thenExecute(() -> {
            // A signal has to arrive: one already there when the block was placed is not one arriving.
            before[0] = clock();
            energy[0] = controller().energy().getAmountAsInt();
            place(source, Blocks.REDSTONE_BLOCK);
        }).thenIdle(WALK).thenExecute(() -> {
            assertJumped(before[0], energy[0], "on the signal");
            energy[0] = controller().energy().getAmountAsInt();
        }).thenIdle(WALK + 3).thenExecute(() -> {
            assertEquals(energy[0], controller().energy().getAmountAsInt(), "a signal held on is not paid for again");
            level.removeBlock(source, false);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(energy[0], controller().energy().getAmountAsInt(), "nor is the signal going");
            controller().setMoment(DayMoment.MIDNIGHT);
        }).thenIdle(WALK).thenExecute(() -> {
            assertEquals(energy[0], controller().energy().getAmountAsInt(), "and picking a moment waits for the signal");
        }).thenSucceed();
    }

    private void aSignalGoingFiresOnTheOtherSetting(GameTestHelper helper) {
        BlockPos source = machine.west();
        long[] before = new long[1];
        int[] energy = new int[1];
        helper.startSequence().thenExecute(() -> {
            place(source, Blocks.REDSTONE_BLOCK);
            controller(RedstoneMode.LOW);
        }).thenIdle(WALK).thenExecute(() -> {
            before[0] = clock();
            energy[0] = controller().energy().getAmountAsInt();
            level.removeBlock(source, false);
        }).thenIdle(WALK).thenExecute(() -> assertJumped(before[0], energy[0], "on the signal going")).thenSucceed();
    }

    private void withTheRedstoneIgnoredPickingAMomentFires(GameTestHelper helper) {
        long[] before = new long[1];
        int[] energy = new int[1];
        helper.startSequence().thenExecute(() -> {
            place(machine, ModBlocks.TIME_CONTROLLER.get());
            var controller = controller();
            ((TickLimitedEnergyHandler) controller.energy()).set(TimeControllerBlockEntity.CAPACITY);
            controller.redstoneControl().setMode(RedstoneMode.IGNORED);
            before[0] = clock();
            energy[0] = controller.energy().getAmountAsInt();
            controller.setMoment(DayMoment.NOON);
            assertJumped(before[0], energy[0], "on the pick");
        }).thenSucceed();
    }

    /** A step of a sequence fails its test by the framework's own exception; anything else would stop the server. */
    private void assertTrue(boolean value, String message) {
        if (!value) throw helper.assertionException(net.minecraft.network.chat.Component.literal(message));
    }

    private void assertEquals(long expected, long actual, String message) {
        assertTrue(expected == actual, message + ": " + expected + " != " + actual);
    }

    private static final java.util.Map<String, java.util.function.BiConsumer<EnergyCableGameTests, GameTestHelper>> CASES = java.util.Map.of(
            "beside", EnergyCableGameTests::aBatteryBesideAMachineFillsIt,
            "cable", EnergyCableGameTests::aBatteryFillsAMachineAlongACable,
            "signal_on", EnergyCableGameTests::aSignalArrivingMovesTheClockForwardAndTakesTheFareOnce,
            "signal_off", EnergyCableGameTests::aSignalGoingFiresOnTheOtherSetting,
            "ignored", EnergyCableGameTests::withTheRedstoneIgnoredPickingAMomentFires);

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "energy_" + name);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> CASES.forEach((name, test) ->
                registry.register(id(name), (java.util.function.Consumer<GameTestHelper>) helper ->
                        test.accept(new EnergyCableGameTests(helper, helper.absolutePos(new BlockPos(5, 5, 5))), helper))));
    }

    /** The cases that move the clock, which the whole server shares, so each runs in a batch of its own. */
    private static final java.util.Set<String> CLOCK_CASES = java.util.Set.of("signal_on", "signal_off", "ignored");

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var shared = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "energy"));
        for (String name : CASES.keySet()) {
            var environment = CLOCK_CASES.contains(name) ? event.registerEnvironment(id(name)) : shared;
            var data = new net.minecraft.gametest.framework.TestData<>(environment,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "energy_empty"), 200, 0, true);
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
