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
 * Where a teleport card points: a teleporter's place in the world and the name it had when the
 * card was written. The name is a copy, so a card keeps reading right even while the teleporter
 * it names is far away and unloaded.
 */
public record TeleportTarget(GlobalPos pos, String name) {
    public static final Codec<TeleportTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(TeleportTarget::pos),
            Codec.STRING.fieldOf("name").forGetter(TeleportTarget::name)
    ).apply(instance, TeleportTarget::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportTarget> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, TeleportTarget::pos,
            ByteBufCodecs.STRING_UTF8, TeleportTarget::name,
            TeleportTarget::new);

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
