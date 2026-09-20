package dev.futuretech.redstone;

import dev.futuretech.block.RainSensorBlock;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * The rain sensor under a real sky: dry it says nothing, rain set over the world reaches it once
 * the rain has thickened, a storm fills it, and inverted it says the opposite. It reads once a
 * second and the rain thickens by a hundredth a tick, so every step waits for both. A run of
 * dust beside it is watched too: what the sensor says has to reach the wire, up and down.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class RainSensorGameTests {
    /** The rain crosses a fifth — where the world calls it raining — twenty ticks after it starts, plus a read. */
    private static final int THICKEN = 45;
    /** Rain set to stop takes as long to thin as it took to thicken, and longer from a storm's full strength. */
    private static final int THIN = 120;

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final BlockPos sensor;

    private RainSensorGameTests(GameTestHelper helper, BlockPos sensor) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.sensor = sensor;
    }

    private int power() { return level.getBlockState(sensor).getValue(RainSensorBlock.POWER); }

    private int wire() { return level.getBlockState(sensor.east()).getValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER); }

    private void weather(boolean rain, boolean thunder) {
        if (rain) level.getServer().setWeatherParameters(0, 6_000, true, thunder);
        else level.getServer().setWeatherParameters(6_000, 0, false, false);
    }

    private void theSensorFollowsTheRainAndInvertedTheDry(GameTestHelper helper) {
        helper.startSequence().thenExecute(() -> {
            weather(false, false);
            // The test framework roofs its plot; the sensor needs the sky, so the column over it is opened.
            for (int up = 1; up <= 16; up++) level.removeBlock(sensor.above(up), false);
            level.setBlock(sensor, ModBlocks.RAIN_SENSOR.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sensor.east().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sensor.east(), net.minecraft.world.level.block.Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        }).thenIdle(THICKEN).thenExecute(() -> {
            assertEquals(0, power(), "dry");
            assertEquals(0, wire(), "dry, on the wire");
            weather(true, false);
        }).thenIdle(THICKEN).thenExecute(() -> {
            int power = power();
            assertTrue(power > 0 && power < 15, "raining, and still thickening: " + power);
            weather(true, true);
        }).thenIdle(THICKEN).thenExecute(() -> {
            assertEquals(15, power(), "a storm");
            assertEquals(15, wire(), "a storm, on the wire");
            level.setBlock(sensor, level.getBlockState(sensor).cycle(RainSensorBlock.INVERTED), Block.UPDATE_ALL);
        }).thenIdle(THICKEN).thenExecute(() -> {
            assertEquals(0, power(), "a storm, inverted");
            assertEquals(0, wire(), "a storm, inverted, on the wire");
            weather(false, false);
        }).thenIdle(THIN).thenExecute(() -> {
            assertEquals(15, power(), "dry, inverted");
            assertEquals(15, wire(), "dry, inverted, on the wire");
            level.setBlock(sensor, level.getBlockState(sensor).cycle(RainSensorBlock.INVERTED), Block.UPDATE_ALL);
        }).thenIdle(THICKEN).thenExecute(() -> {
            assertEquals(0, power(), "dry again, the right way round");
            assertEquals(0, wire(), "and the wire went out with it");
        }).thenSucceed();
    }

    private void assertTrue(boolean value, String message) {
        if (!value) throw helper.assertionException(net.minecraft.network.chat.Component.literal(message));
    }

    private void assertEquals(int expected, int actual, String message) {
        assertTrue(expected == actual, message + ": " + expected + " != " + actual);
    }

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "rain_sensor_" + name);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(id("sky"), (java.util.function.Consumer<GameTestHelper>) helper ->
                        new RainSensorGameTests(helper, helper.absolutePos(new BlockPos(5, 5, 5))).theSensorFollowsTheRainAndInvertedTheDry(helper)));
    }

    /** Its own environment: the weather is the whole server's, so nothing else runs beside it. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("sky"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "energy_empty"), 400, 0, true);
        event.registerTest(id("sky"), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id("sky")), data));
    }
}
