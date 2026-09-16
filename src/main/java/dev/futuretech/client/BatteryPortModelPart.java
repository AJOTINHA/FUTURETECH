package dev.futuretech.client;

import dev.futuretech.client.BatteryPortGeometry.Point;
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

/** Solid steel port with a real square opening; all sides remain visible through the open frame. */
public final class BatteryPortModelPart implements BlockStateModelPart {
    private final List<BakedQuad> quads;
    private final Material.Baked metal;

    public BatteryPortModelPart(Direction side, TextureAtlasSprite steel, TextureAtlasSprite accent) {
        this(side, steel, accent, accent, 13.85F);
    }

    /** Tank ports sit outside the glass; a second accent divides bidirectional ports into two halves. */
    public BatteryPortModelPart(Direction side, TextureAtlasSprite steel, TextureAtlasSprite accent,
                                TextureAtlasSprite secondAccent, float innerY) {
        metal = new Material.Baked(steel, false);
        var result = new ArrayList<BakedQuad>();
        var boxes = new ArrayList<BatteryPortGeometry.Box>();
        for (var box : BatteryPortGeometry.BOXES) {
            if (accent != secondAccent && box.x0() < 8 && box.x1() > 8) {
                boxes.add(new BatteryPortGeometry.Box(box.x0(), box.y0(), box.z0(), 8, box.y1(), box.z1(), box.rim()));
                boxes.add(new BatteryPortGeometry.Box(8, box.y0(), box.z0(), box.x1(), box.y1(), box.z1(), box.rim()));
            } else boxes.add(box);
        }
        for (var b : boxes) {
            Point a = new Point(b.x0(), b.y0(), b.z0());
            Point c = new Point(b.x1(), b.y1(), b.z1());
            var color = (b.x0() + b.x1()) / 2 < 8 ? accent : secondAccent;
            add(result, side, steel, color, b.rim(), innerY, new Point(a.x(),c.y(),a.z()), new Point(a.x(),c.y(),c.z()), c, new Point(c.x(),c.y(),a.z()));
            add(result, side, steel, color, b.rim(), innerY, a, new Point(c.x(),a.y(),a.z()), new Point(c.x(),a.y(),c.z()), new Point(a.x(),a.y(),c.z()));
            add(result, side, steel, color, b.rim(), innerY, a, new Point(a.x(),c.y(),a.z()), new Point(c.x(),c.y(),a.z()), new Point(c.x(),a.y(),a.z()));
            add(result, side, steel, color, b.rim(), innerY, new Point(a.x(),a.y(),c.z()), new Point(c.x(),a.y(),c.z()), c, new Point(a.x(),c.y(),c.z()));
            add(result, side, steel, color, b.rim(), innerY, a, new Point(a.x(),a.y(),c.z()), new Point(a.x(),c.y(),c.z()), new Point(a.x(),c.y(),a.z()));
            add(result, side, steel, color, b.rim(), innerY, new Point(c.x(),a.y(),a.z()), new Point(c.x(),c.y(),a.z()), c, new Point(c.x(),a.y(),c.z()));
        }
        quads = List.copyOf(result);
    }

    private static void add(List<BakedQuad> output, Direction side, TextureAtlasSprite steel,
                            TextureAtlasSprite accent, boolean rim, float innerY, Point... points) {
        Vector3f[] vertices = new Vector3f[4];
        long[] uvs = new long[4];
        boolean cap = points[0].y() == points[1].y() && points[0].y() == points[2].y();
        boolean alongX = points[0].x() != points[1].x() || points[0].x() != points[2].x();
        var sprite = cap || rim ? accent : steel;
        for (int i = 0; i < 4; i++) {
            Point original = points[i];
            float depth = innerY == 13.85F ? original.y()
                    : innerY + (original.y() - 13.85F) * (16 - innerY) / (16 - 13.85F);
            Point p = BatteryPortGeometry.rotate(new Point(original.x(), depth, original.z()), side);
            vertices[i] = new Vector3f(p.x(), p.y(), p.z()).div(16);
            // Both ends share the detailed face. Sleeve walls use a separate shaded strip
            // so the accent continues through the bore without stretching a single pixel.
            float u = cap ? points[i].x() / 16 : (alongX ? points[i].x() : points[i].z()) / 16;
            float v = cap ? points[i].z() / 16 : rim
                    ? (0.5F + (points[i].y() - 13.85F) / (16 - 13.85F) * 7) / 64
                    : points[i].y() / 16;
            uvs[i] = UVPair.pack(sprite.getU(u), sprite.getV(v));
        }
        Vector3f normal = new Vector3f(vertices[1]).sub(vertices[0])
                .cross(new Vector3f(vertices[2]).sub(vertices[0])).normalize();
        Direction facing = Math.abs(normal.x) > .5F ? (normal.x > 0 ? Direction.EAST : Direction.WEST)
                : Math.abs(normal.y) > .5F ? (normal.y > 0 ? Direction.UP : Direction.DOWN)
                : (normal.z > 0 ? Direction.SOUTH : Direction.NORTH);
        var material = BakedQuad.MaterialInfo.of(new Material.Baked(sprite, false), sprite.transparency(),
                -1, true, 0, true);
        output.add(new BakedQuad(vertices[0], vertices[1], vertices[2], vertices[3],
                uvs[0], uvs[1], uvs[2], uvs[3], facing, material));
    }

    @Override public List<BakedQuad> getQuads(@Nullable Direction side) { return side == null ? quads : List.of(); }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public TriState ambientOcclusion() { return TriState.DEFAULT; }
    @Override public Material.Baked particleMaterial() { return metal; }
    @Override public int materialFlags() { return 0; }
}
