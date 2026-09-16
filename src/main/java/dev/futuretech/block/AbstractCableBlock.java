package dev.futuretech.block;

import dev.futuretech.perf.TickProfiler;
import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.menu.CableConnectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Shape, connections and connector menu shared by every kind of cable. The six connection
 * properties only drive the model and shape; what flows is handled by each kind's network.
 * Cables of different kinds never join: an item cable beside an energy cable is just a neighbour.
 */
public abstract class AbstractCableBlock extends PipeBlock implements EntityBlock {
    private static final float SIZE = 8.0F;
    /**
     * One shape per pair of run connections and collars, built on first use and handed out by the
     * same instance from then on. The chunk mesher asks whether a block is a full cube once per
     * quad, and vanilla answers that from a cache keyed by shape identity: a fresh {@code Shapes.or}
     * per call misses it every time and redoes the 3D join, which was ~100 µs per quad of cable.
     * That is also why the block does not declare {@code dynamicShape()}: the state cache then
     * holds the full-cube answer, and the mesher never asks for the shape at all.
     */
    private final VoxelShape[] shapesWithConnectors = new VoxelShape[64 * 64];

    protected AbstractCableBlock(Properties properties) {
        super(SIZE, properties);
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    public abstract CableKind kind();

    /** Whether {@code neighbour} is a cable of this same kind, which the run continues through. */
    public abstract boolean joins(BlockState neighbour);

    /** Whether the block at {@code neighbour} offers this cable's resource on {@code face}. */
    protected abstract boolean offers(Level level, BlockPos neighbour, Direction face);

    protected abstract BlockEntityType<? extends AbstractCableBlockEntity> blockEntityType();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape cable = super.getShape(state, level, pos, context);
        int connectors = CableConnector.mask(level, pos, state);
        if (connectors == 0) return cable;
        int slot = connectionMask(state) << 6 | connectors;
        VoxelShape shape = shapesWithConnectors[slot];
        // Two threads may build the same slot at once; both results are equal, so nothing is lost.
        if (shape == null) shapesWithConnectors[slot] = shape = Shapes.or(cable, CableConnector.shape(connectors));
        return shape;
    }

    /** The six run connections as bits, {@code Direction.ordinal()} order, like the collar mask. */
    private static int connectionMask(BlockState state) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(side))) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /**
     * Cables join others of their kind and any block that offers their resource on the touching
     * face, unless the wrench cut that side, on this cable or on the cable beyond.
     */
    private boolean connectsTo(LevelReader level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        if (isCut(level, pos, side) || isCut(level, neighbour, side.getOpposite())) return false;
        if (joins(level.getBlockState(neighbour))) return true;
        return level instanceof Level realLevel && offers(realLevel, neighbour, side.getOpposite());
    }

    private static boolean isCut(LevelReader level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable && cable.isCut(side);
    }

    /**
     * The wrench on a cable: cuts the link on the side the player hit, or restores a cut one. A
     * side that is neither linked nor cut is left alone, so the click falls through to the block.
     * Both cables of a run carry the cut, so either end restores it; both rebuild their networks.
     */
    public InteractionResult toggleLink(Level level, BlockPos pos, BlockState state, Vec3 hitLocation, Direction clickedFace) {
        Direction side = hitSide(pos, hitLocation, clickedFace);
        boolean linked = state.getValue(PROPERTY_BY_DIRECTION.get(side));
        BlockPos neighbourPos = pos.relative(side);
        AbstractCableBlockEntity cable = level.getBlockEntity(pos) instanceof AbstractCableBlockEntity own ? own : null;
        AbstractCableBlockEntity beyond = level.getBlockEntity(neighbourPos) instanceof AbstractCableBlockEntity other
                && other.kind() == kind() ? other : null;
        if (cable == null) return InteractionResult.PASS;
        boolean cut = cable.isCut(side) || (beyond != null && beyond.isCut(side.getOpposite()));
        if (!linked && !cut) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        cable.setCut(side, linked);
        if (beyond != null) beyond.setCut(side.getOpposite(), linked);
        level.setBlock(pos, state.setValue(PROPERTY_BY_DIRECTION.get(side), !linked), UPDATE_ALL);
        BlockState beyondState = level.getBlockState(neighbourPos);
        if (beyondState.getBlock() instanceof AbstractCableBlock block && block.kind() == kind()) {
            level.setBlock(neighbourPos, beyondState.setValue(PROPERTY_BY_DIRECTION.get(side.getOpposite()), !linked), UPDATE_ALL);
        }
        cable.invalidateNetwork();
        if (beyond != null) beyond.invalidateNetwork();
        level.playSound(null, pos, linked ? SoundEvents.CHAIN_BREAK : SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /**
     * The side a click on the cable means: the arm or collar it landed on, told by the axis the
     * hit point is farthest from the centre along; a hit on the core itself means the face hit.
     */
    static Direction hitSide(BlockPos pos, Vec3 hitLocation, Direction clickedFace) {
        Vec3 local = hitLocation.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double x = Math.abs(local.x), y = Math.abs(local.y), z = Math.abs(local.z);
        double farthest = Math.max(x, Math.max(y, z));
        // The core spans a quarter block each way from the centre; only the arms reach past it.
        if (farthest <= 0.25 + 1.0E-4) return clickedFace;
        if (farthest == x) return local.x > 0 ? Direction.EAST : Direction.WEST;
        if (farthest == y) return local.y > 0 ? Direction.UP : Direction.DOWN;
        return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side),
                    connectsTo(context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour),
                connectsTo(level, pos, directionToNeighbour));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) return;
        if (oldState.is(this)) {
            // Same cable, new links: a machine was placed beside it, or taken away. The network
            // captured its endpoints when it was discovered, so one that is not rebuilt keeps
            // ignoring the machine that just appeared on this face.
            if (connectionMask(state) != connectionMask(oldState)
                    && level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable) {
                cable.invalidateNetwork();
            }
            return;
        }
        // Neighbouring networks must absorb the new cable, so they rebuild on their next tick.
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            if (joins(level.getBlockState(neighbour))
                    && level.getBlockEntity(neighbour) instanceof AbstractCableBlockEntity cable) {
                cable.invalidateNetwork();
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        int connectors = CableConnector.mask(level, pos, state);
        if (connectors == 0) return InteractionResult.PASS;
        Direction side = clickedConnector(connectors, pos, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, viewer) -> CableConnectorMenu.opening(id, inventory, cable, side),
                    Component.translatable("gui.futuretech.cable.connector",
                            Component.translatable("gui.futuretech.direction." + side.getName()))),
                    buffer -> CableConnectorMenu.writeOpeningData(buffer, cable, side));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Which collar the click belongs to. A cable with one connector opens it from anywhere on the
     * block, so the player does not have to find the collar; with several, the hit point decides,
     * falling back to the nearest one when the click landed on bare cable between them.
     */
    private static Direction clickedConnector(int connectors, BlockPos pos, BlockHitResult hit) {
        Direction single = Direction.values()[Integer.numberOfTrailingZeros(connectors)];
        if (Integer.bitCount(connectors) == 1) return single;
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction nearest = single;
        double nearestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            if ((connectors & (1 << side.ordinal())) == 0) continue;
            AABB collar = CableConnector.shape(1 << side.ordinal()).bounds();
            if (collar.inflate(1.0E-4).contains(local)) return side;
            double distance = collar.getCenter().distanceToSqr(local);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = side;
            }
        }
        return nearest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != blockEntityType()) return null;
        return (BlockEntityTicker<T>) TickProfiler.<AbstractCableBlockEntity>wrap(
                (tickLevel, pos, tickState, cable) -> cable.serverTick());
    }
}
