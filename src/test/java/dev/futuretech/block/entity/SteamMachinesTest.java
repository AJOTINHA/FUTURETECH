package dev.futuretech.block.entity;

import static org.junit.jupiter.api.Assertions.*;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SidedFluids;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModFluids;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class SteamMachinesTest {
    private BoilerBlockEntity boiler() { return new BoilerBlockEntity(BlockPos.ZERO,ModBlocks.BOILER.get().defaultBlockState()); }
    private SteamTurbineBlockEntity turbine() { return new SteamTurbineBlockEntity(BlockPos.ZERO,ModBlocks.STEAM_TURBINE.get().defaultBlockState()); }
    private FluidResource water() { return FluidResource.of(Fluids.WATER); }
    private FluidResource steam() { return FluidResource.of(ModFluids.STEAM.get()); }
    private int insert(ResourceHandler<FluidResource> tank,FluidResource fluid,int amount) {
        try(var tx=Transaction.openRoot()) { int moved=tank.insert(fluid,amount,tx); tx.commit(); return moved; }
    }
    /** One coal is 1 600 heat; a level pays less heat per mB, so the same coal boils more water and the turbine returns more. */
    @Test void eachLevelStretchesACoalFurther(MinecraftServer server) {
        for(int mk=1;mk<=4;mk++) {
            var boiler=boiler();var turbine=turbine();
            boiler.setBlockState(boiler.getBlockState().setValue(MachineLevel.MK,mk));
            turbine.setBlockState(turbine.getBlockState().setValue(MachineLevel.MK,mk));
            boiler.setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL));
            boiler.sideConfig().set(Direction.DOWN,SideMode.INPUT);boiler.sideConfig().set(Direction.UP,SideMode.OUTPUT);
            insert(boiler.handler(Direction.DOWN),water(),8000);
            int percent=BoilerBlockEntity.MK_HEAT_PERCENT[mk-1];
            assertEquals(percent,boiler.heatPercent());
            long harvested=0;
            for(int tick=0;tick<1800;tick++) {
                boiler.boil(server.fuelValues());
                try(var tx=Transaction.openRoot()) {
                    ResourceHandlerUtil.move(boiler.handler(Direction.UP),turbine.steam(),resource -> true,16000,tx);
                    tx.commit();
                }
                turbine.generateEnergy();
                // The buffer could not hold a coal's worth; drain it like a cable would, one tick's output at a time.
                harvested+=turbine.energy().getAmountAsInt();((TickLimitedEnergyHandler)turbine.energy()).set(0);
            }
            int boiled=1600*100/percent;
            assertEquals(8000-boiled,boiler.waterAmount());
            assertEquals(BoilerBlockEntity.NO_FUEL,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
            assertTrue(boiler.getItem(BoilerBlockEntity.SLOT_FUEL).isEmpty());
            assertEquals(0,turbine.steamAmount());
            assertEquals((long)boiled*BoilerBlockEntity.STEAM_PER_WATER*SteamTurbineBlockEntity.FE_PER_MB,harvested);
        }
    }
    /** Efficiency upgrades take 15% each off the heat bill, on top of the level's own share. */
    @Test void efficiencyUpgradesCutTheHeatBill(MinecraftServer server) {
        var boiler=boiler();
        boiler.setBlockState(boiler.getBlockState().setValue(MachineLevel.MK,4));
        for(int slot=0;slot<3;slot++) boiler.upgrades().setItem(slot,new ItemStack(ModItems.EFFICIENCY_UPGRADE.get()));
        assertEquals(70*55/100,boiler.heatPercent());
        boiler.setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL));
        insert(boiler.tanks(),water(),8000);
        for(int tick=0;tick<2000;tick++) {
            boiler.boil(server.fuelValues());
            ((FluidStacksResourceHandler)boiler.tanks()).set(1,FluidResource.EMPTY,0);
        }
        // 1 600 heat at 38% per mB boils 4 210 mB, against 1 600 on a plain MK1.
        assertEquals(8000-1600*100/38,boiler.waterAmount());
        assertTrue(boiler.getItem(BoilerBlockEntity.SLOT_FUEL).isEmpty());
    }
    /** A speed upgrade adds the level's rate again on both machines, at a tenth more heat per mB and a tenth less FE per mB. */
    @Test void speedUpgradesTradeYieldForRate(MinecraftServer server) {
        var boiler=boiler();var turbine=turbine();
        boiler.setBlockState(boiler.getBlockState().setValue(MachineLevel.MK,2));
        turbine.setBlockState(turbine.getBlockState().setValue(MachineLevel.MK,2));
        boiler.upgrades().setItem(0,new ItemStack(ModItems.SPEED_UPGRADE.get()));boiler.upgrades().setItem(1,new ItemStack(ModItems.SPEED_UPGRADE.get()));
        turbine.upgrades().setItem(0,new ItemStack(ModItems.SPEED_UPGRADE.get()));
        assertEquals(9,boiler.waterPerTick(),"MK2 boils 3 mB/t; two speed upgrades make it three times that");
        assertEquals(90*120/100,boiler.heatPercent(),"MK2 pays 90%, plus 10% per speed upgrade");
        assertEquals(9,boiler.menuData().get(BoilerBlockEntity.DATA_MAX_WATER));
        boiler.setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL));insert(boiler.tanks(),water(),8000);
        boiler.boil(server.fuelValues());
        assertEquals(90,boiler.menuData().get(BoilerBlockEntity.DATA_RATE));
        assertEquals(60,turbine.steamPerTick(),"MK2 draws 30 mB/t; one speed upgrade doubles it");
        assertEquals(9,turbine.fePerMb());
        assertEquals(540,turbine.menuData().get(SteamTurbineBlockEntity.DATA_MAX_RATE));
        insert(turbine.steam(),steam(),1000);turbine.generateEnergy();
        assertEquals(940,turbine.steamAmount());assertEquals(540,turbine.energy().getAmountAsInt());
    }
    /** With the lava upgrade the fuel slot takes lava buckets, input faces take lava, and each mB is five heat. */
    @Test void lavaUpgradeHeatsWithLava(MinecraftServer server) {
        var boiler=boiler();
        assertEquals(BoilerBlockEntity.SOLID,boiler.fuelMode());
        assertEquals(0,insert(boiler.tanks(),FluidResource.of(Fluids.LAVA),1000));
        boiler.upgrades().setItem(0,new ItemStack(ModItems.LAVA_UPGRADE.get()));
        assertEquals(BoilerBlockEntity.LAVA,boiler.fuelMode());
        // The slot is gone from the screen: lava comes through input faces, or from a bucket clicked on the block.
        assertFalse(boiler.canPlaceItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL)));
        assertFalse(boiler.canPlaceItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.LAVA_BUCKET)));
        boiler.sideConfig().set(Direction.NORTH,SideMode.INPUT);
        assertEquals(1000,insert(boiler.handler(Direction.NORTH),FluidResource.of(Fluids.LAVA),1000));
        assertEquals(1000,insert(boiler.handler(null),FluidResource.of(Fluids.LAVA),1000),"A bucket on the block fills the lava tank");
        assertEquals(2000,boiler.lavaAmount());
        boiler.setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL));
        insert(boiler.tanks(),water(),8000);
        boiler.boil(server.fuelValues());
        assertEquals(BoilerBlockEntity.ACTIVE,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
        assertEquals(1,boiler.getItem(BoilerBlockEntity.SLOT_FUEL).getCount(),"Coal in the slot is left alone");
        assertEquals(1999,boiler.lavaAmount());
        // One mB bought five heat, two were spent on 2 mB of water.
        assertEquals(3,boiler.menuData().get(BoilerBlockEntity.DATA_BURN));
        assertEquals(BoilerBlockEntity.LAVA,boiler.menuData().get(BoilerBlockEntity.DATA_MODE));
        assertEquals(1999,boiler.menuData().get(BoilerBlockEntity.DATA_RESERVE));
        ((FluidStacksResourceHandler)boiler.tanks()).set(2,FluidResource.EMPTY,0);
        boiler.boil(server.fuelValues());boiler.boil(server.fuelValues());boiler.boil(server.fuelValues());
        assertEquals(BoilerBlockEntity.NO_LAVA,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
    }
    /** With the energy upgrade input faces offer an energy buffer, and heat is bought at 300 FE a unit. */
    @Test void energyUpgradeHeatsWithEnergyAndNeverPaysForItself(MinecraftServer server) {
        var boiler=boiler();
        boiler.sideConfig().set(Direction.NORTH,SideMode.INPUT);
        assertNull(boiler.energyHandler(Direction.NORTH),"No buffer is offered without the upgrade");
        boiler.upgrades().setItem(0,new ItemStack(ModItems.ENERGY_UPGRADE.get()));
        assertEquals(BoilerBlockEntity.ENERGY,boiler.fuelMode());
        assertFalse(boiler.canPlaceItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL)));
        assertNull(boiler.energyHandler(Direction.SOUTH),"Closed faces offer nothing");
        var handler=boiler.energyHandler(Direction.NORTH);
        assertNotNull(handler);
        try(var tx=Transaction.openRoot()) { assertEquals(2000,handler.insert(5000,tx));assertEquals(0,handler.extract(100,tx));tx.commit(); }
        insert(boiler.tanks(),water(),8000);
        boiler.boil(server.fuelValues());
        assertEquals(BoilerBlockEntity.ACTIVE,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
        assertEquals(2000-6*BoilerBlockEntity.FE_PER_HEAT,boiler.energy().getAmountAsInt(),"Only six units fit in 2 000 FE");
        assertEquals(4,boiler.menuData().get(BoilerBlockEntity.DATA_BURN));
        ((TickLimitedEnergyHandler)boiler.energy()).set(0);
        boiler.boil(server.fuelValues());boiler.boil(server.fuelValues());boiler.boil(server.fuelValues());
        assertEquals(BoilerBlockEntity.NO_ENERGY,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
        // Even the best boiler pays more per mB of steam than the turbine gives back for it.
        int bestPercent=BoilerBlockEntity.MK_HEAT_PERCENT[3]*(100-3*dev.futuretech.api.upgrade.UpgradeInventory.EFFICIENCY_PERCENT)/100;
        double cheapestFePerSteam=(double)BoilerBlockEntity.FE_PER_HEAT*bestPercent/100/BoilerBlockEntity.STEAM_PER_WATER;
        assertTrue(cheapestFePerSteam>SteamTurbineBlockEntity.FE_PER_MB,"Steam never pays for itself: "+cheapestFePerSteam);
        // Lava outranks energy when both are installed.
        boiler.setBlockState(boiler.getBlockState().setValue(MachineLevel.MK,2));
        boiler.upgrades().setItem(1,new ItemStack(ModItems.LAVA_UPGRADE.get()));
        assertEquals(BoilerBlockEntity.LAVA,boiler.fuelMode());
        assertNull(boiler.energyHandler(Direction.NORTH));
    }
    @Test void dryFullAndRedstonePausedBoilerPreservesFuelAndWater(MinecraftServer server) {
        var boiler=boiler();
        boiler.setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL,2));
        boiler.boil(server.fuelValues());
        assertEquals(2,boiler.getItem(0).getCount());
        assertEquals(BoilerBlockEntity.NO_WATER,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
        insert(boiler.tanks(),water(),1000);
        insert(boiler.tanks(),steam(),15995);
        boiler.boil(server.fuelValues());
        assertEquals(1000,boiler.waterAmount());assertEquals(2,boiler.getItem(0).getCount());
        assertEquals(BoilerBlockEntity.FULL,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
        ((FluidStacksResourceHandler)boiler.tanks()).set(1,FluidResource.EMPTY,0);
        boiler.boil(server.fuelValues());
        int water=boiler.waterAmount(),heat=boiler.menuData().get(BoilerBlockEntity.DATA_BURN);
        boiler.redstoneControl().setMode(RedstoneMode.HIGH);
        boiler.boil(server.fuelValues());
        assertEquals(water,boiler.waterAmount());assertEquals(heat,boiler.menuData().get(BoilerBlockEntity.DATA_BURN));
        assertEquals(BoilerBlockEntity.DISABLED,boiler.menuData().get(BoilerBlockEntity.DATA_STATUS));
    }
    @Test void tankRolesRejectWrongFluidsAndCachedFacesRespectChanges(MinecraftServer server) {
        var boiler=boiler();
        // Every face starts closed; the test opens the two it uses, and a turbine is closed too.
        for(Direction side:Direction.values()) { assertEquals(SideMode.NONE,boiler.sideConfig().mode(side));assertNull(boiler.handler(side)); }
        boiler.sideConfig().set(Direction.NORTH,SideMode.INPUT);boiler.sideConfig().set(Direction.UP,SideMode.OUTPUT);
        var input=boiler.handler(Direction.NORTH);var output=boiler.handler(Direction.UP);
        assertEquals(0,insert(input,steam(),1000));
        assertEquals(0,insert(input,FluidResource.of(Fluids.LAVA),1000));
        assertEquals(0,insert(output,water(),1000));
        assertEquals(1000,insert(input,water(),1000));
        try(var tx=Transaction.openRoot()) { assertEquals(0,output.extract(water(),1000,tx)); }
        boiler.setItem(0,new ItemStack(Items.COAL));boiler.boil(server.fuelValues());
        try(var tx=Transaction.openRoot()) {
            assertEquals(0,input.extract(steam(),1000,tx));
            assertEquals(20,output.extract(steam(),1000,tx));
            // Aborting this transfer must return all steam.
        }
        assertEquals(20,boiler.steamAmount());
        boiler.sideConfig().set(Direction.UP,SideMode.NONE);
        try(var tx=Transaction.openRoot()) { assertEquals(0,output.extract(steam(),1000,tx)); }
        var turbine=turbine();
        for(Direction side:Direction.values()) assertEquals(SideMode.NONE,turbine.sideConfig().mode(side));
        turbine.sideConfig().set(Direction.NORTH,SideMode.INPUT);
        var intake=SidedFluids.intake(turbine.steam(),turbine.sideConfig(),Direction.NORTH);
        assertEquals(0,insert(intake,water(),1000));assertEquals(1000,insert(intake,steam(),1000));
        try(var tx=Transaction.openRoot()) { assertEquals(0,intake.extract(steam(),1000,tx)); }
        turbine.sideConfig().set(Direction.NORTH,SideMode.NONE);
        assertEquals(0,insert(intake,steam(),1000));
    }
    @Test void waterBucketsLeaveOneEmptyBucketAndBlockedOutputRollsBack() {
        var boiler=boiler();
        boiler.setItem(BoilerBlockEntity.SLOT_INPUT,new ItemStack(Items.WATER_BUCKET));
        boiler.setItem(BoilerBlockEntity.SLOT_OUTPUT,new ItemStack(Items.BUCKET,16));
        assertFalse(boiler.drainContainer());assertEquals(0,boiler.waterAmount());
        assertTrue(boiler.getItem(BoilerBlockEntity.SLOT_INPUT).is(Items.WATER_BUCKET));
        boiler.setItem(BoilerBlockEntity.SLOT_OUTPUT,ItemStack.EMPTY);
        assertTrue(boiler.drainContainer());assertEquals(1000,boiler.waterAmount());
        assertTrue(boiler.getItem(BoilerBlockEntity.SLOT_INPUT).isEmpty());
        assertTrue(boiler.getItem(BoilerBlockEntity.SLOT_OUTPUT).is(Items.BUCKET));
    }
    @Test void turbineOnlyConsumesSteamForEnergyThatFitsAndRespectsRedstone() {
        var turbine=turbine();insert(turbine.steam(),steam(),100);
        ((TickLimitedEnergyHandler)turbine.energy()).set(SteamTurbineBlockEntity.CAPACITY-25);
        turbine.generateEnergy();assertEquals(98,turbine.steamAmount());
        assertEquals(SteamTurbineBlockEntity.CAPACITY-5,turbine.energy().getAmountAsInt());
        turbine.generateEnergy();assertEquals(98,turbine.steamAmount());
        assertEquals(SteamTurbineBlockEntity.FULL,turbine.menuData().get(SteamTurbineBlockEntity.DATA_STATUS));
        ((TickLimitedEnergyHandler)turbine.energy()).set(0);
        turbine.redstoneControl().setMode(RedstoneMode.HIGH);
        turbine.generateEnergy();assertEquals(98,turbine.steamAmount());assertEquals(0,turbine.energy().getAmountAsInt());
        turbine.redstoneControl().setPowered(true);
        turbine.generateEnergy();assertEquals(78,turbine.steamAmount());assertEquals(200,turbine.energy().getAmountAsInt());
    }
    @Test void upgradesIncreaseRateWithoutLosingBuffers(MinecraftServer server) {
        var boiler=boiler();var turbine=turbine();
        insert(boiler.tanks(),water(),1000);insert(turbine.steam(),steam(),1000);
        boiler.setItem(0,new ItemStack(Items.COAL));
        int steamTotal=0,energyTotal=0;
        for(int mk=1;mk<=4;mk++) {
            boiler.setBlockState(boiler.getBlockState().setValue(MachineLevel.MK,mk));
            turbine.setBlockState(turbine.getBlockState().setValue(MachineLevel.MK,mk));
            assertEquals(steamTotal,boiler.steamAmount());assertEquals(energyTotal,turbine.energy().getAmountAsInt());
            boiler.boil(server.fuelValues());turbine.generateEnergy();
            int rate=MachineLevel.consumption(20,mk);
            assertEquals(rate,boiler.menuData().get(BoilerBlockEntity.DATA_RATE));
            assertEquals(rate*SteamTurbineBlockEntity.FE_PER_MB,turbine.menuData().get(SteamTurbineBlockEntity.DATA_RATE));
            steamTotal+=rate;energyTotal+=rate*SteamTurbineBlockEntity.FE_PER_MB;
        }
    }
    @Test void reloadPreservesHeatFluidsEnergyAndSettings(MinecraftServer server) {
        var boiler=boiler();var turbine=turbine();
        insert(boiler.tanks(),water(),1000);insert(turbine.steam(),steam(),1000);
        boiler.setItem(0,new ItemStack(Items.COAL,3));
        boiler.boil(server.fuelValues());turbine.generateEnergy();
        boiler.sideConfig().set(Direction.EAST,SideMode.OUTPUT);
        boiler.redstoneControl().setMode(RedstoneMode.LOW);
        var restored=boiler();var restoredTurbine=turbine();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),boiler.saveWithoutMetadata(server.registryAccess())));
        restoredTurbine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),turbine.saveWithoutMetadata(server.registryAccess())));
        assertEquals(998,restored.waterAmount());assertEquals(20,restored.steamAmount());
        assertEquals(1598,restored.menuData().get(BoilerBlockEntity.DATA_BURN));assertEquals(2,restored.getItem(0).getCount());
        assertEquals(SideMode.OUTPUT,restored.sideConfig().mode(Direction.EAST));assertEquals(RedstoneMode.LOW,restored.redstoneControl().mode());
        assertEquals(980,restoredTurbine.steamAmount());assertEquals(200,restoredTurbine.energy().getAmountAsInt());
    }
    @Test void bothRecipesAndARealNonEmptySteamFluidAreRegistered(MinecraftServer server) {
        for(String name:new String[]{"boiler","steam_turbine"}) assertTrue(server.getRecipeManager().byKey(
                ResourceKey.create(Registries.RECIPE,Identifier.fromNamespaceAndPath("futuretech",name))).isPresent());
        assertFalse(steam().isEmpty());assertFalse(ModFluids.STEAM.get().defaultFluidState().isEmpty());
        assertSame(ModFluids.STEAM_TYPE.get(),ModFluids.STEAM.get().getFluidType());
    }
}
