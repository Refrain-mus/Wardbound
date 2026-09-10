package dev.marrowseal.wardbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Card-only masters that do not author physical wards. Their identities unlock through field-card play. */
public enum CardMaster {
    // The three Masters are peers, not a tutorial ladder. They all enter the
    // late-game card layer together so early Wardbound reads as wards + cards.
    ASHEN_CURATOR("ashen_curator", "The Ashen Curator", MasterSignature.VEILED, 120, 160),
    MOURNING_NOTARY("mourning_notary", "The Mourning Notary", MasterSignature.EXACTING, 120, 160),
    PALE_GAMBLER("pale_gambler", "The Pale Gambler", MasterSignature.CROOKED, 120, 160);

    public final String id;
    public final String title;
    public final MasterSignature visualSignature;
    public final int entersPoolAfter;
    public final int revealAfter;

    CardMaster(String id, String title, MasterSignature visualSignature, int entersPoolAfter, int revealAfter) {
        this.id = id;
        this.title = title;
        this.visualSignature = visualSignature;
        this.entersPoolAfter = entersPoolAfter;
        this.revealAfter = revealAfter;
    }

    public boolean known(LockData data, UUID player) {
        return data.hasUnique(player, "dealer_known_" + id);
    }

    public int relation(LockData data, UUID player) {
        return data.uniqueInt(player, "dealer_relation_" + id);
    }

    public int addRelation(LockData data, UUID player, int delta) {
        int before = relation(data, player);
        int next = Mth.clamp(before + delta, -12, 20);
        data.setUniqueInt(player, "dealer_relation_" + id, next);
        return next;
    }

    /** 0 hostile, 1 cold, 2 unread, 3 interested, 4 familiar, 5 favored. */
    public int relationTier(LockData data, UUID player) {
        int relation = relation(data, player);
        if (relation <= -8) return 0;
        if (relation <= -3) return 1;
        if (relation < 3) return 2;
        if (relation < 8) return 3;
        if (relation < 14) return 4;
        return 5;
    }

    public String mood(LockData data, UUID player) {
        return switch (relationTier(data, player)) {
            case 0 -> "hostile";
            case 1 -> "cold";
            case 3 -> "interested";
            case 4 -> "familiar";
            case 5 -> "favored";
            default -> "unread";
        };
    }

    public String displayName(LockData data, UUID player) {
        return known(data, player) ? title : "???";
    }

    public String presenterLabel(LockData data, UUID player) {
        if (!known(data, player)) return "Unread Hand";
        int relation = relation(data, player);
        return title + " · " + mood(data, player) + " · favor " + (relation >= 0 ? "+" : "") + relation + "/20";
    }

    public boolean prefers(ForbiddenBargain card) {
        if (card == null) return false;
        CardMaster lineage = CardLineage.affinity(card);
        if (lineage != null) return lineage == this;
        return switch (this) {
            case ASHEN_CURATOR -> card.kind == ForbiddenBargain.Kind.SCAR || card.kind == ForbiddenBargain.Kind.REMEDY
                    || card.kind == ForbiddenBargain.Kind.DEBT || card.kind == ForbiddenBargain.Kind.CURSE
                    || card.kind == ForbiddenBargain.Kind.COVENANT;
            case MOURNING_NOTARY -> card.kind == ForbiddenBargain.Kind.CONTRACT || card.kind == ForbiddenBargain.Kind.RITUAL
                    || card.kind == ForbiddenBargain.Kind.COVENANT || card.kind == ForbiddenBargain.Kind.REMEDY;
            case PALE_GAMBLER -> card.kind == ForbiddenBargain.Kind.WAGER || card.kind == ForbiddenBargain.Kind.CURSE
                    || card.kind == ForbiddenBargain.Kind.EPIC || card.kind == ForbiddenBargain.Kind.UNIQUE
                    || card.kind == ForbiddenBargain.Kind.DEATH;
        };
    }

    /** Relation reward is larger when the player signs the kind this dealer actually wanted to see. */
    public int acceptanceRelationDelta(ForbiddenBargain card) {
        if (card == null) return 1;
        if (card == signatureCard()) return 3;
        if (!prefers(card)) return 1;
        return switch (this) {
            case ASHEN_CURATOR -> card.isRemedy() || card.isCurse() ? 3 : 2;
            case MOURNING_NOTARY -> card.isObjectiveCard() ? 3 : 2;
            case PALE_GAMBLER -> card.isDeath() || card.isUnique() || card.isEpic() ? 3 : 2;
        };
    }

    public int grudge(LockData data, UUID player) {
        return Mth.clamp(data.uniqueInt(player, "dealer_grudge_" + id), 0, 3);
    }

    public void addGrudge(LockData data, UUID player, int delta) {
        data.setUniqueInt(player, "dealer_grudge_" + id, Mth.clamp(grudge(data, player) + delta, 0, 3));
    }

    /** A signature clears the table; an on-theme acceptance softens one remembered refusal. */
    public void settleGrudge(LockData data, UUID player, ForbiddenBargain card) {
        if (card == signatureCard()) data.setUniqueInt(player, "dealer_grudge_" + id, 0);
        else if (prefers(card) && grudge(data, player) > 0) addGrudge(data, player, -1);
    }

    /** Refusing a favored master carries more relational weight because the offer was already unusually generous. */
    public int refusalRelationDelta(LockData data, UUID player) {
        return relationTier(data, player) >= 5 ? -2 : -1;
    }

    /** Known, trusted dealers reveal their private law more reliably; hostile dealers almost never do. */
    public float signatureChance(LockData data, UUID player) {
        if (!known(data, player)) return 0f;
        int relation = relation(data, player);
        float base;
        if (relation <= -8) base = 0.015f;
        else if (relation <= -3) base = 0.04f;
        else base = Math.min(0.62f, 0.10f + Math.max(0, relation) * 0.032f);
        // A remembered refusal does not erase favor, but it makes the dealer less willing
        // to put the one irreplaceable page on the table until the grudge is settled.
        float grudgeScale = Math.max(0.42f, 1.0f - grudge(data, player) * 0.18f);
        float story = MasterStory.signatureBonus(data, player, this);
        return Mth.clamp(base * grudgeScale + story, 0f, 0.72f);
    }

    /** Number of cards a field hand tries to expose before dealer-specific composition rules apply. */
    public int targetHandSize(LockData data, UUID player, Random random) {
        int tier = relationTier(data, player);
        int size;
        if (tier <= 0) size = 1;
        else if (tier == 1) size = random.nextFloat() < 0.25f ? 2 : 1;
        else if (tier <= 3) size = 2;
        else if (tier == 4) size = random.nextFloat() < 0.48f ? 3 : 2;
        else size = random.nextFloat() < 0.78f ? 3 : 2;
        // Three remembered refusals can narrow an otherwise generous hand by one card.
        // A fully favored master still refuses to become a one-card vending machine.
        if (grudge(data, player) >= 3 && tier < 5) size = Math.max(1, size - 1);
        size += MasterStory.handBonus(data, player, this);
        return Mth.clamp(size, 1, 4);
    }

    /** One private law can only appear in field hands from its matching card master. */
    public ForbiddenBargain signatureCard() {
        return switch (this) {
            case ASHEN_CURATOR -> ForbiddenBargain.VEILED_PRIVATE;
            case MOURNING_NOTARY -> ForbiddenBargain.EXACTING_PRIVATE;
            case PALE_GAMBLER -> ForbiddenBargain.CROOKED_PRIVATE;
        };
    }

    public static CardMaster forSignature(MasterSignature signature) {
        if (signature == null) return ASHEN_CURATOR;
        for (CardMaster master : values()) if (master.visualSignature == signature) return master;
        return ASHEN_CURATOR;
    }

    public long visualSeed() {
        for (long seed = 1; seed < 10000; seed++) {
            if (MasterSignature.ofSeed(seed) == visualSignature) return seed;
        }
        return 1L;
    }

    public static CardMaster byId(String id) {
        for (CardMaster master : values()) if (master.id.equals(id)) return master;
        return ASHEN_CURATOR;
    }

    /** True only after at least one Master has actually entered the field-card pool. */
    public static boolean anyEligible(int opened) {
        for (CardMaster master : values()) if (opened >= master.entersPoolAfter) return true;
        return false;
    }

    /**
     * Single authoritative gate for the late dealer/Master layer.
     *
     * <p>Field cards are intentionally farmable once discovered, so their counter
     * cannot be the only key to the late game. Requiring the matching resolved-ward
     * shelf prevents a mob farm from revealing Curator/Notary/Gambler while the
     * player's actual Wardbound progression is still in its onboarding chapters.</p>
     */
    public static boolean phaseActive(LockData data, UUID player) {
        return phaseActiveAt(data, player, data == null || player == null ? 0 : data.uniqueInt(player, "field_cards_opened"));
    }

    /** Prospective variant used while the next field card is being opened. */
    public static boolean phaseActiveAt(LockData data, UUID player, int opened) {
        return data != null && player != null
                && anyEligible(Math.max(0, opened))
                && data.totalBeaten(player) >= WardConfig.masterCardsAfterBeaten;
    }

    /** Weighted to avoid the same dealer dominating every field drop while still respecting relationships. */
    public static CardMaster choose(LockData data, UUID player, RandomSource random) {
        return chooseAt(data, player, random, data.uniqueInt(player, "field_cards_opened"));
    }

    /** Selection against an explicit opened-card shelf; avoids a one-card activation lag. */
    public static CardMaster chooseAt(LockData data, UUID player, RandomSource random, int opened) {
        List<CardMaster> pool = new ArrayList<>();
        int totalWeight = 0;
        int last = data.uniqueInt(player, "last_card_master");
        int streak = data.uniqueInt(player, "last_card_master_streak");
        int eligibleCount = 0;
        for (CardMaster master : values()) if (opened >= master.entersPoolAfter) eligibleCount++;
        List<Integer> weights = new ArrayList<>();
        for (CardMaster master : values()) {
            if (opened < master.entersPoolAfter) continue;
            int weight = 100 + Mth.clamp(master.relation(data, player) * 3, -30, 45);
            if (eligibleCount > 1 && last == master.ordinal() + 1) {
                float fatigue = streak >= 3 ? 0.22f : streak == 2 ? 0.36f : 0.52f;
                weight = Math.max(18, Math.round(weight * fatigue));
            }
            pool.add(master);
            weights.add(weight);
            totalWeight += weight;
        }
        if (pool.isEmpty()) return ASHEN_CURATOR;
        int roll = random.nextInt(Math.max(1, totalWeight));
        CardMaster chosen = pool.get(0);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) { chosen = pool.get(i); break; }
        }
        int chosenKey = chosen.ordinal() + 1;
        data.setUniqueInt(player, "last_card_master_streak", last == chosenKey ? Math.min(4, streak + 1) : 1);
        data.setUniqueInt(player, "last_card_master", chosenKey);
        return chosen;
    }

    public String acceptanceLine(LockData data, UUID player, ForbiddenBargain card, RandomSource random) {
        String speaker = displayName(data, player);
        if (card == signatureCard()) {
            return speaker + ": " + switch (this) {
                case ASHEN_CURATOR -> "Now you are carrying a page I normally keep behind the ash.";
                case MOURNING_NOTARY -> "Private law entered. There will be no duplicate original.";
                case PALE_GAMBLER -> "That was the card beneath the table. I wondered when you would ask for it.";
            };
        }
        String[] lines = switch (this) {
            case ASHEN_CURATOR -> prefers(card)
                    ? new String[]{"Good. Some pages improve after the fire.", "Keep the consequence. Discard the sentiment.", "That belongs in the part of you that does not heal cleanly."}
                    : new String[]{"Not my preferred shelf, but it will still leave a mark.", "You chose the cleaner page. Curious."};
            case MOURNING_NOTARY -> prefers(card)
                    ? new String[]{"Accepted. I have entered the obligation under your name.", "The count begins now. Do not ask me to round it down.", "Good. A measurable debt is at least an honest one."}
                    : new String[]{"Irregular, but admissible.", "I will record the exception. I will not excuse it."};
            case PALE_GAMBLER -> prefers(card)
                    ? new String[]{"There. Now the hand is interesting.", "You signed the side I would have chosen for you.", "That choice has enough teeth to be worth remembering."}
                    : new String[]{"Safe choices are still choices. Usually.", "You have declined to entertain me without technically refusing."};
        };
        return speaker + ": " + lines[random.nextInt(lines.length)];
    }

    public String refusalLine(LockData data, UUID player, RandomSource random) {
        String speaker = displayName(data, player);
        if (grudge(data, player) >= 3) {
            return speaker + ": " + switch (this) {
                case ASHEN_CURATOR -> "Three omissions are enough. I will stop bringing the pages that mend cleanly.";
                case MOURNING_NOTARY -> "Third refusal entered. Future offers will contain fewer blank spaces and more obligations.";
                case PALE_GAMBLER -> "Three folds. Fine. I will stop pretending the next hand needs to be kind.";
            };
        }
        String[] lines = switch (this) {
            case ASHEN_CURATOR -> new String[]{"Then let the paper cool unopened.", "Ash remembers refusal as well as flame.", "You preserved yourself. I will preserve the omission."};
            case MOURNING_NOTARY -> new String[]{"Refusal entered. The blank line remains yours.", "Very well. I have marked the offer declined.", "The absence of a signature is still evidence."};
            case PALE_GAMBLER -> new String[]{"Fold, then. The table remembers that too.", "A refusal is merely a wager placed on the next hand.", "You saved nothing. You only moved the risk elsewhere."};
        };
        return speaker + ": " + lines[random.nextInt(lines.length)];
    }

    public String revealLine() {
        return switch (this) {
            case ASHEN_CURATOR -> "The ash finally keeps a signature: " + title + ".";
            case MOURNING_NOTARY -> "A witness signs beneath the margin: " + title + ".";
            case PALE_GAMBLER -> "The dealer turns one card face-up and, at last, gives a name: " + title + ".";
        };
    }

    /** Returns true when this interaction reveals this dealer's identity. */
    public static boolean updateReveal(ServerPlayerView view, LockData data, UUID player, CardMaster master) {
        if (master == null || !phaseActive(data, player)) return false;
        int opened = data.uniqueInt(player, "field_cards_opened");
        int audiences = data.uniqueInt(player, "dealer_audiences_" + master.id);
        // Equal thresholds do not mean simultaneous exposition. Each peer Master
        // identifies itself only on one of its own post-threshold audiences.
        if (opened >= master.revealAfter && audiences >= 3 && !master.known(data, player)) {
            data.setUnique(player, "dealer_known_" + master.id, true);
            if (view != null) view.reveal(master);
            return true;
        }
        return false;
    }

    /** Compatibility/debug helper; gameplay should prefer updateReveal for the active dealer. */
    public static boolean updateReveals(ServerPlayerView view, LockData data, UUID player) {
        if (!phaseActive(data, player)) return false;
        boolean any = false;
        for (CardMaster master : values()) any |= updateReveal(view, data, player, master);
        return any;
    }

    /** Tiny adapter keeps the enum free of server/client imports. */
    public interface ServerPlayerView {
        void reveal(CardMaster master);
    }
}
