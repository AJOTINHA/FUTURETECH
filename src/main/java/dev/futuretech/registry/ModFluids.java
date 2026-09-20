package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.fluid.SteamFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FutureTech.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, FutureTech.MOD_ID);
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(
            FluidType.Properties.create().descriptionId("fluid.futuretech.steam").density(-100).temperature(373).viscosity(100)));
    public static final DeferredHolder<Fluid, SteamFluid> STEAM = FLUIDS.register("steam", SteamFluid::new);
    private ModFluids() {}
}
