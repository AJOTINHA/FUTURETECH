package dev.futuretech.api.redstone;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a machine reacts to a redstone signal. Ordinals travel through menu data slots, so keep the order stable. */
public enum RedstoneMode implements StringRepresentable {
    /** Runs regardless of redstone. */
    IGNORED("ignored"),
    /** Runs only while there is no signal. */
    LOW("low"),
    /** Runs only while there is a signal. */
    HIGH("high");

    public static final Codec<RedstoneMode> CODEC = StringRepresentable.fromEnum(RedstoneMode::values);

    private final String serializedName;

    RedstoneMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /** Whether the machine may run given the signal at its position. */
    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case LOW -> !powered;
            case HIGH -> powered;
        };
    }

    public String translationKey() { return "gui.futuretech.redstone." + serializedName; }

    public String descriptionKey() { return translationKey() + ".desc"; }

    public static RedstoneMode byOrdinal(int ordinal) {
        RedstoneMode[] modes = values();
        return modes[Math.clamp(ordinal, 0, modes.length - 1)];
    }

    @Override
    public String getSerializedName() { return serializedName; }
}
