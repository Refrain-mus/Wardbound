package dev.marrowseal.wardbound;

import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/**
 * Reactive mechanics for Apothic card IDs 558..627.
 *
 * The permanent/raw attribute arithmetic stays in ApothicCardEffects so there is still only one
 * transient modifier per Apothic attribute.  This class owns the parts that make the third wave
 * behave like cards rather than a spreadsheet: conditional surges, hit/kill counters, ore hooks,
 * and the deliberately misleading-but-literal rare House joke encounters.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ApothicThirdWaveMechanics {
    private ApothicThirdWaveMechanics() {}

    private static final String JOKE_OWNER = "wardbound_apothic_joke_owner";
    private static final String JOKE_TYPE = "wardbound_apothic_joke_type";
    private static final String JOKE_SEEN = "apothic_joke_seen_";
    private static final String JOKE_WALK_SAFE = "joke_walk_start_safe";

    public static boolean isThird(ForbiddenBargain card) {
        return card != null && card.id >= 558 && card.id <= 627;
    }

    public static boolean isJoke(ForbiddenBargain card) {
        return card != null && card.id >= 618 && card.id <= 627;
    }

    /** Kept intentionally tiny: ten joke cards together should still be rarer than one ordinary card. */
    public static float jokeOfferMultiplier(ForbiddenBargain card) {
        return isJoke(card) ? 0.08f : 1.0f;
    }

    public static boolean jokeAvailable(LockData data, UUID id, ForbiddenBargain card) {
        return data != null && id != null && isJoke(card) && !data.hasUnique(id, JOKE_SEEN + card.id);
    }

    public static String jokeStatus(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isJoke(card)) return "UNKNOWN";
        if (!data.hasUnique(id, JOKE_SEEN + card.id)) return "UNSEEN HOUSE JOKE";
        return switch (card) {
            case QUICK_MINING_JOB -> data.hasUnique(id, "joke_mining_armed") ? "WAITING FOR ONE STONE" : "JOKE RESOLVED";
            case SHORT_WALK -> data.hasUnique(id, "joke_walk_armed") ? "COUNTING TWELVE BLOCKS" : "JOKE RESOLVED";
            case LUCKY_SEVEN -> data.hasUnique(id, "joke_seven_armed") ? "COUNTING HOSTILES · " + data.uniqueInt(id, "joke_seven_kills") + "/7" : "JOKE RESOLVED";
            case NOTHING_HAPPENS -> data.uniqueLong(id, "joke_nothing_at") != Long.MIN_VALUE ? "NOTHING PENDING" : "JOKE RESOLVED";
            default -> "JOKE SIGNED / ENCOUNTER ISSUED";
        };
    }

    public static void applyJoke(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isJoke(card)) return;
        UUID id = player.getUUID();
        if (data.hasUnique(id, JOKE_SEEN + card.id)) return;
        data.setUnique(id, JOKE_SEEN + card.id, true);
        ServerLevel level = player.serverLevel();

        switch (card) {
            case ZOMBIE_ONE_V_ONE -> {
                Zombie z = EntityType.ZOMBIE.create(level);
                if (z != null) {
                    prepareChallengeMob(player, z, "One Zombie", 560.0, 18.0, 0.38, 20.0, 0.78, card.id);
                    setApothic(z, ALObjects.Attributes.ARMOR_PIERCE.get(), 8.0);
                    setApothic(z, ALObjects.Attributes.CRIT_CHANCE.get(), 0.18);
                    setApothic(z, ALObjects.Attributes.CRIT_DAMAGE.get(), 2.0);
                    setApothic(z, ALObjects.Attributes.LIFE_STEAL.get(), 0.05);
                    spawnNear(player, z, 5);
                    jokeMessage(player, "The House has produced exactly one zombie. The contract remains technically flawless.");
                }
            }
            case FREE_DIAMOND -> {
                give(player, new ItemStack(Items.DIAMOND));
                for (int i = 0; i < 12; i++) spawnSilverfish(player, 3 + i % 3, 18.0, 0.34);
                jokeMessage(player, "Diamond delivered. The twelve complimentary witnesses were not listed as a charge.");
            }
            case ONE_SKELETON -> {
                Skeleton skeleton = EntityType.SKELETON.create(level);
                if (skeleton != null) {
                    prepareChallengeMob(player, skeleton, "One Skeleton", 430.0, 9.0, 0.32, 16.0, 0.35, card.id);
                    setApothic(skeleton, ALObjects.Attributes.ARMOR_PIERCE.get(), 7.0);
                    setApothic(skeleton, ALObjects.Attributes.ARROW_DAMAGE.get(), 1.85);
                    setApothic(skeleton, ALObjects.Attributes.ARROW_VELOCITY.get(), 1.35);
                    setApothic(skeleton, ALObjects.Attributes.DRAW_SPEED.get(), 1.65);
                    setApothic(skeleton, ALObjects.Attributes.CRIT_CHANCE.get(), 0.14);
                    spawnNear(player, skeleton, 7);
                    jokeMessage(player, "One skeleton. The House did not promise it was an ordinary employee.");
                }
            }
            case QUICK_MINING_JOB -> {
                data.setUnique(id, "joke_mining_armed", true);
                jokeMessage(player, "The employment contract is active. One natural stone, please.");
            }
            case SHORT_WALK -> {
                data.setUnique(id, "joke_walk_armed", true);
                data.setUniqueLong(id, "joke_walk_start", player.blockPosition().asLong()); // legacy XYZ key
                CardTeleportUtil.store(data,id,JOKE_WALK_SAFE,player);
                jokeMessage(player, "Twelve blocks. A very reasonable amount of walking.");
            }
            case FREE_HEALING -> {
                player.setHealth(player.getMaxHealth());
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1, false, true, true));
                Husk bill = EntityType.HUSK.create(level);
                if (bill != null) {
                    prepareChallengeMob(player, bill, "Medical Bill", 300.0, 14.0, 0.34, 14.0, 0.55, card.id);
                    setApothic(bill, ALObjects.Attributes.ARMOR_SHRED.get(), 0.20);
                    setApothic(bill, ALObjects.Attributes.LIFE_STEAL.get(), 0.08);
                    spawnNear(player, bill, 6);
                }
                jokeMessage(player, "The healing was free. The medical bill is a separate entity.");
            }
            case ONE_BABY_ZOMBIE -> {
                Zombie baby = EntityType.ZOMBIE.create(level);
                if (baby != null) {
                    baby.setBaby(true);
                    prepareChallengeMob(player, baby, "One Baby Zombie", 360.0, 15.0, 0.48, 12.0, 0.65, card.id);
                    setApothic(baby, ALObjects.Attributes.DODGE_CHANCE.get(), 0.18);
                    setApothic(baby, ALObjects.Attributes.LIFE_STEAL.get(), 0.08);
                    setApothic(baby, ALObjects.Attributes.CRIT_CHANCE.get(), 0.16);
                    spawnNear(player, baby, 5);
                    jokeMessage(player, "It is, without dispute, a baby zombie. The remaining statistics were not age-related.");
                }
            }
            case LUCKY_SEVEN -> {
                data.setUnique(id, "joke_seven_armed", true);
                data.setUniqueInt(id, "joke_seven_kills", 0);
                jokeMessage(player, "Seven hostiles. Seven diamonds. Seven is a very trustworthy number.");
            }
            case NOTHING_HAPPENS -> {
                data.setUniqueLong(id, "joke_nothing_at", player.level().getGameTime() + 60L);
                jokeMessage(player, "Nothing has begun successfully.");
            }
            case SMALL_REWARD -> {
                give(player, new ItemStack(Items.NETHERITE_SCRAP));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, true, true));
                player.setDeltaMovement(player.getDeltaMovement().x, 1.45, player.getDeltaMovement().z);
                player.hurtMarked = true;
                level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 0.72f);
                jokeMessage(player, "Small reward delivered. Delivery altitude was not discussed.");
            }
            default -> { }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();

        // Outgoing player clauses.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer() != null) {
            LockData data = LockData.get(attacker.getServer());
            UUID id = attacker.getUUID();
            long now = attacker.level().getGameTime();
            boolean directMelee = event.getSource().getDirectEntity() == attacker;
            boolean projectile = event.getSource().getDirectEntity() instanceof Projectile;

            if (directMelee && data.hasUnique(id, "apothic_hollow_aim")) event.setAmount(event.getAmount() * 0.85f);

            if (directMelee && law(data, id, ForbiddenBargain.EXECUTIONER_CLOCK)) {
                long last = data.uniqueLong(id, "third_executioner_last");
                if (last == Long.MIN_VALUE || now - last >= 200L) {
                    event.setAmount(event.getAmount() + 6.0f);
                    fx(attacker.serverLevel(), target, ParticleTypes.CRIT, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f);
                }
                data.setUniqueLong(id, "third_executioner_last", now);
            }

            if (directMelee && law(data, id, ForbiddenBargain.REDIRECTION_LAW) && data.uniqueInt(id, "third_redirection_armed") > 0) {
                event.setAmount(event.getAmount() + 5.0f);
                data.setUniqueInt(id, "third_redirection_armed", 0);
                fx(attacker.serverLevel(), target, ParticleTypes.ENCHANTED_HIT, SoundEvents.PLAYER_ATTACK_STRONG, 0.95f);
            }

            if (directMelee && law(data, id, ForbiddenBargain.CRITICAL_LOTTERY)) {
                float roll = attacker.getRandom().nextFloat();
                if (roll < 0.07f) {
                    event.setAmount(event.getAmount() + 12.0f);
                    attacker.displayClientMessage(Component.literal("Critical Lottery: JACKPOT +12").withStyle(ChatFormatting.GOLD), true);
                } else if (roll > 0.97f) {
                    attacker.hurt(attacker.damageSources().magic(), 4.0f);
                    attacker.displayClientMessage(Component.literal("Critical Lottery: handling fee -2 hearts").withStyle(ChatFormatting.RED), true);
                }
            }

            if (directMelee && law(data, id, ForbiddenBargain.ECHO_CHAMBER)) {
                int hit = data.uniqueInt(id, "third_echo_hits") + 1;
                if (hit >= 4) {
                    hit = 0;
                    event.setAmount(event.getAmount() * 1.35f);
                    fx(attacker.serverLevel(), target, ParticleTypes.SCULK_SOUL, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.72f);
                }
                data.setUniqueInt(id, "third_echo_hits", hit);
            }

            if (projectile && law(data, id, ForbiddenBargain.LAST_ARROW_IN_QUIVER) && arrowCount(attacker) == 1) {
                event.setAmount(event.getAmount() * 1.50f);
                fx(attacker.serverLevel(), target, ParticleTypes.CRIT, SoundEvents.ARROW_HIT, 1.15f);
            }

            if (projectile && law(data, id, ForbiddenBargain.METEOR_QUIVER)
                    && now >= ready(data.uniqueLong(id, "third_meteor_ready")) && attacker.level() instanceof ServerLevel level) {
                data.setUniqueLong(id, "third_meteor_ready", now + 40L);
                List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
                        e -> e.isAlive() && e != target && e != attacker && e instanceof Enemy);
                for (LivingEntity other : around) other.hurt(other.damageSources().magic(), 4.0f);
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.8, target.getZ(), 28, 1.6, .7, 1.6, .04);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.45f, 1.25f);
            }
        }

        // Incoming player clauses.
        if (target instanceof ServerPlayer player && player.getServer() != null) {
            LockData data = LockData.get(player.getServer());
            UUID id = player.getUUID();
            long now = player.level().getGameTime();

            if (data.hasUnique(id, "apothic_borrowed_skin"))
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true, true));

            if (law(data, id, ForbiddenBargain.REDIRECTION_LAW)) data.setUniqueInt(id, "third_redirection_armed", 1);

            if (law(data, id, ForbiddenBargain.REVOLVING_DOOR) && now >= ready(data.uniqueLong(id, "third_door_ready"))) {
                data.setUniqueLong(id, "third_door_ready", now + 240L);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 2, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, true, true));
                player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 32, .5, .8, .5, .12);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.55f, 1.35f);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();

        if (dead.getPersistentData().contains(JOKE_TYPE)) resolveChallengeDeath(event, dead);

        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.getServer() == null || !(dead instanceof Enemy)) return;
        LockData data = LockData.get(killer.getServer());
        UUID id = killer.getUUID();
        long now = killer.level().getGameTime();
        boolean projectileKill = event.getSource().getDirectEntity() instanceof Projectile;

        if (active(data, id, ForbiddenBargain.KILLING_MOMENTUM, now))
            killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true, true));

        if (law(data, id, ForbiddenBargain.BLOODHOUND_CALCULUS)) {
            int n = data.uniqueInt(id, "third_bloodhound_kills") + 1;
            if (n >= 3) {
                n = 0;
                data.setUniqueLong(id, "third_bloodhound_surge", now + 140L);
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 1, false, true, true));
            }
            data.setUniqueInt(id, "third_bloodhound_kills", n);
        }

        if (projectileKill && law(data, id, ForbiddenBargain.HUNTERS_RHYTHM))
            data.setUniqueLong(id, "third_hunter_rhythm", now + 120L);

        if (law(data, id, ForbiddenBargain.EMBER_DEBT_COLLECTOR) && dead.isOnFire())
            killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 0, false, true, true));

        if (law(data, id, ForbiddenBargain.RIME_COLLECTOR) && killer.isInWaterOrRain()) {
            int n = data.uniqueInt(id, "third_rime_pages") + 1;
            if (n >= 3) {
                n = 0;
                chillCrowd(killer, 7.0, 100, 0, false);
            }
            data.setUniqueInt(id, "third_rime_pages", n);
        }

        if (projectileKill && law(data, id, ForbiddenBargain.FULL_QUIVER_LAW) && killer.getRandom().nextFloat() < 0.25f)
            give(killer, new ItemStack(Items.ARROW));

        if (law(data, id, ForbiddenBargain.VULTURES_MARGIN)) {
            killer.heal(2.0f);
            killer.giveExperiencePoints(3);
        }

        if (data.hasUnique(id, "apothic_auditors_hunger")) killer.getFoodData().addExhaustion(1.5f);

        if (law(data, id, ForbiddenBargain.IMPOSSIBLE_WINTER) && killer.isInWaterOrRain())
            chillCrowd(killer, 8.0, 120, 1, true);

        if (law(data, id, ForbiddenBargain.CREMATION_CLAUSE) && dead.isOnFire() && killer.level() instanceof ServerLevel level) {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(6.0),
                    e -> e.isAlive() && e != killer && e instanceof Enemy)) other.setSecondsOnFire(6);
            level.sendParticles(ParticleTypes.FLAME, dead.getX(), dead.getY() + .6, dead.getZ(), 36, 2.0, .8, 2.0, .06);
        }

        if (law(data, id, ForbiddenBargain.QUARRY_OF_FLESH)) {
            int n = data.uniqueInt(id, "third_flesh_quarry") + 1;
            if (n >= 8) {
                n = 0;
                ItemStack prize = switch (killer.getRandom().nextInt(4)) {
                    case 0 -> new ItemStack(Items.DIAMOND);
                    case 1 -> new ItemStack(Items.EMERALD, 2);
                    case 2 -> new ItemStack(Items.GOLD_INGOT, 4);
                    default -> new ItemStack(Items.AMETHYST_SHARD, 8);
                };
                give(killer, prize);
                killer.displayClientMessage(Component.literal("Quarry of Flesh: material extracted.").withStyle(ChatFormatting.DARK_RED), true);
            }
            data.setUniqueInt(id, "third_flesh_quarry", n);
        }

        if (law(data, id, ForbiddenBargain.LAST_ACCOUNT)) {
            int n = data.uniqueInt(id, "third_last_account_kills") + 1;
            if (n >= 25) {
                n = 0;
                give(killer, new ItemStack(Items.NETHERITE_SCRAP));
                killer.displayClientMessage(Component.literal("The Last Account pays one netherite scrap.").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            data.setUniqueInt(id, "third_last_account_kills", n);
        }

        if (law(data, id, ForbiddenBargain.HOUSE_FAVORITE_SEVEN)) {
            int n = data.uniqueInt(id, "third_house_seven") + 1;
            if (n >= 7) {
                n = 0;
                killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 140, 1, false, true, true));
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 1, false, true, true));
                killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 140, 1, false, true, true));
                killer.level().playSound(null, killer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.75f);
            }
            data.setUniqueInt(id, "third_house_seven", n);
        }

        if (law(data, id, ForbiddenBargain.MERCY_AFTER_MURDER) && now >= ready(data.uniqueLong(id, "third_mercy_ready"))) {
            data.setUniqueLong(id, "third_mercy_ready", now + 200L);
            killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true, true));
        }

        if (data.hasUnique(id, "joke_seven_armed")) {
            int n = data.uniqueInt(id, "joke_seven_kills") + 1;
            data.setUniqueInt(id, "joke_seven_kills", n);
            if (n >= 7) {
                data.setUnique(id, "joke_seven_armed", false);
                data.setUniqueInt(id, "joke_seven_kills", 0);
                give(killer, new ItemStack(Items.DIAMOND, 7));
                for (int i = 0; i < 7; i++) spawnSevenBaby(killer, i);
                jokeMessage(killer, "Seven diamonds delivered. Seven additional participants have also been delivered.");
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        BlockState state = event.getState();

        if (active(data, id, ForbiddenBargain.QUARRY_TEMPER, now) && state.is(Tags.Blocks.ORES))
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true, true));

        if (law(data, id, ForbiddenBargain.QUARRY_COMMUNION) && state.is(Tags.Blocks.ORES)) {
            int n = data.uniqueInt(id, "third_quarry_communion") + 1;
            if (n >= 3) {
                n = 0;
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0, false, true, true));
            }
            data.setUniqueInt(id, "third_quarry_communion", n);
        }

        if (data.hasUnique(id, "apothic_red_quarry") && state.is(Tags.Blocks.ORES)
                && now >= ready(data.uniqueLong(id, "third_red_quarry_ready"))) {
            data.setUniqueLong(id, "third_red_quarry_ready", now + 100L);
            player.hurt(player.damageSources().magic(), 1.0f);
            player.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0, player.getZ(), 6, .3, .4, .3, .02);
        }

        if (data.hasUnique(id, "joke_mining_armed") && state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            data.setUnique(id, "joke_mining_armed", false);
            give(player, new ItemStack(Items.DIAMOND));
            for (int i = 0; i < 12; i++) spawnSilverfish(player, 2 + i % 4, 18.0, 0.36);
            jokeMessage(player, "One stone mined. One diamond paid. The subcontractors appear upset.");
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer() == null) return;
        LockData data = LockData.get(player.getServer());
        UUID id = player.getUUID();
        long now = player.level().getGameTime();

        // Per-tick only for the two joke counters and the stillness accumulator.
        if (active(data, id, ForbiddenBargain.STILL_BALLISTICS, now)) {
            boolean still = player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < .0007;
            int n = still ? data.uniqueInt(id, "third_still_ticks") + 1 : 0;
            if (n >= 30) data.setUniqueLong(id, "third_still_focus_until", now + 30L);
            if (n != data.uniqueInt(id, "third_still_ticks")) data.setUniqueInt(id, "third_still_ticks", Math.min(40, n));
        }

        if (data.hasUnique(id, "joke_walk_armed")) {
            CardTeleportUtil.Stored stored=CardTeleportUtil.load(data,id,JOKE_WALK_SAFE,player);
            long legacy=data.uniqueLong(id,"joke_walk_start");
            if(stored==null && legacy!=Long.MIN_VALUE && data.uniqueString(id,JOKE_WALK_SAFE+"_dim").isBlank()){
                // Legacy XYZ-only save. Corrupted/new dimension-aware state must not be
                // reinterpreted in whatever dimension the player happens to occupy now.
                BlockPos start=BlockPos.of(legacy);
                stored=new CardTeleportUtil.Stored(player.level().dimension(),new net.minecraft.world.phys.Vec3(start.getX()+.5,start.getY(),start.getZ()+.5));
            }
            // A coordinate in another dimension is not twelve blocks away; pause the measurement
            // until the witness returns instead of reinterpreting Nether/End XYZ in the current world.
            if(stored!=null && player.level().dimension().equals(stored.dimension())){
                double dx=player.getX()-stored.position().x;
                double dz=player.getZ()-stored.position().z;
                if(dx*dx+dz*dz>=144.0){
                    data.setUnique(id,"joke_walk_armed",false);
                    data.setUniqueLong(id,"joke_walk_start",Long.MIN_VALUE);
                    give(player,new ItemStack(Items.EMERALD_BLOCK,2));
                    CardTeleportUtil.teleportStored(player,data,id,JOKE_WALK_SAFE);
                    CardTeleportUtil.clear(data,id,JOKE_WALK_SAFE);
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,80,0,false,true,true));
                    jokeMessage(player,"Twelve blocks completed. The House has returned you to the point from which the walk was measured.");
                }
            }
        }

        long nothing = data.uniqueLong(id, "joke_nothing_at");
        if (nothing != Long.MIN_VALUE && now >= nothing) {
            data.setUniqueLong(id, "joke_nothing_at", Long.MIN_VALUE);
            for (int i = 0; i < 12; i++) {
                var chicken = EntityType.CHICKEN.create(player.serverLevel());
                if (chicken != null) spawnNear(player, chicken, 2 + i % 5);
            }
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + .8, player.getZ(), 60, 2.5, 1.0, 2.5, .04);
            player.level().playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.45f, 1.6f);
            jokeMessage(player, "Nothing has now happened twelve times.");
        }

        if (player.tickCount % 20 != 0) return;

        if (active(data, id, ForbiddenBargain.LAST_BREATH_MARGIN, now) && player.getHealth() <= player.getMaxHealth() * .25f)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));

        if (active(data, id, ForbiddenBargain.MOVING_TARGET_CLAUSE, now) && player.isSprinting())
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));

        if (active(data, id, ForbiddenBargain.FURNACE_ARITHMETIC, now) && player.isOnFire())
            data.setUniqueLong(id, "third_furnace_hot_until", now + 30L);

        if (active(data, id, ForbiddenBargain.WINTER_PULSE, now) && player.isInWaterOrRain())
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));

        if (active(data, id, ForbiddenBargain.EMPTY_STOMACH_DOCTRINE, now) && player.getFoodData().getFoodLevel() <= 6)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));

        if (active(data, id, ForbiddenBargain.FULL_STOMACH_DIVIDEND, now) && player.getFoodData().getFoodLevel() >= 20)
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, true, true));

        if (active(data, id, ForbiddenBargain.SUNBURN_LEDGER, now) && isDay(player) && player.level().canSeeSky(player.blockPosition().above()))
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));

        int hostiles = -1;
        if (needsHostileCensus(data, id, now)) hostiles = hostileCount(player, 12.0);

        if (active(data, id, ForbiddenBargain.DUELISTS_EXCEPTION, now) && hostiles == 1)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
        if (active(data, id, ForbiddenBargain.MOB_INTEREST, now) && hostiles >= 4)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, true, true));
        if (active(data, id, ForbiddenBargain.BROKEN_SHIELD_PREMIUM, now) && player.getOffhandItem().isEmpty())
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
        if (active(data, id, ForbiddenBargain.HEAVY_POCKETS_CLAUSE, now) && occupiedSlots(player) >= 30)
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 1, false, true, true));
        if (active(data, id, ForbiddenBargain.CLEAN_HANDS_CLAUSE, now) && armorPieces(player) == 0)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, true, true));
        if (active(data, id, ForbiddenBargain.PLATED_DEBT, now) && armorPieces(player) >= 4)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, true, true));

        if (law(data, id, ForbiddenBargain.LONE_PREDATOR) && hostiles == 1)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
        if (law(data, id, ForbiddenBargain.CROWD_AUDITOR) && hostiles >= 5)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, true, true));
        if (law(data, id, ForbiddenBargain.REDLINE_ANATOMY) && player.getHealth() <= player.getMaxHealth() * .30f) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));
        }
        if (law(data, id, ForbiddenBargain.PERFECT_MURDER_GEOMETRY) && hostiles == 1)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, true, true));
        if (law(data, id, ForbiddenBargain.HOUSE_BLOOD_BANK) && player.getAbsorptionAmount() >= 8.0f)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, true, true));
    }

    private static boolean needsHostileCensus(LockData data, UUID id, long now) {
        return active(data, id, ForbiddenBargain.DUELISTS_EXCEPTION, now)
                || active(data, id, ForbiddenBargain.MOB_INTEREST, now)
                || law(data, id, ForbiddenBargain.LONE_PREDATOR)
                || law(data, id, ForbiddenBargain.CROWD_AUDITOR)
                || law(data, id, ForbiddenBargain.PERFECT_MURDER_GEOMETRY);
    }

    private static void resolveChallengeDeath(LivingDeathEvent event, LivingEntity dead) {
        String ownerRaw = dead.getPersistentData().getString(JOKE_OWNER);
        if (ownerRaw.isBlank() || dead.level().isClientSide()) return;
        if (!(dead.level() instanceof ServerLevel level)) return;
        UUID owner;
        try { owner = UUID.fromString(ownerRaw); } catch (IllegalArgumentException ex) { return; }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) return;
        boolean ownerKill = event.getSource().getEntity() instanceof ServerPlayer killer && killer.getUUID().equals(owner);
        int type = dead.getPersistentData().getInt(JOKE_TYPE);
        if (!ownerKill) {
            jokeMessage(player, "The House observed outside assistance. No challenge reward is due.");
            return;
        }
        switch (type) {
            case 618 -> {
                give(player, new ItemStack(Items.NETHERITE_INGOT));
                player.giveExperienceLevels(3);
                jokeMessage(player, "Zombie 1v1 complete. The zombie was one; the statistics were several.");
            }
            case 620 -> {
                give(player, new ItemStack(Items.NETHERITE_SCRAP, 3));
                give(player, new ItemStack(Items.SPECTRAL_ARROW, 16));
                jokeMessage(player, "One Skeleton complete. The House has paid for the inconvenience.");
            }
            case 624 -> {
                give(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
                give(player, new ItemStack(Items.NETHERITE_SCRAP));
                jokeMessage(player, "The baby zombie has been professionally reviewed and rejected.");
            }
            case 623 -> jokeMessage(player, "Medical Bill settled. The healing remains free.");
            default -> { }
        }
    }

    private static void prepareChallengeMob(ServerPlayer owner, Mob mob, String name, double health, double attack, double speed,
                                            double armor, double knockback, int type) {
        mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        setVanilla(mob, Attributes.MAX_HEALTH, health);
        setVanilla(mob, Attributes.ATTACK_DAMAGE, attack);
        setVanilla(mob, Attributes.MOVEMENT_SPEED, speed);
        setVanilla(mob, Attributes.ARMOR, armor);
        setVanilla(mob, Attributes.KNOCKBACK_RESISTANCE, knockback);
        mob.setHealth((float) health);
        mob.getPersistentData().putString(JOKE_OWNER, owner.getUUID().toString());
        mob.getPersistentData().putInt(JOKE_TYPE, type);
    }

    private static void spawnSevenBaby(ServerPlayer player, int index) {
        Zombie baby = EntityType.ZOMBIE.create(player.serverLevel());
        if (baby == null) return;
        baby.setBaby(true);
        baby.setCustomName(Component.literal("Seven's Assistant " + (index + 1)).withStyle(ChatFormatting.GOLD));
        baby.setPersistenceRequired();
        setVanilla(baby, Attributes.MAX_HEALTH, 77.0);
        setVanilla(baby, Attributes.ATTACK_DAMAGE, 7.0);
        setVanilla(baby, Attributes.MOVEMENT_SPEED, 0.40);
        setApothic(baby, ALObjects.Attributes.DODGE_CHANCE.get(), 0.07);
        setApothic(baby, ALObjects.Attributes.CRIT_CHANCE.get(), 0.07);
        baby.setHealth(77.0f);
        spawnNear(player, baby, 3 + index % 4);
    }

    private static void spawnSilverfish(ServerPlayer player, int radius, double health, double speed) {
        Silverfish fish = EntityType.SILVERFISH.create(player.serverLevel());
        if (fish == null) return;
        setVanilla(fish, Attributes.MAX_HEALTH, health);
        setVanilla(fish, Attributes.MOVEMENT_SPEED, speed);
        fish.setHealth((float) health);
        spawnNear(player, fish, radius);
    }

    private static void spawnNear(ServerPlayer player, LivingEntity entity, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        BlockPos chosen = origin.offset(radius, 0, 0);
        int[][] offsets = {{radius,0},{-radius,0},{0,radius},{0,-radius},{radius,radius},{-radius,radius},{radius,-radius},{-radius,-radius}};
        for (int dy = 0; dy <= 2; dy++) {
            for (int[] off : offsets) {
                BlockPos p = origin.offset(off[0], dy, off[1]);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                    chosen = p;
                    dy = 3;
                    break;
                }
            }
        }
        entity.moveTo(chosen.getX() + .5, chosen.getY(), chosen.getZ() + .5, player.getYRot() + 180f, 0f);
        level.addFreshEntity(entity);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY() + .8, entity.getZ(), 28, .6, .8, .6, .04);
        level.playSound(null, chosen, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.75f, 0.65f);
    }

    private static void chillCrowd(ServerPlayer player, double radius, int ticks, int amplifier, boolean hurt) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != player && e instanceof Enemy)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amplifier, false, true, true));
            if (hurt) e.hurt(e.damageSources().magic(), 3.0f);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + .8, player.getZ(), 42, radius * .35, .8, radius * .35, .03);
    }

    private static int hostileCount(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != player && e instanceof Enemy).size();
    }

    private static int occupiedSlots(ServerPlayer player) {
        int used = 0;
        for (int i = 0; i < 36; i++) if (!player.getInventory().getItem(i).isEmpty()) used++;
        return used;
    }

    private static int armorPieces(ServerPlayer player) {
        int n = 0;
        for (ItemStack stack : player.getArmorSlots()) if (!stack.isEmpty()) n++;
        return n;
    }

    private static int arrowCount(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) count += stack.getCount();
            if (count > 1) return count;
        }
        return count;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void jokeMessage(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[House] " + text).withStyle(ChatFormatting.LIGHT_PURPLE));
        WardHistory.recordSpecial(player, "CARD", "House joke // " + text);
    }

    private static void fx(ServerLevel level, LivingEntity target, net.minecraft.core.particles.ParticleOptions particle,
                           net.minecraft.sounds.SoundEvent sound, float pitch) {
        level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * .55, target.getZ(), 18, .45, .45, .45, .05);
        level.playSound(null, target.blockPosition(), sound, SoundSource.PLAYERS, .65f, pitch);
    }

    private static boolean active(LockData data, UUID id, ForbiddenBargain card, long now) {
        long until = data.uniqueLong(id, "apothic_timer_" + card.id);
        return until != Long.MIN_VALUE && until > now;
    }

    private static boolean law(LockData data, UUID id, ForbiddenBargain card) {
        return data.hasUnique(id, "apothic_law_" + card.id);
    }

    private static long ready(long value) { return value == Long.MIN_VALUE ? 0L : value; }

    private static boolean isDay(ServerPlayer player) {
        long t = Math.floorMod(player.level().getDayTime(), 24000L);
        return t < 12000L;
    }

    private static void setVanilla(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static void setApothic(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
}
