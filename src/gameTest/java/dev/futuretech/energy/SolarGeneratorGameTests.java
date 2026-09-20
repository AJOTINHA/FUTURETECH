package dev.futuretech.energy;

import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.ControllerKind;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.SolarGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class SolarGeneratorGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "solar_generator_sky_and_cable");

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static void skyAndCable(GameTestHelper helper) {
        var level=helper.getLevel();
        var pos=helper.absolutePos(new BlockPos(5,5,5));
        int[] batteryBeforeNight = {0};
        java.util.function.Supplier<SolarGeneratorBlockEntity> solar=() -> (SolarGeneratorBlockEntity)level.getBlockEntity(pos);
        java.util.function.Supplier<BatteryBlockEntity> battery=() -> (BatteryBlockEntity)level.getBlockEntity(pos.below(2));
        helper.startSequence().thenExecute(() -> {
            for(int up=1;up<=16;up++) level.removeBlock(pos.above(up),false);
            level.getServer().setWeatherParameters(6000,0,false,false);
            ControllerKind.TIME.apply(level,DayMoment.NOON.ordinal());
            // setBlock bypasses item placement: place the cable first so each
            // endpoint placement subsequently refreshes its connection shape.
            level.setBlock(pos.below(),ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(pos.below(2),ModBlocks.BATTERY_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            battery.get().sideConfig().set(Direction.UP,SideMode.INPUT);
            battery.get().sideConfigChanged();
            level.setBlock(pos,ModBlocks.SOLAR_GENERATOR.get().defaultBlockState(),Block.UPDATE_ALL);
            for(Direction side:Direction.values()) check(helper,solar.get().sideConfig().mode(side)==SideMode.NONE,"A placed panel starts with every face closed");
            solar.get().sideConfig().set(Direction.DOWN,SideMode.OUTPUT);
            solar.get().sideConfigChanged();
        }).thenIdle(120).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)>0,"Open sky at noon generates energy");
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)>=38,"Noon reaches the new 40 FE/t peak");
            check(helper,battery.get().energy().getAmountAsInt()>0,"Energy travels down through a cable to the battery");
            level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"Roof stops solar generation");
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_STATUS)==SolarGeneratorBlockEntity.COVERED,"GUI reports covered panel");
            level.removeBlock(pos.above(),false);
            level.setBlock(pos.above(6),Blocks.GLASS.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"Glass roof six blocks above also blocks production");
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_STATUS)==SolarGeneratorBlockEntity.COVERED,"Transparent roof is reported as cover");
            level.removeBlock(pos.above(6),false);
            level.setBlock(pos.above(3),Blocks.STONE_SLAB.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"A partial slab blocks production");
            level.removeBlock(pos.above(3),false);
            level.setBlock(pos.above(4),Blocks.OAK_LEAVES.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"Tree leaves block production");
            level.removeBlock(pos.above(4),false);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)>0,"Removing the roof resumes production");
            solar.get().redstoneControl().setMode(RedstoneMode.HIGH);
            solar.get().redstoneControlChanged();
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"High redstone mode waits for a signal");
            level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)>0,"A real neighbour redstone signal resumes generation");
            ControllerKind.TIME.apply(level,DayMoment.MIDNIGHT.ordinal());
        }).thenIdle(3).thenExecute(() -> {
            check(helper,solar.get().menuData().get(SolarGeneratorBlockEntity.DATA_RATE)==0,"Moonlight generates no energy");
            ((TickLimitedEnergyHandler)solar.get().energy()).set(1000);
            batteryBeforeNight[0]=battery.get().energy().getAmountAsInt();
        }).thenIdle(12).thenExecute(() -> {
            check(helper,battery.get().energy().getAmountAsInt()>=batteryBeforeNight[0]+1000,"Stored energy still leaves at night");
            check(helper,solar.get().energy().getAmountAsInt()==0,"The night adds no new energy");
        }).thenSucceed();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry ->
                registry.register(ID,(java.util.function.Consumer<GameTestHelper>)SolarGeneratorGameTests::skyAndCable));
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        // Own environment: clock and weather changes must not race with other tests.
        var environment=event.registerEnvironment(ID);
        var data=new net.minecraft.gametest.framework.TestData<>(environment,
                Identifier.fromNamespaceAndPath("futuretech","energy_empty"),300,0,true);
        event.registerTest(ID,new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,ID),data));
    }
}
