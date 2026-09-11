package dev.futuretech.api.side;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Per-face energy modes of one machine. The machine decides which modes it allows and what each
 * face starts as; players cycle faces through the allowed modes from the machine's screen.
 */
public final class SideConfig {
    /** Order a face moves through when clicked; modes the machine does not allow are skipped. */
    public static final List<SideMode> CYCLE = List.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);
    /** Number of menu data slots {@link #data} exposes: one per face. */
    public static final int DATA_COUNT = 6;
    private static final String TAG = "Sides";
    private static final Codec<Map<Direction, SideMode>> CODEC = Codec.unboundedMap(Direction.CODEC, SideMode.CODEC);

    private final Set<SideMode> allowed;
    private final EnumMap<Direction, SideMode> modes = new EnumMap<>(Direction.class);

    public SideConfig(Set<SideMode> allowed, Function<Direction, SideMode> defaults) {
        if (allowed.isEmpty()) throw new IllegalArgumentException("A side config needs at least one allowed mode");
        this.allowed = Set.copyOf(allowed);
        for (Direction side : Direction.values()) set(side, defaults.apply(side));
    }

    public Set<SideMode> allowed() { return allowed; }

    public SideMode mode(Direction side) { return modes.get(side); }

    /** Sideless access (context {@code null}) is unrestricted; it is how the machine itself reaches its buffer. */
    public boolean allowsInput(@Nullable Direction side) { return side == null || modes.get(side).allowsInput(); }

    public boolean allowsOutput(@Nullable Direction side) { return side == null || modes.get(side).allowsOutput(); }

    public void set(Direction side, SideMode mode) {
        if (!allowed.contains(mode)) {
            throw new IllegalArgumentException(mode + " is not allowed on this machine, only " + allowed);
        }
        modes.put(side, mode);
    }

    /** Moves the face to the next allowed mode in {@link #CYCLE} and returns it. */
    public SideMode cycle(Direction side) {
        int start = CYCLE.indexOf(modes.get(side));
        for (int step = 1; step <= CYCLE.size(); step++) {
            SideMode candidate = CYCLE.get((start + step) % CYCLE.size());
            if (allowed.contains(candidate)) {
                modes.put(side, candidate);
                return candidate;
            }
        }
        return modes.get(side);
    }

    /** Puts every face on {@link SideMode#NONE}; does nothing on machines that do not allow it. */
    public void clear() {
        if (!allowed.contains(SideMode.NONE)) return;
        for (Direction side : Direction.values()) modes.put(side, SideMode.NONE);
    }

    /** Menu data slot value for face {@code index} (a {@link Direction} ordinal). */
    public int data(int index) {
        return modes.get(Direction.values()[Math.clamp(index, 0, DATA_COUNT - 1)]).ordinal();
    }

    public void save(ValueOutput output) {
        output.store(TAG, CODEC, Map.copyOf(modes));
    }

    /** Restores saved faces; faces missing from the save or saved with a mode no longer allowed keep their default. */
    public void load(ValueInput input) {
        input.read(TAG, CODEC).ifPresent(saved -> saved.forEach((side, mode) -> {
            if (allowed.contains(mode)) modes.put(side, mode);
        }));
    }
}
