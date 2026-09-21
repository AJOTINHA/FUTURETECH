package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.side.AutoTransfer;
import dev.futuretech.api.side.AutoTransferable;
import dev.futuretech.api.side.SideConfig;
import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideConfigurable;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.QuarryArea;
import dev.futuretech.block.QuarryBlock;
import dev.futuretech.block.QuarryFrame;
import dev.futuretech.block.QuarryFrameBlock;
import dev.futuretech.block.QuarryStatus;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.menu.QuarryMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.transfer.ItemTransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Digs the box its area markers mark, layer by layer from the top down to the bottom of the world,
 * paying energy for every block. What it breaks lands in its own buffer and leaves through the
 * faces in an output mode; with the buffer full the quarry waits instead of dropping anything on
 * the ground. Bedrock, the markers themselves and anything holding a block entity — chests,
 * spawners, other machines — are left standing, and fluids in the way are cleared so the hole
 * does not fill in behind the digging.
 *
 * <p>What was dug is never final: a second cursor keeps sweeping the emptied part of the box, and
 * anything that turns up there — a block somebody placed, gravel that fell, cobblestone a lava flow
 * made — is dug again before the digging carries on. A finished quarry keeps sweeping the whole
 * box, so filling the hole back in only feeds it.
 *
 * <p>Every level above MK1 digs proportionally faster for proportionally more energy a tick, so a
 * block costs the same on every level, and accepts a longer side: see {@link QuarryArea#maxSide}.
 */
public final class QuarryBlockEntity extends BaseContainerBlockEntity
        implements AutoTransferable, SideConfigurable, RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 20_000;
    /** Drawn from the buffer for every tick of digging; higher levels draw proportionally more. */
    public static final int ENERGY_PER_TICK = 40;
    public static final int INPUT_PER_TICK = 400;
    /** One second of powered work per block on a plain MK1, so 800 FE a block at every level. */
    public static final int WORK_TICKS = 20;
    /** The buffer: three rows of five, and the quarry stops once what it digs no longer fits. */
    public static final int INVENTORY_SIZE = 15;
    /**
     * Positions the cursor may walk past in one tick while looking for the next block worth
     * digging. Air costs nothing to skip, but a layer of open sky is a thousand of them, so the
     * budget keeps one tick bounded without making a quarry crawl through a cave.
     */
    public static final int SKIPS_PER_TICK = 256;
    /**
     * Positions the sweep looks at a tick, on top of the digging. It walks nothing but the
     * emptied part of the box, so this is what bounds how long a block placed there stands.
     */
    public static final int SWEEPS_PER_TICK = 128;
    /** How often a quarry stopped on a full buffer tries the block again; see {@link #dig}. */
    public static final int FULL_RETRY_TICKS = 20;
    /** Girders a tick while the scaffold goes up, so a big frame is seen rising rather than appearing. */
    public static final int GIRDERS_PER_TICK = 2;
    /** Girder positions checked a tick once the scaffold stands, to put back what was broken. */
    public static final int REPAIRS_PER_TICK = 2;
    /** Ticks the arm takes to slide from the block it finished to the next one. */
    public static final int ARM_TRAVEL_TICKS = 6;
    // Energy is synced as two 16-bit halves; see EnergySync.
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_PROGRESS_TOTAL = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_WIDTH = 5;
    public static final int DATA_DEPTH = 6;
    /** The layer being dug; the world's own y, which fits the 16 bits a data slot travels in. */
    public static final int DATA_LAYER = 7;
    public static final int DATA_SIDE_BASE = 8;
    public static final int DATA_FRONT = DATA_SIDE_BASE + SideConfig.DATA_COUNT;
    public static final int DATA_AUTO_BASE = DATA_FRONT + 1;
    public static final int DATA_REDSTONE_BASE = DATA_AUTO_BASE + AutoTransfer.DATA_COUNT;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;

    private static final String TAG_TARGET = "Target";
    private static final String TAG_BUILDING = "Building";
    private static final String TAG_GIRDER = "Girder";
    private static final int[] NO_SLOTS = {};
    private static final int[] ALL_SLOTS = buildSlots();
    /**
     * What the quarry breaks blocks with, for the sake of their drop tables: stone drops
     * cobblestone and iron ore drops raw iron, as a pickaxe would. It is never damaged or shown.
     */
    private static final ItemStack TOOL = new ItemStack(Items.NETHERITE_PICKAXE);

    private NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    /** The box being dug, or null while the markers have not been read into one. */
    private @Nullable QuarryArea area;
    /** Why the last scan gave no box; only read while {@link #area} is null. */
    private QuarryArea.Result scan = QuarryArea.Result.NO_MARKER_BEHIND;
    private QuarryStatus status = QuarryStatus.NO_MARKER_BEHIND;
    /** The layer being dug and how far along it the cursor is. */
    private int layer;
    private int cursor;
    /** The block being dug right now, and the ticks already spent on it. */
    private @Nullable BlockPos target;
    private int progress;
    /** Whether {@link #target} came from the sweep; such a block moves the cursor nothing. */
    private boolean revisiting;
    /** A block the sweep found standing where the box was already dug; dug next, before the cursor goes on. */
    private @Nullable BlockPos revisit;
    /** Where the sweep is in the emptied part of the box; it starts over from the top when it catches the cursor. */
    private int sweepLayer;
    private int sweepCursor;
    /** The scaffold's girder positions for the current box, worked out once per box. */
    private @Nullable List<BlockPos> frame;
    /** How far along {@link #frame} the machine is, and whether it is still putting it up. */
    private int girder;
    private boolean building = true;
    /** The rolling check that puts back a girder somebody broke, while the digging carries on. */
    private int repair;
    /** Client side only: where the arm was before the last packet, and when that packet came. */
    private @Nullable BlockPos previousTarget;
    private long targetChanged;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::markChanged);
    private final SideConfig sides;
    private final AutoTransfer auto = new AutoTransfer();
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final ItemTransferUtil transfer = new ItemTransferUtil();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::markChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_PROGRESS -> progress;
                case DATA_PROGRESS_TOTAL -> workTicks();
                case DATA_STATUS -> status.ordinal();
                case DATA_WIDTH -> area == null ? 0 : area.width();
                case DATA_DEPTH -> area == null ? 0 : area.depth();
                case DATA_LAYER -> area == null ? 0 : layer;
                case DATA_FRONT -> front().ordinal();
                case DATA_MK -> MachineLevel.of(getBlockState());
                default -> {
                    if (index >= DATA_SIDE_BASE && index < DATA_FRONT) yield sides.data(index - DATA_SIDE_BASE);
                    if (index >= DATA_AUTO_BASE && index < DATA_REDSTONE_BASE) yield auto.data(index - DATA_AUTO_BASE);
                    if (index >= DATA_REDSTONE_BASE && index < DATA_MK) yield redstone.data(index - DATA_REDSTONE_BASE);
                    yield 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // The authoritative data is read-only; clients use SimpleContainerData.
        }

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY.get(), pos, state);
        this.sides = ((SideConfigurableBlock) ModBlocks.QUARRY.get()).createSideConfig(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    private static int[] buildSlots() {
        int[] slots = new int[INVENTORY_SIZE];
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) slots[slot] = slot;
        return slots;
    }

    @Override
    public SideConfig sideConfig() { return sides; }

    @Override
    public ModelData getModelData() { return SideConfigVisuals.modelData(sides); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // The faces, the box and where the arm is: what the renderer needs and nothing more.
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        sides.save(output);
        if (area != null) area.save(output);
        if (target != null) output.store(TAG_TARGET, BlockPos.CODEC, target);
        output.putBoolean(TAG_BUILDING, building);
        return output.buildResult();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(ValueInput input) {
        sides.load(input);
        area = QuarryArea.load(input);
        frame = null;
        building = input.getBooleanOr(TAG_BUILDING, false);
        BlockPos next = input.read(TAG_TARGET, BlockPos.CODEC).orElse(null);
        if (!java.util.Objects.equals(next, target)) {
            // The arm slides from where it was to where it now is; the renderer needs both.
            previousTarget = target;
            targetChanged = level == null ? 0 : level.getGameTime();
            target = next;
        }
        SideConfigVisuals.refresh(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override
    public AutoTransfer autoTransfer() { return auto; }

    @Override
    public void autoTransferChanged() { setChanged(); }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    /** An upgrade kit swaps the block state under us; the buffer grows with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        Containers.dropContents(level, pos, upgrades);
        clearFrame(level);
    }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    @Override
    public Direction front() {
        return getBlockState().hasProperty(QuarryBlock.FACING)
                ? getBlockState().getValue(QuarryBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void sideConfigChanged() {
        setChanged();
        invalidateCapabilities();
        SideConfigVisuals.refresh(this);
        if (level != null) getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
    }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    public QuarryStatus status() { return status; }

    public @Nullable QuarryArea area() { return area; }

    /** The layer being dug, as a world y; meaningless while there is no area. */
    public int layer() { return layer; }

    /** Ticks one block takes at this level with the upgrades installed. */
    public int workTicks() { return upgrades.duration(WORK_TICKS, MachineLevel.of(getBlockState())); }

    /** Energy a tick of digging draws at this level with the upgrades installed. */
    public int consumptionPerTick() { return upgrades.consumption(ENERGY_PER_TICK, MachineLevel.of(getBlockState())); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        RedstoneControl.sample(level, worldPosition);
        // A quarry placed next to its markers starts without the player having to ask.
        if (!level.isClientSide() && area == null) readMarkers();
    }

    /**
     * Reads the square behind the quarry into a box and starts at its top layer. The screen's
     * button comes here, and so does a quarry that loaded without one; a scan that finds nothing
     * leaves the old box alone only when there is none to lose.
     */
    public void readMarkers() {
        if (level == null || level.isClientSide()) return;
        var found = QuarryArea.scan(level, worldPosition, front().getOpposite(), MachineLevel.of(getBlockState()));
        scan = found.result();
        if (found.area() == null) {
            clearFrame(level);
            area = null;
            frame = null;
            status = QuarryStatus.of(scan);
        } else if (!found.area().equals(area)) {
            clearFrame(level);
            area = found.area();
            frame = null;
            layer = area.topY();
            cursor = 0;
            sweepLayer = area.topY();
            sweepCursor = 0;
            revisit = null;
            girder = 0;
            repair = 0;
            building = true;
            target = null;
            progress = 0;
            status = QuarryStatus.BUILDING;
        }
        setChanged();
        sendToClients();
    }

    private void markChanged() { ComparatorNotifier.markChanged(this); }

    @Override
    public void setChanged() { markChanged(); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity quarry) {
        quarry.beginTick();
        // Only pushing: what a quarry digs leaves, nothing useful ever comes in.
        if (quarry.auto.isPushing()) quarry.transfer.pushToNeighbours(level, pos, quarry, quarry.sides);
        boolean digging = false;
        if (!quarry.redstone.allowsRunning()) quarry.status = QuarryStatus.OFF;
        else if (level instanceof ServerLevel server) {
            // The scaffold goes up first; once it stands, a couple of its girders are checked a
            // tick so anything broken is put back without the digging ever stopping for it.
            digging = quarry.building ? quarry.build(server) : quarry.repair(server) | quarry.dig(server);
        }
        boolean lit = quarry.litHold.update(digging);
        if (state.getValue(QuarryBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(QuarryBlock.LIT, lit), Block.UPDATE_ALL);
        }
        quarry.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(quarry.energy));
    }

    /** Opens a new tick's input budget so neighbours can push their rated amount in. */
    void beginTick() { energy.beginTick(); }

    /** One tick of digging; answers whether energy was spent on a block this tick. */
    boolean dig(ServerLevel level) {
        if (area == null) {
            status = QuarryStatus.of(scan);
            return false;
        }
        sweep(level);
        // A full buffer is retried now and then, not every tick: the retry rolls the block's loot
        // table to see whether it would fit, and a stopped quarry should not pay for that 20 times
        // a second.
        if (status == QuarryStatus.FULL && level.getGameTime() % FULL_RETRY_TICKS != 0) return false;
        BlockPos pos = target;
        if (pos == null) {
            if (revisit != null) {
                // Something stands where the box was already dug: go back for it before going on.
                pos = target = revisit;
                revisit = null;
                revisiting = true;
                progress = 0;
                sendToClients();
            } else if (layer < level.getMinY()) {
                status = QuarryStatus.DONE;
                return false;
            } else {
                revisiting = false;
                pos = findTarget(level);
            }
        }
        if (pos == null) return false;
        BlockState state = level.getBlockState(pos);
        // The world moved under us between two ticks: drop the block and look again next tick.
        if (!isMineable(level, pos, state)) {
            abandonTarget();
            return false;
        }
        int perTick = consumptionPerTick();
        if (energy.getAmountAsInt() < perTick) {
            status = QuarryStatus.NO_ENERGY;
            return false;
        }
        int total = workTicks();
        if (progress + 1 >= total && !breakBlock(level, pos, state)) {
            // The drops have nowhere to go; the block stays whole and the progress waits at full.
            progress = total - 1;
            status = QuarryStatus.FULL;
            return false;
        }
        energy.set(energy.getAmountAsInt() - perTick);
        progress++;
        status = QuarryStatus.MINING;
        if (progress >= total) {
            progress = 0;
            target = null;
            if (!revisiting) cursor++;
        }
        markChanged();
        return true;
    }

    /**
     * Walks the sweep a little further through the emptied part of the box: every position before
     * the cursor, or the whole box once it has reached the bottom. The first block worth digging
     * it meets is kept for {@link #dig}; fluids are cleared as the cursor clears them.
     */
    private void sweep(ServerLevel level) {
        if (revisit != null) return;
        for (int step = 0; step < SWEEPS_PER_TICK; step++) {
            if (sweepLayer < level.getMinY() || !dugBefore(sweepLayer, sweepCursor)) {
                sweepLayer = area.topY();
                sweepCursor = 0;
                // Nothing has been dug yet, so there is nothing to sweep.
                if (!dugBefore(sweepLayer, sweepCursor)) return;
            }
            if (sweepCursor >= area.layerSize()) {
                sweepCursor = 0;
                sweepLayer--;
                continue;
            }
            BlockPos pos = area.posAt(sweepCursor, sweepLayer);
            if (!level.hasChunkAt(pos)) return;
            BlockState state = level.getBlockState(pos);
            sweepCursor++;
            if (isMineable(level, pos, state)) {
                revisit = pos;
                return;
            }
            if (!state.getFluidState().isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Whether the cursor has already been past this position: a higher layer, or earlier on its own. */
    private boolean dugBefore(int atLayer, int atCursor) {
        return atLayer > layer || (atLayer == layer && atCursor < cursor);
    }

    /** Forgets the block being dug without paying for it; the cursor tries the same spot again. */
    private void abandonTarget() {
        target = null;
        progress = 0;
    }

    /** What happened to one girder this tick. */
    private enum Girder { PLACED, SKIPPED, WAITING }

    /** The scaffold's girder positions for the box being dug, worked out once per box. */
    private @Nullable List<BlockPos> frame() {
        if (area == null) return null;
        if (frame == null) frame = QuarryFrame.positions(area);
        return frame;
    }

    /** Puts up a few girders; answers whether energy was spent. Ends the build at the last one. */
    boolean build(ServerLevel level) {
        var girders = frame();
        if (girders == null) {
            status = QuarryStatus.of(scan);
            return false;
        }
        boolean worked = false;
        status = QuarryStatus.BUILDING;
        for (int step = 0; step < GIRDERS_PER_TICK; step++) {
            if (girder >= girders.size()) {
                building = false;
                status = QuarryStatus.MINING;
                setChanged();
                sendToClients();
                return worked;
            }
            Girder result = placeGirder(level, girders.get(girder));
            if (result == Girder.WAITING) return worked;
            if (result == Girder.PLACED) worked = true;
            girder++;
        }
        return worked;
    }

    /**
     * Checks a couple of girders and puts back what is missing, so a scaffold somebody broke into
     * mends itself while the digging carries on. The status is the digging's to set, not this.
     */
    boolean repair(ServerLevel level) {
        var girders = frame();
        if (girders == null || girders.isEmpty()) return false;
        boolean worked = false;
        for (int step = 0; step < REPAIRS_PER_TICK; step++) {
            if (repair >= girders.size()) repair = 0;
            BlockPos pos = girders.get(repair++);
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).is(ModBlocks.QUARRY_FRAME.get())) continue;
            if (placeGirder(level, pos) == Girder.PLACED) worked = true;
        }
        return worked;
    }

    /**
     * Stands one girder up, clearing whatever is in its way into the buffer first. A spot that
     * cannot be cleared — bedrock, a chest, a machine — is left as a gap rather than voided.
     */
    private Girder placeGirder(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            status = QuarryStatus.UNLOADED;
            return Girder.WAITING;
        }
        var girderBlock = ModBlocks.QUARRY_FRAME.get();
        BlockState state = level.getBlockState(pos);
        if (state.is(girderBlock)) return Girder.SKIPPED;
        int perTick = consumptionPerTick();
        if (energy.getAmountAsInt() < perTick) {
            status = QuarryStatus.NO_ENERGY;
            return Girder.WAITING;
        }
        if (!state.isAir() && !state.canBeReplaced()) {
            // A leg stands where a marker did: the marker is picked up into the buffer, the way
            // anything else in the girder's way is, so the corner can close over it.
            boolean marker = state.is(ModBlocks.MINING_MARKER.get());
            if (!marker && !isMineable(level, pos, state)) return Girder.SKIPPED;
            if (!breakBlock(level, pos, state)) {
                status = QuarryStatus.FULL;
                return Girder.WAITING;
            }
        }
        energy.set(energy.getAmountAsInt() - perTick);
        level.setBlock(pos, QuarryFrameBlock.connected(girderBlock.defaultBlockState(), level, pos), Block.UPDATE_ALL);
        markChanged();
        return Girder.PLACED;
    }

    /** Takes the scaffold down: the machine leaving, or a new box replacing the old one. */
    private void clearFrame(Level level) {
        var girders = frame();
        if (girders == null || level.isClientSide()) return;
        for (BlockPos pos : girders) {
            if (level.getBlockState(pos).is(ModBlocks.QUARRY_FRAME.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Tells the clients where the arm is, which is what their renderer draws it from. */
    private void sendToClients() {
        if (level == null || level.isClientSide()) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The block under the arm right now, for the renderer. */
    public @Nullable BlockPos target() { return target; }

    /** Where the arm was before the last packet, so the renderer can slide it across. */
    public @Nullable BlockPos previousTarget() { return previousTarget; }

    /** The tick the arm was last given somewhere new to be. */
    public long targetChanged() { return targetChanged; }

    /** Whether the scaffold is still going up. */
    public boolean building() { return building; }

    /**
     * Walks the cursor to the next block worth digging, clearing fluids it meets on the way, and
     * answers where it stopped. Null means this tick found nothing: the layer ran out of budget,
     * the box is dug, or the cursor is waiting on a chunk nobody is loading.
     */
    private @Nullable BlockPos findTarget(ServerLevel level) {
        for (int step = 0; step < SKIPS_PER_TICK; step++) {
            if (layer < level.getMinY()) {
                status = QuarryStatus.DONE;
                return null;
            }
            if (cursor >= area.layerSize()) {
                cursor = 0;
                layer--;
                markChanged();
                continue;
            }
            BlockPos pos = area.posAt(cursor, layer);
            if (!level.hasChunkAt(pos)) {
                status = QuarryStatus.UNLOADED;
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (isMineable(level, pos, state)) {
                target = pos;
                progress = 0;
                sendToClients();
                return pos;
            }
            // Water and lava are taken away rather than dug, so the hole does not fill in behind us.
            if (!state.getFluidState().isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            cursor++;
        }
        status = QuarryStatus.MINING;
        return null;
    }

    /**
     * Whether the quarry digs this block: not air, not a fluid, breakable at all, inside the
     * border, and neither a marker nor anything holding a block entity — a chest, a spawner or a
     * machine is left where it stands rather than voided.
     */
    private boolean isMineable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || state.is(ModBlocks.MINING_MARKER.get())) return false;
        if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        return state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * Breaks the block and stows what it drops, or answers false and leaves it standing when the
     * buffer cannot take all of it.
     */
    private boolean breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, TOOL);
        if (!fits(drops)) return false;
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (ItemStack drop : drops) store(drop);
        return true;
    }

    /** Whether the buffer takes all of {@code drops} at once; the real slots are not touched. */
    private boolean fits(List<ItemStack> drops) {
        if (drops.isEmpty()) return true;
        ItemStack[] copy = new ItemStack[INVENTORY_SIZE];
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) copy[slot] = items.get(slot).copy();
        for (ItemStack drop : drops) {
            int left = drop.getCount();
            int limit = Math.min(getMaxStackSize(), drop.getMaxStackSize());
            for (int slot = 0; slot < INVENTORY_SIZE && left > 0; slot++) {
                ItemStack held = copy[slot];
                if (held.isEmpty()) {
                    int taken = Math.min(left, limit);
                    copy[slot] = drop.copyWithCount(taken);
                    left -= taken;
                } else if (ItemStack.isSameItemSameComponents(held, drop) && held.getCount() < limit) {
                    int taken = Math.min(left, limit - held.getCount());
                    held.grow(taken);
                    left -= taken;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    /** Puts one drop away; the caller has already made sure it fits. */
    private void store(ItemStack drop) {
        int limit = Math.min(getMaxStackSize(), drop.getMaxStackSize());
        for (int slot = 0; slot < INVENTORY_SIZE && !drop.isEmpty(); slot++) {
            ItemStack held = items.get(slot);
            if (held.isEmpty()) {
                items.set(slot, drop.split(limit));
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(held, drop) || held.getCount() >= limit) continue;
            int taken = Math.min(drop.getCount(), limit - held.getCount());
            held.grow(taken);
            drop.shrink(taken);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        area = QuarryArea.load(input);
        layer = input.getIntOr("Layer", area == null ? 0 : area.topY());
        cursor = Math.max(0, input.getIntOr("Cursor", 0));
        girder = Math.max(0, input.getIntOr(TAG_GIRDER, 0));
        // A quarry loaded without a box has nothing to build yet; one with a box carries on.
        building = area != null && input.getBooleanOr(TAG_BUILDING, true);
        frame = null;
        repair = 0;
        sides.load(input);
        auto.load(input);
        redstone.load(input);
        // The block being dug is looked up again; only whole blocks are ever paid for. The sweep
        // starts over from the top: it carries no state worth keeping.
        target = null;
        progress = 0;
        revisiting = false;
        revisit = null;
        sweepLayer = layer;
        sweepCursor = 0;
        status = area == null ? QuarryStatus.NO_MARKER_BEHIND : building ? QuarryStatus.BUILDING : QuarryStatus.MINING;
        scan = QuarryArea.Result.NO_MARKER_BEHIND;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy.getAmountAsInt());
        if (area != null) {
            area.save(output);
            output.putInt("Layer", layer);
            output.putInt("Cursor", cursor);
            output.putInt(TAG_GIRDER, girder);
            output.putBoolean(TAG_BUILDING, building);
        }
        sides.save(output);
        auto.save(output);
        redstone.save(output);
        upgrades.save(output);
    }

    /** Nothing goes in: the buffer holds what the quarry digs and only hands it out. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return false; }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return sides.allowsItemOutput(side) ? ALL_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return sides.allowsItemOutput(side);
    }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.quarry"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new QuarryMenu(id, inventory, this, upgrades, data);
    }
}
