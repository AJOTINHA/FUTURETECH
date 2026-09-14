package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Battery levels, selected by the block's MK state and preserved on its dropped item.
 */
public enum BatteryTier implements StringRepresentable {
    MK1("mk1", 100_000, 200, 0xFFFFFF),
    MK2("mk2", 200_000, 400, 0xF2C202),
    MK3("mk3", 400_000, 800, 0xD21A1E),
    MK4("mk4", 800_000, 1_600, 0x01FDFE);

    public static final Codec<BatteryTier> CODEC = StringRepresentable.fromEnum(BatteryTier::values);

    private final String serializedName;
    private final int capacity;
    private final int transferPerTick;
    private final int lineColor;

    BatteryTier(String serializedName, int capacity, int transferPerTick, int lineColor) {
        this.serializedName = serializedName;
        this.capacity = capacity;
        this.transferPerTick = transferPerTick;
        this.lineColor = lineColor;
    }

    /** Name used by the base registration and the tier's translation key. */
    public String blockName() { return "battery_" + serializedName; }

    public int capacity() { return capacity; }

    /** Maximum energy accepted and handed out per tick, each. */
    public int transferPerTick() { return transferPerTick; }

    /** RGB sampled from the machine casing corner at (0, 0), with white for MK1. */
    public int lineColor() { return lineColor; }

    public static BatteryTier byOrdinal(int ordinal) {
        BatteryTier[] tiers = values();
        return tiers[Math.clamp(ordinal, 0, tiers.length - 1)];
    }

    @Override
    public String getSerializedName() { return serializedName; }
}
