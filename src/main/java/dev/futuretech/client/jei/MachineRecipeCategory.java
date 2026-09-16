package dev.futuretech.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One recipe viewer page per machine. The machine's block is the title and icon; a layout places the
 * slots and the page ends with the MK1 time and the energy that time costs.
 */
public final class MachineRecipeCategory<R extends Recipe<?>> implements IRecipeCategory<RecipeHolder<R>> {
    /** Places the recipe's slots; the arrow and the stats line are shared. */
    public interface Layout<R> {
        void build(IRecipeLayoutBuilder builder, R recipe);
    }

    private static final int TEXT = 0xFF283541;

    private final IRecipeHolderType<R> type;
    private final Block machine;
    private final int width, height, arrowX, arrowY;
    private final int energyPerTick;
    private final ToIntFunction<R> duration;
    private final Layout<R> layout;
    private final IDrawable icon;

    public MachineRecipeCategory(IGuiHelper gui, IRecipeHolderType<R> type, Block machine, int width, int height,
                                 int arrowX, int arrowY, int energyPerTick, ToIntFunction<R> duration, Layout<R> layout) {
        this.type = type;
        this.machine = machine;
        this.width = width;
        this.height = height;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.energyPerTick = energyPerTick;
        this.duration = duration;
        this.layout = layout;
        this.icon = gui.createDrawableItemLike(machine);
    }

    @Override public IRecipeHolderType<R> getRecipeType() { return type; }
    @Override public Component getTitle() { return machine.getName(); }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<R> holder, IFocusGroup focuses) {
        layout.build(builder, holder.value());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<R> holder, IFocusGroup focuses) {
        int ticks = duration.applyAsInt(holder.value());
        builder.addAnimatedRecipeArrowWidget(ticks).setPosition(arrowX, arrowY);
        String seconds = ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format("%.1f", ticks / 20.0);
        builder.addText(Component.translatable("jei.futuretech.stats", seconds, String.format("%,d", ticks * energyPerTick)),
                        width, 10)
                .setPosition(0, height - 10)
                .setColor(TEXT)
                .setShadow(false)
                .setTextAlignment(HorizontalAlignment.CENTER);
    }

    /** Every item the ingredient accepts, shown with the count the machine consumes. */
    static List<ItemStack> stacks(Ingredient ingredient, int count) {
        return ingredient.items().map(item -> new ItemStack(item, count)).toList();
    }
}
