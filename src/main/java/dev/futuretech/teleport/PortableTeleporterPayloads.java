package dev.futuretech.teleport;

import dev.futuretech.FutureTech;
import dev.futuretech.item.PortableTeleporterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * The packets of the portable teleporter. Its list is read through a panel that may be in
 * another dimension, so there is no menu under the screen: the server sends the destinations,
 * priced for where the player stands, and the pick comes back naming the card — the block it
 * is kept in and the slot there — with the hand the teleporter is in.
 */
public final class PortableTeleporterPayloads {
    private PortableTeleporterPayloads() {}

    /** Server to client: open the screen with these destinations and this much in the buffer. */
    public record Open(InteractionHand hand, List<PanelView.Card> cards, int energy) implements CustomPacketPayload {
        public static final Type<Open> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "portable_teleporter_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL.map(off -> off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                        hand -> hand == InteractionHand.OFF_HAND), Open::hand,
                PanelView.Card.STREAM_CODEC.apply(ByteBufCodecs.list(PanelView.MAX_CARDS)), Open::cards,
                ByteBufCodecs.VAR_INT, Open::energy,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: go to the card in this slot of this block, with the teleporter in this hand. */
    public record Go(InteractionHand hand, BlockPos source, int slot) implements CustomPacketPayload {
        public static final Type<Go> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "portable_teleporter_go"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Go> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL.map(off -> off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                        hand -> hand == InteractionHand.OFF_HAND), Go::hand,
                BlockPos.STREAM_CODEC, Go::source,
                ByteBufCodecs.VAR_INT, Go::slot,
                Go::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void open(ServerPlayer player, InteractionHand hand, List<PanelView.Card> cards, int energy) {
        PacketDistributor.sendToPlayer(player, new Open(hand, cards, energy));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) ->
                dev.futuretech.client.PortableTeleporterScreen.open(payload));
        // The teleporter has to be in the hand the packet names: a packet cannot travel on an item the player put away.
        registrar.playToServer(Go.TYPE, Go.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PortableTeleporterItem.go(player, payload.hand(), payload.source(), payload.slot());
            }
        });
    }
}
