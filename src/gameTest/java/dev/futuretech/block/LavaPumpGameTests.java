package dev.futuretech.block;

import dev.futuretech.block.entity.LavaPumpBlockEntity;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Supplier;

/**
 * The lava pump over a small pool: the pipe going down to the floor, the pool taken from the far
 * end in, stone left where every source stood and the tank filling a bucket a source.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class LavaPumpGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "lava_pump_drains_a_pool");

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static int count(ServerLevel level, BlockPos from, BlockPos to, Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).is(block)) count++;
        }
        return count;
    }

    private static void drainsAPool(GameTestHelper helper) {
        var level = helper.getLevel();
        // A stone floor, a pool of nine sources on it, two blocks of air, and the pump over the middle.
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos poolFrom = floor.offset(-1, 1, -1);
        BlockPos poolTo = floor.offset(1, 1, 1);
        BlockPos middle = floor.above();
        BlockPos pumpPos = floor.above(4);
        Supplier<LavaPumpBlockEntity> pump = () -> (LavaPumpBlockEntity) level.getBlockEntity(pumpPos);
        helper.startSequence().thenExecute(() -> {
            fill(level, floor.offset(-4, -1, -4), floor.offset(4, 6, 4), Blocks.AIR);
            fill(level, floor.offset(-2, 0, -2), floor.offset(2, 0, 2), Blocks.STONE);
            // Walls round the pool, so nothing flows out of the test.
            fill(level, floor.offset(-2, 1, -2), floor.offset(2, 1, 2), Blocks.STONE);
            fill(level, poolFrom, poolTo, Blocks.LAVA);
            level.setBlock(pumpPos, ModBlocks.LAVA_PUMP.get().defaultBlockState()
                    .setValue(LavaPumpBlock.FACING, Direction.NORTH)
                    .setValue(dev.futuretech.api.upgrade.MachineLevel.MK, 4), Block.UPDATE_ALL);
            ((TickLimitedEnergyHandler) pump.get().energy()).set(pump.get().energy().getCapacityAsInt());
            check(helper, count(level, poolFrom, poolTo, Blocks.LAVA) == 9, "Nine sources to start with");
        }).thenIdle(15).thenExecute(() -> {
            // Two blocks of air and one of lava: the pipe stands on the floor, not on the surface.
            check(helper, pump.get().pipe() == 3, "The pipe drops through the lava to the floor, got " + pump.get().pipe());
            check(helper, pump.get().status() == LavaPumpBlockEntity.Status.PUMPING, "And the pump is drawing");
        }).thenIdle(60).thenExecute(() -> {
            int stone = count(level, poolFrom, poolTo, Blocks.STONE);
            check(helper, stone >= 2 && stone < 9, "Some sources are gone by now, got " + stone);
            check(helper, level.getFluidState(middle).isSourceOfType(Fluids.LAVA),
                    "The source under the pipe is the last to go");
            check(helper, pump.get().lavaAmount() == stone * LavaPumpBlockEntity.SOURCE_VOLUME,
                    "A bucket in the tank for every source taken");
            for (BlockPos pos : BlockPos.betweenClosed(poolFrom, poolTo)) {
                var fluid = level.getFluidState(pos);
                check(helper, fluid.isEmpty() || fluid.isSource(), "Stone in the gap: nothing is flowing at " + pos);
            }
        }).thenIdle(150).thenExecute(() -> {
            check(helper, count(level, poolFrom, poolTo, Blocks.STONE) == 9, "The whole pool is stone");
            check(helper, pump.get().lavaAmount() == 9 * LavaPumpBlockEntity.SOURCE_VOLUME, "And nine buckets are in the tank");
            check(helper, pump.get().status() == LavaPumpBlockEntity.Status.NO_LAVA, "With nothing left the pump waits");
            check(helper, pump.get().pipe() == 2, "The pipe came back up to the stone, got " + pump.get().pipe());
            // More lava turns up in the pipe's way: it is found again without anyone asking.
            level.setBlock(pumpPos.below(2), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        }).thenIdle(140).thenExecute(() -> {
            check(helper, level.getBlockState(pumpPos.below(2)).is(Blocks.STONE), "The new source is taken too");
            check(helper, pump.get().lavaAmount() == 10 * LavaPumpBlockEntity.SOURCE_VOLUME, "Ten buckets now");
        }).thenSucceed();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(ID, (java.util.function.Consumer<GameTestHelper>) LavaPumpGameTests::drainsAPool));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(ID);
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                Identifier.fromNamespaceAndPath("futuretech", "quarry_empty"), 420, 0, true);
        event.registerTest(ID, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, ID), data));
    }
}
