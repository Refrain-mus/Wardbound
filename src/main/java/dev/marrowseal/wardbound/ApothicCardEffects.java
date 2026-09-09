package dev.marrowseal.wardbound;

import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Wardbound's Apothic Attributes card pool (IDs 438..627).
 *
 * All Wardbound bonuses are collapsed into one transient modifier per Apothic attribute.
 * The totals are reconstructed from LockData, which prevents stale modifiers after relog,
 * respawn, remedy use, timer expiry, or combinations of multiple Apothic cards.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ApothicCardEffects {
    private ApothicCardEffects() {}

    private static final long NONE = Long.MIN_VALUE;

    private static final UUID ARMOR_PIERCE_UUID = uuid("armor_pierce");
    private static final UUID ARMOR_SHRED_UUID = uuid("armor_shred");
    private static final UUID ARROW_DAMAGE_UUID = uuid("arrow_damage");
    private static final UUID ARROW_VELOCITY_UUID = uuid("arrow_velocity");
    private static final UUID COLD_DAMAGE_UUID = uuid("cold_damage");
    private static final UUID CRIT_CHANCE_UUID = uuid("crit_chance");
    private static final UUID CRIT_DAMAGE_UUID = uuid("crit_damage");
    private static final UUID CURRENT_HP_DAMAGE_UUID = uuid("current_hp_damage");
    private static final UUID DODGE_CHANCE_UUID = uuid("dodge_chance");
    private static final UUID DRAW_SPEED_UUID = uuid("draw_speed");
    private static final UUID EXPERIENCE_UUID = uuid("experience_gained");
    private static final UUID FIRE_DAMAGE_UUID = uuid("fire_damage");
    private static final UUID HEALING_UUID = uuid("healing_received");
    private static final UUID LIFE_STEAL_UUID = uuid("life_steal");
    private static final UUID MINING_SPEED_UUID = uuid("mining_speed");
    private static final UUID OVERHEAL_UUID = uuid("overheal");
    private static final UUID PROT_PIERCE_UUID = uuid("prot_pierce");
    private static final UUID PROT_SHRED_UUID = uuid("prot_shred");

    private static final String[] LESSER_BURDENS = {
            "apothic_rusted_edge",
            "apothic_split_nerve",
            "apothic_hollow_marrow",
            "apothic_broken_sight",
            "apothic_ashen_pick",
            "apothic_open_wound",
            "apothic_paper_skin",
            "apothic_frozen_marrow",
            "apothic_cindered_nerve",
            "apothic_greedy_pick",
            "apothic_serrated_memory",
            "apothic_hungry_quiver",
            "apothic_glass_blood",
            "apothic_lead_fingers",
            "apothic_white_hot_marrow",
            "apothic_dead_winter_nerve",
            "apothic_auditors_hunger",
            "apothic_hollow_aim",
            "apothic_borrowed_skin",
            "apothic_red_quarry"
    };

    public static boolean isApothic(ForbiddenBargain card) {
        return card != null && card.id >= 438 && card.id <= 627;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isApothic(card)) return false;
        if (ApothicThirdWaveMechanics.isJoke(card)) return ApothicThirdWaveMechanics.jokeAvailable(data, id, card);
        return switch (card) {
            case CLEAN_THE_RUST -> data.hasUnique(id, "apothic_rusted_edge");
            case MEND_THE_NERVE -> data.hasUnique(id, "apothic_split_nerve");
            case FILL_THE_MARROW -> data.hasUnique(id, "apothic_hollow_marrow");
            case RESET_THE_SIGHT -> data.hasUnique(id, "apothic_broken_sight");
            case WASH_THE_PICK -> data.hasUnique(id, "apothic_ashen_pick");
            case CLOSE_THE_WOUND -> data.hasUnique(id, "apothic_open_wound");
            case BIND_THE_SKIN -> data.hasUnique(id, "apothic_paper_skin");
            case WARM_THE_MARROW -> data.hasUnique(id, "apothic_frozen_marrow");
            case COOL_THE_NERVE -> data.hasUnique(id, "apothic_cindered_nerve");
            case BREAK_THE_GREED -> data.hasUnique(id, "apothic_greedy_pick");
            case FILE_THE_MEMORY -> data.hasUnique(id, "apothic_serrated_memory");
            case FEED_THE_QUIVER -> data.hasUnique(id, "apothic_hungry_quiver");
            case THICKEN_GLASS_BLOOD -> data.hasUnique(id, "apothic_glass_blood");
            case UNCLASP_FINGERS -> data.hasUnique(id, "apothic_lead_fingers");
            case QUENCH_WHITE_HOT_MARROW -> data.hasUnique(id, "apothic_white_hot_marrow");
            case WAKE_WINTER_NERVE -> data.hasUnique(id, "apothic_dead_winter_nerve");
            case CLOSE_THE_AUDIT -> data.hasUnique(id, "apothic_auditors_hunger");
            case CORRECT_THE_AIM -> data.hasUnique(id, "apothic_hollow_aim");
            case RETURN_THE_SKIN -> data.hasUnique(id, "apothic_borrowed_skin");
            case COOL_THE_QUARRY -> data.hasUnique(id, "apothic_red_quarry");

            case RUSTED_EDGE -> !data.hasUnique(id, "apothic_rusted_edge");
            case SPLIT_NERVE -> !data.hasUnique(id, "apothic_split_nerve");
            case HOLLOW_MARROW -> !data.hasUnique(id, "apothic_hollow_marrow");
            case BROKEN_SIGHT -> !data.hasUnique(id, "apothic_broken_sight");
            case ASHEN_PICK -> !data.hasUnique(id, "apothic_ashen_pick");
            case OPEN_WOUND_LEDGER -> !data.hasUnique(id, "apothic_open_wound");
            case PAPER_SKIN -> !data.hasUnique(id, "apothic_paper_skin");
            case FROZEN_MARROW -> !data.hasUnique(id, "apothic_frozen_marrow");
            case CINDERED_NERVE -> !data.hasUnique(id, "apothic_cindered_nerve");
            case GREEDY_PICK -> !data.hasUnique(id, "apothic_greedy_pick");
            case SERRATED_MEMORY -> !data.hasUnique(id, "apothic_serrated_memory");
            case HUNGRY_QUIVER -> !data.hasUnique(id, "apothic_hungry_quiver");
            case GLASS_BLOOD -> !data.hasUnique(id, "apothic_glass_blood");
            case LEAD_FINGERS -> !data.hasUnique(id, "apothic_lead_fingers");
            case WHITE_HOT_MARROW -> !data.hasUnique(id, "apothic_white_hot_marrow");
            case DEAD_WINTER_NERVE -> !data.hasUnique(id, "apothic_dead_winter_nerve");
            case AUDITORS_HUNGER -> !data.hasUnique(id, "apothic_auditors_hunger");
            case HOLLOW_AIM -> !data.hasUnique(id, "apothic_hollow_aim");
            case BORROWED_SKIN -> !data.hasUnique(id, "apothic_borrowed_skin");
            case RED_QUARRY -> !data.hasUnique(id, "apothic_red_quarry");
            default -> {
                if (isDeathLaw(card)) yield data.deathHeartDebt(id) < 4 && !data.hasUnique(id, lawKey(card));
                if (isPersistentLaw(card)) yield !data.hasUnique(id, lawKey(card));
                yield data.uniqueLong(id, timerKey(card)) == NONE;
            }
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isApothic(card)) return;
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        if (ApothicThirdWaveMechanics.isJoke(card)) {
            ApothicThirdWaveMechanics.applyJoke(player, data, card);
            sync(player, data, now);
            return;
        }

        switch (card) {
            case RUSTED_EDGE -> data.setUnique(id, "apothic_rusted_edge", true);
            case CLEAN_THE_RUST -> data.setUnique(id, "apothic_rusted_edge", false);
            case SPLIT_NERVE -> data.setUnique(id, "apothic_split_nerve", true);
            case MEND_THE_NERVE -> data.setUnique(id, "apothic_split_nerve", false);
            case HOLLOW_MARROW -> data.setUnique(id, "apothic_hollow_marrow", true);
            case FILL_THE_MARROW -> data.setUnique(id, "apothic_hollow_marrow", false);
            case BROKEN_SIGHT -> data.setUnique(id, "apothic_broken_sight", true);
            case RESET_THE_SIGHT -> data.setUnique(id, "apothic_broken_sight", false);
            case ASHEN_PICK -> data.setUnique(id, "apothic_ashen_pick", true);
            case WASH_THE_PICK -> data.setUnique(id, "apothic_ashen_pick", false);
            case OPEN_WOUND_LEDGER -> data.setUnique(id, "apothic_open_wound", true);
            case CLOSE_THE_WOUND -> data.setUnique(id, "apothic_open_wound", false);
            case PAPER_SKIN -> data.setUnique(id, "apothic_paper_skin", true);
            case BIND_THE_SKIN -> data.setUnique(id, "apothic_paper_skin", false);
            case FROZEN_MARROW -> data.setUnique(id, "apothic_frozen_marrow", true);
            case WARM_THE_MARROW -> data.setUnique(id, "apothic_frozen_marrow", false);
            case CINDERED_NERVE -> data.setUnique(id, "apothic_cindered_nerve", true);
            case COOL_THE_NERVE -> data.setUnique(id, "apothic_cindered_nerve", false);
            case GREEDY_PICK -> data.setUnique(id, "apothic_greedy_pick", true);
            case BREAK_THE_GREED -> data.setUnique(id, "apothic_greedy_pick", false);
            case SERRATED_MEMORY -> data.setUnique(id, "apothic_serrated_memory", true);
            case FILE_THE_MEMORY -> data.setUnique(id, "apothic_serrated_memory", false);
            case HUNGRY_QUIVER -> data.setUnique(id, "apothic_hungry_quiver", true);
            case FEED_THE_QUIVER -> data.setUnique(id, "apothic_hungry_quiver", false);
            case GLASS_BLOOD -> data.setUnique(id, "apothic_glass_blood", true);
            case THICKEN_GLASS_BLOOD -> data.setUnique(id, "apothic_glass_blood", false);
            case LEAD_FINGERS -> data.setUnique(id, "apothic_lead_fingers", true);
            case UNCLASP_FINGERS -> data.setUnique(id, "apothic_lead_fingers", false);
            case WHITE_HOT_MARROW -> data.setUnique(id, "apothic_white_hot_marrow", true);
            case QUENCH_WHITE_HOT_MARROW -> data.setUnique(id, "apothic_white_hot_marrow", false);
            case DEAD_WINTER_NERVE -> data.setUnique(id, "apothic_dead_winter_nerve", true);
            case WAKE_WINTER_NERVE -> data.setUnique(id, "apothic_dead_winter_nerve", false);
            case AUDITORS_HUNGER -> data.setUnique(id, "apothic_auditors_hunger", true);
            case CLOSE_THE_AUDIT -> data.setUnique(id, "apothic_auditors_hunger", false);
            case HOLLOW_AIM -> data.setUnique(id, "apothic_hollow_aim", true);
            case CORRECT_THE_AIM -> data.setUnique(id, "apothic_hollow_aim", false);
            case BORROWED_SKIN -> data.setUnique(id, "apothic_borrowed_skin", true);
            case RETURN_THE_SKIN -> data.setUnique(id, "apothic_borrowed_skin", false);
            case RED_QUARRY -> data.setUnique(id, "apothic_red_quarry", true);
            case COOL_THE_QUARRY -> data.setUnique(id, "apothic_red_quarry", false);
            default -> {
                if (isDeathLaw(card)) {
                    // Defense in depth: commands/debug hooks cannot bypass the four-heart Death-law cap.
                    if (data.deathHeartDebt(id) >= 4 || data.hasUnique(id, lawKey(card))) return;
                    data.setUnique(id, lawKey(card), true);
                    data.addDeathHeartDebt(id, 1);
                } else if (isPersistentLaw(card)) {
                    if (data.hasUnique(id, lawKey(card))) return;
                    data.setUnique(id, lawKey(card), true);
                } else {
                    timer(data, id, card, now, durationSeconds(card));
                }
            }
        }

        sync(player, data, now);
    }

    public static boolean hasLesserBurden(LockData data, UUID id) {
        if (data == null || id == null) return false;
        for (String key : LESSER_BURDENS) if (data.hasUnique(id, key)) return true;
        return false;
    }

    public static boolean clearOneLesserBurden(LockData data, UUID id) {
        if (data == null || id == null) return false;
        for (String key : LESSER_BURDENS) {
            if (data.hasUnique(id, key)) {
                data.setUnique(id, key, false);
                return true;
            }
        }
        return false;
    }

    /** Ordinary Absolution clears Apothic scars/curses but deliberately leaves Epic/Unique/Death laws. */
    public static void absolve(LockData data, UUID id) {
        if (data == null || id == null) return;
        for (String key : LESSER_BURDENS) data.setUnique(id, key, false);
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (data == null || id == null || !isApothic(card)) return "UNKNOWN";
        if (ApothicThirdWaveMechanics.isJoke(card)) return ApothicThirdWaveMechanics.jokeStatus(data, id, card);
        if (isRemedy(card)) return "RESOLVED ON SIGNING";
        if (isBurden(card)) return data.hasUnique(id, burdenKey(card)) ? "ACTIVE ATTRIBUTE SCAR" : "REMOVED / INACTIVE";
        if (isDeathLaw(card)) return data.hasUnique(id, lawKey(card)) ? "ACTIVE DEATH ATTRIBUTE LAW · buried heart" : "NOT YET SIGNED";
        if (isPersistentLaw(card)) return data.hasUnique(id, lawKey(card)) ? "ACTIVE ATTRIBUTE LAW" : "NOT YET SIGNED";

        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE || until <= now) return "EXPIRED / READY TO RETURN";
        long sec = Math.max(0L, (until - now) / 20L);
        return "ACTIVE APOTHIC CLAUSE · " + (sec / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60L) + " remaining";
    }

    public static String reliefText(ForbiddenBargain card) {
        if (!isApothic(card)) return "The card ends according to the condition written in its terms.";
        if (ApothicThirdWaveMechanics.isJoke(card)) return "This is a rare House joke encounter. Its visible text is literal, but intentionally incomplete; it resolves through the encounter it starts.";
        if (isRemedy(card)) return "This is a one-use remedy; its work is done when signed.";
        if (isBurden(card)) return switch (card) {
            case RUSTED_EDGE -> "Clean the Rust, Debt Unwritten, or Absolution removes this attribute scar.";
            case SPLIT_NERVE -> "Mend the Nerve, Debt Unwritten, or Absolution removes this attribute scar.";
            case HOLLOW_MARROW -> "Fill the Marrow, Debt Unwritten, or Absolution removes this attribute curse.";
            case BROKEN_SIGHT -> "Reset the Sight, Debt Unwritten, or Absolution removes this attribute scar.";
            case ASHEN_PICK -> "Wash the Pick, Debt Unwritten, or Absolution removes this attribute curse.";
            case OPEN_WOUND_LEDGER -> "Close the Wound, Debt Unwritten, or Absolution removes this attribute curse.";
            case PAPER_SKIN -> "Bind the Skin, Debt Unwritten, or Absolution removes this attribute scar.";
            case FROZEN_MARROW -> "Warm the Marrow, Debt Unwritten, or Absolution removes this attribute curse.";
            case CINDERED_NERVE -> "Cool the Nerve, Debt Unwritten, or Absolution removes this attribute scar.";
            case GREEDY_PICK -> "Break the Greed, Debt Unwritten, or Absolution removes this attribute curse.";
            case SERRATED_MEMORY -> "File the Memory, Debt Unwritten, or Absolution removes this attribute scar.";
            case HUNGRY_QUIVER -> "Feed the Quiver, Debt Unwritten, or Absolution removes this attribute curse.";
            case GLASS_BLOOD -> "Thicken the Glass Blood, Debt Unwritten, or Absolution removes this attribute curse.";
            case LEAD_FINGERS -> "Unclasp the Fingers, Debt Unwritten, or Absolution removes this attribute scar.";
            case WHITE_HOT_MARROW -> "Quench the White-Hot Marrow, Debt Unwritten, or Absolution removes this attribute curse.";
            case DEAD_WINTER_NERVE -> "Wake the Winter Nerve, Debt Unwritten, or Absolution removes this attribute scar.";
            case AUDITORS_HUNGER -> "Close the Audit, Debt Unwritten, or Absolution removes this attribute curse.";
            case HOLLOW_AIM -> "Correct the Aim, Debt Unwritten, or Absolution removes this attribute scar.";
            case BORROWED_SKIN -> "Return the Skin, Debt Unwritten, or Absolution removes this attribute curse.";
            case RED_QUARRY -> "Cool the Quarry, Debt Unwritten, or Absolution removes this attribute scar.";
            default -> "Absolution removes this lesser attribute burden.";
        };
        if (isDeathLaw(card)) return "This is a Death attribute law. Ordinary remedies do not erase it or return its buried heart.";
        if (isPersistentLaw(card)) return "This is a persistent Apothic attribute law; ordinary milk and Absolution do not remove it.";
        return "The Apothic attribute clause ends automatically when its written timer expires.";
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer() == null) return;
        if (player.tickCount % 10 != 0) return;
        LockData data = LockData.get(player.getServer());
        sync(player, data, player.level().getGameTime());
    }

    private static void sync(ServerPlayer player, LockData data, long now) {
        UUID id = player.getUUID();
        Totals t = new Totals();

        // First timed wave.
        if (active(data,id,ForbiddenBargain.RAZOR_DIVIDEND,now)) { t.armorPierce += 4; t.critChance += .08; }
        if (active(data,id,ForbiddenBargain.SPLINTERED_PLATE,now)) { t.armorShred += .15; t.currentHp += .02; }
        if (active(data,id,ForbiddenBargain.LONGBOW_TESTAMENT,now)) { t.arrowDamage += .28; t.arrowVelocity += .18; }
        if (active(data,id,ForbiddenBargain.QUICKDRAW_CLAUSE,now)) { t.drawSpeed += .35; t.arrowVelocity += .12; }
        if (active(data,id,ForbiddenBargain.CRIMSON_ODDS,now)) { t.critChance += .12; t.critDamage += .30; t.healing -= .10; }
        if (active(data,id,ForbiddenBargain.FROST_WIT,now)) { t.coldDamage += 2.5; t.dodge += .05; }
        if (active(data,id,ForbiddenBargain.CINDER_WIT,now)) { t.fireDamage += 2.5; t.critDamage += .20; }
        if (active(data,id,ForbiddenBargain.BLOOD_RETURN,now)) { t.lifeSteal += .06; t.healing += .10; }
        if (active(data,id,ForbiddenBargain.PALE_RESERVE,now)) { t.overheal += .08; t.healing += .12; }
        if (active(data,id,ForbiddenBargain.DEEP_MINERS_LEDGER,now)) { t.mining += .40; t.experience += .15; }
        if (active(data,id,ForbiddenBargain.SCHOLARS_TITHE,now)) { t.experience += .55; t.healing -= .10; }
        if (active(data,id,ForbiddenBargain.DODGERS_INK,now)) { t.dodge += .12; t.drawSpeed -= .10; }
        if (active(data,id,ForbiddenBargain.PLATEBREAKER_SCRIPT,now)) { t.armorPierce += 3; t.protPierce += 2; }
        if (active(data,id,ForbiddenBargain.SUNDERERS_NOTE,now)) { t.armorShred += .10; t.protShred += .18; }
        if (active(data,id,ForbiddenBargain.OPENING_FEE,now)) { t.currentHp += .04; t.critChance += .05; }
        if (active(data,id,ForbiddenBargain.HUNTERS_SCRIPT,now)) { t.arrowDamage += .20; t.drawSpeed += .25; }
        if (active(data,id,ForbiddenBargain.BLOODLETTERS_MARGIN,now)) { t.lifeSteal += .04; t.critChance += .08; }
        if (active(data,id,ForbiddenBargain.WARMTH_AGAINST_STEEL,now)) { t.fireDamage += 3; t.armorPierce += 2; }
        if (active(data,id,ForbiddenBargain.WINTER_AGAINST_BONE,now)) { t.coldDamage += 3; t.currentHp += .02; }
        if (active(data,id,ForbiddenBargain.SWIFT_PICK_CLAUSE,now)) { t.mining += .50; t.experience += .20; }
        if (active(data,id,ForbiddenBargain.SILVER_LEDGER,now)) { t.experience += .35; t.dodge += .06; }
        if (active(data,id,ForbiddenBargain.SECOND_HEART_ACCOUNTING,now)) { t.overheal += .10; t.lifeSteal += .025; }
        if (active(data,id,ForbiddenBargain.DOUBLED_EDGE,now)) { t.critDamage += .50; t.healing -= .15; }
        if (active(data,id,ForbiddenBargain.IMPOSSIBLE_AIM,now)) { t.arrowDamage += .35; t.critChance += .08; t.drawSpeed -= .15; }

        // Second timed wave: hybrid build clauses and explicit opportunity costs.
        if (active(data,id,ForbiddenBargain.GLASS_RAZOR,now)) { t.critChance += .10; t.critDamage += .35; t.armorPierce += 3; t.healing -= .12; }
        if (active(data,id,ForbiddenBargain.BALLISTIC_PRAYER,now)) { t.arrowDamage += .24; t.arrowVelocity += .22; t.drawSpeed += .20; }
        if (active(data,id,ForbiddenBargain.BLACK_ICE_LEDGER,now)) { t.coldDamage += 3; t.armorShred += .12; }
        if (active(data,id,ForbiddenBargain.ASHEN_VOLLEY,now)) { t.fireDamage += 2.5; t.arrowDamage += .22; }
        if (active(data,id,ForbiddenBargain.SURGEONS_MARGIN,now)) { t.lifeSteal += .05; t.currentHp += .03; t.healing -= .15; }
        if (active(data,id,ForbiddenBargain.GHOST_STEP_LEDGER,now)) { t.dodge += .10; t.drawSpeed += .20; }
        if (active(data,id,ForbiddenBargain.PLUNDERED_LESSON,now)) { t.experience += .45; t.mining += .30; t.healing -= .08; }
        if (active(data,id,ForbiddenBargain.IRON_ALGEBRA,now)) { t.armorPierce += 4; t.protPierce += 3; }
        if (active(data,id,ForbiddenBargain.BREAKERS_INTEREST,now)) { t.armorShred += .14; t.protShred += .20; }
        if (active(data,id,ForbiddenBargain.RED_RESERVOIR,now)) { t.lifeSteal += .07; t.overheal += .11; }
        if (active(data,id,ForbiddenBargain.COLD_ACCOUNTING,now)) { t.coldDamage += 3.5; t.critDamage += .25; }
        if (active(data,id,ForbiddenBargain.CINDER_ACCOUNTING,now)) { t.fireDamage += 3.5; t.critChance += .09; }
        if (active(data,id,ForbiddenBargain.HIGH_VELOCITY_CLAUSE,now)) { t.arrowVelocity += .30; t.arrowDamage += .18; t.drawSpeed -= .08; }
        if (active(data,id,ForbiddenBargain.SNIPERS_DEBT,now)) { t.arrowDamage += .38; t.currentHp += .03; t.drawSpeed -= .18; }
        if (active(data,id,ForbiddenBargain.QUICK_HAND_TAX,now)) { t.drawSpeed += .45; t.critChance += .07; t.arrowDamage -= .10; }
        if (active(data,id,ForbiddenBargain.PALE_REFLEX,now)) { t.dodge += .09; t.healing += .15; }
        if (active(data,id,ForbiddenBargain.QUARRY_FEVER,now)) { t.mining += .65; t.experience += .25; t.dodge -= .06; }
        if (active(data,id,ForbiddenBargain.EDUCATED_VIOLENCE,now)) { t.experience += .35; t.critChance += .10; }
        if (active(data,id,ForbiddenBargain.MERCYS_INTEREST,now)) { t.healing += .20; t.overheal += .10; t.currentHp -= .015; }
        if (active(data,id,ForbiddenBargain.HEMORRHAGE_CLAUSE,now)) { t.currentHp += .05; t.critDamage += .35; t.healing -= .18; }
        if (active(data,id,ForbiddenBargain.DEFLECTORS_WAGE,now)) { t.dodge += .11; t.protShred += .12; }
        if (active(data,id,ForbiddenBargain.PICK_AND_BLADE,now)) { t.mining += .40; t.armorPierce += 3; }
        if (active(data,id,ForbiddenBargain.PREDATORS_ARITHMETIC,now)) { t.currentHp += .04; t.arrowDamage += .18; }
        if (active(data,id,ForbiddenBargain.HOUSE_ACCELERANT,now)) { t.drawSpeed += .20; t.critChance += .08; t.mining += .25; t.healing -= .12; }

        // Reversible scars/curses. Every entry has a named Remedy and is visible to Absolution.
        if (data.hasUnique(id,"apothic_rusted_edge")) { t.armorShred += .12; t.healing -= .20; }
        if (data.hasUnique(id,"apothic_split_nerve")) { t.critChance += .10; t.drawSpeed -= .25; }
        if (data.hasUnique(id,"apothic_hollow_marrow")) { t.lifeSteal += .08; t.healing -= .30; }
        if (data.hasUnique(id,"apothic_broken_sight")) { t.arrowDamage += .35; t.drawSpeed -= .30; }
        if (data.hasUnique(id,"apothic_ashen_pick")) { t.mining += .50; t.experience -= .30; }
        if (data.hasUnique(id,"apothic_open_wound")) { t.currentHp += .05; t.healing -= .25; }
        if (data.hasUnique(id,"apothic_paper_skin")) { t.dodge += .16; t.healing -= .35; }
        if (data.hasUnique(id,"apothic_frozen_marrow")) { t.coldDamage += 4; t.drawSpeed -= .20; t.healing -= .15; }
        if (data.hasUnique(id,"apothic_cindered_nerve")) { t.fireDamage += 4; t.critChance += .08; t.dodge -= .10; }
        if (data.hasUnique(id,"apothic_greedy_pick")) { t.mining += .70; t.experience += .35; t.dodge -= .08; t.healing -= .15; }
        if (data.hasUnique(id,"apothic_serrated_memory")) { t.armorShred += .20; t.protShred += .12; t.critDamage -= .20; }
        if (data.hasUnique(id,"apothic_hungry_quiver")) { t.arrowDamage += .45; t.arrowVelocity += .25; t.drawSpeed -= .30; t.healing -= .15; }

        // First persistent doctrine wave.
        if (law(data,id,ForbiddenBargain.RAZOR_DOCTRINE)) { t.armorPierce += 5; t.critChance += .08; }
        if (law(data,id,ForbiddenBargain.SUNDERED_CREED)) { t.armorShred += .18; t.protShred += .12; }
        if (law(data,id,ForbiddenBargain.GOLDEN_BALLISTICS)) { t.arrowDamage += .30; t.drawSpeed += .25; t.arrowVelocity += .12; }
        if (law(data,id,ForbiddenBargain.SECOND_STRING)) { t.critChance += .10; t.critDamage += .35; }
        if (law(data,id,ForbiddenBargain.WINTER_TONGUE)) { t.coldDamage += 3; t.dodge += .06; }
        if (law(data,id,ForbiddenBargain.CINDER_TONGUE)) { t.fireDamage += 3; t.critChance += .08; }
        if (law(data,id,ForbiddenBargain.RED_CATECHISM)) { t.lifeSteal += .05; t.overheal += .08; }
        if (law(data,id,ForbiddenBargain.LEARNED_GRAVE)) { t.experience += .40; t.healing += .10; }
        if (law(data,id,ForbiddenBargain.STONES_MEMORY)) { t.mining += .45; t.armorPierce += 2; }
        if (law(data,id,ForbiddenBargain.PERFECT_DEFLECTION)) { t.dodge += .14; t.healing += .12; }
        if (law(data,id,ForbiddenBargain.VEILED_PROTECTION)) { t.protPierce += 3; t.protShred += .15; }
        if (law(data,id,ForbiddenBargain.HUNGER_FOR_THE_LIVING)) { t.currentHp += .025; t.lifeSteal += .04; }

        // Second persistent doctrine wave.
        if (law(data,id,ForbiddenBargain.BLACK_LANCET)) { t.armorPierce += 4; t.currentHp += .03; }
        if (law(data,id,ForbiddenBargain.MERCILESS_GEOMETRY)) { t.critChance += .10; t.critDamage += .30; t.protPierce += 2; }
        if (law(data,id,ForbiddenBargain.WINTER_ENGINE)) { t.coldDamage += 4; t.drawSpeed += .15; t.dodge += .05; }
        if (law(data,id,ForbiddenBargain.CINDER_ENGINE)) { t.fireDamage += 4; t.arrowDamage += .22; }
        if (law(data,id,ForbiddenBargain.VAMPIRE_LEDGER)) { t.lifeSteal += .07; t.overheal += .10; t.healing += .08; }
        if (law(data,id,ForbiddenBargain.MASTER_QUARRY)) { t.mining += .55; t.experience += .30; t.armorPierce += 2; }
        if (law(data,id,ForbiddenBargain.ABSENT_TARGET)) { t.dodge += .16; t.critChance += .07; }
        if (law(data,id,ForbiddenBargain.PERFECT_TRAJECTORY)) { t.arrowDamage += .36; t.arrowVelocity += .20; t.drawSpeed += .20; }
        if (law(data,id,ForbiddenBargain.PROFANED_AEGIS)) { t.armorShred += .16; t.protShred += .20; t.protPierce += 2; }
        if (law(data,id,ForbiddenBargain.RED_INSTRUCTION)) { t.currentHp += .035; t.critDamage += .40; }
        if (law(data,id,ForbiddenBargain.PATIENT_HAND)) { t.drawSpeed += .25; t.critChance += .07; t.healing += .08; }
        if (law(data,id,ForbiddenBargain.HOUSE_COMPOUND)) { t.armorPierce += 2; t.critChance += .06; t.dodge += .05; t.experience += .25; }

        // First Death-law wave.
        if (law(data,id,ForbiddenBargain.RED_LAW)) { t.critChance += .18; t.critDamage += .65; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.NO_ARMOR_IS_SACRED)) { t.armorPierce += 8; t.armorShred += .25; t.healing -= .15; }
        if (law(data,id,ForbiddenBargain.ARROW_OF_LAST_ACCOUNT)) { t.arrowDamage += .55; t.arrowVelocity += .40; t.drawSpeed += .35; t.healing -= .15; }
        if (law(data,id,ForbiddenBargain.FROZEN_VERDICT)) { t.coldDamage += 6; t.currentHp += .035; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.BURNING_VERDICT)) { t.fireDamage += 6; t.currentHp += .035; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.CRIMSON_USURY)) { t.lifeSteal += .10; t.overheal += .12; t.healing -= .15; }
        if (law(data,id,ForbiddenBargain.HOUSE_TAKES_EXPERIENCE)) { t.experience += 1.00; t.mining -= .25; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.EMPTY_PLATE_DOCTRINE)) { t.dodge += .20; t.critChance += .12; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.BONEBREAKER_COVENANT)) { t.protPierce += 6; t.protShred += .30; t.armorPierce += 4; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.BLOOD_IN_EXCESS)) { t.overheal += .15; t.lifeSteal += .08; t.arrowDamage -= .25; }
        if (law(data,id,ForbiddenBargain.EXECUTIONER_OF_FULL_HEALTH)) { t.currentHp += .07; t.critChance += .12; t.critDamage += .45; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.HOUSE_HAS_NUMBERS)) { t.critChance += .10; t.arrowDamage += .25; t.mining += .25; t.experience += .30; t.dodge += .06; t.healing -= .35; }

        // Second Death-law wave. The four-buried-heart global cap still governs availability.
        if (law(data,id,ForbiddenBargain.GLASS_SOVEREIGN)) { t.critChance += .25; t.critDamage += 1.00; t.healing -= .45; }
        if (law(data,id,ForbiddenBargain.TEETH_BEHIND_STEEL)) { t.armorPierce += 10; t.protPierce += 5; t.currentHp += .04; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.TERMINAL_BALLISTICS)) { t.arrowDamage += .75; t.arrowVelocity += .50; t.drawSpeed += .50; t.dodge -= .10; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.WINTER_OWNS_BLOOD)) { t.coldDamage += 8; t.lifeSteal += .05; t.currentHp += .04; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.ASH_OWNS_BREATH)) { t.fireDamage += 8; t.critChance += .12; t.currentHp += .04; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.RED_RESERVOIR_LAW)) { t.lifeSteal += .12; t.overheal += .20; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.FINAL_QUARRY)) { t.mining += 1.00; t.experience += .75; t.armorPierce += 5; t.dodge -= .10; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.UNTOUCHABLE_DEBT)) { t.dodge += .25; t.drawSpeed += .25; t.healing -= .40; }
        if (law(data,id,ForbiddenBargain.SHREDDERS_CROWN)) { t.armorShred += .35; t.protShred += .35; t.critChance += .10; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.OPENING_EXECUTION)) { t.currentHp += .10; t.critDamage += .50; t.healing -= .35; }
        if (law(data,id,ForbiddenBargain.LAST_QUIVER)) { t.arrowDamage += .65; t.critChance += .15; t.critDamage += .40; t.drawSpeed -= .15; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.HOUSE_REWRITES_BODY)) { t.armorPierce += 4; t.armorShred += .12; t.protShred += .12; t.critChance += .10; t.dodge += .08; t.lifeSteal += .05; t.experience += .30; t.healing -= .45; }

        // Third timed wave: base Apothic clauses; situational reactions live in ApothicThirdWaveMechanics.
        if (active(data,id,ForbiddenBargain.KILLING_MOMENTUM,now)) { t.critChance += .08; t.critDamage += .20; t.lifeSteal += .04; }
        if (active(data,id,ForbiddenBargain.FIRST_CUT_CLAUSE,now)) { t.currentHp += .04; t.armorPierce += 2; }
        if (active(data,id,ForbiddenBargain.LAST_BREATH_MARGIN,now)) { t.dodge += .12; t.lifeSteal += .06; t.healing -= .20; }
        if (active(data,id,ForbiddenBargain.STILL_BALLISTICS,now)) { t.arrowDamage += .32; t.arrowVelocity += .20; t.drawSpeed += .15; }
        if (active(data,id,ForbiddenBargain.MOVING_TARGET_CLAUSE,now)) { t.dodge += .10; t.arrowVelocity += .25; }
        if (active(data,id,ForbiddenBargain.QUARRY_TEMPER,now)) { t.mining += .55; t.critChance += .08; }
        if (active(data,id,ForbiddenBargain.FURNACE_ARITHMETIC,now)) { t.fireDamage += 4; t.lifeSteal += .05; t.healing -= .10; }
        if (active(data,id,ForbiddenBargain.WINTER_PULSE,now)) { t.coldDamage += 4; t.dodge += .08; }
        if (active(data,id,ForbiddenBargain.EMPTY_STOMACH_DOCTRINE,now)) { t.currentHp += .05; t.critDamage += .20; t.healing -= .15; }
        if (active(data,id,ForbiddenBargain.FULL_STOMACH_DIVIDEND,now)) { t.healing += .20; t.overheal += .12; t.experience += .25; }
        if (active(data,id,ForbiddenBargain.MOONSHOT_RECEIPT,now)) { t.arrowDamage += .35; t.arrowVelocity += .30; }
        if (active(data,id,ForbiddenBargain.SUNBURN_LEDGER,now)) { t.fireDamage += 4; t.armorShred += .16; }
        if (active(data,id,ForbiddenBargain.DUELISTS_EXCEPTION,now)) { t.critChance += .10; t.critDamage += .30; t.dodge += .08; }
        if (active(data,id,ForbiddenBargain.MOB_INTEREST,now)) { t.armorShred += .18; t.protShred += .12; t.lifeSteal += .05; }
        if (active(data,id,ForbiddenBargain.BROKEN_SHIELD_PREMIUM,now)) { t.armorPierce += 5; t.critChance += .12; }
        if (active(data,id,ForbiddenBargain.HEAVY_POCKETS_CLAUSE,now)) { t.mining += .45; t.experience += .40; }
        if (active(data,id,ForbiddenBargain.CLEAN_HANDS_CLAUSE,now)) { t.dodge += .16; t.critChance += .12; t.healing -= .20; }
        if (active(data,id,ForbiddenBargain.PLATED_DEBT,now)) { t.armorPierce += 5; t.armorShred += .15; t.protPierce += 3; t.drawSpeed -= .15; }
        if (active(data,id,ForbiddenBargain.STILL_BALLISTICS,now) && data.uniqueLong(id,"third_still_focus_until") > now) { t.arrowDamage += .18; t.drawSpeed += .15; t.critChance += .05; }
        if (active(data,id,ForbiddenBargain.FURNACE_ARITHMETIC,now) && data.uniqueLong(id,"third_furnace_hot_until") > now) { t.fireDamage += 3; t.lifeSteal += .025; }
        if (active(data,id,ForbiddenBargain.LAST_BREATH_MARGIN,now) && player.getHealth() <= player.getMaxHealth() * .25f) { t.critChance += .10; t.critDamage += .20; }
        if (active(data,id,ForbiddenBargain.MOONSHOT_RECEIPT,now)) { long day=Math.floorMod(player.level().getDayTime(),24000L); if(day>=13000L && day<23000L){t.critChance += .08; t.drawSpeed += .10;} }
        if (active(data,id,ForbiddenBargain.EMPTY_STOMACH_DOCTRINE,now) && player.getFoodData().getFoodLevel() <= 6) { t.critChance += .08; }
        if (active(data,id,ForbiddenBargain.FULL_STOMACH_DIVIDEND,now) && player.getFoodData().getFoodLevel() >= 20) { t.overheal += .05; }

        // Third reversible body bargains.
        if (data.hasUnique(id,"apothic_glass_blood")) { t.critChance += .15; t.critDamage += .60; t.healing -= .35; }
        if (data.hasUnique(id,"apothic_lead_fingers")) { t.armorPierce += 6; t.drawSpeed -= .35; }
        if (data.hasUnique(id,"apothic_white_hot_marrow")) { t.fireDamage += 6; t.dodge -= .12; t.healing -= .15; }
        if (data.hasUnique(id,"apothic_dead_winter_nerve")) { t.coldDamage += 6; t.dodge += .10; t.drawSpeed -= .20; }
        if (data.hasUnique(id,"apothic_auditors_hunger")) { t.experience += .70; t.currentHp += .05; t.healing -= .25; }
        if (data.hasUnique(id,"apothic_hollow_aim")) { t.arrowDamage += .50; t.currentHp += .04; t.drawSpeed -= .30; }
        if (data.hasUnique(id,"apothic_borrowed_skin")) { t.dodge += .20; t.healing -= .20; }
        if (data.hasUnique(id,"apothic_red_quarry")) { t.mining += 1.00; t.experience += .50; }

        // Third persistent law wave.
        if (law(data,id,ForbiddenBargain.BLOODHOUND_CALCULUS)) { t.critChance += .06; t.critDamage += .25; }
        if (law(data,id,ForbiddenBargain.HUNTERS_RHYTHM)) { t.arrowDamage += .30; t.drawSpeed += .20; }
        if (law(data,id,ForbiddenBargain.EXECUTIONER_CLOCK)) { t.currentHp += .04; t.armorPierce += 4; }
        if (law(data,id,ForbiddenBargain.REDIRECTION_LAW)) { t.dodge += .12; t.healing += .10; }
        if (law(data,id,ForbiddenBargain.GLASS_CANNON_ARCHIVE)) { t.critChance += .15; t.critDamage += .55; t.healing -= .20; }
        if (law(data,id,ForbiddenBargain.QUARRY_COMMUNION)) { t.mining += .60; t.experience += .30; }
        if (law(data,id,ForbiddenBargain.EMBER_DEBT_COLLECTOR)) { t.fireDamage += 4; t.overheal += .06; }
        if (law(data,id,ForbiddenBargain.RIME_COLLECTOR)) { t.coldDamage += 4; t.dodge += .06; }
        if (law(data,id,ForbiddenBargain.LONE_PREDATOR)) { t.critChance += .10; t.currentHp += .04; }
        if (law(data,id,ForbiddenBargain.CROWD_AUDITOR)) { t.dodge += .12; t.protShred += .18; }
        if (law(data,id,ForbiddenBargain.FULL_QUIVER_LAW)) { t.arrowDamage += .35; t.arrowVelocity += .25; t.drawSpeed += .20; }
        if (law(data,id,ForbiddenBargain.VULTURES_MARGIN)) { t.lifeSteal += .04; t.experience += .20; }
        if (law(data,id,ForbiddenBargain.CRITICAL_LOTTERY)) { t.critChance += .05; t.critDamage += .20; }
        if (law(data,id,ForbiddenBargain.ECHO_CHAMBER)) { t.armorPierce += 3; t.critDamage += .20; }
        if (law(data,id,ForbiddenBargain.HOUSE_FAVORITE_SEVEN)) { t.critChance += .07; t.dodge += .07; }
        if (law(data,id,ForbiddenBargain.LAST_ARROW_IN_QUIVER)) { t.arrowDamage += .50; t.critDamage += .30; }
        if (law(data,id,ForbiddenBargain.MERCY_AFTER_MURDER)) { t.lifeSteal += .05; t.healing += .15; }
        if (law(data,id,ForbiddenBargain.REVOLVING_DOOR)) { t.dodge += .08; t.drawSpeed += .15; }
        if (law(data,id,ForbiddenBargain.BLOODHOUND_CALCULUS) && data.uniqueLong(id,"third_bloodhound_surge") > now) { t.critChance += .08; t.critDamage += .25; }
        if (law(data,id,ForbiddenBargain.HUNTERS_RHYTHM) && data.uniqueLong(id,"third_hunter_rhythm") > now) { t.arrowDamage += .20; t.drawSpeed += .15; t.arrowVelocity += .10; }

        // Third Death-law wave.
        if (law(data,id,ForbiddenBargain.REDLINE_ANATOMY)) { t.critChance += .20; t.critDamage += .80; t.lifeSteal += .10; t.healing -= .40; }
        if (law(data,id,ForbiddenBargain.PERFECT_MURDER_GEOMETRY)) { t.armorPierce += 10; t.armorShred += .35; t.currentHp += .08; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.METEOR_QUIVER)) { t.arrowDamage += .80; t.arrowVelocity += .45; t.critChance += .20; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.HOUSE_BLOOD_BANK)) { t.lifeSteal += .15; t.overheal += .25; t.healing += .10; }
        if (law(data,id,ForbiddenBargain.IMPOSSIBLE_WINTER)) { t.coldDamage += 10; t.currentHp += .06; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.CREMATION_CLAUSE)) { t.fireDamage += 10; t.armorShred += .20; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.QUARRY_OF_FLESH)) { t.mining += 1.00; t.experience += 1.00; t.armorPierce += 6; t.healing -= .25; }
        if (law(data,id,ForbiddenBargain.LAST_ACCOUNT)) { t.critChance += .12; t.critDamage += .45; t.currentHp += .05; t.healing -= .30; }
        if (law(data,id,ForbiddenBargain.REDLINE_ANATOMY) && player.getHealth() <= player.getMaxHealth() * .30f) { t.critChance += .12; t.dodge += .08; t.lifeSteal += .04; }
        if (law(data,id,ForbiddenBargain.HOUSE_BLOOD_BANK) && player.getAbsorptionAmount() >= 8.0f) { t.critChance += .08; t.critDamage += .20; }

        sync(player, ALObjects.Attributes.ARMOR_PIERCE.get(), ARMOR_PIERCE_UUID, "Wardbound Apothic: Armor Pierce", t.armorPierce);
        sync(player, ALObjects.Attributes.ARMOR_SHRED.get(), ARMOR_SHRED_UUID, "Wardbound Apothic: Armor Shred", t.armorShred);
        sync(player, ALObjects.Attributes.ARROW_DAMAGE.get(), ARROW_DAMAGE_UUID, "Wardbound Apothic: Arrow Damage", t.arrowDamage);
        sync(player, ALObjects.Attributes.ARROW_VELOCITY.get(), ARROW_VELOCITY_UUID, "Wardbound Apothic: Arrow Velocity", t.arrowVelocity);
        sync(player, ALObjects.Attributes.COLD_DAMAGE.get(), COLD_DAMAGE_UUID, "Wardbound Apothic: Cold Damage", t.coldDamage);
        sync(player, ALObjects.Attributes.CRIT_CHANCE.get(), CRIT_CHANCE_UUID, "Wardbound Apothic: Crit Chance", t.critChance);
        sync(player, ALObjects.Attributes.CRIT_DAMAGE.get(), CRIT_DAMAGE_UUID, "Wardbound Apothic: Crit Damage", t.critDamage);
        sync(player, ALObjects.Attributes.CURRENT_HP_DAMAGE.get(), CURRENT_HP_DAMAGE_UUID, "Wardbound Apothic: Current HP Damage", t.currentHp);
        sync(player, ALObjects.Attributes.DODGE_CHANCE.get(), DODGE_CHANCE_UUID, "Wardbound Apothic: Dodge", t.dodge);
        sync(player, ALObjects.Attributes.DRAW_SPEED.get(), DRAW_SPEED_UUID, "Wardbound Apothic: Draw Speed", t.drawSpeed);
        sync(player, ALObjects.Attributes.EXPERIENCE_GAINED.get(), EXPERIENCE_UUID, "Wardbound Apothic: Experience", t.experience);
        sync(player, ALObjects.Attributes.FIRE_DAMAGE.get(), FIRE_DAMAGE_UUID, "Wardbound Apothic: Fire Damage", t.fireDamage);
        sync(player, ALObjects.Attributes.HEALING_RECEIVED.get(), HEALING_UUID, "Wardbound Apothic: Healing", t.healing);
        sync(player, ALObjects.Attributes.LIFE_STEAL.get(), LIFE_STEAL_UUID, "Wardbound Apothic: Life Steal", t.lifeSteal);
        sync(player, ALObjects.Attributes.MINING_SPEED.get(), MINING_SPEED_UUID, "Wardbound Apothic: Mining Speed", t.mining);
        sync(player, ALObjects.Attributes.OVERHEAL.get(), OVERHEAL_UUID, "Wardbound Apothic: Overheal", t.overheal);
        sync(player, ALObjects.Attributes.PROT_PIERCE.get(), PROT_PIERCE_UUID, "Wardbound Apothic: Protection Pierce", t.protPierce);
        sync(player, ALObjects.Attributes.PROT_SHRED.get(), PROT_SHRED_UUID, "Wardbound Apothic: Protection Shred", t.protShred);
    }

    private static void sync(ServerPlayer player, Attribute attribute, UUID uuid, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier old = instance.getModifier(uuid);
        if (Math.abs(amount) < 0.000001D) {
            if (old != null) instance.removeModifier(old);
            return;
        }
        if (old != null && Math.abs(old.getAmount() - amount) < 0.000001D) return;
        if (old != null) instance.removeModifier(old);
        instance.addTransientModifier(new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.ADDITION));
    }

    private static boolean active(LockData data, UUID id, ForbiddenBargain card, long now) {
        long until = data.uniqueLong(id, timerKey(card));
        if (until == NONE) return false;
        if (until > now) return true;
        data.setUniqueLong(id, timerKey(card), NONE);
        return false;
    }

    private static boolean law(LockData data, UUID id, ForbiddenBargain card) {
        return data.hasUnique(id, lawKey(card));
    }

    private static boolean isRemedy(ForbiddenBargain card) {
        return switch (card) {
            case CLEAN_THE_RUST, MEND_THE_NERVE, FILL_THE_MARROW, RESET_THE_SIGHT, WASH_THE_PICK, CLOSE_THE_WOUND,
                    BIND_THE_SKIN, WARM_THE_MARROW, COOL_THE_NERVE, BREAK_THE_GREED, FILE_THE_MEMORY, FEED_THE_QUIVER,
                    THICKEN_GLASS_BLOOD, UNCLASP_FINGERS, QUENCH_WHITE_HOT_MARROW, WAKE_WINTER_NERVE, CLOSE_THE_AUDIT,
                    CORRECT_THE_AIM, RETURN_THE_SKIN, COOL_THE_QUARRY -> true;
            default -> false;
        };
    }

    private static boolean isBurden(ForbiddenBargain card) {
        return switch (card) {
            case RUSTED_EDGE, SPLIT_NERVE, HOLLOW_MARROW, BROKEN_SIGHT, ASHEN_PICK, OPEN_WOUND_LEDGER,
                    PAPER_SKIN, FROZEN_MARROW, CINDERED_NERVE, GREEDY_PICK, SERRATED_MEMORY, HUNGRY_QUIVER,
                    GLASS_BLOOD, LEAD_FINGERS, WHITE_HOT_MARROW, DEAD_WINTER_NERVE, AUDITORS_HUNGER, HOLLOW_AIM, BORROWED_SKIN, RED_QUARRY -> true;
            default -> false;
        };
    }

    private static boolean isPersistentLaw(ForbiddenBargain card) {
        return switch (card) {
            case RAZOR_DOCTRINE, SUNDERED_CREED, GOLDEN_BALLISTICS, SECOND_STRING, WINTER_TONGUE, CINDER_TONGUE,
                    RED_CATECHISM, LEARNED_GRAVE, STONES_MEMORY, PERFECT_DEFLECTION, VEILED_PROTECTION, HUNGER_FOR_THE_LIVING,
                    BLACK_LANCET, MERCILESS_GEOMETRY, WINTER_ENGINE, CINDER_ENGINE, VAMPIRE_LEDGER, MASTER_QUARRY,
                    ABSENT_TARGET, PERFECT_TRAJECTORY, PROFANED_AEGIS, RED_INSTRUCTION, PATIENT_HAND, HOUSE_COMPOUND,
                    BLOODHOUND_CALCULUS, HUNTERS_RHYTHM, EXECUTIONER_CLOCK, REDIRECTION_LAW, GLASS_CANNON_ARCHIVE, QUARRY_COMMUNION,
                    EMBER_DEBT_COLLECTOR, RIME_COLLECTOR, LONE_PREDATOR, CROWD_AUDITOR, FULL_QUIVER_LAW, VULTURES_MARGIN,
                    CRITICAL_LOTTERY, ECHO_CHAMBER, HOUSE_FAVORITE_SEVEN, LAST_ARROW_IN_QUIVER, MERCY_AFTER_MURDER, REVOLVING_DOOR -> true;
            default -> false;
        };
    }

    private static boolean isDeathLaw(ForbiddenBargain card) {
        if (card == null || card.kind != ForbiddenBargain.Kind.DEATH) return false;
        return isApothic(card);
    }

    private static String burdenKey(ForbiddenBargain card) {
        return switch (card) {
            case RUSTED_EDGE -> "apothic_rusted_edge";
            case SPLIT_NERVE -> "apothic_split_nerve";
            case HOLLOW_MARROW -> "apothic_hollow_marrow";
            case BROKEN_SIGHT -> "apothic_broken_sight";
            case ASHEN_PICK -> "apothic_ashen_pick";
            case OPEN_WOUND_LEDGER -> "apothic_open_wound";
            case PAPER_SKIN -> "apothic_paper_skin";
            case FROZEN_MARROW -> "apothic_frozen_marrow";
            case CINDERED_NERVE -> "apothic_cindered_nerve";
            case GREEDY_PICK -> "apothic_greedy_pick";
            case SERRATED_MEMORY -> "apothic_serrated_memory";
            case HUNGRY_QUIVER -> "apothic_hungry_quiver";
            case GLASS_BLOOD -> "apothic_glass_blood";
            case LEAD_FINGERS -> "apothic_lead_fingers";
            case WHITE_HOT_MARROW -> "apothic_white_hot_marrow";
            case DEAD_WINTER_NERVE -> "apothic_dead_winter_nerve";
            case AUDITORS_HUNGER -> "apothic_auditors_hunger";
            case HOLLOW_AIM -> "apothic_hollow_aim";
            case BORROWED_SKIN -> "apothic_borrowed_skin";
            case RED_QUARRY -> "apothic_red_quarry";
            default -> "apothic_none";
        };
    }

    private static long durationSeconds(ForbiddenBargain card) {
        return switch (card) {
            case RAZOR_DIVIDEND, SPLINTERED_PLATE, CRIMSON_ODDS, BLOOD_RETURN, OPENING_FEE, DOUBLED_EDGE,
                    GLASS_RAZOR, SURGEONS_MARGIN, SNIPERS_DEBT, HEMORRHAGE_CLAUSE, PREDATORS_ARITHMETIC, HOUSE_ACCELERANT,
                    KILLING_MOMENTUM, FIRST_CUT_CLAUSE, LAST_BREATH_MARGIN -> 90L;
            case DEEP_MINERS_LEDGER, SILVER_LEDGER, PLUNDERED_LESSON, QUARRY_FEVER, QUARRY_TEMPER, HEAVY_POCKETS_CLAUSE -> 180L;
            default -> 120L;
        };
    }

    private static void timer(LockData data, UUID id, ForbiddenBargain card, long now, long seconds) {
        data.setUniqueLong(id, timerKey(card), now + Math.max(1L, seconds) * 20L);
    }

    private static String timerKey(ForbiddenBargain card) { return "apothic_timer_" + card.id; }
    private static String lawKey(ForbiddenBargain card) { return "apothic_law_" + card.id; }

    private static UUID uuid(String suffix) {
        return UUID.nameUUIDFromBytes(("wardbound:apothic:" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private static final class Totals {
        double armorPierce;
        double armorShred;
        double arrowDamage;
        double arrowVelocity;
        double coldDamage;
        double critChance;
        double critDamage;
        double currentHp;
        double dodge;
        double drawSpeed;
        double experience;
        double fireDamage;
        double healing;
        double lifeSteal;
        double mining;
        double overheal;
        double protPierce;
        double protShred;
    }
}
