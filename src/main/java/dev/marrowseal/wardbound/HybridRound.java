package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Very rare micro-rounds borrowed from a different minigame family. */
public final class HybridRound {
    private HybridRound() {}

    public enum Mode {
        NONE(""),
        RUNE_ECHO("hybrid · rune echo"),
        PARALLAX_SNAP("hybrid · parallax snap"),
        PRESSURE_CLAMP("hybrid · pressure clamp"),
        LATTICE_KNOT("hybrid · lattice knot");

        public final String label;
        Mode(String label) { this.label = label; }
    }

    public static Mode byOrdinal(int ordinal) {
        Mode[] v = Mode.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : Mode.NONE;
    }

    public static Mode choose(LockData data, UUID player, MinigameType host, long seed,
                              int resolvedWards, int masteryTier, boolean blocked) {
        if (!WardConfig.hybridRoundsEnabled || data == null || player == null || blocked || host == MinigameType.CTHULHUS_GAME) return Mode.NONE;
        if (resolvedWards < WardConfig.hybridUnlockAfter || masteryTier < 2) return Mode.NONE;
        float readiness = MinigameSkillProfile.challengeReadiness(data, player);
        float chance = Math.min(WardConfig.hybridMaxChance, WardConfig.hybridBaseChance
                + Math.max(0, resolvedWards - WardConfig.hybridUnlockAfter) * 0.00007f + readiness * 0.012f);
        if (roll(seed, player.getMostSignificantBits(), 0x48B21DL) >= chance) return Mode.NONE;

        Mode[] pool = switch (host) {
            case DRUM, TIMING, PRESSURE, PULSE, GYRE, KEYWAY, BALANCE, RESONANCE ->
                    new Mode[]{Mode.RUNE_ECHO, Mode.LATTICE_KNOT, Mode.PARALLAX_SNAP};
            case MEMORY, RUNES, CONSTELLATION, SHARDSONG ->
                    new Mode[]{Mode.PARALLAX_SNAP, Mode.PRESSURE_CLAMP, Mode.LATTICE_KNOT};
            case CIPHER, AUGURY, LATTICE, RUNEBRAID, BLACK_MEASURE, EPITAPH ->
                    new Mode[]{Mode.PARALLAX_SNAP, Mode.PRESSURE_CLAMP, Mode.RUNE_ECHO};
            case PARALLAX, VEILSTEP, ROOTWAY, MIRROR, VESSEL, YOKE ->
                    new Mode[]{Mode.RUNE_ECHO, Mode.PRESSURE_CLAMP, Mode.LATTICE_KNOT};
            case ORRERY ->
                    new Mode[]{Mode.PARALLAX_SNAP, Mode.PRESSURE_CLAMP, Mode.RUNE_ECHO};
            case PROCESSION ->
                    new Mode[]{Mode.LATTICE_KNOT, Mode.RUNE_ECHO, Mode.PARALLAX_SNAP};
            case CTHULHUS_GAME -> new Mode[]{Mode.NONE};
        };

        // Once the profile is mature, the ward preferentially borrows a mechanism
        // from the player's strongest observed execution axis. This changes *what*
        // appears, not the host minigame's base difficulty or reward entitlement.
        if (WardConfig.adaptiveSkillProfileEnabled && MinigameSkillProfile.observedAxes(data, player) >= 3) {
            Mode preferred = switch (MinigameSkillProfile.strongest(data, player)) {
                case TIMING -> Mode.PRESSURE_CLAMP;
                case LOGIC -> Mode.LATTICE_KNOT;
                case MEMORY -> Mode.RUNE_ECHO;
                case COMPOSURE -> Mode.PARALLAX_SNAP;
            };
            if (contains(pool, preferred) && roll(seed, player.getLeastSignificantBits(), 0x51C11A5L) < 0.62f)
                return preferred;
        }
        return pool[Math.floorMod((int) mix(seed ^ player.getLeastSignificantBits()), pool.length)];
    }

    private static boolean contains(Mode[] pool, Mode needle) {
        for (Mode mode : pool) if (mode == needle) return true;
        return false;
    }

    public static void recordEncounter(ServerPlayer player, LockData data, Mode mode) {
        if (player == null || data == null || mode == null || mode == Mode.NONE) return;
        UUID id = player.getUUID();
        int bit = 1 << (mode.ordinal() - 1);
        int mask = data.uniqueInt(id, "hybrid_round_mask");
        if ((mask & bit) != 0) return;
        data.setUniqueInt(id, "hybrid_round_mask", mask | bit);
        WardHistory.recordSpecial(player, "HYBRID", mode.label.replace("hybrid · ", "") + " // cross-discipline interruption");
    }

    private static float roll(long a, long b, long salt) {
        long z = mix(a ^ Long.rotateLeft(b, 23) ^ salt);
        return (z >>> 40) / (float) (1L << 24);
    }

    private static long mix(long z) {
        z ^= z >>> 33; z *= 0xff51afd7ed558ccdL;
        z ^= z >>> 33; z *= 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
