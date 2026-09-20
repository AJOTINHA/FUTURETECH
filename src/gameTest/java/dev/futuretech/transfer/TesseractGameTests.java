package dev.futuretech.transfer;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.TesseractBlockEntity;
import dev.futuretech.block.entity.TesseractBlockEntity.Kind;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A channel with one sender and two receivers, each receiver with a chest, a battery and a tank
 * beside it. Whatever is pushed into the sender must come out beside the receivers once - no more,
 * whatever room each has - for items, energy and fluids alike.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class TesseractGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "tesseract_delivers_each_item_once");

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static TesseractBlockEntity tesseract(GameTestHelper helper, BlockPos pos, SideMode items) {
        helper.getLevel().setBlock(pos, ModBlocks.TESSERACT.get().defaultBlockState(), Block.UPDATE_ALL);
        var tesseract = (TesseractBlockEntity) helper.getLevel().getBlockEntity(pos);
        tesseract.setChannel("split");
        tesseract.setMode(Kind.ITEMS, items);
        return tesseract;
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static int count(ChestBlockEntity chest) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.COBBLESTONE)) total += chest.getItem(slot).getCount();
        return total;
    }

    private static void splitDelivery(GameTestHelper helper) {
        var level = helper.getLevel();
        var sender = tesseract(helper, helper.absolutePos(new BlockPos(2, 2, 2)), SideMode.OUTPUT);
        var first = tesseract(helper, helper.absolutePos(new BlockPos(6, 2, 2)), SideMode.INPUT);
        var second = tesseract(helper, helper.absolutePos(new BlockPos(6, 2, 6)), SideMode.INPUT);
        var firstChest = chest(helper, first.getBlockPos().above());
        var secondChest = chest(helper, second.getBlockPos().above());
        // The first chest has room for exactly ten more cobblestone.
        for (int slot = 0; slot < firstChest.getContainerSize(); slot++) firstChest.setItem(slot, new net.minecraft.world.item.ItemStack(Items.DIRT, 64));
        firstChest.setItem(0, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE, 54));
        var handler = level.getCapability(Capabilities.Item.BLOCK, sender.getBlockPos(), Direction.NORTH);
        check(helper, handler != null, "The sending tesseract offers an item slot");
        int placed;
        try (var tx = Transaction.openRoot()) {
            placed = handler.insert(0, ItemResource.of(Items.COBBLESTONE), 64, tx);
            tx.commit();
        }
        int delivered = (count(firstChest) - 54) + count(secondChest);
        check(helper, placed == 64, "All 64 were placed: " + placed);
        check(helper, delivered == 64, "The chests received the 64 once between them, not " + delivered
                + " (first " + (count(firstChest) - 54) + ", second " + count(secondChest) + ")");

        // Energy: a battery beside each receiver, the first with room for 10 FE only.
        sender.setMode(Kind.ENERGY, SideMode.OUTPUT); first.setMode(Kind.ENERGY, SideMode.INPUT); second.setMode(Kind.ENERGY, SideMode.INPUT);
        var firstBattery = battery(helper, first.getBlockPos().north());
        var secondBattery = battery(helper, second.getBlockPos().north());
        ((dev.futuretech.energy.TickLimitedEnergyHandler) firstBattery.energy()).set(firstBattery.energy().getCapacityAsInt() - 10);
        var energy = level.getCapability(Capabilities.Energy.BLOCK, sender.getBlockPos(), Direction.NORTH);
        check(helper, energy != null, "The sending tesseract offers energy");
        int fed;
        try (var tx = Transaction.openRoot()) { fed = energy.insert(100, tx); tx.commit(); }
        int stored = (firstBattery.energy().getAmountAsInt() - (firstBattery.energy().getCapacityAsInt() - 10)) + secondBattery.energy().getAmountAsInt();
        check(helper, fed == 100 && stored == 100, "The batteries received the 100 FE once between them: fed " + fed + ", stored " + stored);

        // Fluids: a tank beside each receiver, the first with room for 100 mB only.
        sender.setMode(Kind.FLUIDS, SideMode.OUTPUT); first.setMode(Kind.FLUIDS, SideMode.INPUT); second.setMode(Kind.FLUIDS, SideMode.INPUT);
        var firstTank = tank(helper, first.getBlockPos().south());
        var secondTank = tank(helper, second.getBlockPos().south());
        try (var tx = Transaction.openRoot()) { firstTank.handler(null).insert(FluidResource.of(Fluids.WATER), firstTank.capacity() - 100, tx); tx.commit(); }
        var fluids = level.getCapability(Capabilities.Fluid.BLOCK, sender.getBlockPos(), Direction.NORTH);
        check(helper, fluids != null, "The sending tesseract offers a fluid slot");
        int poured;
        try (var tx = Transaction.openRoot()) { poured = fluids.insert(0, FluidResource.of(Fluids.WATER), 1000, tx); tx.commit(); }
        int held = (firstTank.handler(null).getAmountAsInt(0) - (firstTank.capacity() - 100)) + secondTank.handler(null).getAmountAsInt(0);
        check(helper, poured == 1000 && held == 1000, "The tanks received the 1 000 mB once between them: poured " + poured + ", held " + held);
        helper.succeed();
    }

    private static dev.futuretech.block.entity.BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, ModBlocks.BATTERY_MK1.get().defaultBlockState(), Block.UPDATE_ALL);
        var battery = (dev.futuretech.block.entity.BatteryBlockEntity) helper.getLevel().getBlockEntity(pos);
        battery.sideConfig().set(Direction.SOUTH, SideMode.INPUT); battery.sideConfigChanged();
        return battery;
    }

    private static dev.futuretech.block.entity.FluidTankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, ModBlocks.FLUID_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        var tank = (dev.futuretech.block.entity.FluidTankBlockEntity) helper.getLevel().getBlockEntity(pos);
        tank.sideConfig().set(Direction.NORTH, SideMode.INPUT); tank.sideConfigChanged();
        return tank;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(ID, (java.util.function.Consumer<GameTestHelper>) TesseractGameTests::splitDelivery));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(ID);
        var data = new net.minecraft.gametest.framework.TestData<>(environment, Identifier.fromNamespaceAndPath("futuretech", "energy_empty"), 100, 0, true);
        event.registerTest(ID, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, ID), data));
    }
}
