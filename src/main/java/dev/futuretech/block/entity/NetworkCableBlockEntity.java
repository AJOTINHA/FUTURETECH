package dev.futuretech.block.entity;

import dev.futuretech.block.CableKind;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries what every cable carries — the cut links and the panels on its faces — and no network
 * behind them. The block never asks for a ticker, so {@link #serverTick()} is never called; when
 * the network cable is given something to move, both methods are where it goes.
 */
public final class NetworkCableBlockEntity extends AbstractCableBlockEntity {
    public NetworkCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_CABLE.get(), pos, state, CableKind.NETWORK);
    }

    @Override
    public CableKind kind() { return CableKind.NETWORK; }

    @Override
    public void invalidateNetwork() {}

    @Override
    public void serverTick() {}
}
