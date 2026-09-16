package dev.futuretech.client.jei;

import dev.futuretech.FutureTech;
import dev.futuretech.block.entity.AssemblerBlockEntity;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.block.entity.MetalPressBlockEntity;
import dev.futuretech.block.entity.SmelteryBlockEntity;
import dev.futuretech.client.SyncedRecipes;
import dev.futuretech.recipe.AlloyingRecipe;
import dev.futuretech.recipe.AssemblingRecipe;
import dev.futuretech.recipe.LaneMachineRecipe;
import dev.futuretech.recipe.PressingRecipe;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** Shows what each machine makes: crushing, sawing, pressing, alloying and assembling, plus the vanilla furnace and fuel pages. */
@JeiPlugin
public final class FutureTechJeiPlugin implements IModPlugin {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    // By id, not by registry lookup: JEI instantiates plugins while mods are still being constructed,
    // before the recipe types are registered, and a failing static initializer drops the plugin silently.
    private static final IRecipeHolderType<LaneMachineRecipe> CRUSHING = IRecipeHolderType.create(ModRecipes.CRUSHING.getId());
    private static final IRecipeHolderType<LaneMachineRecipe> SAWING = IRecipeHolderType.create(ModRecipes.SAWING.getId());
    private static final IRecipeHolderType<PressingRecipe> PRESSING = IRecipeHolderType.create(ModRecipes.PRESSING.getId());
    private static final IRecipeHolderType<AlloyingRecipe> ALLOYING = IRecipeHolderType.create(ModRecipes.ALLOYING.getId());
    private static final IRecipeHolderType<AssemblingRecipe> ASSEMBLING = IRecipeHolderType.create(ModRecipes.ASSEMBLING.getId());

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "machines");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MachineRecipeCategory<>(gui, CRUSHING, ModBlocks.CRUSHER.get(), 100, 42, 34, 9,
                        LaneMachineBlockEntity.ENERGY_PER_TICK, recipe -> LaneMachineBlockEntity.WORK_TICKS, FutureTechJeiPlugin::singleItem),
                new MachineRecipeCategory<>(gui, SAWING, ModBlocks.SAWMILL.get(), 100, 42, 34, 9,
                        LaneMachineBlockEntity.ENERGY_PER_TICK, recipe -> LaneMachineBlockEntity.WORK_TICKS, FutureTechJeiPlugin::singleItem),
                new MachineRecipeCategory<>(gui, PRESSING, ModBlocks.METAL_PRESS.get(), 122, 42, 56, 9,
                        MetalPressBlockEntity.ENERGY_PER_TICK, PressingRecipe::duration, FutureTechJeiPlugin::pressing),
                new MachineRecipeCategory<>(gui, ALLOYING, ModBlocks.SMELTERY.get(), 122, 42, 56, 9,
                        SmelteryBlockEntity.ENERGY_PER_TICK, AlloyingRecipe::duration, FutureTechJeiPlugin::alloying),
                new MachineRecipeCategory<>(gui, ASSEMBLING, ModBlocks.ASSEMBLY_TABLE.get(), 126, 70, 64, 22,
                        AssemblerBlockEntity.ENERGY_PER_TICK, AssemblingRecipe::duration, FutureTechJeiPlugin::assembling));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var crushing = SyncedRecipes.of(ModRecipes.CRUSHING.get());
        var sawing = SyncedRecipes.of(ModRecipes.SAWING.get());
        var pressing = SyncedRecipes.of(ModRecipes.PRESSING.get());
        var alloying = SyncedRecipes.of(ModRecipes.ALLOYING.get());
        var assembling = SyncedRecipes.of(ModRecipes.ASSEMBLING.get());
        LOG.info("JEI: {} crushing, {} sawing, {} pressing, {} alloying, {} assembling recipes",
                crushing.size(), sawing.size(), pressing.size(), alloying.size(), assembling.size());
        registration.addRecipes(CRUSHING, crushing);
        registration.addRecipes(SAWING, sawing);
        registration.addRecipes(PRESSING, pressing);
        registration.addRecipes(ALLOYING, alloying);
        registration.addRecipes(ASSEMBLING, assembling);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CRUSHING, ModBlocks.CRUSHER.get());
        registration.addCraftingStation(SAWING, ModBlocks.SAWMILL.get());
        registration.addCraftingStation(PRESSING, ModBlocks.METAL_PRESS.get());
        registration.addCraftingStation(ALLOYING, ModBlocks.SMELTERY.get());
        registration.addCraftingStation(ASSEMBLING, ModBlocks.ASSEMBLY_TABLE.get(), ModBlocks.ASSEMBLER_TERMINAL.get());
        // The electric furnace smelts vanilla recipes; the solid fuel generator burns vanilla fuels.
        registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.ELECTRIC_FURNACE.get());
        registration.addCraftingStation(RecipeTypes.SMELTING_FUEL, ModBlocks.SOLID_FUEL_GENERATOR.get());
    }

    private static void singleItem(IRecipeLayoutBuilder builder, LaneMachineRecipe recipe) {
        builder.addInputSlot(8, 8).setStandardSlotBackground().add(recipe.input());
        builder.addOutputSlot(68, 8).setOutputSlotBackground().add(recipe.result());
    }

    private static void pressing(IRecipeLayoutBuilder builder, PressingRecipe recipe) {
        builder.addInputSlot(8, 8).setStandardSlotBackground()
                .addItemStacks(MachineRecipeCategory.stacks(recipe.ingredient(), recipe.count()));
        // The mold is reusable, so it counts as part of the machine rather than an input.
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 30, 8).setStandardSlotBackground()
                .add(recipe.gear() ? ModItems.GEAR_MOLD.get() : ModItems.PLATE_MOLD.get());
        builder.addOutputSlot(90, 8).setOutputSlotBackground().add(recipe.result());
    }

    private static void alloying(IRecipeLayoutBuilder builder, AlloyingRecipe recipe) {
        builder.addInputSlot(8, 8).setStandardSlotBackground()
                .addItemStacks(MachineRecipeCategory.stacks(recipe.first().ingredient(), recipe.first().count()));
        builder.addInputSlot(30, 8).setStandardSlotBackground()
                .addItemStacks(MachineRecipeCategory.stacks(recipe.second().ingredient(), recipe.second().count()));
        builder.addOutputSlot(90, 8).setOutputSlotBackground().add(recipe.result());
        builder.setShapeless();
    }

    private static void assembling(IRecipeLayoutBuilder builder, AssemblingRecipe recipe) {
        List<Ingredient> ingredients = recipe.ingredients();
        for (int i = 0; i < 9; i++) {
            var slot = builder.addInputSlot(4 + (i % 3) * 18, 4 + (i / 3) * 18).setStandardSlotBackground();
            if (i < ingredients.size()) slot.add(ingredients.get(i));
        }
        builder.addOutputSlot(96, 22).setOutputSlotBackground().add(recipe.result());
    }
}
