package dev.futuretech.client;

import dev.futuretech.menu.AssemblerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import static dev.futuretech.client.MachineScreenStyle.*;

public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
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
        var recipe = menu.selectedRecipe();
        if (recipe != null) {
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                if (menu.slots.get(i).hasItem()) continue;
                var preview = recipe.ingredients().get(i).items().findFirst();
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
        graphics.fill(leftPos + 108, topPos + 79, leftPos + 161, topPos + 89, BAR_BACK);
        graphics.fill(leftPos + 109, topPos + 80, leftPos + 109 + menu.progress() * 51 / 100, topPos + 88, 0xFF30C8DF);
        for (int i = 0; i < 4; i++) {
            int x = leftPos + 31 + i * 47;
            graphics.fill(x, topPos + 132, x + 7, topPos + 139, (menu.connections() & 1 << i) != 0 ? 0xFF409B76 : 0xFF815861);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        var recipe = menu.selectedRecipe();
        Component name = recipe == null ? Component.translatable("gui.futuretech.assembler.select") : recipe.result().create().getHoverName();
        String shortName = font.plainSubstrByWidth(name.getString(), 154);
        graphics.text(font, shortName, (imageWidth - font.width(shortName)) / 2, 32, TEXT, false);
        Component status = Component.translatable("gui.futuretech.assembler.status." + menu.status());
        graphics.text(font, status, (imageWidth - font.width(status)) / 2, 119, TEXT, false);
        String[] keys = {"in_short", "work_short", "out_short", "terminal_short"};
        for (int i = 0; i < 4; i++) graphics.text(font, Component.translatable("gui.futuretech.assembler." + keys[i]), 41 + i * 47, 131, TEXT, false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        var recipe = menu.selectedRecipe();
        if (recipe == null) return;
        for (int i = 0; i < 10; i++) {
            var slot = menu.slots.get(i);
            if (slot.hasItem() || mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16 || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) continue;
            if (i == 9) graphics.setTooltipForNextFrame(recipe.result().create().getHoverName(), mouseX, mouseY);
            else if (i < recipe.ingredients().size()) recipe.ingredients().get(i).items().findFirst().ifPresent(item ->
                    graphics.setTooltipForNextFrame(new net.minecraft.world.item.ItemStack(item).getHoverName(), mouseX, mouseY));
        }
    }
}
