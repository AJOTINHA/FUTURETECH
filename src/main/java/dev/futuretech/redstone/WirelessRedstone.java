package dev.futuretech.redstone;

import dev.futuretech.block.WirelessRedstoneBlock.Kind;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which transmitters and receivers are on which frequency, server-wide. A plate signs in when its
 * chunk loads and out when it unloads or it is broken, so a frequency only ever lists plates that
 * are there to answer: a transmitter in an unloaded chunk sends nothing, the way a machine in one
 * does nothing. The frequency crosses dimensions — the whole point of the thing is that the two
 * plates never have to see each other.
 *
 * <p>Nothing is saved: the frequency a plate is on is the plate's own to save, and these lists
 * rebuild themselves as the world loads. Every change goes out at once rather than being polled;
 * a signal that did not change tells nobody, which is also what stops two plates wired into each
 * other from going round for ever.
 */
public final class WirelessRedstone {
    private static final Map<Integer, Set<WirelessRedstoneBlockEntity>> TRANSMITTERS = new HashMap<>();
    private static final Map<Integer, Set<WirelessRedstoneBlockEntity>> RECEIVERS = new HashMap<>();

    private WirelessRedstone() {}

    private static Map<Integer, Set<WirelessRedstoneBlockEntity>> lists(WirelessRedstoneBlockEntity plate) {
        return plate.kind() == Kind.TRANSMITTER ? TRANSMITTERS : RECEIVERS;
    }

    /** Signs a plate in: a transmitter's signal reaches the frequency at once, a receiver takes what is on it. */
    public static void join(WirelessRedstoneBlockEntity plate) {
        lists(plate).computeIfAbsent(plate.frequency(), key -> new LinkedHashSet<>()).add(plate);
        if (plate.kind() == Kind.TRANSMITTER) broadcast(plate.frequency());
        else plate.receive(strength(plate.frequency()));
    }

    /** Signs a plate out; a transmitter that leaves takes its signal off the frequency with it. */
    public static void leave(WirelessRedstoneBlockEntity plate) {
        Map<Integer, Set<WirelessRedstoneBlockEntity>> lists = lists(plate);
        Set<WirelessRedstoneBlockEntity> members = lists.get(plate.frequency());
        if (members == null) return;
        members.remove(plate);
        if (members.isEmpty()) lists.remove(plate.frequency());
        if (plate.kind() == Kind.TRANSMITTER) broadcast(plate.frequency());
    }

    /** The strongest signal any loaded transmitter puts on {@code frequency}, 0 to 15. */
    public static int strength(int frequency) {
        int best = 0;
        for (WirelessRedstoneBlockEntity transmitter : listening(TRANSMITTERS, frequency)) {
            best = Math.max(best, transmitter.power());
            if (best >= 15) return 15;
        }
        return best;
    }

    /** Hands every receiver on {@code frequency} what the transmitters on it now add up to. */
    public static void broadcast(int frequency) {
        int signal = strength(frequency);
        for (WirelessRedstoneBlockEntity receiver : listening(RECEIVERS, frequency)) receiver.receive(signal);
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
