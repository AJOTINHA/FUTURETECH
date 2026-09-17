package dev.futuretech.transfer;

import dev.futuretech.FutureTech;
import dev.futuretech.block.entity.TesseractBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * The packets a tesseract's screen needs. There is no menu under that screen — the block has no
 * slots and nothing to sync — so the server opens it with what it shows, hears what the player
 * typed, chose, made or deleted, and tells every screen when the world's list of channels changed.
 */
public final class TesseractPayloads {
    private static final StreamCodec<RegistryFriendlyByteBuf, String> NAME =
            ByteBufCodecs.stringUtf8(TesseractBlockEntity.CHANNEL_LENGTH).cast();
    /** Enough channels for any sane world, and a cap the packet cannot be pushed past. */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> NAMES = NAME.apply(ByteBufCodecs.list(1024));

    private TesseractPayloads() {}

    /** Server to client: open the screen on this tesseract, with its name, its channel and the world's list. */
    public record Open(BlockPos pos, String name, String channel, List<String> channels) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos,
                NAME, Open::name,
                NAME, Open::channel,
                NAMES, Open::channels,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: the world's list of channels, as it stands now; a screen that is open takes it. */
    public record Channels(List<String> channels) implements CustomPacketPayload {
        public static final Type<Channels> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_channels"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Channels> STREAM_CODEC = NAMES.map(Channels::new, Channels::channels);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: how many tesseracts are on a channel the player wants gone, for the screen to ask with. */
    public record Members(String channel, int count) implements CustomPacketPayload {
        public static final Type<Members> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_members"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Members> STREAM_CODEC = StreamCodec.composite(
                NAME, Members::channel,
                ByteBufCodecs.VAR_INT, Members::count,
                Members::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: the player typed a name for this tesseract. Checked like a block click. */
    public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rename::pos,
                NAME, Rename::name,
                Rename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: make a channel with this name. Everyone hears the list it joins. */
    public record Create(String name) implements CustomPacketPayload {
        public static final Type<Create> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Create> STREAM_CODEC = NAME.map(Create::new, Create::name);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Client to server: put this tesseract on this channel, or on none with an empty name. The
     * server checks what it would for any block click — the tesseract is there and the player is
     * close enough to have clicked it — and that the channel is one the world has.
     */
    public record Choose(BlockPos pos, String channel) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choose::pos,
                NAME, Choose::channel,
                Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: how many tesseracts are on this channel? The answer is a {@link Members}. */
    public record Ask(String channel) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_ask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ask> STREAM_CODEC = NAME.map(Ask::new, Ask::channel);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: delete this channel; every tesseract on it comes off it. Everyone hears the list. */
    public record Delete(String channel) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> STREAM_CODEC = NAME.map(Delete::new, Delete::channel);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Opens the screen for {@code player} on the tesseract at {@code pos}. */
    public static void open(ServerPlayer player, TesseractBlockEntity tesseract) {
        var book = TesseractChannelBook.of(player.level().getServer());
        PacketDistributor.sendToPlayer(player, new Open(tesseract.getBlockPos(), tesseract.name(), tesseract.channel(), book.channels()));
    }

    /** The tesseract at {@code pos} if the player could have clicked it, else null. */
    private static TesseractBlockEntity clicked(ServerPlayer player, BlockPos pos) {
        if (!player.isWithinBlockInteractionRange(pos, 1.0)) return null;
        return player.level().getBlockEntity(pos) instanceof TesseractBlockEntity tesseract ? tesseract : null;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) ->
                dev.futuretech.client.TesseractScreen.open(payload));
        registrar.playToClient(Channels.TYPE, Channels.STREAM_CODEC, (payload, context) ->
                dev.futuretech.client.TesseractScreen.refresh(payload.channels()));
        registrar.playToClient(Members.TYPE, Members.STREAM_CODEC, (payload, context) ->
                dev.futuretech.client.TesseractScreen.confirmDelete(payload.channel(), payload.count()));
        registrar.playToServer(Rename.TYPE, Rename.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var tesseract = clicked(player, payload.pos());
            if (tesseract != null) tesseract.setName(payload.name());
        });
        registrar.playToServer(Create.TYPE, Create.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var book = TesseractChannelBook.of(player.level().getServer());
            if (book.create(payload.name())) PacketDistributor.sendToAllPlayers(new Channels(book.channels()));
        });
        registrar.playToServer(Choose.TYPE, Choose.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!payload.channel().isEmpty() && !TesseractChannelBook.of(player.level().getServer()).has(payload.channel())) return;
            var tesseract = clicked(player, payload.pos());
            if (tesseract != null) tesseract.setChannel(payload.channel());
        });
        registrar.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(player, new Members(payload.channel(), TesseractChannels.count(payload.channel())));
        });
        registrar.playToServer(Delete.TYPE, Delete.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var book = TesseractChannelBook.of(player.level().getServer());
            if (!book.delete(payload.channel())) return;
            // Off the channel before the list goes out, so a screen that hears it sees the tesseract off too.
            for (TesseractBlockEntity member : TesseractChannels.members(payload.channel())) member.setChannel("");
            PacketDistributor.sendToAllPlayers(new Channels(book.channels()));
        });
    }
}
