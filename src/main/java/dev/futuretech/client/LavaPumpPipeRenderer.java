package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.entity.LavaPumpBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The pipe hanging under the pump: one block-long section per block the server says it has
 * reached, each lit by the block it passes through, so the part standing in lava glows while the
 * part in the air above does not. It is drawn rather than placed, so it displaces nothing.
 */
public final class LavaPumpPipeRenderer implements BlockEntityRenderer<LavaPumpBlockEntity, LavaPumpPipeRenderer.State> {
    /** Half the pipe's width: four model pixels across. */
    private static final float HALF = 0.125F;

    private static @Nullable TextureAtlasSprite pipeSprite;

    /** The pipe's material, refreshed when the atlas is stitched. */
    public static void setSprite(TextureAtlasSprite sprite) { pipeSprite = sprite; }

    public static final class State extends BlockEntityRenderState {
        int pipe;
        /** Packed light per section, top first. */
        int[] light = new int[0];
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(LavaPumpBlockEntity pump, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pump, state, partialTick, camera, breakProgress);
        state.pipe = pump.pipe();
        var level = pump.getLevel();
        if (state.light.length != state.pipe) state.light = new int[state.pipe];
        if (level == null) {
            Arrays.fill(state.light, 0);
            return;
        }
        for (int section = 0; section < state.pipe; section++) {
            state.light[section] = lightAt(level, pump.getBlockPos().below(section + 1));
        }
    }

    /** The two light levels at {@code pos} in the packed form a vertex wants: sky high, block low. */
    private static int lightAt(Level level, BlockPos pos) {
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return sky << 20 | block << 4;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = pipeSprite;
        if (state.pipe <= 0 || sprite == null) return;
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(sprite.atlasLocation()), (p, b) -> {
            for (int section = 0; section < state.pipe; section++) {
                float top = -section;
                box(p, b, sprite, state.light[section], 0.5F - HALF, top - 1, 0.5F - HALF, 0.5F + HALF, top, 0.5F + HALF);
            }
        });
    }

    /** One section: four sides with the whole sprite, top and bottom with its middle. */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light,
                            float x, float y, float z, float X, float Y, float Z) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        // The side faces run the sprite top to bottom, so its rings line up section to section.
        quad(pose, buffer, light, 0, 0, -1, X, Y, z, X, y, z, x, y, z, x, Y, z, u0, v0, u1, v1);
        quad(pose, buffer, light, 0, 0, 1, x, Y, Z, x, y, Z, X, y, Z, X, Y, Z, u0, v0, u1, v1);
        quad(pose, buffer, light, -1, 0, 0, x, Y, z, x, y, z, x, y, Z, x, Y, Z, u0, v0, u1, v1);
        quad(pose, buffer, light, 1, 0, 0, X, Y, Z, X, y, Z, X, y, z, X, Y, z, u0, v0, u1, v1);
        float vm0 = v0 + (v1 - v0) * 0.375F, vm1 = v0 + (v1 - v0) * 0.625F;
        quad(pose, buffer, light, 0, 1, 0, x, Y, z, x, Y, Z, X, Y, Z, X, Y, z, u0, vm0, u1, vm1);
        quad(pose, buffer, light, 0, -1, 0, x, y, Z, x, y, z, X, y, z, X, y, Z, u0, vm0, u1, vm1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        vertex(pose, buffer, ax, ay, az, u0, v0, nx, ny, nz, light);
        vertex(pose, buffer, bx, by, bz, u0, v1, nx, ny, nz, light);
        vertex(pose, buffer, cx, cy, cz, u1, v1, nx, ny, nz, light);
        vertex(pose, buffer, dx, dy, dz, u1, v0, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** The pipe hangs below the block, as far as the floor of the pool. */
    @Override
    public AABB getRenderBoundingBox(LavaPumpBlockEntity pump) {
        var origin = pump.getBlockPos();
        return new AABB(origin.getX(), origin.getY() - pump.pipe(), origin.getZ(),
                origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1);
    }

    /** Drawn like a beacon's beam: looking down at a long pipe, the pump's own section is off screen. */
    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
