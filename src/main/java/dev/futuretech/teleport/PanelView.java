package dev.futuretech.teleport;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.item.TeleportCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a network panel shows: the pad on its cables and the cards in it, the pad's own screen seen
 * from somewhere else. The panel is looking at a teleporter it is not standing next to, so none of
 * this can be read off a container — the server works it out and ships it, already answered,
 * because what a card costs and whether it can be reached depend on the pad holding it.
 */
public record PanelView(Optional<Pad> pad, int cables) {
    public static final PanelView EMPTY = new PanelView(Optional.empty(), 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Pad.STREAM_CODEC), PanelView::pad,
            ByteBufCodecs.VAR_INT, PanelView::cables,
            PanelView::new);

    /** Whether any network cable is touching the panel at all; with none, there is nothing to list. */
    public boolean unplugged() { return cables == 0; }

    /** The panel's pad, or null while the cables reach none. */
    public @Nullable Pad padOrNull() { return pad.orElse(null); }

    /** The teleporter the panel is wired to, with what its screen would show. */
    public record Pad(BlockPos pos, String name, int mk, int selected, List<Card> cards) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Pad> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Pad::pos,
                ByteBufCodecs.STRING_UTF8, Pad::name,
                ByteBufCodecs.VAR_INT, Pad::mk,
                ByteBufCodecs.VAR_INT, Pad::selected,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list(TeleporterBlockEntity.MAX_CARDS)), Pad::cards,
                Pad::new);
    }

    /**
     * One written card of the pad, with the slot it sits in. Empty slots are not sent: the panel
     * lists destinations, not slots, so a row is a place to go and its slot is what a click names.
     */
    public record Card(int slot, String name, int colour, int cost, boolean reaches) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Card::slot,
                ByteBufCodecs.STRING_UTF8, Card::name,
                ByteBufCodecs.INT, Card::colour,
                ByteBufCodecs.VAR_INT, Card::cost,
                ByteBufCodecs.BOOL, Card::reaches,
                Card::new);
    }

    /** Reads the pad as the panel should show it: its name, its level and the cards it holds. */
    public static Pad of(BlockPos pos, TeleporterBlockEntity pad) {
        int mk = MachineLevel.of(pad.getBlockState());
        int slots = pad.unlockedCards();
        List<Card> cards = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            TeleportTarget target = TeleportCardItem.target(pad.getItem(slot));
            if (target == null) continue;
            // The cost and the reach are the pad's, not the panel's: the trip starts where the
            // player will be standing, which is the pad, however far the panel is from it.
            cards.add(new Card(slot, target.name(), target.colour(),
                    TeleporterBlockEntity.cost(pad.globalPos(), target, mk),
                    TeleporterBlockEntity.reaches(pad.globalPos(), target, mk)));
        }
        return new Pad(pos, pad.displayName(), mk, pad.selected(), cards);
    }
}
