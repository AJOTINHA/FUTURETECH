package dev.futuretech.transfer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Registers the journey payloads and hands what arrives to whoever draws it. The client installs
 * its drawer at start-up; a dedicated server has none and the payloads are never handled there
 * anyway, so this class stays free of client code.
 */
public final class ItemJourneys {
    private static @Nullable Consumer<ItemJourneyPayload> drawer;
    private static @Nullable LongConsumer eraser;

    private ItemJourneys() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(ItemJourneyPayload.TYPE, ItemJourneyPayload.STREAM_CODEC, (payload, context) -> {
                    if (drawer != null) drawer.accept(payload);
                })
                .playToClient(ItemJourneyEndPayload.TYPE, ItemJourneyEndPayload.STREAM_CODEC, (payload, context) -> {
                    if (eraser != null) eraser.accept(payload.id());
                });
    }

    /** Client only: where journeys go once they arrive, and who forgets them when they end. */
    public static void setDrawer(Consumer<ItemJourneyPayload> drawer, LongConsumer eraser) {
        ItemJourneys.drawer = drawer;
        ItemJourneys.eraser = eraser;
    }
}
