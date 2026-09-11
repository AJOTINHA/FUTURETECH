package dev.futuretech.menu;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;

/**
 * Base of the mod's machine menus. On the client it remembers whether the server's data slots
 * have arrived yet, so screens can wait instead of animating from zero on the first frames.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    private boolean synced;

    protected MachineMenu(@Nullable MenuType<?> type, int id) {
        super(type, id);
    }

    /** True once the client received its first data-slot update; always true on the server. */
    public boolean isSynced() { return synced; }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        synced = true;
    }

    /** Server menus never wait for a sync. */
    protected void markSynced() { synced = true; }
}
