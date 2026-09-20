package dev.futuretech.energy;

import static dev.futuretech.block.entity.WindGeneratorBlockEntity.*;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.ControllerKind;
import dev.futuretech.block.DayMoment;
import dev.futuretech.block.WindGeneratorBlock;
import dev.futuretech.block.WindTurbineStructure;
import dev.futuretech.block.WindTurbinePartBlock;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.WindGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public final class WindGeneratorGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("futuretech", "wind_generator_clearance_and_cable");
    private static final Identifier ADJACENT_ID = Identifier.fromNamespaceAndPath("futuretech", "wind_generator_adjacent_and_legacy_cleanup");
    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(Component.literal(message));
    }

    private static void windAndCable(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(5,5,5));
        int[] rate = {0};
        java.util.function.Supplier<WindGeneratorBlockEntity> wind = () -> (WindGeneratorBlockEntity)level.getBlockEntity(pos);
        java.util.function.Supplier<BatteryBlockEntity> battery = () -> (BatteryBlockEntity)level.getBlockEntity(pos.below(2));
        helper.startSequence().thenExecute(() -> {
            for (int up=1; up<=16; up++) level.removeBlock(pos.above(up),false);
            for (int part=1; part<=WindTurbineStructure.PARTS; part++) {
                level.removeBlock(WindTurbineStructure.position(pos,Direction.NORTH,part),false);
                level.removeBlock(WindTurbineStructure.position(pos,Direction.EAST,part),false);
            }
            for (Direction dir : Direction.Plane.HORIZONTAL)
                for (int distance=1; distance<=4; distance++) level.removeBlock(pos.above(3).relative(dir,distance),false);
            ControllerKind.TIME.apply(level,DayMoment.NOON.ordinal());
            level.setBlock(pos.below(),ModBlocks.ENERGY_CABLE_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(pos.below(2),ModBlocks.BATTERY_MK1.get().defaultBlockState(),Block.UPDATE_ALL);
            battery.get().sideConfig().set(Direction.UP,SideMode.INPUT);
            battery.get().sideConfigChanged();
            level.setBlock(pos.above(4),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            check(helper,!WindTurbineStructure.fits(level,pos,Direction.NORTH),"Placement rejects a roof in the tower space");
            level.removeBlock(pos.above(4),false);
            level.setBlock(pos,ModBlocks.WIND_GENERATOR.get().defaultBlockState(),Block.UPDATE_ALL);
            for(Direction side:Direction.values()) check(helper,wind.get().sideConfig().mode(side)==SideMode.NONE,"A placed turbine starts with every face closed");
            wind.get().sideConfig().set(Direction.DOWN,SideMode.OUTPUT);
            wind.get().sideConfigChanged();
            for (int part : WindTurbineStructure.COLUMN_PARTS) {
                check(helper,WindTurbineStructure.belongsTo(level,WindTurbineStructure.position(pos,Direction.NORTH,part),pos),"Placement reserves the central column");
            }
        }).thenIdle(120).thenExecute(() -> {
            rate[0] = wind.get().menuData().get(DATA_RATE);
            check(helper,rate[0]>0,"Open-air turbine generates energy");
            check(helper,battery.get().energy().getAmountAsInt()>0,"Wind power crosses a cable into the battery");
            ControllerKind.TIME.apply(level,DayMoment.MIDNIGHT.ordinal());
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==rate[0],"Midnight generates the same power as noon");
            level.setBlock(pos.above(3).north(2),Blocks.GLASS.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==0,"Obstruction two blocks in front stops the rotor");
            check(helper,wind.get().menuData().get(DATA_STATUS)==OBSTRUCTED,"GUI identifies the obstruction");
            check(helper,!wind.get().getBlockState().getValue(WindGeneratorBlock.LIT),"Blocked rotor switches off");
            level.removeBlock(pos.above(3).north(2),false);
            level.setBlock(pos.above(6),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==0,"A roof blocks wind generation");
            level.removeBlock(pos.above(6),false);
            level.setBlock(pos,wind.get().getBlockState().setValue(WindGeneratorBlock.FACING,Direction.EAST),Block.UPDATE_ALL);
            level.setBlock(pos.above(3).east(2),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==0,"Front clearance follows the block facing");
            level.removeBlock(pos.above(3).east(2),false);
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==rate[0],"Removing obstacles restores production");
            wind.get().redstoneControl().setMode(RedstoneMode.HIGH);
            wind.get().redstoneControlChanged();
            ((TickLimitedEnergyHandler)wind.get().energy()).set(1000);
            rate[0] = battery.get().energy().getAmountAsInt();
        }).thenIdle(12).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)==0,"Redstone mode pauses generation");
            check(helper,battery.get().energy().getAmountAsInt()>=rate[0]+1000,"Stored energy still leaves while paused");
            level.setBlock(pos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(22).thenExecute(() -> {
            check(helper,wind.get().menuData().get(DATA_RATE)>0,"Real neighbour signal resumes production");
            level.setBlock(pos,wind.get().getBlockState().setValue(MachineLevel.MK,4),Block.UPDATE_ALL);
            check(helper,WindTurbineStructure.belongsTo(level,pos.above(4),pos),"MK upgrade preserves the structure");
            level.removeBlock(pos.west(),false);
            // Breaking any part must drop exactly one generator and clear the other parts.
            level.destroyBlock(pos.above(2),true);
            check(helper,level.getBlockState(pos).isAir(),"Breaking the mast removes the base");
            for (int part=1; part<=WindTurbineStructure.PARTS; part++) {
                check(helper,!WindTurbineStructure.belongsTo(level,WindTurbineStructure.position(pos,Direction.EAST,part),pos),"Dismantling leaves no invisible structure cells");
            }
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(4),item -> item.getItem().is(dev.futuretech.registry.ModItems.WIND_GENERATOR.get()));
            check(helper,drops.stream().mapToInt(item -> item.getItem().getCount()).sum()==1,"Breaking a part drops one generator");
            check(helper,drops.getFirst().getItem().getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                    net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).get(MachineLevel.MK)==4,"The dropped item retains MK4");
            level.setBlock(pos,ModBlocks.WIND_GENERATOR.get().defaultBlockState(),Block.UPDATE_ALL);
            level.removeBlock(pos,false);
            check(helper,level.getBlockState(pos.above(4)).isAir(),"Removing the base clears the entire tower");
        }).thenSucceed();
    }

    private static void adjacentAndLegacyCleanup(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(5,5,5));
        helper.startSequence().thenExecute(() -> {
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for (int y=0;y<=6;y++)
                level.removeBlock(pos.offset(x,y,z),false);
            level.setBlock(pos,ModBlocks.WIND_GENERATOR.get().defaultBlockState().setValue(MachineLevel.MK,4),Block.UPDATE_ALL);
            ((TickLimitedEnergyHandler)((WindGeneratorBlockEntity)level.getBlockEntity(pos)).energy()).set(1000);
            // Recreate saved cells from the old wide structure before its first server tick.
            for (int part=1;part<=WindTurbineStructure.PARTS;part++) {
                if (WindTurbineStructure.sideways(part)==0) continue;
                var cell=WindTurbineStructure.position(pos,Direction.NORTH,part);
                var state=ModBlocks.WIND_TURBINE_PART.get().defaultBlockState().setValue(WindTurbinePartBlock.PART,part);
                level.setBlock(cell,state,Block.UPDATE_ALL);
                check(helper,state.getShape(level,cell).isEmpty(),"Legacy rotor cells have no selection box");
                check(helper,state.getCollisionShape(level,cell).isEmpty(),"Legacy rotor cells have no collision");
            }
            // A neighbour may be placed even before the saved legacy cells have been cleaned.
            check(helper,WindTurbineStructure.fits(level,pos.east(),Direction.NORTH),"Legacy blade reservations do not block a neighbouring turbine");
            level.setBlock(pos.east(),ModBlocks.WIND_GENERATOR.get().defaultBlockState(),Block.UPDATE_ALL);
            check(helper,level.getBlockEntity(pos) instanceof WindGeneratorBlockEntity,"Replacing legacy cells preserves their original generator");
            // Keep a real block beside the mast to ensure cleanup only removes owned legacy cells.
            level.setBlock(pos.west().above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        }).thenIdle(3).thenExecute(() -> {
            check(helper,MachineLevel.of(level.getBlockState(pos))==4,"Cleanup preserves the upgrade");
            check(helper,((WindGeneratorBlockEntity)level.getBlockEntity(pos)).energy().getAmountAsInt()>=1000,"Cleanup preserves stored energy");
            check(helper,level.getBlockState(pos.west().above()).is(Blocks.STONE),"Cleanup preserves real neighbouring blocks");
            for (int y=2;y<=4;y++) check(helper,level.getBlockState(pos.west().above(y)).isAir(),"Old lateral cells are removed automatically");
            check(helper,WindTurbineStructure.belongsTo(level,pos.east().above(3),pos.east()),"Cleanup preserves the neighbouring turbine's column");
            level.removeBlock(pos.west().above(),false);
            level.removeBlock(pos.east(),false);
            level.removeBlock(pos,false);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state=ModBlocks.WIND_GENERATOR.get().defaultBlockState().setValue(WindGeneratorBlock.FACING,facing);
                var next=pos.relative(facing.getClockWise());
                level.setBlock(pos,state,Block.UPDATE_ALL);
                check(helper,WindTurbineStructure.fits(level,next,facing),"Turbines fit side by side in every facing");
                level.setBlock(next,state,Block.UPDATE_ALL);
                var turned=state.setValue(WindGeneratorBlock.FACING,facing.getClockWise());
                check(helper,turned.canSurvive(level,pos),"A neighbouring turbine does not prevent rotation");
                level.setBlock(pos,turned,Block.UPDATE_ALL);
                check(helper,WindTurbineStructure.belongsTo(level,next.above(3),next),"Rotation leaves the neighbour intact");
                level.destroyBlock(pos.above(2),true);
                check(helper,level.getBlockEntity(next) instanceof WindGeneratorBlockEntity,"Dismantling one turbine preserves its neighbour");
                level.removeBlock(next,false);
            }
        }).thenSucceed();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> {
            registry.register(ID,(java.util.function.Consumer<GameTestHelper>)WindGeneratorGameTests::windAndCable);
            registry.register(ADJACENT_ID,(java.util.function.Consumer<GameTestHelper>)WindGeneratorGameTests::adjacentAndLegacyCleanup);
        });
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(ID);
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                Identifier.fromNamespaceAndPath("futuretech","energy_empty"),400,0,true);
        event.registerTest(ID,new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,ID),data));
        var adjacentEnvironment=event.registerEnvironment(ADJACENT_ID);
        var adjacentData=new net.minecraft.gametest.framework.TestData<>(adjacentEnvironment,
                Identifier.fromNamespaceAndPath("futuretech","energy_empty"),100,0,true);
        event.registerTest(ADJACENT_ID,new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,ADJACENT_ID),adjacentData));
    }
}
