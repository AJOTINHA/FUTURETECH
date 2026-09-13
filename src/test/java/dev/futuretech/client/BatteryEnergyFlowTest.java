package dev.futuretech.client;

import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.client.BatterySphereMesh.Point;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BatteryEnergyFlowTest {
    @Test
    void inputAndOutputSpiralAtTheSameSpeedInOppositeDirections() {
        for (Direction side : Direction.values()) {
            Point axis = new Point(side.getStepX(), side.getStepY(), side.getStepZ());
            int mask = 1 << side.ordinal();
            for (float scale : new float[]{0.12F, 0.5F, 1}) {
                for (double time : new double[]{8, 16, 24, 32}) {
                    var inputNow = BatteryEnergyFlow.create(time, scale, mask, 0).getFirst().b();
                    var inputNext = BatteryEnergyFlow.create(time + 0.2, scale, mask, 0).getFirst().b();
                    var outputNow = BatteryEnergyFlow.create(time, scale, 0, mask).getFirst().b();
                    var outputNext = BatteryEnergyFlow.create(time + 0.2, scale, 0, mask).getFirst().b();
                    double inputTurn = angularStep(inputNow, inputNext, axis);
                    double outputTurn = angularStep(outputNow, outputNext, axis);
                    assertTrue(inputTurn < 0);
                    assertTrue(outputTurn > 0);
                    assertEquals(outputTurn, -inputTurn, 1e-10,
                            "Input must retain the output's spiral speed when travel is reversed");
                }
            }
        }
    }

    private static double angularStep(Point from, Point to, Point axis) {
        Point radialFrom = from.subtract(axis.scale(from.dot(axis)));
        Point radialTo = to.subtract(axis.scale(to.dot(axis)));
        return Math.atan2(axis.dot(radialFrom.cross(radialTo)), radialFrom.dot(radialTo));
    }

    @Test
    void inputTrailsMoveIntoTheCoreInBlueOnEveryFaceAndCoreSize() {
        for (Direction side : Direction.values()) {
            Point axis = new Point(side.getStepX(), side.getStepY(), side.getStepZ());
            for (float scale : new float[]{0.12F, 0.5F, 1}) {
                for (double time : new double[]{0, 0.01, 10.5, 39.99, 40}) {
                    var flow = BatteryEnergyFlow.create(time, scale, 1 << side.ordinal(), 0);
                    assertFalse(flow.isEmpty());
                    for (var segment : flow) {
                        assertTrue(segment.b().dot(axis) < segment.a().dot(axis), "Input head must be closer to the core");
                        Point delta = segment.b().subtract(segment.a());
                        assertTrue(delta.dot(delta) > 0 && delta.dot(delta) < 0.025 * 0.025);
                        assertEquals(255, segment.color() & 255);
                        assertTrue((segment.color() >> 8 & 255) > (segment.color() >> 16 & 255));
                    }
                }
            }
        }
        var beginning = BatteryEnergyFlow.create(0, 1, 63, 0);
        var wrapped = BatteryEnergyFlow.create(40, 1, 63, 0);
        assertEquals(beginning.size(), wrapped.size());
        for (int i = 0; i < beginning.size(); i++) {
            Point delta = beginning.get(i).a().subtract(wrapped.get(i).a());
            assertEquals(0, delta.dot(delta), 1e-20);
        }
    }

    @Test
    void mixedInputAndOutputFacesFollowTheirOwnModeAndClearImmediately() {
        var sides = new SideConfig(Set.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT), true, side -> SideMode.NONE);
        sides.set(Direction.UP, SideMode.INPUT);
        sides.set(Direction.WEST, SideMode.OUTPUT);
        var flow = BatteryEnergyFlow.create(10, 1, BatteryEnergyFlow.inputMask(sides), BatteryEnergyFlow.outputMask(sides));
        assertTrue(flow.stream().anyMatch(s -> s.a().y() > 0.24 && s.b().y() < s.a().y()));
        assertTrue(flow.stream().anyMatch(s -> s.a().x() < -0.24 && s.b().x() < s.a().x()));
        sides.set(Direction.WEST, SideMode.INPUT);
        assertEquals(0, BatteryEnergyFlow.outputMask(sides));
        assertEquals((1 << Direction.UP.ordinal()) | (1 << Direction.WEST.ordinal()), BatteryEnergyFlow.inputMask(sides));
        sides.clear();
        assertTrue(BatteryEnergyFlow.create(10, 1, BatteryEnergyFlow.inputMask(sides), BatteryEnergyFlow.outputMask(sides)).isEmpty());
    }

    @Test
    void onlyOutputFacesEmitAndChangingModesClearsTheFlow() {
        var sides = new SideConfig(Set.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT), true, side -> SideMode.NONE);
        sides.set(Direction.UP, SideMode.INPUT);
        assertTrue(BatteryEnergyFlow.create(10, 1, 0, BatteryEnergyFlow.outputMask(sides)).isEmpty());
        sides.set(Direction.UP, SideMode.OUTPUT);
        int oneOutput = BatteryEnergyFlow.create(10, 1, 0, BatteryEnergyFlow.outputMask(sides)).size();
        assertTrue(oneOutput > 0);
        sides.set(Direction.WEST, SideMode.OUTPUT);
        assertEquals(oneOutput * 2, BatteryEnergyFlow.create(10, 1, 0, BatteryEnergyFlow.outputMask(sides)).size());
        sides.clear();
        assertTrue(BatteryEnergyFlow.create(10, 1, 0, BatteryEnergyFlow.outputMask(sides)).isEmpty());
    }

    @Test
    void pathsLeaveEveryCoreSizeAndConvergeThroughAllSixOpenings() {
        for (Direction side : Direction.values()) {
            Point axis = new Point(side.getStepX(), side.getStepY(), side.getStepZ());
            for (float scale : new float[]{0.12F, 0.5F, 1}) {
                for (double phase : new double[]{0, 1, 2, 3, 4, 5}) {
                    Point start = BatteryEnergyFlow.point(side, scale, 0, phase);
                    assertEquals(BatterySphereMesh.RADIUS * scale + 0.003, Math.sqrt(start.dot(start)), 1e-9);
                    assertEquals(axis.scale(0.496), BatteryEnergyFlow.point(side, scale, 1, phase));
                    double previousDepth = 0;
                    for (int step = 0; step <= 100; step++) {
                        Point point = BatteryEnergyFlow.point(side, scale, step / 100.0, phase);
                        double depth = point.dot(axis);
                        assertTrue(depth > previousDepth);
                        previousDepth = depth;
                        // Include the glow width; no particles should escape the block or hit the metal bore.
                        assertTrue(Math.abs(point.x()) <= 0.496 && Math.abs(point.y()) <= 0.496 && Math.abs(point.z()) <= 0.496);
                        if (depth >= 13.85 / 16 - 0.5) {
                            Point radial = point.subtract(axis.scale(depth));
                            assertTrue(Math.sqrt(radial.dot(radial)) + 0.007 < 2.0 / 16);
                        }
                    }
                }
            }
        }
    }

    @Test
    void trailsMoveOutwardWithoutSpanningTheAnimationWrap() {
        int allFaces = (1 << Direction.values().length) - 1;
        for (double time : new double[]{0, 0.01, 10.5, 39.99, 40}) {
            var flow = BatteryEnergyFlow.create(time, 1, 0, allFaces);
            assertFalse(flow.isEmpty());
            for (var segment : flow) {
                Point delta = segment.b().subtract(segment.a());
                assertTrue(delta.dot(delta) > 0 && delta.dot(delta) < 0.025 * 0.025);
                assertTrue(segment.b().dot(segment.b()) > segment.a().dot(segment.a()));
            }
        }
        var beginning = BatteryEnergyFlow.create(0, 1, 0, allFaces);
        var wrapped = BatteryEnergyFlow.create(40, 1, 0, allFaces);
        assertEquals(beginning.size(), wrapped.size());
        for (int i = 0; i < beginning.size(); i++) {
            Point delta = beginning.get(i).a().subtract(wrapped.get(i).a());
            assertEquals(0, delta.dot(delta), 1e-20);
        }
    }
}
