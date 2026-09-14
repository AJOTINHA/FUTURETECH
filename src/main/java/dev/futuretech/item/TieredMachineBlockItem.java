package dev.futuretech.item;

import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/** Vanilla block-state components restore the MK on placement; its name exposes that saved level. */
public final class TieredMachineBlockItem extends BlockItem {
    public TieredMachineBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Integer mk = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(MachineLevel.MK);
        return mk == null || mk == 1 ? super.getName(stack)
                : Component.translatable("item.futuretech.machine_level", super.getName(stack), mk);
    }
}
