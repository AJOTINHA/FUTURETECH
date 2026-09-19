package dev.futuretech.block.entity;

import dev.futuretech.block.WirelessRedstoneBlock;
import dev.futuretech.block.WirelessRedstoneBlock.Kind;
import dev.futuretech.redstone.WirelessRedstone;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a wireless plate keeps: the frequency it is on, and the signal it last read or was last
 * handed. Which of the two it is doing is the block's to say — one block entity serves both ends,
 * because past the job they are the same thing.
 *
 * <p>Nothing here ticks. A transmitter reads the redstone around it when a neighbour changes and
 * when it loads; a receiver is told by {@link WirelessRedstone} when the frequency changes. Both
 * only act when the number actually moved, which is what keeps a transmitter wired to a receiver
 * on its own frequency from going round for ever.
 */
public final class WirelessRedstoneBlockEntity extends BlockEntity {
    /** The highest frequency there is: four digits, which is what the screen's box takes. */
    public static final int MAX_FREQUENCY = 9999;
    private static final String FREQUENCY = "Frequency";
    private static final String POWER = "Power";

    private int frequency;
    /** A transmitter: the strongest signal it reads. A receiver: what it gives out. */
    private int power;

    public WirelessRedstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_REDSTONE.get(), pos, state);
    }

    /** Which end this is, as the block says; a block entity without its block is read as a transmitter. */
    public Kind kind() {
        return getBlockState().getBlock() instanceof WirelessRedstoneBlock plate ? plate.kind : Kind.TRANSMITTER;
    }

    public int frequency() { return frequency; }

    public int power() { return power; }

    /**
     * Moves the plate to another frequency: off the old one and on to the new one in one go, so
     * both frequencies are told at the moment the plate leaves and the moment it arrives.
     */
    public void setFrequency(int wanted) {
        int frequency = Math.clamp(wanted, 0, MAX_FREQUENCY);
        if (frequency == this.frequency) return;
        boolean listed = level instanceof ServerLevel;
        if (listed) {
            WirelessRedstone.leave(this);
            // A transmitter's signal comes off the old frequency with it.
            if (kind() == Kind.TRANSMITTER) WirelessRedstone.forget(((ServerLevel) level).getServer(), globalPos());
        }
        this.frequency = frequency;
        setChanged();
        if (!listed) return;
        WirelessRedstone.join(this);
        // The number is written on the face of the plate, so the client has to be told it changed.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * A transmitter reads the signal on its front — the side its lamp is on — and puts that on the
     * frequency. Only that side: what is at its back or beside it is somebody else's wiring.
     */
    public void sample() {
        if (level == null || level.isClientSide() || kind() != Kind.TRANSMITTER) return;
        Direction front = WirelessRedstoneBlock.front(getBlockState());
        int read = level.getSignal(worldPosition.relative(front), front);
        if (read == power) return;
        power = read;
        setChanged();
        showLamp();
        if (level instanceof ServerLevel serverLevel) WirelessRedstone.send(serverLevel.getServer(), this);
    }

    /** Where the plate is, dimension and all: how the ether knows it. */
    public GlobalPos globalPos() {
        return GlobalPos.of(level == null ? Level.OVERWORLD : level.dimension(), worldPosition.immutable());
    }

    /** A receiver takes what is on its frequency and gives out the same; the blocks around it hear at once. */
    public void receive(int signal) {
        if (level == null || level.isClientSide() || kind() != Kind.RECEIVER || signal == power) return;
        power = signal;
        setChanged();
        showLamp();
        WirelessRedstoneBlock.updateNeighbours(level, getBlockState(), worldPosition);
    }

    /** The lamp on the plate follows the signal: it is in the state, so it is what the client sees. */
    private void showLamp() {
        BlockState state = getBlockState();
        if (level == null || !state.hasProperty(WirelessRedstoneBlock.LIT)) return;
        boolean lit = power > 0;
        if (state.getValue(WirelessRedstoneBlock.LIT) == lit) return;
        // Only the look changes here; a receiver nudges its neighbours itself, with the mounted block.
        level.setBlock(worldPosition, state.setValue(WirelessRedstoneBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** Signs the plate in on its frequency, and reads what is touching it if it is a transmitter. */
    public void connect() {
        if (isRemoved() || level == null || level.isClientSide()) return;
        WirelessRedstone.join(this);
        sample();
    }

    /**
     * Signs in on the frequency, but on the next tick: a plate that has to light its lamp or nudge
     * its neighbours cannot do it while the chunk around it is still loading.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().execute(this::connect);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) WirelessRedstone.leave(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) WirelessRedstone.leave(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        frequency = Math.clamp(input.getIntOr(FREQUENCY, 0), 0, MAX_FREQUENCY);
        // Kept so a receiver comes back giving out what it was giving out, rather than blinking
        // off for the tick it takes the frequency to answer.
        power = Math.clamp(input.getIntOr(POWER, 0), 0, 15);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (frequency != 0) output.putInt(FREQUENCY, frequency);
        if (power != 0) output.putInt(POWER, power);
    }

    /** The frequency reaches the client with the chunk, for the screen to open on. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        tag.putInt(FREQUENCY, frequency);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
