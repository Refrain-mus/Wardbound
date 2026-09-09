package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.LockData;
import dev.marrowseal.wardbound.WardHistory;
import dev.marrowseal.wardbound.WardAdvancements;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.maestroart.MaestroArtEntity;
import dev.marrowseal.wardbound.maestroart.MaestroParticles;
import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Full server-authoritative Maestro encounter. The performer rig remains the visual source of truth;
 * every authored attack clip below has a matching combat timeline instead of being a cosmetic preview.
 */
public final class MaestroEntity extends MaestroArtEntity implements CinematicVitalityHost {
    public static final int INTRO=0, IDLE=1, CONDUCT=2, STACCATO=3, LEGATO=4, NOTE_BARRAGE=5,
            SILENCE=6, ORCHESTRA=7, DASH=8, COUNTER_GUARD=9, COUNTER_RIPOSTE=10,
            CRESCENDO=11, PHASE=12, DYING=13;
    private static final double BASE_HEALTH=11000D;
    private static final EntityDataAccessor<Integer> PHASE_DATA=SynchedEntityData.defineId(MaestroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_DATA=SynchedEntityData.defineId(MaestroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEAT_DATA=SynchedEntityData.defineId(MaestroEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.wardbound.maestro"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private MasterVitality vitality=new MasterVitality(BASE_HEALTH);
    private UUID owner;
    private BlockPos stage;
    private boolean practice;
    private boolean initialized;
    private boolean rewarded;
    private int stateTick;
    private int cooldown=30;
    private int lastAttack=-1;
    private int emptyTicks;
    private int deathTicks;
    private Vec3 dashVector=Vec3.ZERO;
    private final List<Vec3> noteMarks=new ArrayList<>();
    private final Set<UUID> dashVictims=new HashSet<>();

    public MaestroEntity(EntityType<? extends MaestroEntity> type, Level level){
        super(type,level);
        setNoGravity(false);
        setCustomName(Component.literal("Maestro"));
        xpReward=80;
    }

    public static AttributeSupplier.Builder attributes(){
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MasterVitality.REGISTRATION_MAX_HEALTH)
                .add(Attributes.ARMOR,22).add(Attributes.ARMOR_TOUGHNESS,12)
                .add(Attributes.KNOCKBACK_RESISTANCE,.88).add(Attributes.MOVEMENT_SPEED,.32)
                .add(Attributes.ATTACK_DAMAGE,30).add(Attributes.FOLLOW_RANGE,56)
                .add(ALObjects.Attributes.ARMOR_PIERCE.get(),5.0)
                .add(ALObjects.Attributes.ARMOR_SHRED.get(),0.06)
                .add(ALObjects.Attributes.CRIT_CHANCE.get(),0.10)
                .add(ALObjects.Attributes.CRIT_DAMAGE.get(),1.65)
                .add(ALObjects.Attributes.CURRENT_HP_DAMAGE.get(),0.0)
                .add(ALObjects.Attributes.LIFE_STEAL.get(),0.015)
                .add(ALObjects.Attributes.DODGE_CHANCE.get(),0.0);
    }

    @Override protected void defineSynchedData(){
        super.defineSynchedData();
        entityData.define(PHASE_DATA,1);entityData.define(STATE_DATA,INTRO);entityData.define(BEAT_DATA,0);
    }
    @Override protected boolean previewMotionLock(){return false;}

    public void begin(ServerPlayer player, BlockPos center, boolean practice){
        if(level().isClientSide||player==null||center==null)return;
        owner=player.getUUID();stage=center.immutable();this.practice=practice;initialized=true;rewarded=false;
        vitality=new MasterVitality(BASE_HEALTH);vitality.syncNativeHealth(this);
        entityData.set(PHASE_DATA,1);entityData.set(STATE_DATA,INTRO);entityData.set(BEAT_DATA,0);
        stateTick=0;cooldown=28;emptyTicks=0;deathTicks=0;lastAttack=-1;noteMarks.clear();dashVictims.clear();
        setInvulnerable(true);setNoGravity(false);getNavigation().stop();
        playClip("intro");applyPhaseAttributes(1);bar.setProgress(1f);bar.setVisible(true);
        level().playSound(null,blockPosition(),SoundEvents.NOTE_BLOCK_BASS.value(),SoundSource.HOSTILE,1.35f,.45f);
        WardHud.send(player,"MAESTRO // I was human once. Keep that fact if it comforts you. I misplaced the century in which it mattered.",WardHud.Mood.GLITCH,5200);
        WardHistory.recordSpecial(player,"GLITCH","MAESTRO // Once human. Longevity became traversal; traversal became witness. He rose by a road unrelated to the three known Masters and speaks of dead worlds as movements already concluded.");
    }

    public boolean includes(UUID id){return id!=null&&id.equals(owner);}
    public int phase(){return entityData.get(PHASE_DATA);}
    public int combatState(){return entityData.get(STATE_DATA);}
    public int combatBeat(){return entityData.get(BEAT_DATA);}

    @Override protected void serverPerformanceTick(){
        if(!initialized||!(level() instanceof ServerLevel sl))return;
        if(entityData.get(STATE_DATA)==DYING){tickDeath(sl);return;}
        ServerPlayer target=primary(sl);
        if(target==null){if(++emptyTicks>80)discard();return;}else emptyTicks=0;
        enforceStage(target);
        bar.setProgress(vitality.fraction());
        int state=entityData.get(STATE_DATA);stateTick++;entityData.set(BEAT_DATA,stateTick);
        if(state!=DASH&&state!=PHASE&&state!=DYING)face(target);

        if(state==INTRO){
            if(stateTick==72)WardHud.send(target,"MAESTRO // Gambler. Curator. Notary. Your score carries those names. I do not. We did not ascend by the same road, and our paths never required one another.",WardHud.Mood.GLITCH,6000);
            if(stateTick==112)stagePulse(4.5,36);
            if(stateTick>=144){setInvulnerable(false);enterIdle(24);}
            return;
        }
        if(state==PHASE){tickPhaseTransition();return;}
        if(shouldTransitionPhase()){beginPhaseTransition(phase()+1);return;}
        if(state==IDLE){tickIdle(target);return;}

        switch(state){
            case CONDUCT -> tickConduct(target);
            case STACCATO -> tickStaccato(target);
            case LEGATO -> tickLegato(target);
            case NOTE_BARRAGE -> tickNoteBarrage(target);
            case SILENCE -> tickSilence(target);
            case ORCHESTRA -> tickOrchestra(target);
            case DASH -> tickDash(target);
            case COUNTER_GUARD -> tickCounterGuard(target);
            case COUNTER_RIPOSTE -> tickCounterRiposte(target);
            case CRESCENDO -> tickCrescendo(target);
            default -> enterIdle(20);
        }
    }

    private void tickIdle(ServerPlayer target){
        double d=distanceTo(target);
        if(d>9.5)getNavigation().moveTo(target,phase()>=3?1.18:phase()==2?1.03:.90);
        else getNavigation().stop();
        if(d<4.2){Vec3 away=position().subtract(target.position()).normalize().scale(.09);setDeltaMovement(getDeltaMovement().add(away.x,0,away.z));}
        if(--cooldown<=0)startAttack(chooseAttack(target));
    }

    private int chooseAttack(ServerPlayer target){
        int p=phase();double d=distanceTo(target);
        int[] pool=p==1?new int[]{CONDUCT,STACCATO,LEGATO,NOTE_BARRAGE,DASH,CONDUCT,STACCATO}
                :p==2?new int[]{CONDUCT,STACCATO,LEGATO,NOTE_BARRAGE,DASH,SILENCE,ORCHESTRA,COUNTER_GUARD,STACCATO,LEGATO}
                :new int[]{STACCATO,LEGATO,NOTE_BARRAGE,DASH,SILENCE,ORCHESTRA,COUNTER_GUARD,CRESCENDO,CRESCENDO,STACCATO,ORCHESTRA};
        int pick=pool[random.nextInt(pool.length)];
        if(d>12&&pick==STACCATO)pick=NOTE_BARRAGE;
        if(d<4&&pick==NOTE_BARRAGE)pick=DASH;
        if(pick==lastAttack)pick=pool[(random.nextInt(pool.length-1)+1)%pool.length];
        lastAttack=pick;return pick;
    }

    private void startAttack(int state){
        entityData.set(STATE_DATA,state);entityData.set(BEAT_DATA,0);stateTick=0;noteMarks.clear();dashVictims.clear();getNavigation().stop();
        String clip=switch(state){
            case CONDUCT -> "conduct_strike";case STACCATO -> "staccato_combo";case LEGATO -> "legato_sweep";
            case NOTE_BARRAGE -> "note_barrage";case SILENCE -> "silence_field";case ORCHESTRA -> "orchestra_summon";
            case DASH -> "dash_lunge";case COUNTER_GUARD -> "counter_guard";case COUNTER_RIPOSTE -> "counter_riposte";
            case CRESCENDO -> "grand_crescendo";default -> "idle_composed";};
        playClip(clip);
        if(state==DASH){ServerPlayer t=primary((ServerLevel)level());if(t!=null)dashVector=t.position().subtract(position()).multiply(1,0,1).normalize();}
    }

    private void tickConduct(ServerPlayer target){
        if(stateTick==18){coneTelegraph(target,5.8,66);sound(SoundEvents.NOTE_BLOCK_HAT.value(),.8f,1.45f);}
        if(stateTick==35){coneHit(5.8,66,29f,1.1);stagePulse(2.4,20);sound(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(),1.1f,.78f);}
        if(stateTick>=56)enterIdle(nextCooldown());
    }
    private void tickStaccato(ServerPlayer target){
        int[] tells={14,34,54};int[] hits={23,43,63};
        for(int i=0;i<tells.length;i++){if(stateTick==tells[i]){coneTelegraph(target,4.7,44);sound(SoundEvents.NOTE_BLOCK_HAT.value(),.55f,1.55f+i*.09f);}if(stateTick==hits[i]){coneHit(4.7,44,i==2?22f:16f,.75);sound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.8f,.75f+i*.08f);}}
        if(stateTick>=84)enterIdle(nextCooldown());
    }
    private void tickLegato(ServerPlayer target){
        if(stateTick==20||stateTick==44||stateTick==68)stageRing(stateTick==20?4.5:stateTick==44?7.5:10.5,34);
        if(stateTick==30)arcHit(-65,65,6.5,18f);
        if(stateTick==54)arcHit(25,145,8.5,20f);
        if(stateTick==78)arcHit(-145,-25,10.5,23f);
        if(stateTick>=104)enterIdle(nextCooldown());
    }
    private void tickNoteBarrage(ServerPlayer target){
        int[] aim={12,32,52,72};int[] hit={22,42,62,82};
        for(int i=0;i<aim.length;i++){
            if(stateTick==aim[i]){noteMarks.add(target.position());drawLine(eye(),target.position().add(0,1,0),24);sound(SoundEvents.NOTE_BLOCK_CHIME.value(),.6f,1.15f+i*.12f);}
            if(stateTick==hit[i]&&i<noteMarks.size()){Vec3 mark=noteMarks.get(i);lineHit(eye(),mark.add(0,1,0),1.25,19f+i*1.75f);burst(mark.add(0,.8,0),18);}
        }
        if(stateTick>=100)enterIdle(nextCooldown());
    }
    private void tickSilence(ServerPlayer target){
        if(stateTick==18||stateTick==30)stageRing(7.0,40);
        if(stateTick>=36&&stateTick<=94&&stateTick%8==0){
            for(ServerPlayer p:participants())if(horizontalDistance(p.position(),Vec3.atBottomCenterOf(stage))<=7.2){
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,34,2,false,true,true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,1,false,true,true));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,34,2,false,true,true));
            }
        }
        if(stateTick==62){circleHit(7.2,21f);stagePulse(7.2,44);sound(SoundEvents.WARDEN_SONIC_BOOM,SoundSource.HOSTILE,.65f,.62f);}
        if(stateTick>=112)enterIdle(nextCooldown()+8);
    }
    private void tickOrchestra(ServerPlayer target){
        Vec3 c=Vec3.atBottomCenterOf(stage);
        if(stateTick==22){for(int i=0;i<8;i++){double a=i*Math.PI/4;Vec3 q=c.add(Math.cos(a)*12,.1,Math.sin(a)*12);drawLine(q,target.position().add(0,1,0),18);}sound(SoundEvents.NOTE_BLOCK_BELL.value(),.9f,.72f);}
        if(stateTick==42||stateTick==68||stateTick==94){
            int hitTick=stateTick+10;double phaseOffset=(hitTick-52)*.025;
            orchestraLines(c,phaseOffset,false,0);sound(SoundEvents.NOTE_BLOCK_HAT.value(),.55f,1.15f+(stateTick-42)*.006f);
        }
        if(stateTick==52||stateTick==78||stateTick==104){
            double phaseOffset=(stateTick-52)*.025;float damage=17f+(stateTick-52)/26*2.5f;
            orchestraLines(c,phaseOffset,true,damage);stagePulse(5+(stateTick-52)*.08,28);
        }
        if(stateTick>=140)enterIdle(nextCooldown()+10);
    }
    private void orchestraLines(Vec3 c,double phaseOffset,boolean hit,float damage){
        List<Vec3[]> lines=new ArrayList<>(4);
        for(int i=0;i<4;i++){double a=i*Math.PI/2+phaseOffset;Vec3 a0=c.add(Math.cos(a)*13,1,Math.sin(a)*13),b0=c.add(-Math.cos(a)*13,1,-Math.sin(a)*13);lines.add(new Vec3[]{a0,b0});drawLine(a0,b0,28);}
        if(!hit)return;
        for(ServerPlayer p:participants()){double best=Double.MAX_VALUE;Vec3 sample=p.position().add(0,1,0);for(Vec3[] line:lines)best=Math.min(best,distanceToSegment(sample,line[0],line[1]));if(best<=1.05){damage(p,damage);push(p,p.position().subtract(c).multiply(1,0,1).normalize(),.7);}}
    }
    private void tickDash(ServerPlayer target){
        if(stateTick<=12){face(target);if(stateTick==8)drawLine(position().add(0,1,0),target.position().add(0,1,0),28);}
        if(stateTick>=16&&stateTick<=25){Vec3 next=position().add(dashVector.scale(.78));if(stage!=null&&horizontalDistance(next,Vec3.atCenterOf(stage))<20.5)moveTo(next.x,getY(),next.z,getYRot(),getXRot());dashHitOnce(position().subtract(dashVector.scale(1.2)),position().add(dashVector.scale(1.2)),1.45,32f);}
        if(stateTick==26)stagePulse(2.1,18);
        if(stateTick>=44)enterIdle(Math.max(10,nextCooldown()-8));
    }
    private void dashHitOnce(Vec3 a,Vec3 b,double width,float damage){for(ServerPlayer p:participants())if(!dashVictims.contains(p.getUUID())&&distanceToSegment(p.position().add(0,1,0),a.add(0,1,0),b.add(0,1,0))<=width){dashVictims.add(p.getUUID());damage(p,damage);push(p,dashVector,.95);}}
    private void tickCounterGuard(ServerPlayer target){
        if(stateTick==10){stageRing(3.2,22);sound(SoundEvents.SHIELD_BLOCK,SoundSource.HOSTILE,.65f,.72f);}
        if(stateTick>=56)enterIdle(Math.max(8,nextCooldown()-10));
    }
    private void tickCounterRiposte(ServerPlayer target){
        if(stateTick==10)coneTelegraph(target,6.2,58);
        if(stateTick==23){coneHit(6.2,58,34f,1.35);stagePulse(3.1,30);sound(SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.65f,1.35f);}
        if(stateTick>=84)enterIdle(nextCooldown()+6);
    }
    private void tickCrescendo(ServerPlayer target){
        int[] tell={24,54,84,114};double[] r={4.5,7.5,10.5,14.0};
        for(int i=0;i<tell.length;i++)if(stateTick==tell[i]){stageRing(r[i],48);sound(SoundEvents.NOTE_BLOCK_CHIME.value(),.75f,.72f+i*.14f);}
        if(stateTick==38)ringHit(4.5,1.5,17f);
        if(stateTick==68)ringHit(7.5,1.6,20f);
        if(stateTick==98)ringHit(10.5,1.8,23f);
        if(stateTick==128)ringHit(14.0,2.0,27f);
        if(stateTick==148){circleHit(5.2,36f);stagePulse(16,72);sound(SoundEvents.ENDER_DRAGON_GROWL,SoundSource.HOSTILE,1.0f,.78f);}
        if(stateTick>=180)enterIdle(nextCooldown()+14);
    }

    private void enterIdle(int ticks){entityData.set(STATE_DATA,IDLE);entityData.set(BEAT_DATA,0);stateTick=0;cooldown=Math.max(8,ticks);playClip(phase()>=3?"idle_rage":"idle_composed");}
    private int nextCooldown(){return phase()==1?26+random.nextInt(20):phase()==2?17+random.nextInt(15):9+random.nextInt(12);}

    private boolean shouldTransitionPhase(){return phase()<3&&vitality.fraction()<=(phase()==1?.66f:.33f);}
    private void beginPhaseTransition(int next){
        entityData.set(PHASE_DATA,next);entityData.set(STATE_DATA,PHASE);entityData.set(BEAT_DATA,0);stateTick=0;setInvulnerable(true);getNavigation().stop();
        applyPhaseAttributes(next);playClip("phase_transition");stagePulse(6+next*2,48);sound(SoundEvents.BEACON_POWER_SELECT,1f,.58f+next*.1f);
        ServerPlayer witness=primary((ServerLevel)level());
        if(witness!=null){
            if(next==2)WardHud.send(witness,"MAESTRO // I watched planets die, then constellations lose the worlds that gave them names. Once, an entire reach of stars went quiet after the Sleeper passed beyond it. I did not mistake witness for invitation.",WardHud.Mood.GLITCH,6600);
            else if(next==3)WardHud.send(witness,"MAESTRO // To the sublimity that bestowed art, I return these notes. When I look upon the stars, I remember how young their light is beside what waits beyond them. You may call it worship. I call it proportion.",WardHud.Mood.DANGER,7600);
        }
    }
    private void tickPhaseTransition(){
        if(stateTick==34||stateTick==70)stageRing(phase()==2?8:12,56);
        if(stateTick==84&&phase()==3)stagePulse(14,64);
        if(stateTick>=128){setInvulnerable(false);enterIdle(phase()==3?8:16);}
    }
    private void applyPhaseAttributes(int p){
        setAttr(ALObjects.Attributes.ARMOR_PIERCE.get(),p==1?5:p==2?8:11);
        setAttr(ALObjects.Attributes.ARMOR_SHRED.get(),p==1?.06:p==2?.12:.20);
        setAttr(ALObjects.Attributes.CRIT_CHANCE.get(),p==1?.10:p==2?.18:.27);
        setAttr(ALObjects.Attributes.CRIT_DAMAGE.get(),p==1?1.65:p==2?1.95:2.30);
        setAttr(ALObjects.Attributes.CURRENT_HP_DAMAGE.get(),p<3?0:.03);
        setAttr(ALObjects.Attributes.LIFE_STEAL.get(),p==1?.015:p==2?.035:.055);
        setAttr(ALObjects.Attributes.DODGE_CHANCE.get(),p<2?0:p==2?.025:.065);
    }
    private void setAttr(net.minecraft.world.entity.ai.attributes.Attribute attribute,double value){AttributeInstance a=getAttribute(attribute);if(a!=null)a.setBaseValue(value);}

    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return hurtThroughMob(source,amount);
        if(level().isClientSide||!initialized||entityData.get(STATE_DATA)==DYING||isInvulnerable()||!isAlive())return false;
        ServerPlayer attacker=source.getEntity() instanceof ServerPlayer p?p:null;
        // Maestro is a private Master contract. Nearby players may see the performance, but only
        // the invoking witness can damage the boss or be treated as a combat participant.
        if(attacker==null||owner==null||!owner.equals(attacker.getUUID()))return false;
        boolean guard=entityData.get(STATE_DATA)==COUNTER_GUARD;
        float incoming=guard?amount*.22f:amount;
        float cap=phase()==1?170f:phase()==2?210f:260f;
        float before=vitality.prepareNativeDamage(this);MasterNativeDamageBridge.arm(this);boolean hit=hurtThroughMob(source,Math.min(cap,incoming));float actual=MasterNativeDamageBridge.consume(this,Math.max(0,before-getHealth()));
        if((hit||actual>0)&&actual>0){
            if(vitality.damage(actual)){bar.setProgress(0);setHealth(1);beginDeath(attacker);}
            else{bar.setProgress(vitality.fraction());vitality.syncNativeHealth(this);}
            if(guard&&entityData.get(STATE_DATA)!=DYING){startAttack(COUNTER_RIPOSTE);sound(SoundEvents.SHIELD_BLOCK,SoundSource.HOSTILE,1f,.55f);}
            else if(actual>55&&random.nextFloat()<.22f&&entityData.get(STATE_DATA)==IDLE)playClip("stagger");
        }
        return hit||actual>0;
    }

    private void beginDeath(ServerPlayer killer){
        if(entityData.get(STATE_DATA)==DYING)return;
        entityData.set(STATE_DATA,DYING);entityData.set(BEAT_DATA,0);stateTick=0;deathTicks=0;setInvulnerable(true);getNavigation().stop();setDeltaMovement(Vec3.ZERO);playClip("death_start");
        if(stage!=null&&level() instanceof ServerLevel sl)MasterArenaManager.cleanupCombatTransients(sl,stage);
        stagePulse(10,72);sound(SoundEvents.ENDER_DRAGON_DEATH,SoundSource.HOSTILE,.8f,.8f);
        ServerPlayer p=primary((ServerLevel)level());
        if(killer!=null&&includes(killer.getUUID()))p=killer;
        if(p!=null&&includes(p.getUUID())){
            MasterArenaManager.beginVictoryLinger(p,MasterArenaManager.Kind.MAESTRO);
            if(!practice&&!rewarded){
                rewarded=true;LockData d=LockData.get(p.getServer());d.setUniqueInt(p.getUUID(),"maestro_defeated",1);
                MasterRewardDelivery.queue(p,MasterArenaManager.Kind.MAESTRO);WardAdvancements.maestroDefeated(p);
                WardHistory.recordSpecial(p,"GLITCH","THE ARTIST BEYOND THE THREE // Maestro never belonged to the road of Gambler, Curator or Notary, and none of them knew his name. He rose elsewhere, crossed realms, and witnessed the voids left when Cthulhu erased worlds and reaches of stars. He treats even that terror as material for art.");
                WardHud.send(p,"MAESTRO // Curtain. I never fought the Sleeper. I followed what remained after it passed: absent worlds, broken constellations, silence where history should have been. That is enough to know the direction of the end.",WardHud.Mood.REWARD,7600);
                WardHud.send(p,"MAESTRO // Take the Canticle into the End. It follows one of those scars. The Head will answer there. Do not insult what I witnessed by believing the Head is the whole body.",WardHud.Mood.GLITCH,7600);
            } else if(practice) WardHud.send(p,"MAESTRO // Rehearsal concluded. No contract was signed, and no reward follows.",WardHud.Mood.WARD,3200);
        }
    }
    private void tickDeath(ServerLevel sl){
        deathTicks++;entityData.set(BEAT_DATA,deathTicks);
        if(deathTicks==75)stagePulse(7,52);
        if(deathTicks==82)playClip("death_fall");
        if(deathTicks==170){playClip("death_end");stagePulse(3.5,34);}
        if(deathTicks>=232)discard();
    }

    private ServerPlayer primary(ServerLevel sl){
        if(owner==null||sl==null)return null;
        ServerPlayer p=sl.getServer().getPlayerList().getPlayer(owner);
        return p!=null&&p.isAlive()&&!p.isSpectator()&&p.level()==sl?p:null;
    }
    private List<ServerPlayer> participants(){
        if(!(level() instanceof ServerLevel sl))return List.of();
        ServerPlayer p=primary(sl);
        return p==null?List.of():List.of(p);
    }
    private void enforceStage(ServerPlayer p){
        if(stage==null)return;Vec3 c=Vec3.atBottomCenterOf(stage);double d=horizontalDistance(p.position(),c);
        if(d>22){Vec3 inward=c.subtract(p.position()).multiply(1,0,1).normalize();p.setDeltaMovement(p.getDeltaMovement().add(inward.scale(.16)));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,1,false,false,false));}
        if(d>27&&level() instanceof ServerLevel sl){Vec3 q=c.add(p.position().subtract(c).multiply(1,0,1).normalize().scale(18));p.teleportTo(sl,q.x,c.y,q.z,p.getYRot(),p.getXRot());p.fallDistance=0;}
        if(horizontalDistance(position(),c)>21){Vec3 q=c.add(position().subtract(c).multiply(1,0,1).normalize().scale(16));moveTo(q.x,c.y,q.z,getYRot(),0);}
    }
    private void face(LivingEntity e){getLookControl().setLookAt(e,80f,70f);setYBodyRot(getYHeadRot());}
    private Vec3 eye(){return position().add(0,1.45,0);}

    private void coneTelegraph(ServerPlayer target,double range,double degrees){
        Vec3 a=eye(),f=target.position().subtract(position()).multiply(1,0,1).normalize();for(int side:new int[]{-1,1}){double yaw=Math.toRadians(degrees*.5*side);Vec3 d=rotateY(f,yaw);drawLine(a,a.add(d.scale(range)),20);}
    }
    private void coneHit(double range,double degrees,float damage,double knockback){
        Vec3 f=getLookAngle().multiply(1,0,1);if(f.lengthSqr()<.001)f=new Vec3(0,0,1);f=f.normalize();double cos=Math.cos(Math.toRadians(degrees*.5));
        for(ServerPlayer p:participants()){Vec3 v=p.position().subtract(position()).multiply(1,0,1);double d=v.length();if(d<=range&&d>.001&&f.dot(v.normalize())>=cos){damage(p,damage);push(p,v.normalize(),knockback);}}
    }
    private void arcHit(double fromDeg,double toDeg,double radius,float damage){
        Vec3 f=getLookAngle().multiply(1,0,1);double base=Math.atan2(f.z,f.x);
        for(ServerPlayer p:participants()){Vec3 v=p.position().subtract(position()).multiply(1,0,1);double d=v.length();if(d>radius||d<1)continue;double ang=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(v.z,v.x)-base));if(ang>=fromDeg&&ang<=toDeg){damage(p,damage);push(p,v.normalize(),.85);}}
    }
    private void circleHit(double radius,float damage){Vec3 c=stage==null?position():Vec3.atBottomCenterOf(stage);for(ServerPlayer p:participants())if(horizontalDistance(p.position(),c)<=radius)damage(p,damage);}
    private void ringHit(double radius,double width,float damage){Vec3 c=stage==null?position():Vec3.atBottomCenterOf(stage);for(ServerPlayer p:participants()){double d=horizontalDistance(p.position(),c);if(Math.abs(d-radius)<=width)damage(p,damage);}}
    private void lineHit(Vec3 a,Vec3 b,double width,float damage){for(ServerPlayer p:participants())if(distanceToSegment(p.position().add(0,1,0),a,b)<=width){damage(p,damage);push(p,p.position().subtract(position()).multiply(1,0,1).normalize(),.65);}}
    private void capsuleHit(Vec3 a,Vec3 b,double width,float damage){lineHit(a.add(0,1,0),b.add(0,1,0),width,damage);}
    private void damage(ServerPlayer p,float amount){p.hurt(damageSources().mobAttack(this),amount);}
    private void push(ServerPlayer p,Vec3 dir,double strength){if(dir.lengthSqr()>.001)p.push(dir.x*strength,.16,dir.z*strength);}

    private void stageRing(double radius,int count){
        if(!(level() instanceof ServerLevel sl)||stage==null)return;Vec3 c=Vec3.atBottomCenterOf(stage).add(0,.08,0);
        for(int i=0;i<count;i++){double a=i*Math.PI*2/count;sl.sendParticles(MaestroParticles.ARC,c.x+Math.cos(a)*radius,c.y,c.z+Math.sin(a)*radius,1,0,0,0,0);}
    }
    private void stagePulse(double radius,int count){stageRing(radius,count);if(level() instanceof ServerLevel sl&&stage!=null){Vec3 c=Vec3.atBottomCenterOf(stage).add(0,.12,0);sl.sendParticles(MaestroParticles.RING,c.x,c.y,c.z,3,.15,.02,.15,0);}}
    private void burst(Vec3 p,int count){if(level() instanceof ServerLevel sl)sl.sendParticles(MaestroParticles.BURST,p.x,p.y,p.z,count,.7,.5,.7,.03);}
    private void drawLine(Vec3 a,Vec3 b,int count){if(!(level() instanceof ServerLevel sl))return;for(int i=0;i<count;i++){Vec3 p=a.lerp(b,i/(double)Math.max(1,count-1));sl.sendParticles(MaestroParticles.NOTE,p.x,p.y,p.z,1,0,0,0,0);}}
    private void sound(net.minecraft.sounds.SoundEvent event,float volume,float pitch){level().playSound(null,blockPosition(),event,SoundSource.HOSTILE,volume,pitch);}
    private void sound(net.minecraft.sounds.SoundEvent event,SoundSource source,float volume,float pitch){level().playSound(null,blockPosition(),event,source,volume,pitch);}

    private static Vec3 rotateY(Vec3 v,double radians){double c=Math.cos(radians),s=Math.sin(radians);return new Vec3(v.x*c-v.z*s,v.y,v.x*s+v.z*c);}
    private static double horizontalDistance(Vec3 a,Vec3 b){double x=a.x-b.x,z=a.z-b.z;return Math.sqrt(x*x+z*z);}
    private static double distanceToSegment(Vec3 p,Vec3 a,Vec3 b){Vec3 ab=b.subtract(a);double len=ab.lengthSqr();if(len<1e-6)return p.distanceTo(a);double t=Mth.clamp(p.subtract(a).dot(ab)/len,0,1);return p.distanceTo(a.add(ab.scale(t)));}

    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override public boolean removeWhenFarAway(double distance){return false;}

    @Override public MasterVitality wardboundVitality(){return vitality;}
    @Override public boolean wardboundVitalityActive(){return initialized&&entityData.get(STATE_DATA)!=DYING;}
    @Override public boolean wardboundVitalityLocked(){return isInvulnerable()||entityData.get(STATE_DATA)==DYING;}
    @Override public void wardboundVitalityChanged(){bar.setProgress(vitality.fraction());}

    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(owner!=null)tag.putUUID("MaestroOwner",owner);if(stage!=null)tag.putLong("MaestroStage",stage.asLong());tag.putBoolean("MaestroPractice",practice);tag.putBoolean("MaestroInitialized",initialized);tag.putBoolean("MaestroRewarded",rewarded);tag.putDouble("MaestroVitalityMax",vitality.maximum());tag.putDouble("MaestroVitality",vitality.current());tag.putInt("MaestroPhase",phase());tag.putInt("MaestroState",combatState());tag.putInt("MaestroStateTick",stateTick);tag.putInt("MaestroCooldown",cooldown);tag.putInt("MaestroDeathTick",deathTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);owner=tag.hasUUID("MaestroOwner")?tag.getUUID("MaestroOwner"):null;stage=tag.contains("MaestroStage")?BlockPos.of(tag.getLong("MaestroStage")):null;practice=tag.getBoolean("MaestroPractice");initialized=tag.getBoolean("MaestroInitialized");rewarded=tag.getBoolean("MaestroRewarded");double max=tag.contains("MaestroVitalityMax")?tag.getDouble("MaestroVitalityMax"):BASE_HEALTH;double cur=tag.contains("MaestroVitality")?tag.getDouble("MaestroVitality"):max;vitality=new MasterVitality(max);vitality.restore(cur);vitality.bindMaximum(this);entityData.set(PHASE_DATA,Math.max(1,Math.min(3,tag.getInt("MaestroPhase"))));entityData.set(STATE_DATA,tag.getInt("MaestroState"));stateTick=tag.getInt("MaestroStateTick");cooldown=tag.getInt("MaestroCooldown");deathTicks=tag.getInt("MaestroDeathTick");applyPhaseAttributes(phase());bar.setProgress(vitality.fraction());
    }
}
