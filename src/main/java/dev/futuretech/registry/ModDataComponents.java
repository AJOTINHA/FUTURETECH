package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE, FutureTech.MOD_ID);

    /** Energy stored inside an item, in FE. Absent means empty. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = TYPES.register(
            "energy", () -> DataComponentType.<Integer>builder()
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private ModDataComponents() {}
}
