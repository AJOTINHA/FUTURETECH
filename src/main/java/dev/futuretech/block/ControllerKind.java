package dev.futuretech.block;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;

/**
 * What a controller controls: the two controllers are one machine with different things to
 * set, so this is where they differ. Each kind names its choices, prices a change and makes it.
 *
 * <p>The time controller moves the overworld's clock — the one every dimension's sky follows —
 * forward to the next of the chosen {@link DayMoment}, the way {@code /time add} moves it, and
 * pays by the tick skipped: {@value #COST_PER_TICK} FE each, a full day {@code 120,000}. Always
 * forward, so the day count only ever climbs, where {@code /time set} would drop it to day zero.
 *
 * <p>The weather controller sets the sky to the chosen {@link WeatherState} for
 * {@value #WEATHER_TICKS} ticks, the way {@code /weather} does, for a flat {@value #WEATHER_COST}
 * FE; the sky already that way costs nothing and changes nothing.
 */
public enum ControllerKind implements StringRepresentable {
    TIME("time_controller", DayMoment.values().length, 250_000) {
        @Override
        public String choiceKey(int choice) { return DayMoment.byOrdinal(choice).translationKey(); }

        @Override
        public int cost(Level level, int choice) {
            return (int) (DayMoment.byOrdinal(choice).skipped(level.getOverworldClockTime()) * COST_PER_TICK);
        }

        @Override
        public void apply(ServerLevel level, int choice) {
            ServerClockManager clocks = level.getServer().clockManager();
            Holder<WorldClock> clock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            clocks.addTicks(clock, (int) DayMoment.byOrdinal(choice).skipped(clocks.getTotalTicks(clock)));
        }
    },
    WEATHER("weather_controller", WeatherState.values().length, 100_000) {
        @Override
        public String choiceKey(int choice) { return WeatherState.byOrdinal(choice).translationKey(); }

        @Override
        public int cost(Level level, int choice) {
            return WeatherState.byOrdinal(choice).holds(level) ? 0 : WEATHER_COST;
        }

        @Override
        public void apply(ServerLevel level, int choice) {
            WeatherState.byOrdinal(choice).set(level.getServer(), WEATHER_TICKS);
        }
    };

    public static final Codec<ControllerKind> CODEC = StringRepresentable.fromEnum(ControllerKind::values);
    /** What a tick of skipped time costs. */
    public static final int COST_PER_TICK = 5;
    /** What a change of weather costs, whatever it is from and to. */
    public static final int WEATHER_COST = 20_000;
    /** How long a weather the controller set lasts before the sky goes its own way again: ten minutes. */
    public static final int WEATHER_TICKS = 12_000;
    /** Every controller takes energy in at the same rate; the buffers differ with what a change costs. */
    public static final int INPUT_PER_TICK = 2_000;

    private final String serializedName;
    private final int choices;
    private final int capacity;

    ControllerKind(String serializedName, int choices, int capacity) {
        this.serializedName = serializedName;
        this.choices = choices;
        this.capacity = capacity;
    }

    @Override
    public String getSerializedName() { return serializedName; }

    /** Registry name of the block and its item. */
    public String blockName() { return serializedName; }

    public int choices() { return choices; }

    public int capacity() { return capacity; }

    public String translationKey() { return "gui.futuretech." + serializedName; }

    /** Translation key of one choice's name, as the screen's button shows it. */
    public abstract String choiceKey(int choice);

    /**
     * What making choice {@code choice} would cost from the world as it is; zero where the world
     * is there already. Read on the client from its own level for the screen, and on the server
     * before a change is made.
     */
    public abstract int cost(Level level, int choice);

    /** Makes the change; only called when {@link #cost} was paid. */
    public abstract void apply(ServerLevel level, int choice);

    public int clampChoice(int choice) { return Math.clamp(choice, 0, choices - 1); }
}
