package dev.futuretech.client;

import dev.futuretech.block.SolarPanelGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolarPanelGeometryTest {
    @Test void entireMovingPanelStaysInsideItsBlockAndAboveBaseThroughoutTheDay() {
        for (int time = 0; time <= 24000; time += 10) {
            float tilt = SolarPanelRenderer.tilt(time);
            for (float x : new float[]{SolarPanelGeometry.MIN_X, SolarPanelGeometry.MAX_X})
                for (float y : new float[]{SolarPanelGeometry.BOTTOM, SolarPanelGeometry.TOP})
                    for (float z : new float[]{0, 16}) {
                        var p = SolarPanelGeometry.rotate(x, y, z, tilt);
                        assertTrue(p.x() > 0 && p.x() < 16, "Panel enters adjacent block at " + time);
                        assertTrue(p.y() > SolarPanelGeometry.BASE_TOP + .5, "Panel intersects base at " + time);
                        assertTrue(p.y() < 16, "Panel enters block above at " + time);
                        assertTrue(p.y() >= SolarPanelGeometry.ENVELOPE_BOTTOM - .0001);
                        assertTrue(p.y() <= SolarPanelGeometry.ENVELOPE_TOP + .0001);
                    }
        }
    }

    @Test void fixedMastCannotPierceTheCellsAtAnyTrackingAngle() {
        for (float tilt = -SolarPanelGeometry.MAX_TILT; tilt <= SolarPanelGeometry.MAX_TILT; tilt += .1F) {
            // Bottom surface over each edge of the slim fixed mast.
            double angle = Math.toRadians(-tilt);
            for (double x : new double[]{SolarPanelGeometry.MAST_MIN, SolarPanelGeometry.MAST_MAX}) {
                double underside = SolarPanelGeometry.PIVOT_Y
                        + (x-8)*Math.tan(angle)
                        + (SolarPanelGeometry.BOTTOM-SolarPanelGeometry.PIVOT_Y)/Math.cos(angle);
                assertTrue(underside > SolarPanelGeometry.MAST_TOP + .2, "Mast pierces tilted panel");
            }
        }
    }
}
