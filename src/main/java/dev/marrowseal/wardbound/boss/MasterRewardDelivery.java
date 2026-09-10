package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Delivers Master rewards only after the player has safely left the private arena. */
public final class MasterRewardDelivery {
    private MasterRewardDelivery() {}

    private record Reward(ItemStack stack, boolean uniqueRelic) {
        static Reward unique(ItemStack stack) { return new Reward(stack, true); }
        static Reward stackable(ItemStack stack) { return new Reward(stack, false); }
    }

    public static void queue(ServerPlayer player, MasterArenaManager.Kind kind) {
        if (player == null || player.getServer() == null || kind == null) return;
        LockData.get(player.getServer()).setUniqueInt(player.getUUID(), key(kind), 1);
    }

    public static void tick(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.isAlive() || player.isSpectator()) return;
        if (MasterArenaManager.active(player) || player.level().dimension().equals(MasterArenaManager.REALM)) return;
        LockData data = LockData.get(player.getServer()); UUID id = player.getUUID();
        for (MasterArenaManager.Kind kind : MasterArenaManager.Kind.values()) {
            if (data.uniqueInt(id, key(kind)) == 0) continue;
            List<Reward> rewards = rewards(kind);
            if (!canFitAll(player, rewards)) continue;

            // Keep delivery atomic. canFitAll should make insertion deterministic, but another mod
            // can still mutate inventory during an add hook. Without rollback, a partial delivery
            // would leave the pending flag set and duplicate the already-added stacks next tick.
            List<ItemStack> before = new ArrayList<>(player.getInventory().items.size());
            for (ItemStack slot : player.getInventory().items) before.add(slot.copy());
            boolean ok = true;
            for (Reward reward : rewards) {
                ItemStack stack = reward.stack();
                if (stack.isEmpty() || (reward.uniqueRelic() && contains(player, stack))) continue;
                if (!player.getInventory().add(stack.copy())) { ok = false; break; }
            }
            if (!ok) {
                for (int i = 0; i < before.size(); i++) player.getInventory().items.set(i, before.get(i));
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                continue;
            }

            int xp = xp(kind); if (xp > 0) player.giveExperiencePoints(xp);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            data.setUniqueInt(id, key(kind), 0);
            WardHud.send(player, deliveredLine(kind), WardHud.Mood.REWARD, 3400);
        }
    }

    private static boolean contains(ServerPlayer player, ItemStack wanted) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (ItemStack.isSameItemSameTags(player.getInventory().getItem(i), wanted)) return true;
        return false;
    }

    /** Exact preflight for Inventory#add against the 36 ordinary inventory slots. */
    private static boolean canFitAll(ServerPlayer player, List<Reward> rewards) {
        List<ItemStack> slots = new ArrayList<>(player.getInventory().items.size());
        for (ItemStack stack : player.getInventory().items) slots.add(stack.copy());

        for (Reward reward : rewards) {
            ItemStack wanted = reward.stack();
            if (wanted.isEmpty() || (reward.uniqueRelic() && contains(player, wanted))) continue;
            ItemStack remaining = wanted.copy();

            if (remaining.isStackable()) {
                for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
                    ItemStack slot = slots.get(i);
                    if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, remaining)) continue;
                    int room = Math.min(slot.getMaxStackSize(), remaining.getMaxStackSize()) - slot.getCount();
                    if (room <= 0) continue;
                    int move = Math.min(room, remaining.getCount());
                    slot.grow(move);
                    remaining.shrink(move);
                }
            }

            for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
                if (!slots.get(i).isEmpty()) continue;
                int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                slots.set(i, placed);
                remaining.shrink(move);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static List<Reward> rewards(MasterArenaManager.Kind kind) { return switch (kind) {
        case GAMBLER -> List.of(
                Reward.unique(new ItemStack(WardItems.HOUSEBREAKERS_ACE.get())),
                Reward.unique(new ItemStack(WardItems.DEALERS_HAND.get())));
        case CURATOR -> List.of(Reward.unique(new ItemStack(WardItems.LAST_MARGIN.get())));
        case NOTARY -> List.of(Reward.unique(new ItemStack(WardItems.UNSIGNED_VERDICT.get())));
        // Maestro's materials are payment, not one-per-player relic flags. Existing copies must
        // never cause the new reward stack to disappear.
        case MAESTRO -> List.of(
                Reward.stackable(new ItemStack(WardItems.ECHO_SHARD.get(), 4)),
                Reward.stackable(new ItemStack(Items.NETHERITE_INGOT, 2)),
                Reward.stackable(new ItemStack(Items.MUSIC_DISC_5)),
                Reward.unique(new ItemStack(WardItems.CANTICLE_FOR_THE_SLEEPER.get())));
    }; }

    private static int xp(MasterArenaManager.Kind kind) { return switch (kind) {
        case GAMBLER, CURATOR -> 250;
        case NOTARY -> 275;
        case MAESTRO -> 325;
    }; }

    private static String key(MasterArenaManager.Kind kind) { return "master_reward_" + kind.name().toLowerCase() + "_pending"; }
    private static String deliveredLine(MasterArenaManager.Kind kind) { return switch (kind) {
        case GAMBLER -> "THE HOUSE'S DUE // The Ace and the Dealer's private Hand have followed you out of the Last Table.";
        case CURATOR -> "ASH ARCHIVE // The Last Margin has been filed into your possession.";
        case NOTARY -> "FINAL JUDGMENT // An unsigned verdict has followed the witness home.";
        case MAESTRO -> "CURTAIN CALL // The material payment followed you out. So did a Canticle whose notation points toward the End.";
    }; }
}
