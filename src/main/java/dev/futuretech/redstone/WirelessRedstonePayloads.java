package dev.futuretech.redstone;

import dev.futuretech.FutureTech;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The two packets the wireless plates need. There is no menu under their screen — neither plate
 * has a slot or anything to sync — so the server opens it with the frequency the plate is on and
 * hears back the one the player typed.
 */
public final class WirelessRedstonePayloads {
    private WirelessRedstonePayloads() {}

    /** Server to client: open the screen on the plate at {@code pos}, showing the frequency it is on. */
    public record Open(BlockPos pos, int frequency) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "wireless_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos,
                ByteBufCodecs.VAR_INT, Open::frequency,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: put the plate at {@code pos} on this frequency. */
    public record Frequency(BlockPos pos, int frequency) implements CustomPacketPayload {
        public static final Type<Frequency> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "wireless_frequency"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Frequency> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Frequency::pos,
                ByteBufCodecs.VAR_INT, Frequency::frequency,
                Frequency::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Opens the screen for {@code player} on the plate they clicked. */
    public static void open(ServerPlayer player, WirelessRedstoneBlockEntity plate) {
        PacketDistributor.sendToPlayer(player, new Open(plate.getBlockPos(), plate.frequency()));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) ->
                dev.futuretech.client.WirelessRedstoneScreen.open(payload));
        registrar.playToServer(Frequency.TYPE, Frequency.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // The same reach the click needed: a packet cannot set a plate the player could not have opened.
            if (!player.isWithinBlockInteractionRange(payload.pos(), 1.0)) return;
            if (player.level().getBlockEntity(payload.pos()) instanceof WirelessRedstoneBlockEntity plate) {
                plate.setFrequency(payload.frequency());
            }
        });
    }
}
