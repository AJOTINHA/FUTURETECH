package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.block.entity.NetworkPanelBlockEntity;
import dev.futuretech.menu.NetworkPanelMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The network panel: a teleporter's screen, mounted wherever the network cable reaches. It lists
 * the destinations of the pad on its cables and sets which one the pad sends to, so the player
 * picks where to go from the wall and then steps on. It does nothing on its own — it has no
 * energy, no inventory and nothing to tick. Everything it shows it finds by walking the cables when
 * someone opens it, and the destination it sets it writes to the pad itself, where the cards live.
 *
 * <p>It is a plate on a face, not a block in its own right: it takes the two pixels of its space
 * that touch whatever it was mounted on, which is exactly where a cable's arm stops, so the two
 * meet with nothing between them. It mounts on any of the six faces, the screen looking out.
 */
public final class NetworkPanelBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkPanelBlock> CODEC = simpleCodec(NetworkPanelBlock::new);
    /** Which way the screen looks; the plate is on the other side, against what it is mounted on. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** How far the plate stands off the face, in pixels, matching the model. */
    private static final double THICKNESS = 2.0 / 16.0;
    private static final VoxelShape[] SHAPES = shapes();

    private static VoxelShape[] shapes() {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length];
        for (Direction facing : Direction.values()) {
            // The plate hugs the face opposite the way it looks: that is the one it is mounted on.
            shapes[facing.ordinal()] = switch (facing) {
                case NORTH -> Shapes.box(0, 0, 1 - THICKNESS, 1, 1, 1);
                case SOUTH -> Shapes.box(0, 0, 0, 1, 1, THICKNESS);
                case WEST -> Shapes.box(1 - THICKNESS, 0, 0, 1, 1, 1);
                case EAST -> Shapes.box(0, 0, 0, THICKNESS, 1, 1);
                case UP -> Shapes.box(0, 0, 0, 1, THICKNESS, 1);
                case DOWN -> Shapes.box(0, 1 - THICKNESS, 0, 1, 1, 1);
            };
        }
        return shapes;
    }

    public NetworkPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Override
    protected MapCodec<NetworkPanelBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Mounts on the face that was clicked, looking away from it — at whoever clicked. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Nudges the cables around it to work their links out again. A cable placed before this block
     * existed carries a state that never had a reason to point at one, and the arm it should grow
     * towards the panel is drawn from that state.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) state.updateNeighbourShapes(level, pos, UPDATE_ALL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkPanelBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NetworkPanelBlockEntity panel) {
            player.openMenu(panel, buffer -> NetworkPanelMenu.writeOpeningData(buffer, panel));
        }
        return InteractionResult.SUCCESS;
    }
}
