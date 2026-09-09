package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import dev.marrowseal.wardbound.item.SealedCardItem;
import dev.marrowseal.wardbound.item.WardItems;

import java.util.UUID;

/** Stateful cross-system sequences. A chain is earned by overlap, not by a standalone roll. */
public final class OccultChainEvents {
    private OccultChainEvents() {}

    public enum ChainEvent {
        BLACK_TABLE("black_table", "The Black Table", 5),
        ASH_MARGIN("ash_margin", "Ash in the Margin", 5),
        WITNESS_SEQUENCE("witness_sequence", "The Witness Sequence", 5);

        public final String id, title;
        public final int maxStage;
        ChainEvent(String id, String title, int maxStage) { this.id = id; this.title = title; this.maxStage = maxStage; }
    }

    public static int stage(LockData data, UUID id, ChainEvent event) {
        return Math.max(0, Math.min(event.maxStage, data.uniqueInt(id, "occult_chain_" + event.id)));
    }

    private static void advance(ServerPlayer player, LockData data, ChainEvent event, int target, String text) {
        UUID id = player.getUUID();
        int before = stage(data, id, event);
        if (target <= before) return;
        data.setUniqueInt(id, "occult_chain_" + event.id, target);
        WardHistory.discoverOccultChain(player, event, target);
        WardHistory.recordSpecial(player, "GLITCH", event.title + " // " + target + "/" + event.maxStage + " // " + text);
        WardHud.message(player, Component.literal(event.title + " // " + target + "/" + event.maxStage)
                .withStyle(target >= event.maxStage ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        SavantMemory.onOccultChain(player, event, target);
        MasterStory.evaluateAll(player, data);
    }

    public static void onChestResolved(ServerPlayer player, LockData data, int threatTier, boolean eldritchShard, boolean success) {
        if (player == null || data == null) return;
        UUID id = player.getUUID();
        int black = stage(data, id, ChainEvent.BLACK_TABLE);
        if (black == 0 && AttentionSystem.current(data, id).ordinal() >= AttentionSystem.Stage.HUNTED.ordinal()
                && CardMaster.PALE_GAMBLER.relation(data, id) >= 8) {
            advance(player, data, ChainEvent.BLACK_TABLE, 1, "The Gambler notices that the thing hunting you is also pricing the outcome.");
            black = 1;
        }
        if (black == 1 && eldritchShard && threatTier >= ChestThreat.Tier.PROFANE.ordinal()) {
            advance(player, data, ChainEvent.BLACK_TABLE, 2, "An Eldritch Shard is spent on a profane seal while the table is already occupied.");
            black = 2;
        }
        if (black == 2 && success && threatTier >= ChestThreat.Tier.UNNAMEABLE.ordinal()) {
            advance(player, data, ChainEvent.BLACK_TABLE, 3, "The unnameable seal is beaten without the table folding.");
            rewardFieldCard(player, CardMaster.PALE_GAMBLER, "A fourth card appears under the chest after everything else stops moving.");
            data.setUniqueInt(id, "field_card_pity", 0);
            CardMaster.PALE_GAMBLER.addRelation(data, id, 2);
        }

        int witness = stage(data, id, ChainEvent.WITNESS_SEQUENCE);
        if (witness == 2 && success && threatTier >= ChestThreat.Tier.UNNAMEABLE.ordinal()) {
            advance(player, data, ChainEvent.WITNESS_SEQUENCE, 3, "A fully documented resonance survives an unnameable ward.");
            player.giveExperiencePoints(12);
            data.setUnique(id, "witness_sequence_core", true);
        }
    }

    public static void onCardSigned(ServerPlayer player, LockData data, CardMaster dealer, ForbiddenBargain card) {
        if (player == null || data == null || card == null) return;
        UUID id = player.getUUID();

        int ash = stage(data, id, ChainEvent.ASH_MARGIN);
        if (ash == 0 && dealer == CardMaster.ASHEN_CURATOR && card.isCurse() && CardMaster.ASHEN_CURATOR.relation(data, id) >= 8) {
            advance(player, data, ChainEvent.ASH_MARGIN, 1, "The Curator lets a curse survive the ash on purpose.");
            ash = 1;
        }
        if (ash == 1 && dealer == CardMaster.MOURNING_NOTARY && card.isRitual()) {
            advance(player, data, ChainEvent.ASH_MARGIN, 2, "The Notary countersigns a ritual beside the Curator's surviving curse.");
            ash = 2;
        }
        if (ash == 3 && dealer == CardMaster.ASHEN_CURATOR && dealer.prefers(card)) {
            advance(player, data, ChainEvent.ASH_MARGIN, 4, "After the truce, the Curator deliberately returns a page it would normally keep.");
            ash = 4;
        }
        if (ash == 4 && dealer == CardMaster.MOURNING_NOTARY && card.isObjectiveCard()) {
            advance(player, data, ChainEvent.ASH_MARGIN, 5, "The Notary files the returned page as admissible evidence instead of contradiction.");
            CardMaster.ASHEN_CURATOR.addRelation(data, id, 3);
            CardMaster.MOURNING_NOTARY.addRelation(data, id, 3);
            data.setUnique(id, "curator_notary_truce_complete", true);
            player.giveExperiencePoints(12);
        }

        int black = stage(data, id, ChainEvent.BLACK_TABLE);
        if (black == 3 && dealer == CardMaster.PALE_GAMBLER
                && (card.kind == ForbiddenBargain.Kind.WAGER || card.isDeath() || card.isCurse())) {
            advance(player, data, ChainEvent.BLACK_TABLE, 4, "The Gambler prices the impossible chest back into a new hand instead of closing the account.");
        }

        int witness = stage(data, id, ChainEvent.WITNESS_SEQUENCE);
        if (witness == 1 && card.isDeath()) {
            advance(player, data, ChainEvent.WITNESS_SEQUENCE, 2, "A death-law is signed after the Grimoire establishes a resonance network.");
        } else if (witness == 4 && card.isDeath()) {
            advance(player, data, ChainEvent.WITNESS_SEQUENCE, 5, "A second death-law is accepted after the completed resonance archive refuses to forget the first.");
            data.setUnique(id, "witness_sequence_complete", true);
            player.giveExperiencePoints(20);
        }
    }

    public static void onRitualComplete(ServerPlayer player, LockData data, int purity) {
        if (player == null || data == null) return;
        UUID id = player.getUUID();
        if (stage(data, id, ChainEvent.ASH_MARGIN) == 2 && purity >= 100) {
            advance(player, data, ChainEvent.ASH_MARGIN, 3, "The countersigned rite is completed without a breach.");
            CardMaster.ASHEN_CURATOR.addRelation(data, id, 2);
            CardMaster.MOURNING_NOTARY.addRelation(data, id, 2);
            data.setUnique(id, "curator_notary_truce", true);
            player.giveExperiencePoints(8);
        }
    }

    public static void onConjunctionDiscovered(ServerPlayer player, LockData data) {
        if (player == null || data == null) return;
        UUID id = player.getUUID();
        int known = CardConjunctions.knownCount(data, id);
        if (stage(data, id, ChainEvent.WITNESS_SEQUENCE) == 0 && known >= 3)
            advance(player, data, ChainEvent.WITNESS_SEQUENCE, 1, "Three conjunctions make the Grimoire dense enough for the Savant to call it a network.");
        if (stage(data, id, ChainEvent.WITNESS_SEQUENCE) == 3 && known >= 9)
            advance(player, data, ChainEvent.WITNESS_SEQUENCE, 4, "Nine conjunctions force the archive to treat resonance as a structure rather than a coincidence.");
        if (stage(data, id, ChainEvent.BLACK_TABLE) == 4 && known >= 6) {
            advance(player, data, ChainEvent.BLACK_TABLE, 5, "Six conjunctions give the table enough cross-prices to calculate a wager against itself.");
            CardMaster.PALE_GAMBLER.addRelation(data, id, 4);
            rewardFieldCard(player, CardMaster.PALE_GAMBLER, "The house pays a card it did not intend to lose.");
            data.setUnique(id, "black_table_complete", true);
        }
    }

    private static void rewardFieldCard(ServerPlayer player, CardMaster dealer, String message) {
        ItemStack card = new ItemStack(WardItems.SEALED_CARD.get());
        SealedCardItem.setDealer(card, dealer.id);
        if (!player.addItem(card)) player.drop(card, false);
        WardHud.message(player, Component.literal(message).withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    public static String stageText(ChainEvent event, int stage) {
        if (stage <= 0) return "Unstarted";
        return switch (event) {
            case BLACK_TABLE -> switch (stage) {
                case 1 -> "The hunter and the Gambler have noticed the same account.";
                case 2 -> "A profane ward has accepted an Eldritch stake.";
                case 3 -> "The table survived an unnameable settlement.";
                case 4 -> "The impossible result has been priced back into a hand.";
                default -> "The house has wagered against its own calculation.";
            };
            case ASH_MARGIN -> switch (stage) {
                case 1 -> "A curse was deliberately preserved.";
                case 2 -> "The preserved page has been countersigned beside a rite.";
                case 3 -> "The countersigned rite survived without a breach.";
                case 4 -> "The Curator returned a page after the truce.";
                default -> "The Notary filed contradiction as evidence.";
            };
            case WITNESS_SEQUENCE -> switch (stage) {
                case 1 -> "Conjunctions have become a documented network.";
                case 2 -> "A death-law has entered that network.";
                case 3 -> "The network survived an unnameable ward.";
                case 4 -> "Nine conjunctions have forced a structural record.";
                default -> "A second death-law has closed the witness sequence.";
            };
        };
    }

    /** A deliberately incomplete next-step hint: useful, but never a recipe. */
    public static String nextHint(ChainEvent event, int stage) {
        if (stage >= event.maxStage) return "The chain is closed.";
        return switch (event) {
            case BLACK_TABLE -> switch (stage) {
                case 0 -> "A dealer, a hunter and sufficient attention must first share a table.";
                case 1 -> "The table wants a profane stake that did not come from a card.";
                case 2 -> "The wager still lacks a ward too severe to be named politely.";
                case 3 -> "The Gambler must be made to price the result again.";
                default -> "The table requires enough resonances to calculate itself.";
            };
            case ASH_MARGIN -> switch (stage) {
                case 0 -> "The Curator must decide that a curse deserves preservation.";
                case 1 -> "Preservation alone is not admissible. Another hand must countersign it.";
                case 2 -> "A countersigned rite is only useful if its margin remains clean.";
                case 3 -> "After the truce, ask the Curator for something it would normally keep.";
                default -> "The returned page still requires filing by the Notary.";
            };
            case WITNESS_SEQUENCE -> switch (stage) {
                case 0 -> "The book needs several independent resonances before it can witness structure.";
                case 1 -> "A law of death must be written into the network.";
                case 2 -> "The network must survive a ward that should have broken ordinary testimony.";
                case 3 -> "The archive remains too sparse to prove structure.";
                default -> "One death-law is testimony. The sequence asks for corroboration.";
            };
        };
    }

    public static String summary(LockData data, UUID id, ChainEvent event) {
        int s = stage(data, id, event);
        return s + "/" + event.maxStage + (s >= event.maxStage ? " COMPLETE" : "");
    }
}

