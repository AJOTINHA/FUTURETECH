package dev.futuretech.recipe;

import com.mojang.serialization.MapCodec;
import dev.futuretech.api.facade.CableFacades;
import dev.futuretech.item.FacadeItem;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Four panels from one block and four steel plates, the block in the middle and a plate on each of
 * its sides. Any block that may be worn as a facade works, so the recipe is written in code rather
 * than one JSON per block: what comes out carries the block that went in.
 */
public final class FacadeRecipe extends CustomRecipe {
    /** How many panels one block makes: the four sides a cable run usually shows. */
    public static final int YIELD = 4;
    /**
     * The recipe carries nothing of its own; what varies is the block the player puts in. Both
     * codecs hand out this one instance, because the stream codec that writes it to the client
     * checks identity: a second instance, such as one parsed fresh from the JSON, is refused and
     * takes the connection down with it.
     */
    public static final FacadeRecipe INSTANCE = new FacadeRecipe();
    public static final MapCodec<FacadeRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return covered(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        BlockState state = covered(input);
        if (state == null) return ItemStack.EMPTY;
        ItemStack result = FacadeItem.of(state);
        result.setCount(YIELD);
        return result;
    }

    /**
     * The block being covered, or null when the grid is not this recipe: the middle has to hold a
     * block that may be worn, its four sides a steel plate each, and the corners nothing.
     */
    private static @Nullable BlockState covered(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3) return null;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                ItemStack stack = input.getItem(x, y);
                boolean corner = x != 1 && y != 1;
                boolean middle = x == 1 && y == 1;
                if (corner) {
                    if (!stack.isEmpty()) return null;
                } else if (!middle && !stack.is(ModItems.STEEL_PLATE.get())) {
                    return null;
                }
            }
        }
        if (!(input.getItem(1, 1).getItem() instanceof BlockItem block)) return null;
        BlockState state = block.getBlock().defaultBlockState();
        return CableFacades.isValid(state) ? state : null;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.FACADE.get();
    }
}
