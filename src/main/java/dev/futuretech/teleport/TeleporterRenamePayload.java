package dev.futuretech.teleport;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.TeleporterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The player typed a new name for the teleporter whose screen they have open. Menu buttons only
 * carry numbers, so the name travels on its own; the server takes it for the menu the sender has
 * open and nothing else, which is what keeps it honest.
 */
public record TeleporterRenamePayload(String name) implements CustomPacketPayload {
    public static final Type<TeleporterRenamePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "teleporter_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleporterRenamePayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(TeleporterMenu.NAME_LENGTH).map(TeleporterRenamePayload::new, TeleporterRenamePayload::name).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof TeleporterMenu menu) menu.rename(payload.name());
        });
    }
}
