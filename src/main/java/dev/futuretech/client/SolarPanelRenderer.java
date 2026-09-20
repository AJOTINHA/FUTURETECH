package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.SolarPanelGeometry;
import dev.futuretech.block.entity.SolarGeneratorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The solar generator's panel, drawn here rather than in the block model so it can follow the
 * sun: the slab turns about the mast on the north-south line, its east edge down at dawn, flat
 * at noon and its west edge down at dusk, and swings back east over the night to be ready for
 * the next sunrise. Nothing here depends on which way the base faces — the sun is the world's.
 * The straight mast and rounded bearing stay fixed in the block model; only the panel turns.
 * Dedicated material sprites are picked up
 * when the models bake.
 */
public final class SolarPanelRenderer implements BlockEntityRenderer<SolarGeneratorBlockEntity, SolarPanelRenderer.State> {
    /** The furthest the panel leans either way; a real tracker stops short of the horizon too. */
    public static final float MAX_TILT = SolarPanelGeometry.MAX_TILT;
    private static final float LEFT = SolarPanelGeometry.MIN_X / 16;
    private static final float RIGHT = SolarPanelGeometry.MAX_X / 16;
    private static final float BOTTOM = SolarPanelGeometry.BOTTOM / 16;
    /** One pixel thick: a sheet of cells, not a slab. */
    private static final float TOP = SolarPanelGeometry.TOP / 16;
    private static final float PIVOT_Y = SolarPanelGeometry.PIVOT_Y / 16;

    private static final TextureAtlasSprite[] PANELS = new TextureAtlasSprite[MachineLevel.MAX + 1];
    private static final TextureAtlasSprite[] BACKS = new TextureAtlasSprite[MachineLevel.MAX + 1];
    private static final TextureAtlasSprite[] MASTS = new TextureAtlasSprite[MachineLevel.MAX + 1];
    private static final TextureAtlasSprite[] SIDES = new TextureAtlasSprite[MachineLevel.MAX + 1];

    public static final class State extends BlockEntityRenderState {
        float tilt;
        int mk;
    }

    /** Called when the models bake: the panel top and the side sheet of every level. */
    public static void setSprites(int mk, TextureAtlasSprite panel, TextureAtlasSprite side, TextureAtlasSprite back, TextureAtlasSprite mast) {
        PANELS[mk] = panel;
        SIDES[mk] = side;
        BACKS[mk] = back;
        MASTS[mk] = mast;
    }

    /**
     * How far the panel leans east (positive) or west at {@code dayTime} ticks into the world's
     * clock: through the day it tracks the sun from the east horizon to the west one, capped at
     * {@link #MAX_TILT}; through the night it swings back at a steady pace, so there is no jump
     * at dawn or dusk.
     */
    public static float tilt(double dayTime) {
        double time = ((dayTime % 24000) + 24000) % 24000;
        if (time < 12000) {
            double sun = time / 12000 * 180;
            return (float) Math.clamp(90 - sun, -MAX_TILT, MAX_TILT);
        }
        return (float) (-MAX_TILT + 2 * MAX_TILT * (time - 12000) / 12000);
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(SolarGeneratorBlockEntity generator, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(generator, state, partialTick, camera, breakProgress);
        Level level = generator.getLevel();
        state.tilt = level == null ? 0 : tilt(level.getOverworldClockTime() + partialTick);
        state.mk = MachineLevel.of(generator.getBlockState());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite panel = PANELS[Math.clamp(state.mk, 1, MachineLevel.MAX)];
        TextureAtlasSprite side = SIDES[Math.clamp(state.mk, 1, MachineLevel.MAX)];
        TextureAtlasSprite back = BACKS[Math.clamp(state.mk, 1, MachineLevel.MAX)];
        TextureAtlasSprite mast = MASTS[Math.clamp(state.mk, 1, MachineLevel.MAX)];
        if (panel == null || side == null || back == null || mast == null) return;
        int light = state.lightCoords;
        float tilt = state.tilt;
        pose.pushPose();
        pose.translate(0.5F, PIVOT_Y, 0.5F);
        // A positive tilt drops the east edge: turning about north-south the other way round from the pose's.
        pose.mulPose(Axis.ZP.rotationDegrees(-tilt));
        pose.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(panel.atlasLocation()), (p, b) -> {
            // Top: the cells. The rest: the side sheet, the way the block model stretches it.
            quad(p, b, panel, light, 0, 1, 0, LEFT, TOP, 0, LEFT, TOP, 1, RIGHT, TOP, 1, RIGHT, TOP, 0);
            quad(p, b, back, light, 0, -1, 0, LEFT, BOTTOM, 1, LEFT, BOTTOM, 0, RIGHT, BOTTOM, 0, RIGHT, BOTTOM, 1);
            quad(p, b, side, light, 0, 0, -1, RIGHT, TOP, 0, RIGHT, BOTTOM, 0, LEFT, BOTTOM, 0, LEFT, TOP, 0);
            quad(p, b, side, light, 0, 0, 1, LEFT, TOP, 1, LEFT, BOTTOM, 1, RIGHT, BOTTOM, 1, RIGHT, TOP, 1);
            quad(p, b, side, light, -1, 0, 0, LEFT, TOP, 0, LEFT, BOTTOM, 0, LEFT, BOTTOM, 1, LEFT, TOP, 1);
            quad(p, b, side, light, 1, 0, 0, RIGHT, TOP, 1, RIGHT, BOTTOM, 1, RIGHT, BOTTOM, 0, RIGHT, TOP, 0);
        });
        pose.popPose();
    }

    /** One face, its corners given anticlockwise as seen from outside, with the whole sprite on it. */
    private static void quad(PoseStack.Pose pose, VertexConsumer b, TextureAtlasSprite sprite, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        // Crop the dedicated 32x2 edge strip instead of squeezing a complete face onto it.
        for (TextureAtlasSprite frame : SIDES) {
            if (frame == sprite) { v1 = sprite.getV(2F / 32); break; }
        }
        // The chunk mesher shades faces by direction; the panel matches so it sits with its base.
        int colour = shade(ny > 0 ? 1.0F : ny < 0 ? 0.5F : nz != 0 ? 0.8F : 0.6F);
        vertex(pose, b, ax, ay, az, u0, v0, nx, ny, nz, colour, light);
        vertex(pose, b, bx, by, bz, u0, v1, nx, ny, nz, colour, light);
        vertex(pose, b, cx, cy, cz, u1, v1, nx, ny, nz, colour, light);
        vertex(pose, b, dx, dy, dz, u1, v0, nx, ny, nz, colour, light);
    }

    private static int shade(float shade) {
        int channel = Math.round(255 * shade);
        return 0xFF000000 | channel << 16 | channel << 8 | channel;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer b, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int colour, int light) {
        b.addVertex(pose, x, y, z).setColor(colour).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
