package dev.futuretech.block;

import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.menu.CableConnectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
        return connectors == 0 ? cable : Shapes.or(cable, CableConnector.shape(connectors));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Cables join others of their kind and any block that offers their resource on the touching face. */
    private boolean connectsTo(LevelReader level, BlockPos pos, Direction side) {
        BlockPos neighbour = pos.relative(side);
        if (joins(level.getBlockState(neighbour))) return true;
        return level instanceof Level realLevel && offers(realLevel, neighbour, side.getOpposite());
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
        if (level.isClientSide() || oldState.is(this)) return;
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
        return (BlockEntityTicker<T>) (BlockEntityTicker<AbstractCableBlockEntity>)
                (tickLevel, pos, tickState, cable) -> cable.serverTick();
    }
}
