package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.FutureTech;
import dev.futuretech.block.MiningMarkerBlock;
import dev.futuretech.block.entity.MiningMarkerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lines between markers. An edge — two markers sharing an axis within reach — is drawn solid,
 * so a closed frame reads as the square the quarry will dig inside of. A marker with a redstone
 * signal also draws its empty directions, fainter and running the full reach, as a straight line
 * showing where the next corner may be stood.
 */
public final class MiningMarkerRenderer
        implements BlockEntityRenderer<MiningMarkerBlockEntity, MiningMarkerRenderer.State> {
    /** A plain white texture, so the vertex colour is the colour. */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/battery_sphere_white.png");
    /** The height of the torch's head, where the lines leave from. */
    private static final float HEAD = 0.6F;
    private static final float EDGE_HALF_WIDTH = 0.03F;
    private static final float GUIDE_HALF_WIDTH = 0.02F;
    /** The blue of the marker's own flame, and the deeper blue the guide runs in. */
    private static final int EDGE_COLOUR = 0x55C8FF;
    private static final int GUIDE_COLOUR = 0x1676C4;
    private static final float EDGE_ALPHA = 0.85F;
    private static final float GUIDE_ALPHA = 0.30F;
    private static final int FULL_BRIGHT = 15728880;

    public static final class State extends BlockEntityRenderState {
        boolean powered;
        /** Distance to the linked marker per horizontal direction, by {@code get2DDataValue}; 0 is none. */
        final int[] links = new int[4];
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(MiningMarkerBlockEntity marker, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(marker, state, partialTick, camera, breakProgress);
        var blockState = marker.getBlockState();
        state.powered = blockState.hasProperty(MiningMarkerBlock.POWERED) && blockState.getValue(MiningMarkerBlock.POWERED);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state.links[side.get2DDataValue()] = marker.link(side);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        boolean anything = state.powered;
        for (int link : state.links) anything |= link > 0;
        if (!anything) return;
        pose.pushPose();
        pose.translate(0.5, HEAD, 0.5);
        int[] links = state.links.clone();
        boolean powered = state.powered;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, buffer) -> {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int link = links[side.get2DDataValue()];
                if (link > 0) {
                    // An edge only needs drawing once, by the marker on the low side of the axis.
                    if (side == Direction.NORTH || side == Direction.WEST) continue;
                    beam(p, buffer, side, link, EDGE_HALF_WIDTH, EDGE_COLOUR, EDGE_ALPHA);
                } else if (powered) {
                    beam(p, buffer, side, MiningMarkerBlockEntity.LINK_RANGE, GUIDE_HALF_WIDTH, GUIDE_COLOUR, GUIDE_ALPHA);
                }
            }
        });
        pose.popPose();
    }

    /**
     * A square tube of four faces running {@code length} blocks along {@code side}, drawn both ways
     * round so it shows from inside and out, and fading to nothing at its far end.
     */
    private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Direction side, float length,
                             float half, int colour, float alpha) {
        float dx = side.getStepX();
        float dz = side.getStepZ();
        // Each corner of the tube, as how far it sits across the line and how far above it.
        float[][] ring = {{half, half}, {half, -half}, {-half, -half}, {-half, half}};
        int near = (Math.round(alpha * 255) << 24) | colour;
        int far = colour;
        for (int corner = 0; corner < 4; corner++) {
            float[] a = ring[corner];
            float[] b = ring[(corner + 1) % 4];
            face(pose, buffer, a, b, dx, dz, length, near, far);
            face(pose, buffer, b, a, dx, dz, length, near, far);
        }
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer, float[] a, float[] b,
                             float dx, float dz, float length, int near, int far) {
        point(pose, buffer, dx, dz, 0, a, near);
        point(pose, buffer, dx, dz, 0, b, near);
        point(pose, buffer, dx, dz, length, b, far);
        point(pose, buffer, dx, dz, length, a, far);
    }

    /** One corner of the tube {@code along} blocks down the line, offset across it and up from it. */
    private static void point(PoseStack.Pose pose, VertexConsumer buffer, float dx, float dz,
                              float along, float[] offset, int colour) {
        vertex(pose, buffer, dx * along - dz * offset[0], offset[1], dz * along + dx * offset[0], colour);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int colour) {
        buffer.addVertex(pose, x, y, z).setColor(colour).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** The lines run far past the block, so the marker leaving the screen must not cull them. */
    @Override
    public AABB getRenderBoundingBox(MiningMarkerBlockEntity marker) {
        int reach = MiningMarkerBlockEntity.LINK_RANGE;
        return new AABB(marker.getBlockPos()).inflate(reach, 1, reach);
    }
}
