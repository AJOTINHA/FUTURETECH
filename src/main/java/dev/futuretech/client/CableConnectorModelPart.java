package dev.futuretech.client;

import dev.futuretech.block.CableConnector;
import dev.futuretech.block.CableConnector.Point;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Immutable, shared mesh for a stepped steel collar with a continuous square face texture. */
public final class CableConnectorModelPart implements BlockStateModelPart {
    private final List<BakedQuad> quads;
    private final Material.Baked metal;

    public CableConnectorModelPart(Direction side, TextureAtlasSprite sprite) {
        metal=new Material.Baked(sprite,false);
        var output=new ArrayList<BakedQuad>();
        for (var b : CableConnector.BOXES) {
            Point a=new Point(b.x0(),b.y0(),b.z0()), c=new Point(b.x1(),b.y1(),b.z1());
            add(output,side,sprite,b.flange(),new Point(a.x(),c.y(),a.z()),new Point(a.x(),c.y(),c.z()),c,new Point(c.x(),c.y(),a.z()));
            add(output,side,sprite,b.flange(),a,new Point(c.x(),a.y(),a.z()),new Point(c.x(),a.y(),c.z()),new Point(a.x(),a.y(),c.z()));
            add(output,side,sprite,b.flange(),a,new Point(a.x(),c.y(),a.z()),new Point(c.x(),c.y(),a.z()),new Point(c.x(),a.y(),a.z()));
            add(output,side,sprite,b.flange(),new Point(a.x(),a.y(),c.z()),new Point(c.x(),a.y(),c.z()),c,new Point(a.x(),c.y(),c.z()));
            add(output,side,sprite,b.flange(),a,new Point(a.x(),a.y(),c.z()),new Point(a.x(),c.y(),c.z()),new Point(a.x(),c.y(),a.z()));
            add(output,side,sprite,b.flange(),new Point(c.x(),a.y(),a.z()),new Point(c.x(),c.y(),a.z()),c,new Point(c.x(),a.y(),c.z()));
        }
        quads=List.copyOf(output);
    }

    private static void add(List<BakedQuad> output, Direction side, TextureAtlasSprite sprite, boolean flange, Point... points) {
        Vector3f[] vertices=new Vector3f[4];
        long[] uvs=new long[4];
        boolean cap=points[0].z()==points[1].z() && points[0].z()==points[2].z();
        boolean alongX=points[0].x()!=points[1].x() || points[0].x()!=points[2].x();
        for (int i=0;i<4;i++) {
            Point p=CableConnector.rotate(points[i],side);
            vertices[i]=new Vector3f(p.x(),p.y(),p.z()).div(16);
            // Both sidewall stages use full strips: flange in rows 0..6 and the
            // wider neck in rows 26..32. No visible side samples a single texel.
            float u=cap ? points[i].x()/16 : (alongX ? points[i].x() : points[i].y())/16;
            float v=cap ? points[i].y()/16 : flange
                    ? points[i].z()*6/32 : (26+(points[i].z()-1)*4)/32;
            uvs[i]=UVPair.pack(sprite.getU(u),sprite.getV(v));
        }
        Vector3f normal=new Vector3f(vertices[1]).sub(vertices[0]).cross(new Vector3f(vertices[2]).sub(vertices[0])).normalize();
        Direction facing=Math.abs(normal.x)>.5F ? (normal.x>0 ? Direction.EAST : Direction.WEST)
                : Math.abs(normal.y)>.5F ? (normal.y>0 ? Direction.UP : Direction.DOWN)
                : (normal.z>0 ? Direction.SOUTH : Direction.NORTH);
        var material=BakedQuad.MaterialInfo.of(new Material.Baked(sprite,false),sprite.transparency(),-1,true,0,true);
        output.add(new BakedQuad(vertices[0],vertices[1],vertices[2],vertices[3],uvs[0],uvs[1],uvs[2],uvs[3],facing,material));
    }

    @Override public List<BakedQuad> getQuads(@Nullable Direction side) { return side==null ? quads : List.of(); }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public TriState ambientOcclusion() { return TriState.DEFAULT; }
    @Override public Material.Baked particleMaterial() { return metal; }
    @Override public int materialFlags() { return 0; }
}
