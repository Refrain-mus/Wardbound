package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The first progression step beyond the three known Masters.
 * Maestro is deliberately not added to CardMaster: he is the first proof that
 * Gambler, Curator and Notary were only the limits of the known record.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class MaestroProgression {
    private MaestroProgression() {}

    public static boolean threeMastersDefeated(LockData data, java.util.UUID id) {
        return data != null && id != null
                && data.uniqueInt(id, "gambler_defeated") > 0
                && data.uniqueInt(id, "curator_defeated") > 0
                && data.uniqueInt(id, "notary_defeated") > 0;
    }

    public static boolean eligible(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        LockData data = LockData.get(player.getServer());
        return threeMastersDefeated(data, player.getUUID())
                && data.totalBeaten(player.getUUID()) >= WardConfig.deathCardsAfterBeaten;
    }

    public static String status(ServerPlayer player) {
        if (player == null || player.getServer() == null) return "Maestro // no witness";
        LockData d = LockData.get(player.getServer());
        var id = player.getUUID();
        return "Known Masters "
                + d.uniqueInt(id, "gambler_defeated") + "/1 Gambler · "
                + d.uniqueInt(id, "curator_defeated") + "/1 Curator · "
                + d.uniqueInt(id, "notary_defeated") + "/1 Notary · "
                + "wards " + d.totalBeaten(id) + "/" + WardConfig.deathCardsAfterBeaten + " · "
                + "Maestro " + d.uniqueInt(id, "maestro_defeated") + "/1";
    }

    public static void unlockIfReady(ServerPlayer player) {
        if (player == null || player.getServer() == null || !eligible(player)) return;
        LockData d = LockData.get(player.getServer());
        if (d.uniqueInt(player.getUUID(), "maestro_defeated") > 0) return;
        if (d.uniqueInt(player.getUUID(), "maestro_score_revealed") > 0) return;
        if (contains(player, WardItems.SCORE_BEYOND_THE_MARGIN.get())) return;

        ItemStack score = new ItemStack(WardItems.SCORE_BEYOND_THE_MARGIN.get());
        if (!player.getInventory().add(score)) player.drop(score, false);
        player.getInventory().setChanged();

        if (d.uniqueInt(player.getUUID(), "maestro_score_revealed") == 0) {
            d.setUniqueInt(player.getUUID(), "maestro_score_revealed", 1);
            dev.marrowseal.wardbound.WardAdvancements.maestroRevealed(player);
            WardHistory.recordSpecial(player, "GLITCH",
                    "THREE CONCLUSIONS // A fourth score appeared after the Gambler, Curator and Notary were all concluded. It is not a superior office in their order; it belongs to a road their records never touched.");
            WardHud.send(player,
                    "SILAS VEYR // No. I know those three hands. This belongs to none of them. There is no reference to its author in their bargains, archives or court. I thought those three were the whole threat. I was wrong about the size of the world.",
                    WardHud.Mood.GLITCH, 6200);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) unlockIfReady(p);
    }

    public static boolean summon(ServerPlayer p, boolean practice, boolean debug) {
        if (p == null || p.getServer() == null) return false;
        if (p.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            p.displayClientMessage(Component.literal("MAESTRO // Master encounters require Easy difficulty or higher."), false);
            return false;
        }
        LockData d = LockData.get(p.getServer());
        if (!debug) {
            if (!eligible(p)) {
                WardHud.send(p, "THE SCORE // Three known Masters still occupy the record. The fourth movement remains unreadable.", WardHud.Mood.WARD, 3600);
                return false;
            }
            if (d.uniqueInt(p.getUUID(), "maestro_defeated") > 0) {
                WardHud.send(p, "THE SCORE // That performance already has an ending.", WardHud.Mood.WARD, 3000);
                return false;
            }
            if (!contains(p, WardItems.SCORE_BEYOND_THE_MARGIN.get())) {
                WardHud.send(p, "THE SCORE // The fourth movement is not in your possession.", WardHud.Mood.WARD, 3000);
                return false;
            }
            if (!MasterInvocationRelics.canInvoke(p, MasterArenaManager.Kind.MAESTRO)) return false;
        }

        for (var level : p.getServer().getAllLevels())
            for (var e : level.getAllEntities())
                if (e instanceof MaestroEntity m && !m.isRemoved() && m.includes(p.getUUID())) {
                    WardHud.send(p, "MAESTRO // Your performance is already in progress.", WardHud.Mood.WARD, 2600);
                    return false;
                }

        MasterArenaManager.Arena arena = MasterArenaManager.enter(p, MasterArenaManager.Kind.MAESTRO);
        if (arena == null) return false;
        MaestroEntity boss = WardBosses.MAESTRO.get().create(arena.level());
        if (boss == null) { MasterArenaManager.abort(p); return false; }
        Vec3 at = Vec3.atBottomCenterOf(arena.center());
        boss.moveTo(at.x, at.y, at.z, 0, 0);
        boss.begin(p, arena.center(), practice);
        if (!arena.level().addFreshEntity(boss)) { MasterArenaManager.abort(p); return false; }

        WardHud.send(p,
                practice ? "MAESTRO // Rehearsal. Nothing beyond the curtain will remember this."
                        : "FOURTH MOVEMENT // This stage was never part of the three known Masters' world.",
                practice ? WardHud.Mood.WARD : WardHud.Mood.GLITCH, 3600);
        return true;
    }

    private static boolean contains(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(item)) return true;
        return false;
    }
}
