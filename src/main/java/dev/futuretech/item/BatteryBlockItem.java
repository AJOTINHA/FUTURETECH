package dev.futuretech.item;

import dev.futuretech.block.entity.BatteryBlockEntity;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Block item that shows the battery's stored charge as a tooltip line and a bar. */
public final class BatteryBlockItem extends BlockItem {
    public BatteryBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int storedEnergy(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), 0, BatteryBlockEntity.CAPACITY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("gui.futuretech.stored", storedEnergy(stack), BatteryBlockEntity.CAPACITY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return storedEnergy(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * storedEnergy(stack) / BatteryBlockEntity.CAPACITY), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
