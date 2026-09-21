package dev.futuretech.api.upgrade;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.IntSupplier;

/**
 * The upgrade slots of one machine. Only items in the {@code futuretech:upgrades} tag fit, one per
 * slot, and only the first {@link #unlocked()} slots take anything: a machine unlocks as many as
 * its MK level, so a plain MK1 has one and an MK4 all four. What the installed upgrades do is
 * asked here too, on top of the level's own scale from {@link MachineLevel}, the way Thermal
 * Expansion's augments work: a speed upgrade adds another whole level's worth of power and makes
 * every item cost a little more; an efficiency upgrade takes a slice off the draw with the job
 * taking just as long, so every item costs that much less.
 */
public final class UpgradeInventory extends SimpleContainer {
    public static final int SLOTS = 4;
    public static final TagKey<Item> UPGRADES = tag("upgrades");
    /** Speed upgrades: each adds the level's whole power again, at {@link #SPEED_ENERGY_PERCENT} more per item. */
    public static final TagKey<Item> SPEED = tag("upgrades/speed");
    /** Efficiency upgrades: each takes {@link #EFFICIENCY_PERCENT} off the draw, and so off every item. */
    public static final TagKey<Item> EFFICIENCY = tag("upgrades/efficiency");
    /** Lava upgrades: a boiler carrying one heats its water with lava instead of solid fuel. */
    public static final TagKey<Item> LAVA = tag("upgrades/lava");
    /** Energy upgrades: a boiler carrying one heats its water with FE instead of solid fuel. */
    public static final TagKey<Item> ENERGY = tag("upgrades/energy");
    /** Sand upgrades: an extruder carrying one turns out sand, gravel and their kin instead of stone. */
    public static final TagKey<Item> SAND = tag("upgrades/sand");
    /** Percent more energy an item costs for every speed upgrade; four of them cost 40% more. */
    public static final int SPEED_ENERGY_PERCENT = 10;
    /** Percent of the draw every efficiency upgrade takes off; four of them leave 40%. */
    public static final int EFFICIENCY_PERCENT = 15;
    private static final String TAG = "Upgrades";

    private final IntSupplier unlocked;
    private final Runnable onChanged;

    /** Every slot unlocked; for menus on the client and tests. */
    public UpgradeInventory(Runnable onChanged) {
        this(() -> SLOTS, onChanged);
    }

    public UpgradeInventory(IntSupplier unlocked, Runnable onChanged) {
        super(SLOTS);
        this.unlocked = unlocked;
        this.onChanged = onChanged;
    }

    /** How many slots, counted from the first, take upgrades right now. */
    public int unlocked() { return Math.clamp(unlocked.getAsInt(), 1, SLOTS); }

    public boolean isUnlocked(int slot) { return slot >= 0 && slot < unlocked(); }

    /** Upgrades sitting in unlocked slots. */
    public int installed() {
        int count = 0;
        for (int slot = 0; slot < unlocked(); slot++) if (isUpgrade(getItem(slot))) count++;
        return count;
    }

    /** Upgrades in unlocked slots that are in {@code kind}. */
    public int installed(TagKey<Item> kind) {
        int count = 0;
        for (int slot = 0; slot < unlocked(); slot++) if (getItem(slot).is(kind)) count++;
        return count;
    }

    /**
     * Energy drawn per tick by a level-{@code mk} machine whose MK1 draws {@code base}: the level's
     * draw once for the machine and once more for every speed upgrade, less what the efficiency
     * upgrades take off.
     */
    public int consumption(int base, int mk) {
        return efficient(MachineLevel.consumption(base, mk) * (1 + installed(SPEED)));
    }

    /**
     * Ticks a job takes on a level-{@code mk} machine when it takes {@code base} on a plain MK1:
     * the level's time, with the speed upgrades' extra power spread over the extra energy they
     * make the job cost; never below one.
     */
    public int duration(int base, int mk) {
        int speed = installed(SPEED);
        return Math.max(1, Math.round(MachineLevel.duration(base, mk) * (100 + SPEED_ENERGY_PERCENT * speed) / (100.0F * (1 + speed))));
    }

    /**
     * What a one-off job costing {@code energy} on a plain machine costs with the upgrades
     * installed: more for every speed upgrade, less for every efficiency upgrade; never below one.
     */
    public int cost(int energy) {
        int percent = Math.max(0, 100 + SPEED_ENERGY_PERCENT * installed(SPEED) - EFFICIENCY_PERCENT * installed(EFFICIENCY));
        return Math.max(1, (int) ((long) energy * percent / 100));
    }

    /**
     * Energy a level-{@code mk} generator makes per tick when a plain MK1 makes {@code base}: the
     * level's rate, and the level's rate again for every speed upgrade, the way the boiler and the
     * turbine already read them.
     */
    public int generation(int base, int mk) {
        return MachineLevel.consumption(base, mk) * (1 + installed(SPEED));
    }

    /**
     * What a fuel worth {@code energy} yields with the upgrades installed: every efficiency
     * upgrade gets {@link #EFFICIENCY_PERCENT} more out of it and every speed upgrade burns
     * {@link #SPEED_ENERGY_PERCENT} of it away, which is {@link #cost} seen from the other side.
     * Never below one.
     */
    public int yield(int energy) {
        int percent = Math.max(0, 100 + EFFICIENCY_PERCENT * installed(EFFICIENCY) - SPEED_ENERGY_PERCENT * installed(SPEED));
        return Math.max(1, (int) ((long) energy * percent / 100));
    }

    /** What {@code energy} comes to once the installed efficiency upgrades take their share; never below one. */
    public int efficient(int energy) {
        int percent = Math.max(0, 100 - EFFICIENCY_PERCENT * installed(EFFICIENCY));
        return Math.max(1, energy * percent / 100);
    }

    public static boolean isUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UPGRADES);
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("futuretech", path));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return isUnlocked(slot) && isUpgrade(stack); }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public void setChanged() {
        super.setChanged();
        onChanged.run();
    }

    public void save(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child(TAG), items());
    }

    public void load(ValueInput input) {
        NonNullList<ItemStack> loaded = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(TAG), loaded);
        for (int slot = 0; slot < SLOTS; slot++) setItem(slot, loaded.get(slot));
    }

    private NonNullList<ItemStack> items() {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) list.set(slot, getItem(slot));
        return list;
    }
}
