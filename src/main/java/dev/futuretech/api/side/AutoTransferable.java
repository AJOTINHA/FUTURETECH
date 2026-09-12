package dev.futuretech.api.side;

import net.minecraft.world.WorldlyContainer;

/** A machine that can move items through its own configured faces. */
public interface AutoTransferable extends WorldlyContainer {
    AutoTransfer autoTransfer();

    /** Called on the server after a toggle changed, so the machine can save. */
    void autoTransferChanged();
}
