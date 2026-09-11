package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.BatteryBlockEntity.*;

import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.item.BatteryBlockItem;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BatteryTest {
    private static final int CAPACITY = BatteryTier.MK1.capacity();

    private static BatteryBlockEntity battery() {
        return new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
    }

    private static BatteryBlockEntity chargedBattery(int amount) {
        var battery = battery();
        var stack = new ItemStack(ModItems.BATTERY_MK1.get());
        stack.set(ModDataComponents.ENERGY.get(), amount);
        battery.applyComponentsFromItemStack(stack);
        return battery;
    }

    private static int insert(BatteryBlockEntity battery, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = battery.energy().insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int extract(BatteryBlockEntity battery, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int extracted = battery.energy().extract(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    @Test
    void chargeAndDischargeAreEachCappedPerTick(MinecraftServer server) {
        var battery = battery();
        battery.beginTick();
        assertEquals(150, insert(battery, 150));
        assertEquals(50, insert(battery, 1000));
        assertEquals(0, insert(battery, 1));
        assertEquals(200, battery.energy().getAmountAsInt());

        // Output has its own budget in the same tick, and both reset on the next one.
        assertEquals(200, extract(battery, 1000));
        assertEquals(0, extract(battery, 1));
        battery.beginTick();
        assertEquals(200, battery.menuData().get(DATA_INPUT));
        assertEquals(200, battery.menuData().get(DATA_OUTPUT));
        assertEquals(200, insert(battery, 1000));
        assertEquals(200, battery.energy().getAmountAsInt());
    }

    @Test
    void menuReportsPreviousTickTransfersAndSplitsEnergy(MinecraftServer server) {
        var battery = chargedBattery(70_000);
        battery.beginTick();
        insert(battery, 120);
        extract(battery, 45);
        battery.beginTick();
        assertEquals(120, battery.menuData().get(DATA_INPUT));
        assertEquals(45, battery.menuData().get(DATA_OUTPUT));
        assertEquals(70_075, EnergySync.unpack(
                (short) battery.menuData().get(DATA_ENERGY_LOW), (short) battery.menuData().get(DATA_ENERGY_HIGH)));
        battery.beginTick();
        assertEquals(0, battery.menuData().get(DATA_INPUT));
        assertEquals(0, battery.menuData().get(DATA_OUTPUT));
    }

    @Test
    void generatorOutputChargesTheBatteryAtTheGeneratorsRate(MinecraftServer server) {
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        generator.setItem(0, new ItemStack(Items.COAL));
        var battery = battery();
        for (int tick = 0; tick < 100; tick++) {
            generator.beginTick();
            generator.generateEnergy(server.fuelValues());
            battery.beginTick();
            EnergyHandlerUtil.move(generator.energy(), battery.energy(), Integer.MAX_VALUE, null);
        }
        // 20 FE/t generated, drained fully each tick because 80 FE/t output exceeds it.
        assertEquals(2_000, battery.energy().getAmountAsInt());
        assertEquals(0, generator.energy().getAmountAsInt());
    }

    @Test
    void fullBatteryRejectsEnergyAndFeedsAConsumer(MinecraftServer server) {
        var battery = chargedBattery(CAPACITY);
        battery.beginTick();
        assertEquals(0, insert(battery, 100));
        var consumer = new SimpleEnergyHandler(500);
        assertEquals(200, EnergyHandlerUtil.move(battery.energy(), consumer, 500, null));
        assertEquals(CAPACITY - 200, battery.energy().getAmountAsInt());
    }

    @Test
    void savedChargeSurvivesReload(MinecraftServer server) {
        var original = chargedBattery(12_345);
        var restored = battery();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                original.saveWithoutMetadata(server.registryAccess())));
        assertEquals(12_345, restored.energy().getAmountAsInt());
    }

    @Test
    void chargeTravelsWithTheItemAndBackIntoThePlacedBlock(MinecraftServer server) {
        var placed = chargedBattery(65_000);
        var components = placed.collectComponents();
        assertEquals(65_000, components.get(ModDataComponents.ENERGY.get()));

        var dropped = new ItemStack(ModItems.BATTERY_MK1.get());
        dropped.applyComponents(components);
        assertEquals(65_000, BatteryBlockItem.storedEnergy(dropped));
        assertTrue(dropped.getItem().isBarVisible(dropped));
        assertEquals(8, dropped.getItem().getBarWidth(dropped));

        var replaced = battery();
        replaced.applyComponentsFromItemStack(dropped);
        assertEquals(65_000, replaced.energy().getAmountAsInt());

        // An empty battery carries no component, so fresh items stay stackable and bar-less.
        assertFalse(battery().collectComponents().has(ModDataComponents.ENERGY.get()));
        var fresh = new ItemStack(ModItems.BATTERY_MK1.get());
        assertFalse(fresh.getItem().isBarVisible(fresh));
        assertEquals(0, BatteryBlockItem.storedEnergy(fresh));
    }

    @Test
    void overchargedItemsAreClampedToCapacity(MinecraftServer server) {
        var battery = chargedBattery(CAPACITY * 3);
        assertEquals(CAPACITY, battery.energy().getAmountAsInt());
        var stack = new ItemStack(ModItems.BATTERY_MK1.get());
        stack.set(ModDataComponents.ENERGY.get(), CAPACITY * 3);
        assertEquals(CAPACITY, BatteryBlockItem.storedEnergy(stack));
        assertEquals(13, stack.getItem().getBarWidth(stack));
    }

    @Test
    void tierFlowsFromBlockToEntityItemAndMenu(MinecraftServer server) {
        var battery = battery();
        assertEquals(BatteryTier.MK1, battery.tier());
        assertEquals(BatteryTier.MK1, ModItems.BATTERY_MK1.get().tier());
        assertEquals(BatteryTier.MK1, ModBlocks.BATTERY_MK1.get().tier());
        assertEquals(BatteryTier.MK1.ordinal(), battery.menuData().get(DATA_TIER));
        assertEquals(CAPACITY, battery.energy().getCapacityAsInt());
        assertEquals("battery_mk1", ModBlocks.BATTERY_MK1.getId().getPath());
        assertEquals("battery_mk1", ModItems.BATTERY_MK1.getId().getPath());
    }

    @Test
    void outputSideFollowsTheFacingProperty(MinecraftServer server) {
        var state = ModBlocks.BATTERY_MK1.get().defaultBlockState();
        assertEquals(Direction.NORTH, state.getValue(BatteryBlock.FACING));
        assertEquals(Direction.NORTH, battery().outputSide());
        var east = new BatteryBlockEntity(BlockPos.ZERO, state.setValue(BatteryBlock.FACING, Direction.EAST));
        assertEquals(Direction.EAST, east.outputSide());
    }

    @Test
    void batteryRecipeIsLoadedByTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath("futuretech", "battery_mk1"))).isPresent());
    }
}
