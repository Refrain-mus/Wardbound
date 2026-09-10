package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * A mid-game three-ward cadence that turns ordinary Wardbound minigames into a
 * legible run structure instead of isolated interruptions.
 *
 * <p>Opening and Pressure are ordinary wards. Verdict is the third ward and
 * receives one existing authored minigame clause (mirror, tempo or binding),
 * so the system recombines Wardbound mechanics rather than inventing a foreign
 * puzzle. Strong execution across the three rounds banks one or two Cuts: a
 * one-card mulligan that may be spent in a later ordinary bargain hand.</p>
 */
public final class WardMeasureSystem {
    private WardMeasureSystem() {}

    /** Long-progression cadence: after Curses are established, just before Rituals fully enter the table. */
    public static final int UNLOCK_AFTER = 220;
    public static final int MAX_CUTS = 2;

    private static final String KEY_STAGE = "house_measure_stage";
    private static final String KEY_POINTS = "house_measure_points";
    private static final String KEY_CYCLES = "house_measure_cycles";
    private static final String KEY_CUTS = "house_measure_cuts";
    private static final String KEY_CUTS_SPENT = "house_measure_cuts_spent";
    private static final String KEY_FOLDS = "house_measure_folds";

    public static boolean unlocked(LockData data, UUID id) {
        // House Measures explicitly belong to the post-reveal Master layer. A player
        // who advances wards while ignoring field cards must not expose the three-hand
        // cadence ahead of the shared Master Phase gate.
        return data != null && id != null
                && data.totalBeaten(id) >= UNLOCK_AFTER
                && CardMaster.phaseActive(data, id);
    }

    public static int stage(LockData data, UUID id) {
        if (!unlocked(data, id)) return 0;
        return Math.max(0, Math.min(2, data.uniqueInt(id, KEY_STAGE)));
    }

    public static int nextStage(LockData data, UUID id) {
        if (!unlocked(data, id)) return 0;
        return Math.max(1, Math.min(3, stage(data, id) + 1));
    }

    public static int points(LockData data, UUID id) {
        return data == null || id == null ? 0 : Math.max(0, data.uniqueInt(id, KEY_POINTS));
    }

    public static int cycles(LockData data, UUID id) {
        return data == null || id == null ? 0 : Math.max(0, data.uniqueInt(id, KEY_CYCLES));
    }

    public static int cuts(LockData data, UUID id) {
        return data == null || id == null ? 0 : Math.max(0, Math.min(MAX_CUTS, data.uniqueInt(id, KEY_CUTS)));
    }

    public static int spentCuts(LockData data, UUID id) {
        return data == null || id == null ? 0 : Math.max(0, data.uniqueInt(id, KEY_CUTS_SPENT));
    }

    public static int folds(LockData data, UUID id) {
        return data == null || id == null ? 0 : Math.max(0, data.uniqueInt(id, KEY_FOLDS));
    }

    /**
     * Writes one Measure marker into a fresh ordinary ward state. Call only
     * after singular/chain ward classes are known, so special encounters never
     * accidentally consume or stack the cadence.
     */
    public static void prepare(ServerPlayer player, LockData data, CompoundTag state, long seed) {
        if (player == null || data == null || state == null || !unlocked(data, player.getUUID())) return;
        int stage = nextStage(data, player.getUUID());
        state.putInt("measureStage", stage);
        if (stage == 3) {
            int clause = verdictClause(seed, cycles(data, player.getUUID()));
            state.putInt("measureClause", clause);
            WardAdvancements.measureVerdictSeen(player);
            WardHud.message(player, Component.literal("HOUSE MEASURE III // VERDICT · " + clauseName(clause))
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        } else {
            state.putInt("measureClause", 0);
            WardHud.message(player, Component.literal(stage == 1
                            ? "HOUSE MEASURE I // OPENING"
                            : "HOUSE MEASURE II // PRESSURE")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        }
    }

    /** 1 mirror, 2 quicken, 3 bind. */
    public static int verdictClause(long seed, int cycle) {
        long mixed = seed ^ 0x4D454153555245L ^ ((long) cycle * 0x9E3779B97F4A7C15L);
        return 1 + Math.floorMod((int) (mixed ^ (mixed >>> 32)), 3);
    }

    public static int clauseMask(int clause) {
        return switch (clause) {
            case 1 -> MinigameCardEffects.MIRROR_UI;
            case 2 -> MinigameCardEffects.QUICKEN;
            case 3 -> MinigameCardEffects.BIND_ONE;
            default -> 0;
        };
    }

    public static String clauseName(int clause) {
        return switch (clause) {
            case 1 -> "Reversed Face";
            case 2 -> "Loaded Tempo";
            case 3 -> "Binding Clause";
            default -> "No Clause";
        };
    }

    private static int gradePoints(RewardBreakdown.PerformanceGrade grade) {
        if (grade == null) return 0;
        return switch (grade) {
            case PERFECT -> 2;
            case CLEAN -> 1;
            default -> 0;
        };
    }

    /** Advance only after the physical ordinary ward actually resolves. */
    public static void recordSuccess(ServerPlayer player, LockData data, CompoundTag state,
                                     RewardBreakdown.PerformanceGrade grade) {
        if (player == null || data == null || state == null || state.getBoolean("measureSettled")) return;
        int current = state.getInt("measureStage");
        if (current < 1 || current > 3) return;
        state.putBoolean("measureSettled", true);
        UUID id = player.getUUID();
        int score = points(data, id) + gradePoints(grade);
        if (current < 3) {
            data.setUniqueInt(id, KEY_STAGE, current);
            data.setUniqueInt(id, KEY_POINTS, score);
            return;
        }

        data.setUniqueInt(id, KEY_STAGE, 0);
        data.setUniqueInt(id, KEY_POINTS, 0);
        data.setUniqueInt(id, KEY_CYCLES, cycles(data, id) + 1);
        WardAdvancements.measureCompleted(player);

        int earned = score >= 5 ? 2 : score >= 3 ? 1 : 0;
        if (earned > 0) {
            int before = cuts(data, id);
            int after = Math.min(MAX_CUTS, before + earned);
            data.setUniqueInt(id, KEY_CUTS, after);
            if (after > before) {
                WardAdvancements.mulliganBanked(player);
                if (score >= 5) WardAdvancements.measureMastered(player);
                WardHistory.recordSpecial(player, "MASTERY", "House Measure complete // " + score
                        + " margin · " + (after - before) + " Cut" + (after - before == 1 ? "" : "s") + " banked");
                WardHud.message(player, Component.literal("MEASURE CLOSED // " + (after - before)
                                + " CUT" + (after - before == 1 ? "" : "S") + " BANKED · " + after + "/" + MAX_CUTS)
                        .withStyle(score >= 5 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            } else {
                WardHud.message(player, Component.literal("MEASURE CLOSED // margin was clean, but your Cut reserve is already full.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
        } else {
            WardHistory.recordSpecial(player, "WARD", "House Measure complete // no Cut earned; clean execution was insufficient across the three wards");
            WardHud.message(player, Component.literal("MEASURE CLOSED // no Cut banked. Clean work across the whole measure earns leverage.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** Any failed Measure ward breaks the current three-round line. */
    public static void recordFailure(ServerPlayer player, LockData data, CompoundTag state) {
        if (player == null || data == null || state == null || state.getInt("measureStage") <= 0
                || state.getBoolean("measureSettled")) return;
        state.putBoolean("measureSettled", true);
        UUID id = player.getUUID();
        data.setUniqueInt(id, KEY_STAGE, 0);
        data.setUniqueInt(id, KEY_POINTS, 0);
        WardHistory.recordSpecial(player, "WARD", "House Measure broken // the three-ward line restarts at Opening");
        WardHud.message(player, Component.literal("HOUSE MEASURE BROKEN // the next eligible ward begins a new Opening.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
    }

    /** Refusing an ordinary card hand is a Gwent-like tempo trade: card now, redraw leverage later. */
    public static boolean bankFromFold(ServerPlayer player, LockData data) {
        if (player == null || data == null || !unlocked(data, player.getUUID())) return false;
        UUID id = player.getUUID();
        data.setUniqueInt(id, KEY_FOLDS, folds(data, id) + 1);
        int before = cuts(data, id);
        if (before >= MAX_CUTS) return false;
        data.setUniqueInt(id, KEY_CUTS, before + 1);
        WardAdvancements.mulliganBanked(player);
        WardHistory.recordSpecial(player, "CARD", "Folded an ordinary hand // one Cut reserved for a later hand");
        return true;
    }

    public static boolean consumeCut(ServerPlayer player, LockData data) {
        if (player == null || data == null) return false;
        UUID id = player.getUUID();
        int before = cuts(data, id);
        if (before <= 0) return false;
        data.setUniqueInt(id, KEY_CUTS, before - 1);
        data.setUniqueInt(id, KEY_CUTS_SPENT, spentCuts(data, id) + 1);
        WardAdvancements.mulliganSpent(player);
        return true;
    }

    public static String stageName(int stage) {
        return switch (stage) {
            case 1 -> "Opening";
            case 2 -> "Pressure";
            case 3 -> "Verdict";
            default -> "Dormant";
        };
    }
}
