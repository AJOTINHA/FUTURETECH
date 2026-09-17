package dev.futuretech.transfer;

import dev.futuretech.block.entity.TesseractBlockEntity;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which tesseracts are on which channel, server-wide. A tesseract signs in when its chunk loads and
 * out when it unloads or it is broken, so a channel only ever lists tesseracts that are there to
 * answer: one in an unloaded chunk is not a destination, the way it is not for anything else.
 * Nothing is saved — the channel a tesseract is on is the tesseract's own to save, and the lists
 * rebuild themselves as the world loads.
 */
public final class TesseractChannels {
    private static final Map<String, Set<TesseractBlockEntity>> CHANNELS = new HashMap<>();

    private TesseractChannels() {}

    public static void join(TesseractBlockEntity tesseract) {
        if (tesseract.channel().isEmpty()) return;
        CHANNELS.computeIfAbsent(tesseract.channel(), key -> new LinkedHashSet<>()).add(tesseract);
    }

    public static void leave(TesseractBlockEntity tesseract) {
        Set<TesseractBlockEntity> members = CHANNELS.get(tesseract.channel());
        if (members == null) return;
        members.remove(tesseract);
        if (members.isEmpty()) CHANNELS.remove(tesseract.channel());
    }

    /**
     * The other tesseracts on {@code tesseract}'s channel, in the order they signed in. A member
     * that was removed without signing out is skipped rather than trusted.
     */
    public static List<TesseractBlockEntity> peers(TesseractBlockEntity tesseract) {
        Set<TesseractBlockEntity> members = CHANNELS.get(tesseract.channel());
        if (members == null) return List.of();
        List<TesseractBlockEntity> peers = new ArrayList<>(members.size());
        for (TesseractBlockEntity member : members) {
            if (member != tesseract && !member.isRemoved() && member.getLevel() != null) peers.add(member);
        }
        return peers;
    }

    /** Every tesseract on {@code channel} that is loaded, in the order they signed in. */
    public static List<TesseractBlockEntity> members(String channel) {
        Set<TesseractBlockEntity> members = CHANNELS.get(channel);
        if (members == null) return List.of();
        List<TesseractBlockEntity> found = new ArrayList<>(members.size());
        for (TesseractBlockEntity member : members) {
            if (!member.isRemoved() && member.getLevel() != null) found.add(member);
        }
        return found;
    }

    /** How many tesseracts are on {@code channel} right now: the loaded ones, which are the ones that answer. */
    public static int count(String channel) { return members(channel).size(); }

    /** The peers standing in {@code level}: what a walk down the cables can reach without changing worlds. */
    public static List<TesseractBlockEntity> peersIn(TesseractBlockEntity tesseract, LevelReader level) {
        List<TesseractBlockEntity> peers = new ArrayList<>();
        for (TesseractBlockEntity peer : peers(tesseract)) {
            if (peer.getLevel() == level) peers.add(peer);
        }
        return peers;
    }

    /** A stopped server leaves nothing behind for the next world to find. */
    public static void onServerStopped(ServerStoppedEvent event) { CHANNELS.clear(); }
}
