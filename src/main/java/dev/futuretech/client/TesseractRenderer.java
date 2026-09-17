package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.futuretech.block.entity.TesseractBlockEntity;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The cube inside the tesseract's frame: the End's own portal, the way an end portal block is
 * drawn, on the six faces of a cube set back from the frame's bars. The frame is the block model;
 * this is only what fills it, and it needs nothing from the block entity to draw.
 */
public final class TesseractRenderer implements BlockEntityRenderer<TesseractBlockEntity, BlockEntityRenderState> {
    /**
     * The cube fills the frame to its bars, which stand a quarter pixel in from the faces; what
     * would poke into the corner blocks is inside them, where nothing shows.
     */
    private static final float INNER = 2.75F / 16F;
    private static final Vector3fc FROM = new Vector3f(INNER, INNER, INNER);
    private static final Vector3fc TO = new Vector3f(1 - INNER, 1 - INNER, 1 - INNER);
    private static final List<Vector3fc> VERTICES = vertices();

    private static List<Vector3fc> vertices() {
        List<Vector3fc> vertices = new ArrayList<>(24);
        for (Direction direction : Direction.values()) {
            FaceInfo face = FaceInfo.fromFacing(direction);
            for (int corner = 0; corner < 4; corner++) vertices.add(face.getVertexInfo(corner).select(FROM, TO));
        }
        return List.copyOf(vertices);
    }

    @Override
    public BlockEntityRenderState createRenderState() { return new BlockEntityRenderState(); }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(pose, RenderTypes.endPortal(), (entry, buffer) -> {
            for (Vector3fc vertex : VERTICES) buffer.addVertex(entry, vertex);
        });
    }
}
