package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.UUID;

/**
 * Persistent, player-specific execution profile built from real resolved minigames.
 * It is deliberately descriptive rather than a difficulty class: later systems may
 * choose how to test the player, but never reduce the base puzzle because the player
 * has performed badly in the past.
 */
public final class MinigameSkillProfile {
    private MinigameSkillProfile() {}

    public enum Axis {
        TIMING("Timing accuracy"),
        LOGIC("Logic efficiency"),
        MEMORY("Memory discipline"),
        COMPOSURE("Composure");

        public final String label;
        Axis(String label) { this.label = label; }
    }

    private enum Family { TIMING, LOGIC, MEMORY, SPATIAL, MIXED }

    private static String scoreKey(Axis axis) { return "skill_" + axis.name().toLowerCase(Locale.ROOT) + "_score"; }
    private static String sampleKey(Axis axis) { return "skill_" + axis.name().toLowerCase(Locale.ROOT) + "_samples"; }

    public static int samples(LockData data, UUID id, Axis axis) {
        return Math.max(0, data.uniqueInt(id, sampleKey(axis)));
    }

    /** 0..1000. Unobserved axes return a neutral 500 but are labelled unread elsewhere. */
    public static int score(LockData data, UUID id, Axis axis) {
        if (samples(data, id, axis) <= 0) return 500;
        return Mth.clamp(data.uniqueInt(id, scoreKey(axis)), 0, 1000);
    }

    public static float normalized(LockData data, UUID id, Axis axis) {
        return score(data, id, axis) / 1000f;
    }

    private static void observe(LockData data, UUID id, Axis axis, float value) {
        int oldSamples = samples(data, id, axis);
        int oldScore = score(data, id, axis);
        int observed = Math.round(Mth.clamp(value, 0f, 1f) * 1000f);
        // A bounded EMA: early attempts matter, but an old bad evening does not define a save forever.
        float weight = oldSamples < 5 ? 1f / (oldSamples + 1f) : 0.16f;
        int next = oldSamples <= 0 ? observed : Math.round(oldScore + (observed - oldScore) * weight);
        data.setUniqueInt(id, scoreKey(axis), Mth.clamp(next, 0, 1000));
        data.setUniqueInt(id, sampleKey(axis), Math.min(9999, oldSamples + 1));
    }

    public static void record(ServerPlayer player, LockData data, MinigameType game,
                              boolean success, float performance, float timeRemaining,
                              int mistakes, int rapidMistakes, int maxLives,
                              int hybridOutcome) {
        if (!WardConfig.adaptiveSkillProfileEnabled || player == null || data == null || game == null || game == MinigameType.CTHULHUS_GAME) return;
        UUID id = player.getUUID();
        float perf = Mth.clamp(performance, 0f, 1f);
        float time = Mth.clamp(timeRemaining, 0f, 1f);
        int errors = Math.max(0, mistakes);
        int rapid = Math.max(0, rapidMistakes);
        int lifeBase = Math.max(1, maxLives);
        float errorDiscipline = Mth.clamp(1f - errors / (float) (lifeBase + 1), 0f, 1f);
        float successFloor = success ? 1f : 0.22f;

        Family family = family(game);
        switch (family) {
            case TIMING -> {
                observe(data, id, Axis.TIMING, perf * 0.62f + time * 0.18f + errorDiscipline * 0.20f);
                add(data, id, "skill_timing_mistakes", errors);
            }
            case LOGIC -> {
                observe(data, id, Axis.LOGIC, perf * 0.70f + time * 0.10f + errorDiscipline * 0.20f);
                add(data, id, "skill_logic_mistakes", errors);
            }
            case MEMORY -> {
                observe(data, id, Axis.MEMORY, perf * 0.48f + errorDiscipline * 0.37f + successFloor * 0.15f);
                add(data, id, "skill_memory_mistakes", errors);
            }
            case SPATIAL -> {
                observe(data, id, Axis.LOGIC, perf * 0.56f + time * 0.14f + errorDiscipline * 0.30f);
                observe(data, id, Axis.TIMING, perf * 0.48f + time * 0.22f + errorDiscipline * 0.30f);
                add(data, id, "skill_spatial_mistakes", errors);
            }
            case MIXED -> {
                observe(data, id, Axis.TIMING, perf * 0.50f + time * 0.20f + errorDiscipline * 0.30f);
                observe(data, id, Axis.LOGIC, perf * 0.52f + time * 0.13f + errorDiscipline * 0.35f);
                add(data, id, "skill_mixed_mistakes", errors);
            }
        }

        float panicPenalty = Mth.clamp(rapid * 0.12f + errors * 0.055f, 0f, 0.72f);
        float composure = Mth.clamp(0.58f * errorDiscipline + 0.28f * successFloor + 0.14f * time - panicPenalty, 0f, 1f);
        observe(data, id, Axis.COMPOSURE, composure);
        data.setUniqueInt(id, "skill_panic_mistakes", Math.min(999999,
                data.uniqueInt(id, "skill_panic_mistakes") + rapid));
        int profileAttempts = Math.min(999999, data.uniqueInt(id, "skill_profile_attempts") + 1);
        data.setUniqueInt(id, "skill_profile_attempts", profileAttempts);
        if (profileAttempts == 8 || profileAttempts == 24 || profileAttempts == 64) {
            WardHistory.recordSpecial(player, "PROFILE", compact(data, id));
            SavantMemory.onSkillProfile(player, strongest(data, id), weakest(data, id));
        }
        if (hybridOutcome == 1) data.setUniqueInt(id, "skill_hybrid_wins", data.uniqueInt(id, "skill_hybrid_wins") + 1);
        else if (hybridOutcome == 2) data.setUniqueInt(id, "skill_hybrid_losses", data.uniqueInt(id, "skill_hybrid_losses") + 1);
    }

    private static void add(LockData data, UUID id, String key, int amount) {
        if (amount <= 0) return;
        data.setUniqueInt(id, key, Math.min(999999, data.uniqueInt(id, key) + amount));
    }

    private static Family family(MinigameType game) {
        return switch (game) {
            case DRUM, TIMING, PRESSURE, PULSE, GYRE, KEYWAY -> Family.TIMING;
            case CIPHER, AUGURY, LATTICE, RUNEBRAID, BLACK_MEASURE, EPITAPH, ORRERY, PROCESSION -> Family.LOGIC;
            case MEMORY, RUNES, SHARDSONG, CONSTELLATION -> Family.MEMORY;
            case PARALLAX, VEILSTEP, ROOTWAY, MIRROR -> Family.SPATIAL;
            case RESONANCE, BALANCE, VESSEL, YOKE -> Family.MIXED;
            case CTHULHUS_GAME -> Family.MIXED;
        };
    }

    public static Axis strongest(LockData data, UUID id) {
        Axis best = Axis.TIMING;
        int bestValue = -1;
        for (Axis axis : Axis.values()) {
            if (samples(data, id, axis) <= 0) continue;
            int v = score(data, id, axis);
            if (v > bestValue) { bestValue = v; best = axis; }
        }
        return best;
    }

    public static Axis weakest(LockData data, UUID id) {
        Axis weak = Axis.TIMING;
        int low = 1001;
        for (Axis axis : Axis.values()) {
            if (samples(data, id, axis) <= 0) continue;
            int v = score(data, id, axis);
            if (v < low) { low = v; weak = axis; }
        }
        return weak;
    }

    public static int observedAxes(LockData data, UUID id) {
        int n = 0;
        for (Axis axis : Axis.values()) if (samples(data, id, axis) >= 3) n++;
        return n;
    }

    /** Strong profiles invite stranger tests; weak profiles are never punished with extra difficulty. */
    public static float challengeReadiness(LockData data, UUID id) {
        if (!WardConfig.adaptiveSkillProfileEnabled || observedAxes(data, id) < 3) return 0f;
        float avg = 0f;
        for (Axis axis : Axis.values()) avg += normalized(data, id, axis);
        avg /= Axis.values().length;
        return Mth.clamp((avg - 0.55f) / 0.30f, 0f, 1f);
    }

    public static String compact(LockData data, UUID id) {
        if (observedAxes(data, id) == 0) return "Unread";
        return "Timing " + percent(data, id, Axis.TIMING)
                + " · Logic " + percent(data, id, Axis.LOGIC)
                + " · Memory " + percent(data, id, Axis.MEMORY)
                + " · Composure " + percent(data, id, Axis.COMPOSURE);
    }

    public static String detail(LockData data, UUID id) {
        if (observedAxes(data, id) == 0) return "The ward has not seen enough resolved minigames to infer a skill profile.";
        Axis strong = strongest(data, id), weak = weakest(data, id);
        int panic = data.uniqueInt(id, "skill_panic_mistakes");
        int memoryMistakes = data.uniqueInt(id, "skill_memory_mistakes");
        int logicMistakes = data.uniqueInt(id, "skill_logic_mistakes");
        int timingMistakes = data.uniqueInt(id, "skill_timing_mistakes");
        int hw = data.uniqueInt(id, "skill_hybrid_wins"), hl = data.uniqueInt(id, "skill_hybrid_losses");
        return "Strongest observed axis: " + strong.label + " · weakest: " + weak.label
                + " · timing misses " + timingMistakes + " · logic mistakes " + logicMistakes
                + " · memory mistakes " + memoryMistakes + " · panic clicks " + panic
                + " · hybrid record " + hw + "/" + (hw + hl)
                + ". This profile changes which advanced tests notice you; it never lowers base puzzle difficulty.";
    }

    private static String percent(LockData data, UUID id, Axis axis) {
        if (samples(data, id, axis) <= 0) return "—";
        return Math.round(score(data, id, axis) / 10f) + "%";
    }
}
