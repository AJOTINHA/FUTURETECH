package dev.futuretech.client.jei;

import dev.futuretech.item.FacadeItem;
import dev.futuretech.block.entity.PaintMachineBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The paint machine's one page for the recipe viewer: every block a facade may wear, and the
 * panels each becomes, in the same order so the two slots can be linked. Built from the item
 * registry once the viewer asks, when every mod's blocks are in.
 */
public record PaintingDisplay(List<ItemStack> blocks, List<ItemStack> panels) {
    public static PaintingDisplay everyBlock() {
        List<ItemStack> blocks = new ArrayList<>();
        List<ItemStack> panels = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            BlockState facade = PaintMachineBlockEntity.facadeOf(stack);
            if (facade == null) continue;
            blocks.add(stack);
            panels.add(FacadeItem.of(facade).copyWithCount(PaintMachineBlockEntity.YIELD));
        }
        return new PaintingDisplay(List.copyOf(blocks), List.copyOf(panels));
    }
}
