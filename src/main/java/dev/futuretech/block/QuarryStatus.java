package dev.futuretech.block;

/**
 * What a quarry is doing, as one line for its screen. The server keeps it, the menu syncs it as
 * an ordinal and the screen looks the name up in the language file.
 */
public enum QuarryStatus {
    /** Redstone says to hold. */
    OFF,
    /** A marker has to stand against the machine's back face, and none does. */
    NO_MARKER_BEHIND,
    /** There is a marker in front, but its four corners do not close a square. */
    NO_SQUARE,
    /** The markers touch or stand in a line: the frame encloses nothing. */
    TOO_SMALL,
    /** The markers span more than this level's longest side. */
    TOO_BIG,
    /** The scaffold is still going up; the digging waits for it. */
    BUILDING,
    /** The next block is in a chunk nobody is keeping loaded. */
    UNLOADED,
    /** The buffer has no room for what the next block drops. */
    FULL,
    /** Not enough energy in the buffer for a tick of digging. */
    NO_ENERGY,
    MINING,
    /** The box is dug down to the bottom of the world. */
    DONE;

    private final String key = "gui.futuretech.quarry.status." + name().toLowerCase(java.util.Locale.ROOT);

    /** The line a scan without a box leaves on the screen. */
    public static QuarryStatus of(QuarryArea.Result result) {
        return switch (result) {
            case NO_SQUARE -> NO_SQUARE;
            case TOO_SMALL -> TOO_SMALL;
            case TOO_BIG -> TOO_BIG;
            default -> NO_MARKER_BEHIND;
        };
    }

    public static QuarryStatus byOrdinal(int ordinal) {
        var values = values();
        return values[Math.clamp(ordinal, 0, values.length - 1)];
    }

    /** Translation key of this status' line. */
    public String key() { return key; }

    /** Whether the line should be drawn in the warning colour: the quarry is stopped and waiting on the player. */
    public boolean isProblem() {
        return this == NO_MARKER_BEHIND || this == NO_SQUARE || this == TOO_SMALL || this == TOO_BIG || this == FULL || this == UNLOADED;
    }
}
