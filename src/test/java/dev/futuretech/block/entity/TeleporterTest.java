package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.TeleporterBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The teleporter's sums and its slot: what a trip costs by level and distance, which card it takes, and what a pick names. */
@ExtendWith(EphemeralTestServerProvider.class)
class TeleporterTest {
    private static final GlobalPos HOME = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);

    private static TeleportTarget target(GlobalPos pos) { return new TeleportTarget(pos, "there"); }

    @Test
    void tripsCostBasePlusDistanceAndEveryLevelIsCheaper() {
        var near = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(100, 0, 0)));
        assertEquals(BASE_COST + 100 * COST_PER_BLOCK, cost(HOME, near, 1));
        assertEquals((BASE_COST + 100 * COST_PER_BLOCK) * 90 / 100, cost(HOME, near, 2));
        assertEquals((BASE_COST + 100 * COST_PER_BLOCK) * 70 / 100, cost(HOME, near, 4));
        var nether = target(GlobalPos.of(Level.NETHER, BlockPos.ZERO));
        assertEquals(CROSS_DIMENSION_COST, cost(HOME, nether, 1));
        assertEquals(CROSS_DIMENSION_COST * 70 / 100, cost(HOME, nether, 4));
    }

    @Test
    void onlyTheTopLevelReachesAnotherDimensionAndHigherLevelsChargeFaster() {
        var nether = target(GlobalPos.of(Level.NETHER, BlockPos.ZERO));
        var far = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(10_000, 0, 10_000)));
        for (int mk = 1; mk < CROSS_DIMENSION_LEVEL; mk++) {
            assertFalse(reaches(HOME, nether, mk), "MK" + mk);
            assertTrue(reaches(HOME, far, mk), "MK" + mk + " goes anywhere in its own world");
        }
        assertTrue(reaches(HOME, nether, CROSS_DIMENSION_LEVEL));
        assertEquals(CHARGE_TICKS, chargeTicks(1));
        assertTrue(chargeTicks(4) < chargeTicks(1));
    }

    private static TeleporterBlockEntity pad(int mk) {
        return new TeleporterBlockEntity(BlockPos.ZERO,
                ModBlocks.TELEPORTER.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    /** One card slot at every level; the rest of a pad's destinations live on the network. */
    @Test
    void everyLevelHoldsOneCardAndNothingElse(MinecraftServer server) {
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        written.set(ModDataComponents.TELEPORT_TARGET.get(), target(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO)));
        for (int mk = 1; mk <= MachineLevel.MAX; mk++) {
            var pad = pad(mk);
            assertEquals(1, pad.getContainerSize(), "MK" + mk);
            assertTrue(pad.canPlaceItem(CARD_SLOT, written), "MK" + mk + " takes a card in its slot");
            assertFalse(pad.canPlaceItem(1, written), "MK" + mk + " has no second slot");
        }
    }

    /** A slot that does not exist is no destination, however the pick arrives. */
    @Test
    void aSlotThatDoesNotExistCannotBeChosen(MinecraftServer server) {
        var pad = pad(1);
        pad.select(1);
        assertEquals(-1, pad.selected(), "a second slot is not picked");
        assertNull(pad.target());
        pad.select(new BlockPos(1, 2, 3), StorageCardsBlockEntity.SLOTS);
        assertEquals(-1, pad.selectedSlot(), "nor is one past a storage's shelf");
        assertNull(pad.selectedSource());
    }

    /**
     * A pick on the network names the storage and the slot in it; the pad's own slot is then not
     * the chosen one, and the same pick again unpicks it.
     */
    @Test
    void aPickOnTheNetworkNamesTheStorageAndItsSlot(MinecraftServer server) {
        var pad = pad(1);
        var storage = new BlockPos(4, 64, 4);
        pad.select(storage, 3);
        assertEquals(storage, pad.selectedSource());
        assertEquals(3, pad.selectedSlot());
        assertEquals(-1, pad.selected(), "the pad's own slot is not the chosen one");
        assertNull(pad.target(), "no world to read the storage from, so nowhere to send");
        pad.select(storage, 3);
        assertNull(pad.selectedSource(), "the same pick again unpicks it");
        assertEquals(-1, pad.selectedSlot());
    }

    /** What an upgrade does to the card: nothing, it keeps the card and the choice. */
    @Test
    void anUpgradeKeepsTheCardAndTheChoice(MinecraftServer server) {
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        var there = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 5)));
        written.set(ModDataComponents.TELEPORT_TARGET.get(), there);
        var pad = pad(1);
        pad.setItem(CARD_SLOT, written);
        pad.select(CARD_SLOT);
        assertEquals(there, pad.target());
        // The MK lives in the block state, so an upgrade is the same entity under a new state.
        pad.setBlockState(ModBlocks.TELEPORTER.get().defaultBlockState().setValue(MachineLevel.MK, 2));
        assertEquals(there, pad.target(), "the chosen card survives the upgrade");
        assertTrue(pad.canPlaceItem(CARD_SLOT, written));
    }

    @Test
    void cardSlotsTakeWrittenCardsOnlyAndTheChosenOneIsTheDestination(MinecraftServer server) {
        var teleporter = new TeleporterBlockEntity(BlockPos.ZERO, ModBlocks.TELEPORTER.get().defaultBlockState().setValue(MachineLevel.MK, 1));
        var blank = new ItemStack(ModItems.TELEPORT_CARD.get());
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        var there = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 5)));
        written.set(ModDataComponents.TELEPORT_TARGET.get(), there);
        assertFalse(TeleportCardItem.isWritten(blank));
        assertFalse(teleporter.canPlaceItem(CARD_SLOT, blank));
        assertTrue(teleporter.canPlaceItem(CARD_SLOT, written));
        assertNull(teleporter.target(), "nothing chosen yet");
        teleporter.setItem(CARD_SLOT, written);
        teleporter.select(CARD_SLOT);
        assertEquals(CARD_SLOT, teleporter.selected());
        assertEquals(there, teleporter.target());
        // Choosing the same slot again unchooses it; an empty slot is no destination.
        teleporter.select(CARD_SLOT);
        assertEquals(-1, teleporter.selected());
        teleporter.setItem(CARD_SLOT, ItemStack.EMPTY);
        teleporter.select(CARD_SLOT);
        assertNull(teleporter.target());
        teleporter.setName("  Base  ");
        assertEquals("Base", teleporter.name());
        assertEquals("Base", teleporter.displayName());
        teleporter.setName("");
        assertEquals("0, 0, 0", teleporter.displayName(), "nameless pads go by their coordinates");
    }
}
