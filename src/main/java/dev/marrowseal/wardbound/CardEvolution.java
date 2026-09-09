package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Repeated signatures rewrite a stable card in-place. Revision III is a
 * palimpsest state: not merely more charges, but a clause that has been revised
 * often enough for its side-effect or cadence to change again.
 */
public final class CardEvolution {
    private CardEvolution() {}

    public static final int MAX_REVISION = 3;

    public static boolean evolvable(ForbiddenBargain card) {
        if (ExpandedProgressionCardEffects.isExpanded(card)) return true;
        return switch (card) {
            case LOADED_DICE, MERCYS_DUE, EMBER_COUNT, ORE_WHISPER, BORROWED_MOMENTUM,
                    HUNTERS_DIVIDEND, BELLGLASS_SIGHT, COAL_KISS, HEARTHMARK, SALT_CIRCLE,
                    BLACK_COMPASS, IRON_ECHO, GRAVE_RATION, POCKET_ECLIPSE, PILGRIMS_LUCK,
                    VEIN_DRINKER, QUICKSILVER_PRAYER, FERRYMAN_LEDGER, BLOODWELL_REFLEX, LONG_HAND,
                    HARVEST_SHARE, BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST, IRON_PULSE -> true;
            default -> false;
        };
    }

    /** Existing revised cards keep their two-clause branch choice; the new progressive utility laws revise linearly. */
    public static boolean branchable(ForbiddenBargain card) {
        return switch (card) {
            case LOADED_DICE, MERCYS_DUE, EMBER_COUNT, ORE_WHISPER, BORROWED_MOMENTUM,
                    HUNTERS_DIVIDEND, BELLGLASS_SIGHT, COAL_KISS, HEARTHMARK, SALT_CIRCLE,
                    BLACK_COMPASS, IRON_ECHO, GRAVE_RATION, POCKET_ECLIPSE, PILGRIMS_LUCK,
                    VEIN_DRINKER, QUICKSILVER_PRAYER, FERRYMAN_LEDGER -> true;
            default -> false;
        };
    }

    /** Variant that would be signed if the card were accepted now. */
    public static int nextVariantLevel(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null || !evolvable(card)) return 0;
        int signed = data.uniqueInt(id, "card_signed_" + card.id);
        return signed >= 7 ? 3 : signed >= 3 ? 2 : signed >= 1 ? 1 : 0;
    }

    /** Records one signature and returns the variant that became active. */
    public static int accept(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || card == null) return 0;
        UUID id = player.getUUID();
        int variant = nextVariantLevel(data, id, card);
        int signed = data.uniqueInt(id, "card_signed_" + card.id) + 1;
        data.setUniqueInt(id, "card_signed_" + card.id, signed);
        if (variant > 0) {
            int before = data.uniqueInt(id, "card_variant_" + card.id);
            if (variant > before) {
                data.setUniqueInt(id, "card_variant_" + card.id, variant);
                WardHistory.discoverCardMutation(player, card, variant);
                WardHistory.recordSpecial(player, "GLITCH", "Card revision: " + variantTitle(card, variant));
                SavantMemory.onRevision(player, card, variant);
            }
        }
        return variant;
    }

    public static int signatureCount(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return 0;
        return Math.max(0, data.uniqueInt(id, "card_signed_" + card.id));
    }

    public static boolean palimpsest(LockData data, UUID id, ForbiddenBargain card) {
        return activeVariant(data, id, card) >= MAX_REVISION;
    }

    public static int revisedCount(LockData data, UUID id) {
        if (data == null || id == null) return 0;
        int count = 0;
        for (ForbiddenBargain card : ForbiddenBargain.values()) if (activeVariant(data, id, card) > 0) count++;
        return count;
    }

    public static int activeVariant(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return 0;
        int earned = data.uniqueInt(id, "card_variant_" + card.id);
        int echoed = data.uniqueInt(id, "card_echo_variant_" + card.id);
        return Math.max(0, Math.min(MAX_REVISION, Math.max(earned, echoed)));
    }

    public static String progress(LockData data, UUID id, ForbiddenBargain card) {
        if (!evolvable(card)) return "stable";
        int signed = data.uniqueInt(id, "card_signed_" + card.id);
        int level = activeVariant(data, id, card);
        String path = branchable(card)
                ? CardBranches.title(card, data.uniqueInt(id, "card_branch_" + card.id)) + " · "
                : "";
        if (level >= MAX_REVISION) return path + "PALIMPSEST · " + signed + " signatures recorded";
        int nextAt = level == 0 ? 2 : level == 1 ? 4 : 8;
        String current = level == 0 ? "Stable" : level == 1 ? "Revision I" : "Revision II";
        String next = level == 0 ? "Revision I" : level == 1 ? "Revision II" : "Palimpsest";
        return path + current + " · " + signed + "/" + nextAt + " signatures toward " + next;
    }

    public static String variantTitle(ForbiddenBargain card, int level) {
        if (card == null || level <= 0) return card == null ? "Unknown Card" : card.title;
        if (ExpandedProgressionCardEffects.isExpanded(card)) return ExpandedProgressionCardEffects.variantTitle(card, level);
        return switch (card) {
            case LOADED_DICE -> level >= 3 ? "Loaded Dice: House Loaded" : level >= 2 ? "Loaded Dice: House Cut" : "Loaded Dice: Crooked Faces";
            case MERCYS_DUE -> level >= 3 ? "Mercy's Due: Forgiving Ledger" : level >= 2 ? "Mercy's Due: Open Account" : "Mercy's Due: Lenient Ink";
            case EMBER_COUNT -> level >= 3 ? "The Ember Count: Furnace Margin" : level >= 2 ? "The Ember Count: Cinder Column" : "The Ember Count: Warm Margin";
            case ORE_WHISPER -> level >= 3 ? "Ore Whisper: Buried Choir" : level >= 2 ? "Ore Whisper: Deep Choir" : "Ore Whisper: Lower Voice";
            case BORROWED_MOMENTUM -> level >= 3 ? "Borrowed Momentum: No Fixed Pace" : level >= 2 ? "Borrowed Momentum: Runaway Clause" : "Borrowed Momentum: Long Step";
            case HUNTERS_DIVIDEND -> level >= 3 ? "Hunter's Dividend: Paid in Red" : level >= 2 ? "Hunter's Dividend: Red Account" : "Hunter's Dividend: Revised Share";
            case BELLGLASS_SIGHT -> level >= 3 ? "Bellglass Inverted" : level >= 2 ? "Bellglass Reversed" : "Cracked Bellglass";
            case COAL_KISS -> level >= 3 ? "Coal Kiss: Furnace Mouth" : level >= 2 ? "Coal Kiss: Black Mouth" : "Coal Kiss: Sooted Lip";
            case HEARTHMARK -> level >= 3 ? "Hearthmark: Undying Coals" : level >= 2 ? "Hearthmark: Last Coals" : "Hearthmark: Banked Fire";
            case SALT_CIRCLE -> level >= 3 ? "Salt Circle: White Ossuary" : level >= 2 ? "Salt Circle: Ossuary Ring" : "Salt Circle: Thick Line";
            case BLACK_COMPASS -> level >= 3 ? "The Black Compass: Burial North" : level >= 2 ? "The Black Compass: Fixed North" : "The Black Compass: Long Needle";
            case IRON_ECHO -> level >= 3 ? "Iron Echo: Furnace Answer" : level >= 2 ? "Iron Echo: Foundry Reply" : "Iron Echo: Heavy Reply";
            case GRAVE_RATION -> level >= 3 ? "Grave Ration: Keeper's Share" : level >= 2 ? "Grave Ration: Undertaker's Share" : "Grave Ration: Second Spoon";
            case POCKET_ECLIPSE -> level >= 3 ? "A Pocket Eclipse: Private Night" : level >= 2 ? "A Pocket Eclipse: Closed Sky" : "A Pocket Eclipse: Wider Umbra";
            case PILGRIMS_LUCK -> level >= 3 ? "Pilgrim's Luck: Favoured Road" : level >= 2 ? "Pilgrim's Luck: Loaded Road" : "Pilgrim's Luck: Kind Road";
            case VEIN_DRINKER -> level >= 3 ? "Vein Drinker: Red Communion" : level >= 2 ? "Vein Drinker: Open Throat" : "Vein Drinker: Second Cup";
            case QUICKSILVER_PRAYER -> level >= 3 ? "Quicksilver Prayer: Liquid Psalm" : level >= 2 ? "Quicksilver Prayer: Mercury Psalm" : "Quicksilver Prayer: Faster Verse";
            case FERRYMAN_LEDGER -> level >= 3 ? "The Ferryman's Ledger: Paid Crossing" : level >= 2 ? "The Ferryman's Ledger: Return Fare" : "The Ferryman's Ledger: Extra Crossing";
            case BLOODWELL_REFLEX -> level >= 3 ? "Bloodwell Reflex: Unending Pulse" : level >= 2 ? "Bloodwell Reflex: Deep Pulse" : "Bloodwell Reflex: Quickened Pulse";
            case LONG_HAND -> level >= 3 ? "The Long Hand: No Horizon" : level >= 2 ? "The Long Hand: Four Fingers Farther" : "The Long Hand: Third Knuckle";
            case HARVEST_SHARE -> level >= 3 ? "The Harvest Share: Perpetual Tithe" : level >= 2 ? "The Harvest Share: Heavy Sheaf" : "The Harvest Share: Wider Sheaf";
            case BALLISTIC_SCRIPT -> level >= 3 ? "Ballistic Script: Permanent Margin" : level >= 2 ? "Ballistic Script: Twenty Percent" : "Ballistic Script: Fifteen Percent";
            case FAR_LEDGER -> level >= 3 ? "The Far Ledger: Endless Interest" : level >= 2 ? "The Far Ledger: Long Account" : "The Far Ledger: Extended Account";
            case CROWD_INTEREST -> level >= 3 ? "Crowd Interest: House Full" : level >= 2 ? "Crowd Interest: Six Witnesses" : "Crowd Interest: Five Witnesses";
            case IRON_PULSE -> level >= 3 ? "The Iron Pulse: Lasting Plate" : level >= 2 ? "The Iron Pulse: Twelve Plates" : "The Iron Pulse: Nine Plates";
            default -> card.title;
        };
    }

    public static String variantText(ForbiddenBargain card, int level) {
        if (card == null || level <= 0) return card == null ? "" : card.debtText;
        if (ExpandedProgressionCardEffects.isExpanded(card)) return ExpandedProgressionCardEffects.variantText(card, level);
        return switch (card) {
            case LOADED_DICE -> level >= 3
                    ? "For your next five ordinary wards: -1 starting life, +28% loot. The house now admits the dice are loaded."
                    : level >= 2 ? "For your next four ordinary wards: -1 starting life, +25% loot. The house has stopped pretending the faces are fair."
                    : "For your next three ordinary wards: -1 starting life, +25% loot.";
            case MERCYS_DUE -> level >= 3
                    ? "For your next four ordinary wards: +1 starting life, -8% loot. Mercy has almost stopped charging interest."
                    : level >= 2 ? "For your next three ordinary wards: +1 starting life, -12% loot. Mercy has learned to charge less interest."
                    : "For your next two ordinary wards: +1 starting life, -10% loot.";
            case EMBER_COUNT -> level >= 3 ? "Your next twenty-four melee hits ignite targets for seven seconds." : level >= 2 ? "Your next twenty melee hits ignite targets for six seconds." : "Your next sixteen melee hits ignite targets for five seconds.";
            case ORE_WHISPER -> level >= 3 ? "For seven minutes, a wider buried choir answers more readily around you." : level >= 2 ? "For six minutes, nearby ores answer with a deeper, wider whisper." : "For five minutes, nearby ores betray themselves with cold sparks.";
            case BORROWED_MOMENTUM -> level >= 3 ? "Speed II for four and a half minutes; Slowness I collects only thirty seconds afterward." : level >= 2 ? "Speed II for four minutes; Slowness I collects only forty seconds afterward." : "Speed II for three and a half minutes; Slowness I collects fifty seconds afterward.";
            case HUNTERS_DIVIDEND -> level >= 3 ? "Your next sixteen hostile kills repay two experience and one hunger; every fourth payment marks you instead of every third." : level >= 2 ? "Your next fourteen hostile kills repay two experience and one point of hunger; every third payment still marks you." : "Your next twelve hostile kills repay two experience and one point of hunger; every third payment still briefly marks you.";
            case BELLGLASS_SIGHT -> level >= 3 ? "Night Vision remains. Only absolute darkness makes the inverted glass reveal you; direct daylight still disorients you briefly." : level >= 2 ? "Night Vision remains. The reversed glass only makes you glow in near-total darkness, but bright daylight now feels briefly disorienting." : "Night Vision remains; only very low light makes the cracked glass answer by making you glow.";
            case COAL_KISS -> level >= 3 ? "Your next twenty-eight melee hits leave seven seconds of Weakness." : level >= 2 ? "Your next twenty-four melee hits leave longer Weakness on the target." : "Your next twenty melee hits leave Weakness on the target.";
            case HEARTHMARK -> level >= 3 ? "Your next fourteen meals grant stronger Regeneration; the final Hunger debt lasts only fifteen seconds." : level >= 2 ? "Your next twelve meals grant stronger brief Regeneration; the last closes with only twenty seconds of Hunger." : "Your next ten meals grant longer brief Regeneration; the last still closes with thirty seconds of Hunger.";
            case SALT_CIRCLE -> level >= 3 ? "Your next fourteen undead kills grant Absorption; every third kill strengthens the layer." : level >= 2 ? "Your next twelve undead kills grant brief Absorption, with every fourth kill strengthening the layer." : "Your next ten undead kills grant brief Absorption.";
            case BLACK_COMPASS -> level >= 3 ? "For six minutes, nearby hostiles remain outlined at still greater range." : level >= 2 ? "For five minutes, nearby hostiles remain outlined at greater range." : "For four minutes, nearby hostiles are outlined through walls.";
            case IRON_ECHO -> level >= 3 ? "Your next sixteen readied melee strikes carry extreme extra knockback." : level >= 2 ? "Your next fourteen readied melee strikes carry brutal extra knockback." : "Your next twelve readied melee strikes carry extra knockback.";
            case GRAVE_RATION -> level >= 3 ? "Your next eighteen hostile kills restore hunger; at full hunger they repay three experience." : level >= 2 ? "Your next sixteen hostile kills restore hunger; full hunger pays two experience instead." : "Your next fourteen hostile kills restore hunger or experience.";
            case POCKET_ECLIPSE -> level >= 3 ? "For six minutes, low light renews a longer private veil before brightness breaks it." : level >= 2 ? "For five minutes, low light renews a longer veil before brightness breaks it." : "For four minutes, low light repeatedly grants brief Invisibility.";
            case PILGRIMS_LUCK -> level >= 3 ? "For your next fifty-six mined blocks, each break has a twenty-six percent chance to shake loose experience." : level >= 2 ? "For your next forty-eight mined blocks, each break has a twenty-two percent chance to shake loose experience." : "For your next forty mined blocks, each break has an eighteen percent chance to shake loose experience.";
            case VEIN_DRINKER -> level >= 3 ? "Your next fourteen hostile kills return one heart; every third returns an additional half-heart." : level >= 2 ? "Your next twelve hostile kills each return one heart; every fourth returns an additional half-heart." : "Your next ten hostile kills each return one heart.";
            case QUICKSILVER_PRAYER -> level >= 3 ? "Speed I and Haste I for three and a half minutes; the collected Weakness lasts only fifteen seconds." : level >= 2 ? "Speed I and Haste I for three minutes; the collected Weakness lasts only twenty seconds." : "Speed I and Haste I for two and a half minutes; Weakness follows for twenty-five seconds.";
            case FERRYMAN_LEDGER -> level >= 3 ? "The next eight heavy blows are reduced; the eighth crossing grants stronger brief Resistance." : level >= 2 ? "The next seven heavy blows are reduced; the seventh crossing also grants brief Resistance." : "The next six heavy blows are reduced.";
            case BLOODWELL_REFLEX -> level >= 3 ? "Permanent. Below half health you regenerate one health each second; below one quarter the pulse strengthens further." : level >= 2 ? "For ten minutes, below half health you regenerate one health each second; below one quarter the pulse strengthens further." : "For seven minutes, low-health renewal is stronger and accelerates again below one quarter health.";
            case LONG_HAND -> level >= 3 ? "Permanent. Block interaction reach is extended by four blocks." : level >= 2 ? "For eight minutes, block interaction reach is extended by four blocks." : "For five minutes, block interaction reach is extended by three blocks.";
            case HARVEST_SHARE -> level >= 3 ? "Permanent. Mature crop breaks produce roughly sixty percent additional normal harvest." : level >= 2 ? "For twelve minutes, mature crop breaks produce roughly sixty percent additional normal harvest." : "For eight minutes, mature crop breaks produce roughly forty percent additional normal harvest.";
            case BALLISTIC_SCRIPT -> level >= 3 ? "Permanent. Projectile damage is increased by twenty percent." : level >= 2 ? "For ten minutes, projectile damage is increased by twenty percent." : "For seven minutes, projectile damage is increased by fifteen percent.";
            case FAR_LEDGER -> level >= 3 ? "Permanent. Projectile damage gains distance interest from four to twenty-four blocks, up to twenty percent on long shots." : level >= 2 ? "For ten minutes, projectile damage gains distance interest up to twenty percent on long shots." : "For seven minutes, projectile damage gains distance interest up to fifteen percent on long shots.";
            case CROWD_INTEREST -> level >= 3 ? "Permanent. Each nearby hostile adds five percent outgoing damage, counting up to six threats." : level >= 2 ? "For eight minutes, each nearby hostile adds five percent outgoing damage, counting up to six threats." : "For five minutes, each nearby hostile adds four percent outgoing damage, counting up to five threats.";
            case IRON_PULSE -> level >= 3 ? "Permanent. Armor rises by +4 below sixty percent health, +8 below thirty-five percent, and +12 below twenty percent." : level >= 2 ? "For ten minutes, armor rises by +4/+8/+12 as health crosses sixty, thirty-five and twenty percent." : "For seven minutes, armor rises by +3/+6/+9 as health crosses sixty, thirty-five and twenty percent.";
            default -> card.debtText;
        };
    }
}
