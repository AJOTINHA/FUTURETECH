package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.api.side.SidedEnergy;
import dev.futuretech.api.side.SidedFluids;
import dev.futuretech.api.side.SidedItems;
import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.block.entity.ChargerBlockEntity;
import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.block.entity.CableBlockEntity;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
import dev.futuretech.block.entity.FluidCableBlockEntity;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.block.entity.LavaGeneratorBlockEntity;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.block.entity.MetalPressBlockEntity;
import dev.futuretech.block.entity.PaintMachineBlockEntity;
import dev.futuretech.block.entity.SmelteryBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import dev.futuretech.item.PortableBatteryItem;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE, FutureTech.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolidFuelGeneratorBlockEntity>> SOLID_FUEL_GENERATOR =
            TYPES.register("solid_fuel_generator", () -> new BlockEntityType<>(
                    SolidFuelGeneratorBlockEntity::new, ModBlocks.SOLID_FUEL_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LavaGeneratorBlockEntity>> LAVA_GENERATOR =
            TYPES.register("lava_generator", () -> new BlockEntityType<>(
                    LavaGeneratorBlockEntity::new, ModBlocks.LAVA_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            TYPES.register("electric_furnace", () -> new BlockEntityType<>(
                    ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaneMachineBlockEntity>> CRUSHER =
            TYPES.register("crusher", () -> new BlockEntityType<>(
                    (pos, state) -> new LaneMachineBlockEntity(LaneMachineKind.CRUSHER, pos, state), ModBlocks.CRUSHER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargerBlockEntity>> CHARGER =
            TYPES.register("charger", () -> new BlockEntityType<>(ChargerBlockEntity::new, ModBlocks.CHARGER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaneMachineBlockEntity>> SAWMILL =
            TYPES.register("sawmill", () -> new BlockEntityType<>(
                    (pos, state) -> new LaneMachineBlockEntity(LaneMachineKind.SAWMILL, pos, state), ModBlocks.SAWMILL.get()));

    // One block entity type serves every battery tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalPressBlockEntity>> METAL_PRESS =
            TYPES.register("metal_press", () -> new BlockEntityType<>(MetalPressBlockEntity::new, ModBlocks.METAL_PRESS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelteryBlockEntity>> SMELTERY =
            TYPES.register("smeltery", () -> new BlockEntityType<>(SmelteryBlockEntity::new, ModBlocks.SMELTERY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaintMachineBlockEntity>> PAINT_MACHINE =
            TYPES.register("paint_machine", () -> new BlockEntityType<>(PaintMachineBlockEntity::new, ModBlocks.PAINT_MACHINE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY =
            TYPES.register("battery", () -> new BlockEntityType<>(
                    BatteryBlockEntity::new, ModBlocks.BATTERY_MK1.get()));

    // A single storage tank with fluid access on every face.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
            TYPES.register("fluid_tank", () -> new BlockEntityType<>(
                    FluidTankBlockEntity::new, ModBlocks.FLUID_TANK.get()));

    // One block entity type serves every cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            TYPES.register("cable", () -> new BlockEntityType<>(
                    CableBlockEntity::new, ModBlocks.CABLE_MK1.get()));

    // One block entity type serves every item cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemCableBlockEntity>> ITEM_CABLE =
            TYPES.register("item_cable", () -> new BlockEntityType<>(
                    ItemCableBlockEntity::new, ModBlocks.ITEM_CABLE_OPAQUE.get(), ModBlocks.ITEM_CABLE.get()));

    // One block entity type serves every fluid cable tier; list each tier's block here.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidCableBlockEntity>> FLUID_CABLE =
            TYPES.register("fluid_cable", () -> new BlockEntityType<>(
                    FluidCableBlockEntity::new, ModBlocks.FLUID_CABLE_OPAQUE.get(), ModBlocks.FLUID_CABLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.futuretech.block.entity.AssemblerBlockEntity>> ASSEMBLER =
            TYPES.register("assembler", () -> new BlockEntityType<>(dev.futuretech.block.entity.AssemblerBlockEntity::new,
                    ModBlocks.ASSEMBLY_TABLE.get(), ModBlocks.TRANSPORT_ARM.get(), ModBlocks.ASSEMBLY_ARM.get(), ModBlocks.ASSEMBLER_TERMINAL.get()));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Energy items: the portable battery, and the battery blocks it can fill while they sit in a pocket.
        for (var battery : List.of(ModItems.PORTABLE_BATTERY, ModItems.PORTABLE_BATTERY_MK2, ModItems.PORTABLE_BATTERY_MK3, ModItems.PORTABLE_BATTERY_MK4)) {
            event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> battery.get().handler(access), battery.get());
        }
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                ModDataComponents.ENERGY.get(), ModItems.BATTERY_MK1.get().tier(stack).capacity()), ModItems.BATTERY_MK1.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, METAL_PRESS.get(), (press, side) ->
                SidedEnergy.view(press.energy(), press.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, METAL_PRESS.get(), (press, side) ->
                SidedItems.view(press, press.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SMELTERY.get(), (smeltery, side) ->
                SidedEnergy.view(smeltery.energy(), smeltery.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, SMELTERY.get(), (smeltery, side) ->
                SidedItems.view(smeltery, smeltery.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, PAINT_MACHINE.get(), (painter, side) ->
                SidedEnergy.view(painter.energy(), painter.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, PAINT_MACHINE.get(), (painter, side) ->
                SidedItems.view(painter, painter.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ASSEMBLER.get(), (assembler, side) -> assembler.energyHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK.get(), (tank, side) -> tank.handler(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CHARGER.get(), (charger, side) ->
                SidedEnergy.view(charger.energy(), charger.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, CHARGER.get(), (charger, side) ->
                SidedItems.view(charger, charger.sideConfig(), side));
        for (var lane : List.of(CRUSHER, SAWMILL)) {
            event.registerBlockEntity(Capabilities.Energy.BLOCK, lane.get(), (machine, side) ->
                    SidedEnergy.view(machine.energy(), machine.sideConfig(), side));
            event.registerBlockEntity(Capabilities.Item.BLOCK, lane.get(), (machine, side) ->
                    SidedItems.view(machine, machine.sideConfig(), side));
        }
        // A null side is the machine's own unrestricted access; real faces follow their configured mode.
        // Only the battery configures energy; the generator and furnace pass every face straight through.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SOLID_FUEL_GENERATOR.get(), (generator, side) ->
                SidedEnergy.view(generator.energy(), generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ELECTRIC_FURNACE.get(), (furnace, side) ->
                SidedEnergy.view(furnace.energy(), furnace.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LAVA_GENERATOR.get(), (generator, side) ->
                SidedEnergy.view(generator.energy(), generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BATTERY.get(), (battery, side) ->
                SidedEnergy.view(battery.energy(), battery.sideConfig(), side));

        // Items follow the face modes. The battery has no inventory, so it offers no item handler.
        event.registerBlockEntity(Capabilities.Item.BLOCK, SOLID_FUEL_GENERATOR.get(), (generator, side) ->
                SidedItems.view(generator, generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ELECTRIC_FURNACE.get(), (furnace, side) ->
                SidedItems.view(furnace, furnace.sideConfig(), side));
        // Lava only goes in, through the faces in an input mode; buckets follow the same faces above.
        event.registerBlockEntity(Capabilities.Item.BLOCK, LAVA_GENERATOR.get(), (generator, side) ->
                SidedItems.view(generator, generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, LAVA_GENERATOR.get(), (generator, side) ->
                SidedFluids.intake(generator.lava(), generator.sideConfig(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CABLE.get(),
                (cable, side) -> cable.handler(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_CABLE.get(),
                (cable, side) -> cable.handler(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_CABLE.get(),
                (cable, side) -> cable.handler(side));
    }

    private ModBlockEntities() {}
}
