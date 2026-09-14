package dev.futuretech.item;

import dev.futuretech.registry.ModDataComponents;
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
 * A filter card for item cable connectors: up to {@value #SLOTS} item kinds and whether they are
 * the only ones allowed through or the only ones kept out. Matching looks at the item alone, so
 * one entry covers every damage value and every enchantment of that item.
 */
public final class ItemFilterItem extends Item {
    public static final int SLOTS = 9;

    public ItemFilterItem(Properties properties) {
        super(properties);
    }

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

    /** The nine entries, empty slots included, as copies the caller may edit. */
    public static NonNullList<ItemStack> entries(ItemStack filter) {
        NonNullList<ItemStack> entries = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        filter.getOrDefault(ModDataComponents.FILTER_ITEMS.get(), ItemContainerContents.EMPTY).copyInto(entries);
        return entries;
    }

    /** Stores the entries; only the item kind matters, so each is trimmed to a single item. */
    public static void setEntries(ItemStack filter, NonNullList<ItemStack> entries) {
        NonNullList<ItemStack> trimmed = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        boolean any = false;
        for (int slot = 0; slot < SLOTS && slot < entries.size(); slot++) {
            ItemStack entry = entries.get(slot);
            if (entry.isEmpty()) continue;
            trimmed.set(slot, entry.copyWithCount(1));
            any = true;
        }
        if (any) filter.set(ModDataComponents.FILTER_ITEMS.get(), ItemContainerContents.fromItems(trimmed));
        else filter.remove(ModDataComponents.FILTER_ITEMS.get());
    }

    public static boolean lists(ItemStack filter, ItemResource resource) {
        for (ItemStack entry : entries(filter)) {
            if (!entry.isEmpty() && resource.is(entry.getItem())) return true;
        }
        return false;
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
        boolean any = false;
        for (ItemStack entry : entries(stack)) {
            if (entry.isEmpty()) continue;
            lines.accept(Component.literal("  ").append(entry.getHoverName()));
            any = true;
        }
        if (!any) lines.accept(Component.translatable("item.futuretech.filter.empty"));
    }
}
