package dev.futuretech.energy;

import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.BoilerBlockEntity;
import dev.futuretech.block.entity.SteamTurbineBlockEntity;
import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.block.entity.FluidCableBlockEntity;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

@net.neoforged.fml.common.EventBusSubscriber(modid="futuretech")
public final class SteamMachinesGameTests {
    private static final Identifier ID=Identifier.fromNamespaceAndPath("futuretech","water_boiler_steam_turbine_battery");
    private static final Identifier UPGRADES_ID=Identifier.fromNamespaceAndPath("futuretech","boiler_lava_and_energy_upgrades");
    private static void check(GameTestHelper helper,boolean condition,String message) {
        if(!condition) throw helper.assertionException(Component.literal(message));
    }
    private static void circuit(GameTestHelper helper) {
        var level=helper.getLevel();
        var pos=helper.absolutePos(new BlockPos(4,3,4));
        var turbinePos=pos.east(3);
        java.util.function.Supplier<BoilerBlockEntity> boiler=()->(BoilerBlockEntity)level.getBlockEntity(pos);
        java.util.function.Supplier<SteamTurbineBlockEntity> turbine=()->(SteamTurbineBlockEntity)level.getBlockEntity(turbinePos);
        java.util.function.Supplier<BatteryBlockEntity> battery=()->(BatteryBlockEntity)level.getBlockEntity(turbinePos.below(2));
        int[] before={0,0,0};
        helper.startSequence().thenExecute(()->{
            level.setBlock(pos.west(),ModBlocks.FLUID_CABLE.get().defaultBlockState(),Block.UPDATE_ALL);
            for(int x=0;x<=3;x++) level.setBlock(pos.above().east(x),ModBlocks.FLUID_CABLE.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(turbinePos.below(),ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(pos.west(2),ModBlocks.FLUID_TANK.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(pos,ModBlocks.BOILER.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(turbinePos,ModBlocks.STEAM_TURBINE.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(turbinePos.below(2),ModBlocks.BATTERY_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            for(Direction side:Direction.values()) check(helper,boiler.get().sideConfig().mode(side)==SideMode.NONE && turbine.get().sideConfig().mode(side)==SideMode.NONE,"Placed steam machines start with every face closed");
            boiler.get().sideConfig().set(Direction.WEST,SideMode.INPUT);boiler.get().sideConfig().set(Direction.UP,SideMode.OUTPUT);boiler.get().sideConfigChanged();
            // Only the steam face is opened: energy leaves the turbine through every face, like the other generators.
            turbine.get().sideConfig().set(Direction.UP,SideMode.INPUT);turbine.get().sideConfigChanged();
            check(helper,level.getCapability(Capabilities.Energy.BLOCK,turbinePos,Direction.DOWN)!=null,"The turbine offers energy on an unconfigured face");
            battery.get().sideConfig().set(Direction.UP,SideMode.INPUT);battery.get().sideConfigChanged();
            var tank=(FluidTankBlockEntity)level.getBlockEntity(pos.west(2));
            tank.sideConfig().set(Direction.EAST,SideMode.OUTPUT);tank.sideConfigChanged();
            try(var tx=Transaction.openRoot()) {
                check(helper,tank.handler(null).insert(FluidResource.of(Fluids.WATER),12000,tx)==12000,"Water source tank accepts water");tx.commit();
            }
            var waterCable=(FluidCableBlockEntity)level.getBlockEntity(pos.west());
            waterCable.setConnectorMode(Direction.WEST,SideMode.INPUT);
            waterCable.setConnectorMode(Direction.EAST,SideMode.OUTPUT);
            ((FluidCableBlockEntity)level.getBlockEntity(pos.above())).setConnectorMode(Direction.DOWN,SideMode.INPUT);
            ((FluidCableBlockEntity)level.getBlockEntity(turbinePos.above())).setConnectorMode(Direction.DOWN,SideMode.OUTPUT);
            boiler.get().setItem(BoilerBlockEntity.SLOT_FUEL,new ItemStack(Items.COAL,4));
            check(helper,level.getCapability(Capabilities.Energy.BLOCK,pos,Direction.DOWN)==null,"The boiler has no electrical output");
        }).thenIdle(120).thenExecute(()->{
            check(helper,boiler.get().waterAmount()>0,"Water travels from tank through a fluid cable into the boiler");
            check(helper,boiler.get().menuData().get(BoilerBlockEntity.DATA_RATE)==60,"Boiler produces 60 mB of steam per tick");
            check(helper,battery.get().energy().getAmountAsInt()>1000,"Steam travels through pipes to the turbine and energy through cable to battery");
            turbine.get().redstoneControl().setMode(RedstoneMode.HIGH);turbine.get().redstoneControlChanged();
        }).thenIdle(12).thenExecute(()->{
            before[0]=battery.get().energy().getAmountAsInt();before[1]=turbine.get().steamAmount();
        }).thenIdle(24).thenExecute(()->{
            check(helper,battery.get().energy().getAmountAsInt()==before[0],"Paused turbine generates no energy after its buffer drains");
            check(helper,turbine.get().steamAmount()>before[1],"Steam is retained while the turbine is paused");
            boiler.get().redstoneControl().setMode(RedstoneMode.HIGH);boiler.get().redstoneControlChanged();
            before[2]=boiler.get().menuData().get(BoilerBlockEntity.DATA_BURN);
            turbine.get().redstoneControl().setMode(RedstoneMode.IGNORED);turbine.get().redstoneControlChanged();
        }).thenIdle(12).thenExecute(()->{
            check(helper,boiler.get().menuData().get(BoilerBlockEntity.DATA_BURN)==before[2],"Paused boiler preserves remaining fuel heat");
            check(helper,boiler.get().menuData().get(BoilerBlockEntity.DATA_RATE)==0,"Paused boiler produces no new steam");
            check(helper,battery.get().energy().getAmountAsInt()>before[0],"Turbine resumes using its stored steam");
        }).thenSucceed();
    }
    /** Two boilers side by side: one heats with lava piped from a tank, the other with FE from a battery through a cable. */
    private static void upgrades(GameTestHelper helper) {
        var level=helper.getLevel();
        var lavaPos=helper.absolutePos(new BlockPos(2,3,4));
        var energyPos=helper.absolutePos(new BlockPos(6,3,4));
        java.util.function.Supplier<BoilerBlockEntity> lavaBoiler=()->(BoilerBlockEntity)level.getBlockEntity(lavaPos);
        java.util.function.Supplier<BoilerBlockEntity> energyBoiler=()->(BoilerBlockEntity)level.getBlockEntity(energyPos);
        helper.startSequence().thenExecute(()->{
            level.setBlock(lavaPos.west(),ModBlocks.FLUID_CABLE.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(lavaPos.west(2),ModBlocks.FLUID_TANK.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(lavaPos,ModBlocks.BOILER.get().defaultBlockState(),Block.UPDATE_ALL);
            var lavaCable=(FluidCableBlockEntity)level.getBlockEntity(lavaPos.west());
            lavaCable.setConnectorMode(Direction.WEST,SideMode.INPUT);lavaCable.setConnectorMode(Direction.EAST,SideMode.OUTPUT);
            // An MK1 cable carries 400 FE/t and an MK1 battery hands out 200; the boiler burns 1 800 FE/t at full rate.
            level.setBlock(energyPos.east(),ModBlocks.ENERGY_CABLE_MK4.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(energyPos.east(2),ModBlocks.BATTERY_MK1.get().defaultBlockState().setValue(dev.futuretech.api.upgrade.MachineLevel.MK,4),Block.UPDATE_ALL);
            level.setBlock(energyPos,ModBlocks.BOILER.get().defaultBlockState(),Block.UPDATE_ALL);
            // The battery does not push on its own: the cable pulls from it through an input connector.
            ((dev.futuretech.block.entity.EnergyCableBlockEntity)level.getBlockEntity(energyPos.east())).setConnectorMode(Direction.EAST,SideMode.INPUT);
            var tank=(FluidTankBlockEntity)level.getBlockEntity(lavaPos.west(2));
            tank.sideConfig().set(Direction.EAST,SideMode.OUTPUT);tank.sideConfigChanged();
            try(var tx=Transaction.openRoot()) { tank.handler(null).insert(FluidResource.of(Fluids.LAVA),4000,tx);tx.commit(); }
            var battery=(BatteryBlockEntity)level.getBlockEntity(energyPos.east(2));
            battery.sideConfig().set(Direction.WEST,SideMode.OUTPUT);battery.sideConfigChanged();
            ((dev.futuretech.energy.TickLimitedEnergyHandler)battery.energy()).set(100000);
            for(var boiler:java.util.List.of(lavaBoiler.get(),energyBoiler.get())) {
                try(var tx=Transaction.openRoot()) { boiler.tanks().insert(FluidResource.of(Fluids.WATER),4000,tx);tx.commit(); }
            }
            lavaBoiler.get().sideConfig().set(Direction.WEST,SideMode.INPUT);lavaBoiler.get().sideConfigChanged();
            energyBoiler.get().sideConfig().set(Direction.EAST,SideMode.INPUT);energyBoiler.get().sideConfigChanged();
            check(helper,level.getCapability(Capabilities.Energy.BLOCK,energyPos,Direction.EAST)==null,"Without the upgrade an input face offers no energy buffer");
            lavaBoiler.get().upgrades().setItem(0,new ItemStack(dev.futuretech.registry.ModItems.LAVA_UPGRADE.get()));
            energyBoiler.get().upgrades().setItem(0,new ItemStack(dev.futuretech.registry.ModItems.ENERGY_UPGRADE.get()));
            check(helper,level.getCapability(Capabilities.Energy.BLOCK,energyPos,Direction.EAST)!=null,"The energy upgrade opens the input face to FE");
            check(helper,level.getCapability(Capabilities.Energy.BLOCK,energyPos,Direction.NORTH)==null,"A closed face still offers no FE");
        }).thenIdle(60).thenExecute(()->{
            check(helper,lavaBoiler.get().lavaAmount()>0,"Lava travels from the tank into the boiler's lava tank");
            check(helper,lavaBoiler.get().menuData().get(BoilerBlockEntity.DATA_RATE)==60,"The lava boiler produces 60 mB of steam per tick");
            // The boiler spends FE as fast as it arrives, so the steam it made is the proof the FE got there.
            check(helper,energyBoiler.get().steamAmount()>0,"FE travels from the battery through the cable into the boiler");
            // At 6 heat/t the energy boiler wants 1 800 FE/t and an MK4 battery hands out 1 600, so it runs a little short some ticks.
            check(helper,energyBoiler.get().menuData().get(BoilerBlockEntity.DATA_RATE)>0,"The energy boiler produces steam on FE");
            check(helper,energyBoiler.get().menuData().get(BoilerBlockEntity.DATA_MODE)==BoilerBlockEntity.ENERGY,"The menu reports the energy mode");
        }).thenSucceed();
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION,registry -> {
            registry.register(ID,(java.util.function.Consumer<GameTestHelper>)SteamMachinesGameTests::circuit);
            registry.register(UPGRADES_ID,(java.util.function.Consumer<GameTestHelper>)SteamMachinesGameTests::upgrades);
        });
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        for(var id:java.util.List.of(ID,UPGRADES_ID)) {
            var environment=event.registerEnvironment(id);
            var data=new net.minecraft.gametest.framework.TestData<>(environment,Identifier.fromNamespaceAndPath("futuretech","energy_empty"),300,0,true);
            event.registerTest(id,new net.minecraft.gametest.framework.FunctionGameTestInstance(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,id),data));
        }
    }
}
