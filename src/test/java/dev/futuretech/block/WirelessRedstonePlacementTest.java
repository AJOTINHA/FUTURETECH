package dev.futuretech.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A plate on the floor or the ceiling turns its front to the edge of the face the crosshair was nearest. */
class WirelessRedstonePlacementTest {
    private static final BlockPos POS = new BlockPos(10, 64, -3);

    private static Direction edge(double x, double z) {
        return WirelessRedstoneBlock.edgeAimedAt(new Vec3(POS.getX() + x, POS.getY(), POS.getZ() + z), POS, Direction.UP);
    }

    /** A point on the north face of the block: {@code across} runs west to east, {@code y} bottom to top. */
    private static Direction northWallEdge(double across, double y) {
        return WirelessRedstoneBlock.edgeAimedAt(new Vec3(POS.getX() + across, POS.getY() + y, POS.getZ()), POS, Direction.NORTH);
    }

    @Test
    void onAWallTheEdgesAreUpDownAndTheTwoSides() {
        assertEquals(Direction.UP, northWallEdge(0.5, 0.9));
        assertEquals(Direction.DOWN, northWallEdge(0.5, 0.1));
        // Seen by whoever faces the north wall from the south, east is on their right: the wall's clockwise.
        assertEquals(Direction.EAST, northWallEdge(0.95, 0.5));
        assertEquals(Direction.WEST, northWallEdge(0.05, 0.5));
        for (Direction wall : Direction.Plane.HORIZONTAL) {
            for (Direction edge : new Direction[]{Direction.UP, wall.getClockWise(), Direction.DOWN, wall.getCounterClockWise()}) {
                assertTrue(java.util.stream.IntStream.range(0, 4).anyMatch(spin -> WirelessRedstoneBlock.front(wall, spin) == edge),
                        "some turn on the " + wall + " wall looks " + edge);
            }
        }
    }

    @Test
    void theNearestEdgeWinsAndACornerGoesToTheCloserOfItsTwo() {
        assertEquals(Direction.NORTH, edge(0.5, 0.1));
        assertEquals(Direction.SOUTH, edge(0.5, 0.9));
        assertEquals(Direction.WEST, edge(0.1, 0.5));
        assertEquals(Direction.EAST, edge(0.9, 0.5));
        assertEquals(Direction.EAST, edge(0.95, 0.1), "nearer the east edge than the north one");
        assertEquals(Direction.NORTH, edge(0.9, 0.05), "and the other way round");
    }

    @Test
    void thePlateStandsOnTheFloorLookingAtThatEdge() {
        for (Direction edge : Direction.Plane.HORIZONTAL) {
            assertTrue(java.util.stream.IntStream.range(0, 4).anyMatch(spin -> WirelessRedstoneBlock.front(Direction.UP, spin) == edge),
                    "some turn on the floor looks " + edge);
            assertTrue(java.util.stream.IntStream.range(0, 4).anyMatch(spin -> WirelessRedstoneBlock.front(Direction.DOWN, spin) == edge),
                    "some turn on the ceiling looks " + edge);
        }
    }
}
