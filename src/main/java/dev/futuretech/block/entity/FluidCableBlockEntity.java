package dev.futuretech.block.entity;

import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.block.CableKind;
import dev.futuretech.block.FluidCableBlock;
import dev.futuretech.block.FluidCableTier;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.transfer.FluidCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Holds this cable's share of a {@link FluidCableNetwork}; the network itself carries the fluid.
 * The cable also remembers what fluid the network is showing, which reaches the client with the
 * connector modes so a see-through cable can draw it.
 */
public final class FluidCableBlockEntity extends AbstractCableBlockEntity {
    private static final String SHOWN_TAG = "Shown";
    private static final String FLOW_TAG = "Flow";

    private final FluidCableTier tier;
    private @Nullable FluidCableNetwork network;
    /** What the see-through cable draws inside; empty when nothing has flowed lately. Not saved. */
    private FluidStack shown = FluidStack.EMPTY;
    /** Which way it streams through this cable, if the cable is on a route. Not saved. */
    private @Nullable Direction flow;
    /** Client only: the fluid being drawn (kept while it drains away) and how full the cable looks. */
    private FluidStack drawn = FluidStack.EMPTY;
    private float fill;
    private double fillTime;
    /** How much of the cable fills per tick while fluid flows, and empties per tick once it stops. */
    private static final float RISE = 0.1F;
    private static final float FALL = 0.025F;

    public FluidCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof FluidCableBlock block ? block.tier() : FluidCableTier.OPAQUE;
    }

    public FluidCableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.FLUID; }

    /** The fluid drawn inside this cable, on either side. */
    public FluidStack shown() { return shown; }

    /** The way the fluid streams through this cable, or {@code null} to draw it standing. */
    public @Nullable Direction flow() { return flow; }

    /**
     * Client: the fluid to draw right now. While the network shows one it is that; once the
     * network stops, the last one stays while its level drains to nothing.
     */
    public FluidStack drawnFluid() { return shown.isEmpty() ? drawn : shown; }

    /**
     * Client: how full the cable looks at {@code now}, from 0 to 1. Rises quickly while fluid is
     * shown and sinks slowly after, so the fluid drains out instead of vanishing.
     */
    public float drawnLevel(double now) {
        if (!shown.isEmpty()) drawn = shown;
        float elapsed = (float) Math.max(0, now - fillTime);
        fillTime = now;
        fill = shown.isEmpty() ? Math.max(0, fill - elapsed * FALL) : Math.min(1, fill + elapsed * RISE);
        if (fill <= 0) drawn = FluidStack.EMPTY;
        return fill;
    }

    /** Server: the network changed what it carries or which way; the clients get told with the next update tag. */
    public void show(FluidStack fluid, @Nullable Direction direction) {
        boolean sameFluid = FluidStack.isSameFluidSameComponents(shown, fluid) && shown.isEmpty() == fluid.isEmpty();
        if (sameFluid && flow == direction) return;
        shown = fluid.copy();
        flow = direction;
        SideConfigVisuals.refresh(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!shown.isEmpty()) tag.store(SHOWN_TAG, FluidStack.CODEC, shown);
        if (flow != null) tag.store(FLOW_TAG, Direction.CODEC, flow);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        shown = input.read(SHOWN_TAG, FluidStack.CODEC).orElse(FluidStack.EMPTY);
        flow = input.read(FLOW_TAG, Direction.CODEC).orElse(null);
    }

    /** The current network, rebuilt on demand after cables were added or removed nearby. */
    public FluidCableNetwork network() {
        if (network == null || !network.isValid()) {
            network = FluidCableNetwork.discover((ServerLevel) level, worldPosition);
        }
        return network;
    }

    public void setNetwork(FluidCableNetwork network) { this.network = network; }

    public void clearNetwork(FluidCableNetwork stale) {
        if (network == stale) network = null;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null && level instanceof ServerLevel serverLevel) network.invalidate(serverLevel);
        network = null;
    }

    /** Fluid handler seen by the neighbour beyond {@code side}; resolves the network on every call. */
    public @Nullable ResourceHandler<FluidResource> handler(@Nullable Direction side) {
        if (!(level instanceof ServerLevel)) return null;
        // A connector the player closed to incoming fluid refuses what the neighbour pushes.
        boolean accepts = connectors().allowsItemInput(side);
        return new ResourceHandler<>() {
            private ResourceHandler<FluidResource> current() {
                return side == null ? network().handlerFor(null)
                        : network().handlerFor(new FluidCableNetwork.EndpointKey(worldPosition, side));
            }

            @Override
            public int size() { return 1; }

            @Override
            public FluidResource getResource(int index) { return current().getResource(index); }

            @Override
            public long getAmountAsLong(int index) { return current().getAmountAsLong(index); }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return current().getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) { return current().isValid(index, resource); }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return accepts ? current().insert(index, resource, amount, transaction) : 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    @Override
    public void serverTick() {
        if (level != null) network().tick(level.getGameTime());
    }
}
