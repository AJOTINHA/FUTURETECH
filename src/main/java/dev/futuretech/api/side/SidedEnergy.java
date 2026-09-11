package dev.futuretech.api.side;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Restricts an energy handler to what a face's {@link SideMode} permits. */
public final class SidedEnergy {
    /** The view neighbours get through a face: {@code null} for {@link SideMode#NONE}, so no capability is offered. */
    public static @Nullable EnergyHandler view(EnergyHandler full, SideMode mode) {
        return switch (mode) {
            case NONE -> null;
            case BOTH -> full;
            case INPUT, OUTPUT -> new EnergyHandler() {
                @Override
                public long getAmountAsLong() { return full.getAmountAsLong(); }

                @Override
                public long getCapacityAsLong() { return full.getCapacityAsLong(); }

                @Override
                public int insert(int amount, TransactionContext transaction) {
                    return mode.allowsInput() ? full.insert(amount, transaction) : 0;
                }

                @Override
                public int extract(int amount, TransactionContext transaction) {
                    return mode.allowsOutput() ? full.extract(amount, transaction) : 0;
                }
            };
        };
    }

    private SidedEnergy() {}
}
