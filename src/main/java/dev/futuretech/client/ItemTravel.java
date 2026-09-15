package dev.futuretech.client;

import dev.futuretech.transfer.ItemJourneyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The items currently drawn on their way through item cables. Each journey is a polyline from
 * the middle of the entry face, through the centre of every cable crossed, to the middle of the
 * exit face; the item moves along it at the server's pace on a clock of this class's own, counted
 * in client ticks, so nothing the world's time does can make it jump. When the server says the
 * item went in, the picture still finishes the trip before it goes; a fresh report with the same
 * id replaces the old picture.
 */
public final class ItemTravel {
    /** How big the item is drawn: a block item comes out just over three units, clear of the capsule's glass. */
    public static final float ITEM_SCALE = 0.2F;
    /** A journey the server never closed is forgotten after this long past its arrival. */
    private static final long GRACE_TICKS = 20 * 60 * 5;

    public static final class Journey {
        final List<BlockPos> path;
        final Direction from;
        final Direction to;
        final ItemStack stack;
        final long start;
        final int ticksPerBlock;
        boolean ended;
        /** The item's resolved model, built by the first cable that draws it and kept for the trip. */
        @Nullable ItemStackRenderState render;

        Journey(List<BlockPos> path, Direction from, Direction to, ItemStack stack, long start, int ticksPerBlock) {
            this.path = path;
            this.from = from;
            this.to = to;
            this.stack = stack;
            this.start = start;
            this.ticksPerBlock = ticksPerBlock;
        }

        public ItemStack stack() { return stack; }

        public long start() { return start; }

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
    /** Where every journey is this frame, grouped by the cable it is in; built once per frame, see {@link #inside}. */
    private static final Map<BlockPos, List<Placed>> placed = new HashMap<>();
    private static double placedAt = Double.NaN;

    /** A journey and the point it is drawn at this frame. */
    public record Placed(Journey journey, Vec3 position) {}
    /** Client ticks seen so far; every journey is timed against this. */
    private static long clock;

    private ItemTravel() {}

    /** The current moment on the journeys' clock, part-way into the tick being drawn. */
    public static double now(float partialTick) { return clock + partialTick; }

    /** A report from the server: a new journey, or the current state of one already drawn. */
    public static void add(ItemJourneyPayload payload) {
        if (Minecraft.getInstance().level == null || payload.path().isEmpty()) return;
        journeys.put(payload.id(), new Journey(payload.path(), payload.from(), payload.to(), payload.stack(),
                clock - payload.travelled(), Math.max(1, payload.ticksPerBlock())));
    }

    /** The server delivered or dropped the item; its picture goes once it has reached the end. */
    public static void end(long id) {
        Journey journey = journeys.get(id);
        if (journey != null) journey.ended = true;
    }

    /** Advances the clock and drops what is done, and everything when there is no level to draw in. */
    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            journeys.clear();
            return;
        }
        clock++;
        journeys.values().removeIf(journey -> {
            long elapsed = clock - journey.start;
            return journey.ended ? elapsed >= journey.duration() : elapsed >= journey.duration() + GRACE_TICKS;
        });
    }

    public static Collection<Journey> journeys() { return journeys.values(); }

    /**
     * The journeys inside the cable at {@code pos} at {@code now}. Every cable drawn in a frame
     * asks with the same {@code now}, so the positions are worked out once per frame for all
     * journeys and grouped by cable; before, every visible cable walked every journey itself.
     */
    public static List<Placed> inside(BlockPos pos, double now) {
        if (now != placedAt) {
            placedAt = now;
            placed.clear();
            for (Journey journey : journeys.values()) {
                Vec3 position = journey.position(now - journey.start);
                placed.computeIfAbsent(BlockPos.containing(position), key -> new ArrayList<>(2)).add(new Placed(journey, position));
            }
        }
        return placed.getOrDefault(pos, List.of());
    }
}
