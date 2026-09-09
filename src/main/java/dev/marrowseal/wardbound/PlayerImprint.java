package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * A deliberately soft, hidden behavioural profile. It never locks the player
 * into a class; it merely gives Wardbound a little memory of how they tend to
 * approach risk so later systems can respond without pretending every player
 * behaves the same way.
 */
public final class PlayerImprint {
    private PlayerImprint() {}

    public enum Trace {
        GREEDY("Grasping", "You repeatedly wind profitable seals tighter and accept risk for yield."),
        CAUTIOUS("Measured", "You spend protection, brace seals and walk away from bad margins."),
        VIOLENT("Red-Handed", "You answer the ward's creatures with force and finish what it sends."),
        MARKED("Marked", "You keep curses, death-laws and stains close enough for the system to notice."),
        BOUND("Bound", "You sign obligations and cultivate relationships with the card-masters."),
        UNREAD("Unread", "No behavioural trace is strong enough to dominate the record yet.");

        public final String title;
        public final String detail;
        Trace(String title, String detail) { this.title = title; this.detail = detail; }
    }

    private static String key(Trace trace) { return "imprint_" + trace.name().toLowerCase(java.util.Locale.ROOT); }

    public static int score(LockData data, UUID player, Trace trace) {
        if (trace == Trace.UNREAD) return 0;
        return Mth.clamp(data.uniqueInt(player, key(trace)), 0, 200);
    }

    public static void add(ServerPlayer player, LockData data, Trace trace, int delta, String reason) {
        if (player == null || data == null || trace == null || trace == Trace.UNREAD || delta == 0) return;
        UUID id = player.getUUID();
        int before = score(data, id, trace);
        int after = Mth.clamp(before + delta, 0, 200);
        data.setUniqueInt(id, key(trace), after);
        data.setUniqueInt(id, "imprint_samples", data.uniqueInt(id, "imprint_samples") + Math.abs(delta));

        // The first clear trace is a discovery, not a loud level-up. It exists
        // mostly so the Grimoire can later explain why the world is responding
        // differently to two players in the same save.
        if (before < 8 && after >= 8) {
            WardHistory.discoverImprint(player, trace);
            SavantMemory.onImprint(player, trace);
            WardHistory.recordSpecial(player, "WARD", "Behavioural trace established: " + trace.title
                    + (reason == null || reason.isBlank() ? "." : " · " + reason));
        }
    }

    public static Trace dominant(LockData data, UUID player) {
        int samples = data.uniqueInt(player, "imprint_samples");
        if (samples < 5) return Trace.UNREAD;
        Trace best = Trace.UNREAD;
        int bestScore = 0;
        for (Trace trace : Trace.values()) {
            if (trace == Trace.UNREAD) continue;
            int score = score(data, player, trace);
            if (score > bestScore) { bestScore = score; best = trace; }
        }
        return bestScore >= 4 ? best : Trace.UNREAD;
    }

    public static Trace secondary(LockData data, UUID player) {
        Trace first = dominant(data, player);
        Trace second = Trace.UNREAD;
        int secondScore = 0;
        for (Trace trace : Trace.values()) {
            if (trace == Trace.UNREAD || trace == first) continue;
            int value = score(data, player, trace);
            if (value > secondScore) { secondScore = value; second = trace; }
        }
        return secondScore >= 6 ? second : Trace.UNREAD;
    }

    /** Two strong traces form a soft compound profile. It is descriptive, not a class lock. */
    public static String profile(LockData data, UUID player) {
        Trace a = dominant(data, player), b = secondary(data, player);
        if (a == Trace.UNREAD) return "Unread";
        if (b == Trace.UNREAD) return a.title;
        if (pair(a, b, Trace.GREEDY, Trace.CAUTIOUS)) return "Calculated";
        if (pair(a, b, Trace.GREEDY, Trace.VIOLENT)) return "Predatory";
        if (pair(a, b, Trace.MARKED, Trace.BOUND)) return "Indentured";
        if (pair(a, b, Trace.CAUTIOUS, Trace.BOUND)) return "Methodical";
        if (pair(a, b, Trace.VIOLENT, Trace.MARKED)) return "Blood-Marked";
        return a.title + " / " + b.title;
    }

    private static boolean pair(Trace a, Trace b, Trace x, Trace y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    public static float difficultyScale(LockData data, UUID player) {
        return switch (dominant(data, player)) {
            case GREEDY -> 1.03f;
            case CAUTIOUS -> 0.985f;
            case VIOLENT -> 1.02f;
            case MARKED -> 1.035f;
            case BOUND, UNREAD -> 1.0f;
        };
    }

    public static float lootScale(LockData data, UUID player) {
        float scale = switch (dominant(data, player)) {
            case GREEDY -> 1.04f;
            case CAUTIOUS -> 0.985f;
            case MARKED -> 1.02f;
            default -> 1.0f;
        };
        Trace second = secondary(data, player);
        if (second == Trace.GREEDY) scale += 0.015f;
        if (second == Trace.CAUTIOUS) scale -= 0.008f;
        return Mth.clamp(scale, 0.96f, 1.07f);
    }

    public static float guardianScale(LockData data, UUID player) {
        float scale = switch (dominant(data, player)) {
            case VIOLENT -> 1.12f;
            case CAUTIOUS -> 0.92f;
            case MARKED -> 1.08f;
            default -> 1.0f;
        };
        return Mth.clamp(scale, 0.88f, 1.16f);
    }

    /** Small risk nudge only. Behaviour should colour the system, not dictate it. */
    public static int threatAdjustment(LockData data, UUID player) {
        Trace dominant = dominant(data, player);
        return switch (dominant) {
            case GREEDY, MARKED -> 1;
            case CAUTIOUS -> -1;
            default -> 0;
        };
    }

    public static String compact(LockData data, UUID player) {
        Trace t = dominant(data, player);
        if (t == Trace.UNREAD) return "Unread";
        Trace second = secondary(data, player);
        return profile(data, player) + " · " + score(data, player, t)
                + (second == Trace.UNREAD ? "" : "/" + score(data, player, second));
    }

    public static String full(LockData data, UUID player) {
        return "Profile " + profile(data, player) + " · Grasping " + score(data, player, Trace.GREEDY)
                + " · Measured " + score(data, player, Trace.CAUTIOUS)
                + " · Red-Handed " + score(data, player, Trace.VIOLENT)
                + " · Marked " + score(data, player, Trace.MARKED)
                + " · Bound " + score(data, player, Trace.BOUND);
    }
}
