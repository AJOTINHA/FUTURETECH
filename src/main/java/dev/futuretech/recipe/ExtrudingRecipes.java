package dev.futuretech.recipe;

import dev.futuretech.registry.ModRecipes;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The extrusion recipes in one fixed order, so an extruder and its screen number them the same way
 * and a choice can travel between them as a plain index.
 *
 * <p>Finding them on the server means walking every loaded recipe, which is far too much to do per
 * machine per check, so the walk's result is kept until {@link #invalidate} says the data packs
 * changed. The client has its own copy of these recipes already and only needs {@link #sorted}.
 */
public final class ExtrudingRecipes {
    private static List<RecipeHolder<ExtrudingRecipe>> loaded = List.of();
    private static @Nullable RecipeManager walked;
    private static boolean stale = true;

    private ExtrudingRecipes() {}

    /** Every extrusion recipe the server has loaded, in order. */
    @SuppressWarnings("unchecked")
    public static List<RecipeHolder<ExtrudingRecipe>> of(RecipeManager manager) {
        // Another manager is another server's recipes, which a reload event of ours never touches.
        if (stale || manager != walked) {
            walked = manager;
            loaded = sorted(manager.getRecipes().stream()
                    .filter(holder -> holder.value() instanceof ExtrudingRecipe)
                    .map(holder -> (RecipeHolder<ExtrudingRecipe>) holder)
                    .toList());
            stale = false;
        }
        return loaded;
    }

    /** Drops the kept walk, so the next call sees the recipes a reload brought. */
    public static void invalidate() {
        loaded = List.of();
        walked = null;
        stale = true;
    }

    /**
     * The order both sides agree on: the quickest product first, and the recipe id to break a tie.
     * Recipes arrive in whatever order the packs are read, and an index only means the same thing
     * on the machine and on its screen if it is fixed; sorting by cost on top of that makes the
     * button walk from the cheapest product to the dearest instead of down an alphabet, and leaves
     * the cheapest one as what a fresh machine makes.
     */
    public static List<RecipeHolder<ExtrudingRecipe>> sorted(Collection<RecipeHolder<ExtrudingRecipe>> recipes) {
        return recipes.stream()
                .filter(holder -> holder.value().getType() == ModRecipes.EXTRUDING.get())
                .sorted(Comparator.<RecipeHolder<ExtrudingRecipe>>comparingInt(holder -> holder.value().duration())
                        .thenComparing(holder -> holder.id().identifier()))
                .toList();
    }
}
