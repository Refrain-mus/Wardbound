package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Runtime implementation for card catalogue IDs 290..339. */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class SecondWaveCardEffects {
    private SecondWaveCardEffects() {}

    private static final long NONE = Long.MIN_VALUE;
    private static final ForbiddenBargain[] TIMED_CARDS = java.util.Arrays.stream(ForbiddenBargain.values())
            .filter(SecondWaveCardEffects::isSecondWave).filter(SecondWaveCardEffects::isTimed).toArray(ForbiddenBargain[]::new);
    private static final UUID HEAVY_HAND_UUID = uuid("second_wave_heavy_hand");
    private static final UUID THIN_ARMOR_UUID = uuid("second_wave_thin_armor");
    private static final UUID COLD_IRON_UUID = uuid("second_wave_cold_iron");

    public static boolean isSecondWave(ForbiddenBargain card) {
        return card != null && card.id >= 290 && card.id <= 339;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isSecondWave(card)) return false;
        return switch (card) {
            case WARM_IRON -> data.hasUnique(id, "wave_cold_iron");
            case COLOR_RETURNED -> data.hasUnique(id, "wave_pale_skin");
            case FRESH_AIR -> data.hasUnique(id, "wave_salt_lungs");
            case PAY_THE_EMBER -> data.hasUnique(id, "wave_ember_mortgage");
            case COLD_IRON -> !data.hasUnique(id, "wave_cold_iron");
            case PALE_SKIN -> !data.hasUnique(id, "wave_pale_skin");
            case SALT_LUNGS -> !data.hasUnique(id, "wave_salt_lungs");
            case EMBER_MORTGAGE -> !data.hasUnique(id, "wave_ember_mortgage");
            case GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, RED_LEDGER, BLACK_LEDGER,
                    CANDLE_TAX, IRON_HARVEST, STONE_HARVEST -> data.uniqueInt(id, counterKey(card)) <= 0;
            case GRAVE_CREDIT, EMPTY_PULSE -> !data.hasUnique(id, lawKey(card));
            default -> data.uniqueLong(id, timerKey(card)) == NONE;
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isSecondWave(card)) return;
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        switch (card) {
            case SWIFT_MERCY -> {
                timer(data,id,card,now,90);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*90,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*10,0,false,true,true));
            }
            case IRON_WAKE -> {
                timer(data,id,card,now,75);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*75,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*45,0,false,true,true));
            }
            case MOONWATER_DRAFT -> {
                timer(data,id,card,now,180);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*180,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20*180,0,false,true,true));
            }
            case FURNACE_VEIN -> {
                timer(data,id,card,now,120);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,20*120,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,20*120,0,false,true,true));
            }
            case QUIET_STEP -> timer(data,id,card,now,120);
            case RED_HOUR -> {
                timer(data,id,card,now,60);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*60,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*60,1,false,true,true));
            }
            case PALE_STEP -> {
                timer(data,id,card,now,150);
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*150,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*150,1,false,true,true));
            }
            case HUNGRY_STEEL -> {
                timer(data,id,card,now,120);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*120,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*120,0,false,true,true));
            }
            case WATCHERS_DRAFT -> {
                timer(data,id,card,now,180);
                player.addEffect(new MobEffectInstance(MobEffects.LUCK,20*180,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*60,0,false,true,true));
            }
            case STONE_CHOIR, RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LONG_BREATH, DEEP_STEP,
                    SKY_STEP, HUNTERS_REST, HUNTERS_RUSH -> timer(data,id,card,now,card==ForbiddenBargain.LONG_BREATH?300:card.id>=309?240:180);
            case LAST_MATCH -> {
                timer(data,id,card,now,180);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,20*180,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*30,0,false,true,true));
            }
            case GRAVE_SALT, ASH_DIVIDEND -> data.setUniqueInt(id,counterKey(card),12);
            case FIRST_BLOOD, LAST_ARROW -> data.setUniqueInt(id,counterKey(card),1);
            case RED_LEDGER, BLACK_LEDGER -> data.setUniqueInt(id,counterKey(card),16);
            case CANDLE_TAX -> data.setUniqueInt(id,counterKey(card),8);
            case IRON_HARVEST -> data.setUniqueInt(id,counterKey(card),48);
            case STONE_HARVEST -> data.setUniqueInt(id,counterKey(card),64);
            case GLASS_RUNNER -> {
                timer(data,id,card,now,150);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*150,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*150,1,false,true,true));
            }
            case HEAVY_HAND, THIN_ARMOR, CORPSE_LANTERN -> timer(data,id,card,now,180);
            case WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS -> timer(data,id,card,now,240);
            case COLD_IRON -> data.setUnique(id,"wave_cold_iron",true);
            case WARM_IRON -> data.setUnique(id,"wave_cold_iron",false);
            case PALE_SKIN -> data.setUnique(id,"wave_pale_skin",true);
            case COLOR_RETURNED -> data.setUnique(id,"wave_pale_skin",false);
            case SALT_LUNGS -> data.setUnique(id,"wave_salt_lungs",true);
            case FRESH_AIR -> data.setUnique(id,"wave_salt_lungs",false);
            case EMBER_MORTGAGE -> data.setUnique(id,"wave_ember_mortgage",true);
            case PAY_THE_EMBER -> data.setUnique(id,"wave_ember_mortgage",false);
            case GRAVE_CREDIT -> data.setUnique(id,lawKey(card),true);
            case EMPTY_PULSE -> {
                data.setUnique(id,lawKey(card),true);
                data.addDeathHeartDebt(id,1);
            }
            default -> { }
        }
    }

    public static boolean hasLesserBurden(LockData data, UUID id) {
        return data != null && id != null && (data.hasUnique(id,"wave_cold_iron") || data.hasUnique(id,"wave_pale_skin")
                || data.hasUnique(id,"wave_salt_lungs") || data.hasUnique(id,"wave_ember_mortgage"));
    }

    public static boolean clearOneLesserBurden(LockData data, UUID id) {
        if (data == null || id == null) return false;
        String[] keys={"wave_cold_iron","wave_pale_skin","wave_salt_lungs","wave_ember_mortgage"};
        for(String key:keys) if(data.hasUnique(id,key)){data.setUnique(id,key,false);return true;}
        return false;
    }

    public static void absolve(LockData data, UUID id) {
        if (data == null || id == null) return;
        data.setUnique(id,"wave_cold_iron",false);
        data.setUnique(id,"wave_pale_skin",false);
        data.setUnique(id,"wave_salt_lungs",false);
        data.setUnique(id,"wave_ember_mortgage",false);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer()==null || player.tickCount%10!=0) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
        for(ForbiddenBargain card:TIMED_CARDS) active(data,id,card,now);

        if(active(data,id,ForbiddenBargain.QUIET_STEP,now) && player.isShiftKeyDown()) refresh(player,MobEffects.DAMAGE_RESISTANCE,0);
        if(active(data,id,ForbiddenBargain.STONE_CHOIR,now) && horizontalSpeed(player)<.018D) refresh(player,MobEffects.DIG_SPEED,1);
        if(active(data,id,ForbiddenBargain.RUNNING_DEBT,now) && player.isSprinting()){refresh(player,MobEffects.MOVEMENT_SPEED,1);refresh(player,MobEffects.HUNGER,0);}
        if(active(data,id,ForbiddenBargain.BLACK_CURRENT,now) && player.isInWaterOrBubble()){refresh(player,MobEffects.WATER_BREATHING,0);refresh(player,MobEffects.DOLPHINS_GRACE,0);}
        if(active(data,id,ForbiddenBargain.HOLLOW_LIGHT,now)){
            refresh(player,MobEffects.NIGHT_VISION,0);
            glowHostiles(player,16.0D,false);
        }
        if(active(data,id,ForbiddenBargain.LONG_BREATH,now)){
            refresh(player,MobEffects.WATER_BREATHING,0);
            if(player.isInWaterOrBubble()) refresh(player,MobEffects.REGENERATION,0);
        }
        if(active(data,id,ForbiddenBargain.DEEP_STEP,now) && player.getY()<0) refresh(player,MobEffects.DIG_SPEED,1);
        if(active(data,id,ForbiddenBargain.SKY_STEP,now) && player.getY()>96){refresh(player,MobEffects.MOVEMENT_SPEED,0);refresh(player,MobEffects.JUMP,0);}
        if(active(data,id,ForbiddenBargain.HUNTERS_REST,now) && horizontalSpeed(player)<.018D && player.getHealth()<=player.getMaxHealth()*.5F) refresh(player,MobEffects.REGENERATION,0);
        if(active(data,id,ForbiddenBargain.HUNTERS_RUSH,now) && player.isSprinting()) refresh(player,MobEffects.DAMAGE_BOOST,0);
        if(active(data,id,ForbiddenBargain.CORPSE_LANTERN,now)) glowHostiles(player,18.0D,true);
        boolean wolves=active(data,id,ForbiddenBargain.WOLFS_DEBT,now);
        boolean emptyRoom=active(data,id,ForbiddenBargain.EMPTY_ROOM,now);
        boolean crowdedRoom=active(data,id,ForbiddenBargain.CROWDED_ROOM,now);
        int hostiles=(wolves||emptyRoom||crowdedRoom)?hostileCount(player.serverLevel(),player,10.0D):-1;
        if(wolves && hostiles>=3) refresh(player,MobEffects.DAMAGE_BOOST,0);
        if(emptyRoom && hostiles==0){refresh(player,MobEffects.DAMAGE_RESISTANCE,0);refresh(player,MobEffects.LUCK,0);}
        if(crowdedRoom && hostiles>=4) refresh(player,MobEffects.DAMAGE_RESISTANCE,0);
        if(active(data,id,ForbiddenBargain.RAIN_CLERK,now) && (player.isInWaterOrBubble() || player.serverLevel().isRainingAt(player.blockPosition()))) refresh(player,MobEffects.MOVEMENT_SPEED,0);
        if(active(data,id,ForbiddenBargain.SUN_CLERK,now) && CardConditions.isDirectDaylight(player.serverLevel(),player.blockPosition())) refresh(player,MobEffects.DIG_SPEED,0);
        if(active(data,id,ForbiddenBargain.MOON_CLERK,now) && player.level().isNight()) refresh(player,MobEffects.MOVEMENT_SPEED,0);
        if(active(data,id,ForbiddenBargain.BLOOD_CLOCK,now) && player.getHealth()<=player.getMaxHealth()*.5F){refresh(player,MobEffects.MOVEMENT_SPEED,0);refresh(player,MobEffects.DAMAGE_BOOST,0);}
        if(active(data,id,ForbiddenBargain.CLEAN_HANDS,now) && player.getHealth()>=player.getMaxHealth()*.8F) refresh(player,MobEffects.DAMAGE_RESISTANCE,0);

        if(data.hasUnique(id,"wave_cold_iron")) refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);
        if(data.hasUnique(id,"wave_pale_skin")){
            refresh(player,MobEffects.NIGHT_VISION,0);
            if(CardConditions.isDirectDaylight(player.serverLevel(),player.blockPosition())) refresh(player,MobEffects.WEAKNESS,0);
        }
        if(data.hasUnique(id,"wave_salt_lungs")){
            refresh(player,MobEffects.WATER_BREATHING,0);
            if(!player.isInWaterOrBubble()) refresh(player,MobEffects.HUNGER,0);
        }
        if(data.hasUnique(id,"wave_ember_mortgage")){refresh(player,MobEffects.FIRE_RESISTANCE,0);refresh(player,MobEffects.GLOWING,0);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.EMPTY_PULSE)) && player.getHealth()<=8.0F){refresh(player,MobEffects.MOVEMENT_SPEED,1);refresh(player,MobEffects.DAMAGE_BOOST,0);}

        syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK),HEAVY_HAND_UUID,
                "wardbound.second_wave_heavy_hand",active(data,id,ForbiddenBargain.HEAVY_HAND,now)?1.0D:0D);
        syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),THIN_ARMOR_UUID,
                "wardbound.second_wave_thin_armor",active(data,id,ForbiddenBargain.THIN_ARMOR,now)?-4.0D:0D);
        syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),COLD_IRON_UUID,
                "wardbound.second_wave_cold_iron",data.hasUnique(id,"wave_cold_iron")?.25D:0D);
    }

    @SubscribeEvent(priority= EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if(event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer()!=null){
            LockData data=LockData.get(attacker.getServer()); UUID id=attacker.getUUID();
            boolean projectile=event.getSource().getDirectEntity() instanceof Projectile;
            boolean melee=!projectile && event.getSource().getDirectEntity()==attacker;
            if(melee && data.uniqueInt(id,counterKey(ForbiddenBargain.FIRST_BLOOD))>0){
                event.setAmount(event.getAmount()*1.60F);data.setUniqueInt(id,counterKey(ForbiddenBargain.FIRST_BLOOD),0);attacker.heal(2.0F);
                attacker.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR,event.getEntity().getX(),event.getEntity().getY()+1,event.getEntity().getZ(),8,.22,.25,.22,.02);
            }
            if(projectile && data.uniqueInt(id,counterKey(ForbiddenBargain.LAST_ARROW))>0){
                event.setAmount(event.getAmount()*1.60F);data.setUniqueInt(id,counterKey(ForbiddenBargain.LAST_ARROW),0);attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*5,0,false,true,true));
            }
        }
        if(event.getEntity() instanceof ServerPlayer player && player.getServer()!=null){
            LockData data=LockData.get(player.getServer());UUID id=player.getUUID();long now=player.level().getGameTime();
            if(active(data,id,ForbiddenBargain.GLASS_RUNNER,now)) event.setAmount(event.getAmount()*1.10F);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if(!(event.getSource().getEntity() instanceof ServerPlayer player) || player.getServer()==null || !(event.getEntity() instanceof Enemy)) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID();
        int grave=data.uniqueInt(id,counterKey(ForbiddenBargain.GRAVE_SALT));
        if(grave>0 && event.getEntity().getMobType()== MobType.UNDEAD){data.setUniqueInt(id,counterKey(ForbiddenBargain.GRAVE_SALT),grave-1);ExperienceOrb.award(player.serverLevel(),player.position(),3);}
        int ash=data.uniqueInt(id,counterKey(ForbiddenBargain.ASH_DIVIDEND));
        if(ash>0){int done=13-ash;data.setUniqueInt(id,counterKey(ForbiddenBargain.ASH_DIVIDEND),ash-1);ExperienceOrb.award(player.serverLevel(),player.position(),1);if(done%3==0)player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*6,0,false,true,true));}
        int red=data.uniqueInt(id,counterKey(ForbiddenBargain.RED_LEDGER));
        if(red>0){int done=17-red;data.setUniqueInt(id,counterKey(ForbiddenBargain.RED_LEDGER),red-1);if(done%4==0){player.heal(2.0F);ExperienceOrb.award(player.serverLevel(),player.position(),3);}}
        int black=data.uniqueInt(id,counterKey(ForbiddenBargain.BLACK_LEDGER));
        if(black>0){data.setUniqueInt(id,counterKey(ForbiddenBargain.BLACK_LEDGER),black-1);ExperienceOrb.award(player.serverLevel(),player.position(),3);player.getFoodData().setFoodLevel(Math.max(0,player.getFoodData().getFoodLevel()-1));}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.GRAVE_CREDIT))){int n=data.uniqueInt(id,"wave_grave_credit_kills")+1;if(n>=20){n=0;player.heal(4.0F);ExperienceOrb.award(player.serverLevel(),player.position(),8);player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*15,1,false,true,true));}data.setUniqueInt(id,"wave_grave_credit_kills",n);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if(event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative() || player.getServer()==null) return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();BlockState state=event.getState();BlockPos pos=event.getPos();
        int ore=data.uniqueInt(id,counterKey(ForbiddenBargain.IRON_HARVEST));
        if(ore>0 && isOre(state)){data.setUniqueInt(id,counterKey(ForbiddenBargain.IRON_HARVEST),ore-1);ExperienceOrb.award(player.serverLevel(),net.minecraft.world.phys.Vec3.atCenterOf(pos),1+player.getRandom().nextInt(2));}
        int stone=data.uniqueInt(id,counterKey(ForbiddenBargain.STONE_HARVEST));
        if(stone>0 && state.is(BlockTags.MINEABLE_WITH_PICKAXE)){data.setUniqueInt(id,counterKey(ForbiddenBargain.STONE_HARVEST),stone-1);if(player.getRandom().nextFloat()<.25F)ExperienceOrb.award(player.serverLevel(),net.minecraft.world.phys.Vec3.atCenterOf(pos),1);}
    }

    @SubscribeEvent
    public static void onFoodFinished(LivingEntityUseItemEvent.Finish event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || player.getServer()==null || !event.getItem().isEdible()) return;
        LockData data=LockData.get(player.getServer());UUID id=player.getUUID();int n=data.uniqueInt(id,counterKey(ForbiddenBargain.CANDLE_TAX));
        if(n<=0)return;data.setUniqueInt(id,counterKey(ForbiddenBargain.CANDLE_TAX),n-1);player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*30,0,false,true,true));player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,20*5,0,false,true,true));
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if(!isSecondWave(card))return "INACTIVE";
        return switch(card){
            case WARM_IRON, COLOR_RETURNED, FRESH_AIR, PAY_THE_EMBER -> "RESOLVED ON SIGNING";
            case COLD_IRON -> burden(data,id,"wave_cold_iron","SCAR");
            case PALE_SKIN -> burden(data,id,"wave_pale_skin","SCAR");
            case SALT_LUNGS -> burden(data,id,"wave_salt_lungs","SCAR");
            case EMBER_MORTGAGE -> burden(data,id,"wave_ember_mortgage","CURSE");
            case GRAVE_SALT, ASH_DIVIDEND, FIRST_BLOOD, LAST_ARROW, RED_LEDGER, BLACK_LEDGER, CANDLE_TAX, IRON_HARVEST, STONE_HARVEST -> {
                int n=data.uniqueInt(id,counterKey(card));yield n>0?"ACTIVE · "+n+" charge(s) remain":"SPENT";
            }
            case GRAVE_CREDIT, EMPTY_PULSE -> data.hasUnique(id,lawKey(card))?"ACTIVE WORLD LAW":"INACTIVE";
            default -> {
                long until=data.uniqueLong(id,timerKey(card));if(until==NONE || until<=now)yield "EXPIRED / READY TO RETURN";long sec=Math.max(0,(until-now)/20L);yield "ACTIVE · "+(sec/60L)+":"+String.format(java.util.Locale.ROOT,"%02d",sec%60L)+" remaining";
            }
        };
    }

    public static String reliefText(ForbiddenBargain card) {
        return switch(card){
            case COLD_IRON -> "Warm the Iron or Absolution in Black Ink removes the scar.";
            case PALE_SKIN -> "Color Returned or Absolution in Black Ink removes the scar.";
            case SALT_LUNGS -> "Fresh Air or Absolution in Black Ink removes the scar.";
            case EMBER_MORTGAGE -> "Pay the Ember or Absolution in Black Ink removes the curse.";
            case EMPTY_PULSE -> "This is a Death law. Ordinary remedies do not erase it.";
            case GRAVE_CREDIT -> "This is a persistent world law; ordinary milk does not remove it.";
            case WARM_IRON, COLOR_RETURNED, FRESH_AIR, PAY_THE_EMBER -> "This card completes its work when signed.";
            default -> "This card returns after its timer or written charges are exhausted.";
        };
    }

    private static boolean isTimed(ForbiddenBargain card){
        return switch(card){
            case SWIFT_MERCY, IRON_WAKE, MOONWATER_DRAFT, FURNACE_VEIN, QUIET_STEP, RED_HOUR, PALE_STEP, HUNGRY_STEEL, WATCHERS_DRAFT,
                    STONE_CHOIR, RUNNING_DEBT, BLACK_CURRENT, HOLLOW_LIGHT, LAST_MATCH, LONG_BREATH, DEEP_STEP, SKY_STEP, HUNTERS_REST, HUNTERS_RUSH,
                    GLASS_RUNNER, HEAVY_HAND, THIN_ARMOR, CORPSE_LANTERN, WOLFS_DEBT, EMPTY_ROOM, CROWDED_ROOM, RAIN_CLERK, SUN_CLERK, MOON_CLERK, BLOOD_CLOCK, CLEAN_HANDS -> true;
            default -> false;
        };
    }
    private static void timer(LockData d,UUID id,ForbiddenBargain card,long now,long seconds){d.setUniqueLong(id,timerKey(card),now+seconds*20L);}
    private static boolean active(LockData d,UUID id,ForbiddenBargain card,long now){long until=d.uniqueLong(id,timerKey(card));if(until==NONE)return false;if(until>now)return true;d.setUniqueLong(id,timerKey(card),NONE);return false;}
    private static String timerKey(ForbiddenBargain card){return "wave_card_"+card.id+"_until";}
    private static String counterKey(ForbiddenBargain card){return "wave_card_"+card.id+"_count";}
    private static String lawKey(ForbiddenBargain card){return "wave_law_"+card.id;}
    private static void refresh(ServerPlayer p,net.minecraft.world.effect.MobEffect effect,int amp){p.addEffect(new MobEffectInstance(effect,40,amp,true,false,true));}
    private static double horizontalSpeed(ServerPlayer p){return p.getDeltaMovement().horizontalDistanceSqr();}
    private static int hostileCount(ServerLevel level,LivingEntity around,double radius){return level.getEntitiesOfClass(LivingEntity.class,around.getBoundingBox().inflate(radius),e->e.isAlive()&&e instanceof Enemy).size();}
    private static void glowHostiles(ServerPlayer player,double radius,boolean undeadOnly){for(LivingEntity e:player.serverLevel().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),e->e.isAlive()&&e instanceof Enemy&&(!undeadOnly||e.getMobType()==MobType.UNDEAD)))e.addEffect(new MobEffectInstance(MobEffects.GLOWING,35,0,true,false));}
    private static boolean isOre(BlockState state){String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();return path.endsWith("_ore")||path.contains("ore_");}
    private static UUID uuid(String s){return UUID.nameUUIDFromBytes(("wardbound:"+s).getBytes(StandardCharsets.UTF_8));}
    private static void syncModifier(AttributeInstance attr,UUID uuid,String name,double amount){if(attr==null)return;AttributeModifier old=attr.getModifier(uuid);if(Math.abs(amount)<.0001D){if(old!=null)attr.removeModifier(uuid);return;}if(old!=null&&Math.abs(old.getAmount()-amount)<.0001D)return;if(old!=null)attr.removeModifier(uuid);attr.addTransientModifier(new AttributeModifier(uuid,name,amount,AttributeModifier.Operation.ADDITION));}
    private static String burden(LockData d,UUID id,String key,String type){return d.hasUnique(id,key)?"ACTIVE "+type:"REMOVED / INACTIVE";}
}
