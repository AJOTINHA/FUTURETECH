package dev.futuretech.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** All hinges and the wrist share one swivel frame, independent of placement direction. */
final class AssemblerArmPose {
    static final Vec3 SHOULDER = new Vec3(.5, .55, .5);

    record Pose(Vec3 hand, float swivel) {}

    static Pose waypoint(Vec3 hand, float previousSwivel) {
        Vec3 delta = hand.subtract(SHOULDER);
        return new Pose(hand, delta.x * delta.x + delta.z * delta.z < 1e-10
                ? previousSwivel : (float)Math.atan2(-delta.x, -delta.z));
    }

    /** Interpolate the swivel itself, rather than deriving it from a line crossing the base. */
    static Pose travel(Pose from, Pose to, float amount) {
        double t = Math.clamp(amount, 0, 1);
        if (t == 0) return from;
        if (t == 1) return to;
        double delta = Math.atan2(Math.sin(to.swivel - from.swivel), Math.cos(to.swivel - from.swivel));
        // Opposite targets have two equal routes; choose the same one on every frame/reload.
        if (Math.abs(Math.abs(delta) - Math.PI) < 1e-6) delta = Math.PI;
        double angle = from.swivel + delta * t;
        double radiusFrom = Math.hypot(from.hand.x - SHOULDER.x, from.hand.z - SHOULDER.z);
        double radiusTo = Math.hypot(to.hand.x - SHOULDER.x, to.hand.z - SHOULDER.z);
        double radius = radiusFrom + (radiusTo - radiusFrom) * t;
        double height = from.hand.y + (to.hand.y - from.hand.y) * t + Math.sin(Math.PI * t) * .45;
        return new Pose(new Vec3(SHOULDER.x - Math.sin(angle) * radius, height,
                SHOULDER.z - Math.cos(angle) * radius), (float)angle);
    }

    static float swivel(Vec3 hand, Direction facing) {
        Vec3 delta = hand.subtract(SHOULDER);
        double x = delta.x, z = delta.z;
        if (x * x + z * z < 1e-10) { x = facing.getStepX(); z = facing.getStepZ(); }
        return (float)Math.atan2(-x, -z);
    }

    static Vec3 elbow(Vec3 hand, float swivel) {
        Vec3 delta = hand.subtract(SHOULDER);
        Vec3 perpendicular = new Vec3(-delta.x * delta.y, delta.x * delta.x + delta.z * delta.z, -delta.z * delta.y).normalize();
        if (perpendicular.lengthSqr() < .01) perpendicular = new Vec3(Math.sin(swivel), 0, Math.cos(swivel));
        return SHOULDER.add(delta.scale(.5)).add(perpendicular.scale(.72));
    }

    static Quaternionf linkRotation(Vec3 from, Vec3 to, float swivel) {
        Vec3 delta = to.subtract(from);
        double localZ = Math.sin(swivel) * delta.x + Math.cos(swivel) * delta.z;
        return new Quaternionf().rotationY(swivel).rotateX((float)Math.atan2(localZ, delta.y));
    }

    static boolean working(int phase, float tick, int duration) {
        return phase == 2 && tick >= 20 && tick < duration + 20;
    }

    static float drillAngle(int phase, float tick, int duration) {
        if (phase != 2) return 0;
        return (Math.clamp(tick - 20, 0, duration) * 36) % 360;
    }
}
