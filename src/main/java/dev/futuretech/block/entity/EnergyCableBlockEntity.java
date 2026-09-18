package dev.futuretech.block.entity;

import dev.futuretech.block.EnergyCableBlock;
import dev.futuretech.block.CableKind;
import dev.futuretech.block.EnergyCableTier;
import dev.futuretech.energy.EnergyCableNetwork;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Holds this cable's share of a {@link EnergyCableNetwork}; the network itself carries the energy. */
public final class EnergyCableBlockEntity extends AbstractCableBlockEntity {
    private final EnergyCableTier tier;
    private @Nullable EnergyCableNetwork network;

    public EnergyCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state, CableKind.ENERGY);
        this.tier = state.getBlock() instanceof EnergyCableBlock block ? block.tier() : EnergyCableTier.MK1;
    }

    public EnergyCableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.ENERGY; }

    /** The current network, rebuilt on demand after cables were added or removed nearby. */
    public EnergyCableNetwork network() {
        if (network == null || !network.isValid()) {
            network = EnergyCableNetwork.discover((ServerLevel) level, worldPosition);
        }
        return network;
    }

    public void setNetwork(EnergyCableNetwork network) { this.network = network; }

    public void clearNetwork(EnergyCableNetwork stale) {
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
        // A connector the player closed to incoming energy refuses what the neighbour pushes,
        // and so does one redstone has switched off; that one is asked as it goes, so it needs no
        // new handler when the signal changes.
        boolean open = connectors().allowsEnergyInput(side);
        return new EnergyHandler() {
            private EnergyHandler current() {
                return side == null ? network().handlerFor(null)
                        : network().handlerFor(new EnergyCableNetwork.EndpointKey(worldPosition, side));
            }

            @Override
            public long getAmountAsLong() { return current().getAmountAsLong(); }

            /** A face closed to input has no room, so pushers can tell without opening a transaction. */
            @Override
            public long getCapacityAsLong() { return (open && connectorActive(side)) ? current().getCapacityAsLong() : 0; }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return (open && connectorActive(side)) ? current().insert(amount, transaction) : 0;
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
