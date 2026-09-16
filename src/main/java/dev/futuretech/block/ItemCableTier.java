package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Item cable sizes. Every tier shares the same block, block entity, item and network code;
 * only the rate differs, so adding a tier is one constant plus one registered block.
 * Keep the constants ordered from slowest to fastest: a network runs at its slowest tier.
 */
public enum ItemCableTier implements StringRepresentable {
    /**
     * The opaque item cable: one item a second, the pace basic pipes usually have; a hopper does
     * one every eight ticks.
     */
    OPAQUE("opaque", "item_cable_opaque", 1, 20, false),
    /** The plain item cable: the same cage left open, no core inside; same pace. Items show through it. */
    STANDARD("standard", "item_cable", 1, 20, true);

    public static final Codec<ItemCableTier> CODEC = StringRepresentable.fromEnum(ItemCableTier::values);

    private final String serializedName;
    private final String blockName;
    private final int batch;
    private final int interval;
    private final boolean showsItems;

    ItemCableTier(String serializedName, String blockName, int batch, int interval, boolean showsItems) {
        this.serializedName = serializedName;
        this.blockName = blockName;
        this.batch = batch;
        this.interval = interval;
        this.showsItems = showsItems;
    }

    /** Registry name of this tier's block and item, e.g. {@code item_cable}. */
    public String blockName() { return blockName; }

    /** Items a network of this tier moves per {@link #interval()}, in total. */
    public int batch() { return batch; }

    /** Ticks between one batch and the next. */
    public int interval() { return interval; }

    /** Whether the items passing through are drawn; an opaque cable hides them and skips the work. */
    public boolean showsItems() { return showsItems; }

    @Override
    public String getSerializedName() { return serializedName; }
}
