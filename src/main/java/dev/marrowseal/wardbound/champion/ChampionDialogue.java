package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.OpenSilasDialoguePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import dev.marrowseal.wardbound.item.WardItems;
import dev.marrowseal.wardbound.item.WornScabbardRingItem;
import net.minecraft.world.entity.item.ItemEntity;

/** Progression-bounded dialogue pools. Silas never leaks a later revelation into an earlier road. */
public final class ChampionDialogue {
    // Index 0 is intentionally unused: Silas does not appear during TRACE.
    private static final int[] POOL_SIZES = {0, 5, 5, 5, 5, 3};

    private ChampionDialogue() {}

    public static boolean speak(ServerPlayer p, boolean repeat) {
        if (!ChampionEncounters.eligible(p)) {
            WardHud.send(p, Component.translatable("silas.too_early").getString(), WardHud.Mood.WARD);
            return false;
        }
        LockData data = LockData.get(p.getServer());
        int stage = SilasProgression.stage(data, p.getUUID());
        stage = Math.max(SilasProgression.WAYFARER, Math.min(SilasProgression.WITNESS, stage));

        long now = p.serverLevel().getGameTime();
        long last = p.getPersistentData().getLong("SilasLastTalk");
        int cooldown = stage >= SilasProgression.WITNESS ? 180 : 100;
        if (now >= last && now - last < cooldown && p.getPersistentData().contains("SilasLastTalk")) return false;
        p.getPersistentData().putLong("SilasLastTalk", now);

        firstStageObservation(p, data, stage);

        String indexKey = "silas_stage_" + stage + "_index";
        int pool = POOL_SIZES[stage];
        int index;
        if (repeat && data.uniqueInt(p.getUUID(), "silas_latest_stage") == stage) {
            index = Math.floorMod(data.uniqueInt(p.getUUID(), "silas_latest_index"), pool);
        } else {
            int cursor = Math.floorMod(data.uniqueInt(p.getUUID(), indexKey), pool);
            index = cursor;
            data.setUniqueInt(p.getUUID(), indexKey, (cursor + 1) % pool);
        }

        data.setUniqueInt(p.getUUID(), "silas_latest_stage", stage);
        data.setUniqueInt(p.getUUID(), "silas_latest_index", index);
        if (!repeat) {
            String talksKey = "silas_stage_" + stage + "_talks";
            int talks = data.uniqueInt(p.getUUID(), talksKey) + 1;
            data.setUniqueInt(p.getUUID(), talksKey, talks);
            // The grip is not a random page in a chest. The player notices it by actually spending time with Silas.
            if (stage >= SilasProgression.WAYFARER && talks >= 2) SilasLore.observeGrip(p);
        }

        String key = "silas.dialogue." + stage + "." + index;
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenSilasDialoguePacket(key, stage));
        WardHistory.recordSpecial(p, "WARD", "Silas Veyr // " + stageLabel(stage) + " // line " + (index + 1));
        return true;
    }


    public static boolean offerCompleteTestimony(ServerPlayer p, ItemStack held) {
        if (p == null || p.getServer() == null || held == null || !held.is(WardItems.UNCONCLUDED_TESTIMONY.get())) return false;
        LockData data = LockData.get(p.getServer());
        int stage = SilasProgression.stage(data, p.getUUID());
        if (stage < SilasProgression.HANDS) {
            Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new OpenSilasDialoguePacket("silas.dialogue.testimony_not_yet", Math.max(SilasProgression.WAYFARER, stage)));
            return true;
        }
        if (data.uniqueInt(p.getUUID(), "silas_ring_received") != 0) {
            if (!hasScabbardRing(p)) {
                deliverScabbardRing(p, held, false);
                data.setUniqueInt(p.getUUID(), "silas_call_unlocked", 1);
                WardHud.send(p, "THE WORN SCABBARD RING // RETURNED TO YOUR HAND", WardHud.Mood.REWARD);
                WardHistory.recordSpecial(p, "WARD", "SILAS VEYR // The ring's record said it had already been entrusted. The missing object was restored to the witness rather than duplicating the reward state.");
                Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                        new OpenSilasDialoguePacket("silas.dialogue.ring_reward", stage));
                return true;
            }
            Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new OpenSilasDialoguePacket("silas.dialogue.testimony_already_given", stage));
            return true;
        }
        held.shrink(1);
        deliverScabbardRing(p, held, true);
        data.setUniqueInt(p.getUUID(), "silas_ring_received", 1);
        data.setUniqueInt(p.getUUID(), "silas_call_unlocked", 1);
        WardHistory.recordSpecial(p, "WARD", "SILAS VEYR // The twelve unresolved testimonies were returned. Silas removed the worn ring from Nhal-Sûl's scabbard and entrusted it to the witness.");
        WardHud.send(p, "THE WORN SCABBARD RING // SILAS WILL ANSWER IF HE CAN", WardHud.Mood.REWARD);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new OpenSilasDialoguePacket("silas.dialogue.ring_reward", stage));
        return true;
    }


    private static boolean hasScabbardRing(ServerPlayer player) {
        if (player == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(WardItems.WORN_SCABBARD_RING.get())) return true;
        }
        return false;
    }

    /**
     * Ring delivery is deliberately transaction-safe. Consuming the testimony frees the main-hand
     * slot in the common case, so prefer putting the reward directly there; otherwise use inventory
     * insertion and finally a protected world drop rather than silently losing it.
     */
    private static void deliverScabbardRing(ServerPlayer player, ItemStack testimonyStack, boolean preferFreedHand) {
        ItemStack ring = WornScabbardRingItem.boundTo(player);
        int selectedSlot = player.getInventory().selected;
        if (preferFreedHand && selectedSlot >= 0 && selectedSlot < player.getInventory().getContainerSize()) {
            ItemStack selected = player.getInventory().getItem(selectedSlot);
            if (selected.isEmpty() || (testimonyStack != null && testimonyStack.isEmpty())) {
                player.getInventory().setItem(selectedSlot, ring);
                player.inventoryMenu.broadcastChanges();
                return;
            }
        }
        if (!player.getInventory().add(ring)) {
            ItemEntity drop = player.drop(ring, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    public static boolean speakAfterUnconcluded(ServerPlayer p) {
        if (p == null || p.getServer() == null) return false;
        int stage = Math.max(SilasProgression.WAYFARER, Math.min(SilasProgression.WITNESS, SilasProgression.stage(p)));
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new OpenSilasDialoguePacket("silas.dialogue.post_unconcluded", stage));
        WardHistory.recordSpecial(p, "GLITCH", "Silas Veyr // after the unfinished death: Look away.");
        return true;
    }

    private static void firstStageObservation(ServerPlayer p, LockData data, int stage) {
        String key = "silas_stage_seen_" + stage;
        if (data.uniqueInt(p.getUUID(), key) != 0) return;
        data.setUniqueInt(p.getUUID(), key, 1);
        String line = switch (stage) {
            case SilasProgression.WAYFARER -> "SILAS VEYR // The road has placed a failed champion in your path. His sword is better kept than the man carrying it.";
            case SilasProgression.COMPANIONS -> "SILAS VEYR // Two absent names now recur around Nhal-Sûl: Sera and İlen.";
            case SilasProgression.HANDS -> "OBSERVATION // When the three Masters are named, anger is not Silas Veyr's first reaction. Fear arrives before it.";
            case SilasProgression.UNCONCLUDED -> "OMITTED TESTIMONY // Silas speaks of endings as if he has survived more than one of his own.";
            default -> "WITNESS // Silas no longer sounds like a teacher. He watches to see whether your road bends where his did.";
        };
        WardHistory.recordSpecial(p, stage >= SilasProgression.HANDS ? "GLITCH" : "WARD", line);
        WardHud.send(p, "SILAS VEYR // A new margin opens in the Witness Ledger.", WardHud.Mood.GLITCH);
    }

    private static String stageLabel(int stage) {
        return switch (stage) {
            case SilasProgression.WAYFARER -> "WAYFARER";
            case SilasProgression.COMPANIONS -> "THREE COMPANIONS";
            case SilasProgression.HANDS -> "THE HANDS BEHIND IT";
            case SilasProgression.UNCONCLUDED -> "UNCONCLUDED";
            default -> "WITNESS";
        };
    }

    public static boolean reactToAwakenedNhal(ServerPlayer p, ItemStack held) {
        if (p == null || p.getServer() == null || held == null || !(held.getItem() instanceof dev.marrowseal.wardbound.nhalsul.NhalSulItem)
                || !dev.marrowseal.wardbound.nhalsul.NhalSulItem.awakened(held)) return false;
        LockData data = LockData.get(p.getServer());
        if (data.uniqueInt(p.getUUID(), "silas_saw_awakened_nhal") != 0) return false;
        data.setUniqueInt(p.getUUID(), "silas_saw_awakened_nhal", 1);
        WardHistory.recordSpecial(p, "GLITCH", "NHAL-SÛL // Silas Veyr did not recognise the awakened mechanism of the Unwritten Cut. The physical blade he carried never revealed it to him.");
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new OpenSilasDialoguePacket("silas.dialogue.nhal_awakened_unknown", SilasProgression.WITNESS));
        return true;
    }

    public static void masterReaction(ServerPlayer p, LockData data, CardMaster master) {
        if (master != CardMaster.ASHEN_CURATOR) return;
        if (SilasProgression.stage(data, p.getUUID()) < SilasProgression.HANDS) return;
        String key = "silas_reaction_" + master.id;
        if (data.uniqueInt(p.getUUID(), key) != 0) return;
        data.setUniqueInt(p.getUUID(), key, 1);
        WardHud.send(p, Component.translatable("silas.master." + master.id).getString(), WardHud.Mood.GLITCH);
    }
}
