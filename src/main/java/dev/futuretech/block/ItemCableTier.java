package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Item cable sizes. Every tier shares the same block, block entity, item and network code;
 * only the rate differs, so adding a tier is one constant plus one registered block.
 * Keep the constants ordered from slowest to fastest: a network runs at its slowest tier.
 */
public enum ItemCableTier implements StringRepresentable {
    /** One item a second, the pace basic pipes usually have; a hopper does one every eight ticks. */
    MK1("mk1", 1, 20);

    public static final Codec<ItemCableTier> CODEC = StringRepresentable.fromEnum(ItemCableTier::values);

    private final String serializedName;
    private final int batch;
    private final int interval;

    ItemCableTier(String serializedName, int batch, int interval) {
        this.serializedName = serializedName;
        this.batch = batch;
        this.interval = interval;
    }

    /** Registry name of this tier's block and item, e.g. {@code item_cable_mk1}. */
    public String blockName() { return "item_cable_" + serializedName; }

    /** Items a network of this tier moves per {@link #interval()}, in total. */
    public int batch() { return batch; }

    /** Ticks between one batch and the next. */
    public int interval() { return interval; }

    @Override
    public String getSerializedName() { return serializedName; }
}
