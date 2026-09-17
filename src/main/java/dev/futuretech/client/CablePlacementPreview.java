package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.NetworkPanelBlock;
import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.item.FacadeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A ghost of where a click would land. With a cable in hand it is the cable's bare core at the
 * spot it would take; with a network panel it is the plate on the face it would mount on; with a
 * facade it is the panel on the face the click would cover. All are the real models drawn
 * see-through, so the ghost looks like the block it stands for.
 *
 * <p>Nothing here changes the world: the state is worked out the way placing does, on the client's
 * copy, and thrown away with the frame.
 */
public final class CablePlacementPreview {
    /** White at a bit over half strength: the ghost reads as the block without hiding what is behind it. */
    private static final int GHOST = 0xA0FFFFFF;

    private CablePlacementPreview() {}

    public static void submit(SubmitCustomGeometryEvent event) {
        var minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || player.isSpectator()) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        InteractionHand hand = handHolding(player);
        if (hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        if (stack.getItem() instanceof FacadeItem) {
            submitFacade(event, level, stack, hit, camera);
        } else {
            submitBlock(event, level, player, hand, stack, hit, camera);
        }
    }

    /** The hand a click would use, the way the game tries them: main first, then off. */
    private static @Nullable InteractionHand handHolding(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            var item = player.getItemInHand(hand).getItem();
            if (item instanceof FacadeItem || item instanceof BlockItem block && previews(block.getBlock())) {
                return hand;
            }
            // Something else in the main hand takes the click before the off hand is tried.
            if (!player.getItemInHand(hand).isEmpty()) return null;
        }
        return null;
    }

    /** The blocks that get a ghost when held: the ones whose place is hard to tell from the crosshair alone. */
    private static boolean previews(Block block) {
        return block instanceof AbstractCableBlock || block instanceof NetworkPanelBlock;
    }

    private static void submitBlock(SubmitCustomGeometryEvent event, ClientLevel level, LocalPlayer player,
                                    InteractionHand hand, ItemStack stack, BlockHitResult hit, Vec3 camera) {
        Block block = ((BlockItem) stack.getItem()).getBlock();
        var context = new BlockPlaceContext(player, hand, stack, hit);
        if (!context.canPlace()) return;
        BlockPos pos = context.getClickedPos();
        if (!player.mayUseItemAt(pos, hit.getDirection(), stack)) return;
        BlockState state = block.getStateForPlacement(context);
        if (state == null || !state.canSurvive(level, pos)
                || !level.isUnobstructed(state, pos, CollisionContext.placementContext(player))) return;
        // A cable's ghost is only the core: where it lands is the question the ghost answers. The
        // links it would make are drawn the moment it is placed, and a bare knot is easier to read.
        // The panel's is the state itself: which face the plate hugs is the whole point of looking.
        BlockState shown = block instanceof AbstractCableBlock ? block.defaultBlockState() : state;
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(shown)
                .collectParts(level, pos, shown, RandomSource.create(42), parts);
        submitParts(event, level, pos, parts, camera);
    }

    private static void submitFacade(SubmitCustomGeometryEvent event, ClientLevel level, ItemStack stack,
                                     BlockHitResult hit, Vec3 camera) {
        BlockState facade = FacadeItem.block(stack);
        BlockPos pos = hit.getBlockPos();
        if (facade == null || !(level.getBlockState(pos).getBlock() instanceof AbstractCableBlock)) return;
        if (!(level.getBlockEntity(pos) instanceof AbstractCableBlockEntity entity)) return;
        // The same face the click would cover: the arm or collar the crosshair rests on.
        Direction side = AbstractCableBlock.hitSide(pos, hit.getLocation(), hit.getDirection());
        if (entity.hasFacade(side)) return;
        var panel = FacadeModelPart.of(facade, side);
        if (panel != null) submitParts(event, level, pos, List.of(panel), camera);
    }

    /** Every quad of the parts, culled or not, laid at {@code pos} in the ghost's colour and the light there. */
    private static void submitParts(SubmitCustomGeometryEvent event, ClientLevel level, BlockPos pos,
                                    List<BlockStateModelPart> parts, Vec3 camera) {
        if (parts.isEmpty()) return;
        List<BakedQuad> quads = new ArrayList<>();
        for (var part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction face : Direction.values()) quads.addAll(part.getQuads(face));
        }
        if (quads.isEmpty()) return;
        int light = LightCoordsUtil.getLightCoords(level, pos);
        var lighting = level.cardinalLighting();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        // Captured by value: the collector draws later, after the pose has been popped.
        List<BakedQuad> snapshot = List.copyOf(quads);
        collector.submitCustomGeometry(pose, RenderTypes.translucentMovingBlock(), (poseEntry, buffer) ->
                draw(poseEntry, buffer, snapshot, light, lighting));
        pose.popPose();
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, int light,
                             CardinalLighting shade) {
        var instance = new QuadInstance();
        instance.setLightCoords(light);
        for (BakedQuad quad : quads) {
            // The chunk mesher darkens faces by direction; the ghost matches, so it sits in the world.
            float faceShade = quad.materialInfo().shade() ? shade.byFace(quad.direction()) : 1.0F;
            instance.setColor(ARGB.scaleRGB(GHOST, faceShade));
            buffer.putBakedQuad(pose, quad, instance);
        }
    }
}
