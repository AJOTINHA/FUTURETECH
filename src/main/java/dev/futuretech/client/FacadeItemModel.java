package dev.futuretech.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.api.facade.CableFacades;
import dev.futuretech.item.FacadeItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The facade in hand and in the inventory is the panel itself, not the block it copies: the same
 * slab the cable wears, dressed in that block's own textures. It stands upright, and the peg is
 * left out, since an item has no cable to be bolted to.
 *
 * <p>A facade with no block left on it falls back to the plain panel icon.
 */
public record FacadeItemModel(ModelRenderProperties properties, Matrix4fc transformation,
                              ItemModel fallback) implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "facade");
    /** The face the icon's panel sits on. A vertical one catches the light of the standard block pose. */
    private static final Direction ICON_SIDE = Direction.NORTH;
    /**
     * How far the panel is pushed to end up in the middle of the block box. It is built against a
     * face, which is where it belongs on a cable but leaves the icon hugging one edge of the slot.
     */
    private static final float CENTRING = (8.0F - CableFacades.THICKNESS / 2.0F) / 16.0F;

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        BlockState facade = FacadeItem.block(item);
        FacadeModelPart panel = facade == null ? null : FacadeModelPart.of(facade, ICON_SIDE, false);
        if (panel == null) {
            fallback.update(output, item, resolver, displayContext, level, owner, seed);
            return;
        }
        output.appendModelIdentityElement(this);
        // Part of the identity, so two facades of different blocks are not cached as one.
        output.appendModelIdentityElement(facade.getBlock());
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        properties.applyToLayer(layer, displayContext);
        layer.setLocalTransform(new Matrix4f(transformation).translate(
                -ICON_SIDE.getStepX() * CENTRING, -ICON_SIDE.getStepY() * CENTRING, -ICON_SIDE.getStepZ() * CENTRING));
        List<BakedQuad> quads = layer.prepareQuadList();
        quads.addAll(panel.getQuads(null));
        quads.addAll(panel.getQuads(ICON_SIDE));
        Vector3fc[] extents = CuboidItemModelWrapper.computeExtents(quads);
        layer.setExtents(() -> extents);
        if ((panel.materialFlags() & BakedQuad.FLAG_ANIMATED) != 0) output.setAnimated();
    }

    /**
     * @param base a model that lends nothing but its display transforms and gui light, so the
     *             panel is held and shown the way a block is; its own geometry is never drawn.
     */
    public record Unbaked(Identifier base, ItemModel.Unbaked fallback) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() { return MAP_CODEC; }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(base);
            fallback.resolveDependencies(resolver);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            var baker = context.blockModelBaker();
            var resolved = baker.getModel(base);
            var properties = ModelRenderProperties.fromResolvedModel(baker, resolved, resolved.getTopTextureSlots());
            return new FacadeItemModel(properties, transformation, fallback.bake(context, transformation));
        }
    }
}
