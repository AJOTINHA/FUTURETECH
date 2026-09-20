package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.block.entity.NetworkCableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The network cable: the shape, the links, the wrench and the facades every cable shares, and
 * nothing flowing through them yet. It has one size, so there is no tier to choose between.
 *
 * <p>What it reaches for is the teleporter, the panel that sets it, the card storage that feeds
 * it and the tesseract that carries the network elsewhere. Every other cable asks the neighbour
 * for the capability of what it carries; this one carries nothing yet, so it names the blocks
 * instead, and every other machine beside it stays a neighbour it never touches.
 */
public final class NetworkCableBlock extends AbstractCableBlock {
    public static final MapCodec<NetworkCableBlock> CODEC = simpleCodec(NetworkCableBlock::new);

    public NetworkCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NetworkCableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.NETWORK; }

    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof NetworkCableBlock;
    }

    /**
     * The teleporter, the card storage and the panel that sets it: the blocks this cable has
     * anything to say to. A pad or a storage takes a cable on any face, but a panel is a plate
     * with a front and a back, and only its back — the face it is mounted on — has anywhere for a
     * cable to go.
     */
    @Override
    protected boolean linksTo(LevelReader level, BlockPos neighbourPos, BlockState neighbour, Direction side) {
        if (neighbour.getBlock() instanceof TeleporterBlock || neighbour.getBlock() instanceof StorageCardsBlock
                || neighbour.getBlock() instanceof TesseractBlock) return true;
        // The panel lies `side` of this cable, so the cable is on the panel's `side.getOpposite()`
        // face; that is its back when the screen looks the other way, which is `side`.
        return neighbour.getBlock() instanceof NetworkPanelBlock
                && neighbour.getValue(NetworkPanelBlock.FACING) == side;
    }

    /** Nothing is carried yet, so no neighbour is asked for a capability. */
    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return false;
    }

    /**
     * The collar on the teleporter's side is the link showing, not a connector to open: this cable
     * has no mode, priority, filter or colour to set yet. The click falls through instead of
     * opening a screen with nothing on it.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    protected BlockEntityType<NetworkCableBlockEntity> blockEntityType() {
        return ModBlockEntities.NETWORK_CABLE.get();
    }

    /** No network to run, so the cable is not ticked at all rather than ticked into a no-op. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkCableBlockEntity(pos, state);
    }
}
