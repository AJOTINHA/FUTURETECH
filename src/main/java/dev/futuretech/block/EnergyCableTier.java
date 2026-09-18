package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Cable sizes. Every tier shares the same block, block entity, item and network code;
 * only the throughput differs, so adding a tier is one constant plus one registered block.
 * Each MK doubles the one before, the way the batteries do, and a network of mixed tiers
 * runs at its slowest. The core wears the MK's colour, white to yellow to red to cyan, like
 * the lines of the battery; a tier is reached by crafting eight cables of the one below
 * around that MK's upgrade kit.
 */
public enum EnergyCableTier implements StringRepresentable {
    MK1("mk1", 400),
    MK2("mk2", 800),
    MK3("mk3", 1_600),
    MK4("mk4", 3_200);

    public static final Codec<EnergyCableTier> CODEC = StringRepresentable.fromEnum(EnergyCableTier::values);

    private final String serializedName;
    private final int throughput;

    EnergyCableTier(String serializedName, int throughput) {
        this.serializedName = serializedName;
        this.throughput = throughput;
    }

    /** Registry name of this tier's block and item, e.g. {@code energy_cable_mk1}. */
    public String blockName() { return "energy_cable_" + serializedName; }

    /** Maximum energy a network of this tier moves per tick, in total. */
    public int throughput() { return throughput; }

    @Override
    public String getSerializedName() { return serializedName; }
}
