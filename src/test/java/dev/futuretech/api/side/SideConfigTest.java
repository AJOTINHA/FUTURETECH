package dev.futuretech.api.side;

import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class SideConfigTest {
    @Test
    void cycleVisitsOnlyAllowedModesInOrder(MinecraftServer server) {
        var all = new SideConfig(EnumSet.allOf(SideMode.class), side -> SideMode.NONE);
        assertEquals(SideMode.INPUT, all.cycle(Direction.UP));
        assertEquals(SideMode.OUTPUT, all.cycle(Direction.UP));
        assertEquals(SideMode.BOTH, all.cycle(Direction.UP));
        assertEquals(SideMode.NONE, all.cycle(Direction.UP));
        assertEquals(SideMode.INPUT, all.cycle(Direction.UP));
        assertEquals(SideMode.NONE, all.mode(Direction.DOWN), "other faces are untouched");

        var noBoth = new SideConfig(Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE), side -> SideMode.INPUT);
        assertEquals(SideMode.OUTPUT, noBoth.cycle(Direction.NORTH));
        assertEquals(SideMode.NONE, noBoth.cycle(Direction.NORTH));
        assertEquals(SideMode.INPUT, noBoth.cycle(Direction.NORTH));
        assertThrows(IllegalArgumentException.class, () -> noBoth.set(Direction.NORTH, SideMode.BOTH));
    }

    @Test
    void sidelessAccessIsUnrestrictedAndDataSlotsMirrorModes(MinecraftServer server) {
        var config = new SideConfig(EnumSet.allOf(SideMode.class), side -> side == Direction.UP ? SideMode.OUTPUT : SideMode.NONE);
        assertTrue(config.allowsInput(null));
        assertTrue(config.allowsOutput(null));
        assertFalse(config.allowsInput(Direction.UP));
        assertTrue(config.allowsOutput(Direction.UP));
        assertFalse(config.allowsOutput(Direction.DOWN));
        assertEquals(SideMode.OUTPUT.ordinal(), config.data(Direction.UP.ordinal()));
        assertEquals(SideMode.NONE.ordinal(), config.data(Direction.DOWN.ordinal()));
    }

    @Test
    void sidedViewsHideOrLimitTheHandler(MinecraftServer server) {
        var full = new SimpleEnergyHandler(1_000, 1_000, 1_000, 500);
        assertNull(SidedEnergy.view(full, SideMode.NONE));
        assertSame(full, SidedEnergy.view(full, SideMode.BOTH));
        var inputOnly = SidedEnergy.view(full, SideMode.INPUT);
        var outputOnly = SidedEnergy.view(full, SideMode.OUTPUT);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(100, inputOnly.insert(100, transaction));
            assertEquals(0, inputOnly.extract(100, transaction));
            assertEquals(0, outputOnly.insert(100, transaction));
            assertEquals(100, outputOnly.extract(100, transaction));
            transaction.commit();
        }
        assertEquals(500, full.getAmountAsInt());
        assertEquals(1_000, inputOnly.getCapacityAsInt());
    }

    @Test
    void batteryFacesStartClosedAndNeverAllowBoth(MinecraftServer server) {
        var block = ModBlocks.BATTERY_MK1.get();
        assertEquals(Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE), block.allowedSideModes());
        var state = block.defaultBlockState().setValue(dev.futuretech.block.BatteryBlock.FACING, Direction.EAST);
        var battery = new BatteryBlockEntity(BlockPos.ZERO, state);
        assertEquals(Direction.EAST, battery.front());
        for (Direction side : Direction.values()) {
            assertEquals(SideMode.NONE, battery.sideConfig().mode(side), side.toString());
        }
        // Menu button ids are direction ordinals; a face cycles NONE -> INPUT -> OUTPUT -> NONE, skipping BOTH.
        assertTrue(SideConfigMenu.handleButton(battery, Direction.EAST.ordinal()));
        assertEquals(SideMode.INPUT, battery.sideConfig().mode(Direction.EAST));
        assertTrue(SideConfigMenu.handleButton(battery, Direction.EAST.ordinal()));
        assertEquals(SideMode.OUTPUT, battery.sideConfig().mode(Direction.EAST));
        assertTrue(SideConfigMenu.handleButton(battery, Direction.EAST.ordinal()));
        assertEquals(SideMode.NONE, battery.sideConfig().mode(Direction.EAST));
        assertFalse(SideConfigMenu.handleButton(battery, SideConfigMenu.BUTTON_CLEAR_ALL + 1));
        assertFalse(SideConfigMenu.handleButton(null, 0));
    }

    @Test
    void clearAllButtonClosesEveryFace(MinecraftServer server) {
        var battery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        battery.sideConfig().set(Direction.NORTH, SideMode.OUTPUT);
        battery.sideConfig().set(Direction.UP, SideMode.INPUT);
        assertTrue(SideConfigMenu.handleButton(battery, SideConfigMenu.BUTTON_CLEAR_ALL));
        for (Direction side : Direction.values()) assertEquals(SideMode.NONE, battery.sideConfig().mode(side));

        // Machines that cannot close a face keep what they had.
        var alwaysOpen = new SideConfig(Set.of(SideMode.BOTH), side -> SideMode.BOTH);
        alwaysOpen.clear();
        assertEquals(SideMode.BOTH, alwaysOpen.mode(Direction.DOWN));
    }

    @Test
    void generatorFacesOnlyOutputOrClose(MinecraftServer server) {
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        for (Direction side : Direction.values()) assertEquals(SideMode.NONE, generator.sideConfig().mode(side));
        SideConfigMenu.handleButton(generator, Direction.UP.ordinal());
        assertEquals(SideMode.OUTPUT, generator.sideConfig().mode(Direction.UP));
        SideConfigMenu.handleButton(generator, Direction.UP.ordinal());
        assertEquals(SideMode.NONE, generator.sideConfig().mode(Direction.UP));
        SideConfigMenu.handleButton(generator, Direction.UP.ordinal());
        assertEquals(SideMode.OUTPUT.ordinal(),
                generator.menuData().get(SolidFuelGeneratorBlockEntity.DATA_SIDE_BASE + Direction.UP.ordinal()));
        assertEquals(Direction.NORTH.ordinal(), generator.menuData().get(SolidFuelGeneratorBlockEntity.DATA_FRONT));
    }

    @Test
    void sideConfigSurvivesReloadAndDropsModesNoLongerAllowed(MinecraftServer server) {
        var original = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        original.sideConfig().set(Direction.UP, SideMode.OUTPUT);
        original.sideConfig().set(Direction.NORTH, SideMode.INPUT);
        var restored = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                original.saveWithoutMetadata(server.registryAccess())));
        assertEquals(SideMode.OUTPUT, restored.sideConfig().mode(Direction.UP));
        assertEquals(SideMode.INPUT, restored.sideConfig().mode(Direction.NORTH));
        assertEquals(SideMode.NONE, restored.sideConfig().mode(Direction.DOWN));

        // A save from a machine that allowed BOTH must not smuggle it into one that does not.
        var permissive = new SideConfig(EnumSet.allOf(SideMode.class), side -> SideMode.BOTH);
        var strict = new SideConfig(Set.of(SideMode.INPUT, SideMode.NONE), side -> SideMode.INPUT);
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        permissive.save(out);
        strict.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), out.buildResult()));
        for (Direction side : Direction.values()) assertEquals(SideMode.INPUT, strict.mode(side));
    }
}
