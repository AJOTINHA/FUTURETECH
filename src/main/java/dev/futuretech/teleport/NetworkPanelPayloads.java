package dev.futuretech.teleport;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.NetworkPanelMenu;
import dev.futuretech.menu.TeleporterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The packets a network panel needs. What it shows is about blocks the player is not standing
 * next to, so none of it fits in a container's slots or a menu's data: the list goes down whole,
 * a pick comes back naming the card it means — the block it is kept in and the slot there — and
 * a card's new name and colour come back the same way.
 */
public final class NetworkPanelPayloads {
    private NetworkPanelPayloads() {}

    /** Server to client: the pad this panel reaches and its destinations, as they stand now. */
    public record View(PanelView view) implements CustomPacketPayload {
        public static final Type<View> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "network_panel_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC =
                PanelView.STREAM_CODEC.map(View::new, View::view);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Client to server: send the panel's pad to the card in this slot of this block. The pad is
     * the panel's own walk down the cables, never the packet's word, and the block is checked to
     * be that pad or a storage on the same cables.
     */
    public record Pick(BlockPos source, int slot) implements CustomPacketPayload {
        public static final Type<Pick> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "network_panel_pick"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Pick::source,
                ByteBufCodecs.VAR_INT, Pick::slot,
                Pick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Client to server: the player gave the card in this slot of this block a new name and beam
     * colour. Checked the way a pick is: the block has to be the panel's pad or a storage on the
     * same cables.
     */
    public record Edit(BlockPos source, int slot, String name, int colour) implements CustomPacketPayload {
        public static final Type<Edit> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "network_panel_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Edit::source,
                ByteBufCodecs.VAR_INT, Edit::slot,
                ByteBufCodecs.stringUtf8(TeleporterMenu.NAME_LENGTH), Edit::name,
                ByteBufCodecs.INT, Edit::colour,
                Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(View.TYPE, View.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof NetworkPanelMenu menu) menu.setView(payload.view());
        });
        // Only the panel the sender has open is touched, so the packet cannot reach across the world.
        registrar.playToServer(Pick.TYPE, Pick.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof NetworkPanelMenu menu) menu.pick(payload.source(), payload.slot());
        });
        registrar.playToServer(Edit.TYPE, Edit.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof NetworkPanelMenu menu) {
                menu.edit(payload.source(), payload.slot(), payload.name(), payload.colour());
            }
        });
    }
}
