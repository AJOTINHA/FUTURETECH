package dev.futuretech.transfer;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.block.entity.TesseractBlockEntity;
import dev.futuretech.block.entity.TesseractBlockEntity.Kind;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * A chest pumped into a sending tesseract; the receiving tesseract feeds a run of three item
 * cables into a second chest. The middle cable is broken while items are flowing and put back
 * a little later. Everything that left the first chest must end up in the second, with nothing
 * left waiting inside a cable: a stuck flight is what the player sees as a capsule that never
 * moves, gathering items.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class TesseractCableReconnectGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "tesseract_cable_reconnect_delivers_everything");
    private static final int ITEMS = 6;

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static ItemCableBlockEntity cable(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, ModBlocks.ITEM_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        return (ItemCableBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static int count(ChestBlockEntity chest) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.COBBLESTONE)) total += chest.getItem(slot).getCount();
        return total;
    }

    private static void reconnect(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos sourceChest = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos sourceCable = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos senderPos = helper.absolutePos(new BlockPos(3, 2, 2));
        BlockPos receiverPos = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos first = helper.absolutePos(new BlockPos(4, 2, 6));
        BlockPos middle = helper.absolutePos(new BlockPos(5, 2, 6));
        BlockPos last = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos targetChest = helper.absolutePos(new BlockPos(7, 2, 6));

        // Cables first: a cable links to what is placed beside it, not to what was there before it.
        var pump = cable(helper, sourceCable);
        var head = cable(helper, first);
        cable(helper, middle);
        var tail = cable(helper, last);

        level.setBlock(sourceChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        var source = (ChestBlockEntity) level.getBlockEntity(sourceChest);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, ITEMS));
        level.setBlock(targetChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        var target = (ChestBlockEntity) level.getBlockEntity(targetChest);

        // The channel has to be in the world's book, or the tesseracts drop it as one that was deleted.
        TesseractChannelBook.of(level.getServer()).create("reconnect");
        level.setBlock(senderPos, ModBlocks.TESSERACT.get().defaultBlockState(), Block.UPDATE_ALL);
        var sender = (TesseractBlockEntity) level.getBlockEntity(senderPos);
        sender.setChannel("reconnect");
        sender.setMode(Kind.ITEMS, SideMode.OUTPUT);
        level.setBlock(receiverPos, ModBlocks.TESSERACT.get().defaultBlockState(), Block.UPDATE_ALL);
        var receiver = (TesseractBlockEntity) level.getBlockEntity(receiverPos);
        receiver.setChannel("reconnect");
        receiver.setMode(Kind.ITEMS, SideMode.INPUT);

        // The chest is pumped by an extract connector; the tesseract is fed by an insert one.
        pump.setConnectorMode(Direction.WEST, SideMode.INPUT);
        pump.setConnectorMode(Direction.EAST, SideMode.OUTPUT);
        // On the far side the player set the tesseract's connector to extract, and the chest's to insert.
        head.setConnectorMode(Direction.WEST, SideMode.INPUT);
        tail.setConnectorMode(Direction.EAST, SideMode.OUTPUT);

        helper.startSequence().thenIdle(45).thenExecute(() -> {
            var pumpNetwork = ((ItemCableBlockEntity) level.getBlockEntity(sourceCable)).network();
            var headNetwork = ((ItemCableBlockEntity) level.getBlockEntity(first)).network();
            check(helper, count(target) + count(source) < ITEMS, "some items are on their way through the cables: source " + count(source)
                    + ", target " + count(target) + ", pump flights " + pumpNetwork.flights().size()
                    + ", head flights " + headNetwork.flights().size());
            level.destroyBlock(middle, false);
        }).thenIdle(60).thenExecute(() -> {
            cable(helper, middle);
        }).thenIdle(400).thenExecute(() -> {
            var network = ((ItemCableBlockEntity) level.getBlockEntity(first)).network();
            int inFlight = network.flights().size();
            int waiting = 0;
            for (var flight : network.flights()) if (flight.waiting) waiting++;
            // What was inside the broken cable fell out of it, the way it does for the player.
            int dropped = 0;
            for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(11, 11, 11))))) {
                if (entity.getItem().is(Items.COBBLESTONE)) dropped += entity.getItem().getCount();
            }
            int pumpFlights = ((ItemCableBlockEntity) level.getBlockEntity(sourceCable)).network().flights().size();
            check(helper, count(target) + dropped == ITEMS, "all " + ITEMS + " reached the far chest or fell out of the broken cable, not "
                    + count(target) + " + " + dropped + " (source " + count(source) + ", in flight " + inFlight + ", waiting " + waiting
                    + ", pump flights " + pumpFlights + ")");
            check(helper, inFlight == 0, "nothing is left inside the cables: " + inFlight + " flights, " + waiting + " waiting");
        }).thenSucceed();
    }

    private static int push(GameTestHelper helper, BlockPos sender) {
        var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, sender, Direction.WEST);
        check(helper, handler != null, "the sending tesseract offers an item slot");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int placed = handler.insert(0, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COBBLESTONE), 1, tx);
            tx.commit();
            return placed;
        }
    }

    /**
     * The receiving tesseract's cable is cut off with the wrench, then joined again with its
     * connector on "none", then opened. Only the open connector takes what the channel brings:
     * a tesseract holding on to the handler it saw first would keep pushing through a cut link.
     */
    private static void cutConnector(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos senderPos = helper.absolutePos(new BlockPos(3, 2, 2));
        BlockPos receiverPos = helper.absolutePos(new BlockPos(3, 2, 6));
        BlockPos first = helper.absolutePos(new BlockPos(4, 2, 6));
        BlockPos last = helper.absolutePos(new BlockPos(5, 2, 6));
        BlockPos targetChest = helper.absolutePos(new BlockPos(6, 2, 6));

        var head = cable(helper, first);
        var tail = cable(helper, last);
        level.setBlock(targetChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        TesseractChannelBook.of(level.getServer()).create("cut");
        level.setBlock(senderPos, ModBlocks.TESSERACT.get().defaultBlockState(), Block.UPDATE_ALL);
        var sender = (TesseractBlockEntity) level.getBlockEntity(senderPos);
        sender.setChannel("cut");
        sender.setMode(Kind.ITEMS, SideMode.OUTPUT);
        level.setBlock(receiverPos, ModBlocks.TESSERACT.get().defaultBlockState(), Block.UPDATE_ALL);
        var receiver = (TesseractBlockEntity) level.getBlockEntity(receiverPos);
        receiver.setChannel("cut");
        receiver.setMode(Kind.ITEMS, SideMode.INPUT);
        head.setConnectorMode(Direction.WEST, SideMode.INPUT);
        tail.setConnectorMode(Direction.EAST, SideMode.OUTPUT);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            check(helper, push(helper, senderPos) == 1, "the open connector takes an item from the channel");
            // The wrench's cut, step for step: the cable remembers it, the arm goes, the network is rebuilt.
            head.setCut(Direction.WEST, true);
            level.setBlock(first, level.getBlockState(first).setValue(net.minecraft.world.level.block.PipeBlock.WEST, false), Block.UPDATE_ALL);
            head.invalidateNetwork();
        }).thenIdle(2).thenExecute(() -> {
            check(helper, push(helper, senderPos) == 0, "a cut connector takes nothing from the channel");
            head.setCut(Direction.WEST, false);
            level.setBlock(first, level.getBlockState(first).setValue(net.minecraft.world.level.block.PipeBlock.WEST, true), Block.UPDATE_ALL);
            head.invalidateNetwork();
            head.setConnectorMode(Direction.WEST, SideMode.NONE);
        }).thenIdle(2).thenExecute(() -> {
            check(helper, push(helper, senderPos) == 0, "a connector on none takes nothing from the channel");
            head.setConnectorMode(Direction.WEST, SideMode.INPUT);
        }).thenIdle(2).thenExecute(() -> {
            check(helper, push(helper, senderPos) == 1, "the connector opened again takes an item from the channel");
        }).thenSucceed();
    }

    private static final Identifier CUT_ID = Identifier.fromNamespaceAndPath("futuretech", "tesseract_cut_connector_takes_nothing");

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(ID, (java.util.function.Consumer<GameTestHelper>) TesseractCableReconnectGameTests::reconnect));
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(CUT_ID, (java.util.function.Consumer<GameTestHelper>) TesseractCableReconnectGameTests::cutConnector));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(ID);
        var data = new net.minecraft.gametest.framework.TestData<>(environment, Identifier.fromNamespaceAndPath("futuretech", "energy_empty"), 600, 0, true);
        event.registerTest(ID, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, ID), data));
        var cut = new net.minecraft.gametest.framework.TestData<>(event.registerEnvironment(CUT_ID), Identifier.fromNamespaceAndPath("futuretech", "energy_empty"), 100, 0, true);
        event.registerTest(CUT_ID, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, CUT_ID), cut));
    }
}
