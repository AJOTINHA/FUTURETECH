package dev.futuretech.recipe;

import dev.futuretech.block.LaneMachineKind;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;

/**
 * One ingredient becomes a stack of results; the machine supplies time and energy. Crushing and
 * sawing are the same recipe shape under different registered types, so the kind picks the type.
 */
public final class LaneMachineRecipe extends SingleItemRecipe {
    private final LaneMachineKind kind;

    public LaneMachineRecipe(LaneMachineKind kind, Recipe.CommonInfo commonInfo, Ingredient ingredient, ItemStackTemplate result) {
        super(commonInfo, ingredient, result);
        this.kind = kind;
    }

    /** Public for the recipe viewer; vanilla only exposes the result through {@code assemble}. */
    @Override public ItemStackTemplate result() { return super.result(); }
    @Override public RecipeSerializer<LaneMachineRecipe> getSerializer() { return kind.serializer(); }
    @Override public RecipeType<LaneMachineRecipe> getType() { return kind.recipeType(); }
    @Override public RecipeBookCategory recipeBookCategory() { return kind.book(); }
    @Override public String group() { return ""; }
    @Override public boolean isSpecial() { return true; }
}
