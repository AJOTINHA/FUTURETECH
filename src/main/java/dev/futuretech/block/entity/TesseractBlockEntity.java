package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.TesseractBlock;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.transfer.TesseractChannelBook;
import dev.futuretech.transfer.TesseractChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/**
 * A tesseract: a block that hands whatever is pushed into it to the blocks around every other
 * tesseract on its channel, wherever they stand. Energy, items and fluids go through it the same
 * way — it keeps nothing, it has one always-empty slot on every face whose inserts are delivered
 * on the spot, so whatever a cable or a machine pushes in comes out beside a tesseract far away.
 * Nothing can be pulled out of it, and nothing it delivers ever goes into another tesseract, so a
 * channel cannot feed itself in a circle.
 *
 * <p>The teleport network crosses it too: a network cable touching a tesseract reaches the cables
 * touching the others on the channel, which is the cables' walk to work out, not this block's.
 */
public final class TesseractBlockEntity extends BlockEntity implements RedstoneControllable {
    /** What a tesseract carries, each with its own mode: sends, receives, both or neither. */
    public enum Kind {
        ITEMS("Items"), ENERGY("Energy"), FLUIDS("Fluids"), TELEPORT("Teleport");

        /** The tag the mode is saved under. */
        final String tag;

        Kind(String tag) { this.tag = tag; }

        public String translationKey() { return "gui.futuretech.tesseract.kind." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final int CHANNEL_LENGTH = 24;
    /** What a channel's neighbours are told a tesseract can hold: as much as they can give. */
    private static final long ROOM = Integer.MAX_VALUE;
    /**
     * Deep in a delivery, a tesseract takes nothing: a delivery lands beside a peer, and if the
     * block there hands it straight to a tesseract again the chain would never end.
     */
    private static boolean delivering;

    private String channel = "";
    private String name = "";
    private final RedstoneControl redstone = new RedstoneControl();
    /** Each kind's mode; nothing moves until the player opens a kind, on purpose. */
    private final EnumMap<Kind, SideMode> modes = new EnumMap<>(Kind.class);
    private final EnergyHandler energy = new EnergyHandler() {
        @Override
        public long getAmountAsLong() { return 0; }

        @Override
        public long getCapacityAsLong() { return sends(Kind.ENERGY) ? ROOM : 0; }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return deliver(Kind.ENERGY, (peer, share) -> peer.deliverEnergy(share, transaction), amount);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) { return 0; }
    };
    private final ResourceHandler<ItemResource> items = new Slot<>(Kind.ITEMS, Capabilities.Item.BLOCK, ItemResource.EMPTY, 1);
    private final ResourceHandler<FluidResource> fluids = new Slot<>(Kind.FLUIDS, Capabilities.Fluid.BLOCK, FluidResource.EMPTY, 2);
    /** The handlers around this tesseract, one cache per face and capability, made on first use. */
    private final BlockCapabilityCache<?, @Nullable Direction>[] caches = new BlockCapabilityCache[3 * Direction.values().length];

    public TesseractBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESSERACT.get(), pos, state);
        for (Kind kind : Kind.values()) modes.put(kind, SideMode.NONE);
    }

    public SideMode mode(Kind kind) { return modes.get(kind); }

    /** Sets a kind's mode; the client hears it for the screen. */
    public void setMode(Kind kind, SideMode mode) {
        if (modes.get(kind) == mode) return;
        modes.put(kind, mode);
        setChanged();
        sync();
    }

    /** Whether what is pushed into this tesseract goes out to the channel, for this kind. */
    public boolean sends(Kind kind) { return isRunning() && modes.get(kind).allowsOutput(); }

    /** Whether what the channel carries comes out beside this tesseract, for this kind. */
    public boolean receives(Kind kind) { return isRunning() && modes.get(kind).allowsInput(); }

    public String channel() { return channel; }

    /** The name the player gave it; empty until they do, and the screen then shows the block's own. */
    public String name() { return name; }

    public void setName(String name) {
        String trimmed = name.strip();
        if (trimmed.length() > CHANNEL_LENGTH) trimmed = trimmed.substring(0, CHANNEL_LENGTH);
        if (trimmed.equals(this.name)) return;
        this.name = trimmed;
        setChanged();
    }

    /**
     * Moves the tesseract to another channel; the client hears the new name, the old channel loses
     * it, and the cables around hear whether there is anything to link to now.
     */
    public void setChannel(String channel) {
        String trimmed = channel.strip();
        if (trimmed.length() > CHANNEL_LENGTH) trimmed = trimmed.substring(0, CHANNEL_LENGTH);
        if (trimmed.equals(this.channel)) return;
        boolean signedIn = level != null && !level.isClientSide();
        if (signedIn) TesseractChannels.leave(this);
        this.channel = trimmed;
        if (signedIn) TesseractChannels.join(this);
        setChanged();
        if (signedIn) {
            showChannel();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Puts whether there is a channel into the block state, for the cables to see, and drops the
     * handlers the neighbours were holding, so they ask again and get an answer that matches.
     */
    private void showChannel() {
        if (level == null) return;
        boolean on = !channel.isEmpty();
        BlockState state = getBlockState();
        if (state.getValue(TesseractBlock.CHANNEL) != on) {
            level.setBlock(worldPosition, state.setValue(TesseractBlock.CHANNEL, on), Block.UPDATE_ALL);
        }
        level.invalidateCapabilities(worldPosition);
    }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    /** The mode changed: saved, and told to the client for the screen. */
    @Override
    public void redstoneControlChanged() {
        setChanged();
        sync();
    }

    /** Tells the client what the screen shows: name, channel, mode and signal. */
    public void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the tesseract is on a channel and redstone lets it work: when not, it neither takes nor gives, and the network stops at it. */
    public boolean isRunning() { return !channel.isEmpty() && redstone.allowsRunning(); }

    public EnergyHandler energy() { return energy; }

    public ResourceHandler<ItemResource> items() { return items; }

    public ResourceHandler<FluidResource> fluids() { return fluids; }

    /** What a delivery does at one peer, given what is still to place; answers how much it placed there. */
    private interface Delivery {
        int to(TesseractBlockEntity peer, int amount);
    }

    /**
     * Hands {@code amount} around the peers in turn until it is all placed or every peer has been
     * asked. The first peer served rotates with the tick, so one busy peer cannot take everything.
     */
    private int deliver(Kind kind, Delivery delivery, int amount) {
        if (delivering || amount <= 0 || !sends(kind) || level == null) return 0;
        List<TesseractBlockEntity> peers = TesseractChannels.peers(this);
        if (peers.isEmpty()) return 0;
        delivering = true;
        try {
            int first = (int) Math.floorMod(level.getGameTime(), peers.size());
            int placed = 0;
            for (int step = 0; step < peers.size() && placed < amount; step++) {
                placed += peers.get((first + step) % peers.size()).take(kind, delivery, amount - placed);
            }
            return placed;
        } finally {
            delivering = false;
        }
    }

    /**
     * One peer's share of a delivery: what it managed to place of what is still to place; nothing
     * unless it receives the kind. The peer is offered only the remainder - offered the whole and
     * counted for the remainder, a second peer would place what the first already had, and the
     * channel would make items out of nothing.
     */
    private int take(Kind kind, Delivery delivery, int amount) {
        return receives(kind) ? Math.min(amount, delivery.to(this, amount)) : 0;
    }

    /** Puts energy into the blocks around this tesseract, as much as they take. */
    private int deliverEnergy(int amount, TransactionContext transaction) {
        int placed = 0;
        for (Direction side : Direction.values()) {
            if (placed >= amount) break;
            EnergyHandler handler = neighbour(Capabilities.Energy.BLOCK, 0, side);
            if (handler != null) placed += handler.insert(amount - placed, transaction);
        }
        return placed;
    }

    /** Puts a resource into the blocks around this tesseract, as much as they take. */
    private <T extends Resource> int deliverResource(BlockCapability<ResourceHandler<T>, @Nullable Direction> capability,
                                                     int slot, T resource, int amount, TransactionContext transaction) {
        int placed = 0;
        for (Direction side : Direction.values()) {
            if (placed >= amount) break;
            ResourceHandler<T> handler = neighbour(capability, slot, side);
            if (handler != null) placed += ResourceHandlerUtil.insertStacking(handler, resource, amount - placed, transaction);
        }
        return placed;
    }

    /**
     * The handler of the block beyond {@code side}, or null with none or with another tesseract
     * there: a tesseract never delivers into a tesseract. {@code slot} tells the three capabilities'
     * caches apart.
     */
    @SuppressWarnings("unchecked")
    private <C> @Nullable C neighbour(BlockCapability<C, @Nullable Direction> capability, int slot, Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        BlockPos pos = worldPosition.relative(side);
        if (!serverLevel.hasChunkAt(pos) || serverLevel.getBlockState(pos).getBlock() instanceof TesseractBlock) return null;
        int index = slot * Direction.values().length + side.ordinal();
        var cache = (BlockCapabilityCache<C, @Nullable Direction>) caches[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, serverLevel, pos, side.getOpposite());
            caches[index] = cache;
        }
        return cache.getCapability();
    }

    /**
     * The one always-empty slot a neighbour sees for items or fluids: what goes in is delivered
     * beside the peers on the spot, and nothing ever comes out.
     */
    private final class Slot<T extends Resource> implements ResourceHandler<T> {
        private final Kind kind;
        private final BlockCapability<ResourceHandler<T>, @Nullable Direction> capability;
        private final T empty;
        private final int cacheSlot;

        /** {@code cacheSlot} is this capability's row of neighbour caches: energy has row 0. */
        private Slot(Kind kind, BlockCapability<ResourceHandler<T>, @Nullable Direction> capability, T empty, int cacheSlot) {
            this.kind = kind;
            this.capability = capability;
            this.empty = empty;
            this.cacheSlot = cacheSlot;
        }

        @Override
        public int size() { return 1; }

        @Override
        public T getResource(int index) { return empty; }

        @Override
        public long getAmountAsLong(int index) { return 0; }

        @Override
        public long getCapacityAsLong(int index, T resource) { return sends(kind) ? ROOM : 0; }

        @Override
        public boolean isValid(int index, T resource) { return sends(kind); }

        @Override
        public int insert(int index, T resource, int amount, TransactionContext transaction) {
            if (index != 0 || resource.isEmpty()) return 0;
            return deliver(kind, (peer, share) -> peer.deliverResource(capability, cacheSlot, resource, share, transaction), amount);
        }

        @Override
        public int extract(int index, T resource, int amount, TransactionContext transaction) { return 0; }
    }

    /**
     * Signs in on the channel — unless the world no longer has it: a channel deleted while this
     * tesseract's chunk was unloaded took it off the channel too, and that is settled here.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!channel.isEmpty() && !TesseractChannelBook.of(serverLevel.getServer()).has(channel)) {
            channel = "";
            setChanged();
            // Not while the chunk is still loading: the state waits for the next tick.
            serverLevel.getServer().execute(() -> { if (!isRemoved()) showChannel(); });
        }
        TesseractChannels.join(this);
        // The signal is sampled here and on neighbour changes, not every tick.
        redstone.update(serverLevel, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) TesseractChannels.leave(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) TesseractChannels.leave(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        channel = input.getStringOr("Channel", "");
        name = input.getStringOr("Name", "");
        redstone.load(input);
        redstone.setPowered(input.getBooleanOr("Powered", false));
        for (Kind kind : Kind.values()) modes.put(kind, input.read("Mode" + kind.tag, SideMode.CODEC).orElse(SideMode.NONE));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!channel.isEmpty()) output.putString("Channel", channel);
        if (!name.isEmpty()) output.putString("Name", name);
        redstone.save(output);
        for (Kind kind : Kind.values()) output.store("Mode" + kind.tag, SideMode.CODEC, modes.get(kind));
    }

    /** What the screen shows reaches the client with the chunk and with every change. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        if (!channel.isEmpty()) tag.putString("Channel", channel);
        if (!name.isEmpty()) tag.putString("Name", name);
        tag.putString("RedstoneMode", redstone.mode().getSerializedName());
        tag.putBoolean("Powered", redstone.isPowered());
        for (Kind kind : Kind.values()) tag.putString("Mode" + kind.tag, modes.get(kind).getSerializedName());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
