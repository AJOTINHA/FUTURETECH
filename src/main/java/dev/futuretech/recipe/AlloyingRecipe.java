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

/**
 * Two ingredients melt together into one alloy. The two lanes' slots are unordered: iron in the
 * first slot and coal in the second alloys the same as the other way round.
 */
public record AlloyingRecipe(Part first, Part second, ItemStackTemplate result, int duration)
        implements Recipe<AlloyingRecipe.Input> {
    /** One ingredient and how many of it a batch consumes. */
    public record Part(Ingredient ingredient, int count) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Part::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Part::count)
        ).apply(i, Part::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Part::ingredient,
                ByteBufCodecs.VAR_INT, Part::count, Part::new);

        public boolean fits(ItemStack stack) { return ingredient.test(stack) && stack.getCount() >= count; }
    }

    public static final MapCodec<AlloyingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Part.CODEC.fieldOf("first").forGetter(AlloyingRecipe::first),
            Part.CODEC.fieldOf("second").forGetter(AlloyingRecipe::second),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(AlloyingRecipe::result),
            Codec.intRange(1, 12000).optionalFieldOf("duration", 200).forGetter(AlloyingRecipe::duration)
    ).apply(i, AlloyingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> STREAM_CODEC = StreamCodec.composite(
            Part.STREAM_CODEC, AlloyingRecipe::first,
            Part.STREAM_CODEC, AlloyingRecipe::second,
            ItemStackTemplate.STREAM_CODEC, AlloyingRecipe::result,
            ByteBufCodecs.VAR_INT, AlloyingRecipe::duration, AlloyingRecipe::new);

    public record Input(ItemStack a, ItemStack b) implements RecipeInput {
        @Override public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> a;
                case 1 -> b;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
        @Override public int size() { return 2; }
        @Override public boolean isEmpty() { return a.isEmpty() && b.isEmpty(); }
    }

    /** Whether {@code a} is the first part and {@code b} the second, counts included. */
    public boolean matchesOrdered(ItemStack a, ItemStack b) { return first.fits(a) && second.fits(b); }

    @Override public boolean matches(Input input, Level level) {
        return matchesOrdered(input.a(), input.b()) || matchesOrdered(input.b(), input.a());
    }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<AlloyingRecipe> getSerializer() { return ModRecipes.ALLOYING_SERIALIZER.get(); }
    @Override public RecipeType<AlloyingRecipe> getType() { return ModRecipes.ALLOYING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.SMELTERY_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.create(List.of(first.ingredient(), second.ingredient())); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
