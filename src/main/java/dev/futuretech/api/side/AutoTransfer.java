package dev.futuretech.api.side;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Whether a machine moves items on its own through the faces it already has configured. Pulling
 * takes from whatever sits against an input face; pushing hands results to whatever sits against an
 * output face. Both start off, so a freshly placed machine never touches a neighbour by surprise.
 */
public final class AutoTransfer {
    /** Menu data slots {@link #data} exposes: one flag each. */
    public static final int DATA_COUNT = 2;
    private static final String TAG_PULL = "AutoPull";
    private static final String TAG_PUSH = "AutoPush";

    private boolean pulling;
    private boolean pushing;

    public boolean isPulling() { return pulling; }

    public boolean isPushing() { return pushing; }

    public void setPulling(boolean pulling) { this.pulling = pulling; }

    public void setPushing(boolean pushing) { this.pushing = pushing; }

    public int data(int index) {
        return switch (index) {
            case 0 -> pulling ? 1 : 0;
            case 1 -> pushing ? 1 : 0;
            default -> 0;
        };
    }

    public void save(ValueOutput output) {
        output.putBoolean(TAG_PULL, pulling);
        output.putBoolean(TAG_PUSH, pushing);
    }

    public void load(ValueInput input) {
        pulling = input.getBooleanOr(TAG_PULL, false);
        pushing = input.getBooleanOr(TAG_PUSH, false);
    }
}
