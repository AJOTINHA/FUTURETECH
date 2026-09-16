package dev.futuretech.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BatteryStabilizerMeshTest {
    @Test
    void metalRingsClearSphereEachOtherAndFrameAtAnyRotation() {
        double inner = BatteryStabilizerMesh.INNER_RADIUS;
        double outer = BatteryStabilizerMesh.OUTER_RADIUS;
        double glow = BatteryStabilizerMesh.TUBE_RADIUS;
        assertTrue(inner - glow > BatterySphereMesh.RADIUS + 0.01);
        assertTrue(inner + glow < outer - glow);
        assertTrue(outer + glow < 5.0 / 16.0);
        for (double radius : new double[]{inner, outer}) {
            {
                var mesh = BatteryStabilizerMesh.create(radius);
                assertEquals(96 * 8, mesh.size());
                for (var quad : mesh) {
                    for (var p : List.of(quad.a(),quad.b(),quad.c(),quad.d())) {
                        double distance = Math.sqrt(p.dot(p));
                        assertTrue(distance >= radius - glow - 1e-9 && distance <= radius + glow + 1e-9);
                    }
                    assertTrue(quad.b().subtract(quad.a()).cross(quad.d().subtract(quad.a())).dot(
                            quad.b().subtract(quad.a()).cross(quad.d().subtract(quad.a()))) > 0);
                }
                // Last segment closes exactly onto the first, with no gap at the seam.
                for (int side = 0; side < 8; side++) {
                    var first = mesh.get(side).a();
                    var last = mesh.get(95 * 8 + side).d();
                    assertEquals(0, first.subtract(last).dot(first.subtract(last)), 1e-20);
                }
            }
        }
    }
}
