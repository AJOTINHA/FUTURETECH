package dev.futuretech.menu;

import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teleporter's menu buttons share one number line: the redstone modes, then the pick of the
 * pad's one card. They must not overlap — a mode read as a pick would send the player somewhere
 * they did not choose.
 */
class TeleporterButtonsTest {
    @Test
    void thePickNeverOverlapsTheRedstoneButtons() {
        int redstoneEnd = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;
        assertTrue(TeleporterMenu.SELECT_BASE + TeleporterBlockEntity.CARD_SLOT >= redstoneEnd, "the pick is past the redstone buttons");
    }

    /** One card row, and the window keeps the height the energy column and the tabs need. */
    @Test
    void theWindowKeepsItsHeightAroundTheOneRow() {
        assertEquals(TeleporterMenu.LIST_TOP + TeleporterMenu.LIST_HEIGHT + 14, TeleporterMenu.INVENTORY_Y);
        assertTrue(TeleporterMenu.CARD_Y >= TeleporterMenu.LIST_TOP, "the row is inside the area");
        assertTrue(TeleporterMenu.CARD_Y + 16 <= TeleporterMenu.LIST_TOP + TeleporterMenu.LIST_HEIGHT, "and does not run out of it");
    }
}
