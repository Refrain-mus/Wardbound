package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import java.util.UUID;

/** Permanent laws taken from the Pale Gambler after the Last Table. */
public final class MasterRewards {
    private MasterRewards() {}

    public static final String ACE_WARD_LAW = "house_ace_ward_law";
    public static final String ACE_WORLD_LAW = "house_ace_world_law";

    public static boolean hasWardLaw(LockData d, UUID id) {
        return d != null && id != null && d.hasUnique(id, ACE_WARD_LAW);
    }

    public static boolean hasWorldLaw(LockData d, UUID id) {
        return d != null && id != null && d.hasUnique(id, ACE_WORLD_LAW);
    }

    public static int signedCount(LockData d, UUID id) {
        int n = 0;
        if (hasWardLaw(d, id)) n++;
        if (hasWorldLaw(d, id)) n++;
        return n;
    }

    /**
     * The Ace no longer queues a disposable next-ward trick. Each signature
     * permanently steals one of the House's laws. Each of the two laws can only
     * be signed once; taking both consumes the relic.
     */
    public static boolean signAceLaw(LockData d, UUID id, int choice, boolean debug) {
        if (d == null || id == null || choice < 0 || choice > 1) return false;
        if (!debug && d.uniqueInt(id, "gambler_defeated") == 0) return false;

        String key = choice == 0 ? ACE_WARD_LAW : ACE_WORLD_LAW;
        if (d.hasUnique(id, key)) return false;
        if (!debug && signedCount(d, id) >= 2) return false;

        d.setUnique(id, key, true);
        // Keep the old counter synchronized for save compatibility / old UI code.
        d.setUniqueInt(id, "house_ace_uses", signedCount(d, id));
        // Any pre-rework queued one-ward cut is obsolete once a permanent law is signed.
        d.setUniqueInt(id, "house_ace_queued", 0);
        return true;
    }
}
