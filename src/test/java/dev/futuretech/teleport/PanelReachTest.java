package dev.futuretech.teleport;

import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The panel, a cable and a pad, wired the way the world wires them: the cable's links are not set
 * by hand here but worked out by its own shape update, which is what the game runs when a block is
 * placed beside it. The grid's own test asserts the walk; this one asserts the links it walks.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class PanelReachTest {
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, BlockEntity> entities = new HashMap<>();

    private LevelReader level() {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> entities.get(args[0]);
                    case "hasChunkAt" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** The screen looks west, so the back that takes a cable is turned east, where the run is. */
    private static BlockState facingWest() {
        return ModBlocks.NETWORK_PANEL.get().defaultBlockState()
                .setValue(dev.futuretech.block.NetworkPanelBlock.FACING, Direction.WEST);
    }

    /** Runs the cable's own shape update for all six sides, the way a placement does. */
    private void settle(BlockPos pos) {
        BlockState cable = states.get(pos);
        for (Direction side : Direction.values()) {
            BlockPos beyond = pos.relative(side);
            cable = cable.updateShape(level(), null, pos, side, beyond,
                    states.getOrDefault(beyond, Blocks.AIR.defaultBlockState()), RandomSource.create());
            states.put(pos, cable);
        }
    }

    @Test
    void theCableLinksThePanelToThePadAndThePanelSeesIt(MinecraftServer server) {
        BlockPos panel = BlockPos.ZERO;
        BlockPos cable = panel.east();
        BlockPos pad = cable.east();
        states.put(panel, facingWest());
        states.put(cable, ModBlocks.NETWORK_CABLE.get().defaultBlockState());
        BlockState padState = ModBlocks.TELEPORTER.get().defaultBlockState();
        states.put(pad, padState);
        entities.put(pad, new TeleporterBlockEntity(pad, padState));
        settle(cable);

        BlockState wired = states.get(cable);
        assertTrue(wired.getValue(AbstractCableBlock.WEST), "the cable links to the panel's back");
        assertTrue(wired.getValue(AbstractCableBlock.EAST), "the cable links to the pad");
        assertEquals(List.of(pad), TeleporterGrid.teleporters(level(), panel));
    }

    /** Two cables between them, each settled against what is really around it. */
    @Test
    void aRunOfCablesCarriesTheSameReach(MinecraftServer server) {
        BlockPos panel = BlockPos.ZERO;
        BlockPos near = panel.east();
        BlockPos far = panel.east(2);
        BlockPos pad = panel.east(3);
        states.put(panel, facingWest());
        states.put(near, ModBlocks.NETWORK_CABLE.get().defaultBlockState());
        states.put(far, ModBlocks.NETWORK_CABLE.get().defaultBlockState());
        BlockState padState = ModBlocks.TELEPORTER.get().defaultBlockState();
        states.put(pad, padState);
        entities.put(pad, new TeleporterBlockEntity(pad, padState));
        settle(near);
        settle(far);
        settle(near);

        assertTrue(states.get(near).getValue(AbstractCableBlock.WEST), "the near cable links to the panel");
        assertTrue(states.get(far).getValue(AbstractCableBlock.EAST), "the far cable links to the pad");
        assertEquals(List.of(pad), TeleporterGrid.teleporters(level(), panel));
    }
}
