package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.api.side.SidedEnergy;
import dev.futuretech.api.side.SidedItems;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.CableBlockEntity;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.block.entity.CrusherBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE, FutureTech.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolidFuelGeneratorBlockEntity>> SOLID_FUEL_GENERATOR =
            TYPES.register("solid_fuel_generator", () -> new BlockEntityType<>(
                    SolidFuelGeneratorBlockEntity::new, ModBlocks.SOLID_FUEL_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            TYPES.register("electric_furnace", () -> new BlockEntityType<>(
                    ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER =
            TYPES.register("crusher", () -> new BlockEntityType<>(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()));

    // One block entity type serves every battery tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY =
            TYPES.register("battery", () -> new BlockEntityType<>(
                    BatteryBlockEntity::new, ModBlocks.BATTERY_MK1.get()));

    // One block entity type serves every cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            TYPES.register("cable", () -> new BlockEntityType<>(
                    CableBlockEntity::new, ModBlocks.CABLE_MK1.get()));

    // One block entity type serves every item cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemCableBlockEntity>> ITEM_CABLE =
            TYPES.register("item_cable", () -> new BlockEntityType<>(
                    ItemCableBlockEntity::new, ModBlocks.ITEM_CABLE_OPAQUE.get()));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CRUSHER.get(), (crusher, side) ->
                SidedEnergy.view(crusher.energy(), crusher.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, CRUSHER.get(), (crusher, side) ->
                SidedItems.view(crusher, crusher.sideConfig(), side));
        // A null side is the machine's own unrestricted access; real faces follow their configured mode.
        // Only the battery configures energy; the generator and furnace pass every face straight through.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SOLID_FUEL_GENERATOR.get(), (generator, side) ->
                SidedEnergy.view(generator.energy(), generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ELECTRIC_FURNACE.get(), (furnace, side) ->
                SidedEnergy.view(furnace.energy(), furnace.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BATTERY.get(), (battery, side) ->
                SidedEnergy.view(battery.energy(), battery.sideConfig(), side));

        // Items follow the face modes. The battery has no inventory, so it offers no item handler.
        event.registerBlockEntity(Capabilities.Item.BLOCK, SOLID_FUEL_GENERATOR.get(), (generator, side) ->
                SidedItems.view(generator, generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ELECTRIC_FURNACE.get(), (furnace, side) ->
                SidedItems.view(furnace, furnace.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CABLE.get(),
                (cable, side) -> cable.handler(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_CABLE.get(),
                (cable, side) -> cable.handler(side));
    }

    private ModBlockEntities() {}
}
