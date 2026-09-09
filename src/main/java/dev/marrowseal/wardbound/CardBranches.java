package dev.marrowseal.wardbound;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** A revised signature chooses its clause; old saves retain the original clause. */
public final class CardBranches {
    private CardBranches() {}

    public static boolean eligible(int revision, ForbiddenBargain card) {
        return card != null && revision > 0 && CardEvolution.branchable(card);
    }

    public static boolean validChoice(int revision, ForbiddenBargain card, int branch) {
        return eligible(revision, card) ? branch == 1 || branch == 2 : branch == 0;
    }

    public static boolean alternate(LockData data, UUID id, ForbiddenBargain card) {
        return CardEvolution.activeVariant(data, id, card) > 0
                && data.uniqueInt(id, "card_branch_" + card.id) == 2;
    }

    public static String activeDescription(LockData data, UUID id, ForbiddenBargain card) {
        int revision = CardEvolution.activeVariant(data, id, card);
        return eligible(revision, card) ? description(card, revision, alternate(data, id, card) ? 2 : 1)
                : card.debtText;
    }

    public static void select(ServerPlayer player, LockData data, ForbiddenBargain card, int branch) {
        if (!eligible(CardEvolution.nextVariantLevel(data, player.getUUID(), card), card)) return;
        if (branch != 1 && branch != 2) throw new IllegalArgumentException("Invalid card branch");
        data.setUniqueInt(player.getUUID(), "card_branch_" + card.id, branch);
        // Branch-local cadence starts with this signature, not with a prior copy.
        data.setUniqueInt(player.getUUID(), "card_branch_paid_" + card.id, 0);
        WardHistory.recordSpecial(player, "WARD", card.title + " // " + title(card, branch));
    }

    public static String title(ForbiddenBargain card, int branch) {
        if (branch != 2) return "Original Clause";
        return switch (card) {
            case LOADED_DICE -> "Quiet Odds";
            case MERCYS_DUE -> "Time to Repent";
            case EMBER_COUNT -> "Beacon Brand";
            case ORE_WHISPER -> "Close Reading";
            case BORROWED_MOMENTUM -> "Measured Pace";
            case HUNTERS_DIVIDEND -> "Public Bounty";
            case BELLGLASS_SIGHT -> "Night Reader";
            case COAL_KISS -> "Clinging Ash";
            case HEARTHMARK -> "Shelter at Supper";
            case SALT_CIRCLE -> "Pale Pursuit";
            case BLACK_COMPASS -> "Closing Circle";
            case IRON_ECHO -> "Iron Anchor";
            case GRAVE_RATION -> "Living Ration";
            case POCKET_ECLIPSE -> "Still Shadow";
            case PILGRIMS_LUCK -> "Counted Steps";
            case VEIN_DRINKER -> "Blood Reserve";
            case QUICKSILVER_PRAYER -> "Miner's Psalm";
            case FERRYMAN_LEDGER -> "Deep Crossing";
            default -> "Original Clause";
        };
    }

    public static String description(ForbiddenBargain card, int revision, int branch) {
        if (branch != 2) {
            String text = CardEvolution.variantText(card, revision);
            if (card == ForbiddenBargain.LOADED_DICE) text += " The ward also runs faster.";
            if (card == ForbiddenBargain.MERCYS_DUE) text += " The first mistake is forgiven.";
            return text;
        }
        String change = switch (card) {
            case LOADED_DICE -> "+12% loot per charged ward. No starting-life penalty and no faster mechanism.";
            case MERCYS_DUE -> "+6 seconds per charged ward instead of +1 starting life. First mistake forgiveness and the original loot cost remain.";
            case EMBER_COUNT -> "Hits burn for only 2 seconds but reveal the target for 8 seconds. An Ashen Brand conjunction still burns for 8 seconds.";
            case ORE_WHISPER -> "Every nearby ore is revealed on each pulse, but the search radius shrinks to 4 blocks.";
            case BORROWED_MOMENTUM -> "Speed I instead of Speed II. No Slowness debt when this signature ends.";
            case HUNTERS_DIVIDEND -> "Each hostile kill pays 4 experience instead of 2 experience and food. Every payment reveals you for 5 seconds.";
            case BELLGLASS_SIGHT -> "Low light renews Night Vision; in brighter light it expires naturally. No self-reveal or daylight confusion from this card.";
            case COAL_KISS -> "Hits apply Slowness I instead of Weakness. The original duration per hit remains.";
            case HEARTHMARK -> "Meals grant Absorption I for 20 seconds instead of Regeneration. The final Hunger debt remains.";
            case SALT_CIRCLE -> "Undead kills grant Speed I for 12 seconds instead of Absorption.";
            case BLACK_COMPASS -> "Hostiles within 12 blocks are revealed and slowed. The original longer detection range is lost.";
            case IRON_ECHO -> "Readied hits apply Slowness I for 4 seconds, with only light knockback instead of the original heavy push.";
            case GRAVE_RATION -> "Hostile kills restore half a heart instead of hunger or experience.";
            case POCKET_ECLIPSE -> "Standing still while crouching renews Invisibility, even in light. Darkness alone no longer activates it.";
            case PILGRIMS_LUCK -> "One guaranteed experience every " + (revision >= 3 ? 3 : revision >= 2 ? 4 : 5) + " charged block breaks instead of random payments. The Ore Whisper pairing reduces that interval by one.";
            case VEIN_DRINKER -> "Hostile kills grant Absorption I for 10 seconds instead of healing. Red Communion strengthens it to Absorption II.";
            case QUICKSILVER_PRAYER -> "Haste II instead of Speed I and Haste I. The original Weakness debt remains.";
            case FERRYMAN_LEDGER -> "Reduce hits of at least 4 hearts by 50%, instead of reducing hits of at least 2 hearts by 35%. Fall damage remains excluded; the final crossing bonus remains.";
            default -> CardEvolution.variantText(card, revision);
        };
        return change + " Original charges and total duration remain.";
    }
}
