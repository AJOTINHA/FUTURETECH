package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.TimeControllerBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.TimeControllerMenu;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

/**
 * A machine that sets the time of day. The player picks a {@link DayMoment} on its screen, and
 * when the machine fires the world jumps forward to that moment, paying for the jump by the
 * tick: every tick skipped costs {@value #COST_PER_TICK} FE, so a full day is {@code 120,000}.
 * A jump the buffer cannot pay for does not happen at all.
 *
 * <p>When it fires is its redstone control, read the way the other machines read theirs but
 * meaning something of its own here. Set to ignore the signal, it fires once each time a moment
 * is picked on the screen, and the redstone is nothing to it. Set to work with a signal, or
 * without one, it fires each time that condition comes about — the signal arriving, or going —
 * and not again until it has gone away and come back. It is placed set to "with a signal".
 *
 * <p>The jump is always forward, to the next time that moment comes round, which is what keeps
 * the day count where it was: {@code /time set} would put the world back on day zero. The
 * overworld's clock is the one that moves, the way {@code /time add} moves it, and every
 * dimension's sky follows it. Energy comes in on every face.
 */
public final class TimeControllerBlockEntity extends BlockEntity implements MenuProvider, RedstoneControllable {
    public static final int CAPACITY = 250_000;
    public static final int INPUT_PER_TICK = 2_000;
    /** What a tick of skipped time costs. */
    public static final int COST_PER_TICK = 5;
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_MOMENT = 2;
    public static final int DATA_REDSTONE_BASE = 3;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;

    private DayMoment moment = DayMoment.SUNRISE;
    /**
     * Whether the redstone condition held at the last tick; a signal mode fires on the tick it
     * starts holding. Starts true and is not saved, so a block placed or loaded with its
     * condition already met waits for it to go and come back.
     */
    private boolean wasMet = true;
    /** Set by a jump and read by the tick, which lights the block for a moment. */
    private boolean jumped;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::setChanged);
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_MOMENT -> moment.ordinal();
                default -> {
                    if (index >= DATA_REDSTONE_BASE && index < DATA_COUNT) yield redstone.data(index - DATA_REDSTONE_BASE);
                    yield 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public TimeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIME_CONTROLLER.get(), pos, state);
        // Placed, it fires on a signal; the other machines start on "ignored", which here fires from the screen alone.
        redstone.setMode(RedstoneMode.HIGH);
    }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    public DayMoment moment() { return moment; }

    /** The screen picked a moment: remembered, and — with the redstone ignored — fired at once. */
    public void setMoment(DayMoment moment) {
        this.moment = moment;
        setChanged();
        if (redstone.mode() == RedstoneMode.IGNORED && level instanceof ServerLevel server) jump(server);
    }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    /** What a jump from {@code clockTime} to {@code moment} costs; zero when the world is there already. */
    public static int cost(long clockTime, DayMoment moment) {
        return (int) (moment.skipped(clockTime) * COST_PER_TICK);
    }

    /** Moves the overworld's clock forward to the next of the chosen moment, if the buffer can pay for the ticks between. */
    private void jump(ServerLevel level) {
        ServerClockManager clocks = level.getServer().clockManager();
        Holder<WorldClock> clock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        long now = clocks.getTotalTicks(clock);
        long skipped = moment.skipped(now);
        int cost = cost(now, moment);
        if (skipped == 0 || energy.getAmountAsInt() < cost) return;
        energy.set(energy.getAmountAsInt() - cost);
        clocks.addTicks(clock, (int) skipped);
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        jumped = true;
        setChanged();
    }

    /** The redstone is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    @Override
    public void setChanged() { ComparatorNotifier.markChanged(this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TimeControllerBlockEntity controller) {
        controller.energy.beginTick();
        // A signal mode fires on the edge: the tick its condition starts to hold. Ignoring the signal, it never holds "anew".
        boolean met = controller.redstone.allowsRunning();
        if (met && !controller.wasMet && controller.redstone.mode() != RedstoneMode.IGNORED) controller.jump((ServerLevel) level);
        controller.wasMet = met;
        // Lit for a moment after a jump: the light says the clock just moved.
        boolean lit = controller.litHold.update(controller.jumped);
        controller.jumped = false;
        if (state.getValue(TimeControllerBlock.LIT) != lit) level.setBlock(pos, state.setValue(TimeControllerBlock.LIT, lit), 3);
        controller.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(controller.energy));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, CAPACITY));
        moment = input.read("Moment", DayMoment.CODEC).orElse(DayMoment.SUNRISE);
        redstone.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("Moment", DayMoment.CODEC, moment);
        redstone.save(output);
    }

    @Override
    public Component getDisplayName() { return Component.translatable("block.futuretech.time_controller"); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TimeControllerMenu(id, inventory, this, data);
    }
}
