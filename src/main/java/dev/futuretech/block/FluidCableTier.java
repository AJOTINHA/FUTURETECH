package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Fluid cable sizes. Every tier shares the same block, block entity, item and network code; only
 * the throughput differs, so adding a tier is one constant plus one registered block. Keep the
 * constants ordered from slowest to fastest: a network runs at its slowest tier.
 */
public enum FluidCableTier implements StringRepresentable {
    /** Green core; the fluid inside stays hidden. */
    OPAQUE("opaque", "fluid_cable_opaque", 500, false),
    /** Glass core: the fluid passing through shows. Same pace. */
    STANDARD("standard", "fluid_cable", 500, true);

    public static final Codec<FluidCableTier> CODEC = StringRepresentable.fromEnum(FluidCableTier::values);

    private final String serializedName;
    private final String blockName;
    private final int throughput;
    private final boolean showsFluid;

    FluidCableTier(String serializedName, String blockName, int throughput, boolean showsFluid) {
        this.serializedName = serializedName;
        this.blockName = blockName;
        this.throughput = throughput;
        this.showsFluid = showsFluid;
    }

    /** Registry name of this tier's block and item, e.g. {@code fluid_cable}. */
    public String blockName() { return blockName; }

    /** Millibuckets each line of a network of this tier moves per tick. */
    public int throughput() { return throughput; }

    /** Whether the fluid passing through is drawn inside the cable. */
    public boolean showsFluid() { return showsFluid; }

    @Override
    public String getSerializedName() { return serializedName; }
}
