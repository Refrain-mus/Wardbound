package dev.marrowseal.wardbound;

/**
 * Names for the things you are breaking into.
 *
 * <p>A lock with no name is a mechanism. A lock with a maker, a house and a
 * casting number is an object somebody made, badly or well, some time ago, and
 * that is most of what separates a puzzle from a place. None of this costs
 * anything at runtime: every name is a pure function of the chest's seed, so the
 * server and the client arrive at the same one without a byte crossing between
 * them, and the same chest is always the same seal.
 *
 * <p>The maker matters mechanically too. Beat enough of one house's work and you
 * start to know it; see {@code LockData} for where that is counted.
 */
public final class Sealmakers {

    /** Family names. Guttural, a bit worn, nothing that sounds like a wizard. */
    private static final String[] HOUSES = {
            "Verrick", "Aldemar", "Corbin", "Draven", "Falk", "Grausse",
            "Halloway", "Ivensk", "Karr", "Lemoine", "Mordaunt", "Nell",
            "Ostrey", "Pryce", "Rennick", "Saltmarsh", "Thane", "Ubris",
            "Vaux", "Wexford", "Yarrow", "Zoll",
            "Marrow", "Cradock", "Ilse", "Quist", "Renfrew", "Sable",
    };

    /** What the house was, which is usually not "locksmith". */
    private static final String[] TRADES = {
            "the Elder", "the Younger", "of the Low Quarter", "of Ashfen",
            "the Debtor", "the Quiet", "of Three Rivers", "the Left-Handed",
            "of the Salt Road", "the Widow", "the Exile", "of Greywater",
            "of the Drowned Road", "the Unlettered", "of the Long Winter", "the Last of Hers",
    };

    /** Adjective for the seal itself. */
    private static final String[] TEMPERS = {
            "Iron", "Sullen", "Patient", "Crooked", "Bitter", "Winter",
            "Copper", "Quiet", "Hollow", "Stubborn", "Grey", "Weeping",
            "Blind", "Long", "Cold", "Faithful",
            "Drowned", "Voiceless", "Unmoored", "Star-Cold", "Half-Remembered", "Tide-Worn",
    };

    private static final String[] FORMS = {
            "Seal", "Ward", "Bind", "Clasp", "Charge", "Knot", "Fetter", "Oath",
            "Rite", "Reckoning", "Silence", "Tithe",
    };

    /**
     * Syllable fragments for what answers underneath the maker's name. Never
     * shown by itself, never explained: see {@link #trueName}. Deliberately
     * unpronounceable rather than evocative of anything specific - the fear is
     * in there being an answer at all, not in what the answer is.
     */
    private static final String[] TRUE_FRAGMENTS = {
            "Kho", "Ssa", "Vre", "Nyth", "Ulg", "Xath", "Orr", "Iss", "Vugh", "Thal",
            "Zek", "Mron", "Iyeth", "Ghol", "Utha", "Yrr", "Enq", "Ashk", "Ovul", "Skarn",
    };

    private Sealmakers() {
    }

    /** Stable index into an array from the seed and a salt. */
    private static int pick(long seed, int salt, int size) {
        long h = (seed + salt * 0x9E3779B97F4A7C15L) * 6364136223846793005L
                + 1442695040888963407L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (int) Math.floorMod(h, size);
    }

    /** The house whose work this is. Used as the key for familiarity, so keep it stable. */
    public static String house(long seed) {
        return HOUSES[pick(seed, 11, HOUSES.length)];
    }

    /** A stable rival house. Houses are paired so rivalry is mutual. */
    public static String rival(String house) {
        int idx = -1;
        for (int i = 0; i < HOUSES.length; i++) {
            if (HOUSES[i].equals(house)) { idx = i; break; }
        }
        if (idx < 0) return HOUSES[0];
        int rival = (idx % 2 == 0) ? Math.min(idx + 1, HOUSES.length - 1) : idx - 1;
        return HOUSES[rival];
    }

    /** House plus the epithet, for the footer. */
    public static String maker(long seed) {
        return house(seed) + " " + TRADES[pick(seed, 23, TRADES.length)];
    }

    /** What this particular seal is called. */
    public static String sealName(long seed) {
        return "The " + TEMPERS[pick(seed, 37, TEMPERS.length)] + " "
                + FORMS[pick(seed, 41, FORMS.length)];
    }

    /**
     * Which casting this is. Purely decorative, but a number on a thing implies
     * all the ones before it, and that implication is doing a lot of work for
     * very little.
     */
    public static int casting(long seed) {
        return 1 + pick(seed, 53, 40);
    }

    /** "The Sullen Clasp, Verrick the Quiet, ninth casting" in one line. */
    public static String full(long seed) {
        return sealName(seed) + " \u00b7 " + maker(seed) + " \u00b7 casting "
                + casting(seed);
    }

    /**
     * What answers under the maker's name, for the rare moment something is
     * looking back. Three fragments rather than one so it never scans as a
     * word - the point is that it very deliberately fails to sound like
     * anything, the same way the house names deliberately do.
     */
    public static String trueName(long seed) {
        int a = pick(seed, 97, TRUE_FRAGMENTS.length);
        int b = pick(seed, 101, TRUE_FRAGMENTS.length);
        int c = pick(seed, 103, TRUE_FRAGMENTS.length);
        if (b == a) b = (b + 1) % TRUE_FRAGMENTS.length;
        if (c == b) c = (c + 1) % TRUE_FRAGMENTS.length;
        return TRUE_FRAGMENTS[a] + "'" + TRUE_FRAGMENTS[b] + "-" + TRUE_FRAGMENTS[c];
    }
}
