package dev.futuretech.transfer;

import com.mojang.serialization.Codec;
import dev.futuretech.FutureTech;
import dev.futuretech.block.entity.TesseractBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The channels the players have made, world-wide: the list every tesseract's screen shows, in
 * the order they were made. It lives with the overworld's saved data, whichever dimension the
 * tesseract is in, so one list serves the whole world. A channel is only a name; which
 * tesseracts are on it is theirs to say, and {@link TesseractChannels} to keep.
 */
public final class TesseractChannelBook extends SavedData {
    public static final SavedDataType<TesseractChannelBook> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_channels"),
            TesseractChannelBook::new,
            Codec.STRING.listOf().fieldOf("channels").codec().xmap(TesseractChannelBook::new, book -> book.channels));

    private final List<String> channels;

    private TesseractChannelBook() { this(List.of()); }

    private TesseractChannelBook(List<String> channels) { this.channels = new ArrayList<>(channels); }

    /** The world's one book. */
    public static TesseractChannelBook of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The channels, oldest first. */
    public List<String> channels() { return List.copyOf(channels); }

    public boolean has(String channel) { return channels.contains(channel); }

    /** Takes a channel out of the book; false for one it did not have. The tesseracts on it are the caller's to take off. */
    public boolean delete(String channel) {
        if (!channels.remove(channel)) return false;
        setDirty();
        return true;
    }

    /**
     * Adds a channel, trimmed to what a tesseract can hold; false for a blank name or one already
     * in the book, which changes nothing.
     */
    public boolean create(String name) {
        String trimmed = name.strip();
        if (trimmed.length() > TesseractBlockEntity.CHANNEL_LENGTH) trimmed = trimmed.substring(0, TesseractBlockEntity.CHANNEL_LENGTH);
        if (trimmed.isEmpty() || channels.contains(trimmed)) return false;
        channels.add(trimmed);
        setDirty();
        return true;
    }
}
