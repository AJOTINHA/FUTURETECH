package dev.futuretech.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
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
 * slots and the page ends with the MK1 time and the energy that time costs. The page is over any
 * kind of entry: the recipes the data packs hold, through {@link #ofHolders}, or one a machine
 * writes in code when it has no recipe book.
 */
public final class MachineRecipeCategory<T> implements IRecipeCategory<T> {
    /** Places the recipe's slots; the arrow and the stats line are shared. */
    public interface Layout<R> {
        void build(IRecipeLayoutBuilder builder, R recipe);
    }

    private static final int TEXT = 0xFF283541;

    private final IRecipeType<T> type;
    private final Block machine;
    private final int width, height, arrowX, arrowY;
    private final int energyPerTick;
    private final ToIntFunction<T> duration;
    private final Layout<T> layout;
    private final IDrawable icon;

    /** A page over data-pack recipes: the layout and the duration see the recipe itself, not its holder. */
    public static <R extends Recipe<?>> MachineRecipeCategory<RecipeHolder<R>> ofHolders(
            IGuiHelper gui, IRecipeHolderType<R> type, Block machine, int width, int height,
            int arrowX, int arrowY, int energyPerTick, ToIntFunction<R> duration, Layout<R> layout) {
        return new MachineRecipeCategory<>(gui, type, machine, width, height, arrowX, arrowY, energyPerTick,
                holder -> duration.applyAsInt(holder.value()), (builder, holder) -> layout.build(builder, holder.value()));
    }

    public MachineRecipeCategory(IGuiHelper gui, IRecipeType<T> type, Block machine, int width, int height,
                                 int arrowX, int arrowY, int energyPerTick, ToIntFunction<T> duration, Layout<T> layout) {
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

    @Override public IRecipeType<T> getRecipeType() { return type; }
    @Override public Component getTitle() { return machine.getName(); }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        layout.build(builder, recipe);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses) {
        int ticks = duration.applyAsInt(recipe);
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
