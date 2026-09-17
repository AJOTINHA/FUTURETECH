package dev.futuretech.menu;

import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teleporter's menu buttons share one number line: the redstone modes, then one button per
 * card to pick it, then one per scroll position. They must not overlap — a scroll read as a pick
 * would send the player somewhere they did not choose.
 */
class TeleporterButtonsTest {
    @Test
    void theSelectAndScrollButtonsNeverOverlap() {
        int redstoneEnd = RedstoneControlMenu.BUTTON_BASE + RedstoneMode.values().length;
        assertTrue(TeleporterMenu.SELECT_BASE >= redstoneEnd, "picks start past the redstone buttons");
        // One select button per card an MK4 holds, and the scroll buttons start beyond all of them.
        int selectEnd = TeleporterMenu.SELECT_BASE + TeleporterBlockEntity.MAX_CARDS;
        assertTrue(TeleporterMenu.SCROLL_BASE > selectEnd, "scrolls start past every pick");
    }

    /** Four rows on screen, and the window always lands on a card that exists. */
    @Test
    void theWindowNeverScrollsPastTheLastCard() {
        assertEquals(4, TeleporterMenu.VISIBLE);
        assertEquals(TeleporterMenu.CARD_Y + TeleporterMenu.VISIBLE * TeleporterMenu.CARD_SPACING + 14,
                TeleporterMenu.INVENTORY_Y, "the window is four rows tall at every level");
        for (int mk = 1; mk <= MachineLevel.MAX; mk++) {
            int cards = TeleporterBlockEntity.cards(mk);
            int maxScroll = Math.max(0, cards - TeleporterMenu.VISIBLE);
            assertEquals(mk == 1, maxScroll == 0, "only an MK1 has nothing to scroll");
            // The bottom row of the furthest window is the last card, never past it.
            assertEquals(cards - 1, maxScroll + TeleporterMenu.VISIBLE - 1, "MK" + mk);
        }
    }
}
