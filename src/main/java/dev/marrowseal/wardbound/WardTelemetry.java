package dev.marrowseal.wardbound;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Optional balance telemetry written to the local server/game log directory. */
public final class WardTelemetry {
    private static final Object LOCK = new Object();
    private static final String HEADER = "timestamp,dimension,minigame,success,difficulty,lives_left,max_lives,lives_lost,mistakes,performance,time_remaining,elapsed_seconds,latency_ms,modifiers,unsigned,chain_link,grudge,reseal_depth,clean_grade,reward\n";

    private WardTelemetry() {}

    public static void record(String dimension, MinigameType game, boolean success,
                              float difficulty, int livesLeft, int maxLives, float performance,
                              float timeRemaining, float elapsedSeconds, int mistakes, int latencyMs,
                              String modifiers, boolean unsigned, int chainLink, int spite, int depth,
                              String cleanGrade, float reward) {
        if (!WardConfig.telemetryEnabled) return;
        if (!success && !WardConfig.telemetryLogFailures) return;
        String mods = WardConfig.telemetryIncludeModifiers ? safe(modifiers) : "";
        String row = String.join(",",
                safe(Instant.now().toString()), safe(dimension), safe(game.id), String.valueOf(success),
                f(difficulty), Integer.toString(livesLeft), Integer.toString(maxLives),
                Integer.toString(Math.max(0, maxLives - livesLeft)), Integer.toString(Math.max(0, mistakes)),
                f(performance), f(timeRemaining), f(elapsedSeconds), Integer.toString(Math.max(0, latencyMs)),
                mods, String.valueOf(unsigned), Integer.toString(chainLink), Integer.toString(spite),
                Integer.toString(depth), safe(cleanGrade == null ? "NONE" : cleanGrade.toLowerCase(java.util.Locale.ROOT)),
                f(reward)) + "\n";
        Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("wardbound-telemetry.csv");
        synchronized (LOCK) {
            try {
                Files.createDirectories(file.getParent());
                boolean fresh = !Files.exists(file) || Files.size(file) == 0L;
                try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    if (fresh) out.write(HEADER);
                    out.write(row);
                }
            } catch (IOException e) {
                Wardbound.LOG.warn("[Wardbound] telemetry write failed: {}", e.toString());
            }
        }
    }

    private static String f(float v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }

    private static String safe(String s) {
        if (s == null) return "";
        String t = s.replace('"', '\'').replace('\r', ' ').replace('\n', ' ');
        if (t.indexOf(',') >= 0 || t.indexOf('|') >= 0 || t.indexOf('\'') >= 0) {
            return "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }
}
