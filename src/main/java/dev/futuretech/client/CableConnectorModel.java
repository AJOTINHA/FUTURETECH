package dev.futuretech.client;

import dev.futuretech.api.facade.CableFacades;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.CableConnector;
import dev.futuretech.block.CableKind;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Uses only the chunk's block-state snapshot, so existing cables also gain connectors on reload.
 * Which collar is drawn depends on the connector's mode, which rides in as model data: the band on
 * its rim tells the player what that connector does without opening it.
 */
public final class CableConnectorModel extends DelegateBlockStateModel {
    /** What to draw before the block entity's data arrives: the mode a fresh connector of this kind starts on. */
    private final int defaultModes;

    private final Map<Direction,Map<SideMode,BlockStateModelPart>> connectors;
    private final int connectorFlags;

    public CableConnectorModel(BlockStateModel delegate, CableKind kind, Map<Direction,Map<SideMode,BlockStateModelPart>> connectors) {
        super(delegate);
        this.defaultModes=SideConfigVisuals.faceModes(kind.freshConnector());
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
        int modes=stored==null ? defaultModes : stored;
        for (var entry : connectors.entrySet()) {
            if ((mask & (1 << entry.getKey().ordinal())) == 0) continue;
            var part=entry.getValue().get(SideConfigVisuals.mode(modes,entry.getKey()));
            if (part != null) output.add(part);
        }
        // The panels come last so the collars stay on top of them, which is what tells the player
        // a hidden cable still has a connector on that face.
        for (var facade : facades(level,pos).entrySet()) {
            var panel=FacadeModelPart.of(facade.getValue(),facade.getKey());
            if (panel != null) output.add(panel);
        }
    }

    /**
     * The blocks covering this cable's faces; empty until the block entity's data arrives, and
     * empty for as long as the player holds the wrench and sneaks, which is the see-through mode.
     */
    private static Map<Direction,BlockState> facades(BlockAndTintGetter level, BlockPos pos) {
        var stored=level.getModelData(pos).get(CableFacades.FACADES);
        if (stored == null || stored.isEmpty()) return Map.of();
        // Noted even while hidden: this is the cable that has to be drawn again when the mode flips.
        FacadeVisibility.note(pos);
        return FacadeVisibility.hidden() ? Map.of() : stored;
    }

    /**
     * What this model's geometry depends on: the delegate's own key, which collars are drawn, and
     * their modes. {@link DelegateBlockStateModel} does not forward this one, and its default answer
     * of {@code null} tells the chunk mesher the geometry cannot be cached at all: every cable in a
     * section would rebuild its quads from scratch on every rebuild of that section, which is every
     * time any block in it is placed or broken.
     */
    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              RandomSource random) {
        Object delegateKey=delegate.createGeometryKey(level,pos,state,random);
        if (delegateKey == null) return null;
        Integer stored=level.getModelData(pos).get(SideConfigVisuals.FACE_MODES);
        return new GeometryKey(this,delegateKey,CableConnector.mask(level,pos,state),
                stored==null ? defaultModes : stored,facades(level,pos));
    }

    /**
     * The wrapper's identity separates the kinds, which draw different contacts on the same collars.
     * The facades ride along by value: two cables wearing the same blocks on the same faces share
     * their geometry, and one that is uncovered stops matching the covered one at once.
     */
    private record GeometryKey(CableConnectorModel model, Object delegate, int mask, int modes,
                               Map<Direction,BlockState> facades) {}

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int flags=delegate.materialFlags(level,pos,state) | connectorFlags;
        // A panel may be cut out or animated where the cable is not, and the mesher has to know.
        for (var facade : facades(level,pos).entrySet()) {
            var panel=FacadeModelPart.of(facade.getValue(),facade.getKey());
            if (panel != null) flags |= panel.materialFlags();
        }
        return flags;
    }
}
