package dev.futuretech.block.entity;

import com.mojang.serialization.Codec;
import dev.futuretech.block.CableKind;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.transfer.ConnectorFilters;
import dev.futuretech.transfer.ItemCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/** Holds this cable's share of an {@link ItemCableNetwork}; the network itself moves the items. */
public final class ItemCableBlockEntity extends AbstractCableBlockEntity {
    public static final int MIN_PRIORITY = -100;
    public static final int MAX_PRIORITY = 100;
    public static final int MAX_CHANNEL = 100;
    private static final String PRIORITIES_TAG = "Priorities";
    private static final Codec<Map<Direction, Integer>> PRIORITIES_CODEC =
            Codec.unboundedMap(Direction.CODEC, Codec.INT);
    private static final String CHANNELS_TAG = "Channels";
    private static final Codec<Map<Direction, Integer>> CHANNELS_CODEC =
            Codec.unboundedMap(Direction.CODEC, Codec.INT);
    private static final String COLORS_TAG = "Colors";
    private static final Codec<Map<Direction, DyeColor>> COLORS_CODEC =
            Codec.unboundedMap(Direction.CODEC, DyeColor.CODEC);

    private final ItemCableTier tier;
    /** Only faces the player moved off 0 are kept, so an untouched cable saves nothing extra. */
    private final EnumMap<Direction, Integer> priorities = new EnumMap<>(Direction.class);
    /** Only faces moved off white are kept; a face without an entry is on the white channel. */
    private final EnumMap<Direction, DyeColor> colors = new EnumMap<>(Direction.class);
    /** Only faces moved off 0 are kept. */
    private final EnumMap<Direction, Integer> channels = new EnumMap<>(Direction.class);
    // Swapping or editing a card changes what a connector lets through, and the network reads the
    // card live, so a rebuild only matters when a card is put in or taken out.
    private final ConnectorFilters filters = new ConnectorFilters(() -> {
        setChanged();
        invalidateNetwork();
    });
    private @Nullable ItemCableNetwork network;

    public ItemCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof ItemCableBlock block ? block.tier() : ItemCableTier.MK1;
    }

    public ItemCableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.ITEMS; }

    @Override
    public int connectorPriority(Direction side) { return priorities.getOrDefault(side, 0); }

    /** Clamped to the allowed range; the network caches priorities, so it is rebuilt on a change. */
    @Override
    public void setConnectorPriority(Direction side, int priority) {
        int clamped = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
        if (clamped == connectorPriority(side)) return;
        if (clamped == 0) priorities.remove(side);
        else priorities.put(side, clamped);
        setChanged();
        invalidateNetwork();
    }

    @Override
    public DyeColor connectorColor(Direction side) { return colors.getOrDefault(side, DyeColor.WHITE); }

    /** The network caches channels, so it is rebuilt on a change. */
    @Override
    public void setConnectorColor(Direction side, DyeColor color) {
        if (color == connectorColor(side)) return;
        if (color == DyeColor.WHITE) colors.remove(side);
        else colors.put(side, color);
        setChanged();
        invalidateNetwork();
    }

    @Override
    public int connectorChannel(Direction side) { return channels.getOrDefault(side, 0); }

    /** Clamped to 0..{@value #MAX_CHANNEL}; the network caches channels, so it is rebuilt on a change. */
    @Override
    public void setConnectorChannel(Direction side, int channel) {
        int clamped = Math.clamp(channel, 0, MAX_CHANNEL);
        if (clamped == connectorChannel(side)) return;
        if (clamped == 0) channels.remove(side);
        else channels.put(side, clamped);
        setChanged();
        invalidateNetwork();
    }

    @Override
    public ConnectorFilters connectorFilters() { return filters; }

    /** The filter card on {@code side}'s connector, or an empty stack. */
    public ItemStack connectorFilter(Direction side) { return filters.on(side); }

    /** What {@code side}'s connector lets through, in either direction. */
    public Predicate<ItemResource> connectorAccepts(Direction side) {
        return ItemFilterItem.predicate(filters.on(side));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, filters);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        filters.load(input);
        colors.clear();
        input.read(COLORS_TAG, COLORS_CODEC).ifPresent(colors::putAll);
        channels.clear();
        input.read(CHANNELS_TAG, CHANNELS_CODEC).ifPresent(saved -> saved.forEach((side, channel) -> {
            int clamped = Math.clamp(channel, 0, MAX_CHANNEL);
            if (clamped != 0) channels.put(side, clamped);
        }));
        priorities.clear();
        input.read(PRIORITIES_TAG, PRIORITIES_CODEC).ifPresent(saved -> saved.forEach((side, priority) -> {
            int clamped = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
            if (clamped != 0) priorities.put(side, clamped);
        }));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        filters.save(output);
        if (!colors.isEmpty()) output.store(COLORS_TAG, COLORS_CODEC, Map.copyOf(colors));
        if (!channels.isEmpty()) output.store(CHANNELS_TAG, CHANNELS_CODEC, Map.copyOf(channels));
        if (!priorities.isEmpty()) output.store(PRIORITIES_TAG, PRIORITIES_CODEC, Map.copyOf(priorities));
    }

    /** The current network, rebuilt on demand after cables were added or removed nearby. */
    public ItemCableNetwork network() {
        if (network == null || !network.isValid()) {
            network = ItemCableNetwork.discover((ServerLevel) level, worldPosition);
        }
        return network;
    }

    public void setNetwork(ItemCableNetwork network) { this.network = network; }

    public void clearNetwork(ItemCableNetwork stale) {
        if (network == stale) network = null;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null && level instanceof ServerLevel serverLevel) network.invalidate(serverLevel);
        network = null;
    }

    /** Item handler seen by the neighbour beyond {@code side}; resolves the network on every call. */
    public @Nullable ResourceHandler<ItemResource> handler(@Nullable Direction side) {
        if (!(level instanceof ServerLevel)) return null;
        // A connector the player closed to incoming items refuses what the neighbour pushes.
        boolean accepts = connectors().allowsItemInput(side);
        return new ResourceHandler<>() {
            private ResourceHandler<ItemResource> current() {
                return side == null ? network().handlerFor(null)
                        : network().handlerFor(new ItemCableNetwork.EndpointKey(worldPosition, side));
            }

            @Override
            public int size() { return current().size(); }

            @Override
            public ItemResource getResource(int index) { return current().getResource(index); }

            @Override
            public long getAmountAsLong(int index) { return current().getAmountAsLong(index); }

            @Override
            public long getCapacityAsLong(int index, ItemResource resource) {
                return current().getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, ItemResource resource) { return current().isValid(index, resource); }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return accepts ? current().insert(index, resource, amount, transaction) : 0;
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return current().extract(index, resource, amount, transaction);
            }
        };
    }

    @Override
    public void serverTick() {
        if (level != null) network().tick(level.getGameTime());
    }
}
