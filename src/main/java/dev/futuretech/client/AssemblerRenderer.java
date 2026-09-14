package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.block.AssemblerBlock;
import dev.futuretech.block.AssemblerBlock.Kind;
import dev.futuretech.block.entity.AssemblerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import org.jspecify.annotations.Nullable;
import java.util.*;

/** Articulated steel arms, telescopic links, grippers and a separate rotating assembly tool. */
public final class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity, AssemblerRenderer.State> {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("futuretech", "textures/entity/battery_sphere_white.png");
    private static final int DARK = 0xFF242F3B, STEEL = 0xFFADB9C6, JOINT = 0xFF526274;
    private static final float TABLE_TOP = 13.15F / 16, TABLE_SCALE = .30F;
    public static final class State extends BlockEntityRenderState {
        Kind kind;
        int color;
        float toolSpin;
        float swivel;
        boolean welding;
        final AssemblerMonitor.State monitor = new AssemblerMonitor.State();
        Vec3 hand = Vec3.ZERO;
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Vec3> positions = new ArrayList<>();
        final List<Boolean> flat = new ArrayList<>();
    }
    private final ItemModelResolver resolver;
    public AssemblerRenderer(BlockEntityRendererProvider.Context context) { resolver = context.itemModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(AssemblerBlockEntity a, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(a, s, partial, camera, breaking);
        s.kind = a.kind(); s.items.clear(); s.positions.clear(); s.flat.clear();
        s.color = a.kind() == Kind.ASSEMBLY || a.outputMode() ? 0xFFEC761C : 0xFF008FFF;
        s.welding = false;
        if (s.kind == Kind.TABLE) {
            for (int i = 0; i < 9; i++) addTableItem(a, s, a.inventory.getItem(i), .25 + i % 3 * .25, .25 + i / 3 * .25, 0);
            addTableItem(a, s, a.inventory.getItem(9), .5, .5, .02);
            return;
        }
        if (s.kind == Kind.TERMINAL) { AssemblerMonitor.extract(a, s.monitor, resolver); return; }
        var facing = a.getBlockState().getValue(AssemblerBlock.FACING);
        Vec3 idle = new Vec3(.5 + facing.getStepX() * .28, 1.10, .5 + facing.getStepZ() * .28);
        Vec3 table = Vec3.atLowerCornerOf(a.tablePos().subtract(a.getBlockPos())).add(.5, 1.12, .5);
        Vec3 chest = Vec3.atLowerCornerOf(a.chestPos().subtract(a.getBlockPos())).add(.5, 1.12, .5);
        float t = a.animationTick(partial);
        s.toolSpin = AssemblerArmPose.drillAngle(a.phase(), t, a.duration());
        var idlePose = AssemblerArmPose.waypoint(idle, AssemblerArmPose.swivel(idle, facing));
        AssemblerArmPose.Pose motion;
        if (a.phase() == 0) motion = idlePose;
        else if (a.phase() == 2) {
            var tablePose = AssemblerArmPose.waypoint(table, idlePose.swivel());
            motion = t < 20 ? AssemblerArmPose.travel(idlePose, tablePose, t / 20) : t < a.duration() + 20 ? tablePose
                    : AssemblerArmPose.travel(tablePose, idlePose, (t - a.duration() - 20) / 20);
            boolean toolAtWork = AssemblerArmPose.working(a.phase(), t, a.duration());
            s.welding = toolAtWork && a.moving();
            if (toolAtWork) {
                double fade = Math.sin(Math.PI * (t - 20) / a.duration());
                float angle = (float)(tablePose.swivel() + Math.sin(t * .25) * .04 * fade);
                double radius = Math.hypot(table.x - AssemblerArmPose.SHOULDER.x, table.z - AssemblerArmPose.SHOULDER.z);
                motion = new AssemblerArmPose.Pose(new Vec3(AssemblerArmPose.SHOULDER.x - Math.sin(angle) * radius,
                        table.y + Math.sin(t * .6) * .035 * fade, AssemblerArmPose.SHOULDER.z - Math.cos(angle) * radius), angle);
            }
        } else {
            Vec3 from = a.outputMode() ? table : chest, to = a.outputMode() ? chest : table;
            var fromPose = AssemblerArmPose.waypoint(from, idlePose.swivel());
            var toPose = AssemblerArmPose.waypoint(to, fromPose.swivel());
            motion = t < 20 ? AssemblerArmPose.travel(idlePose, fromPose, t / 20)
                    : t < 40 ? AssemblerArmPose.travel(fromPose, toPose, (t - 20) / 20)
                    : a.cargo().isEmpty() ? AssemblerArmPose.travel(toPose, idlePose, (t - 40) / 20) : toPose;
            if (t >= 20) addItem(a, s, a.cargo(), motion.hand().add(0, -.16, 0));
        }
        s.hand = motion.hand();
        s.swivel = motion.swivel();
    }
    private void addItem(AssemblerBlockEntity a, State s, ItemStack stack, Vec3 pos) {
        if (stack.isEmpty()) return;
        var item = new ItemStackRenderState();
        resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, a.getLevel(), null, 0);
        s.items.add(item); s.positions.add(pos); s.flat.add(false);
    }
    /** Rests the item on the table top: sprites lie flat, blocks stand on their base. */
    private void addTableItem(AssemblerBlockEntity a, State s, ItemStack stack, double x, double z, double extra) {
        if (stack.isEmpty()) return;
        var item = new ItemStackRenderState();
        resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, a.getLevel(), null, 0);
        boolean flat = !item.usesBlockLight();
        AABB box = item.getModelBoundingBox();
        double bottom = flat ? -box.maxZ : box.minY; // lying flat, the sprite's depth becomes its height
        s.items.add(item); s.flat.add(flat);
        s.positions.add(new Vec3(x, TABLE_TOP + extra - bottom * TABLE_SCALE, z));
    }
    @Override public AABB getRenderBoundingBox(AssemblerBlockEntity a) { return new AABB(a.getBlockPos()).inflate(4); }
    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.kind == Kind.TERMINAL) { AssemblerMonitor.submit(s.monitor, pose, collector); return; }
        if (s.kind == Kind.TRANSPORT || s.kind == Kind.ASSEMBLY) {
            Vec3 shoulder = AssemblerArmPose.SHOULDER;
            Vec3 elbow = AssemblerArmPose.elbow(s.hand, s.swivel);
            link(pose, collector, shoulder, elbow, s.swivel, s.color, s.lightCoords);
            link(pose, collector, elbow, s.hand, s.swivel, s.color, s.lightCoords);
            joint(pose, collector, shoulder, .24F, s.swivel, s.color, s.lightCoords);
            joint(pose, collector, elbow, .22F, s.swivel, s.color, s.lightCoords);
            pose.pushPose();
            pose.translate(s.hand.x, s.hand.y, s.hand.z);
            pose.mulPose(Axis.YP.rotation(s.swivel));
            if (s.kind == Kind.ASSEMBLY) {
                box(pose, collector, -.14F, -.08F, -.14F, .14F, .16F, .14F, STEEL, s.lightCoords);
                box(pose, collector, -.09F, -.13F, -.09F, .09F, -.08F, .09F, DARK, s.lightCoords);
                pose.mulPose(Axis.YP.rotationDegrees(s.toolSpin));
                box(pose, collector, -.04F, -.27F, -.04F, .04F, -.13F, .04F, s.welding ? 0xFF93F4FF : s.color, s.welding ? 15728880 : s.lightCoords);
                if (s.welding) for (int i = 0; i < 4; i++) {
                    pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(i * 90));
                    box(pose, collector, .08F, -.25F, -.015F, .19F, -.225F, .015F, 0xFFFFD276, 15728880); pose.popPose();
                }
            } else {
                box(pose, collector, -.17F, -.03F, -.10F, .17F, .10F, .10F, DARK, s.lightCoords);
                box(pose, collector, -.18F, -.23F, -.06F, -.12F, .02F, .06F, STEEL, s.lightCoords);
                box(pose, collector, .12F, -.23F, -.06F, .18F, .02F, .06F, STEEL, s.lightCoords);
                box(pose, collector, -.12F, .06F, -.105F, .12F, .12F, .105F, s.color, s.lightCoords);
            }
            pose.popPose();
        }
        for (int i = 0; i < s.items.size(); i++) {
            Vec3 at = s.positions.get(i);
            pose.pushPose(); pose.translate(at.x, at.y, at.z);
            if (s.kind == Kind.TABLE) {
                pose.mulPose(Axis.YP.rotationDegrees(180));
                if (s.flat.get(i)) pose.mulPose(Axis.XP.rotationDegrees(90));
            } else pose.mulPose(Axis.YP.rotationDegrees(25));
            float scale = s.kind == Kind.TABLE ? TABLE_SCALE : .25F;
            pose.scale(scale, scale, scale);
            s.items.get(i).submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
    private static void link(PoseStack pose, SubmitNodeCollector collector, Vec3 from, Vec3 to, float swivel, int color, int light) {
        Vec3 d = to.subtract(from);
        float length = (float)d.length();
        pose.pushPose(); pose.translate(from.x, from.y, from.z);
        pose.mulPose(AssemblerArmPose.linkRotation(from, to, swivel));
        box(pose, collector, -.09F, 0, -.09F, .09F, length, .09F, STEEL, light);
        box(pose, collector, -.15F, .08F, -.13F, .15F, length * .70F, .13F, color, light);
        box(pose, collector, -.10F, .15F, -.145F, .10F, length * .61F, -.13F, DARK, light);
        box(pose, collector, -.18F, .12F, -.04F, -.145F, length * .85F, .04F, JOINT, light);
        pose.popPose();
    }
    private static void joint(PoseStack pose, SubmitNodeCollector collector, Vec3 at, float size, float swivel, int color, int light) {
        pose.pushPose(); pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotation(swivel));
        box(pose, collector, -size, -size / 2, -size / 2, size, size / 2, size / 2, DARK, light);
        box(pose, collector, -size - .015F, -.07F, -.07F, -size, .07F, .07F, color, light);
        box(pose, collector, size, -.07F, -.07F, size + .015F, .07F, .07F, STEEL, light);
        pose.popPose();
    }
    private static void box(PoseStack pose, SubmitNodeCollector collector, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (p, b) -> {
            quad(p,b,color,light,0,0,-1,x0,y1,z0,x1,y1,z0,x1,y0,z0,x0,y0,z0);
            quad(p,b,color,light,0,0,1,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);
            quad(p,b,color,light,-1,0,0,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
            quad(p,b,color,light,1,0,0,x1,y1,z0,x1,y1,z1,x1,y0,z1,x1,y0,z0);
            quad(p,b,color,light,0,-1,0,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
            quad(p,b,color,light,0,1,0,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0);
        });
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer b, int color, int light, float nx,float ny,float nz,float... xyz) {
        for (int i = 0; i < 4; i++) b.addVertex(pose,xyz[i*3],xyz[i*3+1],xyz[i*3+2]).setColor(color).setUv(.5F,.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
    }
}
