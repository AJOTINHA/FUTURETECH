package dev.futuretech.block.entity;

import dev.futuretech.block.CableBlock;
import dev.futuretech.block.CableTier;
import dev.futuretech.energy.CableNetwork;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Holds this cable's share of a {@link CableNetwork}; the network itself carries the energy. */
public final class CableBlockEntity extends BlockEntity {
    private final CableTier tier;
    private @Nullable CableNetwork network;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof CableBlock block ? block.tier() : CableTier.MK1;
    }

    public CableTier tier() { return tier; }

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

    /** Invalidates the network this cable belongs to, if it has one; the next tick rebuilds it. */
    public void invalidateNetwork() {
        if (network != null && level instanceof ServerLevel serverLevel) network.invalidate(serverLevel);
        network = null;
    }

    /** Energy handler seen by the neighbour beyond {@code side}; resolves the network on every call. */
    public @Nullable EnergyHandler handler(@Nullable Direction side) {
        if (!(level instanceof ServerLevel)) return null;
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
                return current().insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return current().extract(amount, transaction);
            }
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CableBlockEntity cable) {
        cable.network().tick(level.getGameTime());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
