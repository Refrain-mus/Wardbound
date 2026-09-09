package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Persistent, deliberately soft memory for card-family use.
 *
 * Heat is short-term: it cools when real new hands are dealt. Familiarity is
 * long-term: it records what the player repeatedly chooses.  Offer weighting
 * uses both to make the House answer a strategy without turning a favourite
 * family into a punishment or a hard class lock.
 */
public final class CardEcology {
    private CardEcology() {}

    public static final int MAX_HEAT = 60;

    public enum Family {
        BALLISTICS("Ballistics", "projectile, distance and firing clauses"),
        EDGEWORK("Edgework", "melee pressure and execution clauses"),
        BULWARK("Bulwark", "guard, resistance and survival clauses"),
        PILGRIMAGE("Pilgrimage", "movement, terrain and travel clauses"),
        INDUSTRY("Industry", "mining, harvest and material clauses"),
        WAGER("Wager", "ordinary spendable risk/reward laws"),
        DEBT("Debt", "deferred payment and liability"),
        SCAR("Scar", "persistent marks carried in ordinary play"),
        REMEDY("Remedy", "release, repair and burden-control clauses"),
        CONTRACT("Contract", "measured obligations with explicit goals"),
        RITUAL("Ritual", "condition-bound authored work"),
        COVENANT("Covenant", "long-form hostile obligations"),
        CURSE("Curse", "maturing hostile clauses"),
        DEATH("Death", "death-law and resonance clauses"),
        EPIC("Epic", "rare high-order laws"),
        UNIQUE("Unique", "singular world laws"),
        MASTER("Master", "dealer-authored private law"),
        META("Meta", "hand editing, Echo and non-law card machinery");

        public final String title;
        public final String detail;
        Family(String title, String detail) { this.title = title; this.detail = detail; }
    }

    public static Family family(ForbiddenBargain card) {
        if (card == null) return Family.META;
        ExpandedProgressionCardEffects.Family expanded = ExpandedProgressionCardEffects.family(card);
        if (expanded != null) return switch (expanded) {
            case BALLISTICS -> Family.BALLISTICS;
            case EDGEWORK -> Family.EDGEWORK;
            case BULWARK -> Family.BULWARK;
            case PILGRIMAGE -> Family.PILGRIMAGE;
            case INDUSTRY -> Family.INDUSTRY;
        };
        if (CardEchoSystem.isPrimer(card) || card == ForbiddenBargain.THE_RED_PEN || card.kind == ForbiddenBargain.Kind.REFRESH)
            return Family.META;
        return switch (card.kind) {
            case WAGER -> Family.WAGER;
            case DEBT -> Family.DEBT;
            case SCAR -> Family.SCAR;
            case REMEDY -> Family.REMEDY;
            case CONTRACT -> Family.CONTRACT;
            case RITUAL -> Family.RITUAL;
            case COVENANT -> Family.COVENANT;
            case CURSE -> Family.CURSE;
            case DEATH -> Family.DEATH;
            case EPIC -> Family.EPIC;
            case UNIQUE -> Family.UNIQUE;
            case MASTER -> Family.MASTER;
            case REFRESH -> Family.META;
        };
    }

    private static String key(String stem, Family family) {
        return "ecology_" + stem + "_" + family.name().toLowerCase(Locale.ROOT);
    }

    public static int heat(LockData data, UUID id, Family family) {
        if (data == null || id == null || family == null) return 0;
        return Mth.clamp(data.uniqueInt(id, key("heat", family)), 0, MAX_HEAT);
    }

    public static int familiarity(LockData data, UUID id, Family family) {
        if (data == null || id == null || family == null) return 0;
        return Math.max(0, data.uniqueInt(id, key("signed", family)));
    }

    public static int exposure(LockData data, UUID id, Family family) {
        if (data == null || id == null || family == null) return 0;
        return Math.max(0, data.uniqueInt(id, key("offered", family)));
    }

    /** Called once for a newly dealt real hand; redraws/Cuts deliberately do not cool the ecology. */
    public static void onHandDealt(LockData data, UUID id, Collection<ForbiddenBargain> offers) {
        if (data == null || id == null || offers == null || offers.isEmpty()) return;
        data.setUniqueInt(id, "ecology_hands", data.uniqueInt(id, "ecology_hands") + 1);
        for (Family family : Family.values()) {
            int before = heat(data, id, family);
            if (before > 0) data.setUniqueInt(id, key("heat", family), Math.max(0, before - 1));
        }
        EnumSet<Family> seen = EnumSet.noneOf(Family.class);
        for (ForbiddenBargain card : offers) seen.add(family(card));
        for (Family family : seen)
            data.setUniqueInt(id, key("offered", family), exposure(data, id, family) + 1);
    }

    /** Records a voluntary signed card after branch/revision selection has become authoritative. */
    public static void onSigned(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || card == null || CardEchoSystem.isPrimer(card)
                || card == ForbiddenBargain.THE_RED_PEN || card.kind == ForbiddenBargain.Kind.REFRESH) return;
        UUID id = player.getUUID();
        Family family = family(card);
        int familiarityBefore = familiarity(data, id, family);
        data.setUniqueInt(id, key("signed", family), familiarityBefore + 1);
        data.setUniqueInt(id, "ecology_signed_total", data.uniqueInt(id, "ecology_signed_total") + 1);

        int addition = switch (card.kind) {
            case CURSE, DEATH, COVENANT -> 7;
            case EPIC, UNIQUE, MASTER -> 6;
            case DEBT, SCAR, CONTRACT, RITUAL -> 6;
            default -> 5;
        };
        int previousOrdinal = data.uniqueInt(id, "ecology_last_family_index") - 1;
        int streak = previousOrdinal == family.ordinal() ? data.uniqueInt(id, "ecology_family_streak") + 1 : 1;
        data.setUniqueInt(id, "ecology_last_family_index", family.ordinal() + 1);
        data.setUniqueInt(id, "ecology_family_streak", streak);
        addition += Math.min(3, Math.max(0, streak - 1));

        int before = heat(data, id, family);
        int after = Mth.clamp(before + addition, 0, MAX_HEAT);
        data.setUniqueInt(id, key("heat", family), after);

        // Milestones are informational. They never apply raw stat penalties.
        int crossed = after >= 28 && before < 28 ? 28 : after >= 16 && before < 16 ? 16 : after >= 8 && before < 8 ? 8 : 0;
        if (crossed > 0) {
            WardAdvancements.ecologyHeat(player, after);
            String response = crossed >= 28
                    ? "The House is now answering this family with counter-families and unfamiliar ink."
                    : crossed >= 16
                    ? "The House has begun to answer the pattern instead of merely repeating it."
                    : "Repeated signatures have made this family legible to the House.";
            WardHistory.recordSpecial(player, "CARD", "Card ecology // " + family.title + " heat " + after + "/" + MAX_HEAT + " · " + response);
        }
    }

    /**
     * Weight used only inside an already-legal rarity/shelf pool. It cannot make
     * a locked card legal and cannot upgrade a card's rarity. Low heat produces
     * a mild echo of player preference; high heat introduces counterplay and
     * novelty instead of simply deleting the favourite family.
     */
    public static float offerWeight(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return 1.0f;
        Family own = family(card);
        int ownHeat = heat(data, id, own);
        float weight;
        if (ownHeat <= 10) weight = 1.0f + ownHeat * 0.018f;
        else if (ownHeat <= 18) weight = 1.16f - (ownHeat - 10) * 0.008f;
        else weight = Math.max(0.68f, 1.10f - (ownHeat - 18) * 0.018f);

        int familiar = familiarity(data, id, own);
        // Familiarity is the long-memory trace: when current Heat is calm it
        // gives a very small echo of the player's established style. Heat still
        // has priority once a family becomes watched/answered.
        if (ownHeat < 16 && familiar > 0)
            weight *= 1.0f + Math.min(12, familiar) * 0.006f;

        Family dominant = dominantHeatFamily(data, id);
        int dominantHeat = dominant == null ? 0 : heat(data, id, dominant);
        if (dominant != null && dominantHeat >= 16 && isResponse(dominant, own)) {
            float response = dominantHeat >= 36 ? 1.55f : dominantHeat >= 28 ? 1.38f : 1.22f;
            weight *= response;
        }

        if (familiar >= 10 && ownHeat >= 20) weight *= Math.max(0.86f, 1.0f - (familiar - 9) * 0.008f);
        weight *= ApothicThirdWaveMechanics.jokeOfferMultiplier(card);
        return ApothicThirdWaveMechanics.isJoke(card) ? Mth.clamp(weight, 0.03f, 0.18f) : Mth.clamp(weight, 0.55f, 1.70f);
    }

    public static void weightedShuffle(List<ForbiddenBargain> pool, LockData data, UUID id, Random random) {
        if (pool == null || pool.size() < 2 || random == null) return;
        List<WeightedCard> scored = new ArrayList<>(pool.size());
        for (ForbiddenBargain card : pool) {
            double u = Math.max(1.0e-9, random.nextDouble());
            double priority = -Math.log(u) / Math.max(0.05, offerWeight(data, id, card));
            scored.add(new WeightedCard(card, priority));
        }
        scored.sort(Comparator.comparingDouble(WeightedCard::priority));
        pool.clear();
        for (WeightedCard entry : scored) pool.add(entry.card());
    }

    public static ForbiddenBargain weightedPick(List<ForbiddenBargain> pool, LockData data, UUID id, Random random) {
        if (pool == null || pool.isEmpty()) return null;
        if (pool.size() == 1) return pool.get(0);
        double total = 0.0;
        for (ForbiddenBargain card : pool) total += offerWeight(data, id, card);
        double roll = random.nextDouble() * total;
        for (ForbiddenBargain card : pool) {
            roll -= offerWeight(data, id, card);
            if (roll <= 0.0) return card;
        }
        return pool.get(pool.size() - 1);
    }

    public static Family dominantHeatFamily(LockData data, UUID id) {
        Family best = null;
        int value = 0;
        for (Family family : Family.values()) {
            int h = heat(data, id, family);
            if (h > value) { value = h; best = family; }
        }
        return value <= 0 ? null : best;
    }

    public static Family dominantFamiliarityFamily(LockData data, UUID id) {
        Family best = null;
        int value = 0;
        for (Family family : Family.values()) {
            int n = familiarity(data, id, family);
            if (n > value) { value = n; best = family; }
        }
        return value <= 0 ? null : best;
    }

    public static Family responseFamily(LockData data, UUID id) {
        Family hot = dominantHeatFamily(data, id);
        if (hot == null || heat(data, id, hot) < 16) return null;
        for (Family candidate : Family.values()) if (isResponse(hot, candidate)) return candidate;
        return null;
    }

    public static String heatName(int heat) {
        if (heat >= 36) return "Contested";
        if (heat >= 28) return "Answered";
        if (heat >= 16) return "Watched";
        if (heat >= 8) return "Warm";
        return heat > 0 ? "Trace" : "Quiet";
    }

    public static String summary(LockData data, UUID id) {
        Family hot = dominantHeatFamily(data, id);
        Family familiar = dominantFamiliarityFamily(data, id);
        if (hot == null && familiar == null) return "No stable family pattern yet.";
        StringBuilder out = new StringBuilder();
        if (hot != null) out.append("heat ").append(hot.title).append(" ").append(heat(data, id, hot)).append("/").append(MAX_HEAT)
                .append(" ").append(heatName(heat(data, id, hot)));
        if (familiar != null) {
            if (out.length() > 0) out.append(" · ");
            out.append("familiarity ").append(familiar.title).append(" ").append(familiarity(data, id, familiar));
        }
        Family response = responseFamily(data, id);
        if (response != null) out.append(" · House response: ").append(response.title);
        return out.toString();
    }

    public static List<Family> hottest(LockData data, UUID id, int count) {
        List<Family> families = new ArrayList<>(List.of(Family.values()));
        families.sort(Comparator.<Family>comparingInt(f -> heat(data, id, f)).reversed()
                .thenComparing(Comparator.<Family>comparingInt(f -> familiarity(data, id, f)).reversed()));
        families.removeIf(f -> heat(data, id, f) <= 0 && familiarity(data, id, f) <= 0 && exposure(data, id, f) <= 0);
        if (families.size() > count) return List.copyOf(families.subList(0, count));
        return List.copyOf(families);
    }

    public static java.util.List<Family> responsesTo(Family hot) {
        if (hot == null) return java.util.List.of();
        java.util.List<Family> out = new java.util.ArrayList<>();
        for (Family candidate : Family.values()) if (isResponse(hot, candidate)) out.add(candidate);
        return java.util.List.copyOf(out);
    }

    public static boolean respondsTo(Family hot, Family candidate) {
        return isResponse(hot, candidate);
    }

    private static boolean isResponse(Family hot, Family candidate) {
        if (hot == null || candidate == null || hot == candidate) return false;
        return switch (hot) {
            case CURSE, DEATH, COVENANT, SCAR, DEBT -> candidate == Family.REMEDY || candidate == Family.BULWARK;
            case WAGER, EPIC, UNIQUE -> candidate == Family.DEBT || candidate == Family.CONTRACT;
            case CONTRACT, RITUAL -> candidate == Family.WAGER || candidate == Family.PILGRIMAGE;
            case BALLISTICS -> candidate == Family.BULWARK || candidate == Family.PILGRIMAGE;
            case EDGEWORK -> candidate == Family.BULWARK || candidate == Family.BALLISTICS;
            case BULWARK -> candidate == Family.EDGEWORK || candidate == Family.CURSE;
            case PILGRIMAGE -> candidate == Family.INDUSTRY || candidate == Family.BALLISTICS;
            case INDUSTRY -> candidate == Family.PILGRIMAGE || candidate == Family.WAGER;
            case REMEDY -> candidate == Family.WAGER || candidate == Family.CONTRACT;
            case MASTER -> candidate == Family.WAGER || candidate == Family.CONTRACT;
            case META -> false;
        };
    }

    private record WeightedCard(ForbiddenBargain card, double priority) {}
}
