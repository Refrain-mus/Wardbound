package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Rare late-game misinformation that never changes the authoritative puzzle state. */
public final class MinigameDeception {
    private MinigameDeception() {}

    public enum Mode {
        NONE("", ""),
        FALSE_CHIME("deception · false chime", "Lives and board state remain truthful."),
        FALSE_CLOCK("deception · borrowed seconds", "The final danger band and numeric clock remain truthful."),
        GHOST_CRACK("deception · ghost wound", "The life eyes remain authoritative."),
        FALSE_CONFIRM("deception · counterfeit assent", "Progress marks and puzzle geometry remain authoritative.");

        public final String label;
        public final String reliableChannel;
        Mode(String label, String reliableChannel) {
            this.label = label;
            this.reliableChannel = reliableChannel;
        }
    }

    public static Mode byOrdinal(int ordinal) {
        Mode[] v = Mode.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : Mode.NONE;
    }

    public static Mode choose(LockData data, UUID player, MinigameType game, long seed,
                              int resolvedWards, int masteryTier, boolean blocked,
                              MinigameCorruption.Variant corruption) {
        if (!WardConfig.deceptionLayerEnabled || data == null || player == null || blocked || game == MinigameType.CTHULHUS_GAME) return Mode.NONE;
        if (resolvedWards < WardConfig.deceptionUnlockAfter || masteryTier < 2) return Mode.NONE;
        float readiness = MinigameSkillProfile.challengeReadiness(data, player);
        float chance = Math.min(WardConfig.deceptionMaxChance, WardConfig.deceptionBaseChance
                + Math.max(0, resolvedWards - WardConfig.deceptionUnlockAfter) * 0.00013f + readiness * 0.018f);
        // Low composure does not attract more false feedback. The system learns restraint too.
        if (MinigameSkillProfile.samples(data, player, MinigameSkillProfile.Axis.COMPOSURE) >= 3
                && MinigameSkillProfile.normalized(data, player, MinigameSkillProfile.Axis.COMPOSURE) < 0.48f) chance *= 0.55f;
        if (roll(seed, player.getLeastSignificantBits(), 0xD3CE710L) >= chance) return Mode.NONE;
        Mode[] pool = {Mode.FALSE_CHIME, Mode.FALSE_CLOCK, Mode.GHOST_CRACK, Mode.FALSE_CONFIRM};
        if (WardConfig.adaptiveSkillProfileEnabled && MinigameSkillProfile.observedAxes(data, player) >= 3) {
            Mode preferred = switch (MinigameSkillProfile.strongest(data, player)) {
                case TIMING -> Mode.FALSE_CLOCK;
                case LOGIC -> Mode.FALSE_CONFIRM;
                case MEMORY -> Mode.FALSE_CHIME;
                case COMPOSURE -> Mode.GHOST_CRACK;
            };
            boolean conflict = preferred == Mode.FALSE_CLOCK && corruption != null
                    && corruption.profile() == MinigameCorruption.Profile.FALSE_CLOCK;
            if (!conflict && roll(seed, player.getMostSignificantBits(), 0xA0A971EL) < 0.62f) return preferred;
        }
        int start = Math.floorMod((int) mix(seed ^ player.getMostSignificantBits() ^ 0x51A1D00DL), pool.length);
        for (int i = 0; i < pool.length; i++) {
            Mode m = pool[(start + i) % pool.length];
            if (m == Mode.FALSE_CLOCK && corruption != null
                    && corruption.profile() == MinigameCorruption.Profile.FALSE_CLOCK) continue;
            return m;
        }
        return Mode.FALSE_CHIME;
    }

    public static void recordEncounter(ServerPlayer player, LockData data, Mode mode) {
        if (player == null || data == null || mode == null || mode == Mode.NONE) return;
        UUID id = player.getUUID();
        int bit = 1 << (mode.ordinal() - 1);
        int mask = data.uniqueInt(id, "minigame_deception_mask");
        if ((mask & bit) != 0) return;
        data.setUniqueInt(id, "minigame_deception_mask", mask | bit);
        WardHistory.recordSpecial(player, "DECEPTION", mode.label.replace("deception · ", "")
                + " // reliable channel: " + mode.reliableChannel);
    }

    private static float roll(long a, long b, long salt) {
        long z = mix(a ^ Long.rotateLeft(b, 17) ^ salt);
        return (z >>> 40) / (float) (1L << 24);
    }

    private static long mix(long z) {
        z ^= z >>> 33; z *= 0xff51afd7ed558ccdL;
        z ^= z >>> 33; z *= 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
