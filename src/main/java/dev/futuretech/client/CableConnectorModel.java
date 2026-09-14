package dev.futuretech.client;

import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
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

/**
 * Uses only the chunk's block-state snapshot, so existing cables also gain connectors on reload.
 * Which collar is drawn depends on the connector's mode, which rides in as model data: the band on
 * its rim tells the player what that connector does without opening it.
 */
public final class CableConnectorModel extends DelegateBlockStateModel {
    /** What to draw before the block entity's data arrives: the mode a fresh connector starts on. */
    private static final int DEFAULT_MODES=SideConfigVisuals.faceModes(SideMode.BOTH);

    private final Map<Direction,Map<SideMode,BlockStateModelPart>> connectors;
    private final int connectorFlags;

    public CableConnectorModel(BlockStateModel delegate, Map<Direction,Map<SideMode,BlockStateModelPart>> connectors) {
        super(delegate);
        this.connectors=Map.copyOf(connectors);
        int flags=0;
        for (var modes : connectors.values()) {
            for (var part : modes.values()) flags |= part.materialFlags();
        }
        connectorFlags=flags;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> output) {
        delegate.collectParts(level,pos,state,random,output);
        int mask=CableConnector.mask(level,pos,state);
        Integer stored=level.getModelData(pos).get(SideConfigVisuals.FACE_MODES);
        int modes=stored==null ? DEFAULT_MODES : stored;
        for (var entry : connectors.entrySet()) {
            if ((mask & (1 << entry.getKey().ordinal())) == 0) continue;
            var part=entry.getValue().get(SideConfigVisuals.mode(modes,entry.getKey()));
            if (part != null) output.add(part);
        }
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return delegate.materialFlags(level,pos,state) | connectorFlags;
    }
}
