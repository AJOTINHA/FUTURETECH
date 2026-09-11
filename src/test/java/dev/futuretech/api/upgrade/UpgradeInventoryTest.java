package dev.futuretech.api.upgrade;

import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class UpgradeInventoryTest {
    @Test
    void onlyTaggedItemsFitAndTheTagIsEmptyForNow(MinecraftServer server) {
        var upgrades = new UpgradeInventory(() -> {});
        assertEquals(UpgradeInventory.SLOTS, upgrades.getContainerSize());
        assertFalse(UpgradeInventory.isUpgrade(new ItemStack(Items.DIAMOND)));
        assertFalse(upgrades.canPlaceItem(0, new ItemStack(Items.REDSTONE)));
        assertEquals(1, upgrades.getMaxStackSize());
        var tagged = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                .get(UpgradeInventory.UPGRADES);
        assertTrue(tagged.isEmpty() || tagged.get().size() == 0, "no upgrade items exist yet");
    }

    @Test
    void contentsSurviveReloadOnBothMachines(MinecraftServer server) {
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        generator.upgrades().setItem(2, new ItemStack(Items.DIAMOND));
        var restoredGenerator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        restoredGenerator.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                generator.saveWithoutMetadata(server.registryAccess())));
        assertTrue(restoredGenerator.upgrades().getItem(2).is(Items.DIAMOND));
        assertTrue(restoredGenerator.upgrades().getItem(0).isEmpty());

        var battery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        battery.upgrades().setItem(3, new ItemStack(Items.EMERALD));
        var restoredBattery = new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
        restoredBattery.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                battery.saveWithoutMetadata(server.registryAccess())));
        assertTrue(restoredBattery.upgrades().getItem(3).is(Items.EMERALD));
    }

    @Test
    void slotLayoutMatchesTheFirstTab(MinecraftServer server) {
        assertEquals(0, UpgradeSlots.TAB_INDEX);
        assertEquals(28, UpgradeSlots.slotY());
        assertEquals(176 + 5, UpgradeSlots.slotX(176, 0));
        assertEquals(176 + 5 + 3 * UpgradeSlots.CELL, UpgradeSlots.slotX(176, 3));
        assertEquals(UpgradeSlots.PADDING * 2 + UpgradeSlots.ROW, UpgradeSlots.TAB_WIDTH);
    }
}
