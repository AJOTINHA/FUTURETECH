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
 * Per-face modes of one machine. A mode always decides what items may cross that face.
 *
 * <p>Whether it also decides energy depends on the machine. Machines and generators leave energy
 * unconfigured: a machine draws power from any side and a generator pushes power out of any side,
 * so the player only ever configures items on them. A battery is the exception - moving energy is
 * its whole job - so there the same mode governs energy too.
 */
public final class SideConfig {
    /** Order a face moves through when clicked; modes the machine does not allow are skipped. */
    public static final List<SideMode> CYCLE = List.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE);
    /** Number of menu data slots {@link #data} exposes: one per face. */
    public static final int DATA_COUNT = 6;
    private static final String TAG = "Sides";
    private static final Codec<Map<Direction, SideMode>> CODEC = Codec.unboundedMap(Direction.CODEC, SideMode.CODEC);

    private final Set<SideMode> allowed;
    private final boolean governsEnergy;
    private final EnumMap<Direction, SideMode> modes = new EnumMap<>(Direction.class);

    /** A configuration that only governs items; energy flows through every face untouched. */
    public SideConfig(Set<SideMode> allowed, Function<Direction, SideMode> defaults) {
        this(allowed, false, defaults);
    }

    /**
     * @param governsEnergy true when a face's mode also decides which way energy may flow, as on a
     *                      battery; false when energy ignores the configuration, as on every machine
     */
    public SideConfig(Set<SideMode> allowed, boolean governsEnergy, Function<Direction, SideMode> defaults) {
        if (allowed.isEmpty()) throw new IllegalArgumentException("A side config needs at least one allowed mode");
        this.allowed = Set.copyOf(allowed);
        this.governsEnergy = governsEnergy;
        for (Direction side : Direction.values()) set(side, defaults.apply(side));
    }

    /** Whether the face modes decide energy flow as well as items. */
    public boolean governsEnergy() { return governsEnergy; }

    public Set<SideMode> allowed() { return allowed; }

    public SideMode mode(Direction side) { return modes.get(side); }

    /** Items this face lets in. Sideless access (context {@code null}) is the machine's own, unrestricted. */
    public boolean allowsItemInput(@Nullable Direction side) { return side == null || modes.get(side).allowsInput(); }

    /** Items this face lets out. */
    public boolean allowsItemOutput(@Nullable Direction side) { return side == null || modes.get(side).allowsOutput(); }

    /** Energy this face lets in; unconfigured machines take power from any side. */
    public boolean allowsEnergyInput(@Nullable Direction side) {
        return side == null || !governsEnergy || modes.get(side).allowsInput();
    }

    /** Energy this face lets out; unconfigured generators push power out of any side. */
    public boolean allowsEnergyOutput(@Nullable Direction side) {
        return side == null || !governsEnergy || modes.get(side).allowsOutput();
    }

    public void set(Direction side, SideMode mode) {
        if (!allowed.contains(mode)) {
            throw new IllegalArgumentException(mode + " is not allowed on this machine, only " + allowed);
        }
        modes.put(side, mode);
    }

    /** Moves the face to the next allowed mode in {@link #CYCLE} and returns it. */
    public SideMode cycle(Direction side) {
        return cycle(side, false);
    }

    /** Moves one step through the allowed modes, reversing the order for a right click. */
    public SideMode cycle(Direction side, boolean backwards) {
        int start = CYCLE.indexOf(modes.get(side));
        for (int step = 1; step <= CYCLE.size(); step++) {
            SideMode candidate = CYCLE.get(Math.floorMod(start + (backwards ? -step : step), CYCLE.size()));
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

    /** Carry the configured ports with the block, preserving top and bottom during a yaw turn. */
    public void rotate(net.minecraft.world.level.block.Rotation rotation) {
        var previous = new EnumMap<>(modes);
        previous.forEach((side, mode) -> modes.put(rotation.rotate(side), mode));
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
