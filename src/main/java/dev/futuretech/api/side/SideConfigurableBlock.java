package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** A block whose block entity implements {@link SideConfigurable}; shared by server and client menus. */
public interface SideConfigurableBlock {
    Set<SideMode> allowedSideModes();

    /** Fresh configuration for a block placed with this state. */
    SideConfig createSideConfig(BlockState state);

    /** State used to draw the six face textures for a machine whose front points {@code front}. */
    BlockState displayState(Direction front);

    /** Whether this machine can take items from whatever sits against an input face. */
    default boolean supportsAutoPull() { return false; }

    /** Whether it can hand results to whatever sits against an output face. */
    default boolean supportsAutoPush() { return false; }
}
