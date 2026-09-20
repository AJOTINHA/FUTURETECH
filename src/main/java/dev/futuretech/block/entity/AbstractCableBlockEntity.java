package dev.futuretech.block.entity;

import com.mojang.serialization.Codec;
import dev.futuretech.api.facade.CableFacades;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.CableKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import dev.futuretech.item.FacadeItem;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The part of a cable that does not care what it carries: the six connector modes, their
 * priority, colour and channel, how they reach the client and how the player changes them. Each
 * kind of cable adds its own network on top, and only reads the settings its kind exposes.
 */
public abstract class AbstractCableBlockEntity extends BlockEntity {
    public static final int MIN_PRIORITY = -100;
    public static final int MAX_PRIORITY = 100;
    public static final int MAX_CHANNEL = 100;
    /** A connector may insert, extract, do both, or nothing at all. */
    private static final Set<SideMode> ALLOWED_MODES =
            Set.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH);
    private static final String PRIORITIES_TAG = "Priorities";
    private static final String CUT_TAG = "Cut";
    private static final String FORCED_TAG = "Forced";
    private static final String CHANNELS_TAG = "Channels";
    private static final String COLORS_TAG = "Colors";
    private static final String FACADES_TAG = "Facades";
    private static final String REDSTONE_TAG = "Redstone";
    private static final Codec<Map<Direction, Integer>> INTS_CODEC = Codec.unboundedMap(Direction.CODEC, Codec.INT);
    private static final Codec<Map<Direction, DyeColor>> COLORS_CODEC = Codec.unboundedMap(Direction.CODEC, DyeColor.CODEC);
    private static final Codec<Map<Direction, BlockState>> FACADES_CODEC = Codec.unboundedMap(Direction.CODEC, BlockState.CODEC);
    private static final Codec<Map<Direction, RedstoneMode>> REDSTONE_CODEC = Codec.unboundedMap(Direction.CODEC, RedstoneMode.CODEC);

    /** What each connector does; a fresh one starts on {@link CableKind#freshConnector()}. */
    private final SideConfig connectors;
    /** Only faces the player moved off 0 are kept, so an untouched cable saves nothing extra. */
    private final EnumMap<Direction, Integer> priorities = new EnumMap<>(Direction.class);
    /** Only faces moved off white are kept; a face without an entry is on the white channel. */
    private final EnumMap<Direction, DyeColor> colors = new EnumMap<>(Direction.class);
    /** Only faces moved off 0 are kept. */
    private final EnumMap<Direction, Integer> channels = new EnumMap<>(Direction.class);
    /**
     * Sides the player cut with the wrench, one bit per {@code Direction.ordinal()}. A cut side
     * joins nothing: not the cable beyond it, not a machine. Both cables of a cut run carry the
     * bit, so either end restores it. Reaches the client, whose shape updates must agree.
     */
    private int cutSides;
    /**
     * Sides the player joined with the wrench to a block the cable would not link to on its own,
     * one bit per {@code Direction.ordinal()}; only a kind that allows it ever sets one. Reaches
     * the client like the cuts, for the same reason.
     */
    private int forcedSides;
    /**
     * The block each covered face wears. Only covered faces are kept, so a bare cable saves
     * nothing extra, and the map reaches the client because the panel is drawn from it.
     */
    private final EnumMap<Direction, BlockState> facades = new EnumMap<>(Direction.class);
    /**
     * How each connector answers to redstone; only faces moved off "ignored" are kept. The signal
     * is the cable block's own, sampled when it loads and when a neighbour changes, the way the
     * machines do, and every connector on this cable reads that one sample.
     */
    private final EnumMap<Direction, RedstoneMode> redstone = new EnumMap<>(Direction.class);
    private boolean powered;

    protected AbstractCableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, CableKind kind) {
        super(type, pos, state);
        connectors = new SideConfig(ALLOWED_MODES, true, side -> kind.freshConnector());
    }

    public abstract CableKind kind();

    /** Invalidates the network this cable belongs to, if it has one; the next tick rebuilds it. */
    public abstract void invalidateNetwork();

    /** One server tick of this cable's network. */
    public abstract void serverTick();

    public SideConfig connectors() { return connectors; }

    /** Whether the wrench cut the link on {@code side}. */
    public boolean isCut(Direction side) { return (cutSides & (1 << side.ordinal())) != 0; }

    /**
     * The neighbours are told: a cut side offers no handler, and a machine holding the old one
     * would keep pushing through a link that is no longer there.
     */
    public void setCut(Direction side, boolean cut) {
        int bit = 1 << side.ordinal();
        int updated = cut ? cutSides | bit : cutSides & ~bit;
        if (updated == cutSides) return;
        cutSides = updated;
        setChanged();
        invalidateCapabilities();
    }

    /** Whether the wrench forced the link on {@code side} to a block the cable would not link to itself. */
    public boolean isForced(Direction side) { return (forcedSides & (1 << side.ordinal())) != 0; }

    public void setForced(Direction side, boolean forced) {
        int bit = 1 << side.ordinal();
        int updated = forced ? forcedSides | bit : forcedSides & ~bit;
        if (updated == forcedSides) return;
        forcedSides = updated;
        setChanged();
        invalidateCapabilities();
    }

    /** The block covering {@code side}, or null while that face shows the bare cable. */
    public @Nullable BlockState facade(Direction side) { return facades.get(side); }

    public boolean hasFacade(Direction side) { return facades.containsKey(side); }

    /** The covered faces, for the model and for what a broken cable owes the player back. */
    public Map<Direction, BlockState> facades() { return Map.copyOf(facades); }

    /**
     * Covers or uncovers one face. The panel is geometry, so the clients need the change and the
     * chunk has to be meshed again; the shape changes with it, which is why the block is updated.
     */
    public void setFacade(Direction side, @Nullable BlockState state) {
        if (state == null ? facades.remove(side) == null : state.equals(facades.put(side, state))) return;
        setChanged();
        if (level != null && !level.isClientSide()) SideConfigVisuals.refresh(this);
    }

    /** Where this connector stands when the network chooses whom to serve first; higher goes first. */
    public int connectorPriority(Direction side) { return priorities.getOrDefault(side, 0); }

    /** Clamped to the allowed range; the network caches priorities, so it is rebuilt on a change. */
    public void setConnectorPriority(Direction side, int priority) {
        int clamped = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
        if (clamped == connectorPriority(side)) return;
        if (clamped == 0) priorities.remove(side);
        else priorities.put(side, clamped);
        setChanged();
        invalidateNetwork();
    }

    /**
     * The filter cards of the six connectors, slot {@code side.ordinal()} for each side. Kinds
     * without filters answer an empty container that never shows in a menu.
     */
    public Container connectorFilters() { return new SimpleContainer(Direction.values().length); }

    /** The upgrade modules of the six connectors, laid out like {@link #connectorFilters()}. */
    public Container connectorUpgrades() { return new SimpleContainer(Direction.values().length); }

    /** The colour channel of a connector; white until changed. */
    public DyeColor connectorColor(Direction side) { return colors.getOrDefault(side, DyeColor.WHITE); }

    /** The network caches colours, so it is rebuilt on a change. */
    public void setConnectorColor(Direction side, DyeColor color) {
        if (color == connectorColor(side)) return;
        if (color == DyeColor.WHITE) colors.remove(side);
        else colors.put(side, color);
        setChanged();
        invalidateNetwork();
    }

    /** The numbered channel of a connector, 0 until changed; with the colour it says who talks to whom. */
    public int connectorChannel(Direction side) { return channels.getOrDefault(side, 0); }

    /** Clamped to 0..{@value #MAX_CHANNEL}; the network caches channels, so it is rebuilt on a change. */
    public void setConnectorChannel(Direction side, int channel) {
        int clamped = Math.clamp(channel, 0, MAX_CHANNEL);
        if (clamped == connectorChannel(side)) return;
        if (clamped == 0) channels.remove(side);
        else channels.put(side, clamped);
        setChanged();
        invalidateNetwork();
    }

    /** How the connector on {@code side} answers to redstone; ignored until changed. */
    public RedstoneMode connectorRedstone(Direction side) { return redstone.getOrDefault(side, RedstoneMode.IGNORED); }

    /**
     * No network rebuild: the networks ask {@link #connectorActive} as they go, so a signal that
     * flips every few ticks costs nothing but a lookup.
     */
    public void setConnectorRedstone(Direction side, RedstoneMode mode) {
        if (mode == connectorRedstone(side)) return;
        if (mode == RedstoneMode.IGNORED) redstone.remove(side);
        else redstone.put(side, mode);
        setChanged();
    }

    /** Whether the cable block has a redstone signal, as last sampled. */
    public boolean isPowered() { return powered; }

    /**
     * Whether the connector on {@code side} is working right now: its mode allows the signal the
     * cable last saw. A connector that is not working neither delivers nor pulls, and refuses what
     * the neighbour pushes into it, but keeps its settings for when the signal lets it work again.
     */
    public boolean connectorActive(@Nullable Direction side) {
        return side == null || connectorRedstone(side).allows(powered);
    }

    /** Samples the signal at the cable; called on load and from the block's {@code neighborChanged}. */
    public void samplePower(Level level) { powered = level.hasNeighborSignal(worldPosition); }

    /**
     * Applies a connector's new mode and rebuilds the network, which caches what each face allows.
     * The handlers the neighbours see capture the mode too, so their caches are dropped, the way
     * a machine drops them when a face of its own changes.
     */
    public void setConnectorMode(Direction side, SideMode mode) {
        if (connectors.mode(side) == mode) return;
        connectors.set(side, mode);
        setChanged();
        invalidateNetwork();
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
    }

    // The connector modes are the cable's only render data, and they drive the coloured band on
    // each collar, so they have to reach the client like the machines' face modes do.
    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(SideConfigVisuals.FACE_MODES, SideConfigVisuals.faceModes(connectors))
                .with(CableFacades.FACADES, facades()).build();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = SideConfigVisuals.updateTag(connectors);
        if (cutSides != 0) tag.putInt(CUT_TAG, cutSides);
        if (forcedSides != 0) tag.putInt(FORCED_TAG, forcedSides);
        if (!facades.isEmpty()) {
            FACADES_CODEC.encodeStart(NbtOps.INSTANCE, Map.copyOf(facades)).result()
                    .ifPresent(encoded -> tag.put(FACADES_TAG, encoded));
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        int previous = SideConfigVisuals.faceModes(connectors);
        var previousFacades = Map.copyOf(facades);
        connectors.load(input);
        cutSides = input.getIntOr(CUT_TAG, 0);
        forcedSides = input.getIntOr(FORCED_TAG, 0);
        loadFacades(input);
        if (previous != SideConfigVisuals.faceModes(connectors) || !previousFacades.equals(facades)) {
            SideConfigVisuals.refresh(this);
        }
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        connectors.load(input);
        cutSides = input.getIntOr(CUT_TAG, 0);
        forcedSides = input.getIntOr(FORCED_TAG, 0);
        loadFacades(input);
        colors.clear();
        input.read(COLORS_TAG, COLORS_CODEC).ifPresent(colors::putAll);
        channels.clear();
        input.read(CHANNELS_TAG, INTS_CODEC).ifPresent(saved -> saved.forEach((side, channel) -> {
            int clamped = Math.clamp(channel, 0, MAX_CHANNEL);
            if (clamped != 0) channels.put(side, clamped);
        }));
        priorities.clear();
        input.read(PRIORITIES_TAG, INTS_CODEC).ifPresent(saved -> saved.forEach((side, priority) -> {
            int clamped = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
            if (clamped != 0) priorities.put(side, clamped);
        }));
        redstone.clear();
        input.read(REDSTONE_TAG, REDSTONE_CODEC).ifPresent(saved -> saved.forEach((side, mode) -> {
            if (mode != RedstoneMode.IGNORED) redstone.put(side, mode);
        }));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        connectors.save(output);
        if (cutSides != 0) output.putInt(CUT_TAG, cutSides);
        if (forcedSides != 0) output.putInt(FORCED_TAG, forcedSides);
        if (!facades.isEmpty()) output.store(FACADES_TAG, FACADES_CODEC, Map.copyOf(facades));
        if (!colors.isEmpty()) output.store(COLORS_TAG, COLORS_CODEC, Map.copyOf(colors));
        if (!channels.isEmpty()) output.store(CHANNELS_TAG, INTS_CODEC, Map.copyOf(channels));
        if (!priorities.isEmpty()) output.store(PRIORITIES_TAG, INTS_CODEC, Map.copyOf(priorities));
        if (!redstone.isEmpty()) output.store(REDSTONE_TAG, REDSTONE_CODEC, Map.copyOf(redstone));
    }

    /** Keeps only what is still a legal facade, so a block that changed between versions is dropped quietly. */
    private void loadFacades(ValueInput input) {
        facades.clear();
        input.read(FACADES_TAG, FACADES_CODEC).ifPresent(saved -> saved.forEach((side, state) -> {
            if (CableFacades.isValid(state)) facades.put(side, state);
        }));
    }

    /**
     * A cable that just loaded may border cables whose network was discovered while this chunk was
     * still unloaded, and so stops at the chunk edge. Those networks are rebuilt on their next
     * tick, the way placing a cable rebuilds them, so a network never stays cut at a chunk border.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        samplePower(level);
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            if (level.hasChunkAt(neighbour) && level.getBlockEntity(neighbour) instanceof AbstractCableBlockEntity cable
                    && cable.kind() == kind()) {
                cable.invalidateNetwork();
            }
        }
    }

    /** A broken cable hands back every panel it was wearing, like a machine hands back its upgrades. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        for (var facade : facades.values()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    FacadeItem.of(facade));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
