package dev.futuretech.redstone;

import dev.futuretech.block.WirelessRedstoneBlock.Kind;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which transmitters and receivers are on which frequency, server-wide. A plate signs in when its
 * chunk loads and out when it unloads or it is broken, so these lists only ever hold plates that
 * are there to answer. What a frequency carries, though, is not read off the lists but off the
 * {@link WirelessEther}: every transmitter's last signal, saved, so a transmitter in an unloaded
 * chunk goes on being heard until it is broken. That is what lets the frequency cross dimensions
 * — a signal put on in the overworld comes out in the nether, whose chunks are the only ones
 * loaded while anyone is there — which is the whole point of the thing.
 *
 * <p>The lists rebuild themselves as the world loads; the ether is the world's. Every change goes
 * out at once rather than being polled; a signal that did not change tells nobody, which is also
 * what stops two plates wired into each other from going round for ever.
 */
public final class WirelessRedstone {
    private static final Map<Integer, Set<WirelessRedstoneBlockEntity>> TRANSMITTERS = new HashMap<>();
    private static final Map<Integer, Set<WirelessRedstoneBlockEntity>> RECEIVERS = new HashMap<>();

    private WirelessRedstone() {}

    private static Map<Integer, Set<WirelessRedstoneBlockEntity>> lists(WirelessRedstoneBlockEntity plate) {
        return plate.kind() == Kind.TRANSMITTER ? TRANSMITTERS : RECEIVERS;
    }

    /**
     * Signs a plate in: a transmitter writes its signal into the ether and the frequency hears at
     * once, a receiver takes what is on it.
     */
    public static void join(WirelessRedstoneBlockEntity plate) {
        MinecraftServer server = serverOf(plate);
        if (server == null) return;
        lists(plate).computeIfAbsent(plate.frequency(), key -> new LinkedHashSet<>()).add(plate);
        if (plate.kind() == Kind.TRANSMITTER) send(server, plate);
        else plate.receive(strength(server, plate.frequency()));
    }

    /** A transmitter's signal, as it now reads it, into the ether; the frequency hears when that is news. */
    public static void send(MinecraftServer server, WirelessRedstoneBlockEntity transmitter) {
        if (WirelessEther.of(server).put(transmitter.globalPos(), transmitter.frequency(), transmitter.power())) {
            broadcast(server, transmitter.frequency());
        }
    }

    /**
     * Signs a plate out of the lists, as its chunk unloads or it is broken. A transmitter's signal
     * stays in the ether: a chunk going away is not the plate going away. {@link #forget} is for
     * a plate that is.
     */
    public static void leave(WirelessRedstoneBlockEntity plate) {
        Map<Integer, Set<WirelessRedstoneBlockEntity>> lists = lists(plate);
        Set<WirelessRedstoneBlockEntity> members = lists.get(plate.frequency());
        if (members == null) return;
        members.remove(plate);
        if (members.isEmpty()) lists.remove(plate.frequency());
    }

    /** Takes the transmitter that stood at {@code pos} out of the ether, and tells its frequency it is gone. */
    public static void forget(MinecraftServer server, GlobalPos pos) {
        WirelessEther.Entry entry = WirelessEther.of(server).remove(pos);
        if (entry != null) broadcast(server, entry.frequency());
    }

    /** The strongest signal any transmitter, loaded or not, last put on {@code frequency}, 0 to 15. */
    public static int strength(MinecraftServer server, int frequency) {
        return WirelessEther.of(server).strength(frequency);
    }

    /** Hands every loaded receiver on {@code frequency} what the transmitters on it now add up to. */
    public static void broadcast(MinecraftServer server, int frequency) {
        int signal = strength(server, frequency);
        for (WirelessRedstoneBlockEntity receiver : listening(RECEIVERS, frequency)) receiver.receive(signal);
    }

    private static @Nullable MinecraftServer serverOf(WirelessRedstoneBlockEntity plate) {
        return plate.getLevel() instanceof ServerLevel level ? level.getServer() : null;
    }

    /** How many plates of both kinds are on {@code frequency} right now: the loaded ones, which are the ones that answer. */
    public static int count(int frequency) {
        return listening(TRANSMITTERS, frequency).size() + listening(RECEIVERS, frequency).size();
    }

    /**
     * The plates of one list that are there to answer, copied out: a plate removed without signing
     * out is skipped rather than trusted, and the copy survives the walk changing the lists — which
     * it does, since a receiver being handed a signal can make a neighbour break another plate.
     */
    private static List<WirelessRedstoneBlockEntity> listening(Map<Integer, Set<WirelessRedstoneBlockEntity>> lists, int frequency) {
        Set<WirelessRedstoneBlockEntity> members = lists.get(frequency);
        if (members == null) return List.of();
        List<WirelessRedstoneBlockEntity> found = new ArrayList<>(members.size());
        for (WirelessRedstoneBlockEntity member : members) {
            if (!member.isRemoved() && member.getLevel() != null) found.add(member);
        }
        return found;
    }

    /** A stopped server leaves nothing behind for the next world to find. */
    public static void onServerStopped(ServerStoppedEvent event) {
        TRANSMITTERS.clear();
        RECEIVERS.clear();
    }
}
