package dev.futuretech.client;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatteryPortGeometryTest {
    @Test
    void plateHasThreePhysicalLedgesWithoutGapsAroundTheSleeve() {
        var steel = BatteryPortGeometry.BOXES.stream().filter(b -> !b.rim()).toList();
        assertEquals(3, steel.stream().map(BatteryPortGeometry.Box::y1).distinct().count());
        // Probe between the frame and connector at the left edge: each step is lower.
        float previousHeight = 16;
        for (float x : new float[]{3, 3.75F, 4.5F}) {
            float top = steel.stream().filter(b -> x > b.x0() && x < b.x1() && 8 > b.z0() && 8 < b.z1())
                    .findFirst().orElseThrow().y1();
            assertTrue(top < previousHeight);
            previousHeight = top;
        }
        // All of the old plate footprint remains covered except the real center opening.
        for (float x = 2.875F; x < 13.25F; x += .25F) {
            for (float z = 2.875F; z < 13.25F; z += .25F) {
                if (x > 6 && x < 10 && z > 6 && z < 10) continue;
                final float px = x, pz = z;
                assertTrue(BatteryPortGeometry.BOXES.stream().anyMatch(b ->
                        px >= b.x0() && px <= b.x1() && pz >= b.z0() && pz <= b.z1()));
            }
        }
    }

    @Test
    void coloredSleeveReachesCableBoundaryAndRemainsVisibleInsideOnEverySide() {
        for (Direction side : Direction.values()) {
            for (var rim : BatteryPortGeometry.BOXES.stream().filter(BatteryPortGeometry.Box::rim).toList()) {
                var outside = BatteryPortGeometry.rotate(new BatteryPortGeometry.Point(rim.x0(), rim.y1(), rim.z0()), side);
                float boundary = switch (side.getAxis()) {
                    case X -> outside.x();
                    case Y -> outside.y();
                    case Z -> outside.z();
                };
                assertEquals(side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 : 0, boundary);
                for (var steel : BatteryPortGeometry.BOXES.stream().filter(b -> !b.rim()).toList()) {
                    assertTrue(rim.y0() < steel.y0(), "The colored inner lip must protrude beyond the steel");
                    assertFalse(rim.x0() < steel.x1() && rim.x1() > steel.x0()
                            && rim.z0() < steel.z1() && rim.z1() > steel.z0(),
                            "Steel must not overlap the sleeve or cover its colored bore");
                }
            }
        }
    }

    @Test
    void everyPortHasAnOpenCenterAndClearsTheRotatingRings() {
        double ringEnvelope = (BatteryStabilizerMesh.OUTER_RADIUS + BatteryStabilizerMesh.TUBE_RADIUS) * 16;
        for (Direction side : Direction.values()) {
            var outerCenter = BatteryPortGeometry.rotate(new BatteryPortGeometry.Point(8, 16, 8), side);
            assertEquals(8 + side.getStepX() * 8, outerCenter.x());
            assertEquals(8 + side.getStepY() * 8, outerCenter.y());
            assertEquals(8 + side.getStepZ() * 8, outerCenter.z());
            for (var box : BatteryPortGeometry.BOXES) {
                var a = BatteryPortGeometry.rotate(new BatteryPortGeometry.Point(box.x0(), box.y0(), box.z0()), side);
                var b = BatteryPortGeometry.rotate(new BatteryPortGeometry.Point(box.x1(), box.y1(), box.z1()), side);
                float[] min = {Math.min(a.x(),b.x()), Math.min(a.y(),b.y()), Math.min(a.z(),b.z())};
                float[] max = {Math.max(a.x(),b.x()), Math.max(a.y(),b.y()), Math.max(a.z(),b.z())};
                double nearestSquared = 0;
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(min[axis] >= 0 && max[axis] <= 16);
                    double distance = Math.max(Math.max(min[axis] - 8, 8 - max[axis]), 0);
                    nearestSquared += distance * distance;
                }
                assertTrue(nearestSquared > ringEnvelope * ringEnvelope, "Port must clear rings at any angle");
                // A ray through the center must pass through the port, from either side.
                for (int depth = 0; depth <= 16; depth++) {
                    var ray = BatteryPortGeometry.rotate(new BatteryPortGeometry.Point(8, depth, 8), side);
                    assertFalse(ray.x() > min[0] && ray.x() < max[0]
                            && ray.y() > min[1] && ray.y() < max[1]
                            && ray.z() > min[2] && ray.z() < max[2]);
                }
            }
        }
    }
}
