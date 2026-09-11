package dev.futuretech.api.side;

import net.minecraft.core.Direction;

/** A block entity whose faces can be configured from its menu. */
public interface SideConfigurable {
    SideConfig sideConfig();

    /** Face the screen shows in the middle of its layout; the others are named relative to it. */
    Direction front();

    /** Called on the server after a face changed, so the machine can re-expose capabilities and update neighbours. */
    void sideConfigChanged();
}
