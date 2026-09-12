package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Restricts an energy handler to what a face allows. */
public final class SidedEnergy {
    /**
     * The view neighbours get through a face, or {@code null} when the face offers no energy at all.
     * On a machine the configuration does not govern energy, so every face gets the full handler; on
     * a battery the face's mode decides each direction.
     */
    public static @Nullable EnergyHandler view(EnergyHandler full, SideConfig sides, @Nullable Direction side) {
        boolean acceptsIn = sides.allowsEnergyInput(side);
        boolean handsOut = sides.allowsEnergyOutput(side);
        if (acceptsIn && handsOut) return full;
        if (!acceptsIn && !handsOut) return null;
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() { return full.getAmountAsLong(); }

            @Override
            public long getCapacityAsLong() { return full.getCapacityAsLong(); }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return acceptsIn ? full.insert(amount, transaction) : 0;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return handsOut ? full.extract(amount, transaction) : 0;
            }
        };
    }

    private SidedEnergy() {}
}
