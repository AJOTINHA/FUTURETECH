package dev.futuretech.api.upgrade;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The machine's permanent level, saved and synchronized by its block state. MK1 is the plain
 * machine; every level above it unlocks one more upgrade slot and scales the machine's power:
 * it draws more per tick, finishes a job proportionally sooner and holds a proportionally
 * bigger buffer, so an item costs the same energy on every level. The scale follows Thermal
 * Expansion's machine tiers: 1x, 1.5x, 2x and 3x. What the upgrades in the slots add is in
 * {@link UpgradeInventory}.
 */
public final class MachineLevel {
    public static final IntegerProperty MK = IntegerProperty.create("mk", 1, 4);
    public static final int MAX = 4;
    /** Power of each level as a percent of MK1's, indexed by level; slot 0 is unused. */
    public static final int[] SCALE_PERCENT = {100, 100, 150, 200, 300};

    private MachineLevel() {}

    public static int of(BlockState state) {
        return state.hasProperty(MK) ? state.getValue(MK) : 1;
    }

    public static boolean canUpgrade(BlockState state, int target) {
        return state.hasProperty(MK) && target >= 2 && target <= 4 && of(state) + 1 == target;
    }

    /** Power of level {@code mk} as a percent of MK1's. */
    public static int scale(int mk) { return SCALE_PERCENT[Math.clamp(mk, 1, MAX)]; }

    /** Energy capacity of a level-{@code mk} machine whose MK1 holds {@code base}. */
    public static int capacity(int base, int mk) {
        return base * scale(mk) / 100;
    }

    /** Ticks a job takes on a level-{@code mk} machine when it takes {@code base} on an MK1; never below one. */
    public static int duration(int base, int mk) {
        return Math.max(1, Math.round(base * 100.0F / scale(mk)));
    }

    /** Energy drawn per tick of work on a level-{@code mk} machine whose MK1 draws {@code base}. */
    public static int consumption(int base, int mk) {
        return base * scale(mk) / 100;
    }
}
