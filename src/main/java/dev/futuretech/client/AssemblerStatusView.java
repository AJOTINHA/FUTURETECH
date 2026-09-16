package dev.futuretech.client;

import net.minecraft.network.chat.Component;

/** One label and meaning shared by the controller GUI and its physical monitor. */
final class AssemblerStatusView {
    static Component label(int status) { return Component.translatable("gui.futuretech.assembler.status." + status); }
    static int color(int status, boolean monitor) {
        return switch (status) {
            case 3, 4, 5, 6 -> monitor ? 0xFF68EBB3 : 0xFF146544;
            case 9, 10, 1 -> monitor ? 0xFFFF8176 : 0xFF9C2F28;
            case 0, 7, 8, 11, 12 -> monitor ? 0xFFFFD16D : 0xFF795113;
            default -> monitor ? 0xFFB8CBD9 : 0xFF3C4B59;
        };
    }
}
