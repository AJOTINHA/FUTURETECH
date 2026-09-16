package dev.futuretech.item;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PortableBatteryItemTest {
    @Test
    void switchingOnGlintsAndSwitchingOffClearsBothTheSwitchAndTheGlint() {
        var battery = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        assertFalse(PortableBatteryItem.isActive(battery));
        assertNull(battery.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        PortableBatteryItem.setActive(battery, true);
        assertTrue(PortableBatteryItem.isActive(battery));
        assertEquals(Boolean.TRUE, battery.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        PortableBatteryItem.setActive(battery, false);
        assertFalse(PortableBatteryItem.isActive(battery));
        assertEquals(Boolean.FALSE, battery.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }

    @Test
    void theBatteryStoresEnergyInItsComponentThroughTheItemCapability() {
        var battery = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        var handler = ItemAccess.forStack(battery).getCapability(Capabilities.Energy.ITEM);
        assertNotNull(handler);
        assertEquals(PortableBatteryItem.Tier.MK1.capacity, handler.getCapacityAsInt());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(PortableBatteryItem.Tier.MK1.capacity, handler.insert(PortableBatteryItem.Tier.MK1.capacity + 5, transaction));
            transaction.commit();
        }
        assertEquals(PortableBatteryItem.Tier.MK1.capacity, PortableBatteryItem.storedEnergy(battery));
        assertEquals(PortableBatteryItem.Tier.MK1.capacity, battery.get(ModDataComponents.ENERGY.get()));
        assertTrue(ModItems.PORTABLE_BATTERY.get().isBarVisible(battery));
        assertEquals(13, ModItems.PORTABLE_BATTERY.get().getBarWidth(battery));
    }

    @Test
    void eachLevelDoublesCapacityAndRateAndAKitInTheGridKeepsTheChargeAndTheSwitch(net.minecraft.server.MinecraftServer server) {
        var tiers = java.util.List.of(ModItems.PORTABLE_BATTERY, ModItems.PORTABLE_BATTERY_MK2, ModItems.PORTABLE_BATTERY_MK3, ModItems.PORTABLE_BATTERY_MK4);
        for (int i = 0; i < tiers.size(); i++) {
            var item = tiers.get(i).get();
            assertEquals(i + 1, item.tier.mk());
            assertEquals(200_000 << i, item.tier.capacity);
            assertEquals(500 << i, item.tier.chargePerTick);
            assertEquals(item.tier.capacity, ItemAccess.forStack(item.getDefaultInstance()).getCapability(Capabilities.Energy.ITEM).getCapacityAsInt());
        }
        var kits = java.util.List.of(ModItems.UPGRADE_KIT_MK2, ModItems.UPGRADE_KIT_MK3, ModItems.UPGRADE_KIT_MK4);
        var battery = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        battery.set(ModDataComponents.ENERGY.get(), 150_000);
        PortableBatteryItem.setActive(battery, true);
        for (int i = 0; i < kits.size(); i++) {
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(battery, kits.get(i).get().getDefaultInstance()));
            var recipe = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, null);
            assertTrue(recipe.isPresent(), "kit MK" + (i + 2));
            battery = recipe.get().value().assemble(input);
            assertSame(tiers.get(i + 1).get(), battery.getItem());
            // The charge and the switch travel with the upgrade.
            assertEquals(150_000, PortableBatteryItem.storedEnergy(battery));
            assertTrue(PortableBatteryItem.isActive(battery));
            assertEquals(Boolean.TRUE, battery.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        }
    }

    @Test
    void aBatteryBlockInThePocketTakesChargeUpToItsTiersCapacity() {
        var portable = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        portable.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.Tier.MK1.capacity);
        var mk1 = ModItems.BATTERY_MK1.get().getDefaultInstance();
        var mk3 = ModItems.BATTERY_MK1.get().getDefaultInstance();
        mk3.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MachineLevel.MK, 3));
        var source = ItemAccess.forStack(portable).getCapability(Capabilities.Energy.ITEM);
        var target1 = ItemAccess.forStack(mk1).getCapability(Capabilities.Energy.ITEM);
        var target3 = ItemAccess.forStack(mk3).getCapability(Capabilities.Energy.ITEM);
        assertEquals(100_000, target1.getCapacityAsInt());
        assertEquals(400_000, target3.getCapacityAsInt());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(PortableBatteryItem.Tier.MK1.chargePerTick, EnergyHandlerUtil.move(source, target1, PortableBatteryItem.Tier.MK1.chargePerTick, transaction));
            transaction.commit();
        }
        assertEquals(PortableBatteryItem.Tier.MK1.chargePerTick, BatteryBlockItem.storedEnergy(mk1));
        assertEquals(PortableBatteryItem.Tier.MK1.capacity - PortableBatteryItem.Tier.MK1.chargePerTick, PortableBatteryItem.storedEnergy(portable));
        // A full MK1 block takes no more; the portable battery keeps the rest.
        try (var transaction = Transaction.openRoot()) {
            EnergyHandlerUtil.move(source, target1, Integer.MAX_VALUE, transaction);
            transaction.commit();
        }
        assertEquals(100_000, BatteryBlockItem.storedEnergy(mk1));
        assertTrue(EnergyHandlerUtil.isFull(target1));
        assertEquals(PortableBatteryItem.Tier.MK1.capacity - 100_000, PortableBatteryItem.storedEnergy(portable));
    }
}
