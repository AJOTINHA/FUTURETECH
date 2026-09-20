package dev.futuretech.block;

import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Only the central column is reserved; the blades have no placement or collision footprint. */
public final class WindTurbineStructure {
    // Keep the original state IDs readable so existing worlds can shed the old side cells.
    public static final int HEIGHT = 5, PARTS = 10;
    public static final java.util.List<Integer> COLUMN_PARTS = java.util.List.of(1, 3, 6, 9);
    public static int height(int part) { return part == 1 ? 1 : 2 + (part - 2) / 3; }
    public static int sideways(int part) { return part == 1 ? 0 : (part - 2) % 3 - 1; }
    public static BlockPos position(BlockPos base, Direction facing, int part) {
        return base.above(height(part)).relative(facing.getClockWise(), sideways(part));
    }
    public static BlockPos base(BlockPos pos, BlockState state) {
        int part = state.getValue(WindTurbinePartBlock.PART);
        return pos.below(height(part)).relative(state.getValue(WindTurbinePartBlock.FACING).getClockWise(), -sideways(part));
    }
    public static boolean belongsTo(LevelReader level, BlockPos pos, BlockPos base) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WIND_TURBINE_PART.get()) && base(pos, state).equals(base);
    }
    public static boolean isLegacyRotorPart(BlockState state) {
        return state.is(ModBlocks.WIND_TURBINE_PART.get()) && sideways(state.getValue(WindTurbinePartBlock.PART)) != 0;
    }
    /** Retry unloaded neighbours later instead of force-loading their chunks. */
    public static boolean removeLegacyRotorParts(Level level, BlockPos base, Direction facing) {
        boolean complete = true;
        for (int part = 1; part <= PARTS; part++) {
            if (sideways(part) == 0) continue;
            BlockPos pos = position(base, facing, part);
            if (!level.hasChunkAt(pos)) { complete = false; continue; }
            if (isLegacyRotorPart(level.getBlockState(pos)) && belongsTo(level, pos, base)) level.removeBlock(pos, false);
        }
        return complete;
    }
    public static boolean fits(LevelReader level, BlockPos base, Direction facing) {
        if (level.isOutsideBuildHeight(base.above(HEIGHT - 1))) return false;
        for (int part : COLUMN_PARTS) {
            BlockPos pos = position(base, facing, part);
            if (!level.hasChunkAt(pos) || (!level.getBlockState(pos).isAir()
                    && !isLegacyRotorPart(level.getBlockState(pos)) && !belongsTo(level, pos, base))) return false;
        }
        return true;
    }
    public static void remove(Level level, BlockPos base, Direction facing) {
        for (int part = 1; part <= PARTS; part++) {
            BlockPos pos = position(base, facing, part);
            if (belongsTo(level, pos, base)) level.removeBlock(pos, false);
        }
    }
    public static void place(Level level, BlockPos base, Direction facing) {
        for (int part : COLUMN_PARTS) {
            level.setBlock(position(base, facing, part), ModBlocks.WIND_TURBINE_PART.get().defaultBlockState()
                    .setValue(WindTurbinePartBlock.PART, part).setValue(WindTurbinePartBlock.FACING, facing), Block.UPDATE_ALL);
        }
    }
    private WindTurbineStructure() {}
}
