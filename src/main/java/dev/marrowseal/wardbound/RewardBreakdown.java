package dev.marrowseal.wardbound;

import net.minecraft.util.Mth;
import dev.marrowseal.wardbound.item.Charm;

/**
 * One source of truth for every successful ward's loot multiplier.
 *
 * <p>The UI preview and the server-side payout used to assemble almost the same
 * formula in two different places. Chain, grudge and contempt bonuses were in
 * the preview but missing from the payout, while unsigned/gauntlet bonuses were
 * applied after the multiplier had already been written to the chest. This
 * class deliberately owns the whole formula so the two can no longer drift.
 */
public final class RewardBreakdown {
    public final float performance;
    public final float game;
    public final float streak;
    public final float dimension;
    public final float charm;
    public final float tier;
    public final float greed;
    public final float chain;
    public final float grudge;
    public final float contempt;
    public final float unsigned;
    public final float gauntlet;
    public final float global;
    public final float cleanAdd;
    public final float raw;
    public final float total;
    public final boolean capped;

    private RewardBreakdown(float performance, float game, float streak, float dimension,
                            float charm, float tier, float greed, float chain, float grudge,
                            float contempt, float unsigned, float gauntlet, float global,
                            float cleanAdd) {
        this.performance = performance;
        this.game = game;
        this.streak = streak;
        this.dimension = dimension;
        this.charm = charm;
        this.tier = tier;
        this.greed = greed;
        this.chain = chain;
        this.grudge = grudge;
        this.contempt = contempt;
        this.unsigned = unsigned;
        this.gauntlet = gauntlet;
        this.global = global;
        this.cleanAdd = cleanAdd;

        float product = performance * game * streak * dimension * charm * tier * greed
                * chain * grudge * contempt * unsigned * gauntlet * global;
        raw = Math.max(0f, product + cleanAdd);
        total = clamp(raw);
        capped = total + 0.0001f < raw;
    }

    public static RewardBreakdown success(MinigameType game, DimSettings settings,
                                          int priorStreak, Charm charm, int tier, int depth,
                                          int chainLink, int spite, boolean contempt,
                                          boolean unsigned, boolean gauntletFinal,
                                          int livesLeft, int maxLives, float skill,
                                          PerformanceGrade grade) {
        float clean = switch (grade) {
            case PERFECT -> WardConfig.perfectWinLootAdd;
            case CLEAN -> WardConfig.cleanWinLootAdd;
            default -> 0f;
        };
        float gradeScale = WardConfig.cleanWinsEnabled && grade == PerformanceGrade.SCRAPED ? WardConfig.scrapedWinLootScale : 1f;
        return new RewardBreakdown(
                ChestValuator.successMultiplier(livesLeft, maxLives, skill) * gradeScale,
                game.reward * WardConfig.tuning(game).rewardScale,
                ChestValuator.streakBonus(priorStreak),
                settings.lootMultiplier,
                charm.lootBonus(),
                ChestValuator.tierLoot(tier),
                ChestValuator.temptBonus(depth),
                1f + Math.max(0, chainLink) * WardConfig.chainLootPerLink,
                1f + Math.max(0, spite) * WardConfig.spiteLoot,
                contempt ? WardConfig.contemptLoot : 1f,
                unsigned ? WardConfig.unsignedLoot : 1f,
                gauntletFinal ? WardConfig.gauntletLoot : 1f,
                1f, // global ward-roll frequency is intentionally not a reward factor
                clean);
    }

    /**
     * Preview of factors already known before the player performs the lock. The
     * skill/lives component deliberately stays at 1 so the footer calls this a
     * base multiplier rather than promising a reward not yet earned.
     */
    public static float preview(MinigameType game, DimSettings settings, int streak,
                                Charm charm, int tier, int depth, int chainLink, int spite,
                                boolean contempt, boolean unsigned, boolean gauntletFinal) {
        RewardBreakdown r = new RewardBreakdown(
                1f,
                game.reward * WardConfig.tuning(game).rewardScale,
                ChestValuator.streakBonus(streak), settings.lootMultiplier,
                charm.lootBonus(), ChestValuator.tierLoot(tier), ChestValuator.temptBonus(depth),
                1f + Math.max(0, chainLink) * WardConfig.chainLootPerLink,
                1f + Math.max(0, spite) * WardConfig.spiteLoot,
                contempt ? WardConfig.contemptLoot : 1f,
                unsigned ? WardConfig.unsignedLoot : 1f,
                gauntletFinal ? WardConfig.gauntletLoot : 1f,
                1f, 0f); // global ward-roll frequency is intentionally not a reward factor
        return r.total;
    }

    public static float clamp(float multiplier) {
        float cap = Math.max(1f, WardConfig.maxLootMultiplier);
        return Mth.clamp(multiplier, 0f, cap);
    }

    public enum PerformanceGrade {
        /** Reserved for a failed/unresolved attempt. Every successful ward is Scraped, Clean or Perfect. */
        NONE,
        SCRAPED,
        CLEAN,
        PERFECT;

        public static PerformanceGrade of(int livesLeft, int maxLives, float performance,
                                          float timeRemainingRatio, int mistakes) {
            if (maxLives <= 0 || livesLeft <= 0) return NONE;
            float perf = Mth.clamp(performance, 0f, 1f);
            float time = Mth.clamp(timeRemainingRatio, 0f, 1f);
            int errors = Math.max(0, mistakes);
            if (!WardConfig.cleanWinsEnabled) return SCRAPED;
            if (livesLeft >= maxLives && errors == 0
                    && perf >= WardConfig.perfectWinPerformanceThreshold
                    && time >= WardConfig.perfectWinTimeRemaining) return PERFECT;
            if (livesLeft >= maxLives
                    && perf >= WardConfig.cleanWinPerformanceThreshold
                    && time >= WardConfig.cleanWinTimeRemaining) return CLEAN;
            return SCRAPED;
        }

        public String display(MinigameType game) {
            if (this == SCRAPED) return "SCRAPED";
            if (this == CLEAN) return "CLEAN";
            if (this != PERFECT || game == null) return this == PERFECT ? "PERFECT" : "";
            return switch (game) {
                case RUNEBRAID -> "PERFECT WEAVE";
                case BLACK_MEASURE -> "PERFECT MEASURE";
                case EPITAPH -> "PERFECT READING";
                case ORRERY -> "PERFECT ORBIT";
                case PROCESSION -> "PERFECT VERDICT";
                case LATTICE -> "PERFECT PROOF";
                case SHARDSONG -> "PERFECT CHORD";
                case VEILSTEP -> "PERFECT PASSAGE";
                case PARALLAX -> "PERFECT ALIGNMENT";
                case PRESSURE -> "PERFECT PRESSURE";
                case CTHULHUS_GAME -> "PERFECT EXAM";
                default -> "PERFECT";
            };
        }
    }
}
