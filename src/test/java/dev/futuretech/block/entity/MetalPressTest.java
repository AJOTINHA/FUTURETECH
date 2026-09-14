package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.MetalPressBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class MetalPressTest {
    private static MetalPressBlockEntity machine(int mk) {
        var press = new MetalPressBlockEntity(BlockPos.ZERO, ModBlocks.METAL_PRESS.get().defaultBlockState().setValue(MachineLevel.MK, mk));
        press.setItem(SLOT_MOLD, new ItemStack(ModItems.PLATE_MOLD.get()));
        return press;
    }

    private static void charge(MetalPressBlockEntity press, int amount) {
        while (amount > 0) {
            press.beginTick();
            try (var tx = Transaction.openRoot()) {
                int inserted = press.energy().insert(amount, tx);
                assertTrue(inserted > 0);
                amount -= inserted;
                tx.commit();
            }
        }
    }

    private static boolean tick(MetalPressBlockEntity press, MinecraftServer server) {
        press.beginTick();
        return press.press(input -> server.getRecipeManager().getRecipeFor(ModRecipes.PRESSING.get(), input, null).orElse(null));
    }

    @Test
    void missingMoldStopsWorkAndRemovingItCancelsProgress(MinecraftServer server) {
        var press = machine(1);
        press.setItem(SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 4));
        press.removeItem(SLOT_MOLD, 1);
        charge(press, CAPACITY);
        assertFalse(tick(press, server));
        assertEquals(CAPACITY, press.energy().getAmountAsInt());
        press.setItem(SLOT_MOLD, new ItemStack(Items.IRON_INGOT));
        assertFalse(tick(press, server));
        press.setItem(SLOT_MOLD, new ItemStack(ModItems.GEAR_MOLD.get()));
        for (int t = 0; t < 30; t++) assertTrue(tick(press, server));
        ItemStack mold = press.removeItem(SLOT_MOLD, 1);
        assertEquals(0, press.menuData().get(DATA_PROGRESS));
        assertEquals(0, press.menuData().get(DATA_WORKING));
        assertFalse(tick(press, server));
        assertEquals(4, press.getItem(SLOT_INPUT).getCount());
        press.setItem(SLOT_MOLD, mold);
        assertTrue(tick(press, server));
        assertEquals(1, press.menuData().get(DATA_PROGRESS));
        press.removeItemNoUpdate(SLOT_MOLD);
        assertEquals(0, press.menuData().get(DATA_PROGRESS));
    }

    @Test
    void moldSlotIsDedicatedAndExcludedFromAutomation() {
        var press = machine(4);
        var mold = new ItemStack(ModItems.GEAR_MOLD.get());
        assertTrue(press.canPlaceItem(SLOT_MOLD, mold));
        assertFalse(press.canPlaceItem(SLOT_MOLD, new ItemStack(Items.IRON_INGOT)));
        assertFalse(press.canPlaceItem(SLOT_INPUT, mold));
        for (Direction side : Direction.values()) {
            press.sideConfig().set(side, SideMode.BOTH);
            for (int slot : press.getSlotsForFace(side)) assertNotEquals(SLOT_MOLD, slot);
            assertFalse(press.canPlaceItemThroughFace(SLOT_MOLD, mold, side));
            assertFalse(press.canPlaceItemThroughFace(SLOT_INPUT, mold, side));
            assertFalse(press.canTakeItemThroughFace(SLOT_MOLD, mold, side));
        }
    }

    @Test
    void menuShiftClickUsesMoldSlotAndKeepsInventoryRangesCorrect() {
        for (int mk = 1; mk <= 4; mk++) {
            var press = machine(mk);
            press.removeItem(SLOT_MOLD, 1);
            var inventory = new net.minecraft.world.entity.player.Inventory(null, new net.minecraft.world.entity.EntityEquipment());
            var menu = new dev.futuretech.menu.MetalPressMenu(1, inventory, press, press.upgrades(), press.menuData());
            int moldIndex = 2 * mk;
            int inventoryStart = moldIndex + 1;
            var moldSlot = menu.getSlot(moldIndex);
            assertEquals(SLOT_MOLD, moldSlot.getContainerSlot());
            assertEquals(1, moldSlot.getMaxStackSize());
            assertFalse(moldSlot.mayPlace(new ItemStack(Items.IRON_INGOT)));
            menu.getSlot(inventoryStart).set(new ItemStack(ModItems.GEAR_MOLD.get()));
            assertFalse(menu.quickMoveStack(null, inventoryStart).isEmpty());
            assertTrue(press.gearMode());
            assertTrue(menu.getSlot(inventoryStart).getItem().isEmpty());
            menu.getSlot(inventoryStart).set(new ItemStack(Items.IRON_INGOT, 4));
            assertFalse(menu.quickMoveStack(null, inventoryStart).isEmpty());
            assertEquals(4, press.getItem(SLOT_INPUT).getCount());
            assertFalse(menu.quickMoveStack(null, moldIndex).isEmpty());
            assertTrue(press.getItem(SLOT_MOLD).isEmpty());
            assertEquals(4, press.getItem(SLOT_INPUT).getCount());
        }
    }

    @Test
    void allMetalsConsumeExactCountsOnlyOnCompletion(MinecraftServer server) {
        Item[] ingots = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.NETHERITE_INGOT, ModItems.STEEL_INGOT.get()};
        Item[] plates = {ModItems.IRON_PLATE.get(), ModItems.GOLD_PLATE.get(), ModItems.COPPER_PLATE.get(), ModItems.NETHERITE_PLATE.get(), ModItems.STEEL_PLATE.get()};
        Item[] gears = {ModItems.IRON_GEAR.get(), ModItems.GOLD_GEAR.get(), ModItems.COPPER_GEAR.get(), ModItems.NETHERITE_GEAR.get(), ModItems.STEEL_GEAR.get()};
        for (boolean gear : new boolean[]{false, true}) {
            for (int metal = 0; metal < ingots.length; metal++) {
                var press = machine(1);
                if (gear) press.setItem(SLOT_MOLD, new ItemStack(ModItems.GEAR_MOLD.get()));
                int count = gear ? 4 : 1;
                int duration = gear ? 160 : 100;
                charge(press, duration * ENERGY_PER_TICK);
                press.setItem(SLOT_INPUT, new ItemStack(ingots[metal], count + 1));
                for (int t = 0; t < duration - 1; t++) assertTrue(tick(press, server));
                assertEquals(count + 1, press.getItem(SLOT_INPUT).getCount());
                assertTrue(press.getItem(SLOT_OUTPUT).isEmpty());
                assertTrue(tick(press, server));
                assertEquals(1, press.getItem(SLOT_INPUT).getCount());
                assertEquals(1, press.getItem(SLOT_OUTPUT).getCount());
                assertTrue(press.getItem(SLOT_OUTPUT).is((gear ? gears : plates)[metal]));
                assertEquals(0, press.energy().getAmountAsInt());
                assertEquals(1, press.getItem(SLOT_MOLD).getCount());
                assertTrue(press.getItem(SLOT_MOLD).is((gear ? ModItems.GEAR_MOLD : ModItems.PLATE_MOLD).get()));
            }
        }
    }

    @Test
    void switchingMoldRequiresFourIngotsAndRestartsProgress(MinecraftServer server) {
        var press = machine(1);
        charge(press, CAPACITY);
        press.setItem(SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 3));
        for (int t = 0; t < 30; t++) assertTrue(tick(press, server));
        press.setItem(SLOT_MOLD, new ItemStack(ModItems.GEAR_MOLD.get()));
        assertEquals(0, press.menuData().get(DATA_PROGRESS));
        int before = press.energy().getAmountAsInt();
        assertFalse(tick(press, server));
        assertEquals(before, press.energy().getAmountAsInt());
        press.setItem(SLOT_INPUT, new ItemStack(Items.IRON_INGOT, 4));
        for (int t = 0; t < 160; t++) assertTrue(tick(press, server));
        assertTrue(press.getItem(SLOT_INPUT).isEmpty());
        assertTrue(press.getItem(SLOT_OUTPUT).is(ModItems.IRON_GEAR.get()));
    }

    @Test
    void pausesWithoutEnergyOrOutputSpaceAndResumesAfterReload(MinecraftServer server) {
        var press = machine(1);
        press.setItem(SLOT_MOLD, new ItemStack(ModItems.GEAR_MOLD.get()));
        charge(press, 25 * ENERGY_PER_TICK);
        press.setItem(SLOT_INPUT, new ItemStack(Items.GOLD_INGOT, 4));
        for (int t = 0; t < 25; t++) assertTrue(tick(press, server));
        assertFalse(tick(press, server));
        assertEquals(25, press.menuData().get(DATA_PROGRESS));
        charge(press, 135 * ENERGY_PER_TICK);
        press.setItem(SLOT_OUTPUT, new ItemStack(ModItems.GOLD_GEAR.get(), 64));
        assertFalse(tick(press, server));
        assertEquals(25, press.menuData().get(DATA_PROGRESS));
        assertEquals(2700, press.energy().getAmountAsInt());
        press.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
        var restored = machine(1);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), press.saveWithoutMetadata(server.registryAccess())));
        assertTrue(restored.gearMode());
        assertEquals(25, restored.menuData().get(DATA_PROGRESS));
        for (int t = 25; t < 160; t++) assertTrue(tick(restored, server));
        assertTrue(restored.getItem(SLOT_OUTPUT).is(ModItems.GOLD_GEAR.get()));
        assertEquals(0, restored.energy().getAmountAsInt());
        assertTrue(restored.getItem(SLOT_INPUT).isEmpty());
    }

    @Test
    void hopperCompletesBatchesAcrossMk4LanesThenAllFourWork(MinecraftServer server) {
        var press = machine(4);
        press.setItem(SLOT_MOLD, new ItemStack(ModItems.GEAR_MOLD.get()));
        press.sideConfig().set(Direction.UP, SideMode.INPUT);
        for (int n = 0; n < 16; n++) {
            ItemStack ingot = new ItemStack(Items.COPPER_INGOT);
            int accepted = -1;
            for (int lane = 0; lane < 4; lane++) {
                if (press.canPlaceItemThroughFace(lane, ingot, Direction.UP)) {
                    assertEquals(-1, accepted);
                    accepted = lane;
                }
            }
            assertEquals(n / 4, accepted);
            var held = press.getItem(accepted);
            if (held.isEmpty()) press.setItem(accepted, ingot);
            else held.grow(1);
        }
        charge(press, CAPACITY);
        int duration = MachineLevel.duration(160, 4);
        for (int t = 0; t < duration; t++) assertTrue(tick(press, server));
        for (int lane = 0; lane < 4; lane++) {
            assertTrue(press.getItem(lane).isEmpty());
            assertTrue(press.getItem(SLOT_OUTPUT + lane).is(ModItems.COPPER_GEAR.get()));
        }
        assertEquals(CAPACITY - duration * 4 * MachineLevel.consumption(ENERGY_PER_TICK, 4), press.energy().getAmountAsInt());
    }
}
