package dev.futuretech.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The recipe viewer's page for a generator or the boiler: the machine's block is the title and
 * icon, its inputs stand in a row with the arrow after them, the fluid it makes (if any) past the
 * arrow, and under the row the lines that say what it does with them. One category per machine,
 * so looking the block up lands on its own pages.
 */
public final class GeneratorCategory implements IRecipeCategory<GeneratorPage> {
    private static final int TEXT = 0xFF283541;
    private static final int WIDTH = 176;
    private static final int SLOT_STEP = 22;
    private static final int ROW_Y = 4;
    private static final int LINES_Y = 26;
    private static final int LINE_HEIGHT = 10;

    private final IRecipeType<GeneratorPage> type;
    private final Block machine;
    private final int lines;
    private final IDrawable icon;

    /** @param lines the most lines any of this machine's pages carries, which sets the page's height */
    public GeneratorCategory(IGuiHelper gui, IRecipeType<GeneratorPage> type, Block machine, int lines) {
        this.type = type;
        this.machine = machine;
        this.lines = lines;
        this.icon = gui.createDrawableItemLike(machine);
    }

    @Override public IRecipeType<GeneratorPage> getRecipeType() { return type; }
    @Override public Component getTitle() { return machine.getName(); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return LINES_Y + lines * LINE_HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GeneratorPage page, IFocusGroup focuses) {
        int x = 4;
        for (GeneratorPage.Input input : page.inputs()) {
            IRecipeSlotBuilder slot = input.station()
                    ? builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, x, ROW_Y)
                    : builder.addInputSlot(x, ROW_Y);
            slot.setStandardSlotBackground();
            if (input.fluid() != null) {
                slot.setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 16)
                        .add(input.fluid().getFluid(), input.fluid().getAmount(), DataComponentPatch.EMPTY);
            } else {
                slot.addItemStacks(input.items());
            }
            x += SLOT_STEP;
        }
        if (page.output() != null) {
            builder.addOutputSlot(arrowX(page) + 30, ROW_Y).setOutputSlotBackground()
                    .setFluidRenderer(FluidType.BUCKET_VOLUME, false, 16, 16)
                    .add(page.output().getFluid(), page.output().getAmount(), DataComponentPatch.EMPTY);
        }
    }

    /** The arrow sits after the last input; with none it sits where a first input would. */
    private static int arrowX(GeneratorPage page) { return 4 + page.inputs().size() * SLOT_STEP + 2; }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, GeneratorPage page, IFocusGroup focuses) {
        if (!page.inputs().isEmpty() || page.output() != null) builder.addRecipeArrowWidget().setPosition(arrowX(page), ROW_Y + 1);
        for (int index = 0; index < page.lines().size(); index++) {
            builder.addText(page.lines().get(index), WIDTH, LINE_HEIGHT)
                    .setPosition(0, LINES_Y + index * LINE_HEIGHT)
                    .setColor(TEXT)
                    .setShadow(false)
                    .setTextAlignment(HorizontalAlignment.CENTER);
        }
    }
}
