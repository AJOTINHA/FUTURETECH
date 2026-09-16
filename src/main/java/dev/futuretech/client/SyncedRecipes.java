package dev.futuretech.client;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.List;

/**
 * The machine recipes the server sent (see {@code ModRecipes.syncToClients}). Vanilla keeps no
 * recipes on the client, so the recipe viewer reads them from here instead of the recipe manager.
 */
public final class SyncedRecipes {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static RecipeMap recipes = RecipeMap.EMPTY;

    private SyncedRecipes() {}

    public static void onReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
        LOG.debug("Received {} recipes of types {}", recipes.values().size(), event.getRecipeTypes());
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> of(RecipeType<T> type) {
        return List.copyOf(recipes.byType(type));
    }
}
