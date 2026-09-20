package dev.futuretech.block;

import dev.futuretech.block.entity.WindGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible structure cells share the base's menu and drop one generator when broken. */
public final class WindTurbinePartBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 1, WindTurbineStructure.PARTS);
    public WindTurbinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 1));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = state.getValue(PART), y = WindTurbineStructure.height(part);
        boolean centre = WindTurbineStructure.sideways(part) == 0;
        VoxelShape shape = Shapes.empty();
        if (centre) {
            if (y == 1) shape = Shapes.or(shape,
                    box(5.5,0,5.5,10.5,15,10.5), box(6.25,15,6.25,9.75,16,9.75));
            else if (y == 2) shape = Shapes.or(shape, box(6.25,0,6.25,9.75,16,9.75));
            else if (y == 3) shape = Shapes.or(shape,
                    box(6.25,0,6.25,9.75,5,9.75), box(5.8,1,5.8,10.2,3,10.2),
                    box(3.5,4,4,12.5,12,15));
        }
        return Shapes.rotateHorizontal(shape).get(state.getValue(FACING));
    }
    @Override protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return WindTurbineStructure.isLegacyRotorPart(state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(WindTurbineStructure.base(pos, state)) instanceof WindGeneratorBlockEntity generator) player.openMenu(generator);
        return InteractionResult.SUCCESS;
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        BlockPos base = WindTurbineStructure.base(pos, state);
        return level.getBlockState(base).getCloneItemStack(level, base, includeData);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !WindTurbineStructure.isLegacyRotorPart(state)) {
            BlockPos base = WindTurbineStructure.base(pos, state);
            if (level.getBlockState(base).is(ModBlocks.WIND_GENERATOR.get())) level.destroyBlock(base, !player.preventsBlockDrops(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (WindTurbineStructure.isLegacyRotorPart(state)) return;
        BlockPos base = WindTurbineStructure.base(pos, state);
        BlockState root = level.getBlockState(base);
        // During rotation, old cells are removed after the base has acquired its new facing.
        if (root.is(ModBlocks.WIND_GENERATOR.get()) && root.getValue(WindGeneratorBlock.FACING) == state.getValue(FACING)) level.destroyBlock(base, true);
    }
}
