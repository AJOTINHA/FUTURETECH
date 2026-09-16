package dev.futuretech.transfer;

import dev.futuretech.FutureTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Where an item travelling through item cables is and where it is going, sent to clients so they
 * can draw it. Sent when the item sets off and again whenever its route or state changes; the
 * same {@code id} replaces the earlier picture.
 *
 * @param path          the cables crossed, entry cable first
 * @param from          the face of the first cable the item came in through
 * @param to            the face of the last cable it leaves through
 * @param ticksPerBlock how long the item takes to cross one cable
 * @param travelled     ticks already spent on the trip when this was sent
 * @param waiting       true when it sits at the exit face because the destination is full
 */
public record ItemJourneyPayload(long id, List<BlockPos> path, Direction from, Direction to, ItemStack stack,
                                 int ticksPerBlock, int travelled, boolean waiting) implements CustomPacketPayload {
    public static final Type<ItemJourneyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "item_journey"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemJourneyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ItemJourneyPayload::id,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ItemJourneyPayload::path,
            Direction.STREAM_CODEC, ItemJourneyPayload::from,
            Direction.STREAM_CODEC, ItemJourneyPayload::to,
            ItemStack.STREAM_CODEC, ItemJourneyPayload::stack,
            ByteBufCodecs.VAR_INT, ItemJourneyPayload::ticksPerBlock,
            ByteBufCodecs.VAR_INT, ItemJourneyPayload::travelled,
            ByteBufCodecs.BOOL, ItemJourneyPayload::waiting,
            ItemJourneyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
