package dev.futuretech.item;

import dev.futuretech.api.side.SideConfigVisuals;
import dev.futuretech.api.upgrade.MachineLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Sneak-use upgrades the existing block in place, retaining its block entity and contents. */
public final class MachineUpgradeKitItem extends Item {
    private final int targetLevel;

    public MachineUpgradeKitItem(int targetLevel, Properties properties) {
        super(properties);
        if (targetLevel < 2 || targetLevel > 4) throw new IllegalArgumentException("Kit level must be 2..4");
        this.targetLevel = targetLevel;
    }

    public int targetLevel() { return targetLevel; }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        var state = level.getBlockState(pos);
        if (!state.hasProperty(MachineLevel.MK)) {
            if (!level.isClientSide()) player.sendOverlayMessage(
                    Component.translatable("message.futuretech.upgrade_kit.unsupported"));
            return InteractionResult.FAIL;
        }
        if (!MachineLevel.canUpgrade(state, targetLevel)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable(
                    "message.futuretech.upgrade_kit.requires", targetLevel - 1, MachineLevel.of(state)));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!level.setBlock(pos, state.setValue(MachineLevel.MK, targetLevel), Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            var entity = level.getBlockEntity(pos);
            if (entity != null) SideConfigVisuals.refresh(entity);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.translatable(
                    "message.futuretech.upgrade_kit.success", targetLevel));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("item.futuretech.upgrade_kit.usage", targetLevel - 1, targetLevel));
    }
}
