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
}
