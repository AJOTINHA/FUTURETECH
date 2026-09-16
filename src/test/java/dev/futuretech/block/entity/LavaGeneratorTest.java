package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.LavaGeneratorBlockEntity.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SidedFluids;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class LavaGeneratorTest {
    private static FluidResource lava() { return FluidResource.of(Fluids.LAVA); }

    private static LavaGeneratorBlockEntity generator() {
        return new LavaGeneratorBlockEntity(BlockPos.ZERO, ModBlocks.LAVA_GENERATOR.get().defaultBlockState());
    }

    /** Runs the bucket and generation half of a server tick, as {@code serverTick} does without a level. */
    private static void tick(LavaGeneratorBlockEntity generator) {
        generator.beginTick();
        generator.drainContainer();
        generator.generateEnergy();
    }

    private static int insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(fluid, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static void extract(ResourceHandler<FluidResource> handler, int amount) {
        try (var tx = Transaction.openRoot()) {
            assertEquals(amount, handler.extract(lava(), amount, tx));
            tx.commit();
        }
    }

    @Test
    void oneBucketOfLavaProducesExactly50000FEAndLeavesTheEmptyBucket(MinecraftServer server) {
        var generator = generator();
        var receiver = new SimpleEnergyHandler(100_000);
        generator.setItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET));
        tick(generator);
        assertTrue(generator.getItem(SLOT_INPUT).isEmpty());
        assertTrue(generator.getItem(SLOT_OUTPUT).is(Items.BUCKET));
        assertEquals(1_000 - LAVA_PER_TICK, generator.lavaAmount());
        EnergyHandlerUtil.move(generator.energy(), receiver, OUTPUT_PER_TICK, null);
        for (int t = 1; t < 1_000; t++) {
            tick(generator);
            EnergyHandlerUtil.move(generator.energy(), receiver, OUTPUT_PER_TICK, null);
        }
        assertEquals(50_000, receiver.getAmountAsInt());
        assertEquals(0, generator.lavaAmount());
        tick(generator);
        assertEquals(0, generator.energy().getAmountAsInt());
        assertEquals(0, generator.menuData().get(DATA_GENERATING));
    }

    @Test
    void fullBufferPausesWithoutBurningLava() {
        var generator = generator();
        assertEquals(3_000, insert(generator.lava(), lava(), 3_000));
        for (int t = 0; t < 1_000; t++) tick(generator);
        assertEquals(CAPACITY, generator.energy().getAmountAsInt());
        assertEquals(3_000 - 800, generator.lavaAmount());
        assertEquals(0, generator.menuData().get(DATA_GENERATING));
        // A partial tick's space must not burn lava for energy that does not fit.
        try (var transaction = Transaction.openRoot()) {
            assertEquals(10, generator.energy().extract(10, transaction));
            transaction.commit();
        }
        tick(generator);
        assertEquals(CAPACITY - 10, generator.energy().getAmountAsInt());
        assertEquals(2_200, generator.lavaAmount());
        try (var transaction = Transaction.openRoot()) {
            generator.energy().extract(40, transaction);
            transaction.commit();
        }
        tick(generator);
        assertEquals(CAPACITY, generator.energy().getAmountAsInt());
        assertEquals(2_199, generator.lavaAmount());
    }

    @Test
    void onlyLavaGoesInAndNothingComesBackOut() {
        var generator = generator();
        assertEquals(0, insert(generator.lava(), FluidResource.of(Fluids.WATER), 1_000));
        assertFalse(generator.canPlaceItem(SLOT_INPUT, new ItemStack(Items.WATER_BUCKET)));
        assertFalse(generator.canPlaceItem(SLOT_INPUT, new ItemStack(Items.BUCKET)));
        assertFalse(generator.canPlaceItem(SLOT_OUTPUT, new ItemStack(Items.LAVA_BUCKET)));
        assertTrue(generator.canPlaceItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET)));
        generator.setItem(SLOT_INPUT, new ItemStack(Items.WATER_BUCKET));
        tick(generator);
        assertEquals(0, generator.lavaAmount());
        assertTrue(generator.getItem(SLOT_INPUT).is(Items.WATER_BUCKET));
        assertEquals(0, generator.energy().getAmountAsInt());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, generator.energy().insert(1_000, transaction));
            transaction.commit();
        }

        // Cables see lava go in through an input face only, and never come out anywhere.
        var sides = generator.sideConfig();
        sides.set(Direction.NORTH, SideMode.INPUT);
        sides.set(Direction.SOUTH, SideMode.OUTPUT);
        var north = SidedFluids.intake(generator.lava(), sides, Direction.NORTH);
        var south = SidedFluids.intake(generator.lava(), sides, Direction.SOUTH);
        assertNotNull(north);
        assertNotNull(south);
        assertNull(SidedFluids.intake(generator.lava(), sides, Direction.EAST));
        assertEquals(0, insert(south, lava(), 500));
        assertEquals(500, insert(north, lava(), 500));
        assertEquals(TANK_CAPACITY - 500, insert(north, lava(), Integer.MAX_VALUE));
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, north.extract(lava(), 100, tx));
        }
        assertEquals(TANK_CAPACITY, generator.lavaAmount());
        // The face closed later is honoured by a handler handed out earlier.
        sides.set(Direction.NORTH, SideMode.NONE);
        extract(generator.lava(), 1_000);
        assertEquals(0, insert(north, lava(), 100));
    }

    @Test
    void bucketWaitsForRoomInTheTankAndForAFreeOutputSlot() {
        var generator = generator();
        assertEquals(7_500, insert(generator.lava(), lava(), 7_500));
        generator.setItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET));
        tick(generator);
        // 500 mB of room is not a whole bucket: nothing moves and nothing is lost.
        assertTrue(generator.getItem(SLOT_INPUT).is(Items.LAVA_BUCKET));
        assertTrue(generator.getItem(SLOT_OUTPUT).isEmpty());
        assertEquals(7_499, generator.lavaAmount());
        for (int t = 0; t < 499; t++) tick(generator);
        assertEquals(7_000, generator.lavaAmount());
        assertTrue(generator.getItem(SLOT_INPUT).is(Items.LAVA_BUCKET));
        tick(generator);
        assertEquals(7_999, generator.lavaAmount());
        assertTrue(generator.getItem(SLOT_INPUT).isEmpty());
        assertEquals(1, generator.getItem(SLOT_OUTPUT).getCount());

        // A full output slot holds the next bucket back too, even with room for it in the tank.
        // The room is made directly: the buffer fills long before burning would open a bucket's worth.
        extract(generator.lava(), 1_000);
        generator.setItem(SLOT_OUTPUT, new ItemStack(Items.BUCKET, 16));
        generator.setItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET));
        for (int t = 0; t < 20; t++) tick(generator);
        assertTrue(generator.getItem(SLOT_INPUT).is(Items.LAVA_BUCKET));
        assertEquals(16, generator.getItem(SLOT_OUTPUT).getCount());
        generator.setItem(SLOT_OUTPUT, new ItemStack(Items.BUCKET, 15));
        tick(generator);
        assertTrue(generator.getItem(SLOT_INPUT).isEmpty());
        assertEquals(16, generator.getItem(SLOT_OUTPUT).getCount());
    }

    @Test
    void facesSteerBucketsInAndEmptyBucketsOut() {
        var generator = generator();
        var sides = generator.sideConfig();
        sides.set(Direction.UP, SideMode.INPUT);
        sides.set(Direction.DOWN, SideMode.OUTPUT);
        sides.set(Direction.EAST, SideMode.BOTH);
        assertArrayEquals(new int[] {SLOT_INPUT}, generator.getSlotsForFace(Direction.UP));
        assertArrayEquals(new int[] {SLOT_OUTPUT}, generator.getSlotsForFace(Direction.DOWN));
        assertArrayEquals(new int[] {SLOT_INPUT, SLOT_OUTPUT}, generator.getSlotsForFace(Direction.EAST));
        assertArrayEquals(new int[] {}, generator.getSlotsForFace(Direction.WEST));
        assertTrue(generator.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET), Direction.UP));
        assertFalse(generator.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET), Direction.DOWN));
        assertFalse(generator.canPlaceItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.LAVA_BUCKET), Direction.UP));
        assertTrue(generator.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.BUCKET), Direction.DOWN));
        assertFalse(generator.canTakeItemThroughFace(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET), Direction.DOWN));
        assertFalse(generator.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.BUCKET), Direction.UP));
    }

    @Test
    void saveAndReloadPreserveLavaEnergyAndBuckets(MinecraftServer server) {
        var original = generator();
        original.setItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET));
        for (int t = 0; t < 17; t++) tick(original);
        original.setItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET));
        var saved = original.saveWithoutMetadata(server.registryAccess());
        var restored = generator();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved));
        assertEquals(850, restored.energy().getAmountAsInt());
        assertEquals(983, restored.lavaAmount());
        assertTrue(restored.getItem(SLOT_INPUT).is(Items.LAVA_BUCKET));
        assertTrue(restored.getItem(SLOT_OUTPUT).is(Items.BUCKET));
        tick(restored);
        assertEquals(900, restored.energy().getAmountAsInt());
        assertEquals(1_982, restored.lavaAmount());
        assertEquals(2, restored.getItem(SLOT_OUTPUT).getCount());
    }

    @Test
    void menuEnergyAndLavaSurviveThe16BitDataSlotSync() {
        var generator = generator();
        assertEquals(TANK_CAPACITY, insert(generator.lava(), lava(), TANK_CAPACITY));
        for (int t = 0; t < 1_000; t++) tick(generator);
        var synced = new SimpleContainerData(DATA_COUNT);
        // The vanilla packet writes and reads each slot as a signed short.
        for (int i = 0; i < DATA_COUNT; i++) synced.set(i, (short) generator.menuData().get(i));
        assertEquals(CAPACITY, EnergySync.unpack(synced.get(DATA_ENERGY_LOW), synced.get(DATA_ENERGY_HIGH)));
        assertEquals(TANK_CAPACITY - 800, EnergySync.unpack(synced.get(DATA_LAVA_LOW), synced.get(DATA_LAVA_HIGH)));
    }

    @Test
    void outputIsCappedPerTickAcrossCalls() {
        var generator = generator();
        assertEquals(1_000, insert(generator.lava(), lava(), 1_000));
        for (int t = 0; t < 20; t++) tick(generator);
        assertEquals(1_000, generator.energy().getAmountAsInt());
        generator.beginTick();
        try (var transaction = Transaction.openRoot()) {
            assertEquals(150, generator.energy().extract(150, transaction));
            transaction.commit();
        }
        try (var transaction = Transaction.openRoot()) {
            assertEquals(50, generator.energy().extract(1_000, transaction));
            transaction.commit();
        }
        assertEquals(800, generator.energy().getAmountAsInt());
    }

    @Test
    void generatorRecipeIsLoadedByTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath("futuretech", "lava_generator"))).isPresent());
    }
}
