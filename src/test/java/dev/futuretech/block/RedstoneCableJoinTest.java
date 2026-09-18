package dev.futuretech.block;

import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the redstone cable touches: its own kind, whatever dust would join, the mod's machines
 * and the blocks the links tag names. A wall of stone, a chest and every other cable stay
 * neighbours. What it links to it wears a collar on, and dust joins it back on that face.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RedstoneCableJoinTest {
    /** A level of nothing but the block states given; the cable asks for no more than these. */
    private static Object level(Map<BlockPos, BlockState> states, Class<?> face) {
        return Proxy.newProxyInstance(face.getClassLoader(), new Class<?>[]{face},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BlockState cable() { return ModBlocks.REDSTONE_CABLE.get().defaultBlockState(); }

    /** The cable's own link on {@code side}, as its shape update works it out against {@code neighbour}. */
    private static boolean links(BlockState neighbour, Direction side) {
        BlockState cable = cable();
        BlockPos beyond = BlockPos.ZERO.relative(side);
        var level = (LevelReader) level(Map.of(BlockPos.ZERO, cable, beyond, neighbour), LevelReader.class);
        return cable.updateShape(level, null, BlockPos.ZERO, side, beyond, neighbour, RandomSource.create())
                .getValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    @Test
    void linksToItsOwnKindAndToWhatDustWouldJoin(MinecraftServer server) {
        RedstoneCableBlock block = ModBlocks.REDSTONE_CABLE.get();
        assertEquals(CableKind.REDSTONE, block.kind());
        assertTrue(block.joins(cable()));
        assertTrue(links(cable(), Direction.EAST), "two redstone cables are one run");
        for (Direction side : Direction.values()) {
            assertTrue(links(Blocks.REDSTONE_WIRE.defaultBlockState(), side), "dust, on " + side.getName());
            assertTrue(links(Blocks.LEVER.defaultBlockState(), side), "a lever, on " + side.getName());
            assertTrue(links(Blocks.REDSTONE_TORCH.defaultBlockState(), side), "a torch, on " + side.getName());
            assertTrue(links(Blocks.REDSTONE_BLOCK.defaultBlockState(), side), "a block of redstone, on " + side.getName());
        }
    }

    /** A repeater takes a wire at its two ends and turns one away at its sides, and the cable follows. */
    @Test
    void linksToARepeaterAtItsEndsAlone(MinecraftServer server) {
        BlockState repeater = Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.NORTH);
        assertTrue(links(repeater, Direction.NORTH), "its input end");
        assertTrue(links(repeater, Direction.SOUTH), "its output end");
        assertFalse(links(repeater, Direction.EAST), "not its side");
        assertFalse(links(repeater, Direction.WEST), "not its side");
    }

    /** The machines answer to a signal and say nothing about wires; the cable links to them all the same. */
    @Test
    void linksToTheMachinesAndToTheTaggedBlocks(MinecraftServer server) {
        for (BlockState taker : new BlockState[]{
                ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState(),
                ModBlocks.BATTERY_MK1.get().defaultBlockState(),
                ModBlocks.TELEPORTER.get().defaultBlockState(),
                Blocks.REDSTONE_LAMP.defaultBlockState(),
                Blocks.PISTON.defaultBlockState(),
                Blocks.IRON_DOOR.defaultBlockState(),
                Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.DISPENSER.defaultBlockState(),
                Blocks.HOPPER.defaultBlockState(),
                Blocks.COPPER_BULB.weathering().unaffected().defaultBlockState()}) {
            for (Direction side : Direction.values()) {
                assertTrue(links(taker, side), taker + " on " + side.getName());
            }
        }
    }

    @Test
    void passesWallsChestsAndEveryOtherCableBy(MinecraftServer server) {
        RedstoneCableBlock block = ModBlocks.REDSTONE_CABLE.get();
        for (BlockState other : new BlockState[]{
                Blocks.STONE.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),
                ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState(),
                ModBlocks.ITEM_CABLE.get().defaultBlockState(),
                ModBlocks.FLUID_CABLE.get().defaultBlockState(),
                ModBlocks.NETWORK_CABLE.get().defaultBlockState()}) {
            assertFalse(block.joins(other), other.toString());
            assertFalse(links(other, Direction.EAST), other.toString());
        }
    }

    /**
     * Surrounded by everything, the cable wears its collar on the lever and the lamp and nowhere
     * else — the run into its own kind is a splice, which never takes one — and dust joins it
     * back only on a face it links on.
     */
    @Test
    void wearsItsCollarsWhereItLinksAndDustJoinsItThere(MinecraftServer server) {
        Map<BlockPos, BlockState> states = new HashMap<>(Map.of(
                BlockPos.ZERO.east(), Blocks.LEVER.defaultBlockState(),
                BlockPos.ZERO.west(), cable(),
                BlockPos.ZERO.above(), ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState(),
                BlockPos.ZERO.below(), Blocks.STONE.defaultBlockState(),
                BlockPos.ZERO.north(), Blocks.REDSTONE_LAMP.defaultBlockState()));
        states.put(BlockPos.ZERO, cable());
        var reader = (LevelReader) level(states, LevelReader.class);
        BlockState cable = cable();
        for (Direction side : Direction.values()) {
            BlockPos beyond = BlockPos.ZERO.relative(side);
            cable = cable.updateShape(reader, null, BlockPos.ZERO, side, beyond,
                    states.getOrDefault(beyond, Blocks.AIR.defaultBlockState()), RandomSource.create());
        }
        states.put(BlockPos.ZERO, cable);
        assertTrue(cable.getValue(AbstractCableBlock.EAST), "linked to the lever");
        assertTrue(cable.getValue(AbstractCableBlock.WEST), "the run continues into the redstone cable");
        assertTrue(cable.getValue(AbstractCableBlock.NORTH), "linked to the lamp");
        assertFalse(cable.getValue(AbstractCableBlock.UP), "the energy cable is another kind");
        assertFalse(cable.getValue(AbstractCableBlock.DOWN), "stone is a wall");
        assertFalse(cable.getValue(AbstractCableBlock.SOUTH), "air is nothing");
        var getter = (BlockGetter) level(states, BlockGetter.class);
        assertEquals(1 << Direction.EAST.ordinal() | 1 << Direction.NORTH.ordinal(),
                CableConnector.mask(getter, BlockPos.ZERO, cable), "two collars, on the lever and the lamp");
        // Dust asks with the direction from itself to the cable.
        assertTrue(cable.canRedstoneConnectTo(getter, BlockPos.ZERO, Direction.WEST), "dust east of it may join");
        assertFalse(cable.canRedstoneConnectTo(getter, BlockPos.ZERO, Direction.NORTH), "dust south of it may not");
        assertFalse(cable.canRedstoneConnectTo(getter, BlockPos.ZERO, null));
    }

    /** The cable is a source of signal, or nothing around it would ever ask what it gives out. */
    @Test
    void isASignalSource(MinecraftServer server) {
        assertTrue(cable().isSignalSource());
        assertFalse(ModBlocks.NETWORK_CABLE.get().defaultBlockState().isSignalSource(), "the others are not");
        assertFalse(cable().hasProperty(BlockStateProperties.POWERED), "no state to flip: the network answers");
    }
}
