package dev.futuretech.client;

import dev.futuretech.api.facade.CableFacades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One face of a cable wearing another block. The panel is the covered block's own quads, clipped
 * to the {@link CableFacades#THICKNESS} the panel occupies: the face the player sees stays exactly
 * where the block's face would be, the opposite one slides in to the back of the panel, and the
 * four rims are cut down, UV and all, so nothing is stretched.
 *
 * <p>Only the outer face is offered for culling, the way a real block face is: the rest is drawn
 * unculled, since a neighbour that would hide it cannot be seen past the panel anyway.
 *
 * <p>Tint is dropped. The chunk mesher resolves a tint index against the block being drawn, which
 * here is the cable, so a grass panel would take the cable's colour. Untinted is the honest answer
 * until the facade carries its own colour.
 */
public final class FacadeModelPart implements BlockStateModelPart {
    private static final Map<Long, FacadeModelPart> CACHE = new ConcurrentHashMap<>();
    /** The peg's cross-section and how far it reaches, in model units: from the panel's back to the cable's core. */
    private static final float PIN_MIN = 6.0F / 16;
    private static final float PIN_MAX = 10.0F / 16;
    private static final float PIN_REACH = 5.0F / 16;
    /** Steel for the peg, handed over when the models bake; null until then, and the peg is skipped. */
    private static @Nullable TextureAtlasSprite pinSprite;

    private final List<BakedQuad> outer;
    private final List<BakedQuad> unculled;
    private final Direction side;
    private final Material.Baked particle;
    private final int flags;

    private FacadeModelPart(Direction side, List<BakedQuad> outer, List<BakedQuad> unculled,
                            Material.Baked particle, int flags) {
        this.side = side;
        this.outer = List.copyOf(outer);
        this.unculled = List.copyOf(unculled);
        this.particle = particle;
        this.flags = flags;
    }

    /** The panel for this block on this face, built once and handed out from then on. */
    public static @Nullable FacadeModelPart of(BlockState facade, Direction side) {
        long key = (long) Block.BLOCK_STATE_REGISTRY.getId(facade) << 3 | side.ordinal();
        FacadeModelPart cached = CACHE.get(key);
        if (cached != null) return cached;
        FacadeModelPart built = build(facade, side);
        if (built != null) CACHE.put(key, built);
        return built;
    }

    /** Dropped when the resource pack reloads, since the sprites the panels hold no longer exist. */
    public static void clearCache() { CACHE.clear(); }

    /** The steel the peg is made of, taken from the freshly baked atlas. */
    public static void setPinSprite(TextureAtlasSprite sprite) { pinSprite = sprite; }

    private static @Nullable FacadeModelPart build(BlockState facade, Direction side) {
        var source = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(facade);
        List<BlockStateModelPart> parts = new ArrayList<>();
        // The covered blocks are plain cubes, so the model answers the same for every position.
        source.collectParts(RandomSource.create(0), parts);
        if (parts.isEmpty()) return null;
        List<BakedQuad> outer = new ArrayList<>();
        List<BakedQuad> unculled = new ArrayList<>();
        int flags = 0;
        for (BlockStateModelPart part : parts) {
            flags |= part.materialFlags();
            for (Direction face : Direction.values()) {
                for (BakedQuad quad : part.getQuads(face)) {
                    (face == side ? outer : unculled).add(clip(quad, side));
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                (quad.direction() == side ? outer : unculled).add(clip(quad, side));
            }
        }
        if (outer.isEmpty() && unculled.isEmpty()) return null;
        addPin(unculled, side);
        return new FacadeModelPart(side, outer, unculled, parts.getFirst().particleMaterial(), flags);
    }

    /**
     * Brings a quad of the covered block into the panel. A quad crossing the panel's axis, which is
     * one of the four rims, is cut: each vertex at the far end is pulled to the panel's back and its
     * texture coordinate travels with it, borrowed from the vertex it shares an edge with across
     * that axis. A quad lying flat against the far face has no such edge to borrow from — it is the
     * back of the panel, so it is moved across whole, texture untouched.
     */
    private static BakedQuad clip(BakedQuad quad, Direction side) {
        float depth = CableFacades.THICKNESS / 16.0F;
        int axis = side.getAxis().ordinal();
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // Where the panel's back lies, and which end of the axis is the far one.
        float back = positive ? 1 - depth : depth;
        float far = positive ? 0 : 1;
        Vector3fc[] positions = new Vector3fc[4];
        long[] uvs = new long[4];
        boolean moved = false;
        boolean flatAgainstFar = true;
        for (int i = 0; i < 4; i++) {
            positions[i] = quad.position(i);
            uvs[i] = quad.packedUV(i);
            if (Math.abs(component(positions[i], axis) - far) > 1.0E-4F) flatAgainstFar = false;
        }
        if (flatAgainstFar) {
            for (int i = 0; i < 4; i++) positions[i] = withComponent(positions[i], axis, back);
            moved = true;
        }
        for (int i = 0; !flatAgainstFar && i < 4; i++) {
            if (Math.abs(component(positions[i], axis) - far) > 1.0E-4F) continue;
            int partner = partner(quad, i, axis);
            if (partner < 0) continue;
            float from = component(quad.position(partner), axis);
            float span = far - from;
            if (Math.abs(span) < 1.0E-4F) continue;
            float weight = (back - from) / span;
            positions[i] = lerp(quad.position(partner), positions[i], weight, axis, back);
            uvs[i] = lerpUV(quad.packedUV(partner), uvs[i], weight);
            moved = true;
        }
        if (!moved) return quad;
        var material = quad.materialInfo();
        var untinted = new BakedQuad.MaterialInfo(material.sprite(), material.layer(), material.itemRenderType(),
                -1, material.shade(), material.lightEmission(), material.ambientOcclusion());
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3],
                uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(), untinted,
                quad.bakedNormals(), quad.bakedColors());
    }

    /**
     * The peg that holds the panel to the cable: a short steel stub bridging the gap between the
     * panel's back and the cable's core, so a covered face reads as bolted on rather than floating.
     * A face the cable runs through has its arm there already and swallows the peg, which is right:
     * there is no gap to bridge.
     */
    private static void addPin(List<BakedQuad> output, Direction side) {
        TextureAtlasSprite sprite = pinSprite;
        if (sprite == null) return;
        float depth = CableFacades.THICKNESS / 16.0F;
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float near = positive ? 1 - PIN_REACH : depth;
        float far = positive ? 1 - depth : PIN_REACH;
        float x0 = PIN_MIN, y0 = PIN_MIN, z0 = PIN_MIN, x1 = PIN_MAX, y1 = PIN_MAX, z1 = PIN_MAX;
        switch (side.getAxis()) {
            case X -> { x0 = near; x1 = far; }
            case Y -> { y0 = near; y1 = far; }
            case Z -> { z0 = near; z1 = far; }
        }
        for (Direction face : Direction.values()) {
            // The end buried in the panel is never seen, and the one in the cable only when the
            // cable stops short of it, which the arm covers.
            if (face.getAxis() == side.getAxis()) continue;
            output.add(box(sprite, face, x0, y0, z0, x1, y1, z1));
        }
    }

    /** One face of an axis-aligned box, wound so it faces out, with the sprite laid on it flat. */
    private static BakedQuad box(TextureAtlasSprite sprite, Direction face,
                                 float x0, float y0, float z0, float x1, float y1, float z1) {
        Vector3fc[] corners = switch (face) {
            case UP -> new Vector3fc[]{point(x0, y1, z0), point(x0, y1, z1), point(x1, y1, z1), point(x1, y1, z0)};
            case DOWN -> new Vector3fc[]{point(x0, y0, z1), point(x0, y0, z0), point(x1, y0, z0), point(x1, y0, z1)};
            case NORTH -> new Vector3fc[]{point(x1, y1, z0), point(x1, y0, z0), point(x0, y0, z0), point(x0, y1, z0)};
            case SOUTH -> new Vector3fc[]{point(x0, y1, z1), point(x0, y0, z1), point(x1, y0, z1), point(x1, y1, z1)};
            case WEST -> new Vector3fc[]{point(x0, y1, z0), point(x0, y0, z0), point(x0, y0, z1), point(x0, y1, z1)};
            case EAST -> new Vector3fc[]{point(x1, y1, z1), point(x1, y0, z1), point(x1, y0, z0), point(x1, y1, z0)};
        };
        long[] uvs = new long[4];
        for (int i = 0; i < 4; i++) {
            // The sprite is laid on the block's own grid, so the steel reads the same as the casing.
            float u = face.getAxis() == Direction.Axis.X ? corners[i].z() : corners[i].x();
            float v = face.getAxis() == Direction.Axis.Y ? corners[i].z() : 1 - corners[i].y();
            uvs[i] = UVPair.pack(sprite.getU(u), sprite.getV(v));
        }
        var material = BakedQuad.MaterialInfo.of(new Material.Baked(sprite, false), sprite.transparency(),
                -1, true, 0, true);
        return new BakedQuad(corners[0], corners[1], corners[2], corners[3],
                uvs[0], uvs[1], uvs[2], uvs[3], face, material);
    }

    private static Vector3fc point(float x, float y, float z) { return new Vector3f(x, y, z); }

    /** The vertex sharing an edge with {@code vertex} across {@code axis}: the one it is pulled towards. */
    private static int partner(BakedQuad quad, int vertex, int axis) {
        Vector3fc own = quad.position(vertex);
        for (int i = 0; i < 4; i++) {
            if (i == vertex) continue;
            Vector3fc other = quad.position(i);
            if (Math.abs(component(other, axis) - component(own, axis)) < 1.0E-4F) continue;
            boolean sameElsewhere = true;
            for (int other_axis = 0; other_axis < 3; other_axis++) {
                if (other_axis == axis) continue;
                if (Math.abs(component(other, other_axis) - component(own, other_axis)) > 1.0E-4F) sameElsewhere = false;
            }
            if (sameElsewhere) return i;
        }
        return -1;
    }

    private static float component(Vector3fc position, int axis) {
        return switch (axis) {
            case 0 -> position.x();
            case 1 -> position.y();
            default -> position.z();
        };
    }

    private static Vector3fc lerp(Vector3fc from, Vector3fc to, float weight, int axis, float back) {
        return withComponent(new Vector3f(from).lerp(new Vector3f(to), weight), axis, back);
    }

    private static Vector3fc withComponent(Vector3fc position, int axis, float value) {
        return switch (axis) {
            case 0 -> new Vector3f(value, position.y(), position.z());
            case 1 -> new Vector3f(position.x(), value, position.z());
            default -> new Vector3f(position.x(), position.y(), value);
        };
    }

    private static long lerpUV(long from, long to, float weight) {
        float u = UVPair.unpackU(from) + (UVPair.unpackU(to) - UVPair.unpackU(from)) * weight;
        float v = UVPair.unpackV(from) + (UVPair.unpackV(to) - UVPair.unpackV(from)) * weight;
        return UVPair.pack(u, v);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction face) {
        return face == null ? unculled : face == side ? outer : List.of();
    }

    @Override
    public boolean useAmbientOcclusion() { return true; }

    @Override
    public TriState ambientOcclusion() { return TriState.DEFAULT; }

    @Override
    public Material.Baked particleMaterial() { return particle; }

    @Override
    public int materialFlags() { return flags; }
}
