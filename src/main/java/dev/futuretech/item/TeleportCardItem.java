package dev.futuretech.item;

import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.menu.TeleporterMenu;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A card that remembers one teleporter. Sneak-use it on a teleporter to write that one on it;
 * in another teleporter's card slots it becomes a destination the player can pick. A blank card
 * points nowhere and takes no slot.
 */
public final class TeleportCardItem extends Item {
    public TeleportCardItem(Properties properties) {
        super(properties);
    }

    public static @Nullable TeleportTarget target(ItemStack stack) {
        return stack.getItem() instanceof TeleportCardItem ? stack.get(ModDataComponents.TELEPORT_TARGET.get()) : null;
    }

    /** A card with a destination on it, the only kind a teleporter's slots take. */
    public static boolean isWritten(ItemStack stack) { return target(stack) != null; }

    /**
     * The target as the player edited it: the label the screens show for that destination and the
     * colour a pad's beam takes for it, never the pad it points at. A blank name falls back to the
     * destination's coordinates, the way a card written on a nameless pad reads.
     */
    public static TeleportTarget edited(TeleportTarget target, String name, int colour) {
        String trimmed = name.strip();
        if (trimmed.length() > TeleporterMenu.NAME_LENGTH) trimmed = trimmed.substring(0, TeleporterMenu.NAME_LENGTH);
        if (trimmed.isBlank()) {
            var pos = target.pos().pos();
            trimmed = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
        return new TeleportTarget(target.pos(), trimmed, colour & 0xFFFFFF);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity teleporter)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        // One card at a time: the written one splits off a stack, so the rest stays blank.
        var written = stack.copyWithCount(1);
        written.set(ModDataComponents.TELEPORT_TARGET.get(), new TeleportTarget(GlobalPos.of(level.dimension(), pos.immutable()), teleporter.displayName()));
        if (stack.getCount() == 1) {
            player.setItemInHand(context.getHand(), written);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(written)) player.drop(written, false);
        }
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.sendOverlayMessage(Component.translatable("message.futuretech.teleport_card.written", teleporter.displayName()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        TeleportTarget target = target(stack);
        return target == null ? super.getName(stack)
                : Component.translatable("item.futuretech.teleport_card.named", target.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        TeleportTarget target = target(stack);
        if (target == null) {
            lines.accept(Component.translatable("item.futuretech.teleport_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        var pos = target.pos().pos();
        lines.accept(Component.translatable("item.futuretech.teleport_card.target",
                pos.getX(), pos.getY(), pos.getZ(), target.dimensionName()).withStyle(ChatFormatting.GRAY));
    }
}
