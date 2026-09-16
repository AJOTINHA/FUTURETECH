package dev.futuretech.perf;

import dev.futuretech.FutureTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What the server tells a player while profiling: the measured blocks around them, and the
 * dimension's total. {@code enabled} false clears the picture; it is sent once when profiling
 * stops.
 */
public record PerfSamplesPayload(boolean enabled, int totalMicros, int count, List<TickProfiler.Entry> entries)
        implements CustomPacketPayload {
    public static final Type<PerfSamplesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "perf_samples"));
    private static final StreamCodec<RegistryFriendlyByteBuf, TickProfiler.Entry> ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TickProfiler.Entry::pos,
            ByteBufCodecs.VAR_INT, TickProfiler.Entry::micros,
            ByteBufCodecs.VAR_INT, TickProfiler.Entry::cables, TickProfiler.Entry::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfSamplesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PerfSamplesPayload::enabled,
            ByteBufCodecs.VAR_INT, PerfSamplesPayload::totalMicros,
            ByteBufCodecs.VAR_INT, PerfSamplesPayload::count,
            ENTRY_CODEC.apply(ByteBufCodecs.list()), PerfSamplesPayload::entries, PerfSamplesPayload::new);

    public static final PerfSamplesPayload OFF = new PerfSamplesPayload(false, 0, 0, List.of());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
