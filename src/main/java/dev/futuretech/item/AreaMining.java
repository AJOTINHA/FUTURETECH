package dev.futuretech.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Expands a successful break with an area tool, using the normal player destruction path for every block. */
public final class AreaMining {
    /** Upper bound for one felled tree, so a log wall or a jungle giant cannot stall the server. */
    static final int TREE_LIMIT = 128;

    private record Target(BlockPos pos, BlockState state) {}
    private record Pending(BreakBlockEvent event, ServerPlayer player, ServerLevel level,
                           ItemStack tool, List<Target> targets) {}

    // Accessed exclusively on the logical server thread.
    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean expanding;

    private AreaMining() {}

    public static void onBreak(BreakBlockEvent event) {
        if (expanding || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isShiftKeyDown() || player.isSpectator()) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof AreaToolItem) || !(player.level() instanceof ServerLevel level)) return;
        BlockPos center = event.getPos();
        BlockState state = event.getState();
        if (!canMine(tool, state, level, center, Float.MAX_VALUE)) return;

        // The center still exists here, so the ray picks the actual face, including floors and ceilings.
        var hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        Direction face = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(center) ? blockHit.getDirection()
                : Direction.getApproximateNearest(player.getViewVector(1.0F)).getOpposite();
        List<Target> targets = new ArrayList<>();
        for (BlockPos pos : additionalBlocks(level, tool, center, face)) {
            targets.add(new Target(pos, level.getBlockState(pos)));
        }
        if (!targets.isEmpty()) PENDING.add(new Pending(event, player, level, tool, targets));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        var pending = List.copyOf(PENDING);
        PENDING.clear();
        expanding = true;
        try {
            for (Pending job : pending) {
                ServerPlayer player = job.player;
                ServerLevel level = job.level;
                // Wait until removal succeeds, and observe cancellations from later event listeners too.
                BlockState remaining = level.getBlockState(job.event.getPos());
                if (level.getServer() != event.getServer() || job.event.isCanceled()
                        || (!remaining.isAir() && !remaining.equals(remaining.getFluidState().createLegacyBlock()))
                        || player.isRemoved() || player.level() != level || player.isShiftKeyDown()
                        || player.isSpectator()) continue;
                for (Target target : job.targets) {
                    // Damage is paid per block. Stop immediately if the hammer breaks or is swapped out.
                    if (job.tool.isEmpty() || player.getMainHandItem() != job.tool) break;
                    BlockPos pos = target.pos;
                    if (!level.hasChunkAt(pos) || level.getBlockState(pos) != target.state
                            || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                            || level.getServer().isUnderSpawnProtection(level, pos, player)) continue;
                    // This respects protection events, adventure restrictions, enchantments, XP and drops.
                    player.gameMode.destroyBlock(pos);
                }
            }
        } finally {
            expanding = false;
        }
    }

    /** Shared by mining and the client preview, so material, hardness and tree exclusions match. */
    public static List<BlockPos> additionalBlocks(Level level, ItemStack tool, BlockPos center, Direction face) {
        if (!(tool.getItem() instanceof AreaToolItem item) || !level.hasChunkAt(center)) return List.of();
        BlockState state = level.getBlockState(center);
        if (!canMine(tool, state, level, center, Float.MAX_VALUE)) return List.of();
        return switch (item.reach()) {
            case PLANE -> plane(level, tool, state, center, face.getAxis());
            case TREE -> tree(level, tool, state, center);
        };
    }

    private static List<BlockPos> plane(Level level, ItemStack tool, BlockState state, BlockPos center, Direction.Axis axis) {
        float maxHardness = Math.max(0, state.getDestroySpeed(level, center)) * 3;
        List<BlockPos> result = new ArrayList<>(8);
        for (BlockPos pos : neighbors(center, axis)) {
            if (reachable(level, pos) && canMine(tool, level.getBlockState(pos), level, pos, maxHardness)) result.add(pos);
        }
        return List.copyOf(result);
    }

    /** Breadth-first over the 26 neighbors: trunk, branches and the diagonal steps of big trees. */
    private static List<BlockPos> tree(Level level, ItemStack tool, BlockState state, BlockPos center) {
        // Only logs start a felling, so planks or a crafting table never pull their neighbors down.
        if (!state.is(BlockTags.LOGS)) return List.of();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(center);
        List<BlockPos> result = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(center);
        while (!queue.isEmpty() && result.size() < TREE_LIMIT) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos pos = current.offset(dx, dy, dz);
                if (!seen.add(pos) || !reachable(level, pos)) continue;
                BlockState other = level.getBlockState(pos);
                // Same kind of log keeps a neighboring birch out of a felled oak and leaves stripped logs alone.
                if (!other.is(state.getBlock()) || !canMine(tool, other, level, pos, Float.MAX_VALUE)) continue;
                result.add(pos);
                if (result.size() >= TREE_LIMIT) break;
                queue.add(pos);
            }
        }
        return List.copyOf(result);
    }

    private static boolean reachable(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    static boolean canMine(ItemStack tool, BlockState state, Level level, BlockPos pos, float maxHardness) {
        float hardness = state.getDestroySpeed(level, pos);
        return tool.getItem() instanceof AreaToolItem item && state.is(item.mineable())
                && tool.isCorrectToolForDrops(state) && hardness >= 0 && hardness <= maxHardness;
    }

    static List<BlockPos> neighbors(BlockPos center, Direction.Axis normal) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            positions.add(switch (normal) {
                case X -> center.offset(0, a, b);
                case Y -> center.offset(a, 0, b);
                case Z -> center.offset(a, b, 0);
            });
        }
        return positions;
    }
}
