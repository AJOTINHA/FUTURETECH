package dev.futuretech.block;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the girders of a quarry's scaffold go. The markers' own rectangle — the box the quarry
 * digs, grown by one on every side — carries four legs, and the legs carry a ring the machine's
 * arm runs along, {@link #HEIGHT} blocks above the ground that was marked. The markers' own line
 * carries a ring too, so the box is closed at the bottom as well as the top; a leg stands on the
 * marker's own block, so the machine picks the markers up as it builds and the corners close.
 */
public final class QuarryFrame {
    /** How far above the marked layer the top ring runs, which is also how tall the legs are. */
    public static final int HEIGHT = 3;

    private QuarryFrame() {}

    /** The layer the ring and the arm run at. */
    public static int ringY(QuarryArea area) { return area.topY() + HEIGHT; }

    /** West edge of the scaffold, which is the markers' own line rather than the pit's. */
    public static int minX(QuarryArea area) { return area.minX() - 1; }

    public static int maxX(QuarryArea area) { return area.maxX() + 1; }

    public static int minZ(QuarryArea area) { return area.minZ() - 1; }

    public static int maxZ(QuarryArea area) { return area.maxZ() + 1; }

    /**
     * Every girder of the scaffold, in the order it is put up: the outline on the ground first,
     * then the legs rising from its corners, then the ring that closes over the pit. The three
     * together are the box the markers drew, with the pit inside it.
     */
    public static List<BlockPos> positions(QuarryArea area) {
        int minX = minX(area), maxX = maxX(area), minZ = minZ(area), maxZ = maxZ(area);
        int groundY = area.topY();
        int ringY = ringY(area);
        List<BlockPos> girders = new ArrayList<>(4 * (maxX - minX + maxZ - minZ) + 4 * HEIGHT);
        ring(girders, minX, maxX, minZ, maxZ, groundY);
        for (int y = groundY; y <= ringY; y++) {
            girders.add(new BlockPos(minX, y, minZ));
            girders.add(new BlockPos(maxX, y, minZ));
            girders.add(new BlockPos(minX, y, maxZ));
            girders.add(new BlockPos(maxX, y, maxZ));
        }
        ring(girders, minX, maxX, minZ, maxZ, ringY);
        return girders;
    }

    /** The four edges of one layer, corners left out: those belong to the legs that pass through. */
    private static void ring(List<BlockPos> girders, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX + 1; x < maxX; x++) {
            girders.add(new BlockPos(x, y, minZ));
            girders.add(new BlockPos(x, y, maxZ));
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            girders.add(new BlockPos(minX, y, z));
            girders.add(new BlockPos(maxX, y, z));
        }
    }
}
