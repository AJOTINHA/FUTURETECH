package dev.futuretech.api.redstone;

import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RedstoneControlTest {
    @Test
    void modesGateRunningBySignal(MinecraftServer server) {
        assertTrue(RedstoneMode.IGNORED.allows(false));
        assertTrue(RedstoneMode.IGNORED.allows(true));
        assertTrue(RedstoneMode.LOW.allows(false));
        assertFalse(RedstoneMode.LOW.allows(true));
        assertFalse(RedstoneMode.HIGH.allows(false));
        assertTrue(RedstoneMode.HIGH.allows(true));
        assertEquals(RedstoneMode.HIGH, RedstoneMode.byOrdinal(99));
    }

    @Test
    void menuButtonsSelectModesAfterTheSideConfigIds(MinecraftServer server) {
        var battery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        assertEquals(RedstoneMode.IGNORED, battery.redstoneControl().mode());
        assertTrue(RedstoneControlMenu.handleButton(battery, RedstoneControlMenu.BUTTON_BASE + RedstoneMode.HIGH.ordinal()));
        assertEquals(RedstoneMode.HIGH, battery.redstoneControl().mode());
        assertEquals(RedstoneMode.HIGH.ordinal(), battery.menuData().get(BatteryBlockEntity.DATA_REDSTONE_BASE));
        assertEquals(0, battery.menuData().get(BatteryBlockEntity.DATA_REDSTONE_BASE + 1), "not powered yet");
        // Side configuration ids stay with the side configuration.
        assertFalse(RedstoneControlMenu.handleButton(battery, RedstoneControlMenu.BUTTON_BASE - 1));
        assertFalse(RedstoneControlMenu.handleButton(battery, RedstoneControlMenu.BUTTON_BASE + 3));
        assertFalse(RedstoneControlMenu.handleButton(null, RedstoneControlMenu.BUTTON_BASE));
        assertEquals(7, RedstoneControlMenu.BUTTON_BASE);
    }

    @Test
    void modeSurvivesReload(MinecraftServer server) {
        var original = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        original.redstoneControl().setMode(RedstoneMode.LOW);
        var restored = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                original.saveWithoutMetadata(server.registryAccess())));
        assertEquals(RedstoneMode.LOW, restored.redstoneControl().mode());
        assertEquals(RedstoneMode.LOW.ordinal(), restored.menuData().get(SolidFuelGeneratorBlockEntity.DATA_REDSTONE_BASE));
    }
}
