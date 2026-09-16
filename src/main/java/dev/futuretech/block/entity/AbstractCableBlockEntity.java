package dev.futuretech.block.entity;

import com.mojang.serialization.Codec;
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
    private static final String CHANNELS_TAG = "Channels";
    private static final String COLORS_TAG = "Colors";
    private static final Codec<Map<Direction, Integer>> INTS_CODEC = Codec.unboundedMap(Direction.CODEC, Codec.INT);
    private static final Codec<Map<Direction, DyeColor>> COLORS_CODEC = Codec.unboundedMap(Direction.CODEC, DyeColor.CODEC);

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

    public void setCut(Direction side, boolean cut) {
        int bit = 1 << side.ordinal();
        int updated = cut ? cutSides | bit : cutSides & ~bit;
        if (updated == cutSides) return;
        cutSides = updated;
        setChanged();
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
        CompoundTag tag = SideConfigVisuals.updateTag(connectors);
        if (cutSides != 0) tag.putInt(CUT_TAG, cutSides);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        int previous = SideConfigVisuals.faceModes(connectors);
        connectors.load(input);
        cutSides = input.getIntOr(CUT_TAG, 0);
        if (previous != SideConfigVisuals.faceModes(connectors)) SideConfigVisuals.refresh(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        connectors.load(input);
        cutSides = input.getIntOr(CUT_TAG, 0);
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        connectors.save(output);
        if (cutSides != 0) output.putInt(CUT_TAG, cutSides);
        if (!colors.isEmpty()) output.store(COLORS_TAG, COLORS_CODEC, Map.copyOf(colors));
        if (!channels.isEmpty()) output.store(CHANNELS_TAG, INTS_CODEC, Map.copyOf(channels));
        if (!priorities.isEmpty()) output.store(PRIORITIES_TAG, INTS_CODEC, Map.copyOf(priorities));
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
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            if (level.hasChunkAt(neighbour) && level.getBlockEntity(neighbour) instanceof AbstractCableBlockEntity cable
                    && cable.kind() == kind()) {
                cable.invalidateNetwork();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
