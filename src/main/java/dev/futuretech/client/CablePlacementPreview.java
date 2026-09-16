package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.futuretech.block.AbstractCableBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * A ghost of what a click would put down. With a cable in hand it is the cable where it would
 * land, with the links and collars it would come with, and the arm each neighbouring cable would
 * grow to meet it; with a facade it is the panel on the face the click would cover. Both are the
 * real models drawn see-through, so what the player sees is what they get.
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
            submitCable(event, level, player, hand, stack, hit, camera);
        }
    }

    /** The hand a click would use, the way the game tries them: main first, then off. */
    private static @Nullable InteractionHand handHolding(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            var item = player.getItemInHand(hand).getItem();
            if (item instanceof FacadeItem || item instanceof BlockItem block && block.getBlock() instanceof AbstractCableBlock) {
                return hand;
            }
            // Something else in the main hand takes the click before the off hand is tried.
            if (!player.getItemInHand(hand).isEmpty()) return null;
        }
        return null;
    }

    private static void submitCable(SubmitCustomGeometryEvent event, ClientLevel level, LocalPlayer player,
                                    InteractionHand hand, ItemStack stack, BlockHitResult hit, Vec3 camera) {
        var cable = (AbstractCableBlock) ((BlockItem) stack.getItem()).getBlock();
        var context = new BlockPlaceContext(player, hand, stack, hit);
        if (!context.canPlace()) return;
        BlockPos pos = context.getClickedPos();
        if (!player.mayUseItemAt(pos, hit.getDirection(), stack)) return;
        BlockState state = cable.getStateForPlacement(context);
        if (state == null || !state.canSurvive(level, pos)
                || !level.isUnobstructed(state, pos, CollisionContext.placementContext(player))) return;
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        var random = RandomSource.create(42);
        List<BlockStateModelPart> parts = new ArrayList<>();
        // Asked at the real position, so the collars the neighbouring machines would earn show too.
        models.get(state).collectParts(level, pos, state, random, parts);
        submitParts(event, level, pos, parts, camera);
        // A cable of the same kind beside the spot grows an arm to meet the new one. Only the arm
        // is drawn: the parts the neighbour would show that it does not show already.
        for (Direction side : Direction.values()) {
            if (!state.getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
            BlockPos neighbourPos = pos.relative(side);
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (!cable.joins(neighbour)) continue;
            var property = AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite());
            if (neighbour.getValue(property)) continue;
            BlockState joined = neighbour.setValue(property, true);
            List<BlockStateModelPart> before = new ArrayList<>();
            List<BlockStateModelPart> after = new ArrayList<>();
            models.get(neighbour).collectParts(level, neighbourPos, neighbour, random, before);
            models.get(joined).collectParts(level, neighbourPos, joined, random, after);
            Set<BlockStateModelPart> shown = Collections.newSetFromMap(new IdentityHashMap<>());
            shown.addAll(before);
            after.removeIf(shown::contains);
            submitParts(event, level, neighbourPos, after, camera);
        }
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
