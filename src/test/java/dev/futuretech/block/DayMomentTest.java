package dev.futuretech.block;

import org.junit.jupiter.api.Test;

import static dev.futuretech.block.DayMoment.DAY_TICKS;
import static org.junit.jupiter.api.Assertions.*;

class DayMomentTest {
    @Test
    void aMomentStillAheadTodayIsReachedToday() {
        long morning = 3 * DAY_TICKS + 2_000;
        assertEquals(3 * DAY_TICKS + 6_000, DayMoment.NOON.next(morning));
        assertEquals(4_000, DayMoment.NOON.skipped(morning));
    }

    @Test
    void aMomentAlreadyBehindIsReachedTomorrowAndNeverByTurningBack() {
        long afternoon = 3 * DAY_TICKS + 9_000;
        assertEquals(4 * DAY_TICKS + 1_000, DayMoment.SUNRISE.next(afternoon), "sunrise has gone by: tomorrow's");
        assertTrue(DayMoment.SUNRISE.skipped(afternoon) > 0, "the clock only moves forward");
        assertEquals(3, Math.floorDiv(afternoon, DAY_TICKS), "and the day count is not what changes");
        assertEquals(4, Math.floorDiv(DayMoment.SUNRISE.next(afternoon), DAY_TICKS), "it climbs by one, like sleeping");
    }

    @Test
    void theSameMomentExactlyIsNowAndSkipsNothing() {
        long noon = 7 * DAY_TICKS + 6_000;
        assertEquals(noon, DayMoment.NOON.next(noon));
        assertEquals(0, DayMoment.NOON.skipped(noon));
    }

    @Test
    void dawnSitsAtTheEndOfTheDaySoItIsAlmostAlwaysAhead() {
        long midnight = 2 * DAY_TICKS + 18_000;
        assertEquals(2 * DAY_TICKS + 23_000, DayMoment.DAWN.next(midnight), "still tonight");
        long lateDawn = 2 * DAY_TICKS + 23_500;
        assertEquals(3 * DAY_TICKS + 23_000, DayMoment.DAWN.next(lateDawn), "just missed it: tomorrow night");
    }

    @Test
    void aFullDayIsTheMostAJumpEverSkips() {
        for (DayMoment moment : DayMoment.values()) {
            for (long tick = 0; tick < DAY_TICKS; tick += 250) {
                long skipped = moment.skipped(5 * DAY_TICKS + tick);
                assertTrue(skipped >= 0 && skipped < DAY_TICKS, moment + " at " + tick + " skips " + skipped);
            }
        }
    }
}
