package dev.futuretech.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thick metal frame with a six-pixel square opening through each pair of faces. */
public final class MachineCasingBlock extends Block {
    public static final MapCodec<MachineCasingBlock> CODEC = simpleCodec(MachineCasingBlock::new);
    private static final VoxelShape FRAME = createFrame();

    public MachineCasingBlock(Properties properties) {
        super(properties);
    }

    private static VoxelShape createFrame() {
        int[] edges = {0, 5, 11, 16};
        VoxelShape result = Shapes.empty();
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
            // Keep only corners and edge beams, leaving the three crossing tunnels empty.
            if ((x == 1 ? 1 : 0) + (y == 1 ? 1 : 0) + (z == 1 ? 1 : 0) > 1) continue;
            result = Shapes.or(result, box(edges[x], edges[y], edges[z],
                    edges[x + 1], edges[y + 1], edges[z + 1]));
        }
        return result.optimize();
    }

    @Override
    protected MapCodec<MachineCasingBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FRAME;
    }
}
