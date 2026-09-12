package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.ElectricFurnaceBlockEntity.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ElectricFurnaceTest {
    private static ElectricFurnaceBlockEntity furnace() {
        return new ElectricFurnaceBlockEntity(BlockPos.ZERO, ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
    }

    /** Fills the buffer the way a cable would: the handler only accepts INPUT_PER_TICK each tick. */
    private static void charge(ElectricFurnaceBlockEntity furnace, int amount) {
        for (int filled = 0; filled < amount; filled += INPUT_PER_TICK) {
            furnace.beginTick();
            try (var transaction = Transaction.openRoot()) {
                assertEquals(Math.min(INPUT_PER_TICK, amount - filled),
                        furnace.energy().insert(Math.min(INPUT_PER_TICK, amount - filled), transaction));
                transaction.commit();
            }
        }
        furnace.beginTick();
    }

    /**
     * The smelting book of the ephemeral server, which loads datapacks but has no level. Smelting
     * recipes match on the item alone - {@code SingleItemRecipe.matches} ignores its level argument -
     * so the lookup needs no world, which is why the furnace takes a lookup instead of a level.
     */
    private static ElectricFurnaceBlockEntity.SmeltingLookup recipes(MinecraftServer server) {
        return input -> server.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, null).orElse(null);
    }

    /** One tick of work, as {@code serverTick} does without needing a block state in the world. */
    private static boolean tick(ElectricFurnaceBlockEntity furnace, ElectricFurnaceBlockEntity.SmeltingLookup recipes) {
        furnace.beginTick();
        return furnace.smelt(recipes);
    }

    @Test
    void smeltsRawIronAndSpendsExactlyOneTickOfEnergyPerTick(MinecraftServer server) {
        var level = recipes(server);
        var furnace = furnace();
        furnace.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON, 2));
        charge(furnace, CAPACITY);
        int before = furnace.energy().getAmountAsInt();

        // A vanilla smelt takes 200 ticks; the electric furnace halves it.
        for (int tick = 0; tick < SMELT_TICKS; tick++) assertTrue(tick(furnace, level), "tick " + tick);

        assertEquals(new ItemStack(Items.IRON_INGOT).getItem(), furnace.getItem(SLOT_OUTPUT).getItem());
        assertEquals(1, furnace.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, furnace.getItem(SLOT_INPUT).getCount());
        assertEquals(before - SMELT_TICKS * ENERGY_PER_TICK, furnace.energy().getAmountAsInt());
        assertEquals(0, furnace.menuData().get(DATA_PROGRESS));
    }

    @Test
    void anEmptyBufferStopsTheWorkWithoutLosingProgress(MinecraftServer server) {
        var level = recipes(server);
        var furnace = furnace();
        furnace.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        charge(furnace, ENERGY_PER_TICK * 10);
        for (int tick = 0; tick < 10; tick++) assertTrue(tick(furnace, level));
        assertEquals(0, furnace.energy().getAmountAsInt());
        assertEquals(10, furnace.menuData().get(DATA_PROGRESS));

        // Out of energy: no more progress, and the half-done item is still in the slot.
        assertFalse(tick(furnace, level));
        assertEquals(10, furnace.menuData().get(DATA_PROGRESS));
        assertTrue(furnace.getItem(SLOT_OUTPUT).isEmpty());
        assertEquals(1, furnace.getItem(SLOT_INPUT).getCount());
    }

    @Test
    void itemsWithNoSmeltingRecipeAreLeftAlone(MinecraftServer server) {
        var level = recipes(server);
        var furnace = furnace();
        furnace.setItem(SLOT_INPUT, new ItemStack(Items.DIAMOND));
        charge(furnace, CAPACITY);
        assertFalse(tick(furnace, level));
        assertEquals(CAPACITY, furnace.energy().getAmountAsInt(), "no recipe must cost no energy");
        assertEquals(0, furnace.menuData().get(DATA_PROGRESS));
    }

    @Test
    void aFullOutputSlotHaltsSmeltingInsteadOfVoidingTheResult(MinecraftServer server) {
        var level = recipes(server);
        var furnace = furnace();
        furnace.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        furnace.setItem(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT, 64));
        charge(furnace, CAPACITY);
        assertFalse(tick(furnace, level));
        assertEquals(CAPACITY, furnace.energy().getAmountAsInt());
        assertEquals(64, furnace.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, furnace.getItem(SLOT_INPUT).getCount());

        // A different result cannot stack onto the one already there either.
        furnace.setItem(SLOT_OUTPUT, new ItemStack(Items.GOLD_INGOT));
        assertFalse(tick(furnace, level));
        assertEquals(1, furnace.getItem(SLOT_OUTPUT).getCount());
    }

    @Test
    void hopperFacesFollowTheResourceConfigurationInsteadOfBeingFixed(MinecraftServer server) {
        var furnace = furnace();
        var resources = furnace.sideConfig();

        // Machines start closed, so nothing may go in or out of any face until the player opens one.
        for (Direction side : Direction.values()) {
            assertArrayEquals(new int[] {}, furnace.getSlotsForFace(side), side.toString());
            assertFalse(furnace.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), side));
            assertFalse(furnace.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), side));
        }

        resources.set(Direction.UP, SideMode.INPUT);
        assertArrayEquals(new int[] {SLOT_INPUT}, furnace.getSlotsForFace(Direction.UP));
        assertTrue(furnace.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), Direction.UP));
        assertFalse(furnace.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), Direction.UP));

        resources.set(Direction.DOWN, SideMode.OUTPUT);
        assertArrayEquals(new int[] {SLOT_OUTPUT}, furnace.getSlotsForFace(Direction.DOWN));
        assertTrue(furnace.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), Direction.DOWN));
        assertFalse(furnace.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), Direction.DOWN));

        resources.set(Direction.EAST, SideMode.BOTH);
        assertArrayEquals(new int[] {SLOT_INPUT, SLOT_OUTPUT}, furnace.getSlotsForFace(Direction.EAST));
        assertTrue(furnace.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), Direction.EAST));
        assertTrue(furnace.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), Direction.EAST));

        // The result slot is never an entrance and the ingredient slot is never an exit.
        assertFalse(furnace.canPlaceItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT), Direction.EAST));
        assertFalse(furnace.canTakeItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), Direction.EAST));
        // The machine's own access (a null side) is unrestricted.
        assertTrue(furnace.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.RAW_IRON), null));
    }

    @Test
    void faceModesDecideItemsOnlyAndNeverBlockIncomingEnergy(MinecraftServer server) {
        var furnace = furnace();
        var sides = furnace.sideConfig();
        assertFalse(sides.governsEnergy(), "a machine is powered from any side without being configured");
        for (Direction side : Direction.values()) {
            for (SideMode mode : sides.allowed()) {
                sides.set(side, mode);
                assertTrue(sides.allowsEnergyInput(side), side + " " + mode);
            }
            sides.set(side, SideMode.NONE);
        }

        // Its buffer hands nothing back out, whatever a neighbour asks for.
        charge(furnace, 5_000);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, furnace.energy().extract(1_000, transaction));
            transaction.commit();
        }
        assertEquals(5_000, furnace.energy().getAmountAsInt());
    }

    @Test
    void progressAndEnergySurviveAReload(MinecraftServer server) {
        var level = recipes(server);
        var source = furnace();
        source.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        charge(source, CAPACITY);
        for (int tick = 0; tick < 25; tick++) assertTrue(tick(source, level));

        var restored = furnace();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                source.saveWithoutMetadata(server.registryAccess())));
        assertEquals(25, restored.menuData().get(DATA_PROGRESS));
        assertEquals(source.menuData().get(DATA_PROGRESS_TOTAL), restored.menuData().get(DATA_PROGRESS_TOTAL));
        assertEquals(source.energy().getAmountAsInt(), restored.energy().getAmountAsInt());
        assertEquals(1, restored.getItem(SLOT_INPUT).getCount());

        // It picks the work back up exactly where it stopped.
        for (int tick = 25; tick < SMELT_TICKS; tick++) assertTrue(tick(restored, level));
        assertEquals(1, restored.getItem(SLOT_OUTPUT).getCount());
    }
}
