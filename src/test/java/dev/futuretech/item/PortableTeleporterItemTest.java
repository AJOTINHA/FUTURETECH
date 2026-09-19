package dev.futuretech.item;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.TeleporterBlockEntity;
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

import static org.junit.jupiter.api.Assertions.*;

/** What the portable teleporter keeps on itself: the pads it saved, its link, and the fare it charges. */
@ExtendWith(EphemeralTestServerProvider.class)
class PortableTeleporterItemTest {
    private static TeleportTarget pad(int x) {
        return new TeleportTarget(GlobalPos.of(Level.OVERWORLD, new BlockPos(x, 64, 0)), "pad " + x);
    }

    @Test
    void aShiftClickSavesAPadAndTheNextForgetsItAgain(MinecraftServer server) {
        ItemStack stack = new ItemStack(ModItems.PORTABLE_TELEPORTER.get());
        assertTrue(PortableTeleporterItem.saved(stack).isEmpty());
        assertNull(PortableTeleporterItem.link(stack));
        assertTrue(PortableTeleporterItem.toggleSaved(stack, pad(1)));
        assertTrue(PortableTeleporterItem.toggleSaved(stack, pad(2)));
        assertEquals(2, PortableTeleporterItem.saved(stack).size());
        assertEquals("pad 1", PortableTeleporterItem.saved(stack).getFirst().name());
        // The same pad again, whatever it is called now, comes off the list.
        assertTrue(PortableTeleporterItem.toggleSaved(stack, new TeleportTarget(pad(1).pos(), "renamed")));
        assertEquals(1, PortableTeleporterItem.saved(stack).size());
        assertEquals("pad 2", PortableTeleporterItem.saved(stack).getFirst().name());
        // The list has a top.
        for (int x = 10; x < 10 + PortableTeleporterItem.SAVED_PADS - 1; x++) assertTrue(PortableTeleporterItem.toggleSaved(stack, pad(x)));
        assertFalse(PortableTeleporterItem.toggleSaved(stack, pad(999)));
        assertEquals(PortableTeleporterItem.SAVED_PADS, PortableTeleporterItem.saved(stack).size());
    }

    @Test
    void theFareIsTheTopLevelsAndTheLinkIsAPanel(MinecraftServer server) {
        GlobalPos from = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        assertEquals(TeleporterBlockEntity.cost(from, pad(100), MachineLevel.MAX), PortableTeleporterItem.cost(from, pad(100)));
        assertTrue(PortableTeleporterItem.cost(from, pad(100)) < TeleporterBlockEntity.cost(from, pad(100), 1));
        ItemStack stack = new ItemStack(ModItems.PORTABLE_TELEPORTER.get());
        GlobalPos panel = GlobalPos.of(Level.NETHER, new BlockPos(3, 60, 3));
        PortableTeleporterItem.link(stack, panel);
        assertEquals(panel, PortableTeleporterItem.link(stack));
        assertEquals(0, PortableTeleporterItem.storedEnergy(stack));
    }
}
