package dev.futuretech.block;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.MiningMarkerBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The box a quarry digs, read from the square its markers close. Two things are asked of the
 * player: a marker standing behind the machine, against the face opposite the one it shows, which
 * is the corner it reads from, and a closed square of four markers linked corner to corner along
 * their axes. What gets dug is the
 * inside of that square — the markers' own line carries the scaffold's legs and ring, so it is
 * never broken — and the highest of the four corners is the first layer.
 */
public record QuarryArea(int minX, int minZ, int maxX, int maxZ, int topY) {
    /** Longest side the dug box may have, indexed by level; slot 0 is unused. */
    private static final int[] MAX_SIDE = {0, 9, 16, 25, 33};
    private static final String TAG_MIN_X = "AreaMinX";
    private static final String TAG_MIN_Z = "AreaMinZ";
    private static final String TAG_MAX_X = "AreaMaxX";
    private static final String TAG_MAX_Z = "AreaMaxZ";
    private static final String TAG_TOP_Y = "AreaTopY";
    private static final String TAG_SET = "AreaSet";

    /** Why a scan gave the box it gave, so the screen can say what the player has to fix. */
    public enum Result { OK, NO_MARKER_BEHIND, NO_SQUARE, TOO_SMALL, TOO_BIG }

    /** What one scan found: a box only when the result is {@link Result#OK}. */
    public record Scan(Result result, @Nullable QuarryArea area) {
        static Scan empty(Result result) { return new Scan(result, null); }
    }

    /** Longest side a level-{@code mk} quarry digs: 9, 16, 25 and 33 blocks. */
    public static int maxSide(int mk) { return MAX_SIDE[Math.clamp(mk, 1, MachineLevel.MAX)]; }

    /**
     * Reads the square behind a quarry, on the {@code back} side of it. The block against that
     * face has to be a marker; from there the square is walked corner to corner, and what comes
     * back is the pit inside it. The machine therefore stands outside its own pit, with its face
     * to whoever placed it and the square at its back.
     */
    public static Scan scan(Level level, BlockPos quarry, Direction back, int mk) {
        BlockPos seed = quarry.relative(back);
        if (!level.hasChunkAt(seed) || !level.getBlockState(seed).is(ModBlocks.MINING_MARKER.get())) {
            return Scan.empty(Result.NO_MARKER_BEHIND);
        }
        BlockPos opposite = closes(level, seed);
        if (opposite == null) return Scan.empty(Result.NO_SQUARE);
        int minX = Math.min(seed.getX(), opposite.getX());
        int maxX = Math.max(seed.getX(), opposite.getX());
        int minZ = Math.min(seed.getZ(), opposite.getZ());
        int maxZ = Math.max(seed.getZ(), opposite.getZ());
        // The markers' line is the scaffold's; the pit is what it encloses.
        if (maxX - minX < 2 || maxZ - minZ < 2) return Scan.empty(Result.TOO_SMALL);
        var area = new QuarryArea(minX + 1, minZ + 1, maxX - 1, maxZ - 1,
                Math.max(seed.getY(), opposite.getY()));
        int side = maxSide(mk);
        if (area.width() > side || area.depth() > side) return Scan.empty(Result.TOO_BIG);
        return new Scan(Result.OK, area);
    }

    /**
     * The corner across the square from {@code seed}, or null when the four markers do not close
     * one. A square closes when the seed reaches a marker on each of two axes and both of those
     * reach the same fourth corner back, which is the shape the lines draw in the world.
     */
    private static @Nullable BlockPos closes(Level level, BlockPos seed) {
        for (Direction first : Direction.Plane.HORIZONTAL) {
            int along = MiningMarkerBlockEntity.reach(level, seed, first);
            if (along == 0) continue;
            BlockPos corner = seed.relative(first, along);
            for (Direction second : Direction.Plane.HORIZONTAL) {
                if (second.getAxis() == first.getAxis()) continue;
                int across = MiningMarkerBlockEntity.reach(level, seed, second);
                if (across == 0) continue;
                BlockPos other = seed.relative(second, across);
                // Both sides have to run the same length into the same corner, or it is not a square.
                if (MiningMarkerBlockEntity.reach(level, corner, second) != across) continue;
                if (MiningMarkerBlockEntity.reach(level, other, first) != along) continue;
                return corner.relative(second, across);
            }
        }
        return null;
    }

    public int width() { return maxX - minX + 1; }

    public int depth() { return maxZ - minZ + 1; }

    /** Blocks in one layer, which is how far the cursor runs before dropping a level. */
    public int layerSize() { return width() * depth(); }

    /** The {@code index}-th block of layer {@code y}, walking x first and then z. */
    public BlockPos posAt(int index, int y) {
        int width = width();
        return new BlockPos(minX + index % width, y, minZ + index / width);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public void save(ValueOutput output) {
        output.putBoolean(TAG_SET, true);
        output.putInt(TAG_MIN_X, minX);
        output.putInt(TAG_MIN_Z, minZ);
        output.putInt(TAG_MAX_X, maxX);
        output.putInt(TAG_MAX_Z, maxZ);
        output.putInt(TAG_TOP_Y, topY);
    }

    /** The saved box, or null when the quarry had none; the level's limit is not applied again. */
    public static @Nullable QuarryArea load(ValueInput input) {
        if (!input.getBooleanOr(TAG_SET, false)) return null;
        int minX = input.getIntOr(TAG_MIN_X, 0);
        int minZ = input.getIntOr(TAG_MIN_Z, 0);
        int maxX = input.getIntOr(TAG_MAX_X, 0);
        int maxZ = input.getIntOr(TAG_MAX_Z, 0);
        if (maxX < minX || maxZ < minZ) return null;
        return new QuarryArea(minX, minZ, maxX, maxZ, input.getIntOr(TAG_TOP_Y, 0));
    }
}
