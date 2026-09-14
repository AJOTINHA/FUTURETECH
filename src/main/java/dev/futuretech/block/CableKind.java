package dev.futuretech.block;

/** What a cable carries. Ordinals travel through the connector menu's data slots, so keep the order stable. */
public enum CableKind {
    /** Energy runs in the cable's own cyan and blue. */
    ENERGY("cable", 0xFF1676C4, false, false, false),
    /** Items run in the item cable's brass. */
    ITEMS("item_cable", 0xFFD9AA3C, true, true, true);

    private final String key;
    private final int accent;
    private final boolean prioritised;
    private final boolean filtered;
    private final boolean coloured;

    CableKind(String key, int accent, boolean prioritised, boolean filtered, boolean coloured) {
        this.key = key;
        this.accent = accent;
        this.prioritised = prioritised;
        this.filtered = filtered;
        this.coloured = coloured;
    }

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

    public static CableKind byOrdinal(int ordinal) {
        CableKind[] kinds = values();
        return kinds[Math.clamp(ordinal, 0, kinds.length - 1)];
    }
}
