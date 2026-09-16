package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Restricts a fluid handler to what a face allows, the way {@link SidedItems} does for items. */
public final class SidedFluids {
    /**
     * The intake neighbours get through a face of a machine that burns what it is given: fluid goes
     * in through faces in an input mode and never comes back out. {@code null} on a closed face, so
     * cables see no fluid capability there; a null side is the machine's own unrestricted access.
     */
    public static @Nullable ResourceHandler<FluidResource> intake(
            ResourceHandler<FluidResource> full, SideConfig sides, @Nullable Direction side) {
        if (side == null) return full;
        if (sides.mode(side) == SideMode.NONE) return null;
        // The mode is read on every call, so a cached handler cannot bypass a face closed later.
        return new ResourceHandler<>() {
            @Override public int size() { return full.size(); }
            @Override public FluidResource getResource(int index) { return full.getResource(index); }
            @Override public long getAmountAsLong(int index) { return full.getAmountAsLong(index); }
            @Override public long getCapacityAsLong(int index, FluidResource fluid) { return full.getCapacityAsLong(index, fluid); }
            @Override public boolean isValid(int index, FluidResource fluid) { return full.isValid(index, fluid); }

            @Override
            public int insert(int index, FluidResource fluid, int amount, TransactionContext transaction) {
                return sides.mode(side).allowsInput() ? full.insert(index, fluid, amount, transaction) : 0;
            }

            @Override
            public int extract(int index, FluidResource fluid, int amount, TransactionContext transaction) { return 0; }
        };
    }

    private SidedFluids() {}
}
