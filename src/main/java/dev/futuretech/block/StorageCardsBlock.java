package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.StorageCardsBlockEntity;
import dev.futuretech.menu.StorageCardsMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/**
 * The card storage: a cabinet of teleport cards that every teleporter on its network cables can
 * send to, so a pad needs no card of its own for a place the storage already knows. It does
 * nothing on its own — no energy, no ticking; it holds cards, and the pads and the panel read
 * them off it when they need to. Its level is how many it holds: four at MK1, doubling each level.
 */
public final class StorageCardsBlock extends BaseEntityBlock {
    public static final MapCodec<StorageCardsBlock> CODEC = simpleCodec(StorageCardsBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StorageCardsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLevel.MK, 1));
    }

    @Override
    protected MapCodec<StorageCardsBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MachineLevel.MK);
    }

    /** The door faces whoever placed it, the way a furnace does. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Like the panel: a cable placed before this block never had a reason to point at one. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) state.updateNeighbourShapes(level, pos, UPDATE_ALL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCardsBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StorageCardsBlockEntity storage) {
            player.openMenu(storage, buffer -> StorageCardsMenu.writeOpeningData(buffer, storage));
        }
        return InteractionResult.SUCCESS;
    }
}
