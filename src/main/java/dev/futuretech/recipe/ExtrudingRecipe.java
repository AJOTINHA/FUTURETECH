package dev.futuretech.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Two fluids extruded into one item: which two, how much of each a batch drinks, and how many ticks
 * that takes at MK1. What goes in are fluids, so nothing is ever laid out in a slot and the recipe
 * has no ingredients to place. The two tanks are unordered: water in the first tank and lava in the
 * second extrudes the same as the other way round, and {@link #matchesOrdered} says which way it was.
 *
 * <p>Several recipes may share the same pair of fluids - water and lava give cobblestone, stone or
 * obsidian - so which one runs is the player's choice on the machine, never a search.
 *
 * <p>A recipe may name an {@link #upgrade}: an item tag one of the machine's upgrade slots has to
 * hold for it to be on offer. Those recipes replace the plain ones rather than join them - a
 * machine carrying a sand upgrade makes sand, gravel and their kin instead of stone - so the plain
 * recipes are only on offer while no such upgrade is installed.
 */
public record ExtrudingRecipe(Part first, Part second, ItemStackTemplate result, int duration,
                              Optional<TagKey<Item>> upgrade)
        implements Recipe<ExtrudingRecipe.Input> {
    /** A plain recipe, on offer whenever no product upgrade is installed. */
    public ExtrudingRecipe(Part first, Part second, ItemStackTemplate result, int duration) {
        this(first, second, result, duration, Optional.empty());
    }

    /**
     * One feed: the fluid a tank has to hold, and how much of it a batch drinks - which may be
     * none at all. A machine fed water and lava turns out cobblestone forever without spending
     * either of them, while obsidian costs a bucket of both; both still need the two fluids there.
     */
    public record Part(Holder<Fluid> fluid, int consumes) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(Part::fluid),
                Codec.intRange(0, 1_000_000).fieldOf("consumes").forGetter(Part::consumes)
        ).apply(i, Part::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.FLUID), Part::fluid,
                ByteBufCodecs.VAR_INT, Part::consumes, Part::new);

        /** The least a tank must hold: a batch's worth, or a drop of it when a batch drinks none. */
        public int required() { return Math.max(1, consumes); }

        /**
         * Whether a tank covers this part. The machine asks this every tick, so a tank is its
         * resource and its amount rather than a stack: reading one costs nothing, building one
         * allocates.
         */
        public boolean covered(FluidResource held, int amount) {
            return !held.isEmpty() && held.getFluid() == fluid.value() && amount >= required();
        }

        /** The same question about a stack, for tests and for the recipe machinery. */
        public boolean covered(FluidStack held) { return covered(FluidResource.of(held), held.getAmount()); }
    }

    public static final MapCodec<ExtrudingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Part.CODEC.fieldOf("first").forGetter(ExtrudingRecipe::first),
            Part.CODEC.fieldOf("second").forGetter(ExtrudingRecipe::second),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ExtrudingRecipe::result),
            Codec.intRange(1, 12000).optionalFieldOf("duration", 200).forGetter(ExtrudingRecipe::duration),
            TagKey.codec(Registries.ITEM).optionalFieldOf("upgrade").forGetter(ExtrudingRecipe::upgrade)
    ).apply(i, ExtrudingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtrudingRecipe> STREAM_CODEC = StreamCodec.composite(
            Part.STREAM_CODEC, ExtrudingRecipe::first,
            Part.STREAM_CODEC, ExtrudingRecipe::second,
            ItemStackTemplate.STREAM_CODEC, ExtrudingRecipe::result,
            ByteBufCodecs.VAR_INT, ExtrudingRecipe::duration,
            ByteBufCodecs.optional(TagKey.streamCodec(Registries.ITEM)), ExtrudingRecipe::upgrade, ExtrudingRecipe::new);

    /** What the two tanks hold when something asks a recipe about them. */
    public record Input(FluidStack a, FluidStack b) implements RecipeInput {
        /** Nothing about an extrusion sits in a slot; a recipe reads {@link #a} and {@link #b} instead. */
        @Override public ItemStack getItem(int index) { throw new IndexOutOfBoundsException(index); }
        @Override public int size() { return 0; }
        @Override public boolean isEmpty() { return a.isEmpty() && b.isEmpty(); }
    }

    /** Whether the first tank covers the first part and the second tank the second. */
    public boolean matchesOrdered(FluidResource a, int amountA, FluidResource b, int amountB) {
        return first.covered(a, amountA) && second.covered(b, amountB);
    }

    /** Whether the two tanks hold enough of both parts, either way round. */
    public boolean fits(FluidResource a, int amountA, FluidResource b, int amountB) {
        return matchesOrdered(a, amountA, b, amountB) || matchesOrdered(b, amountB, a, amountA);
    }

    /** The same question about two stacks, for the recipe machinery and for tests. */
    public boolean fits(FluidStack a, FluidStack b) {
        return fits(FluidResource.of(a), a.getAmount(), FluidResource.of(b), b.getAmount());
    }

    @Override public boolean matches(Input input, Level level) { return fits(input.a(), input.b()); }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<ExtrudingRecipe> getSerializer() { return ModRecipes.EXTRUDING_SERIALIZER.get(); }
    @Override public RecipeType<ExtrudingRecipe> getType() { return ModRecipes.EXTRUDING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.EXTRUDER_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
