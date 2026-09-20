package dev.futuretech.block;

/** Model-pixel dimensions shared by the animated renderer, collision envelope and checks. */
public final class SolarPanelGeometry {
    public static final float MAX_TILT = 30;
    public static final float MIN_X = .25F, MAX_X = 15.75F;
    public static final float BOTTOM = 10.75F, TOP = 11.75F, PIVOT_Y = BOTTOM;
    public static final float BASE_TOP = 6, MAST_TOP = 10.1F;
    public static final float MAST_MIN = 7.25F, MAST_MAX = 8.75F;
    public static final float ENVELOPE_BOTTOM = (float) (PIVOT_Y
            - (MAX_X - 8) * Math.sin(Math.toRadians(MAX_TILT)));
    public static final float ENVELOPE_TOP = (float) (PIVOT_Y
            + (MAX_X - 8) * Math.sin(Math.toRadians(MAX_TILT))
            + (TOP - PIVOT_Y) * Math.cos(Math.toRadians(MAX_TILT)));

    private SolarPanelGeometry() {}

    /** Rotation about the north-south axle, in model pixels, matching Axis.ZP(-tilt). */
    public static Point rotate(float x, float y, float z, float tilt) {
        double angle = Math.toRadians(-tilt), c = Math.cos(angle), s = Math.sin(angle);
        return new Point(8 + (x-8)*c - (y-PIVOT_Y)*s,
                PIVOT_Y + (x-8)*s + (y-PIVOT_Y)*c, z);
    }

    public record Point(double x, double y, double z) {}
}
