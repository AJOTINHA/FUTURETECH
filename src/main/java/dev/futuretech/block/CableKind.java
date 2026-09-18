package dev.futuretech.block;

import dev.futuretech.api.side.SideMode;

/** What a cable carries. Ordinals travel through the connector menu's data slots, so keep the order stable. */
public enum CableKind {
    /**
     * Energy runs in the cable's own core, which wears the MK's colour, white through cyan; the
     * tiers are {@link CableTier}. A fresh connector inserts and extracts at once:
     * energy only ever leaves the blocks that push it out, so there is nothing to drain by
     * accident, and every generator, battery and machine is wired the moment the cable touches it.
     */
    ENERGY("cable", 0xFF1676C4, false, false, false, false, false, SideMode.BOTH),
    /** Items run in the item cable's grey. A fresh connector moves nothing until the player picks a mode. */
    ITEMS("item_cable", 0xFF9AA0A6, true, true, true, true, false, SideMode.NONE),
    /** Fluids run in the fluid cable's green. A fresh connector moves nothing until the player picks a mode. */
    FLUID("fluid_cable", 0xFF4EC878, true, false, true, false, false, SideMode.NONE),
    /**
     * The network cable's purple. Nothing runs in it yet: it is the cable itself, with the links,
     * the wrench and the panels every kind shares, waiting for what it will carry. It links to
     * other network cables and to the teleporter, and the collar it wears there stays shut — none
     * of the settings below apply to a cable with nothing to move.
     */
    NETWORK("network_cable", 0xFFA855D6, false, false, false, false, false, SideMode.NONE),
    /**
     * Redstone runs in the redstone cable's red. The words turn round on this kind: a signal
     * lives in the wire, so a connector set to insert reads the signal of the block beside it
     * into the cable, and one set to extract gives the signal out, the way a repeater does. Each
     * connector is on a colour and a channel, and every colour-channel pair is a line of its own,
     * carrying the strongest signal read on it: sixteen wires in one cable. There is no priority
     * to serve by and nothing to filter, and a fresh connector reads and gives nothing until the
     * player says which, since a cable that gave out whatever it read would loop through the
     * first block of stone it touched. Its connectors carry the two switches a signal wants: a
     * sensor, to read the block the way a comparator does, and strong or weak power to give.
     */
    REDSTONE("redstone_cable", 0xFFD21A1E, false, false, true, false, true, SideMode.NONE);

    private final String key;
    private final int accent;
    private final boolean prioritised;
    private final boolean filtered;
    private final boolean coloured;
    private final boolean upgradable;
    private final boolean signalled;
    private final SideMode freshConnector;

    CableKind(String key, int accent, boolean prioritised, boolean filtered, boolean coloured, boolean upgradable,
              boolean signalled, SideMode freshConnector) {
        this.key = key;
        this.accent = accent;
        this.prioritised = prioritised;
        this.filtered = filtered;
        this.coloured = coloured;
        this.upgradable = upgradable;
        this.signalled = signalled;
        this.freshConnector = freshConnector;
    }

    /** The mode a connector of this kind starts on when the cable is placed. */
    public SideMode freshConnector() { return freshConnector; }

    /** Short name shared by this kind's textures and translation keys, e.g. {@code item_cable}. */
    public String id() { return key; }

    /** ARGB colour of this kind's cable, used wherever a screen lights something up for it. */
    public int accent() { return accent; }

    /** Stem of the connector screen's translation keys, e.g. {@code gui.futuretech.cable}. */
    public String translationKey() { return "gui.futuretech." + key; }

    /** Whether connectors of this kind carry a priority the network serves them by. */
    public boolean prioritised() { return prioritised; }

    /** Whether connectors of this kind take a filter card deciding what may cross them. */
    public boolean filtered() { return filtered; }

    /** Whether connectors of this kind carry a colour, and only talk to connectors of the same one. */
    public boolean coloured() { return coloured; }

    /** Whether connectors of this kind take an upgrade module. */
    public boolean upgradable() { return upgradable; }

    /**
     * Whether connectors of this kind carry a redstone signal, and with it the sensor switch,
     * which reads the block beside it as a comparator would, and the strong switch, which says
     * whether the signal given out powers the block through or only wakes it.
     */
    public boolean signalled() { return signalled; }

    public static CableKind byOrdinal(int ordinal) {
        CableKind[] kinds = values();
        return kinds[Math.clamp(ordinal, 0, kinds.length - 1)];
    }
}
