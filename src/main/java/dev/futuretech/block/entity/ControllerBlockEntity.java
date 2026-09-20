package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.block.ControllerBlock;
import dev.futuretech.block.ControllerKind;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.ControllerMenu;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
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
 * A machine that sets something about the world — the time of day, the weather — to a choice
 * made on its screen; what it sets, what the choices are and what a change costs are its
 * {@link ControllerKind}. A change the buffer cannot pay for does not happen at all.
 *
 * <p>When it fires is its redstone control, read the way the other machines read theirs but
 * meaning something of its own here. Set to ignore the signal, it fires once each time a choice
 * is picked on the screen, and the redstone is nothing to it. Set to work with a signal, or
 * without one, it fires each time that condition comes about — the signal arriving, or going —
 * and not again until it has gone away and come back. It is placed set to "with a signal".
 * Energy comes in on every face.
 */
public final class ControllerBlockEntity extends BlockEntity implements MenuProvider, RedstoneControllable {
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_CHOICE = 2;
    public static final int DATA_REDSTONE_BASE = 3;
    public static final int DATA_COUNT = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;

    private final ControllerKind kind;
    private int choice;
    /**
     * Whether the redstone condition held at the last tick; a signal mode fires on the tick it
     * starts holding. Starts true and is not saved, so a block placed or loaded with its
     * condition already met waits for it to go and come back.
     */
    private boolean wasMet = true;
    /** Set by a change and read by the tick, which lights the block for a moment. */
    private boolean fired;
    private final TickLimitedEnergyHandler energy;
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_CHOICE -> choice;
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

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROLLER.get(), pos, state);
        kind = state.getBlock() instanceof ControllerBlock block ? block.kind() : ControllerKind.TIME;
        energy = new TickLimitedEnergyHandler(kind.capacity(), ControllerKind.INPUT_PER_TICK, 0, this::setChanged);
        // Placed, it fires on a signal; the other machines start on "ignored", which here fires from the screen alone.
        redstone.setMode(RedstoneMode.HIGH);
    }

    public ControllerKind kind() { return kind; }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    public int choice() { return choice; }

    /** The screen picked a choice: remembered, and — with the redstone ignored — fired at once. */
    public void setChoice(int choice) {
        this.choice = kind.clampChoice(choice);
        setChanged();
        if (redstone.mode() == RedstoneMode.IGNORED && level instanceof ServerLevel server) fire(server);
    }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    /** Makes the change, if there is one to make and the buffer can pay for it. */
    private void fire(ServerLevel level) {
        int cost = kind.cost(level, choice);
        if (cost == 0 || energy.getAmountAsInt() < cost) return;
        energy.set(energy.getAmountAsInt() - cost);
        kind.apply(level, choice);
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        fired = true;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity controller) {
        controller.energy.beginTick();
        // A signal mode fires on the edge: the tick its condition starts to hold. Ignoring the signal, it never holds "anew".
        boolean met = controller.redstone.allowsRunning();
        if (met && !controller.wasMet && controller.redstone.mode() != RedstoneMode.IGNORED) controller.fire((ServerLevel) level);
        controller.wasMet = met;
        // Lit for a moment after a change: the light says it just fired.
        boolean lit = controller.litHold.update(controller.fired);
        controller.fired = false;
        if (state.getValue(ControllerBlock.LIT) != lit) level.setBlock(pos, state.setValue(ControllerBlock.LIT, lit), 3);
        controller.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(controller.energy));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, kind.capacity()));
        choice = kind.clampChoice(input.getIntOr("Choice", 0));
        redstone.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", energy.getAmountAsInt());
        output.putInt("Choice", choice);
        redstone.save(output);
    }

    @Override
    public Component getDisplayName() { return Component.translatable("block.futuretech." + kind.blockName()); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ControllerMenu(id, inventory, this, data);
    }
}
