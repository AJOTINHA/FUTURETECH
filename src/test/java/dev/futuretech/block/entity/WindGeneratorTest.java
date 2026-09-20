package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.WindGeneratorBlockEntity.*;
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
class WindGeneratorTest {
    private WindGeneratorBlockEntity generator() {
        return new WindGeneratorBlockEntity(BlockPos.ZERO, ModBlocks.WIND_GENERATOR.get().defaultBlockState());
    }

    @Test void altitudeAndClearanceScaleOutputWithBoundedInputs() {
        assertEquals(25, windPercent(64,16,16));
        assertEquals(25, windPercent(-64,16,16));
        assertEquals(62, windPercent(128,16,16));
        assertEquals(100, windPercent(192,16,16));
        assertEquals(100, windPercent(320,16,16));
        assertEquals(50, windPercent(192,8,16));
        assertEquals(0, windPercent(192,0,16));
        assertEquals(0, windPercent(192,4,0));
        assertEquals(100, windPercent(192,30,16));
    }

    @Test void generationStopsWithoutWindAndFillsEvenTheLastFewFE() {
        var wind=generator();
        wind.generateEnergy(100);
        assertEquals(24,wind.energy().getAmountAsInt());
        wind.generateEnergy(0);
        assertEquals(24,wind.energy().getAmountAsInt());
        assertEquals(CALM,wind.menuData().get(DATA_STATUS));
        for(int tick=0;tick<1000;tick++) wind.generateEnergy(100);
        assertEquals(CAPACITY,wind.energy().getAmountAsInt());
        assertEquals(FULL,wind.menuData().get(DATA_STATUS));
        try(var tx=Transaction.openRoot()) {
            assertEquals(7,wind.energy().extract(7,tx)); tx.commit();
        }
        wind.generateEnergy(100);
        assertEquals(CAPACITY,wind.energy().getAmountAsInt());
        assertEquals(7,wind.menuData().get(DATA_RATE));
    }

    @Test void redstonePausesGenerationButStoredEnergyCanStillLeave() {
        var wind=generator();
        wind.generateEnergy(100);
        wind.redstoneControl().setMode(RedstoneMode.HIGH);
        wind.generateEnergy(100);
        assertEquals(24,wind.energy().getAmountAsInt());
        assertEquals(DISABLED,wind.menuData().get(DATA_STATUS));
        try(var tx=Transaction.openRoot()) {
            assertEquals(24,wind.energy().extract(24,tx)); tx.commit();
        }
        wind.redstoneControl().setPowered(true);
        wind.generateEnergy(100);
        assertEquals(24,wind.energy().getAmountAsInt());
    }

    @Test void outputsAreConfigurableAndCannotAcceptEnergy() {
        var wind=generator();
        for(int i=0;i<20;i++) wind.generateEnergy(100);
        // A freshly placed generator keeps every face closed until the player opens one.
        for(Direction side:Direction.values()) assertNull(SidedEnergy.view(wind.energy(),wind.sideConfig(),side));
        wind.sideConfig().set(Direction.DOWN,SideMode.OUTPUT);
        var bottom=SidedEnergy.view(wind.energy(),wind.sideConfig(),Direction.DOWN);
        assertNotNull(bottom);
        try(var tx=Transaction.openRoot()) {
            assertEquals(0,bottom.insert(200,tx));
            assertEquals(150,bottom.extract(150,tx)); tx.commit();
        }
        try(var tx=Transaction.openRoot()) {
            assertEquals(50,bottom.extract(200,tx)); tx.commit();
        }
        wind.sideConfig().set(Direction.DOWN,SideMode.NONE);
        assertNull(SidedEnergy.view(wind.energy(),wind.sideConfig(),Direction.DOWN));
        wind.sideConfig().set(Direction.EAST,SideMode.OUTPUT);
        var east=SidedEnergy.view(wind.energy(),wind.sideConfig(),Direction.EAST);
        assertNotNull(east);
        // Every output shares one transfer budget, including newly opened faces.
        try(var tx=Transaction.openRoot()) { assertEquals(0,east.extract(1,tx)); }
    }

    @Test void kitLevelsScaleCapacityAndGenerationWithoutLosingStoredEnergy() {
        var wind=generator();
        int[] rate={24,36,48,72};
        int[] capacity={20000,30000,40000,60000};
        int total=0;
        for(int mk=1;mk<=4;mk++) {
            wind.setBlockState(wind.getBlockState().setValue(MachineLevel.MK,mk));
            assertEquals(total,wind.energy().getAmountAsInt());
            wind.generateEnergy(100); total+=rate[mk-1];
            assertEquals(total,wind.energy().getAmountAsInt());
            assertEquals(capacity[mk-1],wind.energy().getCapacityAsInt());
        }
    }

    @Test void reloadAndMenuSyncKeepEnergyAndConfiguration(MinecraftServer server) {
        var wind=generator();
        wind.setBlockState(wind.getBlockState().setValue(MachineLevel.MK,4));
        for(int i=0;i<750;i++) wind.generateEnergy(100);
        wind.sideConfig().set(Direction.EAST,SideMode.OUTPUT);
        wind.redstoneControl().setMode(RedstoneMode.LOW);
        var restored=new WindGeneratorBlockEntity(BlockPos.ZERO,wind.getBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),
                wind.saveWithoutMetadata(server.registryAccess())));
        assertEquals(54000,restored.energy().getAmountAsInt());
        assertEquals(SideMode.OUTPUT,restored.sideConfig().mode(Direction.EAST));
        assertEquals(RedstoneMode.LOW,restored.redstoneControl().mode());
        var synced=new SimpleContainerData(DATA_COUNT);
        for(int i=0;i<DATA_COUNT;i++) synced.set(i,(short)restored.menuData().get(i));
        assertEquals(54000,EnergySync.unpack(synced.get(DATA_ENERGY_LOW),synced.get(DATA_ENERGY_HIGH)));
    }

    @Test void recipeLoadsInTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("futuretech","wind_generator"))).isPresent());
    }
}
