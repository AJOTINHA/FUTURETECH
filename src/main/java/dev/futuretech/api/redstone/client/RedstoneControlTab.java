package dev.futuretech.api.redstone.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Redstone control tab: one tile per {@link RedstoneMode}, the current one outlined; clicking a
 * tile selects it on the server. A line below says whether the block is powered right now.
 */
public final class RedstoneControlTab<M extends AbstractContainerMenu & RedstoneControlMenu> extends MachineTab {
    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    private static final int ROW = CELL * RedstoneMode.values().length - (CELL - TILE) + 2;
    private static final int SELECTED = 0xFFEC761C;
    private static final int UNSELECTED = 0xFF56616D;

    private final M menu;
    private @Nullable TextureAtlasSprite unlitTorch;

    public RedstoneControlTab(M menu, Font font) {
        super(font);
        this.menu = menu;
    }

    /** Draws the 16 px icon of a mode: gunpowder that turns into redstone dust once chosen, an unlit torch or a lit torch. */
    private void drawModeIcon(GuiGraphicsExtractor graphics, RedstoneMode mode, boolean selected, int x, int y) {
        switch (mode) {
            case IGNORED -> graphics.item(new ItemStack(selected ? Items.REDSTONE : Items.GUNPOWDER), x, y);
            case HIGH -> graphics.item(new ItemStack(Items.REDSTONE_TORCH), x, y);
            case LOW -> {
                TextureAtlasSprite sprite = unlitTorchSprite();
                if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TILE, TILE);
                else graphics.item(new ItemStack(Items.REDSTONE_TORCH), x, y);
            }
        }
    }

    /** There is no unlit torch item, so the icon comes from the unlit block state's texture. */
    private @Nullable TextureAtlasSprite unlitTorchSprite() {
        if (unlitTorch != null) return unlitTorch;
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        BlockState state = Blocks.REDSTONE_TORCH.defaultBlockState().setValue(RedstoneTorchBlock.LIT, false);
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        unlitTorch = model.particleMaterial(level, BlockPos.ZERO, state).sprite();
        return unlitTorch;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.redstone"); }

    @Override
    protected int contentWidth() { return ROW; }

    @Override
    protected int contentHeight() { return TILE + 2 + 4 + font.lineHeight; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // The tab shows the icon of the mode currently selected, centred in the 20 px square.
        drawModeIcon(graphics, menu.redstoneMode(), true, x + 2, y + 2);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            int x = tileX(contentX, mode);
            int y = contentY + 1;
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, mode == menu.redstoneMode() ? SELECTED : UNSELECTED);
            graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            drawModeIcon(graphics, mode, mode == menu.redstoneMode(), x, y);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
        Component signal = Component.translatable(menu.isPowered() ? "gui.futuretech.redstone.powered" : "gui.futuretech.redstone.unpowered");
        graphics.text(font, signal, contentX + (ROW - font.width(signal)) / 2, contentY + TILE + 2 + 4, TEXT_COLOR, false);
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (!isOver(mouseX, mouseY, tileX(contentX, mode), contentY + 1, TILE, TILE)) continue;
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(mode.translationKey()).getVisualOrderText(),
                    Component.translatable(mode.descriptionKey()).getVisualOrderText()), mouseX, mouseY);
            return;
        }
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (!isOver(event.x(), event.y(), tileX(contentX, mode), contentY + 1, TILE, TILE)) continue;
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, RedstoneControlMenu.BUTTON_BASE + mode.ordinal());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    private static int tileX(int contentX, RedstoneMode mode) { return contentX + 1 + mode.ordinal() * CELL; }
}
