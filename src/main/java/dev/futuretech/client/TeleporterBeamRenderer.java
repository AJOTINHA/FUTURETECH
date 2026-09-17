package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.FutureTech;
import dev.futuretech.block.TeleporterBlock;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The beam a teleporter shows while it is lit: a column in the chosen card's colour rising from
 * the pad and fading out, so a pad with a destination chosen can be told from across the room,
 * and which destination by its colour. It turns slowly, the way a beacon's does, and breathes a
 * little so it reads as light and not as a block. Whether it shows is the block state's, and its
 * colour rides on the pad's update packet; it goes out the tick the state does.
 */
public final class TeleporterBeamRenderer implements BlockEntityRenderer<TeleporterBlockEntity, TeleporterBeamRenderer.State> {
    /** A plain white texture, so the vertex colour is the colour; the battery's, which is nothing but white. */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/battery_sphere_white.png");
    /** How far the beam rises above the pad, in blocks. */
    private static final float HEIGHT = 3.0F;
    private static final float CORE_HALF_WIDTH = 0.08F;
    private static final float GLOW_HALF_WIDTH = 0.24F;
    private static final int FULL_BRIGHT = 15728880;

    public static final class State extends BlockEntityRenderState {
        boolean lit;
        /** The card's colour for the core, and a deeper cut of it for the glow around it. */
        int core;
        int glow;
        float turn;
        float breath;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(TeleporterBlockEntity teleporter, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(teleporter, state, partialTick, camera, breakProgress);
        state.lit = teleporter.getBlockState().getValue(TeleporterBlock.LIT);
        if (!state.lit || teleporter.getLevel() == null) return;
        state.core = teleporter.beamColour() & 0xFFFFFF;
        state.glow = ARGB.scaleRGB(state.core, 0.65F) & 0xFFFFFF;
        double time = Math.floorMod(teleporter.getLevel().getGameTime(), 360) + partialTick;
        state.turn = (float) time;
        state.breath = (float) (0.5 - 0.5 * Math.cos(time * Math.PI * 2 / 40));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.lit) return;
        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(state.turn));
        float breath = state.breath;
        int core = state.core;
        int glow = state.glow;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (p, b) -> {
            column(p, b, GLOW_HALF_WIDTH, glow, 0.30F + 0.10F * breath);
            column(p, b, CORE_HALF_WIDTH, core, 0.75F + 0.25F * breath);
        });
        pose.popPose();
    }

    /**
     * A square column of four faces, each drawn both ways round so it shows from inside and out,
     * solid at the base and gone at the top.
     */
    private static void column(PoseStack.Pose pose, VertexConsumer buffer, float half, int colour, float alpha) {
        int base = (Math.round(alpha * 255) << 24) | colour;
        int top = colour;
        float[][] corners = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            face(pose, buffer, a, b, base, top);
            face(pose, buffer, b, a, base, top);
        }
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer, float[] a, float[] b, int base, int top) {
        vertex(pose, buffer, a[0], 0, a[1], base);
        vertex(pose, buffer, b[0], 0, b[1], base);
        vertex(pose, buffer, b[0], HEIGHT, b[1], top);
        vertex(pose, buffer, a[0], HEIGHT, a[1], top);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int colour) {
        buffer.addVertex(pose, x, y, z).setColor(colour).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** The beam stands above the block, so the block being off screen must not cull it. */
    @Override
    public AABB getRenderBoundingBox(TeleporterBlockEntity teleporter) {
        return new AABB(teleporter.getBlockPos()).expandTowards(0, HEIGHT, 0);
    }
}
