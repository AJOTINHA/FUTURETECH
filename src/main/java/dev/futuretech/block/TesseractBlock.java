package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.block.entity.TesseractBlockEntity;
import dev.futuretech.transfer.TesseractPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The tesseract: the battery's steel frame around a cube of the End, with no faces to configure —
 * everything pushed into any side goes to the other tesseracts on its channel. The channel is
 * picked on its screen from the ones the world's players have made; two tesseracts on the same
 * one are one. The block itself only opens that screen; the moving is the block entity's, and the
 * walk the cables'.
 */
public final class TesseractBlock extends BaseEntityBlock {
    public static final MapCodec<TesseractBlock> CODEC = simpleCodec(TesseractBlock::new);
    /**
     * Whether the tesseract is on a channel. Off it, there is nothing to link to: the cables do
     * not join it and it offers no handler, so a fresh one is just a frame until a channel is
     * picked. In the state, rather than the block entity, so the cables can see it when they
     * work out their links, and relink the moment it changes.
     */
    public static final BooleanProperty CHANNEL = BooleanProperty.create("channel");

    public TesseractBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL);
    }

    @Override
    protected MapCodec<TesseractBlock> codec() { return CODEC; }

    /** The frame is the model; the cube inside is the renderer's. */
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    /** An open frame: light gets through, and the cube inside is seen from every side. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) { return true; }

    /** Redstone is sampled on change rather than polled every tick, and the client hears it for the screen. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TesseractBlockEntity tesseract) {
            boolean was = tesseract.redstoneControl().isPowered();
            RedstoneControl.sample(level, pos);
            if (tesseract.redstoneControl().isPowered() != was) tesseract.sync();
        }
    }

    /** Like the panel: a cable placed before this block never had a reason to point at one. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) state.updateNeighbourShapes(level, pos, UPDATE_ALL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TesseractBlockEntity(pos, state);
    }

    /** The server opens the screen, with the tesseract's channel and the world's list of channels in the packet. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof TesseractBlockEntity tesseract) {
            TesseractPayloads.open(serverPlayer, tesseract);
        }
        return InteractionResult.SUCCESS;
    }
}
