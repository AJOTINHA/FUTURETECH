package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.QuarryFrame;
import dev.futuretech.block.entity.QuarryBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The arm that rides the scaffold: a beam across the ring, a carriage on it and a drill on a shaft
 * dropping to whatever block the quarry is breaking. It wears the machine's own plating rather
 * than the scaffold's, so it reads as the moving part, and the bit wears cutting steel. The girders themselves are real blocks the
 * machine builds, so nothing here draws them; the arm has to be drawn, because it moves.
 *
 * <p>The server says where the drill is, one packet per block, and the carriage slides across to
 * it over {@link QuarryBlockEntity#ARM_TRAVEL_TICKS} ticks, so the movement reads as continuous
 * however fast the machine is working.
 */
public final class QuarryArmRenderer implements BlockEntityRenderer<QuarryBlockEntity, QuarryArmRenderer.State> {
    /** Inset each beam face by 1/4 model pixel (1/64 block) from the frame's faces. */
    private static final float BEAM = (3.0F - 0.25F) / 16;
    private static final float CARRIAGE = 0.25F;
    private static final float SHAFT = 0.09375F;
    private static final float BIT = 0.15625F;
    /** How far the drill bobs into the block it is breaking, and how fast. */
    private static final float BOB = 0.12F;
    private static final float BOB_PERIOD = 7.0F;

    private static @Nullable TextureAtlasSprite armSprite;
    private static @Nullable TextureAtlasSprite drillSprite;

    /** Dedicated arm and cutting-head materials, refreshed when the atlas is stitched. */
    public static void setSprites(TextureAtlasSprite arm, TextureAtlasSprite drill) {
        armSprite = arm;
        drillSprite = drill;
    }

    public static final class State extends BlockEntityRenderState {
        boolean armed;
        /** Light where the arm actually is, not where the machine is: it hangs deep in the pit. */
        int armLight;
        /** The scaffold's rectangle and ring height, relative to the quarry's own block. */
        float minX, maxX, minZ, maxZ, ringY;
        /** Where the drill is, relative to the quarry's own block; the bottom of the shaft. */
        float armX, armY, armZ;
        float bob;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(QuarryBlockEntity quarry, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(quarry, state, partialTick, camera, breakProgress);
        state.armed = false;
        var area = quarry.area();
        var level = quarry.getLevel();
        var target = quarry.target();
        if (area == null || level == null || target == null || quarry.building()) return;
        var origin = quarry.getBlockPos();
        state.minX = QuarryFrame.minX(area) + 0.5F - origin.getX();
        state.maxX = QuarryFrame.maxX(area) + 0.5F - origin.getX();
        state.minZ = QuarryFrame.minZ(area) + 0.5F - origin.getZ();
        state.maxZ = QuarryFrame.maxZ(area) + 0.5F - origin.getZ();
        state.ringY = QuarryFrame.ringY(area) + 0.5F - origin.getY();
        // The carriage slides from the block just finished to the one now under the drill.
        var previous = quarry.previousTarget() == null ? target : quarry.previousTarget();
        float travel = Math.clamp((level.getGameTime() - quarry.targetChanged() + partialTick)
                / QuarryBlockEntity.ARM_TRAVEL_TICKS, 0, 1);
        state.armX = lerp(previous.getX(), target.getX(), travel) + 0.5F - origin.getX();
        state.armZ = lerp(previous.getZ(), target.getZ(), travel) + 0.5F - origin.getZ();
        state.armY = lerp(previous.getY(), target.getY(), travel) + 1.0F - origin.getY();
        // The arm is lit by the pit it hangs in, which is nowhere near the machine's own block.
        state.armLight = lightAt(level, target.above());
        double time = (level.getGameTime() % 1000) + partialTick;
        state.bob = (float) (BOB * (0.5 - 0.5 * Math.cos(time * Math.PI * 2 / BOB_PERIOD)));
        state.armed = true;
    }

    private static float lerp(int from, int to, float amount) { return from + (to - from) * amount; }

    /** The two light levels at {@code pos} in the packed form a vertex wants: sky high, block low. */
    private static int lightAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
        return sky << 20 | block << 4;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = armSprite;
        TextureAtlasSprite drill = drillSprite;
        if (!state.armed || sprite == null || drill == null) return;
        int light = state.armLight;
        float armX = state.armX, armZ = state.armZ;
        float beamY = state.ringY;
        float top = state.armY - state.bob;
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(sprite.atlasLocation()), (p, b) -> {
            // Stay centered inside the frame; the thinner beam avoids coplanar faces at its ends.
            box(p, b, sprite, light, state.minX, beamY - BEAM, armZ - BEAM, state.maxX, beamY + BEAM, armZ + BEAM);
            box(p, b, sprite, light, armX - CARRIAGE, beamY - CARRIAGE, armZ - CARRIAGE,
                    armX + CARRIAGE, beamY + CARRIAGE, armZ + CARRIAGE);
            // The shaft telescopes down to the block being broken, with the drill on its end.
            box(p, b, sprite, light, armX - SHAFT, top + BIT, armZ - SHAFT, armX + SHAFT, beamY, armZ + SHAFT);
            box(p, b, drill, light, armX - BIT, top, armZ - BIT, armX + BIT, top + BIT * 2, armZ + BIT);
        });
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light,
                            float x, float y, float z, float X, float Y, float Z) {
        quad(pose, buffer, sprite, light, 0, 1, 0, x, Y, z, x, Y, Z, X, Y, Z, X, Y, z);
        quad(pose, buffer, sprite, light, 0, -1, 0, x, y, Z, x, y, z, X, y, z, X, y, Z);
        quad(pose, buffer, sprite, light, 0, 0, -1, X, Y, z, X, y, z, x, y, z, x, Y, z);
        quad(pose, buffer, sprite, light, 0, 0, 1, x, Y, Z, x, y, Z, X, y, Z, X, Y, Z);
        quad(pose, buffer, sprite, light, -1, 0, 0, x, Y, z, x, y, z, x, y, Z, x, Y, Z);
        quad(pose, buffer, sprite, light, 1, 0, 0, X, Y, Z, X, y, Z, X, y, z, X, Y, z);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light,
                             float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float alongAB = Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az);
        float alongAD = Math.abs(dx - ax) + Math.abs(dy - ay) + Math.abs(dz - az);
        float shortEdge = Math.min(alongAB, alongAD);
        // Rotate the casing edges along the longest edge, including the horizontal
        // beam. Split at one-block intervals: atlas sprites cannot repeat outside their UV bounds.
        if (alongAD > alongAB) {
            float oldAx = ax, oldAy = ay, oldAz = az;
            ax = bx; ay = by; az = bz;
            bx = cx; by = cy; bz = cz;
            cx = dx; cy = dy; cz = dz;
            dx = oldAx; dy = oldAy; dz = oldAz;
            alongAB = alongAD;
        }
        if (alongAB <= 0) return;
        // Only the middle strip of the casing runs along a beam or shaft. This retains two
        // thin edge highlights without repeating end borders and bolts all along the rail.
        // Square carriage faces and end caps keep the complete casing panel.
        boolean rail = alongAB > shortEdge * 1.5F;
        float vStart = rail ? 5.0F / 16 : 0;
        float vEnd = rail ? 11.0F / 16 : 1;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * vStart;
        float vLimit = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * vEnd;
        for (int segment = 0; segment < Math.ceil(alongAB); segment++) {
            float start = segment / alongAB;
            float end = Math.min(segment + 1.0F, alongAB) / alongAB;
            // Small end caps and the carriage use the whole tile; a final long-face fragment
            // uses only its corresponding fraction, so the pattern retains a constant scale.
            float fraction = alongAB <= 1 ? 1 : Math.min(1, alongAB - segment);
            float v1 = v0 + (vLimit - v0) * fraction;
            vertex(pose, buffer, ax + (bx - ax) * start, ay + (by - ay) * start,
                    az + (bz - az) * start, u0, v0, nx, ny, nz, light);
            vertex(pose, buffer, ax + (bx - ax) * end, ay + (by - ay) * end,
                    az + (bz - az) * end, u0, v1, nx, ny, nz, light);
            vertex(pose, buffer, dx + (cx - dx) * end, dy + (cy - dy) * end,
                    dz + (cz - dz) * end, u1, v1, nx, ny, nz, light);
            vertex(pose, buffer, dx + (cx - dx) * start, dy + (cy - dy) * start,
                    dz + (cz - dz) * start, u1, v0, nx, ny, nz, light);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** The arm hangs out over the pit, far from the machine; its box has to cover the scaffold. */
    @Override
    public AABB getRenderBoundingBox(QuarryBlockEntity quarry) {
        var area = quarry.area();
        var origin = quarry.getBlockPos();
        if (area == null) return new AABB(origin);
        var target = quarry.target();
        int bottom = target == null ? QuarryFrame.ringY(area) : target.getY();
        return new AABB(QuarryFrame.minX(area), Math.min(bottom, origin.getY()) - 1, QuarryFrame.minZ(area),
                QuarryFrame.maxX(area) + 1, QuarryFrame.ringY(area) + 2, QuarryFrame.maxZ(area) + 1);
    }

    /** The scaffold can be a long way across, so the arm must not stop being drawn at 64 blocks. */
    @Override
    public int getViewDistance() { return 128; }

    /**
     * Drawn like a beacon's beam, whatever chunk section the camera is looking at: the arm hangs
     * over the pit, and the machine's own section is off screen from most places one watches it.
     */
    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
