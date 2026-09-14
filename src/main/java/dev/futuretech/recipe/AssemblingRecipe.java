package dev.futuretech.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Each ingredient occupies one dedicated table position and consumes exactly one item. */
public record AssemblingRecipe(List<Ingredient> ingredients, ItemStackTemplate result, int duration)
        implements Recipe<AssemblingRecipe.Input> {
    public static final MapCodec<AssemblingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(AssemblingRecipe::ingredients),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(AssemblingRecipe::result),
            Codec.intRange(20, 12000).optionalFieldOf("duration", 80).forGetter(AssemblingRecipe::duration)
    ).apply(i, AssemblingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::ingredients,
            ItemStackTemplate.STREAM_CODEC, AssemblingRecipe::result,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::duration, AssemblingRecipe::new);

    public AssemblingRecipe { ingredients = List.copyOf(ingredients); }
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int index) { return items.get(index); }
        @Override public int size() { return items.size(); }
    }
    @Override public boolean matches(Input input, Level level) {
        if (input.size() < ingredients.size()) return false;
        for (int slot = 0; slot < input.size(); slot++) {
            if (slot < ingredients.size()) {
                if (!ingredients.get(slot).test(input.getItem(slot))) return false;
            } else if (!input.getItem(slot).isEmpty()) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<AssemblingRecipe> getSerializer() { return ModRecipes.ASSEMBLING_SERIALIZER.get(); }
    @Override public RecipeType<AssemblingRecipe> getType() { return ModRecipes.ASSEMBLING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.ASSEMBLER_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.create(ingredients); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
