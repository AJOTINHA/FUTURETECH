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
        assertEquals(PortableBatteryItem.CAPACITY, handler.getCapacityAsInt());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(PortableBatteryItem.CAPACITY, handler.insert(PortableBatteryItem.CAPACITY + 5, transaction));
            transaction.commit();
        }
        assertEquals(PortableBatteryItem.CAPACITY, PortableBatteryItem.storedEnergy(battery));
        assertEquals(PortableBatteryItem.CAPACITY, battery.get(ModDataComponents.ENERGY.get()));
        assertTrue(ModItems.PORTABLE_BATTERY.get().isBarVisible(battery));
        assertEquals(13, ModItems.PORTABLE_BATTERY.get().getBarWidth(battery));
    }

    @Test
    void aBatteryBlockInThePocketTakesChargeUpToItsTiersCapacity() {
        var portable = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        portable.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.CAPACITY);
        var mk1 = ModItems.BATTERY_MK1.get().getDefaultInstance();
        var mk3 = ModItems.BATTERY_MK1.get().getDefaultInstance();
        mk3.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MachineLevel.MK, 3));
        var source = ItemAccess.forStack(portable).getCapability(Capabilities.Energy.ITEM);
        var target1 = ItemAccess.forStack(mk1).getCapability(Capabilities.Energy.ITEM);
        var target3 = ItemAccess.forStack(mk3).getCapability(Capabilities.Energy.ITEM);
        assertEquals(100_000, target1.getCapacityAsInt());
        assertEquals(400_000, target3.getCapacityAsInt());
        try (var transaction = Transaction.openRoot()) {
            assertEquals(PortableBatteryItem.CHARGE_PER_TICK, EnergyHandlerUtil.move(source, target1, PortableBatteryItem.CHARGE_PER_TICK, transaction));
            transaction.commit();
        }
        assertEquals(PortableBatteryItem.CHARGE_PER_TICK, BatteryBlockItem.storedEnergy(mk1));
        assertEquals(PortableBatteryItem.CAPACITY - PortableBatteryItem.CHARGE_PER_TICK, PortableBatteryItem.storedEnergy(portable));
        // A full MK1 block takes no more; the portable battery keeps the rest.
        try (var transaction = Transaction.openRoot()) {
            EnergyHandlerUtil.move(source, target1, Integer.MAX_VALUE, transaction);
            transaction.commit();
        }
        assertEquals(100_000, BatteryBlockItem.storedEnergy(mk1));
        assertTrue(EnergyHandlerUtil.isFull(target1));
        assertEquals(PortableBatteryItem.CAPACITY - 100_000, PortableBatteryItem.storedEnergy(portable));
    }
}
