package dev.futuretech.block.entity;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.menu.StorageCardsMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The cards of a card storage: a shelf of written teleport cards that the pads on its network
 * read as destinations. It is only a container; what a card costs and whether a pad can reach it
 * are the pad's sums, made where the pad is, so nothing here knows about any pad in particular.
 *
 * <p>The shelf grows with the level: four slots at MK1, doubling each level to thirty-two. The
 * slots are always stored at the full size and only the first of them take a card, the way the
 * upgrade slots unlock — the storage never has to resize, and an upgrade only opens what was
 * already there.
 */
public final class StorageCardsBlockEntity extends BaseContainerBlockEntity implements Upgradeable {
    /** Card slots an MK1 has; every level above it doubles the count. */
    public static final int BASE_CARDS = 4;
    /** What an MK4 reaches, and the size the slots are always stored at. */
    public static final int SLOTS = BASE_CARDS << (MachineLevel.MAX - 1);

    private NonNullList<ItemStack> cards = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::setChanged);

    public StorageCardsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CARDS.get(), pos, state);
    }

    /** How many card slots a level-{@code mk} storage has: four at MK1, doubling each level. */
    public static int cards(int mk) {
        return BASE_CARDS << (Math.clamp(mk, 1, MachineLevel.MAX) - 1);
    }

    /** The card slots this storage has open at its level. */
    public int unlockedCards() { return cards(MachineLevel.of(getBlockState())); }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    /** Where the card in {@code slot} points, or null with no written card in an open slot there. */
    public @Nullable TeleportTarget target(int slot) {
        return slot < 0 || slot >= unlockedCards() ? null : TeleportCardItem.target(cards.get(slot));
    }

    /**
     * Edits the card in {@code slot}: the label the pads and the panel show for that destination
     * and the colour a pad's beam takes for it, never the pad it points at. The same rule as the
     * teleporter's own card: a blank name falls back to the destination's coordinates.
     */
    public boolean editCard(int slot, String name, int colour) {
        TeleportTarget target = target(slot);
        if (target == null) return false;
        TeleportTarget edited = TeleportCardItem.edited(target, name, colour);
        if (edited.equals(target)) return false;
        cards.get(slot).set(ModDataComponents.TELEPORT_TARGET.get(), edited);
        setChanged();
        return true;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cards = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, cards);
        upgrades.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, cards);
        upgrades.save(output);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < unlockedCards() && TeleportCardItem.isWritten(stack);
    }

    /** Always the full size: the locked slots exist and stay empty, so an upgrade never resizes. */
    @Override
    public int getContainerSize() { return SLOTS; }

    @Override
    protected NonNullList<ItemStack> getItems() { return cards; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.cards = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.storage_cards"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new StorageCardsMenu(id, inventory, this, upgrades);
    }
}
