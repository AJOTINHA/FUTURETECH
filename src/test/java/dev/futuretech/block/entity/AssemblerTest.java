package dev.futuretech.block.entity;

import dev.futuretech.block.AssemblerBlock;
import dev.futuretech.recipe.AssemblingRecipe;
import dev.futuretech.registry.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AssemblerTest {
    private static final String RECIPE = "futuretech:assembling/machine_casing";
    private static class Cell implements AssemblerBlockEntity.Environment {
        final Map<BlockPos, AssemblerBlockEntity> blocks = new HashMap<>();
        final Map<BlockPos, ItemStacksResourceHandler> chests = new HashMap<>();
        final List<AssemblerBlockEntity.Entry> book;
        AssemblerBlockEntity table, input, output, worker, terminal;
        final ItemStacksResourceHandler source = new ItemStacksResourceHandler(2), destination = new ItemStacksResourceHandler(1);
        Cell(MinecraftServer server) {
            book = server.getRecipeManager().getRecipes().stream().filter(h -> h.value() instanceof AssemblingRecipe)
                    .map(h -> new AssemblerBlockEntity.Entry(h.id().identifier().toString(), (AssemblingRecipe)h.value())).toList();
            assertEquals(1, book.size());
            table = add(BlockPos.ZERO, ModBlocks.ASSEMBLY_TABLE.get().defaultBlockState());
            input = add(new BlockPos(-2,0,0), ModBlocks.TRANSPORT_ARM.get().defaultBlockState());
            output = add(new BlockPos(2,0,0), ModBlocks.TRANSPORT_ARM.get().defaultBlockState().setValue(AssemblerBlock.OUTPUT,true));
            worker = add(new BlockPos(0,0,2), ModBlocks.ASSEMBLY_ARM.get().defaultBlockState());
            terminal = add(new BlockPos(0,0,-2), ModBlocks.ASSEMBLER_TERMINAL.get().defaultBlockState());
            terminal.energy().set(AssemblerBlockEntity.ENERGY_CAPACITY);
            chests.put(new BlockPos(-4,0,0),source); chests.put(new BlockPos(4,0,0),destination);
            source.set(0,ItemResource.of(Items.STONE),1); source.set(1,ItemResource.of(Items.IRON_INGOT),1);
            assertTrue(table.select(RECIPE));
        }
        AssemblerBlockEntity add(BlockPos pos, BlockState state) {
            var block = new AssemblerBlockEntity(pos,state,this); blocks.put(pos,block); return block;
        }
        @Override public AssemblerBlockEntity assembler(BlockPos pos) { return blocks.get(pos); }
        @Override public List<BlockPos> positions() { var positions = new ArrayList<>(blocks.keySet()); positions.addAll(chests.keySet()); return positions; }
        @Override public ResourceHandler<ItemResource> handler(BlockPos pos, Direction side) { return chests.get(pos); }
        @Override public List<AssemblerBlockEntity.Entry> recipes() { return book; }
        void ticks(int amount) {
            for (int i = 0; i < amount; i++) for (var arm : List.of(input,worker,output)) {
                if (!blocks.containsKey(arm.getBlockPos())) continue;
                if (arm.phase() == 0) arm.begin(); else arm.advance();
            }
        }
        void fillTable() { input.begin(); for(int i=0;i<60;i++) input.advance(); input.begin(); for(int i=0;i<60;i++) input.advance(); }
        void restore(MinecraftServer server) {
            var saved = new ArrayList<>(blocks.values());
            for (var old : saved) {
                var tag = old.saveWithoutMetadata(server.registryAccess());
                var fresh = add(old.getBlockPos(),old.getBlockState());
                fresh.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),tag));
            }
            table=blocks.get(table.getBlockPos()); input=blocks.get(input.getBlockPos()); worker=blocks.get(worker.getBlockPos());
            output=blocks.get(output.getBlockPos()); terminal=blocks.get(terminal.getBlockPos());
        }
    }
    @Test void completeCellConsumesOnlyStoneAndIronAndOutputsOneCasing(MinecraftServer server) {
        var c = new Cell(server);
        assertEquals(15,c.table.connections());
        c.ticks(400);
        assertEquals(0,c.source.getAmountAsInt(0)); assertEquals(0,c.source.getAmountAsInt(1));
        assertEquals(ItemResource.of(ModItems.MACHINE_CASING.get()),c.destination.getResource(0));
        assertEquals(1,c.destination.getAmountAsInt(0));
        assertTrue(c.table.inventory.isEmpty()); assertTrue(c.input.cargo().isEmpty()); assertTrue(c.output.cargo().isEmpty());
        c.ticks(400); assertEquals(1,c.destination.getAmountAsInt(0));
        assertEquals(AssemblerBlockEntity.ENERGY_CAPACITY - 6000, c.terminal.energy().getAmountAsInt());
    }
    @Test void monitorShowsRecipeWithoutOpeningMenuAndUpdatesDeliveredIngredients(MinecraftServer server) {
        var c = new Cell(server);
        assertTrue(c.terminal.refreshMonitor());
        assertEquals(2,c.terminal.monitorIngredients().size());
        assertTrue(c.terminal.monitorIngredients().get(0).is(Items.STONE));
        assertTrue(c.terminal.monitorIngredients().get(1).is(Items.IRON_INGOT));
        assertTrue(c.terminal.monitorResult().is(ModItems.MACHINE_CASING.get()));
        assertEquals(0,c.terminal.monitorPresent());
        assertFalse(c.terminal.refreshMonitor());
        assertEquals(0,c.table.status()); assertEquals(c.table.status(),c.terminal.monitorStatus());
        c.fillTable(); assertTrue(c.terminal.refreshMonitor());
        assertEquals(3,c.terminal.monitorPresent());
    }
    @Test void monitorSnapshotSendsProgressAndResultToClientAndClearsMissingTable(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.worker.begin();
        for(int i=0;i<60;i++) c.worker.advance();
        var client = new AssemblerBlockEntity(c.terminal.getBlockPos(),c.terminal.getBlockState());
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),c.terminal.getUpdateTag(server.registryAccess())));
        assertEquals(50,client.monitorProgress()); assertEquals(3,client.monitorPresent());
        assertEquals(4,client.monitorStatus());
        assertTrue(client.monitorIngredients().get(0).is(Items.STONE));
        assertTrue(client.monitorResult().is(ModItems.MACHINE_CASING.get()));
        c.terminal.energy().set(0);
        for(int i=0;i<30;i++) c.worker.advance();
        c.terminal.refreshMonitor(); assertEquals(50,c.terminal.monitorProgress());
        assertEquals(9,c.terminal.monitorStatus());
        c.terminal.energy().set(10000);
        for(int i=0;i<40;i++) c.worker.advance();
        c.terminal.refreshMonitor(); assertEquals(100,c.terminal.monitorProgress());
        assertEquals(0,c.terminal.monitorPresent());
        c.blocks.remove(c.table.getBlockPos());
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),c.terminal.getUpdateTag(server.registryAccess())));
        assertTrue(client.monitorIngredients().isEmpty()); assertTrue(client.monitorResult().isEmpty());
        assertEquals(0,client.monitorProgress());
        assertEquals(10,client.monitorStatus());
    }
    @Test void monitorSyncsStatusChangesEvenWhenProgressAndItemsDoNotChange(MinecraftServer server) {
        var c = new Cell(server); c.terminal.refreshMonitor();
        c.terminal.energy().set(0);
        assertTrue(c.terminal.refreshMonitor()); assertEquals(9,c.terminal.monitorStatus());
        assertFalse(c.terminal.refreshMonitor());
        c.terminal.energy().set(10000);
        assertTrue(c.terminal.refreshMonitor()); assertEquals(0,c.terminal.monitorStatus());
        c.blocks.remove(c.input.getBlockPos());
        assertTrue(c.terminal.refreshMonitor()); assertEquals(12,c.terminal.monitorStatus());
        var client = new AssemblerBlockEntity(c.terminal.getBlockPos(),c.terminal.getBlockState());
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),c.terminal.getUpdateTag(server.registryAccess())));
        assertEquals(c.table.status(),client.monitorStatus());
    }
    @Test void emptyControllerPreventsIngredientExtraction(MinecraftServer server) {
        var c = new Cell(server); c.terminal.energy().set(0); c.ticks(100);
        assertEquals(1,c.source.getAmountAsInt(0)); assertEquals(1,c.source.getAmountAsInt(1));
        assertTrue(c.table.inventory.isEmpty()); assertTrue(c.input.cargo().isEmpty());
        assertEquals(9,c.table.status());
    }
    @Test void powerLossFreezesCargoAndAnimationThenResumesAfterReload(MinecraftServer server) {
        var c = new Cell(server); c.terminal.energy().set(20 * 25);
        c.ticks(100);
        assertEquals(25,c.input.animationTick(.5F)); assertFalse(c.input.moving());
        assertEquals(0,c.terminal.energy().getAmountAsInt()); assertTrue(c.input.cargo().is(Items.STONE));
        c.restore(server); c.ticks(50);
        assertEquals(25,c.input.animationTick(.5F)); assertTrue(c.input.cargo().is(Items.STONE));
        c.terminal.energy().set(10000); c.ticks(400);
        assertEquals(1,c.destination.getAmountAsInt(0)); assertTrue(c.table.inventory.isEmpty());
    }
    @Test void assemblyPausesWithoutConsumingMaterialsAndFinishesOnce(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.terminal.energy().set(20 * 35); c.worker.begin();
        for(int i=0;i<100;i++) c.worker.advance();
        assertEquals(35,c.worker.animationTick(0)); assertFalse(c.worker.moving());
        assertTrue(c.table.inventory.getItem(0).is(Items.STONE)); assertTrue(c.table.inventory.getItem(9).isEmpty());
        c.terminal.energy().set(10000); c.ticks(300);
        assertEquals(1,c.destination.getAmountAsInt(0));
    }
    @Test void energyEntersOnlyTheControllerWithATickLimitAndNoExtraction(MinecraftServer server) {
        var c = new Cell(server);
        assertNull(c.table.energyHandler()); assertNull(c.input.energyHandler()); assertNull(c.output.energyHandler()); assertNull(c.worker.energyHandler());
        var handler = c.terminal.energyHandler(); assertNotNull(handler); c.terminal.energy().set(0);
        try(var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            assertEquals(200,handler.insert(1000,tx)); tx.commit();
        }
        try(var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            assertEquals(0,handler.insert(1000,tx)); assertEquals(0,handler.extract(1000,tx)); tx.commit();
        }
        c.terminal.energy().beginTick();
        try(var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) { assertEquals(200,handler.insert(1000,tx)); }
        assertEquals(200,handler.getAmountAsInt());
        c.restore(server); assertEquals(200,c.terminal.energy().getAmountAsInt());
    }
    @Test void aBlockedArmStopsUsingControllerEnergy(MinecraftServer server) {
        var c = new Cell(server); c.input.begin(); c.blocks.remove(c.table.getBlockPos());
        int before = c.terminal.energy().getAmountAsInt();
        for(int i=0;i<100;i++) c.input.advance();
        assertEquals(before,c.terminal.energy().getAmountAsInt()); assertFalse(c.input.moving());
    }
    @Test void outputWaitsForAssemblyToolToRetract(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.worker.begin();
        for(int i=0;i<100;i++) c.worker.advance();
        assertTrue(c.table.inventory.getItem(9).is(ModItems.MACHINE_CASING.get()));
        c.output.begin(); assertEquals(0,c.output.phase());
        for(int i=0;i<20;i++) c.worker.advance();
        c.output.begin(); assertEquals(1,c.output.phase());
        assertTrue(c.table.inventory.getItem(9).is(ModItems.MACHINE_CASING.get()));
        assertTrue(c.output.cargo().isEmpty());
        for(int i=0;i<19;i++) c.output.advance();
        assertTrue(c.table.inventory.getItem(9).is(ModItems.MACHINE_CASING.get()));
        assertTrue(c.output.cargo().isEmpty());
        c.output.advance();
        assertTrue(c.table.inventory.getItem(9).isEmpty());
        assertTrue(c.output.cargo().is(ModItems.MACHINE_CASING.get()));
    }
    @Test void savingDuringOutputApproachKeepsResultOnTableUntilPickup(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.worker.begin();
        for(int i=0;i<120;i++) c.worker.advance();
        c.output.begin(); for(int i=0;i<10;i++) c.output.advance();
        c.restore(server);
        assertTrue(c.table.inventory.getItem(9).is(ModItems.MACHINE_CASING.get()));
        assertTrue(c.output.cargo().isEmpty());
        assertTrue(c.table.locked());
        for(int i=0;i<10;i++) c.output.advance();
        assertTrue(c.table.inventory.getItem(9).isEmpty());
        assertTrue(c.output.cargo().is(ModItems.MACHINE_CASING.get()));
        c.ticks(100); assertEquals(1,c.destination.getAmountAsInt(0));
    }

    @Test void missingTableDuringOutputApproachDoesNotCreateCargo(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.worker.begin();
        for(int i=0;i<120;i++) c.worker.advance();
        c.output.begin(); c.blocks.remove(c.table.getBlockPos());
        for(int i=0;i<60;i++) c.output.advance();
        assertTrue(c.output.cargo().isEmpty());
        assertEquals(0,c.destination.getAmountAsInt(0));
        c.blocks.put(c.table.getBlockPos(),c.table);
        c.ticks(100); assertEquals(1,c.destination.getAmountAsInt(0));
    }
    @Test void fullDestinationKeepsResultOnTable(MinecraftServer server) {
        var c = new Cell(server); c.destination.set(0,ItemResource.of(Items.DIRT),64); c.ticks(400);
        assertTrue(c.table.inventory.getItem(9).is(ModItems.MACHINE_CASING.get())); assertTrue(c.output.cargo().isEmpty());
        c.destination.set(0,ItemResource.EMPTY,0); c.ticks(100);
        assertEquals(1,c.destination.getAmountAsInt(0));
    }
    @Test void destinationFillingDuringFlightKeepsCargoAndRetries(MinecraftServer server) {
        var c = new Cell(server); c.fillTable(); c.worker.begin(); for(int i=0;i<120;i++) c.worker.advance(); c.output.begin();
        c.destination.set(0,ItemResource.of(Items.DIRT),64);
        for(int i=0;i<100;i++) c.output.advance();
        assertTrue(c.output.cargo().is(ModItems.MACHINE_CASING.get()));
        assertTrue(c.table.inventory.getItem(9).isEmpty()); assertEquals(64,c.destination.getAmountAsInt(0));
        c.destination.set(0,ItemResource.EMPTY,0); for(int i=0;i<30;i++) c.output.advance();
        assertTrue(c.output.cargo().isEmpty()); assertEquals(1,c.destination.getAmountAsInt(0));
    }
    @Test void savedCellResumesDuringEveryStageWithoutLosingOrDuplicatingItems(MinecraftServer server) {
        for(int stop : new int[]{1,25,45,80,130,160,220,230,250,275}) {
            var c = new Cell(server); c.ticks(stop); c.restore(server); c.ticks(400);
            assertEquals(1,c.destination.getAmountAsInt(0),"save at tick "+stop);
            assertTrue(c.table.inventory.isEmpty()); assertTrue(c.input.cargo().isEmpty()); assertTrue(c.output.cargo().isEmpty());
            assertEquals(0,c.source.getAmountAsInt(0)); assertEquals(0,c.source.getAmountAsInt(1));
        }
    }
    @Test void removedTableLeavesInFlightIngredientInArm(MinecraftServer server) {
        var c = new Cell(server); c.input.begin(); c.blocks.remove(c.table.getBlockPos());
        for(int i=0;i<100;i++) c.input.advance();
        assertTrue(c.input.cargo().is(Items.STONE)); assertEquals(1,c.input.cargo().getCount());
        assertEquals(0,c.source.getAmountAsInt(0));
        assertEquals(0,c.input.animationTick(0));
        c.blocks.put(c.table.getBlockPos(),c.table); for(int i=0;i<60;i++) c.input.advance();
        assertTrue(c.input.cargo().isEmpty()); assertTrue(c.table.inventory.getItem(0).is(Items.STONE));
    }
    @Test void overlappingInputArmsDoNotReserveTheSameIngredient(MinecraftServer server) {
        var c = new Cell(server);
        var second = c.add(new BlockPos(-2,0,1),ModBlocks.TRANSPORT_ARM.get().defaultBlockState());
        c.source.set(0,ItemResource.of(Items.STONE),10);
        c.input.begin(); second.begin();
        assertEquals(9,c.source.getAmountAsInt(0)); assertTrue(second.cargo().isEmpty());
        assertFalse(c.table.select(RECIPE));
    }
    @Test void missingTerminalAndOutOfReachChestDoNotExtract(MinecraftServer server) {
        var c = new Cell(server); c.blocks.remove(c.terminal.getBlockPos()); c.ticks(50);
        assertEquals(1,c.source.getAmountAsInt(0));
        c.blocks.put(c.terminal.getBlockPos(),c.terminal);
        c.chests.remove(new BlockPos(-4,0,0)); c.chests.put(new BlockPos(-6,0,0),c.source); c.ticks(100);
        assertEquals(1,c.source.getAmountAsInt(0));
    }
    @Test void recipeRejectsCobblestoneAndDoesNotConsumePartialInputs(MinecraftServer server) {
        var c = new Cell(server); var recipe = c.book.getFirst().recipe();
        c.table.inventory.setItem(0,new ItemStack(Items.COBBLESTONE)); c.table.inventory.setItem(1,new ItemStack(Items.IRON_INGOT));
        assertFalse(c.table.finish(recipe)); assertEquals(1,c.table.inventory.getItem(1).getCount());
        c.table.inventory.setItem(0,new ItemStack(Items.STONE)); assertTrue(c.table.finish(recipe));
        assertFalse(c.table.finish(recipe)); assertEquals(1,c.table.inventory.getItem(9).getCount());
    }
    @Test void partialInsertionRollsBackAndSimulationNeverMutates(MinecraftServer server) {
        var destination = new ItemStacksResourceHandler(1); destination.set(0,ItemResource.of(Items.IRON_INGOT),63);
        assertFalse(AssemblerBlockEntity.insertAll(destination,new ItemStack(Items.IRON_INGOT,2)));
        assertEquals(63,destination.getAmountAsInt(0));
        assertTrue(AssemblerBlockEntity.canInsert(destination,new ItemStack(Items.IRON_INGOT)));
        assertEquals(63,destination.getAmountAsInt(0));
    }

    /** The survey clock starts at "never", which must count as due rather than overflow into "fresh". */
    @Test void surveyIsDueWhenNeverTakenAndAfterTheInterval() {
        assertTrue(AssemblerBlockEntity.surveyDue(Long.MIN_VALUE, 0));
        assertTrue(AssemblerBlockEntity.surveyDue(Long.MIN_VALUE, 123456));
        assertFalse(AssemblerBlockEntity.surveyDue(100, 100));
        assertFalse(AssemblerBlockEntity.surveyDue(100, 139));
        assertTrue(AssemblerBlockEntity.surveyDue(100, 140));
        assertTrue(AssemblerBlockEntity.surveyDue(200, 100), "a clock that went backwards resurveys");
    }
}
