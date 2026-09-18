package dev.futuretech.block.entity;

import dev.futuretech.block.DayMoment;
import org.junit.jupiter.api.Test;

import static dev.futuretech.block.DayMoment.DAY_TICKS;
import static dev.futuretech.block.entity.TimeControllerBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

class TimeControllerTest {
    @Test
    void theJumpIsPricedByTheTicksSkipped() {
        long noon = 4 * DAY_TICKS + 6_000;
        assertEquals(0, cost(noon, DayMoment.NOON), "already there");
        assertEquals(6_000 * COST_PER_TICK, cost(noon, DayMoment.SUNSET));
        assertEquals((DAY_TICKS - 5_000) * COST_PER_TICK, cost(noon, DayMoment.SUNRISE), "tomorrow's sunrise");
        for (DayMoment moment : DayMoment.values()) {
            for (long tick = 0; tick < DAY_TICKS; tick += 250) {
                assertTrue(cost(5 * DAY_TICKS + tick, moment) <= CAPACITY, "a full buffer pays for any jump: " + moment + " at " + tick);
            }
        }
    }
}
