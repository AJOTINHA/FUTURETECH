package dev.futuretech.block.entity;

/**
 * Keeps a machine's lit look on for a moment after its work stops. A machine fed less energy than
 * it draws works one tick in every few; switching {@code LIT} on and off at that pace would remesh
 * the chunk and, for machines that give light, rerun the light engine every few ticks. Holding the
 * light for a second turns that into a steady glow, and a machine that truly stopped goes dark a
 * second late, which nobody notices. Not saved: a reloaded machine relights within a tick.
 */
final class LitHold {
    /** Ticks the light stays on after the last tick of work. */
    static final int TICKS = 20;
    private int remaining;

    /** Records this tick's work and answers whether the block should show as lit. */
    boolean update(boolean working) {
        if (working) remaining = TICKS;
        else if (remaining > 0) remaining--;
        return remaining > 0;
    }
}
