package dev.futuretech.client.jei;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.BoilerBlockEntity;
import dev.futuretech.block.entity.LavaGeneratorBlockEntity;
import dev.futuretech.block.entity.SolarGeneratorBlockEntity;
import dev.futuretech.block.entity.SteamTurbineBlockEntity;
import dev.futuretech.block.entity.WindGeneratorBlockEntity;
import dev.futuretech.client.jei.GeneratorPage.Input;
import dev.futuretech.registry.ModFluids;
import dev.futuretech.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;

/**
 * The pages of the machines that have no recipe book, written from the same constants the
 * machines run on, so the numbers the viewer shows are the numbers the machines use.
 */
public final class GeneratorPages {
    private GeneratorPages() {}

    private static final int BUCKET = FluidType.BUCKET_VOLUME;

    private static Component line(String key, Object... arguments) {
        return Component.translatable("jei.futuretech." + key, arguments);
    }

    private static String thousands(long value) { return String.format("%,d", value); }

    private static int mk1(int base) { return MachineLevel.consumption(base, 1); }

    private static int mk4(int base) { return MachineLevel.consumption(base, MachineLevel.MAX); }

    /** The boiler's three ways to heat a bucket of water: solid fuel, lava with its upgrade, FE with its upgrade. */
    public static List<GeneratorPage> boiler() {
        var water = Input.fluid(new FluidStack(Fluids.WATER, BUCKET));
        var steam = new FluidStack(ModFluids.STEAM.get(), BUCKET * BoilerBlockEntity.STEAM_PER_WATER);
        int steamPerTick = mk1(BoilerBlockEntity.WATER_PER_TICK) * BoilerBlockEntity.STEAM_PER_WATER;
        int steamPerTickMk4 = mk4(BoilerBlockEntity.WATER_PER_TICK) * BoilerBlockEntity.STEAM_PER_WATER;
        Component rate = line("boiler.rate", steamPerTick, steamPerTickMk4);
        Component cheaper = line("boiler.cheaper", BoilerBlockEntity.MK_HEAT_PERCENT[MachineLevel.MAX - 1]);
        // A bucket of water is a thousand units of heat at MK1: what that costs in each source.
        int coalHeat = 1_600;
        int lavaPerBucket = BUCKET / BoilerBlockEntity.HEAT_PER_LAVA_MB;
        return List.of(
                GeneratorPage.of(List.of(water, Input.items(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL),
                                new ItemStack(Items.COAL_BLOCK), new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.DRIED_KELP_BLOCK))),
                        steam, rate, line("boiler.solid", thousands((long) coalHeat * BoilerBlockEntity.STEAM_PER_WATER)), cheaper),
                GeneratorPage.of(List.of(water, Input.fluid(new FluidStack(Fluids.LAVA, lavaPerBucket)),
                                Input.station(new ItemStack(ModItems.LAVA_UPGRADE.get()))),
                        steam, rate, line("boiler.lava", BoilerBlockEntity.HEAT_PER_LAVA_MB * BoilerBlockEntity.STEAM_PER_WATER), cheaper),
                GeneratorPage.of(List.of(water, Input.station(new ItemStack(ModItems.ENERGY_UPGRADE.get()))),
                        steam, rate, line("boiler.energy", BoilerBlockEntity.FE_PER_HEAT), line("boiler.energy_loss")));
    }

    public static List<GeneratorPage> turbine() {
        var steam = Input.fluid(new FluidStack(ModFluids.STEAM.get(), BUCKET));
        return List.of(GeneratorPage.of(List.of(steam), null,
                line("turbine.yield", SteamTurbineBlockEntity.FE_PER_MB),
                line("generator.rate", mk1(SteamTurbineBlockEntity.GENERATION_PER_TICK), mk4(SteamTurbineBlockEntity.GENERATION_PER_TICK)),
                line("turbine.steam", mk1(SteamTurbineBlockEntity.STEAM_PER_TICK), mk4(SteamTurbineBlockEntity.STEAM_PER_TICK)),
                line("generator.every_face")));
    }

    public static List<GeneratorPage> lavaGenerator() {
        var lava = Input.fluid(new FluidStack(Fluids.LAVA, BUCKET));
        int perMb = LavaGeneratorBlockEntity.GENERATION_PER_TICK / LavaGeneratorBlockEntity.LAVA_PER_TICK;
        return List.of(GeneratorPage.of(List.of(lava), null,
                line("lava_generator.yield", perMb),
                line("lava_generator.rate", LavaGeneratorBlockEntity.GENERATION_PER_TICK),
                line("generator.every_face")));
    }

    public static List<GeneratorPage> solar() {
        return List.of(GeneratorPage.of(List.of(), null,
                line("solar.sky"),
                line("generator.rate", mk1(SolarGeneratorBlockEntity.GENERATION_PER_TICK), mk4(SolarGeneratorBlockEntity.GENERATION_PER_TICK)),
                line("solar.weather")));
    }

    public static List<GeneratorPage> wind() {
        return List.of(GeneratorPage.of(List.of(), null,
                line("wind.height"),
                line("generator.rate", mk1(WindGeneratorBlockEntity.GENERATION_PER_TICK), mk4(WindGeneratorBlockEntity.GENERATION_PER_TICK)),
                line("wind.obstacles")));
    }
}
