package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.api.side.SidedEnergy;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.CableBlockEntity;
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

    // One block entity type serves every battery tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY =
            TYPES.register("battery", () -> new BlockEntityType<>(
                    BatteryBlockEntity::new, ModBlocks.BATTERY_MK1.get()));

    // One block entity type serves every cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            TYPES.register("cable", () -> new BlockEntityType<>(
                    CableBlockEntity::new, ModBlocks.CABLE_MK1.get()));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // A null side is the machine's own unrestricted access; real faces follow their configured mode.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SOLID_FUEL_GENERATOR.get(), (generator, side) ->
                side == null ? generator.energy() : SidedEnergy.view(generator.energy(), generator.sideConfig().mode(side)));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BATTERY.get(), (battery, side) ->
                side == null ? battery.energy() : SidedEnergy.view(battery.energy(), battery.sideConfig().mode(side)));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CABLE.get(),
                (cable, side) -> cable.handler(side));
    }

    private ModBlockEntities() {}
}
