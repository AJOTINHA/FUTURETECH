package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Item handler a face offers. The container's own {@code getSlotsForFace} and
 * {@code can*ItemThroughFace} already follow the side configuration, so hoppers and this capability
 * read the same thing; this only hides the handler entirely on a closed face.
 */
public final class SidedItems {
    /** {@code null} for {@link SideMode#NONE}, so neighbours see no item capability on a closed face. */
    public static @Nullable ResourceHandler<ItemResource> view(
            WorldlyContainer container, SideConfig sides, @Nullable Direction side) {
        if (side != null && sides.mode(side) == SideMode.NONE) return null;
        return new WorldlyContainerWrapper(container, side);
    }

    private SidedItems() {}
}
