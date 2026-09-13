package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.CrusherBlockEntity.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CrusherTest {
    @Test
    void loadsAllCrushingRecipesWithTheirFullOutputCounts(MinecraftServer server) {
        var inputs = new net.minecraft.world.item.Item[] {
                Items.COBBLESTONE, Items.GRAVEL, Items.SANDSTONE, Items.RED_SANDSTONE,
                Items.BONE, Items.BLAZE_ROD, Items.GLASS};
        var outputs = new net.minecraft.world.item.Item[] {
                Items.GRAVEL, Items.SAND, Items.SAND, Items.RED_SAND,
                Items.BONE_MEAL, Items.BLAZE_POWDER, Items.SAND};
        int[] counts = {1, 1, 4, 4, 6, 4, 1};
        for (int i = 0; i < inputs.length; i++) {
            var machine = crusher();
            charge(machine, CRUSH_TICKS * ENERGY_PER_TICK);
            machine.setItem(SLOT_INPUT, new ItemStack(inputs[i]));
            for (int t = 0; t < CRUSH_TICKS; t++) assertTrue(tick(machine, recipes(server)));
            assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
            assertTrue(machine.getItem(SLOT_OUTPUT).is(outputs[i]));
            assertEquals(counts[i], machine.getItem(SLOT_OUTPUT).getCount());
            assertEquals(0, machine.energy().getAmountAsInt());
        }
    }

    @Test
    void replacingInputStartsTheNewRecipeFromZero(MinecraftServer server) {
        var machine = crusher();
        charge(machine, CAPACITY);
        machine.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        for (int t = 0; t < 25; t++) assertTrue(tick(machine, recipes(server)));
        machine.setItem(SLOT_INPUT, new ItemStack(Items.BONE));
        assertTrue(tick(machine, recipes(server)));
        assertEquals(1, machine.menuData().get(DATA_PROGRESS));
        assertTrue(machine.getItem(SLOT_OUTPUT).isEmpty());
    }

    @Test
    void refusesPartialOutputAndResumesWhenTheWholeResultFits(MinecraftServer server) {
        var machine = crusher();
        charge(machine, CAPACITY);
        machine.setItem(SLOT_INPUT, new ItemStack(Items.BONE));
        for (int t = 0; t < 25; t++) assertTrue(tick(machine, recipes(server)));
        machine.setItem(SLOT_OUTPUT, new ItemStack(Items.BONE_MEAL, 59));
        int energyBefore = machine.energy().getAmountAsInt();
        assertFalse(tick(machine, recipes(server)));
        assertEquals(25, machine.menuData().get(DATA_PROGRESS));
        assertEquals(energyBefore, machine.energy().getAmountAsInt());
        machine.setItem(SLOT_OUTPUT, new ItemStack(Items.BONE_MEAL, 58));
        for (int t = 25; t < CRUSH_TICKS; t++) assertTrue(tick(machine, recipes(server)));
        assertEquals(64, machine.getItem(SLOT_OUTPUT).getCount());
        assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
    }

    @Test
    void doesNotUseFurnaceRecipes(MinecraftServer server) {
        var machine = crusher();
        charge(machine, CAPACITY);
        machine.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        assertFalse(tick(machine, recipes(server)));
        assertEquals(CAPACITY, machine.energy().getAmountAsInt());
        assertTrue(machine.getItem(SLOT_OUTPUT).isEmpty());
    }

    private static CrusherBlockEntity crusher() {
        return new CrusherBlockEntity(BlockPos.ZERO, ModBlocks.CRUSHER.get().defaultBlockState());
    }

    /** Fills the buffer the way a cable would: the handler only accepts INPUT_PER_TICK each tick. */
    private static void charge(CrusherBlockEntity crusher, int amount) {
        for (int filled = 0; filled < amount; filled += INPUT_PER_TICK) {
            crusher.beginTick();
            try (var transaction = Transaction.openRoot()) {
                assertEquals(Math.min(INPUT_PER_TICK, amount - filled),
                        crusher.energy().insert(Math.min(INPUT_PER_TICK, amount - filled), transaction));
                transaction.commit();
            }
        }
        crusher.beginTick();
    }

    /**
     * The crushing book of the ephemeral server, which loads datapacks but has no level. Crushing
     * recipes match on the item alone - {@code SingleItemRecipe.matches} ignores its level argument -
     * so the lookup needs no world, which is why the crusher takes a lookup instead of a level.
     */
    private static CrusherBlockEntity.CrushingLookup recipes(MinecraftServer server) {
        return input -> server.getRecipeManager().getRecipeFor(ModRecipes.CRUSHING.get(), input, null).orElse(null);
    }

    /** One tick of work, as {@code serverTick} does without needing a block state in the world. */
    private static boolean tick(CrusherBlockEntity crusher, CrusherBlockEntity.CrushingLookup recipes) {
        crusher.beginTick();
        return crusher.crush(recipes);
    }

    @Test
    void crushsRawIronAndSpendsExactlyOneTickOfEnergyPerTick(MinecraftServer server) {
        var level = recipes(server);
        var crusher = crusher();
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 2));
        charge(crusher, CAPACITY);
        int before = crusher.energy().getAmountAsInt();

        // Each crushing recipe takes 100 ticks.
        for (int tick = 0; tick < CRUSH_TICKS; tick++) assertTrue(tick(crusher, level), "tick " + tick);

        assertEquals(new ItemStack(Items.GRAVEL).getItem(), crusher.getItem(SLOT_OUTPUT).getItem());
        assertEquals(1, crusher.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, crusher.getItem(SLOT_INPUT).getCount());
        assertEquals(before - CRUSH_TICKS * ENERGY_PER_TICK, crusher.energy().getAmountAsInt());
        assertEquals(0, crusher.menuData().get(DATA_PROGRESS));
    }

    @Test
    void anEmptyBufferStopsTheWorkWithoutLosingProgress(MinecraftServer server) {
        var level = recipes(server);
        var crusher = crusher();
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        charge(crusher, ENERGY_PER_TICK * 10);
        for (int tick = 0; tick < 10; tick++) assertTrue(tick(crusher, level));
        assertEquals(0, crusher.energy().getAmountAsInt());
        assertEquals(10, crusher.menuData().get(DATA_PROGRESS));

        // Out of energy: no more progress, and the half-done item is still in the slot.
        assertFalse(tick(crusher, level));
        assertEquals(10, crusher.menuData().get(DATA_PROGRESS));
        assertTrue(crusher.getItem(SLOT_OUTPUT).isEmpty());
        assertEquals(1, crusher.getItem(SLOT_INPUT).getCount());
    }

    @Test
    void itemsWithNoCrushingRecipeAreLeftAlone(MinecraftServer server) {
        var level = recipes(server);
        var crusher = crusher();
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.DIAMOND));
        charge(crusher, CAPACITY);
        assertFalse(tick(crusher, level));
        assertEquals(CAPACITY, crusher.energy().getAmountAsInt(), "no recipe must cost no energy");
        assertEquals(0, crusher.menuData().get(DATA_PROGRESS));
    }

    @Test
    void aFullOutputSlotHaltsCrushingInsteadOfVoidingTheResult(MinecraftServer server) {
        var level = recipes(server);
        var crusher = crusher();
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        crusher.setItem(SLOT_OUTPUT, new ItemStack(Items.GRAVEL, 64));
        charge(crusher, CAPACITY);
        assertFalse(tick(crusher, level));
        assertEquals(CAPACITY, crusher.energy().getAmountAsInt());
        assertEquals(64, crusher.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, crusher.getItem(SLOT_INPUT).getCount());

        // A different result cannot stack onto the one already there either.
        crusher.setItem(SLOT_OUTPUT, new ItemStack(Items.GOLD_INGOT));
        assertFalse(tick(crusher, level));
        assertEquals(1, crusher.getItem(SLOT_OUTPUT).getCount());
    }

    @Test
    void hopperFacesFollowTheResourceConfigurationInsteadOfBeingFixed(MinecraftServer server) {
        var crusher = crusher();
        var resources = crusher.sideConfig();

        // Machines start closed, so nothing may go in or out of any face until the player opens one.
        for (Direction side : Direction.values()) {
            assertArrayEquals(new int[] {}, crusher.getSlotsForFace(side), side.toString());
            assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), side));
            assertFalse(crusher.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.GRAVEL), side));
        }

        resources.set(Direction.UP, SideMode.INPUT);
        assertArrayEquals(new int[] {SLOT_INPUT}, crusher.getSlotsForFace(Direction.UP));
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), Direction.UP));
        assertFalse(crusher.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.GRAVEL), Direction.UP));

        resources.set(Direction.DOWN, SideMode.OUTPUT);
        assertArrayEquals(new int[] {SLOT_OUTPUT}, crusher.getSlotsForFace(Direction.DOWN));
        assertTrue(crusher.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.GRAVEL), Direction.DOWN));
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), Direction.DOWN));

        resources.set(Direction.EAST, SideMode.BOTH);
        assertArrayEquals(new int[] {SLOT_INPUT, SLOT_OUTPUT}, crusher.getSlotsForFace(Direction.EAST));
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), Direction.EAST));
        assertTrue(crusher.canTakeItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.GRAVEL), Direction.EAST));

        // The result slot is never an entrance and the ingredient slot is never an exit.
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_OUTPUT, new ItemStack(Items.GRAVEL), Direction.EAST));
        assertFalse(crusher.canTakeItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), Direction.EAST));
        // The machine's own access (a null side) is unrestricted.
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.COBBLESTONE), null));
    }

    @Test
    void faceModesDecideItemsOnlyAndNeverBlockIncomingEnergy(MinecraftServer server) {
        var crusher = crusher();
        var sides = crusher.sideConfig();
        assertFalse(sides.governsEnergy(), "a machine is powered from any side without being configured");
        for (Direction side : Direction.values()) {
            for (SideMode mode : sides.allowed()) {
                sides.set(side, mode);
                assertTrue(sides.allowsEnergyInput(side), side + " " + mode);
            }
            sides.set(side, SideMode.NONE);
        }

        // Its buffer hands nothing back out, whatever a neighbour asks for.
        charge(crusher, 5_000);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, crusher.energy().extract(1_000, transaction));
            transaction.commit();
        }
        assertEquals(5_000, crusher.energy().getAmountAsInt());
    }

    @Test
    void progressAndEnergySurviveAReload(MinecraftServer server) {
        var level = recipes(server);
        var source = crusher();
        source.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        charge(source, CAPACITY);
        for (int tick = 0; tick < 25; tick++) assertTrue(tick(source, level));

        var restored = crusher();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                source.saveWithoutMetadata(server.registryAccess())));
        assertEquals(25, restored.menuData().get(DATA_PROGRESS));
        assertEquals(source.menuData().get(DATA_PROGRESS_TOTAL), restored.menuData().get(DATA_PROGRESS_TOTAL));
        assertEquals(source.energy().getAmountAsInt(), restored.energy().getAmountAsInt());
        assertEquals(1, restored.getItem(SLOT_INPUT).getCount());

        // It picks the work back up exactly where it stopped.
        for (int tick = 25; tick < CRUSH_TICKS; tick++) assertTrue(tick(restored, level));
        assertEquals(1, restored.getItem(SLOT_OUTPUT).getCount());
    }
}
