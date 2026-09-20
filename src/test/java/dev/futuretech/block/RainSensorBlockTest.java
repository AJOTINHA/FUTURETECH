package dev.futuretech.block;

import org.junit.jupiter.api.Test;

import static dev.futuretech.block.RainSensorBlock.signal;
import static org.junit.jupiter.api.Assertions.*;

class RainSensorBlockTest {
    @Test
    void drySaysNothingRainSaysHowHardAndAStormSaysEverything() {
        assertEquals(0, signal(false, false, 0.0F, false), "dry");
        assertEquals(1, signal(true, false, 0.02F, false), "the first drops still count");
        assertEquals(8, signal(true, false, 0.5F, false), "half a rain");
        assertEquals(15, signal(true, false, 1.0F, false), "rain at its thickest");
        assertEquals(15, signal(true, true, 0.3F, false), "a storm is full whatever the rain");
    }

    @Test
    void invertedIsTheSameTakenFromFifteen() {
        assertEquals(15, signal(false, false, 0.0F, true), "dry, inverted, is full");
        assertEquals(7, signal(true, false, 0.5F, true));
        assertEquals(0, signal(true, true, 1.0F, true), "a storm, inverted, is nothing");
    }
}
