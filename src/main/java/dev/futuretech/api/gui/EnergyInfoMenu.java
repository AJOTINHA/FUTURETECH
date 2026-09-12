package dev.futuretech.api.gui;

/** What an {@link EnergyInfoTab} reads off a machine menu; every value comes from synced data slots. */
public interface EnergyInfoMenu {
    /** Whether the machine spends energy or makes it; decides how the rate lines are labelled. */
    enum Kind { CONSUMER, GENERATOR }

    int energyStored();

    int energyCapacity();

    /** Energy moved per tick while running: drawn by a consumer, made by a generator. */
    int energyRatePerTick();

    /** True while the machine is actually doing work this tick. */
    boolean isWorking();

    Kind kind();

    /** Fastest the machine hands energy to its neighbours; 0 leaves the line out. */
    default int energyOutputPerTick() { return 0; }
}
