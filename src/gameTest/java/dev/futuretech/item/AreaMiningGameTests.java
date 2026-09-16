package dev.futuretech.item;

import com.mojang.authlib.GameProfile;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class AreaMiningGameTests {
    private final BlockPos CENTER;
    private AreaMiningGameTests(BlockPos center) { CENTER = center; }

    private FakePlayer prepare(MinecraftServer server, ItemStack stack, Direction.Axis axis) {
        return prepare(server, stack, axis, Blocks.STONE);
    }

    private FakePlayer prepare(MinecraftServer server, ItemStack stack, Direction.Axis axis, net.minecraft.world.level.block.Block filler) {
        var level = server.overworld();
        // Clear a small volume so ray picking is independent of the generated world and prior tests.
        for (BlockPos pos : BlockPos.betweenClosed(CENTER.offset(-3, -3, -3), CENTER.offset(3, 3, 3))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : AreaMining.neighbors(CENTER, axis)) level.setBlockAndUpdate(pos, filler.defaultBlockState());
        level.setBlockAndUpdate(CENTER, filler.defaultBlockState());
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AreaMiningTest"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        switch (axis) {
            case Z -> { player.setPos(CENTER.getX() + .5, CENTER.getY() - 1.1, CENTER.getZ() - 2.5); player.setYRot(0); player.setXRot(0); }
            case X -> { player.setPos(CENTER.getX() - 2.5, CENTER.getY() - 1.1, CENTER.getZ() + .5); player.setYRot(-90); player.setXRot(0); }
            case Y -> { player.setPos(CENTER.getX() + .5, CENTER.getY() + 2, CENTER.getZ() + .5); player.setYRot(0); player.setXRot(90); }
        }
        player.setYHeadRot(player.getYRot());
        return player;
    }

    private void finish(MinecraftServer server) {
        AreaMining.onServerTick(new ServerTickEvent.Post(() -> true, server));
    }

    void minesExactlyOneThreeByThreePlaneInAllOrientationsAndPaysPerBlock(MinecraftServer server) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
            var player = prepare(server, stack, axis);
            BlockPos outside = CENTER.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
            server.overworld().setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
            assertTrue(player.gameMode.destroyBlock(CENTER));
            finish(server);
            assertTrue(server.overworld().isEmptyBlock(CENTER));
            for (BlockPos pos : AreaMining.neighbors(CENTER, axis)) assertTrue(server.overworld().isEmptyBlock(pos), axis + " " + pos);
            assertEquals(Blocks.STONE.defaultBlockState(), server.overworld().getBlockState(outside));
            assertEquals(9, stack.getDamageValue());
            finish(server); // Secondary breaks must not queue more 3x3 jobs.
            assertEquals(9, stack.getDamageValue());
        }
    }

    void sneakingMinesOnlyTheCenter(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        player.setShiftKeyDown(true);
        player.gameMode.destroyBlock(CENTER);
        finish(server);
        assertEquals(1, stack.getDamageValue());
        for (var pos : AreaMining.neighbors(CENTER, Direction.Axis.Z)) assertFalse(server.overworld().isEmptyBlock(pos));
    }

    void creativeAlsoMinesTheAreaWithoutWear(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.gameMode.destroyBlock(CENTER);
        finish(server);
        for (var pos : AreaMining.neighbors(CENTER, Direction.Axis.Z)) assertTrue(server.overworld().isEmptyBlock(pos));
        assertEquals(0, stack.getDamageValue());
    }

    void stopsWhenToolBreaks(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        stack.setDamageValue(stack.getMaxDamage() - 2);
        var player = prepare(server, stack, Direction.Axis.Z);
        player.gameMode.destroyBlock(CENTER);
        finish(server);
        assertTrue(stack.isEmpty());
        assertEquals(1, AreaMining.neighbors(CENTER, Direction.Axis.Z).stream().filter(server.overworld()::isEmptyBlock).count());
    }

    void rejectsWrongMaterialsUnbreakableAndMuchHarderNeighbors(MinecraftServer server) {
        var stack = ModItems.WOODEN_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        var blocked = List.of(Blocks.DIAMOND_ORE, Blocks.BEDROCK, Blocks.DIRT, Blocks.OAK_LOG, Blocks.OBSIDIAN);
        var positions = AreaMining.neighbors(CENTER, Direction.Axis.Z);
        for (int i = 0; i < blocked.size(); i++) server.overworld().setBlockAndUpdate(positions.get(i), blocked.get(i).defaultBlockState());
        player.gameMode.destroyBlock(CENTER);
        finish(server);
        for (int i = 0; i < blocked.size(); i++) assertEquals(blocked.get(i).defaultBlockState(), server.overworld().getBlockState(positions.get(i)));
        assertEquals(4, stack.getDamageValue());
        var diamond = ModItems.DIAMOND_HAMMER.get().getDefaultInstance();
        assertFalse(AreaMining.canMine(diamond, Blocks.OBSIDIAN.defaultBlockState(), server.overworld(), CENTER, 4.5F));
        assertTrue(AreaMining.canMine(diamond, Blocks.OBSIDIAN.defaultBlockState(), server.overworld(), CENTER, 150));
    }

    void canceledCenterNeverBreaksNeighborsEvenWhenCanceledAfterOurListener(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        Consumer<BreakBlockEvent> cancel = event -> { if (event.getPlayer() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
        try {
            assertFalse(player.gameMode.destroyBlock(CENTER));
            finish(server);
            assertEquals(0, stack.getDamageValue());
            for (var pos : AreaMining.neighbors(CENTER, Direction.Axis.Z)) assertFalse(server.overworld().isEmptyBlock(pos));
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
    }

    void neighborProtectionIsRespected(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        var protectedPos = CENTER.above();
        Consumer<BreakBlockEvent> cancel = event -> { if (event.getPos().equals(protectedPos)) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            player.gameMode.destroyBlock(CENTER);
            finish(server);
            assertFalse(server.overworld().isEmptyBlock(protectedPos));
            assertEquals(8, stack.getDamageValue());
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
    }

    void replacingTheToolBeforeExpansionDoesNotMineWithAnEmptyHand(MinecraftServer server) {
        var stack = ModItems.IRON_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        player.gameMode.destroyBlock(CENTER);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        finish(server);
        for (var pos : AreaMining.neighbors(CENTER, Direction.Axis.Z)) assertFalse(server.overworld().isEmptyBlock(pos));
        assertEquals(1, stack.getDamageValue());
    }

    void excavatorMinesShovelBlocksOnlyAndLeavesStoneAlone(MinecraftServer server) {
        var stack = ModItems.IRON_EXCAVATOR.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Y, Blocks.DIRT);
        var level = server.overworld();
        var positions = AreaMining.neighbors(CENTER, Direction.Axis.Y);
        level.setBlockAndUpdate(positions.get(0), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(positions.get(1), Blocks.GRAVEL.defaultBlockState());
        assertEquals(7, AreaMining.additionalBlocks(level, stack, CENTER, Direction.UP).size());
        assertTrue(player.gameMode.destroyBlock(CENTER));
        finish(server);
        assertEquals(Blocks.STONE.defaultBlockState(), level.getBlockState(positions.get(0)));
        for (int i = 1; i < positions.size(); i++) assertTrue(level.isEmptyBlock(positions.get(i)), positions.get(i).toString());
        assertEquals(8, stack.getDamageValue());
        // A hammer never expands into dirt, and an excavator never expands into stone.
        var hammer = ModItems.IRON_HAMMER.get().getDefaultInstance();
        assertFalse(AreaMining.canMine(hammer, Blocks.DIRT.defaultBlockState(), level, CENTER, 150));
        assertFalse(AreaMining.canMine(stack, Blocks.STONE.defaultBlockState(), level, CENTER, 150));
        assertTrue(AreaMining.canMine(stack, Blocks.SAND.defaultBlockState(), level, CENTER, 150));
    }

    void lumberAxeFellsTheConnectedTreeOfOneKindAndNothingElse(MinecraftServer server) {
        var stack = ModItems.IRON_LUMBER_AXE.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z, Blocks.AIR);
        var level = server.overworld();
        // Trunk of four, a diagonal branch, a birch touching the oak, a stripped oak log and oak planks.
        var oak = Blocks.OAK_LOG.defaultBlockState();
        var trunk = List.of(CENTER, CENTER.above(), CENTER.above(2), CENTER.above(3));
        var branch = List.of(CENTER.above(3).offset(1, 1, 1), CENTER.above(3).offset(2, 2, 1));
        for (var pos : trunk) level.setBlockAndUpdate(pos, oak);
        for (var pos : branch) level.setBlockAndUpdate(pos, oak);
        var birch = CENTER.above(2).east();
        var stripped = CENTER.above(1).west();
        var planks = CENTER.north();
        level.setBlockAndUpdate(birch, Blocks.BIRCH_LOG.defaultBlockState());
        level.setBlockAndUpdate(stripped, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
        var selection = AreaMining.additionalBlocks(level, stack, CENTER, Direction.NORTH);
        assertEquals(5, selection.size());
        assertTrue(player.gameMode.destroyBlock(CENTER));
        finish(server);
        for (var pos : trunk) assertTrue(level.isEmptyBlock(pos), pos.toString());
        for (var pos : branch) assertTrue(level.isEmptyBlock(pos), pos.toString());
        assertEquals(Blocks.BIRCH_LOG.defaultBlockState(), level.getBlockState(birch));
        assertEquals(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), level.getBlockState(stripped));
        assertEquals(Blocks.OAK_PLANKS.defaultBlockState(), level.getBlockState(planks));
        assertEquals(6, stack.getDamageValue());
        // Planks are axe blocks but not logs: breaking them never starts a felling.
        assertTrue(AreaMining.additionalBlocks(level, stack, planks, Direction.NORTH).isEmpty());
        level.setBlockAndUpdate(planks, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(birch, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stripped, Blocks.AIR.defaultBlockState());
    }

    void lumberAxeStopsAtTheTreeLimit(MinecraftServer server) {
        var stack = ModItems.DIAMOND_LUMBER_AXE.get().getDefaultInstance();
        prepare(server, stack, Direction.Axis.Z, Blocks.AIR);
        var level = server.overworld();
        var logs = new java.util.ArrayList<BlockPos>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            var pos = CENTER.offset(x, 0, z);
            level.setBlockAndUpdate(pos, Blocks.SPRUCE_LOG.defaultBlockState());
            logs.add(pos);
        }
        assertEquals(AreaMining.TREE_LIMIT, AreaMining.additionalBlocks(level, stack, CENTER, Direction.UP).size());
        for (var pos : logs) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    void rightClickStripsLogsAndFlattensGrassLikeTheVanillaTools(MinecraftServer server) {
        var level = server.overworld();
        var axe = ModItems.IRON_LUMBER_AXE.get().getDefaultInstance();
        var player = prepare(server, axe, Direction.Axis.Z, Blocks.AIR);
        level.setBlockAndUpdate(CENTER, Blocks.OAK_LOG.defaultBlockState());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(CENTER), Direction.UP, CENTER, false);
        assertTrue(axe.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction());
        assertEquals(Blocks.STRIPPED_OAK_LOG, level.getBlockState(CENTER).getBlock());
        assertEquals(1, axe.getDamageValue());

        var excavator = ModItems.IRON_EXCAVATOR.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, excavator);
        level.setBlockAndUpdate(CENTER, Blocks.GRASS_BLOCK.defaultBlockState());
        assertTrue(excavator.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction());
        assertEquals(Blocks.DIRT_PATH, level.getBlockState(CENTER).getBlock());
        assertEquals(1, excavator.getDamageValue());

        // A hammer has no right-click use, and a lumber axe does not make paths.
        var hammer = ModItems.IRON_HAMMER.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        level.setBlockAndUpdate(CENTER, Blocks.GRASS_BLOCK.defaultBlockState());
        assertFalse(hammer.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction());
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        assertFalse(axe.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction());
        assertEquals(Blocks.GRASS_BLOCK, level.getBlockState(CENTER).getBlock());
        level.setBlockAndUpdate(CENTER, Blocks.AIR.defaultBlockState());
    }

    void previewSelectsOnlyTheBlocksThatTheHammerActuallyMines(MinecraftServer server) {
        var stack = ModItems.WOODEN_HAMMER.get().getDefaultInstance();
        var player = prepare(server, stack, Direction.Axis.Z);
        var level = server.overworld();
        level.setBlockAndUpdate(CENTER.above(), Blocks.DIAMOND_ORE.defaultBlockState());
        level.setBlockAndUpdate(CENTER.below(), Blocks.DIRT.defaultBlockState());
        var selection = AreaMining.additionalBlocks(level, stack, CENTER, Direction.NORTH);
        assertEquals(6, selection.size());
        assertFalse(selection.contains(CENTER));
        assertFalse(selection.contains(CENTER.above()));
        assertFalse(selection.contains(CENTER.below()));
        player.gameMode.destroyBlock(CENTER);
        finish(server);
        for (var pos : AreaMining.neighbors(CENTER, Direction.Axis.Z)) {
            assertEquals(selection.contains(pos), level.isEmptyBlock(pos));
        }
    }

    private static void assertTrue(boolean value) { assertTrue(value, "Expected true"); }
    private static void assertTrue(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void assertFalse(boolean value) { assertTrue(!value, "Expected false"); }
    private static void assertEquals(long expected, long actual) { assertTrue(expected == actual, expected + " != " + actual); }
    private static void assertEquals(Object expected, Object actual) { assertTrue(java.util.Objects.equals(expected, actual), expected + " != " + actual); }

    private static final java.util.Map<String, java.util.function.BiConsumer<AreaMiningGameTests, MinecraftServer>> CASES = java.util.Map.ofEntries(
            java.util.Map.entry("planes", AreaMiningGameTests::minesExactlyOneThreeByThreePlaneInAllOrientationsAndPaysPerBlock),
            java.util.Map.entry("sneaking", AreaMiningGameTests::sneakingMinesOnlyTheCenter),
            java.util.Map.entry("creative", AreaMiningGameTests::creativeAlsoMinesTheAreaWithoutWear),
            java.util.Map.entry("durability", AreaMiningGameTests::stopsWhenToolBreaks),
            java.util.Map.entry("materials", AreaMiningGameTests::rejectsWrongMaterialsUnbreakableAndMuchHarderNeighbors),
            java.util.Map.entry("canceled_center", AreaMiningGameTests::canceledCenterNeverBreaksNeighborsEvenWhenCanceledAfterOurListener),
            java.util.Map.entry("protected_neighbor", AreaMiningGameTests::neighborProtectionIsRespected),
            java.util.Map.entry("preview_selection", AreaMiningGameTests::previewSelectsOnlyTheBlocksThatTheHammerActuallyMines),
            java.util.Map.entry("excavator", AreaMiningGameTests::excavatorMinesShovelBlocksOnlyAndLeavesStoneAlone),
            java.util.Map.entry("lumber_axe", AreaMiningGameTests::lumberAxeFellsTheConnectedTreeOfOneKindAndNothingElse),
            java.util.Map.entry("tree_limit", AreaMiningGameTests::lumberAxeStopsAtTheTreeLimit),
            java.util.Map.entry("right_click", AreaMiningGameTests::rightClickStripsLogsAndFlattensGrassLikeTheVanillaTools),
            java.util.Map.entry("swapped_tool", AreaMiningGameTests::replacingTheToolBeforeExpansionDoesNotMineWithAnEmptyHand));

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "area_mining_" + name);
    }

    /**
     * Each case is a vanilla test function, so the test instances are plain {@code FunctionGameTestInstance}s.
     * The test registry is synced to clients, and an anonymous instance borrowing the function codec breaks
     * joining a world from the development client.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> CASES.forEach((name, test) ->
                registry.register(id(name), (java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper>) helper -> {
                    test.accept(new AreaMiningGameTests(helper.absolutePos(new BlockPos(5, 5, 5))), helper.getLevel().getServer());
                    helper.succeed();
                })));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "area_mining"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "area_mining_empty"), 200, 0, true);
        for (String name : CASES.keySet()) {
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
