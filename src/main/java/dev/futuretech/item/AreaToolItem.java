package dev.futuretech.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tool whose break expands beyond the struck block: the hammer and excavator mine a 3x3 plane of
 * pickaxe or shovel blocks; the lumber axe fells every connected log of the same tree.
 * The vanilla tool properties supply the material's mining, repair and enchantment rules.
 */
public final class AreaToolItem extends Item {
    /** Which blocks join the struck one. */
    public enum Reach {
        /** The eight neighbors in the plane of the struck face. */
        PLANE("tooltip.futuretech.area_tool.plane"),
        /** Every log of the same kind connected to the struck one, including diagonally. */
        TREE("tooltip.futuretech.area_tool.tree");

        final String tooltip;

        Reach(String tooltip) {
            this.tooltip = tooltip;
        }
    }

    /** The vanilla tool each kind stands in for: its block tag, abilities and right-click behavior. */
    public enum Kind {
        HAMMER(BlockTags.MINEABLE_WITH_PICKAXE, Reach.PLANE, Set.of(), () -> Items.IRON_PICKAXE),
        EXCAVATOR(BlockTags.MINEABLE_WITH_SHOVEL, Reach.PLANE, ItemAbilities.DEFAULT_SHOVEL_ACTIONS, () -> Items.IRON_SHOVEL),
        LUMBER_AXE(BlockTags.MINEABLE_WITH_AXE, Reach.TREE, ItemAbilities.DEFAULT_AXE_ACTIONS, () -> Items.IRON_AXE);

        final TagKey<Block> mineable;
        final Reach reach;
        final Set<ItemAbility> abilities;
        // Vanilla items are not registered yet when this enum is initialized.
        final Supplier<Item> vanilla;

        Kind(TagKey<Block> mineable, Reach reach, Set<ItemAbility> abilities, Supplier<Item> vanilla) {
            this.mineable = mineable;
            this.reach = reach;
            this.abilities = abilities;
            this.vanilla = vanilla;
        }
    }

    private final Kind kind;

    public AreaToolItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    /** The blocks this tool expands into; the material's tool component still decides drops. */
    public TagKey<Block> mineable() {
        return kind.mineable;
    }

    public Reach reach() {
        return kind.reach;
    }

    /**
     * Stripping, scraping, path-making and dousing live in the vanilla tool classes. They read the tool
     * from the context, so the stack in hand takes the wear and the ability check below decides eligibility.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return kind.vanilla.get().useOn(context);
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return kind.abilities.contains(ability);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                               Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable(kind.reach.tooltip).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.futuretech.area_tool.sneak").withStyle(ChatFormatting.DARK_GRAY));
    }
}
