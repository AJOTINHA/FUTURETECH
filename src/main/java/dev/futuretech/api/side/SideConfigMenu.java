package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Menu side of the face configuration. Modes, the front and the auto-transfer toggles reach the
 * client through data slots; clicks travel back as vanilla menu button ids, the six face ordinals
 * followed by {@link #BUTTON_CLEAR_ALL}, {@link #BUTTON_AUTO_PULL} and {@link #BUTTON_AUTO_PUSH}.
 */
public interface SideConfigMenu {
    /** Button id that resets every face to {@link SideMode#NONE}; face ids are the six direction ordinals. */
    int BUTTON_CLEAR_ALL = Direction.values().length;
    int BUTTON_AUTO_PULL = BUTTON_CLEAR_ALL + 1;
    int BUTTON_AUTO_PUSH = BUTTON_AUTO_PULL + 1;
    /** Ids this feature owns; whatever comes next starts here. */
    int BUTTON_COUNT = BUTTON_AUTO_PUSH + 1;

    SideMode sideMode(Direction side);

    Direction front();

    BlockState displayState();

    Set<SideMode> allowedModes();

    /** Whether the machine offers the toggle at all; a battery holds no items, so it offers neither. */
    boolean supportsAutoPull();

    boolean supportsAutoPush();

    boolean isAutoPulling();

    boolean isAutoPushing();

    /** Server-side handling of a face or toggle click; wire it into {@code clickMenuButton}. */
    static boolean handleButton(@Nullable SideConfigurable target, int buttonId) {
        if (target == null || buttonId < 0 || buttonId >= BUTTON_COUNT) return false;
        if (buttonId == BUTTON_AUTO_PULL || buttonId == BUTTON_AUTO_PUSH) {
            // A machine without an inventory has no toggles; ignore rather than crash on a crafted packet.
            if (!(target instanceof AutoTransferable machine)) return false;
            AutoTransfer auto = machine.autoTransfer();
            if (buttonId == BUTTON_AUTO_PULL) auto.setPulling(!auto.isPulling());
            else auto.setPushing(!auto.isPushing());
            machine.autoTransferChanged();
            return true;
        }
        if (buttonId == BUTTON_CLEAR_ALL) target.sideConfig().clear();
        else target.sideConfig().cycle(Direction.values()[buttonId]);
        target.sideConfigChanged();
        return true;
    }
}
