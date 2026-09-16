package dev.futuretech.item;

import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import dev.futuretech.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Block item that shows a battery's stored charge as a tooltip line and a bar. */
public final class BatteryBlockItem extends BlockItem {
    private final BatteryTier tier;

    public BatteryBlockItem(BatteryBlock block, Properties properties) {
        super(block, properties);
        this.tier = block.tier();
    }

    public BatteryTier tier() { return tier; }

    public BatteryTier tier(ItemStack stack) {
        Integer mk = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(MachineLevel.MK);
        return mk == null ? tier : BatteryTier.byOrdinal(mk - 1);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("block.futuretech." + tier(stack).blockName());
    }

    /** Stored charge clamped to the tier's capacity; zero for items that are not batteries. */
    public static int storedEnergy(ItemStack stack) {
        if (!(stack.getItem() instanceof BatteryBlockItem item)) return 0;
        return Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), 0, item.tier(stack).capacity());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("gui.futuretech.stored", storedEnergy(stack), tier(stack).capacity()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return storedEnergy(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * storedEnergy(stack) / tier(stack).capacity()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
