package dev.futuretech.block;

import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the network cable touches: its own kind, and the teleporter. Every other machine and every
 * other cable beside it stays a neighbour. The teleporter's side gets a collar like any machine
 * link, and the cable passes a click on, so that collar never opens a screen with nothing on it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NetworkCableJoinTest {
    /** A level of nothing but the block states given; the cable asks for no more than these. */
    private static Object level(Map<BlockPos, BlockState> states, Class<?> face) {
        return Proxy.newProxyInstance(face.getClassLoader(), new Class<?>[]{face},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BlockState network() { return ModBlocks.NETWORK_CABLE.get().defaultBlockState(); }

    /** The cable's own link on {@code side}, as its shape update works it out against {@code neighbour}. */
    private static boolean links(BlockState neighbour, Direction side) {
        BlockState cable = network();
        BlockPos beyond = BlockPos.ZERO.relative(side);
        var level = (LevelReader) level(Map.of(BlockPos.ZERO, cable, beyond, neighbour), LevelReader.class);
        return cable.updateShape(level, null, BlockPos.ZERO, side, beyond, neighbour, RandomSource.create())
                .getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    @Test
    void linksToItsOwnKindAndToTheTeleporter(MinecraftServer server) {
        NetworkCableBlock block = ModBlocks.NETWORK_CABLE.get();
        assertEquals(CableKind.NETWORK, block.kind());
        assertTrue(block.joins(network()));
        assertTrue(links(network(), Direction.EAST), "two network cables are one run");
        for (Direction side : Direction.values()) {
            assertTrue(links(ModBlocks.TELEPORTER.get().defaultBlockState(), side),
                    "the teleporter is linked to on " + side.getName());
        }
    }

    @Test
    void passesEveryOtherMachineAndEveryOtherCableBy(MinecraftServer server) {
        NetworkCableBlock block = ModBlocks.NETWORK_CABLE.get();
        for (BlockState other : new BlockState[]{
                ModBlocks.CABLE_MK1.get().defaultBlockState(),
                ModBlocks.ITEM_CABLE.get().defaultBlockState(),
                ModBlocks.FLUID_CABLE.get().defaultBlockState(),
                ModBlocks.BATTERY_MK1.get().defaultBlockState(),
                ModBlocks.FLUID_TANK.get().defaultBlockState(),
                ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState(),
                Blocks.CHEST.defaultBlockState()}) {
            assertFalse(block.joins(other), other.toString());
            assertFalse(links(other, Direction.EAST), other.toString());
        }
    }

    /**
     * Surrounded by everything, the cable ends up wearing its collar on the teleporter's face and
     * nowhere else — the run into its own kind is a splice, which never takes one.
     */
    @Test
    void wearsItsCollarOnTheTeleporterAlone(MinecraftServer server) {
        Map<BlockPos, BlockState> states = new HashMap<>(Map.of(
                BlockPos.ZERO.east(), ModBlocks.TELEPORTER.get().defaultBlockState(),
                BlockPos.ZERO.west(), network(),
                BlockPos.ZERO.above(), ModBlocks.CABLE_MK1.get().defaultBlockState(),
                BlockPos.ZERO.below(), Blocks.CHEST.defaultBlockState(),
                BlockPos.ZERO.north(), ModBlocks.FLUID_TANK.get().defaultBlockState()));
        states.put(BlockPos.ZERO, network());
        var reader = (LevelReader) level(states, LevelReader.class);
        BlockState cable = network();
        for (Direction side : Direction.values()) {
            BlockPos beyond = BlockPos.ZERO.relative(side);
            cable = cable.updateShape(reader, null, BlockPos.ZERO, side, beyond,
                    states.getOrDefault(beyond, Blocks.AIR.defaultBlockState()), RandomSource.create());
        }
        states.put(BlockPos.ZERO, cable);
        assertTrue(cable.getValue(AbstractCableBlock.EAST), "linked to the teleporter");
        assertTrue(cable.getValue(AbstractCableBlock.WEST), "the run continues into the network cable");
        assertFalse(cable.getValue(AbstractCableBlock.UP), "the energy cable is another kind");
        assertFalse(cable.getValue(AbstractCableBlock.DOWN), "a chest offers items, which this cable does not carry");
        assertFalse(cable.getValue(AbstractCableBlock.NORTH), "the tank offers fluid, which this cable does not carry");
        assertEquals(1 << Direction.EAST.ordinal(),
                CableConnector.mask((BlockGetter) level(states, BlockGetter.class), BlockPos.ZERO, cable),
                "one collar, on the teleporter");
    }

    /** The collar is the link showing; there is nothing to configure behind it yet. */
    @Test
    void theCollarOpensNothing(MinecraftServer server) {
        BlockState cable = network().setValue(AbstractCableBlock.EAST, true);
        assertEquals(InteractionResult.PASS,
                ModBlocks.NETWORK_CABLE.get().useWithoutItem(cable, null, BlockPos.ZERO, null, null));
    }
}
