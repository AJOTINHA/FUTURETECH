package dev.futuretech.teleport;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.StorageCardsBlockEntity;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.item.TeleportCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a network panel shows: the pad on its cables and every destination that pad can send to —
 * the card in the pad's own slot, then the cards in each storage on the same cables. The panel is
 * looking at blocks it is not standing next to, so none of this can be read off a container —
 * the server works it out and ships it, already answered, because what a card costs and whether
 * it can be reached depend on the pad, not on where the card is kept.
 */
public record PanelView(Optional<Pad> pad, int cables) {
    public static final PanelView EMPTY = new PanelView(Optional.empty(), 0);
    /** Enough cards for any sane network, and a cap the packet cannot be pushed past. */
    public static final int MAX_CARDS = 512;

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Pad.STREAM_CODEC), PanelView::pad,
            ByteBufCodecs.VAR_INT, PanelView::cables,
            PanelView::new);

    /** Whether any network cable is touching the panel at all; with none, there is nothing to list. */
    public boolean unplugged() { return cables == 0; }

    /** The panel's pad, or null while the cables reach none. */
    public @Nullable Pad padOrNull() { return pad.orElse(null); }

    /**
     * The teleporter the panel is wired to, with its destinations. The chosen one is named the
     * way the cards are: by the block it is in and the slot there, the pad itself for its own.
     */
    public record Pad(BlockPos pos, String name, int mk, BlockPos chosenSource, int chosenSlot, List<Card> cards) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Pad> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Pad::pos,
                ByteBufCodecs.STRING_UTF8, Pad::name,
                ByteBufCodecs.VAR_INT, Pad::mk,
                BlockPos.STREAM_CODEC, Pad::chosenSource,
                ByteBufCodecs.VAR_INT, Pad::chosenSlot,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), Pad::cards,
                Pad::new);

        /** Whether {@code card} is the one the pad is sending to. */
        public boolean chose(Card card) { return chosenSlot == card.slot() && chosenSource.equals(card.source()); }
    }

    /**
     * One written card the pad can send to, with where it is kept: the pad itself or a storage,
     * and the slot there. Empty slots are not sent — the panel lists destinations, not slots, so
     * a row is a place to go and its source and slot are what a click names.
     */
    public record Card(BlockPos source, int slot, String name, int colour, int cost, boolean reaches) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Card::source,
                ByteBufCodecs.VAR_INT, Card::slot,
                ByteBufCodecs.STRING_UTF8, Card::name,
                ByteBufCodecs.INT, Card::colour,
                ByteBufCodecs.VAR_INT, Card::cost,
                ByteBufCodecs.BOOL, Card::reaches,
                Card::new);
    }

    /**
     * Reads the pad as the panel should show it: its name, its level, and its destinations — its
     * own card first, then the storages' in the order the walk found them.
     */
    public static Pad of(BlockPos pos, TeleporterBlockEntity pad, List<StorageCardsBlockEntity> storages) {
        int mk = MachineLevel.of(pad.getBlockState());
        GlobalPos from = pad.globalPos();
        List<Card> cards = new ArrayList<>();
        add(cards, pos, TeleporterBlockEntity.CARD_SLOT, TeleportCardItem.target(pad.getItem(TeleporterBlockEntity.CARD_SLOT)), from, mk);
        for (StorageCardsBlockEntity storage : storages) {
            for (int slot = 0; slot < storage.unlockedCards() && cards.size() < MAX_CARDS; slot++) {
                add(cards, storage.getBlockPos(), slot, storage.target(slot), from, mk);
            }
        }
        BlockPos chosenSource = pad.selectedSource() == null ? pos : pad.selectedSource();
        return new Pad(pos, pad.displayName(), mk, chosenSource, pad.selectedSlot(), cards);
    }

    private static void add(List<Card> cards, BlockPos source, int slot, @Nullable TeleportTarget target, GlobalPos from, int mk) {
        if (target == null) return;
        // The cost and the reach are the pad's, wherever the card is kept: the trip starts where
        // the player will be standing, which is the pad, however far the panel or the storage is.
        cards.add(new Card(source, slot, target.name(), target.colour(),
                TeleporterBlockEntity.cost(from, target, mk), TeleporterBlockEntity.reaches(from, target, mk)));
    }
}
