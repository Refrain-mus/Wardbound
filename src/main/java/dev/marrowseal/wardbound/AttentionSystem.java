package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Reads the old Attention countdown as a staged relationship instead of a raw number. */
public final class AttentionSystem {
    private AttentionSystem() {}

    public enum Stage {
        UNNOTICED("Unnoticed"),
        OBSERVED("Observed"),
        FOLLOWED("Followed"),
        MARKED("Marked"),
        HUNTED("Hunted"),
        KNOWN("Known");
        public final String title;
        Stage(String title) { this.title = title; }
    }

    public static Stage stage(int attention) {
        if (attention <= 0) return Stage.UNNOTICED;
        int start = Math.max(1, WardConfig.attentionStart);
        int cap = Math.max(start, WardConfig.attentionCap);
        if (attention <= Math.max(2, start / 4)) return Stage.OBSERVED;
        if (attention <= Math.max(4, start / 2)) return Stage.FOLLOWED;
        if (attention <= start) return Stage.MARKED;
        if (attention <= start + Math.max(2, (cap - start) / 2)) return Stage.HUNTED;
        return Stage.KNOWN;
    }

    public static Stage current(LockData data, UUID player) {
        if (data == null || player == null) return Stage.UNNOTICED;
        if (!CardMaster.phaseActive(data, player)) {
            // Migration/robustness guard: older builds could accumulate Attention
            // before the late dealer layer existed. Do not let it wake up later.
            if (data.attention(player) != 0) data.setAttention(player, 0);
            if (data.uniqueInt(player, "attention_highest_stage") != 0)
                data.setUniqueInt(player, "attention_highest_stage", 0);
            return Stage.UNNOTICED;
        }
        return stage(data.attention(player));
    }

    public static int threatBonus(Stage stage) {
        return switch (stage) {
            case HUNTED -> 1;
            case KNOWN -> 2;
            default -> 0;
        };
    }

    public static float resealScale(Stage stage) {
        return switch (stage) {
            case OBSERVED -> 0.62f;
            case FOLLOWED -> 0.82f;
            case MARKED -> 1.00f;
            case HUNTED -> 1.18f;
            case KNOWN -> 1.34f;
            default -> 0f;
        };
    }


    /** Attention is deliberately asymmetric: early observation is easy to shake,
     * while being Known takes repeated clean resolutions to unwind. */
    public static int reliefPerWin(Stage stage) {
        return switch (stage) {
            case OBSERVED -> 3;
            case FOLLOWED -> 2;
            case MARKED, HUNTED, KNOWN -> 1;
            default -> 0;
        };
    }

    /** Performance grades are part of the relationship: a scraped opening barely shakes observation,
     * clean work is respected, and a perfect resolution can unwind one additional point. */
    public static int reliefForResolution(Stage stage, RewardBreakdown.PerformanceGrade grade) {
        if (grade == null || grade == RewardBreakdown.PerformanceGrade.NONE) return 0;
        if (grade == RewardBreakdown.PerformanceGrade.PERFECT) {
            int base = reliefPerWin(stage);
            return base <= 0 ? 0 : base + 1;
        }
        if (grade == RewardBreakdown.PerformanceGrade.CLEAN) return reliefPerWin(stage);
        return switch (stage) {
            case OBSERVED, FOLLOWED -> 1;
            default -> 0;
        };
    }

    /** A loss teaches high-stage Attention more than low-stage Attention. */
    public static int lossGain(Stage stage, int base) {
        int safe = Math.max(0, base);
        return switch (stage) {
            case MARKED -> safe + 1;
            case HUNTED -> safe + 2;
            case KNOWN -> safe + 3;
            default -> safe;
        };
    }

    public static float difficultyScale(Stage stage) {
        return switch (stage) {
            case OBSERVED -> 1.01f;
            case FOLLOWED -> 1.025f;
            case MARKED -> 1.045f;
            case HUNTED -> 1.075f;
            case KNOWN -> 1.11f;
            default -> 1.0f;
        };
    }

    /** The world pays slightly more when it is actively pricing the player. */
    public static float lootScale(Stage stage) {
        return switch (stage) {
            case OBSERVED -> 1.01f;
            case FOLLOWED -> 1.02f;
            case MARKED -> 1.035f;
            case HUNTED -> 1.055f;
            case KNOWN -> 1.08f;
            default -> 1.0f;
        };
    }

    public static String stageRule(Stage stage) {
        return switch (stage) {
            case UNNOTICED -> "No active pressure.";
            case OBSERVED -> "Perfect/Clean wins remove 4/3 pressure; Scraped still removes 1.";
            case FOLLOWED -> "Perfect/Clean wins remove 3/2 pressure; Scraped removes only 1.";
            case MARKED -> "Perfect/Clean wins remove 2/1 pressure; Scraped removes none; failures add an extra point.";
            case HUNTED -> "Perfect/Clean wins remove 2/1 pressure; Scraped removes none; failures add two extra and dangerous shelves lean closer.";
            case KNOWN -> "Perfect/Clean wins remove 2/1 pressure; Scraped removes none; failures add three extra at maximum adaptive pressure.";
        };
    }

    public static float hordeScale(Stage stage) {
        return switch (stage) {
            case OBSERVED -> 0.55f;
            case FOLLOWED -> 0.78f;
            case MARKED -> 1.00f;
            case HUNTED -> 1.20f;
            case KNOWN -> 1.42f;
            default -> 0f;
        };
    }

    /** Central setter so stage transitions cannot silently bypass history/Savant memory. */
    public static void set(ServerPlayer player, LockData data, int value, String reason) {
        if (player == null || data == null) return;
        UUID id = player.getUUID();
        if (!CardMaster.phaseActive(data, id)) {
            if (data.attention(id) != 0) data.setAttention(id, 0);
            if (data.uniqueInt(id, "attention_highest_stage") != 0)
                data.setUniqueInt(id, "attention_highest_stage", 0);
            return;
        }
        Stage before = current(data, id);
        int clamped = Mth.clamp(value, 0, WardConfig.attentionCap);
        data.setAttention(id, clamped);
        Stage after = stage(clamped);
        if (after.ordinal() > data.uniqueInt(id, "attention_highest_stage"))
            data.setUniqueInt(id, "attention_highest_stage", after.ordinal());
        if (after != before) {
            WardHistory.discoverAttentionStage(player, after);
            WardHistory.recordSpecial(player, after.ordinal() > before.ordinal() ? "DANGER" : "WARD",
                    "Attention: " + before.title + " → " + after.title
                            + (reason == null || reason.isBlank() ? "" : " · " + reason));
            if (after != Stage.UNNOTICED) {
                WardHud.message(player, Component.literal("ATTENTION // " + after.title.toUpperCase(java.util.Locale.ROOT))
                        .withStyle(after.ordinal() >= Stage.HUNTED.ordinal() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_AQUA,
                                ChatFormatting.BOLD), false);
            }
            SavantMemory.onAttentionShift(player, before, after);
        }
    }

    public static String detail(LockData data, UUID id) {
        Stage current = current(data, id);
        if (current == Stage.UNNOTICED) return current.title;
        int value = data.attention(id);
        return current.title + " · pressure " + value + "/" + WardConfig.attentionCap;
    }
}
