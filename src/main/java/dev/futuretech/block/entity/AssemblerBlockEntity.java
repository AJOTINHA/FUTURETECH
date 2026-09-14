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
    public static final int ENERGY_CAPACITY = 32000, ENERGY_INPUT = 200, ENERGY_PER_TICK = 20;
    private final dev.futuretech.energy.TickLimitedEnergyHandler energy = new dev.futuretech.energy.TickLimitedEnergyHandler(
            ENERGY_CAPACITY, ENERGY_INPUT, 0, this::setChanged);
    private boolean moving;
    private List<ItemStack> monitorIngredients = List.of();
    private ItemStack monitorResult = ItemStack.EMPTY;
    private int monitorProgress, monitorPresent, monitorStatus = 10;
    public int monitorStatus() { return monitorStatus; }
    public List<ItemStack> monitorIngredients() { return monitorIngredients; }
    public ItemStack monitorResult() { return monitorResult; }
    public int monitorProgress() { return monitorProgress; }
    public int monitorPresent() { return monitorPresent; }

    /** A compact display snapshot, independent of whether a player has opened the menu. */
    boolean refreshMonitor() {
        var table = nearest(Kind.TABLE);
        var recipe = table == null ? null : table.recipe();
        List<ItemStack> ingredients = new ArrayList<>();
        ItemStack result = ItemStack.EMPTY;
        int progress = 0, present = 0;
        if (recipe != null) {
            for (int slot = 0; slot < recipe.ingredients().size(); slot++) {
                ItemStack actual = table.inventory.getItem(slot);
                if (!actual.isEmpty()) present |= 1 << slot;
                ingredients.add(actual.isEmpty() ? recipe.ingredients().get(slot).items().findFirst()
                        .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY) : actual.copyWithCount(1));
            }
            result = recipe.result().create();
            progress = table.progress();
        }
        int status = table == null ? 10 : table.status();
        boolean different = status != monitorStatus || progress != monitorProgress || present != monitorPresent
                || !ItemStack.matches(result, monitorResult) || ingredients.size() != monitorIngredients.size();
        if (!different) for (int i = 0; i < ingredients.size(); i++) if (!ItemStack.matches(ingredients.get(i), monitorIngredients.get(i))) { different = true; break; }
        monitorIngredients = List.copyOf(ingredients); monitorResult = result; monitorProgress = progress; monitorPresent = present;
        monitorStatus = status;
        return different;
    }
    public dev.futuretech.energy.TickLimitedEnergyHandler energy() { return energy; }
    public net.neoforged.neoforge.transfer.energy.@Nullable EnergyHandler energyHandler() {
        return kind() == Kind.TERMINAL ? energy : null;
    }
    public boolean moving() { return moving; }
    public int controllerEnergy() {
        var controller = kind() == Kind.TERMINAL ? this : nearest(Kind.TERMINAL);
        return controller == null ? 0 : controller.energy.getAmountAsInt();
    }
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
                animationTick + (moving && level != null && level.isClientSide() ? Math.clamp(level.getGameTime() - lastClientSync + partial, 0, 5) : 0));
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
        if (controllerEnergy() < ENERGY_PER_TICK) return 9;
        for (BlockPos p : reachable()) { var a = assemblerAt(p); if (a != null && a.phase != 0 && a.tablePos.equals(worldPosition)) {
            if (!a.moving && a.animationTick > 0) return 11;
            if (a.phase == 2) return a.crafted ? 5 : 4;
            return a.outputMode() ? 6 : 3;
        } }
        if (!inventory.getItem(RESULT).isEmpty()) return 7;
        if (!ready(recipe()) && (connections() & 1) == 0) return 12;
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
        if (a.kind() == Kind.TERMINAL) {
            a.energy.beginTick();
            if (level.getGameTime() % 5 == 0 && a.refreshMonitor()) a.changed();
            return;
        }
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
        if (table.controllerEnergy() < ENERGY_PER_TICK) return;
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
    private void start(int value) { phase = value; animationTick = 0; moving = false; changed(); }
    private void idle() { phase = 0; animationTick = 0; moving = false; jobRecipe = null; crafted = false; changed(); }
    void advance() {
        if (phase == 0) return;
        var table = targetTable();
        var controller = table == null ? null : table.nearest(Kind.TERMINAL);
        boolean wasMoving = moving;
        if (controller == null || controller.energy.getAmountAsInt() < ENERGY_PER_TICK) {
            moving = false;
            if (wasMoving) changed();
            return;
        }
        int previousTick = animationTick, previousPhase = phase;
        advancePowered();
        boolean advanced = animationTick != previousTick || phase != previousPhase;
        if (advanced) controller.energy.set(controller.energy.getAmountAsInt() - ENERGY_PER_TICK);
        moving = advanced && phase != 0;
        if (wasMoving != moving) changed();
    }
    private void advancePowered() {
        if (phase == 2) {
            AssemblerBlockEntity table = targetTable();
            // An unloaded target pauses the job. A removed table can be recovered with the Wrench.
            if (table == null) return;
            if (jobRecipe == null) { idle(); return; }
            if (!crafted && !table.ready(jobRecipe)) { idle(); return; }
            animationTick++;
            if (animationTick >= 20 && animationTick < duration + 20 && animationTick % 8 == 0) emitAssemblyParticles();
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
    /** Light smoke and occasional vanilla lava pops follow the powered drill tip. */
    private void emitAssemblyParticles() {
        if (!(level instanceof ServerLevel server)) return;
        double dx = tablePos.getX() - worldPosition.getX(), dz = tablePos.getZ() - worldPosition.getZ();
        double radius = Math.hypot(dx, dz);
        var facing = getBlockState().getValue(AssemblerBlock.FACING);
        double swivel = radius < 1e-5 ? Math.atan2(-facing.getStepX(), -facing.getStepZ()) : Math.atan2(-dx, -dz);
        double fade = Math.sin(Math.PI * (animationTick - 20) / duration);
        double angle = swivel + Math.sin(animationTick * .25) * .04 * fade;
        double x = worldPosition.getX() + .5 - Math.sin(angle) * radius;
        double y = tablePos.getY() + .85 + Math.sin(animationTick * .6) * .035 * fade;
        double z = worldPosition.getZ() + .5 - Math.cos(angle) * radius;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                x, y, z, 0, 0, .02, 0, 1);
        if (animationTick % 16 == 0) server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                x, y, z, 1, .025, .01, .025, 0);
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
        if (kind() == Kind.TERMINAL) output.putInt("Energy", energy.getAmountAsInt());
        output.putBoolean("Moving", moving);
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
        energy.set(kind() == Kind.TERMINAL ? Math.clamp(input.getIntOr("Energy", 0), 0, ENERGY_CAPACITY) : 0);
        moving = phase != 0 && input.getBooleanOr("Moving", false);
        loading = false;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveAdditional(output);
        if (kind() == Kind.TERMINAL) {
            refreshMonitor();
            output.store("MonitorIngredients", ItemStack.OPTIONAL_CODEC.listOf(), monitorIngredients);
            output.store("MonitorResult", ItemStack.OPTIONAL_CODEC, monitorResult);
            output.putInt("MonitorProgress", monitorProgress); output.putInt("MonitorPresent", monitorPresent);
            output.putInt("MonitorStatus", monitorStatus);
        }
        return output.buildResult();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        loadAdditional(input);
        monitorIngredients = input.read("MonitorIngredients", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        monitorResult = input.read("MonitorResult", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        monitorProgress = Math.clamp(input.getIntOr("MonitorProgress", 0), 0, 100);
        monitorPresent = input.getIntOr("MonitorPresent", 0);
        monitorStatus = Math.clamp(input.getIntOr("MonitorStatus", 10), 0, 12);
    }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, inventory);
    }
}
