package dev.marrowseal.wardbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.MinigameMastery;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.net.OpenMinigamePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cthulhu's Game: an ultra-rare shifting ritual lock.
 *
 * <p>The aim is not to be one more small lock with a different coat of paint,
 * but a single long encounter with its own cadence and personality. Every
 * passage asks for a different skill, the route itself changes by casting, the panel glitches and chatters constantly,
 * and the whole thing runs long enough to feel like an event instead of a pop
 * quiz.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CthulhusGameScreen extends BaseMinigameScreen {

    // Every ordinary Wardbound discipline has its own final-exam identity and
    // discipline-specific micro-exam. Shared visual/control primitives are
    // reused where appropriate, but the 26 syllabus entries keep distinct rules.
    // A single casting samples the syllabus instead of becoming a 26-part checklist.
    private static final int RITUAL_ORBIT = 0;       // Drum
    private static final int RITUAL_PULSE = 1;       // Timing
    private static final int RITUAL_MEMORY = 2;      // Memory
    private static final int RITUAL_MAW = 3;         // Gyre
    private static final int RITUAL_FALSE_CHOIR = 4; // Runes
    private static final int RITUAL_VEIL = 5;        // Parallax
    private static final int RITUAL_LATTICE = 6;     // Lattice
    private static final int RITUAL_EPITAPH = 7;     // Epitaph
    private static final int RITUAL_MEASURE = 8;     // Black Measure
    private static final int RITUAL_VEILSTEP = 9;    // Veilstep
    private static final int RITUAL_RUNEBRAID = 10;  // Runebraid
    private static final int RITUAL_RESONANCE = 11;  // Resonance
    private static final int RITUAL_CIPHER = 12;
    private static final int RITUAL_PRESSURE = 13;
    private static final int RITUAL_PULSE_DISCIPLINE = 14;
    private static final int RITUAL_BALANCE = 15;
    private static final int RITUAL_CONSTELLATION = 16;
    private static final int RITUAL_MIRROR_DISCIPLINE = 17;
    private static final int RITUAL_KEYWAY = 18;
    private static final int RITUAL_AUGURY = 19;
    private static final int RITUAL_VESSEL = 20;
    private static final int RITUAL_YOKE = 21;
    private static final int RITUAL_ROOTWAY = 22;
    private static final int RITUAL_SHARDSONG = 23;
    private static final int RITUAL_ORRERY = 24;
    private static final int RITUAL_PROCESSION = 25;
    private static final int DISCIPLINE_COUNT = 26;
    private static final int RITUAL_FINAL_MAW = 26;
    private static final String[] RITUAL_NAMES = {
            "Drum // Signal Orbit", "Timing // Split Pulse", "Memory // Witness Grid", "Gyre // Maw Verdict",
            "Runes // False Choir", "Parallax // Twin Veil", "Lattice // Black Lattice", "Epitaph // Redacted Verse",
            "Black Measure // Vessel Trial", "Veilstep // Veiled Road", "Runebraid // Knot Trial", "Resonance // Harmonic Trial",
            "Cipher // Ring Echo", "Pressure // Black Bellows", "Pulse // Choir Beat", "Balance // Twin Weight",
            "Constellation // Star Route", "Mirror // False Reflection", "Keyway // Tumbler Verdict", "Augury // Omen Grammar",
            "Vessel // Black Surface", "Yoke // Coupled Veil", "Rootway // Root Route", "Shardsong // Edge Choir",
            "Black Orrery // Coupled Meridian", "Last Procession // Witness Order", "THE FINAL MOUTH"
    };

    private static final int BG_COUNT = 22;
    private final int[] bgGlyph = new int[BG_COUNT];
    private final float[] bgX = new float[BG_COUNT];
    private final float[] bgY = new float[BG_COUNT];
    private final float[] bgDrift = new float[BG_COUNT];
    private final float[] bgPhase = new float[BG_COUNT];
    private final int[] bgScale = new int[BG_COUNT];

    private final Random rng;
    private Random phaseRng;
    /** Route index, not ritual id. The route itself is a seeded permutation. */
    private int phase;
    private int phaseCount;
    private int[] route;
    private float globalTime;
    private long phaseEntryGraceUntilNanos;
    private float glitchTimer;
    private float jitterX;
    private float jitterY;

    /** Final-exam score: one sample per completed passage, never one sample per click. */
    private float performanceTotal;
    private int performanceSamples;
    private float passageScoreTotal;
    private int passageScoreSamples;

    // ---------------------------------------------------------------- phase 0: orbital glyph hunt
    private int orbitTarget;
    private int orbitHits;
    private int orbitHitsNeeded;
    private float orbitSpin;
    private float orbitPulse;

    // ---------------------------------------------------------------- phase 1: timing bar
    private float barMarker;
    private float barDir;
    private float barTargetCenter;
    private float barTargetHalf;
    private float barHazardCenter;
    private float barHazardHalf;
    private int timingHits;
    private int timingHitsNeeded;

    // ---------------------------------------------------------------- phase 2: memory grid
    private final List<Integer> memorySequence = new ArrayList<>();
    private int memoryInputIndex;
    private int memoryFlash = -1;
    private int memoryShowIndex;
    private float memoryTimer;
    private boolean memoryPlayback;
    private float memoryFlashAlpha;

    // ---------------------------------------------------------------- phase 3: sweeping ring
    private final int[] mawSequence = new int[7];
    private int mawHitsNeeded;
    private int mawIndex;
    private float mawAngle;
    private float mawSpeed;
    private float mawWindow;
    private float mawPulse;

    // ---------------------------------------------------------------- ritual: false choir
    private final int[] choirGlyphs = new int[9];
    private int choirTargetGlyph;
    private int choirCorrectCell;
    private int choirRound;
    private int choirRoundsNeeded;
    private float choirPreviewTimer;
    private boolean choirPreview;

    // ---------------------------------------------------------------- ritual: twin veil
    private float veilAngleA;
    private float veilAngleB;
    private float veilSpeedA;
    private float veilSpeedB;
    private float veilWindow;
    private int veilHits;
    private int veilHitsNeeded;

    // ---------------------------------------------------------------- borrowed discipline: black lattice
    private int latticeMask;
    private int latticeMoves;
    private int latticeIdeal;

    // ---------------------------------------------------------------- borrowed discipline: epitaph
    private final int[] epitaphBase = new int[4];
    private final int[] epitaphWitness = new int[4];
    private final int[] epitaphFinal = new int[4];
    private final int[] epitaphOptions = new int[4];
    private int epitaphMissing;
    private int epitaphAnswer;

    // ---------------------------------------------------------------- borrowed discipline: black measure
    private final int[] measureCap = {8, 5, 3};
    private final int[] measureAmt = {8, 0, 0};
    private int measureSelected = -1;
    private int measureMoves;
    private int measureIdeal;
    private int measureTarget;

    // ---------------------------------------------------------------- borrowed discipline: veilstep
    private final int[] stepRoute = new int[6];
    private final int[] stepJump = new int[16];
    private int stepIndex;
    private int stepGoal;

    // ---------------------------------------------------------------- borrowed discipline: runebraid
    private final int[] braid = new int[5];
    private int braidSelected = -1;
    private int braidMoves;
    private int braidIdeal;

    // ---------------------------------------------------------------- borrowed discipline: resonance
    private float resonanceTarget;
    private float resonanceHalf;
    private int resonanceHits;
    private int resonanceHitsNeeded;


    // ---------------------------------------------------------------- discipline echo: cipher
    private final int[] cipherPos = new int[3];
    private final int[] cipherTarget = new int[3];
    private int cipherMoves;
    private int cipherIdeal;

    // ---------------------------------------------------------------- discipline echo: pressure
    private float pressureValue;
    private float pressureTarget;
    private float pressureHalf;
    private int pressureRounds;
    private int pressureRoundsNeeded;
    private float pressureLeak;
    private float pressureBurst;
    private int pressurePumps;

    // ---------------------------------------------------------------- discipline echo: pulse / rhythm
    private final float[] pulseBeats = new float[7];
    private float pulseSongTime;
    private int pulseBeatIndex;
    private float pulseWindow;

    // ---------------------------------------------------------------- discipline echo: balance
    private float balanceTilt;
    private float balanceVelocity;
    private float balanceDrift;
    private float balanceStable;

    // ---------------------------------------------------------------- discipline echo: constellation
    private final float[] constellationX = new float[7];
    private final float[] constellationY = new float[7];
    private final int[] constellationPath = new int[7];
    private boolean constellationPreview;
    private float constellationPreviewTimer;
    private int constellationInput;
    private int constellationMistFlash = -1;
    private float constellationMistFlashTimer;

    // ---------------------------------------------------------------- discipline echo: mirror
    private int mirrorTargetGlyph;
    private boolean mirrorTargetFlipX;
    private boolean mirrorTargetFlipY;
    private final int[] mirrorGlyph = new int[4];
    private final boolean[] mirrorFlipX = new boolean[4];
    private final boolean[] mirrorFlipY = new boolean[4];
    private int mirrorAnswer;
    private int mirrorRound;
    private int mirrorRoundsNeeded;

    // ---------------------------------------------------------------- discipline echo: keyway
    private final float[] keywayCenter = new float[4];
    private final float[] keywayHalf = new float[4];
    private final float[] keywayFalseCenter = new float[4];
    private float keywayMarker;
    private float keywayDir;
    private int keywayPin;

    // ---------------------------------------------------------------- discipline echo: augury
    private final int[] augurySecret = new int[3];
    private final int[] auguryGuess = new int[3];
    private int auguryAttempts;
    private int auguryExact;
    private int auguryMisplaced;

    // ---------------------------------------------------------------- discipline echo: vessel
    private final int[] vesselShell = {0, 1, 2, 3};
    private int vesselTargetToken;
    private boolean vesselReveal;
    private float vesselRevealTimer;
    private float vesselShuffleTimer;
    private int vesselShufflesLeft;
    private int vesselSwapA = -1;
    private int vesselSwapB = -1;
    private float vesselSwapAnim;

    // ---------------------------------------------------------------- discipline echo: yoke
    private float yokeA;
    private float yokeB;
    private float yokeTargetA;
    private float yokeTargetB;
    private float yokeHalf;
    private int yokeMoves;
    private int yokeRound;
    private int yokeRoundsNeeded;

    // ---------------------------------------------------------------- discipline echo: rootway
    private final boolean[] rootOpen = new boolean[16 * 4];
    private final int[] rootRoute = new int[8];
    private int rootIndex;
    private int rootGoal;
    private float rootDecisionTimer;
    private float rootDecisionLimit;

    // ---------------------------------------------------------------- discipline echo: shardsong
    private final int[][] shardEdge = new int[4][4];
    private final int[] shardRot = new int[4];
    private int shardMoves;
    private int shardIdeal;

    // ---------------------------------------------------------------- discipline echo: black orrery
    private final int[] orreryEcho = new int[3];
    private int orreryEchoMoves;
    private int orreryEchoIdeal;

    // ---------------------------------------------------------------- discipline echo: last procession
    private final int[] processionEchoTarget = new int[4];
    private final int[] processionEchoCurrent = new int[4];
    private int processionEchoSelected = -1;
    private int processionEchoMoves;
    private int processionEchoIdeal;

    public CthulhusGameScreen(OpenMinigamePacket msg) {
        super(Component.translatable("wardbound.title.cthulhus_game"), msg);
        rng = new Random(seed ^ 0x435448554C48554CL);
        buildRoute();
        phase = Mth.clamp(progress, 0, phaseCount - 1);
        // Completed passages came from a prior screen instance when resuming.
        // Give them a conservative baseline rather than letting ESC erase them
        // from the final performance score entirely.
        performanceTotal = phase * 0.82f;
        performanceSamples = phase;
        passageScoreTotal = 0f;
        passageScoreSamples = 0;
        modifierLabels.add("singular ward");
        modifierLabels.add("final exam · " + phaseCount + " passages");
        modifierLabels.add("exam weakness · " + MinigameMastery.prettify(examWeakGame.id));
        modifierLabels.add("known strength · " + MinigameMastery.prettify(examStrongGame.id));
        if (eldritch) modifierLabels.add("watched too closely");
        buildBackground();
        enterPhase(false);
    }

    @Override
    protected Theme createTheme() {
        return Theme.of(0xFF58666F, 0xFF080B10, 0xFF7EE0C4, 0xFFF1DDF8,
                1, Theme.PATTERN_SCANLINE);
    }

    @Override
    protected int panelWidth() {
        return 352;
    }

    @Override
    protected int panelHeight() {
        return 286;
    }

    @Override
    protected float timeBudget() {
        int passages = passageCountForSeed(seed, masteryTier);
        // The exam is longer because it now borrows real disciplines instead of
        // only repeating six bespoke reflex checks. Extra mastery opens extra
        // passages, but the clock grows with them rather than becoming a hidden
        // double punishment.
        return Math.max(46f, super.timeBudget() * (2.25f + (passages - 6) * 0.30f));
    }

    @Override
    protected boolean allows(Anomaly a) {
        return a != Anomaly.SHROUDED && a != Anomaly.GUTTERING && a != Anomaly.UNLIT;
    }

    private static int passageCountForSeed(long seed, int masteryTier) {
        int masteryExtra = Math.min(4, Math.max(0, masteryTier));
        int seedExtra = Math.floorMod((int) (seed ^ (seed >>> 32)), 2);
        return Math.min(13, 8 + masteryExtra + seedExtra);
    }

    /** Unique discipline identity. Mechanics are mapped separately below. */
    private static int disciplineRitual(MinigameType game) {
        return switch (game) {
            case DRUM -> RITUAL_ORBIT;
            case TIMING -> RITUAL_PULSE;
            case MEMORY -> RITUAL_MEMORY;
            case RUNES -> RITUAL_FALSE_CHOIR;
            case CIPHER -> RITUAL_CIPHER;
            case PRESSURE -> RITUAL_PRESSURE;
            case RESONANCE -> RITUAL_RESONANCE;
            case PULSE -> RITUAL_PULSE_DISCIPLINE;
            case BALANCE -> RITUAL_BALANCE;
            case CONSTELLATION -> RITUAL_CONSTELLATION;
            case MIRROR -> RITUAL_MIRROR_DISCIPLINE;
            case KEYWAY -> RITUAL_KEYWAY;
            case AUGURY -> RITUAL_AUGURY;
            case GYRE -> RITUAL_MAW;
            case VESSEL -> RITUAL_VESSEL;
            case YOKE -> RITUAL_YOKE;
            case ROOTWAY -> RITUAL_ROOTWAY;
            case LATTICE -> RITUAL_LATTICE;
            case PARALLAX -> RITUAL_VEIL;
            case RUNEBRAID -> RITUAL_RUNEBRAID;
            case SHARDSONG -> RITUAL_SHARDSONG;
            case VEILSTEP -> RITUAL_VEILSTEP;
            case BLACK_MEASURE -> RITUAL_MEASURE;
            case EPITAPH -> RITUAL_EPITAPH;
            case ORRERY -> RITUAL_ORRERY;
            case PROCESSION -> RITUAL_PROCESSION;
            case CTHULHUS_GAME -> RITUAL_FINAL_MAW;
        };
    }

    /**
     * Every ordinary discipline keeps its own micro-exam. Only the singular
     * Final Mouth deliberately reuses the Gyre/Maw mechanic as the closing
     * verdict. This is what makes the encounter an actual cross-system final
     * rather than twelve mechanics wearing twenty-six labels.
     */
    private static int mechanicFor(int ritual) {
        return ritual == RITUAL_FINAL_MAW ? RITUAL_MAW : ritual;
    }

    private boolean isWeakPassage() {
        return ritual() == disciplineRitual(examWeakGame);
    }

    private boolean isStrongPassage() {
        return !isWeakPassage() && ritual() == disciplineRitual(examStrongGame);
    }

    private void buildRoute() {
        // One passage from each major skill family is mandatory. The weakest
        // observed discipline is then injected, one mastered strength is
        // recognised, and the rest is sampled without replacement from all 26
        // ordinary minigames. The Maw is always last.
        Random rr = new Random(seed ^ 0x524F5554455F4354L);
        phaseCount = passageCountForSeed(seed, masteryTier);
        int[][] families = {
                {RITUAL_ORBIT, RITUAL_PULSE, RITUAL_FALSE_CHOIR, RITUAL_PRESSURE, RITUAL_PULSE_DISCIPLINE, RITUAL_MAW, RITUAL_KEYWAY},
                {RITUAL_MEMORY, RITUAL_CONSTELLATION, RITUAL_MIRROR_DISCIPLINE, RITUAL_VESSEL},
                {RITUAL_CIPHER, RITUAL_AUGURY, RITUAL_LATTICE, RITUAL_EPITAPH, RITUAL_RUNEBRAID, RITUAL_SHARDSONG, RITUAL_MEASURE, RITUAL_ORRERY, RITUAL_PROCESSION},
                {RITUAL_RESONANCE, RITUAL_BALANCE, RITUAL_VEIL, RITUAL_YOKE, RITUAL_ROOTWAY, RITUAL_VEILSTEP}
        };
        java.util.ArrayList<Integer> chosen = new java.util.ArrayList<>();
        for (int[] family : families) chosen.add(family[rr.nextInt(family.length)]);

        int weak = disciplineRitual(examWeakGame);
        int strong = disciplineRitual(examStrongGame);
        if (weak < DISCIPLINE_COUNT && !chosen.contains(weak)) chosen.add(weak);
        if (strong < DISCIPLINE_COUNT && strong != weak && !chosen.contains(strong)) chosen.add(strong);

        java.util.ArrayList<Integer> rest = new java.util.ArrayList<>();
        for (int i = 0; i < DISCIPLINE_COUNT; i++) if (!chosen.contains(i)) rest.add(i);
        java.util.Collections.shuffle(rest, rr);
        int middleCount = phaseCount - 1;
        for (int id : rest) {
            if (chosen.size() >= middleCount) break;
            chosen.add(id);
        }
        while (chosen.size() > middleCount) chosen.remove(chosen.size() - 1);
        java.util.Collections.shuffle(chosen, rr);

        route = new int[phaseCount];
        for (int i = 0; i < middleCount; i++) route[i] = chosen.get(i);
        route[phaseCount - 1] = RITUAL_FINAL_MAW;
    }

    private int ritual() {
        return route[Mth.clamp(phase, 0, phaseCount - 1)];
    }

    private int mechanic() { return mechanicFor(ritual()); }

    private String ritualName() {
        return RITUAL_NAMES[Mth.clamp(ritual(), 0, RITUAL_NAMES.length - 1)];
    }

    private Random ritualRandom() {
        long z = seed ^ (0x9E3779B97F4A7C15L * (phase + 1L)) ^ (ritual() * 0x632BE59BD9B4E019L);
        return new Random(z);
    }

    private void buildBackground() {
        int innerW = panelWidth() - 34;
        int innerH = panelHeight() - 82;
        for (int i = 0; i < BG_COUNT; i++) {
            bgGlyph[i] = rng.nextInt(Sigils.TILE.length);
            bgX[i] = 18 + rng.nextFloat() * innerW;
            bgY[i] = 8 + rng.nextFloat() * innerH;
            bgDrift[i] = 10f + rng.nextFloat() * 28f;
            bgPhase[i] = rng.nextFloat() * 6.28318f;
            bgScale[i] = 1 + rng.nextInt(2);
        }
    }

    private void enterPhase(boolean banner) {
        phaseRng = ritualRandom();
        switch (mechanic()) {
            case RITUAL_ORBIT -> setupOrbit();
            case RITUAL_PULSE -> setupTiming();
            case RITUAL_MEMORY -> setupMemory();
            case RITUAL_MAW -> setupMaw();
            case RITUAL_FALSE_CHOIR -> setupChoir();
            case RITUAL_VEIL -> setupVeil();
            case RITUAL_LATTICE -> setupLattice();
            case RITUAL_EPITAPH -> setupEpitaph();
            case RITUAL_MEASURE -> setupMeasure();
            case RITUAL_VEILSTEP -> setupVeilstep();
            case RITUAL_RUNEBRAID -> setupRunebraid();
            case RITUAL_RESONANCE -> setupResonance();
            case RITUAL_CIPHER -> setupCipher();
            case RITUAL_PRESSURE -> setupPressureEcho();
            case RITUAL_PULSE_DISCIPLINE -> setupPulseEcho();
            case RITUAL_BALANCE -> setupBalanceEcho();
            case RITUAL_CONSTELLATION -> setupConstellationEcho();
            case RITUAL_MIRROR_DISCIPLINE -> setupMirrorEcho();
            case RITUAL_KEYWAY -> setupKeywayEcho();
            case RITUAL_AUGURY -> setupAuguryEcho();
            case RITUAL_VESSEL -> setupVesselEcho();
            case RITUAL_YOKE -> setupYokeEcho();
            case RITUAL_ROOTWAY -> setupRootwayEcho();
            case RITUAL_SHARDSONG -> setupShardsongEcho();
            case RITUAL_ORRERY -> setupOrreryEcho();
            case RITUAL_PROCESSION -> setupProcessionEcho();
            default -> finishEncounter();
        }
        // Phase titles used to consume live mechanic time. Memory previews could
        // disappear behind the title, Rootway lost almost half its first decision
        // window, and Pulse could miss a beat before the player had control.
        phaseEntryGraceUntilNanos = System.nanoTime() + (banner ? 950_000_000L : 350_000_000L);
        if (banner) {
            showBanner(ritualName(), Painter.lighten(theme().accent, 0.22f), 900);
            Sfx.play(WardSounds.EYE_OPEN, 0.45f, 0.84f + phase * 0.045f);
        }
    }

    private void advancePhase() {
        commitPassageScore();
        progress = ++phase;
        if (phase >= phaseCount) {
            finishEncounter();
            return;
        }
        enterPhase(true);
    }

    private void commitPassageScore() {
        float score = passageScoreSamples <= 0 ? 0.75f : passageScoreTotal / passageScoreSamples;
        performanceTotal += Mth.clamp(score, 0f, 1f);
        performanceSamples++;
        passageScoreTotal = 0f;
        passageScoreSamples = 0;
    }

    private void finishEncounter() {
        float accuracy = performanceSamples <= 0 ? 0.75f : performanceTotal / performanceSamples;
        float livesScore = maxLives <= 0 ? 1f : Mth.clamp(lives / (float) maxLives, 0f, 1f);
        succeed(Mth.clamp(accuracy * 0.78f + livesScore * 0.22f, 0.30f, 1f));
    }

    /** Records accuracy inside the current passage; the passage itself receives exactly one final-exam vote. */
    private void record(float value) {
        passageScoreTotal += Mth.clamp(value, 0f, 1f);
        passageScoreSamples++;
    }

    private void setupOrbit() {
        orbitHits = 0;
        orbitHitsNeeded = 5 + (value >= 70 ? 1 : 0) + (isWeakPassage() ? 1 : 0);
        orbitTarget = phaseRng.nextInt(7);
        orbitSpin = 0f;
        orbitPulse = 0f;
    }

    private void setupTiming() {
        timingHits = 0;
        int identityExtra = ritual() == RITUAL_PULSE_DISCIPLINE ? 2 : ritual() == RITUAL_PRESSURE ? 1 : 0;
        timingHitsNeeded = 5 + identityExtra + (isWeakPassage() ? 1 : 0);
        barMarker = phaseRng.nextBoolean() ? 0.08f : 0.92f;
        barDir = barMarker < 0.5f ? 1f : -1f;
        resetTimingTarget();
    }

    private void resetTimingTarget() {
        float identityWindow = ritual() == RITUAL_PULSE_DISCIPLINE ? 0.86f : ritual() == RITUAL_PRESSURE ? 1.08f : 1f;
        barTargetHalf = Math.max(0.040f, (0.11f - timingHits * 0.010f - Math.max(0f, difficulty - 1f) * 0.018f)
                * identityWindow * (isWeakPassage() ? 0.88f : 1f));
        barHazardHalf = barTargetHalf * (ritual() == RITUAL_PRESSURE ? 1.05f : 0.85f);
        barTargetCenter = 0.16f + phaseRng.nextFloat() * 0.68f;
        do {
            barHazardCenter = 0.12f + phaseRng.nextFloat() * 0.76f;
        } while (Math.abs(barHazardCenter - barTargetCenter) < barTargetHalf + barHazardHalf + 0.08f);
    }

    private void setupMemory() {
        memorySequence.clear();
        int length = 6 + (value >= 80 ? 1 : 0) + (ritual() == RITUAL_VESSEL ? 1 : 0) + (isWeakPassage() ? 1 : 0);
        for (int i = 0; i < length; i++) memorySequence.add(phaseRng.nextInt(9));
        memoryInputIndex = 0;
        memoryShowIndex = 0;
        memoryFlash = -1;
        memoryTimer = ritual() == RITUAL_VESSEL ? 0.30f : 0.4f;
        memoryPlayback = true;
        memoryFlashAlpha = 0f;
    }

    private void setupMaw() {
        for (int i = 0; i < mawSequence.length; i++) mawSequence[i] = phaseRng.nextInt(8);
        mawHitsNeeded = ritual() == RITUAL_KEYWAY ? 5 : ritual() == RITUAL_MAW ? 6 : 7;
        if (isWeakPassage() && ritual() != RITUAL_FINAL_MAW) mawHitsNeeded = Math.min(7, mawHitsNeeded + 1);
        mawIndex = 0;
        mawAngle = phaseRng.nextFloat() * Mth.TWO_PI;
        float identitySpeed = ritual() == RITUAL_KEYWAY ? 0.90f : ritual() == RITUAL_MAW ? 1.03f : 1.12f;
        mawSpeed = (1.55f + difficulty * 0.55f + masteryTier * 0.07f) * identitySpeed;
        float identityWindow = ritual() == RITUAL_KEYWAY ? 0.90f : ritual() == RITUAL_FINAL_MAW ? 0.86f : 1f;
        mawWindow = Math.max(0.16f, (0.26f - masteryTier * 0.008f) * identityWindow);
        mawPulse = 0f;
    }

    private void setupChoir() {
        choirRound = 0;
        choirRoundsNeeded = 4 + (value >= 72 ? 1 : 0) + (ritual() == RITUAL_MIRROR_DISCIPLINE ? 1 : 0) + (isWeakPassage() ? 1 : 0);
        nextChoirRound();
    }

    private void nextChoirRound() {
        choirTargetGlyph = phaseRng.nextInt(Sigils.TILE.length);
        choirCorrectCell = phaseRng.nextInt(9);
        for (int i = 0; i < choirGlyphs.length; i++) {
            if (i == choirCorrectCell) {
                choirGlyphs[i] = choirTargetGlyph;
            } else {
                int g;
                do g = phaseRng.nextInt(Sigils.TILE.length); while (g == choirTargetGlyph);
                choirGlyphs[i] = g;
            }
        }
        float identityPreview = ritual() == RITUAL_MIRROR_DISCIPLINE ? -0.10f : 0f;
        choirPreviewTimer = Math.max(0.46f, 0.88f + identityPreview - choirRound * 0.055f - Math.max(0f, difficulty - 1f) * 0.06f);
        choirPreview = true;
    }

    private void setupVeil() {
        veilHits = 0;
        int identityExtra = ritual() == RITUAL_YOKE ? 2 : ritual() == RITUAL_BALANCE ? 1 : 0;
        veilHitsNeeded = 5 + identityExtra + (value >= 82 ? 1 : 0) + (isWeakPassage() ? 1 : 0);
        veilAngleA = phaseRng.nextFloat() * Mth.TWO_PI;
        veilAngleB = veilAngleA + 1.2f + phaseRng.nextFloat() * 2.3f;
        float identitySpeed = ritual() == RITUAL_YOKE ? 1.10f : ritual() == RITUAL_BALANCE ? 0.92f : 1f;
        veilSpeedA = (0.82f + difficulty * 0.34f) * identitySpeed;
        veilSpeedB = (1.05f + difficulty * 0.29f) * (ritual() == RITUAL_YOKE ? 1.16f : identitySpeed);
        float identityWindow = ritual() == RITUAL_BALANCE ? 0.90f : ritual() == RITUAL_YOKE ? 0.86f : 1f;
        veilWindow = Math.max(0.105f, (0.24f - Math.max(0f, difficulty - 1f) * 0.035f)
                * identityWindow * (isWeakPassage() ? 0.88f : 1f));
    }

    private static int latticeCrossMask(int cell) {
        int r = cell / 3, c = cell % 3;
        int mask = 1 << cell;
        if (r > 0) mask |= 1 << (cell - 3);
        if (r < 2) mask |= 1 << (cell + 3);
        if (c > 0) mask |= 1 << (cell - 1);
        if (c < 2) mask |= 1 << (cell + 1);
        return mask;
    }

    private static int latticeDistance(int start) {
        if (start == 0) return 0;
        int[] dist = new int[512];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[512];
        int head = 0, tail = 0;
        dist[start] = 0; queue[tail++] = start;
        while (head < tail) {
            int state = queue[head++];
            for (int cell = 0; cell < 9; cell++) {
                int next = state ^ latticeCrossMask(cell);
                if (dist[next] >= 0) continue;
                dist[next] = dist[state] + 1;
                if (next == 0) return dist[next];
                queue[tail++] = next;
            }
        }
        return 9;
    }

    private void setupLattice() {
        int minDepth = isWeakPassage() ? 5 : 4;
        latticeMask = 0;
        latticeIdeal = 0;
        for (int attempt = 0; attempt < 36 && latticeIdeal < minDepth; attempt++) {
            latticeMask = 0;
            int scramble = 5 + phaseRng.nextInt(4) + (ritual() == RITUAL_SHARDSONG ? 2 : 0) + (isWeakPassage() ? 1 : 0);
            for (int i = 0; i < scramble; i++) latticeMask ^= latticeCrossMask(phaseRng.nextInt(9));
            latticeIdeal = latticeDistance(latticeMask);
        }
        // All 3x3 Lights-Out states are solvable here. If unlucky random turns
        // cancelled too heavily, choose a verified deep state instead of letting
        // the final exam collapse into a one-click board.
        if (latticeIdeal < minDepth) {
            int start = phaseRng.nextInt(512);
            for (int i = 0; i < 512; i++) {
                int candidate = (start + i) & 511;
                int d = latticeDistance(candidate);
                if (d >= minDepth) { latticeMask = candidate; latticeIdeal = d; break; }
            }
        }
        latticeMoves = 0;
        latticeIdeal = Math.max(1, latticeIdeal);
    }

    private void setupEpitaph() {
        int glyphs = Sigils.TILE.length;
        int shift = 1 + phaseRng.nextInt(Math.min(4, glyphs - 1));
        java.util.HashSet<Integer> used = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            int g;
            do g = phaseRng.nextInt(glyphs); while (!used.add(g));
            epitaphBase[i] = g;
        }
        int positionStep = ritual() == RITUAL_AUGURY ? 3 : 1;
        for (int i = 0; i < 4; i++) epitaphWitness[i] = Math.floorMod(epitaphBase[(i + positionStep) % 4] + shift, glyphs);
        for (int i = 0; i < 4; i++) epitaphFinal[i] = Math.floorMod(epitaphWitness[(i + positionStep) % 4] + shift, glyphs);
        epitaphMissing = phaseRng.nextInt(4);
        int correct = epitaphFinal[epitaphMissing];
        java.util.ArrayList<Integer> opts = new java.util.ArrayList<>();
        opts.add(correct);
        while (opts.size() < 4) {
            int g = phaseRng.nextInt(glyphs);
            if (!opts.contains(g)) opts.add(g);
        }
        java.util.Collections.shuffle(opts, phaseRng);
        for (int i = 0; i < 4; i++) {
            epitaphOptions[i] = opts.get(i);
            if (opts.get(i) == correct) epitaphAnswer = i;
        }
    }

    private static int measureCode(int a, int b, int c) { return a * 24 + b * 4 + c; }

    private int measureDistance(int target) {
        int totalStates = 9 * 6 * 4;
        int[] dist = new int[totalStates];
        java.util.Arrays.fill(dist, -1);
        int[] qa = new int[totalStates], qb = new int[totalStates], qc = new int[totalStates];
        int head = 0, tail = 0;
        qa[tail] = 8; qb[tail] = 0; qc[tail] = 0; tail++;
        dist[measureCode(8,0,0)] = 0;
        while (head < tail) {
            int[] cur = {qa[head], qb[head], qc[head]};
            int cd = dist[measureCode(cur[0],cur[1],cur[2])]; head++;
            if (cur[0] == target || cur[1] == target || cur[2] == target) return cd;
            for (int src = 0; src < 3; src++) for (int dst = 0; dst < 3; dst++) {
                if (src == dst || cur[src] <= 0 || cur[dst] >= measureCap[dst]) continue;
                int[] n = cur.clone();
                int moved = Math.min(n[src], measureCap[dst] - n[dst]);
                n[src] -= moved; n[dst] += moved;
                int code = measureCode(n[0], n[1], n[2]);
                if (dist[code] >= 0) continue;
                dist[code] = cd + 1;
                qa[tail]=n[0]; qb[tail]=n[1]; qc[tail]=n[2]; tail++;
            }
        }
        return 8;
    }

    private void setupMeasure() {
        measureAmt[0]=8; measureAmt[1]=0; measureAmt[2]=0;
        measureSelected = -1;
        measureMoves = 0;
        // Target 2 has a two-pour solution and made the final exam feel like a
        // tutorial echo. Use only the deeper reachable quantities.
        int[] candidates = {1, 4, 7};
        measureTarget = candidates[phaseRng.nextInt(candidates.length)];
        if (isWeakPassage()) {
            int best = measureTarget, bestD = measureDistance(best);
            for (int candidate : candidates) {
                int d = measureDistance(candidate);
                if (d > bestD) { best = candidate; bestD = d; }
            }
            measureTarget = best;
        }
        measureIdeal = Math.max(1, measureDistance(measureTarget));
    }

    private static int manhattan(int a, int b) {
        return Math.abs(a / 4 - b / 4) + Math.abs(a % 4 - b % 4);
    }

    private void setupVeilstep() {
        java.util.Arrays.fill(stepJump, 1);
        java.util.HashSet<Integer> used = new java.util.HashSet<>();
        int current = phaseRng.nextInt(16);
        stepRoute[0] = current; used.add(current);
        int maxJump = ritual() == RITUAL_ROOTWAY ? 2 : ritual() == RITUAL_CONSTELLATION ? 3 : 3;
        int minJump = ritual() == RITUAL_CONSTELLATION ? 2 : 1;
        for (int i = 1; i < stepRoute.length; i++) {
            java.util.ArrayList<Integer> candidates = new java.util.ArrayList<>();
            for (int c = 0; c < 16; c++) {
                int d = manhattan(current, c);
                if (!used.contains(c) && d >= minJump && d <= maxJump) candidates.add(c);
            }
            if (candidates.isEmpty()) {
                setupVeilstep();
                return;
            }
            int next = candidates.get(phaseRng.nextInt(candidates.size()));
            stepJump[current] = manhattan(current, next);
            stepRoute[i] = next; used.add(next); current = next;
        }
        for (int c = 0; c < 16; c++) if (!used.contains(c)) stepJump[c] = 1 + phaseRng.nextInt(3);
        stepIndex = 0;
        stepGoal = stepRoute[stepRoute.length - 1];
    }

    private static int inversionCount(int[] arr) {
        int n=0;
        for (int i=0;i<arr.length;i++) for (int j=i+1;j<arr.length;j++) if (arr[i]>arr[j]) n++;
        return n;
    }

    private void setupRunebraid() {
        for (int i=0;i<braid.length;i++) braid[i]=i;
        int swaps = 5 + phaseRng.nextInt(4) + (ritual() == RITUAL_CIPHER ? 3 : 0) + (isWeakPassage() ? 2 : 0);
        for (int i=0;i<swaps;i++) {
            int at=phaseRng.nextInt(braid.length-1);
            int t=braid[at]; braid[at]=braid[at+1]; braid[at+1]=t;
        }
        if (inversionCount(braid)==0) { int t=braid[1]; braid[1]=braid[2]; braid[2]=t; }
        braidIdeal=Math.max(1,inversionCount(braid));
        braidMoves=0; braidSelected=-1;
    }

    private void setupResonance() {
        resonanceHits=0;
        resonanceHitsNeeded=3 + (masteryTier >= 4 ? 1 : 0) + (isWeakPassage() ? 1 : 0);
        resonanceHalf=Math.max(0.035f, (0.070f - Math.max(0f,difficulty-1f)*0.012f)
                * (isWeakPassage() ? 0.88f : 1f));
        resonanceTarget=0.18f + phaseRng.nextFloat()*0.64f;
    }


    private static int cipherCode(int a, int b, int c) { return a * 36 + b * 6 + c; }

    private int cipherDistance() {
        int start = cipherCode(cipherPos[0], cipherPos[1], cipherPos[2]);
        int goal = cipherCode(cipherTarget[0], cipherTarget[1], cipherTarget[2]);
        if (start == goal) return 0;
        int[] dist = new int[216];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[216];
        int head = 0, tail = 0;
        dist[start] = 0; queue[tail++] = start;
        while (head < tail) {
            int state = queue[head++];
            int[] v = {state / 36, (state / 6) % 6, state % 6};
            for (int ring = 0; ring < 3; ring++) {
                int[] n = v.clone();
                int neighbour = (ring + 1) % 3;
                n[ring] = (n[ring] + 1) % 6;
                n[neighbour] = Math.floorMod(n[neighbour] - 1, 6);
                int code = cipherCode(n[0], n[1], n[2]);
                if (dist[code] >= 0) continue;
                dist[code] = dist[state] + 1;
                if (code == goal) return dist[code];
                queue[tail++] = code;
            }
        }
        return 12;
    }

    private void setupCipher() {
        for (int i = 0; i < 3; i++) cipherTarget[i] = phaseRng.nextInt(6);
        int minDepth = isWeakPassage() ? 5 : 4;
        cipherIdeal = 0;
        for (int attempt = 0; attempt < 48 && cipherIdeal < minDepth; attempt++) {
            System.arraycopy(cipherTarget, 0, cipherPos, 0, 3);
            int scramble = 7 + phaseRng.nextInt(6) + (isWeakPassage() ? 2 : 0);
            for (int i = 0; i < scramble; i++) rotateCipherCoupled(phaseRng.nextInt(3));
            cipherIdeal = cipherDistance();
        }
        // A rare cancellation streak should never create a tutorial-depth final
        // exam. Continue through reachable states until an exact deep state is found.
        if (cipherIdeal < minDepth) {
            System.arraycopy(cipherTarget, 0, cipherPos, 0, 3);
            for (int a = 0; a < 6 && cipherIdeal < minDepth; a++)
                for (int b = 0; b < 6 && cipherIdeal < minDepth; b++)
                    for (int c = 0; c < 6 && cipherIdeal < minDepth; c++) {
                        cipherPos[0]=a; cipherPos[1]=b; cipherPos[2]=c;
                        int d = cipherDistance();
                        if (d >= minDepth && d < 12) cipherIdeal = d;
                    }
        }
        cipherMoves = 0;
        cipherIdeal = Math.max(1, cipherIdeal);
    }

    private void rotateCipherCoupled(int ring) {
        int neighbour = (ring + 1) % 3;
        cipherPos[ring] = (cipherPos[ring] + 1) % 6;
        cipherPos[neighbour] = Math.floorMod(cipherPos[neighbour] - 1, 6);
    }

    private boolean cipherSolved() {
        for (int i = 0; i < 3; i++) if (cipherPos[i] != cipherTarget[i]) return false;
        return true;
    }

    private void setupPressureEcho() {
        pressureValue = 0f;
        pressureRounds = 0;
        pressureRoundsNeeded = 4 + (isWeakPassage() ? 1 : 0);
        nextPressureBand();
    }

    private void nextPressureBand() {
        pressureTarget = 0.34f + phaseRng.nextFloat() * 0.48f;
        pressureHalf = Math.max(0.050f, (0.112f - pressureRounds * 0.010f - Math.max(0f, difficulty - 1f) * 0.016f)
                * (isWeakPassage() ? 0.88f : 1f));
        pressureLeak = 0.032f + pressureRounds * 0.006f + Math.max(0f, difficulty - 1f) * 0.005f;
        pressureBurst = Mth.clamp(pressureTarget + pressureHalf + 0.12f + phaseRng.nextFloat() * 0.045f, 0.70f, 0.985f);
        pressurePumps = 0;
        pressureValue = Math.max(0f, pressureTarget - 0.30f - phaseRng.nextFloat() * 0.10f);
    }

    private void stepPressureEcho(float dt) {
        // The bellows bleeds continuously. This turns Pressure into an active
        // control problem instead of a deterministic click-count followed by SEAL.
        pressureValue = Math.max(0f, pressureValue - pressureLeak * dt);
    }

    private void setupPulseEcho() {
        pulseSongTime = -0.65f;
        pulseBeatIndex = 0;
        pulseWindow = Math.max(0.085f, 0.155f - Math.max(0f, difficulty - 1f) * 0.018f - (isWeakPassage() ? 0.018f : 0f));
        float at = 0.65f;
        for (int i = 0; i < pulseBeats.length; i++) {
            at += 0.46f + phaseRng.nextFloat() * 0.30f;
            pulseBeats[i] = at;
        }
    }

    private void setupBalanceEcho() {
        balanceTilt = (0.20f + phaseRng.nextFloat() * 0.18f) * (phaseRng.nextBoolean() ? 1f : -1f);
        balanceVelocity = 0f;
        balanceDrift = phaseRng.nextBoolean() ? 0.075f : -0.075f;
        if (isWeakPassage()) balanceDrift *= 1.30f;
        balanceStable = 0f;
    }

    private void setupConstellationEcho() {
        float[][] anchor = {{0.16f,0.24f},{0.50f,0.14f},{0.82f,0.27f},{0.28f,0.51f},{0.69f,0.49f},{0.18f,0.77f},{0.77f,0.77f}};
        for (int i = 0; i < constellationX.length; i++) {
            constellationX[i] = Mth.clamp(anchor[i][0] + (phaseRng.nextFloat() - 0.5f) * 0.07f, 0.08f, 0.92f);
            constellationY[i] = Mth.clamp(anchor[i][1] + (phaseRng.nextFloat() - 0.5f) * 0.07f, 0.08f, 0.92f);
        }
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        for (int i = 0; i < constellationX.length; i++) ids.add(i);
        java.util.Collections.shuffle(ids, phaseRng);
        for (int i = 0; i < constellationPath.length; i++) constellationPath[i] = ids.get(i);
        constellationInput = 0;
        constellationMistFlash = -1;
        constellationMistFlashTimer = 0f;
        constellationPreview = true;
        constellationPreviewTimer = Math.max(1.35f, 2.15f - masteryTier * 0.08f - (isWeakPassage() ? 0.22f : 0f));
    }

    private void setupMirrorEcho() {
        mirrorRound = 0;
        mirrorRoundsNeeded = 3 + (isWeakPassage() ? 1 : 0);
        setupMirrorRound();
    }

    private void setupMirrorRound() {
        int guard = 0;
        do {
            mirrorTargetGlyph = phaseRng.nextInt(Sigils.TILE.length);
        } while (!Sigils.fullyAsymmetric(Sigils.TILE[mirrorTargetGlyph]) && ++guard < 60);
        if (!Sigils.fullyAsymmetric(Sigils.TILE[mirrorTargetGlyph])) {
            for (int i = 0; i < Sigils.TILE.length; i++) {
                if (Sigils.fullyAsymmetric(Sigils.TILE[i])) { mirrorTargetGlyph = i; break; }
            }
        }

        int targetOrientation = phaseRng.nextInt(4);
        mirrorTargetFlipX = (targetOrientation & 1) != 0;
        mirrorTargetFlipY = (targetOrientation & 2) != 0;

        // Four cards now contain the same asymmetric witness in all four possible
        // reflections. The old mix of random glyphs produced obvious throwaway
        // distractors and sometimes duplicated a wrong orientation. This is a
        // reflection exam, not a "find the odd symbol" round.
        int[] order = {0, 1, 2, 3};
        for (int i = order.length - 1; i > 0; i--) {
            int j = phaseRng.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
        for (int i = 0; i < 4; i++) {
            mirrorGlyph[i] = mirrorTargetGlyph;
            mirrorFlipX[i] = (order[i] & 1) != 0;
            mirrorFlipY[i] = (order[i] & 2) != 0;
            if (order[i] == targetOrientation) mirrorAnswer = i;
        }
    }

    private void setupKeywayEcho() {
        keywayPin = 0;
        keywayMarker = phaseRng.nextBoolean() ? 0.08f : 0.92f;
        keywayDir = keywayMarker < 0.5f ? 1f : -1f;
        for (int i = 0; i < 4; i++) {
            keywayCenter[i] = 0.20f + phaseRng.nextFloat() * 0.60f;
            keywayHalf[i] = Math.max(0.040f, 0.084f - i * 0.007f - (isWeakPassage() ? 0.010f : 0f));
            float falseCenter;
            int guard = 0;
            do {
                falseCenter = 0.14f + phaseRng.nextFloat() * 0.72f;
            } while (Math.abs(falseCenter - keywayCenter[i]) < 0.22f && ++guard < 16);
            if (Math.abs(falseCenter - keywayCenter[i]) < 0.22f) {
                falseCenter = keywayCenter[i] < 0.5f
                        ? Math.min(0.90f, keywayCenter[i] + 0.30f)
                        : Math.max(0.10f, keywayCenter[i] - 0.30f);
            }
            keywayFalseCenter[i] = falseCenter;
        }
    }

    private void setupAuguryEcho() {
        auguryAttempts = 0;
        auguryExact = 0;
        auguryMisplaced = 0;
        int minNonZero = isWeakPassage() ? 3 : 2;
        int nonZero;
        do {
            nonZero = 0;
            for (int i = 0; i < 3; i++) {
                augurySecret[i] = phaseRng.nextInt(4);
                if (augurySecret[i] != 0) nonZero++;
                auguryGuess[i] = 0;
            }
        } while (nonZero < minNonZero);
    }

    private static int[] auguryJudge(int[] secret, int[] guess) {
        int exact = 0;
        int[] sc = new int[4], gc = new int[4];
        for (int i = 0; i < 3; i++) {
            if (secret[i] == guess[i]) exact++;
            else { sc[secret[i]]++; gc[guess[i]]++; }
        }
        int misplaced = 0;
        for (int i = 0; i < 4; i++) misplaced += Math.min(sc[i], gc[i]);
        return new int[]{exact, misplaced};
    }

    private void setupVesselEcho() {
        for (int i = 0; i < vesselShell.length; i++) vesselShell[i] = i;
        vesselTargetToken = phaseRng.nextInt(4);
        vesselReveal = true;
        vesselRevealTimer = Math.max(0.85f, 1.30f - masteryTier * 0.04f - (isWeakPassage() ? 0.15f : 0f));
        vesselShuffleTimer = 0.22f;
        vesselShufflesLeft = 7 + (isWeakPassage() ? 2 : 0);
        vesselSwapA = -1;
        vesselSwapB = -1;
        vesselSwapAnim = 0f;
    }

    private void setupYokeEcho() {
        yokeRoundsNeeded = isWeakPassage() ? 4 : 3;
        yokeRound = 0;
        yokeMoves = 0;
        setupYokeRound();
    }

    private void setupYokeRound() {
        yokeA = 0.5f;
        yokeB = 0.5f;
        yokeTargetA = yokeA;
        yokeTargetB = yokeB;
        float d = 0.070f + yokeRound * 0.004f;
        int shapingMoves = 6 + yokeRound * 2 + phaseRng.nextInt(3) + (isWeakPassage() ? 2 : 0);
        for (int i = 0; i < shapingMoves; i++) {
            switch (phaseRng.nextInt(4)) {
                case 0 -> { yokeTargetA = Mth.clamp(yokeTargetA - d, 0.08f, 0.92f); yokeTargetB = Mth.clamp(yokeTargetB + d * 0.52f, 0.08f, 0.92f); }
                case 1 -> { yokeTargetA = Mth.clamp(yokeTargetA + d, 0.08f, 0.92f); yokeTargetB = Mth.clamp(yokeTargetB - d * 0.52f, 0.08f, 0.92f); }
                case 2 -> { yokeTargetB = Mth.clamp(yokeTargetB - d, 0.08f, 0.92f); yokeTargetA = Mth.clamp(yokeTargetA + d * 0.52f, 0.08f, 0.92f); }
                default -> { yokeTargetB = Mth.clamp(yokeTargetB + d, 0.08f, 0.92f); yokeTargetA = Mth.clamp(yokeTargetA - d * 0.52f, 0.08f, 0.92f); }
            }
        }
        // Never generate a nearly-neutral answer; the final exam should require
        // reading the coupled motion rather than clicking one obvious nudge.
        if (Math.abs(yokeTargetA - 0.5f) + Math.abs(yokeTargetB - 0.5f) < 0.24f) {
            yokeTargetA = Mth.clamp(yokeTargetA + (phaseRng.nextBoolean() ? 0.18f : -0.18f), 0.10f, 0.90f);
            yokeTargetB = Mth.clamp(yokeTargetB + (phaseRng.nextBoolean() ? 0.16f : -0.16f), 0.10f, 0.90f);
        }
        yokeHalf = Math.max(0.042f, 0.075f - yokeRound * 0.006f - (isWeakPassage() ? 0.010f : 0f));
    }

    private static int rootDir(int a, int b) {
        int dx = b % 4 - a % 4, dy = b / 4 - a / 4;
        if (dx == 1 && dy == 0) return 1;
        if (dx == -1 && dy == 0) return 3;
        if (dx == 0 && dy == 1) return 2;
        return 0;
    }

    private static int rootOpposite(int dir) { return (dir + 2) & 3; }

    private void openRootEdge(int a, int b) {
        int dir = rootDir(a, b);
        rootOpen[a * 4 + dir] = true;
        rootOpen[b * 4 + rootOpposite(dir)] = true;
    }

    private void setupRootwayEcho() {
        java.util.Arrays.fill(rootOpen, false);
        java.util.HashSet<Integer> used = new java.util.HashSet<>();
        int cur = phaseRng.nextInt(16);
        rootRoute[0] = cur;
        used.add(cur);
        for (int i = 1; i < rootRoute.length; i++) {
            java.util.ArrayList<Integer> n = new java.util.ArrayList<>();
            int r = cur / 4, c = cur % 4;
            if (r > 0 && !used.contains(cur - 4)) n.add(cur - 4);
            if (r < 3 && !used.contains(cur + 4)) n.add(cur + 4);
            if (c > 0 && !used.contains(cur - 1)) n.add(cur - 1);
            if (c < 3 && !used.contains(cur + 1)) n.add(cur + 1);
            if (n.isEmpty()) { setupRootwayEcho(); return; }
            int next = n.get(phaseRng.nextInt(n.size()));
            openRootEdge(cur, next);
            rootRoute[i] = next;
            used.add(next);
            cur = next;
        }
        // Add only true one-edge dead ends. A branch may leave the witnessed
        // route, but it can never reconnect later and masquerade as a second
        // valid solution that the scoring code would unfairly reject.
        boolean[] onRoute = new boolean[16];
        for (int cell : rootRoute) onRoute[cell] = true;
        boolean[] usedDeadEnd = new boolean[16];
        int branches = isWeakPassage() ? 4 : 3;
        for (int tries = 0, added = 0; tries < 48 && added < branches; tries++) {
            int a = rootRoute[phaseRng.nextInt(rootRoute.length - 1)];
            int dir = phaseRng.nextInt(4);
            int r = a / 4, c = a % 4;
            int b = switch (dir) { case 0 -> r > 0 ? a - 4 : -1; case 1 -> c < 3 ? a + 1 : -1; case 2 -> r < 3 ? a + 4 : -1; default -> c > 0 ? a - 1 : -1; };
            if (b < 0 || onRoute[b] || usedDeadEnd[b] || rootOpen[a * 4 + dir]) continue;
            openRootEdge(a, b);
            usedDeadEnd[b] = true;
            added++;
        }
        rootIndex = 0;
        rootGoal = rootRoute[rootRoute.length - 1];
        rootDecisionLimit = isWeakPassage() ? 1.90f : 2.35f;
        rootDecisionTimer = rootDecisionLimit;
    }

    private void setupShardsongEcho() {
        // Build a solved 2x2 tablet: exterior edges are 0, shared edges use 1..3.
        for (int t = 0; t < 4; t++) java.util.Arrays.fill(shardEdge[t], 0);
        int top = 1 + phaseRng.nextInt(3), bottom = 1 + phaseRng.nextInt(3);
        int left = 1 + phaseRng.nextInt(3), right = 1 + phaseRng.nextInt(3);
        shardEdge[0][1] = shardEdge[1][3] = top;
        shardEdge[2][1] = shardEdge[3][3] = bottom;
        shardEdge[0][2] = shardEdge[2][0] = left;
        shardEdge[1][2] = shardEdge[3][0] = right;
        int minDepth = isWeakPassage() ? 7 : 5;
        shardIdeal = 0;
        for (int attempt = 0; attempt < 32 && shardIdeal < minDepth; attempt++) {
            shardIdeal = 0;
            for (int t = 0; t < 4; t++) {
                shardRot[t] = phaseRng.nextInt(4);
                shardIdeal += Math.floorMod(4 - shardRot[t], 4);
            }
        }
        if (shardIdeal < minDepth) {
            int[] fallback = isWeakPassage() ? new int[]{2,2,2,3} : new int[]{2,2,3,0};
            System.arraycopy(fallback, 0, shardRot, 0, 4);
            shardIdeal = 0;
            for (int r : shardRot) shardIdeal += Math.floorMod(4 - r, 4);
        }
        shardMoves = 0;
    }

    private static int orreryCode(int a, int b, int c) { return a * 36 + b * 6 + c; }

    private int orreryDistance() {
        int start = orreryCode(orreryEcho[0], orreryEcho[1], orreryEcho[2]);
        if (start == 0) return 0;
        int[] dist = new int[216];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[216];
        int head = 0, tail = 0;
        dist[start] = 0; queue[tail++] = start;
        while (head < tail) {
            int state = queue[head++];
            int[] v = {state / 36, (state / 6) % 6, state % 6};
            for (int ring = 0; ring < 3; ring++) for (int dir : new int[]{-1, 1}) {
                int[] n = v.clone();
                int next = (ring + 1) % 3;
                n[ring] = Math.floorMod(n[ring] + dir, 6);
                n[next] = Math.floorMod(n[next] + dir, 6);
                int code = orreryCode(n[0], n[1], n[2]);
                if (dist[code] >= 0) continue;
                dist[code] = dist[state] + 1;
                if (code == 0) return dist[code];
                queue[tail++] = code;
            }
        }
        return 12;
    }

    private void setupOrreryEcho() {
        int minDepth = isWeakPassage() ? 4 : 3;
        orreryEchoIdeal = 0;
        for (int attempt = 0; attempt < 40 && orreryEchoIdeal < minDepth; attempt++) {
            java.util.Arrays.fill(orreryEcho, 0);
            int scramble = 5 + phaseRng.nextInt(5) + (isWeakPassage() ? 2 : 0);
            for (int i = 0; i < scramble; i++) {
                int ring = phaseRng.nextInt(3), dir = phaseRng.nextBoolean() ? 1 : -1;
                orreryEcho[ring] = Math.floorMod(orreryEcho[ring] + dir, 6);
                int next = (ring + 1) % 3;
                orreryEcho[next] = Math.floorMod(orreryEcho[next] + dir, 6);
            }
            orreryEchoIdeal = orreryDistance();
        }
        if (orreryEchoIdeal < minDepth) {
            // A known reachable state at the maximum four-move depth.
            orreryEcho[0] = 0; orreryEcho[1] = 1; orreryEcho[2] = 3;
            orreryEchoIdeal = orreryDistance();
        }
        orreryEchoMoves = 0;
        orreryEchoIdeal = Math.max(1, orreryEchoIdeal);
    }

    private static int processionSwapDistance(int[] current, int[] target) {
        int[] work = current.clone();
        int moves = 0;
        for (int i = 0; i < work.length; i++) {
            if (work[i] == target[i]) continue;
            int j = i + 1;
            while (j < work.length && work[j] != target[i]) j++;
            if (j >= work.length) return 8;
            int t = work[i]; work[i] = work[j]; work[j] = t;
            moves++;
        }
        return moves;
    }

    private void setupProcessionEcho() {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>(java.util.List.of(0,1,2,3));
        java.util.Collections.shuffle(ids, phaseRng);
        for (int i = 0; i < 4; i++) processionEchoTarget[i] = ids.get(i);
        int minDepth = isWeakPassage() ? 3 : 2;
        processionEchoIdeal = 0;
        for (int attempt = 0; attempt < 32 && processionEchoIdeal < minDepth; attempt++) {
            System.arraycopy(processionEchoTarget, 0, processionEchoCurrent, 0, 4);
            int swaps = 3 + phaseRng.nextInt(3) + (isWeakPassage() ? 1 : 0);
            for (int i = 0; i < swaps; i++) {
                int a = phaseRng.nextInt(4), b; do b = phaseRng.nextInt(4); while (b == a);
                int x = processionEchoCurrent[a]; processionEchoCurrent[a] = processionEchoCurrent[b]; processionEchoCurrent[b] = x;
            }
            processionEchoIdeal = processionSwapDistance(processionEchoCurrent, processionEchoTarget);
        }
        if (processionEchoIdeal < minDepth) {
            // Rotate the target by one: for four distinct witnesses this is always
            // exactly three arbitrary swaps away from the verdict order.
            for (int i = 0; i < 4; i++) processionEchoCurrent[i] = processionEchoTarget[(i + 1) % 4];
            processionEchoIdeal = processionSwapDistance(processionEchoCurrent, processionEchoTarget);
        }
        processionEchoSelected = -1;
        processionEchoMoves = 0;
    }

    private int shardEdgeAt(int tile, int worldSide) {
        return shardEdge[tile][Math.floorMod(worldSide - shardRot[tile], 4)];
    }

    private boolean shardsSolved() {
        // Exterior must be blank.
        if (shardEdgeAt(0,0)!=0 || shardEdgeAt(0,3)!=0 || shardEdgeAt(1,0)!=0 || shardEdgeAt(1,1)!=0
                || shardEdgeAt(2,2)!=0 || shardEdgeAt(2,3)!=0 || shardEdgeAt(3,1)!=0 || shardEdgeAt(3,2)!=0) return false;
        return shardEdgeAt(0,1)==shardEdgeAt(1,3) && shardEdgeAt(2,1)==shardEdgeAt(3,3)
                && shardEdgeAt(0,2)==shardEdgeAt(2,0) && shardEdgeAt(1,2)==shardEdgeAt(3,0);
    }

    @Override
    protected boolean presentationPauseActive() {
        return !resolved && System.nanoTime() < phaseEntryGraceUntilNanos;
    }

    @Override
    protected void step(float dt) {
        globalTime += dt;
        if (presentationPauseActive()) return;
        orbitPulse += dt;
        mawPulse += dt;

        glitchTimer -= dt;
        if (glitchTimer <= 0f) {
            glitchTimer = WardConfig.accessibilityReduceMotion ? 0.55f : 0.18f + rng.nextFloat() * 0.22f;
            jitterX = (rng.nextFloat() - 0.5f) * (WardConfig.accessibilityDisableShake ? 0f : 2.4f);
            jitterY = (rng.nextFloat() - 0.5f) * (WardConfig.accessibilityDisableShake ? 0f : 1.6f);
        }

        switch (mechanic()) {
            case RITUAL_ORBIT -> orbitSpin += dt * (0.60f + orbitHits * 0.15f + difficulty * 0.20f);
            case RITUAL_PULSE -> {
                float identitySpeed = ritual() == RITUAL_PULSE_DISCIPLINE ? 1.16f : ritual() == RITUAL_PRESSURE ? 0.86f : 1f;
                float speed = (0.65f + timingHits * 0.09f + difficulty * 0.24f) * 0.95f * identitySpeed;
                barMarker += barDir * speed * dt;
                if (barMarker > 1f) {
                    barMarker = 1f;
                    barDir = -1f;
                } else if (barMarker < 0f) {
                    barMarker = 0f;
                    barDir = 1f;
                }
            }
            case RITUAL_MEMORY -> stepMemory(dt);
            case RITUAL_MAW -> mawAngle = (mawAngle + dt * mawSpeed) % Mth.TWO_PI;
            case RITUAL_FALSE_CHOIR -> {
                if (choirPreview) {
                    choirPreviewTimer -= dt;
                    if (choirPreviewTimer <= 0f) choirPreview = false;
                }
            }
            case RITUAL_VEIL -> {
                veilAngleA = (veilAngleA + dt * veilSpeedA) % Mth.TWO_PI;
                veilAngleB = (veilAngleB - dt * veilSpeedB) % Mth.TWO_PI;
                if (veilAngleB < 0f) veilAngleB += Mth.TWO_PI;
            }
            case RITUAL_PRESSURE -> stepPressureEcho(dt);
            case RITUAL_PULSE_DISCIPLINE -> stepPulseEcho(dt);
            case RITUAL_BALANCE -> stepBalanceEcho(dt);
            case RITUAL_CONSTELLATION -> {
                if (constellationPreview) {
                    constellationPreviewTimer -= dt;
                    if (constellationPreviewTimer <= 0f) constellationPreview = false;
                }
                if (constellationMistFlashTimer > 0f) constellationMistFlashTimer = Math.max(0f, constellationMistFlashTimer - dt);
            }
            case RITUAL_KEYWAY -> {
                float speed = 0.72f + keywayPin * 0.08f + difficulty * 0.16f;
                keywayMarker += keywayDir * speed * dt;
                if (keywayMarker >= 1f) { keywayMarker = 1f; keywayDir = -1f; }
                else if (keywayMarker <= 0f) { keywayMarker = 0f; keywayDir = 1f; }
            }
            case RITUAL_VESSEL -> stepVesselEcho(dt);
            case RITUAL_ROOTWAY -> stepRootwayEcho(dt);
            default -> { }
        }
    }

    private void stepPulseEcho(float dt) {
        pulseSongTime += dt;
        while (pulseBeatIndex < pulseBeats.length && pulseSongTime > pulseBeats[pulseBeatIndex] + pulseWindow) {
            record(0f);
            Sfx.markBad();
            boolean alive = loseLife("A pulse passed unanswered");
            pulseBeatIndex++;
            if (!alive) return;
            if (pulseBeatIndex >= pulseBeats.length) { advancePhase(); return; }
        }
    }

    private void stepBalanceEcho(float dt) {
        balanceVelocity += balanceDrift * dt;
        balanceVelocity += balanceTilt * 0.34f * dt;
        balanceVelocity *= Math.max(0f, 1f - dt * 1.8f);
        balanceTilt += balanceVelocity * dt;
        if (Math.abs(balanceTilt) <= 0.085f) balanceStable += dt;
        else balanceStable = Math.max(0f, balanceStable - dt * 0.65f);
        if (balanceStable >= (isWeakPassage() ? 1.35f : 1.10f)) {
            record(Mth.clamp(1f - Math.abs(balanceTilt) * 4f, 0.65f, 1f));
            advancePhase();
            return;
        }
        if (Math.abs(balanceTilt) > 1.05f) {
            record(0f);
            Sfx.markBad();
            loseLife("The balance fell off the witness line");
            balanceTilt = Mth.clamp(balanceTilt, -0.55f, 0.55f);
            balanceVelocity *= -0.18f;
            balanceStable = 0f;
        }
    }

    private void stepRootwayEcho(float dt) {
        rootDecisionTimer -= dt;
        if (rootDecisionTimer > 0f) return;
        record(0f);
        Sfx.markBad();
        if (loseLife("The root withered while you hesitated")) {
            rootIndex = 0;
            rootDecisionTimer = rootDecisionLimit;
            showBanner("THE ROOT STARTS AGAIN", theme().accent, 620);
        }
    }

    private void stepVesselEcho(float dt) {
        if (vesselReveal) {
            vesselRevealTimer -= dt;
            if (vesselRevealTimer <= 0f) vesselReveal = false;
            return;
        }
        if (vesselSwapA >= 0) {
            vesselSwapAnim += dt / Math.max(0.16f, 0.28f - masteryTier * 0.008f - (isWeakPassage() ? 0.035f : 0f));
            if (vesselSwapAnim >= 1f) {
                int t = vesselShell[vesselSwapA]; vesselShell[vesselSwapA] = vesselShell[vesselSwapB]; vesselShell[vesselSwapB] = t;
                vesselShufflesLeft--;
                Sfx.mark(vesselShufflesLeft);
                vesselSwapA = vesselSwapB = -1;
                vesselSwapAnim = 0f;
                vesselShuffleTimer = 0.12f + phaseRng.nextFloat() * 0.10f;
            }
            return;
        }
        if (vesselShufflesLeft <= 0) return;
        vesselShuffleTimer -= dt;
        if (vesselShuffleTimer > 0f) return;
        vesselSwapA = phaseRng.nextInt(3);
        vesselSwapB = vesselSwapA + 1;
        vesselSwapAnim = 0f;
    }

    private void stepMemory(float dt) {
        if (!memoryPlayback) {
            if (memoryFlashAlpha > 0f) memoryFlashAlpha = Math.max(0f, memoryFlashAlpha - dt * 2.5f);
            return;
        }
        memoryTimer -= dt;
        if (memoryTimer > 0f) return;
        if (memoryFlash >= 0) {
            memoryFlash = -1;
            memoryFlashAlpha = 0f;
            memoryShowIndex++;
            memoryTimer = ritual() == RITUAL_VESSEL ? 0.13f : 0.18f;
            return;
        }
        if (memoryShowIndex >= memorySequence.size()) {
            memoryPlayback = false;
            memoryInputIndex = 0;
            showBanner("Repeat the witness grid", theme().accent, 900);
            return;
        }
        memoryFlash = memorySequence.get(memoryShowIndex);
        memoryFlashAlpha = 1f;
        memoryTimer = ritual() == RITUAL_VESSEL ? 0.27f : 0.34f;
        Sfx.mark(memoryShowIndex);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (presentationPauseActive()) return true;
        return switch (mechanic()) {
            case RITUAL_ORBIT -> clickOrbit(mx, my);
            case RITUAL_PULSE -> { commitTiming(); yield true; }
            case RITUAL_MEMORY -> clickMemory(mx, my);
            case RITUAL_MAW -> { commitMaw(); yield true; }
            case RITUAL_FALSE_CHOIR -> clickChoir(mx, my);
            case RITUAL_VEIL -> { commitVeil(); yield true; }
            case RITUAL_LATTICE -> clickLattice(mx, my);
            case RITUAL_EPITAPH -> clickEpitaph(mx, my);
            case RITUAL_MEASURE -> clickMeasure(mx, my);
            case RITUAL_VEILSTEP -> clickVeilstep(mx, my);
            case RITUAL_RUNEBRAID -> clickRunebraid(mx, my);
            case RITUAL_RESONANCE -> clickResonance(mx, my);
            case RITUAL_CIPHER -> clickCipher(mx, my);
            case RITUAL_PRESSURE -> clickPressureEcho(mx, my);
            case RITUAL_PULSE_DISCIPLINE -> { commitPulseEcho(); yield true; }
            case RITUAL_BALANCE -> clickBalanceEcho(mx, my);
            case RITUAL_CONSTELLATION -> clickConstellationEcho(mx, my);
            case RITUAL_MIRROR_DISCIPLINE -> clickMirrorEcho(mx, my);
            case RITUAL_KEYWAY -> { commitKeywayEcho(); yield true; }
            case RITUAL_AUGURY -> clickAuguryEcho(mx, my);
            case RITUAL_VESSEL -> clickVesselEcho(mx, my);
            case RITUAL_YOKE -> clickYokeEcho(mx, my);
            case RITUAL_ROOTWAY -> clickRootwayEcho(mx, my);
            case RITUAL_SHARDSONG -> clickShardsongEcho(mx, my);
            case RITUAL_ORRERY -> clickOrreryEcho(mx, my);
            case RITUAL_PROCESSION -> clickProcessionEcho(mx, my);
            default -> false;
        };
    }

    @Override
    protected boolean onKey(int key) {
        if (!isActionKey(key)) return false;
        if (presentationPauseActive()) return true;
        if (mechanic() == RITUAL_PULSE) { commitTiming(); return true; }
        if (mechanic() == RITUAL_MAW) { commitMaw(); return true; }
        if (mechanic() == RITUAL_VEIL) { commitVeil(); return true; }
        if (mechanic() == RITUAL_PULSE_DISCIPLINE) { commitPulseEcho(); return true; }
        if (mechanic() == RITUAL_KEYWAY) { commitKeywayEcho(); return true; }
        return false;
    }

    private boolean clickOrbit(double mx, double my) {
        int clicked = orbitNodeAt(mx, my);
        if (clicked < 0) return false;
        if (clicked == orbitTarget) {
            orbitHits++;
            record(1f);
            Sfx.play(WardSounds.EYE_CHOOSE, 0.48f, 1.0f + orbitHits * 0.03f);
            showBanner("The eye accepts the mark", COL_GOOD, 520);
            if (orbitHits >= orbitHitsNeeded) {
                advancePhase();
            } else {
                int next;
                do next = phaseRng.nextInt(7); while (next == orbitTarget);
                orbitTarget = next;
            }
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("You fed the wrong signal")) {
                showBanner("Wrong rune", COL_BAD, 650);
            }
        }
        return true;
    }

    private void commitTiming() {
        float dTarget = Math.abs(barMarker - barTargetCenter);
        float dHazard = Math.abs(barMarker - barHazardCenter);
        if (dHazard <= barHazardHalf) {
            record(0f);
            Sfx.overset();
            if (loseLife("You struck the false pulse")) {
                resetTimingTarget();
                showBanner("False pulse", COL_BAD, 680);
            }
            return;
        }
        if (dTarget <= barTargetHalf) {
            float acc = 1f - dTarget / Math.max(0.0001f, barTargetHalf);
            record(acc);
            timingHits++;
            Sfx.mark(timingHits);
            if (timingHits >= timingHitsNeeded) {
                advancePhase();
            } else {
                resetTimingTarget();
                showBanner("Pulse split", COL_GOOD, 420);
            }
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("You missed the split pulse")) {
                showBanner("Too early, or too late", COL_BAD, 620);
            }
        }
    }

    private boolean clickMemory(double mx, double my) {
        int cell = memoryCellAt(mx, my);
        if (cell < 0) return false;
        if (memoryPlayback) {
            showBanner("Watch first", theme().accent, 500);
            return true;
        }
        memoryFlash = cell;
        memoryFlashAlpha = 0.9f;
        if (cell == memorySequence.get(memoryInputIndex)) {
            record(1f);
            Sfx.mark(memoryInputIndex);
            memoryInputIndex++;
            if (memoryInputIndex >= memorySequence.size()) {
                advancePhase();
            }
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("The witness pattern slipped")) {
                setupMemory();
                showBanner("The grid rearranges itself", COL_BAD, 760);
            }
        }
        return true;
    }

    private void commitMaw() {
        float targetAngle = mawAngleForNode(mawSequence[mawIndex]);
        float diff = angularDifference(mawAngle, targetAngle);
        if (diff <= mawWindow) {
            float acc = 1f - diff / Math.max(0.0001f, mawWindow);
            record(acc);
            mawIndex++;
            Sfx.play(WardSounds.HEARTBEAT, 0.42f, 0.72f + mawIndex * 0.05f);
            if (mawIndex >= mawHitsNeeded) {
                advancePhase();
            } else {
                showBanner("The mouth opens further", COL_GOOD, 520);
            }
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("The mouth bit down")) {
                showBanner("The sweep was wrong", COL_BAD, 620);
            }
        }
    }

    private boolean clickChoir(double mx, double my) {
        int cell = choirCellAt(mx, my);
        if (cell < 0) return false;
        if (choirPreview) {
            showBanner("Listen before you answer", theme().accent, 520);
            return true;
        }
        if (cell == choirCorrectCell) {
            record(1f);
            choirRound++;
            Sfx.play(WardSounds.EYE_CHOOSE, 0.42f, 0.92f + choirRound * 0.035f);
            if (choirRound >= choirRoundsNeeded) advancePhase();
            else {
                showBanner("One voice remains true", COL_GOOD, 480);
                nextChoirRound();
            }
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("You answered a borrowed voice")) {
                showBanner("The choir changes key", COL_BAD, 680);
                nextChoirRound();
            }
        }
        return true;
    }

    private void commitVeil() {
        float diff = angularDifference(veilAngleA, veilAngleB);
        if (diff <= veilWindow) {
            float acc = 1f - diff / Math.max(0.0001f, veilWindow);
            record(acc);
            veilHits++;
            Sfx.play(WardSounds.MARK, 0.42f, 0.84f + veilHits * 0.045f);
            if (veilHits >= veilHitsNeeded) {
                advancePhase();
                return;
            }
            // Kick the two veils apart after every convergence so a double-click
            // cannot buy two successes from the same alignment.
            veilAngleB = (veilAngleB + 1.25f + phaseRng.nextFloat() * 1.35f) % Mth.TWO_PI;
            veilSpeedA *= 1.035f;
            veilSpeedB *= 1.045f;
            showBanner("The veils separate again", COL_GOOD, 460);
        } else {
            record(0f);
            Sfx.markBad();
            if (loseLife("The two veils did not agree"))
                showBanner("No convergence", COL_BAD, 620);
        }
    }

    private boolean clickLattice(double mx, double my) {
        int size=34,gap=7,board=size*3+gap*2;
        int sx=centerX()-board/2, sy=contentCenterY()-board/2+5;
        for(int r=0;r<3;r++) for(int c=0;c<3;c++) {
            int x=sx+c*(size+gap), y=sy+r*(size+gap);
            if(mx>=x&&mx<=x+size&&my>=y&&my<=y+size) {
                int cell=r*3+c;
                latticeMask ^= latticeCrossMask(cell);
                latticeMoves++;
                Sfx.mark(latticeMoves);
                if(latticeMask==0) {
                    float score=Mth.clamp(latticeIdeal/(float)Math.max(latticeIdeal,latticeMoves),0.45f,1f);
                    record(score);
                    advancePhase();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickEpitaph(double mx, double my) {
        int spacing=42, oy=contentCenterY()+52, start=centerX()-spacing*3/2;
        for(int i=0;i<4;i++) {
            int cx=start+i*spacing;
            if(distSq(mx,my,cx,oy)<=15*15) {
                if(i==epitaphAnswer) {
                    record(1f); Sfx.play(WardSounds.EYE_CHOOSE, 0.42f, 1.02f); advancePhase();
                } else {
                    record(0f); Sfx.markBad();
                    if(loseLife("The missing rune rejects the ink")) showBanner("False completion",COL_BAD,650);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickMeasure(double mx, double my) {
        int[] cx={centerX()-78,centerX(),centerX()+78};
        int top=contentCenterY()-36, bottom=contentCenterY()+46;
        for(int i=0;i<3;i++) {
            if(mx>=cx[i]-24&&mx<=cx[i]+24&&my>=top&&my<=bottom) {
                if(measureSelected<0) {
                    if(measureAmt[i]<=0) { showBanner("That vessel is empty",theme().dim,450); return true; }
                    measureSelected=i; Sfx.mark(i); return true;
                }
                int src=measureSelected, dst=i; measureSelected=-1;
                if(src==dst||measureAmt[src]<=0||measureAmt[dst]>=measureCap[dst]) {
                    showBanner("No measure moves",theme().dim,450); return true;
                }
                int moved=Math.min(measureAmt[src],measureCap[dst]-measureAmt[dst]);
                measureAmt[src]-=moved; measureAmt[dst]+=moved; measureMoves++;
                Sfx.play(WardSounds.PLUG_TURN,0.28f,0.92f+measureMoves*0.02f);
                if(measureAmt[0]==measureTarget||measureAmt[1]==measureTarget||measureAmt[2]==measureTarget) {
                    float score=Mth.clamp(measureIdeal/(float)Math.max(measureIdeal,measureMoves),0.45f,1f);
                    record(score); advancePhase();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickVeilstep(double mx, double my) {
        int size=27,gap=6,board=size*4+gap*3;
        int sx=centerX()-board/2, sy=contentCenterY()-board/2+4;
        int current=stepRoute[stepIndex];
        for(int r=0;r<4;r++) for(int c=0;c<4;c++) {
            int x=sx+c*(size+gap), y=sy+r*(size+gap);
            if(mx>=x&&mx<=x+size&&my>=y&&my<=y+size) {
                int cell=r*4+c;
                if(cell==current) return true;
                int required=stepJump[current];
                boolean legal=manhattan(current,cell)==required;
                boolean correct=stepIndex+1<stepRoute.length && cell==stepRoute[stepIndex+1];
                if(legal&&correct) {
                    record(1f); stepIndex++; Sfx.mark(stepIndex);
                    if(stepIndex>=stepRoute.length-1) advancePhase();
                    else showBanner("The next distance is written",COL_GOOD,420);
                } else {
                    record(0f); Sfx.markBad();
                    if(loseLife(legal?"The path was legal, but not the path":"The distance was false"))
                        showBanner(legal?"A plausible road ends here":"Wrong distance",COL_BAD,620);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickRunebraid(double mx, double my) {
        int size=34,gap=7,total=size*5+gap*4,sx=centerX()-total/2, y=contentCenterY()-20;
        for(int i=0;i<5;i++) {
            int x=sx+i*(size+gap);
            if(mx>=x&&mx<=x+size&&my>=y&&my<=y+size) {
                if(braidSelected<0) { braidSelected=i; Sfx.mark(i); return true; }
                int a=braidSelected,b=i; braidSelected=-1;
                if(Math.abs(a-b)!=1) { braidSelected=i; showBanner("Only adjacent strands cross",theme().dim,420); return true; }
                int t=braid[a]; braid[a]=braid[b]; braid[b]=t; braidMoves++; Sfx.play(WardSounds.BRAID_PULL,0.30f,0.95f);
                if(inversionCount(braid)==0) {
                    float score=Mth.clamp(braidIdeal/(float)Math.max(braidIdeal,braidMoves),0.45f,1f);
                    record(score); advancePhase();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickResonance(double mx, double my) {
        int bx=left()+42,bw=panelWidth()-84,by=contentCenterY()+5;
        if(mx<bx||mx>bx+bw||my<by-38||my>by+38) return false;
        float proposed=Mth.clamp((float)((mx-bx)/bw),0f,1f);
        float err=Math.abs(proposed-resonanceTarget);
        if(err<=resonanceHalf) {
            float acc=1f-err/Math.max(0.0001f,resonanceHalf);
            record(acc); resonanceHits++; Sfx.play(WardSounds.MARK,0.36f,0.86f+resonanceHits*0.05f);
            if(resonanceHits>=resonanceHitsNeeded) advancePhase();
            else { resonanceTarget=0.18f+phaseRng.nextFloat()*0.64f; showBanner("The next frequency answers",COL_GOOD,430); }
        } else {
            record(0f); Sfx.markBad();
            if(loseLife("The resonance split")) showBanner("Wrong frequency",COL_BAD,620);
        }
        return true;
    }

    private boolean clickCipher(double mx, double my) {
        int spacing = 62, start = centerX() - spacing;
        int cy = contentCenterY() + 4;
        for (int i = 0; i < 3; i++) {
            int cx = start + i * spacing;
            if (distSq(mx, my, cx, cy) <= 24 * 24) {
                rotateCipherCoupled(i);
                cipherMoves++;
                Sfx.mark(cipherMoves);
                if (cipherSolved()) {
                    record(Mth.clamp(cipherIdeal / (float) Math.max(cipherIdeal, cipherMoves), 0.55f, 1f));
                    advancePhase();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickPressureEcho(double mx, double my) {
        int bx = centerX() - 128, by = contentCenterY() - 14;
        // PUMP adds pressure, VENT bleeds it deliberately, SEAL commits the round.
        // Continuous leakage means all three controls matter under time.
        if (mx >= bx && mx <= bx + 84 && my >= by + 30 && my <= by + 58) {
            float stroke = 0.070f + (pressurePumps % 3) * 0.014f + difficulty * 0.003f;
            pressureValue = Math.min(1f, pressureValue + stroke);
            pressurePumps++;
            Sfx.mark(Math.round(pressureValue * 10f));
            if (pressureValue >= pressureBurst) {
                record(0f);
                Sfx.markBad();
                if (loseLife("The bellows burst past the redline")) nextPressureBand();
            }
            return true;
        }
        if (mx >= bx + 94 && mx <= bx + 178 && my >= by + 30 && my <= by + 58) {
            pressureValue = Math.max(0f, pressureValue - (0.050f + pressureRounds * 0.003f));
            Sfx.play(WardSounds.STONE_TURN, 0.26f, 0.76f);
            return true;
        }
        if (mx >= bx + 188 && mx <= bx + 256 && my >= by + 30 && my <= by + 58) {
            float err = Math.abs(pressureValue - pressureTarget);
            if (err <= pressureHalf) {
                record(1f - err / Math.max(0.0001f, pressureHalf));
                pressureRounds++;
                Sfx.play(WardSounds.MARK, 0.36f, 0.90f + pressureRounds * 0.05f);
                if (pressureRounds >= pressureRoundsNeeded) advancePhase();
                else nextPressureBand();
            } else {
                record(0f);
                Sfx.markBad();
                if (loseLife(pressureValue < pressureTarget ? "The pressure was too thin" : "The seal overpressured")) nextPressureBand();
            }
            return true;
        }
        return false;
    }

    private void commitPulseEcho() {
        if (pulseBeatIndex >= pulseBeats.length) return;
        float err = Math.abs(pulseSongTime - pulseBeats[pulseBeatIndex]);
        if (err <= pulseWindow) {
            record(1f - err / Math.max(0.0001f, pulseWindow));
            pulseBeatIndex++;
            Sfx.play(WardSounds.MARK, 0.36f, 0.84f + pulseBeatIndex * 0.045f);
            if (pulseBeatIndex >= pulseBeats.length) advancePhase();
        } else {
            record(0f);
            Sfx.markBad();
            loseLife("You struck between the choir beats");
        }
    }

    private boolean clickBalanceEcho(double mx, double my) {
        int cx = centerX(), cy = contentCenterY() + 10;
        if (my < cy + 44 || my > cy + 78) return false;
        if (mx >= cx - 122 && mx <= cx - 24) {
            balanceVelocity -= 0.30f;
            balanceStable = 0f;
            Sfx.mark(0);
            return true;
        }
        if (mx >= cx + 24 && mx <= cx + 122) {
            balanceVelocity += 0.30f;
            balanceStable = 0f;
            Sfx.mark(1);
            return true;
        }
        return false;
    }

    private boolean clickConstellationEcho(double mx, double my) {
        if (constellationPreview) { showBanner("The stars are still testifying", theme().accent, 420); return true; }
        int fx = left() + 34, fy = contentTop() + 42, fw = panelWidth() - 68, fh = 148;
        int clicked = -1;
        for (int i = 0; i < constellationX.length; i++) {
            int sx = fx + Math.round(constellationX[i] * fw), sy = fy + Math.round(constellationY[i] * fh);
            if (distSq(mx, my, sx, sy) <= 12 * 12) { clicked = i; break; }
        }
        if (clicked < 0) return false;
        int expected = constellationPath[constellationInput];
        if (clicked == expected) {
            record(1f);
            constellationInput++;
            constellationMistFlash = -1;
            Sfx.mark(constellationInput);
            if (constellationInput >= constellationPath.length) advancePhase();
        } else {
            record(0f); Sfx.markBad();
            boolean alive = loseLife("The constellation rejected the false star");
            constellationMistFlash = clicked;
            constellationMistFlashTimer = 0.55f;
            if (alive) {
                // A miss used to replay the entire numbered route for free. That
                // turned one life into an answer reveal. Recoil two witnessed
                // stars instead: progress is punished, but memory remains the skill.
                constellationInput = Math.max(0, constellationInput - 2);
                constellationPreview = false;
                showBanner("THE ROUTE RECOILS", theme().accent, 520);
            }
        }
        return true;
    }

    private boolean clickMirrorEcho(double mx, double my) {
        int size = 42, gap = 14, total = size * 4 + gap * 3, sx = centerX() - total / 2, cy = contentCenterY() + 24;
        for (int i = 0; i < 4; i++) {
            int x = sx + i * (size + gap);
            if (mx >= x && mx <= x + size && my >= cy - size / 2 && my <= cy + size / 2) {
                if (i == mirrorAnswer) {
                    record(1f);
                    mirrorRound++;
                    Sfx.play(WardSounds.EYE_CHOOSE, 0.40f, 1.02f + mirrorRound * 0.03f);
                    if (mirrorRound >= mirrorRoundsNeeded) advancePhase();
                    else {
                        showBanner("The mirror changes its lie", theme().accentLite, 520);
                        setupMirrorRound();
                    }
                } else {
                    record(0f);
                    Sfx.markBad();
                    if (loseLife("The reflection was almost, but not exactly, the witness")) {
                        showBanner("The witness turns away", COL_BAD, 480);
                        setupMirrorRound();
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void commitKeywayEcho() {
        if (keywayPin >= keywayCenter.length) return;
        float err = Math.abs(keywayMarker - keywayCenter[keywayPin]);
        if (err <= keywayHalf[keywayPin]) {
            record(1f - err / Math.max(0.0001f, keywayHalf[keywayPin]));
            keywayPin++;
            Sfx.play(WardSounds.PIN_SET, 0.38f, 0.90f + keywayPin * 0.045f);
            if (keywayPin >= keywayCenter.length) advancePhase();
            else {
                keywayMarker = keywayDir > 0 ? 0.05f : 0.95f;
                keywayDir = -keywayDir;
            }
        } else {
            record(0f); Sfx.markBad(); loseLife("The tumbler overset");
        }
    }

    private boolean clickAuguryEcho(double mx, double my) {
        int size = 36, gap = 16, total = size * 3 + gap * 2, sx = centerX() - total / 2, cy = contentCenterY() - 16;
        for (int i = 0; i < 3; i++) {
            int x = sx + i * (size + gap);
            if (mx >= x && mx <= x + size && my >= cy - size / 2 && my <= cy + size / 2) {
                auguryGuess[i] = (auguryGuess[i] + 1) % 4;
                Sfx.mark(i);
                return true;
            }
        }
        if (mx >= centerX() - 46 && mx <= centerX() + 46 && my >= cy + 42 && my <= cy + 68) {
            int[] judged = auguryJudge(augurySecret, auguryGuess);
            auguryExact = judged[0]; auguryMisplaced = judged[1]; auguryAttempts++;
            if (auguryExact == 3) {
                record(Mth.clamp(1f - Math.max(0, auguryAttempts - 1) * 0.12f, 0.55f, 1f));
                advancePhase();
            } else {
                record(0.25f + auguryExact * 0.20f + auguryMisplaced * 0.08f);
                Sfx.markBad();
                if (auguryAttempts >= (isWeakPassage() ? 4 : 5)) {
                    if (loseLife("The omen exhausted its patience")) {
                        setupAuguryEcho();
                        showBanner("THE OMEN REWRITES ITSELF", theme().accent, 720);
                    }
                } else {
                    showBanner(auguryExact + " exact · " + auguryMisplaced + " misplaced", theme().accent, 720);
                }
            }
            return true;
        }
        return false;
    }

    private boolean clickVesselEcho(double mx, double my) {
        if (vesselReveal || vesselShufflesLeft > 0) { showBanner("Keep the marked thing in sight", theme().accent, 420); return true; }
        int size = 38, gap = 18, total = size * 4 + gap * 3, sx = centerX() - total / 2, cy = contentCenterY() + 12;
        for (int i = 0; i < 4; i++) {
            int x = sx + i * (size + gap);
            if (mx >= x && mx <= x + size && my >= cy - size / 2 && my <= cy + size / 2) {
                if (vesselShell[i] == vesselTargetToken) { record(1f); advancePhase(); }
                else { record(0f); Sfx.markBad(); loseLife("The wrong vessel answered"); }
                return true;
            }
        }
        return false;
    }

    private boolean clickYokeEcho(double mx, double my) {
        int cx = centerX(), cy = contentCenterY() + 4;
        int buttonY = cy + 56;
        int sealY = buttonY + 32;
        boolean onValves = my >= buttonY && my <= buttonY + 26;
        boolean onSeal = my >= sealY && my <= sealY + 24 && mx >= cx - 42 && mx <= cx + 42;
        if (!onValves && !onSeal) return false;

        if (onSeal) {
            float ea = Math.abs(yokeA - yokeTargetA), eb = Math.abs(yokeB - yokeTargetB);
            if (ea <= yokeHalf && eb <= yokeHalf) {
                record(Mth.clamp(1f - (ea + eb) / (yokeHalf * 2f), 0.58f, 1f));
                yokeRound++;
                Sfx.mark(3 + yokeRound);
                if (yokeRound >= yokeRoundsNeeded) advancePhase();
                else { showBanner("The yoke changes its law", theme().accentLite, 560); setupYokeRound(); }
            } else {
                record(0.15f); Sfx.markBad(); loseLife("The yoke was sealed out of balance");
            }
            return true;
        }

        // Four coupled valves. Each valve solves one axis while disturbing the
        // other; the player must infer the coupling, then deliberately seal it.
        if (mx >= cx - 132 && mx < cx - 72) {
            yokeA = Mth.clamp(yokeA - 0.070f, 0f, 1f);
            yokeB = Mth.clamp(yokeB + 0.036f, 0f, 1f);
        } else if (mx >= cx - 66 && mx < cx - 6) {
            yokeA = Mth.clamp(yokeA + 0.070f, 0f, 1f);
            yokeB = Mth.clamp(yokeB - 0.036f, 0f, 1f);
        } else if (mx >= cx + 6 && mx < cx + 66) {
            yokeB = Mth.clamp(yokeB - 0.070f, 0f, 1f);
            yokeA = Mth.clamp(yokeA + 0.036f, 0f, 1f);
        } else if (mx >= cx + 72 && mx <= cx + 132) {
            yokeB = Mth.clamp(yokeB + 0.070f, 0f, 1f);
            yokeA = Mth.clamp(yokeA - 0.036f, 0f, 1f);
        } else return false;
        yokeMoves++;
        Sfx.mark(1 + (yokeMoves % 3));
        return true;
    }

    private boolean clickRootwayEcho(double mx, double my) {
        int size = 28, gap = 5, board = size * 4 + gap * 3, sx = centerX() - board / 2, sy = contentCenterY() - board / 2 + 8;
        int cur = rootRoute[rootIndex];
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
            int x = sx + c * (size + gap), y = sy + r * (size + gap);
            if (mx >= x && mx <= x + size && my >= y && my <= y + size) {
                int cell = r * 4 + c;
                if (cell == cur) return true;
                int dr = Math.abs(r - cur / 4), dc = Math.abs(c - cur % 4);
                if (dr + dc != 1) {
                    record(0f);
                    if (loseLife("The root cannot jump dry stone")) rootDecisionTimer = rootDecisionLimit;
                    return true;
                }
                int dir = rootDir(cur, cell);
                if (!rootOpen[cur * 4 + dir]) {
                    record(0f);
                    if (loseLife("There is no living channel there")) rootDecisionTimer = rootDecisionLimit;
                    return true;
                }
                int expected = rootIndex + 1 < rootRoute.length ? rootRoute[rootIndex + 1] : -1;
                if (cell == expected) {
                    record(1f); rootIndex++; Sfx.mark(rootIndex);
                    rootDecisionTimer = Math.max(1.20f, rootDecisionLimit - rootIndex * 0.09f);
                    if (rootIndex >= rootRoute.length - 1) advancePhase();
                } else {
                    record(0.25f); Sfx.markBad();
                    if (loseLife("The branch is alive, but it is not the rootway"))
                        rootDecisionTimer = Math.max(1.20f, rootDecisionLimit - rootIndex * 0.09f);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickShardsongEcho(double mx, double my) {
        int size = 54, gap = 12, board = size * 2 + gap, sx = centerX() - board / 2, sy = contentCenterY() - board / 2 + 8;
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
            int x = sx + c * (size + gap), y = sy + r * (size + gap);
            if (mx >= x && mx <= x + size && my >= y && my <= y + size) {
                int tile = r * 2 + c;
                shardRot[tile] = (shardRot[tile] + 1) & 3;
                shardMoves++;
                Sfx.play(WardSounds.STONE_TURN, 0.30f, 0.92f + tile * 0.035f);
                if (shardsSolved()) {
                    record(Mth.clamp(shardIdeal / (float) Math.max(shardIdeal, shardMoves), 0.55f, 1f));
                    advancePhase();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickOrreryEcho(double mx, double my) {
        int cx = centerX(), cy = contentCenterY() + 8;
        int[] rad = {42, 68, 94};
        double dx = mx - cx, dy = my - cy, distance = Math.sqrt(dx*dx + dy*dy);
        int ring = -1;
        for (int i = 0; i < 3; i++) if (Math.abs(distance - rad[i]) <= 10) { ring = i; break; }
        if (ring < 0) return false;
        int dir = mx < cx ? -1 : 1;
        orreryEcho[ring] = Math.floorMod(orreryEcho[ring] + dir, 6);
        int next = (ring + 1) % 3;
        orreryEcho[next] = Math.floorMod(orreryEcho[next] + dir, 6);
        orreryEchoMoves++;
        Sfx.play(WardSounds.STONE_TURN, .30f, .78f + ring * .08f);
        boolean solved = true; for (int v : orreryEcho) solved &= v == 0;
        if (solved) {
            record(Mth.clamp(orreryEchoIdeal / (float) Math.max(orreryEchoIdeal, orreryEchoMoves), .52f, 1f));
            advancePhase();
        } else if (orreryEchoMoves >= orreryEchoIdeal + (isWeakPassage() ? 3 : 5)) {
            record(.2f); loseLife("The coupled meridian spends its last tooth"); setupOrreryEcho();
        }
        return true;
    }

    private boolean clickProcessionEcho(double mx, double my) {
        int size = 48, gap = 12, total = size * 4 + gap * 3, sx = centerX() - total / 2, sy = contentCenterY() - 26;
        int slot = -1;
        for (int i = 0; i < 4; i++) {
            int x = sx + i * (size + gap);
            if (mx >= x && mx <= x + size && my >= sy && my <= sy + size) { slot = i; break; }
        }
        if (slot < 0) return false;
        if (processionEchoSelected < 0) { processionEchoSelected = slot; Sfx.mark(slot); return true; }
        if (slot == processionEchoSelected) { processionEchoSelected = -1; return true; }
        int x = processionEchoCurrent[slot]; processionEchoCurrent[slot] = processionEchoCurrent[processionEchoSelected]; processionEchoCurrent[processionEchoSelected] = x;
        processionEchoSelected = -1; processionEchoMoves++;
        Sfx.play(WardSounds.STONE_TURN, .30f, .92f);
        if (java.util.Arrays.equals(processionEchoCurrent, processionEchoTarget)) {
            record(Mth.clamp(processionEchoIdeal / (float) Math.max(processionEchoIdeal, processionEchoMoves), .55f, 1f));
            advancePhase();
        } else if (processionEchoMoves >= processionEchoIdeal + (isWeakPassage() ? 2 : 4)) {
            record(.2f); loseLife("The witnesses refuse the remaining order"); setupProcessionEcho();
        }
        return true;
    }

    private float angularDifference(float a, float b) {
        float d = Math.abs(a - b) % Mth.TWO_PI;
        return d > Mth.PI ? Mth.TWO_PI - d : d;
    }

    private int orbitNodeAt(double mx, double my) {
        int cx = centerX();
        int cy = contentCenterY() + 4;
        for (int i = 0; i < 7; i++) {
            float ang = orbitAngle(i);
            float r = orbitRadius(i);
            float nx = cx + Mth.cos(ang) * r;
            float ny = cy + Mth.sin(ang) * r * 0.72f;
            if (distSq(mx, my, nx, ny) <= 12 * 12) return i;
        }
        return -1;
    }

    private float orbitRadius(int i) {
        return (i % 2 == 0 ? 72f : 48f) + Mth.sin(orbitPulse * 0.8f + i) * 3.2f;
    }

    private float orbitAngle(int i) {
        return orbitSpin + i * (Mth.TWO_PI / 7f);
    }

    private int memoryCellAt(double mx, double my) {
        int size = 34, gap = 8;
        int boardW = size * 3 + gap * 2;
        int sx = centerX() - boardW / 2;
        int sy = contentCenterY() - 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = sx + col * (size + gap);
                int y = sy + row * (size + gap);
                if (mx >= x && mx <= x + size && my >= y && my <= y + size) return row * 3 + col;
            }
        }
        return -1;
    }

    private int choirCellAt(double mx, double my) {
        int size = 30, gap = 7;
        int boardW = size * 3 + gap * 2;
        int sx = centerX() - boardW / 2;
        int sy = contentCenterY() - 31;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = sx + col * (size + gap);
                int y = sy + row * (size + gap);
                if (mx >= x && mx <= x + size && my >= y && my <= y + size) return row * 3 + col;
            }
        }
        return -1;
    }

    private float mawAngleForNode(int node) {
        return -Mth.HALF_PI + node * (Mth.TWO_PI / 8f);
    }

    private double distSq(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    @Override
    protected void renderGame(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left() + 12;
        int y = contentTop() + 6;
        int w = panelWidth() - 24;
        int h = contentBottom() - contentTop() - 10;

        p.roundRect(x, y, w, h, Painter.darken(theme().panel, 0.08f) | 0xFF000000);
        p.roundOutline(x, y, w, h, Painter.withAlpha(theme().accent, 0x58));
        p.rect(x + 1, y + 1, x + w - 1, y + 27, Painter.withAlpha(theme().frame, 0x24));
        renderAmbientNoise(x, y, w, h);
        renderPhaseHeader(g, x, y, w);
        progressDots(centerX(), y + 20, phaseCount, phase);

        switch (mechanic()) {
            case RITUAL_ORBIT -> renderOrbitPhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_PULSE -> renderTimingPhase(g, x, y, w, h);
            case RITUAL_MEMORY -> renderMemoryPhase(g, x, y, w, h);
            case RITUAL_MAW -> renderMawPhase(g, x, y, w, h);
            case RITUAL_FALSE_CHOIR -> renderChoirPhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_VEIL -> renderVeilPhase(g, x, y, w, h);
            case RITUAL_LATTICE -> renderLatticePhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_EPITAPH -> renderEpitaphPhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_MEASURE -> renderMeasurePhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_VEILSTEP -> renderVeilstepPhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_RUNEBRAID -> renderRunebraidPhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_RESONANCE -> renderResonancePhase(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_CIPHER -> renderCipherEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_PRESSURE -> renderPressureEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_PULSE_DISCIPLINE -> renderPulseEcho(g, x, y, w, h);
            case RITUAL_BALANCE -> renderBalanceEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_CONSTELLATION -> renderConstellationEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_MIRROR_DISCIPLINE -> renderMirrorEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_KEYWAY -> renderKeywayEcho(g, x, y, w, h);
            case RITUAL_AUGURY -> renderAuguryEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_VESSEL -> renderVesselEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_YOKE -> renderYokeEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_ROOTWAY -> renderRootwayEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_SHARDSONG -> renderShardsongEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_ORRERY -> renderOrreryEcho(g, mouseX, mouseY, x, y, w, h);
            case RITUAL_PROCESSION -> renderProcessionEcho(g, mouseX, mouseY, x, y, w, h);
            default -> { }
        }

        renderGlitchOverlay(x, y, w, h);
    }

    private void renderPhaseHeader(GuiGraphics g, int x, int y, int w) {
        // Progress is already encoded by the boxes below. Repeating 3/8 in the
        // title only made the header collide with those boxes. Keep one clean
        // discipline name and let color carry the special-passage state.
        int col = phase == phaseCount - 1 ? 0xFFE091A8
                : isWeakPassage() ? 0xFFE0A0B0
                : isStrongPassage() ? 0xFF9BD8D4
                : Painter.lighten(theme().accent, 0.22f);
        smallCentered(g, ritualName(), x + w / 2, y + 5, col, 0.86f);
    }

    private void renderAmbientNoise(int x, int y, int w, int h) {
        int gx = x + Math.round(jitterX);
        int gy = y + Math.round(jitterY);
        for (int i = 0; i < BG_COUNT; i++) {
            int px = gx + Math.round(bgX[i] + Mth.sin(globalTime * 0.55f + bgPhase[i]) * bgDrift[i] * 0.16f);
            int py = gy + Math.round(bgY[i] + Mth.cos(globalTime * 0.48f + bgPhase[i]) * bgDrift[i] * 0.12f);
            int alpha = 0x10 + (int) ((Mth.sin(globalTime + bgPhase[i]) * 0.5f + 0.5f) * 0x24);
            Sigils.drawCentered(p, Sigils.TILE[bgGlyph[i]], px, py, bgScale[i],
                    Painter.withAlpha(theme().accentDark, alpha),
                    Painter.withAlpha(theme().accentLite, alpha / 2), 0);
        }
        for (int i = 0; i < 32; i++) {
            int px = x + 6 + ((i * 37 + (int) (globalTime * 45)) % Math.max(1, w - 12));
            int py = y + 24 + ((i * 19 + (int) (globalTime * 63)) % Math.max(1, h - 30));
            int a = (i % 5 == 0) ? 0x60 : 0x28;
            p.rect(px, py, px + 1 + (i % 2), py + 1 + (i % 3 == 0 ? 1 : 0), Painter.withAlpha(theme().accentLite, a));
        }
        p.scanlines(x + 1, y + 1, w - 2, h - 2, Painter.withAlpha(0x000000, 0x16));
    }

    private void renderGlitchOverlay(int x, int y, int w, int h) {
        if (WardConfig.accessibilityReduceFlashing) return;
        int lines = 3 + (int) ((globalTime * 4f) % 3f);
        for (int i = 0; i < lines; i++) {
            int gy = y + 20 + Math.floorMod((int) (globalTime * 74) + i * 37, Math.max(1, h - 30));
            int gh = 1 + (i % 3);
            p.rect(x + 3, gy, x + w - 3, gy + gh, Painter.withAlpha(i == 1 ? theme().accentLite : 0xFFFFFF, 0x0A + i * 0x06));
        }
        if ((globalTime * 8f) % 1f < 0.16f) {
            int ox = 1 + (int) (Mth.sin(globalTime * 17f) * 2f);
            p.outline(x + ox, y + 22, w - 2, h - 26, 1, Painter.withAlpha(theme().accentLite, 0x12));
        }
    }

    private void renderOrbitPhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int cx = centerX();
        int cy = contentCenterY() + 4;
        p.discPixel(cx, cy, 20, Painter.withAlpha(theme().frame, 0x70));
        p.ringThick(cx, cy, 27, 2, Painter.withAlpha(theme().accentLite, 0x80));
        p.ringThick(cx, cy, 54, 1, Painter.withAlpha(theme().accent, 0x34));
        p.ringThick(cx, cy, 78, 1, Painter.withAlpha(theme().accentDark, 0x34));

        for (int i = 0; i < 7; i++) {
            float ang = orbitAngle(i);
            float r = orbitRadius(i);
            int nx = Math.round(cx + Mth.cos(ang) * r);
            int ny = Math.round(cy + Mth.sin(ang) * r * 0.72f);
            boolean target = i == orbitTarget;
            boolean hover = distSq(mouseX, mouseY, nx, ny) <= 13 * 13;
            int base = target ? theme().accentLite : theme().frameLite;
            int glow = target ? 0x6C : 0x34;
            if (hover) glow += 0x14;
            p.discPixel(nx, ny, target ? 10 : 8, Painter.withAlpha(base, glow));
            p.ringThick(nx, ny, target ? 12 : 10, 1, target ? theme().accentLite : theme().accentDark);
            Sigils.drawCentered(p, Sigils.TILE[(i * 3 + 1) % Sigils.TILE.length], nx, ny, 1,
                    target ? Painter.withAlpha(0x021412, 0xFF) : Painter.withAlpha(theme().well, 0xF0),
                    0, 0);
            p.line(cx, cy, nx, ny, 1, Painter.withAlpha(theme().accentDark, target ? 0x36 : 0x18));
        }
    }

    private void renderTimingPhase(GuiGraphics g, int x, int y, int w, int h) {
        int bx = x + 26;
        int by = contentCenterY() + 4;
        int bw = w - 52;
        int barH = 16;

        p.roundRect(bx, by - barH / 2, bw, barH, theme().well);
        p.outline(bx, by - barH / 2, bw, barH, 1, theme().frame);

        int tx1 = bx + Math.round((barTargetCenter - barTargetHalf) * bw);
        int tx2 = bx + Math.round((barTargetCenter + barTargetHalf) * bw);
        int hx1 = bx + Math.round((barHazardCenter - barHazardHalf) * bw);
        int hx2 = bx + Math.round((barHazardCenter + barHazardHalf) * bw);
        p.rect(tx1, by - barH / 2 + 2, tx2, by + barH / 2 - 2, Painter.withAlpha(COL_GOOD, 0x9A));
        p.rect(hx1, by - barH / 2 + 2, hx2, by + barH / 2 - 2, Painter.withAlpha(COL_BAD, 0x95));

        int mx = bx + Math.round(barMarker * bw);
        p.rect(mx - 2, by - 12, mx + 2, by + 12, theme().accentLite);
        p.rect(mx - 4, by - 3, mx + 4, by + 3, Painter.lighten(theme().accentLite, 0.18f));

    }

    private void renderMemoryPhase(GuiGraphics g, int x, int y, int w, int h) {
        int size = 34, gap = 8;
        int boardW = size * 3 + gap * 2;
        int sx = centerX() - boardW / 2;
        int sy = contentCenterY() - 38;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                int cx = sx + col * (size + gap);
                int cy = sy + row * (size + gap);
                boolean lit = idx == memoryFlash;
                int fill = lit ? Painter.mix(theme().accentLite, theme().panel, 0.25f) | 0xFF000000
                        : Painter.darken(theme().panel, 0.06f) | 0xFF000000;
                p.roundRect(cx, cy, size, size, fill);
                p.roundOutline(cx, cy, size, size,
                        lit ? Painter.withAlpha(theme().accentLite, 0xE0) : Painter.withAlpha(theme().frame, 0x90));
                if (lit) p.dither(cx + 2, cy + 2, size - 4, size - 4,
                        Painter.withAlpha(theme().accentLite, (int) (memoryFlashAlpha * 0x50)), 2, 0);
                Sigils.drawCentered(p, Sigils.TILE[(idx * 2 + 3) % Sigils.TILE.length], cx + size / 2, cy + size / 2,
                        1, lit ? theme().well : Painter.withAlpha(theme().accentDark, 0xA0), 0, 0);
            }
        }
    }

    private void renderChoirPhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size = 30, gap = 7;
        int boardW = size * 3 + gap * 2;
        int sx = centerX() - boardW / 2;
        int sy = contentCenterY() - 31;

        if (choirPreview) {
            p.discPixel(centerX(), contentCenterY(), 32, Painter.withAlpha(theme().frame, 0x5C));
            p.ringThick(centerX(), contentCenterY(), 38, 2, Painter.withAlpha(theme().accentLite, 0x88));
            Sigils.drawCentered(p, Sigils.TILE[choirTargetGlyph], centerX(), contentCenterY(), 2,
                    theme().accentLite, Painter.withAlpha(theme().accentDark, 0x48), 0);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                int bx = sx + col * (size + gap);
                int by = sy + row * (size + gap);
                boolean hover = !choirPreview && mouseX >= bx && mouseX <= bx + size && mouseY >= by && mouseY <= by + size;
                int alpha = choirPreview ? 0x28 : (hover ? 0xA0 : 0x72);
                p.roundRect(bx, by, size, size, Painter.withAlpha(theme().panel, 0xE8));
                p.roundOutline(bx, by, size, size, Painter.withAlpha(hover ? theme().accentLite : theme().frame, alpha));
                if (!choirPreview) Sigils.drawCentered(p, Sigils.TILE[choirGlyphs[idx]], bx + size / 2, by + size / 2, 1,
                        hover ? theme().accentLite : Painter.withAlpha(theme().frameLite, 0xC0), 0, 0);
            }
        }
    }

    private void renderVeilPhase(GuiGraphics g, int x, int y, int w, int h) {
        int cx = centerX(), cy = contentCenterY() + 1;
        int r1 = 48, r2 = 70;
        p.ringThick(cx, cy, r1, 2, Painter.withAlpha(0xFF74D8D2, 0x82));
        p.ringThick(cx, cy, r2, 2, Painter.withAlpha(0xFFD272D9, 0x82));
        p.ringThick(cx, cy, 18, 1, Painter.withAlpha(theme().frameLite, 0x50));

        int ax = Math.round(cx + Mth.cos(veilAngleA) * r1);
        int ay = Math.round(cy + Mth.sin(veilAngleA) * r1);
        int bx = Math.round(cx + Mth.cos(veilAngleB) * r2);
        int by = Math.round(cy + Mth.sin(veilAngleB) * r2);
        p.line(cx, cy, ax, ay, 2, Painter.withAlpha(0xFF74E7DE, 0xD0));
        p.line(cx, cy, bx, by, 2, Painter.withAlpha(0xFFE68AE8, 0xD0));
        p.discPixel(ax, ay, 7, 0xFF74E7DE);
        p.discPixel(bx, by, 7, 0xFFE68AE8);

        float diff = angularDifference(veilAngleA, veilAngleB);
        if (diff <= veilWindow * 1.8f) {
            int a = diff <= veilWindow ? 0xB8 : 0x5C;
            p.ringThick(cx, cy, 27, 2, Painter.withAlpha(diff <= veilWindow ? COL_GOOD : theme().accentLite, a));
        }
    }

    private void renderMawPhase(GuiGraphics g, int x, int y, int w, int h) {
        int cx = centerX();
        int cy = contentCenterY() + 2;
        int r = 62;
        p.discPixel(cx, cy, 18, Painter.withAlpha(theme().frame, 0x65));
        p.ringThick(cx, cy, r, 2, Painter.withAlpha(theme().accentLite, 0x88));
        p.ringThick(cx, cy, r - 14, 1, Painter.withAlpha(theme().accentDark, 0x58));
        float targetAngle = mawAngleForNode(mawSequence[Math.min(mawIndex, mawHitsNeeded - 1)]);
        p.arc(cx, cy, r + 8, (float) Math.toDegrees(targetAngle - mawWindow),
                (float) Math.toDegrees(targetAngle + mawWindow), 3, Painter.withAlpha(COL_GOOD, 0x88));

        for (int i = 0; i < 8; i++) {
            float ang = mawAngleForNode(i);
            int nx = Math.round(cx + Mth.cos(ang) * r);
            int ny = Math.round(cy + Mth.sin(ang) * r);
            boolean isCurrent = mawSequence[Math.min(mawIndex, mawHitsNeeded - 1)] == i;
            p.discPixel(nx, ny, isCurrent ? 10 : 8,
                    Painter.withAlpha(isCurrent ? theme().accentLite : theme().frameLite,
                            isCurrent ? 0x70 : 0x2E));
            p.ringThick(nx, ny, isCurrent ? 12 : 10, 1, isCurrent ? theme().accentLite : theme().accentDark);
            Sigils.drawCentered(p, Sigils.TILE[(i * 4 + 2) % Sigils.TILE.length], nx, ny, 1,
                    isCurrent ? theme().well : Painter.withAlpha(theme().accentDark, 0xD0), 0, 0);
        }

        float sx = cx + Mth.cos(mawAngle) * (r + 8);
        float sy = cy + Mth.sin(mawAngle) * (r + 8);
        p.line(cx, cy, sx, sy, 2, Painter.withAlpha(theme().accentLite,
                0x80 + Math.round(Mth.sin(mawPulse * 5f) * 0.5f * 0x20)));
    }


    private void renderLatticePhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size=34,gap=7,board=size*3+gap*2;
        int sx=centerX()-board/2, sy=contentCenterY()-board/2+5;
        for(int r=0;r<3;r++) for(int c=0;c<3;c++) {
            int cell=r*3+c, px=sx+c*(size+gap), py=sy+r*(size+gap);
            boolean lit=(latticeMask&(1<<cell))!=0;
            boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=py&&mouseY<=py+size;
            p.roundRect(px,py,size,size,lit?Painter.withAlpha(theme().accent,0x88):theme().well);
            p.roundOutline(px,py,size,size,hover?theme().accentLite:theme().frame);
            Sigils.drawCentered(p,Sigils.TILE[cell%Sigils.TILE.length],px+size/2,py+size/2,1,
                    lit?theme().well:Painter.withAlpha(theme().frameLite,0x9A),0,0);
        }
    }

    private void renderEpitaphRow(GuiGraphics g, int[] row, int hidden, int cy, int color) {
        int spacing=42,start=centerX()-spacing*3/2;
        for(int i=0;i<4;i++) {
            int cx=start+i*spacing;
            p.ringThick(cx,cy,12,1,Painter.withAlpha(theme().frame,0x70));
            if(i==hidden) smallCentered(g,"?",cx,cy-4,0xFFEBCDF3,1.0f);
            else Sigils.drawCentered(p,Sigils.TILE[Math.floorMod(row[i],Sigils.TILE.length)],cx,cy,1,color,0,0);
        }
    }

    private void renderEpitaphPhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int cy=contentCenterY()-50;
        renderEpitaphRow(g,epitaphBase,-1,cy,Painter.withAlpha(theme().frameLite,0xE0));
        renderEpitaphRow(g,epitaphWitness,-1,cy+36,Painter.withAlpha(theme().accent,0xE0));
        renderEpitaphRow(g,epitaphFinal,epitaphMissing,cy+72,Painter.withAlpha(theme().accentLite,0xE0));
        int spacing=42,oy=contentCenterY()+52,start=centerX()-spacing*3/2;
        for(int i=0;i<4;i++) {
            int cx=start+i*spacing;
            boolean hover=distSq(mouseX,mouseY,cx,oy)<=15*15;
            p.discPixel(cx,oy,14,Painter.withAlpha(hover?theme().accent:theme().frame,hover?0x7A:0x45));
            p.ringThick(cx,oy,15,1,hover?theme().accentLite:theme().frameLite);
            Sigils.drawCentered(p,Sigils.TILE[Math.floorMod(epitaphOptions[i],Sigils.TILE.length)],cx,oy,1,theme().text,0,0);
        }
    }

    private void renderMeasurePhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int[] cx={centerX()-78,centerX(),centerX()+78};
        int top=contentCenterY()-36,bottom=contentCenterY()+46;
        for(int i=0;i<3;i++) {
            boolean hover=mouseX>=cx[i]-24&&mouseX<=cx[i]+24&&mouseY>=top&&mouseY<=bottom;
            int col=i==measureSelected?theme().accentLite:(hover?theme().accent:theme().frame);
            p.roundOutline(cx[i]-24,top,48,bottom-top,col);
            float frac=measureAmt[i]/(float)measureCap[i];
            int fill=Math.round((bottom-top-6)*frac);
            p.rect(cx[i]-20,bottom-3-fill,cx[i]+20,bottom-3,Painter.withAlpha(theme().accent,0x88));
            smallCentered(g,measureAmt[i]+" / "+measureCap[i],cx[i],bottom+7,theme().text,0.78f);
        }
    }

    private void renderVeilstepPhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size=27,gap=6,board=size*4+gap*3;
        int sx=centerX()-board/2,sy=contentCenterY()-board/2+4;
        int current=stepRoute[stepIndex];
        for(int r=0;r<4;r++) for(int c=0;c<4;c++) {
            int cell=r*4+c,px=sx+c*(size+gap),py=sy+r*(size+gap);
            boolean cur=cell==current,goal=cell==stepGoal;
            boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=py&&mouseY<=py+size;
            p.roundRect(px,py,size,size,cur?Painter.withAlpha(theme().accent,0x82):theme().well);
            p.roundOutline(px,py,size,size,goal?0xFFD890E5:(hover?theme().accentLite:theme().frame));
            if(goal) Sigils.headerEye(p,px+size/2,py+size/2,1,theme().frameLite,theme().accent,0);
            else smallCentered(g,Integer.toString(stepJump[cell]),px+size/2,py+8,cur?theme().well:theme().text,0.86f);
        }
    }

    private void renderRunebraidPhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size=34,gap=7,total=size*5+gap*4,sx=centerX()-total/2,py=contentCenterY()-20;
        for(int i=0;i<5;i++) {
            int px=sx+i*(size+gap);
            boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=py&&mouseY<=py+size;
            boolean selected=i==braidSelected;
            p.roundRect(px,py,size,size,selected?Painter.withAlpha(theme().accent,0x7C):theme().well);
            p.roundOutline(px,py,size,size,hover||selected?theme().accentLite:theme().frame);
            Sigils.drawCentered(p,Sigils.TILE[braid[i]%Sigils.TILE.length],px+size/2,py+size/2,1,theme().text,0,0);
            int targetX=sx+i*(size+gap)+size/2;
            Sigils.drawCentered(p,Sigils.TILE[i%Sigils.TILE.length],targetX,py+50,1,Painter.withAlpha(theme().frameLite,0x75),0,0);
        }
    }

    private void renderResonancePhase(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int bx=left()+42,bw=panelWidth()-84,cy=contentCenterY()+5;
        float proposed=Mth.clamp((mouseX-bx)/(float)Math.max(1,bw),0f,1f);
        p.roundRect(bx,cy-38,bw,76,theme().well);
        p.roundOutline(bx,cy-38,bw,76,theme().frame);
        int prevTX=bx,prevTY=cy,prevPX=bx,prevPY=cy;
        for(int i=1;i<=bw;i+=3) {
            float u=i/(float)bw;
            int tx=bx+i;
            int ty=cy-12+Math.round(Mth.sin(u*Mth.TWO_PI*(1.5f+resonanceTarget*4f))*13f);
            int py=cy+12+Math.round(Mth.sin(u*Mth.TWO_PI*(1.5f+proposed*4f))*13f);
            p.line(prevTX,prevTY,tx,ty,1,Painter.withAlpha(theme().accent,0xD0));
            p.line(prevPX,prevPY,tx,py,1,Painter.withAlpha(theme().frameLite,0xA0));
            prevTX=tx;prevTY=ty;prevPX=tx;prevPY=py;
        }
        p.line(mouseX,cy-40,mouseX,cy+40,1,Painter.withAlpha(theme().accentLite,0x6A));
    }

    private void renderCipherEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int spacing = 62, start = centerX() - spacing, cy = contentCenterY() + 4;
        for (int i = 0; i < 3; i++) {
            int cx = start + i * spacing;
            boolean hover = distSq(mouseX, mouseY, cx, cy) <= 24 * 24;
            p.ringThick(cx, cy, 23, 2, hover ? theme().accentLite : theme().frame);
            Sigils.drawCentered(p, Sigils.TILE[(i * 5 + cipherPos[i]) % Sigils.TILE.length], cx, cy, 1, theme().text, 0, 0);
            int nx = start + ((i + 1) % 3) * spacing;
            p.line(cx + (nx > cx ? 24 : -24), cy, nx + (nx > cx ? -24 : 24), cy, 1, Painter.withAlpha(theme().accent, 0x42));
            smallCentered(g, "TURN / COUPLES NEXT", cx, cy - 37, Painter.withAlpha(theme().accentLite, hover ? 0xD0 : 0x80), 0.58f);
            p.ringThick(cx, cy + 37, 9, 1, Painter.withAlpha(theme().accent, 0x68));
            Sigils.drawCentered(p, Sigils.TILE[(i * 5 + cipherTarget[i]) % Sigils.TILE.length], cx, cy + 37, 1,
                    Painter.withAlpha(theme().accentLite, 0xA8), 0, 0);
        }
    }

    private void renderPressureEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int bx = centerX() - 128, by = contentCenterY() - 14, bw = 256;
        p.roundRect(bx, by, bw, 18, theme().well);
        int t1 = bx + Math.round((pressureTarget - pressureHalf) * bw), t2 = bx + Math.round((pressureTarget + pressureHalf) * bw);
        p.rect(t1, by + 2, t2, by + 16, Painter.withAlpha(COL_GOOD, 0x75));
        int burstX = bx + Math.round(pressureBurst * bw);
        p.rect(burstX, by + 2, bx + bw - 2, by + 16, Painter.withAlpha(COL_BAD, 0x30));
        p.line(burstX, by - 4, burstX, by + 22, 1, Painter.withAlpha(COL_BAD, 0xB8));
        int px = bx + Math.round(pressureValue * bw);
        p.line(px, by - 5, px, by + 23, 2, theme().accentLite);
        int[] cx = {bx, bx + 94, bx + 188};
        int[] cw = {84, 84, 68};
        String[] labels = {"PUMP", "VENT", "SEAL"};
        for (int i = 0; i < 3; i++) {
            boolean hover = mouseX >= cx[i] && mouseX <= cx[i] + cw[i] && mouseY >= by + 30 && mouseY <= by + 58;
            p.roundRect(cx[i], by + 30, cw[i], 28, Painter.withAlpha(i == 2 ? theme().accent : theme().frame, 0x66));
            p.roundOutline(cx[i], by + 30, cw[i], 28, hover ? theme().accentLite : theme().frameLite);
            smallCentered(g, labels[i], cx[i] + cw[i] / 2, by + 38, theme().text, 0.72f);
        }
        smallCentered(g, "BLEED " + Math.round(pressureLeak * 100f) + "%/s · REDLINE " + Math.round(pressureBurst * 100f) + "% · VERDICT " + (pressureRounds + 1) + " / " + pressureRoundsNeeded,
                centerX(), by - 21, theme().accentLite, 0.64f);
    }

    private void renderPulseEcho(GuiGraphics g, int x, int y, int w, int h) {
        int bx = x + 30, bw = w - 60, cy = contentCenterY() + 6;
        p.line(bx, cy, bx + bw, cy, 2, Painter.withAlpha(theme().frameLite, 0x70));
        int strike = bx + 46;
        p.line(strike, cy - 30, strike, cy + 30, 2, theme().accentLite);
        float horizon = 2.2f;
        for (int i = pulseBeatIndex; i < pulseBeats.length; i++) {
            float until = pulseBeats[i] - pulseSongTime;
            if (until < -pulseWindow || until > horizon) continue;
            int nx = strike + Math.round((until / horizon) * (bw - 58));
            p.discPixel(nx, cy, 7, Painter.withAlpha(i == pulseBeatIndex ? theme().accentLite : theme().accent, 0xB0));
        }
        smallCentered(g, pulseBeatIndex + " / " + pulseBeats.length + " beats answered", centerX(), cy + 43, theme().text, 0.82f);
    }

    private void renderBalanceEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int cx = centerX(), cy = contentCenterY() + 10;
        float angle = balanceTilt * 0.48f;
        int dx = Math.round(Mth.cos(angle) * 92), dy = Math.round(Mth.sin(angle) * 92);
        p.line(cx - dx, cy - dy, cx + dx, cy + dy, 4, theme().frameLite);
        p.discPixel(cx, cy, 8, theme().accent);
        p.ringThick(cx, cy, 12, 1, theme().accentLite);
        p.roundRect(cx - 118, cy + 48, 90, 24, Painter.withAlpha(theme().frame, 0x55));
        p.roundRect(cx + 28, cy + 48, 90, 24, Painter.withAlpha(theme().frame, 0x55));
        smallCentered(g, "COUNTER LEFT", cx - 73, cy + 55, theme().text, 0.68f);
        smallCentered(g, "COUNTER RIGHT", cx + 73, cy + 55, theme().text, 0.68f);
        smallCentered(g, "stable " + Math.round(balanceStable * 10f) / 10f + "s", cx, cy - 58, Painter.withAlpha(theme().accentLite, 0xB0), 0.74f);
    }

    private void renderConstellationEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int fx = left() + 34, fy = contentTop() + 42, fw = panelWidth() - 68, fh = 148;
        // Completed links remain on screen. Previously the route vanished after the
        // preview, which made successful clicks look like missing rendering.
        int visibleLinks = constellationPreview ? constellationPath.length - 1 : Math.max(0, constellationInput - 1);
        for (int i = 0; i < visibleLinks; i++) {
            int a = constellationPath[i], b = constellationPath[i + 1];
            int col = constellationPreview ? Painter.withAlpha(theme().accent, 0x8A) : Painter.withAlpha(theme().accentLite, 0xC0);
            p.line(fx + Math.round(constellationX[a] * fw), fy + Math.round(constellationY[a] * fh),
                    fx + Math.round(constellationX[b] * fw), fy + Math.round(constellationY[b] * fh), 2, col);
        }
        for (int i = 0; i < constellationX.length; i++) {
            int sx = fx + Math.round(constellationX[i] * fw), sy = fy + Math.round(constellationY[i] * fh);
            boolean hover = distSq(mouseX, mouseY, sx, sy) <= 12 * 12;
            boolean completed = false;
            for (int j = 0; j < constellationInput; j++) if (constellationPath[j] == i) { completed = true; break; }
            int col = completed ? theme().accentLite : Painter.withAlpha(theme().text, 0xC8);
            if (i == constellationMistFlash && constellationMistFlashTimer > 0f) col = COL_BAD;
            p.discPixel(sx, sy, hover ? 7 : 5, col);
            p.ringThick(sx, sy, hover ? 11 : 9, 1, Painter.withAlpha(hover ? theme().accentLite : theme().frameLite, hover ? 0xD0 : 0x76));
            if (constellationPreview) {
                for (int j = 0; j < constellationPath.length; j++) if (constellationPath[j] == i)
                    smallCentered(g, Integer.toString(j + 1), sx, sy - 15, theme().text, 0.70f);
            } else if (completed) {
                smallCentered(g, "OK", sx, sy - 14, theme().accentLite, 0.58f);
            }
        }
        smallCentered(g, constellationPreview ? "MEMORIZE THE WITNESS ROUTE" : "RETRACE  "+constellationInput+" / "+constellationPath.length,
                centerX(), fy + fh + 14, constellationPreview ? theme().accentLite : theme().text, 0.74f);
    }

    private void renderMirrorEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int tcx = centerX(), tcy = contentCenterY() - 52;
        p.roundOutline(tcx - 24, tcy - 22, 48, 44, theme().accent);
        Sigils.drawCentered(p, Sigils.TILE[mirrorTargetGlyph], tcx, tcy, 2, theme().text, 0, 0);
        smallCentered(g, (mirrorTargetFlipX ? "↔" : "") + (mirrorTargetFlipY ? "↕" : "") + " witness transform", tcx, tcy + 27, theme().accentLite, 0.72f);
        smallCentered(g, "MIRROR VERDICT  " + (mirrorRound + 1) + " / " + mirrorRoundsNeeded, tcx, tcy - 37, theme().accentLite, 0.68f);
        int size=42,gap=14,total=size*4+gap*3,sx=centerX()-total/2,cy=contentCenterY()+24;
        String[] labels={"I","II","III","IV"};
        for (int i=0;i<4;i++) {
            int px=sx+i*(size+gap); boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=cy-size/2&&mouseY<=cy+size/2;
            p.roundRect(px,cy-size/2,size,size,theme().well); p.roundOutline(px,cy-size/2,size,size,hover?theme().accentLite:theme().frame);
            Sigils.drawCenteredFlipped(p,Sigils.TILE[mirrorGlyph[i]],px+size/2,cy,1,theme().text,0,0,mirrorFlipX[i],mirrorFlipY[i]);
            // Do not print the answer's axes beneath each card. With a guaranteed
            // asymmetric target, the reflected drawing itself is sufficient and
            // keeps Mirror a visual-reading test rather than an arrow-matching quiz.
            smallCentered(g,labels[i],px+size/2,cy+25,theme().dim,0.58f);
        }
    }

    private void renderKeywayEcho(GuiGraphics g, int x, int y, int w, int h) {
        int bx=x+34,bw=w-68,cy=contentCenterY()+2;
        p.roundRect(bx,cy-10,bw,20,theme().well); p.roundOutline(bx,cy-10,bw,20,theme().frame);
        int pin=Math.min(keywayPin,3);
        float center=keywayCenter[pin];
        float falseCenter=keywayFalseCenter[pin];
        float trueSignal=Mth.clamp(1f-Math.abs(keywayMarker-center)/0.22f,0f,1f);
        float falseSignal=Mth.clamp((1f-Math.abs(keywayMarker-falseCenter)/0.16f)*0.68f,0f,0.68f);
        float signal=Math.max(trueSignal,falseSignal);

        // The final-exam keyway hides the shear line. The player must read the
        // lock's resistance: a false set plateaus, while the real gate can peak.
        int mx=bx+Math.round(keywayMarker*bw);
        p.line(mx,cy-18,mx,cy+18,2,theme().accentLite);
        if (trueSignal > 0.78f) {
            int halo = 4 + Math.round((trueSignal-0.78f)*18f);
            p.roundOutline(mx-halo,cy-halo,halo*2,halo*2,Painter.withAlpha(COL_GOOD,0x50+Math.round(trueSignal*0x50)));
        }

        int meterW=Math.min(226,bw), meterX=centerX()-meterW/2, meterY=cy+38;
        p.roundRect(meterX,meterY,meterW,9,theme().well);
        p.roundOutline(meterX,meterY,meterW,9,theme().frame);
        int fill=Math.round((meterW-4)*signal);
        if (fill>0) p.rect(meterX+2,meterY+2,meterX+2+fill,meterY+7,
                Painter.withAlpha(trueSignal>=0.70f?COL_GOOD:theme().accentLite,0xC0));
        smallCentered(g,"TENSION  "+Math.round(signal*100f)+"%",centerX(),meterY+15,theme().text,0.72f);
        if (falseSignal>trueSignal && falseSignal>0.44f)
            smallCentered(g,"HOLLOW GIVE",centerX(),meterY+28,theme().dim,0.64f);
        else if (trueSignal>0.82f)
            smallCentered(g,"DEEP GATE",centerX(),meterY+28,theme().accentLite,0.64f);
        smallCentered(g,"TUMBLER "+(pin+1)+" / 4",centerX(),cy-39,theme().text,0.84f);
    }

    private void renderAuguryEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size=36,gap=16,total=size*3+gap*2,sx=centerX()-total/2,cy=contentCenterY()-16;
        for(int i=0;i<3;i++) {
            int px=sx+i*(size+gap); boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=cy-size/2&&mouseY<=cy+size/2;
            p.roundRect(px,cy-size/2,size,size,theme().well); p.roundOutline(px,cy-size/2,size,size,hover?theme().accentLite:theme().frame);
            Sigils.drawCentered(p,Sigils.TILE[(auguryGuess[i]*5+i)%Sigils.TILE.length],px+size/2,cy,1,theme().text,0,0);
            smallCentered(g,Integer.toString(auguryGuess[i]+1),px+size/2,cy+24,theme().accentLite,0.68f);
        }
        p.roundRect(centerX()-46,cy+42,92,26,Painter.withAlpha(theme().accent,0x62));
        smallCentered(g,"SUBMIT OMEN",centerX(),cy+50,theme().text,0.72f);
        if(auguryAttempts>0) smallCentered(g,auguryExact+" exact · "+auguryMisplaced+" misplaced · attempt "+auguryAttempts,centerX(),cy+78,theme().accentLite,0.72f);
    }

    private void renderVesselEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int size=38,gap=18,total=size*4+gap*3,sx=centerX()-total/2,cy=contentCenterY()+12;
        if(vesselReveal) {
            int pos=0; for(int i=0;i<4;i++) if(vesselShell[i]==vesselTargetToken){pos=i;break;}
            int px=sx+pos*(size+gap)+size/2;
            p.ringThick(px,cy,27,2,theme().accentLite);
            smallCentered(g,"REMEMBER THIS VESSEL",centerX(),cy-62,theme().accentLite,0.78f);
        }
        for(int i=0;i<4;i++) {
            float drawSlot=i;
            if(vesselSwapA>=0 && (i==vesselSwapA || i==vesselSwapB)) {
                float eased=Mth.clamp(vesselSwapAnim,0f,1f);
                drawSlot=i==vesselSwapA ? Mth.lerp(eased,vesselSwapA,vesselSwapB) : Mth.lerp(eased,vesselSwapB,vesselSwapA);
            }
            int px=Math.round(sx+drawSlot*(size+gap)); boolean hover=vesselSwapA<0&&mouseX>=px&&mouseX<=px+size&&mouseY>=cy-size/2&&mouseY<=cy+size/2;
            p.roundRect(px,cy-size/2,size,size,theme().well); p.roundOutline(px,cy-size/2,size,size,hover?theme().accentLite:theme().frame);
            Sigils.drawCentered(p,Sigils.TILE[(vesselShell[i]*4+2)%Sigils.TILE.length],px+size/2,cy,1,vesselReveal&&vesselShell[i]==vesselTargetToken?theme().accentLite:theme().text,0,0);
        }
    }

    private void renderYokeEcho(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        int cx=centerX(), cy=contentCenterY()+4;
        int lx=cx-80, rx=cx+80, top=cy-58, bh=96;
        p.line(lx, cy-10, rx, cy-10, 1, Painter.withAlpha(theme().accent, 0x52));
        for(int i=0;i<2;i++) {
            int px=i==0?lx:rx; float val=i==0?yokeA:yokeB, target=i==0?yokeTargetA:yokeTargetB;
            p.roundRect(px-14,top,28,bh,Painter.withAlpha(theme().well,0xE0));
            p.roundOutline(px-14,top,28,bh,theme().frame);
            int t1=top+Math.round((1f-(target+yokeHalf))*bh),t2=top+Math.round((1f-(target-yokeHalf))*bh);
            p.rect(px-11,t1,px+11,t2,Painter.withAlpha(COL_GOOD,0x62));
            int vy=top+Math.round((1f-val)*bh);
            p.line(px-18,vy,px+18,vy,3,theme().accentLite);
            p.discPixel(px,vy,4,theme().accentLite);
        }
        smallCentered(g,"YOKE I",lx,top+bh+7,theme().text,0.70f);
        smallCentered(g,"YOKE II",rx,top+bh+7,theme().text,0.70f);
        smallCentered(g,"COUPLED VERDICT  "+(yokeRound+1)+" / "+yokeRoundsNeeded,cx,top-15,theme().accentLite,0.76f);
        int buttonY=cy+56;
        int[] bx={cx-132,cx-66,cx+6,cx+72};
        String[] labels={"I <","I >","< II","> II"};
        for(int i=0;i<4;i++) {
            boolean hover=mouseX>=bx[i]&&mouseX<=bx[i]+60&&mouseY>=buttonY&&mouseY<=buttonY+26;
            p.roundRect(bx[i],buttonY,60,26,Painter.withAlpha(theme().frame,0x58));
            p.roundOutline(bx[i],buttonY,60,26,hover?theme().accentLite:theme().frameLite);
            smallCentered(g,labels[i],bx[i]+30,buttonY+8,theme().text,0.72f);
        }
        int sealY=buttonY+32;
        boolean sealHover=mouseX>=cx-42&&mouseX<=cx+42&&mouseY>=sealY&&mouseY<=sealY+24;
        p.roundRect(cx-42,sealY,84,24,Painter.withAlpha(theme().accent,0x62));
        p.roundOutline(cx-42,sealY,84,24,sealHover?theme().accentLite:theme().accent);
        smallCentered(g,"BIND YOKE",cx,sealY+7,theme().text,0.72f);
    }

    private void renderRootwayEcho(GuiGraphics g,int mouseX,int mouseY,int x,int y,int w,int h){
        int size=28,gap=5,board=size*4+gap*3,sx=centerX()-board/2,sy=contentCenterY()-board/2+8,cur=rootRoute[rootIndex];
        int timerW = board, timerX = sx, timerY = sy - 18;
        p.roundRect(timerX,timerY,timerW,6,theme().well);
        int timerFill = Math.round(timerW * Mth.clamp(rootDecisionTimer / Math.max(0.001f, rootDecisionLimit), 0f, 1f));
        if(timerFill>0) p.rect(timerX+1,timerY+1,timerX+Math.max(1,timerFill-1),timerY+5,
                Painter.withAlpha(rootDecisionTimer < 0.65f ? COL_BAD : theme().accentLite,0xC0));
        for(int r=0;r<4;r++) for(int c=0;c<4;c++){
            int cell=r*4+c,px=sx+c*(size+gap),py=sy+r*(size+gap); boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=py&&mouseY<=py+size;
            p.roundRect(px,py,size,size,cell==cur?Painter.withAlpha(theme().accent,0x72):theme().well); p.roundOutline(px,py,size,size,cell==rootGoal?theme().accentLite:(hover?theme().frameLite:theme().frame));
            int ccx=px+size/2,ccy=py+size/2;
            for(int d=0;d<4;d++) if(rootOpen[cell*4+d]) { int ex=ccx+(d==1?10:d==3?-10:0),ey=ccy+(d==2?10:d==0?-10:0); p.line(ccx,ccy,ex,ey,2,Painter.withAlpha(theme().accent,0x95)); }
            if(cell==rootGoal) Sigils.headerEye(p,ccx,ccy,1,theme().frameLite,theme().accent,0);
        }
    }

    private int shardColor(int mark){ return mark==0?Painter.withAlpha(theme().frameLite,0x50):mark==1?0xFFE79A9A:mark==2?0xFF8FCBE8:0xFFC5A2E9; }

    private void renderShardsongEcho(GuiGraphics g,int mouseX,int mouseY,int x,int y,int w,int h){
        int size=54,gap=12,board=size*2+gap,sx=centerX()-board/2,sy=contentCenterY()-board/2+8;
        for(int r=0;r<2;r++) for(int c=0;c<2;c++){
            int tile=r*2+c,px=sx+c*(size+gap),py=sy+r*(size+gap); boolean hover=mouseX>=px&&mouseX<=px+size&&mouseY>=py&&mouseY<=py+size;
            p.roundRect(px,py,size,size,theme().well);p.roundOutline(px,py,size,size,hover?theme().accentLite:theme().frame);
            int ccx=px+size/2,ccy=py+size/2;
            p.rect(ccx-5,py-2,ccx+5,py+3,shardColor(shardEdgeAt(tile,0)));p.rect(px+size-3,ccy-5,px+size+2,ccy+5,shardColor(shardEdgeAt(tile,1)));
            p.rect(ccx-5,py+size-3,ccx+5,py+size+2,shardColor(shardEdgeAt(tile,2)));p.rect(px-2,ccy-5,px+3,ccy+5,shardColor(shardEdgeAt(tile,3)));
            Sigils.drawCentered(p,Sigils.TILE[(tile*7+3)%Sigils.TILE.length],ccx,ccy,1,theme().text,0,0);
        }
    }

    private void renderOrreryEcho(GuiGraphics g,int mouseX,int mouseY,int x,int y,int w,int h){
        int cx=centerX(),cy=contentCenterY()+8; int[] rad={42,68,94};
        smallCentered(g,"LEFT / RIGHT HALF TURNS THE CHOSEN RING",cx,y+38,theme().faint,.58f);
        for(int i=2;i>=0;i--){
            p.ringThick(cx,cy,rad[i],1,Painter.withAlpha(theme().accent,0x65));
            for(int s=0;s<6;s++){double a=-Math.PI/2+s*Math.PI*2/6;int px=cx+(int)Math.round(Math.cos(a)*rad[i]),py=cy+(int)Math.round(Math.sin(a)*rad[i]);p.rect(px-1,py-1,px+2,py+2,s==0?theme().accentLite:Painter.withAlpha(theme().frameLite,0x55));}
            double a=-Math.PI/2+orreryEcho[i]*Math.PI*2/6;int px=cx+(int)Math.round(Math.cos(a)*rad[i]),py=cy+(int)Math.round(Math.sin(a)*rad[i]);
            Sigils.drawCentered(p,Sigils.TILE[(i*7+5)%Sigils.TILE.length],px,py,1,theme().text,Painter.withAlpha(theme().accent,0x72),0);
        }
        smallCentered(g,"COUPLING: INNER → MIDDLE → OUTER → INNER",cx,cy+108,theme().dim,.58f);
    }

    private void renderProcessionEcho(GuiGraphics g,int mouseX,int mouseY,int x,int y,int w,int h){
        int size=48,gap=12,total=size*4+gap*3,sx=centerX()-total/2,sy=contentCenterY()-26;
        String[] n={"I","II","III","IV"};
        for(int i=0;i<4;i++){int px=sx+i*(size+gap);boolean sel=i==processionEchoSelected;boolean hov=mouseX>=px&&mouseX<=px+size&&mouseY>=sy&&mouseY<=sy+size;
            p.roundRect(px,sy,size,size,theme().well);p.roundOutline(px,sy,size,size,sel?theme().accentLite:hov?theme().accent:theme().frame);
            int v=processionEchoCurrent[i];Sigils.drawCentered(p,Sigils.TILE[(v*9+2)%Sigils.TILE.length],px+size/2,sy+18,1,theme().text,0,0);smallCentered(g,n[v],px+size/2,sy+33,theme().accentLite,.66f);
        }
        int a=processionEchoTarget[0],b=processionEchoTarget[1],c=processionEchoTarget[2],d=processionEchoTarget[3];
        smallCentered(g,n[a]+" stands somewhere before "+n[c],centerX(),sy+66,theme().text,.60f);
        smallCentered(g,n[b]+" follows "+n[a]+" directly",centerX(),sy+80,theme().text,.60f);
        smallCentered(g,n[d]+" closes the procession",centerX(),sy+94,theme().text,.60f);
    }

    @Override
    protected String hintText() {
        if (ritual() == RITUAL_FINAL_MAW) return "Survive the seven verdicts of the Final Mouth";
        return switch (mechanic()) {
            case RITUAL_ORBIT -> "Click the glowing signal nodes";
            case RITUAL_PULSE -> "Click or press Space to split the pulse";
            case RITUAL_MEMORY -> memoryPlayback ? "Watch the pattern" : "Repeat the witness grid";
            case RITUAL_MAW -> "Click or press Space when the sweep meets the target";
            case RITUAL_FALSE_CHOIR -> choirPreview ? "Remember the true voice" : "Find the matching voice";
            case RITUAL_VEIL -> "Click or press Space when both veils converge";
            case RITUAL_LATTICE -> "Toggle the cross-shaped seals until the lattice goes dark";
            case RITUAL_EPITAPH -> "Read the full lines, find the shared rune rule, then restore the missing sign";
            case RITUAL_MEASURE -> "Pick the vessel you pour from, then the one you pour into";
            case RITUAL_VEILSTEP -> "Read the current stone and move exactly that many tiles orthogonally";
            case RITUAL_RUNEBRAID -> "Swap adjacent strands into the ghost order";
            case RITUAL_RESONANCE -> "Match the two waveforms and click the spectrum";
            case RITUAL_CIPHER -> "Rotate every ring index onto its witness mark";
            case RITUAL_PRESSURE -> "Pump, vent and compensate for leakage; seal each verdict inside the green band";
            case RITUAL_PULSE_DISCIPLINE -> "Strike each approaching choir-beat on the fixed line";
            case RITUAL_BALANCE -> "Counter the drifting beam until it stays centered";
            case RITUAL_CONSTELLATION -> constellationPreview ? "Witness the star route" : "Repeat the route in order";
            case RITUAL_MIRROR_DISCIPLINE -> "Survive the mirror verdicts: choose the exact glyph and both reflection axes";
            case RITUAL_KEYWAY -> "Read the tension response: false sets plateau; the true gate peaks. Set all four tumblers";
            case RITUAL_AUGURY -> "Cycle three omen wheels; submit and read exact/misplaced clues";
            case RITUAL_VESSEL -> vesselReveal ? "Remember the marked vessel" : vesselShufflesLeft > 0 ? "Track the marked vessel" : "Choose where the mark ended";
            case RITUAL_YOKE -> "Use the four coupling zones until both weights enter their bands";
            case RITUAL_ROOTWAY -> "Read the living channels and move before the root withers; dead-end branches lie";
            case RITUAL_SHARDSONG -> "Rotate the four shards until all edge runes agree";
            case RITUAL_ORRERY -> "Turn a ring; its neighbour turns with it. Align all three meridians";
            case RITUAL_PROCESSION -> "Swap the four witnesses into the only order allowed by the testimony";
            default -> "";
        };
    }
}
