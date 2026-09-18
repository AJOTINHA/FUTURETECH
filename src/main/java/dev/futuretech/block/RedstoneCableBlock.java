package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import dev.futuretech.FutureTech;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.block.entity.RedstoneCableBlockEntity;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The redstone cable: a wire in the cable's shape, with the links, the wrench, the facades and
 * the connectors every cable has. A connector set to insert reads the signal beside it into the
 * cable and a connector set to extract gives one out, on the colour and channel the connector is
 * on — the words are the cable's, since the signal lives in it, not the neighbour's; the
 * {@link dev.futuretech.redstone.RedstoneCableNetwork} carries the lines. It has one size, so
 * there is no tier to choose between, and it never ticks: like redstone, it is told.
 *
 * <p>It links to what redstone dust would join — dust, a lever, a torch, a repeater, whatever
 * says it takes a wire — to the mod's machines, which answer to a signal, and to the blocks the
 * {@link #LINKS} tag names: the lamps, pistons and doors that take a signal without saying so.
 * A block of stone or a chest beside it stays a neighbour, so the cable does not grow a collar on
 * every wall it runs along — unless the player joins them with the wrench, which this kind allows.
 */
public final class RedstoneCableBlock extends AbstractCableBlock {
    public static final MapCodec<RedstoneCableBlock> CODEC = simpleCodec(RedstoneCableBlock::new);
    /** Blocks that take a signal without saying so, which the cable links to on any face. */
    public static final TagKey<Block> LINKS = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "redstone_cable_links"));

    public RedstoneCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RedstoneCableBlock> codec() {
        return CODEC;
    }

    @Override
    public CableKind kind() { return CableKind.REDSTONE; }

    @Override
    public boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof RedstoneCableBlock;
    }

    /**
     * Whatever dust would join on that face, asked the way dust asks: {@code side} runs from the
     * cable to the block, the way the dust's own direction runs from the dust to it. Then the
     * machines, and the tag.
     */
    @Override
    protected boolean linksTo(LevelReader level, BlockPos neighbourPos, BlockState neighbour, Direction side) {
        return neighbour.canRedstoneConnectTo(level, neighbourPos, side)
                || neighbour.getBlock() instanceof SideConfigurableBlock
                || neighbour.is(LINKS);
    }

    /** Redstone is not a capability; every link is decided by what the block is, above. */
    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return false;
    }

    /** The links are decided by what the block is, so the player may overrule them with the wrench. */
    @Override
    protected boolean forces() { return true; }

    @Override
    protected BlockEntityType<RedstoneCableBlockEntity> blockEntityType() {
        return ModBlockEntities.REDSTONE_CABLE.get();
    }

    /** Nothing to run each tick: the cable reads when told and answers when asked. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        return null;
    }

    /** The one tick a cable books: the walk that puts it on a network, a tick after the change that called for it. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable) cable.rebuild();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneCableBlockEntity(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    /**
     * Dust joins the cable where the cable has a link: {@code direction} runs from the dust to
     * the cable, so the cable's face towards the dust is the opposite one.
     */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && state.getValue(PROPERTY_BY_DIRECTION.get(direction.getOpposite()));
    }

    /**
     * What the connector facing the asker gives out. {@code direction} runs from the asker to
     * the cable, the way every block is asked, so the connector is on the opposite face.
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable
                ? cable.emitted(direction.getOpposite()) : 0;
    }

    /**
     * Strong power, like a repeater's, so a block of stone the connector gives into passes it on;
     * a connector switched to weak gives none here, and only wakes the block beside it.
     */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable
                ? cable.emittedStrongly(direction.getOpposite()) : 0;
    }

    /**
     * A container beside the cable changed what it holds: that is what a comparator hears, and
     * a connector with its sensor on reads again the way the comparator would.
     */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable) {
            cable.samplePower(serverLevel);
        }
    }
}
