package dev.futuretech.menu;

import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.UpgradeInventory;
import dev.futuretech.block.CableKind;
import dev.futuretech.block.entity.AbstractCableBlockEntity;
import dev.futuretech.block.entity.ItemCableBlockEntity;
import dev.futuretech.block.entity.RedstoneCableBlockEntity;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.registry.ModMenus;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One cable connector's two directions, how it answers to redstone and, on cables that have
 * them, its priority and filter card. The face being configured and the cable's kind ride in the opening packet, so both sides
 * build the same slots from the start; the mode and priority travel as data slots and the button
 * clicks ride vanilla's own channel. Only a filtered kind has slots at all: the card's own, the
 * upgrade module's, and the player's inventory to take them from.
 *
 * <p>Directions are named from the player's side of the glass. Inserting means the cable hands
 * its resource to the neighbour, which is that cable face allowing output; extracting means the
 * resource enters the cable there, which is the face allowing input.
 */
public final class CableConnectorMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 8;
    public static final int TOGGLE_INSERT = 0;
    public static final int TOGGLE_EXTRACT = 1;
    public static final int RAISE_PRIORITY = 2;
    public static final int LOWER_PRIORITY = 3;
    /** The same steps taken with shift held: ten at a time. */
    public static final int RAISE_PRIORITY_FAST = 4;
    public static final int LOWER_PRIORITY_FAST = 5;
    public static final int OPEN_FILTER = 6;
    /** {@code SET_COLOR + dye.getId()} picks that dye as the connector's colour. */
    public static final int SET_COLOR = 10;
    public static final int RAISE_CHANNEL = 30;
    public static final int LOWER_CHANNEL = 31;
    /** The same steps taken with shift held: ten at a time. */
    public static final int RAISE_CHANNEL_FAST = 32;
    public static final int LOWER_CHANNEL_FAST = 33;
    public static final int FAST_STEP = 10;
    /** {@code SET_REDSTONE + mode.ordinal()} picks how the connector answers to redstone. */
    public static final int SET_REDSTONE = 40;
    /** On a signalled kind: whether the connector reads as a comparator, and whether it gives strong power. */
    public static final int TOGGLE_SENSOR = 50;
    public static final int TOGGLE_STRONG = 51;
    /** Where the module's and the card's slots sit on the panel, in line with the minus buttons above; the screen draws the rows around them. */
    public static final int UPGRADE_SLOT_X = 97;
    public static final int UPGRADE_SLOT_Y = 168;
    public static final int FILTER_SLOT_X = 97;
    public static final int FILTER_SLOT_Y = 194;
    public static final int INVENTORY_TOP = 224;
    private static final int MODE = 0;
    private static final int PRIORITY = 1;
    private static final int COLOR = 2;
    private static final int CHANNEL = 3;
    private static final int REDSTONE = 4;
    private static final int POWERED = 5;
    private static final int SENSOR = 6;
    private static final int STRONG = 7;
    private static final int FILTER_SLOT = 0;
    private static final int UPGRADE_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int HOTBAR_START = INVENTORY_START + 27;
    private static final int INVENTORY_END = HOTBAR_START + 9;
    private static final double REACH_SQUARED = 64.0;

    private final ContainerData data;
    private final @Nullable AbstractCableBlockEntity cable;
    private final Direction side;
    private final CableKind kind;
    private final @Nullable Slot filterSlot;
    private final @Nullable Slot upgradeSlot;

    /** Client side: the opening packet says which face and which kind of cable this is. */
    public CableConnectorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, null, extra.readEnum(Direction.class), extra.readEnum(CableKind.class),
                new SimpleContainer(Direction.values().length), new SimpleContainer(Direction.values().length),
                new SimpleContainerData(DATA_COUNT));
    }

    private CableConnectorMenu(int id, Inventory inventory, @Nullable AbstractCableBlockEntity cable,
                               Direction side, CableKind kind, Container filters, Container upgrades, ContainerData data) {
        super(ModMenus.CABLE_CONNECTOR.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.cable = cable;
        this.side = side;
        this.kind = kind;
        this.data = data;
        if (kind.filtered()) {
            checkContainerSize(filters, Direction.values().length);
            filterSlot = addSlot(new Slot(filters, side.ordinal(), FILTER_SLOT_X, FILTER_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) { return ItemFilterItem.isFilter(stack); }

                @Override
                public int getMaxStackSize() { return 1; }
            });
            checkContainerSize(upgrades, Direction.values().length);
            upgradeSlot = addSlot(new Slot(upgrades, side.ordinal(), UPGRADE_SLOT_X, UPGRADE_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) { return UpgradeInventory.isUpgrade(stack); }

                @Override
                public int getMaxStackSize() { return ItemCableBlockEntity.MAX_UPGRADES; }
            });
            addStandardInventorySlots(inventory, 8, INVENTORY_TOP);
        } else {
            filterSlot = null;
            upgradeSlot = null;
        }
        addDataSlots(data);
    }

    /** What the server writes when opening; the client constructor reads it back in the same order. */
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, AbstractCableBlockEntity cable, Direction side) {
        buffer.writeEnum(side);
        buffer.writeEnum(cable.kind());
    }

    /** Server side: reads straight from the cable, so an edit from anywhere reaches every viewer. */
    public static CableConnectorMenu opening(int id, Inventory inventory, AbstractCableBlockEntity cable, Direction side) {
        return new CableConnectorMenu(id, inventory, cable, side, cable.kind(), cable.connectorFilters(),
                cable.connectorUpgrades(), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case MODE -> cable.connectors().mode(side).ordinal();
                    case PRIORITY -> cable.connectorPriority(side);
                    case COLOR -> cable.connectorColor(side).ordinal();
                    case CHANNEL -> cable.connectorChannel(side);
                    case REDSTONE -> cable.connectorRedstone(side).ordinal();
                    case POWERED -> cable.isPowered() ? 1 : 0;
                    case SENSOR -> cable instanceof RedstoneCableBlockEntity wire && wire.isSensor(side) ? 1 : 0;
                    // A kind without the switch gives strong power, which is what every block does.
                    default -> !(cable instanceof RedstoneCableBlockEntity wire) || wire.isStrong(side) ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        });
    }

    public Direction side() { return side; }

    public CableKind kind() { return kind; }

    public SideMode mode() { return SideMode.byOrdinal(data.get(MODE)); }

    public int priority() { return data.get(PRIORITY); }

    /** The connector's colour channel. */
    public DyeColor color() { return DyeColor.byId(Math.clamp(data.get(COLOR), 0, DyeColor.values().length - 1)); }

    public int channel() { return data.get(CHANNEL); }

    /** How this connector answers to redstone. */
    public RedstoneMode redstone() { return RedstoneMode.byOrdinal(data.get(REDSTONE)); }

    /** Whether the cable has a signal right now; every connector on it reads the same one. */
    public boolean isPowered() { return data.get(POWERED) != 0; }

    /** Whether the connector reads the block beside it as a comparator would; signalled kinds only. */
    public boolean sensor() { return data.get(SENSOR) != 0; }

    /** Whether the connector gives strong power, through the block, rather than only waking it. */
    public boolean strong() { return data.get(STRONG) != 0; }

    /** Whether a filter card sits in the connector's slot; the screen shows the gear only then. */
    public boolean hasFilter() { return filterSlot != null && filterSlot.hasItem(); }

    /** The cable hands its resource to the neighbour through this connector. */
    public boolean inserts() { return mode().allowsOutput(); }

    /** The resource enters the cable through this connector. */
    public boolean extracts() { return mode().allowsInput(); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cable == null) return false;
        SideMode current = cable.connectors().mode(side);
        boolean insert = current.allowsOutput();
        boolean extract = current.allowsInput();
        switch (id) {
            case TOGGLE_INSERT -> insert = !insert;
            case TOGGLE_EXTRACT -> extract = !extract;
            case RAISE_PRIORITY, LOWER_PRIORITY, RAISE_PRIORITY_FAST, LOWER_PRIORITY_FAST -> {
                int step = id == RAISE_PRIORITY || id == LOWER_PRIORITY ? 1 : FAST_STEP;
                if (id == LOWER_PRIORITY || id == LOWER_PRIORITY_FAST) step = -step;
                cable.setConnectorPriority(side, cable.connectorPriority(side) + step);
                return true;
            }
            case OPEN_FILTER -> {
                openFilter(player);
                return true;
            }
            case TOGGLE_SENSOR, TOGGLE_STRONG -> {
                if (!kind.signalled() || !(cable instanceof RedstoneCableBlockEntity wire)) return false;
                if (id == TOGGLE_SENSOR) wire.setSensor(side, !wire.isSensor(side));
                else wire.setStrong(side, !wire.isStrong(side));
                return true;
            }
            case RAISE_CHANNEL, LOWER_CHANNEL, RAISE_CHANNEL_FAST, LOWER_CHANNEL_FAST -> {
                if (!kind.coloured()) return false;
                int step = id == RAISE_CHANNEL || id == LOWER_CHANNEL ? 1 : FAST_STEP;
                if (id == LOWER_CHANNEL || id == LOWER_CHANNEL_FAST) step = -step;
                cable.setConnectorChannel(side, cable.connectorChannel(side) + step);
                return true;
            }
            default -> {
                if (id >= SET_REDSTONE && id < SET_REDSTONE + RedstoneMode.values().length) {
                    cable.setConnectorRedstone(side, RedstoneMode.values()[id - SET_REDSTONE]);
                    return true;
                }
                if (!kind.coloured() || id < SET_COLOR || id >= SET_COLOR + DyeColor.values().length) return false;
                cable.setConnectorColor(side, DyeColor.byId(id - SET_COLOR));
                return true;
            }
        }
        cable.setConnectorMode(side, SideMode.of(extract, insert));
        return true;
    }

    /** Swaps this menu for the card's editor; closing that one returns the player to the world. */
    private void openFilter(Player player) {
        if (cable == null || !kind.filtered() || !ItemFilterItem.isFilter(cable.connectorFilters().getItem(side.ordinal()))) return;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> ItemFilterMenu.opening(id, inventory, cable, side),
                Component.translatable("gui.futuretech.filter.title",
                        Component.translatable("gui.futuretech.direction." + side.getName()))),
                buffer -> {
                    buffer.writeEnum(side);
                    buffer.writeEnum(ItemFilterItem.tier(cable.connectorFilters().getItem(side.ordinal())));
                });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (filterSlot == null || upgradeSlot == null || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == FILTER_SLOT || index == UPGRADE_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else if (ItemFilterItem.isFilter(stack) && !filterSlot.hasItem()) {
            if (!moveItemStackTo(stack, FILTER_SLOT, FILTER_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (UpgradeInventory.isUpgrade(stack) && upgradeSlot.getItem().getCount() < ItemCableBlockEntity.MAX_UPGRADES
                && (!upgradeSlot.hasItem() || ItemStack.isSameItemSameComponents(upgradeSlot.getItem(), stack))) {
            if (!moveItemStackTo(stack, UPGRADE_SLOT, UPGRADE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, INVENTORY_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (cable == null) return true;
        return !cable.isRemoved()
                && player.distanceToSqr(Vec3.atCenterOf(cable.getBlockPos())) <= REACH_SQUARED;
    }
}
