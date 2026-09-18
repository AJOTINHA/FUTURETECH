package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import dev.futuretech.api.gui.EnergyInfoTab;
import dev.futuretech.api.gui.TabStrip;
import dev.futuretech.api.redstone.client.RedstoneControlTab;
import dev.futuretech.api.side.client.SideConfigTab;
import dev.futuretech.api.upgrade.client.UpgradeTab;
import dev.futuretech.block.entity.ExtruderBlockEntity;
import dev.futuretech.menu.ExtruderMenu;
import dev.futuretech.recipe.ExtrudingRecipe;
import dev.futuretech.recipe.ExtrudingRecipes;
import dev.futuretech.registry.ModRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The extruder's screen: the energy column at the left as on every machine, then a tank at each end
 * of the row with its own fluid, an arrow running inwards from each, and the output slot they both
 * point at in the middle; the machine tabs beside the window.
 *
 * <p>Above the slot sits the product button, showing what the machine is set to make. The same two
 * fluids give cobblestone, stone or obsidian, so clicking it steps through them - backwards on a
 * right click - the way the face buttons step through their modes.
 */
public final class ExtruderScreen extends AbstractContainerScreen<ExtruderMenu> {
    /**
     * The row reads from both sides into the middle: a tank, its arrow, the slot, then the same
     * again mirrored. Everything hangs off the slot, which is centred on the panel.
     */
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 48;
    private static final int TANK_Y = ExtruderMenu.SLOT_Y + 8 - TANK_HEIGHT / 2;
    /** Between an arrow's point and the slot, and between an arrow's tail and its tank. */
    private static final int ARROW_GAP = 7;
    private static final int TANK_GAP = 5;
    private static final int SLOT_WIDTH = 16;
    private static final int LEFT_ARROW_X = ExtruderMenu.SLOT_X - ARROW_GAP - ARROW_WIDTH;
    private static final int RIGHT_ARROW_X = ExtruderMenu.SLOT_X + SLOT_WIDTH + ARROW_GAP;
    private static final int LEFT_TANK_X = LEFT_ARROW_X - TANK_GAP - TANK_WIDTH;
    private static final int RIGHT_TANK_X = RIGHT_ARROW_X + ARROW_WIDTH + TANK_GAP;
    /** The product button, a slot-sized square in the gap between the header and the slot's row. */
    private static final int CHOICE_SIZE = 16;
    private static final int CHOICE_X = ExtruderMenu.SLOT_X;
    private static final int CHOICE_Y = 24;
    /** The energy column where every machine has it: at the left, centred on the slot's row. */
    private static final int ENERGY_X = 7;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = 49;
    private static final int ENERGY_TOP = ExtruderMenu.SLOT_Y + 8 - (ENERGY_HEIGHT + 1) / 2;

    private final AnimatedBar energyBar = new AnimatedBar();
    private final AnimatedBar[] fluidBars = {new AnimatedBar(), new AnimatedBar()};
    private final AnimatedBar progressBar = new AnimatedBar();
    private final TabStrip tabs;
    /**
     * The recipes in the order the machine numbers them, read once: they arrive with the data pack,
     * long before a screen opens, and the server sends its choice as a place in this list.
     */
    private final List<RecipeHolder<ExtrudingRecipe>> choices =
            ExtrudingRecipes.sorted(SyncedRecipes.of(ModRecipes.EXTRUDING.get()));
    private int shownChoice = -1;
    private ItemStack choiceIcon = ItemStack.EMPTY;

    public ExtruderScreen(ExtruderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ExtruderMenu.IMAGE_WIDTH, ExtruderMenu.INVENTORY_Y + 82);
        titleLabelX = 7;
        inventoryLabelX = 7;
        inventoryLabelY = ExtruderMenu.INVENTORY_Y - 12;
        tabs = new TabStrip(new UpgradeTab(menu, font), new SideConfigTab<>(menu, font),
                new RedstoneControlTab<>(menu, font), new EnergyInfoTab(menu, font));
    }

    /** The left edge of one tank's column: the first is left of the slot, the second right of it. */
    private static int tankX(int tank) { return tank == 0 ? LEFT_TANK_X : RIGHT_TANK_X; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        drawSlots(graphics, x, y, menu.slots);
        for (int tank = 0; tank < fluidBars.length; tank++) drawTank(graphics, x + tankX(tank), y + TANK_Y, tank);
        // One batch, so both arrows fill together: each runs from its own tank towards the slot.
        float filled = progressBar.width(menu.progress(), menu.progressTotal(), ARROW_WIDTH, menu.isSynced());
        int rowY = y + ExtruderMenu.SLOT_Y;
        drawProgressArrow(graphics, x + LEFT_ARROW_X, rowY, filled, ENERGY_START, ENERGY_END);
        drawProgressArrow(graphics, x + RIGHT_ARROW_X, rowY, filled, ENERGY_START, ENERGY_END, true);
        drawChoice(graphics, x + CHOICE_X, y + CHOICE_Y, mouseX, mouseY);
        int energyTop = y + ENERGY_TOP;
        graphics.fill(x + ENERGY_X, energyTop, x + ENERGY_X + ENERGY_WIDTH, energyTop + ENERGY_HEIGHT, BAR_BACK);
        float energyFill = energyBar.width(menu.energyStored(), menu.energyCapacity(), ENERGY_HEIGHT - 2, menu.isSynced());
        drawVerticalGradientBar(graphics, x + ENERGY_X + 1, energyTop + ENERGY_HEIGHT - 1, ENERGY_WIDTH - 2,
                energyFill, ENERGY_START, ENERGY_END);
        tabs.render(graphics, x, y, imageWidth, mouseX, mouseY);
    }

    /** The product button: the slot's own frame, what the machine will make on it, lit under the pointer. */
    private void drawChoice(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        drawSlot(graphics, x, y);
        if (overButton(mouseX, mouseY, x, y, CHOICE_SIZE, CHOICE_SIZE)) {
            graphics.fill(x, y, x + CHOICE_SIZE, y + CHOICE_SIZE, 0x40FFFFFF);
        }
        ItemStack icon = icon();
        if (!icon.isEmpty()) graphics.fakeItem(icon, x, y);
    }

    /** What the chosen recipe makes, built again only when the server's choice moves. */
    private ItemStack icon() {
        int index = menu.choiceIndex();
        if (index != shownChoice) {
            shownChoice = index;
            choiceIcon = index >= 0 && index < choices.size()
                    ? choices.get(index).value().result().create() : ItemStack.EMPTY;
        }
        return choiceIcon;
    }

    /** One tank column, drawn with its fluid's own texture and tint like the fluid tank's bar. */
    private void drawTank(GuiGraphicsExtractor graphics, int x, int y, int tank) {
        graphics.fill(x - 1, y - 1, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, 0xFF56616D);
        graphics.fill(x, y, x + TANK_WIDTH, y + TANK_HEIGHT, BAR_BACK);
        FluidStack fluid = menu.fluid(tank);
        int height = Math.round(fluidBars[tank].width(menu.fluidStored(tank), menu.tankCapacity(), TANK_HEIGHT, menu.isSynced()));
        if (height > 0 && !fluid.isEmpty()) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            var sprite = model.stillMaterial().sprite();
            int color = model.fluidTintSource() == null ? 0xFFFFFFFF : model.fluidTintSource().colorAsStack(fluid);
            graphics.enableScissor(x, y + TANK_HEIGHT - height, x + TANK_WIDTH, y + TANK_HEIGHT);
            for (int row = 0; row < TANK_HEIGHT; row += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y + row, 16, 16, color);
            }
            graphics.disableScissor();
        }
        graphics.fill(x, y, x + 1, y + TANK_HEIGHT, 0x30FFFFFF);
        for (int step = 1; step < 4; step++) {
            int tickY = y + TANK_HEIGHT * step / 4;
            graphics.fill(x + TANK_WIDTH - 4, tickY, x + TANK_WIDTH, tickY + 1, 0xA0FFFFFF);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawMachineTitle(graphics, font, title, menu.mk(), titleLabelX, titleLabelY, imageWidth - titleLabelX - 7);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_TOP, ENERGY_WIDTH, ENERGY_HEIGHT,
                menu.energyStored(), menu.energyCapacity());
        for (int tank = 0; tank < ExtruderBlockEntity.TANKS; tank++) tankTooltip(graphics, mouseX, mouseY, tank);
        tabs.extractTooltip(graphics, mouseX, mouseY);
    }

    /** The same readout the melter's tank gives, for whichever of the two the pointer is on. */
    private void tankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int tank) {
        int x = leftPos + tankX(tank);
        int y = topPos + TANK_Y;
        if (mouseX < x - 1 || mouseX >= x + TANK_WIDTH + 1 || mouseY < y - 1 || mouseY >= y + TANK_HEIGHT + 1) return;
        FluidStack fluid = menu.fluid(tank);
        graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.tank.contents",
                fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName(),
                menu.fluidStored(tank), menu.tankCapacity()), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabs.mouseClicked(event)) return true;
        if (clickedChoice(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    /** Steps the product on a left click and back on a right one, as the face buttons do. */
    private boolean clickedChoice(MouseButtonEvent event) {
        if (choices.isEmpty() || (event.button() != 0 && event.button() != 1)) return false;
        if (!overButton(event.x(), event.y(), leftPos + CHOICE_X, topPos + CHOICE_Y, CHOICE_SIZE, CHOICE_SIZE)) {
            return false;
        }
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) {
            gameMode.handleInventoryButtonClick(menu.containerId, event.button() == 1
                    ? ExtruderMenu.BUTTON_PREVIOUS_CHOICE : ExtruderMenu.BUTTON_NEXT_CHOICE);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }
}
