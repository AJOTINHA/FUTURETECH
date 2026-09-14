package dev.futuretech.client;

import dev.futuretech.transfer.ItemJourneyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The items currently drawn on their way through item cables. Each journey is a polyline from
 * the middle of the entry face, through the centre of every cable crossed, to the middle of the
 * exit face; the item moves along it at the server's pace and stays at the end until the server
 * says it went in. A fresh report with the same id replaces the old picture.
 */
public final class ItemTravel {
    /** How big the item is drawn; small enough to sit inside a cable. */
    public static final float ITEM_SCALE = 0.25F;
    /** A journey the server never closed is forgotten after this long past its arrival. */
    private static final long GRACE_TICKS = 20 * 60 * 5;

    public record Journey(List<BlockPos> path, Direction from, Direction to, ItemStack stack, long start, int ticksPerBlock) {
        /** Blocks travelled from the entry face to the exit face: half a block at each end plus the cables between. */
        double length() { return path.size(); }

        /** Ticks the whole trip takes. */
        long duration() { return Math.round(length() * ticksPerBlock); }

        private Vec3 point(int index) {
            if (index == 0) return Vec3.atCenterOf(path.getFirst()).add(from.getUnitVec3().scale(0.5));
            if (index == path.size() + 1) return Vec3.atCenterOf(path.getLast()).add(to.getUnitVec3().scale(0.5));
            return Vec3.atCenterOf(path.get(index - 1));
        }

        /** Where the item is after {@code ticks} ticks, clamped to the ends of the trip. */
        public Vec3 position(double ticks) {
            double travelled = Math.clamp(ticks / ticksPerBlock, 0, length());
            // The first and last legs are half a block; every leg between cables is a whole one.
            for (int leg = 0; leg <= path.size(); leg++) {
                double legLength = leg == 0 || leg == path.size() ? 0.5 : 1.0;
                if (travelled <= legLength || leg == path.size()) {
                    return point(leg).lerp(point(leg + 1), Math.clamp(travelled / legLength, 0, 1));
                }
                travelled -= legLength;
            }
            return point(path.size() + 1);
        }
    }

    private static final Map<Long, Journey> journeys = new LinkedHashMap<>();

    private ItemTravel() {}

    /** A report from the server: a new journey, or the current state of one already drawn. */
    public static void add(ItemJourneyPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null || payload.path().isEmpty()) return;
        journeys.put(payload.id(), new Journey(payload.path(), payload.from(), payload.to(), payload.stack(),
                level.getGameTime() - payload.travelled(), Math.max(1, payload.ticksPerBlock())));
    }

    public static void end(long id) { journeys.remove(id); }

    /** Drops journeys the server forgot to close, and everything when there is no level to draw them in. */
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            journeys.clear();
            return;
        }
        long now = level.getGameTime();
        journeys.values().removeIf(journey -> now - journey.start() >= journey.duration() + GRACE_TICKS);
    }

    public static Collection<Journey> journeys() { return journeys.values(); }
}
