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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Draws the fluid inside a see-through fluid cable: a column down the middle of the glass core and
 * an arm towards every connected face, in the fluid's own still texture and tint. Only tiers that
 * show their fluid draw anything, and only while the network is carrying something.
 *
 * <p>The fluid arrives as a front: it comes in by one face, crosses the core and goes out by the
 * other arms, so a line of cables fills from its entry outward, one cable after the other, and a
 * connector's arm fills last, as a plug pushed into the collar. When the flow stops the tail runs
 * out the same way.
 */
public final class FluidCableRenderer implements BlockEntityRenderer<FluidCableBlockEntity, FluidCableRenderer.State> {
    /** The glass core runs from 5 to 11; the fluid keeps half a unit inside it on every side. */
    private static final float INNER = 5.5F / 16F;
    private static final float OUTER = 10.5F / 16F;

    public static final class State extends BlockEntityRenderState {
        @Nullable TextureAtlasSprite sprite;
        int color = -1;
        /** The block's light lifted by the fluid's own glow, so lava in a dark room shines like a source. */
        int fluidLight;
        /** The way the fluid comes into the cable; up when unknown, so it rises like a level. */
        Direction travel = Direction.UP;
        /** How far the front has come along {@link #travel}, from 0 at the way in to 1 at the far face. */
        float distance;
        /** Whether the fluid lies behind the front (filling) or beyond it (its tail running out). */
        boolean filling;
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
        FluidCableBlockEntity.Front front = cable.front(time);
        FluidStack fluid = cable.drawnFluid();
        if (front == null || fluid.isEmpty()) return;
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        state.sprite = model.stillMaterial().sprite();
        state.color = model.fluidTintSource() == null ? -1 : model.fluidTintSource().colorAsStack(fluid);
        state.fluidLight = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords,
                Math.clamp(fluid.getFluidType().getLightLevel(fluid), 0, 15));
        state.travel = front.travel() == null ? Direction.UP : front.travel();
        state.distance = front.distance();
        state.filling = front.filling();
        for (Direction side : Direction.values()) {
            if (cable.getBlockState().getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side))) state.arms.add(side);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) return;
        var stream = new Stream(sprite, ARGB.opaque(state.color), state.fluidLight, state.distance, state.filling);
        Set<Direction> arms = EnumSet.copyOf(state.arms);
        Direction travel = state.travel;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, buffer) -> {
            // The core and its arms are one body: neither draws the face where it meets the other,
            // so no two translucent faces share a plane and the seams stay invisible.
            // The core and the arm the fluid comes in by are cut along the way in; every other arm
            // is cut along its own way out, so the front leaves the core through all of them at once.
            stream.box(p, buffer, INNER, INNER, INNER, OUTER, OUTER, OUTER, arms, travel);
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
                Direction along = side == travel.getOpposite() ? travel : side;
                stream.box(p, buffer, x0, y0, z0, x1, y1, z1, EnumSet.of(side.getOpposite()), along);
            }
        });
    }

    /** The fluid's look for one frame: its sprite, tint, light, and where its front stands along whatever way a box is cut. */
    private record Stream(TextureAtlasSprite sprite, int color, int light, float distance, boolean filling) {
        /**
         * A box with the faces in {@code skip} left out, cut by the front along {@code along}:
         * {@code distance} is measured from the face the front sets out from, the one behind
         * {@code along}. While filling, what lies past the front is not drawn; while the tail runs
         * out, what lies before it is not. A box the front cuts through gets a face there.
         */
        void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                 Set<Direction> skip, Direction along) {
            boolean positive = along.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            // In block coordinates the front sits here; the kept side is the one the front set out from.
            float cut = positive ? distance : 1 - distance;
            boolean keepLow = filling == positive;
            float lo = switch (along.getAxis()) { case X -> x0; case Y -> y0; case Z -> z0; };
            float hi = switch (along.getAxis()) { case X -> x1; case Y -> y1; case Z -> z1; };
            Direction cap = null;
            if (keepLow) {
                if (lo >= cut) return;
                if (hi > cut) { hi = cut; cap = Direction.fromAxisAndDirection(along.getAxis(), Direction.AxisDirection.POSITIVE); }
            } else {
                if (hi <= cut) return;
                if (lo < cut) { lo = cut; cap = Direction.fromAxisAndDirection(along.getAxis(), Direction.AxisDirection.NEGATIVE); }
            }
            switch (along.getAxis()) {
                case X -> { x0 = lo; x1 = hi; }
                case Y -> { y0 = lo; y1 = hi; }
                case Z -> { z0 = lo; z1 = hi; }
            }
            if (cap != null && skip.contains(cap)) {
                skip = EnumSet.copyOf(skip);
                skip.remove(cap);
            }
            if (!skip.contains(Direction.NORTH)) face(pose, buffer, 0, 0, -1, v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0), v(x0, y0, z0));
            if (!skip.contains(Direction.SOUTH)) face(pose, buffer, 0, 0, 1, v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1));
            if (!skip.contains(Direction.WEST)) face(pose, buffer, -1, 0, 0, v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0));
            if (!skip.contains(Direction.EAST)) face(pose, buffer, 1, 0, 0, v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1), v(x1, y0, z0));
            if (!skip.contains(Direction.DOWN)) face(pose, buffer, 0, -1, 0, v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1), v(x0, y0, z1));
            if (!skip.contains(Direction.UP)) face(pose, buffer, 0, 1, 0, v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0), v(x0, y1, z0));
        }

        private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

        /** One face, corners in order with texture corners (0,0) (1,0) (1,1) (0,1). */
        private void face(PoseStack.Pose pose, VertexConsumer buffer, float nx, float ny, float nz,
                          Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
            quad(pose, buffer, nx, ny, nz, p0, p1, p2, p3, 0, 0, 1, 0, 1, 1, 0, 1);
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
