package dev.marrowseal.wardbound;

/**
 * Central card-pressure math. Keeping pity curves here prevents the Ledger,
 * drop handlers and bargain generator from quietly drifting apart.
 */
public final class CardBalance {
    private CardBalance() {}

    public static final int FIELD_PITY_START = 110;
    public static final int FIELD_PITY_GUARANTEE = 550;
    public static final int DEATH_PITY_START = 18;
    public static final int DEATH_PITY_GUARANTEE = 80;

    public static final int BARGAIN_RAMP_1 = 90;
    public static final int BARGAIN_RAMP_2 = 220;
    public static final int BARGAIN_RAMP_3 = 450;

    /** Card-table frequency rises slowly across the longer 1.0 progression. */
    public static float bargainProgression(int resolved) {
        if (resolved < WardConfig.normalCardsAfterBeaten) return 0f;
        if (resolved < BARGAIN_RAMP_1) return 0.35f;
        if (resolved < BARGAIN_RAMP_2) return 0.55f;
        if (resolved < BARGAIN_RAMP_3) return 0.78f;
        return 1.0f;
    }

    /** Next eligible hostile-kill chance for a physical Sealed Card. */
    public static float fieldDropChance(int pity) {
        int clean = Math.max(0, pity);
        float boost = clean <= FIELD_PITY_START ? 0f
                : Math.min(0.0150f, (clean - FIELD_PITY_START) * 0.000018f);
        return Math.min(1.0f, WardConfig.fieldCardDropChance + boost);
    }

    /** Conditional Death-hand chance after a normal card table has already appeared. */
    public static float deathHandChance(int pity, boolean afterRefresh) {
        int clean = Math.max(0, pity);
        if (clean >= DEATH_PITY_GUARANTEE) return 1.0f;
        float boost = clean <= DEATH_PITY_START ? 0f
                : Math.min(0.024f, (clean - DEATH_PITY_START) * 0.00040f);
        return Math.min(1.0f, (afterRefresh ? 0.0060f : 0.0015f) + (afterRefresh ? boost * 0.45f : boost));
    }

    public static String pressureBand(int value, int guarantee) {
        return pressureBand(value, 0, guarantee);
    }

    public static String pressureBand(int value, int softStart, int guarantee) {
        if (value >= guarantee) return "certain";
        if (value <= softStart) return "quiet";
        float ratio = (value - softStart) / (float) Math.max(1, guarantee - softStart);
        if (ratio >= 0.80f) return "imminent";
        if (ratio >= 0.50f) return "gathering";
        if (ratio >= 0.20f) return "stirring";
        return "quiet";
    }
}
