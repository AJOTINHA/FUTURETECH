package dev.futuretech.block.entity;

import dev.futuretech.api.redstone.RedstoneControl;
import dev.futuretech.api.redstone.RedstoneControllable;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.api.upgrade.Upgradeable;
import dev.futuretech.block.TeleporterBlock;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.energy.TickLimitedEnergyHandler;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.menu.TeleporterMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.teleport.TeleportTarget;
import dev.futuretech.teleport.TeleporterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A pad that sends whoever stands on it to another teleporter. The destinations are the one
 * teleport card in its slot and every card in the storages on its network cables; the player
 * picks one on the pad's screen or on a network panel, and the pad lights up to say it is armed.
 * Standing on the pad charges for a moment, then the trip is paid from the buffer, base plus
 * distance, and the player lands on the other pad, where nothing happens until they step off and
 * back on. The pick is spent with the trip: the pad goes back to sending nowhere until someone
 * picks again. Every level charges faster and travels cheaper; only an MK4 crosses into another
 * dimension.
 */
public final class TeleporterBlockEntity extends BaseContainerBlockEntity implements RedstoneControllable, Upgradeable {
    public static final int CAPACITY = 100_000;
    public static final int INPUT_PER_TICK = 1_000;
    /** What every trip costs at MK1, plus this much per block of distance in the same dimension. */
    public static final int BASE_COST = 1_000;
    public static final int COST_PER_BLOCK = 10;
    /** A trip to another dimension, flat: distance means nothing across worlds. */
    public static final int CROSS_DIMENSION_COST = 25_000;
    /** How much cheaper every level above MK1 travels, in percent. */
    public static final int DISCOUNT_PER_LEVEL = 10;
    /** Ticks standing on the pad before the trip, at MK1; higher levels shorten it like a job. */
    public static final int CHARGE_TICKS = 20;
    /** The level that reaches other dimensions. */
    public static final int CROSS_DIMENSION_LEVEL = 4;
    /** The one card slot a pad has, at every level; the rest of its destinations live on the network. */
    public static final int CARD_SLOT = 0;
    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    /** The pad's own card slot when it is the chosen destination, or -1: a network card is not this slot. */
    public static final int DATA_SELECTED = 2;
    public static final int DATA_CHARGING = 3;
    /** What the chosen trip costs, wherever its card is; zero with nothing chosen. */
    public static final int DATA_COST = 4;
    public static final int DATA_REDSTONE_BASE = 5;
    public static final int DATA_MK = DATA_REDSTONE_BASE + RedstoneControl.DATA_COUNT;
    public static final int DATA_COUNT = DATA_MK + 1;

    /** What sends a player; tests swap the world's teleport for a record of the trip. */
    @FunctionalInterface
    public interface Trip {
        /** Moves the player onto the pad at {@code target}; false when the pad is not there any more. */
        boolean go(ServerPlayer player, TeleportTarget target);
    }

    private NonNullList<ItemStack> cards = NonNullList.withSize(1, ItemStack.EMPTY);
    private String name = "";
    /**
     * The chosen destination: a slot, and the card storage it is in, or null for the pad's own
     * slot. -1 with nothing chosen. A storage is named by position and read when asked, so a card
     * moved or a storage broken simply leaves the pad sending nowhere.
     */
    private int selected = -1;
    private @Nullable BlockPos selectedSource;
    /** The beam's colour as the client last heard it; the server reads it off the chosen card instead. */
    private int beamColour = TeleportTarget.DEFAULT_COLOUR;
    /** What the client was last told the beam's colour is, so a change is sent once. */
    private int syncedBeam = TeleportTarget.DEFAULT_COLOUR;
    /** Ticks each player on the pad has been charging; a player who steps off is dropped. */
    private final Map<UUID, Integer> charging = new HashMap<>();
    /** Players who arrived here and have not stepped off yet: the pad does nothing for them. */
    private final Set<UUID> landed = new HashSet<>();
    private boolean active;
    private final TickLimitedEnergyHandler energy = new TickLimitedEnergyHandler(CAPACITY, INPUT_PER_TICK, 0, this::setChanged);
    private final RedstoneControl redstone = new RedstoneControl();
    private final ComparatorNotifier comparator = new ComparatorNotifier();
    private final LitHold litHold = new LitHold();
    private final UpgradeInventory upgrades = new UpgradeInventory(() -> MachineLevel.of(getBlockState()), this::setChanged);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergySync.low(energy.getAmountAsInt());
                case DATA_ENERGY_HIGH -> EnergySync.high(energy.getAmountAsInt());
                case DATA_SELECTED -> selected();
                case DATA_CHARGING -> active ? 1 : 0;
                case DATA_COST -> {
                    TeleportTarget target = target();
                    yield target == null ? 0 : cost(globalPos(), target, MachineLevel.of(getBlockState()));
                }
                case DATA_MK -> MachineLevel.of(getBlockState());
                default -> {
                    if (index >= DATA_REDSTONE_BASE && index < DATA_MK) yield redstone.data(index - DATA_REDSTONE_BASE);
                    yield 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORTER.get(), pos, state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    /** An upgrade kit swaps the block state under us; the buffer grows with the new level. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(state)));
    }

    public EnergyHandler energy() { return energy; }

    public ContainerData menuData() { return data; }

    @Override
    public RedstoneControl redstoneControl() { return redstone; }

    @Override
    public void redstoneControlChanged() { setChanged(); }

    @Override
    public UpgradeInventory upgrades() { return upgrades; }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, upgrades);
    }

    /** The name the player gave it; empty until they do. */
    public String name() { return name; }

    /** The name, or the coordinates when there is none: what cards and screens show. */
    public String displayName() { return displayName(name, worldPosition); }

    private static String displayName(String name, BlockPos pos) {
        return name.isBlank() ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : name;
    }

    /**
     * Edits the pad's own card: the label the screens show for that destination and the colour
     * the beam takes for it, never the pad it points at. False with no card in the slot.
     */
    public boolean editCard(int slot, String name, int colour) {
        if (slot != CARD_SLOT) return false;
        ItemStack card = cards.get(slot);
        TeleportTarget target = TeleportCardItem.target(card);
        if (target == null) return false;
        TeleportTarget edited = TeleportCardItem.edited(target, name, colour);
        if (edited.equals(target)) return false;
        card.set(ModDataComponents.TELEPORT_TARGET.get(), edited);
        setChanged();
        return true;
    }

    /**
     * The colour of the beam: the chosen card's, or the default cyan with none. The server reads
     * the card; the client, which has no cards, reads what the last update said.
     */
    public int beamColour() {
        if (level != null && level.isClientSide()) return beamColour;
        TeleportTarget target = target();
        return target == null ? TeleportTarget.DEFAULT_COLOUR : target.colour();
    }

    /**
     * Sends the client what the beam should look like now, once per change: its colour rides on
     * the update packet. Checked every tick rather than on every path that could change it,
     * because a card in a storage far away can change under the pad without the pad hearing.
     */
    private void syncBeam() {
        int beam = beamColour();
        if (beam == syncedBeam || level == null || level.isClientSide()) return;
        syncedBeam = beam;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void setName(String name) {
        String trimmed = name.strip();
        if (trimmed.length() > TeleporterMenu.NAME_LENGTH) trimmed = trimmed.substring(0, TeleporterMenu.NAME_LENGTH);
        if (trimmed.equals(this.name)) return;
        this.name = trimmed;
        setChanged();
    }

    /** The pad's own slot when it is the chosen destination, or -1: none, or a card on the network. */
    public int selected() { return selectedSource == null ? selected : -1; }

    /** The chosen slot, in the pad or in the storage {@link #selectedSource()} names; -1 for none. */
    public int selectedSlot() { return selected; }

    /** The card storage the chosen card is in, or null when it is the pad's own or there is none. */
    public @Nullable BlockPos selectedSource() { return selectedSource; }

    /** Picks the pad's own card slot; the same slot again unpicks it. */
    public void select(int slot) { select(null, slot); }

    /**
     * Picks a destination: the pad's own slot with {@code source} null, or a slot of the card
     * storage at {@code source}. The same pick again unpicks it, and a slot that cannot exist
     * counts as unpicking.
     */
    public void select(@Nullable BlockPos source, int slot) {
        boolean exists = source == null ? slot == CARD_SLOT : slot >= 0 && slot < StorageCardsBlockEntity.SLOTS;
        boolean same = slot == selected && Objects.equals(source, selectedSource);
        if (!exists || same) {
            source = null;
            slot = -1;
        }
        if (slot == selected && Objects.equals(source, selectedSource)) return;
        selected = slot;
        selectedSource = source == null ? null : source.immutable();
        charging.clear();
        setChanged();
    }

    /**
     * Where the pad sends players right now: the chosen card's target, or null with no card
     * there. A network card is read off its storage on the spot, so it is gone the moment the
     * card or the storage is.
     */
    public @Nullable TeleportTarget target() {
        if (selected < 0) return null;
        if (selectedSource == null) return TeleportCardItem.target(cards.get(selected));
        return level != null && level.getBlockEntity(selectedSource) instanceof StorageCardsBlockEntity storage
                ? storage.target(selected) : null;
    }

    /** Whether the pad would send someone who stepped on: a destination chosen and redstone not holding it. */
    public boolean armed() { return target() != null && redstone.allowsRunning(); }

    /** Whether a level may travel to {@code target} from {@code from}: another dimension takes an MK4. */
    public static boolean reaches(GlobalPos from, TeleportTarget target, int mk) {
        return from.dimension().equals(target.dimension()) || mk >= CROSS_DIMENSION_LEVEL;
    }

    /** What a trip from {@code from} to {@code target} costs at level {@code mk}. */
    public static int cost(GlobalPos from, TeleportTarget target, int mk) {
        int base = from.dimension().equals(target.dimension())
                ? BASE_COST + (int) Math.round(COST_PER_BLOCK * Math.sqrt(from.pos().distSqr(target.pos().pos())))
                : CROSS_DIMENSION_COST;
        return base * (100 - DISCOUNT_PER_LEVEL * (Math.clamp(mk, 1, MachineLevel.MAX) - 1)) / 100;
    }

    /** Ticks a player stands on the pad before the trip at level {@code mk}. */
    public static int chargeTicks(int mk) { return MachineLevel.duration(CHARGE_TICKS, mk); }

    public GlobalPos globalPos() {
        return GlobalPos.of(level == null ? Level.OVERWORLD : level.dimension(), worldPosition.immutable());
    }

    /** The space a player has to be in to count as standing on the pad: the block above, feet included. */
    public AABB standingBox() {
        return new AABB(worldPosition.above()).inflate(-0.1, 0, -0.1).expandTowards(0, 0.5, 0);
    }

    /** A player who just arrived: nothing happens until they leave and come back. */
    public void receive(ServerPlayer player) { landed.add(player.getUUID()); }

    /** The redstone signal is sampled here and on neighbour changes, not every tick. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) RedstoneControl.sample(level, worldPosition);
    }

    /** Marks the chunk only; comparators hear about the signal from the tick, not from every change. */
    @Override
    public void setChanged() { ComparatorNotifier.markChanged(this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleporterBlockEntity teleporter) {
        teleporter.energy.beginTick();
        // Lit while armed, and for a moment after a trip: the light says the pad is ready to send.
        boolean lit = teleporter.litHold.update(teleporter.tick((ServerLevel) level)) || teleporter.armed();
        if (state.getValue(TeleporterBlock.LIT) != lit) level.setBlock(pos, state.setValue(TeleporterBlock.LIT, lit), 3);
        teleporter.syncBeam();
        teleporter.comparator.update(level, pos, state, EnergyHandlerUtil.getRedstoneSignalFromEnergyHandler(teleporter.energy));
    }

    private boolean tick(ServerLevel level) {
        var players = level.getEntitiesOfClass(ServerPlayer.class, standingBox());
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : players) present.add(player.getUUID());
        // Stepping off ends the charge and, for one who arrived here, arms the pad again.
        charging.keySet().retainAll(present);
        landed.retainAll(present);
        boolean wasActive = active;
        active = false;
        TeleportTarget target = target();
        if (target != null && redstone.allowsRunning()) {
            for (ServerPlayer player : players) {
                if (landed.contains(player.getUUID())) continue;
                stand(level, player, target);
            }
        } else {
            charging.clear();
        }
        if (active != wasActive) setChanged();
        return active;
    }

    /** One tick of a player on the pad: charging, with the sparks to show it, and the trip once charged. */
    private void stand(ServerLevel level, ServerPlayer player, TeleportTarget target) {
        int ticks = charging.merge(player.getUUID(), 1, Integer::sum);
        active = true;
        var centre = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.PORTAL, centre.x, centre.y + 1.2, centre.z, 4, 0.3, 0.5, 0.3, 0.05);
        if (ticks < chargeTicks(MachineLevel.of(getBlockState()))) return;
        charging.remove(player.getUUID());
        attempt(level, player, target, (traveller, destination) -> travel(level, traveller, destination));
    }

    /**
     * The trip itself, once charged: the level has to reach the destination, the buffer has to
     * cover it, and the pad there has to still exist. Whatever stops it is said to the player.
     * A trip that happens also drops the choice: a pad sends where it was told to once, so the
     * next player to step on is not sent somewhere someone else picked.
     */
    boolean attempt(ServerLevel level, ServerPlayer player, TeleportTarget target, Trip trip) {
        int mk = MachineLevel.of(getBlockState());
        GlobalPos from = globalPos();
        // A network card is only a destination while its storage is still on this pad's cables:
        // the pick was made down them, and a cable cut since takes the destination with it.
        if (selectedSource != null && !TeleporterGrid.walk(level, worldPosition).storages().contains(selectedSource)) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.network"));
            select(null, -1);
            return false;
        }
        if (!reaches(from, target, mk)) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.dimension", CROSS_DIMENSION_LEVEL));
            return false;
        }
        int cost = cost(from, target, mk);
        if (energy.getAmountAsInt() < cost) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.energy", cost));
            return false;
        }
        var centre = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
        if (!trip.go(player, target)) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.missing", target.name()));
            return false;
        }
        energy.set(energy.getAmountAsInt() - cost);
        selected = -1;
        selectedSource = null;
        charging.clear();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y + 1.2, centre.z, 40, 0.3, 0.6, 0.3, 0.2);
        level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
        return true;
    }

    /** Moves the player onto the pad at {@code target}, if that pad is still there, and tells it who arrived. */
    private static boolean travel(ServerLevel from, ServerPlayer player, TeleportTarget target) {
        ServerLevel destination = from.getServer().getLevel(target.dimension());
        if (destination == null) return false;
        BlockPos pad = target.pos().pos();
        if (!(destination.getBlockEntity(pad) instanceof TeleporterBlockEntity other)) return false;
        other.receive(player);
        player.teleportTo(destination, pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        destination.sendParticles(ParticleTypes.REVERSE_PORTAL, pad.getX() + 0.5, pad.getY() + 1.2, pad.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.2);
        destination.playSound(null, pad, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cards = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, cards);
        upgrades.load(input);
        energy.setCapacity(MachineLevel.capacity(CAPACITY, MachineLevel.of(getBlockState())));
        energy.set(Math.clamp(input.getIntOr("Energy", 0), 0, energy.getCapacityAsInt()));
        name = input.getStringOr("Name", "");
        selectedSource = input.read("SelectedSource", BlockPos.CODEC).orElse(null);
        selected = Math.clamp(input.getIntOr("Selected", -1), -1,
                (selectedSource == null ? 1 : StorageCardsBlockEntity.SLOTS) - 1);
        beamColour = input.getIntOr("Beam", TeleportTarget.DEFAULT_COLOUR);
        redstone.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, cards);
        output.putInt("Energy", energy.getAmountAsInt());
        if (!name.isEmpty()) output.putString("Name", name);
        output.putInt("Selected", selected);
        if (selectedSource != null) output.store("SelectedSource", BlockPos.CODEC, selectedSource);
        redstone.save(output);
        upgrades.save(output);
    }

    /**
     * The name reaches the client with the chunk, so the screen can show it before the menu opens;
     * the beam's colour rides along, and again on every update, so the beam is drawn right.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        if (!name.isEmpty()) tag.putString("Name", name);
        tag.putInt("Beam", beamColour());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == CARD_SLOT && TeleportCardItem.isWritten(stack);
    }

    /** The one slot, at every level. */
    @Override
    public int getContainerSize() { return 1; }

    @Override
    protected NonNullList<ItemStack> getItems() { return cards; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.cards = items; }

    @Override
    protected Component getDefaultName() { return Component.translatable("block.futuretech.teleporter"); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new TeleporterMenu(id, inventory, this, upgrades, data);
    }
}
