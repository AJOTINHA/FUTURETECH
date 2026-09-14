package dev.futuretech.block.entity;

import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.CableKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;

import java.util.Set;

/**
 * The part of a cable that does not care what it carries: the six connector modes, how they reach
 * the client and how the player changes them. Each kind of cable adds its own network on top.
 */
public abstract class AbstractCableBlockEntity extends BlockEntity {
    /** A connector may insert, extract, do both, or nothing at all. */
    private static final Set<SideMode> ALLOWED_MODES =
            Set.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH);

    /**
     * What each connector does. A face starts on {@link SideMode#BOTH}, which is how cables behaved
     * before they were configurable: the resource crosses in either direction. Turning a direction
     * off is the player narrowing that connector, so existing builds keep working untouched.
     */
    private final SideConfig connectors = new SideConfig(ALLOWED_MODES, true, side -> SideMode.BOTH);

    protected AbstractCableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract CableKind kind();

    /** Invalidates the network this cable belongs to, if it has one; the next tick rebuilds it. */
    public abstract void invalidateNetwork();

    /** One server tick of this cable's network. */
    public abstract void serverTick();

    public SideConfig connectors() { return connectors; }

    /**
     * Where this connector stands when the network chooses whom to serve first; higher goes first.
     * Kinds whose network does not order its connectors answer 0 and ignore changes.
     */
    public int connectorPriority(Direction side) { return 0; }

    public void setConnectorPriority(Direction side, int priority) {}

    /**
     * The filter cards of the six connectors, slot {@code side.ordinal()} for each side. Kinds
     * without filters answer an empty container that never shows in a menu.
     */
    public Container connectorFilters() { return new SimpleContainer(Direction.values().length); }

    /** The colour channel of a connector; white until changed. Kinds without colours stay white and ignore changes. */
    public DyeColor connectorColor(Direction side) { return DyeColor.WHITE; }

    public void setConnectorColor(Direction side, DyeColor color) {}

    /** The numbered channel of a connector, 0 until changed; with the colour it says who talks to whom. */
    public int connectorChannel(Direction side) { return 0; }

    public void setConnectorChannel(Direction side, int channel) {}

    /** Applies a connector's new mode and rebuilds the network, which caches what each face allows. */
    public void setConnectorMode(Direction side, SideMode mode) {
        if (connectors.mode(side) == mode) return;
        connectors.set(side, mode);
        setChanged();
        invalidateNetwork();
        SideConfigVisuals.refresh(this);
    }

    // The connector modes are the cable's only render data, and they drive the coloured band on
    // each collar, so they have to reach the client like the machines' face modes do.
    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(connectors); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return SideConfigVisuals.updateTag(connectors);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        int previous = SideConfigVisuals.faceModes(connectors);
        connectors.load(input);
        if (previous != SideConfigVisuals.faceModes(connectors)) SideConfigVisuals.refresh(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        connectors.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        connectors.save(output);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
