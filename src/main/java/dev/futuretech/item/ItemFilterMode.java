package dev.futuretech.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** How an item filter reads its list. Ordinals travel through menu data slots, so keep the order stable. */
public enum ItemFilterMode implements StringRepresentable {
    /** Only the listed items pass; an empty list passes nothing. */
    WHITELIST("whitelist"),
    /** Everything but the listed items passes; an empty list passes everything. */
    BLACKLIST("blacklist");

    public static final Codec<ItemFilterMode> CODEC = StringRepresentable.fromEnum(ItemFilterMode::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilterMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ItemFilterMode::byOrdinal, ItemFilterMode::ordinal).cast();

    private final String serializedName;

    ItemFilterMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /** Translation key of the player-facing name. */
    public String translationKey() { return "gui.futuretech.filter." + serializedName; }

    public ItemFilterMode other() { return this == WHITELIST ? BLACKLIST : WHITELIST; }

    public static ItemFilterMode byOrdinal(int ordinal) {
        ItemFilterMode[] modes = values();
        return modes[Math.clamp(ordinal, 0, modes.length - 1)];
    }

    @Override
    public String getSerializedName() { return serializedName; }
}
