package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compact runtime for the intentionally strange early/mid-game cards added in v34.
 * The laws are stored in LockData so the card system remains server-authoritative.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class WildCardEffects {
    private WildCardEffects() {}

    private static final String RETURN_POS = "wild_return_pos"; // legacy XYZ-only save key
    private static final String RETURN_SAFE = "wild_return_address";
    private static final String[] EXPIRING_KEYS = {
            "immortal","village_cold","iron_accusation","warden_blind","empty_deep",
            "zombie_truce","bone_truce","creeper_truce","spider_truce","ender_truce","witch_truce",
            "hostile_census","glass_saint","shared_pain","blackout","night_pass","day_pass",
            "grave_silence","beast_march","golem_escort","golem_enmity","creeper_blessing",
            "falling_crown","open_sky","deep_breath"
    };

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || card == null) return false;
        return switch (card) {
            case VILLAGE_PARDON -> timed(data,id,"village_cold") || timed(data,id,"iron_accusation")
                    || timed(data,id,"golem_enmity") || data.hasUnique(id,"village_exile");
            case FOURTH_CARD -> data.uniqueInt(id,"deck_plus_one") <= 0;
            case FIFTH_CARD -> CardMaster.phaseActive(data, id) && data.uniqueInt(id,"deck_plus_two") <= 0;
            case NARROW_HAND -> data.uniqueInt(id,"deck_narrow") <= 0;
            case CURSE_DAMPER -> data.uniqueInt(id,"deck_curse_damper") <= 0;
            case CURSE_BAIT -> data.uniqueInt(id,"deck_curse_bait") <= 0;
            case RARE_INK -> data.uniqueInt(id,"deck_rare_ink") <= 0;
            case CLEAN_MARGIN -> data.uniqueInt(id,"deck_clean_margin") <= 0;
            case BLACK_MARGIN -> data.uniqueInt(id,"deck_black_margin") <= 0;
            case NO_ECHOES -> data.uniqueInt(id,"deck_no_echoes") <= 0;
            case REMEDY_WITNESS -> data.uniqueInt(id,"deck_remedy_witness") <= 0;
            case DEALERS_FOURTH -> data.uniqueInt(id,"dealer_fourth") <= 0;
            case GOLDEN_CUT -> data.uniqueInt(id,"deck_golden_cut") <= 0;
            case DEEP_SHELF -> data.uniqueInt(id,"deck_deep_shelf") <= 0;
            case CURSE_LULL -> data.uniqueInt(id,"deck_curse_lull") <= 0;
            case STRANGE_INVITATION -> data.uniqueInt(id,"anomaly_lure") <= 0;
            case FOURTH_SHAPE -> data.uniqueInt(id,"anomaly_guarantee") <= 0;
            case WHITE_THREAD -> data.uniqueInt(id,"anomaly_white_thread") <= 0;
            case SCRIBBLED_RESPITE -> data.uniqueInt(id,"minigame_seconds_chain") <= 0;
            case PALE_MARGIN -> !data.hasUnique(id,"minigame_seconds_perm");
            case REGISTRY_LOTTERY, EGG_WITH_NO_SHELL, SIXTY_FOURTH_STEP, STORM_RECEIPT,
                    BORROWED_FACE, MOB_EXCHANGE, BAD_RECEIPT, LUCKY_POCKET, CERTAINLY_NOTHING -> true;
            default -> !active(data,id,card);
        };
    }

    private static boolean active(LockData data, UUID id, ForbiddenBargain card) {
        return switch (card) {
            case TEN_SECONDS_UNWRITTEN -> timed(data,id,"immortal");
            case VILLAGE_COLD_SHOULDER -> timed(data,id,"village_cold");
            case IRON_ACCUSATION -> timed(data,id,"iron_accusation");
            case WARDENS_BLIND_SPOT -> timed(data,id,"warden_blind");
            case EMPTY_DEEP -> timed(data,id,"empty_deep");
            case ZOMBIE_ARMISTICE -> timed(data,id,"zombie_truce");
            case BONE_TRUCE -> timed(data,id,"bone_truce");
            case CREEPER_COURTESY -> timed(data,id,"creeper_truce");
            case SPIDER_TREATY -> timed(data,id,"spider_truce");
            case ENDER_AMNESTY -> timed(data,id,"ender_truce");
            case WITCHS_PRIVILEGE -> timed(data,id,"witch_truce");
            case HOSTILE_CENSUS -> timed(data,id,"hostile_census");
            case HUNDRED_STEPS -> timed(data,id,"hundred_steps") || data.uniqueInt(id,"hundred_steps_debt") > 0;
            case GLASS_SAINT -> timed(data,id,"glass_saint");
            case SHARED_PAIN -> timed(data,id,"shared_pain");
            case MEASURED_MERCY -> data.uniqueInt(id,"measured_mercy") > 0;
            case CHORUS_ERROR -> timed(data,id,"chorus_error");
            case RETURN_ADDRESS -> timed(data,id,"return_address");
            case LANTERN_BREAK -> timed(data,id,"lantern_break") || data.uniqueInt(id,"lantern_break_debt") > 0;
            case BLACKOUT -> timed(data,id,"blackout");
            case NIGHT_PASS -> timed(data,id,"night_pass");
            case DAY_PASS -> timed(data,id,"day_pass");
            case GRAVE_SILENCE -> timed(data,id,"grave_silence");
            case BEAST_MARCH -> timed(data,id,"beast_march");
            case GOLEM_ESCORT -> timed(data,id,"golem_escort");
            case GOLEM_ENMITY -> timed(data,id,"golem_enmity");
            case VILLAGE_EXILE -> data.hasUnique(id,"village_exile");
            case CREEPER_BLESSING -> timed(data,id,"creeper_blessing");
            case FALLING_CROWN -> timed(data,id,"falling_crown");
            case OPEN_SKY -> timed(data,id,"open_sky");
            case DEEP_BREATH -> timed(data,id,"deep_breath");
            case SCRIBBLED_RESPITE -> data.uniqueInt(id,"minigame_seconds_chain") > 0;
            case PALE_MARGIN -> data.hasUnique(id,"minigame_seconds_perm");
            default -> false;
        };
    }

    private static boolean timed(LockData data, UUID id, String key) {
        long until = data.uniqueLong(id, key + "_until");
        return until != Long.MIN_VALUE && until > 0L;
    }

    private static void timer(LockData data, UUID id, String key, long now, long ticks) {
        data.setUniqueLong(id, key + "_until", now + ticks);
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || card == null || !(player.level() instanceof ServerLevel level)) return;
        UUID id = player.getUUID();
        long now = level.getGameTime();
        switch (card) {
            case WRONG_DOOR -> teleportRandomSurface(player, level, 180, 220);
            case TEN_SECONDS_UNWRITTEN -> timer(data,id,"immortal",now,20L*10L);
            case VILLAGE_COLD_SHOULDER -> timer(data,id,"village_cold",now,20L*60L*3L);
            case IRON_ACCUSATION -> timer(data,id,"iron_accusation",now,20L*60L*2L);
            case WARDENS_BLIND_SPOT -> timer(data,id,"warden_blind",now,20L*60L*4L);
            case EMPTY_DEEP -> timer(data,id,"empty_deep",now,20L*60L*4L);
            case ZOMBIE_ARMISTICE -> timer(data,id,"zombie_truce",now,20L*60L*2L);
            case BONE_TRUCE -> timer(data,id,"bone_truce",now,20L*60L*2L);
            case CREEPER_COURTESY -> timer(data,id,"creeper_truce",now,20L*60L*2L);
            case SPIDER_TREATY -> timer(data,id,"spider_truce",now,20L*60L*2L);
            case ENDER_AMNESTY -> timer(data,id,"ender_truce",now,20L*60L*3L);
            case WITCHS_PRIVILEGE -> timer(data,id,"witch_truce",now,20L*60L*3L);
            case HOSTILE_CENSUS -> timer(data,id,"hostile_census",now,20L*60L*2L);
            case FIRE_WITHOUT_FUEL -> {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(18.0), m -> m instanceof Enemy))
                    mob.setSecondsOnFire(10);
            }
            case WRONG_GRAVITY -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20*3, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20*28, 0, false, true, true));
            }
            case HUNDRED_STEPS -> {
                timer(data,id,"hundred_steps",now,20L*30L);
                data.setUniqueInt(id,"hundred_steps_debt",1);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*30,3,false,true,true));
            }
            case RED_MINUTE -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*60,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*60,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*60,0,false,true,true));
            }
            case GLASS_SAINT -> timer(data,id,"glass_saint",now,20L*30L);
            case SHARED_PAIN -> timer(data,id,"shared_pain",now,20L*60L*2L);
            case MEASURED_MERCY -> data.setUniqueInt(id,"measured_mercy",3);
            case TIDAL_BODY -> {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20*60*3,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE,20*60*3,0,false,true,true));
            }
            case CHORUS_ERROR -> {
                timer(data,id,"chorus_error",now,20L*60L*2L);
                data.setUniqueLong(id,"chorus_next",now+20L*20L);
            }
            case RETURN_ADDRESS -> {
                timer(data,id,"return_address",now,20L*60L*2L);
                // Keep the old packed position for save compatibility, but new cards also write the
                // dimension-aware safe-return record so portal travel cannot reinterpret the XYZ.
                data.setUniqueLong(id,RETURN_POS,player.blockPosition().asLong());
                CardTeleportUtil.store(data,id,RETURN_SAFE,player);
            }
            case LANTERN_BREAK -> {
                timer(data,id,"lantern_break",now,20L*20L);
                data.setUniqueInt(id,"lantern_break_debt",1);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,20*20,0,false,true,true));
            }
            case WHITE_NOISE -> {
                List<net.minecraft.world.effect.MobEffect> remove = new ArrayList<>();
                for (MobEffectInstance effect : player.getActiveEffects())
                    if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) remove.add(effect.getEffect());
                for (var effect : remove) player.removeEffect(effect);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,20*10,0,false,true,true));
            }
            case BLOOD_MONEY -> {
                player.setHealth(Math.max(2.0f, player.getHealth()-6.0f));
                ExperienceOrb.award(level,player.position(),15);
            }
            case COIN_EDGE -> {
                if (player.getRandom().nextBoolean()) {
                    player.addEffect(new MobEffectInstance(MobEffects.LUCK,20*60,1,false,true,true));
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*60,0,false,true,true));
                } else {
                    player.addEffect(new MobEffectInstance(MobEffects.UNLUCK,20*60,1,false,true,true));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*60,0,false,true,true));
                }
            }
            case CHANCE_ENGINE -> applyRandomLaw(player);
            case BLACKOUT -> {
                timer(data,id,"blackout",now,20L*45L);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,20*45,0,false,true,true));
            }
            case AIR_BORROWED -> {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*20,3,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*60,0,false,true,true));
            }
            case BURNING_PACT -> {
                player.setSecondsOnFire(5);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,20*60*3,0,false,true,true));
            }
            case FROSTED_BLOOD -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*60*2,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*60*2,1,false,true,true));
            }
            case NIGHT_PASS -> { timer(data,id,"night_pass",now,20L*60L*4L); player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*60*4,0,false,true,true)); }
            case DAY_PASS -> timer(data,id,"day_pass",now,20L*60L*4L);
            case HOLLOW_SKIN -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*90,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*90,0,false,true,true));
            }
            case GRAVE_SILENCE -> timer(data,id,"grave_silence",now,20L*60L*2L);
            case BEAST_MARCH -> timer(data,id,"beast_march",now,20L*90L);
            case GOLEM_ESCORT -> timer(data,id,"golem_escort",now,20L*60L*2L);
            case GOLEM_ENMITY -> timer(data,id,"golem_enmity",now,20L*60L*2L);
            case VILLAGE_EXILE -> data.setUnique(id,"village_exile",true);
            case VILLAGE_PARDON -> clearVillageHostility(data,id);
            case CREEPER_BLESSING -> timer(data,id,"creeper_blessing",now,20L*60L*3L);
            case FALLING_CROWN -> { timer(data,id,"falling_crown",now,20L*60L*2L); player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*60*2,1,false,true,true)); }
            case STONE_SKIN -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*60*2,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*60*2,0,false,true,true));
            }
            case OPEN_SKY -> timer(data,id,"open_sky",now,20L*60L*2L);
            case DEEP_BREATH -> timer(data,id,"deep_breath",now,20L*60L*3L);
            case FOURTH_CARD -> data.setUniqueInt(id,"deck_plus_one",1);
            case FIFTH_CARD -> { data.setUniqueInt(id,"deck_plus_two",1); AttentionSystem.set(player,data,data.attention(id)+2,"the Fifth Finger widened the next hand"); }
            case NARROW_HAND -> data.setUniqueInt(id,"deck_narrow",1);
            case CURSE_DAMPER -> data.setUniqueInt(id,"deck_curse_damper",2);
            case CURSE_BAIT -> data.setUniqueInt(id,"deck_curse_bait",1);
            case RARE_INK -> data.setUniqueInt(id,"deck_rare_ink",1);
            case CLEAN_MARGIN -> data.setUniqueInt(id,"deck_clean_margin",1);
            case BLACK_MARGIN -> data.setUniqueInt(id,"deck_black_margin",1);
            case NO_ECHOES -> data.setUniqueInt(id,"deck_no_echoes",3);
            case REMEDY_WITNESS -> data.setUniqueInt(id,"deck_remedy_witness",1);
            case DEALERS_FOURTH -> data.setUniqueInt(id,"dealer_fourth",1);
            case GOLDEN_CUT -> data.setUniqueInt(id,"deck_golden_cut",2);
            case DEEP_SHELF -> data.setUniqueInt(id,"deck_deep_shelf",1);
            case CURSE_LULL -> data.setUniqueInt(id,"deck_curse_lull",3);
            case REGISTRY_LOTTERY -> {
                String result = AnomalyCardSystem.giveRandomRegisteredItem(player, new java.util.Random(now ^ id.getLeastSignificantBits()), 1, false);
                WardHud.message(player, net.minecraft.network.chat.Component.literal(result).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
            }
            case EGG_WITH_NO_SHELL -> {
                String result = AnomalyCardSystem.giveRandomSpawnEgg(player, new java.util.Random(now ^ id.getMostSignificantBits()));
                WardHud.message(player, net.minecraft.network.chat.Component.literal(result).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
            }
            case SIXTY_FOURTH_STEP -> teleportRandomSurface(player, level, 58, 70);
            case STORM_RECEIPT -> {
                int hit = 0;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20), m -> m instanceof Enemy)) {
                    LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(mob.getX(), mob.getY(), mob.getZ());
                        bolt.setCause(player);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    mob.hurt(level.damageSources().lightningBolt(), 5.0F);
                    if (++hit >= 4) break;
                }
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,20*45,0,false,true,true));
            }
            case BORROWED_FACE -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*60,0,false,true,true));
                List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16), e -> e != player);
                if (!nearby.isEmpty()) nearby.get(player.getRandom().nextInt(nearby.size())).addEffect(new MobEffectInstance(MobEffects.GLOWING,20*60,0,false,true,true));
            }
            case MOB_EXCHANGE -> {
                List<Mob> nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), m -> m instanceof Enemy);
                if (!nearby.isEmpty()) {
                    Mob mob = nearby.get(player.getRandom().nextInt(nearby.size()));
                    double px=player.getX(), py=player.getY(), pz=player.getZ();
                    double mx=mob.getX(), my=mob.getY(), mz=mob.getZ();
                    player.teleportTo(level,mx,my,mz,player.getYRot(),player.getXRot());
                    mob.teleportTo(px,py,pz);
                }
            }
            case BAD_RECEIPT -> {
                ExperienceOrb.award(level,player.position(),20);
                var bad = switch (player.getRandom().nextInt(6)) {
                    case 0 -> MobEffects.WEAKNESS; case 1 -> MobEffects.MOVEMENT_SLOWDOWN; case 2 -> MobEffects.DIG_SLOWDOWN;
                    case 3 -> MobEffects.HUNGER; case 4 -> MobEffects.CONFUSION; default -> MobEffects.UNLUCK;
                };
                player.addEffect(new MobEffectInstance(bad,20*45,player.getRandom().nextBoolean()?1:0,false,true,true));
            }
            case LUCKY_POCKET -> {
                AnomalyCardSystem.giveRandomRegisteredItem(player,new java.util.Random(now ^ 0xA11CE5L),3,true);
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*90,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.UNLUCK,20*90,1,false,true,true));
            }
            case STRANGE_INVITATION -> data.setUniqueInt(id,"anomaly_lure",2);
            case CERTAINLY_NOTHING -> {
                int r=player.getRandom().nextInt(10);
                if (r==0) ExperienceOrb.award(level,player.position(),1);
                else if (r==1) player.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*8,0,false,true,true));
                else if (r==2) {
                    LightningBolt bolt=net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt!=null) { bolt.moveTo(player.getX()+4,player.getY(),player.getZ()+4); level.addFreshEntity(bolt); }
                }
            }
            case FOURTH_SHAPE -> data.setUniqueInt(id,"anomaly_guarantee",1);
            case WHITE_THREAD -> data.setUniqueInt(id,"anomaly_white_thread",1);
            case SCRIBBLED_RESPITE -> data.setUniqueInt(id,"minigame_seconds_chain",3);
            case PALE_MARGIN -> { data.setUnique(id,"minigame_seconds_perm",true); data.addHeartDebt(id,1); }
            default -> { }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.42f, 0.78f + player.getRandom().nextFloat()*0.25f);
        level.sendParticles(ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),8,0.25,0.35,0.25,0.01);
    }

    private static void clearVillageHostility(LockData data, UUID id) {
        for (String key : new String[]{"village_cold","iron_accusation","golem_enmity"})
            data.setUniqueLong(id,key+"_until",Long.MIN_VALUE);
        data.setUnique(id,"village_exile",false);
    }

    private static void applyRandomLaw(ServerPlayer player) {
        int roll = player.getRandom().nextInt(8);
        var effect = switch (roll) {
            case 0 -> MobEffects.MOVEMENT_SPEED;
            case 1 -> MobEffects.DAMAGE_BOOST;
            case 2 -> MobEffects.DIG_SPEED;
            case 3 -> MobEffects.DAMAGE_RESISTANCE;
            case 4 -> MobEffects.INVISIBILITY;
            case 5 -> MobEffects.JUMP;
            case 6 -> MobEffects.HUNGER;
            default -> MobEffects.WEAKNESS;
        };
        player.addEffect(new MobEffectInstance(effect,20*60,roll < 6 ? 1 : 0,false,true,true));
    }

    static void teleportRandomSurface(ServerPlayer player, ServerLevel level, int min, int max) {
        double angle = player.getRandom().nextDouble()*Math.PI*2.0;
        int dist = min + player.getRandom().nextInt(Math.max(1,max-min+1));
        int x = Mth.floor(player.getX()+Math.cos(angle)*dist);
        int z = Mth.floor(player.getZ()+Math.sin(angle)*dist);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        BlockPos pos = new BlockPos(x,y,z);
        if (!level.getWorldBorder().isWithinBounds(pos)) return;
        player.teleportTo(level,x+0.5,y,z+0.5,player.getYRot(),player.getXRot());
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer()==null) return;
        LockData data = LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
        if (until(data,id,"immortal")>now) { event.setAmount(0f); return; }
        int mercy=data.uniqueInt(id,"measured_mercy");
        if (mercy>0 && event.getAmount()>=player.getHealth()) {
            if (player.getHealth() <= 2.0f) {
                event.setAmount(0.0f);
                player.setHealth(2.0f);
            } else {
                event.setAmount(Math.max(0f,player.getHealth()-2f));
            }
            data.setUniqueInt(id,"measured_mercy",mercy-1);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer()==null) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
        if (until(data,id,"falling_crown")>now && event.getSource().is(DamageTypeTags.IS_FALL)) { event.setAmount(0f); return; }
        if (until(data,id,"creeper_blessing")>now && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) event.setAmount(event.getAmount()*0.5f);
        if (until(data,id,"glass_saint")>now) {
            if (event.getSource().getDirectEntity() instanceof Projectile) { event.setAmount(0f); return; }
            event.setAmount(event.getAmount()*1.25f);
        }
        if (until(data,id,"shared_pain")>now && event.getSource().getEntity() instanceof LivingEntity attacker && attacker!=player) {
            float reflected=Math.min(8f,event.getAmount()*0.30f);
            if (reflected>0f) attacker.hurt(player.damageSources().magic(),reflected);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Warden warden)) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(warden) > 48.0*48.0 || player.getServer()==null) continue;
            LockData data=LockData.get(player.getServer());
            if (until(data,player.getUUID(),"empty_deep")>level.getGameTime()) { event.setCanceled(true); return; }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer()==null) return;
        if ((player.tickCount & 7)!=0 || !(player.level() instanceof ServerLevel level)) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=level.getGameTime();

        if (until(data,id,"hundred_steps")<=now && data.uniqueInt(id,"hundred_steps_debt")>0) {
            data.setUniqueInt(id,"hundred_steps_debt",0);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*30,0,false,true,true));
        }
        if (until(data,id,"lantern_break")<=now && data.uniqueInt(id,"lantern_break_debt")>0) {
            data.setUniqueInt(id,"lantern_break_debt",0);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*60*3,0,false,true,true));
        }
        if (until(data,id,"return_address")!=Long.MIN_VALUE && until(data,id,"return_address")<=now) {
            data.setUniqueLong(id,"return_address_until",Long.MIN_VALUE);
            boolean returned=CardTeleportUtil.teleportStored(player,data,id,RETURN_SAFE);
            // Legacy saves from before dimension-aware storage still return safely in the current
            // dimension instead of doing a raw collision-unsafe teleport.
            if(!returned && data.uniqueString(id,RETURN_SAFE+"_dim").isBlank()){
                // Only pre-39.7 XYZ-only saves may use the current-dimension fallback. If a
                // dimension string exists but is malformed/unavailable, fail closed instead.
                long packed=data.uniqueLong(id,RETURN_POS);
                if(packed!=Long.MIN_VALUE){
                    BlockPos pos=BlockPos.of(packed);
                    returned=CardTeleportUtil.teleportSafe(player,level,new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5));
                }
            }
            CardTeleportUtil.clear(data,id,RETURN_SAFE);
            data.setUniqueLong(id,RETURN_POS,Long.MIN_VALUE);
        }
        if (until(data,id,"chorus_error")>now && now>=data.uniqueLong(id,"chorus_next")) {
            data.setUniqueLong(id,"chorus_next",now+20L*20L);
            teleportRandomSurface(player,level,8,18);
        }
        if (until(data,id,"night_pass")>now && level.isNight()) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*12,0,false,false,true));
        if (until(data,id,"day_pass")>now && CardConditions.isDirectDaylight(level,player.blockPosition())) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,20*12,1,false,false,true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*12,0,false,false,true));
        }
        if (until(data,id,"open_sky")>now && level.canSeeSky(player.blockPosition().above())) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*12,1,false,false,true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*12,0,false,false,true));
        }
        if (until(data,id,"deep_breath")>now && player.getBlockY()<=32) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,20*12,1,false,false,true));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*220,0,false,false,true));
        }

        // Availability checks do not have a Level reference, so expired one-shot timers are
        // normalized here. This lets the same strange card return later instead of becoming
        // permanently unavailable merely because its timestamp is still stored.
        for (String key : EXPIRING_KEYS) {
            long until = data.uniqueLong(id,key+"_until");
            if (until != Long.MIN_VALUE && until <= now) data.setUniqueLong(id,key+"_until",Long.MIN_VALUE);
        }
        if (until(data,id,"chorus_error")!=Long.MIN_VALUE && until(data,id,"chorus_error")<=now)
            data.setUniqueLong(id,"chorus_error_until",Long.MIN_VALUE);
        if (until(data,id,"hundred_steps")!=Long.MIN_VALUE && until(data,id,"hundred_steps")<=now && data.uniqueInt(id,"hundred_steps_debt")<=0)
            data.setUniqueLong(id,"hundred_steps_until",Long.MIN_VALUE);
        if (until(data,id,"lantern_break")!=Long.MIN_VALUE && until(data,id,"lantern_break")<=now && data.uniqueInt(id,"lantern_break_debt")<=0)
            data.setUniqueLong(id,"lantern_break_until",Long.MIN_VALUE);

        var box=player.getBoundingBox().inflate(22.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class,box)) {
            if (mob instanceof Warden && until(data,id,"warden_blind")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof Zombie && until(data,id,"zombie_truce")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof AbstractSkeleton && until(data,id,"bone_truce")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof Creeper && (until(data,id,"creeper_truce")>now || until(data,id,"creeper_blessing")>now) && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof Spider && until(data,id,"spider_truce")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof EnderMan && until(data,id,"ender_truce")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof Witch && until(data,id,"witch_truce")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob.getMobType()==net.minecraft.world.entity.MobType.UNDEAD && until(data,id,"grave_silence")>now && mob.getTarget()==player) mob.setTarget(null);
            if (mob instanceof Villager villager && (until(data,id,"village_cold")>now || data.hasUnique(id,"village_exile"))) {
                double dx=villager.getX()-player.getX(), dz=villager.getZ()-player.getZ(); double len=Math.max(0.001,Math.sqrt(dx*dx+dz*dz));
                villager.getNavigation().moveTo(villager.getX()+dx/len*10.0,villager.getY(),villager.getZ()+dz/len*10.0,1.25);
            }
            if (mob instanceof IronGolem golem) {
                if (until(data,id,"iron_accusation")>now || until(data,id,"golem_enmity")>now || data.hasUnique(id,"village_exile")) golem.setTarget(player);
                else if (until(data,id,"golem_escort")>now) {
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    for (Mob candidate : level.getEntitiesOfClass(Mob.class, golem.getBoundingBox().inflate(16.0), m -> m instanceof Enemy)) {
                        double d = golem.distanceToSqr(candidate);
                        if (d < best) { best = d; nearest = candidate; }
                    }
                    if (nearest != null) golem.setTarget(nearest);
                }
            }
            if (mob instanceof Animal animal && until(data,id,"beast_march")>now && animal.distanceToSqr(player)>6.0) animal.getNavigation().moveTo(player,1.15);
            if (mob instanceof Enemy && (until(data,id,"hostile_census")>now || until(data,id,"blackout")>now)) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*12,0,false,false,false));
        }
        if (until(data,id,"hostile_census")>now) player.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*12,0,false,false,false));
    }

    private static long until(LockData data, UUID id, String key) { return data.uniqueLong(id,key+"_until"); }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if (card==null) return "UNKNOWN";
        return switch (card) {
            case MEASURED_MERCY -> data.uniqueInt(id,"measured_mercy")>0 ? "ACTIVE · "+data.uniqueInt(id,"measured_mercy")+" mercy hit(s) remain" : "SPENT";
            case VILLAGE_EXILE -> data.hasUnique(id,"village_exile") ? "ACTIVE SCAR · villagers flee; nearby golems enforce the exile" : "PARDONED";
            case FOURTH_CARD -> deckStatus(data,id,"deck_plus_one","next deck +1 card");
            case FIFTH_CARD -> deckStatus(data,id,"deck_plus_two","next deck +2 cards");
            case NARROW_HAND -> deckStatus(data,id,"deck_narrow","next deck narrower / better ink");
            case CURSE_DAMPER -> deckCount(data,id,"deck_curse_damper","curse-dampened deck(s)");
            case CURSE_BAIT -> deckStatus(data,id,"deck_curse_bait","next deck curse-baited");
            case RARE_INK -> deckStatus(data,id,"deck_rare_ink","next deck rare-inked");
            case CLEAN_MARGIN -> deckStatus(data,id,"deck_clean_margin","next deck clean-margin");
            case BLACK_MARGIN -> deckStatus(data,id,"deck_black_margin","next deck black-margin");
            case NO_ECHOES -> deckCount(data,id,"deck_no_echoes","echo-suppressed deck(s)");
            case REMEDY_WITNESS -> deckStatus(data,id,"deck_remedy_witness","next burdened deck must show a remedy");
            case DEALERS_FOURTH -> deckStatus(data,id,"dealer_fourth","next Sealed Card audience +1");
            case GOLDEN_CUT -> deckCount(data,id,"deck_golden_cut","golden-cut deck(s)");
            case DEEP_SHELF -> deckStatus(data,id,"deck_deep_shelf","next deck reaches for the deep shelf");
            case CURSE_LULL -> deckCount(data,id,"deck_curse_lull","quiet deck(s)");
            case STRANGE_INVITATION -> deckCount(data,id,"anomaly_lure","anomaly-lured deck(s)");
            case FOURTH_SHAPE -> deckStatus(data,id,"anomaly_guarantee","next deck guaranteed anomaly");
            case WHITE_THREAD -> deckStatus(data,id,"anomaly_white_thread","next anomaly restrained");
            case SCRIBBLED_RESPITE -> deckCount(data,id,"minigame_seconds_chain","ward(s) with +3 seconds remaining");
            case PALE_MARGIN -> data.hasUnique(id,"minigame_seconds_perm") ? "ACTIVE WORLD SCAR · all minigames permanently gain +2 seconds; the original heart price can be repaid separately" : "INACTIVE";
            case REGISTRY_LOTTERY, EGG_WITH_NO_SHELL, SIXTY_FOURTH_STEP, STORM_RECEIPT,
                    BORROWED_FACE, MOB_EXCHANGE, BAD_RECEIPT, LUCKY_POCKET, CERTAINLY_NOTHING,
                    WRONG_DOOR, FIRE_WITHOUT_FUEL, WRONG_GRAVITY, RED_MINUTE, TIDAL_BODY, WHITE_NOISE,
                    BLOOD_MONEY, COIN_EDGE, CHANCE_ENGINE, AIR_BORROWED, BURNING_PACT, FROSTED_BLOOD,
                    HOLLOW_SKIN, VILLAGE_PARDON, STONE_SKIN -> "RESOLVED / TEMPORARY EFFECT APPLIED";
            default -> {
                String key=switch(card) {
                    case TEN_SECONDS_UNWRITTEN->"immortal"; case VILLAGE_COLD_SHOULDER->"village_cold";
                    case IRON_ACCUSATION->"iron_accusation"; case WARDENS_BLIND_SPOT->"warden_blind"; case EMPTY_DEEP->"empty_deep";
                    case ZOMBIE_ARMISTICE->"zombie_truce"; case BONE_TRUCE->"bone_truce"; case CREEPER_COURTESY->"creeper_truce";
                    case SPIDER_TREATY->"spider_truce"; case ENDER_AMNESTY->"ender_truce"; case WITCHS_PRIVILEGE->"witch_truce";
                    case HOSTILE_CENSUS->"hostile_census"; case HUNDRED_STEPS->"hundred_steps"; case GLASS_SAINT->"glass_saint";
                    case SHARED_PAIN->"shared_pain"; case CHORUS_ERROR->"chorus_error"; case RETURN_ADDRESS->"return_address";
                    case LANTERN_BREAK->"lantern_break"; case BLACKOUT->"blackout"; case NIGHT_PASS->"night_pass"; case DAY_PASS->"day_pass";
                    case GRAVE_SILENCE->"grave_silence"; case BEAST_MARCH->"beast_march"; case GOLEM_ESCORT->"golem_escort";
                    case GOLEM_ENMITY->"golem_enmity"; case VILLAGE_EXILE->"village_exile"; case CREEPER_BLESSING->"creeper_blessing";
                    case FALLING_CROWN->"falling_crown"; case OPEN_SKY->"open_sky"; case DEEP_BREATH->"deep_breath";
                    default->"";
                };
                long until=key.isEmpty()?Long.MIN_VALUE:data.uniqueLong(id,key+"_until");
                if (until==Long.MIN_VALUE || until<=now) yield "EXPIRED / SPENT";
                long sec=(until-now)/20L; yield "ACTIVE · "+(sec/60)+":"+String.format(java.util.Locale.ROOT,"%02d",sec%60)+" remaining";
            }
        };
    }

    public static String deckSummary(LockData data, UUID id) {
        if (data == null || id == null) return "";
        List<String> parts = new ArrayList<>();
        if (data.uniqueInt(id,"deck_plus_one")>0) parts.add("+1 card");
        if (data.uniqueInt(id,"deck_plus_two")>0) parts.add("+2 cards");
        if (data.uniqueInt(id,"deck_narrow")>0) parts.add("narrow/better");
        if (data.uniqueInt(id,"deck_curse_damper")>0) parts.add("curse dampener ×"+data.uniqueInt(id,"deck_curse_damper"));
        if (data.uniqueInt(id,"deck_curse_bait")>0) parts.add("curse bait");
        if (data.uniqueInt(id,"deck_rare_ink")>0) parts.add("rare ink");
        if (data.uniqueInt(id,"deck_clean_margin")>0) parts.add("clean margin");
        if (data.uniqueInt(id,"deck_black_margin")>0) parts.add("black margin");
        if (data.uniqueInt(id,"deck_no_echoes")>0) parts.add("no echoes ×"+data.uniqueInt(id,"deck_no_echoes"));
        if (data.uniqueInt(id,"deck_remedy_witness")>0) parts.add("forced remedy");
        if (data.uniqueInt(id,"deck_golden_cut")>0) parts.add("golden cut ×"+data.uniqueInt(id,"deck_golden_cut"));
        if (data.uniqueInt(id,"deck_deep_shelf")>0) parts.add("deep shelf");
        if (data.uniqueInt(id,"deck_curse_lull")>0) parts.add("quiet hands ×"+data.uniqueInt(id,"deck_curse_lull"));
        if (data.uniqueInt(id,"dealer_fourth")>0) parts.add("next dealer +1");
        if (data.uniqueInt(id,"anomaly_lure")>0) parts.add("anomaly lure ×"+data.uniqueInt(id,"anomaly_lure"));
        if (data.uniqueInt(id,"anomaly_guarantee")>0) parts.add("guaranteed anomaly");
        if (data.uniqueInt(id,"anomaly_white_thread")>0) parts.add("restrained anomaly");
        if (data.uniqueInt(id,"anomaly_deck_cooldown")>0) parts.add("anomaly quiet ×"+data.uniqueInt(id,"anomaly_deck_cooldown"));
        return String.join(" · ",parts);
    }

    private static String deckStatus(LockData data, UUID id, String key, String label) { return data.uniqueInt(id,key)>0?"QUEUED · "+label:"SPENT"; }
    private static String deckCount(LockData data, UUID id, String key, String label) { int n=data.uniqueInt(id,key); return n>0?"QUEUED · "+n+" "+label:"SPENT"; }
}
