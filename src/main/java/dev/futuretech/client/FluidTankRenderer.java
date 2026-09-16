package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.entity.FluidTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The animated fluid occupies the sealed space behind the glass, with a level top surface. */
public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    private static final float MIN = 2.0F / 16;
    private static final float MAX = 14.0F / 16;

    public static final class State extends BlockEntityRenderState {
        float fill;
        @Nullable TextureAtlasSprite sprite;
        int color;
        int fluidLight;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(FluidTankBlockEntity tank, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTick, camera, breakProgress);
        state.fill = tank.visualFill(partialTick);
        state.sprite = null;
        var fluid = tank.visualFluid();
        if (fluid.isEmpty() || state.fill <= 0) return;
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        state.sprite = model.stillMaterial().sprite();
        state.color = model.fluidTintSource() == null ? 0xFFFFFFFF : model.fluidTintSource().colorAsStack(fluid);
        int emission = Math.clamp(fluid.getFluidType().getLightLevel(fluid), 0, 15) << 4;
        state.fluidLight = (state.lightCoords & 0xFFFF0000) | Math.max(state.lightCoords & 0xFFFF, emission);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var sprite = state.sprite;
        if (sprite == null || state.fill <= 0) return;
        float top = MIN + (MAX - MIN) * state.fill;
        int color = state.color;
        int light = state.fluidLight;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, b) -> {
            // UV height follows the level so the texture is cropped instead of stretched during filling.
            float vBottom = sprite.getV(MAX - MIN);
            float vTop = sprite.getV(MAX - top);
            float u0 = sprite.getU(0), u1 = sprite.getU(MAX - MIN);
            quad(p, b, color, light, 0, 1, 0,
                    MIN, top, MIN, MIN, top, MAX, MAX, top, MAX, MAX, top, MIN,
                    u0, u1, sprite.getV(0), sprite.getV(MAX - MIN));
            quad(p, b, shade(color, 0.55F), light, 0, -1, 0,
                    MIN, MIN, MAX, MIN, MIN, MIN, MAX, MIN, MIN, MAX, MIN, MAX,
                    u0, u1, sprite.getV(0), sprite.getV(MAX - MIN));
            quad(p, b, shade(color, 0.85F), light, 0, 0, -1,
                    MAX, top, MIN, MAX, MIN, MIN, MIN, MIN, MIN, MIN, top, MIN, u0, u1, vTop, vBottom);
            quad(p, b, shade(color, 0.85F), light, 0, 0, 1,
                    MIN, top, MAX, MIN, MIN, MAX, MAX, MIN, MAX, MAX, top, MAX, u0, u1, vTop, vBottom);
            quad(p, b, shade(color, 0.7F), light, -1, 0, 0,
                    MIN, top, MIN, MIN, MIN, MIN, MIN, MIN, MAX, MIN, top, MAX, u0, u1, vTop, vBottom);
            quad(p, b, shade(color, 0.7F), light, 1, 0, 0,
                    MAX, top, MAX, MAX, MIN, MAX, MAX, MIN, MIN, MAX, top, MIN, u0, u1, vTop, vBottom);
        });
    }

    private static int shade(int color, float shade) {
        return (color & 0xFF000000) | Math.round((color >> 16 & 255) * shade) << 16
                | Math.round((color >> 8 & 255) * shade) << 8 | Math.round((color & 255) * shade);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, int color, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float u1, float v0, float v1) {
        vertex(pose, b, ax, ay, az, u0, v0, nx, ny, nz, color, light);
        vertex(pose, b, bx, by, bz, u0, v1, nx, ny, nz, color, light);
        vertex(pose, b, cx, cy, cz, u1, v1, nx, ny, nz, color, light);
        vertex(pose, b, dx, dy, dz, u1, v0, nx, ny, nz, color, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer b, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int color, int light) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
