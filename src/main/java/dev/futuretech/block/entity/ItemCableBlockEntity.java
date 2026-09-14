package dev.futuretech.block.entity;

import dev.futuretech.block.CableKind;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.transfer.ConnectorItems;
import dev.futuretech.transfer.ItemCableNetwork;
import dev.futuretech.transfer.ItemFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Holds this cable's share of an {@link ItemCableNetwork}; the network itself moves the items. */
public final class ItemCableBlockEntity extends AbstractCableBlockEntity {
    /** Speed upgrades one connector takes. */
    public static final int MAX_UPGRADES = ItemCableNetwork.MAX_UPGRADES;
    private static final String FLIGHTS_TAG = "Flights";

    private final ItemCableTier tier;
    // Swapping or editing a card changes what a connector lets through, and the network reads the
    // card live, so a rebuild only matters when a card is put in or taken out.
    private final ConnectorItems filters = new ConnectorItems("Filters", ItemFilterItem::isFilter, 1, () -> {
        setChanged();
        invalidateNetwork();
    });
    /** Speed upgrades per connector; the network caches the count, so it is rebuilt on a change. */
    private final ConnectorItems upgrades = new ConnectorItems("Upgrades", UpgradeInventory::isUpgrade, MAX_UPGRADES, () -> {
        setChanged();
        invalidateNetwork();
    });
    private @Nullable ItemCableNetwork network;
    /**
     * Items inside this cable while it has no network: loaded from the save, or left here when the
     * network was torn down. The next network takes them over.
     */
    private final List<ItemFlight> parked = new ArrayList<>();

    public ItemCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof ItemCableBlock block ? block.tier() : ItemCableTier.OPAQUE;
    }

    public ItemCableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.ITEMS; }

    @Override
    public ConnectorItems connectorFilters() { return filters; }

    @Override
    public ConnectorItems connectorUpgrades() { return upgrades; }

    /** How many speed upgrades sit on {@code side}'s connector. */
    public int connectorSpeedUpgrades(Direction side) { return upgrades.on(side).getCount(); }

    /** The filter card on {@code side}'s connector, or an empty stack. */
    public ItemStack connectorFilter(Direction side) { return filters.on(side); }

    /** What {@code side}'s connector lets through, in either direction. */
    public Predicate<ItemResource> connectorAccepts(Direction side) {
        return ItemFilterItem.predicate(filters.on(side));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        Containers.dropContents(level, pos, filters);
        Containers.dropContents(level, pos, upgrades);
        // Whatever was passing through falls out with the cable.
        List<ItemFlight> inside = network != null && network.isValid() ? network.removeFlightsIn(pos) : List.copyOf(parked);
        parked.clear();
        for (ItemFlight flight : inside) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), flight.stack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        filters.load(input);
        upgrades.load(input);
        parked.clear();
        input.read(FLIGHTS_TAG, ItemFlight.LIST_CODEC).ifPresent(parked::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        filters.save(output);
        upgrades.save(output);
        List<ItemFlight> inside = flightsHere();
        if (!inside.isEmpty()) output.store(FLIGHTS_TAG, ItemFlight.LIST_CODEC, inside);
    }

    /** The current network, rebuilt on demand after cables were added or removed nearby. */
    public ItemCableNetwork network() {
        if (network == null || !network.isValid()) {
            network = ItemCableNetwork.discover((ServerLevel) level, worldPosition);
        }
        return network;
    }

    public void setNetwork(ItemCableNetwork network) { this.network = network; }

    public void park(ItemFlight flight) { parked.add(flight); }

    /** Hands the parked flights to the network that is taking this cable over. */
    public List<ItemFlight> takeParkedFlights() {
        List<ItemFlight> taken = List.copyOf(parked);
        parked.clear();
        return taken;
    }

    /** The flights inside this cable right now, wherever they are kept. */
    private List<ItemFlight> flightsHere() {
        return network != null && network.isValid() ? network.flightsIn(worldPosition) : parked;
    }

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
