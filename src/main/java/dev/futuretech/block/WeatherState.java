package dev.futuretech.block;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;

/** The skies a weather controller can be set to, the three {@code /weather} offers. */
public enum WeatherState {
    CLEAR("clear"),
    RAIN("rain"),
    THUNDER("thunder");

    private final String serializedName;

    WeatherState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String translationKey() { return "gui.futuretech.weather_controller.state." + serializedName; }

    /**
     * Whether the sky over {@code level} is this already. The server reads the weather as set,
     * since rain takes a second to thicken and a change just made must count at once; a client
     * only knows the rain as it sees it, which is close enough for a price on a screen.
     */
    public boolean holds(Level level) {
        boolean raining;
        boolean thundering;
        if (level instanceof ServerLevel server) {
            WeatherData weather = server.getWeatherData();
            raining = weather.isRaining();
            thundering = weather.isThundering();
        } else {
            raining = level.isRaining();
            thundering = level.isThundering();
        }
        return switch (this) {
            case CLEAR -> !raining;
            case RAIN -> raining && !thundering;
            case THUNDER -> raining && thundering;
        };
    }

    /** Sets the sky to this for {@code ticks}, after which the weather goes its own way again. */
    public void set(MinecraftServer server, int ticks) {
        switch (this) {
            case CLEAR -> server.setWeatherParameters(ticks, 0, false, false);
            case RAIN -> server.setWeatherParameters(0, ticks, true, false);
            case THUNDER -> server.setWeatherParameters(0, ticks, true, true);
        }
    }

    public static WeatherState byOrdinal(int ordinal) {
        WeatherState[] states = values();
        return states[Math.clamp(ordinal, 0, states.length - 1)];
    }
}
