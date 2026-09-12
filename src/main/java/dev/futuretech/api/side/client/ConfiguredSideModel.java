package dev.futuretech.api.side.client;

import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Retextures configured faces without overlapping geometry or live block entity reads. */
public final class ConfiguredSideModel extends DelegateBlockStateModel {
    /** Sprite per mode; {@link SideMode#NONE} is absent so untouched faces keep the model's own texture. */
    private final Map<SideMode, TextureAtlasSprite> sprites;
    private final int sideFlags;
    private final Map<PartKey, BlockStateModelPart> partsCache = new ConcurrentHashMap<>();

    public ConfiguredSideModel(BlockStateModel delegate, Map<SideMode, TextureAtlasSprite> sprites) {
        super(delegate);
        this.sprites = new EnumMap<>(sprites);
        int flags = 0;
        for (TextureAtlasSprite sprite : this.sprites.values()) flags |= flags(sprite);
        sideFlags = flags;
    }

    private static int flags(TextureAtlasSprite sprite) {
        return (sprite.isAnimated() ? BakedQuad.FLAG_ANIMATED : 0)
                | (ChunkSectionLayer.byTransparency(sprite.transparency()).translucent() ? BakedQuad.FLAG_TRANSLUCENT : 0);
    }

    /** Sprite the configuration screen should draw for {@code mode}, or {@code null} to keep the model's own. */
    public @Nullable TextureAtlasSprite spriteFor(SideMode mode) {
        return sprites.get(mode);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> output) {
        Integer stored = level.getModelData(pos).get(SideConfigVisuals.FACE_MODES);
        int modes = stored == null ? 0 : stored;
        if (!retextures(modes)) {
            delegate.collectParts(level, pos, state, random, output);
            return;
        }
        List<BlockStateModelPart> original = new ArrayList<>();
        delegate.collectParts(level, pos, state, random, original);
        for (BlockStateModelPart part : original) {
            output.add(partsCache.computeIfAbsent(new PartKey(part, modes), key -> new ConfiguredPart(key.part(), key.modes())));
        }
    }

    /** False when no face carries a mode this model has a sprite for, so the delegate can be used untouched. */
    private boolean retextures(int modes) {
        for (Direction side : Direction.values()) {
            if (sprites.containsKey(SideConfigVisuals.mode(modes, side))) return true;
        }
        return false;
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return delegate.materialFlags(level, pos, state) | sideFlags;
    }

    private record PartKey(BlockStateModelPart part, int modes) {}

    private final class ConfiguredPart implements BlockStateModelPart {
        private final BlockStateModelPart original;
        private final List<List<BakedQuad>> faces;
        private final List<BakedQuad> unculled;

        ConfiguredPart(BlockStateModelPart original, int modes) {
            this.original = original;
            List<List<BakedQuad>> faces = new ArrayList<>();
            for (Direction side : Direction.values()) faces.add(retexture(original.getQuads(side), modes));
            this.faces = List.copyOf(faces);
            unculled = retexture(original.getQuads(null), modes);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            return side == null ? unculled : faces.get(side.ordinal());
        }

        @Override
        public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }

        @Override
        public TriState ambientOcclusion() { return original.ambientOcclusion(); }

        @Override
        public Material.Baked particleMaterial() { return original.particleMaterial(); }

        @Override
        public int materialFlags() { return original.materialFlags() | sideFlags; }
    }

    private List<BakedQuad> retexture(List<BakedQuad> quads, int modes) {
        return quads.stream().map(quad -> {
            TextureAtlasSprite target = sprites.get(SideConfigVisuals.mode(modes, quad.direction()));
            return target == null ? quad : retexture(quad, target);
        }).toList();
    }

    private BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target) {
        var material = quad.materialInfo();
        var sprite = material.sprite();
        var replacement = BakedQuad.MaterialInfo.of(new Material.Baked(target, false), target.transparency(),
                material.tintIndex(), material.shade(), material.lightEmission(), material.ambientOcclusion());
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                remapUV(quad.packedUV0(), sprite, target), remapUV(quad.packedUV1(), sprite, target),
                remapUV(quad.packedUV2(), sprite, target), remapUV(quad.packedUV3(), sprite, target),
                quad.direction(), replacement, quad.bakedNormals(), quad.bakedColors());
    }

    private long remapUV(long uv, TextureAtlasSprite source, TextureAtlasSprite target) {
        float u = (UVPair.unpackU(uv) - source.getU0()) / (source.getU1() - source.getU0());
        float v = (UVPair.unpackV(uv) - source.getV0()) / (source.getV1() - source.getV0());
        return UVPair.pack(target.getU(u), target.getV(v));
    }
}
