package dev.marrowseal.wardbound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import dev.marrowseal.wardbound.DimSettings.FailAction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "wardbound.json";

    /**
     * Alpha config schema. A mismatched file is backed up and regenerated from
     * current defaults instead of being migrated through historical schemas.
     */
    public static final int CONFIG_VERSION = 28;

    /** Value below this uses lockChanceLow, at or above uses lockChanceHigh. */
    public static int lockThreshold = 30;
    public static float lockChanceLow = 0.95f;
    public static float lockChanceHigh = 0.95f;
    /** Flat multiplier applied on top of everything else. */
    public static float globalRollMultiplier = 1.0f;
    /** Ask for a second right click before starting the minigame. */
    public static boolean requireConfirmClick = false;
    /** Chests whose loot table scores below this are never locked. */
    /**
     * Containers scoring below this never ward at all, whatever the chance says.
     * Kept at 1 rather than 5: with the chance at 95% a floor of 5 was quietly
     * exempting a noticeable share of structure chests, so the mod felt less
     * frequent than the number in the config claimed it was.
     */
    public static int minValueToLock = 1;
    /** Work out item worth automatically from tags, tier, rarity and durability. */
    public static boolean autoItemValues = true;
    /** Nudges every automatic score up or down. 1.0 = as calculated. */
    public static float autoValueScale = 1.0f;
    /** Fewest pins/sockets a lockpick minigame will ever roll, even for a worthless chest. */
    public static int minPins = 2;
    /** Most pins/sockets a lockpick minigame can roll for the richest, hardest chests. */
    public static int maxPins = 8;
    /**
     * How much the interface is allowed to breathe. 0 freezes every idle animation, 1 is the old
     * full-strength throb. Anything that glows, swells or fades on its own runs through this.
     */
    public static float pulseIntensity = 0.35f;
    /**
     * Input forgiveness in milliseconds. A press is judged against the stretch of dial, bar or
     * sigil covered during this window rather than a single instant, which absorbs the gap between
     * the frame you reacted to and the moment the click arrived.
     */
    public static int inputGraceMs = 50;
    /** Global ward-clock curve before each game's time_scale is applied. */
    public static float wardClockBaseSeconds = 21f;
    public static float wardClockValueSeconds = 0.13f;
    public static float wardClockDifficultyCompensation = 0.35f;
    public static float wardClockMinimumSeconds = 9f;
    public static float heartWardSeconds = 5f;
    public static float hurriedTimeScale = 0.62f;
    /**
     * What is left of a chest when you lose the minigame and the dimension's on_fail is
     * reduce_loot or spawn_guard. 0.4 means roughly sixty percent of the chest's worth is taken,
     * and it is taken off the top: the best things go first.
     */
    public static float failLootMultiplier = 0.4f;
    /**
     * Each lock beaten without a loss in the same dimension adds this much to the reward, up to
     * {@link #streakBonusCap}. Losing one resets the run to zero.
     */
    public static float streakBonusPerLock = 0.08f;
    public static float streakBonusCap = 0.40f;
    /** And the locks get harder as the run goes on, by this much each, up to the cap. */
    public static float streakDifficultyPerLock = 0.05f;
    public static float streakDifficultyCap = 0.35f;
    /** Clean-win reward curve. Kept modest because modded loot tables are already rich. */
    public static float successLifeBonusMax = 0.35f;
    public static float successSkillBonusMax = 0.25f;
    /** Absolute ceiling after every reward source, including unsigned/gauntlet/bargains. */
    public static float maxLootMultiplier = 3.50f;

    // ---------------------------------------------------------------- clean / perfect work
    public static boolean cleanWinsEnabled = true;
    public static float cleanWinPerformanceThreshold = 0.88f;
    public static float perfectWinPerformanceThreshold = 0.97f;
    public static float cleanWinTimeRemaining = 0.10f;
    public static float perfectWinTimeRemaining = 0.20f;
    public static float scrapedWinLootScale = 0.96f;
    public static float cleanWinLootAdd = 0.03f;
    public static float perfectWinLootAdd = 0.05f;
    public static int cleanWinRegardBonus = 1;
    public static int perfectWinRegardBonus = 2;

    // ---------------------------------------------------------------- v38 adaptive ward tests
    /** Persistent player execution profile derived from resolved ordinary minigames. */
    public static boolean adaptiveSkillProfileEnabled = true;
    /** v39 mastery/value-gated deep rules inside established minigames. */
    public static boolean advancedMinigameVariantsEnabled = true;
    /** Global multiplier for natural expert-variant selection. */
    public static float advancedMinigameVariantChanceScale = 1.0f;
    /** Independent misinformation layer. It only lies through presentation, never puzzle state. */
    public static boolean deceptionLayerEnabled = true;
    public static int deceptionUnlockAfter = 160;
    public static float deceptionBaseChance = 0.025f;
    public static float deceptionMaxChance = 0.095f;
    /** Rare cross-discipline micro-rounds that temporarily freeze the host mechanism. */
    public static boolean hybridRoundsEnabled = true;
    public static int hybridUnlockAfter = 260;
    public static float hybridBaseChance = 0.010f;
    public static float hybridMaxChance = 0.042f;
    /** Small payoff for surviving the extra borrowed mechanism. */
    public static float hybridSuccessLootBonus = 0.04f;

    // ---------------------------------------------------------------- balance telemetry
    public static boolean telemetryEnabled = false;
    public static boolean telemetryLogFailures = true;
    public static boolean telemetryIncludeModifiers = true;

    // ---------------------------------------------------------------- accessibility (client presentation)
    public static boolean accessibilityReduceMotion = false;
    public static boolean accessibilityReduceFlashing = false;
    public static boolean accessibilityHighContrast = false;
    public static boolean accessibilityLargeMarkers = false;
    public static boolean accessibilityColorIndependentFeedback = true;
    public static boolean accessibilityDisableShake = false;
    public static float accessibilityGuiAnimationIntensity = 1.0f;
    public static float accessibilitySfxVolume = 1.0f;
    public static float parallaxMeterRevealThreshold = 0.68f;

    // ---------------------------------------------------------------- hands, debts and presentation
    /** A maker biases one existing quirk; it never adds another quirk. */
    public static boolean masterSignaturesEnabled = true;
    /** Chance the maker's preferred quirk occupies one of the existing slots. */
    public static float masterSignatureBiasChance = 0.78f;
    public static int masterSignatureRevealAfter = 5;
    public static int masterSignatureKnowAfter = 12;
    public static float knownHandBonusPerSeal = 0.02f;
    public static float knownHandBonusCap = 0.25f;

    /** Rare post-ward card choice. Immediate chest rewards are flat additions; some cards also bind future ward terms. */
    public static boolean forbiddenBargainsEnabled = true;
    public static float forbiddenBargainChance = 0.105f;
    public static int forbiddenBargainMaxOffers = 4;
    public static float bargainBorrowedBreathReward = 0.12f;
    public static float bargainIronDebtReward = 0.16f;
    public static float bargainWatchingMarkReward = 0.10f;
    /** Client-side card motion. Kept configurable for accessibility. */
    public static float bargainCardHoverLift = 10f;
    public static float bargainCardAnimSpeed = 12f;

    // ---------------------------------------------------------------- director / persistent presentation
    /** Recent games are de-weighted instead of hard banned, so a narrow pool still works. */
    public static boolean antiRepetitionEnabled = true;
    public static int antiRepetitionWindow = 4;
    /** Weight of the most recently played game; older entries climb toward 1. */
    public static float antiRepetitionRecentWeight = 0.20f;
    public static float antiRepetitionDecay = 0.25f;
    /** Short three-note house motifs when a signed ward opens. */
    public static boolean masterAudioMotifsEnabled = true;
    public static float masterAudioMotifVolume = 0.20f;
    /** Persistent visual wear from prior wins, losses and burned contents. */
    public static boolean wardHistoryScarsEnabled = true;
    public static int wardHistoryScarCap = 6;

    // ---------------------------------------------------------------- shared quirk/anomaly tuning
    public static boolean quirksEnabled = true;
    public static boolean anomaliesEnabled = true;
    public static int quirkOneThreshold = 32;
    public static int quirkTwoThreshold = 65;
    public static float quirkLowValueChance = 0.35f;
    public static float anomalyHurriedChance = 0.11f;
    public static float anomalyShroudedChance = 0.10f;
    public static float anomalyGutteringChance = 0.10f;
    public static float anomalyUnlitChance = 0.06f;
    public static float anomalySpentChance = 0.11f;
    public static int anomalyTwoThreshold = 60;
    public static float hurriedMinSeconds = 14f;
    public static float hurriedBaseSeconds = 46f;

    /**
     * Per-lock tuning. These multipliers sit around each lock's authored maths rather than
     * replacing it, so a config can make one game slower or more forgiving without turning its
     * internal rules into a second implementation. 1.0 preserves the authored behaviour.
     */
    public static final class GameTuning {
        public boolean enabled = true;
        public float timeScale = 1f;
        public float speedScale = 1f;
        public float difficultyScale = 1f;
        public float inputWindowScale = 1f;
        public float rewardScale = 1f;
    }

    public static final Map<MinigameType, GameTuning> MINIGAME_TUNING =
            new EnumMap<>(MinigameType.class);

    public static GameTuning tuning(MinigameType type) {
        return MINIGAME_TUNING.computeIfAbsent(type, t -> new GameTuning());
    }

    private static void resetMinigameTuning() {
        MINIGAME_TUNING.clear();
        for (MinigameType t : MinigameType.values()) MINIGAME_TUNING.put(t, new GameTuning());
    }

    /**
     * Chance a lost ward destroys most of what the container was holding, before
     * scaling by the ward's difficulty and the chest's worth. Losing has to be
     * able to cost you the chest, or it is only ever a delay.
     */
    /**
     * How far the beam may lean before the balance lock drops it. Below 1.0 the
     * kill zone starts where the red is actually drawn, instead of at the very
     * ends of the beam where nothing was reachable.
     */
    /**
     * Chance a ward turns out to carry no maker's mark. Rare on purpose: the
     * whole value of the thing is that you were not expecting it.
     */
    /** Seconds after a look in the vessel before you may commit. */
    public static float vesselSettle = 0.55f;

    /** Ticks between uses of Cthulhu's Eye. 3600 is three minutes. */
    public static int eyeCooldownTicks = 3600;
    /** Loot multiplier an End chest must beat before the Eye can appear at all. */
    public static float eyeLootThreshold = 1.45f;
    /** Savant cannot appear before the player has meaningfully entered late progression. */
    public static int eyeAfterBeaten = 700;
    /** Chance of it once both progression and multiplier gates are cleared. */
    public static float eyeDropChance = 0.10f;
    /** Chance the Cyclopean Lens whispers the hidden name behind a ward. */
    public static float lensTrueNameChance = 1f / 6f;
    /** Seconds a slow ember adds to a ward's clock. */
    public static int emberSeconds = 12;
    /** Multiplier a Nether chest must beat before one can appear. */
    public static float emberLootThreshold = 1.15f;
    public static int emberAfterBeaten = 180;
    public static float emberDropChance = 0.18f;

    public static float unsignedChance = 0.012f;
    /** How much likelier that is in the End. */
    public static float unsignedEndFactor = 7f;
    /** What one is worth, on top of everything else. */
    public static float unsignedLoot = 2.4f;

    /**
     * Unsigned seals to break before they start arriving as gauntlets: three
     * locks back to back on one container, one pool of lives, charms refused.
     * This is the last thing in the mod.
     */
    public static int gauntletAfter = 5;
    public static int gauntletStages = 3;
    /** On top of the unsigned bonus, which it also gets. */
    public static float gauntletLoot = 2.2f;

    /** Singular endgame ward built around Cthulhu's Game. */
    public static boolean cthulhuWardsEnabled = true;
    public static float cthulhuWardChance = 0.0010f;
    public static int cthulhuWardMinValue = 55;
    public static float cthulhuWardDifficulty = 1.16f;
    public static float cthulhuWardLoot = 1.22f;
    public static int cthulhuIdolCooldownSeconds = 45;
    public static float cthulhuIdolFreezeSeconds = 4f;

    /** Rare elite wards: chained mutated seals with premium reward. */
    public static boolean eldritchWardsEnabled = true;
    public static float eldritchWardChance = 0.01f;
    public static int eldritchWardStages = 3;
    public static float eldritchWardLoot = 1.60f;
    /** Rare one-lock corruption. Does not chain like an Eldritch Ward. */
    public static boolean possessedWardsEnabled = true;
    public static float possessedWardChance = 0.0035f;
    public static float possessedWardLoot = 1.18f;
    public static boolean wardAfflictionsEnabled = true;
    public static float wardAfflictionChance = 0.08f;
    public static boolean livingWardsEnabled = true;
    public static float livingWardChance = 0.22f;
    public static float livingWardLoot = 1.10f;

    // ---------------------------------------------------------------- onboarding / progression gates
    /** First few resolved wards stay readable before anomalies and elite forms enter the pool. */
    public static int afflictionAfterBeaten = 35;
    public static int possessedAfterBeaten = 160;
    public static int unsignedAfterBeaten = 260;
    public static int eldritchAfterBeaten = 650;
    public static int cthulhuAfterBeaten = 1200;
    /** Before this many ward wins, bonus loot may duplicate existing results but cannot fresh-reroll the table. */
    public static int bonusFreshRollAfterBeaten = 220;
    /** Long-form large-modpack curve: final reward multipliers are capped by resolved ward count. */
    public static boolean progressionLootCapsEnabled = true;
    /** Field/card progression gates. Extreme hands simply do not exist before these milestones. */
    /** Normal chest bargains stay fully locked during the opening onboarding band. */
    public static int normalCardsAfterBeaten = 15;
    public static int fieldCardAfterBeaten = 40;
    /** Eligible hostile kill chance to leave a physical Sealed Card. */
    public static float fieldCardDropChance = 0.0040f;
    public static int masterCardsAfterBeaten = 90;
    /** Objective-deck progression. Contracts are common tasks; Rituals are slower; Covenants are darker and rarer. */
    public static int contractCardsAfterBeaten = 110;
    public static int ritualCardsAfterBeaten = 240;
    public static int covenantCardsAfterBeaten = 430;
    public static int curseCardsAfterBeaten = 180;
    public static int epicCardsAfterBeaten = 340;
    public static int uniqueCardsAfterBeaten = 600;
    public static int deathCardsAfterBeaten = 900;
    public static float mercyChance = 0.10f;
    public static int mercyAfterLosses = 3;
    public static float mercyLoot = 0.92f;
    public static float perfectRelicChance = 0.04f;
    public static float eldritchRelicChance = 0.35f;
    /** Rival houses react when you become practiced at the other's work. */
    public static boolean masterRivalriesEnabled = true;
    public static int masterRivalryAfter = 30;
    public static float masterRivalryDifficulty = 1.06f;
    public static float masterRivalryLoot = 1.08f;
    /** Persistent scar tiers also make the mechanism a little less tame. */
    public static float wardMutationDifficultyPerLevel = 0.025f;
    /** Physical mutation requires both a sufficiently scarred chest and enough global ward experience. */
    public static int mutationTier1Age = 8;
    public static int mutationTier2Age = 15;
    public static int mutationTier3Age = 24;
    public static int mutationTier1AfterBeaten = 240;
    public static int mutationTier2AfterBeaten = 520;
    public static int mutationTier3AfterBeaten = 850;
    public static boolean postWinResidueEnabled = true;
    /** Older, scarred wards pay slightly better. */
    public static float oldWardLootPerScar = 0.04f;
    public static float oldWardLootCap = 1.24f;

    // ---------------------------------------------------------------- the attention
    /**
     * After a gauntlet, something starts paying attention.
     *
     * <p>There is no boss and no arena. While this is running, containers you
     * have already emptied seal themselves again, everything that seals is
     * unsigned, and winning summons a horde as often as losing does. It is a
     * health bar you bring down by playing the mod, except the bar is in your
     * own base.
     */
    public static boolean attentionEnabled = true;
    /** Seals to break before it lets go. */
    public static int attentionStart = 14;
    /** How much a loss adds back, and how far it can climb. */
    public static int attentionPerLoss = 2;
    public static int attentionCap = 30;
    /** Chance an already-emptied container re-seals when you touch it. */
    public static float attentionResealChance = 0.34f;
    /** Chance beating one of its seals still sends something. */
    public static float attentionHordeChance = 0.38f;

    /** Chance a guardian roll ignores the curated pool and takes any eligible hostile. */
    public static float guardianWildChance = 0.35f;
    /** How many turn up when it sends a horde. */
    public static int hordeSize = 7;

    /** Whether warded containers hum when you are near them. */
    // ---------------------------------------------------------------- chains
    /**
     * Chains: several seals by one house, put in different places, with each
     * one pointing roughly at the next.
     *
     * <p>Nothing is generated for these. The mod decides whether a container is
     * warded when it is touched, so the trail is kept by whatever happens to be
     * standing where the bearing sent you.
     */
    /** What a sealwright's own key is worth on its own house's seals. */
    public static int wrightBonusLives = 2;
    public static float wrightLootBonus = 1.35f;

    public static boolean chainEnabled = true;
    /** Resolved wards required before a seal chain may begin. */
    public static int chainAfterBeaten = 70;
    /** Chance a beaten ward turns out to be the first of a set. */
    public static float chainChance = 0.07f;
    public static int chainMinLinks = 3;
    public static int chainMaxLinks = 5;
    /** How far apart the links are put, in blocks. */
    public static int chainMinDistance = 300;
    public static int chainMaxDistance = 900;
    /** How loose the bearing is. Wide on purpose: a hunt, not a search. */
    public static int chainRadius = 90;
    public static float chainDifficultyPerLink = 0.10f;
    public static float chainLootPerLink = 0.30f;

    public static boolean ambienceEnabled = true;
    /** How loud, before distance falloff. */
    public static float ambienceVolume = 0.34f;
    public static int ambiencePeriodTicks = 80;
    public static float ambienceRange = 13f;

    public static float balanceEdge = 0.86f;

    public static float failBurnChance = 0.30f;
    /** How little can survive when it does happen, and how much. */
    public static float failBurnFloor = 0.05f;
    public static float failBurnCeiling = 0.45f;

    // ---------------------------------------------------------------- grudges
    /**
     * A container that beats you closes tighter for your next attempt, and holds
     * proportionally more. Without this a lost lock costs only the walk back.
     */
    public static float spiteDifficulty = 0.14f;
    public static float spiteLoot = 0.22f;
    /** How far one container can escalate. */
    public static int spiteMax = 4;

    // ---------------------------------------------------------------- the long curve
    /** Seals per tier. Set to 0 to switch the whole progression off. */
    public static int tierStep = 120;
    /** Ceiling, so a very long save does not run away from itself. */
    public static int tierMax = 12;
    public static float tierDifficultyStep = 0.035f;
    /** Bigger than the difficulty step: later wards are harder and better paid. */
    public static float tierLootStep = 0.025f;

    // ---------------------------------------------------------------- the watcher
    /**
     * Whether one sealmaker's work gradually starts turning up more often for a
     * given player. Costs nothing and changes no numbers; it is entirely a thing
     * you notice or do not.
     */
    public static boolean watcherEnabled = true;
    /** Ceiling on how often that happens. Never anywhere near certain. */
    public static float watcherMaxChance = 0.30f;
    /** Seals broken before a hand starts turning up at all. */
    public static int watcherAfter = 35;
    /** How fast its frequency climbs per seal after that. */
    public static float watcherRamp = 0.005f;

    /** Seals of the watching house you must break before it forms a verdict. */
    public static int verdictAfter = 30;
    /** Regard at or above this earns favour; below it earns contempt. */
    public static int verdictThreshold = 58;
    /** What contempt does to that house's seals. Harder, and worth it. */
    public static float contemptDifficulty = 1.28f;
    public static float contemptLoot = 1.45f;

    // ---------------------------------------------------------------- guardians
    /**
     * Chance that losing a lock wakes something up, on top of whatever the
     * dimension's on_fail already does.
     *
     * <p>Not certain, because a container that always fights back is a tax and a
     * container that sometimes does is a scare.
     */
    public static float guardianChance = 0.40f;
    /** Most that can appear at once, scaled down by how poor the container was. */
    public static int guardianMaxCount = 3;
    /**
     * How long, in seconds, a beaten container sulks before it will open at all.
     * Gives the fight somewhere to happen instead of letting the player scoop
     * the loot and run.
     */
    public static int guardianSealSeconds = 25;
    /** Keep progression-specific guardian mobs in their native dimension. */
    public static boolean guardianDimensionLock = true;
    /** Require the player to have visited Nether/End before those guardian tiers unlock. */
    public static boolean guardianProgressionLock = true;

    /** Per dimension spawn pools, by entity id. Empty means use the stock pool. */
    public static final Map<String, List<String>> GUARDIAN_POOLS = new LinkedHashMap<>();
    /** Per dimension bans. An id here is never spawned even if a pool names it. */
    public static final Map<String, List<String>> GUARDIAN_BLACKLIST = new LinkedHashMap<>();

    public static List<String> guardianPool(String dimId) {
        List<String> configured = GUARDIAN_POOLS.get(dimId);
        if (configured != null && !configured.isEmpty()) return configured;
        return Guardians.defaultPool(dimId);
    }

    public static List<String> guardianBlacklist(String dimId) {
        List<String> banned = GUARDIAN_BLACKLIST.get(dimId);
        return banned == null ? List.of() : banned;
    }

    // ---------------------------------------------------------------- the offer
    /**
     * Whether beating a lock offers to re-seal the container instead of just
     * opening it. This is the only decision the player ever makes in this mod,
     * so it is on by default.
     */
    public static boolean temptEnabled = true;
    /**
     * Chance a beaten container offers to be wound tighter at all.
     *
     * <p>Well under half on purpose. An offer that arrives after every single
     * lock stops being a decision and becomes a prompt you dismiss, and the
     * moment it is routine it has lost the only thing it was for.
     */
    public static float temptChance = 0.35f;
    /** Loot multiplier per re-seal, compounding. */
    public static float temptMultiplier = 1.55f;
    /** How many times one container may be wound tighter. */
    public static int temptMaxDepth = 2;
    /** Difficulty added per re-seal. Greed has to cost more than exposure. */
    public static float temptDifficultyPerDepth = 0.22f;

    // ---------------------------------------------------------------- charms
    /**
     * Extra mistakes the rusted key buys on the lock it binds itself to. The key
     * deliberately does nothing to the loot: it buys patience, not money.
     */
    public static int keyBonusLives = 1;
    /** Extra mistakes the ichorheart buys. */
    public static int heartBonusLives = 1;
    /**
     * What the ichorheart does to the chest's final multiplier. 1.25 = a quarter
     * again on top of everything else the run and the performance earned.
     */
    public static float heartLootBonus = 1.25f;
    /**
     * Chance that a chest opened by beating its lock also gives up an old key.
     * This is the only way either charm turns up without a crafting table, and
     * it is deliberately the reward for winning rather than for looting.
     */
    public static float keyDropChance = 0.10f;
    /**
     * Chance of a heart, scaled by how rich the chest was. A worthless chest
     * effectively never drops one; a jackpot chest is the only realistic source.
     */
    public static float heartDropChance = 0.03f;
    /**
     * Chance of a lost-age relic out of a beaten seal, scaled by chest worth.
     * These are not craftable, so this is the only tap.
     */
    public static float relicDropChance = 0.05f;

    public static final Map<String, DimSettings> DIMENSIONS = new LinkedHashMap<>();
    public static DimSettings fallback =
            new DimSettings(MinigameType.RUNES, 1.1f, 1.15f, 3, FailAction.REDUCE_LOOT);

    static {
        fallback.randomGame = true;
        resetMinigameTuning();
    }

    public static Map<String, Integer> itemValues = new LinkedHashMap<>();

    private WardConfig() {
    }

    public static DimSettings forDimension(String dimensionId) {
        DimSettings s = DIMENSIONS.get(dimensionId);
        return s != null ? s : fallback;
    }

    // ------------------------------------------------------------------ defaults

    /**
     * A dimension that runs several named locks rather than one. The first entry
     * is also the dimension's primary minigame for UI/config presentation.
     */
    private static DimSettings pooled(float difficulty, float loot, int lives,
                                      FailAction fail, MinigameType... locks) {
        DimSettings s = new DimSettings(locks[0], difficulty, loot, lives, fail);
        s.pool = locks;
        return s;
    }

    private static void buildDefaultDimensions() {
        DIMENSIONS.clear();
        // The overworld rolls its game from the chest's own seed rather than
        // running one lock forever. Pinning it to a single game was how the
        // first eleven locks ended up unreachable for anyone who never left home.
        // Each dimension gets its own handful rather than the whole set, so the
        // three worlds do not all feel the same. The overworld keeps the ones
        // that teach the mod, the Nether the ones about pressure and heat, the
        // End the ones about not being able to see or think straight.
        DIMENSIONS.put("minecraft:overworld", pooled(1.0f, 1.0f, 3, FailAction.REDUCE_LOOT,
                MinigameType.KEYWAY, MinigameType.DRUM, MinigameType.TIMING,
                MinigameType.RUNES, MinigameType.CIPHER, MinigameType.MIRROR,
                MinigameType.ROOTWAY, MinigameType.RUNEBRAID, MinigameType.VEILSTEP,
                MinigameType.PROCESSION));
        DIMENSIONS.put("minecraft:the_nether", pooled(1.25f, 1.35f, 3, FailAction.EXPLODE,
                MinigameType.PRESSURE, MinigameType.TIMING, MinigameType.PULSE,
                MinigameType.BALANCE, MinigameType.YOKE, MinigameType.GYRE,
                MinigameType.BLACK_MEASURE));
        DIMENSIONS.put("minecraft:the_end", pooled(1.55f, 1.9f, 3, FailAction.SPAWN_GUARD,
                MinigameType.VESSEL, MinigameType.AUGURY, MinigameType.CONSTELLATION,
                MinigameType.MEMORY, MinigameType.RESONANCE, MinigameType.KEYWAY,
                MinigameType.LATTICE, MinigameType.PARALLAX, MinigameType.SHARDSONG,
                MinigameType.EPITAPH, MinigameType.ORRERY, MinigameType.PROCESSION));
        // Every dimension that is not listed above rolls its game from the chest seed, so a pack
        // with modded dimensions sees all locks rather than the same one forever.
        fallback = new DimSettings(MinigameType.RUNES, 1.15f, 1.2f, 3, FailAction.REDUCE_LOOT);
        fallback.randomGame = true;
    }

    /**
     * Only the handful of items the automatic scorer cannot judge on its own,
     * because nothing about the item itself says how special it is.
     * Everything else is worked out at runtime by ItemValueResolver.
     */
    private static Map<String, Integer> buildDefaultItemValues() {
        LinkedHashMap<String, Integer> d = new LinkedHashMap<>();
        d.put("minecraft:enchanted_golden_apple", 40);
        d.put("minecraft:golden_apple", 18);
        d.put("minecraft:heart_of_the_sea", 35);
        d.put("minecraft:nautilus_shell", 20);
        d.put("minecraft:name_tag", 10);
        d.put("minecraft:saddle", 10);
        d.put("minecraft:experience_bottle", 12);
        d.put("minecraft:golden_carrot", 8);
        d.put("minecraft:bread", 1);
        d.put("minecraft:rotten_flesh", 1);
        d.put("minecraft:string", 1);
        d.put("minecraft:bone", 1);
        d.put("minecraft:gunpowder", 2);
        d.put("minecraft:wheat", 1);
        d.put("minecraft:paper", 1);
        return d;
    }

    // ------------------------------------------------------------------ io

    public static void load() {
        buildDefaultDimensions();
        resetMinigameTuning();
        itemValues = buildDefaultItemValues();

        dev.marrowseal.wardbound.boss.BossConfig.reset();
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (!Files.exists(file)) {
            save(file);
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

            int loadedVersion = root.has("config_version") ? root.get("config_version").getAsInt() : 0;
            if (loadedVersion != CONFIG_VERSION) {
                Path backup = file.resolveSibling("wardbound.pre-v" + CONFIG_VERSION + ".json");
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                Wardbound.LOG.warn("[Wardbound] alpha config schema {} != {}; backed up old config to {} and generated clean defaults",
                        loadedVersion, CONFIG_VERSION, backup.getFileName());
                save(file);
                return;
            }

            dev.marrowseal.wardbound.boss.BossConfig.read(root);
            if (!root.has("bosses")) {
                dev.marrowseal.wardbound.boss.BossConfig.write(root);
                Files.writeString(file, GSON.toJson(root));
            }

            if (root.has("lock_threshold")) lockThreshold = root.get("lock_threshold").getAsInt();
            if (root.has("lock_chance_low")) lockChanceLow = root.get("lock_chance_low").getAsFloat();
            if (root.has("lock_chance_high")) lockChanceHigh = root.get("lock_chance_high").getAsFloat();
            if (root.has("global_roll_multiplier")) globalRollMultiplier = root.get("global_roll_multiplier").getAsFloat();
            if (root.has("require_confirm_click")) requireConfirmClick = root.get("require_confirm_click").getAsBoolean();
            if (root.has("min_value_to_lock")) minValueToLock = root.get("min_value_to_lock").getAsInt();
            if (root.has("auto_item_values")) autoItemValues = root.get("auto_item_values").getAsBoolean();
            if (root.has("auto_value_scale")) autoValueScale = root.get("auto_value_scale").getAsFloat();
            if (root.has("min_pins")) minPins = root.get("min_pins").getAsInt();
            if (root.has("max_pins")) maxPins = root.get("max_pins").getAsInt();
            if (root.has("pulse_intensity")) pulseIntensity = root.get("pulse_intensity").getAsFloat();
            if (root.has("input_grace_ms")) inputGraceMs = root.get("input_grace_ms").getAsInt();
            if (root.has("ward_clock_base_seconds")) wardClockBaseSeconds = root.get("ward_clock_base_seconds").getAsFloat();
            if (root.has("ward_clock_value_seconds")) wardClockValueSeconds = root.get("ward_clock_value_seconds").getAsFloat();
            if (root.has("ward_clock_difficulty_compensation")) wardClockDifficultyCompensation = root.get("ward_clock_difficulty_compensation").getAsFloat();
            if (root.has("ward_clock_minimum_seconds")) wardClockMinimumSeconds = root.get("ward_clock_minimum_seconds").getAsFloat();
            if (root.has("heart_ward_seconds")) heartWardSeconds = root.get("heart_ward_seconds").getAsFloat();
            if (root.has("hurried_time_scale")) hurriedTimeScale = root.get("hurried_time_scale").getAsFloat();
            if (root.has("fail_loot_multiplier")) failLootMultiplier = root.get("fail_loot_multiplier").getAsFloat();
            if (root.has("streak_bonus_per_lock")) streakBonusPerLock = root.get("streak_bonus_per_lock").getAsFloat();
            if (root.has("streak_bonus_cap")) streakBonusCap = root.get("streak_bonus_cap").getAsFloat();
            if (root.has("streak_difficulty_per_lock")) streakDifficultyPerLock = root.get("streak_difficulty_per_lock").getAsFloat();
            if (root.has("streak_difficulty_cap")) streakDifficultyCap = root.get("streak_difficulty_cap").getAsFloat();
            if (root.has("success_life_bonus_max")) successLifeBonusMax = root.get("success_life_bonus_max").getAsFloat();
            if (root.has("success_skill_bonus_max")) successSkillBonusMax = root.get("success_skill_bonus_max").getAsFloat();
            if (root.has("max_loot_multiplier")) maxLootMultiplier = root.get("max_loot_multiplier").getAsFloat();
            if (root.has("clean_wins_enabled")) cleanWinsEnabled = root.get("clean_wins_enabled").getAsBoolean();
            if (root.has("clean_win_performance_threshold")) cleanWinPerformanceThreshold = root.get("clean_win_performance_threshold").getAsFloat();
            if (root.has("perfect_win_performance_threshold")) perfectWinPerformanceThreshold = root.get("perfect_win_performance_threshold").getAsFloat();
            if (root.has("clean_win_time_remaining")) cleanWinTimeRemaining = root.get("clean_win_time_remaining").getAsFloat();
            if (root.has("perfect_win_time_remaining")) perfectWinTimeRemaining = root.get("perfect_win_time_remaining").getAsFloat();
            if (root.has("scraped_win_loot_scale")) scrapedWinLootScale = root.get("scraped_win_loot_scale").getAsFloat();
            if (root.has("clean_win_loot_add")) cleanWinLootAdd = root.get("clean_win_loot_add").getAsFloat();
            if (root.has("perfect_win_loot_add")) perfectWinLootAdd = root.get("perfect_win_loot_add").getAsFloat();
            if (root.has("clean_win_regard_bonus")) cleanWinRegardBonus = root.get("clean_win_regard_bonus").getAsInt();
            if (root.has("perfect_win_regard_bonus")) perfectWinRegardBonus = root.get("perfect_win_regard_bonus").getAsInt();
            if (root.has("adaptive_skill_profile_enabled")) adaptiveSkillProfileEnabled = root.get("adaptive_skill_profile_enabled").getAsBoolean();
            if (root.has("advanced_minigame_variants_enabled")) advancedMinigameVariantsEnabled = root.get("advanced_minigame_variants_enabled").getAsBoolean();
            if (root.has("advanced_minigame_variant_chance_scale")) advancedMinigameVariantChanceScale = root.get("advanced_minigame_variant_chance_scale").getAsFloat();
            if (root.has("deception_layer_enabled")) deceptionLayerEnabled = root.get("deception_layer_enabled").getAsBoolean();
            if (root.has("deception_unlock_after")) deceptionUnlockAfter = root.get("deception_unlock_after").getAsInt();
            if (root.has("deception_base_chance")) deceptionBaseChance = root.get("deception_base_chance").getAsFloat();
            if (root.has("deception_max_chance")) deceptionMaxChance = root.get("deception_max_chance").getAsFloat();
            if (root.has("hybrid_rounds_enabled")) hybridRoundsEnabled = root.get("hybrid_rounds_enabled").getAsBoolean();
            if (root.has("hybrid_unlock_after")) hybridUnlockAfter = root.get("hybrid_unlock_after").getAsInt();
            if (root.has("hybrid_base_chance")) hybridBaseChance = root.get("hybrid_base_chance").getAsFloat();
            if (root.has("hybrid_max_chance")) hybridMaxChance = root.get("hybrid_max_chance").getAsFloat();
            if (root.has("hybrid_success_loot_bonus")) hybridSuccessLootBonus = root.get("hybrid_success_loot_bonus").getAsFloat();
            if (root.has("telemetry_enabled")) telemetryEnabled = root.get("telemetry_enabled").getAsBoolean();
            if (root.has("telemetry_log_failures")) telemetryLogFailures = root.get("telemetry_log_failures").getAsBoolean();
            if (root.has("telemetry_include_modifiers")) telemetryIncludeModifiers = root.get("telemetry_include_modifiers").getAsBoolean();
            if (root.has("accessibility_reduce_motion")) accessibilityReduceMotion = root.get("accessibility_reduce_motion").getAsBoolean();
            if (root.has("accessibility_reduce_flashing")) accessibilityReduceFlashing = root.get("accessibility_reduce_flashing").getAsBoolean();
            if (root.has("accessibility_high_contrast")) accessibilityHighContrast = root.get("accessibility_high_contrast").getAsBoolean();
            if (root.has("accessibility_large_markers")) accessibilityLargeMarkers = root.get("accessibility_large_markers").getAsBoolean();
            if (root.has("accessibility_color_independent_feedback")) accessibilityColorIndependentFeedback = root.get("accessibility_color_independent_feedback").getAsBoolean();
            if (root.has("accessibility_disable_shake")) accessibilityDisableShake = root.get("accessibility_disable_shake").getAsBoolean();
            if (root.has("accessibility_gui_animation_intensity")) accessibilityGuiAnimationIntensity = root.get("accessibility_gui_animation_intensity").getAsFloat();
            if (root.has("accessibility_sfx_volume")) accessibilitySfxVolume = root.get("accessibility_sfx_volume").getAsFloat();
            if (root.has("parallax_meter_reveal_threshold")) parallaxMeterRevealThreshold = root.get("parallax_meter_reveal_threshold").getAsFloat();
            if (root.has("master_signatures_enabled")) masterSignaturesEnabled = root.get("master_signatures_enabled").getAsBoolean();
            if (root.has("master_signature_bias_chance")) masterSignatureBiasChance = root.get("master_signature_bias_chance").getAsFloat();
            if (root.has("master_signature_reveal_after")) masterSignatureRevealAfter = root.get("master_signature_reveal_after").getAsInt();
            if (root.has("master_signature_know_after")) masterSignatureKnowAfter = root.get("master_signature_know_after").getAsInt();
            if (root.has("known_hand_bonus_per_seal")) knownHandBonusPerSeal = root.get("known_hand_bonus_per_seal").getAsFloat();
            if (root.has("known_hand_bonus_cap")) knownHandBonusCap = root.get("known_hand_bonus_cap").getAsFloat();
            if (root.has("forbidden_bargains_enabled")) forbiddenBargainsEnabled = root.get("forbidden_bargains_enabled").getAsBoolean();
            if (root.has("forbidden_bargain_chance")) forbiddenBargainChance = root.get("forbidden_bargain_chance").getAsFloat();
            if (root.has("forbidden_bargain_max_offers")) forbiddenBargainMaxOffers = root.get("forbidden_bargain_max_offers").getAsInt();
            if (root.has("bargain_borrowed_breath_reward")) bargainBorrowedBreathReward = root.get("bargain_borrowed_breath_reward").getAsFloat();
            if (root.has("bargain_iron_debt_reward")) bargainIronDebtReward = root.get("bargain_iron_debt_reward").getAsFloat();
            if (root.has("bargain_watching_mark_reward")) bargainWatchingMarkReward = root.get("bargain_watching_mark_reward").getAsFloat();
            if (root.has("bargain_card_hover_lift")) bargainCardHoverLift = root.get("bargain_card_hover_lift").getAsFloat();
            if (root.has("bargain_card_anim_speed")) bargainCardAnimSpeed = root.get("bargain_card_anim_speed").getAsFloat();
            if (root.has("anti_repetition_enabled")) antiRepetitionEnabled = root.get("anti_repetition_enabled").getAsBoolean();
            if (root.has("anti_repetition_window")) antiRepetitionWindow = root.get("anti_repetition_window").getAsInt();
            if (root.has("anti_repetition_recent_weight")) antiRepetitionRecentWeight = root.get("anti_repetition_recent_weight").getAsFloat();
            if (root.has("anti_repetition_decay")) antiRepetitionDecay = root.get("anti_repetition_decay").getAsFloat();
            if (root.has("master_audio_motifs_enabled")) masterAudioMotifsEnabled = root.get("master_audio_motifs_enabled").getAsBoolean();
            if (root.has("master_audio_motif_volume")) masterAudioMotifVolume = root.get("master_audio_motif_volume").getAsFloat();
            if (root.has("ward_history_scars_enabled")) wardHistoryScarsEnabled = root.get("ward_history_scars_enabled").getAsBoolean();
            if (root.has("ward_history_scar_cap")) wardHistoryScarCap = root.get("ward_history_scar_cap").getAsInt();
            if (root.has("quirks_enabled")) quirksEnabled = root.get("quirks_enabled").getAsBoolean();
            if (root.has("anomalies_enabled")) anomaliesEnabled = root.get("anomalies_enabled").getAsBoolean();
            if (root.has("quirk_one_threshold")) quirkOneThreshold = root.get("quirk_one_threshold").getAsInt();
            if (root.has("quirk_two_threshold")) quirkTwoThreshold = root.get("quirk_two_threshold").getAsInt();
            if (root.has("quirk_low_value_chance")) quirkLowValueChance = root.get("quirk_low_value_chance").getAsFloat();
            if (root.has("anomaly_hurried_chance")) anomalyHurriedChance = root.get("anomaly_hurried_chance").getAsFloat();
            if (root.has("anomaly_shrouded_chance")) anomalyShroudedChance = root.get("anomaly_shrouded_chance").getAsFloat();
            if (root.has("anomaly_guttering_chance")) anomalyGutteringChance = root.get("anomaly_guttering_chance").getAsFloat();
            if (root.has("anomaly_unlit_chance")) anomalyUnlitChance = root.get("anomaly_unlit_chance").getAsFloat();
            if (root.has("anomaly_spent_chance")) anomalySpentChance = root.get("anomaly_spent_chance").getAsFloat();
            if (root.has("anomaly_two_threshold")) anomalyTwoThreshold = root.get("anomaly_two_threshold").getAsInt();
            if (root.has("hurried_min_seconds")) hurriedMinSeconds = root.get("hurried_min_seconds").getAsFloat();
            if (root.has("hurried_base_seconds")) hurriedBaseSeconds = root.get("hurried_base_seconds").getAsFloat();
            if (root.has("tempt_enabled")) temptEnabled = root.get("tempt_enabled").getAsBoolean();
            if (root.has("eye_cooldown_ticks")) eyeCooldownTicks = root.get("eye_cooldown_ticks").getAsInt();
            if (root.has("eye_loot_threshold")) eyeLootThreshold = root.get("eye_loot_threshold").getAsFloat();
            if (root.has("eye_after_beaten")) eyeAfterBeaten = root.get("eye_after_beaten").getAsInt();
            if (root.has("eye_drop_chance")) eyeDropChance = root.get("eye_drop_chance").getAsFloat();
            if (root.has("lens_true_name_chance")) lensTrueNameChance = root.get("lens_true_name_chance").getAsFloat();
            if (root.has("ember_seconds")) emberSeconds = root.get("ember_seconds").getAsInt();
            if (root.has("ember_loot_threshold")) emberLootThreshold = root.get("ember_loot_threshold").getAsFloat();
            if (root.has("ember_after_beaten")) emberAfterBeaten = root.get("ember_after_beaten").getAsInt();
            if (root.has("ember_drop_chance")) emberDropChance = root.get("ember_drop_chance").getAsFloat();
            if (root.has("vessel_settle")) vesselSettle = root.get("vessel_settle").getAsFloat();
            if (root.has("unsigned_chance")) unsignedChance = root.get("unsigned_chance").getAsFloat();
            if (root.has("unsigned_end_factor")) unsignedEndFactor = root.get("unsigned_end_factor").getAsFloat();
            if (root.has("unsigned_loot")) unsignedLoot = root.get("unsigned_loot").getAsFloat();
            if (root.has("attention_enabled")) attentionEnabled = root.get("attention_enabled").getAsBoolean();
            if (root.has("attention_start")) attentionStart = root.get("attention_start").getAsInt();
            if (root.has("attention_per_loss")) attentionPerLoss = root.get("attention_per_loss").getAsInt();
            if (root.has("attention_cap")) attentionCap = root.get("attention_cap").getAsInt();
            if (root.has("attention_reseal_chance")) attentionResealChance = root.get("attention_reseal_chance").getAsFloat();
            if (root.has("attention_horde_chance")) attentionHordeChance = root.get("attention_horde_chance").getAsFloat();
            if (root.has("guardian_wild_chance")) guardianWildChance = root.get("guardian_wild_chance").getAsFloat();
            if (root.has("horde_size")) hordeSize = root.get("horde_size").getAsInt();
            if (root.has("gauntlet_after")) gauntletAfter = root.get("gauntlet_after").getAsInt();
            if (root.has("gauntlet_stages")) gauntletStages = root.get("gauntlet_stages").getAsInt();
            if (root.has("gauntlet_loot")) gauntletLoot = root.get("gauntlet_loot").getAsFloat();
            if (root.has("eldritch_wards_enabled")) eldritchWardsEnabled = root.get("eldritch_wards_enabled").getAsBoolean();
            if (root.has("cthulhu_wards_enabled")) cthulhuWardsEnabled = root.get("cthulhu_wards_enabled").getAsBoolean();
            if (root.has("cthulhu_ward_chance")) cthulhuWardChance = root.get("cthulhu_ward_chance").getAsFloat();
            if (root.has("cthulhu_ward_min_value")) cthulhuWardMinValue = root.get("cthulhu_ward_min_value").getAsInt();
            if (root.has("cthulhu_ward_difficulty")) cthulhuWardDifficulty = root.get("cthulhu_ward_difficulty").getAsFloat();
            if (root.has("cthulhu_ward_loot")) cthulhuWardLoot = root.get("cthulhu_ward_loot").getAsFloat();
            if (root.has("cthulhu_idol_cooldown_seconds")) cthulhuIdolCooldownSeconds = root.get("cthulhu_idol_cooldown_seconds").getAsInt();
            if (root.has("cthulhu_idol_freeze_seconds")) cthulhuIdolFreezeSeconds = root.get("cthulhu_idol_freeze_seconds").getAsFloat();
            if (root.has("eldritch_ward_chance")) eldritchWardChance = root.get("eldritch_ward_chance").getAsFloat();
            if (root.has("eldritch_ward_stages")) eldritchWardStages = root.get("eldritch_ward_stages").getAsInt();
            if (root.has("eldritch_ward_loot")) eldritchWardLoot = root.get("eldritch_ward_loot").getAsFloat();
            if (root.has("possessed_wards_enabled")) possessedWardsEnabled = root.get("possessed_wards_enabled").getAsBoolean();
            if (root.has("possessed_ward_chance")) possessedWardChance = root.get("possessed_ward_chance").getAsFloat();
            if (root.has("possessed_ward_loot")) possessedWardLoot = root.get("possessed_ward_loot").getAsFloat();
            if (root.has("ward_afflictions_enabled")) wardAfflictionsEnabled = root.get("ward_afflictions_enabled").getAsBoolean();
            if (root.has("ward_affliction_chance")) wardAfflictionChance = root.get("ward_affliction_chance").getAsFloat();
            if (root.has("living_wards_enabled")) livingWardsEnabled = root.get("living_wards_enabled").getAsBoolean();
            if (root.has("living_ward_chance")) livingWardChance = root.get("living_ward_chance").getAsFloat();
            if (root.has("living_ward_loot")) livingWardLoot = root.get("living_ward_loot").getAsFloat();
            if (root.has("affliction_after_beaten")) afflictionAfterBeaten = root.get("affliction_after_beaten").getAsInt();
            if (root.has("possessed_after_beaten")) possessedAfterBeaten = root.get("possessed_after_beaten").getAsInt();
            if (root.has("unsigned_after_beaten")) unsignedAfterBeaten = root.get("unsigned_after_beaten").getAsInt();
            if (root.has("eldritch_after_beaten")) eldritchAfterBeaten = root.get("eldritch_after_beaten").getAsInt();
            if (root.has("cthulhu_after_beaten")) cthulhuAfterBeaten = root.get("cthulhu_after_beaten").getAsInt();
            if (root.has("bonus_fresh_roll_after_beaten")) bonusFreshRollAfterBeaten = root.get("bonus_fresh_roll_after_beaten").getAsInt();
            if (root.has("progression_loot_caps_enabled")) progressionLootCapsEnabled = root.get("progression_loot_caps_enabled").getAsBoolean();
            if (root.has("normal_cards_after_beaten")) normalCardsAfterBeaten = root.get("normal_cards_after_beaten").getAsInt();
            if (root.has("field_card_after_beaten")) fieldCardAfterBeaten = root.get("field_card_after_beaten").getAsInt();
            if (root.has("field_card_drop_chance")) fieldCardDropChance = root.get("field_card_drop_chance").getAsFloat();
            if (root.has("master_cards_after_beaten")) masterCardsAfterBeaten = root.get("master_cards_after_beaten").getAsInt();
            if (root.has("contract_cards_after_beaten")) contractCardsAfterBeaten = root.get("contract_cards_after_beaten").getAsInt();
            if (root.has("ritual_cards_after_beaten")) ritualCardsAfterBeaten = root.get("ritual_cards_after_beaten").getAsInt();
            if (root.has("covenant_cards_after_beaten")) covenantCardsAfterBeaten = root.get("covenant_cards_after_beaten").getAsInt();
            if (root.has("curse_cards_after_beaten")) curseCardsAfterBeaten = root.get("curse_cards_after_beaten").getAsInt();
            if (root.has("epic_cards_after_beaten")) epicCardsAfterBeaten = root.get("epic_cards_after_beaten").getAsInt();
            if (root.has("unique_cards_after_beaten")) uniqueCardsAfterBeaten = root.get("unique_cards_after_beaten").getAsInt();
            if (root.has("death_cards_after_beaten")) deathCardsAfterBeaten = root.get("death_cards_after_beaten").getAsInt();
            if (root.has("mercy_chance")) mercyChance = root.get("mercy_chance").getAsFloat();
            if (root.has("mercy_after_losses")) mercyAfterLosses = root.get("mercy_after_losses").getAsInt();
            if (root.has("mercy_loot")) mercyLoot = root.get("mercy_loot").getAsFloat();
            if (root.has("perfect_relic_chance")) perfectRelicChance = root.get("perfect_relic_chance").getAsFloat();
            if (root.has("eldritch_relic_chance")) eldritchRelicChance = root.get("eldritch_relic_chance").getAsFloat();
            if (root.has("master_rivalries_enabled")) masterRivalriesEnabled = root.get("master_rivalries_enabled").getAsBoolean();
            if (root.has("master_rivalry_after")) masterRivalryAfter = root.get("master_rivalry_after").getAsInt();
            if (root.has("master_rivalry_difficulty")) masterRivalryDifficulty = root.get("master_rivalry_difficulty").getAsFloat();
            if (root.has("master_rivalry_loot")) masterRivalryLoot = root.get("master_rivalry_loot").getAsFloat();
            if (root.has("ward_mutation_difficulty_per_level")) wardMutationDifficultyPerLevel = root.get("ward_mutation_difficulty_per_level").getAsFloat();
            if (root.has("mutation_tier_1_age")) mutationTier1Age = root.get("mutation_tier_1_age").getAsInt();
            if (root.has("mutation_tier_2_age")) mutationTier2Age = root.get("mutation_tier_2_age").getAsInt();
            if (root.has("mutation_tier_3_age")) mutationTier3Age = root.get("mutation_tier_3_age").getAsInt();
            if (root.has("mutation_tier_1_after_beaten")) mutationTier1AfterBeaten = root.get("mutation_tier_1_after_beaten").getAsInt();
            if (root.has("mutation_tier_2_after_beaten")) mutationTier2AfterBeaten = root.get("mutation_tier_2_after_beaten").getAsInt();
            if (root.has("mutation_tier_3_after_beaten")) mutationTier3AfterBeaten = root.get("mutation_tier_3_after_beaten").getAsInt();
            if (root.has("post_win_residue_enabled")) postWinResidueEnabled = root.get("post_win_residue_enabled").getAsBoolean();
            if (root.has("old_ward_loot_per_scar")) oldWardLootPerScar = root.get("old_ward_loot_per_scar").getAsFloat();
            if (root.has("old_ward_loot_cap")) oldWardLootCap = root.get("old_ward_loot_cap").getAsFloat();
            if (root.has("wright_bonus_lives")) wrightBonusLives = root.get("wright_bonus_lives").getAsInt();
            if (root.has("wright_loot_bonus")) wrightLootBonus = root.get("wright_loot_bonus").getAsFloat();
            if (root.has("chain_enabled")) chainEnabled = root.get("chain_enabled").getAsBoolean();
            if (root.has("chain_after_beaten")) chainAfterBeaten = root.get("chain_after_beaten").getAsInt();
            if (root.has("chain_chance")) chainChance = root.get("chain_chance").getAsFloat();
            if (root.has("chain_min_links")) chainMinLinks = root.get("chain_min_links").getAsInt();
            if (root.has("chain_max_links")) chainMaxLinks = root.get("chain_max_links").getAsInt();
            if (root.has("chain_min_distance")) chainMinDistance = root.get("chain_min_distance").getAsInt();
            if (root.has("chain_max_distance")) chainMaxDistance = root.get("chain_max_distance").getAsInt();
            if (root.has("chain_radius")) chainRadius = root.get("chain_radius").getAsInt();
            if (root.has("chain_difficulty_per_link")) chainDifficultyPerLink = root.get("chain_difficulty_per_link").getAsFloat();
            if (root.has("chain_loot_per_link")) chainLootPerLink = root.get("chain_loot_per_link").getAsFloat();
            if (root.has("ambience_enabled")) ambienceEnabled = root.get("ambience_enabled").getAsBoolean();
            if (root.has("ambience_volume")) ambienceVolume = root.get("ambience_volume").getAsFloat();
            if (root.has("ambience_period_ticks")) ambiencePeriodTicks = root.get("ambience_period_ticks").getAsInt();
            if (root.has("ambience_range")) ambienceRange = root.get("ambience_range").getAsFloat();
            if (root.has("balance_edge")) balanceEdge = root.get("balance_edge").getAsFloat();
            if (root.has("fail_burn_chance")) failBurnChance = root.get("fail_burn_chance").getAsFloat();
            if (root.has("fail_burn_floor")) failBurnFloor = root.get("fail_burn_floor").getAsFloat();
            if (root.has("fail_burn_ceiling")) failBurnCeiling = root.get("fail_burn_ceiling").getAsFloat();
            if (root.has("spite_difficulty")) spiteDifficulty = root.get("spite_difficulty").getAsFloat();
            if (root.has("spite_loot")) spiteLoot = root.get("spite_loot").getAsFloat();
            if (root.has("spite_max")) spiteMax = root.get("spite_max").getAsInt();
            if (root.has("tier_step")) tierStep = root.get("tier_step").getAsInt();
            if (root.has("tier_max")) tierMax = root.get("tier_max").getAsInt();
            if (root.has("tier_difficulty_step")) tierDifficultyStep = root.get("tier_difficulty_step").getAsFloat();
            if (root.has("tier_loot_step")) tierLootStep = root.get("tier_loot_step").getAsFloat();
            if (root.has("watcher_enabled")) watcherEnabled = root.get("watcher_enabled").getAsBoolean();
            if (root.has("watcher_max_chance")) watcherMaxChance = root.get("watcher_max_chance").getAsFloat();
            if (root.has("watcher_after")) watcherAfter = root.get("watcher_after").getAsInt();
            if (root.has("watcher_ramp")) watcherRamp = root.get("watcher_ramp").getAsFloat();
            if (root.has("verdict_after")) verdictAfter = root.get("verdict_after").getAsInt();
            if (root.has("verdict_threshold")) verdictThreshold = root.get("verdict_threshold").getAsInt();
            if (root.has("contempt_difficulty")) contemptDifficulty = root.get("contempt_difficulty").getAsFloat();
            if (root.has("contempt_loot")) contemptLoot = root.get("contempt_loot").getAsFloat();
            if (root.has("guardian_chance")) guardianChance = root.get("guardian_chance").getAsFloat();
            if (root.has("guardian_max_count")) guardianMaxCount = root.get("guardian_max_count").getAsInt();
            if (root.has("guardian_seal_seconds")) guardianSealSeconds = root.get("guardian_seal_seconds").getAsInt();
            if (root.has("guardian_dimension_lock")) guardianDimensionLock = root.get("guardian_dimension_lock").getAsBoolean();
            if (root.has("guardian_progression_lock")) guardianProgressionLock = root.get("guardian_progression_lock").getAsBoolean();
            readIdLists(root, "guardian_pools", GUARDIAN_POOLS);
            readIdLists(root, "guardian_blacklist", GUARDIAN_BLACKLIST);
            if (root.has("tempt_chance")) temptChance = root.get("tempt_chance").getAsFloat();
            if (root.has("tempt_multiplier")) temptMultiplier = root.get("tempt_multiplier").getAsFloat();
            if (root.has("tempt_max_depth")) temptMaxDepth = root.get("tempt_max_depth").getAsInt();
            if (root.has("tempt_difficulty_per_depth")) temptDifficultyPerDepth = root.get("tempt_difficulty_per_depth").getAsFloat();
            if (root.has("key_bonus_lives")) keyBonusLives = root.get("key_bonus_lives").getAsInt();
            if (root.has("heart_bonus_lives")) heartBonusLives = root.get("heart_bonus_lives").getAsInt();
            if (root.has("heart_loot_bonus")) heartLootBonus = root.get("heart_loot_bonus").getAsFloat();
            if (root.has("key_drop_chance")) keyDropChance = root.get("key_drop_chance").getAsFloat();
            if (root.has("relic_drop_chance")) relicDropChance = root.get("relic_drop_chance").getAsFloat();
            if (root.has("heart_drop_chance")) heartDropChance = root.get("heart_drop_chance").getAsFloat();

            if (root.has("minigames") && root.get("minigames").isJsonObject()) {
                JsonObject games = root.getAsJsonObject("minigames");
                for (MinigameType type : MinigameType.values()) {
                    if (!games.has(type.id) || !games.get(type.id).isJsonObject()) continue;
                    JsonObject o = games.getAsJsonObject(type.id);
                    GameTuning t = tuning(type);
                    if (o.has("enabled")) t.enabled = o.get("enabled").getAsBoolean();
                    if (o.has("time_scale")) t.timeScale = o.get("time_scale").getAsFloat();
                    if (o.has("speed_scale")) t.speedScale = o.get("speed_scale").getAsFloat();
                    if (o.has("difficulty_scale")) t.difficultyScale = o.get("difficulty_scale").getAsFloat();
                    if (o.has("input_window_scale")) t.inputWindowScale = o.get("input_window_scale").getAsFloat();
                    if (o.has("reward_scale")) t.rewardScale = o.get("reward_scale").getAsFloat();
                }
            }

            if (root.has("dimensions")) {
                JsonObject dims = root.getAsJsonObject("dimensions");
                for (Map.Entry<String, JsonElement> e : dims.entrySet()) {
                    if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                    DimSettings parsed = readDim(e.getValue().getAsJsonObject());
                    if ("default".equals(e.getKey())) {
                        fallback = parsed;
                    } else {
                        DIMENSIONS.put(e.getKey(), parsed);
                    }
                }
            }

            if (root.has("item_values")) {
                JsonObject items = root.getAsJsonObject("item_values");
                for (Map.Entry<String, JsonElement> e : items.entrySet()) {
                    if (e.getKey().startsWith("_") || !e.getValue().isJsonPrimitive()) continue;
                    try {
                        itemValues.put(e.getKey(), e.getValue().getAsInt());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            Wardbound.LOG.info("[Wardbound] config loaded, {} dimension entries", DIMENSIONS.size());
        } catch (Exception e) {
            Wardbound.LOG.warn("[Wardbound] could not read config: {}", e.toString());
        }

        clampAll();
    }

    /**
     * Forces every setting back inside its limits.
     *
     * <p>Called at the end of a load and again whenever the config screen writes
     * values back, so a number typed into a box is held to exactly the same
     * bounds as a number typed into the file.
     */
    public static void clampAll() {

        // keep the ranges sane no matter what someone typed into the file/screen
        minPins = Math.max(1, Math.min(20, minPins));
        maxPins = Math.max(minPins, Math.min(20, maxPins));
        pulseIntensity = Math.max(0f, Math.min(1f, pulseIntensity));
        inputGraceMs = Math.max(0, Math.min(200, inputGraceMs));
        wardClockBaseSeconds = Math.max(5f, Math.min(120f, wardClockBaseSeconds));
        wardClockValueSeconds = Math.max(0f, Math.min(1.5f, wardClockValueSeconds));
        wardClockDifficultyCompensation = Math.max(0f, Math.min(1.5f, wardClockDifficultyCompensation));
        wardClockMinimumSeconds = Math.max(3f, Math.min(60f, wardClockMinimumSeconds));
        heartWardSeconds = Math.max(0f, Math.min(60f, heartWardSeconds));
        hurriedTimeScale = Math.max(0.35f, Math.min(1f, hurriedTimeScale));
        failLootMultiplier = Math.max(0f, Math.min(1f, failLootMultiplier));
        streakBonusPerLock = Math.max(0f, Math.min(0.5f, streakBonusPerLock));
        streakBonusCap = Math.max(0f, Math.min(3f, streakBonusCap));
        streakDifficultyPerLock = Math.max(0f, Math.min(0.5f, streakDifficultyPerLock));
        streakDifficultyCap = Math.max(0f, Math.min(2f, streakDifficultyCap));
        successLifeBonusMax = Math.max(0f, Math.min(1f, successLifeBonusMax));
        successSkillBonusMax = Math.max(0f, Math.min(1f, successSkillBonusMax));
        maxLootMultiplier = Math.max(1.0f, Math.min(12.0f, maxLootMultiplier));
        cleanWinPerformanceThreshold = Math.max(0.50f, Math.min(1.0f, cleanWinPerformanceThreshold));
        perfectWinPerformanceThreshold = Math.max(cleanWinPerformanceThreshold, Math.min(1.0f, perfectWinPerformanceThreshold));
        cleanWinTimeRemaining = Math.max(0f, Math.min(0.95f, cleanWinTimeRemaining));
        perfectWinTimeRemaining = Math.max(cleanWinTimeRemaining, Math.min(0.95f, perfectWinTimeRemaining));
        scrapedWinLootScale = Math.max(0.70f, Math.min(1.0f, scrapedWinLootScale));
        cleanWinLootAdd = Math.max(0f, Math.min(0.25f, cleanWinLootAdd));
        perfectWinLootAdd = Math.max(cleanWinLootAdd, Math.min(0.35f, perfectWinLootAdd));
        cleanWinRegardBonus = Math.max(0, Math.min(5, cleanWinRegardBonus));
        perfectWinRegardBonus = Math.max(cleanWinRegardBonus, Math.min(8, perfectWinRegardBonus));
        deceptionUnlockAfter = Math.max(0, Math.min(10000, deceptionUnlockAfter));
        deceptionBaseChance = Math.max(0f, Math.min(0.50f, deceptionBaseChance));
        deceptionMaxChance = Math.max(deceptionBaseChance, Math.min(0.75f, deceptionMaxChance));
        hybridUnlockAfter = Math.max(0, Math.min(10000, hybridUnlockAfter));
        hybridBaseChance = Math.max(0f, Math.min(0.25f, hybridBaseChance));
        hybridMaxChance = Math.max(hybridBaseChance, Math.min(0.40f, hybridMaxChance));
        hybridSuccessLootBonus = Math.max(0f, Math.min(0.25f, hybridSuccessLootBonus));
        accessibilityGuiAnimationIntensity = Math.max(0f, Math.min(1f, accessibilityGuiAnimationIntensity));
        accessibilitySfxVolume = Math.max(0f, Math.min(1.5f, accessibilitySfxVolume));
        parallaxMeterRevealThreshold = Math.max(0f, Math.min(0.95f, parallaxMeterRevealThreshold));
        masterSignatureBiasChance = Math.max(0f, Math.min(1f, masterSignatureBiasChance));
        masterSignatureRevealAfter = Math.max(0, Math.min(200, masterSignatureRevealAfter));
        masterSignatureKnowAfter = Math.max(masterSignatureRevealAfter, Math.min(400, masterSignatureKnowAfter));
        knownHandBonusPerSeal = Math.max(0f, Math.min(0.10f, knownHandBonusPerSeal));
        knownHandBonusCap = Math.max(0f, Math.min(0.50f, knownHandBonusCap));
        forbiddenBargainChance = Math.max(0f, Math.min(1f, forbiddenBargainChance));
        forbiddenBargainMaxOffers = Math.max(1, Math.min(4, forbiddenBargainMaxOffers));
        bargainBorrowedBreathReward = Math.max(0f, Math.min(0.40f, bargainBorrowedBreathReward));
        bargainIronDebtReward = Math.max(0f, Math.min(0.40f, bargainIronDebtReward));
        bargainWatchingMarkReward = Math.max(0f, Math.min(0.40f, bargainWatchingMarkReward));
        bargainCardHoverLift = Math.max(0f, Math.min(24f, bargainCardHoverLift));
        bargainCardAnimSpeed = Math.max(1f, Math.min(40f, bargainCardAnimSpeed));
        antiRepetitionWindow = Math.max(1, Math.min(8, antiRepetitionWindow));
        antiRepetitionRecentWeight = Math.max(0.05f, Math.min(1f, antiRepetitionRecentWeight));
        antiRepetitionDecay = Math.max(0f, Math.min(0.5f, antiRepetitionDecay));
        masterAudioMotifVolume = Math.max(0f, Math.min(0.8f, masterAudioMotifVolume));
        wardHistoryScarCap = Math.max(0, Math.min(12, wardHistoryScarCap));
        quirkOneThreshold = Math.max(0, Math.min(100, quirkOneThreshold));
        quirkTwoThreshold = Math.max(quirkOneThreshold, Math.min(100, quirkTwoThreshold));
        quirkLowValueChance = Math.max(0f, Math.min(1f, quirkLowValueChance));
        anomalyHurriedChance = Math.max(0f, Math.min(1f, anomalyHurriedChance));
        anomalyShroudedChance = Math.max(0f, Math.min(1f, anomalyShroudedChance));
        anomalyGutteringChance = Math.max(0f, Math.min(1f, anomalyGutteringChance));
        anomalyUnlitChance = Math.max(0f, Math.min(1f, anomalyUnlitChance));
        anomalySpentChance = Math.max(0f, Math.min(1f, anomalySpentChance));
        anomalyTwoThreshold = Math.max(0, Math.min(100, anomalyTwoThreshold));
        hurriedMinSeconds = Math.max(5f, Math.min(120f, hurriedMinSeconds));
        hurriedBaseSeconds = Math.max(hurriedMinSeconds, Math.min(240f, hurriedBaseSeconds));
        for (GameTuning t : MINIGAME_TUNING.values()) {
            t.timeScale = Math.max(0.35f, Math.min(3.0f, t.timeScale));
            t.speedScale = Math.max(0.35f, Math.min(2.5f, t.speedScale));
            t.difficultyScale = Math.max(0.50f, Math.min(2.0f, t.difficultyScale));
            t.inputWindowScale = Math.max(0.50f, Math.min(2.5f, t.inputWindowScale));
            t.rewardScale = Math.max(0.50f, Math.min(2.0f, t.rewardScale));
        }
        eyeCooldownTicks = Math.max(0, Math.min(72000, eyeCooldownTicks));
        eyeLootThreshold = Math.max(1f, Math.min(maxLootMultiplier, eyeLootThreshold));
        eyeAfterBeaten = Math.max(0, Math.min(10000, eyeAfterBeaten));
        eyeDropChance = Math.max(0f, Math.min(1f, eyeDropChance));
        lensTrueNameChance = Math.max(0f, Math.min(1f, lensTrueNameChance));
        emberSeconds = Math.max(0, Math.min(120, emberSeconds));
        emberLootThreshold = Math.max(1f, Math.min(20f, emberLootThreshold));
        emberAfterBeaten = Math.max(0, Math.min(10000, emberAfterBeaten));
        emberDropChance = Math.max(0f, Math.min(1f, emberDropChance));
        vesselSettle = Math.max(0f, Math.min(5f, vesselSettle));
        unsignedChance = Math.max(0f, Math.min(1f, unsignedChance));
        unsignedEndFactor = Math.max(0f, Math.min(50f, unsignedEndFactor));
        unsignedLoot = Math.max(1f, Math.min(8f, unsignedLoot));
        attentionStart = Math.max(1, Math.min(200, attentionStart));
        attentionPerLoss = Math.max(0, Math.min(20, attentionPerLoss));
        attentionCap = Math.max(attentionStart, Math.min(400, attentionCap));
        attentionResealChance = Math.max(0f, Math.min(1f, attentionResealChance));
        attentionHordeChance = Math.max(0f, Math.min(1f, attentionHordeChance));
        guardianWildChance = Math.max(0f, Math.min(1f, guardianWildChance));
        hordeSize = Math.max(1, Math.min(40, hordeSize));
        gauntletAfter = Math.max(0, Math.min(200, gauntletAfter));
        gauntletStages = Math.max(1, Math.min(8, gauntletStages));
        gauntletLoot = Math.max(1f, Math.min(10f, gauntletLoot));
        cthulhuWardChance = Math.max(0f, Math.min(1f, cthulhuWardChance));
        cthulhuWardMinValue = Math.max(1, Math.min(100, cthulhuWardMinValue));
        cthulhuWardDifficulty = Math.max(1f, Math.min(4f, cthulhuWardDifficulty));
        cthulhuWardLoot = Math.max(1f, Math.min(10f, cthulhuWardLoot));
        cthulhuIdolCooldownSeconds = Math.max(1, Math.min(300, cthulhuIdolCooldownSeconds));
        cthulhuIdolFreezeSeconds = Math.max(0.5f, Math.min(30f, cthulhuIdolFreezeSeconds));
        eldritchWardChance = Math.max(0f, Math.min(1f, eldritchWardChance));
        eldritchWardStages = Math.max(2, Math.min(8, eldritchWardStages));
        eldritchWardLoot = Math.max(1f, Math.min(10f, eldritchWardLoot));
        possessedWardChance = Math.max(0f, Math.min(1f, possessedWardChance));
        possessedWardLoot = Math.max(1f, Math.min(5f, possessedWardLoot));
        wardAfflictionChance = Math.max(0f, Math.min(1f, wardAfflictionChance));
        livingWardChance = Math.max(0f, Math.min(1f, livingWardChance));
        livingWardLoot = Math.max(1f, Math.min(3f, livingWardLoot));
        afflictionAfterBeaten = Math.max(0, Math.min(10000, afflictionAfterBeaten));
        possessedAfterBeaten = Math.max(0, Math.min(10000, possessedAfterBeaten));
        unsignedAfterBeaten = Math.max(0, Math.min(10000, unsignedAfterBeaten));
        eldritchAfterBeaten = Math.max(0, Math.min(10000, eldritchAfterBeaten));
        cthulhuAfterBeaten = Math.max(0, Math.min(10000, cthulhuAfterBeaten));
        bonusFreshRollAfterBeaten = Math.max(0, Math.min(10000, bonusFreshRollAfterBeaten));
        normalCardsAfterBeaten = Math.max(0, Math.min(10000, normalCardsAfterBeaten));
        fieldCardAfterBeaten = Math.max(normalCardsAfterBeaten, Math.min(10000, fieldCardAfterBeaten));
        fieldCardDropChance = Math.max(0f, Math.min(1f, fieldCardDropChance));
        masterCardsAfterBeaten = Math.max(0, Math.min(10000, masterCardsAfterBeaten));
        contractCardsAfterBeaten = Math.max(0, Math.min(10000, contractCardsAfterBeaten));
        ritualCardsAfterBeaten = Math.max(contractCardsAfterBeaten, Math.min(10000, ritualCardsAfterBeaten));
        covenantCardsAfterBeaten = Math.max(ritualCardsAfterBeaten, Math.min(10000, covenantCardsAfterBeaten));
        curseCardsAfterBeaten = Math.max(masterCardsAfterBeaten, Math.min(10000, curseCardsAfterBeaten));
        epicCardsAfterBeaten = Math.max(curseCardsAfterBeaten, Math.min(10000, epicCardsAfterBeaten));
        uniqueCardsAfterBeaten = Math.max(epicCardsAfterBeaten, Math.min(10000, uniqueCardsAfterBeaten));
        deathCardsAfterBeaten = Math.max(uniqueCardsAfterBeaten, Math.min(10000, deathCardsAfterBeaten));
        mercyChance = Math.max(0f, Math.min(1f, mercyChance));
        mercyAfterLosses = Math.max(1, Math.min(20, mercyAfterLosses));
        mercyLoot = Math.max(0.5f, Math.min(1f, mercyLoot));
        perfectRelicChance = Math.max(0f, Math.min(1f, perfectRelicChance));
        eldritchRelicChance = Math.max(0f, Math.min(1f, eldritchRelicChance));
        masterRivalryAfter = Math.max(1, Math.min(100, masterRivalryAfter));
        masterRivalryDifficulty = Math.max(1f, Math.min(2f, masterRivalryDifficulty));
        masterRivalryLoot = Math.max(1f, Math.min(3f, masterRivalryLoot));
        wardMutationDifficultyPerLevel = Math.max(0f, Math.min(0.20f, wardMutationDifficultyPerLevel));
        mutationTier1Age = Math.max(1, Math.min(100, mutationTier1Age));
        mutationTier2Age = Math.max(mutationTier1Age + 1, Math.min(150, mutationTier2Age));
        mutationTier3Age = Math.max(mutationTier2Age + 1, Math.min(200, mutationTier3Age));
        mutationTier1AfterBeaten = Math.max(0, Math.min(10000, mutationTier1AfterBeaten));
        mutationTier2AfterBeaten = Math.max(mutationTier1AfterBeaten + 1, Math.min(10000, mutationTier2AfterBeaten));
        mutationTier3AfterBeaten = Math.max(mutationTier2AfterBeaten + 1, Math.min(10000, mutationTier3AfterBeaten));
        oldWardLootPerScar = Math.max(0f, Math.min(0.25f, oldWardLootPerScar));
        oldWardLootCap = Math.max(1f, Math.min(4f, oldWardLootCap));
        wrightBonusLives = Math.max(0, Math.min(10, wrightBonusLives));
        wrightLootBonus = Math.max(1f, Math.min(4f, wrightLootBonus));
        chainAfterBeaten = Math.max(0, Math.min(10000, chainAfterBeaten));
        chainChance = Math.max(0f, Math.min(1f, chainChance));
        chainMinLinks = Math.max(2, Math.min(12, chainMinLinks));
        chainMaxLinks = Math.max(chainMinLinks, Math.min(12, chainMaxLinks));
        chainMinDistance = Math.max(50, Math.min(6000, chainMinDistance));
        chainMaxDistance = Math.max(chainMinDistance + 50, Math.min(12000, chainMaxDistance));
        chainRadius = Math.max(16, Math.min(400, chainRadius));
        chainDifficultyPerLink = Math.max(0f, Math.min(1f, chainDifficultyPerLink));
        chainLootPerLink = Math.max(0f, Math.min(2f, chainLootPerLink));
        ambienceVolume = Math.max(0f, Math.min(1f, ambienceVolume));
        ambiencePeriodTicks = Math.max(20, Math.min(1200, ambiencePeriodTicks));
        ambienceRange = Math.max(3f, Math.min(48f, ambienceRange));
        balanceEdge = Math.max(0.4f, Math.min(1f, balanceEdge));
        failBurnChance = Math.max(0f, Math.min(1f, failBurnChance));
        failBurnFloor = Math.max(0f, Math.min(1f, failBurnFloor));
        failBurnCeiling = Math.max(failBurnFloor, Math.min(1f, failBurnCeiling));
        spiteDifficulty = Math.max(0f, Math.min(2f, spiteDifficulty));
        spiteLoot = Math.max(0f, Math.min(3f, spiteLoot));
        spiteMax = Math.max(0, Math.min(20, spiteMax));
        tierStep = Math.max(0, tierStep);
        tierMax = Math.max(0, Math.min(20, tierMax));
        tierDifficultyStep = Math.max(0f, Math.min(1f, tierDifficultyStep));
        tierLootStep = Math.max(0f, Math.min(2f, tierLootStep));
        watcherMaxChance = Math.max(0f, Math.min(1f, watcherMaxChance));
        guardianChance = Math.max(0f, Math.min(1f, guardianChance));
        guardianMaxCount = Math.max(1, Math.min(12, guardianMaxCount));
        guardianSealSeconds = Math.max(0, Math.min(600, guardianSealSeconds));
        temptChance = Math.max(0f, Math.min(1f, temptChance));
        temptMultiplier = Math.max(1f, Math.min(4f, temptMultiplier));
        temptMaxDepth = Math.max(0, Math.min(10, temptMaxDepth));
        temptDifficultyPerDepth = Math.max(0f, Math.min(2f, temptDifficultyPerDepth));
        keyBonusLives = Math.max(0, Math.min(10, keyBonusLives));
        heartBonusLives = Math.max(0, Math.min(10, heartBonusLives));
        heartLootBonus = Math.max(1f, Math.min(4f, heartLootBonus));
        keyDropChance = Math.max(0f, Math.min(1f, keyDropChance));
        heartDropChance = Math.max(0f, Math.min(1f, heartDropChance));
        relicDropChance = Math.max(0f, Math.min(1f, relicDropChance));

        for (DimSettings d : DIMENSIONS.values()) clampDimension(d);
        clampDimension(fallback);
    }

    private static void clampDimension(DimSettings d) {
        if (d == null) return;
        d.difficulty = Math.max(0.25f, Math.min(4.0f, d.difficulty));
        d.lootMultiplier = Math.max(0.10f, Math.min(maxLootMultiplier, d.lootMultiplier));
        d.lives = Math.max(1, Math.min(20, d.lives));
        if (d.minigame == null) d.minigame = MinigameType.RUNES;
        if (d.onFail == null) d.onFail = FailAction.REDUCE_LOOT;
    }

    /**
     * Writes the current values back to config/wardbound.json.
     * Used by the in-game config screen (Mods list -> Wardbound -> Config) as well
     * as internally the first time the mod ever runs.
     */
    /** Reads a {@code {"dimension": ["id", ...]}} block, leaving the map empty if absent. */
    private static void readIdLists(JsonObject root, String key, Map<String, List<String>> into) {
        into.clear();
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        JsonObject obj = root.getAsJsonObject(key);
        for (String dim : obj.keySet()) {
            if (!obj.get(dim).isJsonArray()) continue;
            List<String> ids = new ArrayList<>();
            for (JsonElement e : obj.getAsJsonArray(dim)) {
                String id = e.getAsString().trim();
                if (!id.isEmpty()) ids.add(id);
            }
            into.put(dim, ids);
        }
    }

    /**
     * Writes those blocks back out. Pools are seeded with the stock lists on a
     * first run so the file shows what is actually spawning rather than an empty
     * object the player has to guess the shape of; blacklists are written empty
     * because an example ban would be a ban nobody asked for.
     */
    private static JsonObject writeIdLists(Map<String, List<String>> map, boolean seedDefaults) {
        JsonObject out = new JsonObject();
        if (map.isEmpty() && seedDefaults) {
            for (String dim : new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
                JsonArray arr = new JsonArray();
                Guardians.defaultPool(dim).forEach(arr::add);
                out.add(dim, arr);
            }
            return out;
        }
        if (map.isEmpty()) {
            for (String dim : new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
                out.add(dim, new JsonArray());
            }
            return out;
        }
        map.forEach((dim, ids) -> {
            JsonArray arr = new JsonArray();
            ids.forEach(arr::add);
            out.add(dim, arr);
        });
        return out;
    }

    public static void save() {
        save(FMLPaths.CONFIGDIR.get().resolve(FILE));
    }

    private static DimSettings readDim(JsonObject o) {
        String gameId = o.has("minigame") ? o.get("minigame").getAsString() : null;
        boolean random = "random".equalsIgnoreCase(gameId) || "any".equalsIgnoreCase(gameId);
        // A pipe separated list names several locks for one dimension.
        MinigameType[] pool = null;
        if (gameId != null && gameId.contains("|")) {
            String[] parts = gameId.split("\\|");
            List<MinigameType> picked = new ArrayList<>();
            for (String part : parts) {
                MinigameType t = MinigameType.byId(part.trim(), null);
                if (t != null && !picked.contains(t)) picked.add(t);
            }
            if (!picked.isEmpty()) pool = picked.toArray(new MinigameType[0]);
        }

        MinigameType game = MinigameType.byId(gameId, MinigameType.KEYWAY);
        float diff = o.has("difficulty") ? o.get("difficulty").getAsFloat() : 1.0f;
        float loot = o.has("loot_multiplier") ? o.get("loot_multiplier").getAsFloat() : 1.0f;
        int lives = o.has("lives") ? o.get("lives").getAsInt() : 3;
        FailAction fail = FailAction.byId(
                o.has("on_fail") ? o.get("on_fail").getAsString() : null, FailAction.REDUCE_LOOT);
        DimSettings s = new DimSettings(game, diff, loot, Math.max(1, lives), fail);
        s.randomGame = random;
        s.pool = pool;
        return s;
    }

    private static String gameIdOf(DimSettings s) {
        if (s.randomGame) return "random";
        if (s.pool != null && s.pool.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (MinigameType t : s.pool) {
                if (sb.length() > 0) sb.append('|');
                sb.append(t.id);
            }
            return sb.toString();
        }
        return s.minigame.id;
    }

    private static JsonObject writeDim(DimSettings s) {
        JsonObject o = new JsonObject();
        o.addProperty("minigame", gameIdOf(s));
        o.addProperty("difficulty", s.difficulty);
        o.addProperty("loot_multiplier", s.lootMultiplier);
        o.addProperty("lives", s.lives);
        o.addProperty("on_fail", s.onFail.id);
        return o;
    }

    private static void save(Path file) {
        JsonObject root = new JsonObject();
        dev.marrowseal.wardbound.boss.BossConfig.write(root);

        root.addProperty("config_version", CONFIG_VERSION);
        root.addProperty("_1", "=========== WARDBOUND ===========");
        root.addProperty("_2", "Chests generated inside structures get a Value score from their loot table (0-100).");
        root.addProperty("_3", "On right click the chest may be locked; you then play a minigame to open it.");
        root.addProperty("_4", "lock_threshold: below this Value uses lock_chance_low, at or above uses lock_chance_high.");
        root.addProperty("lock_threshold", lockThreshold);
        root.addProperty("lock_chance_low", lockChanceLow);
        root.addProperty("lock_chance_high", lockChanceHigh);
        root.addProperty("_5", "min_value_to_lock: chests with junk loot below this score are never locked.");
        root.addProperty("min_value_to_lock", minValueToLock);
        root.addProperty("_5a", "auto_item_values: judge every item automatically from its tags, tool tier,");
        root.addProperty("_5b", "armour stats, rarity, durability and stack size. Works for modded items with no setup.");
        root.addProperty("_5c", "Turn it off only if you want to score everything by hand in item_values below.");
        root.addProperty("auto_item_values", autoItemValues);
        root.addProperty("_5d", "auto_value_scale: nudge all automatic scores. 1.2 = chests feel richer and lock more often.");
        root.addProperty("auto_value_scale", autoValueScale);
        root.addProperty("_6", "global_roll_multiplier: flat multiplier stacked on every reward. 1.0 = no change.");
        root.addProperty("global_roll_multiplier", globalRollMultiplier);
        root.addProperty("_6a", "require_confirm_click: if true the minigame starts on a second right click, otherwise it opens immediately.");
        root.addProperty("require_confirm_click", requireConfirmClick);
        root.addProperty("_6b", "min_pins / max_pins: how many pins the keyway and the drum can roll. Scales with");
        root.addProperty("_6b2", "chest value and difficulty. The keyway caps at 6 no matter what is set here.");
        root.addProperty("min_pins", minPins);
        root.addProperty("max_pins", maxPins);
        root.addProperty("_6c", "pulse_intensity: how much the interface breathes. 0 = completely still, 1 = old full-strength throb.");
        root.addProperty("_6d", "Lower it if the glowing dots are tiring to look at; 0.35 is the calm default.");
        root.addProperty("pulse_intensity", pulseIntensity);
        root.addProperty("_6e", "input_grace_ms: timing forgiveness. A press counts against everything the dial/bar swept");
        root.addProperty("_6f", "during this window, which covers the lag between the frame you saw and the click landing.");
        root.addProperty("input_grace_ms", inputGraceMs);
        root.addProperty("ward_clock_base_seconds", wardClockBaseSeconds);
        root.addProperty("ward_clock_value_seconds", wardClockValueSeconds);
        root.addProperty("ward_clock_difficulty_compensation", wardClockDifficultyCompensation);
        root.addProperty("ward_clock_minimum_seconds", wardClockMinimumSeconds);
        root.addProperty("heart_ward_seconds", heartWardSeconds);
        root.addProperty("hurried_time_scale", hurriedTimeScale);
        root.addProperty("_6g", "fail_loot_multiplier: what survives when you lose. 0.4 = about 60% of the chest's worth is");
        root.addProperty("_6h", "taken, most valuable first. 1.0 = losing costs you nothing but the lock opening.");
        root.addProperty("fail_loot_multiplier", failLootMultiplier);
        root.addProperty("_6i", "=========== STREAKS ===========");
        root.addProperty("_6j", "Locks beaten back to back in the same dimension build a run. Each one pays more and");
        root.addProperty("_6k", "the next one is harder. Losing a lock resets the run to zero. Set the bonus to 0 to");
        root.addProperty("_6l", "turn the whole system off.");
        root.addProperty("streak_bonus_per_lock", streakBonusPerLock);
        root.addProperty("streak_bonus_cap", streakBonusCap);
        root.addProperty("streak_difficulty_per_lock", streakDifficultyPerLock);
        root.addProperty("streak_difficulty_cap", streakDifficultyCap);
        root.addProperty("_6m", "Each game also carries its own reward weight: pulse 1.30, augury and balance 1.25,");
        root.addProperty("_6n", "keyway 1.20, cipher and constellation 1.15, resonance 1.10, memory and pressure 1.05, rest 1.00.");
        root.addProperty("success_life_bonus_max", successLifeBonusMax);
        root.addProperty("success_skill_bonus_max", successSkillBonusMax);
        root.addProperty("max_loot_multiplier", maxLootMultiplier);
        root.addProperty("_7clean0", "=========== CLEAN / PERFECT WORK ===========");
        root.addProperty("clean_wins_enabled", cleanWinsEnabled);
        root.addProperty("clean_win_performance_threshold", cleanWinPerformanceThreshold);
        root.addProperty("perfect_win_performance_threshold", perfectWinPerformanceThreshold);
        root.addProperty("clean_win_time_remaining", cleanWinTimeRemaining);
        root.addProperty("perfect_win_time_remaining", perfectWinTimeRemaining);
        root.addProperty("scraped_win_loot_scale", scrapedWinLootScale);
        root.addProperty("clean_win_loot_add", cleanWinLootAdd);
        root.addProperty("perfect_win_loot_add", perfectWinLootAdd);
        root.addProperty("clean_win_regard_bonus", cleanWinRegardBonus);
        root.addProperty("perfect_win_regard_bonus", perfectWinRegardBonus);
        root.addProperty("_7adaptive0", "=========== ADAPTIVE WARD TESTS ===========");
        root.addProperty("_7adaptive1", "Skill profile selects which advanced tests notice the player; it never lowers base difficulty.");
        root.addProperty("adaptive_skill_profile_enabled", adaptiveSkillProfileEnabled);
        root.addProperty("advanced_minigame_variants_enabled", advancedMinigameVariantsEnabled);
        root.addProperty("advanced_minigame_variant_chance_scale", advancedMinigameVariantChanceScale);
        root.addProperty("deception_layer_enabled", deceptionLayerEnabled);
        root.addProperty("deception_unlock_after", deceptionUnlockAfter);
        root.addProperty("deception_base_chance", deceptionBaseChance);
        root.addProperty("deception_max_chance", deceptionMaxChance);
        root.addProperty("hybrid_rounds_enabled", hybridRoundsEnabled);
        root.addProperty("hybrid_unlock_after", hybridUnlockAfter);
        root.addProperty("hybrid_base_chance", hybridBaseChance);
        root.addProperty("hybrid_max_chance", hybridMaxChance);
        root.addProperty("hybrid_success_loot_bonus", hybridSuccessLootBonus);
        root.addProperty("_7telemetry0", "=========== OPTIONAL BALANCE TELEMETRY ===========");
        root.addProperty("_7telemetry1", "When enabled, resolved wards append one row to logs/wardbound-telemetry.csv.");
        root.addProperty("telemetry_enabled", telemetryEnabled);
        root.addProperty("telemetry_log_failures", telemetryLogFailures);
        root.addProperty("telemetry_include_modifiers", telemetryIncludeModifiers);
        root.addProperty("_7access0", "=========== ACCESSIBILITY ===========");
        root.addProperty("accessibility_reduce_motion", accessibilityReduceMotion);
        root.addProperty("accessibility_reduce_flashing", accessibilityReduceFlashing);
        root.addProperty("accessibility_high_contrast", accessibilityHighContrast);
        root.addProperty("accessibility_large_markers", accessibilityLargeMarkers);
        root.addProperty("accessibility_color_independent_feedback", accessibilityColorIndependentFeedback);
        root.addProperty("accessibility_disable_shake", accessibilityDisableShake);
        root.addProperty("accessibility_gui_animation_intensity", accessibilityGuiAnimationIntensity);
        root.addProperty("accessibility_sfx_volume", accessibilitySfxVolume);
        root.addProperty("parallax_meter_reveal_threshold", parallaxMeterRevealThreshold);

        root.addProperty("_6hd0", "=========== HANDS & DEBTS ===========");
        root.addProperty("_6hd1", "Master signatures only bias an existing quirk slot; they never add difficulty by adding another quirk.");
        root.addProperty("master_signatures_enabled", masterSignaturesEnabled);
        root.addProperty("master_signature_bias_chance", masterSignatureBiasChance);
        root.addProperty("master_signature_reveal_after", masterSignatureRevealAfter);
        root.addProperty("master_signature_know_after", masterSignatureKnowAfter);
        root.addProperty("known_hand_bonus_per_seal", knownHandBonusPerSeal);
        root.addProperty("known_hand_bonus_cap", knownHandBonusCap);
        root.addProperty("_6hd2", "Forbidden card offers are rare. Immediate chest rewards are flat additions; future ward terms are stored and applied separately.");
        root.addProperty("forbidden_bargains_enabled", forbiddenBargainsEnabled);
        root.addProperty("forbidden_bargain_chance", forbiddenBargainChance);
        root.addProperty("forbidden_bargain_max_offers", forbiddenBargainMaxOffers);
        root.addProperty("bargain_borrowed_breath_reward", bargainBorrowedBreathReward);
        root.addProperty("bargain_iron_debt_reward", bargainIronDebtReward);
        root.addProperty("bargain_watching_mark_reward", bargainWatchingMarkReward);
        root.addProperty("bargain_card_hover_lift", bargainCardHoverLift);
        root.addProperty("bargain_card_anim_speed", bargainCardAnimSpeed);
        root.addProperty("_6dir0", "=========== DIRECTOR & WARD HISTORY ===========");
        root.addProperty("_6dir1", "Recent games are de-weighted, not banned; narrow dimension pools remain playable.");
        root.addProperty("anti_repetition_enabled", antiRepetitionEnabled);
        root.addProperty("anti_repetition_window", antiRepetitionWindow);
        root.addProperty("anti_repetition_recent_weight", antiRepetitionRecentWeight);
        root.addProperty("anti_repetition_decay", antiRepetitionDecay);
        root.addProperty("master_audio_motifs_enabled", masterAudioMotifsEnabled);
        root.addProperty("master_audio_motif_volume", masterAudioMotifVolume);
        root.addProperty("ward_history_scars_enabled", wardHistoryScarsEnabled);
        root.addProperty("ward_history_scar_cap", wardHistoryScarCap);

        root.addProperty("_6qa0", "=========== QUIRKS & ANOMALIES ===========");
        root.addProperty("quirks_enabled", quirksEnabled);
        root.addProperty("anomalies_enabled", anomaliesEnabled);
        root.addProperty("quirk_one_threshold", quirkOneThreshold);
        root.addProperty("quirk_two_threshold", quirkTwoThreshold);
        root.addProperty("quirk_low_value_chance", quirkLowValueChance);
        root.addProperty("anomaly_hurried_chance", anomalyHurriedChance);
        root.addProperty("anomaly_shrouded_chance", anomalyShroudedChance);
        root.addProperty("anomaly_guttering_chance", anomalyGutteringChance);
        root.addProperty("anomaly_unlit_chance", anomalyUnlitChance);
        root.addProperty("anomaly_spent_chance", anomalySpentChance);
        root.addProperty("anomaly_two_threshold", anomalyTwoThreshold);
        root.addProperty("hurried_min_seconds", hurriedMinSeconds);
        root.addProperty("hurried_base_seconds", hurriedBaseSeconds);

        root.addProperty("_6mg0", "=========== PER-MINIGAME TUNING ===========");
        root.addProperty("_6mg1", "enabled removes a game from random/pool selection. time/speed/difficulty/input/reward are safe multipliers around its authored rules.");
        root.addProperty("_6mg2", "1.0 is stock. Bounds are clamped so a typo cannot make a lock effectively impossible or explode loot.");
        JsonObject gameTuning = new JsonObject();
        for (MinigameType type : MinigameType.values()) {
            GameTuning t = tuning(type);
            JsonObject o = new JsonObject();
            o.addProperty("enabled", t.enabled);
            o.addProperty("time_scale", t.timeScale);
            o.addProperty("speed_scale", t.speedScale);
            o.addProperty("difficulty_scale", t.difficultyScale);
            o.addProperty("input_window_scale", t.inputWindowScale);
            o.addProperty("reward_scale", t.rewardScale);
            gameTuning.add(type.id, o);
        }
        root.add("minigames", gameTuning);

        root.addProperty("_6f0", "=========== BURNING ===========");
        root.addProperty("_6f1", "A lost ward can take the contents with it. Chance scales with the ward's");
        root.addProperty("_6f2", "difficulty and the chest's worth: rich chests are the ones that can burn.");
        root.addProperty("_6u0", "=========== UNSIGNED SEALS ===========");
        root.addProperty("_6u1", "A rare ward with no maker's mark. Every quirk its lock has and every anomaly");
        root.addProperty("_6u2", "the chest can carry, all at once, for a great deal more loot. Far commoner in");
        root.addProperty("_6u3", "the End, which is the only honest answer to what has been making them.");
        root.addProperty("_6e0", "Cthulhu's Eye: only from End chests that beat eye_loot_threshold, and only");
        root.addProperty("_6e1", "then at eye_drop_chance. Right click to step to the next dimension along.");
        root.addProperty("eye_cooldown_ticks", eyeCooldownTicks);
        root.addProperty("eye_loot_threshold", eyeLootThreshold);
        root.addProperty("eye_after_beaten", eyeAfterBeaten);
        root.addProperty("eye_drop_chance", eyeDropChance);
        root.addProperty("lens_true_name_chance", lensTrueNameChance);
        root.addProperty("_6e2", "Slow ember: Nether chests that beat ember_loot_threshold may give one up.");
        root.addProperty("_6e3", "Carrying it adds flat seconds to the next ward you bind, and spends a use.");
        root.addProperty("ember_seconds", emberSeconds);
        root.addProperty("ember_loot_threshold", emberLootThreshold);
        root.addProperty("ember_after_beaten", emberAfterBeaten);
        root.addProperty("ember_drop_chance", emberDropChance);
        root.addProperty("vessel_settle", vesselSettle);
        root.addProperty("unsigned_chance", unsignedChance);
        root.addProperty("unsigned_end_factor", unsignedEndFactor);
        root.addProperty("unsigned_loot", unsignedLoot);
        root.addProperty("_6u4", "gauntlet: after this many unsigned seals, the next one is three locks in a");
        root.addProperty("_6u5", "row on one chest, one pool of lives, charms refused. Nothing follows it.");
        root.addProperty("gauntlet_after", gauntletAfter);
        root.addProperty("gauntlet_stages", gauntletStages);
        root.addProperty("gauntlet_loot", gauntletLoot);
        root.addProperty("_6u6", "eldritch wards: rare elite chains that splice several mutated locks into one");
        root.addProperty("_6u7", "ward. They pay extra on the final break and use one shared pool of lives.");
        root.addProperty("cthulhu_wards_enabled", cthulhuWardsEnabled);
        root.addProperty("cthulhu_ward_chance", cthulhuWardChance);
        root.addProperty("cthulhu_ward_min_value", cthulhuWardMinValue);
        root.addProperty("cthulhu_ward_difficulty", cthulhuWardDifficulty);
        root.addProperty("cthulhu_ward_loot", cthulhuWardLoot);
        root.addProperty("cthulhu_idol_cooldown_seconds", cthulhuIdolCooldownSeconds);
        root.addProperty("cthulhu_idol_freeze_seconds", cthulhuIdolFreezeSeconds);
        root.addProperty("eldritch_wards_enabled", eldritchWardsEnabled);
        root.addProperty("eldritch_ward_chance", eldritchWardChance);
        root.addProperty("eldritch_ward_stages", eldritchWardStages);
        root.addProperty("eldritch_ward_loot", eldritchWardLoot);
        root.addProperty("possessed_wards_enabled", possessedWardsEnabled);
        root.addProperty("possessed_ward_chance", possessedWardChance);
        root.addProperty("possessed_ward_loot", possessedWardLoot);
        root.addProperty("ward_afflictions_enabled", wardAfflictionsEnabled);
        root.addProperty("ward_affliction_chance", wardAfflictionChance);
        root.addProperty("living_wards_enabled", livingWardsEnabled);
        root.addProperty("living_ward_chance", livingWardChance);
        root.addProperty("living_ward_loot", livingWardLoot);
        root.addProperty("_6u8", "onboarding gates count resolved wards before special forms can enter the pool");
        root.addProperty("affliction_after_beaten", afflictionAfterBeaten);
        root.addProperty("possessed_after_beaten", possessedAfterBeaten);
        root.addProperty("unsigned_after_beaten", unsignedAfterBeaten);
        root.addProperty("eldritch_after_beaten", eldritchAfterBeaten);
        root.addProperty("cthulhu_after_beaten", cthulhuAfterBeaten);
        root.addProperty("bonus_fresh_roll_after_beaten", bonusFreshRollAfterBeaten);
        root.addProperty("progression_loot_caps_enabled", progressionLootCapsEnabled);
        root.addProperty("normal_cards_after_beaten", normalCardsAfterBeaten);
        root.addProperty("field_card_after_beaten", fieldCardAfterBeaten);
        root.addProperty("field_card_drop_chance", fieldCardDropChance);
        root.addProperty("master_cards_after_beaten", masterCardsAfterBeaten);
        root.addProperty("contract_cards_after_beaten", contractCardsAfterBeaten);
        root.addProperty("ritual_cards_after_beaten", ritualCardsAfterBeaten);
        root.addProperty("covenant_cards_after_beaten", covenantCardsAfterBeaten);
        root.addProperty("curse_cards_after_beaten", curseCardsAfterBeaten);
        root.addProperty("epic_cards_after_beaten", epicCardsAfterBeaten);
        root.addProperty("unique_cards_after_beaten", uniqueCardsAfterBeaten);
        root.addProperty("death_cards_after_beaten", deathCardsAfterBeaten);
        root.addProperty("mercy_chance", mercyChance);
        root.addProperty("mercy_after_losses", mercyAfterLosses);
        root.addProperty("mercy_loot", mercyLoot);
        root.addProperty("perfect_relic_chance", perfectRelicChance);
        root.addProperty("eldritch_relic_chance", eldritchRelicChance);
        root.addProperty("master_rivalries_enabled", masterRivalriesEnabled);
        root.addProperty("master_rivalry_after", masterRivalryAfter);
        root.addProperty("master_rivalry_difficulty", masterRivalryDifficulty);
        root.addProperty("master_rivalry_loot", masterRivalryLoot);
        root.addProperty("ward_mutation_difficulty_per_level", wardMutationDifficultyPerLevel);
        root.addProperty("mutation_tier_1_age", mutationTier1Age);
        root.addProperty("mutation_tier_2_age", mutationTier2Age);
        root.addProperty("mutation_tier_3_age", mutationTier3Age);
        root.addProperty("mutation_tier_1_after_beaten", mutationTier1AfterBeaten);
        root.addProperty("mutation_tier_2_after_beaten", mutationTier2AfterBeaten);
        root.addProperty("mutation_tier_3_after_beaten", mutationTier3AfterBeaten);
        root.addProperty("post_win_residue_enabled", postWinResidueEnabled);
        root.addProperty("old_ward_loot_per_scar", oldWardLootPerScar);
        root.addProperty("old_ward_loot_cap", oldWardLootCap);

        root.addProperty("_6a0", "=========== THE ATTENTION ===========");
        root.addProperty("_6a1", "Finish a gauntlet and something starts watching. No boss and no arena: the");
        root.addProperty("_6a2", "chests you already emptied seal themselves again, everything is unsigned, and");
        root.addProperty("_6a3", "winning sends a horde as often as losing. Break its seals and it lets go.");
        root.addProperty("attention_enabled", attentionEnabled);
        root.addProperty("attention_start", attentionStart);
        root.addProperty("attention_per_loss", attentionPerLoss);
        root.addProperty("attention_cap", attentionCap);
        root.addProperty("attention_reseal_chance", attentionResealChance);
        root.addProperty("attention_horde_chance", attentionHordeChance);
        root.addProperty("_6a4", "guardian_wild_chance: how often a spawn ignores the curated pool and takes");
        root.addProperty("_6a5", "any eligible hostile the game knows, modded included. Bosses and locked dimensions are excluded.");
        root.addProperty("guardian_wild_chance", guardianWildChance);
        root.addProperty("horde_size", hordeSize);
        root.addProperty("_6c0", "=========== CHAINS ===========");
        root.addProperty("_6c1", "Break a ward and it may turn out to be one of a set cast by the same house.");
        root.addProperty("_6c2", "You are given a heading and a rough distance, never coordinates. Whatever");
        root.addProperty("_6c3", "container is standing there becomes the next link: nothing is generated.");
        root.addProperty("_6c4", "Finishing a chain hands over that house's own key: strong on their seals,");
        root.addProperty("_6c5", "worthless on anyone else's, and it lasts far longer than the other charms.");
        root.addProperty("wright_bonus_lives", wrightBonusLives);
        root.addProperty("wright_loot_bonus", wrightLootBonus);
        root.addProperty("chain_enabled", chainEnabled);
        root.addProperty("chain_after_beaten", chainAfterBeaten);
        root.addProperty("chain_chance", chainChance);
        root.addProperty("chain_min_links", chainMinLinks);
        root.addProperty("chain_max_links", chainMaxLinks);
        root.addProperty("chain_min_distance", chainMinDistance);
        root.addProperty("chain_max_distance", chainMaxDistance);
        root.addProperty("chain_radius", chainRadius);
        root.addProperty("chain_difficulty_per_link", chainDifficultyPerLink);
        root.addProperty("chain_loot_per_link", chainLootPerLink);

        root.addProperty("_6h0", "Warded containers hum quietly when you are within a few blocks. Unsigned ones");
        root.addProperty("_6h1", "hum lower and less steadily, so you can hear what you have walked into.");
        root.addProperty("ambience_enabled", ambienceEnabled);
        root.addProperty("ambience_volume", ambienceVolume);
        root.addProperty("ambience_period_ticks", ambiencePeriodTicks);
        root.addProperty("ambience_range", ambienceRange);
        root.addProperty("balance_edge", balanceEdge);
        root.addProperty("fail_burn_chance", failBurnChance);
        root.addProperty("fail_burn_floor", failBurnFloor);
        root.addProperty("fail_burn_ceiling", failBurnCeiling);

        root.addProperty("_6s0", "=========== GRUDGES ===========");
        root.addProperty("_6s1", "A container that beats you closes tighter next time and holds more. Stops a lost");
        root.addProperty("_6s2", "lock from being a free retry. spite_difficulty 0 disables it.");
        root.addProperty("spite_difficulty", spiteDifficulty);
        root.addProperty("spite_loot", spiteLoot);
        root.addProperty("spite_max", spiteMax);

        root.addProperty("_6t0", "=========== THE LONG CURVE ===========");
        root.addProperty("_6t1", "Every ward in the world gets harder as you break more of them, and better");
        root.addProperty("_6t2", "paid faster than it gets harder. This one never resets. tier_step 0 disables it.");
        root.addProperty("tier_step", tierStep);
        root.addProperty("tier_max", tierMax);
        root.addProperty("tier_difficulty_step", tierDifficultyStep);
        root.addProperty("tier_loot_step", tierLootStep);

        root.addProperty("_6w0", "=========== THE WATCHER ===========");
        root.addProperty("_6w1", "One sealmaker, chosen from your player id and never rerolled, slowly starts");
        root.addProperty("_6w2", "turning up more often than chance allows. Changes no numbers at all.");
        root.addProperty("watcher_enabled", watcherEnabled);
        root.addProperty("watcher_max_chance", watcherMaxChance);
        root.addProperty("watcher_after", watcherAfter);
        root.addProperty("watcher_ramp", watcherRamp);
        root.addProperty("_6w3", "After enough of that house's seals it forms a verdict, once and for good.");
        root.addProperty("_6w4", "Favour: +1 life on every lock anywhere, forever. Contempt: that house's seals");
        root.addProperty("_6w5", "get harder and pay considerably more. Neither is simply the better one.");
        root.addProperty("verdict_after", verdictAfter);
        root.addProperty("verdict_threshold", verdictThreshold);
        root.addProperty("contempt_difficulty", contemptDifficulty);
        root.addProperty("contempt_loot", contemptLoot);

        root.addProperty("_6g0", "=========== GUARDIANS ===========");
        root.addProperty("_6g1", "Lose a lock and the container may wake something up. What appears is drawn");
        root.addProperty("_6g2", "from that dimension's pool, so a failed chest is never the same scene twice.");
        root.addProperty("_6g3", "guardian_seal_seconds: how long the container refuses to open afterwards, so");
        root.addProperty("_6g4", "the fight has somewhere to happen instead of being outrun.");
        root.addProperty("guardian_chance", guardianChance);
        root.addProperty("guardian_max_count", guardianMaxCount);
        root.addProperty("guardian_seal_seconds", guardianSealSeconds);
        root.addProperty("_6g4a", "guardian_dimension_lock: Nether/End progression mobs stay in their native dimension.");
        root.addProperty("guardian_dimension_lock", guardianDimensionLock);
        root.addProperty("_6g4b", "guardian_progression_lock: even with the dimension lock off, those mobs remain locked until visited.");
        root.addProperty("guardian_progression_lock", guardianProgressionLock);
        root.addProperty("_6g5", "guardian_pools: entity ids per dimension. Delete a dimension's list to go back");
        root.addProperty("_6g6", "to the stock pool. Unknown ids and ids from mods you do not have are skipped.");
        root.add("guardian_pools", writeIdLists(GUARDIAN_POOLS, true));
        root.addProperty("_6g7", "guardian_blacklist: ids that never spawn in that dimension, whatever the pool");
        root.addProperty("_6g8", "says. This is the list to edit if one particular mob is ruining your day.");
        root.addProperty("_6g9", "Example, doing nothing as written: put ids in a dimension's list to ban them,");
        root.addProperty("_6g10", "e.g. \"minecraft:overworld\": [\"minecraft:cave_spider\", \"minecraft:witch\"].");
        root.add("guardian_blacklist", writeIdLists(GUARDIAN_BLACKLIST, false));

        root.addProperty("_6o0", "=========== THE OFFER ===========");
        root.addProperty("_6o1", "Beat a lock and the container banks the loot but does not open. Sneak-click to");
        root.addProperty("_6o2", "re-seal it harder for a compounding bonus, or click normally to take what you have.");
        root.addProperty("_6o3", "Losing a re-sealed lock drops you to the ordinary failure share: you lose the");
        root.addProperty("_6o4", "winnings, not the container.");
        root.addProperty("tempt_enabled", temptEnabled);
        root.addProperty("tempt_chance", temptChance);
        root.addProperty("tempt_multiplier", temptMultiplier);
        root.addProperty("tempt_max_depth", temptMaxDepth);
        root.addProperty("tempt_difficulty_per_depth", temptDifficultyPerDepth);

        root.addProperty("_6o", "=========== CHARMS ===========");
        root.addProperty("_6p", "Carry a Rusted Ward Key or an Ichorheart and it binds itself to the next lock you");
        root.addProperty("_6q", "open, spending one durability whether you win or lose. Binding happens when the lock");
        root.addProperty("_6r", "does, so you cannot pull one out halfway through a chest that is going badly.");
        root.addProperty("key_bonus_lives", keyBonusLives);
        root.addProperty("heart_bonus_lives", heartBonusLives);
        root.addProperty("_6s", "heart_loot_bonus: multiplier the heart folds into the chest's final roll. 1.25 = +25%.");
        root.addProperty("heart_loot_bonus", heartLootBonus);
        root.addProperty("_6t", "Drop chances apply only to chests whose lock you actually played. Set to 0 to make");
        root.addProperty("_6u", "the charms craft-only. The heart's chance is scaled down further by chest worth.");
        root.addProperty("key_drop_chance", keyDropChance);
        root.addProperty("heart_drop_chance", heartDropChance);
        root.addProperty("relic_drop_chance", relicDropChance);

        root.addProperty("_7", "=========== DIMENSIONS ===========");
        root.addProperty("_8", "Add any dimension id here, modded ones included, e.g. \"twilightforest:twilight_forest\".");
        root.addProperty("_9", "minigame: keyway | augury | drum | timing | memory | runes | cipher | pressure | resonance | pulse | balance");
        root.addProperty("_9b", "         constellation | mirror | gyre | vessel | yoke | rootway | lattice | parallax | runebraid | shardsong | veilstep | measure | epitaph");
        root.addProperty("_9x1", "Give a dimension several locks by listing them: \"minigame\": \"keyway|augury|gyre\".");
        root.addProperty("_9x2", "One is picked from the list using the chest's own seed, so a chest always");
        root.addProperty("_9x3", "runs the same one. \"random\" means every lock in the mod.");
        root.addProperty("_9c", "\"keyway\" is the pin tumbler cutaway; \"drum\" is the old rotating dial, which used");
        root.addProperty("_9d", "to be called \"lockpick\". Old config files saying lockpick still resolve to drum.");
        root.addProperty("_9a", "Use \"random\" to let each chest pick its own game from its seed - same chest, same game, every time.");
        root.addProperty("_10", "difficulty: 0.5 easy, 1.0 normal, 2.0 brutal. Scales speed, pin count, sequence length.");
        root.addProperty("_11", "loot_multiplier: reward scaling for this dimension. 1.0 = vanilla amount.");
        root.addProperty("_12", "on_fail: reduce_loot | lock_forever | explode | spawn_guard");
        root.addProperty("_13", "The \"default\" entry is used for every dimension not listed above.");

        JsonObject dims = new JsonObject();
        for (Map.Entry<String, DimSettings> e : DIMENSIONS.entrySet()) {
            dims.add(e.getKey(), writeDim(e.getValue()));
        }
        dims.add("default", writeDim(fallback));
        root.add("dimensions", dims);

        root.addProperty("_14", "=========== ITEM VALUE OVERRIDES ===========");
        root.addProperty("_15", "You do NOT need to list your modded items here. With auto_item_values on,");
        root.addProperty("_16", "anything not listed is scored automatically. Use this map only to correct");
        root.addProperty("_17", "the few items the automatic scorer gets wrong for your pack.");
        root.addProperty("_18", "1 = junk | 10-14 = iron tier | 15-20 = gold/tool tier | 22-25 = gem tier | 30+ = jackpot");
        root.addProperty("_19", "Use full ids: \"modid:item_path\". Run /wardbound item to see what an item scores now.");

        JsonObject items = new JsonObject();
        for (Map.Entry<String, Integer> e : itemValues.entrySet()) {
            items.addProperty(e.getKey(), e.getValue());
        }
        items.addProperty("_add_overrides_below", "example -> \"mymod:ruby\": 30");
        root.add("item_values", items);

        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
            Wardbound.LOG.info("[Wardbound] wrote default config to {}", file);
        } catch (IOException e) {
            Wardbound.LOG.warn("[Wardbound] could not write config: {}", e.toString());
        }
    }
}
