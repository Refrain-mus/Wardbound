package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** Runtime for cards 408..437: ward-performance clauses plus reactive world laws. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class FifthWaveCardEffects {
    private FifthWaveCardEffects() {}

    private static final int FIRST = 408;
    private static final int LAST = 437;

    private static final String CLEAN_INTEREST = "fifth_clean_interest";
    private static final String SECOND_ATTEMPT = "fifth_second_attempt";
    private static final String THREE_CLEAN = "fifth_three_clean_lines";
    private static final String PERFECT_ORRERY = "fifth_perfect_orrery";
    private static final String PERFECT_PROCESSION = "fifth_perfect_procession";
    private static final String BLACK_STUDY = "fifth_black_study";
    private static final String ASHEN_REBUTTAL = "fifth_ashen_rebuttal";
    private static final String SEVEN_PACES = "fifth_seven_paces";
    private static final String STILL_WITNESS = "fifth_still_witness";
    private static final String HOLLOW_STEP = "fifth_hollow_step";
    private static final String RED_WAKE = "fifth_red_wake";
    private static final String IRON_AFTERTASTE = "fifth_iron_aftertaste";
    private static final String TIMBER_ECHO = "fifth_timber_echo";
    private static final String RAIN_WRIT = "fifth_rain_writ";
    private static final String CENSUS_ONE = "fifth_census_one";
    private static final String EMPTY_HAND = "fifth_empty_hand";
    private static final String FULL_HAND = "fifth_full_hand";
    private static final String WOUND_CLOCK = "fifth_wound_clock";
    private static final String HUNGER_ORDER = "fifth_hunger_order";
    private static final String GRAVE_RECEIPT = "fifth_grave_receipt";
    private static final String FINAL_FOOTNOTE = "fifth_final_footnote";
    private static final String MOON_ARCHIVE = "fifth_moon_archive";
    private static final String SIXTH_WITNESS = "fifth_sixth_witness";

    public record WardTerms(float loot, int lives, int mask, int seconds) {
        public static final WardTerms EMPTY = new WardTerms(0f, 0, 0, 0);
    }

    public static boolean isFifthWave(ForbiddenBargain card) {
        return card != null && card.id >= FIRST && card.id <= LAST;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isFifthWave(card)) return false;
        return switch (card) {
            case CLEAN_INTEREST -> !data.hasUnique(id, CLEAN_INTEREST);
            case SECOND_ATTEMPT -> !data.hasUnique(id, SECOND_ATTEMPT);
            case THREE_CLEAN_LINES -> !data.hasUnique(id, THREE_CLEAN);
            case HURRIED_OATH -> data.uniqueInt(id, "fifth_hurried_charges") <= 0;
            case WHITE_INK -> data.uniqueInt(id, "fifth_white_ink_charges") <= 0;
            case BOUND_TESTIMONY -> data.uniqueInt(id, "fifth_bound_testimony_charges") <= 0;
            case DOUBLE_MARGIN -> data.uniqueInt(id, "fifth_double_margin_charges") <= 0;
            case PERFECT_ORRERY -> !data.hasUnique(id, PERFECT_ORRERY);
            case PERFECT_PROCESSION -> !data.hasUnique(id, PERFECT_PROCESSION);
            case BLACK_STUDY -> !data.hasUnique(id, BLACK_STUDY);
            case CLOSE_THE_BOOK -> data.hasUnique(id, BLACK_STUDY);
            case ASHEN_REBUTTAL -> !data.hasUnique(id, ASHEN_REBUTTAL);
            case SEVEN_PACES -> !data.hasUnique(id, SEVEN_PACES);
            case STILL_WITNESS -> !data.hasUnique(id, STILL_WITNESS);
            case HOLLOW_STEP -> !data.hasUnique(id, HOLLOW_STEP);
            case RED_WAKE -> !data.hasUnique(id, RED_WAKE);
            case IRON_AFTERTASTE -> !data.hasUnique(id, IRON_AFTERTASTE);
            case TIMBER_ECHO -> !data.hasUnique(id, TIMBER_ECHO);
            case RAIN_WRIT -> !data.hasUnique(id, RAIN_WRIT);
            case CENSUS_OF_ONE -> !data.hasUnique(id, CENSUS_ONE);
            case EMPTY_HAND_DOCTRINE -> !data.hasUnique(id, EMPTY_HAND);
            case FULL_HAND_DOCTRINE -> !data.hasUnique(id, FULL_HAND);
            case WOUND_CLOCK -> !data.hasUnique(id, WOUND_CLOCK);
            case STOP_THE_CLOCK -> data.hasUnique(id, WOUND_CLOCK);
            case HUNGER_OF_ORDER -> !data.hasUnique(id, HUNGER_ORDER);
            case BREAK_THE_ORDER -> data.hasUnique(id, HUNGER_ORDER);
            case GRAVE_RECEIPT -> !data.hasUnique(id, GRAVE_RECEIPT);
            case FINAL_FOOTNOTE -> !data.hasUnique(id, FINAL_FOOTNOTE);
            case MOON_ARCHIVE -> !data.hasUnique(id, MOON_ARCHIVE);
            case SIXTH_WITNESS -> !data.hasUnique(id, SIXTH_WITNESS);
            default -> false;
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isFifthWave(card)) return;
        UUID id = player.getUUID();
        switch (card) {
            case CLEAN_INTEREST -> data.setUnique(id, CLEAN_INTEREST, true);
            case SECOND_ATTEMPT -> data.setUnique(id, SECOND_ATTEMPT, true);
            case THREE_CLEAN_LINES -> data.setUnique(id, THREE_CLEAN, true);
            case HURRIED_OATH -> data.setUniqueInt(id, "fifth_hurried_charges", 3);
            case WHITE_INK -> data.setUniqueInt(id, "fifth_white_ink_charges", 2);
            case BOUND_TESTIMONY -> data.setUniqueInt(id, "fifth_bound_testimony_charges", 2);
            case DOUBLE_MARGIN -> data.setUniqueInt(id, "fifth_double_margin_charges", 2);
            case PERFECT_ORRERY -> data.setUnique(id, PERFECT_ORRERY, true);
            case PERFECT_PROCESSION -> data.setUnique(id, PERFECT_PROCESSION, true);
            case BLACK_STUDY -> data.setUnique(id, BLACK_STUDY, true);
            case CLOSE_THE_BOOK -> clearBlackStudy(data, id);
            case ASHEN_REBUTTAL -> data.setUnique(id, ASHEN_REBUTTAL, true);
            case SEVEN_PACES -> data.setUnique(id, SEVEN_PACES, true);
            case STILL_WITNESS -> data.setUnique(id, STILL_WITNESS, true);
            case HOLLOW_STEP -> data.setUnique(id, HOLLOW_STEP, true);
            case RED_WAKE -> data.setUnique(id, RED_WAKE, true);
            case IRON_AFTERTASTE -> data.setUnique(id, IRON_AFTERTASTE, true);
            case TIMBER_ECHO -> data.setUnique(id, TIMBER_ECHO, true);
            case RAIN_WRIT -> data.setUnique(id, RAIN_WRIT, true);
            case CENSUS_OF_ONE -> data.setUnique(id, CENSUS_ONE, true);
            case EMPTY_HAND_DOCTRINE -> data.setUnique(id, EMPTY_HAND, true);
            case FULL_HAND_DOCTRINE -> data.setUnique(id, FULL_HAND, true);
            case WOUND_CLOCK -> data.setUnique(id, WOUND_CLOCK, true);
            case STOP_THE_CLOCK -> clearWoundClock(data, id);
            case HUNGER_OF_ORDER -> data.setUnique(id, HUNGER_ORDER, true);
            case BREAK_THE_ORDER -> clearHungerOrder(data, id);
            case GRAVE_RECEIPT -> data.setUnique(id, GRAVE_RECEIPT, true);
            case FINAL_FOOTNOTE -> data.setUnique(id, FINAL_FOOTNOTE, true);
            case MOON_ARCHIVE -> data.setUnique(id, MOON_ARCHIVE, true);
            case SIXTH_WITNESS -> data.setUnique(id, SIXTH_WITNESS, true);
            default -> { }
        }
    }

    public static WardTerms consumeWardTerms(LockData data, UUID id) {
        if (data == null || id == null) return WardTerms.EMPTY;
        float loot = 0f;
        int lives = 0;
        int mask = 0;
        int seconds = 0;

        if (data.uniqueInt(id, "fifth_second_attempt_pending") > 0) {
            mask |= MinigameCardEffects.REVEAL_ONE;
            seconds += 4;
            data.setUniqueInt(id, "fifth_second_attempt_pending", 0);
        }
        int mercy = data.uniqueInt(id, "fifth_clean_mercy");
        if (mercy > 0) {
            mask |= MinigameCardEffects.FORGIVE_FIRST;
            data.setUniqueInt(id, "fifth_clean_mercy", mercy - 1);
        }
        int hurried = data.uniqueInt(id, "fifth_hurried_charges");
        if (hurried > 0) {
            mask |= MinigameCardEffects.QUICKEN;
            loot += .20f;
            data.setUniqueInt(id, "fifth_hurried_charges", hurried - 1);
        }
        int white = data.uniqueInt(id, "fifth_white_ink_charges");
        if (white > 0) {
            mask |= MinigameCardEffects.REVEAL_ONE;
            loot -= .08f;
            data.setUniqueInt(id, "fifth_white_ink_charges", white - 1);
        }
        int bound = data.uniqueInt(id, "fifth_bound_testimony_charges");
        if (bound > 0) {
            mask |= MinigameCardEffects.BIND_ONE;
            loot += .28f;
            data.setUniqueInt(id, "fifth_bound_testimony_charges", bound - 1);
        }
        int margin = data.uniqueInt(id, "fifth_double_margin_charges");
        if (margin > 0) {
            lives -= 1;
            seconds += 6;
            loot += .20f;
            data.setUniqueInt(id, "fifth_double_margin_charges", margin - 1);
        }
        return new WardTerms(loot, lives, mask, seconds);
    }

    /** Resolution hook. Called exactly once for every completed ordinary ward. */
    public static void onWardResolved(ServerPlayer player, LockData data, MinigameType game,
                                      boolean success, RewardBreakdown.PerformanceGrade grade) {
        if (player == null || data == null || game == null || game == MinigameType.CTHULHUS_GAME) return;
        UUID id = player.getUUID();
        boolean clean = success && (grade == RewardBreakdown.PerformanceGrade.CLEAN || grade == RewardBreakdown.PerformanceGrade.PERFECT);
        boolean perfect = success && grade == RewardBreakdown.PerformanceGrade.PERFECT;

        if (data.hasUnique(id, CLEAN_INTEREST) && clean) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 30, 0, false, true, true));
            if (perfect) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 0, false, true, true));
        }

        if (data.hasUnique(id, SECOND_ATTEMPT) && !success)
            data.setUniqueInt(id, "fifth_second_attempt_pending", 1);

        if (data.hasUnique(id, THREE_CLEAN)) {
            if (clean) {
                int streak = data.uniqueInt(id, "fifth_clean_streak") + 1;
                if (streak >= 3) {
                    int currentMercy=data.uniqueInt(id, "fifth_clean_mercy");
                    if(currentMercy<2){int banked=currentMercy+1;data.setUniqueInt(id, "fifth_clean_mercy",banked);streak=0;WardHud.message(player, Component.literal("THREE CLEAN LINES // Mercy banked: " + banked + "/2"), false);}
                    else streak=2; // full bank: preserve near-complete progress instead of wasting three clean clears
                }
                data.setUniqueInt(id, "fifth_clean_streak", streak);
            } else if (!success) data.setUniqueInt(id, "fifth_clean_streak", 0);
        }

        if (perfect && game == MinigameType.ORRERY && data.hasUnique(id, PERFECT_ORRERY)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 45, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 20, 1, false, true, true));
        }
        if (perfect && game == MinigameType.PROCESSION && data.hasUnique(id, PERFECT_PROCESSION)) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 60, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 0, false, true, true));
        }
        if (!success && data.hasUnique(id, BLACK_STUDY) && isLogic(game)) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 30, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 0, false, true, true));
        }
        if (perfect && data.hasUnique(id, SIXTH_WITNESS)) {
            int n = Math.min(3, data.uniqueInt(id, "fifth_sixth_witness_pages") + 1);
            data.setUniqueInt(id, "fifth_sixth_witness_pages", n);
        }
    }

    private static boolean isLogic(MinigameType game) {
        return switch (game) {
            case CIPHER, AUGURY, LATTICE, RUNEBRAID, SHARDSONG, VEILSTEP, BLACK_MEASURE, EPITAPH, ORRERY, PROCESSION -> true;
            default -> false;
        };
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && victim.getServer() != null) {
            LockData data = LockData.get(victim.getServer());
            UUID id = victim.getUUID();
            long now = victim.level().getGameTime();
            float amount = event.getAmount();

            if (data.hasUnique(id, MOON_ARCHIVE) && amount >= 4f) {
                int pages = data.uniqueInt(id, "fifth_moon_pages");
                if (pages > 0) {
                    event.setAmount(amount * .80f);
                    data.setUniqueInt(id, "fifth_moon_pages", pages - 1);
                    victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, true, true));
                    if (victim.level() instanceof ServerLevel level)
                        level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY() + 1.0, victim.getZ(), 14, .35, .5, .35, .03);
                }
            }
            amount = event.getAmount();
            if (data.hasUnique(id, ASHEN_REBUTTAL) && amount >= 8f
                    && now >= safeReady(data.uniqueLong(id, "fifth_rebuttal_ready"))) {
                data.setUniqueLong(id, "fifth_rebuttal_until", now + 80L);
                data.setUniqueLong(id, "fifth_rebuttal_ready", now + 280L);
            }
            if (data.hasUnique(id, WOUND_CLOCK) && amount >= 3f) {
                long last = data.uniqueLong(id, "fifth_wound_last");
                int amp = last != Long.MIN_VALUE && now - last <= 200L ? 1 : 0;
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, amp, false, true, true));
                data.setUniqueLong(id, "fifth_wound_last", now);
            }
            if (data.hasUnique(id, HUNGER_ORDER) && amount > 0f)
                data.setUniqueLong(id, "fifth_combat_until", now + 160L);
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer() != null) {
            LockData data = LockData.get(attacker.getServer());
            UUID id = attacker.getUUID();
            long now = attacker.level().getGameTime();
            boolean directMelee = event.getSource().getDirectEntity() == attacker;

            if (directMelee && data.uniqueLong(id, "fifth_rebuttal_until") >= now) {
                event.setAmount(event.getAmount() + 6f);
                data.setUniqueLong(id, "fifth_rebuttal_until", Long.MIN_VALUE);
            }
            if (directMelee && data.uniqueInt(id, "fifth_seven_armed") > 0) {
                event.setAmount(event.getAmount() + 7f);
                data.setUniqueInt(id, "fifth_seven_armed", 0);
            }
            if (directMelee && data.uniqueLong(id, "fifth_iron_until") >= now) {
                event.setAmount(event.getAmount() + 4f);
                data.setUniqueLong(id, "fifth_iron_until", Long.MIN_VALUE);
            }
            if (directMelee && data.hasUnique(id, EMPTY_HAND) && attacker.getOffhandItem().isEmpty())
                event.setAmount(event.getAmount() * 1.10f);

            if (data.hasUnique(id, RAIN_WRIT) && event.getSource().getDirectEntity() instanceof Projectile
                    && rainingOpen(attacker))
                event.setAmount(event.getAmount() * 1.15f);

            if (data.hasUnique(id, HOLLOW_STEP) && attacker.hasEffect(MobEffects.INVISIBILITY))
                attacker.removeEffect(MobEffects.INVISIBILITY);
            if (data.hasUnique(id, HUNGER_ORDER))
                data.setUniqueLong(id, "fifth_combat_until", now + 160L);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer player && player.getServer() != null) {
            LockData data = LockData.get(player.getServer());
            UUID id = player.getUUID();
            if (data.hasUnique(id, GRAVE_RECEIPT)) data.setUniqueInt(id, "fifth_grave_receipts", 3);
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.getServer() == null || !(dead instanceof Enemy)) return;
        LockData data = LockData.get(killer.getServer());
        UUID id = killer.getUUID();
        long now = killer.level().getGameTime();

        if (data.hasUnique(id, RED_WAKE)) {
            killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true, true));
            int n = data.uniqueInt(id, "fifth_red_wake_count") + 1;
            if (n >= 3) {
                killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true, true));
                n = 0;
            }
            data.setUniqueInt(id, "fifth_red_wake_count", n);
        }

        int receipts = data.uniqueInt(id, "fifth_grave_receipts");
        if (data.hasUnique(id, GRAVE_RECEIPT) && receipts > 0) {
            data.setUniqueInt(id, "fifth_grave_receipts", receipts - 1);
            killer.heal(2f);
            killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
        }

        if (data.hasUnique(id, FINAL_FOOTNOTE) && killer.getHealth() <= 4.0f
                && now >= safeReady(data.uniqueLong(id, "fifth_final_footnote_ready"))) {
            killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, true, true));
            killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true, true));
            data.setUniqueLong(id, "fifth_final_footnote_ready", now + 1200L);
        }

        if (data.hasUnique(id, MOON_ARCHIVE) && isNight(killer.level().getDayTime())) {
            data.setUniqueInt(id, "fifth_moon_pages", Math.min(5, data.uniqueInt(id, "fifth_moon_pages") + 1));
        }

        if (data.hasUnique(id, SIXTH_WITNESS) && data.uniqueInt(id, "fifth_sixth_witness_pages") >= 3
                && killer.level() instanceof ServerLevel level) {
            data.setUniqueInt(id, "fifth_sixth_witness_pages", 0); // clear before burst: burst kills cannot recursively retrigger it
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, killer.getBoundingBox().inflate(8.0),
                    e -> e.isAlive() && e != killer && e instanceof Enemy);
            for (LivingEntity target : targets) target.hurt(killer.damageSources().playerAttack(killer), 12f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, dead.getX(), dead.getY() + .8, dead.getZ(), 48, 2.6, 1.2, 2.6, .05);
            level.playSound(null, dead.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, .72f);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.getServer() == null) return;
        UUID id = player.getUUID();
        if (FourthWaveCardEffects.isSyntheticChainBreak(id)) return;
        LockData data = LockData.get(player.getServer());
        BlockState state = event.getState();
        long now = player.level().getGameTime();

        if (data.hasUnique(id, IRON_AFTERTASTE) && state.is(Tags.Blocks.ORES)
                && now >= safeReady(data.uniqueLong(id, "fifth_iron_ready"))) {
            data.setUniqueLong(id, "fifth_iron_until", now + 200L);
            data.setUniqueLong(id, "fifth_iron_ready", now + 240L);
        }
        if (data.hasUnique(id, TIMBER_ECHO) && state.is(BlockTags.LOGS)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, 0, false, true, true));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        double horizontal = player.getDeltaMovement().horizontalDistanceSqr();

        if (data.hasUnique(id, SEVEN_PACES)) {
            int previous=data.uniqueInt(id, "fifth_seven_ticks");
            int n = player.isSprinting() && player.onGround() && horizontal > .0025 ? previous + 1 : 0;
            if (n >= 140 && now >= safeReady(data.uniqueLong(id, "fifth_seven_ready"))) {
                data.setUniqueInt(id, "fifth_seven_armed", 1);
                data.setUniqueLong(id, "fifth_seven_ready", now + 440L);
                n = 0;
            }
            if(previous!=n)data.setUniqueInt(id, "fifth_seven_ticks", n);
        }

        if (data.hasUnique(id, STILL_WITNESS)) {
            int previous=data.uniqueInt(id, "fifth_still_ticks");
            int n = player.onGround() && !player.isSprinting() && !player.isShiftKeyDown() && horizontal < .0009
                    ? previous + 1 : 0;
            if (n >= 50 && now >= safeReady(data.uniqueLong(id, "fifth_still_ready"))) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0, false, true, true));
                data.setUniqueLong(id, "fifth_still_ready", now + 700L);
                n = 0;
            }
            if(previous!=n)data.setUniqueInt(id, "fifth_still_ticks", n);
        }

        if (data.hasUnique(id, HOLLOW_STEP)) {
            int previous=data.uniqueInt(id, "fifth_hollow_ticks");
            int n = player.onGround() && player.isShiftKeyDown() && horizontal < .0009
                    ? previous + 1 : 0;
            if (n >= 40 && now >= safeReady(data.uniqueLong(id, "fifth_hollow_ready"))) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0, false, true, true));
                data.setUniqueLong(id, "fifth_hollow_ready", now + 800L);
                n = 0;
            }
            if(previous!=n)data.setUniqueInt(id, "fifth_hollow_ticks", n);
        }

        if (player.tickCount % 20 == 0) {
            if (data.hasUnique(id, RAIN_WRIT) && rainingOpen(player))
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));

            if (data.hasUnique(id, CENSUS_ONE) && player.level() instanceof ServerLevel level) {
                int hostile = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0),
                        e -> e.isAlive() && e instanceof Enemy).size();
                if (hostile == 1) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
                else if (hostile >= 4) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, true, true));
            }
            if (data.hasUnique(id, FULL_HAND) && !player.getOffhandItem().isEmpty())
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, true, true));
        }

        if (data.hasUnique(id, HUNGER_ORDER) && data.uniqueLong(id, "fifth_combat_until") >= now) {
            if (player.tickCount % 20 == 0)
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
            if (player.tickCount % 80 == 0) player.getFoodData().addExhaustion(1.5f);
        }
    }

    private static long safeReady(long value) { return value == Long.MIN_VALUE ? 0L : value; }
    private static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 13000L && t < 23000L;
    }
    private static boolean rainingOpen(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return player.level().isRainingAt(pos) && player.level().canSeeSky(pos.above());
    }

    private static void clearBlackStudy(LockData data, UUID id) { data.setUnique(id, BLACK_STUDY, false); }
    private static void clearWoundClock(LockData data, UUID id) {
        data.setUnique(id, WOUND_CLOCK, false);
        data.setUniqueLong(id, "fifth_wound_last", Long.MIN_VALUE);
    }
    private static void clearHungerOrder(LockData data, UUID id) {
        data.setUnique(id, HUNGER_ORDER, false);
        data.setUniqueLong(id, "fifth_combat_until", Long.MIN_VALUE);
    }

    public static boolean hasLesserBurden(LockData data, UUID id) {
        return data != null && id != null && (data.hasUnique(id, BLACK_STUDY) || data.hasUnique(id, WOUND_CLOCK) || data.hasUnique(id, HUNGER_ORDER));
    }

    public static boolean clearOneLesserBurden(LockData data, UUID id) {
        if (data == null || id == null) return false;
        if (data.hasUnique(id, BLACK_STUDY)) { clearBlackStudy(data, id); return true; }
        if (data.hasUnique(id, WOUND_CLOCK)) { clearWoundClock(data, id); return true; }
        if (data.hasUnique(id, HUNGER_ORDER)) { clearHungerOrder(data, id); return true; }
        return false;
    }

    public static void absolve(LockData data, UUID id) {
        if (data == null || id == null) return;
        clearBlackStudy(data, id);
        clearWoundClock(data, id);
        clearHungerOrder(data, id);
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return "inactive";
        return switch (card) {
            case HURRIED_OATH -> "ward charges " + data.uniqueInt(id, "fifth_hurried_charges") + "/3";
            case WHITE_INK -> "ward charges " + data.uniqueInt(id, "fifth_white_ink_charges") + "/2";
            case BOUND_TESTIMONY -> "ward charges " + data.uniqueInt(id, "fifth_bound_testimony_charges") + "/2";
            case DOUBLE_MARGIN -> "ward charges " + data.uniqueInt(id, "fifth_double_margin_charges") + "/2";
            case THREE_CLEAN_LINES -> "clean line " + data.uniqueInt(id, "fifth_clean_streak") + "/3 · mercy " + data.uniqueInt(id, "fifth_clean_mercy") + "/2";
            case SECOND_ATTEMPT -> data.uniqueInt(id, "fifth_second_attempt_pending") > 0 ? "next ordinary ward carries the second attempt" : "watching the next ordinary failure";
            case MOON_ARCHIVE -> "stored pages " + data.uniqueInt(id, "fifth_moon_pages") + "/5";
            case SIXTH_WITNESS -> "testimony " + data.uniqueInt(id, "fifth_sixth_witness_pages") + "/3";
            case GRAVE_RECEIPT -> "death receipts " + data.uniqueInt(id, "fifth_grave_receipts") + "/3";
            case CLOSE_THE_BOOK, STOP_THE_CLOCK, BREAK_THE_ORDER -> "remedy already resolved";
            default -> available(data, id, card) ? "not yet signed" : "law active";
        };
    }

    public static String reliefText(ForbiddenBargain card) {
        if (card == null) return "";
        return switch (card) {
            case HURRIED_OATH, WHITE_INK, BOUND_TESTIMONY, DOUBLE_MARGIN -> "This clause ends when its remaining ordinary-ward charges are spent.";
            case CLOSE_THE_BOOK, STOP_THE_CLOCK, BREAK_THE_ORDER -> "This is a one-use remedy; its work is complete when signed.";
            case BLACK_STUDY -> "Close the Book, Debt Unwritten or Absolution removes the Study.";
            case WOUND_CLOCK -> "Stop the Clock, Debt Unwritten or Absolution removes the Clock.";
            case HUNGER_OF_ORDER -> "Break the Order, Debt Unwritten or Absolution removes the hunger-law.";
            case GRAVE_RECEIPT, FINAL_FOOTNOTE -> "This is a Death law. Ordinary remedies do not erase it.";
            default -> "This is a persistent world or ward law; ordinary milk does not remove it.";
        };
    }
}
