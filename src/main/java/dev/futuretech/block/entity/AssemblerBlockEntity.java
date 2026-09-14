package dev.futuretech.block.entity;

import dev.futuretech.block.AssemblerBlock;
import dev.futuretech.block.AssemblerBlock.Kind;
import dev.futuretech.menu.AssemblerMenu;
import dev.futuretech.recipe.AssemblingRecipe;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.*;

/** The table owns deposited materials; a transport arm owns its cargo until delivery commits. */
public final class AssemblerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int REACH = 3, RESULT = 9, TRAVEL_TICKS = 60;
    public final SimpleContainer inventory = new SimpleContainer(10) {
        @Override public void setChanged() { super.setChanged(); AssemblerBlockEntity.this.changed(); }
    };
    private String selected = "";
    private int phase, animationTick, duration = 80, targetSlot;
    private BlockPos tablePos = BlockPos.ZERO, chestPos = BlockPos.ZERO;
    private Direction chestSide = Direction.UP;
    private boolean crafted;
    private @Nullable AssemblingRecipe jobRecipe;
    private long lastClientSync;
    private boolean loading;
    /** World queries are separate from the item state machine so a whole cell can be tested deterministically. */
    interface Environment {
        @Nullable AssemblerBlockEntity assembler(BlockPos pos);
        List<BlockPos> positions();
        @Nullable ResourceHandler<ItemResource> handler(BlockPos pos, Direction side);
        List<Entry> recipes();
    }
    private @Nullable Environment environment;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ASSEMBLER.get(), pos, state); }
    AssemblerBlockEntity(BlockPos pos, BlockState state, Environment environment) { this(pos, state); this.environment = environment; }
    public Kind kind() { return ((AssemblerBlock)getBlockState().getBlock()).kind; }
    public boolean outputMode() { return getBlockState().getValue(AssemblerBlock.OUTPUT); }
    public int phase() { return phase; }
    public int duration() { return duration; }
    public BlockPos tablePos() { return tablePos; }
    public BlockPos chestPos() { return chestPos; }
    public ItemStack cargo() { return inventory.getItem(0); }
    public float animationTick(float partial) {
        if (phase == 0) return 0;
        return Math.min(phase == 2 ? duration + 40 : TRAVEL_TICKS,
                animationTick + (level != null && level.isClientSide() ? level.getGameTime() - lastClientSync + partial : 0));
    }

    public record Entry(String id, AssemblingRecipe recipe) {}
    public List<Entry> recipes() {
        if (environment != null) return environment.recipes();
        if (!(level instanceof ServerLevel server)) return List.of();
        return server.getServer().getRecipeManager().getRecipes().stream()
                .filter(h -> h.value() instanceof AssemblingRecipe)
                .map(h -> new Entry(h.id().identifier().toString(), (AssemblingRecipe) h.value()))
                .sorted(Comparator.comparing(Entry::id)).toList();
    }
    public String selectedId() { return selected; }
    public @Nullable AssemblingRecipe recipe() {
        return recipes().stream().filter(e -> e.id.equals(selected)).map(Entry::recipe).findFirst().orElse(null);
    }
    public boolean select(String id) {
        if (!inventory.isEmpty() || locked() || recipes().stream().noneMatch(e -> e.id.equals(id))) return false;
        selected = id;
        changed();
        return true;
    }

    /** Loaded positions only, stable distance ordering, with a spherical three-block reach. */
    private List<BlockPos> reachable() {
        if (level == null && environment == null) return List.of();
        List<BlockPos> positions = new ArrayList<>();
        Iterable<BlockPos> candidates = environment != null ? environment.positions() : BlockPos.betweenClosed(worldPosition.offset(-REACH, -REACH, -REACH), worldPosition.offset(REACH, REACH, REACH));
        for (BlockPos p : candidates) {
            if (!p.equals(worldPosition) && p.distSqr(worldPosition) <= REACH * REACH && (environment != null || level.hasChunkAt(p))) positions.add(p.immutable());
        }
        positions.sort(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(worldPosition)).thenComparingLong(BlockPos::asLong));
        return positions;
    }
    public @Nullable AssemblerBlockEntity nearest(Kind wanted) {
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null && a.kind() == wanted) return a; }
        return null;
    }
    private @Nullable AssemblerBlockEntity assemblerAt(BlockPos pos) {
        if (environment != null) return environment.assembler(pos);
        return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof AssemblerBlockEntity a ? a : null;
    }
    private @Nullable AssemblerBlockEntity targetTable() {
        var a = assemblerAt(tablePos);
        return a != null && a.kind() == Kind.TABLE ? a : null;
    }
    public boolean locked() {
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null && a.phase != 0 && a.tablePos.equals(worldPosition)) return true; }
        return false;
    }
    public int status() {
        if (kind() != Kind.TABLE) return phase == 0 ? 0 : 3;
        if (nearest(Kind.TERMINAL) == null) return 1;
        if (recipe() == null) return 2;
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null && a.phase != 0 && a.tablePos.equals(worldPosition)) {
            if (a.phase == 2) return a.crafted ? 5 : 4;
            return a.outputMode() ? 6 : 3;
        } }
        if (!inventory.getItem(RESULT).isEmpty()) return 7;
        return ready(recipe()) ? 8 : 0;
    }
    public int progress() {
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null && a.phase == 2 && a.tablePos.equals(worldPosition)) return Math.clamp((a.animationTick - 20) * 100 / a.duration, 0, 100); }
        return inventory.getItem(RESULT).isEmpty() ? 0 : 100;
    }
    public int connections() {
        int flags = 0;
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null) {
            if (a.kind() == Kind.TERMINAL) flags |= 8;
            if (a.kind() == Kind.ASSEMBLY && a.nearest(Kind.TABLE) == this) flags |= 2;
            if (a.kind() == Kind.TRANSPORT && a.nearest(Kind.TABLE) == this) flags |= a.outputMode() ? 4 : 1;
        } }
        return flags;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblerBlockEntity a) {
        if (a.kind() == Kind.TABLE || a.kind() == Kind.TERMINAL) return;
        if (a.phase != 0) {
            a.advance();
            a.setChanged();
            if (a.animationTick % 5 == 0) a.changed();
        } else if (level.getGameTime() % 10 == 0) a.begin();
    }
    void begin() {
        AssemblerBlockEntity table = nearest(Kind.TABLE);
        if (table == null || table.locked() || table.nearest(Kind.TERMINAL) == null) return;
        AssemblingRecipe recipe = table.recipe();
        if (recipe == null) return;
        tablePos = table.worldPosition;
        if (kind() == Kind.ASSEMBLY) {
            if (!table.ready(recipe)) return;
            jobRecipe = recipe;
            duration = recipe.duration();
            crafted = false;
            start(2);
        } else if (outputMode()) {
            ItemStack result = table.inventory.getItem(RESULT);
            if (result.isEmpty()) return;
            for (Endpoint endpoint : endpoints()) if (canInsert(endpoint.handler, result)) {
                chestPos = endpoint.pos; chestSide = endpoint.side;
                // Reserve the table through the active arm; leave the visible result there
                // until the gripper actually reaches it at tick 20.
                start(1);
                return;
            }
        } else {
            if (!table.inventory.getItem(RESULT).isEmpty()) return;
            int slot = table.nextIngredient(recipe);
            if (slot < 0) return;
            for (Endpoint endpoint : endpoints()) {
                ItemStack extracted = extractIngredient(endpoint.handler, recipe.ingredients().get(slot));
                if (extracted.isEmpty()) continue;
                chestPos = endpoint.pos; chestSide = endpoint.side; targetSlot = slot;
                selected = table.selected;
                inventory.setItem(0, extracted);
                start(1);
                return;
            }
        }
    }
    private void start(int value) { phase = value; animationTick = 0; changed(); }
    private void idle() { phase = 0; animationTick = 0; jobRecipe = null; crafted = false; changed(); }
    void advance() {
        if (phase == 2) {
            AssemblerBlockEntity table = targetTable();
            // An unloaded target pauses the job. A removed table can be recovered with the Wrench.
            if (table == null) return;
            if (jobRecipe == null) { idle(); return; }
            if (!crafted && !table.ready(jobRecipe)) { idle(); return; }
            animationTick++;
            if (!crafted && animationTick >= duration + 20) {
                crafted = table.finish(jobRecipe);
                if (!crafted) { idle(); return; }
                changed();
            }
            if (animationTick >= duration + 40) idle();
            return;
        }
        if (outputMode() && animationTick == 19 && cargo().isEmpty()) {
            AssemblerBlockEntity table = targetTable();
            if (table == null) return;
            ItemStack result = table.inventory.getItem(RESULT);
            if (result.isEmpty()) { idle(); return; }
            inventory.setItem(0, table.inventory.removeItem(RESULT, result.getCount()));
        }
        // At the destination, retain cargo and retry while its inventory is blocked or unloaded.
        if (animationTick >= 40 && !cargo().isEmpty()) {
            if (outputMode()) {
                ResourceHandler<ItemResource> destination = endpoint(chestPos, chestSide);
                if (destination == null || !insertAll(destination, cargo())) return;
                inventory.setItem(0, ItemStack.EMPTY);
            } else {
                AssemblerBlockEntity table = targetTable();
                if (table == null || !table.selected.equals(selected) || !table.acceptIngredient(targetSlot, cargo())) return;
                inventory.setItem(0, ItemStack.EMPTY);
            }
            changed();
        }
        if (++animationTick >= TRAVEL_TICKS) idle();
    }
    public int nextIngredient(AssemblingRecipe recipe) {
        for (int i = 0; i < recipe.ingredients().size(); i++) if (inventory.getItem(i).isEmpty()) return i;
        return -1;
    }
    public boolean acceptIngredient(int slot, ItemStack stack) {
        AssemblingRecipe recipe = recipe();
        if (recipe == null || slot < 0 || slot >= recipe.ingredients().size() || stack.getCount() != 1
                || !inventory.getItem(slot).isEmpty() || !recipe.ingredients().get(slot).test(stack)) return false;
        inventory.setItem(slot, stack.copy());
        return true;
    }
    private AssemblingRecipe.Input input() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < RESULT; i++) stacks.add(inventory.getItem(i));
        return new AssemblingRecipe.Input(stacks);
    }
    public boolean ready(@Nullable AssemblingRecipe recipe) {
        return recipe != null && inventory.getItem(RESULT).isEmpty() && recipe.matches(input(), level);
    }
    public boolean finish(AssemblingRecipe recipe) {
        if (!ready(recipe)) return false;
        // The complete job is validated before anything is consumed. This is a single server tick.
        ItemStack result = recipe.assemble(input());
        for (int slot = 0; slot < recipe.ingredients().size(); slot++) inventory.removeItem(slot, 1);
        inventory.setItem(RESULT, result);
        changed();
        return true;
    }

    private record Endpoint(BlockPos pos, Direction side, ResourceHandler<ItemResource> handler) {}
    private List<Endpoint> endpoints() {
        List<Endpoint> result = new ArrayList<>();
        for (BlockPos p : reachable()) {
            if (assemblerAt(p) != null) continue;
            for (Direction side : Direction.values()) {
                var handler = endpoint(p, side);
                if (handler != null) result.add(new Endpoint(p, side, handler));
            }
        }
        return result;
    }
    private @Nullable ResourceHandler<ItemResource> endpoint(BlockPos pos, Direction side) {
        if (environment != null) return environment.handler(pos, side);
        return level != null && level.hasChunkAt(pos) ? level.getCapability(Capabilities.Item.BLOCK, pos, side) : null;
    }
    public static ItemStack extractIngredient(ResourceHandler<ItemResource> handler, net.minecraft.world.item.crafting.Ingredient ingredient) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty() || !ingredient.test(resource.toStack())) continue;
            try (var tx = Transaction.openRoot()) {
                if (handler.extract(slot, resource, 1, tx) == 1) { tx.commit(); return resource.toStack(); }
            }
        }
        return ItemStack.EMPTY;
    }
    public static boolean canInsert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (var tx = Transaction.openRoot()) { return handler.insert(ItemResource.of(stack), stack.getCount(), tx) == stack.getCount(); }
    }
    public static boolean insertAll(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (var tx = Transaction.openRoot()) {
            if (handler.insert(ItemResource.of(stack), stack.getCount(), tx) != stack.getCount()) return false;
            tx.commit(); return true;
        }
    }
    public void switchMode(Player player) {
        if (kind() != Kind.TRANSPORT) return;
        if (phase != 0) { player.sendOverlayMessage(Component.translatable("gui.futuretech.assembler.busy")); return; }
        level.setBlock(worldPosition, getBlockState().cycle(AssemblerBlock.OUTPUT), Block.UPDATE_ALL);
        player.sendOverlayMessage(Component.translatable(outputMode() ? "gui.futuretech.assembler.output" : "gui.futuretech.assembler.input"));
    }
    public void open(Player player) {
        AssemblerBlockEntity table = kind() == Kind.TABLE ? this : nearest(Kind.TABLE);
        if (kind() == Kind.TRANSPORT || kind() == Kind.ASSEMBLY) {
            player.sendOverlayMessage(Component.translatable(phase != 0 ? "gui.futuretech.assembler.busy"
                    : kind() == Kind.ASSEMBLY ? "gui.futuretech.assembler.tool_hint" : "gui.futuretech.assembler.arm_hint"));
        } else if (table == null) player.sendOverlayMessage(Component.translatable("gui.futuretech.assembler.no_table"));
        else {
            List<Entry> entries = table.recipes();
            player.openMenu(new MenuProvider() {
                @Override public Component getDisplayName() { return AssemblerBlockEntity.this.getDisplayName(); }
                @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    return new AssemblerMenu(id, inventory, AssemblerBlockEntity.this, table, entries);
                }
            }, buffer -> {
                buffer.writeBlockPos(worldPosition); buffer.writeBlockPos(table.worldPosition);
                buffer.writeCollection(entries, (buf, entry) -> { buf.writeUtf(entry.id); AssemblingRecipe.STREAM_CODEC.encode(buffer, entry.recipe); });
            });
        }
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.futuretech.assembler.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new AssemblerMenu(id, inventory, this, this, recipes()); }

    private void changed() {
        if (loading) return;
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var stacks = NonNullList.withSize(10, ItemStack.EMPTY);
        for (int i = 0; i < 10; i++) stacks.set(i, inventory.getItem(i));
        ContainerHelper.saveAllItems(output, stacks);
        output.putString("Recipe", selected); output.putInt("Phase", phase); output.putInt("AnimationTick", animationTick);
        output.putInt("Duration", duration); output.putInt("TargetSlot", targetSlot);
        output.store("Table", BlockPos.CODEC, tablePos); output.store("Chest", BlockPos.CODEC, chestPos);
        output.putInt("ChestSide", chestSide.ordinal()); output.putBoolean("Crafted", crafted);
        if (jobRecipe != null) output.store("JobRecipe", AssemblingRecipe.CODEC.codec(), jobRecipe);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loading = true;
        var stacks = NonNullList.withSize(10, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, stacks);
        for (int i = 0; i < 10; i++) inventory.setItem(i, stacks.get(i));
        selected = input.getStringOr("Recipe", ""); phase = Math.clamp(input.getIntOr("Phase", 0), 0, 2);
        animationTick = Math.max(0, input.getIntOr("AnimationTick", 0)); duration = Math.clamp(input.getIntOr("Duration", 80), 20, 12000);
        targetSlot = Math.clamp(input.getIntOr("TargetSlot", 0), 0, 8);
        tablePos = input.read("Table", BlockPos.CODEC).orElse(BlockPos.ZERO); chestPos = input.read("Chest", BlockPos.CODEC).orElse(BlockPos.ZERO);
        chestSide = Direction.values()[Math.clamp(input.getIntOr("ChestSide", 1), 0, 5)]; crafted = input.getBooleanOr("Crafted", false);
        jobRecipe = input.read("JobRecipe", AssemblingRecipe.CODEC.codec()).orElse(null);
        lastClientSync = level == null ? 0 : level.getGameTime();
        loading = false;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveAdditional(output); return output.buildResult();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) { loadAdditional(input); }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, inventory);
    }
}
