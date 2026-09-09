package dev.marrowseal.wardbound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * Player-aware risk classification for a physical ward. The tier is intentionally
 * coarse for presentation; pressure is the continuous 0-100 value used to make
 * two wards inside the same tier feel slightly different without creating a new
 * category every few points.
 */
public final class ChestThreat {
    private ChestThreat() {}

    public static final String TAG_TIER = "WardThreatTier";
    public static final String TAG_PRESSURE = "WardThreatPressure";
    public static final String TAG_WHO = "WardThreatWho";

    public enum Tier {
        QUIET("Quiet", "The lock is awake, but not agitated."),
        RESTLESS("Restless", "The seal is carrying more pressure than its contents require."),
        HOSTILE("Hostile", "Failure is likely to wake something with the mechanism."),
        PROFANE("Profane", "The ward is over-invested in the outcome."),
        UNNAMEABLE("Unnameable", "The seal is behaving as if the chest is only part of the transaction.");

        public final String title;
        public final String hint;
        Tier(String title, String hint) { this.title = title; this.hint = hint; }
    }

    /** Preserves the v32 tier composition while exposing finer pressure inside it. */
    public static int compute(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        CompoundTag p = container.getPersistentData();
        int value = p.getInt(ChestValuator.TAG_VALUE);
        int tier = value >= 90 ? 4 : value >= 74 ? 3 : value >= 58 ? 2 : value >= 40 ? 1 : 0;
        tier += p.getInt(ChestValuator.TAG_DEPTH) >= 2 ? 1 : 0;
        tier += p.getInt(ChestValuator.TAG_SPITE) >= 2 ? 1 : 0;
        CompoundTag memory = ChestValuator.chestMemory(p, player.getUUID());
        tier += memory.getInt("losses") > memory.getInt("wins") + 1 ? 1 : 0;
        tier += AttentionSystem.threatBonus(AttentionSystem.current(data, player.getUUID()));
        tier += PlayerImprint.threatAdjustment(data, player.getUUID());
        tier += data.uniqueInt(player.getUUID(), "covenant_stain") >= 4 ? 1 : 0;
        if (p.getBoolean(ChestValuator.TAG_SHARD_OVERCHARGE)) tier += 1;
        if (p.getBoolean(ChestValuator.TAG_SPLINTER_BRACE)) tier -= 1;
        return Mth.clamp(tier, 0, 4);
    }

    /** Continuous pressure inside the coarse tier. */
    public static int computePressure(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        CompoundTag p = container.getPersistentData();
        int tier = compute(player, container, data);
        int value = Mth.clamp(p.getInt(ChestValuator.TAG_VALUE), 0, 120);
        int fine = Math.round((value / 120.0f) * 9.0f); // monotonic 0..9 from chest value
        fine += Math.min(5, p.getInt(ChestValuator.TAG_DEPTH) * 2);
        fine += Math.min(4, p.getInt(ChestValuator.TAG_SPITE));
        CompoundTag memory = ChestValuator.chestMemory(p, player.getUUID());
        {
            int streak = memory.getInt("streak");
            if (streak < 0) fine += Math.min(8, -streak * 2);
            else if (streak >= 3) fine -= Math.min(5, streak - 2);
        }
        if (p.getBoolean(ChestValuator.TAG_SHARD_OVERCHARGE)) fine += 5;
        if (p.getBoolean(ChestValuator.TAG_SPLINTER_BRACE)) fine -= 5;
        int floor = tier * 20;
        int ceiling = tier >= 4 ? 100 : floor + 19;
        return Mth.clamp(floor + fine, floor, ceiling);
    }

    public static int bind(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        int tier = compute(player, container, data);
        int pressure = computePressure(player, container, data);
        CompoundTag p = container.getPersistentData();
        p.putInt(TAG_TIER, tier);
        p.putInt(TAG_PRESSURE, pressure);
        p.putUUID(TAG_WHO, player.getUUID());
        container.setChanged();
        return tier;
    }

    public static int bound(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        CompoundTag p = container.getPersistentData();
        if (p.hasUUID(TAG_WHO) && p.getUUID(TAG_WHO).equals(player.getUUID()) && p.contains(TAG_TIER))
            return Mth.clamp(p.getInt(TAG_TIER), 0, 4);
        return bind(player, container, data);
    }

    public static int boundPressure(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        CompoundTag p = container.getPersistentData();
        if (p.hasUUID(TAG_WHO) && p.getUUID(TAG_WHO).equals(player.getUUID()) && p.contains(TAG_PRESSURE))
            return Mth.clamp(p.getInt(TAG_PRESSURE), 0, 100);
        bind(player, container, data);
        return Mth.clamp(p.getInt(TAG_PRESSURE), 0, 100);
    }

    public static Tier tier(int value) { return Tier.values()[Mth.clamp(value, 0, 4)]; }
    public static String title(int value) { return tier(value).title; }

    public static float difficultyScale(int pressure) {
        return 1.0f + Mth.clamp(pressure, 0, 100) * 0.0011f;
    }

    /** Continuous reward curve: 0 pressure = x1.00, 100 pressure = x1.18. */
    public static float lootScaleForPressure(int pressure) {
        return 1.0f + Mth.clamp(pressure, 0, 100) * 0.0018f;
    }

    /** Continuous guardian curve interpolated through the old tier anchors. */
    public static float guardianScaleForPressure(int pressure) {
        int p = Mth.clamp(pressure, 0, 100);
        float[] anchors = {0.80f, 1.00f, 1.18f, 1.42f, 1.72f, 1.80f};
        int band = Math.min(4, p / 20);
        float local = (p - band * 20) / 20.0f;
        return anchors[band] + (anchors[band + 1] - anchors[band]) * local;
    }

    /** Reward is additive to the risk economy, but capped by the existing progression ceiling. */
    public static float lootScale(int tier) {
        return switch (Mth.clamp(tier, 0, 4)) {
            case 1 -> 1.03f;
            case 2 -> 1.07f;
            case 3 -> 1.12f;
            case 4 -> 1.18f;
            default -> 1.00f;
        };
    }

    public static float guardianScale(int tier) {
        return switch (Mth.clamp(tier, 0, 4)) {
            case 0 -> 0.80f;
            case 2 -> 1.18f;
            case 3 -> 1.42f;
            case 4 -> 1.72f;
            default -> 1.00f;
        };
    }

    public static String debug(ServerPlayer player, RandomizableContainerBlockEntity container, LockData data) {
        CompoundTag p = container.getPersistentData();
        int t = bound(player, container, data);
        int pressure = boundPressure(player, container, data);
        return title(t) + " (" + t + ") · pressure " + pressure + "/100"
                + String.format(java.util.Locale.ROOT, " · diff x%.3f · loot x%.3f · guardian x%.3f",
                        difficultyScale(pressure), lootScaleForPressure(pressure), guardianScaleForPressure(pressure))
                + " · value " + p.getInt(ChestValuator.TAG_VALUE)
                + " · depth " + p.getInt(ChestValuator.TAG_DEPTH)
                + " · spite " + p.getInt(ChestValuator.TAG_SPITE)
                + " · attention " + AttentionSystem.current(data, player.getUUID()).title
                + " · imprint " + PlayerImprint.compact(data, player.getUUID());
    }
}
