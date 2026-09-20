package dev.futuretech.teleport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a teleport card points: a teleporter's place in the world, the name it had when the card
 * was written, and the colour the pad's beam takes while this card is the destination. The name
 * is a copy, so a card keeps reading right even while the teleporter it names is far away and
 * unloaded; the colour is only ever looks, and starts as the pad's own cyan.
 */
public record TeleportTarget(GlobalPos pos, String name, int colour) {
    /** The cyan of a lit pad's top: what a card is until someone picks another colour. */
    public static final int DEFAULT_COLOUR = 0x55E7ED;

    public static final Codec<TeleportTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(TeleportTarget::pos),
            Codec.STRING.fieldOf("name").forGetter(TeleportTarget::name),
            Codec.INT.optionalFieldOf("colour", DEFAULT_COLOUR).forGetter(TeleportTarget::colour)
    ).apply(instance, TeleportTarget::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportTarget> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, TeleportTarget::pos,
            ByteBufCodecs.STRING_UTF8, TeleportTarget::name,
            ByteBufCodecs.INT, TeleportTarget::colour,
            TeleportTarget::new);

    /** A freshly written card: the pad's colour until the player picks one. */
    public TeleportTarget(GlobalPos pos, String name) { this(pos, name, DEFAULT_COLOUR); }

    public ResourceKey<Level> dimension() { return pos.dimension(); }

    /** The dimension as the player reads it: its own name for the vanilla three, the id's path otherwise. */
    public Component dimensionName() {
        String path = pos.dimension().identifier().getPath();
        return switch (path) {
            case "overworld", "the_nether", "the_end" -> Component.translatable("gui.futuretech.dimension." + path);
            default -> Component.literal(path);
        };
    }
}
