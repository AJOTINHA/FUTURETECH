package dev.futuretech.api.gui;

/** What an {@link EnergyInfoTab} reads off a machine menu; every value comes from synced data slots. */
public interface EnergyInfoMenu {
    /** Whether the machine spends energy or makes it; decides how the rate lines are labelled. */
    enum Kind { CONSUMER, GENERATOR }

    int energyStored();

    int energyCapacity();

    /** The most energy the machine moves per tick: drawn by a consumer, made by a generator. */
    int energyRatePerTick();

    /** Energy moved this tick: nothing while idle, and a machine with several lanes only what the busy ones take. */
    default int energyUsagePerTick() { return isWorking() ? energyRatePerTick() : 0; }

    /** True while the machine is actually doing work this tick. */
    boolean isWorking();

    Kind kind();

    /** Fastest the machine hands energy to its neighbours; 0 leaves the line out. */
    default int energyOutputPerTick() { return 0; }
}
