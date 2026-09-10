package dev.marrowseal.wardbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Sealmakers;
import dev.marrowseal.wardbound.MasterSignature;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.MinigameCorruption;
import dev.marrowseal.wardbound.MinigameCardEffects;
import dev.marrowseal.wardbound.MinigameDeception;
import dev.marrowseal.wardbound.WardMeasureSystem;
import dev.marrowseal.wardbound.HybridRound;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.MinigameResultPacket;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared frame for every lock.
 *
 * <p>Three things changed here and they are the reason the whole mod feels different.
 *
 * <p><b>One draw call.</b> All shape drawing goes through {@link Painter}, which batches the entire
 * frame into a single {@code Tesselator} pass. The old code called {@code GuiGraphics.fill} once
 * per pixel of every circle, and that call is not batched on 1.20.1; that alone was the 15-20 fps.
 *
 * <p><b>Input is sampled, not guessed.</b> The simulation lives in {@link #step(float)} and is
 * advanced by {@link #catchUp()} both from render and from every key/mouse event, so a click is
 * judged against where the dial actually is at the moment of the click rather than where it was
 * drawn on the last frame. At 20 fps a fast dial moved further per frame than the whole hit window,
 * which is why pins felt impossible to land.
 *
 * <p><b>Motion is calm by default.</b> {@link #breath(float)} runs every idle animation through
 * {@code pulse_intensity} from the config, so nothing strobes; set it to 0 for a completely still
 * interface.
 */
@OnlyIn(Dist.CLIENT)
public abstract class BaseMinigameScreen extends Screen {

    protected static final int COL_GOOD = 0xFF8FC96E;
    protected static final int COL_BAD = 0xFFCB5340;
    protected static final int COL_SHADOW = 0x66000000;

    protected final BlockPos pos;
    protected final int value;
    protected final MinigameType gameType;
    protected final float gameTimeScale;
    protected final float gameSpeedScale;
    protected final float gameInputWindowScale;
    protected final int maxLives;
    protected final long seed;
    protected final float difficulty;
    protected final float lootMultiplier;
    protected final String dimensionId;
    protected final int streak;
    /**
     * The charm riding this lock: 0 none, 1 the key, 2 the heart. Screens use
     * {@link #heartHint()} rather than reading this directly.
     */
    protected final int charmTier;
    /** How many of this seal's house the player has already broken. */
    protected final int familiarity;
    /**
     * This seal has no maker's mark.
     *
     * <p>It does not add a mechanic. It removes the limits: every quirk the lock
     * has is on, both anomaly slots are filled, and nothing in the header claims
     * to have made it. The mod already had a hardest possible configuration and
     * simply never rolled it; this is the thing that rolls it.
     */
    protected final boolean unsigned;
    /** Seconds bought by a slow ember. */
    protected final int extraSeconds;
    /** Persistent wear carried by the physical container. */
    protected final int scarCount;
    protected final int scarFlags;
    protected final boolean eldritch;
    protected final int eldritchStage;
    protected final int eldritchTotal;
    protected final boolean possessed;
    protected final boolean rivalry;
    protected final int mutationLevel;
    protected final int affliction;
    protected final boolean living;
    protected final boolean mercy;
    protected final int cardMinigameMask;
    protected final int measureStage;
    protected final int measureClause;
    protected final MakerMinigameBehavior.Style makerStyle;
    protected final int masteryTier;
    protected final MinigameCorruption.Variant corruptionVariant;
    protected final MinigameCorruption.Profile corruptionProfile;
    protected final MinigameType examWeakGame;
    protected final MinigameType examStrongGame;
    protected final MinigameDeception.Mode deceptionMode;
    protected final HybridRound.Mode hybridMode;
    private boolean hybridCompleted;
    private boolean hybridActive;
    private boolean hybridTriggered;
    private int hybridOutcome; // 0 none, 1 cleared, 2 failed
    private float hybridTimer;
    private float hybridTriggerAt;
    private int hybridStep;
    private final int[] hybridRuneSequence = new int[3];
    private final int[] hybridRuneOptions = new int[4];
    private float hybridGauge;
    private boolean hybridHeld;
    private float hybridTarget;
    private float deceptionTime;
    private int lastDeceptionBeat = -1;
    private long deceptionFlashUntil;
    private int deceptionFlashType;
    private int rapidMistakes;
    private long lastMistakeMillis;
    private float motifElapsed;
    private int motifNote;
    /** Seconds of wax-seal break animation left after a win. */
    private float sealBreak;

    /** Seconds left on the ward. Zero means the lock is not timed. */
    private float clockLeft;
    private float clockTotal;

    /**
     * Chest-wide conditions layered on top of whatever quirks the lock itself
     * rolled. See {@link Anomaly}. Rolled from the seed, so a given chest always
     * presents the same way.
     */
    protected final java.util.EnumSet<Anomaly> anomalies = java.util.EnumSet.noneOf(Anomaly.class);
    private float clock = 1f;
    private float clockDrain;
    private float gutter;
    private float shroudT;

    // Corrupted wards now alter play, not only presentation. This clock is
    // deliberately independent of the ward timer so closing/reopening cannot
    // be used to wait out a dangerous pulse; the exact cadence is seeded per
    // chest and begins again as part of the attempt snapshot.
    private float corruptionTime;
    private float possessedRage;
    private int lastPossessedBeat = -1;
    private int lastEldritchBeat = -1;
    /** Physical key -> key delivered to the minigame, so refraction cannot leave a held control stuck. */
    private final java.util.Map<Integer, Integer> deliveredKeys = new java.util.HashMap<>();
    /**
     * Mouse buttons whose press was actually delivered to the host minigame.
     *
     * <p>A corruption phase can begin between press and release. Without pairing
     * the two events, the release used to be swallowed by blackout/possession,
     * leaving hold-driven locks (Pressure, Keyway, Balance, Vessel) logically
     * pressed until another input happened. Keyboard input already kept this
     * mapping; mouse input now follows the same rule.</p>
     */
    private final java.util.Set<Integer> deliveredMouseButtons = new java.util.HashSet<>();

    /** Server-authoritative global ward progression. */
    protected final int resolvedWards;
    /** Whether late Master presentation is actually allowed for this player. */
    protected final boolean masterPhase;

    protected int lives;
    /** Balance telemetry: significant wrong inputs/lives lost during this attempt. */
    protected int mistakes;
    protected int progress;
    private boolean cardForgivenessSpent;

    protected boolean resolved;
    protected String banner;
    protected int bannerColor = 0xFFEEDDBB;
    protected long bannerUntil;
    protected final List<String> modifierLabels = new ArrayList<>();

    protected final Painter p = new Painter();

    private Theme cachedTheme;

    private long lastNanos = System.nanoTime();
    private final long openedAtMillis = System.currentTimeMillis();
    /** Elapsed time already spent on this attempt before the current screen instance. */
    private final float elapsedBeforeOpen;
    // Follows the same capped simulation clock as gameplay. Mandatory title/read
    // pauses therefore do not advance hybrid triggers or inflate telemetry.
    private float activeElapsedSeconds;
    private static final int INTRO_MS = 220;

    private final List<Burst> bursts = new ArrayList<>();

    private record Burst(int x, int y, int color, long start) {
    }

    protected BaseMinigameScreen(Component title, OpenMinigamePacket msg) {
        super(title);
        this.pos = msg.pos;
        this.value = msg.value;
        this.resolvedWards = msg.resolvedWards;
        this.masterPhase = msg.masterPhase;
        this.gameType = MinigameType.byOrdinal(msg.gameId);
        this.gameTimeScale = msg.timeScale;
        this.gameSpeedScale = msg.speedScale;
        this.gameInputWindowScale = msg.inputWindowScale;
        this.lives = msg.lives;
        this.maxLives = msg.maxLives;
        this.seed = msg.seed;
        this.progress = msg.progress;
        this.difficulty = msg.difficulty;
        this.lootMultiplier = msg.lootMultiplier;
        this.dimensionId = msg.dimensionId;
        this.streak = msg.streak;
        this.charmTier = msg.charm;
        this.familiarity = msg.familiarity;
        this.unsigned = msg.unsigned;
        this.extraSeconds = msg.extraSeconds;
        this.scarCount = msg.scarCount;
        this.scarFlags = msg.scarFlags;
        this.eldritch = msg.eldritch;
        this.eldritchStage = msg.eldritchStage;
        this.eldritchTotal = msg.eldritchTotal;
        this.possessed = msg.possessed;
        this.rivalry = msg.rivalry;
        this.mutationLevel = msg.mutationLevel;
        this.affliction = msg.affliction;
        this.living = msg.living;
        this.mercy = msg.mercy;
        this.cardMinigameMask = msg.cardMinigameMask;
        this.measureStage = msg.measureStage;
        this.measureClause = msg.measureClause;
        this.masteryTier = Math.max(0, Math.min(5, msg.masteryTier));
        // The global opening stretch teaches clean ward grammar before authored handwriting
        // joins the rotation. After normal card hands emerge, unfamiliar disciplines can still
        // receive one restrained ordinary quirk; hard narrative mutations keep their separate
        // server-side requirement of at least one real win in that discipline.
        this.makerStyle = introductoryWard() ? MakerMinigameBehavior.Style.UNSIGNED
                : MakerMinigameBehavior.style(this.seed, this.unsigned);
        this.corruptionVariant = MinigameCorruption.variant(this.gameType, msg.corruptionVariant);
        this.corruptionProfile = this.corruptionVariant.profile();
        this.examWeakGame = MinigameType.byOrdinal(msg.examWeakGame);
        this.examStrongGame = MinigameType.byOrdinal(msg.examStrongGame);
        this.deceptionMode = MinigameDeception.byOrdinal(msg.deceptionMode);
        this.hybridMode = HybridRound.byOrdinal(msg.hybridMode);
        this.hybridCompleted = msg.hybridCompleted;
        this.hybridOutcome = msg.savedHybridOutcome;
        this.hybridActive = msg.resumed && msg.savedHybridActive && !msg.hybridCompleted;
        this.hybridTriggered = this.hybridActive || msg.hybridCompleted;
        this.hybridTimer = this.hybridActive ? msg.savedHybridTimer : 0f;
        this.hybridStep = this.hybridActive ? msg.savedHybridStep : 0;
        this.hybridGauge = this.hybridActive ? msg.savedHybridGauge : 0f;
        this.hybridHeld = false;
        this.rapidMistakes = msg.savedRapidMistakes;
        this.elapsedBeforeOpen = msg.resumed ? Math.max(0f, msg.savedElapsedSeconds) : 0f;
        this.corruptionTime = this.elapsedBeforeOpen;
        this.mistakes = msg.resumed ? Math.max(0, msg.savedMistakes) : 0;
        this.cardForgivenessSpent = msg.resumed && this.mistakes > 0
                && MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.FORGIVE_FIRST);
        // A Possessed ward starts angrier as meaningful mistakes accumulate.
        // The live surge still cools down, but the mistake-derived floor cannot be reset by closing the GUI.
        this.possessedRage = Math.min(3.0f, this.mistakes * 0.22f);
        this.motifElapsed = 0f;
        this.motifNote = unsigned ? 3 : 0;

        // Listed with the chest's own quirks rather than in a badge of its own.
        // A charm changes what this lock is in exactly the way a quirk does, and
        // splitting them into two vocabularies would mean reading two places.
        // Give this lock the voice of whoever made it, before anything can play.
        // Unsigned seals share one voice, and it is not one of the houses'.
        Sfx.setHouseTone(unsigned ? 0x00E7D400L : Sealmakers.house(seed).hashCode());

        rollAnomalies();

        // A half-picked ward hands you the first stage and takes a life for it.
        // Both halves are visible - a filled progress dot, one fewer eye - which
        // is the entire point of the change. Applied after the roll, obviously:
        // before it, the set is still empty.
        if (has(Anomaly.SPENT) && !msg.spentApplied) {
            progress = Math.max(progress, 1);
            // Never below one, or the anomaly would be a coin flip on a lock
            // that has not started yet. The server remembers that this tax has
            // already fired, so closing/reopening cannot charge it twice.
            if (lives > 1) lives--;
        }

        // Every ward is timed now.
        //
        // Half the locks in this mod could be beaten by simply refusing to be
        // rushed: resonance in particular had no clock at all, so a careful
        // player could sit on one note for a minute. A budget per lock fixes all
        // fifteen at once, and it is the thing that makes the difficulty numbers
        // mean anything, because until now they only ever tightened windows the
        // player could wait out.
        clockTotal = timeBudget();
        clockLeft = msg.resumed
                ? Mth.clamp(msg.savedClockLeft, 0f, clockTotal)
                : clockTotal;
        clock = msg.resumed ? Mth.clamp(msg.savedHurriedClock, 0f, 1f) : 1f;
        // A rolled hybrid should usually be encountered rather than silently disappearing because
        // a strong player solved the host before the interruption's late timestamp. Very fast clears
        // can still outrun it; otherwise it arrives early enough to be a real part of the attempt.
        hybridTriggerAt = Math.max(2.8f, Math.min(6.5f, clockTotal * (0.16f + seedVariance(881) * 0.12f)));
        for (int i = 0; i < hybridRuneOptions.length; i++) hybridRuneOptions[i] = Math.floorMod((int)(seed + 17L * i + 881), Sigils.TILE.length);
        for (int i = 0; i < hybridRuneSequence.length; i++) hybridRuneSequence[i] = Math.floorMod((int)(seed >>> (i * 7)) + i * 3, hybridRuneOptions.length);
        hybridTarget = 0.34f + seedVariance(887) * 0.32f;

        if (charmTier == 1) modifierLabels.add("key bound");
        else if (charmTier >= 2) modifierLabels.add("heart bound");
        if (eldritch) {
            if (eldritchTotal > 0 && eldritchStage >= eldritchTotal) modifierLabels.add("eldritch · final mouth");
            else if (eldritchStage <= 1) modifierLabels.add("eldritch · signal drift");
            else if ((eldritchStage & 1) == 0) modifierLabels.add("eldritch · refraction");
            else modifierLabels.add("eldritch · time splice");
        }
        if (possessed) modifierLabels.add("possessed · " + possessedPatternName());
        if (rivalry) modifierLabels.add("rival hand");
        if (mutationLevel > 0) modifierLabels.add("mutation " + mutationLevel);
        if (affliction > 0) modifierLabels.add(switch (affliction) {
            case 1 -> "whispering"; case 2 -> "hollow"; case 3 -> "blind";
            case 4 -> "starved"; case 5 -> "blood-warm"; default -> "afflicted"; });
        if (living) modifierLabels.add("living ward");
        if (mercy) modifierLabels.add("mercy");
        else if (scarCount >= 2) modifierLabels.add("old ward");
        if (!unsigned && !introductoryWard()) modifierLabels.add(MakerMinigameBehavior.label(seed, false, gameType));
        if (MinigameCardEffects.has(cardMinigameMask, MinigameCardEffects.REVEAL_ONE)) modifierLabels.add("card · revealed tell");
        if (MinigameCardEffects.has(cardMinigameMask, MinigameCardEffects.FORGIVE_FIRST)) modifierLabels.add("card · first error pardoned");
        if (MinigameCardEffects.has(cardMinigameMask, MinigameCardEffects.MIRROR_UI)) modifierLabels.add("card · reversed face");
        if (MinigameCardEffects.has(cardMinigameMask, MinigameCardEffects.QUICKEN)) modifierLabels.add("card · loaded tempo");
        if (MinigameCardEffects.has(cardMinigameMask, MinigameCardEffects.BIND_ONE)) modifierLabels.add("card · binding clause");
        if (measureStage > 0) modifierLabels.add("measure " + romanTier(measureStage) + " · " + WardMeasureSystem.stageName(measureStage).toLowerCase(java.util.Locale.ROOT));
        if (measureStage == 3 && measureClause > 0) modifierLabels.add("verdict · " + WardMeasureSystem.clauseName(measureClause).toLowerCase(java.util.Locale.ROOT));
        if (masteryTier >= 2 && gameType != MinigameType.CTHULHUS_GAME) modifierLabels.add("mastery " + romanTier(masteryTier));
        if (corruptionVariant.active()) modifierLabels.add(corruptionVariant.label());
        if (deceptionMode != MinigameDeception.Mode.NONE) modifierLabels.add("deception · fractured witness");
        if ((hybridActive || hybridCompleted) && hybridMode != HybridRound.Mode.NONE) modifierLabels.add(hybridMode.label);
    }

    private int effectiveMinigameMask() {
        return cardMinigameMask | WardMeasureSystem.clauseMask(measureClause);
    }

    /**
     * Global onboarding keeps the very first stretch of Wardbound readable.
     *
     * <p>Do not tie this to Mastery I: with 26 disciplines that made ordinary
     * quirks/anomalies disappear for far too long, because every discipline had
     * to earn two wins + mastery XP before any handwriting could appear. Once
     * normal card hands enter the game, ordinary sub-features are allowed even
     * on an unfamiliar discipline; their intensity is still capped below.</p>
     */
    protected final boolean introductoryWard() {
        return gameType != MinigameType.CTHULHUS_GAME
                && resolvedWards < WardConfig.normalCardsAfterBeaten
                && !advancedNarrativeWard();
    }

    /** The player has not yet graduated this individual discipline out of tier 0. */
    protected final boolean unfamiliarDiscipline() {
        return gameType != MinigameType.CTHULHUS_GAME && masteryTier == 0 && !advancedNarrativeWard();
    }

    /** These are explicit late/narrative difficulty states, not random onboarding noise. */
    protected final boolean advancedNarrativeWard() {
        return unsigned || eldritch || possessed || gameType == MinigameType.CTHULHUS_GAME;
    }

    private static String romanTier(int tier) {
        return switch (tier) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> "0"; };
    }

    // ------------------------------------------------------------------ theme

    protected abstract Theme createTheme();

    protected final Theme theme() {
        if (cachedTheme == null) {
            Theme raw = createTheme();
            cachedTheme = WardConfig.accessibilityHighContrast ? Theme.highContrast(raw) : raw;
        }
        return cachedTheme;
    }

    protected int accent() {
        return theme().accent;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A live ward is an examination, not a preview screen. ESC is intercepted
     * in keyPressed and immediately resolves the current attempt as a failure,
     * so puzzle state can never be inspected, closed, and resumed for free.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return resolved;
    }

    @Override
    public void onClose() {
        if (!resolved) {
            markAbandonedAttempt();
            failOut();
            return;
        }
        super.onClose();
    }

    /**
     * Programmatic replacement (death screen, another GUI, disconnect teardown) does
     * not necessarily call {@link #onClose()}. Treat every unresolved removal as an
     * abandonment too. The normal fail path marks {@code resolved} before replacing
     * the screen, so this cannot double-submit a result.
     */
    @Override
    public void removed() {
        if (!resolved) {
            markAbandonedAttempt();
            sendFailureResult(false);
        }
        super.removed();
    }

    private void markAbandonedAttempt() {
        if (!modifierLabels.contains("ABANDONED")) modifierLabels.add("ABANDONED");
        mistakes++;
        if (hybridActive && hybridOutcome == 0) {
            hybridOutcome = 2;
            hybridCompleted = true;
            hybridActive = false;
        }
    }

    protected void succeed(float performance) {
        if (resolved) return;
        resolved = true;
        sealBreak = WardConfig.accessibilityReduceMotion ? 0f : 1.1f;
        Sfx.wardBreak(streak);
        Wardbound.CHANNEL.sendToServer(new MinigameResultPacket(pos, seed, true, lives, maxLives, performance,
                timeRemainingRatio(), elapsedSeconds(), mistakes, clientLatencyMs(), telemetryModifiers(),
                rapidMistakes, hybridOutcome));
        Minecraft.getInstance().setScreen(null);
    }

    protected void failOut() {
        if (resolved) return;
        sendFailureResult(true);
        Minecraft.getInstance().setScreen(null);
    }

    private void sendFailureResult(boolean playSound) {
        if (resolved) return;
        resolved = true;
        if (playSound) Sfx.wardFail();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            Wardbound.CHANNEL.sendToServer(new MinigameResultPacket(pos, seed, false, 0, maxLives, 0f,
                    timeRemainingRatio(), elapsedSeconds(), mistakes, clientLatencyMs(), telemetryModifiers(),
                    rapidMistakes, hybridOutcome));
        }
    }

    private float timeRemainingRatio() {
        return clockTotal <= 0.001f ? 1f : Mth.clamp(clockLeft / clockTotal, 0f, 1f);
    }

    private float elapsedSeconds() {
        return elapsedBeforeOpen + Math.max(0f, activeElapsedSeconds);
    }

    private int clientLatencyMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private String telemetryModifiers() {
        return String.join("|", modifierLabels);
    }

    protected boolean loseLife(String why) {
        mistakes++;
        long mistakeNow = System.currentTimeMillis();
        if (lastMistakeMillis > 0L && mistakeNow - lastMistakeMillis <= 650L) rapidMistakes++;
        lastMistakeMillis = mistakeNow;
        if (MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.FORGIVE_FIRST)
                && !cardForgivenessSpent) {
            cardForgivenessSpent = true;
            Sfx.markBad();
            showBanner("The card crosses out the first error", 0xFFD7C4A2, 980);
            return true;
        }
        lives--;
        if (possessed) possessedRage = Math.min(3.0f, possessedRage + 1.0f);
        Sfx.lifeLost();
        if (lives <= 0) {
            failOut();
            return false;
        }
        showBanner(why, COL_BAD, 950);
        return true;
    }

    protected void showBanner(String text, int color, int ms) {
        if (WardConfig.accessibilityColorIndependentFeedback) {
            if (color == COL_BAD || color == theme().bad) text = "X  " + text;
            else if (color == COL_GOOD || color == theme().good) text = "OK  " + text;
        }
        banner = text;
        bannerColor = color;
        bannerUntil = System.currentTimeMillis() + ms;
    }

    // ------------------------------------------------------------------ simulation clock

    /**
     * Advance the game by {@code dt} seconds. Called from render and, crucially, from every input
     * event so the state a click is judged against is current rather than one frame stale.
     */
    protected abstract void step(float dt);

    /**
     * A screen may briefly freeze authored gameplay while it presents a phase title,
     * memory witness, or equivalent mandatory read. The global clock and corruption
     * cadence must not consume that presentation time.
     */
    protected boolean presentationPauseActive() { return false; }

    /** Consumes the time since the last call and runs {@link #step(float)} with it. */
    protected final void catchUp() {
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt <= 0f) return;
        // A long stall (window drag, chunk load) should not teleport the dial across the board.
        if (dt > 0.25f) dt = 0.25f;
        tickMasterMotif(dt);
        boolean presentationPause = presentationPauseActive();
        if (!presentationPause) {
            activeElapsedSeconds += dt;
            tickHybrid(dt);
            tickDeception(dt);
        }
        tickAnomalies(dt);
        if (!presentationPause) tickCorruption(dt);
        if (!resolved && !hybridActive) step(dt * gameSpeedScale * corruptionSpeedScale());
    }

    /** Three short notes near the start of a signed ward. */
    private void tickMasterMotif(float dt) {
        if (!WardConfig.masterAudioMotifsEnabled || !masterPhase || unsigned || motifNote >= 3) return;
        motifElapsed += dt;
        float[] at = {0.06f, 0.22f, 0.41f};
        if (motifNote < 3 && motifElapsed >= at[motifNote]) {
            Sfx.masterMotifNote(seed, motifNote);
            motifNote++;
        }
    }

    /**
     * Rolls the chest's anomalies.
     *
     * <p>Each is checked independently against its own chance, then the result is
     * capped: two unusual things at once is a story, three is soup, and a chest
     * that is strange in every direction stops reading as strange at all.
     */
    /**
     * Whether an anomaly means anything on this particular lock.
     *
     * <p>Anomalies were written on the assumption that a condition which suits
     * one lock suits all fifteen, and it does not. A clock on top of a lock that
     * is already over in twenty seconds is not pressure, it is a coin flip, and
     * hiding the hint line on a lock whose rules are visible on screen costs
     * nothing at all. Screens turn off the ones that do not land.
     */
    protected boolean allows(Anomaly a) {
        return true;
    }

    private void rollAnomalies() {
        if (!WardConfig.anomaliesEnabled || introductoryWard()) return;
        java.util.List<Anomaly> hits = new java.util.ArrayList<>();
        float learningChance = advancedNarrativeWard() ? 1f
                : (masteryTier <= 0 ? 0.30f : masteryTier == 1 ? 0.48f : masteryTier == 2 ? 0.76f : 1f);
        for (Anomaly a : Anomaly.values()) {
            if (!allows(a)) continue;
            // An unsigned seal takes every hostile condition going. It is not
            // offered the kind one.
            if (unsigned ? a != Anomaly.SPENT
                    : seedVariance(120 + a.ordinal() * 7) < anomalyChance(a) * learningChance) {
                hits.add(a);
            }
        }
        int cap = unsigned ? 2 : (value >= WardConfig.anomalyTwoThreshold ? 2 : 1);
        if (!advancedNarrativeWard() && masteryTier <= 2) cap = Math.min(cap, 1);
        if (possessed) cap = Math.min(2, cap + 1);
        while (hits.size() > cap) {
            hits.remove(Math.round(seedVariance(151) * (hits.size() - 1)));
        }
        // Being hurried and being given extra forgiveness at once is not a
        // combination, it is two settings arguing.

        anomalies.addAll(hits);
        for (Anomaly a : anomalies) modifierLabels.add(a.label);

        if (anomalies.contains(Anomaly.HURRIED)) {
            // Long enough that it is never the reason a competent player loses a
            // life on the first pass, short enough to be felt.
            float seconds = Math.max(WardConfig.hurriedMinSeconds,
                    WardConfig.hurriedBaseSeconds / Math.max(0.6f, difficulty));
            clockDrain = 1f / seconds;
        }
    }

    private float anomalyChance(Anomaly a) {
        return switch (a) {
            case HURRIED -> WardConfig.anomalyHurriedChance;
            case SHROUDED -> WardConfig.anomalyShroudedChance;
            case GUTTERING -> WardConfig.anomalyGutteringChance;
            case UNLIT -> WardConfig.anomalyUnlitChance;
            case SPENT -> WardConfig.anomalySpentChance;
        };
    }

    /**
     * How long this ward gives you, in seconds.
     *
     * <p>Deliberately tight. A budget that is comfortable is not a budget; it is
     * a number in the corner. Screens whose locks genuinely need longer override
     * this, and the hurried anomaly cuts it further on top.
     */
    protected float timeBudget() {
        // Difficulty used to divide the clock, which is backwards.
        //
        // Every one of these locks already gets harder with difficulty - faster
        // needles, tighter windows, more pins - and then the End, at 1.55, was
        // handed a third less time to do the harder version in. Two punishments
        // for one number. The clock now grows slightly with difficulty instead,
        // so a hard ward is hard because the lock is hard and not because you
        // were also being rushed.
        float base = WardConfig.wardClockBaseSeconds + value * WardConfig.wardClockValueSeconds;
        base *= 1f + (Mth.clamp(difficulty, 0.6f, 2.2f) - 1f)
                * WardConfig.wardClockDifficultyCompensation;
        if (has(Anomaly.HURRIED)) base *= WardConfig.hurriedTimeScale;

        // The heart holds the ward open a little. Flat seconds rather than a
        // multiplier, so it is worth most on exactly the short, tight locks
        // where a multiplier would have given almost nothing.
        if (heartHint()) base += WardConfig.heartWardSeconds;
        // Flat seconds, added after the per-lock multiplier, so an ember is
        // worth the same everywhere. A multiplier would have been worth almost
        // nothing on the drum and a great deal on augury, which is the opposite
        // of how a burning coal ought to behave.
        base *= MakerMinigameBehavior.timeScale(makerStyle, gameType);
        return Math.max(WardConfig.wardClockMinimumSeconds, base * gameTimeScale) + extraSeconds;
    }

    protected boolean has(Anomaly a) {
        return anomalies.contains(a);
    }

    protected final boolean cardRevealsOne() {
        return MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.REVEAL_ONE);
    }

    protected final boolean cardBindsOne() {
        return MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.BIND_ONE);
    }

    protected final boolean makerBindsLogic() {
        return MakerMinigameBehavior.bindingLogic(makerStyle, gameType);
    }

    /** Seconds of input forgiveness, from the config, doubled on a keen ward. */
    protected float grace() {
        float base = Math.max(0, WardConfig.inputGraceMs) / 1000f;
        float cardScale = 1f;
        if (MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.REVEAL_ONE)) cardScale *= 1.08f;
        if (MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.BIND_ONE)) cardScale *= 0.92f;
        return base * knownHandBonus() * gameInputWindowScale
                * MakerMinigameBehavior.graceScale(makerStyle, gameType) * cardScale;
    }

    /**
     * The edge you get from having broken this house's work before.
     *
     * <p>Deliberately small and hard capped at a quarter. It should feel like
     * recognising a habit, not like the lock giving up; a bonus that grows
     * without a ceiling turns a long save into a mod that plays itself.
     */
    protected float knownHandBonus() {
        return 1f + Math.min(WardConfig.knownHandBonusCap,
                familiarity * WardConfig.knownHandBonusPerSeal);
    }

    /** Two known-hand thresholds worth telling the player about. */
    protected String knownHandNote() {
        if (!WardConfig.masterSignaturesEnabled || !masterPhase) return null;
        MasterSignature signature = MasterSignature.ofSeed(seed);
        if (familiarity >= WardConfig.masterSignatureKnowAfter)
            return signature.label() + " \u00b7 you know this hand well";
        if (familiarity >= WardConfig.masterSignatureRevealAfter)
            return signature.label() + " \u00b7 you have seen this hand before";
        return null;
    }

    /** Drives the anomalies that need time. Runs whether or not the lock is stepping. */
    private void tickAnomalies(float dt) {
        if (sealBreak > 0f) sealBreak -= dt;

        if (clockTotal > 0f && !resolved && !hybridActive && !presentationPauseActive()) {
            clockLeft -= dt;
            if (clockLeft <= 0f) {
                clockLeft = 0f;
                Sfx.wrenchSlip();
                showBanner("The ward closed before you did", COL_BAD, 1400);
                failOut();
            }
        }
        if (has(Anomaly.SHROUDED)) shroudT += dt;
        if (has(Anomaly.GUTTERING)) gutter += dt;
        // Mandatory presentation pauses may keep cosmetic drift alive, but no
        // timed gameplay anomaly is allowed to tax the player behind the title.
        if (presentationPauseActive()) return;
        if (!has(Anomaly.HURRIED) || resolved || hybridActive) return;

        clock -= clockDrain * dt;
        if (clock <= 0f) {
            clock = 1f;
            Sfx.markBad();
            loseLife("The ward would not wait");
        }
    }

    /**
     * Gameplay cadence for corrupted wards. Possessed wards breathe like a bad
     * engine: they seize, then lurch. Eldritch stages have different rules -
     * drift first, refraction in the middle, and a hush/surge cycle at the maw.
     */
    private void tickCorruption(float dt) {
        if (resolved || hybridActive) return;
        corruptionTime += dt;
        float rageFloor = possessed ? Math.min(1.5f, mistakes * 0.22f) : 0f;
        possessedRage = Math.max(rageFloor, possessedRage - dt * 0.22f);

        if (possessed) {
            float period = possessedPeriod();
            int beat = (int) Math.floor((corruptionTime + seedVariance(704) * period) / period);
            if (beat != lastPossessedBeat) {
                if (lastPossessedBeat >= 0) {
                    Sfx.play(WardSounds.HEARTBEAT, 0.34f, 0.62f + seedVariance(705) * 0.12f);
                    showBanner("Something else takes the mechanism", Painter.lighten(0xFF9A4E83, 0.12f), 620);
                }
                lastPossessedBeat = beat;
            }
        }

        if (eldritch) {
            float period = eldritchPeriod();
            int beat = (int) Math.floor((corruptionTime + seedVariance(719) * period) / period);
            if (beat != lastEldritchBeat) {
                if (lastEldritchBeat >= 0 && eldritchStage > 1) {
                    Sfx.play(WardSounds.EYE_OPEN, 0.28f, eldritchFinale() ? 0.58f : 0.76f);
                }
                lastEldritchBeat = beat;
            }
        }
    }

    private int possessedPattern() {
        return Math.floorMod((int) (seed ^ (seed >>> 32)), 3);
    }

    private String possessedPatternName() {
        return switch (possessedPattern()) {
            case 1 -> "broken metronome";
            case 2 -> "hungry pull";
            default -> "lurching pulse";
        };
    }

    private float possessedPeriod() {
        return switch (possessedPattern()) {
            case 1 -> 5.5f + seedVariance(703) * 1.5f;
            case 2 -> 7.0f + seedVariance(703) * 1.8f;
            default -> 6.4f + seedVariance(703) * 2.2f;
        };
    }

    private float eldritchPeriod() {
        if (eldritchFinale()) return 4.65f + seedVariance(718) * 0.9f;
        return 5.6f + seedVariance(717) * 1.4f;
    }

    private boolean eldritchFinale() {
        return eldritch && eldritchTotal > 0 && eldritchStage >= eldritchTotal;
    }

    private float possessedBeatPhase() {
        float period = possessedPeriod();
        float shifted = corruptionTime + seedVariance(704) * period;
        return shifted - (float) Math.floor(shifted / period) * period;
    }

    private float eldritchBeatPhase() {
        float period = eldritchPeriod();
        float shifted = corruptionTime + seedVariance(719) * period;
        return shifted - (float) Math.floor(shifted / period) * period;
    }

    private boolean possessedSeizing() {
        if (!possessed) return false;
        float ph = possessedBeatPhase();
        return switch (possessedPattern()) {
            case 1 -> ph < 1.55f && ((int) (ph / 0.19f) & 1) == 0;
            case 2 -> ph < 0.62f;
            default -> ph < 0.72f;
        };
    }

    private boolean possessedSurging() {
        if (!possessed) return false;
        float ph = possessedBeatPhase();
        return switch (possessedPattern()) {
            case 1 -> ph < 1.55f && ((int) (ph / 0.19f) & 1) == 1;
            case 2 -> ph >= 0.62f && ph < 2.75f;
            default -> ph >= 0.72f && ph < 1.92f;
        };
    }

    private boolean eldritchRefractionActive() {
        if (!eldritch || eldritchFinale() || eldritchStage <= 1 || (eldritchStage & 1) != 0) return false;
        return eldritchBeatPhase() < 1.45f;
    }

    private boolean eldritchTimeSplice() {
        return eldritch && !eldritchFinale() && eldritchStage > 1 && (eldritchStage & 1) != 0;
    }

    private boolean eldritchHushActive() {
        return eldritchFinale() && eldritchBeatPhase() < 0.72f;
    }

    private boolean eldritchMawSurge() {
        if (!eldritchFinale()) return false;
        float ph = eldritchBeatPhase();
        return ph >= 0.72f && ph < 1.78f;
    }

    private float corruptionSpeedScale() {
        float scale = 1f;
        if (possessed) {
            if (possessedSeizing()) {
                scale *= possessedPattern() == 1 ? 0.24f : 0.38f;
            } else if (possessedSurging()) {
                if (possessedPattern() == 2) {
                    float p = Mth.clamp((possessedBeatPhase() - 0.62f) / 2.13f, 0f, 1f);
                    scale *= 1.08f + p * 0.70f + Math.min(0.14f, possessedRage * 0.04f);
                } else {
                    scale *= 1.52f + Math.min(0.18f, possessedRage * 0.05f);
                }
            } else scale *= 1.0f + Math.min(0.10f, possessedRage * 0.03f);
        }
        if (eldritch) {
            if (eldritchFinale()) {
                if (eldritchHushActive()) scale *= 0.22f;
                else if (eldritchMawSurge()) scale *= 1.68f;
                else scale *= 1.08f;
            } else if (eldritchStage <= 1) {
                // A slow, legible drift. The player can feel the cadence moving
                // without the hit windows teleporting between frames.
                scale *= 0.78f + 0.44f * (0.5f + 0.5f * Mth.sin(corruptionTime * 1.43f + seedVariance(715) * 5f));
            } else if (eldritchTimeSplice()) {
                // A middle-stage casting can splice time instead of space: half
                // second slow/fast cuts with no mirrored cursor.
                int slice = (int) (corruptionTime / 0.52f);
                scale *= (slice & 1) == 0 ? 0.62f : 1.46f;
            } else {
                // Refraction is calmer while the input is mirrored, then snaps
                // forward when the image rejoins itself.
                scale *= eldritchRefractionActive() ? 0.88f : 1.27f;
            }
        }
        scale *= MinigameCorruption.speedScale(corruptionVariant, seed, corruptionTime, mistakes);
        scale *= MakerMinigameBehavior.speedScale(makerStyle, gameType, seed, corruptionTime);
        if (MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.QUICKEN)) scale *= 1.12f;
        return Mth.clamp(scale, 0.18f, 2.10f);
    }

    private boolean corruptedKey(int key) {
        return isActionKey(key)
                || key == 65 || key == 68 || key == 83 || key == 87
                || key == 262 || key == 263 || key == 264 || key == 265;
    }

    private boolean corruptionMirrorActive() {
        return MinigameCorruption.mirrorActive(corruptionVariant, seed, corruptionTime);
    }

    private boolean makerMirrorActive() {
        return MakerMinigameBehavior.mirrorActive(makerStyle, gameType, seed, corruptionTime);
    }

    private boolean cardMirrorActive() {
        return MinigameCardEffects.has(effectiveMinigameMask(), MinigameCardEffects.MIRROR_UI);
    }

    private boolean effectiveMirrorActive() {
        return corruptionMirrorActive() ^ makerMirrorActive() ^ cardMirrorActive();
    }

    private boolean presentationMirrorActive() {
        return makerMirrorActive() ^ cardMirrorActive();
    }

    private double refractMouseX(double mx) {
        double delivered = mx;
        if (possessed && possessedPattern() == 2 && possessedSurging()) {
            // Hungry Pull physically drags the delivered input toward the ward's
            // mouth. The cursor itself need not teleport; the rim telegraph above
            // tells the player that the mechanism is stealing leverage.
            float pull = 0.10f + 0.08f * Mth.clamp((possessedBeatPhase() - 0.62f) / 2.13f, 0f, 1f);
            delivered = Mth.lerp(pull, (float) delivered, (float) centerX());
        }
        if (eldritchRefractionActive() || effectiveMirrorActive()) {
            int x = left();
            delivered = x + panelWidth() - (delivered - x);
        }
        if (corruptionVariant.profile() == MinigameCorruption.Profile.ECHO_GLASS) {
            float drift = WardConfig.accessibilityReduceMotion ? 6f
                    : Mth.sin(corruptionTime * 1.85f + (seed & 31)) * 13f;
            delivered = Mth.clamp((float) delivered + drift, left() + 3f, left() + panelWidth() - 3f);
        }
        return delivered;
    }

    private int refractKey(int key) {
        boolean possessedReverse = possessed && possessedPattern() == 1 && possessedSurging();
        if (!eldritchRefractionActive() && !effectiveMirrorActive() && !possessedReverse) return key;
        return switch (key) {
            case 65 -> 68;   // A -> D
            case 68 -> 65;   // D -> A
            case 263 -> 262; // left -> right
            case 262 -> 263; // right -> left
            default -> key;
        };
    }

    private boolean corruptionInputSuppressed() {
        return MinigameCorruption.blackoutActive(corruptionVariant, seed, corruptionTime);
    }

    // ------------------------------------------------------------------ input

    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        catchUp();
        if (!resolved && hybridActive) return (button == 0 || button == 1) ? hybridClick(mx, my, button) : true;
        if (!resolved && corruptionInputSuppressed() && (button == 0 || button == 1)) return true;
        if (!resolved && possessedSeizing() && (button == 0 || button == 1)) {
            showBanner("POSSESSION · INPUT SEIZED", Painter.lighten(0xFF9A4E83, 0.18f), 260);
            return true;
        }
        if (!resolved && eldritchHushActive() && (button == 0 || button == 1)) {
            if (loseLife("The mouth answered before you did"))
                showBanner("DO NOT ANSWER THE HUSH", COL_BAD, 760);
            return true;
        }
        if (!resolved && onClick(refractMouseX(mx), my, button)) {
            deliveredMouseButtons.add(button);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        catchUp();

        // A release paired with a host-minigame press must always reach that
        // minigame, even if blackout, possession, or a hybrid interruption
        // appeared meanwhile. The release is cleanup, not a fresh action.
        boolean wasDelivered = deliveredMouseButtons.remove(button);
        if (wasDelivered) {
            onRelease(refractMouseX(mx), my, button);
            return true;
        }

        if (!resolved && hybridActive) return (button == 0 || button == 1) ? hybridRelease(button) : true;
        if (!resolved && corruptionInputSuppressed() && (button == 0 || button == 1)) return true;
        if (!resolved && possessedSeizing() && (button == 0 || button == 1)) return true;
        if (!resolved && eldritchHushActive() && (button == 0 || button == 1)) return true;
        if (!resolved && onRelease(refractMouseX(mx), my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public final boolean keyPressed(int key, int scan, int mods) {
        catchUp();
        if (!resolved && key == 256) {
            markAbandonedAttempt();
            failOut();
            return true;
        }
        if (!resolved && hybridActive) {
            return hybridKey(key); // No host-minigame key is allowed to leak through the interruption.
        }
        if (!resolved && corruptionInputSuppressed() && corruptedKey(key)) return true;
        if (!resolved && eldritchHushActive() && corruptedKey(key)) {
            if (loseLife("The mouth answered before you did"))
                showBanner("DO NOT ANSWER THE HUSH", COL_BAD, 760);
            return true;
        }
        if (!resolved && possessedSeizing() && corruptedKey(key)) {
            showBanner("POSSESSION · INPUT SEIZED", Painter.lighten(0xFF9A4E83, 0.18f), 260);
            return true;
        }
        if (!resolved) {
            int delivered = refractKey(key);
            if (onKey(delivered)) {
                deliveredKeys.put(key, delivered);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public final boolean keyReleased(int key, int scan, int mods) {
        catchUp();
        if (!resolved && key == 256) return true;

        Integer paired = deliveredKeys.remove(key);
        if (paired != null) {
            // Same invariant as mouse input: a key released after an interruption
            // still has to clear the host lock's held state.
            onKeyRelease(paired);
            return true;
        }
        if (!resolved && hybridActive) {
            return hybridKeyRelease(key);
        }
        if (!resolved && onKeyRelease(key)) return true;
        return super.keyReleased(key, scan, mods);
    }

    protected boolean onClick(double mx, double my, int button) {
        return false;
    }

    protected boolean onRelease(double mx, double my, int button) {
        return false;
    }

    protected boolean onKey(int key) {
        return false;
    }

    protected boolean onKeyRelease(int key) {
        return false;
    }

    /** Space or enter: the keyboard ways every lock accepts a "now". */
    protected static boolean isActionKey(int key) {
        return key == 32 || key == 257 || key == 335;
    }

    // ------------------------------------------------------------------ motion

    /**
     * Damped 0..1 wave. Everything that idles uses this instead of a raw sine so a single config
     * value controls how much the interface moves. At {@code pulse_intensity = 0} it returns a flat
     * 0.5 and the UI stops breathing entirely.
     */
    /**
     * Whether the heart is riding this lock and is therefore owed a tell.
     *
     * <p>The heart costs twice as much per use as the key and lasts half as long,
     * and until now it bought nothing the key did not, only more of it. Each lock
     * that can afford to gives it one concrete piece of the answer instead. What
     * that is depends on the lock; see the screens that override it.
     */
    protected final boolean heartHint() {
        return charmTier >= 2;
    }

    protected float breath(float speedHz) {
        if (WardConfig.accessibilityReduceMotion) return 0.5f;
        float k = Mth.clamp(WardConfig.pulseIntensity * WardConfig.accessibilityGuiAnimationIntensity, 0f, 1f);
        if (k <= 0.001f) return 0.5f;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        float raw = (float) (0.5 + 0.5 * Math.sin(t * speedHz * Math.PI * 2));
        return 0.5f + (raw - 0.5f) * k;
    }

    /** 0..1 ease-out of the panel's opening animation. */
    protected float introT() {
        if (WardConfig.accessibilityReduceMotion || WardConfig.accessibilityGuiAnimationIntensity <= 0.001f) return 1f;
        long age = System.currentTimeMillis() - openedAtMillis;
        float t = Mth.clamp(age / (float) INTRO_MS, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }

    protected void burst(int x, int y, int color) {
        if (WardConfig.accessibilityReduceFlashing || WardConfig.accessibilityReduceMotion) return;
        bursts.add(new Burst(x, y, color, System.currentTimeMillis()));
    }

    private static final int BURST_MS = 280;

    private void renderBursts() {
        if (bursts.isEmpty()) return;
        long now = System.currentTimeMillis();
        bursts.removeIf(b -> now - b.start() > BURST_MS);
        for (Burst b : bursts) {
            float t = (now - b.start()) / (float) BURST_MS;
            int r = 5 + Math.round(t * 15);
            int alpha = Math.round((1f - t) * (1f - t) * 0xB0);
            p.ringThick(b.x(), b.y(), r, 2, Painter.withAlpha(b.color(), alpha));
        }
    }


    // ------------------------------------------------------------------ v38 adaptive profile tests

    private void tickDeception(float dt) {
        if (resolved || deceptionMode == MinigameDeception.Mode.NONE || hybridActive) return;
        deceptionTime += dt;
        float period = 5.2f + seedVariance(903) * 2.1f;
        int beat = (int)Math.floor((deceptionTime + seedVariance(904) * period) / period);
        if (beat == lastDeceptionBeat) return;
        lastDeceptionBeat = beat;
        if (deceptionTime < 2.2f) return;
        deceptionFlashUntil = System.currentTimeMillis() + 720L;
        switch (deceptionMode) {
            case FALSE_CHIME -> { deceptionFlashType = 1; Sfx.markBad(); }
            case GHOST_CRACK -> { deceptionFlashType = 3; Sfx.play(WardSounds.WRENCH_SLIP, 0.22f, 1.18f); }
            case FALSE_CONFIRM -> { deceptionFlashType = 2; Sfx.mark(Math.max(1, progress + 1)); }
            default -> deceptionFlashType = 0;
        }
    }

    private void startHybrid() {
        if (hybridMode == HybridRound.Mode.NONE || hybridCompleted || hybridActive) return;
        hybridActive = true;
        hybridTriggered = true;
        hybridTimer = 0f;
        hybridStep = hybridMode == HybridRound.Mode.LATTICE_KNOT ? hybridLatticeStartMask() : 0;
        hybridGauge = 0f;
        hybridHeld = false;
        if (!modifierLabels.contains(hybridMode.label)) modifierLabels.add(hybridMode.label);
        Sfx.play(WardSounds.EYE_OPEN, 0.28f, 1.24f);
        showBanner("A second mechanism enters the seal", Painter.lighten(theme().accent, 0.35f), 1050);
    }

    private void tickHybrid(float dt) {
        if (resolved || hybridMode == HybridRound.Mode.NONE || hybridCompleted) return;
        if (!hybridActive) {
            if (!hybridTriggered && elapsedSeconds() >= hybridTriggerAt) startHybrid();
            return;
        }
        hybridTimer += dt;
        if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP) {
            float rate = 0.46f + difficulty * 0.05f;
            if (hybridHeld) hybridGauge = Math.min(1.08f, hybridGauge + dt * rate);
            else hybridGauge = Math.max(0f, hybridGauge - dt * 0.13f);
        }
        float limit = switch (hybridMode) {
            case RUNE_ECHO -> 7.0f;
            case LATTICE_KNOT -> 8.0f;
            default -> 5.4f;
        };
        if (hybridTimer > limit) finishHybrid(false, "The borrowed mechanism closes");
    }

    private void finishHybrid(boolean success, String failure) {
        if (!hybridActive) return;
        hybridActive = false;
        hybridCompleted = true;
        hybridOutcome = success ? 1 : 2;
        if (success) {
            Sfx.mark(Math.max(2, progress + 2));
            showBanner("The borrowed mechanism yields", COL_GOOD, 900);
            // The host clock was frozen during the interruption; no hidden time tax is applied.
        } else {
            loseLife(failure);
        }
    }

    private boolean hybridClick(double mx, double my, int button) {
        if (hybridMode == HybridRound.Mode.RUNE_ECHO) {
            if (hybridTimer < 1.35f) return true;
            int y = contentCenterY() + 22;
            int start = centerX() - 63;
            for (int i = 0; i < 4; i++) {
                int cx = start + i * 42;
                if (Math.abs(mx - cx) <= 15 && Math.abs(my - y) <= 15) {
                    if (i == hybridRuneSequence[Math.min(hybridStep, 2)]) {
                        hybridStep++;
                        Sfx.mark(hybridStep);
                        if (hybridStep >= 3) finishHybrid(true, "");
                    } else finishHybrid(false, "The rune echo was answered out of order");
                    return true;
                }
            }
            return true;
        }
        if (hybridMode == HybridRound.Mode.LATTICE_KNOT) {
            int cell = hybridLatticeCellAt(mx, my);
            if (cell >= 0) {
                hybridStep = hybridLatticeToggle(hybridStep, cell);
                Sfx.mark(1 + Integer.bitCount(hybridStep));
                if (hybridStep == 0) finishHybrid(true, "");
            }
            return true;
        }
        if (hybridMode == HybridRound.Mode.PARALLAX_SNAP) {
            float phase = hybridTimer * (2.15f + difficulty * 0.12f) + seedVariance(911) * 6f;
            float marker = 0.5f + 0.5f * Mth.sin(phase);
            if (Math.abs(marker - hybridTarget) <= 0.085f * knownHandBonus()) finishHybrid(true, "");
            else finishHybrid(false, "The borrowed depth would not align");
            return true;
        }
        if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP) {
            hybridHeld = true;
            return true;
        }
        return true;
    }

    private boolean hybridRelease(int button) {
        if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP) {
            hybridHeld = false;
            if (Math.abs(hybridGauge - hybridTarget) <= 0.095f * knownHandBonus()) finishHybrid(true, "");
            else finishHybrid(false, hybridGauge > hybridTarget ? "The borrowed measure overshot" : "The borrowed measure fell short");
        }
        return true;
    }

    private boolean hybridKey(int key) {
        if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP && isActionKey(key)) { hybridHeld = true; return true; }
        if (hybridMode == HybridRound.Mode.PARALLAX_SNAP && isActionKey(key))
            return hybridClick(centerX(), contentCenterY(), 0);
        if (hybridMode == HybridRound.Mode.LATTICE_KNOT && key >= 49 && key <= 52) {
            hybridStep = hybridLatticeToggle(hybridStep, key - 49);
            Sfx.mark(1 + Integer.bitCount(hybridStep));
            if (hybridStep == 0) finishHybrid(true, "");
            return true;
        }
        if (hybridMode == HybridRound.Mode.RUNE_ECHO && hybridTimer >= 1.35f && key >= 49 && key <= 52) {
            int choice = key - 49;
            if (choice == hybridRuneSequence[Math.min(hybridStep, 2)]) {
                hybridStep++;
                Sfx.mark(hybridStep);
                if (hybridStep >= 3) finishHybrid(true, "");
            } else finishHybrid(false, "The rune echo was answered out of order");
            return true;
        }
        return true;
    }

    private boolean hybridKeyRelease(int key) {
        if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP && isActionKey(key)) return hybridRelease(0);
        return true;
    }

    /** 2x2 Lights-Out micro proof. The start state is created only by legal presses, so it is always solvable. */
    private int hybridLatticeStartMask() {
        int state = 0;
        int presses = 1 + Math.floorMod((int)(seed ^ (seed >>> 29) ^ 0x4C415454L), 3);
        int prev = -1;
        for (int i = 0; i < presses; i++) {
            int cell = Math.floorMod((int)(seed >>> (i * 11 + 3)) + i * 3, 4);
            if (cell == prev) cell = (cell + 1 + i) & 3;
            state = hybridLatticeToggle(state, cell);
            prev = cell;
        }
        return state == 0 ? hybridLatticeToggle(0, Math.floorMod((int)seed, 4)) : state;
    }

    private int hybridLatticeToggle(int state, int cell) {
        int row = cell >> 1, col = cell & 1;
        state ^= 1 << cell;
        if (col > 0) state ^= 1 << (cell - 1);
        if (col < 1) state ^= 1 << (cell + 1);
        if (row > 0) state ^= 1 << (cell - 2);
        if (row < 1) state ^= 1 << (cell + 2);
        return state & 0xF;
    }

    private int hybridLatticeCellAt(double mx, double my) {
        int size = 34, gap = 10;
        int gx = centerX() - size - gap / 2;
        int gy = contentCenterY() - size - gap / 2 + 10;
        for (int cell = 0; cell < 4; cell++) {
            int col = cell & 1, row = cell >> 1;
            int x = gx + col * (size + gap), y = gy + row * (size + gap);
            if (mx >= x && mx < x + size && my >= y && my < y + size) return cell;
        }
        return -1;
    }

    private void renderHybrid(GuiGraphics g, int mouseX, int mouseY) {
        if (!hybridActive) return;
        int x = left() + 18, y = contentTop() + 12, w = panelWidth() - 36, h = contentBottom() - contentTop() - 24;
        p.roundRect(x, y, w, h, Painter.withAlpha(0x05040A, 0xEE));
        p.roundOutline(x, y, w, h, Painter.withAlpha(theme().accentLite, 0xD0));
        smallCentered(g, "CROSS-DISCIPLINE INTERRUPTION", centerX(), y + 8, theme().accentLite, 0.8f);
        if (hybridMode == HybridRound.Mode.RUNE_ECHO) {
            if (hybridTimer < 1.35f) {
                smallCentered(g, "remember the borrowed runes", centerX(), y + 24, theme().dim, 0.8f);
                for (int i = 0; i < 3; i++) {
                    int opt = hybridRuneSequence[i];
                    Sigils.drawCentered(p, Sigils.TILE[hybridRuneOptions[opt]], centerX() - 42 + i * 42, y + 62, 2,
                            theme().accentLite, theme().accent, theme().panelDark);
                }
            } else {
                smallCentered(g, "answer them in order · " + hybridStep + "/3", centerX(), y + 24, theme().dim, 0.8f);
                int cy = contentCenterY() + 22, start = centerX() - 63;
                for (int i = 0; i < 4; i++) {
                    int cx = start + i * 42;
                    p.ringThick(cx, cy, 14, 1, Painter.withAlpha(theme().accent, 0xB0));
                    Sigils.drawCentered(p, Sigils.TILE[hybridRuneOptions[i]], cx, cy, 1,
                            theme().accentLite, theme().accent, theme().panelDark);
                }
            }
        } else if (hybridMode == HybridRound.Mode.LATTICE_KNOT) {
            smallCentered(g, "extinguish the borrowed proof · each tile turns its neighbours", centerX(), y + 24, theme().dim, 0.78f);
            int size = 34, gap = 10;
            int gx = centerX() - size - gap / 2;
            int gy = contentCenterY() - size - gap / 2 + 10;
            for (int cell = 0; cell < 4; cell++) {
                int col = cell & 1, row = cell >> 1;
                int tx = gx + col * (size + gap), ty = gy + row * (size + gap);
                boolean lit = (hybridStep & (1 << cell)) != 0;
                p.roundRect(tx, ty, size, size, lit ? Painter.withAlpha(theme().accent, 0x72) : Painter.withAlpha(theme().panelDark, 0xD8));
                p.roundOutline(tx, ty, size, size, lit ? theme().accentLite : theme().faint);
                Sigils.drawCentered(p, Sigils.TILE[hybridRuneOptions[cell]], tx + size / 2, ty + size / 2, 1,
                        lit ? theme().accentLite : theme().dim, theme().accent, theme().panelDark);
            }
            smallCentered(g, "CLICK / 1–4 · DARKEN ALL FOUR", centerX(), gy + size * 2 + gap + 12, theme().accentLite, 0.76f);
        } else if (hybridMode == HybridRound.Mode.PARALLAX_SNAP) {
            smallCentered(g, "align the moving shard with the witness notch", centerX(), y + 24, theme().dim, 0.8f);
            int bx = x + 28, bw = w - 56, by = contentCenterY() + 10;
            p.rect(bx, by, bx + bw, by + 2, theme().faint);
            int tx = bx + Math.round(hybridTarget * bw);
            p.line(tx, by - 13, tx, by + 14, 2, theme().accentLite);
            float marker = 0.5f + 0.5f * Mth.sin(hybridTimer * (2.15f + difficulty * 0.12f) + seedVariance(911) * 6f);
            int mx = bx + Math.round(marker * bw);
            p.disc(mx, by + 1, 6, theme().accent);
            smallCentered(g, "CLICK / SPACE", centerX(), by + 28, theme().accentLite, 0.8f);
        } else if (hybridMode == HybridRound.Mode.PRESSURE_CLAMP) {
            smallCentered(g, "hold, then release inside the borrowed band", centerX(), y + 24, theme().dim, 0.8f);
            int bx = x + 28, bw = w - 56, by = contentCenterY() + 8;
            int lo = bx + Math.round((hybridTarget - 0.095f) * bw), hi = bx + Math.round((hybridTarget + 0.095f) * bw);
            p.rect(bx, by, bx + bw, by + 10, Painter.withAlpha(theme().faint, 0x55));
            p.rect(lo, by - 2, hi, by + 12, Painter.withAlpha(COL_GOOD, 0x60));
            p.rect(bx, by, bx + Math.round(Mth.clamp(hybridGauge, 0f, 1f) * bw), by + 10, theme().accent);
            smallCentered(g, "HOLD CLICK / SPACE", centerX(), by + 28, theme().accentLite, 0.8f);
        }
    }

    private void renderDeception(GuiGraphics g) {
        if (deceptionMode == MinigameDeception.Mode.NONE) return;
        if (System.currentTimeMillis() < deceptionFlashUntil) {
            if (deceptionFlashType == 1) {
                p.roundOutline(left() + 12, contentTop() + 6, panelWidth() - 24, contentBottom() - contentTop() - 12,
                        Painter.withAlpha(COL_BAD, 0x88));
            } else if (deceptionFlashType == 2) {
                p.ringThick(centerX(), contentTop() + 18, 9, 2, Painter.withAlpha(COL_GOOD, 0xA0));
            } else if (deceptionFlashType == 3) {
                int cx = centerX() + 42, cy = contentTop() + 18;
                p.line(cx - 8, cy - 8, cx - 1, cy, 1, Painter.withAlpha(COL_BAD, 0xB0));
                p.line(cx - 1, cy, cx + 7, cy + 10, 1, Painter.withAlpha(COL_BAD, 0xB0));
            }
        }
        String reliable = switch (deceptionMode) {
            case FALSE_CHIME -> "WITNESS: LIVES + BOARD ARE TRUE";
            case FALSE_CLOCK -> "WITNESS NOTCH = TRUE TIME";
            case GHOST_CRACK -> "WITNESS: LIFE EYES ARE TRUE";
            case FALSE_CONFIRM -> "WITNESS: PROGRESS MARKS ARE TRUE";
            default -> "";
        };
        if (!reliable.isEmpty()) smallCentered(g, reliable, centerX(), contentBottom() - 8,
                Painter.withAlpha(theme().accentLite, 0xB8), 0.68f);
    }

    // ------------------------------------------------------------------ seeded variation

    protected float seedVariance(int salt) {
        long h = seed * 6364136223846793005L + salt * 1442695040888963407L;
        h ^= (h >>> 33);
        return Math.abs(h % 10000) / 10000f;
    }

    /**
     * Seeded integer jitter. Used so two chests of the same worth do not hand you an identical
     * board: the pin count, the round count and the note count all wobble around their base.
     */
    protected int wobble(int base, int amount, int salt, int lo, int hi) {
        int d = Math.round((seedVariance(salt) - 0.5f) * 2f * amount);
        return Mth.clamp(base + d, lo, hi);
    }

    /**
     * Late-game / mastery-gated mechanics used by the v39 deep variants.
     *
     * <p>These are intentionally not part of the ordinary quirk budget: mastery
     * is supposed to unlock stranger versions of a familiar puzzle rather than
     * simply make the base puzzle faster. A rich ward can occasionally reveal
     * one early, while Eldritch/Possessed/Unsigned seals are naturally more
     * willing to expose the advanced rule set.</p>
     */
    protected boolean expertVariant(int salt, int requiredMastery, int minimumValue, float baseChance) {
        if (!WardConfig.advancedMinigameVariantsEnabled || introductoryWard()) return false;
        // The long progression reserves expert authored rules for the same shelf that first
        // permits physical mutation/corruption. High-value early chests no longer bypass it.
        if (resolvedWards < WardConfig.mutationTier1AfterBeaten && !advancedNarrativeWard()) return false;
        if (masteryTier < requiredMastery && value < minimumValue && !eldritch && !possessed && !unsigned) return false;
        float chance = baseChance;
        if (!advancedNarrativeWard() && masteryTier < requiredMastery) chance *= 0.35f;
        chance += Math.max(0, masteryTier - requiredMastery) * 0.055f;
        if (value >= minimumValue + 18) chance += 0.06f;
        if (eldritch) chance += 0.05f;
        if (possessed) chance += 0.07f;
        if (unsigned) chance += 0.12f;
        chance *= Mth.clamp(WardConfig.advancedMinigameVariantChanceScale, 0f, 3f);
        return seedVariance(salt) < Mth.clamp(chance, 0f, 0.90f);
    }

    /**
     * Faint marks scratched into the panel at seeded positions and sizes. They sit at very low
     * alpha behind everything else, so they read as texture rather than decoration you have to
     * look at, and no two chests are marked the same way.
     */
    private void drawAmbientSigils(int x, int y, int w, int h) {
        Theme t = theme();
        java.util.Random rng = new java.util.Random(seed * 31L + 0xA11B1E);
        int count = 7 + rng.nextInt(6);
        for (int i = 0; i < count; i++) {
            String[] glyph = Sigils.TILE[rng.nextInt(Sigils.TILE.length)];
            int scale = 1 + rng.nextInt(3);
            int gw = Sigils.width(glyph) * scale;
            int gh = Sigils.height(glyph) * scale;
            int gx = x + 6 + rng.nextInt(Math.max(1, w - gw - 12));
            int gy = y + 30 + rng.nextInt(Math.max(1, h - gh - 40));
            // bigger marks are drawn fainter, so a large one reads as a stain in the wood
            int alpha = 0x0E - scale * 0x03 + rng.nextInt(0x0A);
            Sigils.draw(p, glyph, gx, gy, scale,
                    Painter.withAlpha(t.frameLite, alpha),
                    Painter.withAlpha(t.accent, Math.max(4, alpha / 2)),
                    Painter.withAlpha(0x000000, alpha));
        }
    }

    protected int modifierBudget() {
        if (!WardConfig.quirksEnabled || introductoryWard()) return 0;
        int base;
        if (value >= WardConfig.quirkTwoThreshold) base = 2;
        else if (value >= WardConfig.quirkOneThreshold) base = 1;
        else base = seedVariance(99) < WardConfig.quirkLowValueChance ? 1 : 0;
        // After global onboarding an unfamiliar discipline may show one authored
        // quirk, but never the full two-quirk stack until the player knows it.
        if (!advancedNarrativeWard() && masteryTier <= 1) base = Math.min(base, 1);
        if (possessed) base = Math.min(2, base + 1);
        return base;
    }

    protected boolean[] pickVariants(int poolSize, int salt) {
        boolean[] on = new boolean[poolSize];
        if (!WardConfig.quirksEnabled) return on;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) idx.add(i);
        java.util.Collections.shuffle(idx, new java.util.Random(seed ^ (salt * 0x9E3779B9L)));
        int budget = unsigned ? poolSize : Math.min(modifierBudget(), poolSize);

        // A maker's signature never increases the quirk budget. It only makes
        // one of the quirks that would have existed anyway more characteristic
        // of that house. Unsigned seals remain deliberately patternless.
        int filled = 0;
        if (!unsigned && budget > 0 && WardConfig.masterSignaturesEnabled
                && seedVariance(211 + salt) < WardConfig.masterSignatureBiasChance) {
            int preferred = MasterSignature.ofSeed(seed).preferredVariant(poolSize);
            on[preferred] = true;
            idx.remove(Integer.valueOf(preferred));
            filled = 1;
        }
        for (int i = 0; filled < budget && i < idx.size(); i++, filled++) {
            on[idx.get(i)] = true;
        }
        return on;
    }

    // ------------------------------------------------------------------ layout

    protected int panelWidth() {
        return 296;
    }

    protected int panelHeight() {
        return 218;
    }

    protected int left() {
        return (width - panelWidth()) / 2;
    }

    protected int top() {
        return (height - panelHeight()) / 2;
    }

    protected int centerX() {
        return left() + panelWidth() / 2;
    }

    /**
     * Constant now. The quirk labels used to sit in a band under the header and pushed the play
     * area down by sixteen pixels whenever a chest had any, which is what made half the screens
     * collide with their own footers. They hang off the left edge of the frame instead.
     */
    protected int contentTop() {
        return top() + 32;
    }

    protected int contentBottom() {
        return top() + panelHeight() - 38;
    }

    protected int contentCenterY() {
        return (contentTop() + contentBottom()) / 2;
    }

    // ------------------------------------------------------------------ text (flushes the batch first)

    protected void text(GuiGraphics g, String s, int x, int y, int color) {
        p.flush();
        g.drawString(font, s, x, y, color, false);
    }

    /** Left-aligned small text. Same idea as smallCentered, without the centring. */
    protected void smallText(GuiGraphics g, String s, int x, int y, int color, float scale) {
        p.flush();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    protected void centered(GuiGraphics g, String s, int cx, int y, int color) {
        p.flush();
        g.drawCenteredString(font, s, cx, y, color);
    }

    protected void smallCentered(GuiGraphics g, String s, int cx, int y, int color, float scale) {
        p.flush();
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, s, 0, 0, color);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ shared pixel-art parts

    /** Inset well: a sunken area with a lit bottom edge, used as the play field on most locks. */
    protected void well(int x, int y, int w, int h) {
        Theme t = theme();
        p.roundRect(x - 2, y - 2, w + 4, h + 4, t.wellEdge);
        p.roundRect(x - 1, y - 1, w + 2, h + 2, Painter.darken(t.frame, 0.55f));
        p.roundRect(x, y, w, h, t.well);
        p.rect(x + 1, y + h - 1, x + w - 1, y + h, Painter.withAlpha(t.frameLite, 0x30));
    }

    /** Brass stud. Four flat blocks, no anti-aliasing, on purpose. */
    protected void rivet(int cx, int cy) {
        Theme t = theme();
        p.rect(cx - 2, cy - 1, cx + 2, cy + 2, t.frameDark);
        p.rect(cx - 1, cy - 2, cx + 2, cy + 2, t.frame);
        p.rect(cx - 1, cy - 1, cx + 1, cy + 1, t.frameLite);
    }

    /**
     * Dot with a hard rim and a lit core. The old one pulsed its halo every frame and was the main
     * thing people found tiring, so the glow is now a flat, static ring.
     */
    protected void dot(int cx, int cy, int r, int color, boolean glow) {
        if (glow) p.disc(cx, cy, r + 3, Painter.withAlpha(color, 0x24));
        p.disc(cx, cy, r, 0xFF0B0806);
        p.disc(cx, cy, r - 1, color);
        p.disc(cx, cy, Math.max(1, r - 3), Painter.lighten(color, 0.42f));
    }

    protected void progressDots(int centerX, int y, int total, int done) {
        Theme t = theme();
        int spacing = total > 8 ? 9 : 11;
        int startX = centerX - (total - 1) * spacing / 2;
        for (int i = 0; i < total; i++) {
            int cx = startX + i * spacing;
            if (i < done) {
                p.rect(cx - 3, y - 3, cx + 3, y + 3, t.accentDark);
                p.rect(cx - 2, y - 2, cx + 2, y + 2, t.accent);
                p.rect(cx - 1, y - 1, cx + 1, y + 1, t.accentLite);
            } else {
                p.outline(cx - 3, y - 3, 6, 6, 1, t.faint);
            }
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        catchUp();
        renderBackground(g);

        p.bind(g);
        p.setPixel(WardConfig.accessibilityLargeMarkers ? 3 : 2);

        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        int cx = x + w / 2, cy = y + h / 2;

        float it = introT();
        boolean animating = it < 1f;
        boolean eldritchAnim = eldritch && !WardConfig.accessibilityReduceMotion;
        boolean possessedAnim = possessed && !WardConfig.accessibilityReduceMotion;
        if (animating || eldritchAnim || possessedAnim) g.pose().pushPose();
        if (animating) {
            float s = 0.94f + 0.06f * it;
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1f);
            g.pose().translate(-cx, -cy, 0);
        }
        if (eldritchAnim) {
            long t = System.currentTimeMillis();
            boolean finale = eldritchStage >= eldritchTotal && eldritchTotal > 0;
        // Phase personalities: 1 = signal corruption, 2 = split image, final = maw.
            float amp = (finale ? 2.25f : 1.2f) * WardConfig.accessibilityGuiAnimationIntensity;
            float jx = (float) Math.sin((t / (finale ? 19.0 : 27.0)) + (seed & 7)) * amp;
            float jy = (float) Math.cos((t / (finale ? 29.0 : 41.0)) + ((seed >>> 3) & 7)) * amp * 0.7f;
            g.pose().translate(jx, jy, 0);
        } else if (possessedAnim) {
            long t = System.currentTimeMillis();
            float amp = 0.65f * WardConfig.accessibilityGuiAnimationIntensity;
            g.pose().translate(Math.sin(t / 53.0 + seed) * amp, Math.cos(t / 71.0 + seed) * amp * 0.45f, 0);
        }

        drawFrame(g, x, y, w, h);
        drawHistoryScars(x, y, w, h);

        if (!modifierLabels.isEmpty()) {
            drawChips(g, x, y);
        }

        int gameMouseX = (int) Math.round(refractMouseX(mouseX));
        boolean presentationMirror = presentationMirrorActive();
        if (presentationMirror) {
            g.pose().pushPose();
            g.pose().translate(centerX(), 0, 0);
            g.pose().scale(-1f, 1f, 1f);
            g.pose().translate(-centerX(), 0, 0);
        }
        renderGame(g, gameMouseX, mouseY, partialTick);
        if (presentationMirror) g.pose().popPose();
        renderMakerMutation();
        renderWardMutation();
        renderPossessedMutation();
        renderEldritchMutation(g);
        renderCorruptionMechanic(g, mouseX, mouseY);
        renderDeception(g);
        renderBursts();
        renderAnomalies(g);
        renderAfflictionOverlay(g);
        // The borrowed mechanism is a real input surface. Host shrouds, guttering and
        // affliction overlays must never cover its only readable channel.
        renderHybrid(g, mouseX, mouseY);
        renderSealBreak(g);
        drawClock(g);

        drawFooter(g, x, y, w, h);

        p.flush();
        if (animating || eldritchAnim || possessedAnim) g.pose().popPose();

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Quiet physical history on the frame: old breaks, burn soot, re-seal wax
     * and grudge scratches. These never enter the content well, so remembering
     * a chest cannot make the next minigame harder to read.
     */
    private void drawHistoryScars(int x, int y, int w, int h) {
        if (!WardConfig.wardHistoryScarsEnabled) return;
        int n = Math.min(Math.min(scarCount, WardConfig.wardHistoryScarCap), 12);
        int crack = Painter.withAlpha(Painter.lighten(theme().frameDark, 0.22f), 0xD0);
        for (int i = 0; i < n; i++) {
            long z = seed ^ (0x53434152L + i * 0x9E3779B97F4A7C15L);
            int side = (int) Math.floorMod(z, 3);
            int off = 18 + (int) Math.floorMod(z >>> 9, Math.max(24, w - 52));
            int bend = (int) Math.floorMod(z >>> 17, 9) - 4;
            if (side == 0) {
                int sx = x + off;
                p.line(sx, y + 8, sx + bend, y + 13, 1, crack);
                p.line(sx + bend, y + 13, sx + bend + 4, y + 17, 1, crack);
            } else if (side == 1) {
                int sy = y + 36 + (int) Math.floorMod(z >>> 11, Math.max(24, h - 72));
                p.line(x + 7, sy, x + 13, sy + bend, 1, crack);
                p.line(x + 13, sy + bend, x + 17, sy + bend + 3, 1, crack);
            } else {
                int sy = y + 36 + (int) Math.floorMod(z >>> 13, Math.max(24, h - 72));
                p.line(x + w - 7, sy, x + w - 13, sy - bend, 1, crack);
                p.line(x + w - 13, sy - bend, x + w - 17, sy - bend + 3, 1, crack);
            }
        }

        if ((scarFlags & 1) != 0) { // burned contents
            int soot = Painter.withAlpha(0x120C09, 0xB8);
            p.rect(x + w - 49, y + 8, x + w - 13, y + 12, soot);
            p.rect(x + w - 42, y + 12, x + w - 16, y + 16, Painter.withAlpha(0x241510, 0x88));
        }
        if ((scarFlags & 2) != 0) { // grudge scratches
            int spite = Painter.withAlpha(0x7A4B7F, 0xA8);
            for (int i = 0; i < 3; i++)
                p.line(x + 17 + i * 3, y + h - 24, x + 28 + i * 3, y + h - 11, 1, spite);
        }
        if ((scarFlags & 4) != 0) { // wound/re-sealed wax
            int wx = x + 28, wy = y + h - 18;
            int wax = 0xFF6C222A;
            p.disc(wx, wy, 7, wax);
            p.line(wx - 4, wy - 5, wx + 1, wy, 1, 0xFF2B0A0E);
            p.line(wx + 1, wy, wx - 1, wy + 6, 1, 0xFF2B0A0E);
            p.rect(wx - 1, wy + 6, wx + 1, wy + 11, Painter.withAlpha(wax, 0xC0));
        }
        if (scarCount >= 3) {
            int notch = Painter.withAlpha(Painter.lighten(theme().frameDark, 0.30f), 0xB8);
            p.line(x + w - 22, y + 7, x + w - 17, y + 12, 1, notch);
            p.line(x + w - 17, y + 12, x + w - 11, y + 8, 1, notch);
        }
        if (scarCount >= 5) {
            int stitch = Painter.withAlpha(0x8B6A58, 0xA8);
            for (int i = 0; i < 3; i++) {
                int sy = y + 40 + i * 6;
                p.line(x + w - 20, sy, x + w - 12, sy + 4, 1, stitch);
                p.line(x + w - 19, sy + 2, x + w - 17, sy + 4, 1, stitch);
            }
        }
        if (scarCount >= 7) {
            int ox = x + w / 2 + 48, oy = y + h - 16;
            int patina = Painter.withAlpha(Painter.darken(theme().accentDark, 0.35f), 0x90);
            p.ringThick(ox, oy, 6, 1, patina);
            p.disc(ox, oy, 1, patina);
        }
    }

    /**
     * The visual anomalies, painted over the lock itself so they occlude it, but
     * inside the frame so the panel's own furniture stays readable. Losing track
     * of the chest's own header would make these feel like a rendering fault
     * rather than a condition.
     */
    private void renderAnomalies(GuiGraphics g) {
        if (anomalies.isEmpty()) return;
        int cx0 = left() + 8, cy0 = contentTop() - 4;
        int cw = panelWidth() - 16, ch = contentBottom() - contentTop() + 12;

        if (has(Anomaly.SHROUDED)) {
            // Three slow blobs on different periods, so they never line up into a
            // pattern you can wait out.
            for (int i = 0; i < 3; i++) {
                float ph = shroudT * (0.13f + i * 0.045f) + i * 2.1f;
                int bx = cx0 + Math.round((0.5f + 0.5f * Mth.sin(ph)) * (cw - 90));
                int by = cy0 + Math.round((0.5f + 0.5f * Mth.cos(ph * 0.77f + i)) * (ch - 60));
                // Solid in the middle and feathered only at the rim. The old
                // stack of translucent layers came to about a third opacity in
                // the centre, so the thing it was covering was still perfectly
                // readable and the anomaly cost nothing.
                for (int r = 0; r < 6; r++) {
                    p.roundRect(bx + r * 3, by + r * 2, 96 - r * 6, 64 - r * 4,
                            Painter.withAlpha(0x05070B, 0x38 + r * 0x22));
                }
            }
        }

        if (has(Anomaly.GUTTERING)) {
            // A stable failed lamp, not a blinking renderer-looking failure. One
            // side stays dim for the whole lock so the condition is legible.
            boolean leftSide = ((seed >>> 5) & 1L) == 0L;
            int sideW = Math.max(54, Math.round(cw * 0.36f));
            int sx = leftSide ? cx0 - 4 : cx0 + cw - sideW + 4;
            int alpha = WardConfig.accessibilityReduceFlashing ? 0x58 : 0x72;
            p.rect(sx, cy0 - 3, sx + sideW, cy0 + ch + 3, Painter.withAlpha(0x000000, alpha));
            int rim = leftSide ? sx + sideW - 2 : sx + 1;
            p.rect(rim, cy0 - 1, rim + 2, cy0 + ch + 1,
                    Painter.withAlpha(theme().accentLite, 0x42));
        }

        if (has(Anomaly.HURRIED)) {
            int bx = left() + 12, by = top() + panelHeight() - 30, bw = panelWidth() - 24;
            p.rect(bx, by, bx + bw, by + 3, Painter.withAlpha(0x000000, 0x88));
            int fill = Math.round(Mth.clamp(clock, 0f, 1f) * bw);
            p.rect(bx, by, bx + fill, by + 3,
                    clock < 0.25f ? COL_BAD : Painter.withAlpha(theme().accent, 0xCC));
        }
    }

    private void renderWardMutation() {
        if (mutationLevel <= 0) return;
        Theme t = theme();
        int x = left() + 2, y = top() + 27, w = panelWidth() - 4, h = panelHeight() - 66;
        int vein = Painter.withAlpha(mutationLevel >= 3 ? 0xFF72506F : t.frameDark,
                0x48 + mutationLevel * 0x18);
        for (int i = 0; i < mutationLevel + 1; i++) {
            long z = seed ^ (0x4D55544154454CL + i * 0x9E3779B97F4A7C15L);
            int sx = x + (int) Math.floorMod(z, Math.max(1, w));
            int sy = y + (int) Math.floorMod(z >>> 13, Math.max(1, h));
            p.line(sx, sy, sx + 10 - i * 2, sy + 8, 1, vein);
            p.line(sx + 10 - i * 2, sy + 8, sx + 4, sy + 15, 1, Painter.withAlpha(vein, 0x70));
        }
        if (mutationLevel >= 3) {
            p.roundOutline(x - 2, y - 2, w + 4, h + 4, Painter.withAlpha(0xFF68405E, 0x42));
        }
    }

    private void renderPossessedMutation() {
        if (!possessed) return;
        Theme t = theme();
        long now = System.currentTimeMillis();
        int x = left() + 7, y = contentTop(), w = panelWidth() - 14, h = contentBottom() - contentTop();
        for (int i = 0; i < 3; i++) {
            int yy = y + Math.floorMod((int) (now / (92L + i * 23L) + seed), Math.max(1, h));
            int off = WardConfig.accessibilityReduceMotion ? 0 : Math.round(Mth.sin(now / 120f + i) * 4f);
            p.rect(x + 12 + off, yy, x + w - 18 + off, yy + 1,
                    Painter.withAlpha(i == 1 ? 0xFF8D537D : t.accentLite, 0x20 + i * 8));
        }
        int gi = Math.floorMod((int) (seed ^ 0x50535344L), Sigils.TILE.length);
        int drift = WardConfig.accessibilityReduceMotion ? 0 : Math.round(Mth.sin(now / 190f) * 3f);
        Sigils.drawCentered(p, Sigils.TILE[gi], centerX() + drift, contentTop() + 11, 1,
                Painter.withAlpha(0xFFB96E9F, 0x38), Painter.withAlpha(t.accentDark, 0x18), 0);
    }

    /** The rule-changing part of Possessed/Eldritch wards, rendered loudly enough to be fair. */
    private void renderCorruptionMechanic(GuiGraphics g, int mouseX, int mouseY) {
        int x = left() + 8, y = contentTop() + 2, w = panelWidth() - 16;
        if (corruptionVariant.active()) {
            int cc = Painter.withAlpha(0xFFB16FD1, 0x92);
            p.roundOutline(x - 1, y - 1, w + 2, contentBottom() - contentTop() - 2, cc);
            if (corruptionVariant.profile() == MinigameCorruption.Profile.BLACKOUT
                    && MinigameCorruption.blackoutActive(corruptionVariant, seed, corruptionTime)) {
                // Corrupted blackout is intentional, but a full-screen disappearance
                // looked exactly like a renderer failure and could make a lock
                // literally unplayable. Black ink now consumes one stable side of
                // the field while leaving the other side readable.
                boolean leftSide = ((seed >>> 11) & 1L) == 0L;
                int bh = contentBottom() - contentTop() - 20;
                int bw = Math.max(52, Math.round((w - 8) * 0.44f));
                int bx = leftSide ? x + 4 : x + w - 4 - bw;
                p.roundRect(bx, y + 8, bw, bh,
                        Painter.withAlpha(0x020107, WardConfig.accessibilityReduceFlashing ? 0x9A : 0xBC));
                int rim = leftSide ? bx + bw - 2 : bx + 1;
                p.rect(rim, y + 10, rim + 2, y + 8 + bh - 2, Painter.withAlpha(0xCE79E6, 0x54));
                smallCentered(g, leftSide ? "BLACK INK · LEFT FIELD" : "BLACK INK · RIGHT FIELD",
                        centerX(), contentTop() + 7, 0xFFE6C8F0, 0.68f);
            } else if (corruptionMirrorActive()) {
                int seam = centerX();
                p.rect(seam - 1, contentTop() + 5, seam + 1, contentBottom() - 5,
                        Painter.withAlpha(0xFFCE79E6, 0x70));
                smallCentered(g, "CORRUPTION · " + MinigameCorruption.formName(gameType, corruptionProfile).toUpperCase(java.util.Locale.ROOT), centerX(), contentTop() + 7,
                        Painter.withAlpha(0xFFEACCF4, 0xD8), 0.72f);
            } else if (corruptionVariant.profile() == MinigameCorruption.Profile.FALSE_CLOCK) {
                float ph = corruptionTime % 5.6f;
                String label = ph > 1.1f && ph < 2.55f ? "FALSE CLOCK · DISPLAY UNTRUSTWORTHY"
                        : "CORRUPTION · " + MinigameCorruption.formName(gameType, corruptionProfile).toUpperCase(java.util.Locale.ROOT);
                smallCentered(g, label, centerX(), contentTop() + 7,
                        Painter.withAlpha(ph > 1.1f && ph < 2.55f ? 0xFFFFC7DB : 0xFFE0B8EE, 0xD0), 0.70f);
            } else if (corruptionVariant.profile() == MinigameCorruption.Profile.TIME_FRACTURE) {
                smallCentered(g, "CORRUPTION · " + MinigameCorruption.formName(gameType, corruptionProfile).toUpperCase(java.util.Locale.ROOT), centerX(), contentTop() + 7,
                        Painter.withAlpha(0xFFE0B8EE, 0xC8), 0.70f);
            } else if (corruptionVariant.profile() == MinigameCorruption.Profile.BLOOD_RUSH && mistakes > 0) {
                smallCentered(g, "CORRUPTION · " + MinigameCorruption.formName(gameType, corruptionProfile).toUpperCase(java.util.Locale.ROOT), centerX(), contentTop() + 7,
                        Painter.withAlpha(0xFFFF9CB0, 0xD0), 0.70f);
            } else if (corruptionVariant.profile() == MinigameCorruption.Profile.ECHO_GLASS) {
                int drift = WardConfig.accessibilityReduceMotion ? 1 : Math.round(Mth.sin(corruptionTime * 2.2f) * 3f);
                p.roundOutline(x + drift, y + 2, w, contentBottom() - contentTop() - 6,
                        Painter.withAlpha(0xFF73D7D2, 0x42));
                int rx = (int)Math.round(refractMouseX(mouseX));
                if (mouseY >= contentTop() && mouseY <= contentBottom()) {
                    p.ringThick(rx, mouseY, 5, 1, Painter.withAlpha(0xFF7DE3DD, 0xB8));
                    p.line(mouseX, mouseY, rx, mouseY, 1, Painter.withAlpha(0xFF7DE3DD, 0x58));
                }
                smallCentered(g, "ECHO GLASS · TRUST THE SECOND CURSOR", centerX(), contentTop() + 7,
                        Painter.withAlpha(0xFFAFECE8, 0xC8), 0.70f);
            }
        }
        if (possessed) {
            if (possessedSeizing()) {
                int col = Painter.withAlpha(0xFFB86A9D, 0xA8);
                p.roundOutline(x, y, w, contentBottom() - contentTop() - 4, col);
                p.ringThick(centerX(), contentTop() + 15, 8, 2, col);
                p.disc(centerX(), contentTop() + 15, 2, Painter.withAlpha(0xFFE7B9D5, 0xD0));
                String hold = possessedPattern() == 1 ? "POSSESSION · INPUT STUTTER / DIRECTIONS WILL FLIP"
                        : (possessedPattern() == 2 ? "POSSESSION · DRAWING BREATH / CENTER PULL NEXT" : "POSSESSION · INPUT SEIZED / LURCH NEXT");
                smallCentered(g, hold, centerX(), contentTop() + 26,
                        Painter.lighten(0xFFB86A9D, 0.18f), 0.72f);
            } else if (possessedSurging()) {
                int a = WardConfig.accessibilityReduceFlashing ? 0x62 : 0x58 + Math.round(breath(2.1f) * 0x28);
                p.roundOutline(x - 2, y - 2, w + 4, contentBottom() - contentTop(), Painter.withAlpha(0xFF9A3D72, a));
                String surge = possessedPattern() == 1 ? "POSSESSION · BROKEN BEAT / LEFT ↔ RIGHT"
                        : (possessedPattern() == 2 ? "POSSESSION · HUNGRY PULL / INPUT DRAG" : "POSSESSION · LURCH / MECHANISM ACCELERATED");
                smallCentered(g, surge, centerX(), contentTop() + 8,
                        Painter.withAlpha(0xFFE3A5CA, 0xD0), 0.72f);
            }
        }

        if (!eldritch) return;
        if (eldritchRefractionActive()) {
            int seam = centerX();
            p.rect(seam - 1, contentTop() + 5, seam + 1, contentBottom() - 5,
                    Painter.withAlpha(0xFFD06FE5, 0x78));
            p.line(seam - 34, contentTop() + 17, seam - 9, contentTop() + 17, 1, Painter.withAlpha(0xFF73DCD7, 0x80));
            p.line(seam + 9, contentTop() + 17, seam + 34, contentTop() + 17, 1, Painter.withAlpha(0xFFD06FE5, 0x80));
            smallCentered(g, "REFRACTION · LEFT ↔ RIGHT", centerX(), contentTop() + 7,
                    Painter.withAlpha(0xFFE7B8F2, 0xE0), 0.74f);
            int rx = (int) Math.round(refractMouseX(mouseX));
            if (rx >= left() && rx <= left() + panelWidth()
                    && mouseY >= contentTop() && mouseY <= contentBottom()) {
                p.ringThick(rx, mouseY, 5, 1, Painter.withAlpha(0xFFDCA1EC, 0xB8));
                p.line(rx - 8, mouseY, rx - 3, mouseY, 1, Painter.withAlpha(0xFF70D9D4, 0xA0));
                p.line(rx + 3, mouseY, rx + 8, mouseY, 1, Painter.withAlpha(0xFFDCA1EC, 0xA0));
            }
        } else if (eldritchHushActive()) {
            p.roundRect(left() + 10, contentTop() + 6, panelWidth() - 20,
                    contentBottom() - contentTop() - 12, Painter.withAlpha(0x05020A, 0x72));
            p.ringThick(centerX(), contentCenterY(), 25, 2, Painter.withAlpha(0xFFB64DC4, 0xB8));
            p.ringThick(centerX(), contentCenterY(), 16, 1, Painter.withAlpha(0xFF66D5CF, 0xA0));
            p.disc(centerX(), contentCenterY(), 4, Painter.withAlpha(0xFFF2D8F7, 0xE8));
            centered(g, "DO NOT ANSWER", centerX(), contentCenterY() + 34, 0xFFE9B9F2);
        } else if (eldritchMawSurge()) {
            smallCentered(g, "THE MAW PULLS FASTER", centerX(), contentTop() + 7,
                    Painter.withAlpha(0xFFE3A0EC, 0xD8), 0.74f);
        } else if (eldritchTimeSplice()) {
            smallCentered(g, "TIME SPLICE · CADENCE CUT", centerX(), contentTop() + 7,
                    Painter.withAlpha(0xFFDFA3EC, 0xC8), 0.72f);
        } else if (eldritchStage <= 1) {
            smallCentered(g, "SIGNAL DRIFT", centerX(), contentTop() + 7,
                    Painter.withAlpha(0xFF8FE5DD, 0xA0), 0.70f);
        }
    }

    /**
     * Eldritch Ward mutation pass. This deliberately distorts every ordinary
     * minigame alongside the actual rule mutations above: jittering fault-lines,
     * duplicated runic ghosts, scan tears and a restless vignette make the
     * existing lock look as though the interface itself is becoming unwell.
     */
    private void renderEldritchMutation(GuiGraphics g) {
        if (!eldritch) return;
        Theme t = theme();
        int x = left() + 5, y = contentTop() - 3;
        int w = panelWidth() - 10, h = contentBottom() - contentTop() + 8;
        long now = System.currentTimeMillis();
        float motion = WardConfig.accessibilityReduceMotion ? 0f
                : WardConfig.accessibilityGuiAnimationIntensity;
        boolean finale = eldritchStage >= eldritchTotal && eldritchTotal > 0;
        if (!finale && eldritchStage == 1) {
            for (int i = 0; i < 4; i++) {
                int yy = top() + 38 + i * 37 + (int)Math.floorMod(System.currentTimeMillis()/95 + i*13, 7);
                p.rect(left()+5, yy, left()+panelWidth()-5, yy+1, Painter.withAlpha(0x7CE5DF, 0x28));
            }
        } else if (!finale && eldritchStage == 2) {
            p.roundOutline(left()+3, top()+3, panelWidth()-6, panelHeight()-6, Painter.withAlpha(0xD05AE8, 0x55));
            p.roundOutline(left()+7, top()+5, panelWidth()-10, panelHeight()-8, Painter.withAlpha(0x58D9D4, 0x38));
        }


        // Torn scan bands: the final mouth is intentionally much less stable.
        for (int i = 0; i < (finale ? 7 : 4); i++) {
            int yy = y + Math.floorMod((int) (now / (34L + i * 11L) + seed * (i + 3)), Math.max(1, h));
            int len = 36 + Math.floorMod((int) (seed >>> (i * 7)), Math.max(40, w - 30));
            int sx = x + Math.floorMod((int) (seed >>> (i * 5 + 3)), Math.max(1, w - len));
            int alpha = 0x18 + Math.min(i, 4) * 7 + (finale ? 8 : 0);
            p.rect(sx, yy, sx + len, yy + 1,
                    Painter.withAlpha(i % 2 == 0 ? t.accentLite : 0xFFB44C8C, alpha));
            if (motion > 0.01f && i < 2) {
                int ghost = Math.round(Mth.sin((now / 48f) + i) * 5f * motion);
                p.rect(sx + ghost, yy + 2, sx + len / 2 + ghost, yy + 3,
                        Painter.withAlpha(t.frameLite, 0x16));
            }
        }

        // Sickly duplicated sigils around the play field.
        for (int i = 0; i < (finale ? 9 : 5); i++) {
            long z = seed ^ (0xE1D17C4L + i * 0x9E3779B97F4A7C15L);
            int gx = x + 12 + (int) Math.floorMod(z >>> 7, Math.max(1, w - 24));
            int gy = y + 10 + (int) Math.floorMod(z >>> 19, Math.max(1, h - 20));
            int gi = (int) Math.floorMod(z, Sigils.TILE.length);
            int drift = motion <= 0f ? 0 : Math.round(Mth.sin(now / 170f + i * 1.7f) * 3f * motion);
            Sigils.drawCentered(p, Sigils.TILE[gi], gx + drift, gy, 1,
                    Painter.withAlpha(0xFFD06FAE, 0x20),
                    Painter.withAlpha(t.accent, 0x12), 0);
            Sigils.drawCentered(p, Sigils.TILE[gi], gx - drift - 2, gy + 1, 1,
                    Painter.withAlpha(0xFF7CC6D8, 0x13),
                    Painter.withAlpha(t.accentDark, 0x0B), 0);
        }

        // Uneven mutated border, intentionally asymmetric.
        int edge = Painter.withAlpha(0xFF7D2D67, 0x72);
        p.line(x + 3, y + 7, x + 18, y + 2, 1, edge);
        p.line(x + 18, y + 2, x + 31, y + 9, 1, edge);
        p.line(x + w - 5, y + h - 11, x + w - 19, y + h - 3, 1, edge);
        p.line(x + w - 19, y + h - 3, x + w - 34, y + h - 10, 1, edge);

        if (finale) {
            int cx = centerX(), cy = contentCenterY();
            p.ringThick(cx, cy, 27, 2, Painter.withAlpha(0xFF9B356F, 0x46));
            p.ringThick(cx, cy, 18, 1, Painter.withAlpha(0xFF6EC4CF, 0x35));
            p.line(cx - 22, cy, cx + 22, cy, 1, Painter.withAlpha(0xFFD06FAE, 0x40));
        }
        if (!WardConfig.accessibilityReduceFlashing) {
            float beat = 0.5f + 0.5f * Mth.sin(now / 115f);
            int a = Math.round(0x10 + beat * 0x12);
            p.roundOutline(x - 2, y - 2, w + 4, h + 4,
                    Painter.withAlpha(0xFFC5579A, a));
        }
    }

    /**
     * The wax seal giving way.
     *
     * <p>Winning used to be a sound and a line of text: the thing you had been
     * working on for a minute simply stopped existing. A seal that cracks, splits
     * and drops off the panel gives the win a physical end, which is the whole
     * reason it is here.
     */
    private void renderMakerMutation() {
        float veil = MakerMinigameBehavior.veilStrength(makerStyle, gameType, seed, corruptionTime);
        if (veil <= 0.01f) return;
        int x = left() + 7;
        int y1 = contentTop() + 5;
        int y2 = contentBottom() - 5;
        int w = panelWidth() - 14;
        int band = Math.max(10, (y2 - y1) / 7);
        int travel = Math.max(1, (y2 - y1) - band);
        int by = y1 + Math.round((0.5f + 0.5f * Mth.sin(corruptionTime * 1.15f + (seed & 7))) * travel);
        int alpha = Math.round(0x26 + veil * 0x58);
        p.rect(x, by, x + w, Math.min(y2, by + band), Painter.withAlpha(theme().panel, alpha));
        p.line(x + 3, by, x + w - 3, by, 1, Painter.withAlpha(theme().accentDark, Math.min(0x90, alpha + 0x20)));
        // A second narrow slit leaves a visible reliable channel instead of blanking the puzzle.
        int slit = Math.max(y1, Math.min(y2 - 2, by + band / 2));
        p.rect(x + w / 3, slit, x + (w * 2) / 3, slit + 2, Painter.withAlpha(theme().accentLite, 0x45));
    }

    private void renderAfflictionOverlay(GuiGraphics g) {
        if (affliction <= 0 && !living) return;
        Theme t = theme();
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        long ms = System.currentTimeMillis();
        if (affliction == 1) { // whispering
            for (int i = 0; i < 3; i++) {
                int gx = x + 28 + (int)Math.floorMod(seed + i * 71 + ms / 1800, Math.max(1, w - 56));
                int gy = y + 42 + i * 31;
                Sigils.drawCentered(p, Sigils.TILE[Math.floorMod((int)(seed + i), Sigils.TILE.length)], gx, gy, 1,
                        Painter.withAlpha(t.accentLite, 0x22), Painter.withAlpha(t.accentDark, 0x12), 0);
            }
        } else if (affliction == 2) { // hollow
            p.roundOutline(x + 7, y + 30, w - 14, h - 70, Painter.withAlpha(0x050505, 0xA0));
        } else if (affliction == 4) { // starved
            p.scanlines(x + 6, y + 31, w - 12, h - 70, Painter.withAlpha(0x000000, 0x20));
        } else if (affliction == 5) { // blood-warm
            int a = WardConfig.accessibilityReduceFlashing ? 0x24 : 0x18 + Math.round(breath(0.7f) * 0x22);
            p.roundOutline(x - 2, y - 2, w + 4, h + 4, Painter.withAlpha(0x9E2730, a));
        }
        if (living) {
            int a = WardConfig.accessibilityReduceFlashing ? 0x45 : 0x30 + Math.round(breath(0.9f) * 0x35);
            int cx = x + w / 2, cy = y + h / 2;
            p.ringThick(cx, cy, 22 + Math.round(breath(0.9f) * 3f), 1, Painter.withAlpha(0x7A365E, a));
            p.line(cx - 32, cy, cx - 10, cy - 4, 1, Painter.withAlpha(0x7A365E, a));
            p.line(cx + 10, cy + 4, cx + 32, cy, 1, Painter.withAlpha(0x7A365E, a));
        }
    }

    private void renderSealBreak(GuiGraphics g) {
        if (sealBreak <= 0f) return;
        float t01 = 1f - Mth.clamp(sealBreak / 1.1f, 0f, 1f);   // 0 whole, 1 gone
        int cx = centerX(), cy = (contentTop() + contentBottom()) / 2;

        int wax = 0xFF7E1F24;
        int waxLit = 0xFFA33036;
        int waxDark = 0xFF4A1015;

        // Two halves parting and falling, rotating away from the crack.
        for (int side = -1; side <= 1; side += 2) {
            float slide = t01 * t01 * 26f * side;
            float drop = t01 * t01 * 46f;
            int ox = cx + Math.round(slide);
            int oy = cy + Math.round(drop);
            int alpha = Math.round((1f - t01) * 0xFF);
            if (alpha <= 4) continue;

            for (int dy = -18; dy <= 18; dy++) {
                // half a disc, with a ragged edge along the crack
                int half = (int) Math.sqrt(Math.max(0, 18 * 18 - dy * dy));
                if (half <= 0) continue;
                int inner = 1 + ((dy * 7919) & 3);
                int x1 = side < 0 ? ox - half : ox + inner;
                int x2 = side < 0 ? ox - inner : ox + half;
                int col = dy < -4 ? waxLit : (dy > 8 ? waxDark : wax);
                p.rect(x1, oy + dy, x2, oy + dy + 1, Painter.withAlpha(col, alpha));
            }
        }
    }

    /** The ward's own clock, along the top edge of the panel. */
    private void drawClock(GuiGraphics g) {
        if (clockTotal <= 0f) return;
        float frac = Mth.clamp(clockLeft / clockTotal, 0f, 1f);
        float trueFrac = frac;
        if (corruptionVariant.profile() == MinigameCorruption.Profile.FALSE_CLOCK
                || deceptionMode == MinigameDeception.Mode.FALSE_CLOCK) {
            float ph = corruptionTime % 5.6f;
            if (ph > 1.1f && ph < 2.55f) {
                // Only the broad bar lies. The final danger band is truthful and v38 adds
                // a thin witness-notch underneath that always marks the real remaining time.
                float lie = Mth.clamp(frac + 0.18f * Mth.sin(corruptionTime * 3.1f + (seed & 15)), 0.06f, 1f);
                if (frac >= 0.18f) frac = lie;
            }
        }
        int x = left() + 6, y = top() + 4, w = panelWidth() - 12;

        p.rect(x, y, x + w, y + 3, Painter.withAlpha(0x000000, 0x99));
        int fill = Math.round(frac * w);
        int col = frac < 0.18f ? COL_BAD : (frac < 0.4f ? 0xFFD9A441 : theme().accent);
        p.rect(x, y, x + fill, y + 3, col);
        if (deceptionMode == MinigameDeception.Mode.FALSE_CLOCK) {
            int tx = x + Math.round(trueFrac * w);
            p.rect(tx - 1, y + 4, tx + 1, y + 7, Painter.withAlpha(theme().accentLite, 0xE0));
        }
        if (frac < 0.18f) {
            // Only shouts at the end. A bar that pulses the whole time stops
            // being information and becomes wallpaper.
            p.rect(x, y - 1, x + fill, y + 4,
                    Painter.withAlpha(COL_BAD, 0x30 + Math.round(breath(2.6f) * 0x60)));
            smallCentered(g, String.format("%.1fs", clockLeft), centerX(), y + 5, COL_BAD, 0.8f);
        }
    }

    private void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        Theme t = theme();
        int tint = dimensionTint();

        // drop shadow
        p.roundRect(x + 4, y + 6, w + 4, h + 4, COL_SHADOW);

        // outer keyline, metal band, inner keyline
        p.roundRect(x - 4, y - 4, w + 8, h + 8, t.edge);
        p.roundRect(x - 3, y - 3, w + 6, h + 6, t.frame);
        p.rect(x - 3, y - 3, x + w + 3, y - 2, t.frameLite);
        p.rect(x - 3, y - 3, x - 2, y + h + 3, t.frameLite);
        p.rect(x - 3, y + h + 2, x + w + 3, y + h + 3, t.frameDark);
        p.rect(x + w + 2, y - 3, x + w + 3, y + h + 3, t.frameDark);
        p.roundOutline(x - 1, y - 1, w + 2, h + 2, t.edge);

        // panel body
        p.roundRect(x, y, w, h, t.panel);
        drawPattern(x + 1, y + 1, w - 2, h - 2, t);
        drawAmbientSigils(x, y, w, h);

        // inner bevel
        p.rect(x + 1, y + 1, x + w - 1, y + 2, Painter.withAlpha(t.panelLite, 0x60));
        p.rect(x + 1, y + 1, x + 2, y + h - 1, Painter.withAlpha(t.panelLite, 0x40));
        p.rect(x + 1, y + h - 2, x + w - 1, y + h - 1, Painter.withAlpha(0x000000, 0x50));

        // header plate
        int headerH = 24;
        p.rect(x + 1, y + 1, x + w - 1, y + headerH, Painter.darken(t.panel, 0.35f) | 0xFF000000);
        p.dither(x + 1, y + 1, w - 2, headerH - 1, Painter.withAlpha(t.frame, 0x14), 2, 0);
        p.rect(x + 1, y + headerH, x + w - 1, y + headerH + 1, t.frame);
        p.rect(x + 1, y + headerH + 1, x + w - 1, y + headerH + 2, Painter.withAlpha(tint, 0x66));

        // watching eye in the title bar, lives on the right
        Sigils.headerEye(p, x + 13, y + 12, 1, t.frameLite, Painter.withAlpha(t.accent, 0x88), 0);
        drawLives(x + w - 12, y + 12);

        // corner ornaments
        String[] orn = Sigils.CORNERS[Math.floorMod(t.corner, Sigils.CORNERS.length)];
        int oc = Painter.withAlpha(t.frame, 0x9A);
        int oh = Painter.withAlpha(t.frameLite, 0x50);
        Sigils.corner(p, orn, x + 3, y + headerH + 3, 1, oc, oh, false, false);
        Sigils.corner(p, orn, x + w - 13, y + headerH + 3, 1, oc, oh, true, false);
        Sigils.corner(p, orn, x + 3, y + h - 14, 1, oc, oh, false, true);
        Sigils.corner(p, orn, x + w - 13, y + h - 14, 1, oc, oh, true, true);

        rivet(x + 5, y + 5);
        rivet(x + w - 6, y + 5);
        rivet(x + 5, y + h - 6);
        rivet(x + w - 6, y + h - 6);

        text(g, title.getString(), x + 22, y + 5, eldritch ? Painter.lighten(t.accent, 0.30f) : t.frameLite);
        // The seal's own name, under the name of the mechanism it uses. Which
        // game you are playing and which object you are playing it on are two
        // different facts and the header has room for both.
        String sealLine = unsigned ? "unsigned" : Sealmakers.sealName(seed);
        if (eldritch) sealLine += eldritchStage >= eldritchTotal
                ? "   ·   F̴I̴N̴A̴L̴  M̴O̴U̴T̴H̴"
                : "   ·   E̴L̴D̴R̴I̴T̴C̴H̴ " + eldritchStage + "/" + eldritchTotal;
        else if (possessed) sealLine += "   ·   P̷O̷S̷S̷E̷S̷S̷E̷D̷";
        smallText(g, sealLine, x + 22, y + 16,
                eldritch ? Painter.withAlpha(0xD08AF2, 0xD8)
                        : (unsigned ? Painter.withAlpha(0x7FD4E8, 0xCC) : Painter.withAlpha(t.frameLite, 0x88)),
                0.7f);
    }

    private void drawPattern(int x, int y, int w, int h, Theme t) {
        switch (t.pattern) {
            case Theme.PATTERN_DITHER ->
                    p.dither(x, y, w, h, Painter.withAlpha(t.panelLite, 0x40), 2, 0);
            case Theme.PATTERN_SCANLINE ->
                    p.scanlines(x, y, w, h, Painter.withAlpha(0x000000, 0x22));
            case Theme.PATTERN_PLANK -> {
                for (int i = 0; i < h; i += 14) {
                    p.rect(x, y + i, x + w, y + i + 1, Painter.withAlpha(t.panelDark, 0x88));
                    p.rect(x, y + i + 1, x + w, y + i + 2, Painter.withAlpha(t.panelLite, 0x30));
                }
                p.dither(x, y, w, h, Painter.withAlpha(t.panelLite, 0x22), 2, 1);
            }
            default -> {
            }
        }
    }

    private void drawFooter(GuiGraphics g, int x, int y, int w, int h) {
        Theme t = theme();
        int footerTop = y + h - 36;
        p.rect(x + 1, footerTop, x + w - 1, footerTop + 1, t.frame);
        p.rect(x + 1, footerTop + 1, x + w - 1, footerTop + 2, Painter.withAlpha(0x000000, 0x40));

        String message = (banner != null && System.currentTimeMillis() < bannerUntil) ? banner : null;
        if (message == null) banner = null;

        centered(g, message != null ? message : ((has(Anomaly.UNLIT) || affliction == 3) ? "" : hintText()),
                x + w / 2, footerTop + 10, message != null ? bannerColor : t.dim);

        // Two footer lines only: the previous three-line stack overlapped at
        // normal GUI scales. Keep the useful metadata but compact it into one ledger line.
        String meta = shortDim() + " · v" + value + " · x" + String.format("%.2f", lootMultiplier);
        if (streak > 0) meta += " · run " + streak;
        if (unsigned) {
            meta += " · unsigned · no casting";
            smallCentered(g, meta, x + w / 2, footerTop + 25,
                    Painter.withAlpha(0x7FD4E8, 0xC4), 0.68f);
        } else {
            meta += " · " + Sealmakers.maker(seed) + " · #" + Sealmakers.casting(seed);
            smallCentered(g, meta, x + w / 2, footerTop + 25,
                    Painter.withAlpha(t.faint, 0xB8), 0.68f);
        }

    }

    /**
     * Quirk labels as tabs hanging off the left flank of the frame, stacked downward. Nothing in
     * the play area has to make room for them and a chest can carry as many as it likes.
     */
    /**
     * Quirk labels as tabs hanging off the flank of the frame, stacked downward. Nothing in the
     * play area has to make room for them, so a chest can carry as many as it likes.
     *
     * <p>They go on whichever side has room. On a small window at a high GUI scale there may be no
     * room outside at all, in which case they tuck inside the panel's left edge instead of running
     * off the screen.
     */
    private void drawChips(GuiGraphics g, int panelX, int panelY) {
        Theme t = theme();
        float scale = 0.8f;
        int h = 13, gap = 4;

        int widest = 0;
        for (String s : modifierLabels) widest = Math.max(widest, Math.round(font.width(s) * scale) + 12);

        boolean outsideLeft = panelX - 9 - widest >= 2;
        boolean outsideRight = !outsideLeft && panelX + panelWidth() + 9 + widest <= width - 2;
        boolean right = outsideRight;
        boolean inside = !outsideLeft && !outsideRight;

        int y = panelY + (inside ? 32 : 40);
        for (String s : modifierLabels) {
            int w = Math.round(font.width(s) * scale) + 12;
            int x;
            if (inside) x = panelX + 4;
            else if (right) x = panelX + panelWidth() + 5;
            else x = panelX - 5 - w;

            p.rect(x + 2, y + 3, x + w + 3, y + h + 2, Painter.withAlpha(0x000000, 0x55));
            p.roundRect(x, y, w + 4, h, t.edge);
            p.roundRect(x + 1, y + 1, w + 2, h - 2, Painter.darken(t.panel, 0.10f) | 0xFF000000);
            p.rect(x + 1, y + 1, x + w + 3, y + 2, Painter.withAlpha(t.accent, 0x55));

            if (!inside) {
                // the tongue tucks under the frame so the tab reads as attached, not floating
                int stripeX = right ? x + w + 1 : x + 1;
                p.rect(stripeX, y + 3, stripeX + 2, y + h - 3, t.accent);
                if (right) p.rect(panelX + panelWidth() + 2, y + 2, x + 2, y + h - 2,
                        Painter.darken(t.panel, 0.10f) | 0xFF000000);
                else p.rect(x + w + 2, y + 2, panelX - 2, y + h - 2,
                        Painter.darken(t.panel, 0.10f) | 0xFF000000);
            } else {
                p.rect(x + 1, y + 3, x + 3, y + h - 3, t.accent);
            }

            smallCentered(g, s, x + 5 + w / 2, y + 3, Painter.lighten(t.accent, 0.35f), scale);
            y += h + gap;
        }
    }

    /** Lives as small watching eyes; a spent life is a shuttered one. */
    private void drawLives(int rightX, int cy) {
        Theme t = theme();
        for (int i = 0; i < maxLives; i++) {
            int cx = rightX - i * 12;
            boolean alive = (maxLives - i) <= lives;
            if (alive) {
                p.rect(cx - 4, cy - 2, cx + 4, cy + 2, Painter.darken(COL_GOOD, 0.62f));
                p.rect(cx - 3, cy - 3, cx + 3, cy + 3, COL_GOOD);
                p.rect(cx - 1, cy - 2, cx + 1, cy + 2, 0xFF10180C);
            } else {
                p.outline(cx - 4, cy - 3, 8, 6, 1, t.faint);
                p.rect(cx - 3, cy - 1, cx + 3, cy, t.faint);
            }
        }
    }

    /**
     * Faint per-dimension tint blended into the frame, so the same lock reads a little differently
     * in the Nether or the End without fighting the minigame's own colour.
     */
    protected int dimensionTint() {
        if (dimensionId == null) return theme().accent;
        if (dimensionId.endsWith("the_nether")) return 0xFFE05A2E;
        if (dimensionId.endsWith("the_end")) return 0xFFB177E0;
        if (dimensionId.endsWith("overworld")) return 0xFFC9A052;
        return 0xFF5FC7B0;
    }

    private String shortDim() {
        int i = dimensionId.indexOf(':');
        return i >= 0 ? dimensionId.substring(i + 1) : dimensionId;
    }

    protected abstract void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    protected abstract String hintText();
}
