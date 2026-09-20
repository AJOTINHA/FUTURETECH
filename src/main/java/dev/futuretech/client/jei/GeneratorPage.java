package dev.futuretech.client.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One page of the recipe viewer for a machine that has no recipe book: what goes in, what fluid
 * comes out if any, and a few lines saying what the machine makes of it - the energy a generator
 * turns out has no ingredient to show, so it is told in words.
 */
public record GeneratorPage(List<Input> inputs, @Nullable FluidStack output, List<Component> lines) {
    /**
     * One slot in: a fluid or a cycling set of items, either consumed or, when {@code station} is
     * set, part of the machine the way a mold or an installed upgrade is.
     */
    public record Input(List<ItemStack> items, @Nullable FluidStack fluid, boolean station) {
        public static Input fluid(FluidStack fluid) { return new Input(List.of(), fluid, false); }
        public static Input items(ItemStack... items) { return new Input(List.of(items), null, false); }
        public static Input station(ItemStack item) { return new Input(List.of(item), null, true); }
    }

    public static GeneratorPage of(List<Input> inputs, @Nullable FluidStack output, Component... lines) {
        return new GeneratorPage(inputs, output, List.of(lines));
    }
}
