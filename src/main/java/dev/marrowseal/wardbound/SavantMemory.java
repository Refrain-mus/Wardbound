package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;

import java.util.UUID;

/**
 * Long-form observational memory for the Savant. Counters are recorded even
 * before the eye is acquired, allowing it to make disturbingly informed remarks
 * about habits that pre-date ownership without running a constant background scan.
 */
public final class SavantMemory {
    private SavantMemory() {}

    private static final long GLOBAL_GAP = 20L * 75L;

    private static int bump(ServerPlayer player, String key) {
        if (player == null || player.getServer() == null) return 0;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int n = data.uniqueInt(id, "savant_mem_" + key) + 1;
        data.setUniqueInt(id, "savant_mem_" + key, n);
        int total = data.uniqueInt(id, "savant_mem_total") + 1;
        data.setUniqueInt(id, "savant_mem_total", total);
        WardHistory.discoverSavantObservation(player, key);
        if (total == 12) speak(player, "memory_density_12", "Twelve useful observations. You are beginning to behave statistically.", 20L * 180L);
        else if (total == 32) speak(player, "memory_density_32", "Thirty-two observations. I can now distinguish your caution from your fear with tolerable confidence.", 20L * 220L);
        else if (total == 64) speak(player, "memory_density_64", "Sixty-four observations. I do not need to predict you often anymore. You repeat yourself for me.", 20L * 260L);
        return n;
    }

    /** Counter that refines an observation already recorded by {@link #bump} without double-counting memory density. */
    private static int bumpLocal(ServerPlayer player, String key) {
        if (player == null || player.getServer() == null) return 0;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int n = data.uniqueInt(id, "savant_mem_" + key) + 1;
        data.setUniqueInt(id, "savant_mem_" + key, n);
        return n;
    }

    private static void speak(ServerPlayer player, String key, String line, long cooldown) {
        if (player == null || player.getServer() == null || line == null || line.isBlank()) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        long next = data.uniqueLong(id, "savant_memory_global_next");
        if (next != Long.MIN_VALUE && now < next) return;
        data.setUniqueLong(id, "savant_memory_global_next", now + GLOBAL_GAP);
        CthulhuEyeItem.speakInsight(player, "memory_" + key, line, cooldown);
    }

    public static void onWardFailure(ServerPlayer player, MinigameType game) {
        String id = game == null ? "unknown" : game.id;
        int n = bump(player, "fail_" + id);
        if (n == 3) speak(player, "fail3_" + id,
                "That is the third time " + prettify(id) + " has stopped you. I have enough samples to call it a habit now.", 20L * 150L);
        else if (n == 6) speak(player, "fail6_" + id,
                "Six failures on the same discipline. The seal is no longer the only participant repeating itself.", 20L * 220L);
    }

    public static void onWardSuccess(ServerPlayer player, MinigameType game, RewardBreakdown.PerformanceGrade grade) {
        String id = game == null ? "unknown" : game.id;
        RewardBreakdown.PerformanceGrade resolved = grade == null ? RewardBreakdown.PerformanceGrade.SCRAPED : grade;
        int n = bump(player, "win_" + id);
        int q = bumpLocal(player, "grade_" + resolved.name().toLowerCase(java.util.Locale.ROOT));
        if (resolved == RewardBreakdown.PerformanceGrade.PERFECT && (q == 1 || q == 4 || q == 10)) {
            speak(player, "perfect_" + id + "_" + q,
                    q == 1
                            ? "That was not merely successful. " + prettify(id) + " had no correction to make to your hand."
                            : "Another exact resolution. The distinction between luck and method is becoming statistically inconvenient.",
                    20L * 190L);
        } else if (resolved == RewardBreakdown.PerformanceGrade.CLEAN && n >= 4 && n % 4 == 0) {
            speak(player, "clean_" + id + "_" + n,
                    "You have stopped solving " + prettify(id) + ". You are recognizing it. There is a difference.", 20L * 180L);
        } else if (resolved == RewardBreakdown.PerformanceGrade.SCRAPED && q == 5) {
            speak(player, "scraped_" + id,
                    "Five openings that technically succeeded and still cost you most of the margin. The ward calls that sufficient. I would not.", 20L * 210L);
        }
    }

    /** Memory belonging to one physical ward, rather than one minigame discipline. */
    public static void onChestMemory(ServerPlayer player, int wins, int losses, int streak, boolean success) {
        if (player == null) return;
        bump(player, success ? "same_chest_win" : "same_chest_loss");
        if (!success && losses == 3) speak(player, "same_chest_loss3",
                "That container has beaten you three times. At this point the object has a history with you, not merely a lock.", 20L * 200L);
        else if (!success && streak == -5) speak(player, "same_chest_loss5",
                "Five consecutive losses to the same physical ward. You are no longer testing it. It is testing how long you persist.", 20L * 240L);
        else if (success && losses >= 2 && streak == 1) speak(player, "same_chest_recovered",
                "You returned to the same ward after repeated losses and resolved it. Persistence is also a behavioural sample.", 20L * 200L);
        else if (success && streak == 4) speak(player, "same_chest_mastered",
                "Four consecutive resolutions on the same object. Its memory of you is becoming less useful to it.", 20L * 220L);
    }

    public static void onAttentionShift(ServerPlayer player, AttentionSystem.Stage before, AttentionSystem.Stage after) {
        int n = bump(player, "attention_shift");
        if (after.ordinal() > before.ordinal()) {
            String line = switch (after) {
                case OBSERVED -> "Something has begun allocating attention to you. I recommend treating that as a measurement, not a mood.";
                case FOLLOWED -> "Observation has become continuity. It is no longer only noticing what you open.";
                case MARKED -> "You are marked now. That word is less metaphorical than I would prefer.";
                case HUNTED -> "The distinction between a ward waiting in a chest and a ward looking for you is becoming unhelpful.";
                case KNOWN -> "It knows enough to predict you. Stop giving it inexpensive examples.";
                default -> null;
            };
            if (line != null) speak(player, "attention_" + after.name().toLowerCase(java.util.Locale.ROOT) + "_" + n, line, 20L * 180L);
        } else if (after == AttentionSystem.Stage.UNNOTICED) {
            speak(player, "attention_release", "The pressure is gone. Do not confuse absence with amnesia.", 20L * 240L);
        }
    }

    /** Death-signing is recorded in silence. The Savant deliberately does not interrupt the hand. */
    public static void rememberDeathCard(ServerPlayer player, ForbiddenBargain card) {
        int n = bump(player, "death_signed");
        if (player != null && player.getServer() != null)
            LockData.get(player.getServer()).setUniqueInt(player.getUUID(), "savant_pending_death_comment", n);
        WardHistory.recordSpecial(player, "SAVANT", "The Savant recorded the death-signature without speaking: " + card.title + ".");
    }

    /** Called later from a low-frequency tick so the Death Hand itself remains silent. */
    public static void flushDeferred(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int pending = data.uniqueInt(id, "savant_pending_death_comment");
        if (pending <= 0) return;
        long signedAt = data.uniqueLong(id, "last_death_signature_at");
        if (signedAt != Long.MIN_VALUE && player.level().getGameTime() - signedAt < 20L * 45L) return;
        data.setUniqueInt(id, "savant_pending_death_comment", 0);
        speak(player, "death_after_" + pending,
                pending == 1
                        ? "I did not speak while you signed that page because the hand was listening for witnesses. It has one now."
                        : "Another death-law. The first changed your risk. Repetition changes your classification.", 20L * 240L);
    }

    public static void onRitual(ServerPlayer player, String key, int purity) {
        int n = bump(player, purity >= 100 ? "pure_ritual" : "scarred_ritual");
        if (purity >= 100 && (n == 3 || n == 6)) speak(player, "pure_ritual_" + n,
                n == 3 ? "Three rites without a breach. Precision is becoming a trait rather than an accident."
                        : "Six clean rites. You are beginning to make ritual look less mystical than bookkeeping.", 20L * 180L);
    }

    public static void onMasterEvent(ServerPlayer player, CardMaster master, boolean accepted) {
        int n = bump(player, (accepted ? "accept_" : "refuse_") + master.id);
        if (!accepted && n == 3) speak(player, "refuse_master_" + master.id,
                "You have refused " + master.title + " three times. Dealers call that information.", 20L * 180L);
    }


    public static void onRivalry(ServerPlayer player, CardMaster source, CardMaster target, int heat) {
        if (player == null || source == null || target == null) return;
        int n = bump(player, "master_crossfire");
        if (heat == 6) speak(player, "rivalry_heat_6_" + n,
                "The card-masters are no longer merely adjusting prices around one another. They are using your hands as correspondence.", 20L * 220L);
        else if (heat >= 10) speak(player, "rivalry_heat_10_" + n,
                "Crossfire is dense enough that a private clause may enter the wrong dealer's hand. At that point I would stop calling the table neutral ground.", 20L * 260L);
    }

    public static void onOccultChain(ServerPlayer player, OccultChainEvents.ChainEvent event, int stage) {
        if (player == null || event == null || stage <= 0) return;
        int n = bump(player, "occult_chain");
        if (stage == 3) speak(player, "chain_mid_" + event.id + "_" + n,
                event.title + " has crossed its first irreversible margin. The remaining steps will not be satisfied by repeating the first ones.", 20L * 220L);
        else if (stage >= event.maxStage) speak(player, "chain_complete_" + event.id + "_" + n,
                event.title + " is closed. Notice that the world did not reward you for discovering a secret. It rewarded you for making several systems agree that the secret had happened.", 20L * 280L);
    }

    public static void onRevision(ServerPlayer player, ForbiddenBargain card, int level) {
        if (player == null || card == null || level <= 0) return;
        int n = bump(player, level >= CardEvolution.MAX_REVISION ? "palimpsest" : "revision");
        String key = "revision_" + card.id + "_" + level;
        String line;
        if (level >= CardEvolution.MAX_REVISION) {
            line = "That clause has been written over often enough to stop being a revision. The older terms are still underneath it. Palimpsests are not upgrades; they are arguments between versions.";
        } else if (level == 2) {
            line = "A second revision of " + card.title + ". Repetition is no longer preserving the bargain. It is teaching the bargain how you use it.";
        } else {
            line = "The wording of " + card.title + " has changed. That usually means the card has begun remembering its signer.";
        }
        speak(player, key + "_" + n, line, 20L * 210L);
    }

    public static void onImprint(ServerPlayer player, PlayerImprint.Trace trace) {
        int n = bump(player, "imprint_" + trace.name().toLowerCase(java.util.Locale.ROOT));
        if (n == 1) speak(player, "imprint_" + trace.name().toLowerCase(java.util.Locale.ROOT),
                "The ledger has enough behaviour to name a trace: " + trace.title + ". I would treat that as an observation, not an identity.", 20L * 200L);
    }

    public static void onSkillProfile(ServerPlayer player, MinigameSkillProfile.Axis strongest, MinigameSkillProfile.Axis weakest) {
        int n = bump(player, "skill_profile");
        if (n == 1) speak(player, "skill_profile_first",
                "Your ward record is no longer only success and failure. " + strongest.label
                        + " is currently strongest; " + weakest.label.toLowerCase(java.util.Locale.ROOT)
                        + " remains the least stable axis. Later seals can now choose how to test that contrast.", 20L * 230L);
        else if (n == 3) speak(player, "skill_profile_third",
                "The profile has moved enough to rule out a temporary streak. The later mechanisms are selecting interruptions from it now.", 20L * 260L);
    }

    public static int total(LockData data, UUID id) { return data.uniqueInt(id, "savant_mem_total"); }

    private static String prettify(String id) {
        if (id == null || id.isBlank()) return "that seal";
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
