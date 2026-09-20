package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.LaneMachineBlockEntity.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.futuretech.recipe.LaneMachineRecipe;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class LaneMachineTest {
    @Test
    void loadsAllCrushingRecipesWithTheirFullOutputCounts(MinecraftServer server) {
        var inputs = new net.minecraft.world.item.Item[] {
                Items.COBBLESTONE, Items.GRAVEL, Items.SANDSTONE, Items.RED_SANDSTONE,
                Items.BONE, Items.BLAZE_ROD, Items.GLASS, Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT};
        var outputs = new net.minecraft.world.item.Item[] {
                Items.GRAVEL, Items.SAND, Items.SAND, Items.RED_SAND,
                Items.BONE_MEAL, Items.BLAZE_POWDER, Items.SAND,
                ModItems.IRON_POWDER.get(), ModItems.GOLD_POWDER.get(), ModItems.COPPER_POWDER.get(),
                ModItems.IRON_POWDER.get(), ModItems.GOLD_POWDER.get(), ModItems.COPPER_POWDER.get()};
        int[] counts = {1, 1, 4, 4, 6, 4, 1, 2, 2, 2, 1, 1, 1};
        for (int i = 0; i < inputs.length; i++) {
            var machine = crusher();
            charge(machine, WORK_TICKS * ENERGY_PER_TICK);
            machine.setItem(SLOT_INPUT, new ItemStack(inputs[i]));
            for (int t = 0; t < WORK_TICKS; t++) assertTrue(tick(machine, recipes(server)));
            assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
            assertTrue(machine.getItem(SLOT_OUTPUT).is(outputs[i]));
            assertEquals(counts[i], machine.getItem(SLOT_OUTPUT).getCount());
            assertEquals(0, machine.energy().getAmountAsInt());
        }
    }

    @Test
    void theSawmillTurnsAnyLogOfEveryWoodIntoTwiceThePlanksAndAPlankIntoTwiceTheSticks(MinecraftServer server) {
        var inputs = new net.minecraft.world.item.Item[] {
                Items.OAK_LOG, Items.STRIPPED_OAK_LOG, Items.OAK_WOOD, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.PALE_OAK_LOG,
                Items.CRIMSON_STEM, Items.WARPED_STEM, Items.BAMBOO_BLOCK, Items.OAK_PLANKS};
        var outputs = new net.minecraft.world.item.Item[] {
                Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.PALE_OAK_PLANKS,
                Items.CRIMSON_PLANKS, Items.WARPED_PLANKS, Items.BAMBOO_PLANKS, Items.STICK};
        var sawing = recipes(server, ModRecipes.SAWING.get());
        for (int i = 0; i < inputs.length; i++) {
            var machine = new LaneMachineBlockEntity(LaneMachineKind.SAWMILL, BlockPos.ZERO, ModBlocks.SAWMILL.get().defaultBlockState());
            charge(machine, WORK_TICKS * ENERGY_PER_TICK);
            machine.setItem(SLOT_INPUT, new ItemStack(inputs[i]));
            for (int t = 0; t < WORK_TICKS; t++) assertTrue(tick(machine, sawing), inputs[i].toString());
            assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
            assertTrue(machine.getItem(SLOT_OUTPUT).is(outputs[i]), inputs[i].toString());
            assertEquals(inputs[i] == Items.BAMBOO_BLOCK ? 4 : inputs[i] == Items.OAK_PLANKS ? 4 : 8, machine.getItem(SLOT_OUTPUT).getCount());
        }
        // The books do not leak into each other: a log is nothing to the crusher, cobblestone nothing to the sawmill.
        var crusher = crusher();
        charge(crusher, CAPACITY);
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.OAK_LOG));
        assertFalse(tick(crusher, recipes(server)));
        var sawmill = new LaneMachineBlockEntity(LaneMachineKind.SAWMILL, BlockPos.ZERO, ModBlocks.SAWMILL.get().defaultBlockState());
        charge(sawmill, CAPACITY);
        sawmill.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        assertFalse(tick(sawmill, sawing));
        assertEquals(net.minecraft.network.chat.Component.translatable("block.futuretech.sawmill"), sawmill.getDisplayName());
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
        for (int t = 25; t < WORK_TICKS; t++) assertTrue(tick(machine, recipes(server)));
        assertEquals(64, machine.getItem(SLOT_OUTPUT).getCount());
        assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
    }

    @Test
    void doesNotUseFurnaceRecipes(MinecraftServer server) {
        var machine = crusher();
        charge(machine, CAPACITY);
        machine.setItem(SLOT_INPUT, new ItemStack(Items.WET_SPONGE));
        assertFalse(tick(machine, recipes(server)));
        assertEquals(CAPACITY, machine.energy().getAmountAsInt());
        assertTrue(machine.getItem(SLOT_OUTPUT).isEmpty());
    }

    @Test
    void rawMetalsBecomeTwoPowdersThenTwoIngotsOneAtATime(MinecraftServer server) {
        Item[] raw = {Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER};
        Item[] powder = {ModItems.IRON_POWDER.get(), ModItems.GOLD_POWDER.get(), ModItems.COPPER_POWDER.get()};
        Item[] ingots = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT};
        for (int i = 0; i < raw.length; i++) {
            var machine = crusher();
            machine.setItem(SLOT_INPUT, new ItemStack(raw[i]));
            charge(machine, WORK_TICKS * ENERGY_PER_TICK);
            for (int t = 0; t < WORK_TICKS; t++) assertTrue(tick(machine, recipes(server)));
            ItemStack dust = machine.getItem(SLOT_OUTPUT).copy();
            assertTrue(dust.is(powder[i]));
            assertEquals(2, dust.getCount());
            assertTrue(machine.getItem(SLOT_INPUT).isEmpty());

            // The vanilla furnace and electric furnace share the SMELTING recipe book.
            var input = new SingleRecipeInput(dust);
            var vanillaRecipe = server.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, null).orElseThrow();
            ItemStack result = vanillaRecipe.value().assemble(input);
            assertTrue(result.is(ingots[i]));
            assertEquals(1, result.getCount());
            assertEquals(200, vanillaRecipe.value().cookingTime());

            var furnace = new ElectricFurnaceBlockEntity(BlockPos.ZERO,
                    ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
            furnace.setItem(ElectricFurnaceBlockEntity.SLOT_INPUT, dust);
            for (int t = 0; t < 20; t++) {
                furnace.beginTick();
                try (var transaction = Transaction.openRoot()) {
                    assertEquals(200, furnace.energy().insert(200, transaction));
                    transaction.commit();
                }
            }
            for (int completed = 1; completed <= 2; completed++) {
                for (int t = 0; t < ElectricFurnaceBlockEntity.SMELT_TICKS; t++) {
                    furnace.beginTick();
                    assertTrue(furnace.smelt(in -> server.getRecipeManager()
                            .getRecipeFor(RecipeType.SMELTING, in, null).orElse(null)));
                }
                assertTrue(furnace.getItem(ElectricFurnaceBlockEntity.SLOT_OUTPUT).is(ingots[i]));
                assertEquals(completed, furnace.getItem(ElectricFurnaceBlockEntity.SLOT_OUTPUT).getCount());
                assertEquals(2 - completed, furnace.getItem(ElectricFurnaceBlockEntity.SLOT_INPUT).getCount());
            }
            assertEquals(0, furnace.energy().getAmountAsInt());
            // Recycling a finished ingot produces exactly one powder, with no doubling.
            machine.setItem(SLOT_INPUT, new ItemStack(ingots[i]));
            machine.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
            charge(machine, CAPACITY);
            for (int t = 0; t < WORK_TICKS; t++) assertTrue(tick(machine, recipes(server)));
            assertTrue(machine.getItem(SLOT_INPUT).isEmpty());
            assertTrue(machine.getItem(SLOT_OUTPUT).is(powder[i]));
            assertEquals(1, machine.getItem(SLOT_OUTPUT).getCount());
            assertEquals(CAPACITY - WORK_TICKS * ENERGY_PER_TICK, machine.energy().getAmountAsInt());
        }
    }

    private static LaneMachineBlockEntity crusher() {
        return crusher(1);
    }

    private static LaneMachineBlockEntity crusher(int mk) {
        return new LaneMachineBlockEntity(LaneMachineKind.CRUSHER, BlockPos.ZERO, ModBlocks.CRUSHER.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    @Test
    void anMk4CrushesOnEveryLaneAtOnceAndPaysForEachOfThem(MinecraftServer server) {
        var crusher = crusher(4);
        charge(crusher, CAPACITY);
        Item[] inputs = {Items.COBBLESTONE, Items.BONE, Items.RAW_IRON, Items.BLAZE_ROD};
        for (int lane = 0; lane < 4; lane++) crusher.setItem(SLOT_INPUT + lane, new ItemStack(inputs[lane]));
        int before = crusher.energy().getAmountAsInt();
        int ticks = MachineLevel.duration(WORK_TICKS, 4);
        int perTick = MachineLevel.consumption(ENERGY_PER_TICK, 4);
        for (int tick = 0; tick < ticks; tick++) assertTrue(tick(crusher, recipes(server)));
        assertEquals(before - 4 * ticks * perTick, crusher.energy().getAmountAsInt(), "four jobs, four times the draw");
        assertTrue(crusher.getItem(SLOT_OUTPUT).is(Items.GRAVEL));
        assertEquals(6, crusher.getItem(SLOT_OUTPUT + 1).getCount());
        assertEquals(2, crusher.getItem(SLOT_OUTPUT + 2).getCount());
        assertEquals(4, crusher.getItem(SLOT_OUTPUT + 3).getCount());
        for (int lane = 0; lane < 4; lane++) assertTrue(crusher.getItem(SLOT_INPUT + lane).isEmpty());
        assertEquals(0b1111, crusher.menuData().get(DATA_WORKING), "the last tick had every lane at work");

        // An MK1 leaves the other lanes shut: nothing in them is touched or paid for.
        var single = crusher(1);
        charge(single, CAPACITY);
        single.setItem(SLOT_INPUT + 1, new ItemStack(Items.COBBLESTONE));
        assertFalse(tick(single, recipes(server)));
        assertEquals(CAPACITY, single.energy().getAmountAsInt());
        assertArrayEquals(new int[] {SLOT_INPUT}, lanesOf(single, SideMode.INPUT));
        assertArrayEquals(new int[] {SLOT_INPUT, SLOT_INPUT + 1, SLOT_INPUT + 2, SLOT_INPUT + 3}, lanesOf(crusher, SideMode.INPUT));
        assertArrayEquals(new int[] {SLOT_OUTPUT, SLOT_OUTPUT + 1, SLOT_OUTPUT + 2, SLOT_OUTPUT + 3}, lanesOf(crusher, SideMode.OUTPUT));
    }

    private static int[] lanesOf(LaneMachineBlockEntity crusher, SideMode mode) {
        crusher.sideConfig().set(Direction.UP, mode);
        return crusher.getSlotsForFace(Direction.UP);
    }

    @Test
    void aHopperSpreadsOneKindOfItemOverTheOpenLanes(MinecraftServer server) {
        var crusher = crusher(3);
        crusher.sideConfig().set(Direction.UP, SideMode.INPUT);
        var cobble = new ItemStack(Items.COBBLESTONE);
        // Empty lanes take turns from the first; a lane that already holds more waits its turn.
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT, cobble, Direction.UP));
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT + 1, cobble, Direction.UP));
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 2));
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT, cobble, Direction.UP));
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT + 1, cobble, Direction.UP));
        crusher.setItem(SLOT_INPUT + 1, new ItemStack(Items.COBBLESTONE, 2));
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT + 2, cobble, Direction.UP));
        crusher.setItem(SLOT_INPUT + 2, new ItemStack(Items.COBBLESTONE, 3));
        assertTrue(crusher.canPlaceItemThroughFace(SLOT_INPUT, cobble, Direction.UP), "back to the lane with the least");
        // A different item only goes where it will not mix, and a shut lane never opens.
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT, new ItemStack(Items.BONE), Direction.UP));
        assertFalse(crusher.canPlaceItemThroughFace(SLOT_INPUT + 3, cobble, Direction.UP));
    }

    @Test
    void everyLevelScalesStorageSpeedAndDrawTogetherAndUnlocksASlot(MinecraftServer server) {
        // Thermal Expansion's tiers: 1x, 1.5x, 2x and 3x the power, so an item costs the same on every level.
        assertEquals(CAPACITY, crusher(1).energy().getCapacityAsInt());
        assertEquals(30_000, crusher(2).energy().getCapacityAsInt());
        assertEquals(40_000, crusher(3).energy().getCapacityAsInt());
        assertEquals(60_000, crusher(4).energy().getCapacityAsInt());
        assertEquals(100, MachineLevel.duration(WORK_TICKS, 1));
        assertEquals(67, MachineLevel.duration(WORK_TICKS, 2));
        assertEquals(50, MachineLevel.duration(WORK_TICKS, 3));
        assertEquals(33, MachineLevel.duration(WORK_TICKS, 4));
        assertEquals(20, MachineLevel.consumption(ENERGY_PER_TICK, 1));
        assertEquals(30, MachineLevel.consumption(ENERGY_PER_TICK, 2));
        assertEquals(40, MachineLevel.consumption(ENERGY_PER_TICK, 3));
        assertEquals(60, MachineLevel.consumption(ENERGY_PER_TICK, 4));

        var crusher = crusher(2);
        charge(crusher, 30_000);
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE));
        for (int tick = 0; tick < 67; tick++) assertTrue(tick(crusher, recipes(server)), "tick " + tick);
        assertEquals(1, crusher.getItem(SLOT_OUTPUT).getCount());
        assertEquals(30_000 - 67 * 30, crusher.energy().getAmountAsInt());

        // The MK unlocks as many upgrade slots as its number: the third slot of an MK2 is shut.
        var upgrade = new ItemStack(ModItems.SPEED_UPGRADE.get());
        assertTrue(crusher.upgrades().canPlaceItem(1, upgrade));
        assertFalse(crusher.upgrades().canPlaceItem(2, upgrade));
        assertEquals(1, crusher(1).upgrades().unlocked());
        assertEquals(4, crusher(4).upgrades().unlocked());

        // A kit swaps the state in place and the buffer grows with it; the charge survives a reload.
        var upgraded = crusher(1);
        charge(upgraded, 20_000);
        upgraded.setBlockState(upgraded.getBlockState().setValue(MachineLevel.MK, 3));
        assertEquals(40_000, upgraded.energy().getCapacityAsInt());
        assertEquals(3, upgraded.lanes());
        var restored = crusher(3);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                upgraded.saveWithoutMetadata(server.registryAccess())));
        assertEquals(40_000, restored.energy().getCapacityAsInt());
        assertEquals(20_000, restored.energy().getAmountAsInt());
    }

    /** Fills the buffer the way a cable would: the handler only accepts INPUT_PER_TICK each tick. */
    private static void charge(LaneMachineBlockEntity crusher, int amount) {
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
    private static LaneMachineBlockEntity.RecipeLookup recipes(MinecraftServer server) {
        return recipes(server, ModRecipes.CRUSHING.get());
    }

    private static LaneMachineBlockEntity.RecipeLookup recipes(MinecraftServer server, RecipeType<LaneMachineRecipe> type) {
        return input -> server.getRecipeManager().getRecipeFor(type, input, null).orElse(null);
    }

    /** One tick of work, as {@code serverTick} does without needing a block state in the world. */
    private static boolean tick(LaneMachineBlockEntity crusher, LaneMachineBlockEntity.RecipeLookup recipes) {
        crusher.beginTick();
        return crusher.work(recipes);
    }

    @Test
    void crushsRawIronAndSpendsExactlyOneTickOfEnergyPerTick(MinecraftServer server) {
        var level = recipes(server);
        var crusher = crusher();
        crusher.setItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE, 2));
        charge(crusher, CAPACITY);
        int before = crusher.energy().getAmountAsInt();

        // Each crushing recipe takes 100 ticks.
        for (int tick = 0; tick < WORK_TICKS; tick++) assertTrue(tick(crusher, level), "tick " + tick);

        assertEquals(new ItemStack(Items.GRAVEL).getItem(), crusher.getItem(SLOT_OUTPUT).getItem());
        assertEquals(1, crusher.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, crusher.getItem(SLOT_INPUT).getCount());
        assertEquals(before - WORK_TICKS * ENERGY_PER_TICK, crusher.energy().getAmountAsInt());
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
        for (int tick = 25; tick < WORK_TICKS; tick++) assertTrue(tick(restored, level));
        assertEquals(1, restored.getItem(SLOT_OUTPUT).getCount());
    }
}
