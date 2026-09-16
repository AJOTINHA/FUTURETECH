package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.PaintMachineBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.item.FacadeItem;
import dev.futuretech.menu.PaintMachineMenu;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The paint machine: one block and four plates in, four panels of that block out, gated by energy and the slots' kinds. */
@ExtendWith(EphemeralTestServerProvider.class)
class PaintMachineTest {
    private static PaintMachineBlockEntity machine(int mk) {
        return new PaintMachineBlockEntity(BlockPos.ZERO, ModBlocks.PAINT_MACHINE.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    private static void charge(PaintMachineBlockEntity painter, int amount) {
        while (amount > 0) {
            painter.beginTick();
            try (var tx = Transaction.openRoot()) {
                int inserted = painter.energy().insert(amount, tx);
                assertTrue(inserted > 0);
                amount -= inserted;
                tx.commit();
            }
        }
    }

    private static boolean tick(PaintMachineBlockEntity painter) {
        painter.beginTick();
        return painter.paint();
    }

    @Test
    void oneBlockAndFourPlatesBecomeFourPanelsOfThatBlock(MinecraftServer server) {
        var painter = machine(1);
        painter.setItem(SLOT_BLOCK, new ItemStack(Items.STONE, 2));
        painter.setItem(SLOT_PLATES, new ItemStack(ModItems.STEEL_PLATE.get(), 9));
        charge(painter, CAPACITY);
        for (int t = 0; t < PAINT_TICKS; t++) assertTrue(tick(painter), "tick " + t);
        ItemStack output = painter.getItem(SLOT_OUTPUT);
        assertEquals(YIELD, output.getCount());
        assertEquals(Blocks.STONE.defaultBlockState(), FacadeItem.block(output));
        assertEquals(1, painter.getItem(SLOT_BLOCK).getCount());
        assertEquals(9 - PLATES_PER_JOB, painter.getItem(SLOT_PLATES).getCount());
        assertEquals(CAPACITY - PAINT_TICKS * ENERGY_PER_TICK, painter.energy().getAmountAsInt());
        // A second job stacks onto the first; a third has too few plates and never starts.
        for (int t = 0; t < PAINT_TICKS; t++) assertTrue(tick(painter));
        assertEquals(2 * YIELD, painter.getItem(SLOT_OUTPUT).getCount());
        assertEquals(1, painter.getItem(SLOT_PLATES).getCount());
        assertFalse(tick(painter));
        assertEquals(0, painter.menuData().get(DATA_PROGRESS_BASE));
    }

    @Test
    void nothingHappensWithoutEnergyOrWithABlockNoFacadeMayWear(MinecraftServer server) {
        var painter = machine(1);
        painter.setItem(SLOT_BLOCK, new ItemStack(Items.STONE));
        painter.setItem(SLOT_PLATES, new ItemStack(ModItems.STEEL_PLATE.get(), 4));
        assertFalse(tick(painter), "no energy");
        charge(painter, CAPACITY);
        assertTrue(tick(painter));
        // A glass pane is not a full block, so it cannot be worn; the job is dropped and the progress with it.
        painter.setItem(SLOT_BLOCK, new ItemStack(Items.GLASS_PANE));
        assertFalse(tick(painter));
        assertEquals(0, painter.menuData().get(DATA_PROGRESS_BASE));
        assertNull(facadeOf(new ItemStack(Items.GLASS_PANE)));
        assertNull(facadeOf(new ItemStack(Items.IRON_INGOT)));
        assertNotNull(facadeOf(new ItemStack(Items.OAK_PLANKS)));
    }

    @Test
    void slotsTakeOnlyTheirOwnKindAndAutomationSpreadsOverLanes(MinecraftServer server) {
        var painter = machine(2);
        var stone = new ItemStack(Items.STONE);
        var plate = new ItemStack(ModItems.STEEL_PLATE.get());
        assertTrue(painter.canPlaceItem(SLOT_BLOCK, stone));
        assertFalse(painter.canPlaceItem(SLOT_BLOCK, plate));
        assertTrue(painter.canPlaceItem(SLOT_PLATES, plate));
        assertFalse(painter.canPlaceItem(SLOT_PLATES, stone));
        assertFalse(painter.canPlaceItem(SLOT_OUTPUT, stone));
        painter.sideConfig().set(Direction.UP, SideMode.INPUT);
        assertEquals(SLOT_BLOCK, painter.preferredSlot(stone));
        assertTrue(painter.canPlaceItemThroughFace(SLOT_BLOCK, stone, Direction.UP));
        assertFalse(painter.canPlaceItemThroughFace(SLOT_BLOCK + 1, stone, Direction.UP));
        painter.setItem(SLOT_BLOCK, new ItemStack(Items.STONE, 3));
        // The emptier lane takes the next one, so a hopper feeds both lanes.
        assertEquals(SLOT_BLOCK + 1, painter.preferredSlot(stone));
        assertEquals(SLOT_PLATES, painter.preferredSlot(plate));
        assertFalse(painter.canTakeItemThroughFace(SLOT_BLOCK, stone, Direction.UP));
        painter.sideConfig().set(Direction.DOWN, SideMode.OUTPUT);
        assertTrue(painter.canTakeItemThroughFace(SLOT_OUTPUT, stone, Direction.DOWN));
    }

    @Test
    void menuShiftClickSendsEachKindToItsOwnSlots(MinecraftServer server) {
        var painter = machine(1);
        var inventory = new Inventory(null, new EntityEquipment());
        var menu = new PaintMachineMenu(1, inventory, painter, painter.upgrades(), painter.menuData());
        int inventoryStart = 3;
        assertFalse(menu.getSlot(0).mayPlace(new ItemStack(ModItems.STEEL_PLATE.get())));
        assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.STONE)));
        menu.getSlot(inventoryStart).set(new ItemStack(ModItems.STEEL_PLATE.get(), 8));
        assertFalse(menu.quickMoveStack(null, inventoryStart).isEmpty());
        assertEquals(8, painter.getItem(SLOT_PLATES).getCount());
        menu.getSlot(inventoryStart).set(new ItemStack(Items.STONE, 2));
        assertFalse(menu.quickMoveStack(null, inventoryStart).isEmpty());
        assertEquals(2, painter.getItem(SLOT_BLOCK).getCount());
        // An ingot has no place in the machine, so it only moves between the inventory and the hotbar.
        menu.getSlot(inventoryStart).set(new ItemStack(Items.IRON_INGOT));
        menu.quickMoveStack(null, inventoryStart);
        assertTrue(painter.getItem(SLOT_BLOCK).is(Items.STONE));
        assertTrue(painter.getItem(SLOT_PLATES).is(ModItems.STEEL_PLATE.get()));
    }
}
