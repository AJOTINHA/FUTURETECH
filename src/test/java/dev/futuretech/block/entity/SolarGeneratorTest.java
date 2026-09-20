package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.SolarGeneratorBlockEntity.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SidedEnergy;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class SolarGeneratorTest {
    private SolarGeneratorBlockEntity generator() {
        return new SolarGeneratorBlockEntity(BlockPos.ZERO, ModBlocks.SOLAR_GENERATOR.get().defaultBlockState());
    }

    @Test void sunAngleWeatherAndSkyControlProduction() {
        assertEquals(100, sunlightPercent(true,true,0,0,0));
        assertEquals(25, sunlightPercent(true,true,60,0,0));
        assertEquals(0, sunlightPercent(true,true,180,0,0));
        assertEquals(0, sunlightPercent(true,true,90,0,0));
        assertEquals(0, sunlightPercent(true,false,0,0,0));
        assertEquals(0, sunlightPercent(false,true,0,0,0));
        assertEquals(50, sunlightPercent(true,true,0,1,0));
        assertEquals(20, sunlightPercent(true,true,0,1,1));
    }

    @Test void noonProducesMoreThanMorningAndAfternoon() {
        var solar=generator();
        solar.generateEnergy(sunlightPercent(true,true,60,0,0));
        assertEquals(10,solar.menuData().get(DATA_RATE));
        solar.generateEnergy(sunlightPercent(true,true,45,0,0));
        assertEquals(20,solar.menuData().get(DATA_RATE));
        solar.generateEnergy(sunlightPercent(true,true,0,0,0));
        assertEquals(40,solar.menuData().get(DATA_RATE));
        solar.generateEnergy(sunlightPercent(true,true,315,0,0));
        assertEquals(20,solar.menuData().get(DATA_RATE));
        solar.generateEnergy(sunlightPercent(true,false,0,0,0));
        assertEquals(0,solar.menuData().get(DATA_RATE));
    }

    @Test void generationStopsAtNightAndFillsEvenTheLastFewFE() {
        var solar=generator();
        solar.generateEnergy(100);
        assertEquals(40,solar.energy().getAmountAsInt());
        solar.generateEnergy(0);
        assertEquals(40,solar.energy().getAmountAsInt());
        assertEquals(NIGHT,solar.menuData().get(DATA_STATUS));
        for(int tick=0;tick<1000;tick++) solar.generateEnergy(100);
        assertEquals(CAPACITY,solar.energy().getAmountAsInt());
        assertEquals(FULL,solar.menuData().get(DATA_STATUS));
        try(var tx=Transaction.openRoot()) {
            assertEquals(7,solar.energy().extract(7,tx)); tx.commit();
        }
        solar.generateEnergy(100);
        assertEquals(CAPACITY,solar.energy().getAmountAsInt());
        assertEquals(7,solar.menuData().get(DATA_RATE));
    }

    @Test void redstonePausesGenerationButStoredEnergyCanStillLeave() {
        var solar=generator();
        solar.generateEnergy(100);
        solar.redstoneControl().setMode(RedstoneMode.HIGH);
        solar.generateEnergy(100);
        assertEquals(40,solar.energy().getAmountAsInt());
        assertEquals(DISABLED,solar.menuData().get(DATA_STATUS));
        try(var tx=Transaction.openRoot()) {
            assertEquals(40,solar.energy().extract(40,tx)); tx.commit();
        }
        solar.redstoneControl().setPowered(true);
        solar.generateEnergy(100);
        assertEquals(40,solar.energy().getAmountAsInt());
    }

    @Test void outputsAreConfigurableAndCannotAcceptEnergy() {
        var solar=generator();
        for(int i=0;i<20;i++) solar.generateEnergy(100);
        // A freshly placed generator keeps every face closed until the player opens one.
        for(Direction side:Direction.values()) assertNull(SidedEnergy.view(solar.energy(),solar.sideConfig(),side));
        solar.sideConfig().set(Direction.DOWN,SideMode.OUTPUT);
        var bottom=SidedEnergy.view(solar.energy(),solar.sideConfig(),Direction.DOWN);
        assertNotNull(bottom);
        try(var tx=Transaction.openRoot()) {
            assertEquals(0,bottom.insert(200,tx));
            assertEquals(150,bottom.extract(150,tx)); tx.commit();
        }
        try(var tx=Transaction.openRoot()) {
            assertEquals(50,bottom.extract(200,tx)); tx.commit();
        }
        solar.sideConfig().set(Direction.DOWN,SideMode.NONE);
        assertNull(SidedEnergy.view(solar.energy(),solar.sideConfig(),Direction.DOWN));
        solar.sideConfig().set(Direction.EAST,SideMode.OUTPUT);
        var east=SidedEnergy.view(solar.energy(),solar.sideConfig(),Direction.EAST);
        assertNotNull(east);
        // Every output shares one transfer budget, including newly opened faces.
        try(var tx=Transaction.openRoot()) { assertEquals(0,east.extract(1,tx)); }
    }

    @Test void kitLevelsScaleCapacityAndGenerationWithoutLosingStoredEnergy() {
        var solar=generator();
        int[] rate={40,60,80,120};
        int[] capacity={20000,30000,40000,60000};
        int total=0;
        for(int mk=1;mk<=4;mk++) {
            solar.setBlockState(solar.getBlockState().setValue(MachineLevel.MK,mk));
            assertEquals(total,solar.energy().getAmountAsInt());
            solar.generateEnergy(100); total+=rate[mk-1];
            assertEquals(total,solar.energy().getAmountAsInt());
            assertEquals(capacity[mk-1],solar.energy().getCapacityAsInt());
        }
    }

    @Test void reloadAndMenuSyncKeepEnergyAndConfiguration(MinecraftServer server) {
        var solar=generator();
        solar.setBlockState(solar.getBlockState().setValue(MachineLevel.MK,4));
        for(int i=0;i<450;i++) solar.generateEnergy(100);
        solar.sideConfig().set(Direction.EAST,SideMode.OUTPUT);
        solar.redstoneControl().setMode(RedstoneMode.LOW);
        var restored=new SolarGeneratorBlockEntity(BlockPos.ZERO,solar.getBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),
                solar.saveWithoutMetadata(server.registryAccess())));
        assertEquals(54000,restored.energy().getAmountAsInt());
        assertEquals(SideMode.OUTPUT,restored.sideConfig().mode(Direction.EAST));
        assertEquals(RedstoneMode.LOW,restored.redstoneControl().mode());
        var synced=new SimpleContainerData(DATA_COUNT);
        for(int i=0;i<DATA_COUNT;i++) synced.set(i,(short)restored.menuData().get(i));
        assertEquals(54000,EnergySync.unpack(synced.get(DATA_ENERGY_LOW),synced.get(DATA_ENERGY_HIGH)));
    }

    @Test void recipeLoadsInTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("futuretech","solar_generator"))).isPresent());
    }
}
