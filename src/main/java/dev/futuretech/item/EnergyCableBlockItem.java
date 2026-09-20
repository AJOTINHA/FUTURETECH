package dev.futuretech.item;

import dev.futuretech.block.EnergyCableBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Block item of the energy cable: the tooltip says how much a network of this tier moves. */
public final class EnergyCableBlockItem extends BlockItem {
    private final EnergyCableBlock cable;

    public EnergyCableBlockItem(EnergyCableBlock cable, Properties properties) {
        super(cable, properties);
        this.cable = cable;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("item.futuretech.cable.throughput", String.format("%,d", cable.tier().throughput())));
    }
}
