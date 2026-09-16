package dev.futuretech.recipe;

import dev.futuretech.registry.ModRecipes;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;

/** One ingredient becomes a stack of crushed material; the machine supplies time and energy. */
public final class CrushingRecipe extends SingleItemRecipe {
    public CrushingRecipe(Recipe.CommonInfo commonInfo, Ingredient ingredient, ItemStackTemplate result) {
        super(commonInfo, ingredient, result);
    }

    /** Public for the recipe viewer; vanilla only exposes the result through {@code assemble}. */
    @Override public ItemStackTemplate result() { return super.result(); }
    @Override public RecipeSerializer<CrushingRecipe> getSerializer() { return ModRecipes.CRUSHING_SERIALIZER.get(); }
    @Override public RecipeType<CrushingRecipe> getType() { return ModRecipes.CRUSHING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.CRUSHER_BOOK.get(); }
    @Override public String group() { return ""; }
    @Override public boolean isSpecial() { return true; }
}
