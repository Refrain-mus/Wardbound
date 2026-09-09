package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative per-player ward state plus long-lived progression data.
 * Live attempts are committed once issued: an unresolved screen is a failure,
 * while the persisted fields remain for save compatibility and stale-packet validation.
 */
public class LockData extends SavedData {

    private static final String NAME = "wardbound_state";

    private final Map<String, CompoundTag> states = new HashMap<>();
    /** player|dimension -> {streak, best}. How many locks in a row have fallen here. */
    private final Map<String, CompoundTag> runs = new HashMap<>();

    public static LockData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(LockData::load, LockData::new, NAME);
    }

    private static String key(UUID player, String dim, BlockPos pos) {
        return player + "|" + dim + "|" + pos.asLong();
    }

    public static LockData load(CompoundTag tag) {
        LockData data = new LockData();
        CompoundTag all = tag.getCompound("states");
        for (String k : all.getAllKeys()) {
            data.states.put(k, all.getCompound(k));
        }
        CompoundTag rs = tag.getCompound("runs");
        for (String k : rs.getAllKeys()) {
            data.runs.put(k, rs.getCompound(k));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : states.entrySet()) {
            all.put(e.getKey(), e.getValue());
        }
        tag.put("states", all);
        CompoundTag rs = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : runs.entrySet()) {
            rs.put(e.getKey(), e.getValue());
        }
        tag.put("runs", rs);
        return tag;
    }

    public CompoundTag getOrCreate(UUID player, String dim, BlockPos pos) {
        String k = key(player, dim, pos);
        CompoundTag t = states.get(k);
        if (t == null) {
            t = new CompoundTag();
            states.put(k, t);
            setDirty();
        }
        return t;
    }

    // ------------------------------------------------------------------ streaks

    private static String runKey(UUID player, String dim) {
        return player + "|" + dim;
    }

    public int streak(UUID player, String dim) {
        CompoundTag t = runs.get(runKey(player, dim));
        return t == null ? 0 : t.getInt("streak");
    }

    /** Most recently resolved minigames in this dimension, newest first. */
    public int[] recentGames(UUID player, String dim) {
        CompoundTag t = runs.get(runKey(player, dim));
        return t == null ? new int[0] : t.getIntArray("recent_games");
    }

    /**
     * Remember a resolved game for the anti-repetition director. Eight entries
     * are enough for the largest configurable window and keep the save tiny.
     */
    public void recordGame(UUID player, String dim, MinigameType game) {
        if (game == null) return;
        String k = runKey(player, dim);
        CompoundTag t = runs.computeIfAbsent(k, x -> new CompoundTag());
        int[] old = t.getIntArray("recent_games");
        int n = Math.min(8, old.length + 1);
        int[] next = new int[n];
        next[0] = game.ordinal();
        if (n > 1) System.arraycopy(old, 0, next, 1, n - 1);
        t.putIntArray("recent_games", next);
        setDirty();
    }

    /**
     * How many of a given house's seals this player has broken.
     *
     * <p>Kept per player and not per dimension: knowing Verrick's work is
     * knowledge about Verrick, and it should not evaporate because you walked
     * through a portal.
     */
    public int familiarity(UUID player, String house) {
        CompoundTag t = runs.get("maker:" + player + ":" + house);
        return t == null ? 0 : t.getInt("beaten");
    }

    /** One more of that house's seals down. Returns the new count. */
    public int bumpFamiliarity(UUID player, String house) {
        CompoundTag t = runs.computeIfAbsent("maker:" + player + ":" + house,
                x -> new CompoundTag());
        int n = t.getInt("beaten") + 1;
        t.putInt("beaten", n);
        setDirty();
        return n;
    }

    /**
     * The house that has taken an interest in this player.
     *
     * <p>Chosen once, the first time it is asked for, and then permanent. It is
     * derived from the player rather than rolled, so two players in the same
     * world are followed by different hands and neither can reroll it.
     */
    public String watcher(UUID player) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        if (!t.contains("house")) {
            t.putString("house", Sealmakers.house(
                    player.getMostSignificantBits() ^ player.getLeastSignificantBits()));
            setDirty();
        }
        return t.getString("house");
    }

    /** Total seals this player has broken, anywhere. Drives how closely they are watched. */
    public int totalBeaten(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? 0 : t.getInt("total");
    }

    public int bumpTotal(UUID player) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        int n = t.getInt("total") + 1;
        t.putInt("total", n);
        setDirty();
        return n;
    }

    /** Debug/admin setter used by the progression inspector. */
    public void setTotalBeaten(UUID player, int value) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        t.putInt("total", Math.max(0, value));
        setDirty();
    }

    /**
     * What the watching hand makes of this player, as a running score.
     *
     * <p>Not a random disposition. It is earned: clean work on that house's
     * seals raises it, losing to them or forcing them open lowers it, and
     * leaning on the heart to get through raises nothing at all. A verdict you
     * were given is a coin flip; a verdict you earned is a relationship.
     */
    public int regard(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? 0 : t.getInt("regard");
    }

    public void addRegard(UUID player, int delta) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        t.putInt("regard", Mth.clamp(t.getInt("regard") + delta, -80, 140));
        setDirty();
    }

    /** "", "favour" or "contempt". Set once and never revisited. */
    public String verdict(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? "" : t.getString("verdict");
    }

    public void setVerdict(UUID player, String verdict) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        t.putString("verdict", verdict);
        setDirty();
    }

    // --------------------------------------------------------- forbidden debts

    private CompoundTag bargain(UUID player) {
        return runs.computeIfAbsent("bargain:" + player, x -> new CompoundTag());
    }

    /** Internal snapshot used by the one-shot Echo resolver. */
    CompoundTag bargainSnapshot(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? new CompoundTag() : t.copy();
    }

    /**
     * Doubles numeric state deltas produced by one accepted card without
     * replaying the card's side effects or creating duplicate objectives.
     * Deadlines are extended relative to the current game time instead of
     * doubling their absolute world-time value.
     */
    void amplifyBargainDelta(UUID player, CompoundTag before, long now) {
        CompoundTag after = bargain(player);
        CompoundTag old = before == null ? new CompoundTag() : before;
        for (String key : new java.util.HashSet<>(after.getAllKeys())) {
            if (key.startsWith("unique_int_card_echo_") || key.startsWith("unique_int_echo_pending_kind_")) continue;
            if (after.contains(key, net.minecraft.nbt.Tag.TAG_INT)) {
                int a = after.getInt(key);
                int b = old.contains(key, net.minecraft.nbt.Tag.TAG_INT) ? old.getInt(key) : 0;
                if (a != b) {
                    long doubled = (long) a + (a - (long) b);
                    if (a >= 0 && b >= 0 && doubled < 0) doubled = Integer.MAX_VALUE;
                    after.putInt(key, (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, doubled)));
                }
            } else if (after.contains(key, net.minecraft.nbt.Tag.TAG_LONG)) {
                long a = after.getLong(key);
                long b = old.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? old.getLong(key) : Long.MIN_VALUE;
                if (a == b || key.startsWith("curse_day_")) continue;
                long boosted = a;
                if (a > now) {
                    long remaining = Math.max(1L, a - now);
                    if (key.endsWith("_until")) {
                        // Duration-bearing laws last twice as long.
                        boosted = a > Long.MAX_VALUE - remaining ? Long.MAX_VALUE : a + remaining;
                    } else if (key.endsWith("_next") || key.endsWith("_ready") || key.endsWith("_start")) {
                        // Cooldowns/delays become more forceful, not accidentally weaker.
                        boosted = now + Math.max(1L, remaining / 2L);
                    }
                }
                after.putLong(key, boosted);
            } else if (after.contains(key, net.minecraft.nbt.Tag.TAG_FLOAT)) {
                float a = after.getFloat(key);
                float b = old.contains(key, net.minecraft.nbt.Tag.TAG_FLOAT) ? old.getFloat(key) : 0.0F;
                if (Float.isFinite(a) && Float.isFinite(b) && a != b) after.putFloat(key, a + (a - b));
            } else if (after.contains(key, net.minecraft.nbt.Tag.TAG_DOUBLE)) {
                double a = after.getDouble(key);
                double b = old.contains(key, net.minecraft.nbt.Tag.TAG_DOUBLE) ? old.getDouble(key) : 0.0D;
                if (Double.isFinite(a) && Double.isFinite(b) && a != b) after.putDouble(key, a + (a - b));
            }
        }
        setDirty();
    }

    public boolean hasBorrowedBreath(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t != null && t.getBoolean("breath");
    }

    public void addBorrowedBreath(UUID player) {
        bargain(player).putBoolean("breath", true);
        setDirty();
    }

    /** Returns true once, when the debt is actually collected. */
    public boolean consumeBorrowedBreath(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        if (t == null || !t.getBoolean("breath")) return false;
        t.remove("breath");
        setDirty();
        return true;
    }

    public boolean hasIronDebt(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t != null && t.getBoolean("iron");
    }

    public void addIronDebt(UUID player) {
        bargain(player).putBoolean("iron", true);
        setDirty();
    }

    public boolean consumeIronDebt(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        if (t == null || !t.getBoolean("iron")) return false;
        t.remove("iron");
        setDirty();
        return true;
    }

    public boolean hasWatchingMark(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t != null && t.getBoolean("watching_mark");
    }

    public void addWatchingMark(UUID player) {
        bargain(player).putBoolean("watching_mark", true);
        setDirty();
    }

    public boolean consumeWatchingMark(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        if (t == null || !t.getBoolean("watching_mark")) return false;
        t.remove("watching_mark");
        setDirty();
        return true;
    }

    // ----------------------------------------------------- standing card terms

    public boolean hasCrimsonBalance(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("crimson_balance"); }
    public void setCrimsonBalance(UUID player, boolean on) { bargain(player).putBoolean("crimson_balance", on); setDirty(); }

    public boolean hasGlassNerve(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("glass_nerve"); }
    public void setGlassNerve(UUID player, boolean on) { bargain(player).putBoolean("glass_nerve", on); setDirty(); }

    public boolean hasPaleCovenant(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("pale_covenant"); }
    public void setPaleCovenant(UUID player, boolean on) { bargain(player).putBoolean("pale_covenant", on); setDirty(); }

    public boolean hasOpenVein(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("open_vein"); }
    public void setOpenVein(UUID player, boolean on) { bargain(player).putBoolean("open_vein", on); setDirty(); }

    public boolean hasThinBlood(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("thin_blood"); }
    public void setThinBlood(UUID player, boolean on) { bargain(player).putBoolean("thin_blood", on); setDirty(); }

    public boolean hasStillHeart(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("still_heart"); }
    public void setStillHeart(UUID player, boolean on) { bargain(player).putBoolean("still_heart", on); setDirty(); }

    public int heartDebt(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("heart_debt"); }
    public void addHeartDebt(UUID player, int n) {
        CompoundTag t = bargain(player);
        t.putInt("heart_debt", Mth.clamp(t.getInt("heart_debt") + Math.max(0, n), 0, 3));
        setDirty();
    }
    public void returnHeart(UUID player) {
        CompoundTag t = bargain(player);
        int n = Math.max(0, t.getInt("heart_debt") - 1);
        if (n == 0) t.remove("heart_debt"); else t.putInt("heart_debt", n);
        setDirty();
    }

    /** Hearts buried by Death laws. Ordinary remedies deliberately cannot return these. */
    public int deathHeartDebt(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? 0 : t.getInt("death_heart_debt");
    }
    public void addDeathHeartDebt(UUID player, int n) {
        CompoundTag t = bargain(player);
        t.putInt("death_heart_debt", Mth.clamp(t.getInt("death_heart_debt") + Math.max(0, n), 0, 4));
        setDirty();
    }

    /**
     * One-time-compatible repair for worlds that signed Memento Mori before Death hearts
     * were separated from ordinary heart debt. The old implementation stored its two
     * buried hearts in heart_debt, where remedies could incorrectly return them.
     */
    public void ensureMementoDeathDebt(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        if (t == null || !t.getBoolean("unique_memento_mori")) return;
        int death = Mth.clamp(t.getInt("death_heart_debt"), 0, 4);
        if (death >= 2) return;
        int missing = 2 - death;
        int ordinary = Math.max(0, t.getInt("heart_debt"));
        int migrated = Math.min(ordinary, missing);
        int ordinaryLeft = ordinary - migrated;
        if (ordinaryLeft == 0) t.remove("heart_debt"); else t.putInt("heart_debt", ordinaryLeft);
        // Reassert the Death law even if an old remedy had already returned one of its hearts.
        t.putInt("death_heart_debt", death + missing);
        setDirty();
    }

    public int loot25Charges(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("loot25_charges"); }
    public void addLoot25Charges(UUID player, int n) {
        CompoundTag t = bargain(player);
        t.putInt("loot25_charges", Mth.clamp(t.getInt("loot25_charges") + Math.max(0, n), 0, 8));
        setDirty();
    }

    public int loot15Charges(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("loot15_charges"); }
    public void addLoot15Charges(UUID player, int n) {
        CompoundTag t = bargain(player);
        t.putInt("loot15_charges", Mth.clamp(t.getInt("loot15_charges") + Math.max(0, n), 0, 8));
        setDirty();
    }

    public boolean hasLastCandle(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("last_candle"); }
    public void addLastCandle(UUID player) { bargain(player).putBoolean("last_candle", true); setDirty(); }

    public int loadedDiceCharges(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("loaded_dice"); }
    public void addLoadedDice(UUID player, int n) { bargain(player).putInt("loaded_dice", Mth.clamp(n, 0, 6)); setDirty(); }

    public int mercysDueCharges(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("mercys_due"); }
    public void addMercysDue(UUID player, int n) { bargain(player).putInt("mercys_due", Mth.clamp(n, 0, 6)); setDirty(); }

    public int curseLoot15Charges(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("curse_loot15"); }
    public void addCurseLoot15Charges(UUID player, int n) {
        CompoundTag t = bargain(player);
        t.putInt("curse_loot15", Mth.clamp(t.getInt("curse_loot15") + Math.max(0, n), 0, 8));
        setDirty();
    }

    public boolean hasAshenTongue(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("ashen_tongue"); }
    public void setAshenTongue(UUID player, boolean on) {
        CompoundTag t = bargain(player);
        if (on) t.putBoolean("ashen_tongue", true);
        else { t.remove("ashen_tongue"); t.remove("curse_day_ashen"); }
        setDirty();
    }
    /** Ashen Tongue matures inside its ten-minute lifespan rather than over Minecraft days. */
    public int ashenTongueStage(UUID player, long gameTime) {
        if (!hasAshenTongue(player)) return 0;
        long until = uniqueLong(player, "ashen_tongue_until");
        if (until == Long.MIN_VALUE || gameTime >= until) return 0;
        long total = 20L * 60L * 10L;
        long elapsed = Math.max(0L, total - Math.max(0L, until - gameTime));
        return elapsed >= total * 2L / 3L ? 2 : elapsed >= total / 3L ? 1 : 0;
    }


    public boolean hasBloodTithe(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("blood_tithe"); }
    public void setBloodTithe(UUID player, boolean on) {
        CompoundTag t = bargain(player);
        if (on) t.putBoolean("blood_tithe", true); else { t.remove("blood_tithe"); t.remove("curse_day_blood_tithe"); }
        setDirty();
    }

    // ---------------------------------------------------------- special card laws

    /** -1 means no active maker pact; otherwise MasterSignature ordinal. */
    public int masterPact(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null || !t.contains("master_pact") ? -1 : t.getInt("master_pact");
    }
    public void setMasterPact(UUID player, int pact) {
        CompoundTag t = bargain(player);
        if (pact < 0) t.remove("master_pact"); else t.putInt("master_pact", Mth.clamp(pact, 0, 2));
        setDirty();
    }

    public boolean hasBlackHarvest(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("black_harvest"); }
    public void setBlackHarvest(UUID player, boolean on) { bargain(player).putBoolean("black_harvest", on); setDirty(); }
    public int blackHarvestKills(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null ? 0 : t.getInt("black_harvest_kills"); }
    /** Returns the updated kill count, wrapped to 0 after the fifth kill. */
    public int bumpBlackHarvestKill(UUID player) {
        CompoundTag t = bargain(player);
        int next = t.getInt("black_harvest_kills") + 1;
        if (next >= 5) { t.remove("black_harvest_kills"); next = 5; }
        else t.putInt("black_harvest_kills", next);
        setDirty();
        return next;
    }

    public boolean hasSecondEntry(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("second_entry"); }
    public void setSecondEntry(UUID player, boolean on) { bargain(player).putBoolean("second_entry", on); setDirty(); }
    public long secondEntryDay(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t == null || !t.contains("second_entry_day") ? Long.MIN_VALUE : t.getLong("second_entry_day"); }
    public void setSecondEntryDay(UUID player, long day) { bargain(player).putLong("second_entry_day", day); setDirty(); }

    public boolean hasBrittlePilgrimage(UUID player) { CompoundTag t = runs.get("bargain:" + player); return t != null && t.getBoolean("brittle_pilgrimage"); }
    public void setBrittlePilgrimage(UUID player, boolean on) { bargain(player).putBoolean("brittle_pilgrimage", on); setDirty(); }

    public boolean hasAnyBargainDebt(UUID player) {
        return hasBorrowedBreath(player) || hasIronDebt(player) || hasWatchingMark(player)
                || hasCrimsonBalance(player) || hasGlassNerve(player) || hasPaleCovenant(player)
                || hasOpenVein(player) || hasThinBlood(player) || hasStillHeart(player)
                || heartDebt(player) > 0 || curseLoot15Charges(player) > 0
                || hasAshenTongue(player) || hasBrittlePilgrimage(player) || hasBloodTithe(player)
                || hasUnique(player, "shivering_tithe")
                || uniqueLong(player, "momentum_until") != Long.MIN_VALUE
                || uniqueLong(player, "quicksilver_until") != Long.MIN_VALUE
                || hasUnique(player, "bellglass_sight") || hasUnique(player, "thorn_ledger")
                || hasUnique(player, "dustbound_soles") || hasUnique(player, "lantern_blood")
                || hasUnique(player, "hollow_lantern") || hasUnique(player, "bone_ward")
                || hasUnique(player, "village_exile");
    }

    /** Clears debts/scars/curses while preserving positive master and epic laws. */
    public void clearNegativeBargains(UUID player) {
        CompoundTag t = bargain(player);
        String[] booleans = {"breath", "iron", "watching_mark", "crimson_balance", "glass_nerve",
                "pale_covenant", "open_vein", "thin_blood", "still_heart",
                "ashen_tongue", "brittle_pilgrimage", "blood_tithe", "unique_shivering_tithe",
                "unique_bellglass_sight", "unique_thorn_ledger", "unique_dustbound_soles", "unique_lantern_blood",
                "unique_hollow_lantern", "unique_bone_ward", "unique_village_exile"};
        for (String key : booleans) t.remove(key);
        String[] ints = {"heart_debt", "curse_loot15"};
        for (String key : ints) t.remove(key);
        t.remove("curse_day_blood_tithe");
        t.remove("curse_day_brittle");
        t.remove("curse_day_ashen");
        t.remove("unique_long_ashen_tongue_until");
        t.remove("unique_long_momentum_until");
        t.remove("unique_long_quicksilver_until");
        setDirty();
    }

    public void clearAllBargains(UUID player) {
        runs.remove("bargain:" + player);
        setDirty();
    }

    public void clearOnePendingDebt(UUID player) {
        CompoundTag t = bargain(player);
        if (t.getBoolean("breath")) t.remove("breath");
        else if (t.getBoolean("iron")) t.remove("iron");
        else if (t.getBoolean("watching_mark")) t.remove("watching_mark");
        else if (t.getInt("curse_loot15") > 0) t.remove("curse_loot15");
        else if (t.getBoolean("blood_tithe")) { t.remove("blood_tithe"); t.remove("curse_day_blood_tithe"); if (t.getInt("heart_debt") > 0) t.putInt("heart_debt", t.getInt("heart_debt") - 1); }
        else if (t.getBoolean("brittle_pilgrimage")) { t.remove("brittle_pilgrimage"); t.remove("curse_day_brittle"); }
        else if (t.getBoolean("ashen_tongue")) { t.remove("ashen_tongue"); t.remove("curse_day_ashen"); t.remove("unique_long_ashen_tongue_until"); }
        else if (t.getBoolean("unique_shivering_tithe")) { t.remove("unique_shivering_tithe"); }
        else if (t.contains("unique_long_momentum_until")) { t.remove("unique_long_momentum_until"); }
        else if (t.contains("unique_long_quicksilver_until")) { t.remove("unique_long_quicksilver_until"); }
        setDirty();
    }

    /** Terms bound to one ordinary ward at the moment the player commits to it. */
    public record CardWardTerms(float lootBonus, int lifeDelta, boolean forceOneLife,
                                int minigameMask, int extraSeconds) {
        public boolean empty() {
            return Math.abs(lootBonus) < 0.0001f && lifeDelta == 0 && !forceOneLife
                    && minigameMask == 0 && extraSeconds == 0;
        }
    }

    public CardWardTerms consumeCardWardTerms(UUID player) {
        CompoundTag t = bargain(player);
        float loot = 0f;
        int lives = 0;
        boolean one = false;
        int minigameMask = 0;
        int extraSeconds = 0;

        // Housebreaker's Ace end-endgame law: permanent and paid on every ordinary ward.
        if (t.getBoolean("unique_house_ace_ward_law")) {
            lives += 1;
            loot += 0.25f;
            extraSeconds += 4;
            minigameMask |= MinigameCardEffects.FORGIVE_FIRST;
        }

        // Legacy pre-rework Ace clauses are consumed once if they still exist in an old save.
        int ace = t.getInt("unique_int_house_ace_queued");
        if (ace == 1) lives += 1;
        else if (ace == 2) { lives -= 1; loot += 0.35f; }
        t.remove("unique_int_house_ace_queued");

        int q = t.getInt("loot25_charges");
        if (q > 0) {
            loot += 0.25f;
            extraSeconds += 3;
            if (q == 1) t.remove("loot25_charges"); else t.putInt("loot25_charges", q - 1);
        }

        int thin = t.getInt("loot15_charges");
        if (thin > 0) {
            loot += 0.15f;
            minigameMask |= MinigameCardEffects.BIND_ONE;
            if (thin == 1) {
                t.remove("loot15_charges");
                t.remove("thin_blood");
            } else t.putInt("loot15_charges", thin - 1);
        }

        if (t.getBoolean("last_candle")) {
            one = true;
            loot += 0.30f;
            minigameMask |= MinigameCardEffects.MIRROR_UI;
            t.remove("last_candle");
        }

        int loaded = t.getInt("loaded_dice");
        if (loaded > 0) {
            boolean quietOdds = CardBranches.alternate(this, player, ForbiddenBargain.LOADED_DICE);
            if (!quietOdds) { lives -= 1; minigameMask |= MinigameCardEffects.QUICKEN; }
            int loadedVariant = t.getInt("unique_int_card_variant_" + ForbiddenBargain.LOADED_DICE.id);
            loot += quietOdds ? 0.12f : loadedVariant >= 3 ? 0.28f : loadedVariant >= 1 ? 0.25f : 0.20f;
            if (loaded == 1) t.remove("loaded_dice"); else t.putInt("loaded_dice", loaded - 1);
        }

        int mercy = t.getInt("mercys_due");
        if (mercy > 0) {
            if (CardBranches.alternate(this, player, ForbiddenBargain.MERCYS_DUE)) extraSeconds += 6;
            else lives += 1;
            minigameMask |= MinigameCardEffects.FORGIVE_FIRST;
            int mercyVariant = t.getInt("unique_int_card_variant_" + ForbiddenBargain.MERCYS_DUE.id);
            loot -= mercyVariant >= 3 ? 0.08f : mercyVariant >= 2 ? 0.12f : mercyVariant == 1 ? 0.10f : 0.15f;
            if (mercy == 1) t.remove("mercys_due"); else t.putInt("mercys_due", mercy - 1);
        }

        int curseLoot = t.getInt("curse_loot15");
        if (curseLoot > 0) {
            loot -= 0.15f;
            lives += 1;
            minigameMask |= MinigameCardEffects.REVEAL_ONE;
            if (curseLoot == 1) t.remove("curse_loot15"); else t.putInt("curse_loot15", curseLoot - 1);
        }

        int census = t.getInt("unique_int_red_census_loot_charges");
        if (census > 0) {
            loot += 0.20f;
            minigameMask |= MinigameCardEffects.BIND_ONE;
            if (census == 1) t.remove("unique_int_red_census_loot_charges");
            else t.putInt("unique_int_red_census_loot_charges", census - 1);
        }

        int ossuary = t.getInt("unique_int_ossuary_loot_charges");
        if (ossuary > 0) {
            loot += 0.10f;
            minigameMask |= MinigameCardEffects.REVEAL_ONE;
            if (ossuary == 1) t.remove("unique_int_ossuary_loot_charges");
            else t.putInt("unique_int_ossuary_loot_charges", ossuary - 1);
        }

        FifthWaveCardEffects.WardTerms fifth = FifthWaveCardEffects.consumeWardTerms(this, player);
        loot += fifth.loot();
        lives += fifth.lives();
        minigameMask |= fifth.mask();
        extraSeconds += fifth.seconds();

        if (Math.abs(loot) > 0.0001f || lives != 0 || one || minigameMask != 0 || extraSeconds != 0) setDirty();
        return new CardWardTerms(Mth.clamp(loot, -0.50f, 1.00f), Mth.clamp(lives, -3, 3), one,
                minigameMask, Mth.clamp(extraSeconds, 0, 12));
    }

    // ------------------------------------------------ adaptive director / relationships

    private CompoundTag relationTag(UUID player) {
        return runs.computeIfAbsent("relation:" + player, x -> new CompoundTag());
    }

    public int relation(UUID player, MasterSignature signature) {
        CompoundTag t = runs.get("relation:" + player);
        return t == null ? 0 : t.getInt(signature.name().toLowerCase());
    }

    public int addRelation(UUID player, MasterSignature signature, int delta) {
        CompoundTag t = relationTag(player);
        String key = signature.name().toLowerCase();
        int next = Mth.clamp(t.getInt(key) + delta, -40, 40);
        t.putInt(key, next);
        setDirty();
        return next;
    }

    /** Recent ward performance in one dimension, newest first, each sample 0..100. */
    public int[] directorSamples(UUID player, String dim) {
        CompoundTag t = runs.get(runKey(player, dim));
        return t == null ? new int[0] : t.getIntArray("director_samples");
    }

    public void recordDirectorOutcome(UUID player, String dim, boolean success, int mistakes, float performance) {
        String k = runKey(player, dim);
        CompoundTag t = runs.computeIfAbsent(k, x -> new CompoundTag());
        int clean = Mth.clamp(Math.round(performance * 100f) - Math.max(0, mistakes) * 8, 0, 100);
        int sample = success ? Mth.clamp(45 + clean / 2, 45, 100) : Mth.clamp(28 - Math.max(0, mistakes) * 3, 0, 40);
        int[] old = t.getIntArray("director_samples");
        int n = Math.min(10, old.length + 1);
        int[] next = new int[n];
        next[0] = sample;
        if (n > 1) System.arraycopy(old, 0, next, 1, n - 1);
        t.putIntArray("director_samples", next);

        // Dimension-agnostic rolling analytics for the Witness Ledger. Keep the
        // last twenty real resolutions so the analysis page can show trend rather
        // than only lifetime totals or the current dimension's director sample.
        CompoundTag bargain = bargain(player);
        bargain.putIntArray("analytics_outcomes", pushRolling(bargain.getIntArray("analytics_outcomes"), success ? 1 : 0, 20));
        bargain.putIntArray("analytics_performance", pushRolling(bargain.getIntArray("analytics_performance"), clean, 20));
        bargain.putIntArray("analytics_mistakes", pushRolling(bargain.getIntArray("analytics_mistakes"), Math.max(0, mistakes), 20));
        setDirty();
    }

    private static int[] pushRolling(int[] old, int value, int cap) {
        int n = Math.min(Math.max(1, cap), (old == null ? 0 : old.length) + 1);
        int[] next = new int[n];
        next[0] = value;
        if (n > 1 && old != null && old.length > 0) System.arraycopy(old, 0, next, 1, Math.min(old.length, n - 1));
        return next;
    }

    public int[] analyticsOutcomes(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? new int[0] : t.getIntArray("analytics_outcomes");
    }

    public int[] analyticsPerformance(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? new int[0] : t.getIntArray("analytics_performance");
    }

    public int[] analyticsMistakes(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? new int[0] : t.getIntArray("analytics_mistakes");
    }

    /** -1 = struggling, 0 = steady, 1 = strong. Used to diversify, not to rubber-band raw difficulty. */
    public int directorBand(UUID player, String dim) {
        int[] samples = directorSamples(player, dim);
        if (samples.length < 3) return 0;
        int n = Math.min(samples.length, 8);
        int total = 0;
        for (int i = 0; i < n; i++) total += samples[i];
        float avg = total / (float) n;
        if (avg >= 74f) return 1;
        if (avg <= 46f) return -1;
        return 0;
    }

    // ------------------------------------------------ unique card laws / curse aging

    public boolean hasUnique(UUID player, String key) {
        CompoundTag t = runs.get("bargain:" + player);
        return t != null && t.getBoolean("unique_" + key);
    }
    public void setUnique(UUID player, String key, boolean on) {
        String runKey="bargain:" + player, k="unique_" + key;
        CompoundTag t=runs.get(runKey);
        if(on){
            if(t!=null && t.getBoolean(k)) return;
            bargain(player).putBoolean(k,true);
            setDirty();
            return;
        }
        if(t==null || !t.contains(k)) return;
        t.remove(k);
        setDirty();
    }

    public long uniqueLong(UUID player, String key) {
        CompoundTag t = runs.get("bargain:" + player);
        String k = "unique_long_" + key;
        return t == null || !t.contains(k) ? Long.MIN_VALUE : t.getLong(k);
    }
    public void setUniqueLong(UUID player, String key, long value) {
        String runKey="bargain:" + player, k="unique_long_" + key;
        CompoundTag t=runs.get(runKey);
        if(value==Long.MIN_VALUE){
            if(t==null || !t.contains(k)) return;
            t.remove(k);
            setDirty();
            return;
        }
        if(t!=null && t.contains(k) && t.getLong(k)==value) return;
        bargain(player).putLong(k,value);
        setDirty();
    }

    /** Small string slot for dimension/resource identifiers tied to card state. */
    public String uniqueString(UUID player, String key) {
        CompoundTag t = runs.get("bargain:" + player);
        String k = "unique_string_" + key;
        return t == null || !t.contains(k) ? "" : t.getString(k);
    }
    public void setUniqueString(UUID player, String key, String value) {
        String runKey="bargain:" + player, k="unique_string_" + key;
        CompoundTag t=runs.get(runKey);
        String normalized=value==null?"":value;
        if(normalized.isBlank()){
            if(t==null || !t.contains(k)) return;
            t.remove(k);
            setDirty();
            return;
        }
        if(t!=null && t.contains(k) && normalized.equals(t.getString(k))) return;
        bargain(player).putString(k,normalized);
        setDirty();
    }

    public int uniqueInt(UUID player, String key) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? 0 : t.getInt("unique_int_" + key);
    }
    public void setUniqueInt(UUID player, String key, int value) {
        String runKey="bargain:" + player, k="unique_int_" + key;
        CompoundTag t=runs.get(runKey);
        if(value==0){
            if(t==null || !t.contains(k)) return;
            t.remove(k);
            setDirty();
            return;
        }
        if(t!=null && t.contains(k) && t.getInt(k)==value) return;
        bargain(player).putInt(k,value);
        setDirty();
    }

    /** Small rolling memory used to stop card hands from repeating the same few offers. */
    public int[] recentCardIds(UUID player) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null ? new int[0] : t.getIntArray("recent_card_ids");
    }

    public boolean wasRecentlyOffered(UUID player, int cardId) {
        for (int id : recentCardIds(player)) if (id == cardId) return true;
        return false;
    }

    public void rememberCardOffers(UUID player, int... cardIds) {
        if (cardIds == null || cardIds.length == 0) return;
        int[] old = recentCardIds(player);
        java.util.ArrayList<Integer> merged = new java.util.ArrayList<>();
        for (int id : cardIds) {
            if (id < 0) continue;
            merged.remove(Integer.valueOf(id));
            merged.add(id);
        }
        for (int id : old) {
            if (merged.size() >= 16) break;
            if (!merged.contains(id)) merged.add(id);
        }
        int[] next = new int[Math.min(16, merged.size())];
        for (int i = 0; i < next.length; i++) next[i] = merged.get(i);
        bargain(player).putIntArray("recent_card_ids", next);
        setDirty();
    }

    public int activeObjectiveCount(UUID player) {
        return CardObjectives.activeCount(this, player);
    }

    public void setCurseDay(UUID player, String key, long day) { bargain(player).putLong("curse_day_" + key, day); setDirty(); }
    public long curseDay(UUID player, String key) {
        CompoundTag t = runs.get("bargain:" + player);
        return t == null || !t.contains("curse_day_" + key) ? Long.MIN_VALUE : t.getLong("curse_day_" + key);
    }
    public int curseStage(UUID player, String key, long currentDay) {
        long start = curseDay(player, key);
        if (start == Long.MIN_VALUE) return 0;
        long age = Math.max(0L, currentDay - start);
        return age >= 4 ? 2 : age >= 2 ? 1 : 0;
    }

    /** How many unsigned seals this player has broken. Drives the gauntlet. */
    public int unsignedBeaten(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? 0 : t.getInt("unsigned");
    }

    public int bumpUnsigned(UUID player) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        int n = t.getInt("unsigned") + 1;
        t.putInt("unsigned", n);
        setDirty();
        return n;
    }

    /** Gauntlets completed. There is nothing after this in the mod. */
    public int gauntlets(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? 0 : t.getInt("gauntlets");
    }

    public int bumpGauntlets(UUID player) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        int n = t.getInt("gauntlets") + 1;
        t.putInt("gauntlets", n);
        setDirty();
        return n;
    }

    /**
     * How closely the thing behind the unsigned seals is currently watching.
     *
     * <p>Zero means it is not, or has stopped. Above zero it is a countdown you
     * bring down by breaking its seals and push back up by losing to them.
     */
    public int attention(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t == null ? 0 : t.getInt("attention");
    }

    public void setAttention(UUID player, int v) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        t.putInt("attention", Math.max(0, v));
        setDirty();
    }

    /** Set once the player has been let go of. There is nothing after this. */
    public boolean released(UUID player) {
        CompoundTag t = runs.get("watch:" + player);
        return t != null && t.getBoolean("released");
    }

    public void setReleased(UUID player) {
        CompoundTag t = runs.computeIfAbsent("watch:" + player, x -> new CompoundTag());
        t.putBoolean("released", true);
        setDirty();
    }

    /**
     * The chain a player is currently following: which house cast it, how far
     * along they are, how many links there are, and roughly where the next one
     * is.
     *
     * <p>Kept as one tag so it can be cleared in a single call when a chain is
     * finished or abandoned, and so a half written chain can never exist.
     */
    public CompoundTag chain(UUID player) {
        CompoundTag t = runs.get("chain:" + player);
        return t == null ? new CompoundTag() : t;
    }

    public void setChain(UUID player, CompoundTag chain) {
        if (chain == null || chain.isEmpty()) runs.remove("chain:" + player);
        else runs.put("chain:" + player, chain);
        setDirty();
    }

    public int bestStreak(UUID player, String dim) {
        CompoundTag t = runs.get(runKey(player, dim));
        return t == null ? 0 : t.getInt("best");
    }

    /** One more lock down. Returns the new streak. */
    public int bumpStreak(UUID player, String dim) {
        String k = runKey(player, dim);
        CompoundTag t = runs.computeIfAbsent(k, x -> new CompoundTag());
        int n = t.getInt("streak") + 1;
        t.putInt("streak", n);
        if (n > t.getInt("best")) t.putInt("best", n);
        setDirty();
        return n;
    }

    /** Returns the streak that was just lost, so the player can be told what it cost. */
    public int breakStreak(UUID player, String dim) {
        String k = runKey(player, dim);
        CompoundTag t = runs.get(k);
        if (t == null) return 0;
        int had = t.getInt("streak");
        t.putInt("streak", 0);
        setDirty();
        return had;
    }

    public boolean has(UUID player, String dim, BlockPos pos) {
        return states.containsKey(key(player, dim, pos));
    }

    public void put(UUID player, String dim, BlockPos pos, CompoundTag tag) {
        states.put(key(player, dim, pos), tag);
        setDirty();
    }

    public void clear(UUID player, String dim, BlockPos pos) {
        if (states.remove(key(player, dim, pos)) != null) {
            setDirty();
        }
    }

    /** Wipe every player's state for a chest, used once the chest is opened or destroyed. */
    public void clearAllAt(String dim, BlockPos pos) {
        String suffix = "|" + dim + "|" + pos.asLong();
        if (states.keySet().removeIf(k -> k.endsWith(suffix))) {
            setDirty();
        }
    }
}
