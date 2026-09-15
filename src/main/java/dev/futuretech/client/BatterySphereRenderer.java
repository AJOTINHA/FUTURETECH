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

/** A rotating tier-colored lattice with spiral energy trails following configured input and output faces. */
public final class BatterySphereRenderer implements BlockEntityRenderer<BatteryBlockEntity, BatterySphereRenderer.State> {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/battery_sphere_white.png");
    private static final BatterySphereMesh.Mesh MESH = BatterySphereMesh.create();
    private static final List<Quad> SHELL = shell();
    private static final List<Quad> HALO = lattice(0.006, 0.267, 0x40FFFFFF, 0.008);
    private static final List<Quad> LINES = lattice(0.0018, 0.269, 0xFFFFFFFF, 0.0036);
    private static final List<Quad> INNER_RING = ring(BatteryStabilizerMesh.INNER_RADIUS);
    private static final List<Quad> OUTER_RING = ring(BatteryStabilizerMesh.OUTER_RADIUS);
    private record Quad(Point a, Point b, Point c, Point d, int color) {}

    public static final class State extends BlockEntityRenderState {
        float rotation;
        float scale;
        float ringRotation;
        float pulse;
        int lineColor;
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
        state.rotation = battery.getLevel() == null ? 0 :
                (Math.floorMod(battery.getLevel().getGameTime(), 400) + partialTick) * 0.9F;
        state.ringRotation = battery.getLevel() == null ? 0 :
                (Math.floorMod(battery.getLevel().getGameTime(), 400) + partialTick) * 0.9F;
        double pulseTime = battery.getLevel() == null ? 0 :
                Math.floorMod(battery.getLevel().getGameTime(), 60) + partialTick;
        state.pulse = (float)(0.5 - 0.5 * Math.cos(pulseTime * Math.PI * 2 / 60));
        double flowTime = battery.getLevel() == null ? 0 :
                Math.floorMod(battery.getLevel().getGameTime(), (long)BatteryEnergyFlow.PERIOD) + partialTick;
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
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> draw(SHELL, p, b));
        float brightness = 0.55F + 0.45F * state.pulse;
        float haloAlpha = 0.3F + 0.7F * state.pulse;
        int lineColor = state.lineColor;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE),
                (p, b) -> drawPulsing(HALO, p, b, brightness, haloAlpha, lineColor));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE),
                (p, b) -> drawPulsing(LINES, p, b, brightness, 1, lineColor));
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

    private static List<Quad> ring(double radius) {
        return BatteryStabilizerMesh.create(radius).stream()
                .map(q -> new Quad(q.a(), q.b(), q.c(), q.d(), q.color())).toList();
    }

    private static void submitRing(PoseStack pose, SubmitNodeCollector collector, float angle, float tilt,
                                   List<Quad> ring, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.YP.rotationDegrees(angle * 4));
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> drawMetal(ring, p, b, light));
        pose.popPose();
    }

    private static void drawMetal(List<Quad> quads, PoseStack.Pose pose, VertexConsumer buffer, int light) {
        for (Quad q : quads) {
            Point normal = q.b.subtract(q.a).cross(q.d.subtract(q.a)).unit();
            metalVertex(pose, buffer, q.a, normal, q.color, light);
            metalVertex(pose, buffer, q.b, normal, q.color, light);
            metalVertex(pose, buffer, q.c, normal, q.color, light);
            metalVertex(pose, buffer, q.d, normal, q.color, light);
        }
    }

    private static void metalVertex(PoseStack.Pose pose, VertexConsumer buffer, Point p, Point n, int color, int light) {
        buffer.addVertex(pose, (float)p.x(), (float)p.y(), (float)p.z()).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float)n.x(), (float)n.y(), (float)n.z());
    }

    private static List<Quad> shell() {
        List<Quad> quads = new ArrayList<>();
        for (var cell : MESH.cells()) {
            Point centre = cell.centre().scale(BatterySphereMesh.RADIUS);
            for (int i = 0; i < cell.corners().size(); i++) {
                Point a = MESH.nodes().get(cell.corners().get(i)).scale(BatterySphereMesh.RADIUS);
                Point b = MESH.nodes().get(cell.corners().get((i + 1) % cell.corners().size())).scale(BatterySphereMesh.RADIUS);
                quads.add(new Quad(centre, a, b, b, 0xFF0A293A));
            }
        }
        return List.copyOf(quads);
    }

    private static List<Quad> lattice(double width, double radius, int color, double nodeSize) {
        List<Quad> quads = new ArrayList<>();
        for (var edge : MESH.edges()) {
            Point a = MESH.nodes().get(edge.a()), b = MESH.nodes().get(edge.b());
            Point middle = a.add(b).unit();
            ribbon(quads, a.scale(radius), middle.scale(radius), width, color);
            ribbon(quads, middle.scale(radius), b.scale(radius), width, color);
        }
        for (Point node : MESH.nodes()) {
            Point ref = Math.abs(node.y()) < 0.9 ? new Point(0,1,0) : new Point(1,0,0);
            Point u = ref.cross(node).unit().scale(nodeSize);
            Point v = node.cross(u).unit().scale(nodeSize);
            Point c = node.scale(radius + 0.001);
            int nodeColor = (color & 0xFF000000) | 0xFFFFFF;
            quads.add(new Quad(c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v), nodeColor));
        }
        return List.copyOf(quads);
    }

    private static void ribbon(List<Quad> quads, Point a, Point b, double width, int color) {
        Point normal = a.add(b).unit();
        Point offset = normal.cross(b.subtract(a)).unit().scale(width);
        quads.add(new Quad(a.subtract(offset), b.subtract(offset), b.add(offset), a.add(offset), color));
    }

    private static void draw(List<Quad> quads, PoseStack.Pose pose, VertexConsumer buffer) {
        for (Quad q : quads) {
            vertex(pose, buffer, q.a, q.color); vertex(pose, buffer, q.b, q.color);
            vertex(pose, buffer, q.c, q.color); vertex(pose, buffer, q.d, q.color);
        }
    }

    /** Varies light intensity only; geometry, stored charge and metal rings stay independent. */
    private static void drawPulsing(List<Quad> quads, PoseStack.Pose pose, VertexConsumer buffer,
                                    float brightness, float opacity, int tint) {
        for (Quad q : quads) {
            int alpha = Math.round((q.color >>> 24) * opacity);
            int red = Math.round((tint >> 16 & 255) * brightness);
            int green = Math.round((tint >> 8 & 255) * brightness);
            int blue = Math.round((tint & 255) * brightness);
            int color = alpha << 24 | red << 16 | green << 8 | blue;
            vertex(pose, buffer, q.a, color); vertex(pose, buffer, q.b, color);
            vertex(pose, buffer, q.c, color); vertex(pose, buffer, q.d, color);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Point p, int color) {
        Point n = p.unit();
        buffer.addVertex(pose, (float)p.x(), (float)p.y(), (float)p.z()).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
                .setNormal(pose, (float)n.x(), (float)n.y(), (float)n.z());
    }
}
