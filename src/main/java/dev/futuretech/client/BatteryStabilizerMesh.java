package dev.futuretech.client;

import dev.futuretech.client.BatterySphereMesh.Point;
import java.util.ArrayList;
import java.util.List;

/** Two concentric tubular rings; different radii keep the inclined orbits apart. */
public final class BatteryStabilizerMesh {
    public static final double INNER_RADIUS = 0.287;
    public static final double OUTER_RADIUS = 0.305;
    public static final double TUBE_RADIUS = 0.0055;
    public record Quad(Point a, Point b, Point c, Point d, int color) {}

    public static List<Quad> create(double radius) {
        int segments = 96;
        int sides = 8;
        double tube = TUBE_RADIUS;
        List<Quad> result = new ArrayList<>(segments * sides);
        for (int segment = 0; segment < segments; segment++) {
            // Three silver joints make rotation visible on the graphite steel ring.
            boolean marker = segment % 32 < 3;
            int color = marker ? 0xFFB0B4BC : 0xFF797D87;
            for (int side = 0; side < sides; side++) {
                double u = 2 * Math.PI * segment / segments;
                double nextU = 2 * Math.PI * (segment + 1) / segments;
                double v = 2 * Math.PI * side / sides;
                double nextV = 2 * Math.PI * (side + 1) / sides;
                result.add(new Quad(point(radius,tube,u,v), point(radius,tube,u,nextV),
                        point(radius,tube,nextU,nextV), point(radius,tube,nextU,v), color));
            }
        }
        return List.copyOf(result);
    }

    private static Point point(double radius, double tube, double u, double v) {
        double r = radius + tube * Math.cos(v);
        return new Point(r * Math.cos(u), tube * Math.sin(v), r * Math.sin(u));
    }

    private BatteryStabilizerMesh() {}
}
