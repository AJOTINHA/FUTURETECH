package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.ChargerBlockEntity.*;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.item.PortableBatteryItem;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ChargerTest {
    @Test
    void chargesTheItemAtTheLevelsRateAndMovesItToTheOutputOnceFull(net.minecraft.server.MinecraftServer server) {
        var charger = charger(1);
        charge(charger, MachineLevel.capacity(CAPACITY, 1));
        var battery = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        battery.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.Tier.MK1.capacity - 2_500);
        assertTrue(charger.canPlaceItem(SLOT_INPUT, battery));
        charger.setItem(SLOT_INPUT, battery);
        assertTrue(tick(charger));
        assertEquals(PortableBatteryItem.Tier.MK1.capacity - 1_500, PortableBatteryItem.storedEnergy(charger.getItem(SLOT_INPUT)));
        assertEquals(MachineLevel.capacity(CAPACITY, 1) - CHARGE_PER_TICK, charger.energy().getAmountAsInt());
        assertTrue(tick(charger));
        assertTrue(tick(charger));
        // Full now: the next tick does no charging and moves the battery over.
        assertFalse(tick(charger));
        assertTrue(charger.getItem(SLOT_INPUT).isEmpty());
        assertEquals(PortableBatteryItem.Tier.MK1.capacity, PortableBatteryItem.storedEnergy(charger.getItem(SLOT_OUTPUT)));
        assertFalse(tick(charger));
    }

    @Test
    void aFullOutputSlotHoldsTheChargedItemInPlaceUntilItIsTaken(net.minecraft.server.MinecraftServer server) {
        var charger = charger(1);
        charge(charger, CAPACITY);
        var first = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        first.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.Tier.MK1.capacity);
        charger.setItem(SLOT_OUTPUT, first);
        var second = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        second.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.Tier.MK1.capacity);
        charger.setItem(SLOT_INPUT, second);
        assertFalse(tick(charger));
        assertSame(second, charger.getItem(SLOT_INPUT));
        charger.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
        assertFalse(tick(charger));
        assertSame(second, charger.getItem(SLOT_OUTPUT));
    }

    @Test
    void onlyEnergyItemsGoInAndHigherLevelsChargeFaster(net.minecraft.server.MinecraftServer server) {
        var charger = charger(1);
        assertFalse(charger.canPlaceItem(SLOT_INPUT, new ItemStack(Items.COBBLESTONE)));
        assertFalse(charger.canPlaceItem(SLOT_OUTPUT, ModItems.PORTABLE_BATTERY.get().getDefaultInstance()));
        assertTrue(charger.canPlaceItem(SLOT_INPUT, ModItems.BATTERY_MK1.get().getDefaultInstance()));
        var mk4 = charger(4);
        charge(mk4, MachineLevel.capacity(CAPACITY, 4));
        var block = ModItems.BATTERY_MK1.get().getDefaultInstance();
        mk4.setItem(SLOT_INPUT, block);
        assertTrue(tick(mk4));
        assertEquals(MachineLevel.consumption(CHARGE_PER_TICK, 4), dev.futuretech.item.BatteryBlockItem.storedEnergy(mk4.getItem(SLOT_INPUT)));
        assertTrue(MachineLevel.consumption(CHARGE_PER_TICK, 4) > CHARGE_PER_TICK);
        // No energy in the buffer: nothing moves and the machine is idle.
        var empty = charger(1);
        empty.setItem(SLOT_INPUT, ModItems.PORTABLE_BATTERY.get().getDefaultInstance());
        assertFalse(tick(empty));
    }

    private static ChargerBlockEntity charger(int mk) {
        return new ChargerBlockEntity(BlockPos.ZERO, ModBlocks.CHARGER.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    private static void charge(ChargerBlockEntity charger, int amount) {
        for (int filled = 0; filled < amount; filled += INPUT_PER_TICK) {
            charger.beginTick();
            try (var transaction = Transaction.openRoot()) {
                charger.energy().insert(Math.min(INPUT_PER_TICK, amount - filled), transaction);
                transaction.commit();
            }
        }
        charger.beginTick();
    }

    private static boolean tick(ChargerBlockEntity charger) {
        charger.beginTick();
        return charger.charge();
    }
}
