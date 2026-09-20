package dev.futuretech.api.side;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AutoTransferTest {
    private static int[] counts(NonNullList<ItemStack> items, int base, int lanes) {
        int[] counts = new int[lanes];
        for (int lane = 0; lane < lanes; lane++) counts[lane] = items.get(base + lane).getCount();
        return counts;
    }

    @Test
    void sortingSpreadsAStackOverTheOpenLanesAndSettles(MinecraftServer server) {
        var items = NonNullList.withSize(8, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.COBBLESTONE, 9));
        // Lane 0 spills 4 then 2, and lane 1 hands one of its 4 on to lane 2, all in one pass.
        assertTrue(AutoTransfer.balance(items, 0, 3));
        assertArrayEquals(new int[] {3, 3, 3}, counts(items, 0, 3));
        assertFalse(AutoTransfer.balance(items, 0, 3));
        // The fourth lane is shut, and the slots past the lanes are not touched.
        assertTrue(items.get(3).isEmpty());
        assertTrue(items.get(4).isEmpty());
    }

    @Test
    void sortingOnlyFillsEmptyLanesOrShorterStacksOfTheSameItem(MinecraftServer server) {
        var items = NonNullList.withSize(4, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.COBBLESTONE, 8));
        items.set(1, new ItemStack(Items.OAK_LOG, 1));
        items.set(2, new ItemStack(Items.COBBLESTONE, 2));
        assertTrue(AutoTransfer.balance(items, 0, 4));
        // The log's lane is left alone; the shorter cobblestone and the empty lane each get a share.
        assertTrue(items.get(1).is(Items.OAK_LOG));
        assertEquals(1, items.get(1).getCount());
        assertArrayEquals(new int[] {4, 1, 3, 3}, counts(items, 0, 4));
        assertTrue(items.get(3).is(Items.COBBLESTONE));

        // A single item never moves, and nothing happens with a single lane.
        var single = NonNullList.withSize(2, ItemStack.EMPTY);
        single.set(0, new ItemStack(Items.COBBLESTONE, 1));
        assertFalse(AutoTransfer.balance(single, 0, 2));
        single.set(0, new ItemStack(Items.COBBLESTONE, 64));
        assertFalse(AutoTransfer.balance(single, 0, 1));
    }
}
