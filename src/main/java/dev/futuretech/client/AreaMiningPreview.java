package dev.futuretech.client;

import dev.futuretech.item.AreaToolItem;
import dev.futuretech.item.AreaMining;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Extracts the 3x3 selection and mirrors the local player's real mining stage onto its neighbors. */
public final class AreaMiningPreview {
    private record Outline(BlockPos pos, VoxelShape shape, boolean translucent) {}

    private AreaMiningPreview() {}

    private static boolean active(Minecraft minecraft) {
        var player = minecraft.player;
        return minecraft.level != null && player != null && !player.isSpectator() && !player.isShiftKeyDown()
                && player.getMainHandItem().getItem() instanceof AreaToolItem;
    }

    public static void extractOutline(ExtractBlockOutlineRenderStateEvent event) {
        var minecraft = Minecraft.getInstance();
        if (!active(minecraft)) return;
        var level = event.getLevel();
        var selected = AreaMining.additionalBlocks(level, minecraft.player.getMainHandItem(),
                event.getBlockPos(), event.getHitResult().getDirection());
        if (selected.isEmpty()) return;
        List<Outline> outlines = new ArrayList<>(selected.size());
        for (var pos : selected) {
            var state = level.getBlockState(pos);
            var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            outlines.add(new Outline(pos, state.getShape(level, pos, event.getCollisionContext()),
                    model.hasMaterialFlag(level, pos, state, 1)));
        }
        var snapshot = List.copyOf(outlines);
        float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        // Rendering captures only immutable extracted shapes, never the live world or player.
        event.addCustomRenderer((state, collector, pose, renderState) -> {
            var camera = renderState.cameraRenderState.pos;
            for (var outline : snapshot) {
                var pos = outline.pos;
                pose.pushPose();
                pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                if (state.highContrast()) collector.submitShapeOutline(pose, outline.shape,
                        RenderTypes.secondaryBlockOutline(), 0xFF000000, 7.0F, outline.translucent);
                collector.submitShapeOutline(pose, outline.shape, RenderTypes.lines(),
                        state.highContrast() ? 0xFF57FFFF : 0x99000000, width, outline.translucent);
                pose.popPose();
            }
            return false; // Keep the ordinary outline of the central block.
        });
    }

    public static void extractBreaking(ExtractLevelRenderStateEvent event) {
        var minecraft = Minecraft.getInstance();
        if (!active(minecraft) || minecraft.gameMode == null
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var center = hit.getBlockPos();
        var level = event.getLevel();
        var progress = level.destructionProgress().get(center.asLong());
        // Looking at another block must not transfer the previous block's cracks to a new area.
        int stage = localMiningStage(minecraft.gameMode.isDestroying(), minecraft.gameMode.getDestroyStage(),
                minecraft.player.getId(), progress);
        if (stage < 0) return;
        var selected = AreaMining.additionalBlocks(level, minecraft.player.getMainHandItem(), center, hit.getDirection());
        var breaking = event.getRenderState().blockBreakingRenderStates;
        for (var pos : selected) {
            // Avoid drawing two overlays if a different player is also mining a selected neighbor.
            int progressAtPos = stage;
            for (var existing : breaking) {
                if (existing.blockPos().equals(pos)) progressAtPos = Math.max(progressAtPos, existing.progress());
            }
            breaking.removeIf(existing -> existing.blockPos().equals(pos));
            breaking.add(new BlockBreakingRenderState(pos, level.getBlockState(pos), progressAtPos));
        }
        // These are per-frame states: releasing attack, swapping tools, sneaking or leaving the world
        // clears the additional cracks automatically without fake entity IDs or persistent overlays.
    }

    static int localMiningStage(boolean mining, int stage, int playerId,
                                @Nullable Collection<BlockDestructionProgress> progressAtCrosshair) {
        if (!mining || stage < 0 || stage > 9 || progressAtCrosshair == null) return -1;
        return progressAtCrosshair.stream().anyMatch(value -> value.getId() == playerId) ? stage : -1;
    }
}
