package dev.futuretech.api.side;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a machine face does with energy. Ordinals travel through menu data slots, so keep the order stable. */
public enum SideMode implements StringRepresentable {
    NONE("none", false, false),
    INPUT("input", true, false),
    OUTPUT("output", false, true),
    BOTH("both", true, true);

    public static final Codec<SideMode> CODEC = StringRepresentable.fromEnum(SideMode::values);

    private final String serializedName;
    private final boolean input;
    private final boolean output;

    SideMode(String serializedName, boolean input, boolean output) {
        this.serializedName = serializedName;
        this.input = input;
        this.output = output;
    }

    public boolean allowsInput() { return input; }

    public boolean allowsOutput() { return output; }

    /** Translation key of the player-facing name. */
    public String translationKey() { return "gui.futuretech.mode." + serializedName; }

    public static SideMode byOrdinal(int ordinal) {
        SideMode[] modes = values();
        return modes[Math.clamp(ordinal, 0, modes.length - 1)];
    }

    @Override
    public String getSerializedName() { return serializedName; }
}
