package dev.futuretech.item;

import dev.futuretech.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ItemFilterItemTest {
    private static final ItemResource COBBLESTONE = ItemResource.of(Items.COBBLESTONE);
    private static final ItemResource DIRT = ItemResource.of(Items.DIRT);

    private static ItemStack filter(ItemFilterMode mode, ItemStack... listed) {
        ItemStack filter = new ItemStack(ModItems.FILTER.get());
        NonNullList<ItemStack> entries = NonNullList.withSize(ItemFilterItem.SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < listed.length; slot++) entries.set(slot, listed[slot]);
        ItemFilterItem.setEntries(filter, entries);
        ItemFilterItem.setMode(filter, mode);
        return filter;
    }

    @Test
    void whitelistPassesOnlyTheListedItemsAndNothingWhenEmpty(MinecraftServer server) {
        ItemStack filter = filter(ItemFilterMode.WHITELIST, new ItemStack(Items.COBBLESTONE, 32));
        assertTrue(ItemFilterItem.allows(filter, COBBLESTONE));
        assertFalse(ItemFilterItem.allows(filter, DIRT));
        assertFalse(ItemFilterItem.allows(filter(ItemFilterMode.WHITELIST), DIRT), "An empty whitelist lets nothing through");
    }

    @Test
    void blacklistKeepsOutOnlyTheListedItemsAndNothingWhenEmpty(MinecraftServer server) {
        ItemStack filter = filter(ItemFilterMode.BLACKLIST, new ItemStack(Items.COBBLESTONE));
        assertFalse(ItemFilterItem.allows(filter, COBBLESTONE));
        assertTrue(ItemFilterItem.allows(filter, DIRT));
        assertTrue(ItemFilterItem.allows(filter(ItemFilterMode.BLACKLIST), DIRT), "An empty blacklist lets everything through");
    }

    @Test
    void entriesKeepTheirSlotsAndOnlyTheItemKind(MinecraftServer server) {
        ItemStack filter = filter(ItemFilterMode.WHITELIST, ItemStack.EMPTY, new ItemStack(Items.DIRT, 16));
        NonNullList<ItemStack> entries = ItemFilterItem.entries(filter);
        assertTrue(entries.get(0).isEmpty());
        assertEquals(1, entries.get(1).getCount(), "Counts never matter, so one is stored");
        assertTrue(entries.get(1).is(Items.DIRT));
        // A damaged tool still matches its listed kind: the filter reads the item, not its components.
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(50);
        assertTrue(ItemFilterItem.allows(filter(ItemFilterMode.WHITELIST, new ItemStack(Items.IRON_PICKAXE)), ItemResource.of(worn)));
    }

    @Test
    void anythingThatIsNotAFilterLetsEverythingThrough(MinecraftServer server) {
        assertTrue(ItemFilterItem.allows(ItemStack.EMPTY, DIRT));
        assertTrue(ItemFilterItem.allows(new ItemStack(Items.PAPER), DIRT));
        // The default card is a whitelist, so a fresh one blocks until something is listed.
        assertEquals(ItemFilterMode.WHITELIST, ItemFilterItem.mode(new ItemStack(ModItems.FILTER.get())));
        assertFalse(ItemFilterItem.allows(new ItemStack(ModItems.FILTER.get()), DIRT));
    }
}
