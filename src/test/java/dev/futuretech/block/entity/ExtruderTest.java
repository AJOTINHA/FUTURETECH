package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.ExtruderBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.recipe.ExtrudingRecipe;
import dev.futuretech.recipe.ExtrudingRecipes;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import dev.futuretech.registry.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

/**
 * Every case takes the running server, used or not: fluids and items read components that only a
 * bound registry has, and a test that skips the parameter dies on the first {@code FluidResource}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ExtruderTest {
    private static ExtruderBlockEntity extruder(int mk) {
        return new ExtruderBlockEntity(BlockPos.ZERO, ModBlocks.EXTRUDER.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    /** The mod's own extrusion recipes, in the order the machine and its screen both count them. */
    private static List<RecipeHolder<ExtrudingRecipe>> shipped(MinecraftServer server) {
        return ExtrudingRecipes.of(server.getRecipeManager());
    }

    private static void charge(ExtruderBlockEntity extruder, int amount) {
        while (amount > 0) {
            extruder.beginTick();
            try (var tx = Transaction.openRoot()) {
                int inserted = extruder.energy().insert(amount, tx);
                assertTrue(inserted > 0);
                amount -= inserted;
                tx.commit();
            }
        }
    }

    private static int pour(ResourceHandler<FluidResource> tanks, Fluid fluid, int amount) {
        try (var tx = Transaction.openRoot()) {
            int inserted = tanks.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static ExtrudingRecipe.Part part(Fluid fluid, int consumes) {
        return new ExtrudingRecipe.Part(fluid.builtInRegistryHolder(), consumes);
    }

    /** A recipe of our own, so a test does not depend on which products the data pack ships. */
    private static RecipeHolder<ExtrudingRecipe> recipe(String name, Fluid first, int firstDrinks,
                                                       Fluid second, int secondDrinks) {
        var extruding = new ExtrudingRecipe(part(first, firstDrinks), part(second, secondDrinks),
                new ItemStackTemplate(Items.SPONGE, 1), 20);
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("futuretech", name)), extruding);
    }

    private static int amount(ExtruderBlockEntity extruder, int tank) { return extruder.contents(tank).getAmount(); }

    /** Where a product sits in the cycle, which is also the index the screen is sent. */
    private static int indexOf(List<RecipeHolder<ExtrudingRecipe>> recipes, Item item) {
        for (int index = 0; index < recipes.size(); index++) {
            if (recipes.get(index).value().result().create().is(item)) return index;
        }
        throw new AssertionError("no extrusion recipe makes " + item);
    }

    /** Steps the button until the machine is set to that product, the way a player would. */
    private static void select(ExtruderBlockEntity extruder, List<RecipeHolder<ExtrudingRecipe>> recipes, Item item) {
        int target = indexOf(recipes, item);
        for (int step = 0; step <= recipes.size() && extruder.choiceIndex() != target; step++) {
            extruder.cycleChoice(recipes, false);
        }
        assertEquals(target, extruder.choiceIndex());
    }

    /** Fills both tanks and the buffer, which is the state every batch starts from. */
    private static ExtruderBlockEntity ready(int water, int lava) {
        var extruder = extruder(1);
        charge(extruder, CAPACITY);
        pour(extruder.tanks(), Fluids.WATER, water);
        pour(extruder.tanks(), Fluids.LAVA, lava);
        return extruder;
    }

    @Test
    void theDefaultProductIsCobblestoneAndItCostsNoFluidAtAll(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = ready(1_000, 1_000);
        // Nothing picked yet, so it makes the first product: cobblestone, which drinks neither feed.
        var first = recipes.getFirst().value();
        assertEquals(0, first.first().consumes());
        assertEquals(0, first.second().consumes());
        for (int tick = 0; tick < first.duration(); tick++) assertTrue(extruder.extrude(recipes), "tick " + tick);
        assertTrue(extruder.getItem(SLOT_OUTPUT).is(Items.COBBLESTONE));
        assertEquals(0, extruder.choiceIndex());
        assertEquals(1_000, amount(extruder, TANK_A));
        assertEquals(1_000, amount(extruder, TANK_B));
        // Energy is the whole cost, and it is paid tick by tick rather than in one lump at the end.
        assertEquals(CAPACITY - first.duration() * ENERGY_PER_TICK, extruder.energy().getAmountAsInt());
    }

    @Test
    void cobblestoneCostsNothingButStillNeedsBothFluidsThere(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = extruder(1);
        charge(extruder, CAPACITY);
        pour(extruder.tanks(), Fluids.WATER, 1_000);
        // Water alone is not an extruder; the machine wants both feeds before it makes anything.
        assertFalse(extruder.extrude(recipes));
        assertEquals(1, pour(extruder.tanks(), Fluids.LAVA, 1));
        // A single millibucket of lava is enough, because no batch ever drinks it.
        var cobblestone = recipes.getFirst().value();
        for (int tick = 0; tick < cobblestone.duration(); tick++) assertTrue(extruder.extrude(recipes));
        assertTrue(extruder.getItem(SLOT_OUTPUT).is(Items.COBBLESTONE));
        assertEquals(1, amount(extruder, TANK_B));
    }

    /** The recipes a plain machine offers, in the order it counts them: the ones naming no upgrade. */
    private static List<Integer> stoneIndices(List<RecipeHolder<ExtrudingRecipe>> recipes) {
        List<Integer> indices = new java.util.ArrayList<>();
        for (int index = 0; index < recipes.size(); index++) if (recipes.get(index).value().upgrade().isEmpty()) indices.add(index);
        return indices;
    }

    @Test
    void theButtonStepsThroughEveryProductAndComesBackAround(MinecraftServer server) {
        var recipes = shipped(server);
        assertEquals(13, recipes.size(), "water and lava should give nine stone products and four sand ones");
        var stone = stoneIndices(recipes);
        assertEquals(9, stone.size());
        var extruder = extruder(1);
        extruder.extrude(recipes);
        assertEquals(stone.getFirst(), extruder.choiceIndex());
        // Without the sand upgrade the button walks the stone products only, skipping the sand ones in between.
        for (int step = 1; step <= stone.size(); step++) {
            extruder.cycleChoice(recipes, false);
            assertEquals(stone.get(step % stone.size()), extruder.choiceIndex());
        }
        // And back the other way, which is what a right click does.
        extruder.cycleChoice(recipes, true);
        assertEquals(stone.getLast(), extruder.choiceIndex());
    }

    @Test
    void theSandUpgradeSwapsTheProductsForSandAndBack(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = ready(2_000, 2_000);
        select(extruder, recipes, Items.OBSIDIAN);
        extruder.upgrades().setItem(0, new ItemStack(ModItems.SAND_UPGRADE.get()));
        // The stone choice is gone with the upgrade in: the first sand product stands in, and the button walks sand only.
        extruder.extrude(recipes);
        assertTrue(recipes.get(extruder.choiceIndex()).value().result().create().is(Items.GRAVEL));
        java.util.Set<Item> seen = new java.util.HashSet<>();
        for (int step = 0; step < 8; step++) {
            seen.add(recipes.get(extruder.choiceIndex()).value().result().create().getItem());
            extruder.cycleChoice(recipes, false);
        }
        assertEquals(java.util.Set.of(Items.GRAVEL, Items.SAND, Items.RED_SAND, Items.SOUL_SAND), seen);
        // Sand drinks a bucket of water a batch and makes sand.
        select(extruder, recipes, Items.SAND);
        var sand = recipes.get(extruder.choiceIndex()).value();
        assertEquals(40, sand.duration());
        for (int tick = 0; tick < sand.duration(); tick++) assertTrue(extruder.extrude(recipes));
        assertTrue(extruder.getItem(SLOT_OUTPUT).is(Items.SAND));
        assertEquals(1_000, amount(extruder, TANK_A));
        assertEquals(2_000, amount(extruder, TANK_B));
        // Taking the upgrade out brings the stone products back, starting from the first one.
        extruder.upgrades().setItem(0, ItemStack.EMPTY);
        extruder.extrude(recipes);
        assertTrue(recipes.get(extruder.choiceIndex()).value().result().create().is(Items.COBBLESTONE));
    }

    @Test
    void theChosenProductIsTheOneItMakes(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = ready(2_000, 2_000);
        select(extruder, recipes, Items.STONE);
        var chosen = recipes.get(extruder.choiceIndex()).value();
        for (int tick = 0; tick < chosen.duration(); tick++) assertTrue(extruder.extrude(recipes));
        assertTrue(extruder.getItem(SLOT_OUTPUT).is(Items.STONE));
        // Stone is paid for in water alone: the lava is there to work, not to be spent.
        assertEquals(2_000 - chosen.first().consumes(), amount(extruder, TANK_A));
        assertEquals(0, chosen.second().consumes());
        assertEquals(2_000, amount(extruder, TANK_B));
    }

    @Test
    void switchingProductThrowsAwayTheBatchUnderWay(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = ready(2_000, 2_000);
        // Half a batch of the product it starts on, so there is something to lose.
        int half = recipes.getFirst().value().duration() / 2;
        for (int tick = 0; tick < half; tick++) assertTrue(extruder.extrude(recipes));
        assertEquals(half, extruder.menuData().get(DATA_PROGRESS));
        extruder.cycleChoice(recipes, false);
        // Ticks spent towards cobblestone are not ticks towards the next product along.
        assertEquals(0, extruder.menuData().get(DATA_PROGRESS));
        assertTrue(extruder.getItem(SLOT_OUTPUT).isEmpty());
    }

    @Test
    void theChosenProductSurvivesSaveAndLoad(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = extruder(1);
        extruder.cycleChoice(recipes, false);
        int chosen = extruder.choiceIndex();
        assertEquals(stoneIndices(recipes).get(1), chosen, "the second stone product, past the sand one between");
        var loaded = extruder(1);
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                extruder.saveWithoutMetadata(server.registryAccess())));
        loaded.extrude(recipes);
        assertEquals(chosen, loaded.choiceIndex());
    }

    @Test
    void aChoiceWhoseRecipeIsGoneFallsBackToTheFirst(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = extruder(1);
        extruder.cycleChoice(recipes, false);
        assertEquals(stoneIndices(recipes).get(1), extruder.choiceIndex());
        // A data pack that no longer has that recipe must not leave the machine pointing at nothing.
        extruder.extrude(List.of(recipe("other_mix", Fluids.WATER, 1_000, Fluids.LAVA, 1_000)));
        assertEquals(0, extruder.choiceIndex());
    }

    @Test
    void theSecondTankRefusesTheFluidTheFirstAlreadyHolds(MinecraftServer server) {
        var extruder = extruder(1);
        assertEquals(TANK_CAPACITY, pour(extruder.tanks(), Fluids.WATER, TANK_CAPACITY));
        // Both tanks full of water would leave the other half of every recipe nowhere to go.
        assertEquals(0, pour(extruder.tanks(), Fluids.WATER, 1_000));
        assertEquals(1_000, pour(extruder.tanks(), Fluids.LAVA, 1_000));
        assertEquals(TANK_CAPACITY, amount(extruder, TANK_A));
        assertEquals(1_000, amount(extruder, TANK_B));
    }

    @Test
    void eitherOrderExtrudesAndEachTankGivesUpItsOwnHalf(MinecraftServer server) {
        var recipes = List.of(recipe("uneven", Fluids.WATER, 300, Fluids.LAVA, 700));
        var extruder = extruder(1);
        charge(extruder, CAPACITY);
        // Lava first, so the tanks hold the recipe's parts the other way round.
        pour(extruder.tanks(), Fluids.LAVA, 1_000);
        pour(extruder.tanks(), Fluids.WATER, 1_000);
        for (int tick = 0; tick < 20; tick++) assertTrue(extruder.extrude(recipes));
        assertTrue(extruder.getItem(SLOT_OUTPUT).is(Items.SPONGE));
        assertEquals(1_000 - 700, amount(extruder, TANK_A));
        assertEquals(1_000 - 300, amount(extruder, TANK_B));
        assertEquals(Fluids.LAVA, extruder.contents(TANK_A).getFluid());
        assertEquals(Fluids.WATER, extruder.contents(TANK_B).getFluid());
    }

    @Test
    void tooLittleOfOneHalfIsNoBatchAtAll(MinecraftServer server) {
        var recipes = List.of(recipe("even", Fluids.WATER, 1_000, Fluids.LAVA, 1_000));
        var extruder = extruder(1);
        charge(extruder, CAPACITY);
        pour(extruder.tanks(), Fluids.WATER, 1_000);
        pour(extruder.tanks(), Fluids.LAVA, 999);
        assertFalse(extruder.extrude(recipes));
        assertEquals(CAPACITY, extruder.energy().getAmountAsInt());
        // The last millibucket arriving starts it.
        pour(extruder.tanks(), Fluids.LAVA, 1);
        assertTrue(extruder.extrude(recipes));
    }

    @Test
    void noEnergyMeansNoProgress(MinecraftServer server) {
        var recipes = List.of(recipe("even", Fluids.WATER, 1_000, Fluids.LAVA, 1_000));
        var extruder = extruder(1);
        pour(extruder.tanks(), Fluids.WATER, 1_000);
        pour(extruder.tanks(), Fluids.LAVA, 1_000);
        assertFalse(extruder.extrude(recipes));
        assertEquals(0, extruder.menuData().get(DATA_PROGRESS));
        charge(extruder, ENERGY_PER_TICK);
        assertTrue(extruder.extrude(recipes));
        assertEquals(1, extruder.menuData().get(DATA_PROGRESS));
    }

    @Test
    void aFullOutputSlotHoldsTheBatchAndItsFluids(MinecraftServer server) {
        var recipes = List.of(recipe("even", Fluids.WATER, 1_000, Fluids.LAVA, 1_000));
        var extruder = extruder(1);
        charge(extruder, CAPACITY);
        pour(extruder.tanks(), Fluids.WATER, 2_000);
        pour(extruder.tanks(), Fluids.LAVA, 2_000);
        extruder.setItem(SLOT_OUTPUT, new ItemStack(Items.SPONGE, Items.SPONGE.getDefaultMaxStackSize()));
        assertFalse(extruder.extrude(recipes));
        assertEquals(2_000, amount(extruder, TANK_A));
        assertEquals(CAPACITY, extruder.energy().getAmountAsInt());
        // Emptying the slot lets the batch that was waiting through.
        extruder.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
        for (int tick = 0; tick < 20; tick++) assertTrue(extruder.extrude(recipes));
        assertEquals(1, extruder.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1_000, amount(extruder, TANK_A));
    }

    @Test
    void bothTanksSurviveSaveAndLoadAndGrowWithTheLevel(MinecraftServer server) {
        var extruder = extruder(4);
        assertEquals(TANK_CAPACITY * 8, extruder.tankCapacity());
        pour(extruder.tanks(), Fluids.WATER, 5_000);
        pour(extruder.tanks(), Fluids.LAVA, 3_000);
        charge(extruder, 1_000);
        var loaded = extruder(4);
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                extruder.saveWithoutMetadata(server.registryAccess())));
        assertEquals(Fluids.WATER, loaded.contents(TANK_A).getFluid());
        assertEquals(5_000, amount(loaded, TANK_A));
        assertEquals(Fluids.LAVA, loaded.contents(TANK_B).getFluid());
        assertEquals(3_000, amount(loaded, TANK_B));
        assertEquals(1_000, loaded.energy().getAmountAsInt());
    }

    @Test
    void nothingGoesInAsAnItemAndOnlyOutputFacesGiveItBack(MinecraftServer server) {
        var extruder = extruder(1);
        var sponge = new ItemStack(Items.SPONGE);
        assertFalse(extruder.canPlaceItem(SLOT_OUTPUT, sponge));
        for (Direction side : Direction.values()) {
            extruder.sideConfig().set(side, SideMode.INPUT);
            assertEquals(0, extruder.getSlotsForFace(side).length);
            assertFalse(extruder.canPlaceItemThroughFace(SLOT_OUTPUT, sponge, side));
            assertFalse(extruder.canTakeItemThroughFace(SLOT_OUTPUT, sponge, side));
            extruder.sideConfig().set(side, SideMode.OUTPUT);
            assertArrayEquals(new int[]{SLOT_OUTPUT}, extruder.getSlotsForFace(side));
            assertTrue(extruder.canTakeItemThroughFace(SLOT_OUTPUT, sponge, side));
            assertFalse(extruder.canPlaceItemThroughFace(SLOT_OUTPUT, sponge, side));
        }
    }

    @Test
    void obsidianIsTheOneThatDrinksBothFeeds(MinecraftServer server) {
        var recipes = shipped(server);
        var extruder = ready(2_000, 2_000);
        select(extruder, recipes, Items.OBSIDIAN);
        var chosen = recipes.get(extruder.choiceIndex()).value();
        for (int tick = 0; tick < chosen.duration(); tick++) assertTrue(extruder.extrude(recipes));
        assertEquals(2_000 - chosen.first().consumes(), amount(extruder, TANK_A));
        assertEquals(2_000 - chosen.second().consumes(), amount(extruder, TANK_B));
        assertTrue(chosen.first().consumes() > 0 && chosen.second().consumes() > 0);
    }

    /**
     * The table Thermal's own igneous extruder runs on: 400 RF of cobblestone, 800 for each stone
     * and 1600 for obsidian. This machine draws 20 FE a tick, so those totals are the durations.
     */
    @Test
    void theProductsAndTheirCostsMatchThermalsTable(MinecraftServer server) {
        var recipes = shipped(server);
        // The cheapest product comes first, so that is what a machine nobody has touched makes.
        assertEquals(0, indexOf(recipes, Items.COBBLESTONE));
        var cobblestone = recipes.getFirst().value();
        assertEquals(20, cobblestone.duration());
        assertEquals(0, cobblestone.first().consumes());
        assertEquals(0, cobblestone.second().consumes());
        for (Item item : List.of(Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.DEEPSLATE,
                Items.TUFF)) {
            var stone = recipes.get(indexOf(recipes, item)).value();
            assertEquals(40, stone.duration(), item.toString());
            assertEquals(1_000, stone.first().consumes(), item.toString());
            assertEquals(0, stone.second().consumes(), item.toString());
        }
        // Cobbled deepslate is free like cobblestone, but slower: the rock it comes off is harder.
        var cobbledDeepslate = recipes.get(indexOf(recipes, Items.COBBLED_DEEPSLATE)).value();
        assertEquals(40, cobbledDeepslate.duration());
        assertEquals(0, cobbledDeepslate.first().consumes());
        assertEquals(0, cobbledDeepslate.second().consumes());
        var obsidian = recipes.get(indexOf(recipes, Items.OBSIDIAN)).value();
        assertEquals(80, obsidian.duration());
        assertEquals(1_000, obsidian.first().consumes());
        assertEquals(1_000, obsidian.second().consumes());
    }

    @Test
    void everyProductAndTheBenchRecipeAreRegistered(MinecraftServer server) {
        for (String name : new String[]{"extruding/cobblestone", "extruding/stone", "extruding/granite",
                "extruding/diorite", "extruding/andesite", "extruding/deepslate", "extruding/cobbled_deepslate",
                "extruding/tuff", "extruding/obsidian", "extruder"}) {
            assertTrue(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath("futuretech", name))).isPresent(), name);
        }
    }

    /** The recipe is a {@link Recipe}, so the book can hold it beside the machine's own type. */
    @Test
    void anExtrusionIsNotPlaceableInAGrid(MinecraftServer server) {
        Recipe<?> extruding = recipe("even", Fluids.WATER, 1_000, Fluids.LAVA, 1_000).value();
        assertTrue(extruding.placementInfo().isImpossibleToPlace());
        assertSame(ModRecipes.EXTRUDING.get(), extruding.getType());
    }
}
