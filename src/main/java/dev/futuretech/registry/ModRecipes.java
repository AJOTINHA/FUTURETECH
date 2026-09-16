package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.recipe.AlloyingRecipe;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.recipe.LaneMachineRecipe;
import dev.futuretech.recipe.PressingRecipe;
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
    public static final DeferredHolder<RecipeType<?>, RecipeType<LaneMachineRecipe>> CRUSHING = laneType("crushing");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LaneMachineRecipe>> CRUSHING_SERIALIZER =
            laneSerializer("crushing", LaneMachineKind.CRUSHER);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> CRUSHER_BOOK = BOOKS.register("crusher", RecipeBookCategory::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<LaneMachineRecipe>> SAWING = laneType("sawing");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LaneMachineRecipe>> SAWING_SERIALIZER =
            laneSerializer("sawing", LaneMachineKind.SAWMILL);
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> SAWMILL_BOOK = BOOKS.register("sawmill", RecipeBookCategory::new);

    private static DeferredHolder<RecipeType<?>, RecipeType<LaneMachineRecipe>> laneType(String name) {
        return TYPES.register(name, () -> new RecipeType<>() {
            @Override public String toString() { return "futuretech:" + name; }
        });
    }

    private static DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LaneMachineRecipe>> laneSerializer(String name, LaneMachineKind kind) {
        return SERIALIZERS.register(name, () -> new RecipeSerializer<>(
                SingleItemRecipe.simpleMapCodec((info, ingredient, result) -> new LaneMachineRecipe(kind, info, ingredient, result)),
                SingleItemRecipe.simpleStreamCodec((info, ingredient, result) -> new LaneMachineRecipe(kind, info, ingredient, result))));
    }
    public static final DeferredHolder<RecipeType<?>, RecipeType<dev.futuretech.recipe.AssemblingRecipe>> ASSEMBLING = TYPES.register("assembling", () -> new RecipeType<>() {
        @Override public String toString() { return "futuretech:assembling"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<dev.futuretech.recipe.AssemblingRecipe>> ASSEMBLING_SERIALIZER =
            SERIALIZERS.register("assembling", () -> new RecipeSerializer<>(dev.futuretech.recipe.AssemblingRecipe.CODEC, dev.futuretech.recipe.AssemblingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> ASSEMBLER_BOOK = BOOKS.register("assembler", RecipeBookCategory::new);
    private ModRecipes() {}

    /** Vanilla only syncs recipe displays; the machine recipes go to every client for the recipe viewer. */
    public static void syncToClients(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        event.sendRecipes(CRUSHING.get(), SAWING.get(), PRESSING.get(), ALLOYING.get(), ASSEMBLING.get());
    }
    public static final DeferredHolder<RecipeType<?>, RecipeType<PressingRecipe>> PRESSING = TYPES.register("pressing", () -> new RecipeType<>() {
        @Override public String toString() { return "futuretech:pressing"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PressingRecipe>> PRESSING_SERIALIZER =
            SERIALIZERS.register("pressing", () -> new RecipeSerializer<>(PressingRecipe.CODEC, PressingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> METAL_PRESS_BOOK = BOOKS.register("metal_press", RecipeBookCategory::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyingRecipe>> ALLOYING = TYPES.register("alloying", () -> new RecipeType<>() {
        @Override public String toString() { return "futuretech:alloying"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyingRecipe>> ALLOYING_SERIALIZER =
            SERIALIZERS.register("alloying", () -> new RecipeSerializer<>(AlloyingRecipe.CODEC, AlloyingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> SMELTERY_BOOK = BOOKS.register("smeltery", RecipeBookCategory::new);
}
