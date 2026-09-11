package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Battery sizes. Every tier shares the same block, block entity, item, menu and screen;
 * only these numbers differ, so adding a tier is one constant plus one registered block.
 */
public enum BatteryTier implements StringRepresentable {
    MK1("mk1", 100_000, 200);

    public static final Codec<BatteryTier> CODEC = StringRepresentable.fromEnum(BatteryTier::values);

    private final String serializedName;
    private final int capacity;
    private final int transferPerTick;

    BatteryTier(String serializedName, int capacity, int transferPerTick) {
        this.serializedName = serializedName;
        this.capacity = capacity;
        this.transferPerTick = transferPerTick;
    }

    /** Registry name of this tier's block and item, e.g. {@code battery_mk1}. */
    public String blockName() { return "battery_" + serializedName; }

    public int capacity() { return capacity; }

    /** Maximum energy accepted and handed out per tick, each. */
    public int transferPerTick() { return transferPerTick; }

    public static BatteryTier byOrdinal(int ordinal) {
        BatteryTier[] tiers = values();
        return tiers[Math.clamp(ordinal, 0, tiers.length - 1)];
    }

    @Override
    public String getSerializedName() { return serializedName; }
}
