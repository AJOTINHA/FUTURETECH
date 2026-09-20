package dev.futuretech.client;

import dev.futuretech.menu.AssemblerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import static dev.futuretech.client.MachineScreenStyle.*;

public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
    private final AnimatedBar energyBar = new AnimatedBar();
    /** The lock button under the next-recipe arrow: locks the recipe on show for automation, or unlocks it. */
    private static final int LOCK_X = 195, LOCK_Y = 50, LOCK_SIZE = 12;
    private static final int LOCKED = 0xFF409B76, UNLOCKED = 0xFF8B959F, KEYHOLE = 0xFF283541;
    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AssemblerMenu.WIDTH, AssemblerMenu.HEIGHT);
        titleLabelX = 8; inventoryLabelX = 30; inventoryLabelY = 145;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> choose(-1)).bounds(leftPos + 9, topPos + 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> choose(1)).bounds(leftPos + 191, topPos + 26, 20, 20).build());
    }
    private void choose(int direction) {
        if (menu.entries().isEmpty()) return;
        int next = menu.selected() < 0 ? 0 : Math.floorMod(menu.selected() + direction, menu.entries().size());
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, next);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(graphics, leftPos, topPos, menu.slots);
        graphics.fill(leftPos + 8, topPos + 58, leftPos + 22, topPos + 112, BAR_BACK);
        float chargeHeight = energyBar.width(menu.energyStored(), dev.futuretech.block.entity.AssemblerBlockEntity.ENERGY_CAPACITY, 52, menu.isSynced());
        drawVerticalGradientBar(graphics, leftPos + 9, topPos + 111, 12, chargeHeight, ENERGY_START, ENERGY_END);
        var recipe = menu.selectedRecipe();
        if (recipe != null) {
            for (int i = 0; i < recipe.size(); i++) {
                if (menu.slots.get(i).hasItem()) continue;
                var preview = recipe.preview(i);
                if (preview.isEmpty()) continue;
                int x = leftPos + 36 + i % 3 * 18, y = topPos + 59 + i / 3 * 18;
                graphics.item(new net.minecraft.world.item.ItemStack(preview.get()), x, y);
                graphics.fill(x, y, x + 16, y + 16, 0x808B959F);
            }
            if (!menu.slots.get(9).hasItem()) {
                graphics.item(recipe.result().create(), leftPos + 174, topPos + 77);
                graphics.fill(leftPos + 174, topPos + 77, leftPos + 190, topPos + 93, 0x808B959F);
            }
        }
        boolean hovered = overButton(mouseX, mouseY, leftPos + LOCK_X, topPos + LOCK_Y, LOCK_SIZE, LOCK_SIZE);
        drawButton(graphics, font, leftPos + LOCK_X, topPos + LOCK_Y, LOCK_SIZE, LOCK_SIZE, Component.empty(), hovered, menu.selectedRecipe() != null);
        drawPadlock(graphics, leftPos + LOCK_X + 2, topPos + LOCK_Y + 1, menu.selectedPinned());
        graphics.fill(leftPos + 108, topPos + 79, leftPos + 161, topPos + 89, BAR_BACK);
        graphics.fill(leftPos + 109, topPos + 80, leftPos + 109 + menu.progress() * 51 / 100, topPos + 88, 0xFF30C8DF);
        for (int i = 0; i < 4; i++) {
            int x = leftPos + 31 + i * 47;
            graphics.fill(x, topPos + 132, x + 7, topPos + 139, (menu.connections() & 1 << i) != 0 ? 0xFF409B76 : 0xFF815861);
        }
    }
    /** A padlock, 8 wide and 10 tall: closed and green when the recipe is locked, open and grey when it is not. */
    private static void drawPadlock(GuiGraphicsExtractor graphics, int x, int y, boolean closed) {
        int color = closed ? LOCKED : UNLOCKED;
        // The shackle: a bar across the top with a leg either side; open, the left leg lifts clear of the body.
        int top = closed ? y + 1 : y;
        graphics.fill(x + 2, top, x + 6, top + 1, color);
        graphics.fill(x + 5, top, x + 6, y + 4, color);
        graphics.fill(x + 2, top, x + 3, closed ? y + 4 : y + 2, color);
        // The body, with the keyhole in it.
        graphics.fill(x, y + 4, x + 8, y + 10, color);
        graphics.fill(x + 3, y + 6, x + 5, y + 8, KEYHOLE);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.selectedRecipe() != null && overButton(event.x(), event.y(), leftPos + LOCK_X, topPos + LOCK_Y, LOCK_SIZE, LOCK_SIZE)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.lockButton());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        if (menu.pinnedCount() > 0) {
            Component locked = Component.translatable("gui.futuretech.assembler.locked", menu.pinnedCount());
            graphics.text(font, locked, (imageWidth - font.width(locked)) / 2, 45, LOCKED, false);
        }
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var recipe = menu.selectedRecipe();
        Component name = recipe == null ? Component.translatable("gui.futuretech.assembler.select") : recipe.result().create().getHoverName();
        String shortName = font.plainSubstrByWidth(name.getString(), 154);
        graphics.text(font, shortName, (imageWidth - font.width(shortName)) / 2, 32, TEXT, false);
        Component status = AssemblerStatusView.label(menu.status());
        graphics.text(font, status, (imageWidth - font.width(status)) / 2, 119, AssemblerStatusView.color(menu.status(), false), false);
        String[] keys = {"in_short", "work_short", "out_short", "terminal_short"};
        for (int i = 0; i < 4; i++) graphics.text(font, Component.translatable("gui.futuretech.assembler." + keys[i]), 41 + i * 47, 131, TEXT, false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        energyTooltip(graphics, mouseX, mouseY, leftPos + 8, topPos + 58, 14, 54, menu.energyStored(),
                dev.futuretech.block.entity.AssemblerBlockEntity.ENERGY_CAPACITY);
        var recipe = menu.selectedRecipe();
        if (recipe == null) return;
        for (int i = 0; i < 10; i++) {
            var slot = menu.slots.get(i);
            if (slot.hasItem() || mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16 || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) continue;
            if (i == 9) graphics.setTooltipForNextFrame(recipe.result().create().getHoverName(), mouseX, mouseY);
            else if (i < recipe.size()) recipe.preview(i).ifPresent(item ->
                    graphics.setTooltipForNextFrame(new net.minecraft.world.item.ItemStack(item).getHoverName(), mouseX, mouseY));
        }
    }
}
