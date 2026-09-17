package dev.futuretech.teleport;

import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.NetworkCableBlock;
import dev.futuretech.block.NetworkPanelBlock;
import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.block.entity.StorageCardsBlockEntity;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.block.entity.TesseractBlockEntity;
import dev.futuretech.transfer.TesseractChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a network panel or a pad reaches: the teleporters and card storages on the far end of the
 * network cables running from it. The walk is the same flood-fill the other cables do, but nothing
 * is carried and nothing is cached — it is only asked while a player has a screen open or a trip
 * is about to happen, which is rare enough that walking the cables then costs less than keeping a
 * network alive for every panel in the world.
 *
 * <p>Only a cable's own links are followed, so a side the wrench cut stops the walk exactly the
 * way it stops energy: cutting a cable is how a player splits one panel's pads from another's.
 * A tesseract on the cables carries the walk to the cables touching every other tesseract on its
 * channel in the same world, so two runs far apart are one network.
 */
public final class TeleporterGrid {
    /** How far the walk will go, in cables, so a mistake in the world cannot hang the server. */
    public static final int MAX_CABLES = 4096;

    private TeleporterGrid() {}

    /**
     * The teleporters reachable from {@code start} through network cables, nearest first and then
     * by position, so the panel's list keeps the same order between openings. {@code start} is the
     * panel itself, or anything else the cables link to; it is never in the result.
     */
    public static List<BlockPos> teleporters(LevelReader level, BlockPos start) {
        return walk(level, start).pads();
    }

    /**
     * What one walk saw: the pads, the card storages, and how many cables it went through to find
     * them. The count is what lets the panel tell "no cable is touching me" from "the cables reach
     * no pad", which are two different things for the player to go and fix.
     */
    public record Walk(List<BlockPos> pads, List<BlockPos> storages, int cables) {}

    public static Walk walk(LevelReader level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> found = new HashSet<>();
        Set<BlockPos> storages = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        // The first step out of the panel. A cable's link flag towards a panel or a pad says
        // nothing the cut bit does not: the cable links to those blocks for being those blocks, so
        // the only question is whether the wrench cut that side. Asking the cut directly also means
        // a flag left stale by a cable older than the block beside it cannot hide the connection.
        for (Direction side : exits(level, start)) {
            BlockPos neighbour = start.relative(side);
            if (touches(level, neighbour, side.getOpposite()) && cables.add(neighbour)) {
                queue.add(neighbour);
            }
        }
        while (!queue.isEmpty() && cables.size() <= MAX_CABLES) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof NetworkCableBlock)) continue;
            for (Direction side : Direction.values()) {
                BlockPos neighbour = pos.relative(side);
                if (!level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
                BlockEntity entity = level.getBlockEntity(neighbour);
                if (entity instanceof TesseractBlockEntity tesseract) {
                    // Through the tesseract: the cables around it and around its peers join the
                    // walk. "Sends" is the player's word for the side that offers its pads and
                    // storages to the channel, so the walk, which comes looking for them, goes
                    // out through a tesseract that receives and in through the ones that send.
                    if (!isCut(level, pos, side) && tesseract.receives(TesseractBlockEntity.Kind.TELEPORT)) {
                        stepThrough(level, tesseract, cables, queue);
                        for (TesseractBlockEntity peer : TesseractChannels.peersIn(tesseract, level)) {
                            if (peer.sends(TesseractBlockEntity.Kind.TELEPORT)) stepThrough(level, peer, cables, queue);
                        }
                    }
                    continue;
                }
                if (entity instanceof TeleporterBlockEntity || entity instanceof StorageCardsBlockEntity) {
                    // Same rule as the panel: the block is reached unless that side was cut.
                    if (!isCut(level, pos, side)) {
                        (entity instanceof TeleporterBlockEntity ? found : storages).add(neighbour.immutable());
                    }
                    continue;
                }
                if (!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
                if (level.getBlockState(neighbour).getBlock() instanceof NetworkCableBlock) {
                    // Both ends must carry the link. A cut sets it on both cables, so in the world
                    // the two answers agree; asking anyway means one stale half can never let the
                    // walk through a run the player has cut.
                    if (!linksTowards(level, neighbour, side.getOpposite())) continue;
                    if (cables.size() < MAX_CABLES && cables.add(neighbour)) queue.add(neighbour);
                }
            }
        }
        return new Walk(nearestFirst(found, start), nearestFirst(storages, start), cables.size());
    }

    /** Nearest first and then by position, so a list keeps the same order between openings. */
    private static List<BlockPos> nearestFirst(Set<BlockPos> found, BlockPos start) {
        List<BlockPos> result = new ArrayList<>(found);
        result.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(start))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        return result;
    }

    /** Queues the network cables touching {@code tesseract} on any face, the same way the walk leaves its start. */
    private static void stepThrough(LevelReader level, TesseractBlockEntity tesseract, Set<BlockPos> cables,
                                    ArrayDeque<BlockPos> queue) {
        for (Direction side : Direction.values()) {
            BlockPos beside = tesseract.getBlockPos().relative(side);
            if (touches(level, beside, side.getOpposite()) && cables.size() < MAX_CABLES && cables.add(beside)) {
                queue.add(beside);
            }
        }
    }

    /**
     * The faces of {@code start} a cable may leave by. A panel is a plate with a front and a back,
     * and only its back takes a cable, so a panel has exactly one; anything else has six.
     */
    private static List<Direction> exits(LevelReader level, BlockPos start) {
        BlockState state = level.getBlockState(start);
        if (state.getBlock() instanceof NetworkPanelBlock) {
            return List.of(state.getValue(NetworkPanelBlock.FACING).getOpposite());
        }
        return List.of(Direction.values());
    }

    /**
     * Whether a network cable sits at {@code pos} with {@code side} not cut: what it takes for the
     * cable to reach a panel or a pad, which it links to on sight of the block alone.
     */
    private static boolean touches(LevelReader level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos.getX(), pos.getZ())) return false;
        return level.getBlockState(pos).getBlock() instanceof NetworkCableBlock && !isCut(level, pos, side);
    }

    /** Whether the wrench cut the cable at {@code pos} on {@code side}. */
    private static boolean isCut(LevelReader level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable && cable.isCut(side);
    }

    /** Whether the block at {@code pos} is a network cable whose link on {@code side} is open. */
    private static boolean linksTowards(LevelReader level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos.getX(), pos.getZ())) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NetworkCableBlock
                && state.getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    /** Whether {@code pad} is one of the teleporters {@code panel} reaches; what a trip is checked against. */
    public static boolean reaches(LevelReader level, BlockPos panel, BlockPos pad) {
        return teleporters(level, panel).contains(pad);
    }
}
