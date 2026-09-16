package dev.futuretech.block;

import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
/** The tiers of one kind are separate lines: an opaque item cable and a see-through one never link. */
class CableTierJoinTest {
    @Test
    void opaqueAndSeeThroughCablesOfOneKindStayApart(MinecraftServer server) {
        BlockState opaque = ModBlocks.ITEM_CABLE_OPAQUE.get().defaultBlockState();
        BlockState clear = ModBlocks.ITEM_CABLE.get().defaultBlockState();
        Map<BlockPos, BlockState> states = Map.of(BlockPos.ZERO, opaque, BlockPos.ZERO.east(), clear);
        LevelReader level = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        BlockState updated = opaque.updateShape(level, null, BlockPos.ZERO, Direction.EAST, BlockPos.ZERO.east(), clear, RandomSource.create());
        assertFalse(updated.getValue(AbstractCableBlock.EAST), "opaque does not link to clear");
        BlockState updated2 = clear.updateShape(level, null, BlockPos.ZERO.east(), Direction.WEST, BlockPos.ZERO, opaque, RandomSource.create());
        assertFalse(updated2.getValue(AbstractCableBlock.WEST), "clear does not link to opaque");
        assertFalse(ModBlocks.ITEM_CABLE.get().joins(opaque));
        assertTrue(ModBlocks.ITEM_CABLE.get().joins(clear));
        assertFalse(ModBlocks.FLUID_CABLE.get().joins(ModBlocks.FLUID_CABLE_OPAQUE.get().defaultBlockState()));
    }
}
