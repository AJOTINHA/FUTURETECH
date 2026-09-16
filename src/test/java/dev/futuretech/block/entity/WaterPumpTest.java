package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.WaterPumpBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The water pump: sources around it and energy in, water in the tank and full buckets out. */
@ExtendWith(EphemeralTestServerProvider.class)
class WaterPumpTest {
    private static WaterPumpBlockEntity machine(int mk, int sources) {
        var pump = new WaterPumpBlockEntity(BlockPos.ZERO, ModBlocks.WATER_PUMP.get().defaultBlockState().setValue(MachineLevel.MK, mk));
        pump.setSources(sources);
        return pump;
    }

    private static void charge(WaterPumpBlockEntity pump, int amount) {
        while (amount > 0) {
            pump.beginTick();
            try (var tx = Transaction.openRoot()) {
                int inserted = pump.energy().insert(amount, tx);
                assertTrue(inserted > 0);
                amount -= inserted;
                tx.commit();
            }
        }
    }

    private static void tick(WaterPumpBlockEntity pump) {
        pump.beginTick();
        pump.pump();
    }

    @Test
    void pumpsWaterAtTheRateTheSourcesGiveAndStopsWhenFull(MinecraftServer server) {
        var pump = machine(1, 4);
        charge(pump, CAPACITY);
        tick(pump);
        assertEquals(4 * WATER_PER_SOURCE, pump.waterAmount());
        assertEquals(CAPACITY - ENERGY_PER_TICK, pump.energy().getAmountAsInt());
        assertEquals(1, pump.menuData().get(DATA_PUMPING));
        for (int t = 0; t < 10_000; t++) tick(pump);
        assertEquals(TANK_CAPACITY, pump.waterAmount());
        int energy = pump.energy().getAmountAsInt();
        tick(pump);
        // Full: no water made, no energy spent, and the front goes dark.
        assertEquals(energy, pump.energy().getAmountAsInt());
        assertEquals(0, pump.menuData().get(DATA_PUMPING));
    }

    @Test
    void tooFewSourcesOrNoEnergyMeansNoWater(MinecraftServer server) {
        var pump = machine(1, 2);
        tick(pump);
        assertEquals(0, pump.waterAmount(), "no energy");
        charge(pump, ENERGY_PER_TICK);
        pump.setSources(1);
        tick(pump);
        assertEquals(0, pump.waterAmount(), "one source is a puddle, not a supply");
        assertEquals(0, pump.ratePerTick());
        assertEquals(ENERGY_PER_TICK, pump.energy().getAmountAsInt(), "waiting costs nothing");
        pump.setSources(2);
        tick(pump);
        assertEquals(2 * WATER_PER_SOURCE, pump.waterAmount());
        assertEquals(0, pump.energy().getAmountAsInt());
    }

    @Test
    void higherLevelsPumpFasterAndDoubleTheTank(MinecraftServer server) {
        int mk1 = WaterPumpBlockEntity.ratePerTick(6, 1);
        int mk4 = WaterPumpBlockEntity.ratePerTick(6, 4);
        assertEquals(6 * WATER_PER_SOURCE, mk1);
        assertEquals(8 * mk1, mk4, "doubled three times");
        assertEquals(2 * mk1, WaterPumpBlockEntity.ratePerTick(6, 2));
        assertTrue(MachineLevel.consumption(ENERGY_PER_TICK, 2) > ENERGY_PER_TICK, "and a little more energy");
        assertEquals(TANK_CAPACITY, tankCapacity(1));
        assertEquals(2 * TANK_CAPACITY, tankCapacity(2));
        assertEquals(8 * TANK_CAPACITY, tankCapacity(4));
        var pump = machine(2, 6);
        assertEquals(2 * TANK_CAPACITY, pump.tankCapacity());
        charge(pump, CAPACITY);
        for (int t = 0; t < 10_000; t++) tick(pump);
        assertEquals(2 * TANK_CAPACITY, pump.waterAmount(), "the tank fills to the level's capacity");
        // A kit upgrading the block under a full pump makes room for more.
        pump.setBlockState(pump.getBlockState().setValue(MachineLevel.MK, 3));
        assertEquals(4 * TANK_CAPACITY, pump.tankCapacity());
        tick(pump);
        assertTrue(pump.waterAmount() > 2 * TANK_CAPACITY);
    }

    @Test
    void anEmptyBucketInTheInputSlotComesOutFull(MinecraftServer server) {
        var pump = machine(1, 6);
        pump.setItem(SLOT_INPUT, new ItemStack(Items.BUCKET, 2));
        assertFalse(pump.fillContainer(), "nothing in the tank yet");
        charge(pump, CAPACITY);
        while (pump.waterAmount() < 1_000) tick(pump);
        int water = pump.waterAmount();
        assertTrue(pump.fillContainer());
        assertEquals(water - 1_000, pump.waterAmount());
        assertEquals(1, pump.getItem(SLOT_INPUT).getCount());
        assertTrue(pump.getItem(SLOT_OUTPUT).is(Items.WATER_BUCKET));
        // A lava bucket has no room for water, so it is refused at the slot.
        assertFalse(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET)));
        assertFalse(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.IRON_INGOT)));
        assertTrue(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.BUCKET)));
    }
}
