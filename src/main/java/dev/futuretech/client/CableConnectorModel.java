package dev.futuretech.client;

import dev.futuretech.block.CableConnector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

import java.util.List;
import java.util.Map;

/** Uses only the chunk's block-state snapshot, so existing cables also gain connectors on reload. */
public final class CableConnectorModel extends DelegateBlockStateModel {
    private final Map<Direction,BlockStateModelPart> connectors;
    private final int connectorFlags;

    public CableConnectorModel(BlockStateModel delegate, Map<Direction,BlockStateModelPart> connectors) {
        super(delegate);
        this.connectors=Map.copyOf(connectors);
        int flags=0;
        for (var part : connectors.values()) flags |= part.materialFlags();
        connectorFlags=flags;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> output) {
        delegate.collectParts(level,pos,state,random,output);
        int mask=CableConnector.mask(level,pos,state);
        for (var entry : connectors.entrySet()) {
            if ((mask & (1 << entry.getKey().ordinal())) != 0) output.add(entry.getValue());
        }
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return delegate.materialFlags(level,pos,state) | connectorFlags;
    }
}
