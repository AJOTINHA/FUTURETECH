package dev.futuretech.block;

import org.junit.jupiter.api.Test;

import static dev.futuretech.block.DayMoment.DAY_TICKS;
import static org.junit.jupiter.api.Assertions.*;

class ControllerKindTest {
    @Test
    void everyControllerHoldsEnoughForItsDearestChange() {
        for (DayMoment moment : DayMoment.values()) {
            for (long tick = 0; tick < DAY_TICKS; tick += 250) {
                long fare = moment.skipped(5 * DAY_TICKS + tick) * ControllerKind.COST_PER_TICK;
                assertTrue(fare <= ControllerKind.TIME.capacity(), "a full buffer pays for any jump: " + moment + " at " + tick);
            }
        }
        assertTrue(ControllerKind.WEATHER_COST <= ControllerKind.WEATHER.capacity());
        assertEquals(DayMoment.values().length, ControllerKind.TIME.choices());
        assertEquals(WeatherState.values().length, ControllerKind.WEATHER.choices());
    }

    @Test
    void everyChoiceHasANameAndTheKindsHaveTheirBlocks() {
        for (ControllerKind kind : ControllerKind.values()) {
            for (int choice = 0; choice < kind.choices(); choice++) {
                assertTrue(kind.choiceKey(choice).startsWith("gui.futuretech." + kind.blockName() + "."), kind + " " + choice);
            }
            assertEquals(kind.choices() - 1, kind.clampChoice(99), kind + " clamps a choice past the end");
        }
    }
}
