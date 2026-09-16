package dev.futuretech.client;

import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.CableBlock;
import dev.futuretech.block.CableConnector;
import dev.futuretech.block.CableKind;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CableConnectorModelTest {
    @Test
    void approvedNodeAndArmsHaveEightUnitSelectionAndCollision(MinecraftServer server) {
        var state=ModBlocks.CABLE_MK1.get().defaultBlockState();
        var node=net.minecraft.world.level.block.Block.box(4,4,4,12,12,12);
        assertFalse(Shapes.joinIsNotEmpty(node,state.getShape(snapshot(Map.of()),BlockPos.ZERO),BooleanOp.NOT_SAME));
        for (Direction side : Direction.values()) {
            var connected=state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(side),true);
            var a=CableConnector.rotate(new CableConnector.Point(4,4,0),side);
            var b=CableConnector.rotate(new CableConnector.Point(12,12,4),side);
            var expected=Shapes.or(node,net.minecraft.world.level.block.Block.box(
                    Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),
                    Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z())));
            var empty=snapshot(Map.of());
            assertFalse(Shapes.joinIsNotEmpty(expected,connected.getShape(empty,BlockPos.ZERO),BooleanOp.NOT_SAME));
            assertFalse(Shapes.joinIsNotEmpty(expected,connected.getCollisionShape(empty,BlockPos.ZERO),BooleanOp.NOT_SAME));
        }
    }

    private static final BlockStateModel BASE=new BlockStateModel() {
        @Override public void collectParts(RandomSource random,List<BlockStateModelPart> output) {}
        @Override public Material.Baked particleMaterial() { throw new UnsupportedOperationException(); }
        @Override public int materialFlags() { return 0; }
    };
    private static final BlockStateModelPart CONNECTOR=new BlockStateModelPart() {
        @Override public List<BakedQuad> getQuads(Direction side) { return List.of(); }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public Material.Baked particleMaterial() { throw new UnsupportedOperationException(); }
        @Override public int materialFlags() { return 0; }
    };

    private static BlockAndTintGetter snapshot(Map<BlockPos,BlockState> states) {
        return snapshot(states,ModelData.EMPTY);
    }

    private static BlockAndTintGetter snapshot(Map<BlockPos,BlockState> states, ModelData modelData) {
        return (BlockAndTintGetter)Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class},(proxy,method,args)-> {
                    if (method.getName().equals("getBlockState")) return states.getOrDefault(args[0],Blocks.AIR.defaultBlockState());
                    if (method.getName().equals("getModelData")) return modelData;
                    // The snapshot holds block states only, so nothing here wears a facade.
                    if (method.getName().equals("getBlockEntity")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Every mode resolves to the same stand-in, so tests that ignore the band still work. */
    private static Map<Direction,Map<SideMode,BlockStateModelPart>> uniformParts(BlockStateModelPart part) {
        var parts=new EnumMap<Direction,Map<SideMode,BlockStateModelPart>>(Direction.class);
        for (Direction side : Direction.values()) {
            var perMode=new EnumMap<SideMode,BlockStateModelPart>(SideMode.class);
            for (SideMode mode : SideMode.values()) perMode.put(mode,part);
            parts.put(side,perMode);
        }
        return parts;
    }

    @Test
    void connectorsFollowMachineNeighborsInAllSixDirectionsWithoutExtraBlockStates(MinecraftServer server) {
        var cable=ModBlocks.CABLE_MK1.get().defaultBlockState();
        var model=new CableConnectorModel(BASE,CableKind.ENERGY,uniformParts(CONNECTOR));
        var states=new HashMap<BlockPos,BlockState>();
        var level=snapshot(states);
        for (Direction side : Direction.values()) {
            var connected=cable.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(side),true);
            states.clear();
            states.put(BlockPos.ZERO.relative(side),ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
            var output=new ArrayList<BlockStateModelPart>();
            model.collectParts(level,BlockPos.ZERO,connected,RandomSource.create(),output);
            assertEquals(List.of(CONNECTOR),output,side.toString());
            var collar=CableConnector.shape(1 << side.ordinal());
            assertFalse(Shapes.joinIsNotEmpty(collar,connected.getShape(level,BlockPos.ZERO),BooleanOp.ONLY_FIRST),
                    "The full collar must be selectable");
            // Entities collide through the context overload; the one without context is the
            // per-state cache, built without neighbours, so it never knows about collars.
            assertFalse(Shapes.joinIsNotEmpty(collar,connected.getCollisionShape(level,BlockPos.ZERO,CollisionContext.empty()),BooleanOp.ONLY_FIRST),
                    "The visible collar must be solid");
            output.clear();
            model.collectParts(level,BlockPos.ZERO,cable,RandomSource.create(),output);
            assertTrue(output.isEmpty(),"Disconnected machine face");
            states.put(BlockPos.ZERO.relative(side),cable);
            model.collectParts(level,BlockPos.ZERO,connected,RandomSource.create(),output);
            assertTrue(output.isEmpty(),"Cable-to-cable connections have no collar");
            assertFalse(Shapes.joinIsNotEmpty(collar,connected.getShape(level,BlockPos.ZERO),BooleanOp.AND),
                    "Cable-to-cable hitbox must not include a collar");
            states.clear();
            model.collectParts(level,BlockPos.ZERO,connected,RandomSource.create(),output);
            assertTrue(output.isEmpty(),"Removed machines leave no collar");
        }
        assertEquals(64,cable.getBlock().getStateDefinition().getPossibleStates().size());
    }

    @Test
    void cablesOfDifferentKindsAreNeighboursNotRuns(MinecraftServer server) {
        // An item cable beside an energy cable is a machine as far as either is concerned: the run
        // ends in a collar there, and only a cable of the same kind continues it.
        var model=new CableConnectorModel(BASE,CableKind.ENERGY,uniformParts(CONNECTOR));
        var energy=ModBlocks.CABLE_MK1.get().defaultBlockState();
        var items=ModBlocks.ITEM_CABLE_OPAQUE.get().defaultBlockState();
        for (var pair : List.of(List.of(energy,items),List.of(items,energy))) {
            var connected=pair.get(0).setValue(CableBlock.PROPERTY_BY_DIRECTION.get(Direction.NORTH),true);
            var output=new ArrayList<BlockStateModelPart>();
            model.collectParts(snapshot(Map.of(BlockPos.ZERO.north(),pair.get(1))),BlockPos.ZERO,connected,RandomSource.create(),output);
            assertEquals(List.of(CONNECTOR),output,"Other kind of cable gets a collar");
            output.clear();
            model.collectParts(snapshot(Map.of(BlockPos.ZERO.north(),pair.get(0))),BlockPos.ZERO,connected,RandomSource.create(),output);
            assertTrue(output.isEmpty(),"Same kind of cable is a run");
        }
    }

    @Test
    void collarHasAnOpenBoreAndTouchesOnlyTheIntendedBlockBoundary(MinecraftServer server) {
        for (Direction side : Direction.values()) {
            var center=CableConnector.rotate(new CableConnector.Point(8,8,0),side);
            assertEquals(8+side.getStepX()*8,center.x());
            assertEquals(8+side.getStepY()*8,center.y());
            assertEquals(8+side.getStepZ()*8,center.z());
            for (var box : CableConnector.BOXES) {
                assertFalse(box.x0()<12 && box.x1()>4 && box.y0()<12 && box.y1()>4,"Bore must clear eight-unit cable");
                for (var p : List.of(new CableConnector.Point(box.x0(),box.y0(),box.z0()),new CableConnector.Point(box.x1(),box.y1(),box.z1()))) {
                    var rotated=CableConnector.rotate(p,side);
                    assertTrue(rotated.x()>=0 && rotated.x()<=16 && rotated.y()>=0 && rotated.y()<=16 && rotated.z()>=0 && rotated.z()<=16);
                }
            }
            assertFalse(CableConnector.shape(1 << side.ordinal()).isEmpty());
        }
    }
}
