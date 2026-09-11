package dev.futuretech.energy;

/**
 * Splits an energy amount across two menu data slots. Vanilla syncs each slot as a signed
 * 16-bit value, so anything above 32,767 FE has to travel as separate halves.
 */
public final class EnergySync {
    public static int low(int amount) { return amount & 0xFFFF; }

    public static int high(int amount) { return amount >>> 16; }

    /** Rebuilds the amount from the two synced halves, tolerating sign extension of the low half. */
    public static int unpack(int low, int high) { return (high << 16) | (low & 0xFFFF); }

    private EnergySync() {}
}
