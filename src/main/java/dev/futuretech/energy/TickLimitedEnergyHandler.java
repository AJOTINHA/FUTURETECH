package dev.futuretech.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Energy buffer whose insertion and extraction are capped per game tick rather than per call,
 * so neighbours pushing or pulling through the capability cannot exceed the block's rated transfer.
 */
public final class TickLimitedEnergyHandler extends SimpleEnergyHandler {
    private final int inputPerTick;
    private final int outputPerTick;
    private final Runnable onChanged;
    private int inputRemaining;
    private int outputRemaining;

    private record Budget(int input, int output) {}

    // Journaled so an aborted transaction gives its share of the tick budget back.
    private final SnapshotJournal<Budget> budgetJournal = new SnapshotJournal<>() {
        @Override
        protected Budget createSnapshot() { return new Budget(inputRemaining, outputRemaining); }

        @Override
        protected void revertToSnapshot(Budget snapshot) {
            inputRemaining = snapshot.input();
            outputRemaining = snapshot.output();
        }
    };

    public TickLimitedEnergyHandler(int capacity, int inputPerTick, int outputPerTick, Runnable onChanged) {
        super(capacity, inputPerTick, outputPerTick);
        this.inputPerTick = inputPerTick;
        this.outputPerTick = outputPerTick;
        this.inputRemaining = inputPerTick;
        this.outputRemaining = outputPerTick;
        this.onChanged = onChanged;
    }

    /** Restores the full transfer budgets; call once at the start of each server tick. */
    public void beginTick() {
        inputRemaining = inputPerTick;
        outputRemaining = outputPerTick;
    }

    public int inputRemaining() { return inputRemaining; }

    public int outputRemaining() { return outputRemaining; }

    /** Energy accepted since the last {@link #beginTick}. */
    public int inputUsed() { return inputPerTick - inputRemaining; }

    /** Energy handed out since the last {@link #beginTick}. */
    public int outputUsed() { return outputPerTick - outputRemaining; }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int inserted = super.insert(Math.min(amount, inputRemaining), transaction);
        if (inserted > 0) {
            budgetJournal.updateSnapshots(transaction);
            inputRemaining -= inserted;
        }
        return inserted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int extracted = super.extract(Math.min(amount, outputRemaining), transaction);
        if (extracted > 0) {
            budgetJournal.updateSnapshots(transaction);
            outputRemaining -= extracted;
        }
        return extracted;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) { onChanged.run(); }
}
