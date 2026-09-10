package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.MinigameMastery;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real progression gate for the existing head-only Cthulhu encounter. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class CthulhuHeadProgression {
    private static final String RETURN_CANTICLE = "cthulhu_head_canticle_return_pending";
    private static final String REWARD_PENDING = "cthulhu_head_vestige_pending";
    private CthulhuHeadProgression() {}

    public static boolean invoke(ServerPlayer p) {
        if (p == null || p.getServer() == null) return false;
        LockData d = LockData.get(p.getServer());
        if (d.uniqueInt(p.getUUID(), "maestro_defeated") == 0) {
            WardHud.send(p, "THE CANTICLE // You possess notes whose performer has not yet been concluded.", WardHud.Mood.WARD, 3000);
            return false;
        }
        if (d.totalBeaten(p.getUUID()) < WardConfig.cthulhuAfterBeaten) {
            WardHud.send(p, "THE CANTICLE // The scar is audible, but your record has not reached the depth that can hold it. "
                    + d.totalBeaten(p.getUUID()) + "/" + WardConfig.cthulhuAfterBeaten + " wards.", WardHud.Mood.GLITCH, 3600);
            return false;
        }
        if (WardConfig.cthulhuWardsEnabled
                && MinigameMastery.wins(d, p.getUUID(), MinigameType.CTHULHUS_GAME) <= 0) {
            WardHud.send(p, "THE CANTICLE // The notation has no completed examination to follow. Survive Cthulhu's Game before asking the scar to become a door.",
                    WardHud.Mood.GLITCH, 4200);
            return false;
        }
        if (!p.level().dimension().equals(Level.END)) {
            WardHud.send(p, "THE CANTICLE // The notation refuses this sky. Carry it into the End.", WardHud.Mood.GLITCH, 3600);
            return false;
        }
        if (d.uniqueInt(p.getUUID(), "cthulhu_head_defeated") > 0) {
            WardHud.send(p, "THE CANTICLE // The severed dream has already answered you.", WardHud.Mood.WARD, 3000);
            return false;
        }
        if (!contains(p, WardItems.CANTICLE_FOR_THE_SLEEPER.get())) return false;
        if (!CthulhuHeadArena.start(p, false)) return false;
        // Commit the unique key to the encounter. Keeping it in the inventory allowed the death
        // drop to remain on the ground while RETURN_CANTICLE also issued a replacement.
        removeAll(p, WardItems.CANTICLE_FOR_THE_SLEEPER.get());
        d.setUniqueInt(p.getUUID(), RETURN_CANTICLE, 1);
        return true;
    }

    public static void noteFailedAttempt(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        // The Canticle is committed at encounter start, so the pending return already represents
        // ownership. Reassert it here for legacy/in-progress saves without depending on inventory.
        LockData.get(p.getServer()).setUniqueInt(p.getUUID(), RETURN_CANTICLE, 1);
    }

    public static void queueVictory(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        LockData d = LockData.get(p.getServer());
        removeAll(p, WardItems.CANTICLE_FOR_THE_SLEEPER.get());
        d.setUniqueInt(p.getUUID(), RETURN_CANTICLE, 0);
        d.setUniqueInt(p.getUUID(), REWARD_PENDING, 1);
        WardHistory.recordSpecial(p, "GLITCH",
                "SEVERED DREAM // The head was only a proximal manifestation. Its final residue points toward a body that never entered the arena.");
    }

    public static void deliverVictory(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        LockData d = LockData.get(p.getServer());
        if (d.uniqueInt(p.getUUID(), REWARD_PENDING) == 0) return;
        boolean delivered = contains(p, WardItems.ABYSSAL_VESTIGE.get());
        if (!delivered) {
            ItemStack reward = new ItemStack(WardItems.ABYSSAL_VESTIGE.get());
            delivered = MasterInvocationRelics.giveOrProtectedDrop(p, reward);
        }
        if (!delivered) return;
        d.setUniqueInt(p.getUUID(), REWARD_PENDING, 0);
        WardHud.send(p,
                "ABYSSAL VESTIGE // The Head carried a memory of its absent body. The Idol reacts to it.",
                WardHud.Mood.REWARD, 5200);
    }

    @SubscribeEvent public static void restoreCanticle(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (p.getServer() == null || !p.isAlive() || p.isSpectator() || CthulhuHeadArena.active(p)) return;
        LockData d = LockData.get(p.getServer());
        if (d.uniqueInt(p.getUUID(), REWARD_PENDING) != 0) deliverVictory(p);
        if (d.uniqueInt(p.getUUID(), RETURN_CANTICLE) == 0) return;
        boolean restored = contains(p, WardItems.CANTICLE_FOR_THE_SLEEPER.get());
        if (!restored) {
            ItemStack key = new ItemStack(WardItems.CANTICLE_FOR_THE_SLEEPER.get());
            restored = MasterInvocationRelics.giveOrProtectedDrop(p, key);
        }
        if (!restored) return;
        d.setUniqueInt(p.getUUID(), RETURN_CANTICLE, 0);
        WardHud.send(p, "THE CANTICLE // The notation survived the failed dream.", WardHud.Mood.GLITCH, 2600);
    }

    private static boolean contains(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) return true;
        return false;
    }
    private static void removeAll(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) p.getInventory().setItem(i, ItemStack.EMPTY);
        p.getInventory().setChanged(); p.containerMenu.broadcastChanges();
    }
}
