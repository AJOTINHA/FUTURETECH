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
import net.minecraft.world.item.Item;
import net.minecraft.core.Holder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import java.util.Optional;

/**
 * Each position of the table holds one ingredient and consumes exactly one item, or is blank -
 * written as {@code ""} in the book - so a recipe can keep the shape it had on the crafting table:
 * a coil is its two redstone on a diagonal, not three items in a row. Nothing may lie on a blank.
 */
public record AssemblingRecipe(List<Optional<Ingredient>> positions, ItemStackTemplate result, int duration)
        implements Recipe<AssemblingRecipe.Input> {
    /** A blank position on the table. */
    public static final Optional<Ingredient> BLANK = Optional.empty();
    /** One position of the book: an ingredient, or {@code ""} for a blank. */
    private static final Codec<Optional<Ingredient>> POSITION_CODEC = Codec.either(
            Codec.STRING.validate(text -> text.isEmpty() ? DataResult.success(text)
                    : DataResult.error(() -> "not a blank position: " + text)),
            Ingredient.CODEC)
            .xmap(either -> either.map(blank -> BLANK, Optional::of),
                    position -> position.<Either<String, Ingredient>>map(Either::right).orElse(Either.left("")));
    public static final MapCodec<AssemblingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            POSITION_CODEC.listOf(1, 9).validate(list -> list.stream().allMatch(Optional::isEmpty)
                    ? DataResult.error(() -> "a recipe needs at least one ingredient")
                    : DataResult.success(list)).fieldOf("ingredients").forGetter(AssemblingRecipe::positions),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(AssemblingRecipe::result),
            Codec.intRange(20, 12000).optionalFieldOf("duration", 80).forGetter(AssemblingRecipe::duration)
    ).apply(i, AssemblingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::positions,
            ItemStackTemplate.STREAM_CODEC, AssemblingRecipe::result,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::duration, AssemblingRecipe::new);

    public AssemblingRecipe { positions = List.copyOf(positions); }

    /** A recipe with no blanks: the ingredients fill the positions from the first. */
    public static AssemblingRecipe of(List<Ingredient> ingredients, ItemStackTemplate result, int duration) {
        return new AssemblingRecipe(ingredients.stream().map(Optional::of).toList(), result, duration);
    }

    /** How many positions the shape covers, blanks included. */
    public int size() { return positions.size(); }
    /** Whether the position is a blank of the shape rather than something to fetch. */
    public boolean blank(int slot) { return positions.get(slot).isEmpty(); }
    /** The ingredient at a position that is not blank. */
    public Ingredient ingredient(int slot) { return positions.get(slot).orElseThrow(); }
    /** Whether {@code stack} may lie at the position: the ingredient's own item, or nothing on a blank. */
    public boolean accepts(int slot, ItemStack stack) {
        return positions.get(slot).map(ingredient -> ingredient.test(stack)).orElseGet(stack::isEmpty);
    }
    /** One example item for the position, to draw where nothing lies yet; none on a blank. */
    public Optional<Item> preview(int slot) {
        return positions.get(slot).flatMap(ingredient -> ingredient.items().findFirst()).map(Holder::value);
    }
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int index) { return items.get(index); }
        @Override public int size() { return items.size(); }
    }
    @Override public boolean matches(Input input, Level level) {
        if (input.size() < positions.size()) return false;
        for (int slot = 0; slot < input.size(); slot++) {
            if (slot < positions.size() ? !accepts(slot, input.getItem(slot)) : !input.getItem(slot).isEmpty()) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<AssemblingRecipe> getSerializer() { return ModRecipes.ASSEMBLING_SERIALIZER.get(); }
    @Override public RecipeType<AssemblingRecipe> getType() { return ModRecipes.ASSEMBLING.get(); }
    @Override public RecipeBookCategory recipeBookCategory() { return ModRecipes.ASSEMBLER_BOOK.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
}
