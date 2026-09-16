package dev.futuretech.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Shared geometry and connection selection for the visible connector and its hitbox. */
public final class CableConnector {
    private CableConnector() {}
    public record Point(float x, float y, float z) {}
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, boolean flange) {}

    /** Depth of the collar's bore. The cable's mouth is closed at its back. */
    public static final float BORE_DEPTH = 2.5F;
    /**
     * Where the bore's walls sit. The cable's cage runs from 4 to 12; the bore is a tenth wider on
     * each side so the cage's faces never share a plane with the collar's, and the plug that fills
     * the bore swallows the cage instead of fighting it for the same pixels.
     */
    public static final float BORE_MIN = 3.9F;
    public static final float BORE_MAX = 12.1F;

    /** North-facing collar, entirely inside the cable block, with an eight-unit opening. */
    public static final List<Box> BOXES = List.of(
            new Box(2.5F,2.5F,0,BORE_MIN,13.5F,1,true),
            new Box(BORE_MAX,2.5F,0,13.5F,13.5F,1,true),
            new Box(BORE_MIN,2.5F,0,BORE_MAX,BORE_MIN,1,true),
            new Box(BORE_MIN,BORE_MAX,0,BORE_MAX,13.5F,1,true),
            new Box(3.25F,3.25F,1,BORE_MIN,12.75F,2.5F,false),
            new Box(BORE_MAX,3.25F,1,12.75F,12.75F,2.5F,false),
            new Box(BORE_MIN,3.25F,1,BORE_MAX,BORE_MIN,2.5F,false),
            new Box(BORE_MIN,BORE_MAX,1,BORE_MAX,12.75F,2.5F,false));

    public static Point rotate(Point p, Direction side) {
        return switch (side) {
            case NORTH -> p;
            case SOUTH -> new Point(16-p.x(),p.y(),16-p.z());
            case EAST -> new Point(16-p.z(),p.y(),p.x());
            case WEST -> new Point(p.z(),p.y(),16-p.x());
            case UP -> new Point(p.x(),16-p.z(),p.y());
            case DOWN -> new Point(p.x(),p.z(),16-p.y());
        };
    }

    /**
     * The server's existing connection bit already establishes compatibility; a collar goes on every
     * connection that is not a run continuing into another cable of the same kind.
     */
    public static boolean connectsToMachine(BlockState cable, BlockState neighbor, Direction side) {
        return cable.getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side))
                && !neighbor.isAir()
                && !(cable.getBlock() instanceof AbstractCableBlock block && block.joins(neighbor));
    }

    public static int mask(BlockGetter level, BlockPos pos, BlockState state) {
        int mask=0;
        for (Direction side : Direction.values()) {
            if (connectsToMachine(state,level.getBlockState(pos.relative(side)),side)) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static final VoxelShape[] SHAPES = createShapes();
    private static VoxelShape[] createShapes() {
        VoxelShape[] sides=new VoxelShape[6];
        for (Direction side : Direction.values()) {
            VoxelShape shape=Shapes.empty();
            for (Box box : BOXES) {
                Point a=rotate(new Point(box.x0(),box.y0(),box.z0()),side);
                Point b=rotate(new Point(box.x1(),box.y1(),box.z1()),side);
                shape=Shapes.or(shape,Block.box(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),
                        Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z())));
            }
            sides[side.ordinal()]=shape.optimize();
        }
        VoxelShape[] shapes=new VoxelShape[64];
        shapes[0]=Shapes.empty();
        for (int mask=1;mask<64;mask++) {
            int bit=Integer.numberOfTrailingZeros(mask);
            shapes[mask]=Shapes.or(shapes[mask & (mask-1)],sides[bit]).optimize();
        }
        return shapes;
    }

    public static VoxelShape shape(int mask) { return SHAPES[mask]; }
}
