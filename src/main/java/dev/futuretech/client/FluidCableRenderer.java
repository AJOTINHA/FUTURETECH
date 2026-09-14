package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.entity.FluidCableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Draws the fluid inside a see-through fluid cable: a column down the middle of the glass core and
 * an arm towards every connected face, in the fluid's own texture and tint. While the network
 * reports which way the fluid runs through this cable, the fluid's flowing texture slides along
 * that axis; otherwise the still texture stands. Only tiers that show their fluid draw anything,
 * and only while the network is carrying something.
 */
public final class FluidCableRenderer implements BlockEntityRenderer<FluidCableBlockEntity, FluidCableRenderer.State> {
    /** The glass core runs from 5 to 11; the fluid keeps half a unit inside it on every side. */
    private static final float INNER = 5.5F / 16F;
    private static final float OUTER = 10.5F / 16F;
    /** Texture lengths the stream slides per tick. */
    private static final float SPEED = 0.04F;

    public static final class State extends BlockEntityRenderState {
        @Nullable TextureAtlasSprite sprite;
        int color = -1;
        @Nullable Direction flow;
        float offset;
        /** Height of the fluid's surface in the block, from the body's bottom to its top. */
        float surface;
        final Set<Direction> arms = EnumSet.noneOf(Direction.class);
    }

    public FluidCableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(FluidCableBlockEntity cable, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(cable, state, partialTick, camera, breakProgress);
        state.sprite = null;
        state.arms.clear();
        if (!cable.tier().showsFluid() || cable.getLevel() == null) return;
        double time = cable.getLevel().getGameTime() + partialTick;
        float level = cable.drawnLevel(time);
        FluidStack fluid = cable.drawnFluid();
        if (fluid.isEmpty() || level <= 0) return;
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        state.flow = cable.shown().isEmpty() ? null : cable.flow();
        state.sprite = (state.flow == null ? model.stillMaterial() : model.flowingMaterial()).sprite();
        state.color = model.fluidTintSource() == null ? -1 : model.fluidTintSource().colorAsStack(fluid);
        state.offset = (float) ((time * SPEED) % 1.0);
        for (Direction side : Direction.values()) {
            if (cable.getBlockState().getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side))) state.arms.add(side);
        }
        // The surface runs from the lowest point of the body to its highest, so a cable with no
        // arm below starts draining at once and one with an arm above empties that first.
        float bottom = state.arms.contains(Direction.DOWN) ? 0 : INNER;
        float top = state.arms.contains(Direction.UP) ? 1 : OUTER;
        state.surface = bottom + level * (top - bottom);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) return;
        var stream = new Stream(sprite, ARGB.opaque(state.color), state.lightCoords, state.flow, state.offset, state.surface);
        Set<Direction> arms = EnumSet.copyOf(state.arms);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, buffer) -> {
            // The core and its arms are one body: neither draws the face where it meets the other,
            // so no two translucent faces share a plane and the seams stay invisible.
            stream.box(p, buffer, INNER, INNER, INNER, OUTER, OUTER, OUTER, arms);
            for (Direction side : arms) {
                float x0 = INNER, y0 = INNER, z0 = INNER, x1 = OUTER, y1 = OUTER, z1 = OUTER;
                switch (side) {
                    case WEST -> { x0 = 0; x1 = INNER; }
                    case EAST -> { x0 = OUTER; x1 = 1; }
                    case DOWN -> { y0 = 0; y1 = INNER; }
                    case UP -> { y0 = OUTER; y1 = 1; }
                    case NORTH -> { z0 = 0; z1 = INNER; }
                    case SOUTH -> { z0 = OUTER; z1 = 1; }
                }
                stream.box(p, buffer, x0, y0, z0, x1, y1, z1, EnumSet.of(side.getOpposite()));
            }
        });
    }

    /** The fluid's look for one frame: its sprite, tint, light, how far the stream has slid and how high it stands. */
    private record Stream(TextureAtlasSprite sprite, int color, int light, @Nullable Direction flow, float offset, float surface) {
        /**
         * A box with the faces in {@code skip} left out, cut off at the surface: whatever lies above
         * it is not drawn, and a box the surface cuts through gets a top face there.
         */
        void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                 Set<Direction> skip) {
            if (y0 >= surface) return;
            boolean cut = y1 > surface;
            if (cut) {
                y1 = surface;
                skip = EnumSet.copyOf(skip);
                skip.remove(Direction.UP);
            }
            if (!skip.contains(Direction.NORTH)) face(pose, buffer, 0, 0, -1, v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0), v(x0, y0, z0));
            if (!skip.contains(Direction.SOUTH)) face(pose, buffer, 0, 0, 1, v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1));
            if (!skip.contains(Direction.WEST)) face(pose, buffer, -1, 0, 0, v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0));
            if (!skip.contains(Direction.EAST)) face(pose, buffer, 1, 0, 0, v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1), v(x1, y0, z0));
            if (!skip.contains(Direction.DOWN)) face(pose, buffer, 0, -1, 0, v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1), v(x0, y0, z1));
            if (!skip.contains(Direction.UP)) face(pose, buffer, 0, 1, 0, v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0), v(x0, y1, z0));
        }

        private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

        /**
         * One face, corners in order with texture corners (0,0) (1,0) (1,1) (0,1). When the stream
         * runs along one of the face's edges the texture slides that way, split where it wraps.
         */
        private void face(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz,
                          Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
            float alongU = flow == null ? 0 : along(p0, p1);
            float alongV = flow == null ? 0 : along(p0, p3);
            if (alongU != 0) {
                slide(alongU, (a, b, ta, tb) -> quad(pose, buffer, nx, ny, nz,
                        lerp(p0, p1, a), lerp(p0, p1, b), lerp(p3, p2, b), lerp(p3, p2, a), ta, 0, tb, 0, tb, 1, ta, 1));
            } else if (alongV != 0) {
                slide(alongV, (a, b, ta, tb) -> quad(pose, buffer, nx, ny, nz,
                        lerp(p0, p3, a), lerp(p1, p2, a), lerp(p1, p2, b), lerp(p0, p3, b), 0, ta, 1, ta, 1, tb, 0, tb));
            } else {
                quad(pose, buffer, nx, ny, nz, p0, p1, p2, p3, 0, 0, 1, 0, 1, 1, 0, 1);
            }
        }

        /** +1 or -1 when the edge from {@code a} to {@code b} runs with or against the stream, else 0. */
        private float along(Vector3f a, Vector3f b) {
            Vector3f edge = new Vector3f(b).sub(a);
            var step = flow.getUnitVec3i();
            float dot = edge.x * step.getX() + edge.y * step.getY() + edge.z * step.getZ();
            return dot > 1e-6 ? 1 : dot < -1e-6 ? -1 : 0;
        }

        private interface Segment {
            void emit(float from, float to, float textureFrom, float textureTo);
        }

        /**
         * Walks the edge from 0 to 1 with the texture coordinate {@code dir * x - offset}, wrapped
         * into [0, 1]: as the offset grows the pattern moves the way the stream flows. The one
         * place the coordinate wraps splits the face in two.
         */
        private void slide(float dir, Segment segment) {
            float start = -offset;
            float fractional = start - (float) Math.floor(start);
            float wrap = dir > 0 ? 1 - fractional : fractional;
            if (wrap > 1e-4) segment.emit(0, wrap, fractional, dir > 0 ? 1 : 0);
            if (wrap < 1 - 1e-4) segment.emit(wrap, 1, dir > 0 ? 0 : 1, fractional);
        }

        private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
            return new Vector3f(a).lerp(b, t);
        }

        private void quad(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz,
                          Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float... uv) {
            Vector3f[] points = {p0, p1, p2, p3};
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, points[i].x, points[i].y, points[i].z).setColor(color)
                        .setUv(sprite.getU(uv[i * 2]), sprite.getV(uv[i * 2 + 1]))
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
            }
        }
    }
}
