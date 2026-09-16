package dev.futuretech.block;

import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.block.entity.CableBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The wrench cutting a cable's link on one side, and the cut reaching everything that reads it. */
@ExtendWith(EphemeralTestServerProvider.class)
class CableCutTest {
    private static final BlockPos A = BlockPos.ZERO;
    private static final BlockPos B = A.east();

    private static BlockState cable() { return ModBlocks.CABLE_MK1.get().defaultBlockState(); }

    /** A level of two cables side by side, each with its block entity, and nothing else. */
    private static LevelReader level(Map<BlockPos, BlockState> states, Map<BlockPos, AbstractCableBlockEntity> entities) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> entities.get(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static boolean linked(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        BlockPos beyond = pos.relative(side);
        return state.updateShape(level, null, pos, side, beyond, level.getBlockState(beyond), RandomSource.create())
                .getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    @Test
    void aCutOnEitherCableBreaksTheLinkForBoth(MinecraftServer server) {
        var states = new HashMap<BlockPos, BlockState>();
        var entities = new HashMap<BlockPos, AbstractCableBlockEntity>();
        var a = new CableBlockEntity(A, cable());
        var b = new CableBlockEntity(B, cable());
        states.put(A, cable().setValue(AbstractCableBlock.EAST, true));
        states.put(B, cable().setValue(AbstractCableBlock.WEST, true));
        entities.put(A, a);
        entities.put(B, b);
        var level = level(states, entities);
        assertTrue(linked(level, A, states.get(A), Direction.EAST));
        assertTrue(linked(level, B, states.get(B), Direction.WEST));

        a.setCut(Direction.EAST, true);
        assertTrue(a.isCut(Direction.EAST));
        assertFalse(a.isCut(Direction.WEST), "Only the cut side");
        assertFalse(linked(level, A, states.get(A), Direction.EAST));
        assertFalse(linked(level, B, states.get(B), Direction.WEST), "The cable beyond honours the cut too");
        assertTrue(linked(level, A, states.get(A), Direction.WEST) == false, "A bare side stays unlinked");

        a.setCut(Direction.EAST, false);
        assertTrue(linked(level, A, states.get(A), Direction.EAST));
        assertTrue(linked(level, B, states.get(B), Direction.WEST));
    }

    @Test
    void theCutReachesTheClientWithTheUpdateTag(MinecraftServer server) {
        var a = new CableBlockEntity(A, cable());
        a.setCut(Direction.UP, true);
        a.setCut(Direction.NORTH, true);
        var tag = a.getUpdateTag(server.registryAccess());
        var b = new CableBlockEntity(A, cable());
        b.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));
        for (Direction side : Direction.values()) {
            assertEquals(side == Direction.UP || side == Direction.NORTH, b.isCut(side), side.toString());
        }
        assertFalse(new CableBlockEntity(A, cable()).getUpdateTag(server.registryAccess()).contains("Cut"),
                "An uncut cable sends nothing extra");
    }

    @Test
    void theClickMeansTheArmItLandsOnAndTheFaceOnTheCore(MinecraftServer server) {
        // On the east arm, wherever on it: its top, its side, its end.
        assertEquals(Direction.EAST, AbstractCableBlock.hitSide(A, new Vec3(0.9, 0.75, 0.5), Direction.UP));
        assertEquals(Direction.EAST, AbstractCableBlock.hitSide(A, new Vec3(0.8, 0.5, 0.25), Direction.NORTH));
        assertEquals(Direction.EAST, AbstractCableBlock.hitSide(A, new Vec3(1.0, 0.5, 0.5), Direction.EAST));
        assertEquals(Direction.DOWN, AbstractCableBlock.hitSide(A, new Vec3(0.5, 0.1, 0.6), Direction.WEST));
        assertEquals(Direction.NORTH, AbstractCableBlock.hitSide(A, new Vec3(0.4, 0.4, 0.05), Direction.UP));
        // On the core, the face clicked decides.
        assertEquals(Direction.UP, AbstractCableBlock.hitSide(A, new Vec3(0.5, 0.75, 0.5), Direction.UP));
        assertEquals(Direction.SOUTH, AbstractCableBlock.hitSide(A, new Vec3(0.6, 0.4, 0.75), Direction.SOUTH));
        // The block's own position is taken out first.
        var far = new BlockPos(10, -3, 7);
        assertEquals(Direction.WEST, AbstractCableBlock.hitSide(far, new Vec3(10.05, -2.5, 7.5), Direction.UP));
    }
}
