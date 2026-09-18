package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The times of day a time controller can be set to, each a tick of the twenty-four-thousand a
 * day has. Vanilla's own marks are among them — {@code /time set day} is the sunrise here, its
 * noon, night and midnight the noon, moonrise and midnight — with dawn and sunset filled in from
 * where the sky turns.
 *
 * <p>A controller never turns the clock back: the moment it jumps to is the next one of these,
 * today if it has not gone by yet, tomorrow if it has. That keeps the day count climbing, where
 * {@code /time set} would drop it to day zero.
 */
public enum DayMoment implements StringRepresentable {
    DAWN("dawn", 23_000),
    SUNRISE("sunrise", 1_000),
    NOON("noon", 6_000),
    SUNSET("sunset", 12_000),
    MOONRISE("moonrise", 13_000),
    MIDNIGHT("midnight", 18_000);

    public static final long DAY_TICKS = 24_000;
    public static final Codec<DayMoment> CODEC = StringRepresentable.fromEnum(DayMoment::values);

    private final String serializedName;
    private final long tick;

    DayMoment(String serializedName, long tick) {
        this.serializedName = serializedName;
        this.tick = tick;
    }

    @Override
    public String getSerializedName() { return serializedName; }

    /** Where in the day this moment is, in ticks from the start of a day. */
    public long tick() { return tick; }

    public String translationKey() { return "gui.futuretech.time_controller.moment." + serializedName; }

    /**
     * The world time this moment falls on next: today's, unless it is already behind, in which
     * case tomorrow's. The same time exactly is "now", so nothing is skipped.
     */
    public long next(long clockTime) {
        long day = Math.floorDiv(clockTime, DAY_TICKS);
        long today = Math.floorMod(clockTime, DAY_TICKS);
        return (today <= tick ? day : day + 1) * DAY_TICKS + tick;
    }

    /** How many ticks a jump from {@code clockTime} to this moment skips over. */
    public long skipped(long clockTime) { return next(clockTime) - clockTime; }

    public static DayMoment byOrdinal(int ordinal) {
        DayMoment[] moments = values();
        return moments[Math.clamp(ordinal, 0, moments.length - 1)];
    }
}
