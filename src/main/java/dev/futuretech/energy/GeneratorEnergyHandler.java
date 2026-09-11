package dev.futuretech.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Output-only energy buffer whose extraction is capped per game tick rather than per call,
 * so neighbours pulling through the capability cannot exceed the generator's rated output.
 */
public final class GeneratorEnergyHandler extends SimpleEnergyHandler {
    private final int outputPerTick;
    private final Runnable onChanged;
    private int outputRemaining;
    // Journaled so an aborted transaction gives its share of the tick budget back.
    private final SnapshotJournal<Integer> outputJournal = new SnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() { return outputRemaining; }

        @Override
        protected void revertToSnapshot(Integer snapshot) { outputRemaining = snapshot; }
    };

    public GeneratorEnergyHandler(int capacity, int outputPerTick, Runnable onChanged) {
        super(capacity, 0, outputPerTick);
        this.outputPerTick = outputPerTick;
        this.outputRemaining = outputPerTick;
        this.onChanged = onChanged;
    }

    /** Restores the full output budget; call once at the start of each server tick. */
    public void beginTick() { outputRemaining = outputPerTick; }

    public int outputRemaining() { return outputRemaining; }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int extracted = super.extract(Math.min(amount, outputRemaining), transaction);
        if (extracted > 0) {
            outputJournal.updateSnapshots(transaction);
            outputRemaining -= extracted;
        }
        return extracted;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) { onChanged.run(); }
}
