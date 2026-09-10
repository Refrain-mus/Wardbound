package dev.marrowseal.wardbound;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Small server-side bridge for Wardbound's event-driven advancement tree.
 *
 * <p>Vanilla JSON criteria cannot observe things such as "a Wardbound minigame
 * actually opened" or "the player signed one of our cards".  Those guide-posts
 * therefore use minecraft:impossible criteria and are awarded here at the same
 * authoritative server events that drive the systems themselves.</p>
 */
public final class WardAdvancements {
    private WardAdvancements() {}

    private static void award(ServerPlayer player, String path) {
        if (player == null || player.getServer() == null || path == null || path.isBlank()) return;
        Advancement advancement = player.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation(Wardbound.MODID, path));
        if (advancement == null) return;
        player.getAdvancements().award(advancement, "done");
    }

    public static void firstWard(ServerPlayer player) {
        award(player, "first_chest");
    }

    public static void enterMinigame(ServerPlayer player, MinigameType game) {
        award(player, "first_minigame");
        if (game != null) award(player, "discipline_" + game.id);
        if (game == MinigameType.CTHULHUS_GAME) award(player, "cthulhus_game_exam");
    }

    public static void minigameResolved(ServerPlayer player, boolean success,
                                        RewardBreakdown.PerformanceGrade grade) {
        if (!success) {
            award(player, "first_failure");
            return;
        }
        award(player, "first_resolution");
        if (grade == null) return;
        switch (grade) {
            case SCRAPED -> award(player, "scraped_win");
            case CLEAN -> award(player, "clean_win");
            case PERFECT -> {
                award(player, "clean_win");
                award(player, "perfect_win");
            }
            default -> { }
        }
    }

    public static void mastery(ServerPlayer player, int tier) {
        if (tier >= 1) award(player, "mastery_i");
        if (tier >= 3) award(player, "mastery_iii");
        if (tier >= 5) award(player, "mastery_v");
    }

    public static void cardSigned(ServerPlayer player) {
        award(player, "first_card");
    }

    public static void cardSigned(ServerPlayer player, ForbiddenBargain card) {
        cardSigned(player);
        if (card == null) return;
        award(player, "card_kind_" + card.kind.name().toLowerCase(java.util.Locale.ROOT));
        if (card == ForbiddenBargain.THE_RED_PEN) award(player, "red_pen");
        if (card == ForbiddenBargain.DEBT_ECHO || card == ForbiddenBargain.WAGER_ECHO || card == ForbiddenBargain.SCAR_ECHO
                || card == ForbiddenBargain.REMEDY_ECHO || card == ForbiddenBargain.CONTRACT_ECHO || card == ForbiddenBargain.RITUAL_ECHO
                || card == ForbiddenBargain.COVENANT_ECHO || card == ForbiddenBargain.MASTER_ECHO || card == ForbiddenBargain.EPIC_ECHO
                || card == ForbiddenBargain.UNIQUE_ECHO || card == ForbiddenBargain.CURSE_ECHO || card == ForbiddenBargain.DEATH_ECHO)
            award(player, "echo_signed");
        if (card.id >= 403 && card.id <= 407) award(player, "fourth_wave_law");
        if (card.id >= 408 && card.id <= 437) award(player, "fifth_wave_law");
        if (card.id >= 438 && card.id <= 627) award(player, "apothic_attribute_law");
    }

    public static void ledgerOpened(ServerPlayer player) {
        award(player, "witness_ledger");
    }

    public static void anomalySeen(ServerPlayer player) {
        award(player, "anomaly_seen");
    }

    public static void anomalySeen(ServerPlayer player, String id) {
        anomalySeen(player);
        if (id == null || id.isBlank()) return;
        String clean = id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        award(player, "anomaly_" + clean);
    }

    public static void dealerSeen(ServerPlayer player, CardMaster master) {
        if (player == null || player.getServer() == null || master == null) return;
        LockData data = LockData.get(player.getServer());
        if (CardMaster.phaseActive(data, player.getUUID())) award(player, "dealer_" + master.id);
    }

    public static void masterSeen(ServerPlayer player, MasterSignature signature) {
        if (player == null || player.getServer() == null || signature == null) return;
        LockData data = LockData.get(player.getServer());
        if (CardMaster.phaseActive(data, player.getUUID()))
            award(player, "master_signature_" + signature.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void imprintSeen(ServerPlayer player, PlayerImprint.Trace trace) {
        if (player == null || player.getServer() == null || trace == null || trace == PlayerImprint.Trace.UNREAD) return;
        LockData data = LockData.get(player.getServer());
        if (CardMaster.phaseActive(data, player.getUUID()))
            award(player, "imprint_" + trace.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void attentionSeen(ServerPlayer player, AttentionSystem.Stage stage) {
        if (stage != null && stage != AttentionSystem.Stage.UNNOTICED)
            award(player, "attention_" + stage.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void corruptionSeen(ServerPlayer player) { award(player, "curse_corruption"); }
    public static void occultChainSeen(ServerPlayer player) { award(player, "occult_chain"); }
    public static void guardianSeen(ServerPlayer player) { award(player, "guardian_catalogued"); }
    public static void silasFragmentSeen(ServerPlayer player) { award(player, "silas_fragment_read"); }
    public static void cardRevisionSeen(ServerPlayer player) { award(player, "card_revision"); }
    public static void ancientSmithSeen(ServerPlayer player) { award(player, "ancient_smith"); }
    public static void nhalSulAwakened(ServerPlayer player) { award(player, "nhal_sul_awakened"); }
    public static void cthulhuHeadDefeated(ServerPlayer player) { award(player, "cthulhu_head_defeated"); }
    public static void maestroRevealed(ServerPlayer player) { award(player, "maestro_revealed"); }
    public static void maestroDefeated(ServerPlayer player) { award(player, "defeat_maestro"); }


    public static void conjunctionSeen(ServerPlayer player) {
        award(player, "conjunction_seen");
    }

    public static void makerKnown(ServerPlayer player, int familiarity) {
        if (player == null || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        if (!CardMaster.phaseActive(data, player.getUUID())) return;
        if (familiarity >= 3) award(player, "maker_familiar");
    }

    public static void hybridCleared(ServerPlayer player) {
        award(player, "hybrid_cleared");
    }

    public static void measureVerdictSeen(ServerPlayer player) { award(player, "house_measure_verdict"); }
    public static void measureCompleted(ServerPlayer player) { award(player, "house_measure_complete"); }
    public static void measureMastered(ServerPlayer player) { award(player, "house_measure_mastery"); }
    public static void mulliganBanked(ServerPlayer player) { award(player, "mulligan_banked"); }
    public static void mulliganSpent(ServerPlayer player) { award(player, "mulligan_spent"); }

    public static void ecologyHeat(ServerPlayer player, int heat) {
        if (heat >= 8) award(player, "ecology_warm");
        if (heat >= 16) award(player, "ecology_watched");
        if (heat >= 28) award(player, "ecology_answered");
    }

    /**
     * Progression guide-posts follow the live config rather than hard-coding
     * default ward counts.  Pack authors may move an unlock and the advancement
     * will still appear when that system actually becomes eligible.
     */
    public static void progression(ServerPlayer player, int resolved) {
        if (resolved >= WardConfig.normalCardsAfterBeaten) award(player, "unlock_cards");
        if (resolved >= WardConfig.afflictionAfterBeaten) award(player, "unlock_afflictions");
        if (resolved >= WardConfig.fieldCardAfterBeaten) award(player, "unlock_field_cards");
        if (resolved >= WardConfig.chainAfterBeaten) award(player, "unlock_chains");
        if (player != null && player.getServer() != null
                && CardMaster.phaseActive(LockData.get(player.getServer()), player.getUUID())
                && resolved >= WardConfig.masterCardsAfterBeaten) {
            award(player, "root_house");
            award(player, "unlock_master_cards");
        }
        if (resolved >= WardConfig.contractCardsAfterBeaten) award(player, "unlock_contracts");
        if (resolved >= WardConfig.possessedAfterBeaten) award(player, "unlock_possessed");
        if (resolved >= WardConfig.curseCardsAfterBeaten) award(player, "unlock_curses");
        if (player != null && player.getServer() != null
                && WardMeasureSystem.unlocked(LockData.get(player.getServer()), player.getUUID()))
            award(player, "unlock_house_measure");
        if (resolved >= WardConfig.deceptionUnlockAfter) award(player, "unlock_deception");
        if (resolved >= WardConfig.unsignedAfterBeaten) award(player, "unlock_unsigned");
        if (resolved >= WardConfig.ritualCardsAfterBeaten) award(player, "unlock_rituals");
        if (resolved >= WardConfig.bonusFreshRollAfterBeaten) award(player, "unlock_bonus_offer");
        if (resolved >= WardConfig.hybridUnlockAfter) award(player, "unlock_hybrids");
        if (resolved >= WardConfig.mutationTier1AfterBeaten) award(player, "unlock_revision_i");
        if (resolved >= WardConfig.epicCardsAfterBeaten) award(player, "unlock_epics");
        if (resolved >= WardConfig.covenantCardsAfterBeaten) award(player, "unlock_covenants");
        if (resolved >= WardConfig.uniqueCardsAfterBeaten) award(player, "unlock_uniques");
        if (resolved >= WardConfig.mutationTier2AfterBeaten) award(player, "unlock_revision_ii");
        if (resolved >= WardConfig.eldritchAfterBeaten) award(player, "unlock_eldritch");
        if (resolved >= WardConfig.eyeAfterBeaten) award(player, "unlock_savant");
        if (resolved >= WardConfig.mutationTier3AfterBeaten) award(player, "unlock_revision_iii");
        if (resolved >= WardConfig.deathCardsAfterBeaten) award(player, "unlock_death");
        if (resolved >= WardConfig.cthulhuAfterBeaten) award(player, "unlock_cthulhus_game");
    }
}
