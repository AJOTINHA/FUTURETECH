package dev.futuretech.menu;

import dev.futuretech.block.entity.AssemblerBlockEntity;
import dev.futuretech.block.entity.AssemblerBlockEntity.Entry;
import dev.futuretech.recipe.AssemblingRecipe;
import dev.futuretech.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AssemblerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 220, HEIGHT = 238;
    private final @Nullable AssemblerBlockEntity access, table;
    private final List<Entry> entries;
    private final ContainerData data;
    public AssemblerMenu(int id, Inventory player, RegistryFriendlyByteBuf buffer) {
        this(id, player, find(player, buffer), find(player, buffer), buffer.readList(buf -> new Entry(buf.readUtf(), AssemblingRecipe.STREAM_CODEC.decode(buffer))), true);
    }
    private static @Nullable AssemblerBlockEntity find(Inventory player, RegistryFriendlyByteBuf buffer) {
        return player.player.level().getBlockEntity(buffer.readBlockPos()) instanceof AssemblerBlockEntity a ? a : null;
    }
    public AssemblerMenu(int id, Inventory player, AssemblerBlockEntity access, AssemblerBlockEntity table, List<Entry> entries) {
        this(id, player, access, table, entries, false);
    }
    private AssemblerMenu(int id, Inventory player, @Nullable AssemblerBlockEntity access, @Nullable AssemblerBlockEntity table, List<Entry> entries, boolean client) {
        super(ModMenus.ASSEMBLER.get(), id);
        this.access = access; this.table = table; this.entries = List.copyOf(entries);
        Container contents = client || table == null ? new SimpleContainer(10) : table.inventory;
        for (int i = 0; i < 10; i++) addSlot(new Slot(contents, i, i == 9 ? 174 : 36 + i % 3 * 18, i == 9 ? 77 : 59 + i / 3 * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return client || table != null && !table.locked(); }
        });
        addStandardInventorySlots(player, 30, 156);
        data = client || table == null ? new SimpleContainerData(4) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> { int selected = -1; for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(table.selectedId())) selected = i; yield selected; }
                    case 1 -> table.status();
                    case 2 -> table.progress();
                    default -> table.connections();
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 4; }
        };
        addDataSlots(data);
    }
    public List<Entry> entries() { return entries; }
    public int selected() { return data.get(0); }
    public int status() { return data.get(1); }
    public int progress() { return data.get(2); }
    public int connections() { return data.get(3); }
    public @Nullable AssemblingRecipe selectedRecipe() { return selected() < 0 || selected() >= entries.size() ? null : entries.get(selected()).recipe(); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || table == null || button < 0 || button >= entries.size()) return false;
        if (!table.select(entries.get(button).id())) player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.futuretech.assembler.clear_table"));
        return true;
    }
    @Override public boolean stillValid(Player player) {
        return access != null && table != null && !table.isRemoved() && Container.stillValidBlockEntity(access, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= 10) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (!moveItemStackTo(stack, 10, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
