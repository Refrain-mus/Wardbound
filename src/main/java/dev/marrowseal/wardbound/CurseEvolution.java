package dev.marrowseal.wardbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.UUID;

/** Mature curses and multi-curse corruptions: the hostile counterpart to Conjunctions. */
public final class CurseEvolution {
    private CurseEvolution() {}

    public enum Corruption {
        CINDERED_TONGUE("cindered_tongue", "Cindered Tongue", "Ashen Tongue + Blood Tithe",
                "Low-health blood strength is sharpened while the tongue is active, but hostile kills are more likely to shed experience."),
        RED_FROST("red_frost", "Red Frost", "Blood Tithe + Shivering Tithe",
                "Haste deepens, but low health turns the tithe's hunger harsher."),
        ASHEN_PILGRIMAGE("ashen_pilgrimage", "Ashen Pilgrimage", "Brittle Pilgrimage + Ashen Tongue",
                "Hard falls still answer with speed, but the ash keeps the weakness longer after landing.");

        public final String id, title, formula, effect;
        Corruption(String id, String title, String formula, String effect) {
            this.id = id; this.title = title; this.formula = formula; this.effect = effect;
        }
    }

    public static void tick(ServerPlayer player, LockData data, UUID id, long day) {
        if (player == null || data == null) return;
        set(player, data, id, Corruption.CINDERED_TONGUE, data.hasAshenTongue(id) && data.hasBloodTithe(id));
        set(player, data, id, Corruption.RED_FROST, data.hasBloodTithe(id) && data.hasUnique(id, "shivering_tithe"));
        set(player, data, id, Corruption.ASHEN_PILGRIMAGE, data.hasBrittlePilgrimage(id) && data.hasAshenTongue(id));

        long now = player.level().getGameTime();
        for (Corruption corruption : Corruption.values()) updateStageDiscovery(player, data, id, corruption, now);

        // Mature individual curses become discoveries even without pairing.
        if (data.hasBloodTithe(id) && data.curseStage(id, "blood_tithe", day) >= 2)
            WardHistory.discoverCurseMaturity(player, "blood_tithe", "Blood Tithe II");
        if (data.hasBrittlePilgrimage(id) && data.curseStage(id, "brittle", day) >= 2)
            WardHistory.discoverCurseMaturity(player, "brittle", "Brittle Pilgrimage II");
        if (data.hasAshenTongue(id) && data.ashenTongueStage(id, now) >= 3)
            WardHistory.discoverCurseMaturity(player, "ashen_tongue", "Ashen Tongue III");

        if (active(data, id, Corruption.RED_FROST)) {
            int stage = stage(data, id, Corruption.RED_FROST, now);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, stage >= 3 ? 2 : 1, true, false, true));
            if (player.getHealth() <= player.getMaxHealth() * 0.5f)
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, stage >= 2 ? 1 : 0, true, false, true));
        }
    }

    private static void set(ServerPlayer player, LockData data, UUID id, Corruption corruption, boolean shouldBeActive) {
        String active = "corruption_active_" + corruption.id;
        String since = "corruption_since_" + corruption.id;
        boolean before = data.hasUnique(id, active);
        if (shouldBeActive && !before) {
            data.setUnique(id, active, true);
            data.setUnique(id, "corruption_known_" + corruption.id, true);
            data.setUniqueLong(id, since, player.level().getGameTime());
            data.setUniqueInt(id, "corruption_stage_seen_" + corruption.id, 1);
            WardHistory.discoverCorruption(player, corruption);
            WardHistory.recordSpecial(player, "DANGER", "Curse corruption: " + corruption.title + " // " + corruption.formula);
            WardHud.message(player, net.minecraft.network.chat.Component.literal(
                    "CORRUPTION // " + corruption.title).withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD), false);
            dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player, "corruption_" + corruption.id,
                    "Two curses have stopped behaving as separate clauses. " + corruption.title + ". That is worse than simple addition.", 20L * 180L);
        } else if (!shouldBeActive && before) {
            data.setUnique(id, active, false);
            data.setUniqueLong(id, since, Long.MIN_VALUE);
            WardHistory.recordSpecial(player, "WARD", "Corruption dormant: " + corruption.title + ".");
        }
    }

    private static void updateStageDiscovery(ServerPlayer player, LockData data, UUID id, Corruption corruption, long now) {
        if (!active(data, id, corruption)) return;
        int stage = stage(data, id, corruption, now);
        int seen = data.uniqueInt(id, "corruption_stage_seen_" + corruption.id);
        if (stage <= seen) return;
        data.setUniqueInt(id, "corruption_stage_seen_" + corruption.id, stage);
        WardHistory.recordSpecial(player, "DANGER", "Corruption deepened: " + corruption.title + " // stage " + stage + "/3");
        dev.marrowseal.wardbound.item.CthulhuEyeItem.speakInsight(player,
                "corruption_stage_" + corruption.id + "_" + stage,
                stage == 2 ? "The paired curses are no longer merely coexisting. Their shared behaviour has stabilized."
                        : "That corruption has reached a third stable form. I would stop calling either original curse the primary one.",
                20L * 210L);
    }

    /** 1 immediately, 2 after five continuous minutes, 3 after twelve. */
    public static int stage(LockData data, UUID id, Corruption corruption, long now) {
        if (!active(data, id, corruption)) return 0;
        long since = data.uniqueLong(id, "corruption_since_" + corruption.id);
        if (since == Long.MIN_VALUE) return 1;
        long age = Math.max(0L, now - since);
        if (age >= 20L * 60L * 12L) return 3;
        if (age >= 20L * 60L * 5L) return 2;
        return 1;
    }

    public static boolean active(LockData data, UUID id, Corruption corruption) {
        return data.hasUnique(id, "corruption_active_" + corruption.id);
    }

    public static boolean known(LockData data, UUID id, Corruption corruption) {
        return data.hasUnique(id, "corruption_known_" + corruption.id);
    }

    public static int knownCount(LockData data, UUID id) {
        int count = 0;
        for (Corruption corruption : Corruption.values()) if (known(data, id, corruption)) count++;
        return count;
    }

    public static int bloodStrengthAmplifier(LockData data, UUID id, long day) {
        if (active(data, id, Corruption.CINDERED_TONGUE)) {
            long now = data.uniqueLong(id, "corruption_since_" + Corruption.CINDERED_TONGUE.id);
            int seen = data.uniqueInt(id, "corruption_stage_seen_" + Corruption.CINDERED_TONGUE.id);
            return seen >= 3 ? 2 : 1;
        }
        return data.hasBloodTithe(id) && data.curseStage(id, "blood_tithe", day) >= 2 ? 1 : 0;
    }

    public static float brittleFallMultiplier(LockData data, UUID id, long day) {
        int stage = data.curseStage(id, "brittle", day);
        return stage >= 2 ? 2.05f : stage >= 1 ? 1.90f : 1.75f;
    }

    public static float ashenXpLossChanceBonus(LockData data, UUID id) {
        if (!active(data, id, Corruption.CINDERED_TONGUE)) return 0f;
        int stage = Math.max(1, data.uniqueInt(id, "corruption_stage_seen_" + Corruption.CINDERED_TONGUE.id));
        return stage >= 3 ? 0.34f : stage == 2 ? 0.26f : 0.18f;
    }

    public static int ashenPilgrimageWeaknessTicks(LockData data, UUID id) {
        int stage = Math.max(1, data.uniqueInt(id, "corruption_stage_seen_" + Corruption.ASHEN_PILGRIMAGE.id));
        return 20 * (stage >= 3 ? 22 : stage == 2 ? 15 : 10);
    }
}
