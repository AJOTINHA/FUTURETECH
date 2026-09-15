package dev.futuretech.perf;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The in-world profiler's wiring: the {@code /futuretech perf} command that toggles it, the
 * periodic report of measured blocks to each player, and the payload that carries it. The client
 * installs its viewer at start-up; a dedicated server has none and never handles the payload.
 */
public final class PerfProfiling {
    /** Ticks between reports to each player. */
    public static final int REPORT_TICKS = 10;
    /** Blocks around the player that get a label; farther ones are unreadable anyway. */
    public static final double RADIUS = 32;

    private static @Nullable Consumer<PerfSamplesPayload> viewer;

    private PerfProfiling() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(PerfSamplesPayload.TYPE, PerfSamplesPayload.STREAM_CODEC, (payload, context) -> {
            if (viewer != null) viewer.accept(payload);
        });
    }

    /** Client only: who draws the reports as they arrive. */
    public static void setViewer(Consumer<PerfSamplesPayload> viewer) { PerfProfiling.viewer = viewer; }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("futuretech")
                .then(Commands.literal("perf")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            boolean on = !TickProfiler.enabled();
                            TickProfiler.setEnabled(on);
                            if (!on) broadcast(context.getSource().getServer(), PerfSamplesPayload.OFF);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    on ? "command.futuretech.perf.on" : "command.futuretech.perf.off"), true);
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    /** While profiling, every player gets the blocks measured around them a few times a second. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TickProfiler.enabled()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        TickProfiler.prune(now);
        if (now % REPORT_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            PacketDistributor.sendToPlayer(player, new PerfSamplesPayload(true,
                    TickProfiler.totalMicros(level), TickProfiler.count(level),
                    TickProfiler.near(level, player.position(), RADIUS)));
        }
    }

    private static void broadcast(MinecraftServer server, PerfSamplesPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, payload);
    }
}
