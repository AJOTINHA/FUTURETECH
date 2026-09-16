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
    /** A time so far back that any wave set off then has long passed. */
    private static final long LONG_AGO = Long.MIN_VALUE / 2;
    /** No time at all: a wave without a tail, or a start not yet worked out. */
    private static final long NEVER = Long.MIN_VALUE;
    /**
     * Client: one passage of fluid through this cable: a front that came in when the fluid
     * appeared, and a tail that came in when it stopped. Each sets off through this cable once
     * it has passed the cable the fluid comes in by, so a wave runs cable by cable from wherever
     * it began: the entry of the line, or the first empty cable after a cut. Neither ever stops:
     * what is in the line keeps going to its end and out, and fluid coming back is a fresh wave
     * behind it, which joins the one ahead if it ever catches it up.
     */
    private static final class Wave {
        /** When the network showed the fluid, and stopped showing it, here. */
        final long frontTime;
        long tailTime = NEVER;
        /** When the front and the tail actually came into this cable; {@link #NEVER} until worked out. */
        long frontStart = NEVER;
        long tailStart = NEVER;
        /** Guards against a loop of stale directions while a start is worked out. */
        boolean settling;

        Wave(long frontTime) { this.frontTime = frontTime; }

        Wave(long frontTime, long frontStart) {
            this.frontTime = frontTime;
            this.frontStart = frontStart;
        }
    }

    /** Client: the wave the fluid is on now, and the one before it while its tail still runs out. */
    private @Nullable Wave current;
    private @Nullable Wave previous;
    /** Client: the last way the network said the fluid runs through here; kept once the picture goes, so the tail can be traced. */
    private @Nullable Direction lastFlow;
    /** Client: the cable the fluid comes in by, and the way it comes; null when unknown or not yet traced. */
    private @Nullable BlockPos upstream;
    private @Nullable Direction travel;
    private boolean traced;
    /** Client: whether the first picture from the server has arrived; that one is not animated. */
    private boolean synced;
    /** Ticks the front takes to cross one cable; the wave runs the whole line at this pace. */
    public static final int TICKS_PER_CABLE = 10;

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

    /** A stretch of the cable the fluid occupies, 0 at the face it comes in by to 1 at the far face. */
    public record Run(float from, float to) {
        public boolean isEmpty() { return from >= to; }
    }

    /**
     * What the fluid occupies in this cable along the way it travels: the run of the wave it is
     * on, and, while the tail of the wave before still runs out, that one's run too. A null
     * {@code travel} means the way in is unknown and the fluid rises.
     */
    public record Front(@Nullable Direction travel, Run first, @Nullable Run second) {}

    /**
     * Client: what to draw at {@code now}, or null while there is nothing to draw. A front or a
     * tail crosses one cable per {@value #TICKS_PER_CABLE} ticks and moves on to the next once
     * through, so a line fills, and later empties, cable by cable from wherever the change began.
     * A cut lets what is already in the line run on to its end, the tail behind it; fluid coming
     * back is a fresh wave behind that, joining it if it catches it up.
     */
    public @Nullable Front front(double now) {
        if (!shown.isEmpty()) drawn = shown;
        if (drawn.isEmpty() || current == null) return null;
        Run first = run(current, now);
        Run second = previous == null ? null : run(previous, now);
        // The fresh wave, once in this cable, reaching what the tail left: from here on they are
        // one. Before either has come in, both stand at 0 and nothing has met anything yet.
        if (second != null && first.to() > 0 && first.to() >= second.from()) {
            current.frontStart = previous.frontStart;
            previous = null;
            first = run(current, now);
            second = null;
        }
        // A wave is over once its tail has passed; one whose front has not yet arrived is only waiting.
        if (second != null && second.isEmpty() && passed(previous, now)) {
            previous = null;
            second = null;
        }
        if (first.isEmpty() && passed(current, now)) current = null;
        if (first.isEmpty() && (second == null || second.isEmpty())) {
            if (current == null && previous == null && shown.isEmpty()) drawn = FluidStack.EMPTY;
            return null;
        }
        return first.isEmpty() ? new Front(travel, second, null) : new Front(travel, first, second);
    }

    /** The stretch of this cable a wave occupies at {@code now}: from its tail, if any, to its front. */
    private Run run(Wave wave, double now) {
        float to = progress(now, frontStart(wave));
        float from = wave.tailTime == NEVER ? 0 : progress(now, tailStart(wave));
        return new Run(from, to);
    }

    /** Whether a wave's tail has run all the way through this cable. */
    private boolean passed(Wave wave, double now) {
        return wave.tailTime != NEVER && progress(now, tailStart(wave)) >= 1;
    }

    /** How far something that came into this cable at {@code since} has got through it, 0 to 1. */
    private static float progress(double now, long since) {
        return (float) Math.clamp((now - since) / TICKS_PER_CABLE, 0, 1);
    }

    /**
     * When a wave's front came into this cable: when the network showed the fluid here, unless
     * the cable it comes in by was still filling then, in which case once that one was full. A
     * cable with nothing before it, or nothing known, is where the wave begins.
     */
    private long frontStart(Wave wave) {
        if (wave.frontStart == NEVER && !wave.settling) {
            wave.settling = true;
            long start = wave.frontTime;
            FluidCableBlockEntity behind = behind();
            if (behind != null && behind.current != null) start = Math.max(start, behind.frontStart(behind.current) + TICKS_PER_CABLE);
            wave.frontStart = start;
            wave.settling = false;
        }
        return wave.frontStart == NEVER ? wave.frontTime : wave.frontStart;
    }

    /** When a wave's tail came into this cable, by the same rule as its front. */
    private long tailStart(Wave wave) {
        if (wave.tailStart == NEVER && !wave.settling) {
            wave.settling = true;
            long start = wave.tailTime;
            FluidCableBlockEntity behind = behind();
            Wave ahead = behind == null ? null : behind.current != null && behind.current.tailTime != NEVER ? behind.current : behind.previous;
            if (ahead != null && ahead.tailTime != NEVER) start = Math.max(start, behind.tailStart(ahead) + TICKS_PER_CABLE);
            wave.tailStart = start;
            wave.settling = false;
        }
        return wave.tailStart == NEVER ? wave.tailTime : wave.tailStart;
    }

    /** The cable the fluid comes in by, traced once per change of picture; null at the start of the line. */
    private @Nullable FluidCableBlockEntity behind() {
        if (!traced) {
            traced = true;
            upstream = upstreamOf();
            travel = upstream == null ? lastFlow : Direction.getApproximateNearest(
                    worldPosition.getX() - upstream.getX(), worldPosition.getY() - upstream.getY(), worldPosition.getZ() - upstream.getZ());
        }
        return upstream == null || level == null || !(level.getBlockEntity(upstream) instanceof FluidCableBlockEntity cable) ? null : cable;
    }

    /**
     * The cable the fluid reaches this one from: a neighbour whose direction runs into this
     * cable, else the cable behind this one's own direction. The network gives every cable a
     * direction once fluid is in, on a route or spreading from the entry, so only the cable the
     * fluid comes in by has none. Goes by what the cables still draw and the last direction each
     * was given, so it works after the picture has gone too, when the tail needs it.
     */
    private @Nullable BlockPos upstreamOf() {
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            FluidCableBlockEntity cable = drawingCable(next);
            if (cable != null && cable.lastFlow == side.getOpposite()) return next;
        }
        if (lastFlow != null) {
            BlockPos behind = worldPosition.relative(lastFlow.getOpposite());
            if (drawingCable(behind) != null) return behind;
        }
        return null;
    }

    /** The fluid cable at {@code pos} if it is drawing the same fluid this one is, else null. */
    private @Nullable FluidCableBlockEntity drawingCable(BlockPos pos) {
        if (level == null || !(level.getBlockEntity(pos) instanceof FluidCableBlockEntity cable)) return null;
        return !cable.drawn.isEmpty() && FluidStack.isSameFluidSameComponents(cable.drawn, drawn) ? cable : null;
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
        // Kept here, not where the cable is drawn: a neighbour traces its way in through this
        // cable's fluid and direction whether or not this cable has been drawn yet, or ever is,
        // and still once the picture has gone.
        if (!shown.isEmpty()) drawn = shown;
        if (flow != null) lastFlow = flow;
        // Fluid appearing sends a front off; fluid going sends a tail. A cable that loads mid-flow
        // shows it at once.
        if (shown.isEmpty() != before.isEmpty()) {
            long now = level.getGameTime();
            if (!synced) {
                current = shown.isEmpty() ? null : new Wave(LONG_AGO, LONG_AGO);
            } else if (shown.isEmpty()) {
                if (current != null) current.tailTime = now;
            } else {
                if (current != null && current.tailTime != Long.MIN_VALUE) previous = current;
                current = new Wave(now);
            }
        }
        synced = true;
        // The way in is traced again while fluid shows; once it stops, the routes are gone from the
        // update and the trace from the flowing picture is what the tail follows out.
        if (!shown.isEmpty() && (flow != routeBefore || !FluidStack.isSameFluidSameComponents(shown, before))) traced = false;
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
