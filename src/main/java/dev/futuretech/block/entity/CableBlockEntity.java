package dev.futuretech.block.entity;

import dev.futuretech.block.CableBlock;
import dev.futuretech.block.CableKind;
import dev.futuretech.block.CableTier;
import dev.futuretech.energy.CableNetwork;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Holds this cable's share of a {@link CableNetwork}; the network itself carries the energy. */
public final class CableBlockEntity extends AbstractCableBlockEntity {
    private final CableTier tier;
    private @Nullable CableNetwork network;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof CableBlock block ? block.tier() : CableTier.MK1;
    }

    public CableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.ENERGY; }

    /** The current network, rebuilt on demand after cables were added or removed nearby. */
    public CableNetwork network() {
        if (network == null || !network.isValid()) {
            network = CableNetwork.discover((ServerLevel) level, worldPosition);
        }
        return network;
    }

    public void setNetwork(CableNetwork network) { this.network = network; }

    public void clearNetwork(CableNetwork stale) {
        if (network == stale) network = null;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null && level instanceof ServerLevel serverLevel) network.invalidate(serverLevel);
        network = null;
    }

    /** Energy handler seen by the neighbour beyond {@code side}; resolves the network on every call. */
    public @Nullable EnergyHandler handler(@Nullable Direction side) {
        if (!(level instanceof ServerLevel)) return null;
        // A connector the player closed to incoming energy refuses what the neighbour pushes.
        boolean accepts = connectors().allowsEnergyInput(side);
        return new EnergyHandler() {
            private EnergyHandler current() {
                return side == null ? network().handlerFor(null)
                        : network().handlerFor(new CableNetwork.EndpointKey(worldPosition, side));
            }

            @Override
            public long getAmountAsLong() { return current().getAmountAsLong(); }

            @Override
            public long getCapacityAsLong() { return current().getCapacityAsLong(); }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return accepts ? current().insert(amount, transaction) : 0;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return current().extract(amount, transaction);
            }
        };
    }

    @Override
    public void serverTick() {
        if (level != null) network().tick(level.getGameTime());
    }
}
