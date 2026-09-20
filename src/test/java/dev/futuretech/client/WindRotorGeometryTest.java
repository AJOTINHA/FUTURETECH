package dev.futuretech.client;

import static dev.futuretech.block.WindRotorGeometry.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WindRotorGeometryTest {
    @Test void entireSweepStaysInsideRenderBoundsAndClearOfMastInEveryFacing() {
        assertTrue(BLADE_BACK < AXLE_FRONT);
        for (int facing = 0; facing < 360; facing += 90) {
            double f = Math.toRadians(facing);
            for (int angle = 0; angle < 360; angle++) {
                double a = Math.toRadians(angle);
                for (float x : new float[]{BLADE_LEFT, BLADE_RIGHT})
                    for (float y : new float[]{BLADE_BOTTOM, BLADE_TOP})
                        for (float z : new float[]{BLADE_FRONT, BLADE_BACK}) {
                            double X = PIVOT_X + (x-PIVOT_X)*Math.cos(a) - (y-PIVOT_Y)*Math.sin(a);
                            double Y = PIVOT_Y + (x-PIVOT_X)*Math.sin(a) + (y-PIVOT_Y)*Math.cos(a);
                            double rotatedX = 8+(X-8)*Math.cos(f)+(z-8)*Math.sin(f);
                            double rotatedZ = 8-(X-8)*Math.sin(f)+(z-8)*Math.cos(f);
                            assertTrue(rotatedX > -16 && rotatedX < 32);
                            assertTrue(rotatedZ > -16 && rotatedZ < 32);
                            assertTrue(Y > 32 && Y < 80);
                        }
            }
        }
    }
}
