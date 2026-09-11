package dev.futuretech.api.redstone;

/** A block entity whose redstone mode can be set from its menu. */
public interface RedstoneControllable {
    RedstoneControl redstoneControl();

    /** Called on the server after the mode changed. */
    default void redstoneControlChanged() {}
}
