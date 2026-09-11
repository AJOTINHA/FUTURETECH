package dev.futuretech.api.redstone;

import dev.futuretech.api.side.SideConfigMenu;
import org.jspecify.annotations.Nullable;

/**
 * Menu side of the redstone control. The mode and signal reach the client through data slots;
 * choosing a mode travels back as a vanilla menu button whose id is {@link #BUTTON_BASE} plus the
 * mode's ordinal, right after the side configuration's button ids.
 */
public interface RedstoneControlMenu {
    int BUTTON_BASE = SideConfigMenu.BUTTON_CLEAR_ALL + 1;

    RedstoneMode redstoneMode();

    boolean isPowered();

    /** Server-side handling of a mode button; wire it into {@code clickMenuButton}. */
    static boolean handleButton(@Nullable RedstoneControllable target, int buttonId) {
        int ordinal = buttonId - BUTTON_BASE;
        if (target == null || ordinal < 0 || ordinal >= RedstoneMode.values().length) return false;
        target.redstoneControl().setMode(RedstoneMode.values()[ordinal]);
        target.redstoneControlChanged();
        return true;
    }
}
