package dev.marrowseal.wardbound.champion;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class ChampionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue CHAPTER, INTERVAL, PITY, TRAVEL, COMPANION_SECONDS, CALL_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SILAS_COMPANION_EXCLUDED_TARGETS;

    static {
        var b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Enable the unique Silas Veyr encounter.").define("enabled", true);
        CHAPTER = b.comment("Any Master story chapter required (of seven). Silas appears from the middle of the lore onward.")
                .defineInRange("storyChapter", 4, 1, 7);
        INTERVAL = b.comment("Ticks between eligible Overworld encounter checks.")
                .defineInRange("checkInterval", 1200, 200, 24000);
        CHANCE = b.comment("Per-check chance while eligible before pity applies. Kept intentionally low because Silas is meant to be rare.")
                .defineInRange("encounterChance", .08, 0, 1);
        PITY = b.comment("Guarantee an arrival attempt after this many eligible checks; safe terrain is still required.")
                .defineInRange("guaranteedAfterChecks", 18, 1, 120);
        TRAVEL = b.comment("Minimum ticks since arrival AND since anyone was nearby before unseen travel.")
                .defineInRange("travelAfterTicks", 48000, 24000, 2400000);
        COMPANION_SECONDS = b.comment("How long Silas remains present after answering the Worn Scabbard Ring.")
                .defineInRange("companionSeconds", 90, 30, 600);
        CALL_COOLDOWN_SECONDS = b.comment("Cooldown before the Worn Scabbard Ring can call Silas again.")
                .defineInRange("callCooldownSeconds", 720, 60, 7200);
        SILAS_COMPANION_EXCLUDED_TARGETS = b.comment("Entity ids or #entity_type_tags that Silas must never attack while acting as a companion.")
                .defineListAllowEmpty(List.of("silasCompanionExcludedTargets"), List.of(), o -> o instanceof String s && !s.isBlank());
        SPEC = b.build();
    }

    private ChampionConfig() {}

    public static List<String> silasCompanionExcludedTargets() {
        return SILAS_COMPANION_EXCLUDED_TARGETS.get().stream().map(String::valueOf).toList();
    }
}
