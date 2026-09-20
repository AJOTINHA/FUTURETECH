package dev.futuretech.block.entity;

import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Nothing to keep: the rain sensor has a block entity only to be ticked, like the daylight detector. */
public final class RainSensorBlockEntity extends BlockEntity {
    public RainSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RAIN_SENSOR.get(), pos, state);
    }
}
