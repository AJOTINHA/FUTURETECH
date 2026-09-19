package dev.futuretech.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.FutureTech;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What every transmitter last put on its frequency, world-wide and saved: the signal a receiver
 * reads is the strongest of these, so a transmitter goes on being heard after its chunk unloads,
 * which is what a receiver in another dimension needs, since the chunk on the far side is nearly
 * always unloaded. A transmitter writes here whenever it reads a new signal, and is taken out when
 * it is broken, never when its chunk merely goes away. It lives with the overworld's saved data,
 * whichever dimension the plates are in, the way the tesseract's channel book does.
 */
public final class WirelessEther extends SavedData {
    /** One transmitter: where it is, what it is on, and what it last read. */
    public record Entry(GlobalPos pos, int frequency, int power) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.INT.fieldOf("frequency").forGetter(Entry::frequency),
                Codec.INT.fieldOf("power").forGetter(Entry::power)
        ).apply(instance, Entry::new));
    }

    public static final SavedDataType<WirelessEther> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "wireless_ether"),
            WirelessEther::new,
            Entry.CODEC.listOf().fieldOf("transmitters").codec().xmap(WirelessEther::new, ether -> List.copyOf(ether.transmitters.values())));

    private final Map<GlobalPos, Entry> transmitters = new HashMap<>();

    private WirelessEther() {}

    private WirelessEther(List<Entry> entries) {
        for (Entry entry : entries) transmitters.put(entry.pos(), entry);
    }

    /** The world's one ether. */
    public static WirelessEther of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Writes what the transmitter at {@code pos} now puts on {@code frequency}; true when that is news. */
    public boolean put(GlobalPos pos, int frequency, int power) {
        Entry entry = new Entry(pos, frequency, power);
        if (entry.equals(transmitters.put(pos, entry))) return false;
        setDirty();
        return true;
    }

    /** Takes the transmitter at {@code pos} out, giving back what it was, or null for none there. */
    public @Nullable Entry remove(GlobalPos pos) {
        Entry entry = transmitters.remove(pos);
        if (entry != null) setDirty();
        return entry;
    }

    /** The strongest signal any transmitter, loaded or not, last put on {@code frequency}, 0 to 15. */
    public int strength(int frequency) {
        int best = 0;
        for (Entry entry : transmitters.values()) {
            if (entry.frequency() != frequency) continue;
            best = Math.max(best, entry.power());
            if (best >= 15) return 15;
        }
        return best;
    }

    /** How many transmitters are on {@code frequency}, wherever they are. */
    public int transmitters(int frequency) {
        int count = 0;
        for (Entry entry : transmitters.values()) if (entry.frequency() == frequency) count++;
        return count;
    }
}
