package dev.futuretech.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Immutable dual of a subdivided icosahedron: hexagonal cells with twelve pentagonal poles. */
public final class BatterySphereMesh {
    public static final double RADIUS = 0.265;
    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x + b.x, y + b.y, z + b.z); }
        public Point subtract(Point b) { return new Point(x - b.x, y - b.y, z - b.z); }
        public Point scale(double s) { return new Point(x * s, y * s, z * s); }
        public double dot(Point b) { return x * b.x + y * b.y + z * b.z; }
        public Point cross(Point b) { return new Point(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x); }
        public Point unit() { return scale(1 / Math.sqrt(dot(this))); }
    }
    public record Cell(Point centre, List<Integer> corners) {}
    public record Edge(int a, int b) {}
    public record Mesh(List<Point> nodes, List<Cell> cells, List<Edge> edges) {}

    public static Mesh create() {
        double t = (1 + Math.sqrt(5)) / 2;
        List<Point> vertices = new ArrayList<>(List.of(
                new Point(-1,t,0), new Point(1,t,0), new Point(-1,-t,0), new Point(1,-t,0),
                new Point(0,-1,t), new Point(0,1,t), new Point(0,-1,-t), new Point(0,1,-t),
                new Point(t,0,-1), new Point(t,0,1), new Point(-t,0,-1), new Point(-t,0,1)));
        vertices.replaceAll(Point::unit);
        List<int[]> faces = new ArrayList<>(List.of(
                new int[]{0,11,5},new int[]{0,5,1},new int[]{0,1,7},new int[]{0,7,10},new int[]{0,10,11},
                new int[]{1,5,9},new int[]{5,11,4},new int[]{11,10,2},new int[]{10,7,6},new int[]{7,1,8},
                new int[]{3,9,4},new int[]{3,4,2},new int[]{3,2,6},new int[]{3,6,8},new int[]{3,8,9},
                new int[]{4,9,5},new int[]{2,4,11},new int[]{6,2,10},new int[]{8,6,7},new int[]{9,8,1}));
        for (int level = 0; level < 2; level++) {
            Map<Long, Integer> midpoints = new HashMap<>();
            List<int[]> refined = new ArrayList<>();
            for (int[] f : faces) {
                int ab = midpoint(f[0], f[1], vertices, midpoints);
                int bc = midpoint(f[1], f[2], vertices, midpoints);
                int ca = midpoint(f[2], f[0], vertices, midpoints);
                refined.add(new int[]{f[0], ab, ca}); refined.add(new int[]{f[1], bc, ab});
                refined.add(new int[]{f[2], ca, bc}); refined.add(new int[]{ab, bc, ca});
            }
            faces = refined;
        }
        List<Point> nodes = new ArrayList<>();
        List<List<Integer>> incident = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) incident.add(new ArrayList<>());
        for (int[] f : faces) {
            int index = nodes.size();
            nodes.add(vertices.get(f[0]).add(vertices.get(f[1])).add(vertices.get(f[2])).unit());
            for (int v : f) incident.get(v).add(index);
        }
        List<Cell> cells = new ArrayList<>();
        var edges = new LinkedHashSet<Edge>();
        for (int v = 0; v < vertices.size(); v++) {
            Point normal = vertices.get(v);
            Point reference = Math.abs(normal.y) < 0.9 ? new Point(0,1,0) : new Point(1,0,0);
            Point u = reference.cross(normal).unit();
            Point w = normal.cross(u);
            List<Integer> corners = incident.get(v);
            corners.sort(Comparator.comparingDouble(i -> Math.atan2(nodes.get(i).dot(w), nodes.get(i).dot(u))));
            cells.add(new Cell(normal, List.copyOf(corners)));
            for (int i = 0; i < corners.size(); i++) {
                int a = corners.get(i), b = corners.get((i + 1) % corners.size());
                edges.add(new Edge(Math.min(a,b), Math.max(a,b)));
            }
        }
        return new Mesh(List.copyOf(nodes), List.copyOf(cells), List.copyOf(edges));
    }

    private static int midpoint(int a, int b, List<Point> vertices, Map<Long, Integer> cache) {
        long key = ((long)Math.min(a,b) << 32) | Math.max(a,b);
        return cache.computeIfAbsent(key, ignored -> {
            vertices.add(vertices.get(a).add(vertices.get(b)).unit());
            return vertices.size() - 1;
        });
    }

    private BatterySphereMesh() {}
}
