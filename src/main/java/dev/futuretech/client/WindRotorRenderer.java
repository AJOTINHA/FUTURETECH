package dev.futuretech.client;

import static dev.futuretech.block.WindRotorGeometry.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.WindGeneratorBlockEntity;
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

public final class WindRotorRenderer implements BlockEntityRenderer<WindGeneratorBlockEntity, WindRotorRenderer.State> {
    private static final TextureAtlasSprite[] BLADES = new TextureAtlasSprite[5];
    private static TextureAtlasSprite steel;
    private static TextureAtlasSprite tower;
    private static TextureAtlasSprite housingBottom;
    private static final TextureAtlasSprite[] HOUSINGS = new TextureAtlasSprite[5];
    private static final TextureAtlasSprite[] BASES = new TextureAtlasSprite[5];
    private static final TextureAtlasSprite[] LIDS = new TextureAtlasSprite[5];
    public static final class State extends BlockEntityRenderState {
        float angle, facing;
        int mk;
    }

    public static void setSprites(int mk, TextureAtlasSprite blade, TextureAtlasSprite shaft,
            TextureAtlasSprite column, TextureAtlasSprite housing, TextureAtlasSprite bottom, TextureAtlasSprite base, TextureAtlasSprite lid) {
        BLADES[mk] = blade;
        steel = shaft;
        tower = column;
        housingBottom = bottom;
        HOUSINGS[mk] = housing;
        BASES[mk] = base;
        LIDS[mk] = lid;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(WindGeneratorBlockEntity generator, State state, float partialTick,
            Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(generator, state, partialTick, camera, breakProgress);
        state.angle = generator.rotorAngle(partialTick);
        state.mk = MachineLevel.of(generator.getBlockState());
        state.facing = switch (generator.front()) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        submitTurbine(pose, collector, state.mk, state.angle, state.facing, state.lightCoords, false);
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(WindGeneratorBlockEntity generator) {
        var p = generator.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX()-1, p.getY(), p.getZ()-1, p.getX()+2, p.getY()+5, p.getZ()+2);
    }

    public static void submitTurbine(PoseStack pose, SubmitNodeCollector collector, int mk, float angle,
            float facing, int light, boolean includeBase) {
        mk = Math.clamp(mk, 1, 4);
        TextureAtlasSprite blade = BLADES[mk];
        TextureAtlasSprite shaft = steel;
        TextureAtlasSprite column = tower, housing = HOUSINGS[mk], base = BASES[mk], lid = LIDS[mk];
        TextureAtlasSprite underside = housingBottom;
        if (blade == null || shaft == null || column == null || housing == null || underside == null || base == null || lid == null) return;
        pose.pushPose();
        pose.translate(.5F, 0, .5F);
        pose.mulPose(Axis.YP.rotationDegrees(facing));
        pose.translate(-.5F, 0, -.5F);
        pose.scale(1F / 16, 1F / 16, 1F / 16);
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(shaft.atlasLocation()), (p,b) -> {
            if (includeBase) {
                box(p,b,base,light,1,0,1,15,8,15);
                quad(p,b,lid,light,0,1,0,1,8.01F,1,1,8.01F,15,15,8.01F,15,15,8.01F,1);
            }
            // Both tower sections and their collars share the base's centre (8, 8).
            box(p,b,shaft,light,4.5F,7.9F,4.5F,11.5F,11,11.5F);
            box(p,b,column,light,5.5F,10.9F,5.5F,10.5F,31,10.5F);
            box(p,b,column,light,6.25F,30.9F,6.25F,9.75F,53,9.75F);
            box(p,b,shaft,light,5.8F,49,5.8F,10.2F,51,10.2F);
            box(p,b,housing,underside,light,3.5F,52,4,12.5F,60,15);
            box(p,b,shaft,light,6.8F,54.8F,AXLE_FRONT,9.2F,57.2F,4.1F);
        });
        pose.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        for (int arm = 0; arm < 3; arm++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(arm * 120));
            pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
            collector.submitCustomGeometry(pose, RenderTypes.entitySolid(blade.atlasLocation()), (p, b) -> {
                bladeSection(p,b,blade,light,7.2F,9,58,6,10,63,false);
                bladeSection(p,b,blade,light,6,10,63,7.6F,8.8F,78,true);
            });
            pose.popPose();
        }
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(shaft.atlasLocation()), (p, b) ->
                box(p, b, shaft, light, 5.9F, 53.9F, 1.2F, 10.1F, 58.1F, 3.1F));
        pose.popPose();
    }

    private static void bladeSection(PoseStack.Pose p, VertexConsumer b, TextureAtlasSprite sprite, int light,
            float x0, float X0, float y0, float x1, float X1, float y1, boolean tip) {
        float z=BLADE_FRONT, Z=BLADE_BACK;
        bladeFace(p,b,sprite,light,-1,X1,y1,z,X0,y0,z,x0,y0,z,x1,y1,z,y0,y1);
        bladeFace(p,b,sprite,light,1,x1,y1,Z,x0,y0,Z,X0,y0,Z,X1,y1,Z,y0,y1);
        quad(p,b,steel,light,-1,0,0,x1,y1,z,x0,y0,z,x0,y0,Z,x1,y1,Z);
        quad(p,b,steel,light,1,0,0,X1,y1,Z,X0,y0,Z,X0,y0,z,X1,y1,z);
        if (tip) quad(p,b,steel,light,0,1,0,x1,y1,z,x1,y1,Z,X1,y1,Z,X1,y1,z);
    }

    private static void bladeFace(PoseStack.Pose p, VertexConsumer b, TextureAtlasSprite sprite, int light, float nz,
            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float y0,float y1) {
        float u0=sprite.getU0(), u1=sprite.getU1();
        float v0=sprite.getV((BLADE_TOP-y1)/(BLADE_TOP-BLADE_BOTTOM));
        float v1=sprite.getV((BLADE_TOP-y0)/(BLADE_TOP-BLADE_BOTTOM));
        vertex(p,b,ax,ay,az,u0,v0,0,0,nz,light); vertex(p,b,bx,by,bz,u0,v1,0,0,nz,light);
        vertex(p,b,cx,cy,cz,u1,v1,0,0,nz,light); vertex(p,b,dx,dy,dz,u1,v0,0,0,nz,light);
    }

    private static void box(PoseStack.Pose p, VertexConsumer b, TextureAtlasSprite sprite, int light,
            float x, float y, float z, float X, float Y, float Z) {
        box(p,b,sprite,sprite,light,x,y,z,X,Y,Z);
    }

    private static void box(PoseStack.Pose p, VertexConsumer b, TextureAtlasSprite sprite, TextureAtlasSprite bottom,
            int light, float x, float y, float z, float X, float Y, float Z) {
        quad(p,b,sprite,light,0,1,0,x,Y,z,x,Y,Z,X,Y,Z,X,Y,z);
        quad(p,b,bottom,light,0,-1,0,x,y,Z,x,y,z,X,y,z,X,y,Z);
        quad(p,b,sprite,light,0,0,-1,X,Y,z,X,y,z,x,y,z,x,Y,z);
        quad(p,b,sprite,light,0,0,1,x,Y,Z,x,y,Z,X,y,Z,X,Y,Z);
        quad(p,b,sprite,light,-1,0,0,x,Y,z,x,y,z,x,y,Z,x,Y,Z);
        quad(p,b,sprite,light,1,0,0,X,Y,Z,X,y,Z,X,y,z,X,Y,z);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, TextureAtlasSprite sprite, int light,
            float nx, float ny, float nz, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        vertex(pose,b,ax,ay,az,u0,v0,nx,ny,nz,light);
        vertex(pose,b,bx,by,bz,u0,v1,nx,ny,nz,light);
        vertex(pose,b,cx,cy,cz,u1,v1,nx,ny,nz,light);
        vertex(pose,b,dx,dy,dz,u1,v0,nx,ny,nz,light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer b, float x, float y, float z, float u, float v,
            float nx, float ny, float nz, int light) {
        b.addVertex(p,x,y,z).setColor(0xFFFFFFFF).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p,nx,ny,nz);
    }
}
