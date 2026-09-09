package dev.marrowseal.wardbound;

/**
 * Bit flags written by card bargains onto the next ordinary ward.
 *
 * <p>Keeping these as data on the ward rather than checking the player's active
 * card state from the client makes the effect one-shot, save-safe and impossible
 * to reset by closing/reopening the minigame screen.</p>
 */
public final class MinigameCardEffects {
    private MinigameCardEffects() {}

    /** One strong clue is exposed. Individual logic games may turn this into a concrete solved element. */
    public static final int REVEAL_ONE = 1;
    /** The first significant mistake is recorded but does not consume a life. */
    public static final int FORGIVE_FIRST = 1 << 1;
    /** The minigame content and directional input are mirrored horizontally. */
    public static final int MIRROR_UI = 1 << 2;
    /** The ward runs slightly faster; used by high-return wagers. */
    public static final int QUICKEN = 1 << 3;
    /** A binding is added to the puzzle. Logic games can express this as a frozen/dead element. */
    public static final int BIND_ONE = 1 << 4;

    public static boolean has(int mask, int flag) { return (mask & flag) != 0; }

    public static String labels(int mask) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (has(mask, REVEAL_ONE)) out.add("revealed tell");
        if (has(mask, FORGIVE_FIRST)) out.add("first error pardoned");
        if (has(mask, MIRROR_UI)) out.add("reversed face");
        if (has(mask, QUICKEN)) out.add("loaded tempo");
        if (has(mask, BIND_ONE)) out.add("binding clause");
        return String.join(" · ", out);
    }
}
