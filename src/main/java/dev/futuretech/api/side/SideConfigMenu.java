package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Menu side of the face configuration. Modes, the front and the auto-transfer toggles reach the
 * client through data slots; clicks travel back as vanilla menu button ids, the six face ordinals
 * followed by clear/auto-transfer buttons and six reverse-cycle ids.
 */
public interface SideConfigMenu {
    /** Button id that resets every face to {@link SideMode#NONE}; face ids are the six direction ordinals. */
    int BUTTON_CLEAR_ALL = Direction.values().length;
    int BUTTON_AUTO_PULL = BUTTON_CLEAR_ALL + 1;
    int BUTTON_AUTO_PUSH = BUTTON_AUTO_PULL + 1;
    int BUTTON_AUTO_SORT = BUTTON_AUTO_PUSH + 1;
    /** Six right-click ids, indexed by direction just like the forward face buttons. */
    int BUTTON_REVERSE_BASE = BUTTON_AUTO_SORT + 1;
    /** Ids this feature owns; whatever comes next starts here. */
    int BUTTON_COUNT = BUTTON_REVERSE_BASE + Direction.values().length;

    SideMode sideMode(Direction side);

    Direction front();

    BlockState displayState();

    Set<SideMode> allowedModes();

    /** Whether the machine offers the toggle at all; a battery holds no items, so it offers neither. */
    boolean supportsAutoPull();

    boolean supportsAutoPush();

    boolean isAutoPulling();

    boolean isAutoPushing();

    /** Whether the machine can spread its inputs over lanes: only one with more than one lane open. */
    default boolean supportsSorting() { return false; }

    default boolean isSorting() { return false; }

    /**
     * What the menu slot at {@code index} is to the faces, so the screen can mark the input and
     * output slots while the face configuration is open; the player's own slots are {@link SlotRole#NONE}.
     */
    default SlotRole slotRole(int index) { return SlotRole.NONE; }

    /** Server-side handling of a face or toggle click; wire it into {@code clickMenuButton}. */
    static boolean handleButton(@Nullable SideConfigurable target, int buttonId) {
        if (target == null || buttonId < 0 || buttonId >= BUTTON_COUNT) return false;
        if (buttonId == BUTTON_AUTO_PULL || buttonId == BUTTON_AUTO_PUSH || buttonId == BUTTON_AUTO_SORT) {
            // A machine without an inventory has no toggles; ignore rather than crash on a crafted packet.
            if (!(target instanceof AutoTransferable machine)) return false;
            AutoTransfer auto = machine.autoTransfer();
            if (buttonId == BUTTON_AUTO_PULL) auto.setPulling(!auto.isPulling());
            else if (buttonId == BUTTON_AUTO_PUSH) auto.setPushing(!auto.isPushing());
            else auto.setSorting(!auto.isSorting());
            machine.autoTransferChanged();
            return true;
        }
        if (buttonId == BUTTON_CLEAR_ALL) target.sideConfig().clear();
        else if (buttonId >= BUTTON_REVERSE_BASE) {
            target.sideConfig().cycle(Direction.values()[buttonId - BUTTON_REVERSE_BASE], true);
        }
        else target.sideConfig().cycle(Direction.values()[buttonId]);
        target.sideConfigChanged();
        return true;
    }
}
