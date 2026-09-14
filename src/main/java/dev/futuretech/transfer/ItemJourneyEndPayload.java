package dev.futuretech.transfer;

import dev.futuretech.FutureTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A travelling item reached its destination, or was dropped: stop drawing it. */
public record ItemJourneyEndPayload(long id) implements CustomPacketPayload {
    public static final Type<ItemJourneyEndPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "item_journey_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemJourneyEndPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(ItemJourneyEndPayload::new, ItemJourneyEndPayload::id).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
