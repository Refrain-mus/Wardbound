package dev.marrowseal.wardbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Additional ordinary-to-endgame bargains added after the first 230-card catalogue.
 * The early half deliberately uses readable, low-complexity clauses so fresh worlds
 * do not open on nothing but high-concept permanent laws. Later cards become more
 * conditional and persistent without bypassing Wardbound's existing rarity shelves.
 */
@Mod.EventBusSubscriber(modid = Wardbound.MODID)
public final class FreshCardEffects {
    private FreshCardEffects() {}

    private static final long NONE = Long.MIN_VALUE;
    private static final UUID IRON_STEP_UUID = uuid("fresh_iron_step");
    private static final ForbiddenBargain[] TIMED_CARDS = java.util.Arrays.stream(ForbiddenBargain.values())
            .filter(FreshCardEffects::isFresh).filter(FreshCardEffects::isTimed).toArray(ForbiddenBargain[]::new);

    public static boolean isFresh(ForbiddenBargain card) {
        return card != null && card.id >= 230 && card.id <= 289;
    }

    public static boolean available(LockData data, UUID id, ForbiddenBargain card) {
        if (data == null || id == null || !isFresh(card)) return false;
        return switch (card) {
            case CUT_TETHER -> data.hasUnique(id, "fresh_tethered_blood");
            case CLEAR_THE_LUNGS -> data.hasUnique(id, "fresh_ashen_lungs");
            case FILL_THE_BONES -> data.hasUnique(id, "fresh_hollow_bones");
            case STILL_THE_NERVE -> data.hasUnique(id, "fresh_clockwork_nerve");
            case PAY_THE_HUNGER -> data.hasUnique(id, "fresh_golden_hunger");
            case QUENCH_THE_CROWN -> data.hasUnique(id, "fresh_ash_crown");

            case TETHERED_BLOOD -> !data.hasUnique(id, "fresh_tethered_blood");
            case ASHEN_LUNGS -> !data.hasUnique(id, "fresh_ashen_lungs");
            case HOLLOW_BONES -> !data.hasUnique(id, "fresh_hollow_bones");
            case CLOCKWORK_NERVE -> !data.hasUnique(id, "fresh_clockwork_nerve");
            case GOLDEN_HUNGER -> !data.hasUnique(id, "fresh_golden_hunger");
            case ASH_CROWN -> !data.hasUnique(id, "fresh_ash_crown");

            case HUNTERS_REBATE -> data.uniqueInt(id, counterKey(card)) <= 0;
            case FIRST_CUT_DOUBLED -> data.uniqueInt(id, counterKey(card)) <= 0;
            case FIRST_ARROW_DOUBLED -> data.uniqueInt(id, counterKey(card)) <= 0;
            case RED_HARVEST, GRAY_HARVEST, SCAVENGER_CLAUSE, MASONS_LUCK,
                    WOODSMANS_SHARE, FARMERS_MARGIN, INKED_APPETITE,
                    CHARCOAL_TONGUE, FROSTED_EDGE, BLOODLESS_VICTORY
                    -> data.uniqueInt(id, counterKey(card)) <= 0;

            case LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON,
                    DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER
                    -> !data.hasUnique(id, lawKey(card));

            default -> data.uniqueLong(id, timerKey(card)) == NONE;
        };
    }

    public static void apply(ServerPlayer player, LockData data, ForbiddenBargain card) {
        if (player == null || data == null || !isFresh(card)) return;
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        switch (card) {
            case QUICKENED_PULSE -> {
                timer(data,id,card,now,120);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*120,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*30,0,false,true,true));
            }
            case STONE_BREATH -> {
                timer(data,id,card,now,90);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*90,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*45,0,false,true,true));
            }
            case SHARPENED_HOUR -> {
                timer(data,id,card,now,90);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,20*90,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*30,0,false,true,true));
            }
            case CLEAR_EYES -> {
                timer(data,id,card,now,240);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,20*240,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING,20*45,0,false,true,true));
            }
            case FEATHER_RECEIPT -> {
                timer(data,id,card,now,180);
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*180,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*90,0,false,true,true));
            }
            case DIVERS_MARGIN -> {
                timer(data,id,card,now,240);
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20*240,0,false,true,true));
            }
            case CINDER_RECEIPT -> {
                timer(data,id,card,now,244);
                data.setUniqueLong(id,"fresh_cinder_resist_start",now+80L);
                player.setSecondsOnFire(4);
            }
            case MINERS_CREDIT -> {
                timer(data,id,card,now,120);
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,20*120,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*45,0,false,true,true));
            }
            case HUNTERS_REBATE -> data.setUniqueInt(id,counterKey(card),8);
            case PALE_BANDAGE -> {
                timer(data,id,card,now,45);
                player.heal(6.0F);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*45,0,false,true,true));
            }
            case BLACK_BREAD -> {
                timer(data,id,card,now,60);
                player.getFoodData().eat(6,0.45F);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*60,0,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,20*10,0,false,true,true));
            }
            case FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED -> data.setUniqueInt(id,counterKey(card),1);
            case THIN_AIR -> {
                timer(data,id,card,now,60);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*60,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP,20*60,1,false,true,true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER,20*60,0,false,true,true));
            }
            case STANDING_ORDER, RUNNING_ORDER -> timer(data,id,card,now,120);
            case OPEN_SKY_LEDGER, DEEP_INK -> timer(data,id,card,now,180);
            case RED_HARVEST, GRAY_HARVEST -> data.setUniqueInt(id,counterKey(card),12);
            case SCAVENGER_CLAUSE -> data.setUniqueInt(id,counterKey(card),16);
            case MASONS_LUCK -> data.setUniqueInt(id,counterKey(card),64);
            case WOODSMANS_SHARE -> data.setUniqueInt(id,counterKey(card),48);
            case FARMERS_MARGIN -> data.setUniqueInt(id,counterKey(card),32);
            case IRON_STEP -> timer(data,id,card,now,180);
            case GLASS_STEP -> {
                timer(data,id,card,now,120);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,20*120,1,false,true,true));
            }
            case WARD_LANTERN, QUIET_MOUTH, LOUD_NAME -> timer(data,id,card,now,120);
            case SECOND_WIND -> {
                timer(data,id,card,now,180);
                data.setUniqueInt(id,"fresh_second_wind_ready",1);
            }
            case INKED_APPETITE -> data.setUniqueInt(id,counterKey(card),6);
            case CHARCOAL_TONGUE, FROSTED_EDGE -> data.setUniqueInt(id,counterKey(card),10);

            case TETHERED_BLOOD -> data.setUnique(id,"fresh_tethered_blood",true);
            case CUT_TETHER -> data.setUnique(id,"fresh_tethered_blood",false);
            case ASHEN_LUNGS -> data.setUnique(id,"fresh_ashen_lungs",true);
            case CLEAR_THE_LUNGS -> data.setUnique(id,"fresh_ashen_lungs",false);
            case HOLLOW_BONES -> data.setUnique(id,"fresh_hollow_bones",true);
            case FILL_THE_BONES -> data.setUnique(id,"fresh_hollow_bones",false);
            case CLOCKWORK_NERVE -> data.setUnique(id,"fresh_clockwork_nerve",true);
            case STILL_THE_NERVE -> data.setUnique(id,"fresh_clockwork_nerve",false);

            case BLOODLESS_VICTORY -> data.setUniqueInt(id,counterKey(card),15);
            case WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER
                    -> timer(data,id,card,now,300);
            case GOLDEN_HUNGER -> data.setUnique(id,"fresh_golden_hunger",true);
            case PAY_THE_HUNGER -> data.setUnique(id,"fresh_golden_hunger",false);
            case ASH_CROWN -> data.setUnique(id,"fresh_ash_crown",true);
            case QUENCH_THE_CROWN -> data.setUnique(id,"fresh_ash_crown",false);

            case LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON,
                    DEEP_SAINT, BLACK_PARDON, NINTH_LIFE, THE_LONG_NIGHT -> data.setUnique(id,lawKey(card),true);
            case GRAVE_WALKER -> {
                data.setUnique(id,lawKey(card),true);
                data.addDeathHeartDebt(id,1);
            }
            default -> { }
        }
    }

    /** Used by Absolution without touching Death laws or positive persistent Epic/Unique laws. */
    public static void absolve(LockData data, UUID id) {
        if (data == null || id == null) return;
        data.setUnique(id,"fresh_tethered_blood",false);
        data.setUnique(id,"fresh_ashen_lungs",false);
        data.setUnique(id,"fresh_hollow_bones",false);
        data.setUnique(id,"fresh_clockwork_nerve",false);
        data.setUnique(id,"fresh_golden_hunger",false);
        data.setUnique(id,"fresh_ash_crown",false);
    }

    public static boolean hasLesserBurden(LockData data, UUID id) {
        return data != null && id != null && (data.hasUnique(id,"fresh_tethered_blood")
                || data.hasUnique(id,"fresh_ashen_lungs") || data.hasUnique(id,"fresh_hollow_bones")
                || data.hasUnique(id,"fresh_clockwork_nerve") || data.hasUnique(id,"fresh_golden_hunger")
                || data.hasUnique(id,"fresh_ash_crown"));
    }

    /** Clears one of the new lesser burdens. Returns true when it did work. */
    public static boolean clearOneLesserBurden(LockData data, UUID id) {
        if (data == null || id == null) return false;
        String[] keys={"fresh_tethered_blood","fresh_ashen_lungs","fresh_hollow_bones","fresh_clockwork_nerve","fresh_golden_hunger","fresh_ash_crown"};
        for(String key:keys) if(data.hasUnique(id,key)){data.setUnique(id,key,false);return true;}
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.getServer()==null) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
        if(player.tickCount%20==0){
            for(ForbiddenBargain card:TIMED_CARDS) active(data,id,card,now);

            if(active(data,id,ForbiddenBargain.DIVERS_MARGIN,now) && player.isInWaterOrBubble())
                refresh(player,MobEffects.MOVEMENT_SPEED,0);
            if(active(data,id,ForbiddenBargain.CINDER_RECEIPT,now) && now>=data.uniqueLong(id,"fresh_cinder_resist_start"))
                refresh(player,MobEffects.FIRE_RESISTANCE,0);
            if(active(data,id,ForbiddenBargain.STANDING_ORDER,now) && horizontalSpeed(player)<0.018D)
                refresh(player,MobEffects.DAMAGE_RESISTANCE,0);
            if(active(data,id,ForbiddenBargain.RUNNING_ORDER,now) && player.isSprinting()){
                refresh(player,MobEffects.MOVEMENT_SPEED,0); refresh(player,MobEffects.DAMAGE_BOOST,0);
            }
            if(active(data,id,ForbiddenBargain.OPEN_SKY_LEDGER,now) && player.serverLevel().canSeeSky(player.blockPosition())){
                refresh(player,MobEffects.MOVEMENT_SPEED,0); refresh(player,MobEffects.LUCK,0);
            }
            if(active(data,id,ForbiddenBargain.DEEP_INK,now) && player.getY()<32.0D){
                refresh(player,MobEffects.DIG_SPEED,0); player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,260,0,true,false));
            }
            if(active(data,id,ForbiddenBargain.WARD_LANTERN,now)){
                for(Mob mob:player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(16.0D),m->m.isAlive()&&m instanceof Enemy))
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,40,0,true,false));
            }
            if(active(data,id,ForbiddenBargain.QUIET_MOUTH,now) && player.isShiftKeyDown() && horizontalSpeed(player)<0.015D)
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,35,0,true,false));
            if(active(data,id,ForbiddenBargain.LOUD_NAME,now)){
                refresh(player,MobEffects.GLOWING,0); refresh(player,MobEffects.DAMAGE_BOOST,0);
            }
            if(active(data,id,ForbiddenBargain.SECOND_WIND,now) && data.uniqueInt(id,"fresh_second_wind_ready")>0
                    && player.getHealth()/Math.max(1.0F,player.getMaxHealth())<=0.30F){
                data.setUniqueInt(id,"fresh_second_wind_ready",0); player.heal(8.0F);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*20,0,false,true,true));
                player.serverLevel().sendParticles(ParticleTypes.SOUL,player.getX(),player.getY()+1.0D,player.getZ(),12,.25,.35,.25,.02);
            }

            if(data.hasUnique(id,"fresh_tethered_blood")){
                if(player.getHealth()<=player.getMaxHealth()*.5F) refresh(player,MobEffects.DAMAGE_RESISTANCE,0);
                else refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);
            }
            if(data.hasUnique(id,"fresh_ashen_lungs")){
                refresh(player,MobEffects.FIRE_RESISTANCE,0); refresh(player,MobEffects.DIG_SLOWDOWN,0);
            }
            if(data.hasUnique(id,"fresh_hollow_bones")){
                refresh(player,MobEffects.SLOW_FALLING,0); refresh(player,MobEffects.WEAKNESS,0);
            }
            if(data.hasUnique(id,"fresh_clockwork_nerve")){
                refresh(player,MobEffects.MOVEMENT_SPEED,0); refresh(player,MobEffects.HUNGER,0);
            }
            if(data.hasUnique(id,"fresh_golden_hunger")){
                player.addEffect(new MobEffectInstance(MobEffects.LUCK,40,1,true,false)); refresh(player,MobEffects.HUNGER,0);
            }
            if(data.hasUnique(id,"fresh_ash_crown")){
                refresh(player,MobEffects.FIRE_RESISTANCE,0);
                if(player.isInWaterOrBubble() || player.serverLevel().isRainingAt(player.blockPosition())) refresh(player,MobEffects.WEAKNESS,0);
            }

            if(data.hasUnique(id,lawKey(ForbiddenBargain.LAST_COAL))){
                long day=player.level().getDayTime()/24000L;
                if(player.isOnFire() && player.getHealth()<=8.0F && data.uniqueLong(id,"fresh_last_coal_day")!=day){
                    data.setUniqueLong(id,"fresh_last_coal_day",day); player.clearFire(); player.heal(6.0F);
                    player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*30,1,false,true,true));
                }
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.HUNTERS_MOON))){
                if(player.level().isNight()){refresh(player,MobEffects.DAMAGE_BOOST,0);refresh(player,MobEffects.MOVEMENT_SPEED,0);} else if(CardConditions.isDirectDaylight(player.serverLevel(), player.blockPosition())) refresh(player,MobEffects.GLOWING,0);
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.DEEP_SAINT))){
                if(player.getY()<0){refresh(player,MobEffects.DAMAGE_RESISTANCE,0);refresh(player,MobEffects.DIG_SPEED,0);}
                else if(player.getY()>96) refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.BLACK_PARDON))){
                long day=player.level().getDayTime()/24000L;
                if(data.uniqueLong(id,"fresh_black_pardon_day")!=day){
                    List<net.minecraft.world.effect.MobEffect> harmful=new ArrayList<>();
                    for(MobEffectInstance fx:player.getActiveEffects()) if(fx.getEffect().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL) harmful.add(fx.getEffect());
                    if(harmful.size()>=2){for(var fx:harmful)player.removeEffect(fx);data.setUniqueLong(id,"fresh_black_pardon_day",day);}
                }
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.THE_LONG_NIGHT))){
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,260,0,true,false));
                if(player.level().isNight()) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,40,1,true,false));
                else if(CardConditions.isDirectDaylight(player.serverLevel(), player.blockPosition())){refresh(player,MobEffects.WEAKNESS,0);refresh(player,MobEffects.MOVEMENT_SLOWDOWN,0);}
            }
            if(data.hasUnique(id,lawKey(ForbiddenBargain.GRAVE_WALKER))){
                for(Mob mob:player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(18.0D),m->m.isAlive()&&m.getMobType()== MobType.UNDEAD))
                    if(mob.getTarget()==player) mob.setTarget(null);
            }
        }

        boolean iron=active(data,id,ForbiddenBargain.IRON_STEP,now);
        syncModifier(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),IRON_STEP_UUID,
                "wardbound.fresh_iron_step",iron?.35D:0D);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        // Outgoing rules.
        if(event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.getServer()!=null){
            LockData data=LockData.get(attacker.getServer()); UUID id=attacker.getUUID(); long now=attacker.level().getGameTime();
            boolean projectile=event.getSource().getDirectEntity() instanceof Projectile;
            boolean melee=!projectile && event.getSource().getDirectEntity()==attacker;
            float mul=1.0F;
            if(melee && data.uniqueInt(id,counterKey(ForbiddenBargain.FIRST_CUT_DOUBLED))>0){
                mul*=1.50F; data.setUniqueInt(id,counterKey(ForbiddenBargain.FIRST_CUT_DOUBLED),0);
                attacker.hurt(attacker.damageSources().magic(),2.0F);
            }
            if(projectile && data.uniqueInt(id,counterKey(ForbiddenBargain.FIRST_ARROW_DOUBLED))>0){
                mul*=1.50F; data.setUniqueInt(id,counterKey(ForbiddenBargain.FIRST_ARROW_DOUBLED),0);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*5,0,false,true,true));
            }
            if(melee && data.uniqueInt(id,counterKey(ForbiddenBargain.CHARCOAL_TONGUE))>0){
                event.getEntity().setSecondsOnFire(3); dec(data,id,ForbiddenBargain.CHARCOAL_TONGUE);
            }
            if(melee && data.uniqueInt(id,counterKey(ForbiddenBargain.FROSTED_EDGE))>0){
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20*4,0,false,true,true)); dec(data,id,ForbiddenBargain.FROSTED_EDGE);
            }
            if(active(data,id,ForbiddenBargain.WOUNDED_PROFIT,now) && attacker.getHealth()<=attacker.getMaxHealth()*.5F) mul*=1.15F;
            if(melee && active(data,id,ForbiddenBargain.PATIENT_BLADE,now) && horizontalSpeed(attacker)<.020D) mul*=1.18F;
            if(active(data,id,ForbiddenBargain.SOLITARY_LEDGER,now) && hostileCount(attacker.serverLevel(),attacker,9.0D)<=1) mul*=1.15F;
            if(mul!=1.0F) event.setAmount(event.getAmount()*mul);
        }

        // Incoming rules.
        if(event.getEntity() instanceof ServerPlayer player && player.getServer()!=null){
            LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); long now=player.level().getGameTime();
            float mul=1.0F;
            if(active(data,id,ForbiddenBargain.GLASS_STEP,now)) mul*=1.15F;
            if(active(data,id,ForbiddenBargain.MOVING_TARGET,now) && player.isSprinting() && event.getSource().getDirectEntity() instanceof Projectile) mul*=.80F;
            if(active(data,id,ForbiddenBargain.CROWDED_LEDGER,now) && hostileCount(player.serverLevel(),player,9.0D)>=4) mul*=.85F;
            if(data.hasUnique(id,lawKey(ForbiddenBargain.MIRROR_LEDGER))){
                if(event.getSource().getDirectEntity() instanceof Projectile) mul*=1.10F;
                else if(event.getSource().getEntity() instanceof LivingEntity attacker && attacker!=player){
                    float reflected=Math.min(10.0F,event.getAmount()*.20F);
                    if(reflected>0) attacker.hurt(player.damageSources().magic(),reflected);
                }
            }
            if(mul!=1.0F) event.setAmount(event.getAmount()*mul);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || player.getServer()==null) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID();
        if(data.hasUnique(id,lawKey(ForbiddenBargain.NINTH_LIFE)) && data.uniqueInt(id,"fresh_ninth_life_charge")>0
                && event.getAmount()>=player.getHealth()){
            data.setUniqueInt(id,"fresh_ninth_life_charge",0);
            if(player.getHealth()<=2.0F){event.setAmount(0.0F);player.setHealth(2.0F);} else event.setAmount(Math.max(0.0F,player.getHealth()-2.0F));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,20*4,2,false,true,true));
            player.serverLevel().sendParticles(ParticleTypes.SOUL,player.getX(),player.getY()+.8,player.getZ(),18,.28,.42,.28,.02);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if(!(event.getSource().getEntity() instanceof ServerPlayer player) || player.getServer()==null || !(event.getEntity() instanceof Enemy)) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID();
        int rebate=data.uniqueInt(id,counterKey(ForbiddenBargain.HUNTERS_REBATE));
        if(rebate>0){data.setUniqueInt(id,counterKey(ForbiddenBargain.HUNTERS_REBATE),rebate-1); ExperienceOrb.award(player.serverLevel(),player.position(),2); player.getFoodData().eat(1,.12F);}
        int red=data.uniqueInt(id,counterKey(ForbiddenBargain.RED_HARVEST));
        if(red>0){data.setUniqueInt(id,counterKey(ForbiddenBargain.RED_HARVEST),red-1);player.heal(1.0F);}
        int gray=data.uniqueInt(id,counterKey(ForbiddenBargain.GRAY_HARVEST));
        if(gray>0){int done=13-gray;data.setUniqueInt(id,counterKey(ForbiddenBargain.GRAY_HARVEST),gray-1);if(done%3==0)player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*30,0,false,true,true));}
        int scav=data.uniqueInt(id,counterKey(ForbiddenBargain.SCAVENGER_CLAUSE));
        if(scav>0){data.setUniqueInt(id,counterKey(ForbiddenBargain.SCAVENGER_CLAUSE),scav-1);if(player.getRandom().nextFloat()<.60F)ExperienceOrb.award(player.serverLevel(),player.position(),1+player.getRandom().nextInt(3));}
        int bloodless=data.uniqueInt(id,counterKey(ForbiddenBargain.BLOODLESS_VICTORY));
        if(bloodless>0 && player.getHealth()>=player.getMaxHealth()*.80F){int done=16-bloodless;data.setUniqueInt(id,counterKey(ForbiddenBargain.BLOODLESS_VICTORY),bloodless-1);ExperienceOrb.award(player.serverLevel(),player.position(),2);if(done%5==0)player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*45,0,false,true,true));}

        if(data.hasUnique(id,lawKey(ForbiddenBargain.PALE_RESERVOIR))){int n=data.uniqueInt(id,"fresh_pale_reservoir_kills")+1;if(n>=10){n=0;ExperienceOrb.award(player.serverLevel(),player.position(),5);player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,20*90,1,false,true,true));}data.setUniqueInt(id,"fresh_pale_reservoir_kills",n);}
        if(data.hasUnique(id,lawKey(ForbiddenBargain.NINTH_LIFE)) && data.uniqueInt(id,"fresh_ninth_life_charge")<=0){int n=data.uniqueInt(id,"fresh_ninth_life_kills")+1;if(n>=40){n=0;data.setUniqueInt(id,"fresh_ninth_life_charge",1);}data.setUniqueInt(id,"fresh_ninth_life_kills",n);}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if(event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative() || player.getServer()==null || !(player.level() instanceof ServerLevel level)) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); BlockState state=event.getState(); BlockPos pos=event.getPos();
        int mason=data.uniqueInt(id,counterKey(ForbiddenBargain.MASONS_LUCK));
        if(mason>0){data.setUniqueInt(id,counterKey(ForbiddenBargain.MASONS_LUCK),mason-1);if(player.getRandom().nextFloat()<.20F)ExperienceOrb.award(level,net.minecraft.world.phys.Vec3.atCenterOf(pos),1);}
        int wood=data.uniqueInt(id,counterKey(ForbiddenBargain.WOODSMANS_SHARE));
        if(wood>0 && state.is(BlockTags.LOGS)){data.setUniqueInt(id,counterKey(ForbiddenBargain.WOODSMANS_SHARE),wood-1);if(player.getRandom().nextFloat()<.25F)repeatDrops(level,player,state,pos,.40F);}
        int farm=data.uniqueInt(id,counterKey(ForbiddenBargain.FARMERS_MARGIN));
        if(farm>0 && isMatureCrop(state)){data.setUniqueInt(id,counterKey(ForbiddenBargain.FARMERS_MARGIN),farm-1);if(player.getRandom().nextFloat()<.35F)repeatDrops(level,player,state,pos,.50F);}
    }

    @SubscribeEvent
    public static void onFoodFinished(LivingEntityUseItemEvent.Finish event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || player.getServer()==null || !event.getItem().isEdible()) return;
        LockData data=LockData.get(player.getServer()); UUID id=player.getUUID(); int remaining=data.uniqueInt(id,counterKey(ForbiddenBargain.INKED_APPETITE));
        if(remaining<=0)return; data.setUniqueInt(id,counterKey(ForbiddenBargain.INKED_APPETITE),remaining-1);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,20*8,0,false,true,true));
    }

    public static String status(LockData data, UUID id, ForbiddenBargain card, long now) {
        if(!isFresh(card))return "INACTIVE";
        return switch(card){
            case CUT_TETHER, CLEAR_THE_LUNGS, FILL_THE_BONES, STILL_THE_NERVE, PAY_THE_HUNGER, QUENCH_THE_CROWN -> "RESOLVED ON SIGNING";
            case TETHERED_BLOOD -> law(data,id,"fresh_tethered_blood","SCAR");
            case ASHEN_LUNGS -> law(data,id,"fresh_ashen_lungs","SCAR");
            case HOLLOW_BONES -> law(data,id,"fresh_hollow_bones","SCAR");
            case CLOCKWORK_NERVE -> law(data,id,"fresh_clockwork_nerve","SCAR");
            case GOLDEN_HUNGER -> law(data,id,"fresh_golden_hunger","CURSE");
            case ASH_CROWN -> law(data,id,"fresh_ash_crown","CURSE");
            case HUNTERS_REBATE, FIRST_CUT_DOUBLED, FIRST_ARROW_DOUBLED, RED_HARVEST, GRAY_HARVEST,
                    SCAVENGER_CLAUSE, MASONS_LUCK, WOODSMANS_SHARE, FARMERS_MARGIN, INKED_APPETITE,
                    CHARCOAL_TONGUE, FROSTED_EDGE, BLOODLESS_VICTORY -> {
                int n=data.uniqueInt(id,counterKey(card)); yield n>0?"ACTIVE · "+n+" charge(s) remain":"SPENT";
            }
            case LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON,
                    NINTH_LIFE, THE_LONG_NIGHT, GRAVE_WALKER -> data.hasUnique(id,lawKey(card))?"ACTIVE WORLD LAW":"INACTIVE";
            default -> {
                long until=data.uniqueLong(id,timerKey(card));
                if(until==NONE || until<=now)yield "EXPIRED / READY TO RETURN";
                long sec=Math.max(0,(until-now)/20L); yield "ACTIVE · "+(sec/60L)+":"+String.format(java.util.Locale.ROOT,"%02d",sec%60L)+" remaining";
            }
        };
    }

    public static String reliefText(ForbiddenBargain card){
        return switch(card){
            case TETHERED_BLOOD -> "Cut the Tether or Absolution in Black Ink removes the scar.";
            case ASHEN_LUNGS -> "Clear the Lungs or Absolution in Black Ink removes the scar.";
            case HOLLOW_BONES -> "Fill the Bones or Absolution in Black Ink removes the scar.";
            case CLOCKWORK_NERVE -> "Still the Nerve or Absolution in Black Ink removes the scar.";
            case GOLDEN_HUNGER -> "Pay the Hunger or Absolution in Black Ink removes the curse.";
            case ASH_CROWN -> "Quench the Crown or Absolution in Black Ink removes the curse.";
            case THE_LONG_NIGHT, GRAVE_WALKER -> "This is a Death law. Ordinary remedies do not erase it.";
            case LAST_COAL, PALE_RESERVOIR, MIRROR_LEDGER, HUNTERS_MOON, DEEP_SAINT, BLACK_PARDON, NINTH_LIFE -> "This is a persistent world law; ordinary milk does not remove it.";
            case CUT_TETHER, CLEAR_THE_LUNGS, FILL_THE_BONES, STILL_THE_NERVE, PAY_THE_HUNGER, QUENCH_THE_CROWN -> "This card completes its work when signed.";
            default -> "This card returns after its timer or written charges are exhausted.";
        };
    }

    private static boolean isTimed(ForbiddenBargain card){
        return switch(card){
            case QUICKENED_PULSE, STONE_BREATH, SHARPENED_HOUR, CLEAR_EYES, FEATHER_RECEIPT, DIVERS_MARGIN,
                    CINDER_RECEIPT, MINERS_CREDIT, PALE_BANDAGE, BLACK_BREAD, THIN_AIR, STANDING_ORDER, RUNNING_ORDER, OPEN_SKY_LEDGER,
                    DEEP_INK, IRON_STEP, GLASS_STEP, WARD_LANTERN, QUIET_MOUTH, LOUD_NAME, SECOND_WIND,
                    WOUNDED_PROFIT, PATIENT_BLADE, MOVING_TARGET, CROWDED_LEDGER, SOLITARY_LEDGER -> true;
            default -> false;
        };
    }
    private static void timer(LockData d,UUID id,ForbiddenBargain card,long now,long seconds){d.setUniqueLong(id,timerKey(card),now+seconds*20L);}
    private static boolean active(LockData d,UUID id,ForbiddenBargain card,long now){long until=d.uniqueLong(id,timerKey(card));if(until==NONE)return false;if(until>now)return true;d.setUniqueLong(id,timerKey(card),NONE);if(card==ForbiddenBargain.SECOND_WIND)d.setUniqueInt(id,"fresh_second_wind_ready",0);return false;}
    private static String timerKey(ForbiddenBargain card){return "fresh_card_"+card.id+"_until";}
    private static String counterKey(ForbiddenBargain card){return "fresh_card_"+card.id+"_count";}
    private static String lawKey(ForbiddenBargain card){return "fresh_law_"+card.id;}
    private static void dec(LockData d,UUID id,ForbiddenBargain card){int n=d.uniqueInt(id,counterKey(card));if(n>0)d.setUniqueInt(id,counterKey(card),n-1);}
    private static void refresh(ServerPlayer p,net.minecraft.world.effect.MobEffect effect,int amp){p.addEffect(new MobEffectInstance(effect,40,amp,true,false,true));}
    private static double horizontalSpeed(ServerPlayer p){return p.getDeltaMovement().horizontalDistanceSqr();}
    private static int hostileCount(ServerLevel level,LivingEntity around,double radius){return level.getEntitiesOfClass(LivingEntity.class,around.getBoundingBox().inflate(radius),e->e.isAlive()&&e instanceof Enemy).size();}
    private static UUID uuid(String s){return UUID.nameUUIDFromBytes(("wardbound:"+s).getBytes(StandardCharsets.UTF_8));}
    private static void syncModifier(AttributeInstance attr,UUID uuid,String name,double amount){if(attr==null)return;AttributeModifier old=attr.getModifier(uuid);if(amount<=.0001D){if(old!=null)attr.removeModifier(uuid);return;}if(old!=null&&Math.abs(old.getAmount()-amount)<.0001D)return;if(old!=null)attr.removeModifier(uuid);attr.addTransientModifier(new AttributeModifier(uuid,name,amount, AttributeModifier.Operation.ADDITION));}
    private static String law(LockData d,UUID id,String key,String type){return d.hasUnique(id,key)?"ACTIVE "+type:"REMOVED / INACTIVE";}

    private static void repeatDrops(ServerLevel level,ServerPlayer player,BlockState state,BlockPos pos,float fraction){
        List<ItemStack> drops=Block.getDrops(state,level,pos,level.getBlockEntity(pos),player,player.getMainHandItem());
        for(ItemStack stack:drops){if(stack.isEmpty())continue;int extra=Math.max(1,Mth.floor(stack.getCount()*fraction));ItemStack copy=stack.copy();copy.setCount(Math.min(copy.getMaxStackSize(),extra));Block.popResource(level,pos,copy);}
    }
    private static boolean isMatureCrop(BlockState state){Block b=state.getBlock();if(b instanceof CropBlock crop)return crop.isMaxAge(state);if(b instanceof NetherWartBlock)return state.getValue(NetherWartBlock.AGE)>=3;if(b instanceof CocoaBlock)return state.getValue(CocoaBlock.AGE)>=2;if(b instanceof SweetBerryBushBlock)return state.getValue(SweetBerryBushBlock.AGE)>=3;return false;}
}
