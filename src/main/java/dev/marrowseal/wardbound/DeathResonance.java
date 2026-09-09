package dev.marrowseal.wardbound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Shared pressure created by actually living under Death Laws. */
public final class DeathResonance {
    private DeathResonance() {}

    public enum Stage {
        DORMANT("Dormant"), WHISPER("Whisper"), TOLLING("Tolling"), PROCESSION("Procession"), REQUIEM("Requiem");
        public final String title;
        Stage(String title) { this.title = title; }
    }

    public static int score(LockData data, UUID id) { return Mth.clamp(data.uniqueInt(id, "death_resonance"), 0, 100); }

    public static Stage stage(LockData data, UUID id) {
        int n = score(data, id);
        if (n <= 0) return Stage.DORMANT;
        if (n < 25) return Stage.WHISPER;
        if (n < 50) return Stage.TOLLING;
        if (n < 75) return Stage.PROCESSION;
        return Stage.REQUIEM;
    }

    public static void add(ServerPlayer player, LockData data, int amount, String reason) {
        if (player == null || data == null || amount <= 0) return;
        UUID id = player.getUUID();
        Stage before = stage(data, id);
        int next = Mth.clamp(score(data, id) + amount, 0, 100);
        data.setUniqueInt(id, "death_resonance", next);
        Stage after = stage(data, id);
        if (after != before) {
            data.setUniqueInt(id, "death_resonance_highest", Math.max(data.uniqueInt(id, "death_resonance_highest"), after.ordinal()));
            WardHistory.recordSpecial(player, "DANGER", "Death resonance: " + before.title + " → " + after.title
                    + (reason == null || reason.isBlank() ? "" : " · " + reason));
            WardHud.message(player, Component.literal("DEATH RESONANCE // " + after.title.toUpperCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        }
    }

    public static void relieve(LockData data, UUID id, int amount) {
        if (data == null || id == null || amount <= 0) return;
        data.setUniqueInt(id, "death_resonance", Math.max(0, score(data, id) - amount));
    }

    /** Player-aware relief records only meaningful stage drops; individual points remain quiet. */
    public static void relieve(ServerPlayer player, LockData data, int amount, String reason) {
        if (player == null || data == null || amount <= 0) return;
        UUID id = player.getUUID();
        Stage before = stage(data, id);
        int next = Math.max(0, score(data, id) - amount);
        data.setUniqueInt(id, "death_resonance", next);
        Stage after = stage(data, id);
        if (after != before) {
            WardHistory.recordSpecial(player, "WARD", "Death resonance: " + before.title + " → " + after.title
                    + (reason == null || reason.isBlank() ? "" : " · " + reason));
            WardHud.message(player, Component.literal("DEATH RESONANCE // " + after.title.toUpperCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), false);
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                    "death_resonance_relief_" + after.name().toLowerCase(java.util.Locale.ROOT),
                    after == Stage.DORMANT ? "The death-law pressure has gone quiet. That is not the same thing as the laws forgetting you."
                            : "The death-law pressure has receded one register. Ordinary wards can still teach death that you are difficult to price.",
                    20L * 180L);
        }
    }

    public static int mementoTarget(LockData data, UUID id) {
        return switch (stage(data, id)) {
            case PROCESSION -> 18;
            case REQUIEM -> 16;
            default -> 20;
        };
    }

    public static int graveBellTarget(LockData data, UUID id) {
        return switch (stage(data, id)) {
            case PROCESSION -> 5;
            case REQUIEM -> 4;
            default -> 6;
        };
    }

    /** Added only after normal pity math; even Requiem remains a small conditional nudge. */
    public static float deathHandBonus(LockData data, UUID id, boolean afterRefresh) {
        float base = switch (stage(data, id)) {
            case WHISPER -> 0.00015f;
            case TOLLING -> 0.00035f;
            case PROCESSION -> 0.00070f;
            case REQUIEM -> 0.00120f;
            default -> 0f;
        };
        return afterRefresh ? base * 0.55f : base;
    }

    public static String detail(LockData data, UUID id) {
        return stage(data, id).title + " · " + score(data, id) + "/100";
    }
}
