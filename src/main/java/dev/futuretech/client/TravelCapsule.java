package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.FutureTech;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The little glass box a travelling item rides in: six panes of glass inside twelve bars in the
 * cable frame's grey. Centred on the origin, {@value #SIZE} of a block wide, so it clears the cage
 * of the open cable with the item at its usual scale inside.
 */
final class TravelCapsule {
    static final float SIZE = 6 / 16F;
    private static final float HALF = SIZE / 2;
    private static final float BAR = 0.5F / 16F;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "textures/entity/battery_sphere_white.png");
    /** The frame's dark grey, {@code cable_mk1_frame_gray}. */
    private static final int EDGE = 0xFF202829;
    private static final int GLASS = 0x48BEDCEB;

    private TravelCapsule() {}

    static void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> drawEdges(p, b, light));
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, b) -> drawGlass(p, b, light));
    }

    /** Twelve bars along the cube's edges, each a small box sharing the corner with its neighbours. */
    private static void drawEdges(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        float o = HALF;
        float i = HALF - BAR;
        for (int sign = 0; sign < 4; sign++) {
            float a = (sign & 1) == 0 ? -1 : 1;
            float b = (sign & 2) == 0 ? -1 : 1;
            // Along x, y and z respectively, at each of the four combinations of the other two axes.
            box(pose, buffer, -o, a * i, b * i, o, a * o, b * o, EDGE, light);
            box(pose, buffer, a * i, -o, b * i, a * o, o, b * o, EDGE, light);
            box(pose, buffer, a * i, b * i, -o, a * o, b * o, o, EDGE, light);
        }
    }

    /** Six panes just inside the bars, drawn from both sides so the far pane shows through the near one. */
    private static void drawGlass(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        float g = HALF - BAR / 2;
        box(pose, buffer, -g, -g, -g, g, g, g, GLASS, light);
        boxInside(pose, buffer, -g, -g, -g, g, g, g, GLASS, light);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color, int light) {
        quad(pose, buffer, color, light, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        quad(pose, buffer, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, buffer, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, buffer, color, light, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
        quad(pose, buffer, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, buffer, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
    }

    /** The same box with every face wound the other way, so it is seen from within. */
    private static void boxInside(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0,
                                  float x1, float y1, float z1, int color, int light) {
        quad(pose, buffer, color, light, 0, 0, 1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(pose, buffer, color, light, 0, 0, -1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
        quad(pose, buffer, color, light, 1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        quad(pose, buffer, color, light, -1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, buffer, color, light, 0, 1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        quad(pose, buffer, color, light, 0, -1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light,
                             float nx, float ny, float nz, float... xyz) {
        for (int v = 0; v < 4; v++) {
            buffer.addVertex(pose, xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]).setColor(color).setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
