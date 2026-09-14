package dev.futuretech.api.upgrade;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The machine's permanent level, saved and synchronized by its block state. MK1 is the plain
 * machine; every level above it unlocks one more upgrade slot and, on top of the machine's own
 * numbers, adds more energy storage, a little more speed, and a little more draw per tick to pay
 * for that speed. An MK4 stores 1.75 times as much, works about 1.45 times as fast and draws 1.6
 * times as much while doing so, so each item costs slightly more energy than on an MK1.
 */
public final class MachineLevel {
    public static final IntegerProperty MK = IntegerProperty.create("mk", 1, 4);
    public static final int MAX = 4;
    public static final int CAPACITY_BONUS_PERCENT = 25;
    public static final int SPEED_BONUS_PERCENT = 15;
    public static final int CONSUMPTION_BONUS_PERCENT = 20;

    private MachineLevel() {}

    public static int of(BlockState state) {
        return state.hasProperty(MK) ? state.getValue(MK) : 1;
    }

    public static boolean canUpgrade(BlockState state, int target) {
        return state.hasProperty(MK) && target >= 2 && target <= 4 && of(state) + 1 == target;
    }

    /** Levels above MK1, which is what the bonuses count. */
    private static int steps(int mk) { return Math.clamp(mk, 1, MAX) - 1; }

    /** Energy capacity of a level-{@code mk} machine whose MK1 holds {@code base}. */
    public static int capacity(int base, int mk) {
        return base * (100 + CAPACITY_BONUS_PERCENT * steps(mk)) / 100;
    }

    /** Ticks a job takes on a level-{@code mk} machine when it takes {@code base} on an MK1; never below one. */
    public static int duration(int base, int mk) {
        return Math.max(1, Math.round(base * 100.0F / (100 + SPEED_BONUS_PERCENT * steps(mk))));
    }

    /** Energy drawn per tick of work on a level-{@code mk} machine whose MK1 draws {@code base}. */
    public static int consumption(int base, int mk) {
        return base * (100 + CONSUMPTION_BONUS_PERCENT * steps(mk)) / 100;
    }
}
