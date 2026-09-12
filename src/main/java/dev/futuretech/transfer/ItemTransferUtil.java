package dev.futuretech.transfer;

import dev.futuretech.api.side.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import java.util.function.Predicate;

/**
 * Moves items between a machine and whatever is against its configured faces. The machine's own
 * face rules decide which slots a side may touch, so a face set to input only ever receives and a
 * face set to output only ever gives.
 */
public final class ItemTransferUtil {
    /** Items a machine moves per tick in each direction, shared across all of its faces. */
    public static final int PER_TICK = 4;
    private static final Predicate<ItemResource> ANY = resource -> true;

    /** Takes items from neighbours into the faces configured as input. */
    public static void pullFromNeighbours(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides) {
        transfer(level, pos, container, sides, true);
    }

    /** Hands items from the faces configured as output to their neighbours. */
    public static void pushToNeighbours(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides) {
        transfer(level, pos, container, sides, false);
    }

    private static void transfer(Level level, BlockPos pos, WorldlyContainer container, SideConfig sides, boolean pulling) {
        Direction[] faces = Direction.values();
        // The first face rotates each tick so one busy neighbour cannot starve the others.
        int first = (int) (level.getGameTime() % faces.length);
        int budget = PER_TICK;
        for (int step = 0; step < faces.length && budget > 0; step++) {
            Direction side = faces[(first + step) % faces.length];
            boolean open = pulling ? sides.allowsItemInput(side) : sides.allowsItemOutput(side);
            if (!open) continue;
            BlockPos neighbourPos = pos.relative(side);
            if (!level.hasChunkAt(neighbourPos.getX(), neighbourPos.getZ())) continue;
            ResourceHandler<ItemResource> neighbour =
                    level.getCapability(Capabilities.Item.BLOCK, neighbourPos, side.getOpposite());
            if (neighbour == null) continue;
            // Going through the face wrapper keeps the machine's own slot rules in charge.
            var own = new WorldlyContainerWrapper(container, side);
            budget -= pulling
                    ? ResourceHandlerUtil.moveStacking(neighbour, own, ANY, budget, null)
                    : ResourceHandlerUtil.moveStacking(own, neighbour, ANY, budget, null);
        }
    }

    private ItemTransferUtil() {}
}
