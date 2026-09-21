package dev.futuretech.block.entity;

import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What one marker knows about its neighbours: how far away, along each of the four horizontal
 * directions, the next marker stands. The line between two of them is an edge of the frame the
 * quarry digs inside of, and the renderer draws it; a powered marker also shows the empty
 * directions, as a straight line saying where the next corner may go.
 *
 * <p>The links are worked out on the server and ride to the clients on the block's update packet.
 * Nothing here ticks: a marker recounts its lines when it is placed, broken or loaded, and when
 * another marker on one of its axes tells it to.
 */
public final class MiningMarkerBlockEntity extends BlockEntity {
    /**
     * How far one line reaches. An MK4 quarry digs 33 blocks across, which is a frame of 35, so
     * its two corners stand 34 apart: the longest edge the mod can ask for.
     */
    public static final int LINK_RANGE = 34;
    /** Markers one frame may hold before the search gives up; four corners is the honest number. */
    public static final int MAX_FRAME = 64;
    private static final String TAG = "Links";
    private static final int BITS = 6;
    private static final int MASK = (1 << BITS) - 1;

    /** Distance to the next marker per horizontal direction, indexed by {@code get2DDataValue}; 0 is none. */
    private final int[] links = new int[4];

    public MiningMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINING_MARKER.get(), pos, state);
    }

    /** Distance to the marker this one links to on {@code side}, or 0 when the line runs out empty. */
    public int link(Direction side) {
        return side.getAxis().isHorizontal() ? links[side.get2DDataValue()] : 0;
    }

    /** Recounts the four lines and tells the clients if any of them changed. */
    public void refresh() {
        if (level == null || level.isClientSide()) return;
        boolean changed = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int found = reach(level, worldPosition, side);
            if (links[side.get2DDataValue()] == found) continue;
            links[side.get2DDataValue()] = found;
            changed = true;
        }
        if (!changed) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** How many blocks along {@code side} the next marker stands, or 0 within {@link #LINK_RANGE}. */
    public static int reach(Level level, BlockPos from, Direction side) {
        var cursor = new BlockPos.MutableBlockPos().set(from);
        for (int step = 1; step <= LINK_RANGE; step++) {
            cursor.move(side);
            if (!level.hasChunkAt(cursor)) return 0;
            if (level.getBlockState(cursor).is(ModBlocks.MINING_MARKER.get())) return step;
        }
        return 0;
    }

    /**
     * Recounts the marker at {@code pos} and every marker its four axes reach, which is what a
     * marker being placed or broken changes. The block at {@code pos} may already be gone.
     */
    public static void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof MiningMarkerBlockEntity marker) marker.refresh();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int reach = reach(level, pos, side);
            if (reach == 0) continue;
            if (level.getBlockEntity(pos.relative(side, reach)) instanceof MiningMarkerBlockEntity other) other.refresh();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refresh();
    }

    /** The four lines in one value: {@value #BITS} bits each, which {@link #LINK_RANGE} fits in. */
    private int packed() {
        int packed = 0;
        for (int index = 0; index < links.length; index++) packed |= links[index] << (index * BITS);
        return packed;
    }

    private void unpack(int packed) {
        for (int index = 0; index < links.length; index++) {
            links[index] = Math.clamp((packed >>> (index * BITS)) & MASK, 0, LINK_RANGE);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        unpack(input.getIntOr(TAG, 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG, packed());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        output.putInt(TAG, packed());
        return output.buildResult();
    }

    @Override
    public void handleUpdateTag(ValueInput input) { loadAdditional(input); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
}
