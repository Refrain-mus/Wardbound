package dev.marrowseal.wardbound;

public class DimSettings {

    public enum FailAction {
        /** Chest opens but the loot roll is cut down. */
        REDUCE_LOOT("reduce_loot"),
        /** Chest stays locked forever for that player. */
        LOCK_FOREVER("lock_forever"),
        /** Chest blows up and the loot is lost. */
        EXPLODE("explode"),
        /** A guardian mob spawns next to the chest, loot is reduced. */
        SPAWN_GUARD("spawn_guard");

        public final String id;

        FailAction(String id) {
            this.id = id;
        }

        public static FailAction byId(String s, FailAction fallback) {
            if (s == null) return fallback;
            for (FailAction a : values()) {
                if (a.id.equalsIgnoreCase(s)) return a;
            }
            return fallback;
        }
    }

    public MinigameType minigame;
    /**
     * When true the lock ignores {@link #minigame} and picks one of the available games from the
     * chest's own seed, so a dimension can serve up the whole set instead of one game forever.
     * Set with {@code "minigame": "random"} in the config.
     */
    public boolean randomGame;
    /** Locks this dimension may run, when more than one was named. */
    public MinigameType[] pool;
    /** Scales speed / pin count / sequence length. 1.0 = normal. */
    public float difficulty;
    /** Multiplies the final loot roll for this dimension. */
    public float lootMultiplier;
    /** How many mistakes the player is allowed. */
    public int lives;
    public FailAction onFail;

    public DimSettings(MinigameType minigame, float difficulty, float lootMultiplier, int lives, FailAction onFail) {
        this.minigame = minigame;
        this.difficulty = difficulty;
        this.lootMultiplier = lootMultiplier;
        this.lives = lives;
        this.onFail = onFail;
    }

    /** Which game this chest actually gets. Stable for a given chest seed. */
    public MinigameType gameFor(long seed) {
        return gameFor(seed, null);
    }

    /**
     * The lock this chest runs, optionally refusing one.
     *
     * <p>{@code avoid} exists for re-sealed containers. Winding a ward tighter
     * and being handed the identical game again reads as the mod repeating
     * itself rather than as the container fighting back, and it wastes the one
     * thing a fourteen-lock mod has going for it.
     */
    public MinigameType gameFor(long seed, MinigameType avoid) {
        // A dimension may name several locks with pipes: "keyway|augury|gyre".
        // Picked from the chest's own seed, so a given chest always runs the
        // same one, and it is a far more useful setting than the all-or-nothing
        // choice between one lock and every lock.
        if (pool != null && pool.length > 0) {
            long h = seed * 6364136223846793005L + 1442695040888963407L;
            h ^= (h >>> 33);
            for (int attempt = 0; attempt < pool.length * 3; attempt++) {
                MinigameType pick = pool[(int) Math.floorMod(h + attempt, pool.length)];
                if (!WardConfig.tuning(pick).enabled) continue;
                if (pick != avoid || enabledCount(pool) == 1) return pick;
            }
        }
        if (!randomGame && WardConfig.tuning(minigame).enabled) return minigame;
        MinigameType[] all = MinigameType.values();
        int enabled = enabledOrdinaryCount(all);
        if (enabled == 0) return minigame;
        for (int attempt = 0; attempt < all.length * 4; attempt++) {
            long h = (seed + attempt * 0x9E3779B97F4A7C15L) * 6364136223846793005L
                    + 1442695040888963407L;
            h ^= (h >>> 33);
            MinigameType pick = all[(int) Math.floorMod(h, all.length)];
            if (pick == MinigameType.CTHULHUS_GAME || !WardConfig.tuning(pick).enabled) continue;
            if (pick != avoid || enabled == 1) return pick;
        }
        for (MinigameType t : all)
            if (t != MinigameType.CTHULHUS_GAME && WardConfig.tuning(t).enabled) return t;
        return minigame;
    }

    private static int enabledCount(MinigameType[] values) {
        int n = 0;
        for (MinigameType t : values) if (WardConfig.tuning(t).enabled) n++;
        return n;
    }

    private static int enabledOrdinaryCount(MinigameType[] values) {
        int n = 0;
        for (MinigameType t : values)
            if (t != MinigameType.CTHULHUS_GAME && WardConfig.tuning(t).enabled) n++;
        return n;
    }

    /** What to show in chat and in {@code /wardbound chest}. */
    public String gameLabel() {
        return randomGame ? "random" : minigame.id;
    }

    public DimSettings copy() {
        DimSettings c = new DimSettings(minigame, difficulty, lootMultiplier, lives, onFail);
        c.randomGame = randomGame;
        c.pool = pool == null ? null : pool.clone();
        return c;
    }
}
