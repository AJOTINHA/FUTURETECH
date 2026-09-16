package dev.futuretech.item;

import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;

public final class FluidTankBlockItem extends BlockItem {
    public FluidTankBlockItem(Block block, Properties properties) { super(block, properties); }

    public static FluidStack storedFluid(ItemStack stack) {
        var stored = stack.getOrDefault(ModDataComponents.TANK_FLUID.get(), StoredTankFluid.EMPTY);
        return stored.resource().toStack(Math.min(stored.amount(), FluidTankBlockEntity.CAPACITY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        var fluid = storedFluid(stack);
        lines.accept(Component.translatable("gui.futuretech.tank.contents",
                fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName(),
                fluid.getAmount(), FluidTankBlockEntity.CAPACITY));
        lines.accept(Component.translatable("gui.futuretech.tank.hint"));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return !storedFluid(stack).isEmpty(); }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.clamp(Math.round(13.0F * storedFluid(stack).getAmount() / FluidTankBlockEntity.CAPACITY), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
