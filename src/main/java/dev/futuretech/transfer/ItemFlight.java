package dev.futuretech.transfer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * An item on its way through item cables: what it is, the cables it will cross, where it goes in
 * and out, and how far along it is. It belongs to the network while one exists and is parked on
 * the cable it is inside whenever the network is torn down or the cable is saved.
 */
public final class ItemFlight {
    public static final Codec<ItemFlight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(flight -> flight.id),
            ItemStack.CODEC.fieldOf("item").forGetter(flight -> flight.stack),
            BlockPos.CODEC.listOf().fieldOf("path").forGetter(flight -> flight.path),
            Direction.CODEC.fieldOf("from").forGetter(flight -> flight.from),
            Direction.CODEC.fieldOf("to").forGetter(flight -> flight.to),
            DyeColor.CODEC.fieldOf("color").forGetter(flight -> flight.color),
            Codec.INT.fieldOf("channel").forGetter(flight -> flight.channel),
            Codec.INT.fieldOf("travelled").forGetter(flight -> flight.travelled),
            Codec.INT.fieldOf("ticks_per_block").forGetter(flight -> flight.ticksPerBlock),
            Codec.BOOL.fieldOf("waiting").forGetter(flight -> flight.waiting)
    ).apply(instance, ItemFlight::new));
    public static final Codec<List<ItemFlight>> LIST_CODEC = CODEC.listOf();

    /** Tells the clients which drawn item this is, across re-announcements. */
    public final long id;
    public ItemStack stack;
    /** The cables crossed, entry cable first; the last one holds the exit connector. */
    public List<BlockPos> path;
    /** The face of the first cable the item came in through. */
    public Direction from;
    /** The face of the last cable it leaves through, into its destination. */
    public Direction to;
    /** The line the item travels on; a new destination must match both. */
    public final DyeColor color;
    public final int channel;
    /** Ticks since it left the entry face. */
    public int travelled;
    public final int ticksPerBlock;
    /** True once it reached the exit face but the destination would not take it yet. */
    public boolean waiting;

    public ItemFlight(long id, ItemStack stack, List<BlockPos> path, Direction from, Direction to, DyeColor color,
                      int channel, int travelled, int ticksPerBlock, boolean waiting) {
        this.id = id;
        this.stack = stack;
        this.path = List.copyOf(path);
        this.from = from;
        this.to = to;
        this.color = color;
        this.channel = channel;
        this.travelled = travelled;
        this.ticksPerBlock = Math.max(1, ticksPerBlock);
        this.waiting = waiting;
    }

    /** Ticks from the entry face to the exit face: half a cable at each end plus the cables between. */
    public int duration() { return path.size() * ticksPerBlock; }

    public boolean arrived() { return travelled >= duration(); }

    /** The cable the item is inside right now. */
    public BlockPos current() {
        int index = (int) Math.floor((double) travelled / ticksPerBlock + 0.5);
        return path.get(Math.clamp(index, 0, path.size() - 1));
    }

    /** The connector this flight is heading for. */
    public ItemCableNetwork.EndpointKey destination() {
        return new ItemCableNetwork.EndpointKey(path.getLast(), to);
    }
}
