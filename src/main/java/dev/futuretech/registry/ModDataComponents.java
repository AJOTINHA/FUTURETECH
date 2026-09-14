package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.item.ItemFilterMode;
import dev.futuretech.item.StoredTankFluid;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
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

    /** Stored fluid and its components travel with the tank item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StoredTankFluid>> TANK_FLUID = TYPES.register(
            "tank_fluid", () -> DataComponentType.<StoredTankFluid>builder()
                    .persistent(StoredTankFluid.CODEC)
                    .networkSynchronized(StoredTankFluid.STREAM_CODEC)
                    .build());

    /** The item kinds an item filter lists, one per slot; only the item matters, never the count. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> FILTER_ITEMS =
            TYPES.register("filter_items", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .build());

    /** Whether an item filter's list is the only thing let through or the only thing kept out. Absent means whitelist. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemFilterMode>> FILTER_MODE =
            TYPES.register("filter_mode", () -> DataComponentType.<ItemFilterMode>builder()
                    .persistent(ItemFilterMode.CODEC)
                    .networkSynchronized(ItemFilterMode.STREAM_CODEC)
                    .build());

    private ModDataComponents() {}
}
