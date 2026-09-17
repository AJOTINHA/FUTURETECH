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

/** The teleporter's sums and its slots: what a trip costs by level and distance, and which cards it takes. */
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

    /** Four cards at MK1, doubling each level, and the store always the size an MK4 needs. */
    @Test
    void everyLevelDoublesTheCardSlotsAndOnlyTheOpenOnesTakeACard(MinecraftServer server) {
        assertEquals(BASE_CARDS, cards(1));
        assertEquals(8, cards(2));
        assertEquals(16, cards(3));
        assertEquals(MAX_CARDS, cards(4));
        assertEquals(32, MAX_CARDS);
        // Out of range clamps rather than shifting past the store; a pad is never MK0 or MK5.
        assertEquals(cards(1), cards(0));
        assertEquals(cards(4), cards(9));

        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        written.set(ModDataComponents.TELEPORT_TARGET.get(), target(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO)));
        for (int mk = 1; mk <= MachineLevel.MAX; mk++) {
            var pad = pad(mk);
            assertEquals(MAX_CARDS, pad.getContainerSize(), "the store never resizes");
            assertEquals(cards(mk), pad.unlockedCards(), "MK" + mk);
            assertTrue(pad.canPlaceItem(cards(mk) - 1, written), "MK" + mk + " reaches its last slot");
            if (mk < MachineLevel.MAX) {
                assertFalse(pad.canPlaceItem(cards(mk), written), "MK" + mk + " stops at its last slot");
            }
        }
    }

    /** A slot the level has not opened is no destination, however the pick arrives. */
    @Test
    void aLockedSlotCannotBeChosen(MinecraftServer server) {
        var pad = pad(1);
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        var there = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 5)));
        written.set(ModDataComponents.TELEPORT_TARGET.get(), there);
        // The card is put straight into the store, past canPlaceItem, the way a loaded world could.
        pad.setItem(cards(1), written);
        pad.select(cards(1));
        assertEquals(-1, pad.selected(), "a locked slot is not picked");
        assertNull(pad.target());
        pad.select(MAX_CARDS);
        assertEquals(-1, pad.selected(), "nor is one past the store");
    }

    /** What an upgrade does to the cards: opens more slots, and keeps what was already in them. */
    @Test
    void anUpgradeOpensMoreSlotsAndKeepsTheCardsAlreadyThere(MinecraftServer server) {
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        var there = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 5)));
        written.set(ModDataComponents.TELEPORT_TARGET.get(), there);
        var pad = pad(1);
        pad.setItem(0, written);
        pad.select(0);
        assertEquals(there, pad.target());
        // The MK lives in the block state, so an upgrade is the same entity under a new state.
        pad.setBlockState(ModBlocks.TELEPORTER.get().defaultBlockState().setValue(MachineLevel.MK, 2));
        assertEquals(8, pad.unlockedCards());
        assertEquals(there, pad.target(), "the chosen card survives the upgrade");
        assertTrue(pad.canPlaceItem(7, written), "the new slots are open");
    }

    @Test
    void cardSlotsTakeWrittenCardsOnlyAndTheChosenOneIsTheDestination(MinecraftServer server) {
        var teleporter = new TeleporterBlockEntity(BlockPos.ZERO, ModBlocks.TELEPORTER.get().defaultBlockState().setValue(MachineLevel.MK, 1));
        var blank = new ItemStack(ModItems.TELEPORT_CARD.get());
        var written = new ItemStack(ModItems.TELEPORT_CARD.get());
        var there = target(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 64, 5)));
        written.set(ModDataComponents.TELEPORT_TARGET.get(), there);
        assertFalse(TeleportCardItem.isWritten(blank));
        assertFalse(teleporter.canPlaceItem(0, blank));
        assertTrue(teleporter.canPlaceItem(0, written));
        assertNull(teleporter.target(), "nothing chosen yet");
        teleporter.setItem(1, written);
        teleporter.select(1);
        assertEquals(1, teleporter.selected());
        assertEquals(there, teleporter.target());
        // Choosing the same slot again unchooses it; an empty slot is no destination.
        teleporter.select(1);
        assertEquals(-1, teleporter.selected());
        teleporter.select(0);
        assertNull(teleporter.target());
        teleporter.setName("  Base  ");
        assertEquals("Base", teleporter.name());
        assertEquals("Base", teleporter.displayName());
        teleporter.setName("");
        assertEquals("0, 0, 0", teleporter.displayName(), "nameless pads go by their coordinates");
    }
}
