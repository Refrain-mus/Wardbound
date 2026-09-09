package dev.marrowseal.wardbound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.net.OpenWitnessLedgerPacket;
import dev.marrowseal.wardbound.champion.SilasLore;
import dev.marrowseal.wardbound.champion.SilasProgression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Stores compact per-player Wardbound history while a Witness Ledger is actually carried/equipped. */
public final class WardHistory {
    private static final String ROOT = "WardboundHistory";
    private static final String LOOT = "Loot";
    private static final String RECORDS = "Records";
    private static final String LAST_WARD = "LastWard";
    private static final String DISCOVERIES = "Discoveries";
    private static final int MAX_LINES = 180;
    private static final String SEP = "\u001F";

    private WardHistory() {}

    public static void recordHud(ServerPlayer player, String text, WardHud.Mood mood) {
        if (player == null || text == null || text.isBlank() || !hasLedger(player)) return;
        String kind = switch (mood) {
            case REWARD -> "LOOT";
            case SHARD -> "SHARD";
            case DANGER -> "DANGER";
            case GLITCH -> "GLITCH";
            default -> "WARD";
        };
        boolean loot = mood == WardHud.Mood.REWARD || mood == WardHud.Mood.SHARD;
        addLine(player, loot ? LOOT : RECORDS, kind, text.trim());
    }

    public static void recordSavant(ServerPlayer player, String text) {
        if (player == null || text == null || text.isBlank() || !hasLedger(player)) return;
        addLine(player, RECORDS, "SAVANT", text.trim());
    }

    public static void recordSpecial(ServerPlayer player, String kind, String text) {
        if (player == null || text == null || text.isBlank() || !hasLedger(player)) return;
        addLine(player, RECORDS, kind == null || kind.isBlank() ? "WARD" : kind, text.trim());
    }

    public static void setLastWardSummary(ServerPlayer player, boolean success, String gameId, String details) {
        if (player == null || !hasLedger(player)) return;
        CompoundTag root = persistedRoot(player);
        String title = (success ? "SUCCESS" : "FAILURE") + " // " + prettify(gameId);
        root.putString(LAST_WARD, encode(success ? "SUMMARY_WIN" : "SUMMARY_LOSS",
                title + (details == null || details.isBlank() ? "" : " · " + details)));
        saveRoot(player, root);
    }

    public static boolean hasLedger(ServerPlayer player) {
        if (player == null) return false;
        if (player.getMainHandItem().is(WardItems.WITNESS_LEDGER.get())
                || player.getOffhandItem().is(WardItems.WITNESS_LEDGER.get())) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(WardItems.WITNESS_LEDGER.get())) return true;
        }
        return !CuriosCompat.findFirstEquipped(player, WardItems.WITNESS_LEDGER.get()).isEmpty();
    }

    public static void openLedger(ServerPlayer player) {
        WardAdvancements.ledgerOpened(player);
        CompoundTag root = persistedRoot(player);
        if (player.getServer() != null) {
            LockData data = LockData.get(player.getServer());
            WardAdvancements.progression(player, data.totalBeaten(player.getUUID()));
            backfillAdvancements(player, root);
        }
        Wardbound.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new OpenWitnessLedgerPacket(linesNewestFirst(root, LOOT), linesNewestFirst(root, RECORDS),
                        root.getString(LAST_WARD), grimoireLines(player, root), statsLines(player), ruleLines(player, root)));
    }


    /** Reconstruct event-driven advancement nodes from Ledger discoveries for existing saves. */
    private static void backfillAdvancements(ServerPlayer player, CompoundTag root) {
        for (MinigameType game : MinigameType.values())
            if (discovered(root, "GAME:" + game.id)) WardAdvancements.enterMinigame(player, game);
        for (ForbiddenBargain card : ForbiddenBargain.values())
            if (discovered(root, "ACCEPTED:" + card.id)) WardAdvancements.cardSigned(player, card);
        for (CardMaster master : CardMaster.values())
            if (discovered(root, "DEALER:" + master.id)) WardAdvancements.dealerSeen(player, master);
        for (MasterSignature signature : MasterSignature.values())
            if (discovered(root, "MASTER:" + signature.name())) WardAdvancements.masterSeen(player, signature);
        for (PlayerImprint.Trace trace : PlayerImprint.Trace.values())
            if (trace != PlayerImprint.Trace.UNREAD && discovered(root, "IMPRINT:" + trace.name())) WardAdvancements.imprintSeen(player, trace);
        for (AttentionSystem.Stage stage : AttentionSystem.Stage.values())
            if (stage != AttentionSystem.Stage.UNNOTICED && discovered(root, "ATTENTION_STAGE:" + stage.name())) WardAdvancements.attentionSeen(player, stage);
        if (hasDiscoveryPrefix(root, "CORRUPTION:")) WardAdvancements.corruptionSeen(player);
        if (hasDiscoveryPrefix(root, "OCCULT_CHAIN:")) WardAdvancements.occultChainSeen(player);
        if (hasDiscoveryPrefix(root, "GUARDIAN:")) WardAdvancements.guardianSeen(player);
        if (hasDiscoveryPrefix(root, "SILAS_FRAGMENT:")) WardAdvancements.silasFragmentSeen(player);
        if (hasDiscoveryPrefix(root, "CARD_MUTATION:")) WardAdvancements.cardRevisionSeen(player);
        for (String token : discoveriesWithPrefix(root, "ANOMALY:")) {
            String anomaly = token.substring("ANOMALY:".length());
            WardAdvancements.anomalySeen(player, anomaly);
        }
    }

    public static void discoverGame(ServerPlayer player, MinigameType game) {
        if (player == null || game == null || !hasLedger(player)) return;
        discover(player, "GAME:" + game.id);
    }

    public static void discoverMaster(ServerPlayer player, MasterSignature signature, int relation) {
        if (player == null || signature == null) return;
        WardAdvancements.masterSeen(player, signature);
        if (!hasLedger(player)) return;
        discover(player, "MASTER:" + signature.name());
    }

    public static void discoverAnomaly(ServerPlayer player, String id) {
        if (player == null || id == null || id.isBlank()) return;
        WardAdvancements.anomalySeen(player, id);
        if (!hasLedger(player)) return;
        discover(player, "ANOMALY:" + id.toLowerCase(java.util.Locale.ROOT));
    }

    public static void discoverCard(ServerPlayer player, ForbiddenBargain card) {
        if (player == null || card == null || !hasLedger(player)) return;
        discover(player, "CARD:" + card.id);
    }

    public static void acceptCard(ServerPlayer player, ForbiddenBargain card) {
        if (player == null || card == null) return;
        WardAdvancements.cardSigned(player, card);
        if (!hasLedger(player)) return;
        discover(player, "CARD:" + card.id);
        discover(player, "ACCEPTED:" + card.id);
        recordSpecial(player, (card.kind == ForbiddenBargain.Kind.CURSE || card.kind == ForbiddenBargain.Kind.DEATH
                || card.kind == ForbiddenBargain.Kind.COVENANT) ? "DANGER" : "WARD",
                "Signed card: " + card.title + " [" + card.kind.name().toLowerCase(java.util.Locale.ROOT) + "]");
    }

    public static void discoverConjunction(ServerPlayer player, CardConjunctions.Conjunction conjunction) {
        if (player == null || conjunction == null) return;
        WardAdvancements.conjunctionSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "CONJUNCTION:" + conjunction.id);
    }

    public static void discoverDealer(ServerPlayer player, CardMaster master) {
        if (player == null || master == null) return;
        WardAdvancements.dealerSeen(player, master);
        if (!hasLedger(player)) return;
        discover(player, "DEALER:" + master.id);
        recordSpecial(player, "WARD", "Card-master revealed: " + master.title);
    }

    public static void discoverImprint(ServerPlayer player, PlayerImprint.Trace trace) {
        if (player == null || trace == null || trace == PlayerImprint.Trace.UNREAD) return;
        WardAdvancements.imprintSeen(player, trace);
        if (!hasLedger(player)) return;
        discover(player, "IMPRINT:" + trace.name());
    }

    public static void discoverAttentionStage(ServerPlayer player, AttentionSystem.Stage stage) {
        if (player == null || stage == null || stage == AttentionSystem.Stage.UNNOTICED) return;
        WardAdvancements.attentionSeen(player, stage);
        if (!hasLedger(player)) return;
        discover(player, "ATTENTION_STAGE:" + stage.name());
    }

    public static void discoverSavantObservation(ServerPlayer player, String key) {
        if (player == null || key == null || key.isBlank() || !hasLedger(player)) return;
        discover(player, "SAVANT_OBSERVATION:" + key.toLowerCase(java.util.Locale.ROOT));
    }

    public static void discoverSilasFragment(ServerPlayer player, String key) {
        if (player == null || key == null || key.isBlank()) return;
        WardAdvancements.silasFragmentSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "SILAS_FRAGMENT:" + key.toLowerCase(java.util.Locale.ROOT));
    }

    public static void discoverMasterChapter(ServerPlayer player, CardMaster master, int chapter) {
        if (player == null || master == null || chapter <= 0 || !hasLedger(player)) return;
        discover(player, "MASTER_CHAPTER:" + master.id + ":" + chapter);
    }

    public static void discoverRivalry(ServerPlayer player, CardMaster source, CardMaster rival) {
        if (player == null || source == null || rival == null || !hasLedger(player)) return;
        discover(player, "RIVALRY:" + source.id + ":" + rival.id);
    }

    public static void discoverCardMutation(ServerPlayer player, ForbiddenBargain card, int level) {
        if (player == null || card == null || level <= 0) return;
        WardAdvancements.cardRevisionSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "CARD_MUTATION:" + card.id + ":" + Math.min(CardEvolution.MAX_REVISION, level));
    }

    public static void discoverCorruption(ServerPlayer player, CurseEvolution.Corruption corruption) {
        if (player == null || corruption == null) return;
        WardAdvancements.corruptionSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "CORRUPTION:" + corruption.id);
    }

    public static void discoverCurseMaturity(ServerPlayer player, String curse, int stage) {
        if (player == null || curse == null || curse.isBlank() || stage <= 0 || !hasLedger(player)) return;
        discover(player, "CURSE_MATURE:" + curse.toLowerCase(java.util.Locale.ROOT) + ":" + stage);
    }

    public static void discoverCurseMaturity(ServerPlayer player, String curse, String label) {
        if (player == null || curse == null || curse.isBlank() || !hasLedger(player)) return;
        discover(player, "CURSE_MATURE:" + curse.toLowerCase(java.util.Locale.ROOT) + ":"
                + (label == null ? "mature" : label.toLowerCase(java.util.Locale.ROOT).replace(' ', '_')));
    }

    public static void discoverOccultChain(ServerPlayer player, OccultChainEvents.ChainEvent event, int stage) {
        if (player == null || event == null || stage <= 0) return;
        WardAdvancements.occultChainSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "OCCULT_CHAIN:" + event.id + ":" + Math.min(event.maxStage, stage));
    }

    public static void discoverGuardian(ServerPlayer player, String entityId) {
        if (player == null || entityId == null || entityId.isBlank()) return;
        WardAdvancements.guardianSeen(player);
        if (!hasLedger(player)) return;
        discover(player, "GUARDIAN:" + entityId.toLowerCase(java.util.Locale.ROOT));
    }

    private static void discover(ServerPlayer player, String token) {
        CompoundTag root = persistedRoot(player);
        ListTag list = root.getList(DISCOVERIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (token.equals(list.getString(i))) return;
        list.add(StringTag.valueOf(token));
        root.put(DISCOVERIES, list);
        saveRoot(player, root);
        dev.marrowseal.wardbound.item.WitnessLedgerItem.triggerDiscovery(player);
    }

    private static boolean discovered(CompoundTag root, String token) {
        ListTag list = root.getList(DISCOVERIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (token.equals(list.getString(i))) return true;
        return false;
    }

    private static List<String> grimoireLines(ServerPlayer player, CompoundTag root) {
        List<String> out = new ArrayList<>();
        LockData data = player.getServer() == null ? null : LockData.get(player.getServer());
        out.add("— SEAL DISCIPLINES —");
        for (MinigameType game : MinigameType.values()) {
            String token = "GAME:" + game.id;
            if (!discovered(root, token)) {
                out.add("[◇] ???");
                continue;
            }
            if (data == null) out.add("[◆] " + prettify(game.id));
            else out.add("[◆] " + prettify(game.id) + " · " + MinigameMastery.compact(data, player.getUUID(), game)
                    + SEP + "GAME" + SEP + game.ordinal() + SEP + MinigameMastery.tierLabel(data, player.getUUID(), game)
                    + SEP + MinigameMastery.detail(data, player.getUUID(), game));
        }
        out.add("— MASTER HANDS —");
        for (MasterSignature sig : MasterSignature.values()) {
            String token = "MASTER:" + sig.name();
            if (discovered(root, token)) {
                int relation = data == null ? 0 : data.relation(player.getUUID(), sig);
                String mood = relation >= 12 ? "familiar" : relation <= -12 ? "resentful" : relation > 2 ? "inclined" : relation < -2 ? "cold" : "unread";
                out.add("[✦] " + prettify(sig.name()) + " · " + mood + " (" + (relation >= 0 ? "+" : "") + relation + ")");
            } else out.add("[·] ???");
        }
        out.add("— ENCOUNTERS —");
        String[][] anomalies = {
                {"possessed", "Possessed Ward"}, {"eldritch", "Eldritch Chain"}, {"cthulhu", "Cthulhu's Game"},
                {"living", "Living Ward"}, {"unsigned", "Unsigned Seal"}, {"attention", "The Attention"},
                {"gauntlet", "Gauntlet"}, {"curse_escalation", "Matured Curse"}, {"hidden_page", "Self-Written Page"}
        };
        for (String[] a : anomalies) out.add(discovered(root, "ANOMALY:" + a[0]) ? "[☽] " + a[1] : "[·] ???");
        out.add("— CARD MASTERS —");
        for (CardMaster dealer : CardMaster.values()) {
            boolean seen = discovered(root, "DEALER:" + dealer.id) || dealer.known(data == null ? LockData.get(player.getServer()) : data, player.getUUID());
            out.add(seen ? "[✒] " + dealer.title : "[·] ???");
        }
        out.add("— BARGAINS & LAWS —");
        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            if (card == ForbiddenBargain.REFRESH_HAND) continue;
            boolean seen = discovered(root, "CARD:" + card.id);
            boolean signed = discovered(root, "ACCEPTED:" + card.id);
            String kind = card.kind.name().toLowerCase(java.util.Locale.ROOT);
            String display = signed ? "[■] " + card.title + " · identified " + kind
                    : seen ? "[▣] " + card.title + " · witnessed " + kind : "[□] ???";
            String state = signed ? "SIGNED" : seen ? "SEEN" : "HIDDEN";
            String runtime = signed && data != null ? cardRuntimeStatus(card, data, player.getUUID(), player) : seen ? "Witnessed, but not signed." : "Undiscovered.";
            String terms = signed && data != null ? CardBranches.activeDescription(data, player.getUUID(), card) : card.debtText;
            if (signed && data != null && CardEvolution.activeVariant(data, player.getUUID(), card) > 0)
                runtime = CardBranches.title(card, CardBranches.alternate(data, player.getUUID(), card) ? 2 : 1) + " / " + runtime;
            out.add(display + SEP + "CARD" + SEP + card.id + SEP + state + SEP + runtime + SEP + terms);
        }
        out.add("— CONJUNCTIONS —");
        for (CardConjunctions.Conjunction conjunction : CardConjunctions.Conjunction.values()) {
            boolean seen = data != null && CardConjunctions.known(data, player.getUUID(), conjunction);
            out.add(seen ? "[✦] " + conjunction.title + " · " + conjunction.formula + " · " + conjunction.effect : "[·] ???");
        }

        out.add("— ATTENTION RECORD —");
        for (AttentionSystem.Stage stage : AttentionSystem.Stage.values()) {
            if (stage == AttentionSystem.Stage.UNNOTICED) continue;
            out.add(discovered(root, "ATTENTION_STAGE:" + stage.name())
                    ? "[◉] " + stage.title + " · " + AttentionSystem.stageRule(stage) : "[○] ???");
        }

        out.add("— BEHAVIOURAL TRACES —");
        for (PlayerImprint.Trace trace : PlayerImprint.Trace.values()) {
            if (trace == PlayerImprint.Trace.UNREAD) continue;
            boolean seen = discovered(root, "IMPRINT:" + trace.name());
            out.add(seen ? "[⌁] " + trace.title + " · " + trace.detail : "[·] ???");
        }

        out.add("— ADAPTIVE MINIGAME PROFILE —");
        if (data == null || MinigameSkillProfile.observedAxes(data, player.getUUID()) == 0) {
            out.add("[·] The ward has not observed enough resolved play.");
        } else {
            out.add("[◈] " + MinigameSkillProfile.compact(data, player.getUUID()));
            out.add("    " + MinigameSkillProfile.detail(data, player.getUUID()));
        }
        int deceptionMask = data == null ? 0 : data.uniqueInt(player.getUUID(), "minigame_deception_mask");
        for (MinigameDeception.Mode mode : MinigameDeception.Mode.values()) {
            if (mode == MinigameDeception.Mode.NONE) continue;
            int bit = 1 << (mode.ordinal() - 1);
            out.add((deceptionMask & bit) != 0
                    ? "[◌] " + prettify(mode.name()) + " · reliable channel: " + mode.reliableChannel
                    : "[·] deception: ???");
        }
        int hybridMask = data == null ? 0 : data.uniqueInt(player.getUUID(), "hybrid_round_mask");
        for (HybridRound.Mode mode : HybridRound.Mode.values()) {
            if (mode == HybridRound.Mode.NONE) continue;
            int bit = 1 << (mode.ordinal() - 1);
            out.add((hybridMask & bit) != 0 ? "[⟁] " + prettify(mode.name()) : "[·] hybrid: ???");
        }

        out.add("— MASTER HISTORIES —");
        for (CardMaster master : CardMaster.values()) {
            int chapter = data == null ? 0 : MasterStory.chapter(data, player.getUUID(), master);
            if (chapter <= 0) { out.add("[·] ???"); continue; }
            out.add("[✒] " + master.title + " · Chapter " + chapter + "/" + MasterStory.MAX_CHAPTER + " · " + MasterStory.chapterName(master, chapter));
            out.add("    " + MasterStory.chapterText(master, chapter));
        }
        if (hasDiscoveryPrefix(root, "RIVALRY:"))
            out.add("[⚯] Rival counter-offers documented · " + (data == null ? "relations unavailable" : MasterStory.rivalrySummary(data, player.getUUID())));
        else out.add("[·] Rivalry: ???");

        out.add("— CARD REVISIONS —");
        boolean anyMutation = false;
        if (data != null) for (ForbiddenBargain card : ForbiddenBargain.values()) {
            if (!CardEvolution.evolvable(card)) continue;
            int signedCount = CardEvolution.signatureCount(data, player.getUUID(), card);
            if (signedCount <= 0) continue;
            int level = CardEvolution.activeVariant(data, player.getUUID(), card);
            anyMutation = true;
            if (CardEvolution.palimpsest(data, player.getUUID(), card))
                out.add("[✦] " + CardEvolution.variantTitle(card, level) + " · PALIMPSEST · " + CardEvolution.progress(data, player.getUUID(), card));
            else if (level > 0)
                out.add("[↻] " + CardEvolution.variantTitle(card, level) + " · " + CardEvolution.progress(data, player.getUUID(), card));
            else
                out.add("[·] " + card.title + " · clause still stable · " + CardEvolution.progress(data, player.getUUID(), card));
        }
        if (!anyMutation) out.add("[·] No repeatable clause has begun revising yet.");

        out.add("— CURSE CORRUPTIONS —");
        boolean anyCorruption = false;
        if (data != null) for (CurseEvolution.Corruption corruption : CurseEvolution.Corruption.values()) {
            if (!CurseEvolution.known(data, player.getUUID(), corruption)) continue;
            anyCorruption = true;
            int cstage = CurseEvolution.active(data, player.getUUID(), corruption)
                    ? CurseEvolution.stage(data, player.getUUID(), corruption, player.level().getGameTime()) : 0;
            out.add("[☠] " + corruption.title + (cstage > 0 ? " · stage " + cstage + "/3" : " · dormant")
                    + " · " + corruption.formula + " · " + corruption.effect);
        }
        if (!anyCorruption) out.add("[·] No interacting curses documented.");

        out.add("— DEATH RESONANCE —");
        if (data == null || DeathResonance.score(data, player.getUUID()) <= 0) out.add("[·] No active death resonance documented.");
        else out.add("[☾] " + DeathResonance.detail(data, player.getUUID())
                + " · Higher resonance strengthens parts of existing Death Laws while drawing future Death Hands closer.");

        out.add("— OCCULT CHAINS —");
        for (OccultChainEvents.ChainEvent event : OccultChainEvents.ChainEvent.values()) {
            int stage = data == null ? 0 : OccultChainEvents.stage(data, player.getUUID(), event);
            if (stage > 0) {
                out.add("[⛓] " + event.title + " · " + stage + "/" + event.maxStage + " · " + OccultChainEvents.stageText(event, stage));
                out.add("    Margin hint: " + OccultChainEvents.nextHint(event, stage));
            } else out.add("[·] ???");
        }

        out.add("— GUARDIAN TAXONOMY —");
        List<String> guardians = discoveriesWithPrefix(root, "GUARDIAN:");
        if (guardians.isEmpty()) out.add("[·] No guardian forms catalogued.");
        else {
            int shownGuardians = 0;
            for (String token : guardians) {
                if (shownGuardians++ >= 64) break;
                out.add("[⚔] " + prettify(token.substring("GUARDIAN:".length()).replace(':', '_')));
            }
            if (guardians.size() > 64) out.add("[⚔] +" + (guardians.size() - 64) + " further guardian forms in the record.");
        }

        out.add("— SAVANT OBSERVATIONS —");
        List<String> observations = discoveriesWithPrefix(root, "SAVANT_OBSERVATION:");
        if (observations.isEmpty()) out.add("[·] The Savant has not established a durable observation.");
        else {
            int shown = 0;
            for (String token : observations) {
                if (shown++ >= 12) break;
                out.add("[◌] " + prettify(token.substring("SAVANT_OBSERVATION:".length())));
            }
            if (observations.size() > 12) out.add("[◌] +" + (observations.size() - 12) + " further observations in the record.");
        }

        if (data != null && (SilasProgression.maxChapter(data, player.getUUID()) >= 3
                || SilasLore.mask(data, player.getUUID()) != 0
                || data.uniqueInt(player.getUUID(), "silas_stage_seen_1") > 0)) {
            out.add("— UNRESOLVED TESTIMONIES —");
            int silasStage = SilasProgression.stage(data, player.getUUID());
            out.add("[†] " + SilasProgression.ledgerIdentity(silasStage));
            int knownFragments = 0;
            for (SilasLore.Fragment fragment : SilasLore.Fragment.values()) {
                if (SilasLore.known(data, player.getUUID(), fragment)) {
                    knownFragments++;
                    out.add("[▤] " + fragment.title + " · " + fragment.family.title
                            + SEP + "SILAS" + SEP + fragment.id + SEP + fragment.family.title
                            + SEP + SilasLore.translationKey(fragment));
                } else if (fragment.minStage <= silasStage) {
                    out.add("[□] unread testimony · " + fragment.family.title);
                }
            }
            if (data.uniqueInt(player.getUUID(), "silas_unconcluded_seen") > 0) {
                out.add("[◌] OBSERVATION // HIS DEATH DID NOT COMPLETE");
                out.add("    The fatal threshold was crossed. No ending followed it.");
            }
            if (data.uniqueInt(player.getUUID(), "silas_untargeted_seen") > 0) {
                out.add("[◌] OBSERVATION // THE HOSTILE DO NOT SEE HIM");
                out.add("    Hostile attention passes over Silas until he deliberately declares himself present.");
            }
            if (data.uniqueInt(player.getUUID(), "silas_testimony_assembled") > 0) {
                out.add("[◆] TESTIMONY COMPLETE // 12/12");
            } else {
                out.add("[◇] PHYSICAL SET // " + SilasLore.physicalCount(player) + "/12");
            }
            if (data.uniqueInt(player.getUUID(), "silas_ring_received") > 0) {
                out.add("[○] THE WORN SCABBARD RING // recognised call established");
                int stands=dev.marrowseal.wardbound.champion.SilasMasterEncounter.distinctStands(data,player.getUUID());
                out.add("[◌] MASTER RETURNS // "+stands+"/2 distinct old fears faced beside Silas");
            }
            if (knownFragments == 0) out.add("[·] The ledger has not yet secured a fragment bearing this witness.");
        }

        out.add("— CTHULHU EXAM SYLLABUS —");
        boolean cthulhuComplete = discovered(root, "ANOMALY:cthulhu_completed");
        for (MinigameType passageGame : MinigameType.values()) {
            if (passageGame == MinigameType.CTHULHUS_GAME) continue;
            out.add(cthulhuComplete ? "[◉] " + prettify(passageGame.id) + " · eligible exam echo" : "[○] ???");
        }
        out.add(cthulhuComplete ? "[◎] The Final Mouth" : "[○] ???");
        if (cthulhuComplete && data != null) out.add("[◎] Final-exam depth " + MinigameMastery.examTier(data, player.getUUID())
                + " · all 26 ordinary disciplines are in the seeded passage pool; breadth comes from mastery.");
        return Collections.unmodifiableList(out);
    }


    /**
     * Progressive rules reference for the Witness Ledger. Unlike discoveries, these
     * notes are derived from live progression so a player can never permanently miss
     * an explanation because the Ledger was not in inventory at the unlock moment.
     */
    private static List<String> ruleLines(ServerPlayer player, CompoundTag root) {
        List<String> out = new ArrayList<>();
        if (player == null || player.getServer() == null) return out;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        int beaten = data.totalBeaten(id);

        ruleSection(out, "THE LEDGER",
                "The Ledger is the authoritative reference for Wardbound rules. New sections appear when the corresponding system becomes eligible.",
                "Loot and Chronicle record events only while the Ledger is carried, held or worn. Rules and live Status are reconstructed from persistent progression whenever the book opens.");
        ruleSection(out, "WARD BASICS",
                "A warded container commits to a minigame when opened. Success resolves the seal and scales the reward; failure may reduce or damage the contents according to the ward and configuration.",
                "ESCAPE RULE: leaving an active minigame counts as failure. Closing the screen cannot preserve a puzzle state for a safer retry.",
                "Clean and Perfect resolutions reward execution quality. Mastery records familiarity but never makes the authored puzzle weaker.");
        ruleSection(out, "LOOT & AIDS",
                "Only the chest aid actually used from the interaction hand is consumed. Ward Splinter primes +50% final ward loot; Eldritch Shard duplicates final contents. Only one shard attunement may be active at a time.",
                "Cyclopean Lens reveals mechanism information. Cold Shim removes an opening stage. Slow Ember adds time. Their exact availability still follows progression and loot gates.");

        if (beaten >= WardConfig.normalCardsAfterBeaten) {
            ruleSection(out, "CARD HANDS",
                    "Post-ward hands offer a temporary set of cards. Signing a card affects only that choice; cards are never deleted from the global pool by a hand action.",
                    "Refresh effects, hand-size laws and later hand-control cards alter offers, not the global catalogue.");
        }
        if (beaten >= WardConfig.normalCardsAfterBeaten) ruleSection(out, "CARD ECOLOGY // FAMILY HEAT",
                "The House remembers which card families you actually SIGN. Familiarity is permanent history; Heat is short-term pressure that cools only when a genuinely new hand is dealt.",
                "Low Heat can softly echo a preferred family. High Heat makes the House introduce more counter-family and unfamiliar ink inside the SAME legal rarity/shelf pool. It never unlocks a rarity early, forces a specific card, deletes a favorite family or applies a hidden stat penalty.",
                "Cuts, Red Pen and refreshes edit the current hand but do not advance Heat cooling, so hand-control tools cannot be spammed to erase the House's memory.",
                "The Analysis page exposes the hottest families, long-term familiarity and the House's current response so the system can be learned rather than guessed.");
        if (beaten >= ForbiddenBargain.THE_RED_PEN.minResolved) ruleSection(out, "HAND EDITING // THE RED PEN",
                "The Red Pen removes itself and one chosen card from the CURRENT hand only. The global catalogue is untouched, so the eliminated card can appear in later hands.",
                "Cutting a harmful Curse, Death, Debt or Scar is already the advantage. The cut itself grants no hidden stat bonus, extra loot or permanent rarity manipulation.");
        if (beaten >= ForbiddenBargain.DEBT_ECHO.minResolved) ruleSection(out, "ECHO / NEXT-CARD AMPLIFICATION",
                "Echo cards are ONE-SHOT. Each Echo waits for the next signed card of its matching family, strengthens that resolution, then is consumed immediately.",
                "Choosing cards from other families does not spend a waiting Echo. Example: Epic Echo can wait through Wager or Curse choices until an Epic is actually signed.",
                "Echo never permanently doubles a family. A fresh Echo must be signed for every future amplified card.");
        if (beaten >= WardConfig.fieldCardAfterBeaten) ruleSection(out, "FIELD CARDS",
                "Field cards can be carried out of the immediate post-ward moment. Sealed Cards are opened later, away from combat, and still obey the same card-family and progression rules.");
        if (beaten >= WardConfig.chainAfterBeaten) ruleSection(out, "SEAL CHAINS",
                "A chain is a linked sequence of wards. Following the trail to its last seal can pay unique rewards such as the Sealwright's Key. Breaking a chain abandons its accumulated promise.");
        if (beaten >= WardConfig.masterCardsAfterBeaten) ruleSection(out, "MASTER HANDS & RELATION",
                "Master cards carry a maker's signature and relationship. Familiarity changes presentation, counter-offers and authored clauses; it does not silently replace the base reward rules.",
                "Rival masters can answer one another later in progression. The Grimoire records discovered histories and relation state.");
        if (beaten >= WardConfig.contractCardsAfterBeaten) ruleSection(out, "CONTRACTS",
                "Contracts bind a concrete obligation to a reward. Objective progress is server-authoritative; cosmetic chain-breaking or duplicated effects do not multiply objective credit unless a rule explicitly says so.");
        if (beaten >= WardConfig.curseCardsAfterBeaten) ruleSection(out, "CURSES & SCARS",
                "Curses are deliberately harder to escape than ordinary negative cards and can mature or combine into corruptions. Remedies remove only the clauses they explicitly name.",
                "Red Pen can cut a harmful card from the current offer, but Curse-heavy hands have a lower chance to offer that escape.");
        if (beaten >= WardMeasureSystem.UNLOCK_AFTER) ruleSection(out, "HOUSE MEASURES & CUTS",
                "After this progression band, eligible ordinary signed wards form a recurring three-ward line: Opening, Pressure, then Verdict. Unsigned seals, Cthulhu, Eldritch/Gauntlet stages, possessed wards, chains and depth/reseal loops do not consume the cadence.",
                "Verdict does not invent a foreign puzzle. It applies one clause you already know from Wardbound's authored minigame rules: Reversed Face, Loaded Tempo or Binding Clause.",
                "Clean and Perfect work across all three wards earns leverage. A strong Measure can bank one or two Cuts, to a maximum reserve of " + WardMeasureSystem.MAX_CUTS + ".",
                "FOLD: refusing an entire non-binding ordinary card hand can also bank one Cut if the reserve has room. You give up the current hand now to gain control over a later hand.",
                "CUT: in a later ordinary hand, spend one Cut to redraw exactly ONE chosen visible offer. Only that current hand changes; the global card catalogue is untouched. The replacement is rarity-banded so Cut is a mulligan, not an upgrade exploit.",
                "Cuts are ONE-SHOT. A Cut is consumed only after the server finds and installs a valid replacement. Forced Curse and Death hands cannot spend Cuts.");
        if (beaten >= WardConfig.deceptionUnlockAfter) ruleSection(out, "DECEPTION",
                "Deception may lie through presentation channels, never through the actual puzzle state. Each deception has at least one reliable information channel; discovered channels are recorded in the Grimoire.");
        if (beaten >= WardConfig.ritualCardsAfterBeaten) ruleSection(out, "RITUALS",
                "Rituals track authored actions rather than raw event spam. Indirect area/chain actions are normalized so one physical action cannot fraudulently complete several ritual steps.");
        if (beaten >= WardConfig.bonusFreshRollAfterBeaten) ruleSection(out, "BONUS OFFER",
                "From this progression band, every third eligible card-selection screen gains +1 offer. The cadence is consumed by eligible hands and cannot be reset by repeatedly refreshing the same screen.",
                "Narrow-hand laws may still suppress the bonus. Fourth/Fifth-card laws and the progression bonus are separate systems, so positive and negative hand rules can counter one another.");
        if (beaten >= WardConfig.hybridUnlockAfter) ruleSection(out, "HYBRID ROUNDS",
                "A hybrid round temporarily borrows a second discipline while the host mechanism is frozen. Clearing it grants its small bonus, then returns control to the original ward.");
        if (beaten >= WardConfig.mutationTier1AfterBeaten) ruleSection(out, "CARD REVISIONS",
                "Repeatable clauses can revise after repeated signatures. Revision level belongs to that specific card; it is not the same thing as a one-shot Echo multiplier.");
        if (beaten >= WardConfig.epicCardsAfterBeaten) ruleSection(out, "EPIC LAWS",
                "Epic cards are rarer and can carry stronger persistent rules. Their Echo is still one-use and is consumed only by the next signed Epic card.");
        if (beaten >= WardConfig.covenantCardsAfterBeaten) ruleSection(out, "COVENANTS",
                "Covenants are long-form obligations with stronger consequences. Their objective identity is not duplicated by Echo; the effect is strengthened without creating a second fake covenant.");
        if (beaten >= WardConfig.uniqueCardsAfterBeaten) ruleSection(out, "UNIQUE LAWS",
                "Unique cards establish singular laws. If a Unique law has a strengthened form, Echo upgrades the effective resolution instead of installing duplicate copies of the same world-law.");
        if (beaten >= WardConfig.eldritchAfterBeaten) ruleSection(out, "ELDRITCH CHAINS",
                "Eldritch chains and occult events build persistent stages. The Grimoire reveals stages only after they have been witnessed; margin hints point toward the next stage without exposing the complete solution.");
        if (beaten >= WardConfig.eyeAfterBeaten) ruleSection(out, "THE SAVANT",
                "The Savant can establish durable observations about wards, makers and minigames. Its statements are recorded separately from ordinary Chronicle noise once discovered.");
        if (beaten >= WardConfig.deathCardsAfterBeaten) ruleSection(out, "DEATH LAWS",
                "Death cards can feed Death Resonance. Death Echo itself is not treated as a Death event: resonance and hostile imprints occur only when the real amplified Death card is signed.",
                "Corpse Ledger records a death debt at the fallen position. Death Resonance can strengthen compatible Death laws but does not make Echo permanent.");
        if (beaten >= WardConfig.cthulhuAfterBeaten) ruleSection(out, "CTHULHU'S GAME",
                "Cthulhu's Game is an adaptive final examination assembled from the ordinary disciplines and a Final Mouth. Breadth comes from the player's observed mastery profile; the individual disciplines retain their authored rules.");

        if (discovered(root, "CARD:403") || discovered(root, "CARD:404") || discovered(root, "CARD:405")
                || discovered(root, "CARD:406") || discovered(root, "CARD:407")) {
            ruleSection(out, "FOURTH-WAVE SYNERGIES",
                    "Mirror Writ and Return to Sender cooperate: the reflection law improves the return window instead of competing for the same projectile event.",
                    "Borrowed Anatomy remembers the last eligible hostile archetype. Ninth Margin can expose that memory as an active hotbar ability; Stolen Countenance remains a separate beneficial-effect copy system.",
                    "Chain of Custody breaks connected blocks through normal loot, XP and durability pathways. Mining bonuses may profit from those blocks, but ritual/objective credit is intentionally normalized to prevent multiplication exploits.");
        }

        boolean fifthWaveSeen = false;
        for (int cardId = 408; cardId <= 437 && !fifthWaveSeen; cardId++) fifthWaveSeen = discovered(root, "CARD:" + cardId);
        if (fifthWaveSeen) {
            ruleSection(out, "FIFTH-WAVE WITNESS LAWS",
                    "Some fifth-wave cards listen to ward performance instead of raw world events. Clean, Perfect and failed resolutions are recorded once by the server and cannot be multiplied by reopening a screen.",
                    "The Second Attempt helps the next ordinary ward after a failure; Three Clean Lines banks at most two Mercies. Black Orrery and Last Procession have their own Perfect-resolution laws.",
                    "Reactive world laws use explicit cooldowns and single-consumption windows. Chain of Custody's synthetic block breaks do not arm Iron Aftertaste or Timber Echo, preventing one crouched break from multiplying triggers.",
                    "The Black Study, Wound Clock and Hunger of Order are lesser burdens. Their named remedies, Debt Unwritten and Absolution can remove them; Death laws remain outside ordinary remedy logic.");
        }

        int next = nextRuleUnlock(beaten);
        if (next > beaten) {
            out.add("— SEALED MARGIN —");
            out.add("[LOCKED] Another rules section will become legible after " + next + " resolved wards. Current standing: " + beaten + ".");
        } else {
            out.add("— COMPLETE MARGIN —");
            out.add("[OPEN] No progression-gated rules section remains sealed. Discovery-specific lore can still appear in the Grimoire.");
        }
        return Collections.unmodifiableList(out);
    }

    private static void ruleSection(List<String> out, String title, String... lines) {
        out.add("— " + title + " —");
        if (lines == null) return;
        for (String line : lines) if (line != null && !line.isBlank()) out.add(line);
    }

    private static int nextRuleUnlock(int beaten) {
        int[] thresholds = { WardConfig.normalCardsAfterBeaten, ForbiddenBargain.THE_RED_PEN.minResolved, ForbiddenBargain.DEBT_ECHO.minResolved,
                WardConfig.fieldCardAfterBeaten, WardConfig.chainAfterBeaten,
                WardConfig.masterCardsAfterBeaten, WardConfig.contractCardsAfterBeaten, WardConfig.curseCardsAfterBeaten,
                WardMeasureSystem.UNLOCK_AFTER, WardConfig.deceptionUnlockAfter, WardConfig.ritualCardsAfterBeaten, WardConfig.bonusFreshRollAfterBeaten,
                WardConfig.hybridUnlockAfter, WardConfig.mutationTier1AfterBeaten, WardConfig.epicCardsAfterBeaten,
                WardConfig.covenantCardsAfterBeaten, WardConfig.uniqueCardsAfterBeaten, WardConfig.eldritchAfterBeaten,
                WardConfig.eyeAfterBeaten, WardConfig.deathCardsAfterBeaten, WardConfig.cthulhuAfterBeaten };
        int next = Integer.MAX_VALUE;
        for (int threshold : thresholds) if (threshold > beaten && threshold < next) next = threshold;
        return next == Integer.MAX_VALUE ? beaten : next;
    }

    /** Live status schematic payload. Each row is CATEGORY|LABEL|VALUE|DETAIL. */
    public static List<String> statsLines(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        if (player == null || player.getServer() == null) return out;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        String dim = player.level().dimension().location().toString();
        int[] samples = data.directorSamples(id, dim);
        int avg = 0;
        for (int n : samples) avg += n;
        if (samples.length > 0) avg /= samples.length;
        String band = data.directorBand(id, dim) > 0 ? "strong" : data.directorBand(id, dim) < 0 ? "strained" : "steady";

        // Analysis rows share the live packet but are partitioned into their own
        // Ledger tab on the client. Status therefore stays actionable instead of
        // becoming a second encyclopedia.
        addAnalysisSchematic(out, player, data, id);

        // Put the immediately actionable state first. The remainder of this payload
        // is the deeper diagnostic/status view.
        addStatusOverview(out, player, data, id);
        addActiveLawSchematic(out, player, data, id);

        out.add(stat("CORE", "Health", String.format(java.util.Locale.ROOT, "%.1f / %.1f", player.getHealth(), player.getMaxHealth()),
                "Bargain debt: " + data.heartDebt(id) + " · Death-buried: " + data.deathHeartDebt(id) + " heart(s)"));
        out.add(stat("CORE", "Food", player.getFoodData().getFoodLevel() + " / 20",
                String.format(java.util.Locale.ROOT, "Saturation %.1f", player.getFoodData().getSaturationLevel())));
        addEffectSourceStats(out, player, data, id);
        out.add(stat("WARD", "Standing", data.totalBeaten(id) + " seals broken", "Director: " + band + (samples.length > 0 ? " · avg " + avg : "")));
        int beaten = data.totalBeaten(id);
        String nextUnlock;
        if (beaten < WardConfig.normalCardsAfterBeaten) nextUnlock = "Card table at " + WardConfig.normalCardsAfterBeaten;
        else if (beaten < WardConfig.afflictionAfterBeaten) nextUnlock = "Afflictions at " + WardConfig.afflictionAfterBeaten;
        else if (beaten < WardConfig.fieldCardAfterBeaten) nextUnlock = "Field cards at " + WardConfig.fieldCardAfterBeaten;
        else if (beaten < WardConfig.chainAfterBeaten) nextUnlock = "Seal chains at " + WardConfig.chainAfterBeaten;
        else if (beaten < WardConfig.masterCardsAfterBeaten) nextUnlock = "Master cards at " + WardConfig.masterCardsAfterBeaten;
        else if (beaten < WardConfig.contractCardsAfterBeaten) nextUnlock = "Contract hands at " + WardConfig.contractCardsAfterBeaten;
        else if (beaten < WardConfig.possessedAfterBeaten) nextUnlock = "Possessed wards at " + WardConfig.possessedAfterBeaten;
        else if (beaten < WardConfig.curseCardsAfterBeaten) nextUnlock = "Curse hands at " + WardConfig.curseCardsAfterBeaten;
        else if (beaten < WardMeasureSystem.UNLOCK_AFTER) nextUnlock = "House Measures & Cuts at " + WardMeasureSystem.UNLOCK_AFTER;
        else if (beaten < WardConfig.emberAfterBeaten) nextUnlock = "Nether ember eligibility at " + WardConfig.emberAfterBeaten;
        else if (beaten < WardConfig.unsignedAfterBeaten) nextUnlock = "Unsigned seals at " + WardConfig.unsignedAfterBeaten;
        else if (beaten < WardConfig.ritualCardsAfterBeaten) nextUnlock = "Ritual hands at " + WardConfig.ritualCardsAfterBeaten;
        else if (beaten < WardConfig.mutationTier1AfterBeaten) nextUnlock = "Mutation I at " + WardConfig.mutationTier1AfterBeaten;
        else if (beaten < WardConfig.epicCardsAfterBeaten) nextUnlock = "Epic cards at " + WardConfig.epicCardsAfterBeaten;
        else if (beaten < WardConfig.covenantCardsAfterBeaten) nextUnlock = "Covenant hands at " + WardConfig.covenantCardsAfterBeaten;
        else if (beaten < WardConfig.uniqueCardsAfterBeaten) nextUnlock = "Unique laws at " + WardConfig.uniqueCardsAfterBeaten;
        else if (beaten < WardConfig.mutationTier2AfterBeaten) nextUnlock = "Mutation II at " + WardConfig.mutationTier2AfterBeaten;
        else if (beaten < WardConfig.eldritchAfterBeaten) nextUnlock = "Eldritch chains at " + WardConfig.eldritchAfterBeaten;
        else if (beaten < WardConfig.eyeAfterBeaten) nextUnlock = "Savant eligibility at " + WardConfig.eyeAfterBeaten;
        else if (beaten < WardConfig.mutationTier3AfterBeaten) nextUnlock = "Mutation III at " + WardConfig.mutationTier3AfterBeaten;
        else if (beaten < WardConfig.deathCardsAfterBeaten) nextUnlock = "Death hands at " + WardConfig.deathCardsAfterBeaten;
        else if (beaten < WardConfig.cthulhuAfterBeaten) nextUnlock = "Cthulhu wards at " + WardConfig.cthulhuAfterBeaten;
        else nextUnlock = "All special ward classes unlocked";
        out.add(stat("WARD", "Progression", beaten + " resolved", nextUnlock + " · fresh-table bonuses at " + WardConfig.bonusFreshRollAfterBeaten));
        if (WardMeasureSystem.unlocked(data, id)) {
            int nextMeasure = WardMeasureSystem.nextStage(data, id);
            out.add(stat("WARD/DECK", "House Measure", WardMeasureSystem.stageName(nextMeasure) + " next · Cuts "
                            + WardMeasureSystem.cuts(data, id) + "/" + WardMeasureSystem.MAX_CUTS,
                    WardMeasureSystem.cycles(data, id) + " measure(s) closed · current margin " + WardMeasureSystem.points(data, id)
                            + "/6 · " + WardMeasureSystem.folds(data, id) + " ordinary hand(s) folded · "
                            + WardMeasureSystem.spentCuts(data, id) + " Cut(s) spent."));
        }
        int adeptDisciplines = MinigameMastery.masteredDisciplines(data, id);
        int totalAttempts = 0, totalWins = 0, totalPerfect = 0, corruptSeen = 0;
        for (MinigameType mg : MinigameType.values()) {
            if (mg == MinigameType.CTHULHUS_GAME) continue;
            totalAttempts += MinigameMastery.attempts(data, id, mg);
            totalWins += MinigameMastery.wins(data, id, mg);
            totalPerfect += MinigameMastery.perfectWins(data, id, mg);
            corruptSeen += MinigameMastery.corruptionEncounters(data, id, mg);
        }
        MinigameType strongestGame = MinigameMastery.strongestDiscipline(data, id);
        MinigameType weakestGame = MinigameMastery.weakestDiscipline(data, id);
        out.add(stat("MASTERY", "Seal Mastery", adeptDisciplines + " adept discipline(s)",
                totalWins + "/" + totalAttempts + " total wins · " + totalPerfect + " perfect · " + corruptSeen + " corrupted encounters · strongest: "
                        + prettify(strongestGame.id) + " " + MinigameMastery.tierName(MinigameMastery.tier(data, id, strongestGame))
                        + " · weakest observed: " + prettify(weakestGame.id)
                        + ". Cthulhu's Game uses the observed weakness as part of its exam; mastery never lowers base difficulty."));
        out.add(stat("LOOT", "Loot Ceiling", String.format(java.util.Locale.ROOT, "x%.2f", ChestValuator.progressionLootCap(beaten)),
                "The reward ceiling rises gradually with resolved wards in large modpacks."));
        float dealScale = CardBalance.bargainProgression(beaten);
        out.add(stat("CARDS", "Card Table",
                beaten < WardConfig.normalCardsAfterBeaten ? "Locked" : String.format(java.util.Locale.ROOT, "%.2f%%", WardConfig.forbiddenBargainChance * dealScale * 100f),
                beaten < WardConfig.normalCardsAfterBeaten
                        ? "Ordinary post-chest card hands unlock after " + WardConfig.normalCardsAfterBeaten + " resolved wards."
                        : ("Normal bargain chance ramps toward the configured %.0f%% by " + CardBalance.BARGAIN_RAMP_3 + " resolved wards.")
                                .formatted(WardConfig.forbiddenBargainChance * 100f)));
        int completedObjectives = data.uniqueInt(id, "objectives_completed_total");
        out.add(stat("OBJECTIVE", "Objective Record", completedObjectives + " fulfilled",
                data.uniqueInt(id, "objectives_completed_contract") + " contracts · "
                        + data.uniqueInt(id, "objectives_completed_ritual") + " rituals · "
                        + data.uniqueInt(id, "objectives_completed_covenant") + " covenants · swift clauses "
                        + data.uniqueInt(id, "contract_fine_prints") + " (" + data.uniqueInt(id, "contract_exacting_clauses") + " exacting) · pure rites "
                        + data.uniqueInt(id, "ritual_pure_completions") + " · legible scarred rites " + data.uniqueInt(id, "ritual_legible_completions") + "."));
        out.add(stat("RELIC", "Rare Relics", beaten >= WardConfig.eyeAfterBeaten ? "Savant eligible" : "Savant locked",
                "Slow Ember: Nether, " + WardConfig.emberAfterBeaten + "+ wards, x" + String.format(java.util.Locale.ROOT, "%.2f", WardConfig.emberLootThreshold)
                        + " · Savant: End, " + WardConfig.eyeAfterBeaten + "+ wards, x" + String.format(java.util.Locale.ROOT, "%.2f", WardConfig.eyeLootThreshold)));

        int pact = data.masterPact(id);
        String pactName = pact == MasterSignature.CROOKED.ordinal() ? "The Crooked Step"
                : pact == MasterSignature.VEILED.ordinal() ? "The Veil Between"
                : pact == MasterSignature.EXACTING.ordinal() ? "The Measured Stroke" : "None";
        out.add(stat("MASTER", "Private Law", pactName, privateLawDetail(data, id, pact)));
        for (MasterSignature sig : MasterSignature.values()) {
            int relation = data.relation(id, sig);
            String mood = relation >= 12 ? "familiar" : relation <= -12 ? "resentful" : relation > 2 ? "inclined" : relation < -2 ? "cold" : "unread";
            out.add(stat("MASTER", prettify(sig.name()), (relation >= 0 ? "+" : "") + relation, mood));
        }

        List<String> curses = new ArrayList<>();
        long day = player.level().getDayTime() / 24000L;
        if (data.hasBloodTithe(id)) curses.add("Blood Tithe " + roman(data.curseStage(id, "blood_tithe", day)));
        if (data.hasBrittlePilgrimage(id)) curses.add("Brittle Pilgrimage " + roman(data.curseStage(id, "brittle", day)));
        if (data.hasAshenTongue(id)) curses.add("Ashen Tongue · " + remainingClock(data.uniqueLong(id, "ashen_tongue_until"), player.level().getGameTime()));
        if (data.hasUnique(id, "shivering_tithe")) curses.add("Shivering Tithe");
        if (data.curseLoot15Charges(id) > 0) curses.add("Diminished Share ×" + data.curseLoot15Charges(id));
        out.add(stat("CURSE", "Curses", curses.isEmpty() ? "None" : String.join(", ", curses), "Curse stages mature after 2 and 4 Minecraft days."));

        List<String> scars = new ArrayList<>();
        if (data.hasCrimsonBalance(id)) scars.add("Crimson Balance");
        if (data.hasGlassNerve(id)) scars.add("Glass Nerve");
        if (data.hasPaleCovenant(id)) scars.add("Pale Covenant");
        if (data.hasStillHeart(id)) scars.add("Still Heart");
        if (data.hasUnique(id, "bellglass_sight")) scars.add("Bellglass Sight");
        if (data.hasUnique(id, "thorn_ledger")) scars.add("Thorn Ledger");
        if (data.hasUnique(id, "dustbound_soles")) scars.add("Dustbound Soles");
        if (data.hasUnique(id, "lantern_blood")) scars.add("Lantern Blood");
        out.add(stat("SCAR", "Standing Marks", scars.isEmpty() ? "None" : String.join(", ", scars), "Milk does not erase card state."));

        List<String> laws = new ArrayList<>();
        if (data.hasBlackHarvest(id)) laws.add("Grave Interest");
        if (data.hasSecondEntry(id)) laws.add("A Second Entry");
        if (data.hasUnique(id, "moonlit_hunt")) laws.add("Moonlit Hunt");
        if (data.hasUnique(id, "red_march")) laws.add("Red March");
        if (data.hasUnique(id, "first_supper")) laws.add("First Supper");
        if (data.hasUnique(id, "last_witness")) laws.add("Last Witness");
        if (data.hasUnique(id, "memento_mori")) laws.add("Memento Mori");
        if (data.hasUnique(id, "black_sun")) laws.add("Black Sun");
        if (data.hasUnique(id, "coffin_road")) laws.add("Coffin Road");
        if (data.hasUnique(id, "grave_bell")) laws.add("Grave Bell");
        if (data.hasUnique(id, "cinder_vow")) laws.add("Cinder Vow");
        out.add(stat("LAW", "World Laws", laws.isEmpty() ? "None" : String.join(", ", laws), "Persistent card rules affecting ordinary play."));
        List<String> conjunctions = new ArrayList<>();
        for (CardConjunctions.Conjunction conjunction : CardConjunctions.Conjunction.values())
            if (CardConjunctions.known(data, id, conjunction)) conjunctions.add(conjunction.title);
        int conjunctionCount = conjunctions.size();
        int nextResonance = CardConjunctions.nextResonanceAt(conjunctionCount);
        out.add(stat("LAW", "Known Conjunctions", conjunctionCount + " / " + CardConjunctions.Conjunction.values().length,
                "Resonance: " + CardConjunctions.resonanceName(conjunctionCount)
                        + (conjunctionCount < CardConjunctions.Conjunction.values().length ? " · next threshold " + nextResonance : " · all thresholds documented")
                        + " · " + (conjunctions.isEmpty() ? "None discovered yet." : String.join(", ", conjunctions))));

        List<String> charges = new ArrayList<>();
        addCharge(charges, "Ember", data.uniqueInt(id, "ember_hits"), "hits");
        addCharge(charges, "Coal Kiss", data.uniqueInt(id, "coal_kiss_hits"), "hits");
        addCharge(charges, "Iron Echo", data.uniqueInt(id, "iron_echo_hits"), "hits");
        addCharge(charges, "Hunter", data.uniqueInt(id, "hunter_dividend"), "kills");
        addCharge(charges, "Hearthmark", data.uniqueInt(id, "hearthmark_meals"), "meals");
        addCharge(charges, "Salt Circle", data.uniqueInt(id, "salt_circle"), "undead");
        addCharge(charges, "Grave Ration", data.uniqueInt(id, "grave_ration"), "kills");
        addCharge(charges, "Pilgrim", data.uniqueInt(id, "pilgrims_luck_blocks"), "blocks");
        addCharge(charges, "Ender Steps", data.uniqueInt(id, "ender_step_charges"), "charges");
        addCharge(charges, "Red Census", data.uniqueInt(id, "red_census_loot_charges"), "wards");
        addCharge(charges, "Witchfire", data.uniqueInt(id, "witchfire_reprise"), "reprieve");
        addCharge(charges, "Vein Drinker", data.uniqueInt(id, "vein_drinker"), "kills");
        addCharge(charges, "Ferryman", data.uniqueInt(id, "ferryman_guard"), "hits");
        addCharge(charges, "Ossuary Vow", data.uniqueInt(id, "ossuary_loot_charges"), "wards");
        out.add(stat("CHARGE", "Card Charges", charges.isEmpty() ? "None" : String.join(" · ", charges), "Consumable world-play card counters."));

        List<String> timers = new ArrayList<>();
        long now = player.level().getGameTime();
        addTimer(timers, "Ore Whisper", data.uniqueLong(id, "ore_whisper_until"), now);
        addTimer(timers, "Momentum", data.uniqueLong(id, "momentum_until"), now);
        addTimer(timers, "Black Compass", data.uniqueLong(id, "black_compass_until"), now);
        addTimer(timers, "Pocket Eclipse", data.uniqueLong(id, "pocket_eclipse_until"), now);
        addTimer(timers, "Quicksilver", data.uniqueLong(id, "quicksilver_until"), now);
        addTimer(timers, "Boneward", data.uniqueLong(id, "boneward_until"), now);
        addTimer(timers, "Blastward", data.uniqueLong(id, "blastward_until"), now);
        addTimer(timers, "Witchward", data.uniqueLong(id, "witchward_until"), now);
        out.add(stat("TIMER", "Timed Cards", timers.isEmpty() ? "None" : String.join(" · ", timers), "Time remaining updates while this page is open."));

        String shard = "None";
        if (player.hasEffect(WardEffects.WARD_SPLINTER_ATTUNEMENT.get())) shard = "Ward Splinter · " + effectTime(player.getEffect(WardEffects.WARD_SPLINTER_ATTUNEMENT.get()));
        else if (player.hasEffect(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get())) shard = "Eldritch Shard · " + effectTime(player.getEffect(WardEffects.ELDRITCH_SHARD_ATTUNEMENT.get()));
        else if (player.hasEffect(WardEffects.ECHO_SHARD_ATTUNEMENT.get())) shard = "Echo Shard · " + effectTime(player.getEffect(WardEffects.ECHO_SHARD_ATTUNEMENT.get()));
        else if (player.hasEffect(WardEffects.BLOODGLASS_FERVOR.get())) shard = "Bloodglass · " + effectTime(player.getEffect(WardEffects.BLOODGLASS_FERVOR.get()));
        out.add(stat("SHARD", "Active Shard", shard, "Only one shard attunement can occupy the player at once."));

        int opened = data.uniqueInt(id, "field_cards_opened");
        int signed = data.uniqueInt(id, "field_cards_signed");
        int fieldPity = data.uniqueInt(id, "field_card_pity");
        int deathPity = data.uniqueInt(id, "death_hand_pity");
        float nextFieldChance = CardBalance.fieldDropChance(fieldPity) * 100f;
        float nextDeathChance = (CardBalance.deathHandChance(deathPity, false) + DeathResonance.deathHandBonus(data, id, false)) * 100f;
        out.add(stat("CARDS", "Field Cards", opened + " opened · " + signed + " signed",
                String.format(java.util.Locale.ROOT, "Next hostile drop %.3f%% · pressure %s · pity %d/%d.",
                        nextFieldChance, CardBalance.pressureBand(fieldPity, CardBalance.FIELD_PITY_START, CardBalance.FIELD_PITY_GUARANTEE),
                        fieldPity, CardBalance.FIELD_PITY_GUARANTEE)));
        out.add(stat("DEATH", "Death Hand Pressure", deathPity + " / " + CardBalance.DEATH_PITY_GUARANTEE,
                String.format(java.util.Locale.ROOT, "Next eligible table %.2f%% · %s pity pressure · resonance %s · only while at least one unsigned Death law remains.",
                        nextDeathChance, CardBalance.pressureBand(deathPity, CardBalance.DEATH_PITY_START, CardBalance.DEATH_PITY_GUARANTEE), DeathResonance.detail(data, id))));
        for (CardMaster dealer : CardMaster.values()) {
            boolean known = dealer.known(data, id);
            int relation = dealer.relation(data, id);
            int tier = dealer.relationTier(data, id);
            String hand = switch (tier) {
                case 0 -> "Hostile hands often collapse to a single choice.";
                case 1 -> "Cold hands remain narrow and signature laws are scarce.";
                case 3 -> "The dealer begins leaning harder into its preferred cards.";
                case 4 -> "Three-card hands become common and signature laws become plausible.";
                case 5 -> "Favored hands are broad, strongly themed and signature-aware.";
                default -> "The dealer has not yet committed to a pattern.";
            };
            int grudge = dealer.grudge(data, id);
            int audiences = data.uniqueInt(id, "dealer_audiences_" + dealer.id);
            int accepts = data.uniqueInt(id, "dealer_acceptances_" + dealer.id);
            int refusals = data.uniqueInt(id, "dealer_refusals_" + dealer.id);
            out.add(stat("DEALER", known ? dealer.title : "???",
                    known ? Math.max(0, relation) + " / 20 · " + dealer.mood(data, id) : "unresolved",
                    known ? "True favor " + (relation >= 0 ? "+" : "") + relation + " · grudge " + grudge + "/3 · signature "
                            + String.format(java.util.Locale.ROOT, "%.1f%%", dealer.signatureChance(data, id) * 100f)
                            + " · audiences " + audiences + ", accepted " + accepts + ", refused " + refusals + " · " + hand
                            : "Identity reveals through field-card progression."));
        }
        return Collections.unmodifiableList(out);
    }

    private static String cardRuntimeStatus(ForbiddenBargain card, LockData data, UUID id, ServerPlayer player) {
        long now = player.level().getGameTime();
        return switch (card) {
            case BORROWED_BREATH -> data.hasBorrowedBreath(id) ? "ACTIVE · waiting for the next ordinary ward" : "SPENT";
            case IRON_DEBT -> data.hasIronDebt(id) ? "ACTIVE · waiting for the next lost ward" : "SPENT";
            case WATCHING_MARK -> data.hasWatchingMark(id) ? "ACTIVE · waiting for the next signed ward" : "SPENT";
            case CRIMSON_BALANCE -> data.hasCrimsonBalance(id) ? "ACTIVE · Strength I + Weakness I" : "REMOVED";
            case SEVERED_MEASURE -> data.loot25Charges(id) > 0 ? "ACTIVE · " + data.loot25Charges(id) + " +25% ward payment(s) remain" : "SPENT";
            case LAST_CANDLE -> data.hasLastCandle(id) ? "ACTIVE · next ward begins at one life" : "SPENT";
            case GLASS_NERVE -> data.hasGlassNerve(id) ? "ACTIVE · Speed I + Mining Fatigue I" : "REMOVED";
            case PALE_COVENANT -> data.hasPaleCovenant(id) ? "ACTIVE · Resistance I + Hunger I" : "REMOVED";
            case OPEN_VEIN -> data.hasOpenVein(id) ? "ACTIVE · Strength I; one heart remains forfeit" : "REMOVED";
            case THIN_BLOOD -> data.hasThinBlood(id) ? "ACTIVE · Weakness I; " + data.loot15Charges(id) + " ward payment(s) remain" : "ENDED";
            case LOADED_DICE -> countStatus(data.loadedDiceCharges(id), "ward charge(s)");
            case MERCYS_DUE -> countStatus(data.mercysDueCharges(id), "ward charge(s)");
            case STILL_HEART -> data.hasStillHeart(id) ? "ACTIVE · Regeneration I + Slowness I" : "REMOVED";
            case BLOOD_TITHE -> data.hasBloodTithe(id) ? "ACTIVE · one heart debt; Strength I below half health" : "REMOVED";
            case DIMINISHED_SHARE -> countStatus(data.curseLoot15Charges(id), "penalized ward(s)");
            case FRAIL_HAND -> data.hasBrittlePilgrimage(id) ? "ACTIVE · fall damage is amplified" : "REMOVED";
            case ASHEN_TONGUE -> data.hasAshenTongue(id)
                    ? "Stage " + roman(data.ashenTongueStage(id, now)) + " · " + timerStatus(data.uniqueLong(id, "ashen_tongue_until"), now, "Weakness I")
                    : "EXPIRED / REMOVED";
            case CROOKED_PRIVATE -> data.masterPact(id) == MasterSignature.CROOKED.ordinal() ? "ACTIVE PRIVATE LAW" : "REPLACED";
            case VEILED_PRIVATE -> data.masterPact(id) == MasterSignature.VEILED.ordinal() ? "ACTIVE PRIVATE LAW" : "REPLACED";
            case EXACTING_PRIVATE -> data.masterPact(id) == MasterSignature.EXACTING.ordinal() ? "ACTIVE PRIVATE LAW" : "REPLACED";
            case BLACK_DIVIDEND -> data.hasBlackHarvest(id) ? "ACTIVE WORLD LAW" : "REMOVED";
            case SECOND_LEDGER -> data.hasSecondEntry(id) ? "ACTIVE · one lethal correction per Minecraft day" : "REMOVED";
            case MOONLIT_HUNT -> lawStatus(data, id, "moonlit_hunt");
            case RED_MARCH -> lawStatus(data, id, "red_march");
            case FIRST_SUPPER -> lawStatus(data, id, "first_supper");
            case LAST_WITNESS -> lawStatus(data, id, "last_witness");
            case EMBER_COUNT -> countStatus(data.uniqueInt(id, "ember_hits"), "burning hit(s)");
            case ORE_WHISPER -> timerStatus(data.uniqueLong(id, "ore_whisper_until"), now, "ore sense");
            case BORROWED_MOMENTUM -> timerStatus(data.uniqueLong(id, "momentum_until"), now, "Speed II");
            case HUNTERS_DIVIDEND -> countStatus(data.uniqueInt(id, "hunter_dividend"), "hostile kill(s)");
            case BELLGLASS_SIGHT -> lawStatus(data, id, "bellglass_sight");
            case COAL_KISS -> countStatus(data.uniqueInt(id, "coal_kiss_hits"), "weakening hit(s)");
            case THORN_LEDGER -> lawStatus(data, id, "thorn_ledger");
            case HEARTHMARK -> countStatus(data.uniqueInt(id, "hearthmark_meals"), "meal(s)");
            case SALT_CIRCLE -> countStatus(data.uniqueInt(id, "salt_circle"), "undead kill(s)");
            case DUSTBOUND_SOLES -> lawStatus(data, id, "dustbound_soles");
            case BLACK_COMPASS -> timerStatus(data.uniqueLong(id, "black_compass_until"), now, "hostile tracing");
            case LANTERN_BLOOD -> lawStatus(data, id, "lantern_blood");
            case IRON_ECHO -> countStatus(data.uniqueInt(id, "iron_echo_hits"), "heavy hit(s)");
            case GRAVE_RATION -> countStatus(data.uniqueInt(id, "grave_ration"), "kill payment(s)");
            case POCKET_ECLIPSE -> timerStatus(data.uniqueLong(id, "pocket_eclipse_until"), now, "low-light veil");
            case PILGRIMS_LUCK -> countStatus(data.uniqueInt(id, "pilgrims_luck_blocks"), "block(s)");
            case MEMENTO_MORI -> data.hasUnique(id, "memento_mori")
                    ? "ACTIVE DEATH LAW · " + data.uniqueInt(id, "mori_kills") + "/" + DeathResonance.mementoTarget(data, id) + " names · reprieves " + data.uniqueInt(id, "mori_charge") + "/2 · " + data.deathHeartDebt(id) + " heart(s) buried"
                    : "REMOVED / INACTIVE";
            case BLACK_SUN -> data.hasUnique(id, "black_sun")
                    ? (player.level() instanceof net.minecraft.server.level.ServerLevel sl && CardConditions.isDirectDaylight(sl, player.blockPosition())
                        ? "ACTIVE DEATH LAW · direct daylight penalty"
                        : player.level().isNight() ? "ACTIVE DEATH LAW · night gifts active" : "ACTIVE DEATH LAW · shaded")
                    : "REMOVED / INACTIVE";
            case COFFIN_ROAD -> data.hasUnique(id, "coffin_road") ? "ACTIVE DEATH LAW · Slowness I · fall damage refused" : "REMOVED / INACTIVE";
            case GRAVE_BELL -> data.hasUnique(id, "grave_bell")
                    ? "ACTIVE DEATH LAW · " + data.uniqueInt(id, "grave_bell_kills") + "/" + DeathResonance.graveBellTarget(data, id) + " names toward the next bell"
                    : "REMOVED / INACTIVE";
            case VEIN_DRINKER -> countStatus(data.uniqueInt(id, "vein_drinker"), "healing kill(s)");
            case QUICKSILVER_PRAYER -> timerStatus(data.uniqueLong(id, "quicksilver_until"), now, "Speed I + Haste I");
            case FERRYMAN_LEDGER -> countStatus(data.uniqueInt(id, "ferryman_guard"), "guarded heavy hit(s)");
            case CINDER_VOW -> lawStatus(data, id, "cinder_vow");
            case SHIVERING_TITHE -> data.hasUnique(id, "shivering_tithe") ? "ACTIVE · Haste I + Hunger I" : "REMOVED";
            case ROTTEN_LEDGER -> objectiveStatus(data, id, "rotten_ledger");
            case BONE_TALLY -> objectiveStatus(data, id, "bone_tally");
            case CREEPER_CLAUSE -> objectiveStatus(data, id, "creeper_clause");
            case SILK_WARRANT -> objectiveStatus(data, id, "silk_warrant");
            case ENDER_AUDIT -> objectiveStatus(data, id, "ender_audit");
            case WITCH_LEDGER -> objectiveStatus(data, id, "witch_ledger");
            case STONE_COMMUNION -> objectiveStatus(data, id, "stone_communion");
            case VEIN_LITANY -> objectiveStatus(data, id, "vein_litany");
            case WOODEN_CONFESSION -> objectiveStatus(data, id, "wooden_confession");
            case EARTHEN_VIGIL -> objectiveStatus(data, id, "earthen_vigil");
            case OBSIDIAN_PRAYER -> objectiveStatus(data, id, "obsidian_prayer");
            case RED_CENSUS -> objectiveStatus(data, id, "red_census");
            case IRON_SILENCE -> objectiveStatus(data, id, "iron_silence");
            case INNOCENCE_TAX -> objectiveStatus(data, id, "innocence_tax");
            case WITCHFIRE_TITHE -> objectiveStatus(data, id, "witchfire_tithe");
            case GRIM_HARVEST -> objectiveStatus(data, id, "grim_harvest");
            case SAND_LITURGY -> objectiveStatus(data, id, "sand_liturgy");
            case OSSUARY_VOW -> objectiveStatus(data, id, "ossuary_vow");
            case COLD_LEDGER -> countStatus(data.uniqueInt(id, "cold_ledger"), "hostile payment(s)");
            case HOLLOW_LANTERN -> lawStatus(data, id, "hollow_lantern");
            case SNUFF_HOLLOW -> "RESOLVED ON SIGNING";
            case PALE_RATION -> countStatus(data.uniqueInt(id, "pale_ration"), "meal(s)");
            case WARD_OF_BONE -> lawStatus(data, id, "bone_ward");
            case BREAK_BONE_WARD -> "RESOLVED ON SIGNING";
            case SILENT_DIVIDEND -> countStatus(data.uniqueInt(id, "silent_dividend"), "hostile payment(s)");
            case DROWNED_NAME -> lawStatus(data, id, "drowned_name");
            case BLACK_REPRIEVE -> lawStatus(data, id, "black_reprieve");
            case HOUSE_OF_ASH -> lawStatus(data, id, "house_of_ash");
            case BLOODWELL_REFLEX, LONG_HAND, HARVEST_SHARE, BALLISTIC_SCRIPT, FAR_LEDGER, CROWD_INTEREST, IRON_PULSE
                    -> ProgressionCardEffects.status(data, id, card, now);
            case PINHOLE_DOCTRINE, POINT_BLANK_RECEIPT, HIGH_ARC_CLAUSE, UPWARD_INTEREST, FIRST_VOLLEY, LAST_BOLT,
                    SOLITARY_MARK, RUNNING_SIGHT, STILL_HAND, PIERCED_CROWD, RED_KNUCKLE, OPENING_CUT,
                    EXECUTIONERS_MARGIN, LONE_DUEL, PRESSED_BLADE, HIGH_GROUND, LOW_ROAD, RUNNING_HAND,
                    STILL_POINT, SECOND_WOUND, ASH_PLATE, LAST_PLATE, ARROW_LEDGER, CLOSE_SEAL,
                    CROWD_SHELTER, SOLITARY_WARD, FALLING_INK, FIRE_MARGIN, BLAST_RECEIPT, NAME_WITHOUT_RECOIL,
                    LONG_STRIDE, SPRINT_CLAUSE, EMPTY_ROAD, HUNTED_ROAD, OPEN_SKY_FOOTNOTE, DEEP_ROAD,
                    FEATHERED_DEBT, CLIMBERS_MARGIN, LONGER_HAND, DUELISTS_REACH, STONE_DIVIDEND, TIMBER_SHARE,
                    MASON_TITHE, FORTUNE_MARGIN, SWIFT_TOOL, HARVEST_MEMORY, GRAVE_DIVIDEND, SCAVENGERS_NAME,
                    CANDLEWORK, QUIET_LEDGER
                    -> ExpandedProgressionCardEffects.status(data, id, card, now);
            case WRONG_DOOR, TEN_SECONDS_UNWRITTEN, VILLAGE_COLD_SHOULDER, IRON_ACCUSATION,
                    WARDENS_BLIND_SPOT, EMPTY_DEEP, ZOMBIE_ARMISTICE, BONE_TRUCE, CREEPER_COURTESY,
                    SPIDER_TREATY, ENDER_AMNESTY, WITCHS_PRIVILEGE, HOSTILE_CENSUS, FIRE_WITHOUT_FUEL,
                    WRONG_GRAVITY, HUNDRED_STEPS, RED_MINUTE, GLASS_SAINT, SHARED_PAIN, MEASURED_MERCY,
                    TIDAL_BODY, CHORUS_ERROR, RETURN_ADDRESS, LANTERN_BREAK, WHITE_NOISE, BLOOD_MONEY,
                    COIN_EDGE, CHANCE_ENGINE, BLACKOUT, AIR_BORROWED, BURNING_PACT, FROSTED_BLOOD,
                    NIGHT_PASS, DAY_PASS, HOLLOW_SKIN, GRAVE_SILENCE, BEAST_MARCH, GOLEM_ESCORT,
                    GOLEM_ENMITY, VILLAGE_EXILE, VILLAGE_PARDON, CREEPER_BLESSING, FALLING_CROWN,
                    STONE_SKIN, OPEN_SKY, DEEP_BREATH, FOURTH_CARD, FIFTH_CARD, NARROW_HAND,
                    CURSE_DAMPER, CURSE_BAIT, RARE_INK, CLEAN_MARGIN, BLACK_MARGIN, NO_ECHOES,
                    REMEDY_WITNESS, DEALERS_FOURTH, GOLDEN_CUT, DEEP_SHELF, CURSE_LULL,
                    REGISTRY_LOTTERY, EGG_WITH_NO_SHELL, SIXTY_FOURTH_STEP, STORM_RECEIPT,
                    BORROWED_FACE, MOB_EXCHANGE, BAD_RECEIPT, LUCKY_POCKET, STRANGE_INVITATION,
                    CERTAINLY_NOTHING, FOURTH_SHAPE, WHITE_THREAD, SCRIBBLED_RESPITE, PALE_MARGIN -> WildCardEffects.status(data,id,card,now);
            case QUICKENED_PULSE, STONE_BREATH, SHARPENED_HOUR, CLEAR_EYES, FEATHER_RECEIPT, DIVERS_MARGIN, CINDER_RECEIPT, MINERS_CREDIT, HUNTERS_REBATE, PALE_BANDAGE,
                    BLACK_BREAD, FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED, THIN_AIR, STANDING_ORDER, RUNNING_ORDER, OPEN_SKY_LEDGER, DEEP_INK, RED_HARVEST, GRAY_HARVEST,
                    SCAVENGER_CLAUSE, MASONS_LUCK, WOODSMANS_SHARE, FARMERS_MARGIN, IRON_STEP, GLASS_STEP, WARD_LANTERN, QUIET_MOUTH, LOUD_NAME, SECOND_WIND,
                    INKED_APPETITE, CHARCOAL_TONGUE, FROSTED_EDGE, TETHERED_BLOOD, CUT_TETHER, ASHEN_LUNGS, CLEAR_THE_LUNGS, HOLLOW_BONES, FILL_THE_BONES, CLOCKWORK_NERVE,
                    STILL_THE_NERVE, BLOODLESS_VICTORY, WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER, GOLDEN_HUNGER, PAY_THE_HUNGER, ASH_CROWN,
                    QUENCH_THE_CROWN, LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER
                    -> FreshCardEffects.status(data, id, card, now);
            case SWIFT_MERCY, IRON_WAKE, MOONWATER_DRAFT, FURNACE_VEIN, QUIET_STEP, RED_HOUR, PALE_STEP, HUNGRY_STEEL, WATCHERS_DRAFT, STONE_CHOIR,
                    RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LAST_MATCH, GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, LONG_BREATH, DEEP_STEP,
                    SKY_STEP, HUNTERS_REST, HUNTERS_RUSH, RED_LEDGER, BLACK_LEDGER, CANDLE_TAX, IRON_HARVEST, STONE_HARVEST, GLASS_RUNNER, HEAVY_HAND,
                    THIN_ARMOR, CORPSE_LANTERN, WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS,
                    COLD_IRON, WARM_IRON, PALE_SKIN, COLOR_RETURNED, SALT_LUNGS, FRESH_AIR, EMBER_MORTGAGE, PAY_THE_EMBER, GRAVE_CREDIT, EMPTY_PULSE
                    -> SecondWaveCardEffects.status(data, id, card, now);
            case KNEELING_FUSE, DROWN_THE_FUSE, THIRTEENTH_STEP, BREAK_THE_COUNT, STILLNESS_TAX, MOVE_THE_INK, BLACK_STATIC, SORT_THE_HAND, CHORUS_DEBT, CLOSE_THE_CHORUS,
                    LAST_FOOTPRINT, SECOND_GRAVITY, WITNESS_MARK, BLACKOUT_CLAUSE, BONE_MAGNET, BLOOD_CLOCK_HAND, STOLEN_COUNTENANCE, HOUSE_ALWAYS_WINS, RETURN_TO_SENDER, ASH_RECOIL,
                    AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, CROW_TOLL, PROJECTILE_AMNESTY, LOANED_MOMENT, DEAD_MANS_MARGIN, CROOKED_PARALLAX, BELL_WITHOUT_SOUND, GRAVE_INTEREST,
                    BLOOD_TELEGRAM, WOUND_EXCHANGE, STILL_POINT_BLACK, BLIND_AUCTION, CANCEL_AUCTION, LAST_WARDEN, FURNACE_HEART, KING_IN_RAGS, IRON_IDOL, LAST_CARD_DRAWN,
                    NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL, FIFTH_TOLL, HOLLOW_CROWN, AFTERIMAGE_DEBT, DEBT_OF_DISTANCE, FINAL_AUCTION, UNWRITTEN_REMAINDER
                    -> ThirdWaveCardEffects.status(data, id, card, now);
            case THE_RED_PEN -> "HAND UTILITY · " + data.uniqueInt(id, "red_pen_uses") + " use(s)";
            case DEBT_ECHO, WAGER_ECHO, SCAR_ECHO, REMEDY_ECHO, CONTRACT_ECHO, RITUAL_ECHO, COVENANT_ECHO, MASTER_ECHO, EPIC_ECHO, UNIQUE_ECHO, CURSE_ECHO, DEATH_ECHO
                    -> CardEchoSystem.isArmed(data, id, card) ? "ARMED · next " + CardEchoSystem.targetKind(card).name() + " signature x2" : "SPENT / READY TO RETURN";
            case MIRROR_WRIT, BORROWED_ANATOMY, NINTH_MARGIN, CORPSE_LEDGER, CHAIN_OF_CUSTODY
                    -> FourthWaveCardEffects.status(data, id, card);
            case CLEAN_INTEREST, SECOND_ATTEMPT, THREE_CLEAN_LINES, HURRIED_OATH, WHITE_INK, BOUND_TESTIMONY, DOUBLE_MARGIN,
                    PERFECT_ORRERY, PERFECT_PROCESSION, BLACK_STUDY, CLOSE_THE_BOOK, ASHEN_REBUTTAL, SEVEN_PACES, STILL_WITNESS, HOLLOW_STEP,
                    RED_WAKE, IRON_AFTERTASTE, TIMBER_ECHO, RAIN_WRIT, CENSUS_OF_ONE, EMPTY_HAND_DOCTRINE, FULL_HAND_DOCTRINE, WOUND_CLOCK,
                    STOP_THE_CLOCK, HUNGER_OF_ORDER, BREAK_THE_ORDER, GRAVE_RECEIPT, FINAL_FOOTNOTE, MOON_ARCHIVE, SIXTH_WITNESS
                    -> FifthWaveCardEffects.status(data, id, card);
            case RAZOR_DIVIDEND, SPLINTERED_PLATE, LONGBOW_TESTAMENT, QUICKDRAW_CLAUSE, CRIMSON_ODDS, FROST_WIT, CINDER_WIT, BLOOD_RETURN, PALE_RESERVE, DEEP_MINERS_LEDGER, SCHOLARS_TITHE, DODGERS_INK, PLATEBREAKER_SCRIPT, SUNDERERS_NOTE, OPENING_FEE, HUNTERS_SCRIPT, BLOODLETTERS_MARGIN, WARMTH_AGAINST_STEEL, WINTER_AGAINST_BONE, SWIFT_PICK_CLAUSE, SILVER_LEDGER, SECOND_HEART_ACCOUNTING, DOUBLED_EDGE, IMPOSSIBLE_AIM,
                    RUSTED_EDGE, CLEAN_THE_RUST, SPLIT_NERVE, MEND_THE_NERVE, HOLLOW_MARROW, FILL_THE_MARROW, BROKEN_SIGHT, RESET_THE_SIGHT, ASHEN_PICK, WASH_THE_PICK, OPEN_WOUND_LEDGER, CLOSE_THE_WOUND,
                    RAZOR_DOCTRINE, SUNDERED_CREED, GOLDEN_BALLISTICS, SECOND_STRING, WINTER_TONGUE, CINDER_TONGUE, RED_CATECHISM, LEARNED_GRAVE, STONES_MEMORY, PERFECT_DEFLECTION, VEILED_PROTECTION, HUNGER_FOR_THE_LIVING,
                    RED_LAW, NO_ARMOR_IS_SACRED, ARROW_OF_LAST_ACCOUNT, FROZEN_VERDICT, BURNING_VERDICT, CRIMSON_USURY, HOUSE_TAKES_EXPERIENCE, EMPTY_PLATE_DOCTRINE, BONEBREAKER_COVENANT, BLOOD_IN_EXCESS, EXECUTIONER_OF_FULL_HEALTH, HOUSE_HAS_NUMBERS,
                    GLASS_RAZOR, BALLISTIC_PRAYER, BLACK_ICE_LEDGER, ASHEN_VOLLEY, SURGEONS_MARGIN, GHOST_STEP_LEDGER, PLUNDERED_LESSON, IRON_ALGEBRA, BREAKERS_INTEREST, RED_RESERVOIR, COLD_ACCOUNTING, CINDER_ACCOUNTING, HIGH_VELOCITY_CLAUSE, SNIPERS_DEBT, QUICK_HAND_TAX, PALE_REFLEX, QUARRY_FEVER, EDUCATED_VIOLENCE, MERCYS_INTEREST, HEMORRHAGE_CLAUSE, DEFLECTORS_WAGE, PICK_AND_BLADE, PREDATORS_ARITHMETIC, HOUSE_ACCELERANT, PAPER_SKIN, BIND_THE_SKIN, FROZEN_MARROW, WARM_THE_MARROW, CINDERED_NERVE, COOL_THE_NERVE, GREEDY_PICK, BREAK_THE_GREED, SERRATED_MEMORY, FILE_THE_MEMORY, HUNGRY_QUIVER, FEED_THE_QUIVER, BLACK_LANCET, MERCILESS_GEOMETRY, WINTER_ENGINE, CINDER_ENGINE, VAMPIRE_LEDGER, MASTER_QUARRY, ABSENT_TARGET, PERFECT_TRAJECTORY, PROFANED_AEGIS, RED_INSTRUCTION, PATIENT_HAND, HOUSE_COMPOUND, GLASS_SOVEREIGN, TEETH_BEHIND_STEEL, TERMINAL_BALLISTICS, WINTER_OWNS_BLOOD, ASH_OWNS_BREATH, RED_RESERVOIR_LAW, FINAL_QUARRY, UNTOUCHABLE_DEBT, SHREDDERS_CROWN, OPENING_EXECUTION, LAST_QUIVER, HOUSE_REWRITES_BODY,
                    KILLING_MOMENTUM, FIRST_CUT_CLAUSE, LAST_BREATH_MARGIN, STILL_BALLISTICS, MOVING_TARGET_CLAUSE, QUARRY_TEMPER, FURNACE_ARITHMETIC, WINTER_PULSE, EMPTY_STOMACH_DOCTRINE, FULL_STOMACH_DIVIDEND, MOONSHOT_RECEIPT, SUNBURN_LEDGER, DUELISTS_EXCEPTION, MOB_INTEREST, BROKEN_SHIELD_PREMIUM, HEAVY_POCKETS_CLAUSE, CLEAN_HANDS_CLAUSE, PLATED_DEBT, BLOODHOUND_CALCULUS, HUNTERS_RHYTHM, EXECUTIONER_CLOCK, REDIRECTION_LAW, GLASS_CANNON_ARCHIVE, QUARRY_COMMUNION, EMBER_DEBT_COLLECTOR, RIME_COLLECTOR, LONE_PREDATOR, CROWD_AUDITOR, FULL_QUIVER_LAW, VULTURES_MARGIN, GLASS_BLOOD, THICKEN_GLASS_BLOOD, LEAD_FINGERS, UNCLASP_FINGERS, WHITE_HOT_MARROW, QUENCH_WHITE_HOT_MARROW, DEAD_WINTER_NERVE, WAKE_WINTER_NERVE, AUDITORS_HUNGER, CLOSE_THE_AUDIT, HOLLOW_AIM, CORRECT_THE_AIM, BORROWED_SKIN, RETURN_THE_SKIN, RED_QUARRY, COOL_THE_QUARRY, REDLINE_ANATOMY, PERFECT_MURDER_GEOMETRY, METEOR_QUIVER, HOUSE_BLOOD_BANK, IMPOSSIBLE_WINTER, CREMATION_CLAUSE, QUARRY_OF_FLESH, LAST_ACCOUNT, CRITICAL_LOTTERY, ECHO_CHAMBER, HOUSE_FAVORITE_SEVEN, LAST_ARROW_IN_QUIVER, MERCY_AFTER_MURDER, REVOLVING_DOOR, ZOMBIE_ONE_V_ONE, FREE_DIAMOND, ONE_SKELETON, QUICK_MINING_JOB, SHORT_WALK, FREE_HEALING, ONE_BABY_ZOMBIE, LUCKY_SEVEN, NOTHING_HAPPENS, SMALL_REWARD
                    -> ApothicCardEffects.status(data, id, card, now);
            case ANOMALY_GILDED, ANOMALY_FERAL, ANOMALY_HOLLOW, ANOMALY_STATIC, ANOMALY_MIRROR, ANOMALY_BLACK
                    -> "ANOMALY · result written only after signature";
            case SHATTER_BELLGLASS, PRUNE_THORNS, CUT_DUST_BINDING, SNUFF_LANTERN, THAW_THE_TITHE,
                    RECONCILED_BLOOD, HEART_RETURNED, NERVE_SETTLED, COVENANT_BROKEN, HEART_AWAKENED,
                    DEBT_UNWRITTEN, THICKENED_BLOOD, ABSOLUTION -> "RESOLVED ON SIGNING";
            case REFRESH_HAND -> "REDRAW ONLY";
        };
    }

    private static String lawStatus(LockData data, UUID id, String key) {
        return data.hasUnique(id, key) ? "ACTIVE WORLD LAW" : "REMOVED / INACTIVE";
    }

    private static String countStatus(int count, String unit) {
        return count > 0 ? "ACTIVE · " + count + " " + unit + " remain" : "SPENT";
    }

    private static String timerStatus(long until, long now, String effect) {
        if (until == Long.MIN_VALUE || until <= now) return "EXPIRED";
        long sec = Math.max(0L, (until - now) / 20L);
        return "ACTIVE · " + effect + " · " + (sec / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60L) + " remaining";
    }

    private static String objectiveStatus(LockData data, UUID id, String key) {
        if (!data.hasUnique(id, "obj_" + key)) return "FULFILLED / NO LONGER ACTIVE";
        CardObjectives.Objective objective = CardObjectives.byKey(key);
        int goal = objective == null ? Math.max(1, data.uniqueInt(id, "obj_" + key + "_goal")) : objective.goal;
        return "ACTIVE OBJECTIVE · " + data.uniqueInt(id, "obj_" + key + "_progress") + " / " + goal;
    }

    private static void addEffectSourceStats(List<String> out, ServerPlayer player, LockData data, UUID id) {
        long now = player.level().getGameTime();
        var weakness = player.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        if (weakness != null) {
            List<String> sources = new ArrayList<>();
            if (data.hasCrimsonBalance(id)) sources.add("Crimson Balance");
            if (data.hasThinBlood(id)) sources.add("Thin Blood");
            if (data.hasAshenTongue(id)) sources.add("Ashen Tongue");
            if (data.hasUnique(id, "black_sun") && player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && CardConditions.isDirectDaylight(sl, player.blockPosition())) sources.add("The Black Sun");
            if (data.hasUnique(id, "hollow_lantern") && player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && CardConditions.isDirectDaylight(sl, player.blockPosition())) sources.add("The Hollow Lantern");
            if (data.hasUnique(id, "drowned_name") && !player.isInWaterOrBubble()
                    && player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && CardConditions.isDirectDaylight(sl, player.blockPosition())) sources.add("The Drowned Name");
            if (data.hasUnique(id, "fresh_hollow_bones")) sources.add("Hollow Bones");
            if (data.hasUnique(id, "fresh_ash_crown") && (player.isInWaterOrBubble()
                    || player.serverLevel().isRainingAt(player.blockPosition()))) sources.add("The Ash Crown");
            if (data.hasUnique(id, "fresh_law_288") && player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && CardConditions.isDirectDaylight(sl, player.blockPosition())) sources.add("The Long Night");
            long quick = data.uniqueLong(id, "quicksilver_weak_until");
            if (quick != Long.MIN_VALUE && quick > now) sources.add("Quicksilver Prayer");
            out.add(stat("EFFECT", "Weakness " + roman(weakness.getAmplifier() + 1), effectTime(weakness),
                    "Source: " + (sources.isEmpty() ? "external / temporary effect" : String.join(", ", sources))));
        }
        var hunger = player.getEffect(net.minecraft.world.effect.MobEffects.HUNGER);
        if (hunger != null) {
            List<String> sources = new ArrayList<>();
            if (data.hasPaleCovenant(id)) sources.add("Pale Covenant");
            if (data.hasUnique(id, "grave_bell")) sources.add("The Grave Bell");
            if (data.hasUnique(id, "shivering_tithe")) sources.add("Shivering Tithe");
            if (data.hasUnique(id, "fresh_clockwork_nerve")) sources.add("Clockwork Nerve");
            if (data.hasUnique(id, "fresh_golden_hunger")) sources.add("Golden Hunger");
            out.add(stat("EFFECT", "Hunger " + roman(hunger.getAmplifier() + 1), effectTime(hunger),
                    "Source: " + (sources.isEmpty() ? "temporary card or external effect" : String.join(", ", sources))));
        }
        var slow = player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null) {
            List<String> sources = new ArrayList<>();
            if (data.hasStillHeart(id)) sources.add("The Still Heart");
            if (data.hasUnique(id, "dustbound_soles")) sources.add("Dustbound Soles");
            if (data.hasUnique(id, "coffin_road")) sources.add("The Coffin Road");
            if (data.hasUnique(id, "fresh_tethered_blood") && player.getHealth() > player.getMaxHealth() * 0.5f) sources.add("Tethered Blood");
            if (data.hasUnique(id, "fresh_law_285") && player.getY() > 96) sources.add("The Deep Saint");
            if (data.hasUnique(id, "fresh_law_288") && player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && CardConditions.isDirectDaylight(sl, player.blockPosition())) sources.add("The Long Night");
            out.add(stat("EFFECT", "Slowness " + roman(slow.getAmplifier() + 1), effectTime(slow),
                    "Source: " + (sources.isEmpty() ? "temporary card or external effect" : String.join(", ", sources))));
        }
        var fatigue = player.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
        if (fatigue != null) {
            List<String> sources = new ArrayList<>();
            if (data.hasGlassNerve(id)) sources.add("Glass Nerve");
            if (data.hasUnique(id, "bone_ward") && player.getHealth() > player.getMaxHealth() * 0.5f) sources.add("The Ward of Bone");
            if (data.hasUnique(id, "fresh_ashen_lungs")) sources.add("Ashen Lungs");
            out.add(stat("EFFECT", "Mining Fatigue " + roman(fatigue.getAmplifier() + 1), effectTime(fatigue),
                    "Source: " + (sources.isEmpty() ? "temporary card or external effect" : String.join(", ", sources))));
        }
    }

    private static void addAnalysisSchematic(List<String> out, ServerPlayer player, LockData data, UUID id) {
        int[] outcomes = data.analyticsOutcomes(id);
        int[] performance = data.analyticsPerformance(id);
        int[] mistakes = data.analyticsMistakes(id);
        if (outcomes.length == 0) {
            out.add(stat("ANALYSIS", "Recent Form", "Awaiting samples",
                    "Resolve another ward to begin the rolling twenty-seal analysis. Existing lifetime progression remains intact."));
        } else {
            int wins = 0, perf = 0, errors = 0;
            for (int value : outcomes) wins += value > 0 ? 1 : 0;
            for (int value : performance) perf += value;
            for (int value : mistakes) errors += value;
            int avgPerf = performance.length == 0 ? 0 : Math.round(perf / (float) performance.length);
            float avgMistakes = mistakes.length == 0 ? 0f : errors / (float) mistakes.length;
            int recentN = Math.min(5, performance.length);
            int olderN = Math.min(5, Math.max(0, performance.length - recentN));
            int recent = 0, older = 0;
            for (int i = 0; i < recentN; i++) recent += performance[i];
            for (int i = 0; i < olderN; i++) older += performance[i + recentN];
            String trend = olderN == 0 ? "trend forming" : recent / Math.max(1, recentN) >= older / Math.max(1, olderN) + 6 ? "improving"
                    : recent / Math.max(1, recentN) <= older / Math.max(1, olderN) - 6 ? "slipping" : "stable";
            out.add(stat("ANALYSIS", "Recent Form", wins + " / " + outcomes.length + " resolved",
                    "Rolling last " + outcomes.length + " · average execution " + avgPerf + "% · mistakes "
                            + String.format(java.util.Locale.ROOT, "%.1f", avgMistakes) + " per seal · " + trend + "."));
        }

        int totalAttempts = 0, totalWins = 0, totalPerfect = 0, totalCorrupt = 0;
        for (MinigameType game : MinigameType.values()) {
            if (game == MinigameType.CTHULHUS_GAME) continue;
            totalAttempts += MinigameMastery.attempts(data, id, game);
            totalWins += MinigameMastery.wins(data, id, game);
            totalPerfect += MinigameMastery.perfectWins(data, id, game);
            totalCorrupt += MinigameMastery.corruptionEncounters(data, id, game);
        }
        if (totalAttempts <= 0) {
            out.add(stat("ANALYSIS", "Discipline Shape", "Awaiting observations",
                    "Complete a minigame discipline to establish strongest/weakest readings, mastery shape and corruption exposure."));
        } else {
            MinigameType strongest = MinigameMastery.strongestDiscipline(data, id);
            MinigameType weakest = MinigameMastery.weakestDiscipline(data, id);
            out.add(stat("ANALYSIS", "Discipline Shape", totalWins + " / " + totalAttempts + " wins",
                    "Strongest " + prettify(strongest.id) + " " + MinigameMastery.tierName(MinigameMastery.tier(data, id, strongest))
                            + " · weakest observed " + prettify(weakest.id) + " · perfect " + totalPerfect + " · corrupted " + totalCorrupt + "."));
        }

        PlayerImprint.Trace dominant = PlayerImprint.dominant(data, id);
        PlayerImprint.Trace secondary = PlayerImprint.secondary(data, id);
        out.add(stat("ANALYSIS", "Behaviour Profile", PlayerImprint.profile(data, id),
                "Primary " + dominant.title + " " + PlayerImprint.score(data, id, dominant)
                        + (secondary == PlayerImprint.Trace.UNREAD ? "" : " · secondary " + secondary.title + " " + PlayerImprint.score(data, id, secondary))
                        + " · descriptive memory only; it never locks a class."));

        out.add(stat("ANALYSIS", "Card Ecology", CardEcology.summary(data, id),
                data.uniqueInt(id, "ecology_signed_total") + " voluntary signatures across " + data.uniqueInt(id, "ecology_hands")
                        + " real hands · Heat cools on newly dealt hands; familiarity does not."));
        for (CardEcology.Family family : CardEcology.hottest(data, id, 5)) {
            int heat = CardEcology.heat(data, id, family);
            int signed = CardEcology.familiarity(data, id, family);
            int offered = CardEcology.exposure(data, id, family);
            out.add(stat("ANALYSIS", family.title, heat + " / " + CardEcology.MAX_HEAT + " " + CardEcology.heatName(heat),
                    "Signed " + signed + " · present in " + offered + " dealt hand(s) · " + family.detail + "."));
        }

        CardEcology.Family response = CardEcology.responseFamily(data, id);
        out.add(stat("ANALYSIS", "House Response", response == null ? "No active counter-family" : response.title + " ink rising",
                response == null ? "No family is hot enough for the House to answer strongly; legal shelves remain close to their base mix."
                        : "This response is a soft weighting inside already-legal shelves. It cannot unlock rarity, force a card, or delete the family you favor."));

        int conjunctions = CardConjunctions.knownCount(data, id);
        out.add(stat("ANALYSIS", "Conjunction Network", conjunctions + " / " + CardConjunctions.Conjunction.values().length,
                CardConjunctions.resonanceName(conjunctions) + " · next resonance " + CardConjunctions.nextResonanceAt(conjunctions)
                        + " · interacting laws are tracked separately from family heat."));

        StringBuilder masters = new StringBuilder();
        for (CardMaster master : CardMaster.values()) {
            if (masters.length() > 0) masters.append(" · ");
            int relation = master.relation(data, id);
            masters.append(master.title.replace("The ", "")).append(" ").append(relation >= 0 ? "+" : "").append(relation);
        }
        out.add(stat("ANALYSIS", "Master Influence", masters.toString(),
                "Card lineage: " + CardLineage.summary(data, id) + " · rivalry: " + MasterStory.rivalrySummary(data, id) + "."));

        if (WardMeasureSystem.unlocked(data, id)) {
            out.add(stat("ANALYSIS", "House Leverage", WardMeasureSystem.cuts(data, id) + " / " + WardMeasureSystem.MAX_CUTS + " Cuts",
                    WardMeasureSystem.cycles(data, id) + " Measures closed · " + WardMeasureSystem.folds(data, id) + " folds · "
                            + WardMeasureSystem.spentCuts(data, id) + " Cuts spent · current Measure " + WardMeasureSystem.points(data, id) + "/6."));
        }
    }

    private static void addStatusOverview(List<String> out, ServerPlayer player, LockData data, UUID id) {
        int contracts = CardObjectives.activeCount(data, id, ForbiddenBargain.Kind.CONTRACT);
        int rituals = CardObjectives.activeCount(data, id, ForbiddenBargain.Kind.RITUAL);
        int covenants = CardObjectives.activeCount(data, id, ForbiddenBargain.Kind.COVENANT);
        int total = contracts + rituals + covenants;
        out.add(stat("ACTIVE", "Open Obligations", total + " / 3",
                contracts + " contract · " + rituals + " ritual · " + covenants + " covenant; one active objective per lane, so similar tasks cannot be stacked."));
        if (total > 0) {
            addObjectiveTracking(out, player, data, id);
            addNearestClause(out, player, data, id);
        }

        int curses = 0;
        if (data.hasBloodTithe(id)) curses++;
        if (data.hasBrittlePilgrimage(id)) curses++;
        if (data.hasAshenTongue(id)) curses++;
        if (data.hasUnique(id, "shivering_tithe")) curses++;
        if (data.curseLoot15Charges(id) > 0) curses++;
        int scars = 0;
        if (data.hasCrimsonBalance(id)) scars++;
        if (data.hasGlassNerve(id)) scars++;
        if (data.hasPaleCovenant(id)) scars++;
        if (data.hasOpenVein(id)) scars++;
        if (data.hasThinBlood(id)) scars++;
        if (data.hasStillHeart(id)) scars++;
        if (data.hasUnique(id, "bellglass_sight")) scars++;
        if (data.hasUnique(id, "thorn_ledger")) scars++;
        if (data.hasUnique(id, "dustbound_soles")) scars++;
        if (data.hasUnique(id, "lantern_blood")) scars++;
        if (data.hasUnique(id, "hollow_lantern")) scars++;
        if (data.hasUnique(id, "bone_ward")) scars++;
        if (data.hasUnique(id, "village_exile")) scars++;
        out.add(stat("RISK", "Signed Burden", curses + " curse · " + scars + " scar",
                "Heart debt " + data.heartDebt(id) + " · Death-buried " + data.deathHeartDebt(id)
                        + " · covenant stain " + data.uniqueInt(id, "covenant_stain") + "."));

        AttentionSystem.Stage attentionStage = AttentionSystem.current(data, id);
        int highestAttention = Math.max(attentionStage.ordinal(), data.uniqueInt(id, "attention_highest_stage"));
        out.add(stat("RISK", "Attention", AttentionSystem.detail(data, id),
                "Highest observed stage: " + AttentionSystem.Stage.values()[Math.min(highestAttention, AttentionSystem.Stage.values().length - 1)].title
                        + " · " + AttentionSystem.stageRule(attentionStage)));
        PlayerImprint.Trace trace = PlayerImprint.dominant(data, id);
        out.add(stat("MEMORY", "Behavioural Trace", PlayerImprint.compact(data, id),
                (trace == PlayerImprint.Trace.UNREAD ? "No dominant trace yet. " : trace.detail + " ") + PlayerImprint.full(data, id) + "."));
        out.add(stat("MASTERY", "Adaptive Skill Profile", MinigameSkillProfile.compact(data, id),
                MinigameSkillProfile.detail(data, id)));

        StringBuilder story = new StringBuilder();
        for (CardMaster master : CardMaster.values()) {
            if (story.length() > 0) story.append(" · ");
            story.append(master.title.replace("The ", "")).append(" ").append(MasterStory.chapter(data, id, master)).append("/").append(MasterStory.MAX_CHAPTER);
        }
        out.add(stat("STORY", "Master Threads", story.toString(), MasterStory.rivalrySummary(data, id) + " · " + CardLineage.summary(data, id)));
        boolean knownThree = data.uniqueInt(id,"gambler_defeated")>0 && data.uniqueInt(id,"curator_defeated")>0 && data.uniqueInt(id,"notary_defeated")>0;
        if(knownThree && data.uniqueInt(id,"maestro_defeated")==0)
            out.add(stat("GLITCH","Fourth Signature","Score Beyond the Margin",
                    "Silas believed Gambler, Curator and Notary exhausted the meaningful threat record. The new score belongs to none of their systems. Maestro did not rise above their hierarchy; he became immense along a different road they never knew existed."));
        if(data.uniqueInt(id,"maestro_defeated")>0)
            out.add(stat("BEYOND","Maestro","The Artist Beyond the Three",
                    "Once human. Time stopped being a limit before distance did. He crossed realms without passing through the order of Gambler, Curator or Notary, so they never knew him and he had no reason to know them. He witnessed Cthulhu erase planets and leave reaches of stars silent. He never challenged it; he learned its direction from the absences it left behind. Every sky reminds him that the stars are young beside the thing he calls the end. He still interprets even that terror as material for art."));
        if(data.uniqueInt(id,"cthulhu_head_defeated")>0)
            out.add(stat("ABYSS","The Severed Dream","The Head was not the body",
                    "The entity fought in the Dreaming Abyss repeatedly insisted that its true body was absent. The Abyssal Vestige reacts to the Cthulhu Idol. A future End altar is the missing third condition."));
        if (data.uniqueInt(id, "gambler_defeated") > 0
                || dev.marrowseal.wardbound.boss.MasterRewards.signedCount(data, id) > 0) {
            boolean wardLaw = dev.marrowseal.wardbound.boss.MasterRewards.hasWardLaw(data, id);
            boolean worldLaw = dev.marrowseal.wardbound.boss.MasterRewards.hasWorldLaw(data, id);
            String state = (wardLaw ? "House Owes You" : "—") + " · " + (worldLaw ? "World Is Another Table" : "—");
            String detail = (wardLaw
                    ? "Ward law: every ordinary ward gains +1 life, +4 seconds, first-error pardon and +25% loot. "
                    : "Ward law remains unsigned. ")
                    + (worldLaw
                    ? "World law: +15% hostile damage, 12% mitigation, +10% speed, +4 hearts, extended reach, +2 Luck and +25% damage to Wardbound Masters."
                    : "World law remains unsigned.");
            out.add(stat("MASTER", "Housebreaker's Laws", state, detail));
        }
        StringBuilder chains = new StringBuilder();
        for (OccultChainEvents.ChainEvent event : OccultChainEvents.ChainEvent.values()) {
            if (chains.length() > 0) chains.append(" · ");
            chains.append(event.title).append(" ").append(OccultChainEvents.summary(data, id, event));
        }
        out.add(stat("GLITCH", "Occult Chains", chains.toString(), "Cross-system sequences advance only when their actual conditions occur; ordinary grinding does not skip stages."));

        int revisions = 0, palimpsests = 0;
        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            int revision = CardEvolution.activeVariant(data, id, card);
            if (revision > 0) revisions++;
            if (revision >= CardEvolution.MAX_REVISION) palimpsests++;
        }
        int corruptions = 0;
        for (CurseEvolution.Corruption corruption : CurseEvolution.Corruption.values()) if (CurseEvolution.known(data, id, corruption)) corruptions++;
        out.add(stat("ARCHIVE", "Living Laws", revisions + " revised · " + palimpsests + " palimpsest · " + corruptions + " corrupted",
                "Repeated signatures can revise stable cards through three layers; compatible curses can corrupt one another into hostile combined clauses."));
        out.add(stat("MASTER", "Ash Archive", dev.marrowseal.wardbound.boss.CuratorProgression.status(player),
                "The returned record becomes the Ashen Archive Codex after the Curator's story, Ash in the Margin, and a curse evolution. Open the codex to preserve or burn the testimony, then let it judge the archive space around the reader."));
        if(data.uniqueInt(id,"curator_defeated")>0)out.add(stat("SOVEREIGNTY","The Last Margin","Active archive relic",
                "Open a six-second blank margin. The next wound is mostly archived, then returned to nearby enemies as an Ash Rebuttal after a short delay."));
        String queuedHand = WildCardEffects.deckSummary(data,id);
        if (!queuedHand.isBlank()) out.add(stat("DECK", "Next Hand Modifiers", queuedHand,
                "These clauses are consumed only when an actual eligible deck is dealt; multiple compatible clauses may combine."));
        int anomaliesOpened = data.uniqueInt(id, "anomalies_opened");
        int anomaliesDealt = data.uniqueInt(id, "anomalies_dealt");
        if (anomaliesOpened > 0 || anomaliesDealt > 0) out.add(stat("ANOMALY", "Anomaly Record",
                anomaliesOpened + " signed / " + anomaliesDealt + " dealt",
                "Favorable " + data.uniqueInt(id, "anomaly_good") + " · hostile " + data.uniqueInt(id, "anomaly_bad")
                        + " · outcomes remain unreadable until signature."));
        out.add(stat("DEATH", "Death Resonance", DeathResonance.detail(data, id),
                "Memento target " + DeathResonance.mementoTarget(data, id) + " kills · Grave Bell target " + DeathResonance.graveBellTarget(data, id)
                        + " kills · resonance slowly bleeds off on completed physical wards."));

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var pos = player.blockPosition();
            int light = sl.getMaxLocalRawBrightness(pos);
            String sky = sl.canSeeSky(pos) ? "open sky" : "covered";
            String phase = CardConditions.isDirectDaylight(sl, pos) ? "direct daylight"
                    : CardConditions.isOpenNight(sl, pos) ? "open night"
                    : CardConditions.isDark(sl, pos) ? "low light" : "ordinary light";
            out.add(stat("CONDITION", "Ritual Environment", "Y " + pos.getY() + " · light " + light,
                    phase + " · " + sky + ". These are the live predicates used by environmental card laws."));
        }
    }

    private static void addObjectiveTracking(List<String> out, ServerPlayer player, LockData data, UUID id) {
        long now = player.level().getGameTime();
        for (CardObjectives.Objective objective : CardObjectives.Objective.values()) {
            if (!data.hasUnique(id, "obj_" + objective.key)) continue;
            int progress = data.uniqueInt(id, "obj_" + objective.key + "_progress");
            int remaining = Math.max(0, objective.goal - progress);
            String lane = switch (objective.kind) {
                case CONTRACT -> "CONTRACT"; case RITUAL -> "RITUAL"; case COVENANT -> "COVENANT"; default -> "OBJECTIVE";
            };
            String detail;
            if (objective.kind == ForbiddenBargain.Kind.CONTRACT) {
                long started = data.uniqueLong(id, "obj_" + objective.key + "_started_at");
                long elapsed = started == Long.MIN_VALUE ? 0L : Math.max(0L, now-started);
                detail = remaining + " target(s) remain · Swift "
                        + (elapsed <= CardObjectives.SWIFT_WINDOW_TICKS ? clockFromTicks(CardObjectives.SWIFT_WINDOW_TICKS-elapsed)+" left" : "expired")
                        + " · best streak " + data.uniqueInt(id,"obj_"+objective.key+"_best_streak") + ".";
            } else if (objective.kind == ForbiddenBargain.Kind.RITUAL) {
                int purity = CardObjectives.ritualPurity(data.uniqueInt(id,"obj_"+objective.key+"_missteps"));
                detail = remaining + " block(s) remain · " + ritualConditionNow(player, objective)
                        + " · purity " + purity + "% (" + CardObjectives.ritualGrade(purity) + ").";
            } else {
                int stain = data.uniqueInt(id,"covenant_stain");
                detail = remaining + " target(s) remain · completion writes Covenant Stain " + stain + " → " + (stain+1) + ".";
            }
            out.add(stat("TRACK", lane + " // " + objective.title, progress + " / " + objective.goal, detail));
        }
    }

    private static String clockFromTicks(long ticks) {
        long sec = Math.max(0L, ticks / 20L);
        return (sec / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60L);
    }

    private static void addNearestClause(List<String> out, ServerPlayer player, LockData data, UUID id) {
        CardObjectives.Objective nearest = null;
        float bestRatio = -1f;
        for (CardObjectives.Objective objective : CardObjectives.Objective.values()) {
            if (!data.hasUnique(id, "obj_" + objective.key)) continue;
            float ratio = data.uniqueInt(id, "obj_" + objective.key + "_progress") / (float) Math.max(1, objective.goal);
            if (ratio > bestRatio) { bestRatio = ratio; nearest = objective; }
        }
        if (nearest == null) return;
        int progress = data.uniqueInt(id, "obj_" + nearest.key + "_progress");
        int remaining = Math.max(0, nearest.goal - progress);
        long now = player.level().getGameTime();
        long started = data.uniqueLong(id, "obj_" + nearest.key + "_started_at");
        long elapsed = started == Long.MIN_VALUE ? 0L : Math.max(0L, now - started);
        String detail;
        if (nearest.kind == ForbiddenBargain.Kind.CONTRACT) {
            long swiftLeft = Math.max(0L, CardObjectives.SWIFT_WINDOW_TICKS - elapsed);
            long exactLeft = Math.max(0L, CardObjectives.EXACTING_WINDOW_TICKS - elapsed);
            int best = data.uniqueInt(id, "obj_" + nearest.key + "_best_streak");
            detail = remaining + " target(s) remain · Swift " + (elapsed <= CardObjectives.SWIFT_WINDOW_TICKS ? clockFromTicks(swiftLeft) + " left" : "expired")
                    + " · Exacting " + (elapsed <= CardObjectives.EXACTING_WINDOW_TICKS ? clockFromTicks(exactLeft) + " left; best streak " + best + "/" + CardObjectives.exactingStreakNeeded(nearest) : "expired") + ".";
        } else if (nearest.kind == ForbiddenBargain.Kind.RITUAL) {
            int purity = CardObjectives.ritualPurity(data.uniqueInt(id, "obj_" + nearest.key + "_missteps"));
            detail = remaining + " target block(s) remain · current location: " + ritualConditionNow(player, nearest)
                    + " · purity " + purity + "% (" + CardObjectives.ritualGrade(purity) + ").";
        } else {
            int stain = data.uniqueInt(id, "covenant_stain");
            detail = remaining + " payment target(s) remain · completion writes Covenant Stain " + stain + " → " + (stain + 1) + " and +2 Death pressure.";
        }
        out.add(stat("NEXT", "Nearest Clause", nearest.title, detail));
    }

    private static String ritualConditionNow(ServerPlayer player, CardObjectives.Objective objective) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return "environment unavailable";
        var pos = player.blockPosition();
        boolean met = switch (objective.key) {
            case "stone_communion" -> CardConditions.isDark(level, pos);
            case "vein_litany" -> pos.getY() <= 32;
            case "wooden_confession" -> level.canSeeSky(pos.above());
            case "earthen_vigil" -> CardConditions.isOpenNight(level, pos.above());
            case "obsidian_prayer" -> level.getMaxLocalRawBrightness(pos) >= 10;
            case "sand_liturgy" -> CardConditions.isDirectDaylight(level, pos.above());
            default -> true;
        };
        return met ? "condition presently satisfied" : "condition presently NOT satisfied";
    }

    private static void addActiveLawSchematic(List<String> out, ServerPlayer player, LockData data, UUID id) {
        long now = player.level().getGameTime();
        long day = player.level().getDayTime() / 24000L;
        int active = 0;

        // Death laws get their own rows because their costs cannot be erased by ordinary remedies.
        if (data.hasUnique(id, "memento_mori")) {
            int kills = data.uniqueInt(id, "mori_kills");
            int charge = data.uniqueInt(id, "mori_charge");
            out.add(stat("DEATH", "Memento Mori", kills + " / " + DeathResonance.mementoTarget(data, id) + " names",
                    "Reprieves " + charge + " / 2 · " + data.deathHeartDebt(id) + " heart(s) buried by Death; surplus names carry into the next margin."));
            active++;
        }
        if (data.hasUnique(id, "black_sun")) {
            String state = "DORMANT SHADE";
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                if (sl.isNight()) state = "NIGHT · Strength II + sight";
                else if (CardConditions.isDirectDaylight(sl, player.blockPosition())) state = "DAYLIGHT · Weakness + mark";
            }
            out.add(stat("DEATH", "The Black Sun", state, "Indoor daylight no longer counts as direct sunlight."));
            active++;
        }
        if (data.hasUnique(id, "coffin_road")) {
            out.add(stat("DEATH", "The Coffin Road", "PERMANENT LAW", "Slowness I · fall damage is refused."));
            active++;
        }
        if (data.hasUnique(id, "grave_bell")) {
            out.add(stat("DEATH", "The Grave Bell", data.uniqueInt(id, "grave_bell_kills") + " / " + DeathResonance.graveBellTarget(data, id) + " names",
                    "Hunger I persists · the current resonance target rings payment."));
            active++;
        }

        // Objectives are deliberately individual: this lets the client render progress bars,
        // while shared metadata keeps their fine print and goals consistent with gameplay.
        for (CardObjectives.Objective objective : CardObjectives.Objective.values())
            active += addActiveObjective(out, player, data, id, objective);

        if (data.hasBloodTithe(id)) {
            out.add(stat("CURSE", "Blood Tithe", "Stage " + roman(data.curseStage(id, "blood_tithe", day)),
                    "One bargain heart forfeit · Strength I below half health.")); active++;
        }
        if (data.hasBrittlePilgrimage(id)) {
            out.add(stat("CURSE", "Brittle Pilgrimage", "Stage " + roman(data.curseStage(id, "brittle", day)),
                    "Fall damage escalates as the curse matures.")); active++;
        }
        if (data.hasAshenTongue(id)) {
            out.add(stat("CURSE", "Ashen Tongue", "Stage " + roman(data.ashenTongueStage(id, now)) + " · "
                            + remainingClock(data.uniqueLong(id, "ashen_tongue_until"), now),
                    "Weakness I · experience-shedding chance rises as the mark matures.")); active++;
        }
        if (data.hasUnique(id, "shivering_tithe")) {
            out.add(stat("CURSE", "Shivering Tithe", "ACTIVE", "Haste I and Hunger I remain together.")); active++;
        }
        if (data.curseLoot15Charges(id) > 0) {
            out.add(stat("CURSE", "Diminished Share", data.curseLoot15Charges(id) + " ward(s)", "-15% loot · +1 starting life.")); active++;
        }

        if (data.hasCrimsonBalance(id)) { out.add(stat("SCAR", "Crimson Balance", "ACTIVE", "Strength I + Weakness I.")); active++; }
        if (data.hasGlassNerve(id)) { out.add(stat("SCAR", "Glass Nerve", "ACTIVE", "Speed I + Mining Fatigue I.")); active++; }
        if (data.hasPaleCovenant(id)) { out.add(stat("SCAR", "Pale Covenant", "ACTIVE", "Resistance I + Hunger I.")); active++; }
        if (data.hasOpenVein(id)) { out.add(stat("SCAR", "The Open Vein", "ACTIVE", "Strength I · one bargain heart forfeit.")); active++; }
        if (data.hasThinBlood(id)) { out.add(stat("SCAR", "Thin Blood", data.loot15Charges(id) + " ward payment(s)", "Weakness I remains until resolved.")); active++; }
        if (data.hasStillHeart(id)) { out.add(stat("SCAR", "The Still Heart", "ACTIVE", "Regeneration I + Slowness I.")); active++; }
        if (data.hasUnique(id, "bellglass_sight")) { out.add(stat("SCAR", "Bellglass Sight", "ACTIVE", CardBranches.activeDescription(data, id, ForbiddenBargain.BELLGLASS_SIGHT))); active++; }
        if (data.hasUnique(id, "thorn_ledger")) { out.add(stat("SCAR", "The Thorn Ledger", "ACTIVE", "Melee thorns · +20% incoming projectile damage.")); active++; }
        if (data.hasUnique(id, "dustbound_soles")) { out.add(stat("SCAR", "Dustbound Soles", "ACTIVE", "-30% fall damage · Slowness I.")); active++; }
        if (data.hasUnique(id, "lantern_blood")) { out.add(stat("SCAR", "Lantern Blood", "ACTIVE", "Night Vision · Darkness below five hearts.")); active++; }

        active += addActiveTimer(out, data, id, "ore_whisper_until", "Ore Whisper", now, "WAGER", CardBranches.activeDescription(data, id, ForbiddenBargain.ORE_WHISPER));
        active += addActiveTimer(out, data, id, "momentum_until", "Borrowed Momentum", now, "WAGER", CardBranches.activeDescription(data, id, ForbiddenBargain.BORROWED_MOMENTUM));
        active += addActiveTimer(out, data, id, "black_compass_until", "The Black Compass", now, "WAGER", CardBranches.activeDescription(data, id, ForbiddenBargain.BLACK_COMPASS));
        active += addActiveTimer(out, data, id, "pocket_eclipse_until", "A Pocket Eclipse", now, "WAGER", CardBranches.activeDescription(data, id, ForbiddenBargain.POCKET_ECLIPSE));
        active += addActiveTimer(out, data, id, "quicksilver_until", "Quicksilver Prayer", now, "WAGER", CardBranches.activeDescription(data, id, ForbiddenBargain.QUICKSILVER_PRAYER));

        for (ForbiddenBargain card : ForbiddenBargain.values()) {
            boolean oldProgression = ProgressionCardEffects.isProgressionCard(card);
            boolean expandedProgression = ExpandedProgressionCardEffects.isExpanded(card);
            if (!oldProgression && !expandedProgression) continue;
            boolean shown = oldProgression
                    ? ProgressionCardEffects.activeForDisplay(data, id, card, now)
                    : ExpandedProgressionCardEffects.activeForDisplay(data, id, card, now);
            if (!shown) continue;
            int revision = CardEvolution.activeVariant(data, id, card);
            String runtime = oldProgression
                    ? ProgressionCardEffects.status(data, id, card, now)
                    : ExpandedProgressionCardEffects.status(data, id, card, now);
            out.add(stat("WAGER", CardEvolution.variantTitle(card, revision), runtime, CardEvolution.variantText(card, revision)));
            active++;
        }

        active += addActiveCharge(out, data.uniqueInt(id, "ember_hits"), "The Ember Count", "WAGER", "melee hit(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "coal_kiss_hits"), "Coal Kiss", "WAGER", "melee hit(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "iron_echo_hits"), "Iron Echo", "WAGER", "readied hit(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "hunter_dividend"), "Hunter's Dividend", "WAGER", "hostile kill(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "hearthmark_meals"), "Hearthmark", "WAGER", "meal(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "salt_circle"), "Salt Circle", "WAGER", "undead kill(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "grave_ration"), "Grave Ration", "WAGER", "hostile kill(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "pilgrims_luck_blocks"), "Pilgrim's Luck", "WAGER", "mined block(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "vein_drinker"), "Vein Drinker", "WAGER", "hostile kill(s)");
        active += addActiveCharge(out, data.uniqueInt(id, "ferryman_guard"), "The Ferryman's Ledger", "WAGER", "guarded hit(s)");
        if (data.loadedDiceCharges(id) > 0) { { int v = CardEvolution.activeVariant(data, id, ForbiddenBargain.LOADED_DICE); int bonus = v >= 3 ? 28 : v > 0 ? 25 : 20; out.add(stat("WAGER", CardEvolution.variantTitle(ForbiddenBargain.LOADED_DICE, v), data.loadedDiceCharges(id) + " ward(s)", CardBranches.activeDescription(data, id, ForbiddenBargain.LOADED_DICE))); } active++; }
        if (data.mercysDueCharges(id) > 0) { { int v = CardEvolution.activeVariant(data, id, ForbiddenBargain.MERCYS_DUE); int cost = v >= 3 ? 8 : v >= 2 ? 12 : v == 1 ? 10 : 15; out.add(stat("WAGER", CardEvolution.variantTitle(ForbiddenBargain.MERCYS_DUE, v), data.mercysDueCharges(id) + " ward(s)", CardBranches.activeDescription(data, id, ForbiddenBargain.MERCYS_DUE))); } active++; }
        if (data.hasLastCandle(id)) { out.add(stat("WAGER", "The Last Candle", "NEXT WARD", "Exactly one starting life · +30% loot.")); active++; }

        if (data.hasBlackHarvest(id)) { out.add(stat("LAW", "Grave Interest", data.blackHarvestKills(id) + " / 5 night kills", "The fifth dusk kill pays health, hunger and XP.")); active++; }
        if (data.hasSecondEntry(id)) { out.add(stat("LAW", "A Second Entry", "DAILY LAW", "One lethal correction per Minecraft day.")); active++; }
        if (data.hasUnique(id, "moonlit_hunt")) { out.add(stat("LAW", "The Moonlit Hunt", "NIGHT LAW", "Speed at night; nearby hostiles are roused.")); active++; }
        if (data.hasUnique(id, "red_march")) { out.add(stat("LAW", "The Red March", "WORLD LAW", "Sprinting consumes blood instead of ordinary hunger pressure.")); active++; }
        if (data.hasUnique(id, "first_supper")) { out.add(stat("LAW", "The First Supper", "DAILY LAW", "First completed meal chooses a minute-long boon.")); active++; }
        if (data.hasUnique(id, "last_witness")) { out.add(stat("LAW", "The Last Witness", data.uniqueInt(id, "witness_kills") + " / 7 names", "Echo: " + (data.uniqueInt(id, "witness_charge") > 0 ? "READY" : "empty"))); active++; }
        if (data.hasUnique(id, "cinder_vow")) { out.add(stat("LAW", "Cinder Vow", "WORLD LAW", "Fire damage reduced; melee brands targets.")); active++; }

        if (active == 0) out.add(stat("ACTIVE", "Active Laws", "None", "No signed card is currently altering ordinary play."));
    }

    private static int addActiveObjective(List<String> out, ServerPlayer player, LockData data, UUID id, CardObjectives.Objective objective) {
        String key = objective.key;
        if (!data.hasUnique(id, "obj_" + key)) return 0;
        int progress = data.uniqueInt(id, "obj_" + key + "_progress");
        long now = player.level().getGameTime();
        long started = data.uniqueLong(id, "obj_" + key + "_started_at");
        long elapsed = started == Long.MIN_VALUE ? 0L : Math.max(0L, now - started);
        String detail;
        if (objective.kind == ForbiddenBargain.Kind.CONTRACT) {
            int streak = data.uniqueInt(id, "obj_" + key + "_streak");
            int best = data.uniqueInt(id, "obj_" + key + "_best_streak");
            int needed = CardObjectives.swiftStreakNeeded(objective);
            int exactingNeeded = CardObjectives.exactingStreakNeeded(objective);
            boolean timeOpen = started == Long.MIN_VALUE || elapsed <= CardObjectives.SWIFT_WINDOW_TICKS;
            boolean exactingOpen = started == Long.MIN_VALUE || elapsed <= CardObjectives.EXACTING_WINDOW_TICKS;
            long record = data.uniqueLong(id, "obj_best_time_" + key);
            int recordStreak = data.uniqueInt(id, "obj_record_streak_" + key);
            detail = objective.detail + " · swift " + (timeOpen ? "open" : "expired") + " (need streak " + needed + ")"
                    + " · exacting " + (exactingOpen ? "open" : "expired") + " (need " + exactingNeeded + ")"
                    + " · current/best streak " + streak + "/" + best + " · elapsed " + clockFromTicks(elapsed)
                    + (record == Long.MIN_VALUE ? "." : " · record " + clockFromTicks(record) + ", record streak " + recordStreak + ".");
        } else if (objective.kind == ForbiddenBargain.Kind.RITUAL) {
            int mistakes = data.uniqueInt(id, "obj_" + key + "_missteps");
            int purity = CardObjectives.ritualPurity(mistakes);
            int recordPurity = data.uniqueInt(id, "obj_best_purity_" + key);
            detail = objective.detail + " · purity " + purity + "% (" + CardObjectives.ritualGrade(purity) + ") · "
                    + (mistakes == 0 ? "unscarred: full purity payment remains possible." : mistakes <= 2 ? mistakes + " breach(es): reduced +3 XP payment still possible." : mistakes + " breaches: purity payment lost.")
                    + (recordPurity > 0 ? " · record purity " + recordPurity + "%." : "");
        } else {
            int stain = data.uniqueInt(id, "covenant_stain");
            int completed = data.uniqueInt(id, "obj_completed_" + key);
            detail = objective.detail + " · payment will deepen covenant stain " + stain + " → " + (stain + 1)
                    + " and add Death-hand pressure · prior fulfillments " + completed + ".";
        }
        out.add(stat(objective.category(), objective.title, progress + " / " + objective.goal, detail));
        return 1;
    }

    private static int addActiveTimer(List<String> out, LockData data, UUID id, String key, String title, long now, String kind, String detail) {
        long until = data.uniqueLong(id, key);
        if (until == Long.MIN_VALUE || until <= now) return 0;
        out.add(stat(kind, title, remainingClock(until, now), detail));
        return 1;
    }

    private static int addActiveCharge(List<String> out, int count, String title, String kind, String unit) {
        if (count <= 0) return 0;
        out.add(stat(kind, title, count + " " + unit, "Consumable world-play term."));
        return 1;
    }

    private static String privateLawDetail(LockData data, UUID id, int pact) {
        if (pact < 0) return "No private law is currently binding; signing another replaces the prior one.";
        MasterSignature sig = pact >= 0 && pact < MasterSignature.values().length ? MasterSignature.values()[pact] : null;
        CardMaster dealer = sig == null ? null : CardMaster.forSignature(sig);
        int relation = dealer == null ? 0 : dealer.relation(data, id);
        int tier = dealer == null ? 2 : dealer.relationTier(data, id);
        String scaled = switch (sig == null ? MasterSignature.CROOKED : sig) {
            case CROOKED -> tier <= 1 ? "2.5-block step · 28s recovery" : tier >= 5 ? "4-block step · 16s recovery"
                    : tier >= 4 ? "3.5-block step · 20s recovery" : "3-block step · 24s recovery";
            case VEILED -> tier <= 1 ? "6s veil after 52 still ticks · 38s recovery" : tier >= 5 ? "12s veil + brief Speed after 26 ticks · 22s recovery"
                    : tier >= 4 ? "10s veil after 32 ticks · 26s recovery" : "8s veil after 40 ticks · 30s recovery";
            case EXACTING -> tier <= 1 ? "+40% readied-hit echo · 10s recovery" : tier >= 5 ? "+70% echo · 6s recovery"
                    : tier >= 4 ? "+60% echo · 7s recovery" : "+50% echo · 8s recovery";
        };
        return "Only one private law can bind at once · " + scaled + " · linked dealer favor " + (relation >= 0 ? "+" : "") + relation + ".";
    }

    private static String stat(String category, String label, String value, String detail) {
        return category + SEP + label + SEP + value + SEP + detail;
    }
    private static void addCharge(List<String> out, String name, int count, String unit) { if (count > 0) out.add(name + " " + count + " " + unit); }
    private static void addTimer(List<String> out, String name, long until, long now) {
        if (until == Long.MIN_VALUE || until <= now) return;
        long sec = Math.max(0L, (until - now) / 20L);
        out.add(name + " " + (sec / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60L));
    }
    private static String remainingClock(long until, long now) {
        if (until == Long.MIN_VALUE || until <= now) return "ending";
        long sec = Math.max(0L, (until - now) / 20L);
        return (sec / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60L);
    }

    private static String effectTime(net.minecraft.world.effect.MobEffectInstance effect) {
        if (effect == null) return "0:00";
        int sec = Math.max(0, effect.getDuration() / 20);
        return (sec / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60);
    }
    private static String roman(int stage) { return stage >= 2 ? "II" : stage == 1 ? "I" : "0"; }

    private static boolean hasDiscoveryPrefix(CompoundTag root, String prefix) {
        ListTag list = root.getList(DISCOVERIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).startsWith(prefix)) return true;
        return false;
    }

    private static List<String> discoveriesWithPrefix(CompoundTag root, String prefix) {
        List<String> out = new ArrayList<>();
        ListTag list = root.getList(DISCOVERIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String token = list.getString(i);
            if (token.startsWith(prefix)) out.add(token);
        }
        return out;
    }

    private static void addLine(ServerPlayer player, String key, String kind, String line) {
        CompoundTag root = persistedRoot(player);
        ListTag list = root.getList(key, Tag.TAG_STRING);
        list.add(StringTag.valueOf(encode(kind, line)));
        while (list.size() > MAX_LINES) list.remove(0);
        root.put(key, list);
        saveRoot(player, root);
    }

    private static String encode(String kind, String text) {
        return System.currentTimeMillis() + SEP + kind + SEP + text.replace(SEP, " ");
    }

    private static List<String> linesNewestFirst(CompoundTag root, String key) {
        ListTag list = root.getList(key, Tag.TAG_STRING);
        List<String> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) out.add(list.getString(i));
        return Collections.unmodifiableList(out);
    }

    private static String prettify(String id) {
        if (id == null || id.isBlank()) return "WARD";
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static CompoundTag persistedRoot(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                ? data.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) persisted.put(ROOT, new CompoundTag());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted.getCompound(ROOT);
    }

    private static void saveRoot(ServerPlayer player, CompoundTag rootTag) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                ? data.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
        persisted.put(ROOT, rootTag);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
