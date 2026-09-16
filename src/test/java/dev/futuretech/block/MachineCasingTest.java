package dev.futuretech.block;

import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class MachineCasingTest {
    @Test
    void openingsRemainClearForCollisionAndTargetingOnEveryAxis(MinecraftServer server) {
        var state = ModBlocks.MACHINE_CASING.get().defaultBlockState();
        var level = server.overworld();
        var collision = state.getCollisionShape(level, BlockPos.ZERO);
        assertFalse(state.canOcclude(), "Neighbors must remain visible through the openings");
        for (var tunnel : new net.minecraft.world.phys.shapes.VoxelShape[]{
                Block.box(0, 5, 5, 16, 11, 11), Block.box(5, 0, 5, 11, 16, 11),
                Block.box(5, 5, 0, 11, 11, 16)}) {
            assertFalse(Shapes.joinIsNotEmpty(collision, tunnel, BooleanOp.AND));
        }
        assertTrue(Shapes.joinIsNotEmpty(collision, Block.box(0, 0, 0, 5, 16, 5), BooleanOp.AND));
        var centre = new Vec3(.5, .5, .5);
        for (var direction : Direction.values()) {
            var offset = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            assertNull(state.getShape(level, BlockPos.ZERO).clip(
                    centre.add(offset.scale(2)), centre.subtract(offset.scale(2)), BlockPos.ZERO));
        }
        assertNotNull(state.getShape(level, BlockPos.ZERO).clip(
                new Vec3(.1, .5, -1), new Vec3(.1, .5, 2), BlockPos.ZERO));
    }
}
