package dev.futuretech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.util.function.Predicate;

public final class EnergyNetworkUtil {
    /**
     * Pushes the source's remaining output budget for this tick into adjacent energy receivers.
     * The first face rotates each tick so several consumers can share a small supply.
     *
     * @param throughSide which faces may push; lets a machine honour its side configuration and
     *                    skip neighbours that would bounce energy back
     */
    public static void pushToNeighbours(Level level, BlockPos pos, TickLimitedEnergyHandler source,
                                        Predicate<Direction> throughSide) {
        Direction[] sides = Direction.values();
        int first = (int) (level.getGameTime() % sides.length);
        for (int i = 0; i < sides.length && source.outputRemaining() > 0; i++) {
            Direction side = sides[(first + i) % sides.length];
            BlockPos neighbour = pos.relative(side);
            if (!throughSide.test(side) || !level.hasChunkAt(neighbour.getX(), neighbour.getZ())) continue;
            EnergyHandler receiver = level.getCapability(Capabilities.Energy.BLOCK, neighbour, side.getOpposite());
            EnergyHandlerUtil.move(source, receiver, source.outputRemaining(), null);
        }
    }

    private EnergyNetworkUtil() {}
}
