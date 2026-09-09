package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Shared environmental predicates used by world-play cards and rituals. */
public final class CardConditions {
    private CardConditions() {}

    /** Low enough local light for darkness-reactive cards. */
    public static boolean isDark(ServerLevel level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos) <= 7;
    }

    /** Actual direct daylight: daytime, exposed to the sky, and locally bright. */
    public static boolean isDirectDaylight(ServerLevel level, BlockPos pos) {
        return !level.isNight() && level.canSeeSky(pos) && level.getMaxLocalRawBrightness(pos) >= 12;
    }

    /** Night while exposed to the sky; useful for rituals that explicitly invoke the night. */
    public static boolean isOpenNight(ServerLevel level, BlockPos pos) {
        return level.isNight() && level.canSeeSky(pos);
    }
}
