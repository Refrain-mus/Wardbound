package dev.marrowseal.wardbound.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import dev.marrowseal.wardbound.client.ClientHooks;

import java.util.function.Supplier;

public class OpenMinigamePacket {

    public final BlockPos pos;
    public final int value;
    public final int gameId;
    public final int lives;
    public final int maxLives;
    public final long seed;
    public final int progress;
    public final float difficulty;
    public final float lootMultiplier;
    public final String dimensionId;
    /** How many locks the player has taken in this dimension without a loss. */
    public final int streak;
    /**
     * Which charm bound itself to this lock, as a {@code Charm} ordinal.
     *
     * <p>The client needs this for more than a badge in the header: the heart is
     * supposed to tell you something about the lock it is riding, and a screen
     * cannot show what it was never told.
     */
    public final int charm;
    /** How many of this seal's maker the player has already broken. */
    public final int familiarity;
    /**
     * This seal carries no maker's mark. See {@code ChestValuator.unsigned}.
     */
    public final boolean unsigned;
    /** Extra seconds on the ward's clock, bought by whatever the player is carrying. */
    public final int extraSeconds;
    /** Server-authoritative per-game tuning from wardbound.json. */
    public final float timeScale;
    public final float speedScale;
    public final float inputWindowScale;
    /** Persistent wear carried by this physical container. */
    public final int scarCount;
    /** bit 0 burned, bit 1 grudge, bit 2 re-sealed. */
    public final int scarFlags;
    /** This ward is one stage of an Eldritch Ward chain. */
    public final boolean eldritch;
    /** 1-based current stage of the eldritch sequence. */
    public final int eldritchStage;
    /** How many stages the eldritch sequence has in total. */
    public final int eldritchTotal;
    /** Rare single-lock corruption, separate from an Eldritch chain. */
    public final boolean possessed;
    /** This house is reacting to the player's familiarity with its paired rival. */
    public final boolean rivalry;
    /** Persistent physical mutation tier derived from ward history. */
    public final int mutationLevel;
    /** 0 none, 1 whispering, 2 hollow, 3 blind, 4 starved, 5 blood-warm. */
    public final int affliction;
    public final boolean living;
    public final boolean mercy;
    /** True when this screen is reopening a previously saved attempt. */
    public final boolean resumed;
    /** Saved absolute ward-clock seconds left; meaningful only when {@link #resumed} is true. */
    public final float savedClockLeft;
    /** Elapsed attempt time accumulated before this screen was reopened. */
    public final float savedElapsedSeconds;
    /** Significant mistakes accumulated before this screen was reopened. */
    public final int savedMistakes;
    /** v38: rapid consecutive error events accumulated before reopen. */
    public final int savedRapidMistakes;
    /** v38: prior hybrid outcome, 0 none, 1 cleared, 2 failed. */
    public final int savedHybridOutcome;
    /** v38: a borrowed micro-round was active when the screen was closed. */
    public final boolean savedHybridActive;
    /** v38: elapsed seconds inside the borrowed micro-round. */
    public final float savedHybridTimer;
    /** v38: discrete progress inside rune-style borrowed rounds. */
    public final int savedHybridStep;
    /** v38: 0..1 charge/alignment state for analogue borrowed rounds. */
    public final float savedHybridGauge;
    /** Remaining 0..1 phase of the Hurried anomaly cycle. */
    public final float savedHurriedClock;
    /** Whether the SPENT anomaly's one-time life/progress tax was already applied. */
    public final boolean spentApplied;
    /** One-shot minigame manipulation written by a consumed card bargain. */
    public final int cardMinigameMask;
    /** Per-discipline mastery tier, 0..5. Used for expert presentation and Cthulhu exam depth. */
    public final int masteryTier;
    /** Curated per-minigame corruption slot. 0 ordinary, 1/2 are that discipline's two variants. */
    public final int corruptionVariant;
    /** Ordinal of the player's weakest observed ordinary discipline, used by Cthulhu's final exam. */
    public final int examWeakGame;
    /** Ordinal of the player's strongest observed ordinary discipline, used by Cthulhu's final exam. */
    public final int examStrongGame;
    /** v38: independent late-game misinformation mode; never changes authoritative puzzle state. */
    public final int deceptionMode;
    /** v38: rare borrowed micro-round inserted into the host minigame. */
    public final int hybridMode;
    /** Whether the one-shot hybrid interruption was already resolved before a resume. */
    public final boolean hybridCompleted;
    /** Mid-game three-ward cadence: 0 none, 1 Opening, 2 Pressure, 3 Verdict. */
    public final int measureStage;
    /** Verdict clause id: 0 none, 1 mirror, 2 quicken, 3 bind. */
    public final int measureClause;
    /** Server-authoritative global ward progression; gates client-authored expert variants. */
    public final int resolvedWards;
    /** True only after both halves of the late Master phase gate are satisfied. */
    public final boolean masterPhase;

    public OpenMinigamePacket(BlockPos pos, int value, int gameId, int lives, int maxLives,
                              long seed, int progress, float difficulty, float lootMultiplier,
                              String dimensionId, int streak, int charm,
                              int familiarity, boolean unsigned, int extraSeconds,
                              float timeScale, float speedScale, float inputWindowScale,
                              int scarCount, int scarFlags,
                              boolean eldritch, int eldritchStage, int eldritchTotal,
                              boolean possessed, boolean rivalry, int mutationLevel,
                              int affliction, boolean living, boolean mercy,
                              boolean resumed, float savedClockLeft, float savedElapsedSeconds,
                              int savedMistakes, float savedHurriedClock, boolean spentApplied,
                              int cardMinigameMask, int masteryTier, int corruptionVariant, int examWeakGame, int examStrongGame) {
        this(pos, value, gameId, lives, maxLives, seed, progress, difficulty, lootMultiplier, dimensionId, streak, charm,
                familiarity, unsigned, extraSeconds, timeScale, speedScale, inputWindowScale, scarCount, scarFlags,
                eldritch, eldritchStage, eldritchTotal, possessed, rivalry, mutationLevel, affliction, living, mercy,
                resumed, savedClockLeft, savedElapsedSeconds, savedMistakes, savedHurriedClock, spentApplied,
                cardMinigameMask, masteryTier, corruptionVariant, examWeakGame, examStrongGame,
                0, 0, false, 0, 0, false, 0f, 0, 0f, 0, 0, Integer.MAX_VALUE);
    }

    public OpenMinigamePacket(BlockPos pos, int value, int gameId, int lives, int maxLives,
                              long seed, int progress, float difficulty, float lootMultiplier,
                              String dimensionId, int streak, int charm,
                              int familiarity, boolean unsigned, int extraSeconds,
                              float timeScale, float speedScale, float inputWindowScale,
                              int scarCount, int scarFlags,
                              boolean eldritch, int eldritchStage, int eldritchTotal,
                              boolean possessed, boolean rivalry, int mutationLevel,
                              int affliction, boolean living, boolean mercy,
                              boolean resumed, float savedClockLeft, float savedElapsedSeconds,
                              int savedMistakes, float savedHurriedClock, boolean spentApplied,
                              int cardMinigameMask, int masteryTier, int corruptionVariant, int examWeakGame, int examStrongGame,
                              int deceptionMode, int hybridMode, boolean hybridCompleted, int savedRapidMistakes, int savedHybridOutcome,
                              boolean savedHybridActive, float savedHybridTimer, int savedHybridStep, float savedHybridGauge,
                              int measureStage, int measureClause, int resolvedWards) {
        this(pos, value, gameId, lives, maxLives, seed, progress, difficulty, lootMultiplier, dimensionId, streak, charm,
                familiarity, unsigned, extraSeconds, timeScale, speedScale, inputWindowScale, scarCount, scarFlags,
                eldritch, eldritchStage, eldritchTotal, possessed, rivalry, mutationLevel, affliction, living, mercy,
                resumed, savedClockLeft, savedElapsedSeconds, savedMistakes, savedHurriedClock, spentApplied,
                cardMinigameMask, masteryTier, corruptionVariant, examWeakGame, examStrongGame,
                deceptionMode, hybridMode, hybridCompleted, savedRapidMistakes, savedHybridOutcome,
                savedHybridActive, savedHybridTimer, savedHybridStep, savedHybridGauge,
                measureStage, measureClause, resolvedWards, false);
    }

    public OpenMinigamePacket(BlockPos pos, int value, int gameId, int lives, int maxLives,
                              long seed, int progress, float difficulty, float lootMultiplier,
                              String dimensionId, int streak, int charm,
                              int familiarity, boolean unsigned, int extraSeconds,
                              float timeScale, float speedScale, float inputWindowScale,
                              int scarCount, int scarFlags,
                              boolean eldritch, int eldritchStage, int eldritchTotal,
                              boolean possessed, boolean rivalry, int mutationLevel,
                              int affliction, boolean living, boolean mercy,
                              boolean resumed, float savedClockLeft, float savedElapsedSeconds,
                              int savedMistakes, float savedHurriedClock, boolean spentApplied,
                              int cardMinigameMask, int masteryTier, int corruptionVariant, int examWeakGame, int examStrongGame,
                              int deceptionMode, int hybridMode, boolean hybridCompleted, int savedRapidMistakes, int savedHybridOutcome,
                              boolean savedHybridActive, float savedHybridTimer, int savedHybridStep, float savedHybridGauge,
                              int measureStage, int measureClause, int resolvedWards, boolean masterPhase) {

        this.pos = pos;
        this.value = value;
        this.gameId = gameId;
        this.lives = lives;
        this.maxLives = maxLives;
        this.seed = seed;
        this.progress = progress;
        this.difficulty = difficulty;
        this.lootMultiplier = lootMultiplier;
        this.dimensionId = dimensionId;
        this.streak = streak;
        this.charm = charm;
        this.familiarity = familiarity;
        this.unsigned = unsigned;
        this.extraSeconds = extraSeconds;
        this.timeScale = timeScale;
        this.speedScale = speedScale;
        this.inputWindowScale = inputWindowScale;
        this.scarCount = scarCount;
        this.scarFlags = scarFlags;
        this.eldritch = eldritch;
        this.eldritchStage = eldritchStage;
        this.eldritchTotal = eldritchTotal;
        this.possessed = possessed;
        this.rivalry = rivalry;
        this.mutationLevel = mutationLevel;
        this.affliction = affliction;
        this.living = living;
        this.mercy = mercy;
        this.resumed = resumed;
        this.savedClockLeft = savedClockLeft;
        this.savedElapsedSeconds = savedElapsedSeconds;
        this.savedMistakes = savedMistakes;
        this.savedHurriedClock = savedHurriedClock;
        this.spentApplied = spentApplied;
        this.cardMinigameMask = cardMinigameMask;
        this.masteryTier = Math.max(0, Math.min(5, masteryTier));
        this.corruptionVariant = Math.max(0, Math.min(2, corruptionVariant));
        this.examWeakGame = examWeakGame;
        this.examStrongGame = examStrongGame;
        this.deceptionMode = Math.max(0, deceptionMode);
        this.hybridMode = Math.max(0, hybridMode);
        this.hybridCompleted = hybridCompleted;
        this.savedRapidMistakes = Math.max(0, savedRapidMistakes);
        this.savedHybridOutcome = Math.max(0, Math.min(2, savedHybridOutcome));
        this.savedHybridActive = savedHybridActive && !hybridCompleted;
        this.savedHybridTimer = Math.max(0f, Math.min(30f, savedHybridTimer));
        this.savedHybridStep = Math.max(0, Math.min(15, savedHybridStep));
        this.savedHybridGauge = Math.max(0f, Math.min(1.2f, savedHybridGauge));
        this.measureStage = Math.max(0, Math.min(3, measureStage));
        this.measureClause = Math.max(0, Math.min(3, measureClause));
        this.resolvedWards = Math.max(0, resolvedWards);
        this.masterPhase = masterPhase;
    }

    public static void encode(OpenMinigamePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.value);
        buf.writeVarInt(msg.gameId);
        buf.writeVarInt(msg.lives);
        buf.writeVarInt(msg.maxLives);
        buf.writeLong(msg.seed);
        buf.writeVarInt(msg.progress);
        buf.writeFloat(msg.difficulty);
        buf.writeFloat(msg.lootMultiplier);
        buf.writeUtf(msg.dimensionId, 256);
        buf.writeVarInt(msg.streak);
        buf.writeVarInt(msg.charm);
        buf.writeVarInt(msg.familiarity);
        buf.writeBoolean(msg.unsigned);
        buf.writeVarInt(msg.extraSeconds);
        buf.writeFloat(msg.timeScale);
        buf.writeFloat(msg.speedScale);
        buf.writeFloat(msg.inputWindowScale);
        buf.writeVarInt(msg.scarCount);
        buf.writeVarInt(msg.scarFlags);
        buf.writeBoolean(msg.eldritch);
        buf.writeVarInt(msg.eldritchStage);
        buf.writeVarInt(msg.eldritchTotal);
        buf.writeBoolean(msg.possessed);
        buf.writeBoolean(msg.rivalry);
        buf.writeVarInt(msg.mutationLevel);
        buf.writeVarInt(msg.affliction);
        buf.writeBoolean(msg.living);
        buf.writeBoolean(msg.mercy);
        buf.writeBoolean(msg.resumed);
        buf.writeFloat(msg.savedClockLeft);
        buf.writeFloat(msg.savedElapsedSeconds);
        buf.writeVarInt(msg.savedMistakes);
        buf.writeFloat(msg.savedHurriedClock);
        buf.writeBoolean(msg.spentApplied);
        buf.writeVarInt(msg.cardMinigameMask);
        buf.writeVarInt(msg.masteryTier);
        buf.writeVarInt(msg.corruptionVariant);
        buf.writeVarInt(msg.examWeakGame);
        buf.writeVarInt(msg.examStrongGame);
        buf.writeVarInt(msg.deceptionMode);
        buf.writeVarInt(msg.hybridMode);
        buf.writeBoolean(msg.hybridCompleted);
        buf.writeVarInt(msg.savedRapidMistakes);
        buf.writeVarInt(msg.savedHybridOutcome);
        buf.writeBoolean(msg.savedHybridActive);
        buf.writeFloat(msg.savedHybridTimer);
        buf.writeVarInt(msg.savedHybridStep);
        buf.writeFloat(msg.savedHybridGauge);
        buf.writeVarInt(msg.measureStage);
        buf.writeVarInt(msg.measureClause);
        buf.writeVarInt(msg.resolvedWards);
        buf.writeBoolean(msg.masterPhase);
    }

    public static OpenMinigamePacket decode(FriendlyByteBuf buf) {
        return new OpenMinigamePacket(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean());
    }

    public static void handle(OpenMinigamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openScreen(msg)));
        ctx.get().setPacketHandled(true);
    }
}
