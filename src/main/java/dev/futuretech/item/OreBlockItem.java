package dev.futuretech.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * The item of one of the mod's ores. Its tooltip says where in the world the ore generates: the
 * lines come from the language file (`block.futuretech.<metal>_ore.spawn` and `.spawn2`, the
 * second left blank for an ore with one band), which the same script that writes the worldgen
 * writes, so the heights on the tooltip are the heights in the ground. The deepslate variant
 * shares the stone one's lines.
 */
public final class OreBlockItem extends BlockItem {
    private final String metal;

    public OreBlockItem(Block block, String metal, Properties properties) {
        super(block, properties);
        this.metal = metal;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        for (String suffix : new String[]{".spawn", ".spawn2"}) {
            var line = Component.translatable("block.futuretech." + metal + "_ore" + suffix);
            if (!line.getString().isEmpty()) lines.accept(line.withStyle(ChatFormatting.GRAY));
        }
    }
}
