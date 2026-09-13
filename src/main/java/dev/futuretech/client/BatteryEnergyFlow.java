package dev.futuretech.client;

import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.client.BatterySphereMesh.Point;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Looping spiral trails between the core and configured input/output bores. */
public final class BatteryEnergyFlow {
    public static final double PERIOD = 40;
    private static final int STREAMS = 2;
    private static final int MOTES = 9;
    private static final int TRAIL_SEGMENTS = 3;
    public record Segment(Point a, Point b, int color) {}

    public static int outputMask(SideConfig sides) {
        return modeMask(sides, SideMode.OUTPUT);
    }

    public static int inputMask(SideConfig sides) {
        return modeMask(sides, SideMode.INPUT);
    }

    private static int modeMask(SideConfig sides, SideMode mode) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (sides.mode(side) == mode) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    public static List<Segment> create(double ticks, float coreScale, int inputMask, int outputMask) {
        if ((inputMask | outputMask) == 0) return List.of();
        List<Segment> segments = new ArrayList<>();
        double cycle = ticks / PERIOD;
        for (Direction side : Direction.values()) {
            if (((inputMask | outputMask) & 1 << side.ordinal()) == 0) continue;
            boolean inward = (inputMask & 1 << side.ordinal()) != 0;
            for (int stream = 0; stream < STREAMS; stream++) {
                // Reverse the spin along with travel. Otherwise the two angular motions cancel
                // on input, making its spiral almost straight instead of matching the output.
                double phase = stream * Math.PI + (inward ? -cycle : cycle) * Math.PI * 2;
                for (int mote = 0; mote < MOTES; mote++) {
                    double progress = cycle + (mote + stream * 0.5) / MOTES;
                    progress -= Math.floor(progress);
                    for (int trail = 0; trail < TRAIL_SEGMENTS; trail++) {
                        double end = progress - trail * 0.012;
                        double start = end - 0.012;
                        // Never connect a tail at the port back across the loop to the core.
                        if (start <= 0) continue;
                        double fade = Math.min(1, Math.min(end / 0.12, (1 - end) / 0.08));
                        int alpha = (int)(220 * fade * (1 - trail / (double)TRAIL_SEGMENTS));
                        int red = inward ? (int)(35 + 65 * end) : (int)(100 + 155 * end);
                        int green = inward ? (int)(145 + 80 * end) : (int)(225 - 72 * end);
                        int blue = inward ? 255 : (int)(255 - 205 * end);
                        // Input heads advance towards the core; tails stay behind them towards the port.
                        segments.add(new Segment(point(side, coreScale, inward ? 1 - start : start, phase),
                                point(side, coreScale, inward ? 1 - end : end, phase),
                                alpha << 24 | red << 16 | green << 8 | blue));
                    }
                }
            }
        }
        return List.copyOf(segments);
    }

    /** Starts on the core surface and narrows to the centre of the 4/16-wide opening. */
    static Point point(Direction side, float coreScale, double progress, double phase) {
        double coreRadius = BatterySphereMesh.RADIUS * coreScale + 0.003;
        double startRadius = coreRadius * 0.45;
        double startDepth = Math.sqrt(coreRadius * coreRadius - startRadius * startRadius);
        double depth = startDepth + (0.496 - startDepth) * progress;
        double radius = startRadius * Math.pow(1 - progress, 1.4);
        double angle = phase + progress * Math.PI * 2.5;
        Point axis = new Point(side.getStepX(), side.getStepY(), side.getStepZ());
        Point reference = side.getAxis() == Direction.Axis.Y ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point u = reference.cross(axis).unit();
        Point v = axis.cross(u);
        return axis.scale(depth).add(u.scale(radius * Math.cos(angle))).add(v.scale(radius * Math.sin(angle)));
    }

    private BatteryEnergyFlow() {}
}
