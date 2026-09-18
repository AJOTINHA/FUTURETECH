package dev.futuretech.item;

import dev.futuretech.registry.ModDataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A filter card for item cable connectors: a list of item kinds and whether they are the only
 * ones allowed through or the only ones kept out. Each MK doubles the list, and from the MK2 up
 * the card also carries an {@link ItemFilterMatch} — how closely it reads what it compares —
 * which is the gear on its screen. An MK1 has no gear and no match to carry: it reads the item
 * alone, so one entry covers every damage value and every enchantment of that item.
 */
public final class ItemFilterItem extends Item {
    /** The longest list there is, which is what the entries component is sized against. */
    public static final int MAX_SLOTS = Tier.MK4.slots;
    /** The highest level a card can keep; three digits is what its box holds. */
    public static final int MAX_COUNT = 999;
    /** What {@link #level} answers for a resource the card keeps no level of. */
    public static final int NO_LEVEL = -1;

    /** A card's size, and whether it has the gear. Every MK holds twice the list of the one below. */
    public enum Tier {
        MK1(9), MK2(18), MK3(36), MK4(72);

        public final int slots;

        Tier(int slots) {
            this.slots = slots;
        }

        public int mk() { return ordinal() + 1; }

        /** Registry name: the MK1 keeps the plain name it has always had. */
        public String itemName() { return this == MK1 ? "filter" : "filter_mk" + mk(); }

        /** Whether this card reads more than the item alone, which is the gear on the screen. */
        public boolean configurable() { return this != MK1; }

        /** Whether this card can keep a level in the inventory it touches; the small ones cannot. */
        public boolean counts() { return this == MK3 || this == MK4; }

        /** The switches this card's gear offers, which is all of them once it can count. */
        public boolean offers(ItemFilterMatch.Option option) {
            if (!configurable()) return false;
            return option != ItemFilterMatch.Option.COUNT || counts();
        }

        /** Rows of nine the screen lays the list out in; the MK1 keeps its dispenser-sized square. */
        public int columns() { return this == MK1 ? 3 : 9; }

        public int rows() { return slots / columns(); }

        public static Tier byOrdinal(int ordinal) {
            Tier[] tiers = values();
            return tiers[Math.clamp(ordinal, 0, tiers.length - 1)];
        }
    }

    private final Tier tier;

    public ItemFilterItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() { return tier; }

    /** The tier of a card, or the MK1's for anything that is not a card. */
    public static Tier tier(ItemStack filter) {
        return filter.getItem() instanceof ItemFilterItem card ? card.tier() : Tier.MK1;
    }

    /** How many entries this card holds. */
    public static int slots(ItemStack filter) { return tier(filter).slots; }

    public static boolean isFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFilterItem;
    }

    public static ItemFilterMode mode(ItemStack filter) {
        return filter.getOrDefault(ModDataComponents.FILTER_MODE.get(), ItemFilterMode.WHITELIST);
    }

    public static void setMode(ItemStack filter, ItemFilterMode mode) {
        if (mode == ItemFilterMode.WHITELIST) filter.remove(ModDataComponents.FILTER_MODE.get());
        else filter.set(ModDataComponents.FILTER_MODE.get(), mode);
    }

    /**
     * How closely this card reads what it compares. A switch the card does not offer reads as off
     * whatever is saved on it, so a card that was an MK4's does not keep counting as an MK2.
     */
    public static ItemFilterMatch match(ItemStack filter) {
        Tier tier = tier(filter);
        if (!tier.configurable()) return ItemFilterMatch.ITEM_ONLY;
        ItemFilterMatch saved = filter.getOrDefault(ModDataComponents.FILTER_MATCH.get(), ItemFilterMatch.ITEM_ONLY);
        ItemFilterMatch match = saved;
        for (ItemFilterMatch.Option option : ItemFilterMatch.Option.values()) {
            if (!tier.offers(option)) match = match.with(option, false);
        }
        return match;
    }

    /**
     * The level the card keeps every item it lists at, zero where none was set. Only read while
     * the card is following counts; the number stays on the card either way, so turning counting
     * off and on again finds it where it was left.
     */
    public static int count(ItemStack filter) {
        return Math.clamp(filter.getOrDefault(ModDataComponents.FILTER_COUNT.get(), 0), 0, MAX_COUNT);
    }

    public static void setCount(ItemStack filter, int count) {
        int clamped = Math.clamp(count, 0, MAX_COUNT);
        if (clamped > 0) filter.set(ModDataComponents.FILTER_COUNT.get(), clamped);
        else filter.remove(ModDataComponents.FILTER_COUNT.get());
    }

    /** Whether this card keeps a level at all, which is what makes a connector count what it moves. */
    public static boolean counting(ItemStack filter) { return isFilter(filter) && match(filter).count(); }

    /**
     * The level this card keeps of a resource: its number, for anything it lists, or
     * {@link #NO_LEVEL} where it is not counting or does not list the resource. Zero is a level
     * like any other — nothing goes in, and everything comes out.
     */
    public static int level(ItemStack filter, ItemResource resource) {
        if (!counting(filter) || !lists(filter, resource)) return NO_LEVEL;
        return count(filter);
    }

    public static void setMatch(ItemStack filter, ItemFilterMatch match) {
        if (match.equals(ItemFilterMatch.ITEM_ONLY)) filter.remove(ModDataComponents.FILTER_MATCH.get());
        else filter.set(ModDataComponents.FILTER_MATCH.get(), match);
    }

    /** The entries, empty slots included, as copies the caller may edit. */
    public static NonNullList<ItemStack> entries(ItemStack filter) {
        NonNullList<ItemStack> entries = NonNullList.withSize(slots(filter), ItemStack.EMPTY);
        filter.getOrDefault(ModDataComponents.FILTER_ITEMS.get(), ItemContainerContents.EMPTY).copyInto(entries);
        return entries;
    }

    /** Stores the entries; a single item of each is kept, with whatever data the card may read. */
    public static void setEntries(ItemStack filter, NonNullList<ItemStack> entries) {
        int slots = slots(filter);
        NonNullList<ItemStack> trimmed = NonNullList.withSize(slots, ItemStack.EMPTY);
        boolean any = false;
        for (int slot = 0; slot < slots && slot < entries.size(); slot++) {
            ItemStack entry = entries.get(slot);
            if (entry.isEmpty()) continue;
            trimmed.set(slot, entry.copyWithCount(1));
            any = true;
        }
        if (any) filter.set(ModDataComponents.FILTER_ITEMS.get(), ItemContainerContents.fromItems(trimmed));
        else filter.remove(ModDataComponents.FILTER_ITEMS.get());
    }

    public static boolean lists(ItemStack filter, ItemResource resource) {
        ItemFilterMatch match = match(filter);
        for (ItemStack entry : entries(filter)) {
            if (!entry.isEmpty() && matches(entry, resource, match)) return true;
        }
        return false;
    }

    /**
     * Whether one entry catches a resource. The item has to be the same either way; following the
     * data compares what the two carry, less the kinds of data the card was told to ignore.
     */
    public static boolean matches(ItemStack entry, ItemResource resource, ItemFilterMatch match) {
        if (!resource.is(entry.getItem())) return false;
        if (!match.data()) return true;
        return compared(entry.getComponentsPatch(), match).equals(compared(resource.getComponentsPatch(), match));
    }

    private static DataComponentPatch compared(DataComponentPatch patch, ItemFilterMatch match) {
        return patch.forget(match::ignores);
    }

    /** Whether the filter lets a resource through; anything that is not a filter lets everything through. */
    public static boolean allows(ItemStack filter, ItemResource resource) {
        if (!isFilter(filter)) return true;
        return (mode(filter) == ItemFilterMode.WHITELIST) == lists(filter, resource);
    }

    /** Reads the filter on every call, so edits made while the network exists apply at once. */
    public static Predicate<ItemResource> predicate(ItemStack filter) {
        return resource -> allows(filter, resource);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("item.futuretech.filter.mode",
                Component.translatable(mode(stack).translationKey())));
        ItemFilterMatch match = match(stack);
        if (match.data()) {
            lines.accept(Component.translatable("item.futuretech.filter.follows_data"));
        }
        boolean any = false;
        for (ItemStack entry : entries(stack)) {
            if (entry.isEmpty()) continue;
            lines.accept(Component.literal("  ").append(entry.getHoverName()));
            any = true;
        }
        if (!any) lines.accept(Component.translatable("item.futuretech.filter.empty"));
    }
}
