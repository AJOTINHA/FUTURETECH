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
    void rotatingPortsCarriesInputOutputAndClosedFacesWithTheBlock(MinecraftServer server) {
        var config = new SideConfig(EnumSet.allOf(SideMode.class), true, side -> switch (side) {
            case NORTH -> SideMode.INPUT;
            case EAST -> SideMode.OUTPUT;
            case SOUTH -> SideMode.BOTH;
            case WEST -> SideMode.NONE;
            case UP -> SideMode.OUTPUT;
            case DOWN -> SideMode.INPUT;
        });
        for (int turn = 1; turn <= 4; turn++) {
            config.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            var rotation = net.minecraft.world.level.block.Rotation.values()[turn % 4];
            assertEquals(SideMode.INPUT, config.mode(rotation.rotate(Direction.NORTH)));
            assertEquals(SideMode.OUTPUT, config.mode(rotation.rotate(Direction.EAST)));
            assertEquals(SideMode.BOTH, config.mode(rotation.rotate(Direction.SOUTH)));
            assertEquals(SideMode.NONE, config.mode(rotation.rotate(Direction.WEST)));
            assertEquals(SideMode.OUTPUT, config.mode(Direction.UP));
            assertEquals(SideMode.INPUT, config.mode(Direction.DOWN));
            assertTrue(config.allowsEnergyInput(rotation.rotate(Direction.NORTH)));
            assertFalse(config.allowsEnergyOutput(rotation.rotate(Direction.NORTH)));
            assertTrue(config.allowsEnergyOutput(rotation.rotate(Direction.EAST)));
        }
    }
    @Test
    void reverseCycleWrapsAndSkipsUnsupportedModes(MinecraftServer server) {
        var all = new SideConfig(EnumSet.allOf(SideMode.class), side -> SideMode.NONE);
        for (SideMode expected : new SideMode[]{SideMode.BOTH, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE}) {
            assertEquals(expected, all.cycle(Direction.UP, true));
        }
        assertEquals(SideMode.NONE, all.mode(Direction.DOWN));
        var battery = new SideConfig(Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE), side -> SideMode.NONE);
        for (SideMode expected : new SideMode[]{SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE}) {
            assertEquals(expected, battery.cycle(Direction.NORTH, true));
        }
        // Going back must undo a forward click even for restricted and single-mode machines.
        for (int mask = 1; mask < 16; mask++) {
            var allowed = EnumSet.noneOf(SideMode.class);
            for (SideMode mode : SideMode.values()) if ((mask & 1 << mode.ordinal()) != 0) allowed.add(mode);
            for (SideMode initial : allowed) {
                var config = new SideConfig(allowed, side -> initial);
                config.cycle(Direction.SOUTH);
                assertEquals(initial, config.cycle(Direction.SOUTH, true));
            }
        }
    }

    @Test
    void reverseMenuButtonsReachEachFaceWithoutOverlappingRedstone(MinecraftServer server) {
        var battery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        for (Direction side : Direction.values()) {
            battery.sideConfig().clear();
            int reverseId = SideConfigMenu.BUTTON_REVERSE_BASE + side.ordinal();
            assertTrue(SideConfigMenu.handleButton(battery, reverseId));
            assertEquals(SideMode.OUTPUT, battery.sideConfig().mode(side));
            for (Direction other : Direction.values()) {
                if (other != side) assertEquals(SideMode.NONE, battery.sideConfig().mode(other));
            }
            assertTrue(SideConfigMenu.handleButton(battery, reverseId));
            assertEquals(SideMode.INPUT, battery.sideConfig().mode(side));
            assertTrue(SideConfigMenu.handleButton(battery, side.ordinal()));
            assertEquals(SideMode.OUTPUT, battery.sideConfig().mode(side));
        }
        assertFalse(SideConfigMenu.handleButton(battery, -1));
        assertFalse(SideConfigMenu.handleButton(battery, SideConfigMenu.BUTTON_COUNT));
        assertFalse(SideConfigMenu.handleButton(battery, dev.futuretech.api.redstone.RedstoneControlMenu.BUTTON_BASE));
    }

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
        assertTrue(config.allowsItemInput(null));
        assertTrue(config.allowsItemOutput(null));
        assertFalse(config.allowsItemInput(Direction.UP));
        assertTrue(config.allowsItemOutput(Direction.UP));
        assertFalse(config.allowsItemOutput(Direction.DOWN));
        assertEquals(SideMode.OUTPUT.ordinal(), config.data(Direction.UP.ordinal()));
        assertEquals(SideMode.NONE.ordinal(), config.data(Direction.DOWN.ordinal()));
    }

    @Test
    void onlyMachinesThatGovernEnergySeeTheirFacesRestrictIt(MinecraftServer server) {
        // A machine leaves energy alone: every face takes power in and hands it out, whatever the mode.
        var free = new SideConfig(EnumSet.allOf(SideMode.class), side -> SideMode.NONE);
        assertFalse(free.governsEnergy());
        for (Direction side : Direction.values()) {
            assertTrue(free.allowsEnergyInput(side));
            assertTrue(free.allowsEnergyOutput(side));
        }

        // A battery does govern it, so a closed face neither charges nor discharges.
        var battery = new SideConfig(Set.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE), true, side -> SideMode.NONE);
        assertTrue(battery.governsEnergy());
        assertFalse(battery.allowsEnergyInput(Direction.UP));
        assertFalse(battery.allowsEnergyOutput(Direction.UP));
        battery.set(Direction.UP, SideMode.INPUT);
        assertTrue(battery.allowsEnergyInput(Direction.UP));
        assertFalse(battery.allowsEnergyOutput(Direction.UP));
        battery.set(Direction.UP, SideMode.OUTPUT);
        assertFalse(battery.allowsEnergyInput(Direction.UP));
        assertTrue(battery.allowsEnergyOutput(Direction.UP));
    }

    @Test
    void sidedViewsHideOrLimitTheHandler(MinecraftServer server) {
        var full = new SimpleEnergyHandler(1_000, 1_000, 1_000, 500);
        // Only a configuration that governs energy can narrow the handler; a machine's passes through.
        var machine = new SideConfig(EnumSet.allOf(SideMode.class), side -> SideMode.NONE);
        assertSame(full, SidedEnergy.view(full, machine, Direction.UP));

        var battery = new SideConfig(EnumSet.allOf(SideMode.class), true, side -> switch (side) {
            case UP -> SideMode.INPUT;
            case DOWN -> SideMode.OUTPUT;
            case NORTH -> SideMode.BOTH;
            default -> SideMode.NONE;
        });
        assertNull(SidedEnergy.view(full, battery, Direction.EAST));
        assertSame(full, SidedEnergy.view(full, battery, Direction.NORTH));
        assertSame(full, SidedEnergy.view(full, battery, null), "the machine's own access is unrestricted");
        var inputOnly = SidedEnergy.view(full, battery, Direction.UP);
        var outputOnly = SidedEnergy.view(full, battery, Direction.DOWN);
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
        // Offset 7 is the unused slot in a channel's button group, and a battery has no resource channel.
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
    void generatorFacesOnlyChooseWhetherFuelMayEnter(MinecraftServer server) {
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        var sides = generator.sideConfig();
        assertFalse(sides.governsEnergy(), "a generator pushes energy out of any face, configured or not");
        for (Direction side : Direction.values()) {
            assertEquals(SideMode.NONE, sides.mode(side));
            // A closed face still hands energy over; it just refuses fuel.
            assertTrue(sides.allowsEnergyOutput(side));
            assertFalse(sides.allowsItemInput(side));
        }
        SideConfigMenu.handleButton(generator, Direction.UP.ordinal());
        assertEquals(SideMode.INPUT, sides.mode(Direction.UP));
        assertTrue(sides.allowsItemInput(Direction.UP));
        // The generator has no result item, so output is not on offer at all.
        assertThrows(IllegalArgumentException.class, () -> sides.set(Direction.UP, SideMode.OUTPUT));
        SideConfigMenu.handleButton(generator, Direction.UP.ordinal());
        assertEquals(SideMode.NONE, sides.mode(Direction.UP));
        assertEquals(SideMode.NONE.ordinal(),
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

    @Test
    void autoTransferTogglesFlipAndSurviveReload(MinecraftServer server) {
        var furnace = new dev.futuretech.block.entity.ElectricFurnaceBlockEntity(BlockPos.ZERO,
                ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
        var auto = furnace.autoTransfer();
        assertFalse(auto.isPulling(), "a fresh machine never touches a neighbour on its own");
        assertFalse(auto.isPushing());

        assertTrue(SideConfigMenu.handleButton(furnace, SideConfigMenu.BUTTON_AUTO_PULL));
        assertTrue(auto.isPulling());
        assertFalse(auto.isPushing(), "the toggles are independent");
        assertTrue(SideConfigMenu.handleButton(furnace, SideConfigMenu.BUTTON_AUTO_PUSH));
        assertTrue(auto.isPushing());
        assertEquals(1, furnace.menuData().get(
                dev.futuretech.block.entity.ElectricFurnaceBlockEntity.DATA_AUTO_BASE));
        assertEquals(1, furnace.menuData().get(
                dev.futuretech.block.entity.ElectricFurnaceBlockEntity.DATA_AUTO_BASE + 1));

        // Clicking again turns it back off.
        assertTrue(SideConfigMenu.handleButton(furnace, SideConfigMenu.BUTTON_AUTO_PULL));
        assertFalse(auto.isPulling());

        var restored = new dev.futuretech.block.entity.ElectricFurnaceBlockEntity(BlockPos.ZERO,
                ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                furnace.saveWithoutMetadata(server.registryAccess())));
        assertFalse(restored.autoTransfer().isPulling());
        assertTrue(restored.autoTransfer().isPushing());
    }

    @Test
    void aMachineWithoutAnInventoryHasNoToggles(MinecraftServer server) {
        var battery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        assertFalse(ModBlocks.BATTERY_MK1.get().supportsAutoPull());
        assertFalse(ModBlocks.BATTERY_MK1.get().supportsAutoPush());
        // The button ids must be ignored rather than crash on a crafted packet.
        assertFalse(SideConfigMenu.handleButton(battery, SideConfigMenu.BUTTON_AUTO_PULL));
        assertFalse(SideConfigMenu.handleButton(battery, SideConfigMenu.BUTTON_AUTO_PUSH));
        // A generator pulls fuel but has no result item to hand back.
        assertTrue(ModBlocks.SOLID_FUEL_GENERATOR.get().supportsAutoPull());
        assertFalse(ModBlocks.SOLID_FUEL_GENERATOR.get().supportsAutoPush());
    }
}
