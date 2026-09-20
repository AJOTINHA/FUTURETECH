package dev.futuretech.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How closely a filter reads the items it compares. On its own a filter looks at the item alone,
 * so one entry covers every damage value and every enchantment of that item — which is all an MK1
 * ever does. From the MK2 up the player can ask it to read the item's data as well, and then take
 * single kinds of data back out of the comparison: a filter that follows the data but ignores
 * durability lists a pickaxe once and catches it at any wear.
 *
 * <p>The last switch is not about comparing at all: it turns the card's number into a level the
 * connector keeps in the inventory it touches, and only the MK3 and up offer it.
 *
 * <p>Every switch is a "follow", and absent means "ignore", so a filter saved before any of this
 * existed reads exactly as it always did.
 */
public record ItemFilterMatch(boolean data, boolean damage, boolean enchantments, boolean customData,
                              boolean count) {
    /** The item and nothing else, which is what a filter does until it is told otherwise. */
    public static final ItemFilterMatch ITEM_ONLY = new ItemFilterMatch(false, false, false, false, false);

    public static final Codec<ItemFilterMatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("data", false).forGetter(ItemFilterMatch::data),
            Codec.BOOL.optionalFieldOf("damage", false).forGetter(ItemFilterMatch::damage),
            Codec.BOOL.optionalFieldOf("enchantments", false).forGetter(ItemFilterMatch::enchantments),
            Codec.BOOL.optionalFieldOf("custom_data", false).forGetter(ItemFilterMatch::customData),
            Codec.BOOL.optionalFieldOf("count", false).forGetter(ItemFilterMatch::count)
    ).apply(instance, ItemFilterMatch::new));

    /** One bit per switch, in {@link Option} order. */
    public static final StreamCodec<ByteBuf, ItemFilterMatch> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(bits -> byBits(bits & 0xFF), match -> (byte) match.bits());

    /** The switches, in the order the screen stacks them. */
    public enum Option {
        /** The master switch: whether the item's data is compared at all. */
        DATA("data"),
        DAMAGE("damage"),
        ENCHANTMENTS("enchantments"),
        CUSTOM_DATA("custom_data"),
        /**
         * Not part of comparing at all: with this on, the card's number is a level the connector
         * keeps in the inventory it touches — filling up to it when it inserts, draining down to it
         * when it extracts. Only the cards that are big enough to be worth planning around have it.
         */
        COUNT("count");

        private final String name;

        Option(String name) {
            this.name = name;
        }

        /** Translation key of the line's name; the state's own words hang off it. */
        public String translationKey() { return "gui.futuretech.filter.match." + name; }
    }

    public boolean follows(Option option) {
        return switch (option) {
            case DATA -> data;
            case DAMAGE -> damage;
            case ENCHANTMENTS -> enchantments;
            case CUSTOM_DATA -> customData;
            case COUNT -> count;
        };
    }

    public ItemFilterMatch with(Option option, boolean follow) {
        return switch (option) {
            case DATA -> new ItemFilterMatch(follow, damage, enchantments, customData, count);
            case DAMAGE -> new ItemFilterMatch(data, follow, enchantments, customData, count);
            case ENCHANTMENTS -> new ItemFilterMatch(data, damage, follow, customData, count);
            case CUSTOM_DATA -> new ItemFilterMatch(data, damage, enchantments, follow, count);
            case COUNT -> new ItemFilterMatch(data, damage, enchantments, customData, follow);
        };
    }

    public ItemFilterMatch toggle(Option option) { return with(option, !follows(option)); }

    /**
     * Whether a kind of data is left out of the comparison. Only asked while the filter is
     * following data at all, so the three below the master switch are the exceptions to it.
     */
    public boolean ignores(DataComponentType<?> type) {
        if (!damage && type == DataComponents.DAMAGE) return true;
        if (!enchantments && type == DataComponents.ENCHANTMENTS) return true;
        return !customData && type == DataComponents.CUSTOM_DATA;
    }

    private int bits() {
        int bits = 0;
        for (Option option : Option.values()) {
            if (follows(option)) bits |= 1 << option.ordinal();
        }
        return bits;
    }

    private static ItemFilterMatch byBits(int bits) {
        ItemFilterMatch match = ITEM_ONLY;
        for (Option option : Option.values()) {
            match = match.with(option, (bits & 1 << option.ordinal()) != 0);
        }
        return match;
    }
}
