package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.recipe.CrushingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FutureTech.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FutureTech.MOD_ID);
    public static final DeferredRegister<RecipeBookCategory> BOOKS = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, FutureTech.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<CrushingRecipe>> CRUSHING = TYPES.register("crushing", () -> new RecipeType<>() {
        @Override public String toString() { return "futuretech:crushing"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CrushingRecipe>> CRUSHING_SERIALIZER =
            SERIALIZERS.register("crushing", () -> new RecipeSerializer<>(SingleItemRecipe.simpleMapCodec(CrushingRecipe::new),
                    SingleItemRecipe.simpleStreamCodec(CrushingRecipe::new)));
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> CRUSHER_BOOK = BOOKS.register("crusher", RecipeBookCategory::new);
    private ModRecipes() {}
}
