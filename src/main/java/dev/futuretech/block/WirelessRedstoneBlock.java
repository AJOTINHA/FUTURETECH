package dev.futuretech.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import dev.futuretech.redstone.WirelessRedstonePayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The two ends of the wireless redstone, which are one block with two jobs. The transmitter reads
 * the strongest signal touching it and puts that on a frequency; the receiver gives out whatever is
 * on its frequency, wherever it stands and whatever dimension it stands in. Neither takes energy,
 * neither ticks: a plate only does something when the redstone around it changes or when another
 * plate on its frequency says something. Right-clicking either opens the one screen they share,
 * where the frequency is typed.
 *
 * <p>Like the network panel, it is a plate on a face rather than a block in its own right: two
 * pixels of stone against whatever it was mounted on, an obsidian mast out of the middle of them,
 * and over the mast the hedron that turns there. The receiver carries a dish on an arm off its
 * mast; the dish and the hedron are drawn by {@link dev.futuretech.client.WirelessRedstoneRenderer},
 * since neither is made of boxes. It mounts on any of the six faces.
 */
public final class WirelessRedstoneBlock extends BaseEntityBlock {
    public static final MapCodec<WirelessRedstoneBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(block -> block.kind.name()),
            propertiesCodec()
    ).apply(instance, (kind, properties) -> new WirelessRedstoneBlock(Kind.valueOf(kind), properties)));

    /** Which way the plate looks; the back of it is against what it is mounted on. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Whether there is a signal on the plate right now: the lamp, and the light in the crystal. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /**
     * Quarter turns of the plate on the face it hangs on, so its front — the side the lamp is on
     * and the dish opens towards — can be pointed at whatever the plate is wired to. It is set by
     * where the player stands when placing, the way WR-CBE's own part turns.
     *
     * <p>Only a plate on the floor or the ceiling turns. A blockstate turns a model by an x and
     * then a y, which reaches sixteen of the twenty-four ways round a cube, and rolling a plate on
     * a wall is one of the eight it cannot reach; a wall plate keeps its dish pointing up.
     */
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);

    /**
     * The parts of the plate, in pixels, given the way the model draws them: lying on the floor,
     * looking up. The plate is the whole face, the mast stands off the middle of it towards the
     * back, and the receiver's dish leans out over the plate on its arm — a box around what the
     * dish and the arm reach, because the dish is what a player aims at.
     */
    private static final double[] PLATE = {0, 0, 0, 16, 2, 16};
    private static final double[] TRANSMITTER_MAST = {7, 2, 4, 9, 10, 6};
    private static final double[] RECEIVER_MAST = {7, 2, 4, 9, 9, 6};
    private static final double[] RECEIVER_DISH = {3.2, 5.6, 2.2, 12.8, 13.2, 9.7};

    private static final VoxelShape[][] TRANSMITTER_SHAPES = shapes(PLATE, TRANSMITTER_MAST);
    private static final VoxelShape[][] RECEIVER_SHAPES = shapes(PLATE, RECEIVER_MAST, RECEIVER_DISH);

    public final Kind kind;

    /** The two plates: same block, same block entity, same screen; only the head and the job differ. */
    public enum Kind {
        /** Reads the redstone around it and puts it on the frequency. */
        TRANSMITTER,
        /** Gives out what is on the frequency. */
        RECEIVER
    }

    public WirelessRedstoneBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    /** One shape per face and turn, built once: the boxes of the model, turned the way it is drawn. */
    private static VoxelShape[][] shapes(double[]... boxes) {
        VoxelShape[][] shapes = new VoxelShape[Direction.values().length][SPIN.getPossibleValues().size()];
        for (Direction facing : Direction.values()) {
            for (int spin : SPIN.getPossibleValues()) {
                VoxelShape shape = Shapes.empty();
                for (double[] box : boxes) shape = Shapes.or(shape, turn(angles(facing, spin), box));
                shapes[facing.ordinal()][spin] = shape;
            }
        }
        return shapes;
    }

    /**
     * The turn the blockstate gives the model for a face and a quarter turn on it: an x and then a
     * y, in the model's own clockwise. One table serves the shapes here, the blockstate written by
     * {@code tools/generate_wireless_redstone.py} and the pose in the renderer, so the box, the
     * model and the dish drawn over it can never point three different ways.
     */
    public static int[] angles(Direction facing, int spin) {
        return switch (facing) {
            case UP -> new int[]{0, 90 * spin};
            case DOWN -> new int[]{180, 90 * spin};
            // A wall plate does not turn: those ways round are the ones a blockstate cannot say.
            case NORTH -> new int[]{90, 0};
            case EAST -> new int[]{90, 90};
            case SOUTH -> new int[]{90, 180};
            case WEST -> new int[]{90, 270};
        };
    }

    /** A box of the model under those angles: both corners are turned and squared up again. */
    private static VoxelShape turn(int[] angles, double[] box) {
        double[] one = turn(angles, box[0], box[1], box[2]);
        double[] two = turn(angles, box[3], box[4], box[5]);
        return Block.box(Math.min(one[0], two[0]), Math.min(one[1], two[1]), Math.min(one[2], two[2]),
                Math.max(one[0], two[0]), Math.max(one[1], two[1]), Math.max(one[2], two[2]));
    }

    /**
     * One corner, from the frame the model is drawn in — the plate on the floor, looking up — to
     * the one it stands in. Measured from the middle of the block, turned a quarter at a time the
     * way a blockstate turns a model, and put back.
     */
    private static double[] turn(int[] angles, double x, double y, double z) {
        double a = x - 8;
        double b = y - 8;
        double c = z - 8;
        for (int quarter = 0; quarter < angles[0] / 90; quarter++) {
            double turned = c;
            c = -b;
            b = turned;
        }
        for (int quarter = 0; quarter < angles[1] / 90; quarter++) {
            double turned = -c;
            c = a;
            a = turned;
        }
        return new double[]{a + 8, b + 8, c + 8};
    }

    /**
     * The way the plate's front points: the side its lamp is on and its dish opens towards, which
     * is the one side its redstone uses. On the floor it starts out looking south and turns
     * clockwise from there; on the ceiling it starts looking north, because getting there turns
     * the model over; on a wall it looks up, which is where that plate's dish points.
     */
    public static Direction front(Direction facing, int spin) {
        if (facing.getAxis().isHorizontal()) return Direction.UP;
        Direction front = facing == Direction.DOWN ? Direction.NORTH : Direction.SOUTH;
        for (int quarter = 0; quarter < spin; quarter++) front = front.getClockWise();
        return front;
    }

    /** The front of the plate in this state. */
    public static Direction front(BlockState state) {
        return front(state.getValue(FACING), state.getValue(SPIN));
    }

    /** The turn that points the front of a floor or ceiling plate at {@code wanted}. */
    private static int spinFor(Direction facing, Direction wanted) {
        for (int spin : SPIN.getPossibleValues()) {
            if (front(facing, spin) == wanted) return spin;
        }
        return 0;
    }

    @Override
    protected MapCodec<WirelessRedstoneBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, SPIN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[][] shapes = kind == Kind.TRANSMITTER ? TRANSMITTER_SHAPES : RECEIVER_SHAPES;
        return shapes[state.getValue(FACING).ordinal()][state.getValue(SPIN)];
    }

    /**
     * Mounts on the face that was clicked, looking away from it. On the floor and the ceiling it
     * also turns on that face so its front — the lamp and the dish — faces the player who placed
     * it, which is the side they were wiring from; a wall plate has only the one way up.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        int spin = facing.getAxis().isVertical()
                ? spinFor(facing, context.getHorizontalDirection().getOpposite()) : 0;
        return defaultBlockState().setValue(FACING, facing).setValue(SPIN, spin);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal()) return state.setValue(FACING, rotation.rotate(facing));
        // On the floor and the ceiling the plate keeps its face and turns on it instead.
        return state.setValue(SPIN, spinFor(facing, rotation.rotate(front(facing, state.getValue(SPIN)))));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal()) return state.rotate(mirror.getRotation(facing));
        return state.rotate(mirror.getRotation(front(facing, state.getValue(SPIN))));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessRedstoneBlockEntity(pos, state);
    }

    /** The screen is the same for both: a frequency to type, opened by the server on the block clicked. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WirelessRedstoneBlockEntity plate) {
            WirelessRedstonePayloads.open(serverPlayer, plate);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The transmitter reads the signal on change rather than every tick, the way the machines
     * sample their redstone mode. The receiver has nothing to read: what it gives out is the
     * frequency's, and it hears about that from the other end.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (kind != Kind.TRANSMITTER || level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof WirelessRedstoneBlockEntity plate) plate.sample();
    }

    @Override
    protected boolean isSignalSource(BlockState state) { return kind == Kind.RECEIVER; }

    /**
     * Redstone joins the plate on one side only: the front, where the lamp is. Dust laid at its
     * back or beside it turns away rather than running into it, the way dust turns away from the
     * side of a repeater.
     *
     * <p>{@code direction} is the way from the dust to this plate, so the dust that may join is
     * the dust standing where the front points.
     */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction == front(state).getOpposite();
    }

    /** What the receiver has for whoever is asking; a transmitter has nothing to give. */
    @Override
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
        if (kind != Kind.RECEIVER) return 0;
        return level.getBlockEntity(pos) instanceof WirelessRedstoneBlockEntity plate ? plate.power() : 0;
    }

    /** And it only hands it to what stands on its front, the way a repeater only feeds its own. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == front(state).getOpposite() ? ownSignal(state, level, pos) : 0;
    }

    /** Strong power goes the same way, so a solid block on the front passes the signal on. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** A receiver taken down while it was giving out a signal leaves blocks that still believe in it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (kind == Kind.RECEIVER && !movedByPiston && state.getValue(LIT)) updateNeighbours(level, state, pos);
    }

    /**
     * Tells the blocks around a receiver, and the one on its front, that its signal changed. The
     * second is what carries the strong power on: without it a repeater behind a solid block on
     * the front never hears, the same way it would not hear a lever.
     */
    public static void updateNeighbours(Level level, BlockState state, BlockPos pos) {
        Direction front = front(state);
        // The orientation only matters to the experimental redstone; up has to be across the front.
        Direction up = front.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front, up);
        level.updateNeighborsAt(pos, state.getBlock(), orientation);
        level.updateNeighborsAt(pos.relative(front), state.getBlock(), orientation);
    }
}
