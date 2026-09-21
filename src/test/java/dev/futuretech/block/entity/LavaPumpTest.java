package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.LavaPumpBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The lava pump away from any world: the tank and its levels, what a source costs, and buckets.
 * The pipe, the search through the pool and the stone left behind need a level, and are the
 * game test's.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class LavaPumpTest {
    private static LavaPumpBlockEntity machine(int mk) {
        return new LavaPumpBlockEntity(BlockPos.ZERO, ModBlocks.LAVA_PUMP.get().defaultBlockState().setValue(MachineLevel.MK, mk));
    }

    private static void charge(LavaPumpBlockEntity pump, int amount) {
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

    private static void fill(LavaPumpBlockEntity pump, int amount) {
        try (var tx = Transaction.openRoot()) {
            assertEquals(amount, pump.lava().insert(FluidResource.of(Fluids.LAVA), amount, tx));
            tx.commit();
        }
    }

    @Test
    void aSourceCostsTheSameOnEveryLevelAndHigherLevelsTakeItFaster(MinecraftServer server) {
        var mk1 = machine(1);
        assertEquals(SOURCE_VOLUME / WORK_TICKS, mk1.ratePerTick());
        assertEquals(WORK_TICKS * ENERGY_PER_TICK, mk1.workTicks() * mk1.consumptionPerTick());
        var mk4 = machine(4);
        assertTrue(mk4.workTicks() < mk1.workTicks(), "MK4 takes a source in fewer ticks");
        assertTrue(mk4.consumptionPerTick() > mk1.consumptionPerTick(), "and draws more while it does");
        // The ticks round to whole numbers (50 / 3 is 17), so the bucket costs the same within a few percent.
        int mk1Bucket = mk1.workTicks() * mk1.consumptionPerTick();
        int mk4Bucket = mk4.workTicks() * mk4.consumptionPerTick();
        assertTrue(Math.abs(mk4Bucket - mk1Bucket) * 100 <= 5 * mk1Bucket, "the bucket costs about the same: " + mk4Bucket);
        assertEquals(ratePerTick(4), mk4.ratePerTick());
    }

    @Test
    void theTankDoublesWithTheLevelAndOnlyTakesLava(MinecraftServer server) {
        assertEquals(TANK_CAPACITY, tankCapacity(1));
        assertEquals(2 * TANK_CAPACITY, tankCapacity(2));
        assertEquals(8 * TANK_CAPACITY, tankCapacity(4));
        var pump = machine(2);
        assertEquals(2 * TANK_CAPACITY, pump.tankCapacity());
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, pump.lava().insert(FluidResource.of(Fluids.WATER), 1_000, tx), "water is refused");
            assertEquals(2 * TANK_CAPACITY, pump.lava().insert(FluidResource.of(Fluids.LAVA), 3 * TANK_CAPACITY, tx),
                    "lava fills to the level's capacity");
        }
        // A kit upgrading the block under a full pump makes room for more.
        fill(pump, 2 * TANK_CAPACITY);
        pump.setBlockState(pump.getBlockState().setValue(MachineLevel.MK, 3));
        assertEquals(4 * TANK_CAPACITY, pump.tankCapacity());
        fill(pump, 1_000);
        assertEquals(2 * TANK_CAPACITY + 1_000, pump.lavaAmount());
    }

    @Test
    void anEmptyBucketInTheInputSlotComesOutFull(MinecraftServer server) {
        var pump = machine(1);
        pump.setItem(SLOT_INPUT, new ItemStack(Items.BUCKET, 2));
        assertFalse(pump.fillContainer(), "nothing in the tank yet");
        fill(pump, 1_500);
        assertTrue(pump.fillContainer());
        assertEquals(500, pump.lavaAmount());
        assertEquals(1, pump.getItem(SLOT_INPUT).getCount());
        assertTrue(pump.getItem(SLOT_OUTPUT).is(Items.LAVA_BUCKET));
        assertFalse(pump.fillContainer(), "half a bucket is not a bucket");
        // A water bucket has no room for lava, so it is refused at the slot.
        assertFalse(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.WATER_BUCKET)));
        assertFalse(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.IRON_INGOT)));
        assertTrue(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.BUCKET)));
        assertTrue(pump.canPlaceItem(SLOT_INPUT, new ItemStack(Items.LAVA_BUCKET)), "lava's own container is taken, and left be");
    }

    @Test
    void chargingStaysWithinTheBuffer(MinecraftServer server) {
        var pump = machine(1);
        charge(pump, CAPACITY);
        assertEquals(CAPACITY, pump.energy().getAmountAsInt());
        pump.beginTick();
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, pump.energy().insert(1, tx), "full");
        }
    }
}
