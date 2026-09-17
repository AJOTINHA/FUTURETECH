package dev.futuretech.teleport;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.StorageCardsMenu;
import dev.futuretech.menu.TeleporterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The player gave a card in the storage whose screen they have open a new name and beam colour.
 * Menu buttons only carry numbers, so it travels on its own; the server takes it for the menu the
 * sender has open and nothing else, which is what keeps it honest.
 */
public record StorageCardsEditPayload(int slot, String name, int colour) implements CustomPacketPayload {
    public static final Type<StorageCardsEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "storage_cards_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageCardsEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageCardsEditPayload::slot,
            ByteBufCodecs.stringUtf8(TeleporterMenu.NAME_LENGTH), StorageCardsEditPayload::name,
            ByteBufCodecs.INT, StorageCardsEditPayload::colour,
            StorageCardsEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof StorageCardsMenu menu) {
                menu.edit(payload.slot(), payload.name(), payload.colour());
            }
        });
    }
}
