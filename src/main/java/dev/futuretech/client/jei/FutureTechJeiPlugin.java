package dev.futuretech.client.jei;

import dev.futuretech.FutureTech;
import dev.futuretech.api.gui.TabbedScreen;
import dev.futuretech.block.entity.AssemblerBlockEntity;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.block.entity.MelterBlockEntity;
import dev.futuretech.block.entity.MetalPressBlockEntity;
import dev.futuretech.block.entity.ExtruderBlockEntity;
import dev.futuretech.block.entity.PaintMachineBlockEntity;
import dev.futuretech.block.entity.SmelteryBlockEntity;
import dev.futuretech.client.SyncedRecipes;
import dev.futuretech.recipe.AlloyingRecipe;
import dev.futuretech.recipe.AssemblingRecipe;
import dev.futuretech.recipe.LaneMachineRecipe;
import dev.futuretech.recipe.MeltingRecipe;
import dev.futuretech.recipe.ExtrudingRecipe;
import dev.futuretech.recipe.PressingRecipe;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** Shows what each machine makes: crushing, sawing, pressing, alloying, assembling, melting and painting, plus the vanilla furnace and fuel pages. */
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
    private static final IRecipeHolderType<MeltingRecipe> MELTING = IRecipeHolderType.create(ModRecipes.MELTING.getId());
    private static final IRecipeHolderType<ExtrudingRecipe> EXTRUDING = IRecipeHolderType.create(ModRecipes.EXTRUDING.getId());
    /** The paint machine has no recipe book: its one page is written here, over every block a facade may wear. */
    private static final IRecipeType<PaintingDisplay> PAINTING =
            IRecipeType.create(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "painting"), PaintingDisplay.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "machines");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                MachineRecipeCategory.ofHolders(gui, CRUSHING, ModBlocks.CRUSHER.get(), 100, 42, 34, 9,
                        LaneMachineBlockEntity.ENERGY_PER_TICK, recipe -> LaneMachineBlockEntity.WORK_TICKS, FutureTechJeiPlugin::singleItem),
                MachineRecipeCategory.ofHolders(gui, SAWING, ModBlocks.SAWMILL.get(), 100, 42, 34, 9,
                        LaneMachineBlockEntity.ENERGY_PER_TICK, recipe -> LaneMachineBlockEntity.WORK_TICKS, FutureTechJeiPlugin::singleItem),
                MachineRecipeCategory.ofHolders(gui, PRESSING, ModBlocks.METAL_PRESS.get(), 122, 42, 56, 9,
                        MetalPressBlockEntity.ENERGY_PER_TICK, PressingRecipe::duration, FutureTechJeiPlugin::pressing),
                MachineRecipeCategory.ofHolders(gui, ALLOYING, ModBlocks.SMELTERY.get(), 122, 42, 56, 9,
                        SmelteryBlockEntity.ENERGY_PER_TICK, AlloyingRecipe::duration, FutureTechJeiPlugin::alloying),
                MachineRecipeCategory.ofHolders(gui, ASSEMBLING, ModBlocks.ASSEMBLY_TABLE.get(), 126, 70, 64, 22,
                        AssemblerBlockEntity.ENERGY_PER_TICK, AssemblingRecipe::duration, FutureTechJeiPlugin::assembling),
                MachineRecipeCategory.ofHolders(gui, MELTING, ModBlocks.MELTER.get(), 100, 42, 34, 9,
                        MelterBlockEntity.ENERGY_PER_TICK, MeltingRecipe::duration, FutureTechJeiPlugin::melting),
                MachineRecipeCategory.ofHolders(gui, EXTRUDING, ModBlocks.EXTRUDER.get(), 122, 42, 56, 9,
                        ExtruderBlockEntity.ENERGY_PER_TICK, ExtrudingRecipe::duration, FutureTechJeiPlugin::extruding),
                new MachineRecipeCategory<>(gui, PAINTING, ModBlocks.PAINT_MACHINE.get(), 122, 42, 56, 9,
                        PaintMachineBlockEntity.ENERGY_PER_TICK, display -> PaintMachineBlockEntity.PAINT_TICKS, FutureTechJeiPlugin::painting));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var crushing = SyncedRecipes.of(ModRecipes.CRUSHING.get());
        var sawing = SyncedRecipes.of(ModRecipes.SAWING.get());
        var pressing = SyncedRecipes.of(ModRecipes.PRESSING.get());
        var alloying = SyncedRecipes.of(ModRecipes.ALLOYING.get());
        var assembling = SyncedRecipes.of(ModRecipes.ASSEMBLING.get());
        var melting = SyncedRecipes.of(ModRecipes.MELTING.get());
        var extruding = SyncedRecipes.of(ModRecipes.EXTRUDING.get());
        LOG.info("JEI: {} crushing, {} sawing, {} pressing, {} alloying, {} assembling, {} melting, {} extruding recipes",
                crushing.size(), sawing.size(), pressing.size(), alloying.size(), assembling.size(), melting.size(),
                extruding.size());
        registration.addRecipes(CRUSHING, crushing);
        registration.addRecipes(SAWING, sawing);
        registration.addRecipes(PRESSING, pressing);
        registration.addRecipes(ALLOYING, alloying);
        registration.addRecipes(ASSEMBLING, assembling);
        registration.addRecipes(MELTING, melting);
        registration.addRecipes(EXTRUDING, extruding);
        registration.addRecipes(PAINTING, List.of(PaintingDisplay.everyBlock()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CRUSHING, ModBlocks.CRUSHER.get());
        registration.addCraftingStation(SAWING, ModBlocks.SAWMILL.get());
        registration.addCraftingStation(PRESSING, ModBlocks.METAL_PRESS.get());
        registration.addCraftingStation(ALLOYING, ModBlocks.SMELTERY.get());
        registration.addCraftingStation(ASSEMBLING, ModBlocks.ASSEMBLY_TABLE.get(), ModBlocks.ASSEMBLER_TERMINAL.get());
        registration.addCraftingStation(MELTING, ModBlocks.MELTER.get());
        registration.addCraftingStation(EXTRUDING, ModBlocks.EXTRUDER.get());
        registration.addCraftingStation(PAINTING, ModBlocks.PAINT_MACHINE.get());
        // The electric furnace smelts vanilla recipes; the solid fuel generator burns vanilla fuels.
        registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.ELECTRIC_FURNACE.get());
        registration.addCraftingStation(RecipeTypes.SMELTING_FUEL, ModBlocks.SOLID_FUEL_GENERATOR.get());
    }

    /**
     * The tabs on a screen's edges stand outside its panel, where JEI's ingredient list would sit
     * on top of them; telling JEI where they are, every frame, makes the list keep clear of them,
     * closed or open. One handler for every screen of the mod that has tabs.
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                return screen instanceof TabbedScreen tabbed ? tabbed.tabs().areas() : List.of();
            }
        });
    }

    private static void singleItem(IRecipeLayoutBuilder builder, LaneMachineRecipe recipe) {
        builder.addInputSlot(8, 8).setStandardSlotBackground().add(recipe.input());
        builder.addOutputSlot(68, 8).setOutputSlotBackground().add(recipe.result());
    }

    /** The item in, and the fluid out drawn as a bucket's worth in a slot, the way JEI shows fluids. */
    private static void melting(IRecipeLayoutBuilder builder, MeltingRecipe recipe) {
        builder.addInputSlot(8, 8).setStandardSlotBackground().add(recipe.ingredient());
        var made = recipe.result();
        builder.addOutputSlot(68, 8).setOutputSlotBackground()
                .setFluidRenderer(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME, false, 16, 16)
                .add(made.fluid().value(), made.amount(), made.components());
    }

    /** The two fluids in, each drawn as a bucket's worth in a slot, and the item they make out. */
    private static void extruding(IRecipeLayoutBuilder builder, ExtrudingRecipe recipe) {
        fluidPart(builder, 8, recipe.first());
        fluidPart(builder, 30, recipe.second());
        builder.addOutputSlot(90, 8).setOutputSlotBackground().add(recipe.result());
        // The tanks are unordered, so neither fluid is the first one.
        builder.setShapeless();
    }

    /**
     * One feed of an extrusion: an input when a batch drinks it, and part of the station when it
     * does not - cobblestone needs the water and the lava there without spending a drop of either,
     * the way the press needs its mold. That slot shows a full bucket, having no amount to show.
     */
    private static void fluidPart(IRecipeLayoutBuilder builder, int x, ExtrudingRecipe.Part part) {
        boolean drunk = part.consumes() > 0;
        var slot = drunk ? builder.addInputSlot(x, 8) : builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, x, 8);
        slot.setStandardSlotBackground()
                .setFluidRenderer(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME, false, 16, 16)
                .add(part.fluid().value(),
                        drunk ? part.consumes() : net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME,
                        net.minecraft.core.component.DataComponentPatch.EMPTY);
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

    /** The block and the panel cycle together, so looking up a block lands on its own panel and back. */
    private static void painting(IRecipeLayoutBuilder builder, PaintingDisplay display) {
        var block = builder.addInputSlot(8, 8).setStandardSlotBackground().addItemStacks(display.blocks());
        builder.addInputSlot(30, 8).setStandardSlotBackground()
                .add(new ItemStack(ModItems.STEEL_PLATE.get(), PaintMachineBlockEntity.PLATES_PER_JOB));
        var panels = builder.addOutputSlot(90, 8).setOutputSlotBackground().addItemStacks(display.panels());
        builder.createFocusLink(block, panels);
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
