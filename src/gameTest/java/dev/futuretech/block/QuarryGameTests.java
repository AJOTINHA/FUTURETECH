package dev.futuretech.block;

import dev.futuretech.block.entity.MiningMarkerBlockEntity;
import dev.futuretech.block.entity.QuarryBlockEntity;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * The markers closing a square behind a quarry, the scaffold going up over it and the machine
 * digging what it encloses. One test, not several: markers link along their axes, and two tests
 * placing them at the same time would read each other's corners.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class QuarryGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "quarry_marks_and_digs");

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void marker(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.MINING_MARKER.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void clear(ServerLevel level, BlockPos... positions) {
        for (BlockPos pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static int link(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof MiningMarkerBlockEntity marker ? marker.link(side) : -1;
    }

    /** A spot on the scaffold's ring: the same column as {@code pos}, at the ring's height. */
    private static BlockPos ring(BlockPos corner, BlockPos pos) {
        return new BlockPos(pos.getX(), corner.getY() + QuarryFrame.HEIGHT, pos.getZ());
    }

    private static int standing(ServerLevel level, BlockPos from, BlockPos to) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) count++;
        }
        return count;
    }

    private static int held(QuarryBlockEntity quarry, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < QuarryBlockEntity.INVENTORY_SIZE; slot++) {
            ItemStack stack = quarry.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void marksAndDigs(GameTestHelper helper) {
        var level = helper.getLevel();
        // The machine turns its back on the square that seeds it: a frame of 5 x 5, a pit of 3 x 3.
        BlockPos corner = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos east = corner.offset(4, 0, 0);
        BlockPos south = corner.offset(0, 0, 4);
        BlockPos far = corner.offset(4, 0, 4);
        BlockPos pitFrom = corner.offset(1, 0, 1);
        BlockPos pitTo = corner.offset(3, 0, 3);
        BlockPos onFrame = corner.offset(2, 0, 0);
        BlockPos bedrock = pitFrom;
        BlockPos chest = corner.offset(2, 0, 1);
        BlockPos quarryPos = corner.west();
        Supplier<QuarryBlockEntity> quarry = () -> (QuarryBlockEntity) level.getBlockEntity(quarryPos);
        int[] before = {0};
        BlockPos[] refilled = {null};
        helper.startSequence().thenExecute(() -> {
            // A clean volume, so neither the generated world nor an earlier run decides anything.
            fill(level, corner.offset(-3, -3, -3), corner.offset(14, 6, 14), Blocks.AIR);
            // Facing west means its back face is the east one, against the seed marker.
            level.setBlock(quarryPos, ModBlocks.QUARRY.get().defaultBlockState()
                    .setValue(QuarryBlock.FACING, Direction.WEST), Block.UPDATE_ALL);

            // Nothing behind the machine: there is nothing to read.
            quarry.get().readMarkers();
            check(helper, quarry.get().area() == null, "Without a marker behind it there is no box");
            check(helper, quarry.get().status() == QuarryStatus.NO_MARKER_BEHIND, "The screen asks for the marker behind");

            // A marker behind it, but no square around that.
            marker(level, corner);
            quarry.get().readMarkers();
            check(helper, quarry.get().status() == QuarryStatus.NO_SQUARE, "One marker closes no square");

            // Three corners is still not a square: the fourth has to close both sides.
            marker(level, east);
            marker(level, south);
            quarry.get().readMarkers();
            check(helper, quarry.get().status() == QuarryStatus.NO_SQUARE, "Three corners close no square either");

            // A square of two by two encloses nothing.
            clear(level, east, south);
            marker(level, corner.offset(1, 0, 0));
            marker(level, corner.offset(0, 0, 1));
            marker(level, corner.offset(1, 0, 1));
            quarry.get().readMarkers();
            check(helper, quarry.get().status() == QuarryStatus.TOO_SMALL, "A square with no inside is refused");

            // Twelve blocks across is a pit of eleven, past the nine an MK1 digs.
            clear(level, corner.offset(1, 0, 0), corner.offset(0, 0, 1), corner.offset(1, 0, 1));
            marker(level, corner.offset(12, 0, 0));
            marker(level, corner.offset(0, 0, 12));
            marker(level, corner.offset(12, 0, 12));
            quarry.get().readMarkers();
            check(helper, quarry.get().status() == QuarryStatus.TOO_BIG, "An MK1 refuses a pit wider than nine");
            clear(level, corner.offset(12, 0, 0), corner.offset(0, 0, 12), corner.offset(12, 0, 12));

            // The square it will actually dig, and the lines its corners draw to each other.
            marker(level, east);
            marker(level, south);
            marker(level, far);
            check(helper, link(level, corner, Direction.EAST) == 4, "A corner links along its axis");
            check(helper, link(level, corner, Direction.SOUTH) == 4, "And along the other one");
            check(helper, link(level, corner, Direction.WEST) == 0, "A direction with no marker has no line");
            check(helper, link(level, far, Direction.NORTH) == 4, "The far corner links back");
            clear(level, far);
            check(helper, link(level, east, Direction.SOUTH) == 0, "Breaking a corner takes its neighbour's line with it");
            marker(level, far);
            check(helper, link(level, east, Direction.SOUTH) == 4, "Standing it back up brings the line back");

            // Stone inside the square, on the square's own line, and under that line.
            fill(level, pitFrom, pitTo, Blocks.STONE);
            level.setBlock(onFrame, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(onFrame.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            quarry.get().readMarkers();
            ((TickLimitedEnergyHandler) quarry.get().energy()).set(QuarryBlockEntity.CAPACITY);
            var area = quarry.get().area();
            check(helper, area != null, "Four corners close a square");
            check(helper, area.width() == 3 && area.depth() == 3, "A square of five encloses a pit of three");
            check(helper, quarry.get().layer() == corner.getY(), "Digging starts at the highest marker's layer");
            check(helper, quarry.get().status() == QuarryStatus.BUILDING, "The scaffold goes up before anything is dug");
        }).thenIdle(70).thenExecute(() -> {
            // The scaffold: the legs take the markers' own blocks, so the corners close.
            var girder = ModBlocks.QUARRY_FRAME.get();
            check(helper, level.getBlockState(corner).is(girder), "A leg stands where the marker stood");
            check(helper, level.getBlockState(far).is(girder), "And on the far corner");
            check(helper, held(quarry.get(), ModItems.MINING_MARKER.get()) == 4,
                    "The four markers are picked up into the machine's own buffer");
            check(helper, level.getBlockState(ring(corner, corner)).is(girder), "The ring closes over the corner");
            check(helper, level.getBlockState(ring(corner, onFrame)).is(girder), "And along the edge between the corners");
            check(helper, level.getBlockState(onFrame).is(girder), "The bottom ring closes on the markers' own line");
            check(helper, level.getBlockState(onFrame.below()).is(Blocks.STONE), "Nothing under that line is ever dug");
            check(helper, level.getBlockState(bedrock).is(Blocks.BEDROCK), "Bedrock is left standing");
            check(helper, level.getBlockState(chest).is(Blocks.CHEST), "A block entity is left standing");
            check(helper, standing(level, pitFrom, pitTo) < 9, "The quarry dug part of the top layer");
            check(helper, quarry.get().energy().getAmountAsInt() < QuarryBlockEntity.CAPACITY, "Working costs energy");
            // Somebody breaks a girder: the machine puts it back without being asked.
            clear(level, ring(corner, onFrame));
            // And somebody fills a dug spot back in: the arm has to come back for it.
            for (BlockPos pos : BlockPos.betweenClosed(pitFrom, pitTo)) {
                if (level.getBlockState(pos).isAir()) { refilled[0] = pos.immutable(); break; }
            }
            check(helper, refilled[0] != null, "Something in the top layer was dug");
            level.setBlock(refilled[0], Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }).thenIdle(40).thenExecute(() -> {
            check(helper, level.getBlockState(ring(corner, onFrame)).is(ModBlocks.QUARRY_FRAME.get()),
                    "A girder broken out of the scaffold is stood back up");
        }).thenIdle(30).thenExecute(() -> {
            check(helper, level.getBlockState(refilled[0]).isAir(), "A block put back where the box was dug is dug again");
            // With every slot full of something else, the quarry waits instead of voiding what it digs.
            for (int slot = 0; slot < QuarryBlockEntity.INVENTORY_SIZE; slot++) {
                quarry.get().setItem(slot, new ItemStack(Items.DIAMOND, 64));
            }
            before[0] = standing(level, pitFrom.offset(0, -4, 0), pitTo);
        }).thenIdle(40).thenExecute(() -> {
            check(helper, quarry.get().status() == QuarryStatus.FULL, "A full buffer stops the quarry");
            check(helper, standing(level, pitFrom.offset(0, -4, 0), pitTo) == before[0], "A stopped quarry breaks nothing");
            // The scaffold is the machine's, and it goes with it.
            level.destroyBlock(quarryPos, false);
            check(helper, level.getBlockState(ring(corner, corner)).isAir(), "Breaking the quarry takes the ring down");
            check(helper, level.getBlockState(corner).isAir(), "And the legs with it");
        }).thenSucceed();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(ID, (java.util.function.Consumer<GameTestHelper>) QuarryGameTests::marksAndDigs));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(ID);
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                Identifier.fromNamespaceAndPath("futuretech", "quarry_empty"), 300, 0, true);
        event.registerTest(ID, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, ID), data));
    }
}
