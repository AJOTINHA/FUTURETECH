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

/** A selected plate/gear die consumes the recipe's exact ingot count when pressing completes. */
public record PressingRecipe(Ingredient ingredient, int count, boolean gear, ItemStackTemplate result, int duration)
        implements Recipe<PressingRecipe.Input> {
    public static final MapCodec<PressingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(PressingRecipe::ingredient),
            Codec.intRange(1, 64).fieldOf("count").forGetter(PressingRecipe::count),
            Codec.BOOL.optionalFieldOf("gear", false).forGetter(PressingRecipe::gear),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(PressingRecipe::result),
            Codec.intRange(1, 12000).optionalFieldOf("duration", 100).forGetter(PressingRecipe::duration)
    ).apply(i, PressingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PressingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, PressingRecipe::ingredient,
            ByteBufCodecs.VAR_INT, PressingRecipe::count,
            ByteBufCodecs.BOOL, PressingRecipe::gear,
            ItemStackTemplate.STREAM_CODEC, PressingRecipe::result,
            ByteBufCodecs.VAR_INT, PressingRecipe::duration, PressingRecipe::new);

    public record Input(ItemStack stack, boolean gear) implements RecipeInput {
        @Override public ItemStack getItem(int index) {
            if (index != 0) throw new IndexOutOfBoundsException(index);
            return stack;
        }
        @Override public int size() { return 1; }
    }

    @Override public boolean matches(Input input, Level level) {
        return input.gear() == gear && ingredient.test(input.stack()) && input.stack().getCount() >= count;
    }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<PressingRecipe> getSerializer() { return ModRecipes.PRESSING_SERIALIZER.get(); }
    @Override public RecipeType<PressingRecipe> getType() { return ModRecipes.PRESSING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.METAL_PRESS_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.create(ingredient); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
