package dev.futuretech.api.gui;

/** What an {@link EnergyInfoTab} reads off a machine menu; every value comes from synced data slots. */
public interface EnergyInfoMenu {
    int energyStored();

    int energyCapacity();

    /** Energy the machine draws per tick while running, so the tab can show the current drain. */
    int energyUsagePerTick();

    /** True while the machine is actually doing work this tick. */
    boolean isWorking();
}
