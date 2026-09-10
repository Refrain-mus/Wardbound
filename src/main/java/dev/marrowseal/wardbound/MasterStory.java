package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Hidden long-form story progression and rivalry between the three card-masters. */
public final class MasterStory {
    private MasterStory() {}
    public static final int MAX_CHAPTER = 7;

    public static int chapter(LockData data, UUID id, CardMaster master) {
        return Math.max(0, Math.min(MAX_CHAPTER, data.uniqueInt(id, "dealer_story_" + master.id)));
    }

    public static String chapterName(CardMaster master, int chapter) {
        if (chapter <= 0) return "Unwritten";
        return switch (master) {
            case ASHEN_CURATOR -> switch (chapter) {
                case 1 -> "Warm Ash"; case 2 -> "The Removed Page"; case 3 -> "Preservation";
                case 4 -> "The Fireproof Shelf"; case 5 -> "What the Curator Burned";
                case 6 -> "Ash That Refused the Fire"; default -> "The Catalogue of Survivors";
            };
            case MOURNING_NOTARY -> switch (chapter) {
                case 1 -> "First Witness"; case 2 -> "Admissible Evidence"; case 3 -> "The Missing Signatory";
                case 4 -> "Counter-Signature"; case 5 -> "The Record Before the Event";
                case 6 -> "Evidence Without Cause"; default -> "The Last Signature Is Yours";
            };
            case PALE_GAMBLER -> switch (chapter) {
                case 1 -> "Ante"; case 2 -> "House Memory"; case 3 -> "The Card Beneath";
                case 4 -> "Loaded Table"; case 5 -> "The Bet You Already Made";
                case 6 -> "The House Is Also Playing"; default -> "No More Outside the Table";
            };
        };
    }

    public static String chapterText(CardMaster master, int chapter) {
        return switch (master) {
            case ASHEN_CURATOR -> switch (chapter) {
                case 1 -> "The Curator is not collecting cards. It is deciding which consequences deserve to survive their owners.";
                case 2 -> "Several offers reference pages that were removed before the hand reached you. The omissions are deliberate.";
                case 3 -> "It reacts more strongly to scars that persist than to wounds that merely hurt. Preservation is its actual craft.";
                case 4 -> "The private law is not a gift. It is a page the Curator considers too durable to leave in ordinary circulation.";
                case 5 -> "The Curator has burned records of outcomes it did not want the other masters to price. Some of those outcomes concern you.";
                case 6 -> "A corruption survived beside a countersigned rite. The Curator now treats you as evidence that some consequences can outlive their intended ending.";
                default -> "Its catalogue is no longer a list of cards. It is a list of people and things that remained after the rule designed to erase them. Your entry is open.";
            };
            case MOURNING_NOTARY -> switch (chapter) {
                case 1 -> "The Notary writes as if every event requires a witness even when nobody asked for one.";
                case 2 -> "Contracts are not its preferred product. They are evidence that a future action can be made legally present before it occurs.";
                case 3 -> "The blank signature it keeps referencing is not yours. Another hand was expected to countersign and did not.";
                case 4 -> "Its private law measures violence because measurement is how the Notary turns an act into a record.";
                case 5 -> "Some entries in the Notary's archive are dated before the events they describe. Your name appears in more than one margin.";
                case 6 -> "The archive now contains enough exacting and pure completions to treat your future behaviour as admissible evidence before you perform it.";
                default -> "The missing signatory was never absent. The Notary was leaving room for the version of you produced by the record itself.";
            };
            case PALE_GAMBLER -> switch (chapter) {
                case 1 -> "The Gambler does not care whether you win a hand. It cares that your choice makes the next hand more interesting.";
                case 2 -> "Refusals and safe choices are both priced. The only move it dislikes is one it cannot use to update its odds.";
                case 3 -> "The private card is not hidden for rarity. It is hidden so the table can learn how badly you want to see it.";
                case 4 -> "The Gambler has begun betting against the other card-masters, using your signatures as the stake.";
                case 5 -> "The oldest wager on the table was placed before you found Wardbound. The unsettling part is that the subject of the wager is still you.";
                case 6 -> "The Black Table proved the house is not outside the risk economy. The Gambler has started pricing its own losses into your hands.";
                default -> "There is no longer a meaningful distinction between playing a hand and being part of the table. The Gambler has stopped pretending otherwise.";
            };
        };
    }

    public static void evaluateAll(ServerPlayer player, LockData data) {
        for (CardMaster master : CardMaster.values()) evaluate(player, data, master);
    }

    public static void evaluate(ServerPlayer player, LockData data, CardMaster master) {
        if (player == null || data == null || master == null) return;
        UUID id = player.getUUID();
        // Master story is a late-game layer. Card-lineage counters may accumulate
        // silently beforehand, but no Master chapter may start before the shared
        // dealer gate has actually opened.
        if (!CardMaster.phaseActive(data, id)) return;
        int current = chapter(data, id, master);
        int audience = data.uniqueInt(id, "dealer_audiences_" + master.id);
        int accepted = data.uniqueInt(id, "dealer_acceptances_" + master.id);
        int relation = master.relation(data, id);
        int objective = data.uniqueInt(id, "objectives_completed_total");
        boolean signature = data.masterPact(id) == master.visualSignature.ordinal();
        int conjunctions = CardConjunctions.knownCount(data, id);

        int target = 0;
        if (master.known(data, id)) target = 1;
        if (target >= 1 && audience >= 4 && accepted >= 2) target = 2;
        if (target >= 2 && accepted >= 5 && relation >= 5
                && (master != CardMaster.MOURNING_NOTARY || objective >= 2)) target = 3;
        if (target >= 3 && accepted >= 8 && relation >= 10 && signature) target = 4;
        if (target >= 4 && accepted >= 12 && relation >= 15 && conjunctions >= 3) target = 5;
        if (target >= 5 && accepted >= 16 && relation >= 18 && lateGate(data, id, master, 6)) target = 6;
        if (target >= 6 && accepted >= 22 && relation >= 20 && conjunctions >= 6 && lateGate(data, id, master, 7)) target = 7;

        // Conditions may already be far ahead when a Master finally gives its name.
        // Never dump several chapters in one click: one meaningful event may expose
        // at most one new chapter, preserving the late-game arc as an actual arc.
        if (target > current) {
            int next = current + 1;
            data.setUniqueInt(id, "dealer_story_" + master.id, next);
            WardHistory.discoverMasterChapter(player, master, next);
            WardHistory.recordSpecial(player, "WARD", master.title + " // " + chapterName(master, next));
            WardHud.messageTransient(player, Component.literal(master.title + " // " + chapterName(master, next))
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                    "master_story_" + master.id + "_" + next,
                    "The " + master.title.replace("The ", "") + " has changed the category of information it is willing to expose. That is chapter " + next + ", whether it calls it one or not.",
                    20L * 180L);
        }
    }

    private static boolean lateGate(LockData data, UUID id, CardMaster master, int chapter) {
        return switch (master) {
            case ASHEN_CURATOR -> chapter == 6
                    ? CurseEvolution.knownCount(data, id) >= 1 && OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.ASH_MARGIN) >= 3
                    : OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.ASH_MARGIN) >= 5 && CardEvolution.revisedCount(data, id) >= 3;
            case MOURNING_NOTARY -> chapter == 6
                    ? data.uniqueInt(id, "contract_exacting_clauses") + data.uniqueInt(id, "ritual_pure_completions") >= 3
                    : OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.WITNESS_SEQUENCE) >= 4
                    && data.uniqueInt(id, "objectives_completed_total") >= 10;
            case PALE_GAMBLER -> chapter == 6
                    ? OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.BLACK_TABLE) >= 3
                    : OccultChainEvents.stage(data, id, OccultChainEvents.ChainEvent.BLACK_TABLE) >= 5
                    && data.uniqueInt(id, "attention_highest_stage") >= AttentionSystem.Stage.KNOWN.ordinal();
        };
    }

    public static float signatureBonus(LockData data, UUID id, CardMaster master) {
        return switch (chapter(data, id, master)) {
            case 3 -> 0.03f;
            case 4 -> 0.06f;
            case 5 -> 0.10f;
            case 6 -> 0.14f;
            case 7 -> 0.18f;
            default -> 0f;
        };
    }

    public static int handBonus(LockData data, UUID id, CardMaster master) {
        return chapter(data, id, master) >= 5 ? 1 : 0;
    }

    public static float interferenceChance(LockData data, UUID id, CardMaster master) {
        float chapterChance = switch (chapter(data, id, master)) {
            case 3 -> 0.18f;
            case 4 -> 0.23f;
            case 5 -> 0.29f;
            case 6 -> 0.35f;
            case 7 -> 0.42f;
            default -> 0f;
        };
        int heat = Math.max(0, Math.min(12, data.uniqueInt(id, "dealer_rivalry_heat")));
        return Math.min(0.58f, chapterChance + heat * 0.0125f);
    }

    /** Signing on-theme cards after story chapter III lets the masters use the player as a stake in their rivalry. */
    public static void onAccepted(ServerPlayer player, LockData data, CardMaster dealer, ForbiddenBargain card) {
        if (player == null || data == null || dealer == null || card == null) return;
        dev.marrowseal.wardbound.champion.ChampionDialogue.masterReaction(player,data,dealer);
        evaluate(player, data, dealer);
        if (chapter(data, player.getUUID(), dealer) < 3 || !dealer.prefers(card)) return;
        if (player.getRandom().nextFloat() >= interferenceChance(data, player.getUUID(), dealer)) return;

        CardMaster rival = rivalMostLikelyToCare(data, player.getUUID(), dealer);
        data.setUniqueInt(player.getUUID(), "dealer_interference_" + rival.id, dealer.ordinal() + 1);
        data.setUniqueInt(player.getUUID(), "dealer_rivalry_events", data.uniqueInt(player.getUUID(), "dealer_rivalry_events") + 1);
        data.setUniqueInt(player.getUUID(), "dealer_rivalry_heat", Math.min(12, data.uniqueInt(player.getUUID(), "dealer_rivalry_heat") + 2));
        WardHistory.discoverRivalry(player, dealer, rival);
        SavantMemory.onRivalry(player, dealer, rival, data.uniqueInt(player.getUUID(), "dealer_rivalry_heat"));
        WardHistory.recordSpecial(player, "GLITCH", dealer.title + " has placed a counter-offer into " + rival.title + "'s next hand.");
        int heat = data.uniqueInt(player.getUUID(), "dealer_rivalry_heat");
        if (rival.relation(data, player.getUUID()) > -12 && player.getRandom().nextFloat() < (heat >= 8 ? 0.65f : 0.45f))
            rival.addRelation(data, player.getUUID(), -1);
    }

    private static CardMaster rivalMostLikelyToCare(LockData data, UUID id, CardMaster source) {
        CardMaster best = null;
        int bestRelation = Integer.MIN_VALUE;
        for (CardMaster candidate : CardMaster.values()) {
            if (candidate == source) continue;
            int relation = candidate.relation(data, id);
            if (relation > bestRelation) { bestRelation = relation; best = candidate; }
        }
        return best == null ? CardMaster.PALE_GAMBLER : best;
    }

    public record Interference(CardMaster source, ForbiddenBargain.Kind kind, int heat) {}

    /** Read the queued rivalry clause without mutating persistent progression. */
    public static Interference peekInterference(LockData data, UUID id, CardMaster dealer) {
        if (data == null || id == null || dealer == null) return null;
        int source = data.uniqueInt(id, "dealer_interference_" + dealer.id);
        if (source <= 0 || source > CardMaster.values().length) return null;
        int heat = Math.max(0, Math.min(12, data.uniqueInt(id, "dealer_rivalry_heat")));
        CardMaster rival = CardMaster.values()[source - 1];
        ForbiddenBargain.Kind kind = switch (rival) {
            case ASHEN_CURATOR -> ForbiddenBargain.Kind.SCAR;
            case MOURNING_NOTARY -> ForbiddenBargain.Kind.CONTRACT;
            case PALE_GAMBLER -> ForbiddenBargain.Kind.WAGER;
        };
        return new Interference(rival, kind, heat);
    }

    /** Consume one queued rivalry clause after a real hand has been committed. */
    public static Interference consumeInterference(LockData data, UUID id, CardMaster dealer) {
        Interference out = peekInterference(data, id, dealer);
        if (out == null) return null;
        data.setUniqueInt(id, "dealer_interference_" + dealer.id, 0);
        data.setUniqueInt(id, "dealer_interference_consumed", data.uniqueInt(id, "dealer_interference_consumed") + 1);
        data.setUniqueInt(id, "dealer_rivalry_heat", Math.max(0, out.heat() - 1));
        return out;
    }

    public static String rivalrySummary(LockData data, UUID id) {
        CardMaster a = CardMaster.ASHEN_CURATOR, n = CardMaster.MOURNING_NOTARY, g = CardMaster.PALE_GAMBLER;
        return "Curator " + signed(a.relation(data, id)) + " · Notary " + signed(n.relation(data, id)) + " · Gambler " + signed(g.relation(data, id))
                + " · crossfire " + data.uniqueInt(id, "dealer_rivalry_events") + " · heat " + data.uniqueInt(id, "dealer_rivalry_heat") + "/12";
    }

    private static String signed(int v) { return (v >= 0 ? "+" : "") + v; }
}
