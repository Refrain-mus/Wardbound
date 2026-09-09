package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime implementation for the deliberately stranger late-game catalogue, IDs 340..389. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class ThirdWaveCardEffects {
    private ThirdWaveCardEffects() {}

    private static final long NONE = Long.MIN_VALUE;
    private static final UUID BLOOD_CLOCK_UUID = uuid("third_blood_clock_hand");
    private static final ForbiddenBargain[] TIMED_CARDS = {
            ForbiddenBargain.AIRBORNE_LEDGER, ForbiddenBargain.QUIET_EXECUTION,
            ForbiddenBargain.RED_PURSUIT, ForbiddenBargain.STILL_POINT_BLACK
    };
    private static final Map<UUID, ArrayDeque<Footprint>> FOOTPRINTS = new HashMap<>();
    private static final List<PendingStrike> PENDING = new ArrayList<>();
    private static final Set<UUID> INTERNAL = new HashSet<>();

    private record PendingStrike(UUID owner, net.minecraft.resources.ResourceKey<Level> dimension, UUID target, long due, float damage) {}
    private record Footprint(net.minecraft.resources.ResourceKey<Level> dimension, Vec3 position) {}

    public static boolean isThirdWave(ForbiddenBargain card) {
        return card != null && card.id >= 340 && card.id <= 389;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isThirdWave(card)) return false;
        int masters = defeatedMasters(data,id);
        if (card.id >= 350 && masters < 1) return false;
        if (card.id >= 370 && masters < 2) return false;
        if (card.id >= 380 && masters < 3) return false;
        return switch (card) {
            case DROWN_THE_FUSE -> data.hasUnique(id,"third_kneeling_fuse");
            case BREAK_THE_COUNT -> data.hasUnique(id,"third_thirteenth_step");
            case MOVE_THE_INK -> data.hasUnique(id,"third_stillness_tax");
            case SORT_THE_HAND -> data.hasUnique(id,"third_black_static");
            case CLOSE_THE_CHORUS -> data.hasUnique(id,"third_chorus_debt");
            case CANCEL_AUCTION -> data.hasUnique(id,"third_blind_auction");
            case KNEELING_FUSE -> !data.hasUnique(id,"third_kneeling_fuse");
            case THIRTEENTH_STEP -> !data.hasUnique(id,"third_thirteenth_step");
            case STILLNESS_TAX -> !data.hasUnique(id,"third_stillness_tax");
            case BLACK_STATIC -> !data.hasUnique(id,"third_black_static");
            case CHORUS_DEBT -> !data.hasUnique(id,"third_chorus_debt");
            case BLIND_AUCTION -> !data.hasUnique(id,"third_blind_auction");
            case AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, STILL_POINT_BLACK -> data.uniqueLong(id,timerKey(card)) == NONE;
            default -> !data.hasUnique(id,lawKey(card));
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isThirdWave(card)) return;
        UUID id=player.getUUID(); long now=player.level().getGameTime();
        switch(card) {
            case KNEELING_FUSE -> data.setUnique(id,"third_kneeling_fuse",true);
            case DROWN_THE_FUSE -> data.setUnique(id,"third_kneeling_fuse",false);
            case THIRTEENTH_STEP -> { data.setUnique(id,"third_thirteenth_step",true); data.setUniqueInt(id,"third_step_centis",0); }
            case BREAK_THE_COUNT -> data.setUnique(id,"third_thirteenth_step",false);
            case STILLNESS_TAX -> data.setUnique(id,"third_stillness_tax",true);
            case MOVE_THE_INK -> data.setUnique(id,"third_stillness_tax",false);
            case BLACK_STATIC -> { data.setUnique(id,"third_black_static",true); data.setUniqueLong(id,"third_static_next",now+20L*35L); }
            case SORT_THE_HAND -> data.setUnique(id,"third_black_static",false);
            case CHORUS_DEBT -> data.setUnique(id,"third_chorus_debt",true);
            case CLOSE_THE_CHORUS -> data.setUnique(id,"third_chorus_debt",false);
            case BLIND_AUCTION -> { data.setUnique(id,"third_blind_auction",true); data.setUniqueLong(id,"third_blind_next",now+20L*75L); }
            case CANCEL_AUCTION -> data.setUnique(id,"third_blind_auction",false);
            case AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, STILL_POINT_BLACK -> timer(data,id,card,now,240);
            case FURNACE_HEART, NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL -> { data.setUnique(id,lawKey(card),true); data.addDeathHeartDebt(id,1); }
            case HOLLOW_CROWN, UNWRITTEN_REMAINDER -> { data.setUnique(id,lawKey(card),true); data.addDeathHeartDebt(id,2); }
            default -> data.setUnique(id,lawKey(card),true);
        }
        if (card == ForbiddenBargain.UNWRITTEN_REMAINDER) data.setUniqueLong(id,"third_remainder_ready",now);
    }

    public static boolean hasLesserBurden(LockData data, UUID id) {
        return data != null && id != null && (data.hasUnique(id,"third_kneeling_fuse") || data.hasUnique(id,"third_thirteenth_step")
                || data.hasUnique(id,"third_stillness_tax") || data.hasUnique(id,"third_black_static")
                || data.hasUnique(id,"third_chorus_debt") || data.hasUnique(id,"third_blind_auction"));
    }

    public static boolean clearOneLesserBurden(LockData data, UUID id) {
        if(data==null||id==null)return false;
        String[] keys={"third_kneeling_fuse","third_thirteenth_step","third_stillness_tax","third_black_static","third_chorus_debt","third_blind_auction"};
        for(String key:keys) if(data.hasUnique(id,key)){data.setUnique(id,key,false);return true;}
        return false;
    }

    public static void absolve(LockData data, UUID id) {
        if(data==null||id==null)return;
        data.setUnique(id,"third_kneeling_fuse",false);
        data.setUnique(id,"third_thirteenth_step",false);
        data.setUnique(id,"third_stillness_tax",false);
        data.setUnique(id,"third_black_static",false);
        data.setUnique(id,"third_chorus_debt",false);
        data.setUnique(id,"third_blind_auction",false);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer()==null) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
        ServerLevel level=player.serverLevel();

        if(player.tickCount%10==0){
            if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_FOOTPRINT))){
                ArrayDeque<Footprint> q=FOOTPRINTS.computeIfAbsent(id,k->new ArrayDeque<>());
                // Portal travel starts a new footprint history. A combat rewind must never reuse an
                // old dimension's XYZ in the new dimension.
                if(!q.isEmpty()&&!q.peekLast().dimension().equals(player.level().dimension()))q.clear();
                q.addLast(new Footprint(player.level().dimension(),player.position()));
                while(q.size()>9)q.removeFirst();
            }else FOOTPRINTS.remove(id);
            // Only four cards in this wave are timed. The old loop scanned the entire 628-card enum
            // every ten ticks for every player.
            for(ForbiddenBargain card:TIMED_CARDS) active(data,id,card,now);
        }

        boolean kneelingFuse=data.hasUnique(id,"third_kneeling_fuse");
        if(kneelingFuse){
            boolean sneaking=player.isShiftKeyDown();
            int previousSneak=data.uniqueInt(id,"third_sneak_prev");
            boolean wasSneaking=previousSneak!=0;
            if(sneaking && !wasSneaking && now>=data.uniqueLong(id,"third_fuse_ready")){
                data.setUniqueLong(id,"third_fuse_ready",now+12);
                level.playSound(null,player.blockPosition(),SoundEvents.TNT_PRIMED, SoundSource.PLAYERS,.9f,.78f);
                level.explode(player,player.getX(),player.getY()+.05,player.getZ(),3.0f,Level.ExplosionInteraction.BLOCK);
            }
            int nextSneak=sneaking?1:0;
            if(previousSneak!=nextSneak)data.setUniqueInt(id,"third_sneak_prev",nextSneak);
        } else if(data.uniqueInt(id,"third_sneak_prev")!=0) data.setUniqueInt(id,"third_sneak_prev",0);

        if(data.hasUnique(id,"third_thirteenth_step")){
            int centis=data.uniqueInt(id,"third_step_centis")+(int)Math.round(player.getDeltaMovement().multiply(1,0,1).length()*100.0);
            if(centis>=1300){ centis-=1300; visualLightning(level,player,player.position()); player.hurt(level.damageSources().lightningBolt(),4.0F); }
            data.setUniqueInt(id,"third_step_centis",Math.min(2600,centis));
        }

        if(data.hasUnique(id,"third_stillness_tax")){
            boolean still=player.onGround() && player.getDeltaMovement().horizontalDistanceSqr()<0.0009;
            int ticks=still?data.uniqueInt(id,"third_still_ticks")+1:0;
            if(ticks>=120){
                ticks=0; double a=player.getRandom().nextDouble()*Math.PI*2;
                player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(a)*1.25,.48,Math.sin(a)*1.25));
                player.hurtMarked=true; player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,20*4,0,false,true,true));
                level.playSound(null,player.blockPosition(),SoundEvents.WARDEN_SONIC_BOOM,SoundSource.PLAYERS,.38f,1.35f);
            }
            data.setUniqueInt(id,"third_still_ticks",ticks);
        }

        if(data.hasUnique(id,"third_black_static") && now>=data.uniqueLong(id,"third_static_next")){
            rotateHotbar(player,1+player.getRandom().nextInt(8));
            data.setUniqueLong(id,"third_static_next",now+20L*(35L+player.getRandom().nextInt(26)));
            level.playSound(null,player.blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.PLAYERS,.7f,.45f);
        }
        if(data.hasUnique(id,"third_blind_auction") && now>=data.uniqueLong(id,"third_blind_next")){
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,20*8,0,false,true,true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*8,1,false,true,true));
            data.setUniqueLong(id,"third_blind_next",now+20L*75L);
            level.playSound(null,player.blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.PLAYERS,.55f,.62f);
        }

        if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_FOOTPRINT))) {
            // footprint queue is sampled above; the trigger itself lives in LivingHurtEvent.
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.BONE_MAGNET)) && player.tickCount%5==0){
            for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(8),m->m instanceof Enemy && m.isAlive() && m.getHealth()<=m.getMaxHealth()*.35f)){
                Vec3 d=player.position().subtract(mob.position()).multiply(1,0,1); if(d.lengthSqr()>.01){d=d.normalize().scale(.085);mob.setDeltaMovement(mob.getDeltaMovement().add(d.x,.01,d.z));mob.hurtMarked=true;}
            }
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.BLOOD_CLOCK_HAND))){
            double missing=1.0-player.getHealth()/Math.max(1.0,player.getMaxHealth());
            syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),BLOOD_CLOCK_UUID,"Wardbound blood clock",Math.max(0,missing*.85));
        } else syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),BLOOD_CLOCK_UUID,"Wardbound blood clock",0);

        if(data.hasUnique(id,lawKey(ForbiddenBargain.BELL_WITHOUT_SOUND)) && now>=data.uniqueLong(id,"third_silent_bell_next")){
            LivingEntity nearest=nearestHostile(level,player,20);
            if(nearest!=null){
                nearest.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*10,0,false,true,true));
                nearest.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*10,0,false,true,true));
                data.setUniqueInt(id,"third_silent_bell_target",nearest.getId()); data.setUniqueLong(id,"third_silent_bell_until",now+20*10L);
                level.sendParticles(ParticleTypes.END_ROD,nearest.getX(),nearest.getY()+nearest.getBbHeight()*.6,nearest.getZ(),12,.4,.6,.4,.02);
            }
            data.setUniqueLong(id,"third_silent_bell_next",now+20L*30L);
        }

        boolean airborneLedger=active(data,id,ForbiddenBargain.AIRBORNE_LEDGER,now);
        if(airborneLedger){
            boolean grounded=player.onGround();
            int previousGround=data.uniqueInt(id,"third_ground_prev");
            boolean wasGrounded=previousGround!=0;
            if(grounded && !wasGrounded){
                int charges=data.uniqueInt(id,"third_airborne_charge");
                if(charges>0){
                    float damage=3.5F+charges*3.0F; double radius=3.5+charges;
                    for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(radius),m->m instanceof Enemy&&m.isAlive()))internalHurt(id,mob,level,damage);
                    level.sendParticles(ParticleTypes.EXPLOSION,player.getX(),player.getY()+.1,player.getZ(),charges+2,1.1,.1,1.1,.02);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,player.getX(),player.getY()+.25,player.getZ(),8+charges*5,radius*.35,.2,radius*.35,.02);
                    data.setUniqueInt(id,"third_airborne_charge",0);
                }
            }
            int nextGround=grounded?1:0;
            if(previousGround!=nextGround)data.setUniqueInt(id,"third_ground_prev",nextGround);
        } else {
            if(data.uniqueInt(id,"third_ground_prev")!=0)data.setUniqueInt(id,"third_ground_prev",0);
            if(data.uniqueInt(id,"third_airborne_charge")!=0)data.setUniqueInt(id,"third_airborne_charge",0);
        }

        if(active(data,id,ForbiddenBargain.STILL_POINT_BLACK,now)){
            int previousTicks=data.uniqueInt(id,"third_still_point_ticks");
            int still=player.onGround()&&player.getDeltaMovement().horizontalDistanceSqr()<0.0006?previousTicks+1:0;
            int nextTicks=Math.min(80,still);
            if(previousTicks!=nextTicks)data.setUniqueInt(id,"third_still_point_ticks",nextTicks);
            if(still>=60 && data.uniqueInt(id,"third_still_point_charge")==0)data.setUniqueInt(id,"third_still_point_charge",1);
        } else {
            if(data.uniqueInt(id,"third_still_point_ticks")!=0)data.setUniqueInt(id,"third_still_point_ticks",0);
            if(data.uniqueInt(id,"third_still_point_charge")!=0)data.setUniqueInt(id,"third_still_point_charge",0);
        }

        if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_WARDEN)) && hostileCount(level,player,9)>=5 && now>=data.uniqueLong(id,"third_warden_next")){
            for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(8),m->m instanceof Enemy)){
                Vec3 d=mob.position().subtract(player.position()).multiply(1,0,1); if(d.lengthSqr()>.01){d=d.normalize();mob.push(d.x*1.15,.35,d.z*1.15);}
            }
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*12,0,false,true,true));
            level.sendParticles(ParticleTypes.SONIC_BOOM,player.getX(),player.getY()+1,player.getZ(),1,0,0,0,0);
            data.setUniqueLong(id,"third_warden_next",now+20L*20L);
        }

        if(data.hasUnique(id,lawKey(ForbiddenBargain.FURNACE_HEART)) && player.tickCount%20==0){
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,50,0,true,false,true));
            if(player.isInWaterOrBubble()) player.hurt(level.damageSources().magic(),1.0F);
            else if(player.isOnFire() || player.isInLava()) player.heal(1.0F);
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.KING_IN_RAGS)) && player.tickCount%20==0){
            int armor=player.getArmorValue();
            if(armor<=6){refresh(player,MobEffects.DAMAGE_BOOST,1);refresh(player,MobEffects.MOVEMENT_SPEED,0);} else if(armor>=18) refresh(player,MobEffects.WEAKNESS,0);
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.IRON_IDOL)) && player.tickCount%20==0 && player.getArmorValue()>=18){refresh(player,MobEffects.DAMAGE_RESISTANCE,0);refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_CARD_DRAWN)) && player.getHealth()<=6.0F && now>=data.uniqueLong(id,"third_last_card_next")){
            emergencyClause(player,level); data.setUniqueLong(id,"third_last_card_next",now+20L*90L);
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.NULL_SPRINT)) && player.isSprinting()){
            player.getFoodData().addExhaustion(.035F);
            if(player.tickCount%10==0){
                for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(2.3),m->m instanceof Enemy && m.isAlive())) internalHurt(id,mob,level,3.0F);
                level.sendParticles(ParticleTypes.SCULK_SOUL,player.getX(),player.getY()+.15,player.getZ(),5,.45,.08,.45,.01);
            }
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.HOLLOW_CROWN)) && player.tickCount%20==0){
            if(player.getArmorValue()==0){refresh(player,MobEffects.DAMAGE_BOOST,1);refresh(player,MobEffects.MOVEMENT_SPEED,1);refresh(player,MobEffects.DAMAGE_RESISTANCE,0);} else {refresh(player,MobEffects.WEAKNESS,1);refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);}
        }
        if(data.hasUnique(id,lawKey(ForbiddenBargain.AFTERIMAGE_DEBT))){
            if(player.isSprinting()){
                int cm=data.uniqueInt(id,"third_afterimage_cm")+(int)Math.round(player.getDeltaMovement().multiply(1,0,1).length()*100.0);
                if(cm>=2000){cm-=2000;data.setUniqueInt(id,"third_afterimage_charge",1);}data.setUniqueInt(id,"third_afterimage_cm",Math.min(cm,4000));
            }
        }

        if(player.tickCount%2==0 && !PENDING.isEmpty()){
            for(int i=PENDING.size()-1;i>=0;i--){
                PendingStrike ps=PENDING.get(i);
                if(!ps.owner.equals(id))continue;
                if(!ps.dimension.equals(level.dimension())){PENDING.remove(i);continue;}
                if(ps.due>now)continue;
                PENDING.remove(i);
                var ent=level.getEntity(ps.target);
                if(ent instanceof LivingEntity target&&target.isAlive()&&player.distanceToSqr(target)<32*32){
                    internalHurt(id,target,level,ps.damage);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,target.getX(),target.getY()+target.getBbHeight()*.65,target.getZ(),8,.3,.4,.3,.02);
                }
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event){
        if(event.isCanceled())return;
        LivingEntity victim=event.getEntity();
        if(event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer()!=null && !INTERNAL.contains(attacker.getUUID())){
            LockData data=LockData.get(attacker.getServer());UUID id=attacker.getUUID();long now=attacker.level().getGameTime();
            boolean projectile=event.getSource().getDirectEntity() instanceof Projectile; boolean melee=!projectile && event.getSource().getDirectEntity()==attacker;
            if(data.hasUnique(id,lawKey(ForbiddenBargain.WITNESS_MARK))){int target=data.uniqueInt(id,"third_witness_target");long until=data.uniqueLong(id,"third_witness_until");if(target==victim.getId()&&until>now)event.setAmount(event.getAmount()*1.35F);else if(until>now)event.setAmount(event.getAmount()*.90F);}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.BLACKOUT_CLAUSE))){int light=attacker.serverLevel().getMaxLocalRawBrightness(attacker.blockPosition());event.setAmount(event.getAmount()*(light<=3?1.30F:light>=12?.92F:1F));}
            if(melee && data.hasUnique(id,lawKey(ForbiddenBargain.DEAD_MANS_MARGIN)) && attacker.getHealth()<=attacker.getMaxHealth()*.25F){event.setAmount(event.getAmount()*1.65F);Vec3 d=victim.position().subtract(attacker.position()).multiply(1,0,1);if(d.lengthSqr()>.01){d=d.normalize();victim.push(d.x*.75,.18,d.z*.75);}}
            if(melee && data.hasUnique(id,lawKey(ForbiddenBargain.BLOOD_TELEGRAM))){float echo=Math.min(8f,event.getAmount()*.25F);int n=0;for(LivingEntity other:attacker.serverLevel().getEntitiesOfClass(LivingEntity.class,victim.getBoundingBox().inflate(12),e->e!=victim&&e.isAlive()&&e.getType()==victim.getType())){internalHurt(id,other,attacker.serverLevel(),echo);if(++n>=4)break;}}
            if(melee && data.hasUnique(id,lawKey(ForbiddenBargain.WOUND_EXCHANGE)) && attacker.getHealth()<=attacker.getMaxHealth()*.25F && now>=data.uniqueLong(id,"third_wound_exchange_next")){internalHurt(id,victim,attacker.serverLevel(),6.0F);attacker.heal(6.0F);data.setUniqueLong(id,"third_wound_exchange_next",now+20L*45L);}
            if(projectile && active(data,id,ForbiddenBargain.STILL_POINT_BLACK,now) && data.uniqueInt(id,"third_still_point_charge")>0){event.setAmount(event.getAmount()*2.0F);data.setUniqueInt(id,"third_still_point_charge",0);data.setUniqueInt(id,"third_still_point_ticks",0);}
            if(projectile && data.hasUnique(id,lawKey(ForbiddenBargain.PALE_RECOIL))){event.setAmount(event.getAmount()*1.70F);attacker.hurt(attacker.damageSources().magic(),1.0F);Vec3 back=attacker.getLookAngle().multiply(-.32,0,-.32);attacker.setDeltaMovement(attacker.getDeltaMovement().add(back));attacker.hurtMarked=true;}
            if(melee && data.hasUnique(id,lawKey(ForbiddenBargain.AFTERIMAGE_DEBT)) && data.uniqueInt(id,"third_afterimage_charge")>0){data.setUniqueInt(id,"third_afterimage_charge",0);PENDING.add(new PendingStrike(id,attacker.level().dimension(),victim.getUUID(),now+8,Math.max(1f,event.getAmount()*.40F)));}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.DEBT_OF_DISTANCE))){double dist=attacker.distanceTo(victim);if(dist<=2.5)event.setAmount(event.getAmount()*.80F);else if(dist>=6)event.setAmount(event.getAmount()*(1F+(float)Math.min(.60,(dist-6)*.04)));}
            if(active(data,id,ForbiddenBargain.RED_PURSUIT,now)){data.setUniqueInt(id,"third_red_pursuit_target",victim.getId());data.setUniqueLong(id,"third_red_pursuit_until",now+20L*5L);}
        }

        if(victim instanceof ServerPlayer player && player.getServer()!=null){
            LockData data=LockData.get(player.getServer());UUID id=player.getUUID();long now=player.level().getGameTime();ServerLevel level=player.serverLevel();
            if(data.hasUnique(id,lawKey(ForbiddenBargain.WITNESS_MARK)) && event.getSource().getEntity() instanceof LivingEntity attacker && attacker!=player){data.setUniqueInt(id,"third_witness_target",attacker.getId());data.setUniqueLong(id,"third_witness_until",now+20L*20L);attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*20,0,false,true,true));}
            if(data.hasUnique(id,"third_chorus_debt") && event.getAmount()>=8F && player.getRandom().nextFloat()<.65F) chorusStep(player,level);
            if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_FOOTPRINT)) && player.getHealth()<=player.getMaxHealth()*.35F && now>=data.uniqueLong(id,"third_footprint_next")){
                ArrayDeque<Footprint> q=FOOTPRINTS.get(id);
                if(q!=null&&!q.isEmpty()){
                    Footprint fp=q.peekFirst();
                    if(fp!=null&&fp.dimension().equals(player.level().dimension())&&CardTeleportUtil.safeAt(level,player,fp.position())){
                        Vec3 dest=fp.position();
                        CardTeleportUtil.teleportSafe(player,level,dest);
                        player.heal(2.0F);data.setUniqueLong(id,"third_footprint_next",now+20L*90L);
                        level.sendParticles(ParticleTypes.PORTAL,dest.x,dest.y+1,dest.z,24,.5,.8,.5,.05);
                    }
                }
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.RETURN_TO_SENDER)) && event.getSource().getDirectEntity() instanceof Projectile && event.getSource().getEntity() instanceof LivingEntity shooter && now>=data.uniqueLong(id,"third_return_next")){event.setAmount(event.getAmount()*.50F);teleportToFlank(player,level,shooter);data.setUniqueLong(id,"third_return_next",now+20L*30L);}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.ASH_RECOIL)) && event.getSource().is(DamageTypeTags.IS_EXPLOSION)){event.setAmount(event.getAmount()*.55F);player.setDeltaMovement(player.getDeltaMovement().add(0,.72,0));player.hurtMarked=true;player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*5,0,false,true,true));}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.PROJECTILE_AMNESTY)) && event.getSource().getDirectEntity() instanceof Projectile p){int n=data.uniqueInt(id,"third_projectile_count")+1;if(n>=5){n=0;event.setAmount(0F);p.discard();ExperienceOrb.award(level,player.position(),2);level.sendParticles(ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),12,.45,.6,.45,.03);}data.setUniqueInt(id,"third_projectile_count",n);}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.LOANED_MOMENT)) && event.getAmount()>=8F && now>=data.uniqueLong(id,"third_loaned_moment_next")){for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(10),m->m instanceof Enemy))mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*3,3,false,true,true));player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*3,1,false,true,true));data.setUniqueLong(id,"third_loaned_moment_next",now+20L*20L);}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.CROOKED_PARALLAX)) && event.getSource().getDirectEntity() instanceof Projectile){Vec3 h=player.getDeltaMovement().multiply(1,0,1);Vec3 look=player.getLookAngle().multiply(1,0,1);if(look.lengthSqr()>.001){look=look.normalize();double f=h.dot(look),side=Math.max(0,h.lengthSqr()-f*f);if(side>.035)event.setAmount(event.getAmount()*.60F);}}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.GRAVE_INTEREST)) && player.experienceLevel>=30 && event.getAmount()>0){event.setAmount(event.getAmount()*.70F);player.giveExperiencePoints(-2);}
            if(data.hasUnique(id,lawKey(ForbiddenBargain.RED_ECHO)) && event.getSource().getEntity() instanceof LivingEntity attacker && !(event.getSource().getDirectEntity() instanceof Projectile)){float original=event.getAmount();event.setAmount(original*1.25F);attacker.hurt(player.damageSources().magic(),Math.min(10F,original*.35F));}
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getServer()==null)return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();long now=player.level().getGameTime();
        if(data.hasUnique(id,lawKey(ForbiddenBargain.UNWRITTEN_REMAINDER)) && event.getAmount()>=player.getHealth() && now>=data.uniqueLong(id,"third_remainder_ready")){
            event.setAmount(Math.max(0,player.getHealth()-1.0F));
            data.setUniqueLong(id,"third_remainder_ready",now+20L*60L*10L);
            if(!eraseOrdinaryHotbarStack(player)) player.giveExperienceLevels(-Math.min(10,player.experienceLevel));
            player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),SoundSource.PLAYERS,.9f,.48f);
            player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,player.getX(),player.getY()+1,player.getZ(),36,.7,.9,.7,.08);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getServer()==null||event.getDistance()<8F)return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();long now=player.level().getGameTime();
        if(!data.hasUnique(id,lawKey(ForbiddenBargain.SECOND_GRAVITY))||now<data.uniqueLong(id,"third_second_gravity_next"))return;
        event.setDamageMultiplier(0F);data.setUniqueLong(id,"third_second_gravity_next",now+20L*30L);float dmg=Math.min(14F,3F+event.getDistance()*.45F);
        for(Mob mob:player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(6),m->m instanceof Enemy))internalHurt(id,mob,player.serverLevel(),dmg);
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION,player.getX(),player.getY()+.1,player.getZ(),4,1.2,.1,1.2,.02);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer player)||player.getServer()==null||!(event.getEntity() instanceof Enemy))return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();long now=player.level().getGameTime();ServerLevel level=player.serverLevel();LivingEntity dead=event.getEntity();
        if(active(data,id,ForbiddenBargain.AIRBORNE_LEDGER,now) && !player.onGround())data.setUniqueInt(id,"third_airborne_charge",Math.min(3,data.uniqueInt(id,"third_airborne_charge")+1));
        if(active(data,id,ForbiddenBargain.QUIET_EXECUTION,now) && player.isShiftKeyDown()){player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*6,0,false,true,true));player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*4,1,false,true,true));}
        if(active(data,id,ForbiddenBargain.RED_PURSUIT,now) && data.uniqueInt(id,"third_red_pursuit_target")==dead.getId() && data.uniqueLong(id,"third_red_pursuit_until")>=now){player.heal(4F);player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*5,0,false,true,true));data.setUniqueInt(id,"third_red_pursuit_target",-1);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.BLACKOUT_CLAUSE)) && level.getMaxLocalRawBrightness(player.blockPosition())<=3){player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*5,0,false,true,true));player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*4,1,false,true,true));}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.STOLEN_COUNTENANCE))){for(MobEffectInstance effect:dead.getActiveEffects()){if(effect.getEffect().getCategory()==MobEffectCategory.BENEFICIAL){player.addEffect(new MobEffectInstance(effect.getEffect(),Math.min(20*30,effect.getDuration()),Math.min(2,effect.getAmplifier()),false,true,true));break;}}}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.HOUSE_ALWAYS_WINS))){int n=data.uniqueInt(id,"third_house_count")+1;if(n==7){ExperienceOrb.award(level,player.position(),18);player.addEffect(new MobEffectInstance(MobEffects.LUCK,20*30,1,false,true,true));}else if(n>=8){n=0;player.getFoodData().setFoodLevel(Math.max(0,player.getFoodData().getFoodLevel()-6));player.removeEffect(MobEffects.ABSORPTION);player.addEffect(new MobEffectInstance(MobEffects.UNLUCK,20*20,0,false,true,true));}data.setUniqueInt(id,"third_house_count",n);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.CROW_TOLL))){int n=data.uniqueInt(id,"third_crow_toll")+1;if(n>=12){n=0;for(Projectile p:level.getEntitiesOfClass(Projectile.class,player.getBoundingBox().inflate(8),p->p.isAlive()))p.discard();player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*30,1,false,true,true));level.playSound(null,player.blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.PLAYERS,.8f,.58f);}data.setUniqueInt(id,"third_crow_toll",n);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.BELL_WITHOUT_SOUND)) && data.uniqueInt(id,"third_silent_bell_target")==dead.getId() && data.uniqueLong(id,"third_silent_bell_until")>=now)ExperienceOrb.award(level,player.position(),7);
        if(data.hasUnique(id,lawKey(ForbiddenBargain.FIFTH_TOLL))){int n=data.uniqueInt(id,"third_fifth_toll")+1;if(n>=5){n=0;visualLightning(level,player,dead.position());for(Mob mob:level.getEntitiesOfClass(Mob.class,dead.getBoundingBox().inflate(6),m->m instanceof Enemy&&m!=dead))internalHurt(id,mob,level,6F);player.hurt(level.damageSources().magic(),2F);}data.setUniqueInt(id,"third_fifth_toll",n);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.FINAL_AUCTION))){int n=data.uniqueInt(id,"third_final_auction")+1;if(n>=40){n=0;auctionLot(player);}data.setUniqueInt(id,"third_final_auction",n);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer player)||player.getServer()==null||!(event.getEntity() instanceof Enemy))return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();if(!data.hasUnique(id,lawKey(ForbiddenBargain.WORLD_OWES_NOTHING)))return;
        int n=data.uniqueInt(id,"third_world_owes_count")+1;
        if(n>=10){n=0;List<ItemEntity> copy=new ArrayList<>(event.getDrops());for(ItemEntity item:copy){ItemStack stack=item.getItem().copy();if(stack.isEmpty())continue;ItemEntity extra=new ItemEntity(item.level(),item.getX(),item.getY(),item.getZ(),stack);event.getDrops().add(extra);}}
        else if(player.getRandom().nextFloat()<.25F)event.getDrops().clear();
        data.setUniqueInt(id,"third_world_owes_count",n);
    }

    public static String status(LockData data,UUID id,ForbiddenBargain card,long now){
        if(!isThirdWave(card))return "INACTIVE";
        return switch(card){
            case DROWN_THE_FUSE, BREAK_THE_COUNT, MOVE_THE_INK, SORT_THE_HAND, CLOSE_THE_CHORUS, CANCEL_AUCTION -> "RESOLVED ON SIGNING";
            case KNEELING_FUSE -> burden(data,id,"third_kneeling_fuse","CURSE");
            case THIRTEENTH_STEP -> burden(data,id,"third_thirteenth_step","CURSE");
            case STILLNESS_TAX -> burden(data,id,"third_stillness_tax","CURSE");
            case BLACK_STATIC -> burden(data,id,"third_black_static","CURSE");
            case CHORUS_DEBT -> burden(data,id,"third_chorus_debt","CURSE");
            case BLIND_AUCTION -> burden(data,id,"third_blind_auction","CURSE");
            case AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, STILL_POINT_BLACK -> {long until=data.uniqueLong(id,timerKey(card));if(until==NONE||until<=now)yield "EXPIRED / READY TO RETURN";long sec=Math.max(0,(until-now)/20L);yield "ACTIVE · "+(sec/60L)+":"+String.format(java.util.Locale.ROOT,"%02d",sec%60L)+" remaining";}
            default -> data.hasUnique(id,lawKey(card))?"ACTIVE WORLD LAW":"INACTIVE";
        };
    }

    public static String reliefText(ForbiddenBargain card){
        return switch(card){
            case KNEELING_FUSE -> "Drown the Fuse, Debt Unwritten or Absolution removes the curse.";
            case THIRTEENTH_STEP -> "Break the Count, Debt Unwritten or Absolution removes the curse.";
            case STILLNESS_TAX -> "Move the Ink, Debt Unwritten or Absolution removes the curse.";
            case BLACK_STATIC -> "Sort the Hand, Debt Unwritten or Absolution removes the curse.";
            case CHORUS_DEBT -> "Close the Chorus, Debt Unwritten or Absolution removes the curse.";
            case BLIND_AUCTION -> "Cancel the Auction, Debt Unwritten or Absolution removes the curse.";
            case DROWN_THE_FUSE, BREAK_THE_COUNT, MOVE_THE_INK, SORT_THE_HAND, CLOSE_THE_CHORUS, CANCEL_AUCTION -> "This remedy completes its work when signed.";
            case AIRBORNE_LEDGER, QUIET_EXECUTION, RED_PURSUIT, STILL_POINT_BLACK -> "This wager returns after its four-minute term expires.";
            case FURNACE_HEART, NULL_SPRINT, WORLD_OWES_NOTHING, RED_ECHO, PALE_RECOIL, HOLLOW_CROWN, UNWRITTEN_REMAINDER -> "This is a Death law. Ordinary remedies do not erase it.";
            default -> "This is a persistent late-game world law; ordinary milk does not remove it.";
        };
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event){
        FOOTPRINTS.remove(event.getEntity().getUUID());
        UUID id=event.getEntity().getUUID();
        PENDING.removeIf(p->p.owner.equals(id));
        INTERNAL.remove(id);
    }

    private static boolean isTimed(ForbiddenBargain card){return card==ForbiddenBargain.AIRBORNE_LEDGER||card==ForbiddenBargain.QUIET_EXECUTION||card==ForbiddenBargain.RED_PURSUIT||card==ForbiddenBargain.STILL_POINT_BLACK;}
    private static void timer(LockData d,UUID id,ForbiddenBargain card,long now,long seconds){d.setUniqueLong(id,timerKey(card),now+seconds*20L);}
    private static boolean active(LockData d,UUID id,ForbiddenBargain card,long now){long until=d.uniqueLong(id,timerKey(card));if(until==NONE)return false;if(until>now)return true;d.setUniqueLong(id,timerKey(card),NONE);return false;}
    private static String timerKey(ForbiddenBargain card){return "third_card_"+card.id+"_until";}
    private static String lawKey(ForbiddenBargain card){return "third_law_"+card.id;}
    private static int defeatedMasters(LockData d,UUID id){int n=0;if(d.uniqueInt(id,"gambler_defeated")>0)n++;if(d.uniqueInt(id,"curator_defeated")>0)n++;if(d.uniqueInt(id,"notary_defeated")>0)n++;return n;}
    private static String burden(LockData d,UUID id,String key,String type){return d.hasUnique(id,key)?"ACTIVE "+type:"REMOVED / INACTIVE";}
    private static void refresh(ServerPlayer p,net.minecraft.world.effect.MobEffect effect,int amp){p.addEffect(new MobEffectInstance(effect,45,amp,true,false,true));}
    private static int hostileCount(ServerLevel level,LivingEntity around,double radius){return level.getEntitiesOfClass(LivingEntity.class,around.getBoundingBox().inflate(radius),e->e.isAlive()&&e instanceof Enemy).size();}
    private static LivingEntity nearestHostile(ServerLevel level,ServerPlayer player,double radius){LivingEntity best=null;double d=Double.MAX_VALUE;for(LivingEntity e:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),e->e.isAlive()&&e instanceof Enemy)){double q=player.distanceToSqr(e);if(q<d){d=q;best=e;}}return best;}
    private static UUID uuid(String s){return UUID.nameUUIDFromBytes(("wardbound:"+s).getBytes(StandardCharsets.UTF_8));}
    private static void syncModifier(AttributeInstance attr,UUID uuid,String name,double amount){if(attr==null)return;AttributeModifier old=attr.getModifier(uuid);if(Math.abs(amount)<.0001D){if(old!=null)attr.removeModifier(uuid);return;}if(old!=null&&Math.abs(old.getAmount()-amount)<.0001D)return;if(old!=null)attr.removeModifier(uuid);attr.addTransientModifier(new AttributeModifier(uuid,name,amount,AttributeModifier.Operation.ADDITION));}

    private static void rotateHotbar(ServerPlayer p,int shift){List<ItemStack> copy=new ArrayList<>();for(int i=0;i<9;i++)copy.add(p.getInventory().getItem(i).copy());for(int i=0;i<9;i++)p.getInventory().setItem((i+shift)%9,copy.get(i));p.inventoryMenu.broadcastChanges();}
    private static void visualLightning(ServerLevel level,ServerPlayer owner,Vec3 pos){LightningBolt bolt= EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.moveTo(pos.x,pos.y,pos.z);bolt.setCause(owner);bolt.setVisualOnly(true);level.addFreshEntity(bolt);}level.playSound(null,BlockPos.containing(pos),SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.PLAYERS,.35f,1.4f);}
    private static boolean safeAt(ServerPlayer p,ServerLevel level,Vec3 pos){BlockPos feet=BlockPos.containing(pos);return level.getWorldBorder().isWithinBounds(feet)&&level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(), Direction.UP)&&level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()&&level.getBlockState(feet.above()).getCollisionShape(level,feet.above()).isEmpty()&&level.noCollision(p,p.getBoundingBox().move(pos.subtract(p.position())));}
    private static void chorusStep(ServerPlayer p,ServerLevel level){for(int tries=0;tries<8;tries++){double a=p.getRandom().nextDouble()*Math.PI*2,dist=4+p.getRandom().nextDouble()*7;Vec3 dest=p.position().add(Math.cos(a)*dist,0,Math.sin(a)*dist);if(safeAt(p,level,dest)){level.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+1,p.getZ(),18,.4,.7,.4,.05);p.teleportTo(level,dest.x,dest.y,dest.z,p.getYRot(),p.getXRot());level.sendParticles(ParticleTypes.PORTAL,dest.x,dest.y+1,dest.z,18,.4,.7,.4,.05);return;}}}
    private static void teleportToFlank(ServerPlayer p,ServerLevel level,LivingEntity shooter){Vec3 d=p.position().subtract(shooter.position()).multiply(1,0,1);if(d.lengthSqr()<.01)d=new Vec3(1,0,0);d=d.normalize();Vec3 side=new Vec3(-d.z,0,d.x).scale(p.getRandom().nextBoolean()?2.5:-2.5);Vec3 dest=shooter.position().add(side);if(safeAt(p,level,dest)){level.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+1,p.getZ(),14,.35,.6,.35,.04);p.teleportTo(level,dest.x,dest.y,dest.z,p.getYRot(),p.getXRot());}}
    private static void internalHurt(UUID owner,LivingEntity target,ServerLevel level,float amount){if(target==null||!target.isAlive()||amount<=0)return;INTERNAL.add(owner);try{target.hurt(level.damageSources().magic(),amount);}finally{INTERNAL.remove(owner);}}
    private static void emergencyClause(ServerPlayer p,ServerLevel level){switch(p.getRandom().nextInt(5)){case 0->{p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*8,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*20,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*20,1,false,true,true));}case 1->{p.setDeltaMovement(p.getDeltaMovement().add(0,1.1,0));p.hurtMarked=true;p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*10,0,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,20*5,0,false,true,true));}case 2->{level.explode(p,p.getX(),p.getY()+.2,p.getZ(),2.2f,Level.ExplosionInteraction.NONE);p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*6,1,false,true,true));}case 3->{p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,20*10,0,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*10,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*10,0,false,true,true));}default->{p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*10,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*10,0,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,20*4,0,false,true,true));}}level.playSound(null,p.blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.PLAYERS,.8f,.55f);}
    private static void auctionLot(ServerPlayer p){switch(p.getRandom().nextInt(4)){case 0->{p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*120,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*120,1,false,true,true));}case 1->{p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*120,1,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*120,0,false,true,true));}case 2->{p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*120,0,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*120,0,false,true,true));}default->{p.addEffect(new MobEffectInstance(MobEffects.LUCK,20*120,2,false,true,true));p.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*120,0,false,true,true));}}}
    private static boolean eraseOrdinaryHotbarStack(ServerPlayer p){List<Integer> slots=new ArrayList<>();for(int i=0;i<9;i++){ItemStack s=p.getInventory().getItem(i);if(s.isEmpty())continue;var key=BuiltInRegistries.ITEM.getKey(s.getItem());if(key!=null&&!Wardbound.MODID.equals(key.getNamespace()))slots.add(i);}if(slots.isEmpty())return false;int slot=slots.get(p.getRandom().nextInt(slots.size()));p.getInventory().setItem(slot,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();return true;}
}
