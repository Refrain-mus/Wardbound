package dev.marrowseal.wardbound.champion;

import dev.marrowseal.wardbound.CardMaster;
import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.MasterStory;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** One source of truth for every Silas dialogue, fragment and presentation gate. */
public final class SilasProgression {
    public static final int TRACE = 0;
    public static final int WAYFARER = 1;
    public static final int COMPANIONS = 2;
    public static final int HANDS = 3;
    public static final int UNCONCLUDED = 4;
    public static final int WITNESS = 5;

    private SilasProgression() {}

    public static int stage(ServerPlayer player) {
        if (player == null || player.getServer() == null) return TRACE;
        return stage(LockData.get(player.getServer()), player.getUUID());
    }

    public static int stage(LockData data, UUID id) {
        if (data == null || id == null) return TRACE;
        int max = maxChapter(data, id);
        int defeated = defeatedMasters(data, id);
        if (defeated >= 2) return WITNESS;
        if (defeated >= 1 || max >= 7) return UNCONCLUDED;
        if (max >= 6) return HANDS;
        if (max >= 5) return COMPANIONS;
        if (max >= 4) return WAYFARER;
        return TRACE;
    }

    public static int maxChapter(LockData data, UUID id) {
        int max = 0;
        for (CardMaster master : CardMaster.values()) max = Math.max(max, MasterStory.chapter(data, id, master));
        return max;
    }

    public static int defeatedMasters(LockData data, UUID id) {
        int n = 0;
        if (data.uniqueInt(id, "gambler_defeated") > 0) n++;
        if (data.uniqueInt(id, "curator_defeated") > 0) n++;
        if (data.uniqueInt(id, "notary_defeated") > 0) n++;
        return n;
    }

    public static boolean encounterUnlocked(ServerPlayer player) {
        return stage(player) >= WAYFARER;
    }

    public static String ledgerIdentity(int stage) {
        return switch (stage) {
            case TRACE -> "UNKNOWN WAYFARER";
            case WAYFARER -> "THE FAILED CHAMPION";
            case COMPANIONS, HANDS -> "SILAS VEYR";
            case UNCONCLUDED -> "SILAS VEYR // UNCONCLUDED";
            default -> "SILAS VEYR // LAST WITNESS";
        };
    }
}
