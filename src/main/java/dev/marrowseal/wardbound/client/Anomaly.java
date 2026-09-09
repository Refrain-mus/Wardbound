package dev.marrowseal.wardbound.client;

/**
 * Rare conditions that sit above the individual locks and apply to any of them.
 *
 * <p>Each lock already carries its own quirks, but those are a closed set: once
 * you have seen a keyway with spool pins a few times, you have seen it. An
 * anomaly is a property of the <i>chest</i> rather than of the game it runs, so
 * it multiplies across all fourteen locks instead of adding to one. Fourteen
 * locks times a handful of anomalies is a lot of first encounters, and first
 * encounters are the whole point.
 *
 * <p>They are all deliberately shallow. An anomaly changes the conditions you
 * play under; it never changes the rules, because a rule you have to rediscover
 * mid-lock is a trap rather than a surprise. Every one of them is also announced
 * in the chip strip, so nothing here is ever something you find out by losing.
 *
 * <p>Most are hostile. One is not, on purpose: if every unusual chest is worse
 * than a normal one then unusual becomes a synonym for bad, and finding one
 * stops being interesting.
 */
public enum Anomaly {

    /**
     * A clock over the whole lock. Running it out costs a life and resets it,
     * so it is pressure rather than a wall.
     */
    HURRIED("hurried", 0.11f),

    /**
     * Patches of something drift across the panel and take the view with them.
     * Purely visual, and the patches move, so the information is never gone for
     * long and never all at once.
     */
    SHROUDED("shrouded", 0.10f),

    /**
     * The light is failing. Every few seconds the panel drops to almost nothing
     * for a moment. You can play through it; you would rather not.
     */
    GUTTERING("guttering", 0.10f),

    /**
     * No hint line, no instructions. On a lock you know, this costs nothing. On
     * one you have never seen, it is the mod at its least forgiving, which is
     * why it is the rarest hostile one.
     */
    UNLIT("unlit", 0.06f),

    /**
     * Somebody got here first and did not finish.
     *
     * <p>The first stage of the lock is already behind you, and the ward is one
     * life the worse for whatever they did to it. A trade rather than a gift,
     * which is better than either half would have been alone: a free stage is
     * an anomaly you are pleased to see and never think about again, and a lost
     * life is one more thing going wrong.
     *
     * <p>This replaces a version that widened every timing window instead. That
     * was a mistake and it took two attempts to admit it: a wider window is
     * invisible by construction, because it looks exactly like an ordinary one
     * right up until the moment you would have missed and did not. The player
     * cannot see it, cannot use it, and cannot tell it apart from luck, which
     * makes it a difficulty setting wearing an anomaly's name.
     *
     * <p>Both halves are countable and on screen: a progress dot already filled
     * and one fewer eye in the header. Whether it is worth taking depends on
     * the lock, which is the point.
     */
    SPENT("half-picked", 0.11f);

    public final String label;
    /** Independent chance this one is present on any given chest. */
    public final float chance;

    Anomaly(String label, float chance) {
        this.label = label;
        this.chance = chance;
    }
}
