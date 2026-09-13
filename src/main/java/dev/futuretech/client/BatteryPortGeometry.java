package dev.futuretech.client;

import net.minecraft.core.Direction;
import java.util.List;

/** Model-unit coordinates, with the unrotated port on the upper face. */
public final class BatteryPortGeometry {
    private BatteryPortGeometry() {}

    public record Point(float x, float y, float z) {}
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, boolean rim) {}

    public static final List<Box> BOXES = List.of(
            // Three real ledges descend toward the connector. These change the silhouette
            // and expose vertical risers instead of painting steps onto one flat plate.
            new Box(2.75F, 14, 2.75F, 3.5F, 15.65F, 13.25F, false),
            new Box(12.5F, 14, 2.75F, 13.25F, 15.65F, 13.25F, false),
            new Box(3.5F, 14, 2.75F, 12.5F, 15.65F, 3.5F, false),
            new Box(3.5F, 14, 12.5F, 12.5F, 15.65F, 13.25F, false),
            new Box(3.5F, 14, 3.5F, 4.25F, 15.05F, 12.5F, false),
            new Box(11.75F, 14, 3.5F, 12.5F, 15.05F, 12.5F, false),
            new Box(4.25F, 14, 3.5F, 11.75F, 15.05F, 4.25F, false),
            new Box(4.25F, 14, 11.75F, 11.75F, 15.05F, 12.5F, false),
            new Box(4.25F, 14, 4.25F, 5, 14.5F, 11.75F, false),
            new Box(11, 14, 4.25F, 11.75F, 14.5F, 11.75F, false),
            new Box(5, 14, 4.25F, 11, 14.5F, 5, false),
            new Box(5, 14, 11, 11, 14.5F, 11.75F, false),
            // Continuous colored sleeve: visible inside and flush with the neighboring cable at 16.
            new Box(5, 13.85F, 5, 6, 16, 11, true),
            new Box(10, 13.85F, 5, 11, 16, 11, true),
            new Box(6, 13.85F, 5, 10, 16, 6, true),
            new Box(6, 13.85F, 10, 10, 16, 11, true));

    /** Proper rotations preserve outward winding and use world directions, independent of block facing. */
    public static Point rotate(Point p, Direction side) {
        return switch (side) {
            case UP -> p;
            case DOWN -> new Point(p.x, 16 - p.y, 16 - p.z);
            case NORTH -> new Point(p.x, p.z, 16 - p.y);
            case SOUTH -> new Point(p.x, 16 - p.z, p.y);
            case WEST -> new Point(16 - p.y, p.x, p.z);
            case EAST -> new Point(p.y, 16 - p.x, p.z);
        };
    }
}
