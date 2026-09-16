package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.futuretech.block.AssemblerBlock;
import dev.futuretech.block.entity.AssemblerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Flat GUI item models sit on the monitor face, following the controller's facing. */
final class AssemblerMonitor {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("futuretech", "textures/entity/battery_sphere_white.png");
    private static final int LIGHT = 15728880;
    static final class State {
        Direction facing = Direction.NORTH;
        int progress, present;
        int statusColor;
        int statusWidth;
        FormattedCharSequence statusText = FormattedCharSequence.EMPTY;
        final List<ItemStackRenderState> ingredients = new ArrayList<>();
        ItemStackRenderState result = new ItemStackRenderState();
    }
    static void extract(AssemblerBlockEntity controller, State state, ItemModelResolver resolver) {
        state.facing = controller.getBlockState().getValue(AssemblerBlock.FACING);
        state.progress = controller.monitorProgress(); state.present = controller.monitorPresent();
        var status = AssemblerStatusView.label(controller.monitorStatus());
        state.statusText = status.getVisualOrderText();
        state.statusWidth = Minecraft.getInstance().font.width(state.statusText);
        state.statusColor = AssemblerStatusView.color(controller.monitorStatus(), true);
        state.ingredients.clear();
        for (ItemStack stack : controller.monitorIngredients()) {
            var item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.GUI, controller.getLevel(), null, 0);
            state.ingredients.add(item);
        }
        state.result = new ItemStackRenderState();
        resolver.updateForTopItem(state.result, controller.monitorResult(), ItemDisplayContext.GUI, controller.getLevel(), null, 0);
    }
    static void submit(State state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        float rotation = switch (state.facing) { case EAST -> 90; case SOUTH -> 0; case WEST -> 270; default -> 180; };
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(0, 14.0 / 16, (8 - 4.35) / 16);
        rect(pose, collector, -.412F, -.224F, .412F, .224F, 0xFF111E2A);
        pose.pushPose();
        pose.translate(0, .206, .009);
        float textScale = Math.min(.0045F, .76F / Math.max(1, state.statusWidth));
        pose.scale(textScale, -textScale, textScale);
        collector.submitText(pose, -state.statusWidth / 2F, 0, state.statusText, false,
                Font.DisplayMode.NORMAL, LIGHT, state.statusColor, 0, 0);
        pose.popPose();
        for (int i = 0; i < 9; i++) {
            float x = -.30F + i % 3 * .112F, y = .116F - i / 3 * .112F;
            rect(pose, collector, x - .050F, y - .050F, x + .050F, y + .050F,
                    (state.present & 1 << i) != 0 ? 0xFF306B65 : 0xFF293C4D);
            if (i < state.ingredients.size()) item(pose, collector, state.ingredients.get(i), x, y, .088F);
        }
        boolean resultReady = state.progress == 100;
        rect(pose, collector, .193F, -.075F, .373F, .105F, resultReady ? 0xFF35DB72 : 0xFF293C4D);
        if (resultReady) rect(pose, collector, .201F, -.067F, .365F, .097F, 0xFF215F3A, .002F);
        item(pose, collector, state.result, .283F, .015F, .154F);
        // Arrow between the ingredient grid and the finished item.
        rect(pose, collector, .004F, .009F, .135F, .022F, 0xFF79DEE2);
        rect(pose, collector, .112F, -.010F, .126F, .041F, 0xFF79DEE2);
        rect(pose, collector, .126F, -.001F, .140F, .032F, 0xFF79DEE2);
        rect(pose, collector, .140F, .008F, .153F, .023F, 0xFF79DEE2);
        pose.pushPose();
        pose.translate(0, -1.0 / 128, 0);
        rect(pose, collector, -.383F, -.196F, .383F, -.164F, 0xFF293C4D);
        if (state.progress > 0) rect(pose, collector, -.378F, -.191F,
                -.378F + .756F * state.progress / 100, -.169F, 0xFF44D5DE, .002F);
        pose.popPose();
        pose.popPose();
    }
    private static void item(PoseStack pose, SubmitNodeCollector collector, ItemStackRenderState item, float x, float y, float size) {
        pose.pushPose(); pose.translate(x, y, .006);
        // GUI transforms preserve familiar icons; compress depth to keep them on the display.
        pose.scale(size, size, .001F);
        item.submit(pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    private static void rect(PoseStack pose, SubmitNodeCollector collector, float x0, float y0, float x1, float y1, int color) {
        rect(pose, collector, x0, y0, x1, y1, color, .001F);
    }
    private static void rect(PoseStack pose, SubmitNodeCollector collector, float x0, float y0, float x1, float y1, int color, float z) {
        // Background is recessed relative to the other elements, avoiding coplanar faces.
        float depth = color == 0xFF111E2A ? 0 : z;
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> {
            for (float[] point : new float[][]{{x0,y0},{x1,y0},{x1,y1},{x0,y1}}) {
                b.addVertex(p, point[0], point[1], depth).setColor(color).setUv(.5F,.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(p,0,0,1);
            }
        });
    }
}
