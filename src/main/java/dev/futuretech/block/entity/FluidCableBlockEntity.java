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
    /** Client only: the fluid being drawn, kept while its tail runs out after the network stops. */
    private FluidStack drawn = FluidStack.EMPTY;
    /** Client: when the network last started or stopped showing fluid here; the wave leaves the entry then. */
    private long waveTime = Long.MIN_VALUE / 2;
    /** Client: cables between this one and the entry the fluid comes in by; -1 until traced. */
    private int hops = -1;
    /** Client: the way the fluid comes into this cable, found while tracing; null when unknown. */
    private @Nullable Direction travel;
    /** Client: whether the first picture from the server has arrived; that one is not animated. */
    private boolean synced;
    /** Ticks the front takes to cross one cable; the wave runs the whole line at this pace. */
    public static final int TICKS_PER_CABLE = 10;
    /** Longest line the trace follows back; past this the cable simply starts with its neighbour. */
    private static final int MAX_TRACE = 256;

    public FluidCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_CABLE.get(), pos, state, CableKind.FLUID);
        this.tier = state.getBlock() instanceof FluidCableBlock block ? block.tier() : FluidCableTier.OPAQUE;
    }

    public FluidCableTier tier() { return tier; }

    @Override
    public CableKind kind() { return CableKind.FLUID; }

    /** The fluid drawn inside this cable, on either side. */
    public FluidStack shown() { return shown; }

    /** The way the fluid streams through this cable, or {@code null} to draw it standing. */
    public @Nullable Direction flow() { return flow; }

    /** Client: the fluid to draw right now; the last one shown stays until its tail has run out. */
    public FluidStack drawnFluid() { return drawn; }

    /**
     * Where the fluid's front stands in this cable: {@code distance} from 0 to 1 along the way the
     * fluid travels, measured from the face it comes in by. While {@code filling} the fluid
     * occupies everything up to the front; afterwards the front is the tail and the fluid is what
     * lies beyond it. A null {@code travel} means the way in is unknown and the fluid rises.
     */
    public record Front(@Nullable Direction travel, float distance, boolean filling) {}

    /**
     * Client: the front to draw at {@code now}, or null while there is nothing to draw. The wave
     * starts at the cable the fluid enters the line by and crosses one cable per
     * {@value #TICKS_PER_CABLE} ticks, so a line fills, and later empties, from its entry outward
     * instead of every cable filling on its own at once.
     */
    public @Nullable Front front(double now) {
        if (!shown.isEmpty()) drawn = shown;
        if (drawn.isEmpty()) return null;
        if (hops < 0) trace();
        double start = waveTime + (double) hops * TICKS_PER_CABLE;
        float distance = (float) Math.clamp((now - start) / TICKS_PER_CABLE, 0, 1);
        boolean filling = !shown.isEmpty();
        if (filling ? distance <= 0 : distance >= 1) {
            if (!filling) drawn = FluidStack.EMPTY;
            return null;
        }
        return new Front(travel, distance, filling);
    }

    /**
     * Walks the line back to the cable the fluid enters by, counting cables and noting which way
     * the fluid comes into this one. Traced once per change: the neighbours' routes are all in by
     * the time anything is drawn.
     */
    private void trace() {
        hops = 0;
        travel = flow;
        if (level == null) return;
        var seen = new java.util.HashSet<BlockPos>();
        seen.add(worldPosition);
        BlockPos at = worldPosition;
        Direction route = flow;
        while (hops < MAX_TRACE) {
            BlockPos behind = upstreamOf(at, route, seen);
            if (behind == null) break;
            Direction into = Direction.getApproximateNearest(
                    at.getX() - behind.getX(), at.getY() - behind.getY(), at.getZ() - behind.getZ());
            if (hops == 0) travel = into;
            seen.add(behind);
            hops++;
            at = behind;
            FluidCableBlockEntity cable = showingCable(behind);
            route = cable == null ? null : cable.flow();
        }
    }

    /**
     * The cable the fluid reaches {@code at} from: a neighbour whose direction runs into this
     * cable, else the cable behind this one's own direction. The network gives every cable a
     * direction once fluid is in, on a route or spreading from the entry, so the walk ends only
     * at the cable the fluid comes in by.
     */
    private @Nullable BlockPos upstreamOf(BlockPos at, @Nullable Direction route, java.util.Set<BlockPos> seen) {
        for (Direction side : Direction.values()) {
            BlockPos next = at.relative(side);
            FluidCableBlockEntity cable = showingCable(next);
            if (cable != null && !seen.contains(next) && cable.flow() == side.getOpposite()) return next;
        }
        if (route != null) {
            BlockPos behind = at.relative(route.getOpposite());
            if (showingCable(behind) != null && !seen.contains(behind)) return behind;
        }
        return null;
    }

    /** The fluid cable at {@code pos} if it is showing the same fluid this one is, else null. */
    private @Nullable FluidCableBlockEntity showingCable(BlockPos pos) {
        if (level == null || !(level.getBlockEntity(pos) instanceof FluidCableBlockEntity cable)) return null;
        return !cable.shown().isEmpty() && FluidStack.isSameFluidSameComponents(cable.shown(), drawn) ? cable : null;
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
        FluidStack before = shown;
        Direction routeBefore = flow;
        shown = input.read(SHOWN_TAG, FluidStack.CODEC).orElse(FluidStack.EMPTY);
        flow = input.read(FLOW_TAG, Direction.CODEC).orElse(null);
        if (level == null || !level.isClientSide()) return;
        // The wave sets off when fluid appears or goes; a cable that loads mid-flow shows it at once.
        if (shown.isEmpty() != before.isEmpty() && synced) waveTime = level.getGameTime();
        synced = true;
        // The way in is traced again while fluid shows; once it stops, the routes are gone from the
        // update and the trace from the flowing picture is what the tail follows out.
        if (!shown.isEmpty() && (flow != routeBefore || !FluidStack.isSameFluidSameComponents(shown, before))) hops = -1;
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
