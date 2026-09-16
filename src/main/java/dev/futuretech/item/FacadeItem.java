package dev.futuretech.item;

import dev.futuretech.api.facade.CableFacades;
import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A panel that hides one face of a cable behind another block. The block it copies rides on the
 * stack, so one item covers every kind of cable, and the wrench takes the panel off again.
 */
public final class FacadeItem extends Item {
    public FacadeItem(Properties properties) {
        super(properties);
    }

    /** A facade wearing {@code state}; the stack is what the player holds and what a broken cable gives back. */
    public static ItemStack of(BlockState state) {
        var stack = new ItemStack(ModItems.FACADE.get());
        stack.set(ModDataComponents.FACADE_BLOCK.get(), state);
        return stack;
    }

    /** The block on the stack, or null for a facade that lost it; such a stack covers nothing. */
    public static @Nullable BlockState block(ItemStack stack) {
        BlockState state = stack.get(ModDataComponents.FACADE_BLOCK.get());
        return state != null && CableFacades.isValid(state) ? state : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        BlockState facade = block(context.getItemInHand());
        if (facade == null || !(level.getBlockState(pos).getBlock() instanceof AbstractCableBlock cable)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof AbstractCableBlockEntity entity)) return InteractionResult.PASS;
        // The arm or collar the click landed on decides the face, the same way the wrench reads a cable.
        Direction side = AbstractCableBlock.hitSide(pos, context.getClickLocation(), context.getClickedFace());
        if (entity.hasFacade(side)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        entity.setFacade(side, facade);
        var player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        var sound = facade.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState state = block(stack);
        return state == null ? super.getName(stack)
                : Component.translatable("item.futuretech.facade.named", state.getBlock().getName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("item.futuretech.facade.hint"));
    }

    /** What the creative tab and the recipe book show before a block is chosen. */
    public static ItemStack sample() { return of(Blocks.STONE.defaultBlockState()); }
}
