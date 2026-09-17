package dev.futuretech.block;

import dev.futuretech.api.facade.CableFacades;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shape, connections and connector menu shared by every kind of cable. The six connection
 * properties only drive the model and shape; what flows is handled by each kind's network.
 * Cables of different kinds never join: an item cable beside an energy cable is just a neighbour.
 * A cable placed in water keeps the water around it, like a chain or a fence does.
 */
public abstract class AbstractCableBlock extends PipeBlock implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
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
    /**
     * The same, for the few cables wearing facades. Kept in a map rather than in an array of every
     * combination, which would be a quarter of a million slots for a case most cables never reach;
     * the identity stays stable, which is what the mesher's cache needs.
     */
    private final Map<Integer, VoxelShape> shapesWithFacades = new ConcurrentHashMap<>();
    /** One panel per face, built once: the facade covers its whole face down to {@link CableFacades#THICKNESS}. */
    private static final VoxelShape[] PANELS = new VoxelShape[Direction.values().length];

    static {
        float depth = CableFacades.THICKNESS / 16.0F;
        for (Direction side : Direction.values()) {
            PANELS[side.ordinal()] = switch (side) {
                case DOWN -> Shapes.box(0, 0, 0, 1, depth, 1);
                case UP -> Shapes.box(0, 1 - depth, 0, 1, 1, 1);
                case NORTH -> Shapes.box(0, 0, 0, 1, 1, depth);
                case SOUTH -> Shapes.box(0, 0, 1 - depth, 1, 1, 1);
                case WEST -> Shapes.box(0, 0, 0, depth, 1, 1);
                case EAST -> Shapes.box(1 - depth, 0, 0, 1, 1, 1);
            };
        }
    }

    protected AbstractCableBlock(Properties properties) {
        super(SIZE, properties);
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    public abstract CableKind kind();

    /** Whether {@code neighbour} is a cable of this same kind, which the run continues through. */
    public abstract boolean joins(BlockState neighbour);

    /** Whether the block at {@code neighbour} offers this cable's resource on {@code face}. */
    protected abstract boolean offers(Level level, BlockPos neighbour, Direction face);

    /**
     * Whether the cable links to {@code neighbour} on its block state alone. Nothing does by
     * default: a kind that carries something asks the block for a capability instead, and that
     * needs a real level. A kind that links by what the block <em>is</em> says so here, and is
     * then answered the same on a level that only reads.
     */
    protected boolean linksTo(BlockState neighbour) { return false; }

    protected abstract BlockEntityType<? extends AbstractCableBlockEntity> blockEntityType();

    /**
     * Whether a cable placed beside the cable at {@code neighbour} starts cut off from it, to be
     * joined by the wrench on purpose. Nothing does by default; a kind whose runs carry something
     * that must not mix says otherwise.
     */
    protected boolean placesCut(LevelReader level, BlockPos neighbour) { return false; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape cable = super.getShape(state, level, pos, context);
        int connectors = CableConnector.mask(level, pos, state);
        int facades = facadeMask(level, pos);
        if (connectors == 0 && facades == 0) return cable;
        int slot = connectionMask(state) << 6 | connectors;
        VoxelShape shape = shapesWithConnectors[slot];
        // Two threads may build the same slot at once; both results are equal, so nothing is lost.
        if (shape == null) shapesWithConnectors[slot] = shape = connectors == 0 ? cable
                : Shapes.or(cable, CableConnector.shape(connectors));
        if (facades == 0) return shape;
        VoxelShape withConnectors = shape;
        return shapesWithFacades.computeIfAbsent(facades << 12 | slot, key -> {
            VoxelShape joined = withConnectors;
            for (Direction side : Direction.values()) {
                if ((facades & (1 << side.ordinal())) != 0) joined = Shapes.or(joined, PANELS[side.ordinal()]);
            }
            return joined.optimize();
        });
    }

    /** The covered faces as bits, in {@code Direction.ordinal()} order, like the collar mask. */
    private static int facadeMask(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable)) return 0;
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (cable.hasFacade(side)) mask |= 1 << side.ordinal();
        }
        return mask;
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
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * Cables join others of their kind and any block that offers their resource on the touching
     * face, unless the wrench cut that side, on this cable or on the cable beyond. Another tier of
     * the same kind is neither: it would offer the resource like a machine, but two lines of item
     * cable that happen to touch are meant to stay two lines.
     */
    private boolean connectsTo(LevelReader level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        if (isCut(level, pos, side) || isCut(level, neighbour, side.getOpposite())) return false;
        BlockState beyond = level.getBlockState(neighbour);
        if (joins(beyond)) return true;
        if (beyond.getBlock() instanceof AbstractCableBlock other && other.kind() == kind()) return false;
        if (linksTo(beyond)) return true;
        return level instanceof Level realLevel && offers(realLevel, neighbour, side.getOpposite());
    }

    private static boolean isCut(LevelReader level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable && cable.isCut(side);
    }

    /**
     * The wrench on a covered cable takes the panel off the face it hit and hands it back, before
     * the wrench gets to the links: a player reaching a hidden cable wants the cover off first.
     */
    public InteractionResult removeFacade(Level level, BlockPos pos, Vec3 hitLocation, Direction clickedFace,
                                          @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable)) return InteractionResult.PASS;
        // A covered face swallows the hit, so the clicked face wins over the arm the point is nearest.
        Direction side = cable.hasFacade(clickedFace) ? clickedFace : hitSide(pos, hitLocation, clickedFace);
        BlockState facade = cable.facade(side);
        if (facade == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        cable.setFacade(side, null);
        var stack = dev.futuretech.item.FacadeItem.of(facade);
        if (player == null || !player.getInventory().add(stack)) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        var sound = facade.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        return InteractionResult.SUCCESS;
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
    public static Direction hitSide(BlockPos pos, Vec3 hitLocation, Direction clickedFace) {
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
        // Placed into water, the cable takes the water with it instead of pushing it out.
        BlockState state = defaultBlockState().setValue(WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
        for (Direction side : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side),
                    connectsTo(context.getLevel(), context.getClickedPos(), side)
                            && !startsCut(context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    /** Whether the run on {@code side} of a cable placed at {@code pos} begins cut, per {@link #placesCut}. */
    private boolean startsCut(LevelReader level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        return joins(level.getBlockState(neighbour)) && placesCut(level, neighbour);
    }

    /**
     * The cut a placement began is written down here, once the block entity exists to carry it:
     * both cables take the cut, so either end restores it, and the neighbour's link back, which
     * its shape update made before it could know, is taken off.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable)) return;
        for (Direction side : Direction.values()) {
            if (!startsCut(level, pos, side)) continue;
            BlockPos neighbourPos = pos.relative(side);
            cable.setCut(side, true);
            if (level.getBlockEntity(neighbourPos) instanceof AbstractCableBlockEntity beyond && beyond.kind() == kind()) {
                beyond.setCut(side.getOpposite(), true);
                beyond.invalidateNetwork();
            }
            BlockState beyondState = level.getBlockState(neighbourPos);
            var back = PROPERTY_BY_DIRECTION.get(side.getOpposite());
            if (beyondState.getValue(back)) level.setBlock(neighbourPos, beyondState.setValue(back, false), UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        // The water in the cable flows on like any water: it gets its tick when a neighbour changes.
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
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

    /** The connectors follow the cable's own signal, sampled here, the way the machines sample theirs. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractCableBlockEntity cable) {
            cable.samplePower(level);
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
