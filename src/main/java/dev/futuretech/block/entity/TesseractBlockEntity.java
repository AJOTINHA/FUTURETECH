package dev.futuretech.block.entity;

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
public final class TesseractBlockEntity extends BlockEntity {
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
    private final EnergyHandler energy = new EnergyHandler() {
        @Override
        public long getAmountAsLong() { return 0; }

        @Override
        public long getCapacityAsLong() { return channel.isEmpty() ? 0 : ROOM; }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return deliver(peer -> peer.deliverEnergy(amount, transaction), amount);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) { return 0; }
    };
    private final ResourceHandler<ItemResource> items = new Slot<>(Capabilities.Item.BLOCK, ItemResource.EMPTY, 1);
    private final ResourceHandler<FluidResource> fluids = new Slot<>(Capabilities.Fluid.BLOCK, FluidResource.EMPTY, 2);
    /** The handlers around this tesseract, one cache per face and capability, made on first use. */
    private final BlockCapabilityCache<?, @Nullable Direction>[] caches = new BlockCapabilityCache[3 * Direction.values().length];

    public TesseractBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESSERACT.get(), pos, state);
    }

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

    /** Moves the tesseract to another channel; the client hears the new name, the old channel loses it. */
    public void setChannel(String channel) {
        String trimmed = channel.strip();
        if (trimmed.length() > CHANNEL_LENGTH) trimmed = trimmed.substring(0, CHANNEL_LENGTH);
        if (trimmed.equals(this.channel)) return;
        boolean signedIn = level != null && !level.isClientSide();
        if (signedIn) TesseractChannels.leave(this);
        this.channel = trimmed;
        if (signedIn) TesseractChannels.join(this);
        setChanged();
        if (signedIn) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public EnergyHandler energy() { return energy; }

    public ResourceHandler<ItemResource> items() { return items; }

    public ResourceHandler<FluidResource> fluids() { return fluids; }

    /** What a delivery does at one peer; answers how much it placed there. */
    private interface Delivery {
        int to(TesseractBlockEntity peer);
    }

    /**
     * Hands {@code amount} around the peers in turn until it is all placed or every peer has been
     * asked. The first peer served rotates with the tick, so one busy peer cannot take everything.
     */
    private int deliver(Delivery delivery, int amount) {
        if (delivering || amount <= 0 || channel.isEmpty() || level == null) return 0;
        List<TesseractBlockEntity> peers = TesseractChannels.peers(this);
        if (peers.isEmpty()) return 0;
        delivering = true;
        try {
            int first = (int) Math.floorMod(level.getGameTime(), peers.size());
            int placed = 0;
            for (int step = 0; step < peers.size() && placed < amount; step++) {
                placed += peers.get((first + step) % peers.size()).take(delivery, amount - placed);
            }
            return placed;
        } finally {
            delivering = false;
        }
    }

    /** One peer's share of a delivery: what it managed to place, never more than {@code amount}. */
    private int take(Delivery delivery, int amount) {
        return Math.min(amount, delivery.to(this));
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
        private final BlockCapability<ResourceHandler<T>, @Nullable Direction> capability;
        private final T empty;
        private final int cacheSlot;

        /** {@code cacheSlot} is this capability's row of neighbour caches: energy has row 0. */
        private Slot(BlockCapability<ResourceHandler<T>, @Nullable Direction> capability, T empty, int cacheSlot) {
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
        public long getCapacityAsLong(int index, T resource) { return channel.isEmpty() ? 0 : ROOM; }

        @Override
        public boolean isValid(int index, T resource) { return !channel.isEmpty(); }

        @Override
        public int insert(int index, T resource, int amount, TransactionContext transaction) {
            if (index != 0 || resource.isEmpty()) return 0;
            return deliver(peer -> peer.deliverResource(capability, cacheSlot, resource, amount, transaction), amount);
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
        }
        TesseractChannels.join(this);
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!channel.isEmpty()) output.putString("Channel", channel);
        if (!name.isEmpty()) output.putString("Name", name);
    }

    /** The channel reaches the client with the chunk and with every change, for the screen to show. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        if (!channel.isEmpty()) tag.putString("Channel", channel);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
