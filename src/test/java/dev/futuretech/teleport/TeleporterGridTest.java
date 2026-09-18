package dev.futuretech.teleport;

import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the panel sees down its cables. The walk follows a cable's own links, so everything the
 * wrench and the neighbours already decided about those links decides this too — cutting a run is
 * how a player keeps one panel's pads out of another panel's list.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TeleporterGridTest {
    private static final BlockPos PANEL = BlockPos.ZERO;

    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, BlockEntity> entities = new HashMap<>();

    private LevelReader level() {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> entities.get(args[0]);
                    case "hasChunkAt" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
      * A panel whose back is turned east, which is where every run in this test goes: the screen
      * looks west, and the one face that takes a cable is the one behind it.
      */
    private void panel() {
        panel(Direction.WEST);
    }

    private void panel(Direction facing) {
        states.put(PANEL, ModBlocks.NETWORK_PANEL.get().defaultBlockState()
                .setValue(dev.futuretech.block.NetworkPanelBlock.FACING, facing));
    }

    /** A cable linked on the sides named, and on nothing else. */
    private void cable(BlockPos pos, Direction... links) {
        BlockState state = ModBlocks.NETWORK_CABLE.get().defaultBlockState();
        for (Direction side : links) state = state.setValue(AbstractCableBlock.PROPERTY_BY_DIRECTION.get(side), true);
        states.put(pos, state);
    }

    private void pad(BlockPos pos) {
        BlockState state = ModBlocks.TELEPORTER.get().defaultBlockState();
        states.put(pos, state);
        entities.put(pos, new TeleporterBlockEntity(pos, state));
    }

    /** A run of {@code length} cables east of the panel, each linked to both its neighbours. */
    private BlockPos run(int length) {
        panel();
        for (int step = 1; step <= length; step++) {
            cable(PANEL.east(step), Direction.WEST, Direction.EAST);
        }
        return PANEL.east(length + 1);
    }

    @Test
    void findsThePadAtTheEndOfARunAndNotThePanelItself(MinecraftServer server) {
        BlockPos pad = run(3);
        pad(pad);
        assertEquals(List.of(pad), TeleporterGrid.teleporters(level(), PANEL));
        assertTrue(TeleporterGrid.reaches(level(), PANEL, pad));
        assertFalse(TeleporterGrid.reaches(level(), PANEL, PANEL), "a panel is not one of its own pads");
    }

    @Test
    void findsEveryPadOnTheRunNearestFirst(MinecraftServer server) {
        panel();
        cable(PANEL.east(), Direction.WEST, Direction.EAST, Direction.UP);
        cable(PANEL.east(2), Direction.WEST, Direction.EAST);
        BlockPos near = PANEL.east().above();
        BlockPos far = PANEL.east(3);
        pad(near);
        pad(far);
        assertEquals(List.of(near, far), TeleporterGrid.teleporters(level(), PANEL),
                "the nearer pad heads the list");
    }

    /** The wrench's cut, as it really lands: both cables of the run drop the link. */
    @Test
    void aCutLinkStopsTheWalk(MinecraftServer server) {
        panel();
        cable(PANEL.east(), Direction.WEST);
        cable(PANEL.east(2), Direction.EAST);
        BlockPos pad = PANEL.east(3);
        pad(pad);
        assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL));
        assertFalse(TeleporterGrid.reaches(level(), PANEL, pad), "a trip past a cut is refused");
    }

    /**
     * Half a cut is still a cut. The two cables always agree in the world, but the walk asks both
     * anyway, so one stale half can never open a run the player closed.
     */
    @Test
    void aLinkOnlyOneSideCarriesIsNotFollowed(MinecraftServer server) {
        panel();
        cable(PANEL.east(), Direction.WEST, Direction.EAST);
        // Links on east but never back west: the walk must not step in from the west.
        cable(PANEL.east(2), Direction.EAST);
        BlockPos pad = PANEL.east(3);
        pad(pad);
        assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL));
    }

    /** The plate has a front and a back; only the back has anywhere for a cable to go. */
    @Test
    void onlyTheCableBehindThePanelIsFollowed(MinecraftServer server) {
        BlockPos pad = run(2);
        pad(pad);
        assertEquals(1, TeleporterGrid.teleporters(level(), PANEL).size(), "the back takes the run");
        // Turned any other way, the same cables are against its front or its edge, and are not read.
        for (Direction facing : new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH,
                Direction.UP, Direction.DOWN}) {
            panel(facing);
            assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL),
                    "a panel facing " + facing.getName() + " has its back elsewhere");
        }
    }

    @Test
    void aPanelWithNoCableBesideItSeesNothing(MinecraftServer server) {
        panel();
        pad(PANEL.east());
        assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL),
                "a pad touching the panel is not on its network; the cable is what joins them");
    }

    /** Another kind of cable is not this network, however it is wired. */
    @Test
    void theOtherCablesDoNotConduct(MinecraftServer server) {
        panel();
        BlockState energy = ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState()
                .setValue(AbstractCableBlock.WEST, true).setValue(AbstractCableBlock.EAST, true);
        states.put(PANEL.east(), energy);
        pad(PANEL.east(2));
        assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL));
    }

    /** A run longer than the cap stops rather than walking forever. */
    @Test
    void theWalkIsCapped(MinecraftServer server) {
        BlockPos pad = run(TeleporterGrid.MAX_CABLES + 50);
        pad(pad);
        assertEquals(List.of(), TeleporterGrid.teleporters(level(), PANEL),
                "the pad past the cap is out of reach, and the walk still returns");
    }
}
