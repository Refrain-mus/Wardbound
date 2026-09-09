package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.UUID;

/** Long-form per-player skill record for every seal discipline. */
public final class MinigameMastery {
    private MinigameMastery() {}

    private static final int[] THRESHOLDS = {0, 12, 30, 60, 105, 165};
    public static final int MAX_TIER = 5;

    private static String key(MinigameType game, String suffix) {
        return "mg_" + game.id + "_" + suffix;
    }

    public static int attempts(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "attempts")); }
    public static int wins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "wins")); }
    public static int failures(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "failures")); }
    public static int cleanWins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "clean")); }
    public static int perfectWins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "perfect")); }
    public static int scrapedWins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "scraped")); }
    public static int masteryXp(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "xp")); }
    public static int bestPerformancePermille(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "best_perf")); }
    public static int bestTimeRemainingPermille(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "best_time")); }
    public static int totalPerformancePermille(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "perf_total")); }
    public static int totalMistakes(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "mistakes_total")); }
    public static int cleanStreak(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "clean_streak")); }
    public static int bestCleanStreak(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "best_clean_streak")); }
    public static int corruptionMask(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "corruption_mask")); }
    public static int corruptionEncounters(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "corruption_seen")); }
    public static int corruptedAttempts(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "corrupt_attempts")); }
    public static int corruptedWins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "corrupt_wins")); }
    public static int corruptedPerfectWins(LockData d, UUID id, MinigameType g) { return d.uniqueInt(id, key(g, "corrupt_perfect")); }

    public static int tier(LockData data, UUID id, MinigameType game) {
        int xp = masteryXp(data, id, game);
        int wins = wins(data, id, game);
        int clean = cleanWins(data, id, game);
        int perfect = perfectWins(data, id, game);
        int tier = 0;
        if (xp >= THRESHOLDS[1] && wins >= 2) tier = 1;
        if (xp >= THRESHOLDS[2] && wins >= 4) tier = 2;
        if (xp >= THRESHOLDS[3] && wins >= 7 && clean >= 2) tier = 3;
        if (xp >= THRESHOLDS[4] && wins >= 11 && clean >= 5) tier = 4;
        if (xp >= THRESHOLDS[5] && wins >= 16 && clean >= 8 && perfect >= 2) tier = 5;
        return Mth.clamp(tier, 0, MAX_TIER);
    }

    public static int nextThreshold(int tier) {
        int next = Math.min(MAX_TIER, Math.max(0, tier) + 1);
        return THRESHOLDS[next];
    }

    /**
     * Gentle onboarding scale for an ordinary discipline. The first few clears teach the base
     * grammar before the ward starts stacking its full timing/precision pressure. Narrative hard
     * states (Unsigned/Possessed/Eldritch/Cthulhu) deliberately bypass this protection.
     */
    public static float learningDifficultyScale(int masteryTier, boolean specialWard) {
        if (specialWard) return 1f;
        return switch (Mth.clamp(masteryTier, 0, MAX_TIER)) {
            case 0 -> 0.88f;
            case 1 -> 0.95f;
            default -> 1f;
        };
    }

    public static String nextRequirement(LockData data, UUID id, MinigameType game) {
        int current = tier(data, id, game);
        if (current >= MAX_TIER) return "maximum mastery";
        int next = current + 1;
        int needWins = switch (next) { case 1 -> 2; case 2 -> 4; case 3 -> 7; case 4 -> 11; default -> 16; };
        int needClean = switch (next) { case 3 -> 2; case 4 -> 5; case 5 -> 8; default -> 0; };
        int needPerfect = next >= 5 ? 2 : 0;
        StringBuilder out = new StringBuilder();
        out.append(masteryXp(data, id, game)).append('/').append(THRESHOLDS[next]).append(" xp · ")
                .append(wins(data, id, game)).append('/').append(needWins).append(" wins");
        if (needClean > 0) out.append(" · ").append(cleanWins(data, id, game)).append('/').append(needClean).append(" clean");
        if (needPerfect > 0) out.append(" · ").append(perfectWins(data, id, game)).append('/').append(needPerfect).append(" perfect");
        return out.toString();
    }

    public static String tierName(int tier) {
        return switch (Mth.clamp(tier, 0, MAX_TIER)) {
            case 1 -> "Initiate";
            case 2 -> "Practiced";
            case 3 -> "Adept";
            case 4 -> "Mastered";
            case 5 -> "Witnessed";
            default -> "Unfamiliar";
        };
    }

    public static float averagePerformance(LockData data, UUID id, MinigameType game) {
        int wins = wins(data, id, game);
        return wins <= 0 ? 0f : Mth.clamp(totalPerformancePermille(data, id, game) / (wins * 1000f), 0f, 1f);
    }

    public static float winRate(LockData data, UUID id, MinigameType game) {
        int a = attempts(data, id, game);
        return a <= 0 ? 0f : wins(data, id, game) / (float) a;
    }

    /** A resolved attempt is one mastery observation. Closing/reopening the GUI does not add one. */
    public static void record(ServerPlayer player, LockData data, MinigameType game,
                              boolean success, RewardBreakdown.PerformanceGrade grade,
                              float performance, float timeRemaining, int mistakes,
                              MinigameCorruption.Variant corruption) {
        if (player == null || data == null || game == null) return;
        UUID id = player.getUUID();
        int beforeTier = tier(data, id, game);

        data.setUniqueInt(id, key(game, "attempts"), attempts(data, id, game) + 1);
        data.setUniqueInt(id, key(game, "mistakes_total"), totalMistakes(data, id, game) + Math.max(0, mistakes));
        if (success) data.setUniqueInt(id, key(game, "wins"), wins(data, id, game) + 1);
        else data.setUniqueInt(id, key(game, "failures"), failures(data, id, game) + 1);

        boolean corrupted = corruption != null && corruption.active();
        if (corrupted) {
            data.setUniqueInt(id, key(game, "corrupt_attempts"), corruptedAttempts(data, id, game) + 1);
            if (success) data.setUniqueInt(id, key(game, "corrupt_wins"), corruptedWins(data, id, game) + 1);
        }

        int perf = Mth.clamp(Math.round(performance * 1000f), 0, 1000);
        int time = Mth.clamp(Math.round(timeRemaining * 1000f), 0, 1000);
        if (success) {
            data.setUniqueInt(id, key(game, "best_perf"), Math.max(bestPerformancePermille(data, id, game), perf));
            data.setUniqueInt(id, key(game, "best_time"), Math.max(bestTimeRemainingPermille(data, id, game), time));
            data.setUniqueInt(id, key(game, "perf_total"), totalPerformancePermille(data, id, game) + perf);
        }

        boolean clean = success && (grade == RewardBreakdown.PerformanceGrade.CLEAN || grade == RewardBreakdown.PerformanceGrade.PERFECT);
        boolean perfect = success && grade == RewardBreakdown.PerformanceGrade.PERFECT;
        if (grade == RewardBreakdown.PerformanceGrade.SCRAPED && success) data.setUniqueInt(id, key(game, "scraped"), scrapedWins(data, id, game) + 1);
        if (clean) data.setUniqueInt(id, key(game, "clean"), cleanWins(data, id, game) + 1);
        if (perfect) {
            data.setUniqueInt(id, key(game, "perfect"), perfectWins(data, id, game) + 1);
            if (corrupted) data.setUniqueInt(id, key(game, "corrupt_perfect"), corruptedPerfectWins(data, id, game) + 1);
        }

        int streak = clean ? cleanStreak(data, id, game) + 1 : 0;
        data.setUniqueInt(id, key(game, "clean_streak"), streak);
        if (streak > bestCleanStreak(data, id, game)) data.setUniqueInt(id, key(game, "best_clean_streak"), streak);

        int gain;
        if (!success) gain = 1;
        else {
            gain = 3;
            if (performance >= 0.80f) gain++;
            if (performance >= 0.93f) gain++;
            if (mistakes == 0) gain++;
            if (clean) gain += 2;
            if (perfect) gain += 3;
            if (corrupted) gain += 2;
        }
        // Cthulhu's Game is the examination, not a 27th ordinary discipline.
        // Its attempts/wins/performance remain in the record, while exam depth
        // comes exclusively from breadth across the 26 ordinary disciplines.
        if (game != MinigameType.CTHULHUS_GAME)
            data.setUniqueInt(id, key(game, "xp"), masteryXp(data, id, game) + gain);

        int afterTier = tier(data, id, game);
        WardAdvancements.minigameResolved(player, success, grade);
        if (game != MinigameType.CTHULHUS_GAME) WardAdvancements.mastery(player, afterTier);
        if (game != MinigameType.CTHULHUS_GAME && afterTier > beforeTier) {
            WardHistory.recordSpecial(player, "MASTERY", prettify(game.id) + " mastery " + roman(afterTier) + " // " + tierName(afterTier));
            WardHud.message(player, Component.literal("MASTERY // " + prettify(game.id).toUpperCase(Locale.ROOT) + " " + roman(afterTier))
                    .withStyle(afterTier >= 4 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                    "mastery_" + game.id + "_" + afterTier,
                    afterTier >= 4
                            ? "You have stopped merely solving " + prettify(game.id) + ". The ward has noticed that you recognize its grammar. It will begin using less polite versions."
                            : "Repeated success has made " + prettify(game.id) + " legible to you. Legibility is usually when a seal starts changing the language.",
                    20L * 180L);
        }
    }

    /** Records exposure once per physical ward, and discovery once per form slot. */
    public static void recordCorruption(ServerPlayer player, LockData data, MinigameType game, MinigameCorruption.Variant variant) {
        if (player == null || data == null || game == null || variant == null || !variant.active()) return;
        UUID id = player.getUUID();
        int bit = 1 << Math.max(0, Math.min(1, variant.slot() - 1));
        int before = corruptionMask(data, id, game);
        data.setUniqueInt(id, key(game, "corruption_mask"), before | bit);
        data.setUniqueInt(id, key(game, "corruption_seen"), corruptionEncounters(data, id, game) + 1);
        if ((before & bit) == 0) {
            WardHistory.discoverAnomaly(player, "discipline_" + game.id + "_variant_" + variant.slot());
            WardHistory.recordSpecial(player, "GLITCH", prettify(game.id) + " exposed a corrupted form: " + variant.label().replace("corrupted · ", ""));
        }
    }

    public static int masteredDisciplines(LockData data, UUID id) {
        int count = 0;
        for (MinigameType game : MinigameType.values())
            if (game != MinigameType.CTHULHUS_GAME && tier(data, id, game) >= 3) count++;
        return count;
    }

    /** Overall final-exam depth comes from breadth, not from grinding Cthulhu itself. */
    public static int examTier(LockData data, UUID id) {
        int adept = masteredDisciplines(data, id);
        if (adept >= 16) return 5;
        if (adept >= 12) return 4;
        if (adept >= 8) return 3;
        if (adept >= 5) return 2;
        if (adept >= 2) return 1;
        return 0;
    }

    public static int[] tierProfile(LockData data, UUID id) {
        MinigameType[] games = MinigameType.values();
        int[] out = new int[games.length];
        for (int i = 0; i < games.length; i++) out[i] = tier(data, id, games[i]);
        return out;
    }

    public static MinigameType strongestDiscipline(LockData data, UUID id) {
        MinigameType best = MinigameType.DRUM;
        long bestScore = Long.MIN_VALUE;
        for (MinigameType game : MinigameType.values()) {
            if (game == MinigameType.CTHULHUS_GAME || attempts(data, id, game) <= 0) continue;
            long score = (long) tier(data, id, game) * 1_000_000L
                    + (long) Math.round(averagePerformance(data, id, game) * 1000f) * 1000L
                    + wins(data, id, game);
            if (score > bestScore) { bestScore = score; best = game; }
        }
        return best;
    }

    public static MinigameType weakestDiscipline(LockData data, UUID id) {
        MinigameType weakest = MinigameType.DRUM;
        long weakestScore = Long.MAX_VALUE;
        boolean found = false;
        for (MinigameType game : MinigameType.values()) {
            if (game == MinigameType.CTHULHUS_GAME || attempts(data, id, game) <= 0) continue;
            long score = (long) tier(data, id, game) * 1_000_000L
                    + (long) Math.round(winRate(data, id, game) * 1000f) * 1000L
                    + Math.round(averagePerformance(data, id, game) * 1000f);
            if (score < weakestScore) { weakestScore = score; weakest = game; found = true; }
        }
        return found ? weakest : strongestDiscipline(data, id);
    }

    public static String tierLabel(LockData data, UUID id, MinigameType game) {
        if (game == MinigameType.CTHULHUS_GAME)
            return "Final Exam · Depth " + roman(examTier(data, id));
        int t = tier(data, id, game);
        return "Mastery " + roman(t) + " · " + tierName(t);
    }

    public static String compact(LockData data, UUID id, MinigameType game) {
        int t = tier(data, id, game);
        int a = attempts(data, id, game);
        int w = wins(data, id, game);
        int best = bestPerformancePermille(data, id, game);
        if (game == MinigameType.CTHULHUS_GAME) {
            return "Exam depth " + roman(examTier(data, id)) + " · " + w + "/" + a + " clears · best " + Math.round(best / 10f) + "%";
        }
        return "Mastery " + roman(t) + " · " + w + "/" + a + " wins · best " + Math.round(best / 10f) + "%"
                + (corruptionEncounters(data, id, game) > 0 ? " · corrupted " + corruptionEncounters(data, id, game) : "");
    }

    public static String detail(LockData data, UUID id, MinigameType game) {
        if (game == MinigameType.CTHULHUS_GAME) {
            return wins(data, id, game) + " clear · " + failures(data, id, game) + " fail · "
                    + "avg " + Math.round(averagePerformance(data, id, game) * 100f) + "% · best "
                    + Math.round(bestPerformancePermille(data, id, game) / 10f) + "% · best time left "
                    + Math.round(bestTimeRemainingPermille(data, id, game) / 10f) + "% · mistakes "
                    + totalMistakes(data, id, game) + " · final-exam depth " + roman(examTier(data, id))
                    + " · " + masteredDisciplines(data, id) + "/26 adept ordinary disciplines"
                    + " · exam depth is earned from the syllabus, not Cthulhu clears";
        }
        int t = tier(data, id, game);
        String next = t >= MAX_TIER ? "maximum mastery" : nextRequirement(data, id, game);
        int ca = corruptedAttempts(data, id, game);
        String corrupt = ca <= 0 ? "no corrupted resolution yet"
                : corruptedWins(data, id, game) + "/" + ca + " corrupted wins · " + corruptedPerfectWins(data, id, game) + " perfect";
        return wins(data, id, game) + " win · " + failures(data, id, game) + " fail · "
                + scrapedWins(data, id, game) + " scraped · " + cleanWins(data, id, game) + " clean · " + perfectWins(data, id, game) + " perfect · "
                + "avg " + Math.round(averagePerformance(data, id, game) * 100f) + "% · best "
                + Math.round(bestPerformancePermille(data, id, game) / 10f) + "% · best time left "
                + Math.round(bestTimeRemainingPermille(data, id, game) / 10f) + "% · " + corrupt + " · "
                + "mistakes " + totalMistakes(data, id, game) + " · best clean streak " + bestCleanStreak(data, id, game)
                + " · " + next + " · forms: " + MinigameCorruption.discoveredFormDetails(data, id, game);
    }

    public static String prettify(String s) {
        String[] bits = s.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String bit : bits) {
            if (bit.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(bit.charAt(0))).append(bit.substring(1));
        }
        return out.toString();
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> "—"; };
    }
}
