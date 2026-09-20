package dev.futuretech.item;

import dev.futuretech.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
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
    // Asked for rather than held: a resource is an item stack underneath, and building one while
    // the class loads — before the test server has bootstrapped the registries — is what made
    // every case in here fail at once.
    private static ItemResource cobblestone() { return ItemResource.of(Items.COBBLESTONE); }

    private static ItemResource dirt() { return ItemResource.of(Items.DIRT); }

    private static ItemStack filter(ItemFilterMode mode, ItemStack... listed) {
        ItemStack filter = new ItemStack(ModItems.FILTER.get());
        NonNullList<ItemStack> entries = NonNullList.withSize(ItemFilterItem.Tier.MK1.slots, ItemStack.EMPTY);
        for (int slot = 0; slot < listed.length; slot++) entries.set(slot, listed[slot]);
        ItemFilterItem.setEntries(filter, entries);
        ItemFilterItem.setMode(filter, mode);
        return filter;
    }

    @Test
    void whitelistPassesOnlyTheListedItemsAndNothingWhenEmpty(MinecraftServer server) {
        ItemStack filter = filter(ItemFilterMode.WHITELIST, new ItemStack(Items.COBBLESTONE, 32));
        assertTrue(ItemFilterItem.allows(filter, cobblestone()));
        assertFalse(ItemFilterItem.allows(filter, dirt()));
        assertFalse(ItemFilterItem.allows(filter(ItemFilterMode.WHITELIST), dirt()), "An empty whitelist lets nothing through");
    }

    @Test
    void blacklistKeepsOutOnlyTheListedItemsAndNothingWhenEmpty(MinecraftServer server) {
        ItemStack filter = filter(ItemFilterMode.BLACKLIST, new ItemStack(Items.COBBLESTONE));
        assertFalse(ItemFilterItem.allows(filter, cobblestone()));
        assertTrue(ItemFilterItem.allows(filter, dirt()));
        assertTrue(ItemFilterItem.allows(filter(ItemFilterMode.BLACKLIST), dirt()), "An empty blacklist lets everything through");
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
        assertTrue(ItemFilterItem.allows(ItemStack.EMPTY, dirt()));
        assertTrue(ItemFilterItem.allows(new ItemStack(Items.PAPER), dirt()));
        // The default card is a whitelist, so a fresh one blocks until something is listed.
        assertEquals(ItemFilterMode.WHITELIST, ItemFilterItem.mode(new ItemStack(ModItems.FILTER.get())));
        assertFalse(ItemFilterItem.allows(new ItemStack(ModItems.FILTER.get()), dirt()));
    }

    /** A card of the tier, with the entries listed on it and nothing else set. */
    private static ItemStack card(ItemFilterItem.Tier tier, ItemStack... listed) {
        ItemStack filter = new ItemStack(switch (tier) {
            case MK1 -> ModItems.FILTER.get();
            case MK2 -> ModItems.FILTER_MK2.get();
            case MK3 -> ModItems.FILTER_MK3.get();
            case MK4 -> ModItems.FILTER_MK4.get();
        });
        NonNullList<ItemStack> entries = NonNullList.withSize(tier.slots, ItemStack.EMPTY);
        for (int slot = 0; slot < listed.length; slot++) entries.set(slot, listed[slot]);
        ItemFilterItem.setEntries(filter, entries);
        return filter;
    }

    private static ItemStack marked(String mark) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        CompoundTag tag = new CompoundTag();
        tag.putString("mark", mark);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack worn(int damage) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.set(DataComponents.DAMAGE, damage);
        return stack;
    }

    @Test
    void everyTierHoldsTwiceTheListOfTheOneBelow(MinecraftServer server) {
        assertEquals(9, ItemFilterItem.Tier.MK1.slots);
        assertEquals(18, ItemFilterItem.Tier.MK2.slots);
        assertEquals(36, ItemFilterItem.Tier.MK3.slots);
        assertEquals(72, ItemFilterItem.Tier.MK4.slots);
        assertEquals(ItemFilterItem.Tier.MK4.slots, ItemFilterItem.MAX_SLOTS);
        for (ItemFilterItem.Tier tier : ItemFilterItem.Tier.values()) {
            assertEquals(tier.slots, ItemFilterItem.entries(card(tier)).size(), tier + " holds its own list");
            assertEquals(tier.slots, tier.columns() * tier.rows(), tier + " lays out in whole rows");
        }
    }

    @Test
    void aCardReadingTheItemAloneCatchesItHoweverWornOrMarked(MinecraftServer server) {
        ItemStack filter = card(ItemFilterItem.Tier.MK2, new ItemStack(Items.DIAMOND_PICKAXE));
        ItemFilterItem.setMode(filter, ItemFilterMode.WHITELIST);
        assertTrue(ItemFilterItem.allows(filter, ItemResource.of(worn(400))), "a worn one is the same pickaxe");
        assertTrue(ItemFilterItem.allows(filter, ItemResource.of(marked("a"))), "and so is a marked one");
    }

    @Test
    void aCardFollowingTheDataTellsTwoMarkedItemsApart(MinecraftServer server) {
        ItemStack filter = card(ItemFilterItem.Tier.MK2, marked("keep"));
        ItemFilterItem.setMode(filter, ItemFilterMode.WHITELIST);
        ItemFilterItem.setMatch(filter, ItemFilterMatch.ITEM_ONLY
                .with(ItemFilterMatch.Option.DATA, true)
                .with(ItemFilterMatch.Option.CUSTOM_DATA, true));
        assertTrue(ItemFilterItem.allows(filter, ItemResource.of(marked("keep"))), "the one it listed");
        assertFalse(ItemFilterItem.allows(filter, ItemResource.of(marked("other"))), "not another mark");
        assertFalse(ItemFilterItem.allows(filter, ItemResource.of(new ItemStack(Items.DIAMOND_PICKAXE))),
                "and not a plain one");
    }

    @Test
    void durabilityIsTakenBackOutOfTheComparisonOnItsOwn(MinecraftServer server) {
        ItemStack filter = card(ItemFilterItem.Tier.MK2, new ItemStack(Items.DIAMOND_PICKAXE));
        ItemFilterItem.setMode(filter, ItemFilterMode.WHITELIST);
        ItemFilterItem.setMatch(filter, ItemFilterMatch.ITEM_ONLY.with(ItemFilterMatch.Option.DATA, true));
        assertTrue(ItemFilterItem.allows(filter, ItemResource.of(worn(400))),
                "following the data but ignoring durability still catches a worn one");
        ItemFilterItem.setMatch(filter, ItemFilterItem.match(filter).with(ItemFilterMatch.Option.DAMAGE, true));
        assertFalse(ItemFilterItem.allows(filter, ItemResource.of(worn(400))),
                "and following durability tells them apart");
        assertTrue(ItemFilterItem.allows(filter, ItemResource.of(new ItemStack(Items.DIAMOND_PICKAXE))),
                "the pristine one is still the one listed");
    }

    @Test
    void onlyTheBigCardsKeepLevels(MinecraftServer server) {
        ItemStack small = card(ItemFilterItem.Tier.MK2, new ItemStack(Items.COBBLESTONE));
        ItemFilterItem.setMatch(small, ItemFilterMatch.ITEM_ONLY.with(ItemFilterMatch.Option.COUNT, true));
        assertFalse(ItemFilterItem.counting(small), "an MK2 has no level to keep");
        assertEquals(ItemFilterItem.NO_LEVEL, ItemFilterItem.level(small, cobblestone()));

        ItemStack big = card(ItemFilterItem.Tier.MK3, new ItemStack(Items.COBBLESTONE));
        assertEquals(ItemFilterItem.NO_LEVEL, ItemFilterItem.level(big, cobblestone()), "and none until it is asked to keep one");
        ItemFilterItem.setMatch(big, ItemFilterMatch.ITEM_ONLY.with(ItemFilterMatch.Option.COUNT, true));
        assertTrue(ItemFilterItem.counting(big));
        assertEquals(0, ItemFilterItem.level(big, cobblestone()), "keeping none is a level too, until a number is set");
        ItemFilterItem.setCount(big, 320);
        assertEquals(320, ItemFilterItem.level(big, cobblestone()), "the card's number, for what it lists");
        assertEquals(ItemFilterItem.NO_LEVEL, ItemFilterItem.level(big, dirt()), "and nothing for what it does not list");
        ItemFilterItem.setCount(big, 5000);
        assertEquals(ItemFilterItem.MAX_COUNT, ItemFilterItem.count(big), "three digits is as high as it goes");
    }
}
