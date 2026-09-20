package dev.futuretech.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * What the melter does to one item: which one, how much of which fluid it becomes, and how many
 * ticks that takes at MK1. The item's result is a fluid, not an item, so {@link #assemble} has
 * nothing to hand back; the machine reads {@link #result()} straight. The fluid is a template,
 * not a stack: recipes load before fluids have their components, and a stack cannot be read then.
 */
public record MeltingRecipe(Ingredient ingredient, FluidStackTemplate result, int duration) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<MeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(MeltingRecipe::ingredient),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(MeltingRecipe::result),
            Codec.intRange(1, 12000).optionalFieldOf("duration", 100).forGetter(MeltingRecipe::duration)
    ).apply(i, MeltingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeltingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MeltingRecipe::ingredient,
            FluidStackTemplate.STREAM_CODEC, MeltingRecipe::result,
            ByteBufCodecs.VAR_INT, MeltingRecipe::duration, MeltingRecipe::new);

    /** The fluid this recipe makes, as a stack to put in a tank. */
    public FluidStack made() { return result.create(); }

    @Override public boolean matches(SingleRecipeInput input, Level level) { return ingredient.test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<MeltingRecipe> getSerializer() { return ModRecipes.MELTING_SERIALIZER.get(); }
    @Override public RecipeType<MeltingRecipe> getType() { return ModRecipes.MELTING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.MELTER_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.create(ingredient); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
