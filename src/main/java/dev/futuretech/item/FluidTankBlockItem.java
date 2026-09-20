package dev.futuretech.item;

import dev.futuretech.block.entity.FluidTankBlockEntity;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;

/** Block item that keeps the tank's fluid and level: the level names it and sets what it holds. */
public final class FluidTankBlockItem extends BlockItem {
    public FluidTankBlockItem(Block block, Properties properties) { super(block, properties); }

    /** The MK saved on the stack by the block's drop; a fresh tank is MK1. */
    public static int mk(ItemStack stack) {
        Integer mk = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(MachineLevel.MK);
        return mk == null ? 1 : mk;
    }

    public static int capacity(ItemStack stack) { return FluidTankBlockEntity.capacity(mk(stack)); }

    public static FluidStack storedFluid(ItemStack stack) {
        var stored = stack.getOrDefault(ModDataComponents.TANK_FLUID.get(), StoredTankFluid.EMPTY);
        return stored.resource().toStack(Math.min(stored.amount(), capacity(stack)));
    }

    @Override
    public Component getName(ItemStack stack) {
        int mk = mk(stack);
        return mk == 1 ? super.getName(stack) : Component.translatable("item.futuretech.machine_level", super.getName(stack), mk);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        var fluid = storedFluid(stack);
        lines.accept(Component.translatable("gui.futuretech.tank.contents",
                fluid.isEmpty() ? Component.translatable("gui.futuretech.empty") : fluid.getHoverName(),
                fluid.getAmount(), capacity(stack)));
        lines.accept(Component.translatable("gui.futuretech.tank.hint"));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return !storedFluid(stack).isEmpty(); }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.clamp(Math.round(13.0F * storedFluid(stack).getAmount() / capacity(stack)), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
