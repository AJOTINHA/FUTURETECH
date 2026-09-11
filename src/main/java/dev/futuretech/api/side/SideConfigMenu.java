package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Menu side of the face configuration. Modes and the front reach the client through data slots;
 * clicks travel back as vanilla menu button ids equal to the face's {@link Direction} ordinal.
 */
public interface SideConfigMenu {
    /** Button id that resets every face to {@link SideMode#NONE}; face ids are the six direction ordinals. */
    int BUTTON_CLEAR_ALL = Direction.values().length;

    SideMode sideMode(Direction side);

    Direction front();

    BlockState displayState();

    Set<SideMode> allowedModes();

    /** Server-side handling of a face click; wire it into {@code clickMenuButton}. */
    static boolean handleButton(@Nullable SideConfigurable target, int buttonId) {
        if (target == null || buttonId < 0 || buttonId > BUTTON_CLEAR_ALL) return false;
        if (buttonId == BUTTON_CLEAR_ALL) target.sideConfig().clear();
        else target.sideConfig().cycle(Direction.values()[buttonId]);
        target.sideConfigChanged();
        return true;
    }
}
