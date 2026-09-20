package dev.futuretech.api.redstone.client;

import dev.futuretech.api.redstone.RedstoneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The 16 px icon of each {@link RedstoneMode}, shared by every screen that offers the choice:
 * gunpowder that turns into redstone dust once chosen, an unlit torch, and a lit torch.
 */
public final class RedstoneModeIcons {
    public static final int SIZE = 16;

    private RedstoneModeIcons() {}

    public static void draw(GuiGraphicsExtractor graphics, RedstoneMode mode, boolean selected, int x, int y) {
        switch (mode) {
            case IGNORED -> graphics.item(new ItemStack(selected ? Items.REDSTONE : Items.GUNPOWDER), x, y);
            case HIGH -> graphics.item(new ItemStack(Items.REDSTONE_TORCH), x, y);
            case LOW -> {
                TextureAtlasSprite sprite = unlitTorch();
                if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE);
                else graphics.item(new ItemStack(Items.REDSTONE_TORCH), x, y);
            }
        }
    }

    /** There is no unlit torch item, so the icon comes from the unlit block state's texture; a lookup, so it survives reloads. */
    private static @Nullable TextureAtlasSprite unlitTorch() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        BlockState state = Blocks.REDSTONE_TORCH.defaultBlockState().setValue(RedstoneTorchBlock.LIT, false);
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        return model.particleMaterial(level, BlockPos.ZERO, state).sprite();
    }
}
