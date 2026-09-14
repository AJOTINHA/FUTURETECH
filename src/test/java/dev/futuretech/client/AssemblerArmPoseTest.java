package dev.futuretech.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssemblerArmPoseTest {
    @Test
    void oppositeTargetsTurnAtAConstantRateWithoutSnappingAtTheBase() {
        var from = AssemblerArmPose.waypoint(new Vec3(.5, 1.1, -1.5), 0);
        var to = AssemblerArmPose.waypoint(new Vec3(.5, 1.1, 2.5), 0);
        var previous = from;
        for (int i = 1; i <= 200; i++) {
            var current = AssemblerArmPose.travel(from, to, i / 200F);
            assertEquals(Math.PI / 200, angularDistance(previous.swivel(), current.swivel()), 1e-6);
            assertEquals(2, Math.hypot(current.hand().x - .5, current.hand().z - .5), 1e-6);
            assertTrue(current.hand().distanceTo(previous.hand()) < .04);
            checkPoseLinks(current);
            previous = current;
        }
        assertEquals(from, AssemblerArmPose.travel(from, to, 0));
        assertEquals(to, AssemblerArmPose.travel(from, to, 1));
    }

    @Test
    void crossingTheAngleBoundaryUsesTheShortRoute() {
        float start = (float)Math.toRadians(179), end = (float)Math.toRadians(-179);
        var from = new AssemblerArmPose.Pose(new Vec3(.5 - Math.sin(start), 1, .5 - Math.cos(start)), start);
        var to = new AssemblerArmPose.Pose(new Vec3(.5 - Math.sin(end), 1, .5 - Math.cos(end)), end);
        var halfway = AssemblerArmPose.travel(from, to, .5F);
        assertEquals(Math.toRadians(1), angularDistance(start, halfway.swivel()), 1e-6);
        assertEquals(Math.toRadians(1), angularDistance(halfway.swivel(), end), 1e-6);
    }

    @Test
    void aTargetAboveTheBaseRetainsItsOrientationBetweenTravelStages() {
        var from = AssemblerArmPose.waypoint(new Vec3(-1.5, 1.1, .5), 0);
        var above = AssemblerArmPose.waypoint(new Vec3(.5, 2.1, .5), from.swivel());
        var to = AssemblerArmPose.waypoint(new Vec3(2.5, 1.1, .5), above.swivel());
        var arrived = AssemblerArmPose.travel(from, above, 1);
        var departing = AssemblerArmPose.travel(above, to, 0);
        assertEquals(arrived, departing);
        assertEquals(0, angularDistance(from.swivel(), arrived.swivel()), 1e-6);
        assertTrue(angularDistance(departing.swivel(), AssemblerArmPose.travel(above, to, .001F).swivel()) < .004);
    }

    private static double angularDistance(float a, float b) {
        return Math.abs(Math.atan2(Math.sin(b - a), Math.cos(b - a)));
    }

    private static void checkPoseLinks(AssemblerArmPose.Pose pose) {
        Vec3 elbow = AssemblerArmPose.elbow(pose.hand(), pose.swivel());
        Vec3[] points = {AssemblerArmPose.SHOULDER, elbow, pose.hand()};
        for (int i = 0; i < 2; i++) assertVector(points[i + 1].subtract(points[i]).normalize(),
                AssemblerArmPose.linkRotation(points[i], points[i + 1], pose.swivel()).transform(new Vector3f(0, 1, 0)));
    }

    @Test
    void gripperAndBothHingesStayAlignedForEveryPlacementAndReachDirection() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 idle = new Vec3(.5 + facing.getStepX() * .28, 1.10, .5 + facing.getStepZ() * .28);
            float swivel = AssemblerArmPose.swivel(idle, facing);
            Vector3f forward = new Quaternionf().rotationY(swivel).transform(new Vector3f(0, 0, -1));
            assertVector(new Vec3(facing.getStepX(), 0, facing.getStepZ()), forward);
            checkLinks(idle, facing);
            for (double height : new double[]{.3, 1.12, 2.5}) for (double x : new double[]{-2, 0, 2}) for (double z : new double[]{-2, 0, 2}) {
                checkLinks(new Vec3(.5 + x, height, .5 + z), facing);
            }
        }
    }

    private static void checkLinks(Vec3 hand, Direction facing) {
        float swivel = AssemblerArmPose.swivel(hand, facing);
        Vec3 shoulder = AssemblerArmPose.SHOULDER, elbow = AssemblerArmPose.elbow(hand, swivel);
        Vec3[] points = {shoulder, elbow, hand};
        Vector3f wristAxis = new Quaternionf().rotationY(swivel).transform(new Vector3f(1, 0, 0));
        for (int i = 0; i < 2; i++) {
            Vec3 direction = points[i + 1].subtract(points[i]).normalize();
            Quaternionf rotation = AssemblerArmPose.linkRotation(points[i], points[i + 1], swivel);
            assertVector(direction, rotation.transform(new Vector3f(0, 1, 0)));
            Vector3f hingeAxis = rotation.transform(new Vector3f(1, 0, 0));
            assertEquals(0, hingeAxis.distance(wristAxis), 1e-5, "Links and gripper must share the same hinge axis");
        }
    }

    @Test
    void idleDrillDoesNotSpinRegardlessOfElapsedTimeSinceLoading() {
        for (float tick : new float[]{0, .5F, 1, 30, 59.5F, 60, 10000}) {
            assertEquals(0, AssemblerArmPose.drillAngle(0, tick, 80));
            assertFalse(AssemblerArmPose.working(0, tick, 80));
        }
    }

    @Test
    void drillTurnsOnlyBetweenApproachAndRetraction() {
        assertEquals(0, AssemblerArmPose.drillAngle(2, 19.5F, 37));
        assertFalse(AssemblerArmPose.working(2, 19.5F, 37));
        assertTrue(AssemblerArmPose.working(2, 20.5F, 37));
        assertEquals(18, AssemblerArmPose.drillAngle(2, 20.5F, 37));
        assertFalse(AssemblerArmPose.working(2, 57, 37));
        assertEquals(AssemblerArmPose.drillAngle(2, 57, 37), AssemblerArmPose.drillAngle(2, 77, 37));
    }

    private static void assertVector(Vec3 expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-5);
        assertEquals(expected.y, actual.y, 1e-5);
        assertEquals(expected.z, actual.z, 1e-5);
    }
}
