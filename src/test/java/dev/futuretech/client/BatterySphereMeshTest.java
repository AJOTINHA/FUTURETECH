package dev.futuretech.client;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BatterySphereMeshTest {
    @Test
    void latticeIsAClosedHexagonalSphereWithNoDuplicateEdges() {
        var mesh = BatterySphereMesh.create();
        assertEquals(150, mesh.cells().stream().filter(c -> c.corners().size() == 6).count());
        assertEquals(12, mesh.cells().stream().filter(c -> c.corners().size() == 5).count());
        assertEquals(2, mesh.nodes().size() - mesh.edges().size() + mesh.cells().size());
        Map<BatterySphereMesh.Edge, Integer> uses = new HashMap<>();
        for (var cell : mesh.cells()) {
            for (int i = 0; i < cell.corners().size(); i++) {
                int a = cell.corners().get(i), b = cell.corners().get((i + 1) % cell.corners().size());
                uses.merge(new BatterySphereMesh.Edge(Math.min(a,b), Math.max(a,b)), 1, Integer::sum);
                // Outward winding is required for the opaque core's back-face culling.
                var p = mesh.nodes().get(a);
                var q = mesh.nodes().get(b);
                assertTrue(p.subtract(cell.centre()).cross(q.subtract(cell.centre())).dot(cell.centre()) > 0);
            }
        }
        assertEquals(mesh.edges().size(), uses.size());
        assertTrue(uses.values().stream().allMatch(count -> count == 2));
        int[] degree = new int[mesh.nodes().size()];
        for (var edge : mesh.edges()) { degree[edge.a()]++; degree[edge.b()]++; }
        for (int d : degree) assertEquals(3, d);
    }

    @Test
    void sphereAndGlowFitInsideTheOpenFrameAtEveryRotation() {
        var mesh = BatterySphereMesh.create();
        for (var node : mesh.nodes()) assertEquals(1, Math.sqrt(node.dot(node)), 1e-10);
        // Rotations preserve radius. Include extra space for ribbons and luminous junctions.
        assertTrue(BatterySphereMesh.RADIUS + 0.015 < 5.0 / 16.0);
        assertTrue(mesh.nodes().stream().allMatch(p -> Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())));
    }
}
