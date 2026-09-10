package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Hidden thematic lineage behind progressive cards. The counters intentionally
 * survive the individual card timer: later Master encounters can read what the
 * player repeatedly chose, revised and turned into Palimpsests.
 */
public final class CardLineage {
    private CardLineage() {}

    public static CardMaster affinity(ForbiddenBargain card) {
        if (card == null) return null;
        return switch (card) {
            case BLOODWELL_REFLEX, IRON_PULSE -> CardMaster.ASHEN_CURATOR;
            case LONG_HAND, HARVEST_SHARE -> CardMaster.MOURNING_NOTARY;
            case BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST -> CardMaster.PALE_GAMBLER;

            case PINHOLE_DOCTRINE, HIGH_ARC_CLAUSE, FIRST_VOLLEY, STILL_HAND,
                    OPENING_CUT, LONE_DUEL, STILL_POINT, ARROW_LEDGER, BLAST_RECEIPT,
                    LONGER_HAND, STONE_DIVIDEND, TIMBER_SHARE, MASON_TITHE, FORTUNE_MARGIN,
                    SWIFT_TOOL, HARVEST_MEMORY -> CardMaster.MOURNING_NOTARY;

            case LAST_BOLT, SOLITARY_MARK, RED_KNUCKLE, EXECUTIONERS_MARGIN, SECOND_WOUND,
                    ASH_PLATE, LAST_PLATE, CLOSE_SEAL, CROWD_SHELTER, SOLITARY_WARD,
                    FALLING_INK, FIRE_MARGIN, NAME_WITHOUT_RECOIL, GRAVE_DIVIDEND,
                    SCAVENGERS_NAME, CANDLEWORK, QUIET_LEDGER -> CardMaster.ASHEN_CURATOR;

            case POINT_BLANK_RECEIPT, UPWARD_INTEREST, RUNNING_SIGHT, PIERCED_CROWD,
                    PRESSED_BLADE, HIGH_GROUND, LOW_ROAD, RUNNING_HAND,
                    LONG_STRIDE, SPRINT_CLAUSE, EMPTY_ROAD, HUNTED_ROAD, OPEN_SKY_FOOTNOTE,
                    DEEP_ROAD, FEATHERED_DEBT, CLIMBERS_MARGIN, DUELISTS_REACH -> CardMaster.PALE_GAMBLER;
            default -> null;
        };
    }

    public static boolean preferredBy(CardMaster master, ForbiddenBargain card) {
        return master != null && affinity(card) == master;
    }

    public static void onSigned(ServerPlayer player, LockData data, ForbiddenBargain card, CardMaster dealer) {
        if (player == null || data == null || card == null) return;
        CardMaster master = affinity(card);
        if (master == null) return;
        UUID id = player.getUUID();
        int revision = CardEvolution.activeVariant(data, id, card);
        int delta = 1 + (revision > 0 ? 1 : 0) + (revision >= CardEvolution.MAX_REVISION ? 2 : 0);
        if (dealer == master) delta++;
        String key = resonanceKey(master);
        int before = Math.max(0, data.uniqueInt(id, key));
        int after = Math.min(9999, before + delta);
        data.setUniqueInt(id, key, after);

        if (!data.hasUnique(id, "card_lineage_seen_" + card.id)) {
            data.setUnique(id, "card_lineage_seen_" + card.id, true);
            ExpandedProgressionCardEffects.Family family = ExpandedProgressionCardEffects.family(card);
            if (family != null) {
                String familyKey = "card_lineage_family_" + family.name().toLowerCase(java.util.Locale.ROOT);
                data.setUniqueInt(id, familyKey, data.uniqueInt(id, familyKey) + 1);
            }
        }

        int milestone = after >= 60 ? 60 : after >= 30 ? 30 : after >= 12 ? 12 : 0;
        // Lineage exists from the beginning because ordinary card choices should
        // matter later, but it must not spoil a Master before that identity has
        // been earned. Once known, the next matching signature can surface the
        // highest resonance milestone accumulated in silence.
        String announcedKey = "card_lineage_announced_" + master.id;
        int announced = Math.max(0, data.uniqueInt(id, announcedKey));
        if (milestone > 0 && master.known(data, id) && announced < milestone) {
            data.setUniqueInt(id, announcedKey, milestone);
            WardHistory.recordSpecial(player, "WARD", master.title + " resonance " + milestone
                    + " // repeated card choices have become a readable pattern.");
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                    "card_lineage_" + master.id + "_" + milestone,
                    "The " + master.title.replace("The ", "") + " is no longer reacting only to individual signatures. It can read the pattern between them.",
                    20L * 180L);
        }
    }

    public static int resonance(LockData data, UUID id, CardMaster master) {
        if (data == null || id == null || master == null) return 0;
        return Math.max(0, data.uniqueInt(id, resonanceKey(master)));
    }

    /** Future boss hooks can use this without depending on how the counters were earned. */
    public static int bossReadiness(LockData data, UUID id, CardMaster master) {
        int score = resonance(data, id, master);
        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            if (affinity(card) != master) continue;
            int revision = CardEvolution.activeVariant(data, id, card);
            if (revision > 0) score += 2;
            if (revision >= CardEvolution.MAX_REVISION) score += 5;
        }
        return score;
    }

    public static String summary(LockData data, UUID id) {
        return "resonance · Curator " + resonance(data,id,CardMaster.ASHEN_CURATOR)
                + " · Notary " + resonance(data,id,CardMaster.MOURNING_NOTARY)
                + " · Gambler " + resonance(data,id,CardMaster.PALE_GAMBLER)
                + " · lineage breadth " + familyBreadth(data,id) + "/50";
    }

    private static int familyBreadth(LockData data, UUID id) {
        int total = 0;
        for (ForbiddenBargain card : ForbiddenBargain.values())
            if (ExpandedProgressionCardEffects.isExpanded(card) && data.hasUnique(id, "card_lineage_seen_" + card.id)) total++;
        return total;
    }

    private static String resonanceKey(CardMaster master) {
        return "master_card_resonance_" + master.id;
    }
}
