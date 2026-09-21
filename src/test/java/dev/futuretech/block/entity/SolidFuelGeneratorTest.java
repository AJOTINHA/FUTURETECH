package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity.*;

import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class SolidFuelGeneratorTest {
    private SolidFuelGeneratorBlockEntity generator() {
        return new SolidFuelGeneratorBlockEntity(BlockPos.ZERO, ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
    }

    /** Runs the generation half of a server tick, as {@code serverTick} does without a level. */
    private static void tick(SolidFuelGeneratorBlockEntity generator, MinecraftServer server) {
        generator.beginTick();
        generator.generateEnergy(server.fuelValues());
    }

    /** A speed upgrade burns faster for less out of each fuel; efficiency is the other way. */
    @Test
    void speedAndEfficiencyUpgradesChangeTheRateAndWhatCoalIsWorth(MinecraftServer server) {
        var generator = generator();
        assertEquals(GENERATION_PER_TICK, generator.generationPerTick());
        assertEquals(FE_PER_BURN_TICK, generator.fePerBurnTick());
        assertEquals(400, generator.generatorTicks(1_600));
        generator.upgrades().setItem(0, new ItemStack(dev.futuretech.registry.ModItems.SPEED_UPGRADE.get()));
        assertEquals(2 * GENERATION_PER_TICK, generator.generationPerTick());
        assertEquals(9, generator.fePerBurnTick());
        // Twice the power out of a coal worth a tenth less: 180 ticks at 80 FE, 14.400 FE in all.
        assertEquals(180, generator.generatorTicks(1_600));
        generator.upgrades().setItem(0, new ItemStack(dev.futuretech.registry.ModItems.EFFICIENCY_UPGRADE.get()));
        assertEquals(GENERATION_PER_TICK, generator.generationPerTick());
        assertEquals(11, generator.fePerBurnTick());
        assertEquals(440, generator.generatorTicks(1_600));
    }

    /** The same coal, with an efficiency upgrade in the slot, runs the generator for longer. */
    @Test
    void anEfficiencyUpgradeGetsMoreOutOfTheSameCoal(MinecraftServer server) {
        var generator = generator();
        generator.upgrades().setItem(0, new ItemStack(dev.futuretech.registry.ModItems.EFFICIENCY_UPGRADE.get()));
        var receiver = new SimpleEnergyHandler(100_000);
        generator.setItem(0, new ItemStack(Items.COAL));
        for (int t = 0; t < 500; t++) {
            tick(generator, server);
            EnergyHandlerUtil.move(generator.energy(), receiver, OUTPUT_PER_TICK, null);
        }
        assertEquals(440 * GENERATION_PER_TICK, receiver.getAmountAsInt());
    }

    @Test
    void coalAndCharcoalProduceExactly16000FEPerItemIn400Ticks(MinecraftServer server) {
        // Thermal Expansion's rate: 10 FE for every furnace tick of the fuel, made at 40 FE/t.
        assertEquals(400, BURN_TICKS);
        for (var item : new Item[] {Items.COAL, Items.CHARCOAL}) {
            var generator = generator();
            var receiver = new SimpleEnergyHandler(100_000);
            generator.setItem(0, new ItemStack(item));
            for (int tick = 0; tick < 400; tick++) {
                tick(generator, server);
                EnergyHandlerUtil.move(generator.energy(), receiver, 80, null);
            }
            assertEquals(16_000, receiver.getAmountAsInt());
            assertEquals(0, generator.menuData().get(DATA_BURN_REMAINING));
            assertTrue(generator.getItem(0).isEmpty());
            tick(generator, server);
            assertEquals(0, generator.energy().getAmountAsInt());
            assertEquals(0, generator.menuData().get(DATA_GENERATING));
        }
    }

    @Test
    void fullBufferPausesAndResumesWithoutConsumingAnotherItem(MinecraftServer server) {
        var generator = generator();
        generator.setItem(0, new ItemStack(Items.COAL, 3));
        // The first coal fills 16.000 in 400 ticks; the second tops the buffer up 100 ticks later.
        for (int tick = 0; tick < 1000; tick++) tick(generator, server);
        assertEquals(20_000, generator.energy().getAmountAsInt());
        assertEquals(300, generator.menuData().get(DATA_BURN_REMAINING));
        for (int tick = 0; tick < 100; tick++) tick(generator, server);
        assertEquals(300, generator.menuData().get(DATA_BURN_REMAINING));
        assertEquals(1, generator.getItem(0).getCount());
        assertEquals(0, generator.menuData().get(DATA_GENERATING));

        // A partial tick's space must not discard the remaining 30 FE.
        try (var transaction = Transaction.openRoot()) {
            assertEquals(30, generator.energy().extract(30, transaction));
            transaction.commit();
        }
        tick(generator, server);
        assertEquals(19_970, generator.energy().getAmountAsInt());
        assertEquals(300, generator.menuData().get(DATA_BURN_REMAINING));
        try (var transaction = Transaction.openRoot()) {
            generator.energy().extract(10, transaction);
            transaction.commit();
        }
        tick(generator, server);
        assertEquals(20_000, generator.energy().getAmountAsInt());
        assertEquals(299, generator.menuData().get(DATA_BURN_REMAINING));
        assertEquals(1, generator.getItem(0).getCount());
    }

    @Test
    void rejectedFuelAndIncomingEnergyCannotPowerTheGenerator(MinecraftServer server) {
        var generator = generator();
        assertFalse(generator.canPlaceItem(0, new ItemStack(Items.DIAMOND)));
        generator.setItem(0, new ItemStack(Items.DIAMOND));
        tick(generator, server);
        assertEquals(0, generator.energy().getAmountAsInt());
        assertEquals(1, generator.getItem(0).getCount());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, generator.energy().insert(1000, transaction));
            transaction.commit();
        }
        assertEquals(0, generator.energy().getAmountAsInt());
    }

    @Test
    void transferSimulationRollsBackAndPartialReceiversConserveEnergy(MinecraftServer server) {
        var generator = generator();
        generator.setItem(0, new ItemStack(Items.COAL));
        for (int tick = 0; tick < 5; tick++) tick(generator, server);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(80, generator.energy().extract(1000, transaction));
        }
        assertEquals(200, generator.energy().getAmountAsInt());
        var receiver = new SimpleEnergyHandler(35);
        assertEquals(35, EnergyHandlerUtil.move(generator.energy(), receiver, 80, null));
        assertEquals(165, generator.energy().getAmountAsInt());
        assertEquals(35, receiver.getAmountAsInt());
        assertEquals(0, EnergyHandlerUtil.move(generator.energy(), receiver, 80, null));
        assertEquals(165, generator.energy().getAmountAsInt());
    }

    @Test
    void saveAndReloadPreserveInventoryEnergyAndBurnTime(MinecraftServer server) {
        var original = generator();
        original.setItem(0, new ItemStack(Items.CHARCOAL, 3));
        for (int tick = 0; tick < 17; tick++) tick(original, server);
        var saved = original.saveWithoutMetadata(server.registryAccess());
        var restored = generator();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved));
        assertEquals(680, restored.energy().getAmountAsInt());
        assertEquals(383, restored.menuData().get(DATA_BURN_REMAINING));
        assertEquals(2, restored.getItem(0).getCount());
        assertTrue(restored.getItem(0).is(Items.CHARCOAL));
        tick(restored, server);
        assertEquals(720, restored.energy().getAmountAsInt());
        assertEquals(382, restored.menuData().get(DATA_BURN_REMAINING));
        assertEquals(2, restored.getItem(0).getCount());
    }

    @Test
    void generatorRecipeIsLoadedByTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath("futuretech", "solid_fuel_generator"))).isPresent());
    }

    private record WoodenFuel(Item item, int ticks) {}

    private static final List<WoodenFuel> WOODEN_FUELS_SAMPLE = List.of(
            new WoodenFuel(Items.OAK_LOG, 300), new WoodenFuel(Items.STRIPPED_BIRCH_LOG, 300),
            new WoodenFuel(Items.MANGROVE_WOOD, 300), new WoodenFuel(Items.PALE_OAK_PLANKS, 300),
            new WoodenFuel(Items.BAMBOO_PLANKS, 300), new WoodenFuel(Items.CRIMSON_STEM, 300),
            new WoodenFuel(Items.WARPED_PLANKS, 300), new WoodenFuel(Items.OAK_SLAB, 150),
            new WoodenFuel(Items.CRIMSON_SLAB, 150), new WoodenFuel(Items.OAK_DOOR, 200),
            new WoodenFuel(Items.WARPED_DOOR, 200), new WoodenFuel(Items.OAK_FENCE, 300),
            new WoodenFuel(Items.OAK_HANGING_SIGN, 800), new WoodenFuel(Items.STICK, 100),
            new WoodenFuel(Items.WOODEN_PICKAXE, 200), new WoodenFuel(Items.CHEST, 300),
            new WoodenFuel(Items.BAMBOO, 50), new WoodenFuel(Items.OAK_BOAT, 1200),
            new WoodenFuel(Items.BARREL, 300), new WoodenFuel(Items.CRAFTING_TABLE, 300));

    @Test
    void woodenFuelsProduceEnergyForTheirOwnBurnDuration(MinecraftServer server) {
        for (var fuel : WOODEN_FUELS_SAMPLE) {
            var stack = new ItemStack(fuel.item());
            String name = stack.toString();
            var generator = generator();
            assertTrue(generator.canPlaceItem(0, stack), name);
            assertEquals(fuel.ticks(), SolidFuelGeneratorBlockEntity.burnDuration(stack, server.fuelValues()), name);
            generator.setItem(0, stack);
            var receiver = new SimpleEnergyHandler(100_000);
            int ticks = generator.generatorTicks(fuel.ticks());
            for (int t = 0; t < ticks; t++) {
                tick(generator, server);
                EnergyHandlerUtil.move(generator.energy(), receiver, 80, null);
            }
            assertEquals(fuel.ticks() / 4, ticks, name);
            assertEquals(ticks * GENERATION_PER_TICK, receiver.getAmountAsInt(), name);
            assertEquals(ticks, generator.menuData().get(DATA_BURN_TOTAL), name);
            assertEquals(0, generator.menuData().get(DATA_BURN_REMAINING), name);
            assertTrue(generator.getItem(0).isEmpty(), name);
            tick(generator, server);
            assertEquals(0, generator.energy().getAmountAsInt(), name);
        }
    }

    @Test
    void everyRegisteredLogAndPlankIsAcceptedIncludingNetherWood(MinecraftServer server) {
        for (var tag : java.util.List.of(net.minecraft.tags.ItemTags.LOGS, net.minecraft.tags.ItemTags.PLANKS)) {
            for (var item : server.registryAccess().lookupOrThrow(Registries.ITEM).getOrThrow(tag)) {
                assertTrue(SolidFuelGeneratorBlockEntity.isFuel(new ItemStack(item)), item.toString());
            }
        }
        for (var rejected : new Item[] {Items.LAVA_BUCKET, Items.IRON_DOOR, Items.BED.pick(DyeColor.RED), Items.TORCH,
                Items.PAINTING, Items.JUKEBOX, Items.BEE_NEST}) {
            assertFalse(SolidFuelGeneratorBlockEntity.isFuel(new ItemStack(rejected)), rejected.toString());
        }
    }

    @Test
    void outputIsCappedPerTickAcrossCallsAndRestoredByAbortedTransactions(MinecraftServer server) {
        var generator = generator();
        generator.setItem(0, new ItemStack(Items.COAL));
        for (int t = 0; t < 10; t++) tick(generator, server);
        assertEquals(400, generator.energy().getAmountAsInt());

        // Several pulls in one tick share the 80 FE budget; a rolled-back pull gives its share back,
        // so the committed 50 below would only be 30 if the aborted pull had kept its share.
        generator.beginTick();
        try (var transaction = Transaction.openRoot()) {
            assertEquals(50, generator.energy().extract(50, transaction));
        }
        try (var transaction = Transaction.openRoot()) {
            assertEquals(50, generator.energy().extract(50, transaction));
            transaction.commit();
        }
        try (var transaction = Transaction.openRoot()) {
            assertEquals(30, generator.energy().extract(1000, transaction));
            transaction.commit();
        }
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, generator.energy().extract(1000, transaction));
        }
        assertEquals(320, generator.energy().getAmountAsInt());

        generator.beginTick();
        try (var transaction = Transaction.openRoot()) {
            assertEquals(80, generator.energy().extract(1000, transaction));
            transaction.commit();
        }
        assertEquals(240, generator.energy().getAmountAsInt());
    }

    @Test
    void menuEnergySurvivesThe16BitDataSlotSync(MinecraftServer server) {
        var generator = generator();
        generator.setItem(0, new ItemStack(Items.COAL));
        for (int t = 0; t < 1000; t++) tick(generator, server);
        var synced = new SimpleContainerData(DATA_COUNT);
        // The vanilla packet writes and reads each slot as a signed short.
        for (int i = 0; i < DATA_COUNT; i++) synced.set(i, (short) generator.menuData().get(i));
        assertEquals(16_000, EnergySync.unpack(synced.get(DATA_ENERGY_LOW), synced.get(DATA_ENERGY_HIGH)));
        for (int amount : new int[] {0, 32_767, 32_768, 65_535, 65_536, 100_000, 1_000_000}) {
            int low = (short) EnergySync.low(amount);
            int high = (short) EnergySync.high(amount);
            assertEquals(amount, EnergySync.unpack(low, high), Integer.toString(amount));
        }
    }

    @Test
    void woodBurnDurationSurvivesReloadAndChangesOnlyWithTheNextFuel(MinecraftServer server) {
        var generator = generator();
        // A slab burns 150 furnace ticks: 1.500 FE, which is 37 ticks of the generator.
        generator.setItem(0, new ItemStack(Items.OAK_SLAB));
        for (int tick = 0; tick < 17; tick++) tick(generator, server);
        generator.setItem(0, new ItemStack(Items.COAL));
        var restored = generator();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                generator.saveWithoutMetadata(server.registryAccess())));
        assertEquals(37, restored.menuData().get(DATA_BURN_TOTAL));
        assertEquals(20, restored.menuData().get(DATA_BURN_REMAINING));
        for (int tick = 0; tick < 20; tick++) tick(restored, server);
        assertEquals(37 * GENERATION_PER_TICK, restored.energy().getAmountAsInt());
        assertEquals(1, restored.getItem(0).getCount());
        tick(restored, server);
        assertEquals(400, restored.menuData().get(DATA_BURN_TOTAL));
        assertEquals(399, restored.menuData().get(DATA_BURN_REMAINING));
        assertTrue(restored.getItem(0).isEmpty());
    }

    @Test
    void oldCoalSavesWithoutBurnTotalRemainCompatible(MinecraftServer server) {
        var generator = generator();
        generator.setItem(0, new ItemStack(Items.COAL));
        tick(generator, server);
        var saved = generator.saveWithoutMetadata(server.registryAccess());
        saved.remove("BurnTotal");
        var restored = generator();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved));
        assertEquals(400, restored.menuData().get(DATA_BURN_TOTAL));
        assertEquals(399, restored.menuData().get(DATA_BURN_REMAINING));
        tick(restored, server);
        assertEquals(2 * GENERATION_PER_TICK, restored.energy().getAmountAsInt());
    }
}
