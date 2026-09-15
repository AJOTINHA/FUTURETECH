package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the travelling items that are inside this cable right now. Each cable only draws its own
 * stretch of a journey, so the item stays lit and culled like the block it is passing through.
 * Only tiers that show their items draw anything; the opaque cable hides them and skips the work.
 */
public final class ItemCableRenderer implements BlockEntityRenderer<ItemCableBlockEntity, ItemCableRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Vec3> offsets = new ArrayList<>();
        float spin;
    }

    private final ItemModelResolver itemModelResolver;

    public ItemCableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ItemCableBlockEntity cable, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(cable, state, partialTick, camera, breakProgress);
        state.items.clear();
        state.offsets.clear();
        var level = cable.getLevel();
        if (level == null || !cable.tier().showsItems()) return;
        double now = ItemTravel.now(partialTick);
        state.spin = (float) (now * 3.0);
        BlockPos here = cable.getBlockPos();
        for (ItemTravel.Placed placed : ItemTravel.inside(here, now)) {
            var journey = placed.journey();
            // The item's model is resolved once per journey, not once per cable per frame.
            if (journey.render == null) {
                journey.render = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(journey.render, journey.stack(), ItemDisplayContext.FIXED, level, null, (int) journey.start());
            }
            state.items.add(journey.render);
            state.offsets.add(placed.position().subtract(here.getX(), here.getY(), here.getZ()));
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int index = 0; index < state.items.size(); index++) {
            Vec3 offset = state.offsets.get(index);
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            // The glass box stays square to the world; only the item inside turns.
            TravelCapsule.submit(pose, collector, state.lightCoords);
            pose.mulPose(Axis.YP.rotationDegrees(state.spin));
            pose.scale(ItemTravel.ITEM_SCALE, ItemTravel.ITEM_SCALE, ItemTravel.ITEM_SCALE);
            state.items.get(index).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
