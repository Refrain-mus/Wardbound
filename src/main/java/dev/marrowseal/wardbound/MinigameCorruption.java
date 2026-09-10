package dev.marrowseal.wardbound;

import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Mastery-gated corrupted forms of ordinary minigames.
 *
 * <p>The six low-level profiles are reusable mechanics, but every discipline
 * owns two named forms. This keeps the implementation auditable while making a
 * corrupted Drum feel like a corrupted Drum rather than a generic purple
 * modifier pasted over every screen. Forms are chosen deterministically from
 * the live ward seed and never override Possessed/Eldritch/Cthulhu rules.</p>
 */
public final class MinigameCorruption {
    private MinigameCorruption() {}

    public enum Profile {
        NONE("ordinary seal"),
        MIRROR_PULSE("mirror pulse"),
        TIME_FRACTURE("time fracture"),
        BLACKOUT("black interval"),
        BLOOD_RUSH("blood rush"),
        FALSE_CLOCK("false clock"),
        ECHO_GLASS("echo glass");

        public final String label;
        Profile(String label) { this.label = label; }
    }

    public record Form(Profile profile, String name, String rule) {}

    /** Wire/save-safe per-discipline corruption identity. Slot 0 is ordinary, slots 1/2 are curated forms. */
    public record Variant(int slot, Profile profile, String label, String rule, float rewardBonus, int minMasteryTier) {
        public boolean active() { return slot > 0 && profile != Profile.NONE; }
    }

    private static final Variant NONE_VARIANT = new Variant(0, Profile.NONE, "ordinary seal", "No corrupted rule is active.", 0f, 0);

    private static final Map<MinigameType, Form[]> FORMS = new EnumMap<>(MinigameType.class);
    static {
        forms(MinigameType.DRUM,
                form(Profile.TIME_FRACTURE, "Broken Spring", "The drum alternates between slow drag and violent acceleration."),
                form(Profile.FALSE_CLOCK, "Borrowed Beat", "The visible deadline lies intermittently while the notch remains honest."));
        forms(MinigameType.TIMING,
                form(Profile.TIME_FRACTURE, "Split Second", "The marker cadence alternates between slow and fast slices."),
                form(Profile.FALSE_CLOCK, "False Deadline", "The displayed ward time lies intermittently; the target band never does."));
        forms(MinigameType.MEMORY,
                form(Profile.ECHO_GLASS, "Afterimage Sequence", "A displaced cursor echo complicates recall input, but the shown sequence itself stays fully visible."),
                form(Profile.FALSE_CLOCK, "Lying Hourglass", "The visible ward clock sometimes reports a false remainder; the sequence never lies."));
        forms(MinigameType.RUNES,
                form(Profile.MIRROR_PULSE, "Wandering Reflection", "The reaction field periodically reflects its input space."),
                form(Profile.BLOOD_RUSH, "Bloodlit Grid", "Each mistake makes the living grid move faster."));
        forms(MinigameType.CIPHER,
                form(Profile.BLACKOUT, "Blind Index", "Black ink periodically takes one side of the ring face; locked rotations remain unchanged."),
                form(Profile.TIME_FRACTURE, "Fractured Rotation", "Ring motion alternates between two incompatible cadences."));
        forms(MinigameType.PRESSURE,
                form(Profile.TIME_FRACTURE, "Ruptured Tempo", "Pressure gain cadence fractures into slow and fast phases."),
                form(Profile.BLOOD_RUSH, "Red Bellows", "Every bad release makes later pressure cycles more aggressive."));
        forms(MinigameType.RESONANCE,
                form(Profile.ECHO_GLASS, "Echo Harmonic", "A displaced cursor echo competes with the true spectrum position; the waveform stays exact."),
                form(Profile.FALSE_CLOCK, "False Meter", "The clock lies intermittently while the waveform itself stays honest."));
        forms(MinigameType.PULSE,
                form(Profile.TIME_FRACTURE, "Broken Tempo", "The pulse track alternates between dragged and accelerated time."),
                form(Profile.BLOOD_RUSH, "Red Tempo", "Misses feed speed into the remaining pulse track."));
        forms(MinigameType.BALANCE,
                form(Profile.MIRROR_PULSE, "Counterweight Mirror", "Steering reverses during clearly marked mirror pulses."),
                form(Profile.BLOOD_RUSH, "Blood Pendulum", "Mistakes feed additional speed into the beam."));
        forms(MinigameType.CONSTELLATION,
                form(Profile.ECHO_GLASS, "Doubled Heavens", "A displaced cursor echo interferes with tracing while every true star remains visible."),
                form(Profile.FALSE_CLOCK, "False Ephemeris", "The clock lies; the constellation does not."));
        forms(MinigameType.MIRROR,
                form(Profile.BLACKOUT, "Blind Reflection", "Black ink periodically takes one side of the comparison; the hidden state stays unchanged."),
                form(Profile.FALSE_CLOCK, "False Reflection", "Time information becomes unreliable while orientation remains exact."));
        forms(MinigameType.KEYWAY,
                form(Profile.TIME_FRACTURE, "Stuttering Tumbler", "Pin travel alternates between dragged and accelerated time."),
                form(Profile.BLOOD_RUSH, "Blood Tumbler", "Oversets make the cylinder progressively more violent."));
        forms(MinigameType.AUGURY,
                form(Profile.ECHO_GLASS, "Echoed Omen", "The cursor leaves a displaced echo that must not be mistaken for the true choice."),
                form(Profile.BLACKOUT, "Black Omen", "Black ink periodically takes one side of the omen without changing any deduction clue."));
        forms(MinigameType.GYRE,
                form(Profile.TIME_FRACTURE, "Broken Gyre", "The sweep continually alternates between slow and fast rotational time."),
                form(Profile.BLOOD_RUSH, "Hungry Orbit", "Missed nodes make the remaining orbit progressively faster."));
        forms(MinigameType.VESSEL,
                form(Profile.BLACKOUT, "Black Surface", "Black ink periodically takes one side of the surface while the hidden state persists."),
                form(Profile.FALSE_CLOCK, "False Depth", "The clock lies while the vessel's paid information remains truthful."));
        forms(MinigameType.YOKE,
                form(Profile.TIME_FRACTURE, "Broken Coupling", "Both coupled mechanisms alternately drag and lurch together."),
                form(Profile.BLOOD_RUSH, "Red Load", "Mistakes increase the speed of both coupled mechanisms."));
        forms(MinigameType.ROOTWAY,
                form(Profile.ECHO_GLASS, "Echoed Roots", "The pointer drifts from its visible echo while the channel map remains stable."),
                form(Profile.TIME_FRACTURE, "Time-Split Root", "Growth cadence alternates sharply between slow and fast phases."));
        forms(MinigameType.LATTICE,
                form(Profile.ECHO_GLASS, "Ghost Crosses", "A displaced cursor echo makes careless tile reads dangerous."),
                form(Profile.BLACKOUT, "Dead Lattice", "Black ink periodically consumes one side of the board; its logical state does not change."));
        forms(MinigameType.PARALLAX,
                form(Profile.MIRROR_PULSE, "Mirror Depth", "Horizontal alignment periodically reflects around the frame."),
                form(Profile.BLOOD_RUSH, "Blood Horizon", "Every failed commit makes fragment motion more aggressive."));
        forms(MinigameType.RUNEBRAID,
                form(Profile.ECHO_GLASS, "Echo Strand", "A visible cursor echo complicates adjacent-strand selection."),
                form(Profile.BLACKOUT, "Black Thread", "Black ink periodically consumes one side of the braid; its order never changes."));
        forms(MinigameType.SHARDSONG,
                form(Profile.ECHO_GLASS, "Echoed Edges", "The selection pointer drifts while the edge-rune constraints stay truthful."),
                form(Profile.BLACKOUT, "Silent Shard", "Black ink periodically consumes one side of the tablet without rewriting any shard."));
        forms(MinigameType.VEILSTEP,
                form(Profile.ECHO_GLASS, "Echo Path", "A displaced cursor echo makes route commitment less comfortable."),
                form(Profile.BLACKOUT, "Absent Road", "Black ink periodically consumes one side of the stepping stones; distances remain unchanged."));
        forms(MinigameType.BLACK_MEASURE,
                form(Profile.ECHO_GLASS, "Echoed Vessel", "The cursor echo can suggest the wrong vessel; volumes remain exact."),
                form(Profile.BLACKOUT, "Blind Measure", "Black ink periodically consumes one side of the vessels; exact volumes remain unchanged."));
        forms(MinigameType.EPITAPH,
                form(Profile.ECHO_GLASS, "Echoed Grammar", "The pointer drifts while the written grammar itself remains trustworthy."),
                form(Profile.BLACKOUT, "Erased Witness", "Black ink periodically consumes one side of the inscription; its grammar remains unchanged."));
        forms(MinigameType.ORRERY,
                form(Profile.MIRROR_PULSE, "Reflected Orrery", "The presentation periodically reflects while the visible gear law remains unchanged."),
                form(Profile.TIME_FRACTURE, "Broken Ephemeris", "The occult mechanism alternates between languid and urgent presentation without changing its legal moves."));
        forms(MinigameType.PROCESSION,
                form(Profile.ECHO_GLASS, "Echoed Testimony", "A displaced cursor echo complicates witness exchange; every written statement remains literal."),
                form(Profile.BLACKOUT, "Black Procession", "Black ink periodically consumes one side of the docket; the remaining testimony still has one solution."));
        FORMS.put(MinigameType.CTHULHUS_GAME, new Form[]{
                form(Profile.NONE, "Final Examination", "Cthulhu's Game corrupts itself instead of borrowing ordinary forms."),
                form(Profile.NONE, "Final Examination", "Cthulhu's Game corrupts itself instead of borrowing ordinary forms.")});
    }

    private static Form form(Profile p, String n, String r) { return new Form(p, n, r); }
    private static void forms(MinigameType game, Form a, Form b) { FORMS.put(game, new Form[]{a, b}); }

    public static Variant variant(MinigameType game, int slot) {
        if (game == null || slot <= 0 || game == MinigameType.CTHULHUS_GAME) return NONE_VARIANT;
        Form[] forms = FORMS.get(game);
        int index = slot - 1;
        if (forms == null || index < 0 || index >= forms.length) return NONE_VARIANT;
        Form f = forms[index];
        int minTier = slot == 1 ? 2 : 3;
        return new Variant(slot, f.profile(), "corrupted · " + f.name(), f.rule(), rewardBonus(f.profile()), minTier);
    }

    public static Profile byOrdinal(int ordinal) {
        Profile[] values = Profile.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Profile.NONE;
    }

    public static Form formFor(MinigameType game, Profile profile) {
        if (game == null || profile == null || profile == Profile.NONE)
            return form(Profile.NONE, "Ordinary Seal", "No corrupted rule is active.");
        for (Form f : FORMS.getOrDefault(game, new Form[0])) if (f.profile == profile) return f;
        return form(profile, profile.label, genericRule(profile));
    }

    public static String formName(MinigameType game, Profile profile) { return formFor(game, profile).name(); }
    public static String formRule(MinigameType game, Profile profile) { return formFor(game, profile).rule(); }

    private static String genericRule(Profile profile) {
        return switch (profile) {
            case MIRROR_PULSE -> "Horizontal input periodically reverses.";
            case TIME_FRACTURE -> "Gameplay cadence alternates between slow and fast slices.";
            case BLACKOUT -> "Black intervals cover one side of the playfield without rewriting it.";
            case BLOOD_RUSH -> "Mistakes progressively accelerate the mechanism.";
            case FALSE_CLOCK -> "The displayed clock intermittently lies.";
            case ECHO_GLASS -> "The visible cursor develops a displaced echo.";
            default -> "No corrupted rule is active.";
        };
    }

    public static Variant choose(LockData data, UUID player, MinigameType game, long seed,
                                 int resolvedWards, boolean alreadySpecial) {
        if (data == null || player == null || game == null || alreadySpecial || game == MinigameType.CTHULHUS_GAME)
            return NONE_VARIANT;
        int mastery = MinigameMastery.tier(data, player, game);
        if (mastery < 2 || resolvedWards < WardConfig.mutationTier1AfterBeaten) return NONE_VARIANT;
        float chance = switch (mastery) {
            case 2 -> 0.055f;
            case 3 -> 0.085f;
            case 4 -> 0.125f;
            default -> 0.165f;
        };
        Random rng = new Random(seed ^ 0x434F52525550544CL ^ ((long) game.ordinal() << 32));
        if (rng.nextFloat() >= chance) return NONE_VARIANT;
        Variant first = variant(game, 1);
        Variant second = variant(game, 2);
        if (!first.active()) return NONE_VARIANT;
        if (mastery < second.minMasteryTier() || !second.active()) return first;
        float secondChance = mastery >= 5 ? 0.58f : mastery >= 4 ? 0.46f : 0.34f;
        return rng.nextFloat() < secondChance ? second : first;
    }

    public static boolean hasSeen(LockData data, UUID id, MinigameType game, Profile profile) {
        if (data == null || id == null || game == null || profile == null || profile == Profile.NONE) return false;
        for (int slot = 1; slot <= 2; slot++) {
            Variant v = variant(game, slot);
            if (v.profile() == profile && (MinigameMastery.corruptionMask(data, id, game) & (1 << (slot - 1))) != 0) return true;
        }
        return false;
    }

    public static String discoveredForms(LockData data, UUID id, MinigameType game) {
        StringBuilder out = new StringBuilder();
        for (Form f : FORMS.getOrDefault(game, new Form[0])) {
            if (f.profile == Profile.NONE || !hasSeen(data, id, game, f.profile)) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(f.name());
        }
        return out.length() == 0 ? "none witnessed" : out.toString();
    }

    public static String discoveredFormDetails(LockData data, UUID id, MinigameType game) {
        StringBuilder out = new StringBuilder();
        for (int slot = 1; slot <= 2; slot++) {
            Variant v = variant(game, slot);
            if (!v.active() || (MinigameMastery.corruptionMask(data, id, game) & (1 << (slot - 1))) == 0) continue;
            if (out.length() > 0) out.append(" / ");
            out.append(v.label().replace("corrupted · ", "")).append(": ").append(v.rule());
        }
        return out.length() == 0 ? "none witnessed" : out.toString();
    }

    public static float rewardBonus(Profile profile) {
        return switch (profile) {
            case MIRROR_PULSE, FALSE_CLOCK -> 0.035f;
            case TIME_FRACTURE, BLACKOUT, ECHO_GLASS -> 0.05f;
            case BLOOD_RUSH -> 0.065f;
            default -> 0f;
        };
    }

    public static float rewardBonus(Variant variant) { return variant == null ? 0f : rewardBonus(variant.profile()); }

    public static float blackoutPhase(long seed, float time) {
        float period = 5.4f + Math.floorMod((int) seed, 11) * 0.08f;
        float shifted = time + Math.floorMod((int) (seed >>> 13), 100) / 100f * period;
        return shifted - (float) Math.floor(shifted / period) * period;
    }

    public static boolean blackoutActive(Profile profile, long seed, float time) {
        return profile == Profile.BLACKOUT && blackoutPhase(seed, time) < 0.72f;
    }
    public static boolean blackoutActive(Variant variant, long seed, float time) {
        return variant != null && blackoutActive(variant.profile(), seed, time);
    }

    public static boolean mirrorActive(Profile profile, long seed, float time) {
        if (profile != Profile.MIRROR_PULSE) return false;
        float period = 6.1f + Math.floorMod((int) (seed >>> 7), 7) * 0.12f;
        float shifted = time + Math.floorMod((int) seed, 100) / 100f * period;
        float ph = shifted - (float) Math.floor(shifted / period) * period;
        return ph >= 0.65f && ph < 2.05f;
    }

    public static boolean mirrorActive(Variant variant, long seed, float time) {
        return variant != null && mirrorActive(variant.profile(), seed, time);
    }

    public static float speedScale(Profile profile, long seed, float time, int mistakes) {
        return switch (profile) {
            case TIME_FRACTURE -> {
                int slice = (int) Math.floor((time + Math.floorMod((int) seed, 100) * 0.01f) / 0.72f);
                yield (slice & 1) == 0 ? 0.68f : 1.38f;
            }
            case BLOOD_RUSH -> 1f + Math.min(0.38f, Math.max(0, mistakes) * 0.075f);
            default -> 1f;
        };
    }

    public static float speedScale(Variant variant, long seed, float time, int mistakes) {
        return variant == null ? 1f : speedScale(variant.profile(), seed, time, mistakes);
    }

    public static float clampSpeed(float scale) { return Mth.clamp(scale, 0.55f, 1.55f); }

    private static String prettify(String s) {
        StringBuilder out = new StringBuilder();
        for (String bit : s.replace('_', ' ').split(" ")) {
            if (bit.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(bit.charAt(0))).append(bit.substring(1));
        }
        return out.toString();
    }
}
