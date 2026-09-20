package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.FutureTech;
import dev.futuretech.block.WirelessRedstoneBlock;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import dev.futuretech.client.WirelessHeadMesh.Face;
import dev.futuretech.client.WirelessHeadMesh.Vertex;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The parts of a wireless plate that the model cannot hold: the receiver's dish, and the hedron
 * that floats over the mast of both of them, turning. The plate, the mast and the receiver's arm
 * are an ordinary block model; this only adds what is not made of boxes.
 */
public final class WirelessRedstoneRenderer implements BlockEntityRenderer<WirelessRedstoneBlockEntity, WirelessRedstoneRenderer.State> {
    private static final Identifier DISH = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/wireless_dish.png");
    private static final Identifier HEDRON = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/wireless_hedron.png");
    private static final Identifier DIGITS = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/wireless_digits.png");
    /** The ink the frequency is written in: the dark of the lamp's own frame, against the stone. */
    private static final int INK = 0xFF1A1A1E;
    /** The hedron carries its own light, the way a lit crystal would: it does not go dark in a cellar. */
    private static final int FULL_BRIGHT = 15728880;
    /** A turn every twenty seconds: there is always one, and it never asks to be watched. */
    private static final float SPIN_PER_TICK = 0.9F;
    private static final long SPIN_PERIOD = 400;

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.UP;
        /** Quarter turns of the plate on its face, which the dish and the hedron take with it. */
        int turned;
        boolean receiver;
        float spin;
        /** The number written on the plate's face, as the client last heard it. */
        int frequency;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(WirelessRedstoneBlockEntity plate, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(plate, state, partialTick, camera, breakProgress);
        var blockState = plate.getBlockState();
        state.facing = blockState.hasProperty(WirelessRedstoneBlock.FACING)
                ? blockState.getValue(WirelessRedstoneBlock.FACING) : Direction.UP;
        state.turned = blockState.hasProperty(WirelessRedstoneBlock.SPIN)
                ? blockState.getValue(WirelessRedstoneBlock.SPIN) : 0;
        state.receiver = plate.kind() == WirelessRedstoneBlock.Kind.RECEIVER;
        state.frequency = plate.frequency();
        var level = plate.getLevel();
        state.spin = level == null ? 0 : (Math.floorMod(level.getGameTime(), SPIN_PERIOD) + partialTick) * SPIN_PER_TICK;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        submitHead(pose, collector, state.facing, state.turned, state.receiver, state.spin, state.lightCoords);
        // The frequency written on the face, so a plate says what it is set to without being opened.
        var digits = WirelessHeadMesh.digits(state.frequency);
        pose.pushPose();
        turn(pose, state.facing, state.turned);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(DIGITS),
                (entry, buffer) -> draw(digits, entry, buffer, state.lightCoords, INK));
        pose.popPose();
    }

    /**
     * The dish and the hedron, wherever the pose puts them: the block's renderer draws them in the
     * world and the item's draws them in the hand. Everything is drawn with the plate lying on the
     * floor and then turned to the face it was mounted on, exactly as the blockstate turns the model.
     */
    static void submitHead(PoseStack pose, SubmitNodeCollector collector, Direction facing, int turned,
                           boolean receiver, float spin, int light) {
        submitHead(pose, collector, facing, turned, receiver, spin, light, 0xFFFFFFFF, false);
    }

    /** The head as a placement ghost: the same shapes, see-through, in the ghost's colour. */
    static void submitGhostHead(PoseStack pose, SubmitNodeCollector collector, Direction facing, int turned,
                                boolean receiver, int light, int colour) {
        submitHead(pose, collector, facing, turned, receiver, 0, light, colour, true);
    }

    private static void submitHead(PoseStack pose, SubmitNodeCollector collector, Direction facing, int turned,
                                   boolean receiver, float spin, int light, int colour, boolean translucent) {
        pose.pushPose();
        turn(pose, facing, turned);
        if (receiver) {
            collector.submitCustomGeometry(pose, translucent ? RenderTypes.entityTranslucent(DISH) : RenderTypes.entitySolid(DISH),
                    (entry, buffer) -> draw(WirelessHeadMesh.DISH, entry, buffer, light, colour));
        }
        float[] centre = receiver ? WirelessHeadMesh.RECEIVER_HEDRON : WirelessHeadMesh.TRANSMITTER_HEDRON;
        pose.pushPose();
        pose.translate(centre[0], centre[1], centre[2]);
        // Leaned first and turned after, so the receiver's hedron spins about the dish's own line
        // rather than about the world's, and keeps lying along the dish while it turns.
        pose.mulPose(Axis.XP.rotationDegrees(receiver ? WirelessHeadMesh.RECEIVER_HEDRON_LEAN
                : WirelessHeadMesh.TRANSMITTER_HEDRON_LEAN));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        collector.submitCustomGeometry(pose, translucent ? RenderTypes.entityTranslucent(HEDRON) : RenderTypes.entitySolid(HEDRON),
                (entry, buffer) -> draw(WirelessHeadMesh.HEDRON, entry, buffer, FULL_BRIGHT, colour));
        pose.popPose();
        pose.popPose();
    }

    /**
     * The turn the blockstate gives the model, as a pose: the block's own angles, about the middle
     * of the block, the y first, then the x, then the quarter baked into the model — which is what
     * those come to once they are applied.
     */
    private static void turn(PoseStack pose, Direction facing, int turned) {
        int[] angles = WirelessRedstoneBlock.angles(facing, turned);
        pose.translate(0.5F, 0.5F, 0.5F);
        // A model's turns go clockwise, which is the other way round from a pose's.
        pose.mulPose(Axis.YP.rotationDegrees(-angles[2]));
        pose.mulPose(Axis.XP.rotationDegrees(-angles[1]));
        pose.mulPose(Axis.YP.rotationDegrees(-angles[0]));
        pose.translate(-0.5F, -0.5F, -0.5F);
    }

    private static void draw(List<Face> faces, PoseStack.Pose pose, VertexConsumer buffer, int light) {
        draw(faces, pose, buffer, light, 0xFFFFFFFF);
    }

    private static void draw(List<Face> faces, PoseStack.Pose pose, VertexConsumer buffer, int light, int colour) {
        for (Face face : faces) {
            float nx = (face.b().y() - face.a().y()) * (face.d().z() - face.a().z())
                    - (face.b().z() - face.a().z()) * (face.d().y() - face.a().y());
            float ny = (face.b().z() - face.a().z()) * (face.d().x() - face.a().x())
                    - (face.b().x() - face.a().x()) * (face.d().z() - face.a().z());
            float nz = (face.b().x() - face.a().x()) * (face.d().y() - face.a().y())
                    - (face.b().y() - face.a().y()) * (face.d().x() - face.a().x());
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0) {
                nx /= length;
                ny /= length;
                nz /= length;
            }
            vertex(pose, buffer, face.a(), light, nx, ny, nz, colour);
            vertex(pose, buffer, face.b(), light, nx, ny, nz, colour);
            vertex(pose, buffer, face.c(), light, nx, ny, nz, colour);
            vertex(pose, buffer, face.d(), light, nx, ny, nz, colour);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vertex vertex, int light,
                               float nx, float ny, float nz, int colour) {
        buffer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(colour)
                .setUv(vertex.u(), vertex.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
