package dev.futuretech.api.side;

import dev.futuretech.block.ElectricFurnaceBlock;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class SideConfigVisualsTest {
    private static ElectricFurnaceBlockEntity furnace(Direction front) {
        return new ElectricFurnaceBlockEntity(BlockPos.ZERO,
                ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState().setValue(ElectricFurnaceBlock.FACING, front));
    }

    /** The mode the renderer would draw on {@code side}, read back out of the model data snapshot. */
    private static SideMode rendered(BlockEntity entity, Direction side) {
        return SideConfigVisuals.mode(entity.getModelData().get(SideConfigVisuals.FACE_MODES), side);
    }

    private static void assertAllFaces(BlockEntity entity, SideMode expected) {
        for (Direction side : Direction.values()) assertEquals(expected, rendered(entity, side));
    }

    @Test
    void everyModeSurvivesThePackedEncoding() {
        var sides = new SideConfig(Set.of(SideMode.values()), side -> SideMode.NONE);
        for (Direction side : Direction.values()) {
            sides.set(side, SideMode.byOrdinal(side.ordinal() % SideMode.values().length));
        }
        int packed = SideConfigVisuals.faceModes(sides);
        for (Direction side : Direction.values()) {
            assertEquals(sides.mode(side), SideConfigVisuals.mode(packed, side));
        }
    }

    @Test
    void menuChangesOnlyTheSelectedWorldFaceForEveryOrientation(MinecraftServer server) {
        for (Direction front : Direction.Plane.HORIZONTAL) {
            for (Direction selected : Direction.values()) {
                var furnace = furnace(front);
                assertAllFaces(furnace, SideMode.NONE);
                assertTrue(SideConfigMenu.handleButton(furnace, selected.ordinal()));
                var snapshot = furnace.getModelData();
                for (Direction side : Direction.values()) {
                    assertEquals(side == selected ? SideMode.INPUT : SideMode.NONE,
                            SideConfigVisuals.mode(snapshot.get(SideConfigVisuals.FACE_MODES), side));
                }
                // INPUT -> OUTPUT moves only that face; worker snapshots must stay immutable.
                SideConfigMenu.handleButton(furnace, selected.ordinal());
                assertEquals(SideMode.OUTPUT, rendered(furnace, selected));
                assertEquals(SideMode.INPUT, SideConfigVisuals.mode(snapshot.get(SideConfigVisuals.FACE_MODES), selected));
            }
        }
    }

    @Test
    void aFurnaceFaceCanBeInputAndOutputAtOnce(MinecraftServer server) {
        var furnace = furnace(Direction.NORTH);
        // INPUT -> OUTPUT -> BOTH, the mode the input_output texture exists for.
        for (int step = 0; step < 3; step++) SideConfigMenu.handleButton(furnace, Direction.EAST.ordinal());
        assertEquals(SideMode.BOTH, rendered(furnace, Direction.EAST));
        assertArrayEquals(new int[] {ElectricFurnaceBlockEntity.SLOT_INPUT, ElectricFurnaceBlockEntity.SLOT_OUTPUT},
                furnace.getSlotsForFace(Direction.EAST), "a BOTH face exposes both slots to hoppers");
    }

    @Test
    void chunkUpdateAndLivePacketRestoreAndClearFacesWithoutResettingEnergy(MinecraftServer server) {
        var source = furnace(Direction.WEST);
        var client = furnace(Direction.WEST);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(100, client.energy().insert(100, transaction));
            transaction.commit();
        }
        for (Direction side : Direction.values()) SideConfigMenu.handleButton(source, side.ordinal());
        var tag = source.getUpdateTag(server.registryAccess());
        assertTrue(tag.contains("Sides"));
        assertFalse(tag.contains("Energy"), "energy is menu data, not public render data");
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));
        assertAllFaces(client, SideMode.INPUT);
        assertEquals(100, client.energy().getAmountAsInt());

        SideConfigMenu.handleButton(source, SideConfigMenu.BUTTON_CLEAR_ALL);
        // The ephemeral registry-only test server has no world; exercise the live packet handler with its payload.
        var clearedTag = source.getUpdateTag(server.registryAccess());
        client.onDataPacket(null, TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), clearedTag));
        assertAllFaces(client, SideMode.NONE);
        assertEquals(100, client.energy().getAmountAsInt());
    }

    @Test
    void savedSideModesRebuildRenderDataAfterReload(MinecraftServer server) {
        var source = furnace(Direction.SOUTH);
        SideConfigMenu.handleButton(source, Direction.NORTH.ordinal());
        SideConfigMenu.handleButton(source, Direction.DOWN.ordinal());
        var restored = furnace(Direction.SOUTH);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                source.saveWithoutMetadata(server.registryAccess())));
        assertEquals(source.getModelData().get(SideConfigVisuals.FACE_MODES),
                restored.getModelData().get(SideConfigVisuals.FACE_MODES));
        assertEquals(SideMode.INPUT, rendered(restored, Direction.DOWN));
        assertEquals(SideMode.NONE, rendered(restored, Direction.UP));
    }
}
