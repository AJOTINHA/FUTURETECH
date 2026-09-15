package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.FutureTech;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.client.BatterySphereMesh.Point;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A rotating tier-colored lattice with spiral energy trails following configured input and output faces.
 *
 * <p>The sphere is some six thousand quads. Everything that never changes shape — the shell, the
 * lattice, its halo and the two rings — is baked once into flat float arrays of positions and
 * normals, so a frame copies numbers into the vertex buffer and allocates nothing; the earlier
 * per-vertex {@code Point} maths cost a square root and an object for each of the ~25 000
 * vertices of every battery on screen, every frame. Only the energy trails, which move, are still
 * built per frame, and beyond {@link #DETAIL_DISTANCE} blocks the halo and the trails are left
 * out, since they are invisible from there anyway.
 */
public final class BatterySphereRenderer implements BlockEntityRenderer<BatteryBlockEntity, BatterySphereRenderer.State> {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/battery_sphere_white.png");
    private static final BatterySphereMesh.Mesh MESH = BatterySphereMesh.create();
    private static final int FULL_BRIGHT = 15728880;
    /** Past this many blocks the halo and the energy trails are skipped. */
    private static final double DETAIL_DISTANCE = 24;
    private static final int SHELL_COLOR = 0xFF0A293A;
    private static final int HALO_ALPHA = 0x40;
    private static final int LINES_ALPHA = 0xFF;

    private static final Baked SHELL = Baked.radial(shell());
    private static final Baked HALO = Baked.radial(lattice(0.006, 0.267, 0.008));
    private static final Baked LINES = Baked.radial(lattice(0.0018, 0.269, 0.0036));
    private static final Baked INNER_RING = Baked.flat(ring(BatteryStabilizerMesh.INNER_RADIUS));
    private static final Baked OUTER_RING = Baked.flat(ring(BatteryStabilizerMesh.OUTER_RADIUS));

    private record Quad(Point a, Point b, Point c, Point d, int color) {}

    /**
     * Quads flattened to {@code x y z nx ny nz} per vertex, four vertices per quad, plus one colour
     * per quad. Radial normals point out from the sphere's centre; flat ones follow the quad.
     */
    private record Baked(float[] vertices, int[] colors) {
        private static final int FLOATS_PER_VERTEX = 6;

        static Baked radial(List<Quad> quads) {
            return bake(quads, (quad, corner) -> corner.unit());
        }

        static Baked flat(List<Quad> quads) {
            return bake(quads, (quad, corner) -> quad.b.subtract(quad.a).cross(quad.d.subtract(quad.a)).unit());
        }

        private interface NormalOf { Point at(Quad quad, Point corner); }

        private static Baked bake(List<Quad> quads, NormalOf normalOf) {
            float[] vertices = new float[quads.size() * 4 * FLOATS_PER_VERTEX];
            int[] colors = new int[quads.size()];
            int at = 0;
            for (int i = 0; i < quads.size(); i++) {
                Quad quad = quads.get(i);
                colors[i] = quad.color;
                for (Point corner : new Point[]{quad.a, quad.b, quad.c, quad.d}) {
                    Point normal = normalOf.at(quad, corner);
                    vertices[at++] = (float) corner.x(); vertices[at++] = (float) corner.y(); vertices[at++] = (float) corner.z();
                    vertices[at++] = (float) normal.x(); vertices[at++] = (float) normal.y(); vertices[at++] = (float) normal.z();
                }
            }
            return new Baked(vertices, colors);
        }

        /** Draws every quad in {@code color}, or in its own colour when {@code color} is 0. */
        void draw(PoseStack.Pose pose, VertexConsumer buffer, int color, int light) {
            float[] v = vertices;
            int at = 0;
            for (int quad = 0; quad < colors.length; quad++) {
                int tint = color != 0 ? color : colors[quad];
                for (int corner = 0; corner < 4; corner++) {
                    buffer.addVertex(pose, v[at], v[at + 1], v[at + 2]).setColor(tint).setUv(0.5F, 0.5F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, v[at + 3], v[at + 4], v[at + 5]);
                    at += FLOATS_PER_VERTEX;
                }
            }
        }
    }

    public static final class State extends BlockEntityRenderState {
        float rotation;
        float scale;
        float ringRotation;
        float pulse;
        int lineColor;
        boolean detailed;
        List<BatteryEnergyFlow.Segment> energyFlow = List.of();
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(BatteryBlockEntity battery, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(battery, state, partialTick, camera, breakProgress);
        state.lineColor = battery.tier().lineColor();
        state.scale = 0.12F + 0.88F * battery.visualCharge(partialTick);
        long time = battery.getLevel() == null ? 0 : battery.getLevel().getGameTime();
        state.rotation = (Math.floorMod(time, 400) + partialTick) * 0.9F;
        state.ringRotation = state.rotation;
        double pulseTime = Math.floorMod(time, 60) + partialTick;
        state.pulse = (float) (0.5 - 0.5 * Math.cos(pulseTime * Math.PI * 2 / 60));
        var pos = battery.getBlockPos();
        state.detailed = camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < DETAIL_DISTANCE * DETAIL_DISTANCE;
        if (!state.detailed) {
            state.energyFlow = List.of();
            return;
        }
        double flowTime = Math.floorMod(time, (long) BatteryEnergyFlow.PERIOD) + partialTick;
        state.energyFlow = BatteryEnergyFlow.create(flowTime, state.scale,
                BatteryEnergyFlow.inputMask(battery.sideConfig()), BatteryEnergyFlow.outputMask(battery.sideConfig()));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.pushPose();
        pose.scale(state.scale, state.scale, state.scale);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(12));
        pose.mulPose(Axis.YP.rotationDegrees(state.rotation));
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> SHELL.draw(p, b, SHELL_COLOR, FULL_BRIGHT));
        // The pulse only varies the light: one colour for the whole lattice, one for the halo.
        float brightness = 0.55F + 0.45F * state.pulse;
        int lines = pulsing(state.lineColor, brightness, LINES_ALPHA);
        if (state.detailed) {
            int halo = pulsing(state.lineColor, brightness, Math.round(HALO_ALPHA * (0.3F + 0.7F * state.pulse)));
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, b) -> HALO.draw(p, b, halo, FULL_BRIGHT));
        }
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, b) -> LINES.draw(p, b, lines, FULL_BRIGHT));
        pose.popPose();
        // Distinct speeds prevent the inner ring from appearing locked to the rotating core.
        submitRing(pose, collector, state.ringRotation * 4, 52, INNER_RING, state.lightCoords);
        submitRing(pose, collector, -state.ringRotation * 3 + 90, -58, OUTER_RING, state.lightCoords);
        pose.popPose();
        // The core shrinks with charge, but the endpoints stay fixed at the block's ports.
        if (!state.energyFlow.isEmpty()) {
            var flow = state.energyFlow;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE),
                    (p, b) -> drawFlow(flow, p, b));
        }
        pose.popPose();
    }

    private static int pulsing(int tint, float brightness, int alpha) {
        int red = Math.round((tint >> 16 & 255) * brightness);
        int green = Math.round((tint >> 8 & 255) * brightness);
        int blue = Math.round((tint & 255) * brightness);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void submitRing(PoseStack pose, SubmitNodeCollector collector, float angle, float tilt,
                                   Baked ring, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.YP.rotationDegrees(angle * 4));
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> ring.draw(p, b, 0, light));
        pose.popPose();
    }

    private static void drawFlow(List<BatteryEnergyFlow.Segment> segments, PoseStack.Pose pose, VertexConsumer buffer) {
        for (var segment : segments) {
            Point tangent = segment.b().subtract(segment.a()).unit();
            Point reference = Math.abs(tangent.y()) < 0.9 ? new Point(0, 1, 0) : new Point(1, 0, 0);
            Point u = reference.cross(tangent).unit();
            Point v = tangent.cross(u).unit();
            int glow = ((segment.color() >>> 24) / 5) << 24 | (segment.color() & 0xFFFFFF);
            // Crossed, double-sided ribbons stay visible from every view, including through the bore.
            flowRibbon(segment, u.scale(0.007), glow, pose, buffer);
            flowRibbon(segment, v.scale(0.007), glow, pose, buffer);
            flowRibbon(segment, u.scale(0.002), segment.color(), pose, buffer);
            flowRibbon(segment, v.scale(0.002), segment.color(), pose, buffer);
        }
    }

    private static void flowRibbon(BatteryEnergyFlow.Segment segment, Point offset, int color,
                                   PoseStack.Pose pose, VertexConsumer buffer) {
        Point a = segment.a().subtract(offset), b = segment.b().subtract(offset);
        Point c = segment.b().add(offset), d = segment.a().add(offset);
        vertex(pose, buffer, a, color); vertex(pose, buffer, b, color);
        vertex(pose, buffer, c, color); vertex(pose, buffer, d, color);
        vertex(pose, buffer, d, color); vertex(pose, buffer, c, color);
        vertex(pose, buffer, b, color); vertex(pose, buffer, a, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Point p, int color) {
        Point n = p.unit();
        buffer.addVertex(pose, (float) p.x(), (float) p.y(), (float) p.z()).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose, (float) n.x(), (float) n.y(), (float) n.z());
    }

    private static List<Quad> ring(double radius) {
        return BatteryStabilizerMesh.create(radius).stream()
                .map(q -> new Quad(q.a(), q.b(), q.c(), q.d(), q.color())).toList();
    }

    private static List<Quad> shell() {
        List<Quad> quads = new ArrayList<>();
        for (var cell : MESH.cells()) {
            Point centre = cell.centre().scale(BatterySphereMesh.RADIUS);
            for (int i = 0; i < cell.corners().size(); i++) {
                Point a = MESH.nodes().get(cell.corners().get(i)).scale(BatterySphereMesh.RADIUS);
                Point b = MESH.nodes().get(cell.corners().get((i + 1) % cell.corners().size())).scale(BatterySphereMesh.RADIUS);
                quads.add(new Quad(centre, a, b, b, SHELL_COLOR));
            }
        }
        return List.copyOf(quads);
    }

    /** Ribbons along every edge and a small square on every node; the colour is applied when drawn. */
    private static List<Quad> lattice(double width, double radius, double nodeSize) {
        List<Quad> quads = new ArrayList<>();
        for (var edge : MESH.edges()) {
            Point a = MESH.nodes().get(edge.a()), b = MESH.nodes().get(edge.b());
            Point middle = a.add(b).unit();
            ribbon(quads, a.scale(radius), middle.scale(radius), width);
            ribbon(quads, middle.scale(radius), b.scale(radius), width);
        }
        for (Point node : MESH.nodes()) {
            Point ref = Math.abs(node.y()) < 0.9 ? new Point(0, 1, 0) : new Point(1, 0, 0);
            Point u = ref.cross(node).unit().scale(nodeSize);
            Point v = node.cross(u).unit().scale(nodeSize);
            Point c = node.scale(radius + 0.001);
            quads.add(new Quad(c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v), 0xFFFFFFFF));
        }
        return List.copyOf(quads);
    }

    private static void ribbon(List<Quad> quads, Point a, Point b, double width) {
        Point normal = a.add(b).unit();
        Point offset = normal.cross(b.subtract(a)).unit().scale(width);
        quads.add(new Quad(a.subtract(offset), b.subtract(offset), b.add(offset), a.add(offset), 0xFFFFFFFF));
    }
}
