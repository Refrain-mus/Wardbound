package dev.marrowseal.wardbound;

/**
 * A house's recurring way of building a ward.
 *
 * <p>This deliberately does not add another modifier to a lock. Every minigame
 * already has a small quirk budget; a signature only biases which existing
 * quirk occupies that budget. That makes a house recognisable over a long save
 * without making its seals objectively stronger than everybody else's.
 */
public enum MasterSignature {
    CROOKED("crooked hand", 0),
    VEILED("veiled hand", 1),
    EXACTING("exacting hand", 2);

    private final String label;
    private final int preferredVariant;

    MasterSignature(String label, int preferredVariant) {
        this.label = label;
        this.preferredVariant = preferredVariant;
    }

    public String label() {
        return label;
    }

    /**
     * Existing minigames expose three or four quirks. The first three slots are
     * stable enough to form a house habit; no extra quirk is created here.
     */
    public int preferredVariant(int poolSize) {
        if (poolSize <= 0) return 0;
        return Math.floorMod(preferredVariant, poolSize);
    }

    public static MasterSignature ofHouse(String house) {
        if (house == null || house.isEmpty()) return CROOKED;
        return values()[Math.floorMod(house.hashCode(), values().length)];
    }

    public static MasterSignature ofSeed(long seed) {
        return ofHouse(Sealmakers.house(seed));
    }
}
