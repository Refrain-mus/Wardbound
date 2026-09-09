package dev.marrowseal.wardbound.client;

import net.minecraft.util.Mth;
import dev.marrowseal.wardbound.MinigameType;
import dev.marrowseal.wardbound.Sealmakers;

/**
 * Stable mechanical handwriting for a seal maker.
 *
 * <p>This is deliberately deterministic. A Verrick seal does not roll a new
 * personality every time it opens: the house is the rule. The exact expression
 * depends on the discipline, so the same hand can feel measured in Pressure and
 * cramped in Lattice without becoming a second anomaly table.</p>
 */
public final class MakerMinigameBehavior {
    private MakerMinigameBehavior() {}

    public enum Style {
        MEASURED("measured hand"),
        CROOKED("crooked hand"),
        VEILED("veiled hand"),
        IMPATIENT("impatient hand"),
        PATIENT("patient hand"),
        BINDING("binding hand"),
        OPEN("open hand"),
        UNSIGNED("unsigned hand");

        public final String label;
        Style(String label) { this.label = label; }
    }

    public static Style style(long seed, boolean unsigned) {
        if (unsigned) return Style.UNSIGNED;
        String house = Sealmakers.house(seed);
        int idx = Math.floorMod(house.hashCode(), Style.values().length - 1);
        return Style.values()[idx];
    }

    public static String label(long seed, boolean unsigned, MinigameType game) {
        if (unsigned) return "unsigned hand";
        Style s = style(seed, false);
        return Sealmakers.house(seed) + " · " + ruleName(s, game);
    }

    private static String ruleName(Style s, MinigameType game) {
        boolean logic = logic(game);
        boolean directional = directional(game);
        return switch (s) {
            case MEASURED -> logic ? "narrow proof" : "measured cadence";
            case CROOKED -> directional ? "crooked orientation" : "crooked cadence";
            case VEILED -> logic ? "veiled notation" : "veiled instrument";
            case IMPATIENT -> logic ? "short reckoning" : "impatient cadence";
            case PATIENT -> logic ? "patient proof" : "patient cadence";
            case BINDING -> logic ? "binding proof" : "binding window";
            case OPEN -> logic ? "open proof" : "open cadence";
            case UNSIGNED -> "unsigned";
        };
    }

    public static float speedScale(Style s, MinigameType game, long seed, float t) {
        if (logic(game)) {
            return switch (s) {
                case IMPATIENT -> 1.05f;
                case PATIENT -> 0.96f;
                default -> 1f;
            };
        }
        return switch (s) {
            case MEASURED -> 0.94f;
            case CROOKED -> 0.88f + 0.28f * (0.5f + 0.5f * Mth.sin(t * 1.45f + (seed & 15)));
            case VEILED -> 1.02f;
            case IMPATIENT -> 1.15f + 0.12f * (0.5f + 0.5f * Mth.sin(t * 2.35f + ((seed >>> 4) & 15)));
            case PATIENT -> 0.84f;
            case BINDING -> 1.04f;
            case OPEN -> 1.08f;
            case UNSIGNED -> 1f;
        };
    }

    public static float graceScale(Style s, MinigameType game) {
        return switch (s) {
            case MEASURED -> 0.90f;
            case BINDING -> 0.86f;
            case OPEN -> 1.12f;
            case PATIENT -> 1.06f;
            case IMPATIENT -> timing(game) ? 0.94f : 1f;
            default -> 1f;
        };
    }

    public static float timeScale(Style s, MinigameType game) {
        return switch (s) {
            case IMPATIENT -> logic(game) ? 0.90f : 0.94f;
            case PATIENT -> logic(game) ? 1.10f : 1.04f;
            case MEASURED -> logic(game) ? 0.96f : 1f;
            case OPEN -> 1.03f;
            default -> 1f;
        };
    }

    /** Crooked hands periodically reverse only games where orientation is a meaningful rule. */
    public static boolean mirrorActive(Style s, MinigameType game, long seed, float t) {
        if (s != Style.CROOKED || !directional(game)) return false;
        float phase = (t + Math.floorMod(seed, 17) * 0.13f) % 6.2f;
        return phase >= 3.8f && phase < 5.05f;
    }

    /** The veil occludes only a narrow band and never the whole information channel. */
    public static float veilStrength(Style s, MinigameType game, long seed, float t) {
        if (s != Style.VEILED) return 0f;
        float phase = (t + Math.floorMod(seed, 11) * 0.17f) % 5.4f;
        if (phase < 3.55f) return 0f;
        float local = (phase - 3.55f) / 1.85f;
        return Mth.clamp((float)Math.sin(local * Math.PI), 0f, 1f);
    }

    public static boolean bindingLogic(Style s, MinigameType game) {
        return s == Style.BINDING && logic(game);
    }

    private static boolean logic(MinigameType g) {
        return switch (g) {
            case CIPHER, AUGURY, CONSTELLATION, VESSEL, ROOTWAY, LATTICE,
                    RUNEBRAID, SHARDSONG, VEILSTEP, BLACK_MEASURE, EPITAPH -> true;
            default -> false;
        };
    }

    private static boolean timing(MinigameType g) {
        return switch (g) {
            case DRUM, TIMING, PRESSURE, RESONANCE, PULSE, BALANCE, GYRE, YOKE, PARALLAX -> true;
            default -> false;
        };
    }

    private static boolean directional(MinigameType g) {
        return switch (g) {
            case DRUM, TIMING, RUNES, CIPHER, PRESSURE, PULSE, BALANCE, MIRROR,
                    KEYWAY, GYRE, YOKE, ROOTWAY, PARALLAX, RUNEBRAID, SHARDSONG,
                    VEILSTEP, BLACK_MEASURE, EPITAPH -> true;
            default -> false;
        };
    }
}
