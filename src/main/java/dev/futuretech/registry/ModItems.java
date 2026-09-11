package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FutureTech.MOD_ID);

    public static final DeferredItem<BlockItem> MACHINE_CASING = ITEMS.registerSimpleBlockItem(
            "machine_casing", ModBlocks.MACHINE_CASING);

    public static final DeferredItem<BlockItem> SOLID_FUEL_GENERATOR = ITEMS.registerSimpleBlockItem(
            "solid_fuel_generator", ModBlocks.SOLID_FUEL_GENERATOR);

    private ModItems() {}
}
