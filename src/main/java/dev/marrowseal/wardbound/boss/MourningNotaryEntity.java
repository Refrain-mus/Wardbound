package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.net.MasterLinePacket;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import dev.marrowseal.wardbound.net.MasterDamageReactionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/** A rule-reading Master: precise telegraphs, temporary laws, and punish windows. */
public final class MourningNotaryEntity extends Monster implements GeoEntity, CinematicVitalityHost {
    public static final int MANIFEST=0,REST=1,DECLARE=2,STAMP=3,TOLL=4,JUDGMENT=5,UNBOUND=6,COLLAPSE=7;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(MourningNotaryEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.wardbound.mourning_notary"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> participants=new LinkedHashSet<>();
    private final Map<UUID,Vec3> ruleStart=new HashMap<>();
    private final Map<UUID,Integer> absent=new HashMap<>();
    private final List<Vec3> stamps=new ArrayList<>();
    private final Map<UUID,Integer> verdictQuiet=new HashMap<>();
    private final Map<UUID,Boolean> verdictPrevCrouch=new HashMap<>();
    private final Set<UUID> verdictBraced=new HashSet<>();
    private static final int FINAL_NOTICE_WINDOW_TICKS=4;
    private static final double PROXY_MAX_HEALTH=MasterVitality.REGISTRATION_MAX_HEALTH;
    private BlockPos center;
    private BossConfig.Settings tuning=BossConfig.notary();
    private MasterVitality vitality=new MasterVitality(8000);
    private boolean practice,attackHit,collapsing,lineCross,silasFinalPhaseTriggered;
    private int state=MANIFEST,clock,phase=1,cycle,rule,secondaryRule,variant,emptyTicks,resonanceWave,totalTicks,recoveryTicks,lastAttackState=-1; // rule 1 move, 2 still
    private int verdictStartCount,verdictStrikeTick;
    private boolean lineX;
    private double lineOffset,secondOffset;

    public MourningNotaryEntity(EntityType<? extends Monster> type,Level level){super(type,level);setNoGravity(true);setPersistenceRequired();noCulling=true;xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,PROXY_MAX_HEALTH).add(Attributes.ARMOR,12).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(VIEW,new CompoundTag());}
    public CompoundTag view(){return entityData.get(VIEW);}
    public boolean includes(UUID id){return participants.contains(id);}
    public boolean silasFinalPhase(){return silasFinalPhaseTriggered || phase>=3 || vitality.fraction()<=.333f;}

    public void begin(ServerPlayer owner,BlockPos at,boolean debug){
        center=at.immutable();practice=debug;tuning=BossConfig.notary();participants.clear();
        for(ServerPlayer p:owner.serverLevel().players())if(p.isAlive()&&!p.isSpectator()&&(!p.isCreative()||debug)&&p.distanceToSqr(Vec3.atCenterOf(at))<=40*40)participants.add(p.getUUID());
        participants.add(owner.getUUID());int allies=Math.max(0,participants.size()-1);double hp=tuning.health()+allies*tuning.healthPerAlly();
        vitality=new MasterVitality(hp);vitality.syncNativeHealth(this);state=MANIFEST;clock=0;phase=1;cycle=0;totalTicks=0;recoveryTicks=0;lastAttackState=-1;collapsing=false;attackHit=false;silasFinalPhaseTriggered=false;emptyTicks=0;
        rule=secondaryRule=variant=resonanceWave=0;verdictStartCount=verdictStrikeTick=0;lineCross=false;lineX=false;lineOffset=secondOffset=0;absent.clear();ruleStart.clear();stamps.clear();verdictQuiet.clear();verdictPrevCrouch.clear();verdictBraced.clear();setInvisible(false);setInvulnerable(true);
        tell("NOTICE OF APPEARANCE // Your presence has been admitted before the event it is meant to witness.");
        dev.marrowseal.wardbound.champion.SilasMasterEncounter.onMasterBegin(owner,dev.marrowseal.wardbound.champion.SilasMasterEncounter.NOTARY,this::includes);sound("manifest");syncView();
    }

    private List<ServerPlayer> active(){
        if(center==null || !(level() instanceof ServerLevel sl))return List.of();List<ServerPlayer> out=new ArrayList<>();
        for(UUID id:participants){ServerPlayer p=sl.getServer().getPlayerList().getPlayer(id);if(p!=null&&p.isAlive()&&p.level()==sl&&!p.isSpectator()&&(!p.isCreative()||practice)&&p.distanceToSqr(Vec3.atCenterOf(center))<96*96)out.add(p);}return out;
    }
    private void tell(String text){if(!tuning.dialogue()||text==null||text.isBlank())return;int duration=dialogueDurationMs(text);for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY",text,duration));}
    private int dialogueDurationMs(String text){
        int words=text.trim().split("\\s+").length,reading=1500+words*210;
        int remaining=switch(state){
            case MANIFEST->Math.max(30,100-clock);case DECLARE->Math.max(30,(variant==3?104:variant==2?96:variant==1?88:80)-clock);
            case STAMP->Math.max(30,(variant==2?88:variant==1?96:62)-clock);case TOLL->Math.max(30,(variant==3?108:variant==2?96:variant==1?92:72)-clock);
            case JUDGMENT->Math.max(30,(variant==3?Math.max(80,verdictStrikeTick+18):variant==2?116:variant==1?100:80)-clock);case UNBOUND->Math.max(30,60-clock);case COLLAPSE->Math.max(30,112-clock);default->48;
        };
        return Math.max(2200,Math.min(7800,Math.max(reading,remaining*50)));
    }
    private void motionBeat(int phase,float strength){
        MasterAnimationBeatPacket pkt=new MasterAnimationBeatPacket(getId(),MasterAnimationBeatPacket.NOTARY,phase,strength,getId()*419+tickCount*23+state*41+clock*7+phase);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    private void damageReaction(int kind,float strength,int seed){
        MasterDamageReactionPacket pkt=new MasterDamageReactionPacket(getId(),MasterDamageReactionPacket.NOTARY,kind,strength,seed);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    private void sound(String key){
        if(!(level() instanceof ServerLevel sl))return;
        switch(key){
            case "manifest" -> {sl.playSound(null,blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,.92f,.54f);sl.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.36f,.62f);}
            case "declare" -> {sl.playSound(null,blockPosition(),SoundEvents.ANVIL_PLACE,SoundSource.HOSTILE,.42f,1.55f);sl.playSound(null,blockPosition(),SoundEvents.EVOKER_PREPARE_SUMMON,SoundSource.HOSTILE,.48f,.76f);}
            case "stamp" -> {sl.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.32f,1.62f);sl.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.44f,.72f);}
            case "toll" -> {sl.playSound(null,blockPosition(),SoundEvents.BELL_BLOCK,SoundSource.HOSTILE,1.05f,.52f);sl.playSound(null,blockPosition(),SoundEvents.SOUL_ESCAPE,SoundSource.HOSTILE,.42f,.62f);}
            case "judgment" -> {sl.playSound(null,blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,1.0f,.44f);sl.playSound(null,blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.HOSTILE,.48f,.58f);}
            case "impact" -> {sl.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.24f,1.82f);sl.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.HOSTILE,.38f,.68f);}
            case "unbound" -> {sl.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.72f,.74f);sl.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.52f,1.18f);}
            case "collapse" -> {sl.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.13f,.54f);sl.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.28f,.55f);}
            default -> { }
        }
    }


    private void timelineSound(SoundEvent sound,float volume,float pitch){
        if(level() instanceof ServerLevel sl)sl.playSound(null,blockPosition(),sound,SoundSource.HOSTILE,volume,pitch);
    }
    private void playNotaryAnimationSounds(){
        switch(state){
            case DECLARE -> {
                if(clock==8)timelineSound(SoundEvents.CHAIN_PLACE,.32f,.84f);
                if(clock==18)timelineSound(SoundEvents.IRON_TRAPDOOR_OPEN,.34f,.96f);
                if(clock==34 && (variant==2||variant==3))timelineSound(SoundEvents.BELL_RESONATE,.62f,variant==3?.48f:.58f);
                if(variant==3 && (clock==50||clock==66))timelineSound(SoundEvents.IRON_TRAPDOOR_CLOSE,.42f,clock==50?.74f:.62f);
            }
            case STAMP -> {
                if(clock==12)timelineSound(SoundEvents.CHAIN_PLACE,.28f,.76f);
                if(clock==24)timelineSound(SoundEvents.ANVIL_PLACE,.26f,1.34f);
                if(variant==2){
                    if(clock==32||clock==50||clock==68)timelineSound(SoundEvents.HONEYCOMB_WAX_ON,.38f,.84f);
                    if(clock==36||clock==54||clock==72)timelineSound(SoundEvents.ANVIL_LAND,.32f,1.28f);
                }else if(variant==1){
                    if(clock==44||clock==56)timelineSound(SoundEvents.HONEYCOMB_WAX_ON,.38f,.84f);
                    if(clock==48||clock==60)timelineSound(SoundEvents.ANVIL_LAND,.32f,1.28f);
                }else{
                    if(clock==44)timelineSound(SoundEvents.HONEYCOMB_WAX_ON,.38f,.84f);
                    if(clock==48)timelineSound(SoundEvents.ANVIL_LAND,.32f,1.28f);
                }
            }
            case TOLL -> {
                if(clock==14)timelineSound(SoundEvents.CHAIN_PLACE,.28f,.72f);
                if(clock==26)timelineSound(SoundEvents.BELL_RESONATE,.62f,.54f);
                if(variant==3){if(clock==34||clock==52||clock==70||clock==88)timelineSound(SoundEvents.BELL_BLOCK,.90f,clock==52||clock==88?.42f:.56f);}
                else if(variant==2){if(clock==34||clock==52||clock==70)timelineSound(SoundEvents.BELL_BLOCK,.86f,.48f);}
                else if(variant==1){if(clock==48||clock==74)timelineSound(SoundEvents.BELL_BLOCK,.86f,.48f);}
                else if(clock==62)timelineSound(SoundEvents.BELL_BLOCK,.86f,.48f);
            }
            case JUDGMENT -> {
                if(variant==3){
                    if(clock==8)timelineSound(SoundEvents.CHAIN_PLACE,.42f,.62f);
                    if(clock==12)timelineSound(SoundEvents.BELL_RESONATE,.82f,.42f);
                }else{
                    if(clock==14)timelineSound(SoundEvents.CHAIN_PLACE,.36f,.70f);
                    if(clock==28)timelineSound(SoundEvents.BELL_RESONATE,.72f,.46f);
                    if(clock==44)timelineSound(SoundEvents.HONEYCOMB_WAX_ON,.46f,.78f);
                    if(clock==62)timelineSound(SoundEvents.BELL_BLOCK,.98f,.44f);
                    if(clock==82)timelineSound(SoundEvents.ANVIL_LAND,.34f,1.12f);
                }
            }
            default -> {}
        }
    }

    @Override public void aiStep(){
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        setInvisible(false);
        // IMPORTANT: center is server-authoritative encounter state. It is not a SynchedEntityData
        // field by itself. Moving the entity from this unsynchronised field on the client used to
        // snap the combat Notary to world origin every client tick while the server kept the real
        // boss in the arena. The preview entity never exercised that path, which is why preview
        // summon rendered correctly while the bell encounter had a live boss bar, sounds and
        // damage but no visible body. Anchor only on the server and let normal entity position
        // packets keep the client at the authoritative arena position.
        if(level().isClientSide)return;
        if(center==null){
            // A raw /summon or malformed legacy NBT has no arena authority. Do not anchor to world
            // origin or execute encounter logic against an invented center; fail closed instead.
            bar.removeAllPlayers();
            discard();
            return;
        }
        setPos(center.getX()+.5,center.getY()+0.85,center.getZ()+.5);
        if(!(level() instanceof ServerLevel sl))return;
        if(collapsing){clock++;playCollapseTimeline();if(clock>=112){bar.removeAllPlayers();discard();}syncView();return;}
        for(UUID id:new ArrayList<>(participants)){
            ServerPlayer p=sl.getServer().getPlayerList().getPlayer(id);
            if(p==null || !p.isAlive() || p.level()!=sl || p.isSpectator() || (p.isCreative()&&!practice)){
                participants.remove(id); absent.remove(id); ruleStart.remove(id); if(p!=null)bar.removePlayer(p); continue;
            }
            if(p.distanceToSqr(Vec3.atCenterOf(center))>=96*96){
                int miss=absent.getOrDefault(id,0)+1; absent.put(id,miss);
                if(miss>=220){participants.remove(id); ruleStart.remove(id); bar.removePlayer(p);} 
            }else absent.remove(id);
        }
        List<ServerPlayer> players=active();for(ServerPlayer p:players)bar.addPlayer(p);for(ServerPlayer p:new ArrayList<>(bar.getPlayers()))if(!players.contains(p))bar.removePlayer(p);
        if(state!=MANIFEST)totalTicks++;if(totalTicks>tuning.timeoutSeconds()*20){tell("SESSION CLOSED // The witness failed to remain available for examination.");bar.removeAllPlayers();discard();return;}
        if(!players.isEmpty()&&state!=COLLAPSE&&state!=MANIFEST)getLookControl().setLookAt(players.get(0),state==REST?3.0f:1.35f,state==REST?2.0f:1.0f);
        bar.setProgress(vitality.fraction());
        if(players.isEmpty()){ruleStart.clear(); stamps.clear(); verdictQuiet.clear(); verdictPrevCrouch.clear(); verdictBraced.clear(); lineCross=false; secondaryRule=0; if(++emptyTicks>=100){bar.removeAllPlayers();discard();return;} syncView();return;} else emptyTicks=0;
        // A single tick may cross more than one threshold with aggressively tuned configs.
        // Resolve at most one article transition per tick so dialogue, VFX and state resets never stack.
        if(phase==1&&vitality.fraction()<=.5f){phase=2;setState(REST);tell("SECOND ARTICLE // The court may now file compound judgments.");sound("judgment");}
        else if(!silasFinalPhaseTriggered&&vitality.fraction()<=.333f){phase=3;silasFinalPhaseTriggered=true;setState(REST);tell("THIRD ARTICLE // That is sufficient. The final judgment admits no unresolved prior witness.");sound("judgment");dev.marrowseal.wardbound.champion.SilasMasterEncounter.expelAtFinalPhase(sl,position(),dev.marrowseal.wardbound.champion.SilasMasterEncounter.NOTARY,this::includes);}
        clock++;playNotaryAnimationSounds();switch(state){
            case MANIFEST -> {
                if(clock==12)sl.playSound(null,blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,1.0f,.46f);
                if(clock==30)sl.playSound(null,blockPosition(),SoundEvents.EVOKER_PREPARE_SUMMON,SoundSource.HOSTILE,.72f,.62f);
                if(clock==48)sl.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.70f,.52f);
                if(clock==66)sl.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.18f,.58f);
                if(clock==80)sl.playSound(null,blockPosition(),SoundEvents.BELL_BLOCK,SoundSource.HOSTILE,1.15f,.44f);
                if(clock>=100)setState(REST);
            }
            case REST -> {if(recoveryTicks>0)recoveryTicks--;else if(clock>=Math.max(18,(int)(36/Math.max(.25,tuning.attackSpeed()))))chooseAttack();}
            case DECLARE -> tickDeclare(players);
            case STAMP -> tickStamp(players);
            case TOLL -> tickToll(players);
            case JUDGMENT -> tickJudgment(players);
            case UNBOUND -> {if(clock==1){tell("DEFENSE SIGNATURE BROKEN // The record cannot protect its author while it is being amended.");sound("unbound");}if(clock>=60)setState(REST);}
        }
        fallbackCombatTelegraph();
        syncView();
    }

    /**
     * Vanilla-particle safety layer for the court. The textured/Lodestone layer remains the main
     * presentation, but these marks guarantee that a damaging rule is still readable when a
     * client rendering mod interferes with custom translucent geometry.
     */
    private void fallbackCombatTelegraph(){
        if(!(level() instanceof ServerLevel sl)||clock<0||clock%4!=0)return;
        if(state==MANIFEST){
            if(clock%8==0)sl.sendParticles(ParticleTypes.ENCHANT,getX(),getY()+3.5,getZ(),10,2.3,2.4,2.3,.02);
            return;
        }
        if(state==JUDGMENT&&variant==3){fallbackStamps(sl);return;}
        if(state==DECLARE||state==JUDGMENT)fallbackLines(sl);
        if(state==STAMP||state==JUDGMENT)fallbackStamps(sl);
        if(state==TOLL||state==JUDGMENT)fallbackToll(sl);
    }

    private void fallbackLines(ServerLevel sl){
        // Article-III gates move independently of lineOffset/secondOffset. The old vanilla fallback
        // drew stale static lines, so clients with custom translucent VFX disabled saw a safe-looking
        // floor while the server was damaging moving gates.
        if(variant==3 && state==DECLARE){
            double gate=Math.max(2.0,11.5-Math.max(0,clock-34)*.22);
            fallbackGateLine(sl,false, gate,ParticleTypes.END_ROD);fallbackGateLine(sl,false,-gate,ParticleTypes.END_ROD);
            fallbackGateLine(sl,true, gate,ParticleTypes.ENCHANT);fallbackGateLine(sl,true,-gate,ParticleTypes.ENCHANT);
            return;
        }
        if(variant==2 && state==DECLARE){
            double gate=Math.max(1.6,10.5-Math.max(0,clock-34)*.22);
            fallbackGateLine(sl,gate,ParticleTypes.END_ROD);fallbackGateLine(sl,-gate,ParticleTypes.END_ROD);
            if(clock>=62)fallbackGateLine(sl,0,ParticleTypes.ENCHANT);
            return;
        }
        if(variant==2 && state==JUDGMENT){
            fallbackGateLine(sl,5.2,ParticleTypes.END_ROD);fallbackGateLine(sl,-5.2,ParticleTypes.END_ROD);
            return;
        }
        for(int i=-12;i<=12;i+=2){
            double x=center.getX()+.5+(lineX?i:lineOffset);
            double z=center.getZ()+.5+(lineX?lineOffset:i);
            sl.sendParticles(ParticleTypes.END_ROD,x,center.getY()+.18,z,1,.06,.03,.06,0);
            if(lineCross){
                double x2=center.getX()+.5+(lineX?secondOffset:i);
                double z2=center.getZ()+.5+(lineX?i:secondOffset);
                sl.sendParticles(ParticleTypes.ENCHANT,x2,center.getY()+.20,z2,1,.05,.02,.05,0);
            }
        }
    }
    private void fallbackGateLine(ServerLevel sl,double offset,net.minecraft.core.particles.ParticleOptions particle){
        for(int i=-12;i<=12;i+=2){
            double x=center.getX()+.5+(lineX?i:offset),z=center.getZ()+.5+(lineX?offset:i);
            sl.sendParticles(particle,x,center.getY()+.19,z,1,.06,.03,.06,0);
        }
    }
    private void fallbackGateLine(ServerLevel sl,boolean axisZ,double offset,net.minecraft.core.particles.ParticleOptions particle){
        for(int i=-12;i<=12;i+=2){
            double x=center.getX()+.5+(axisZ?i:offset),z=center.getZ()+.5+(axisZ?offset:i);
            sl.sendParticles(particle,x,center.getY()+.19,z,1,.06,.03,.06,0);
        }
    }

    private void fallbackStamps(ServerLevel sl){
        int cap=Math.min(10,stamps.size());
        for(int i=0;i<cap;i++){
            Vec3 s=stamps.get(i);
            double x=center.getX()+.5+s.x,z=center.getZ()+.5+s.z;
            sl.sendParticles(ParticleTypes.WITCH,x,center.getY()+.20,z,2,.28,.06,.28,.01);
            sl.sendParticles(ParticleTypes.END_ROD,x,center.getY()+1.6,z,1,.08,1.1,.08,.01);
        }
    }

    private void fallbackToll(ServerLevel sl){
        if(state==TOLL&&variant==3){
            double[] radii={3.8,10.8,6.6,12.8};
            for(int r=0;r<radii.length;r++)fallbackTollRing(sl,radii[r],r==Math.max(0,resonanceWave-1)?ParticleTypes.SOUL_FIRE_FLAME:ParticleTypes.ENCHANT);
            return;
        }
        if(state==TOLL&&variant==2){
            // All three Funeral Resonance shells are future damage geometry. Showing only the
            // currently active radius made rings two and three appear at the same tick they hit
            // when the premium renderer was unavailable. Keep the whole filed sequence readable.
            double[] radii={5.0,9.0,12.5};
            for(int r=0;r<radii.length;r++)fallbackTollRing(sl,radii[r],r==resonanceWave?ParticleTypes.SOUL_FIRE_FLAME:ParticleTypes.ENCHANT);
            return;
        }
        double radius=state==JUDGMENT?8.4:5.5+Math.min(5.0,clock*.06);
        fallbackTollRing(sl,radius,ParticleTypes.SOUL_FIRE_FLAME);
    }
    private void fallbackTollRing(ServerLevel sl,double radius,net.minecraft.core.particles.ParticleOptions particle){
        int points=28;
        for(int i=0;i<points;i++){
            double a=i*Math.PI*2/points;
            double x=center.getX()+.5+Math.cos(a)*radius,z=center.getZ()+.5+Math.sin(a)*radius;
            sl.sendParticles(particle,x,center.getY()+.22,z,1,.03,.04,.03,0);
        }
    }

    private void chooseAttack(){
        cycle++;attackHit=false;stamps.clear();ruleStart.clear();resonanceWave=0;
        int next,forced;
        // Article III gains distinct filings instead of only making old articles denser.
        if(phase>=3&&cycle%9==2){next=JUDGMENT;forced=3;}
        else if(phase>=3&&cycle%8==0){next=DECLARE;forced=3;}
        else if(phase>=3&&cycle%8==4){next=TOLL;forced=3;}
        else if(phase>=2&&cycle%7==0){next=JUDGMENT;forced=2;}
        else if(phase>=2&&cycle%5==0){next=JUDGMENT;forced=1;}
        else {int slot=Math.floorMod(cycle,3);next=slot==0?DECLARE:slot==1?STAMP:TOLL;forced=phase>=2?((next==STAMP?(cycle%2==1):(cycle%2==0))?2:1):0;}
        // The authored scheduler already rotates well; this guard makes that invariant explicit and
        // survives future pattern edits/reloads without allowing the same base attack back-to-back.
        if(next==lastAttackState){
            int[] rotate={DECLARE,STAMP,TOLL};
            for(int candidate:rotate)if(candidate!=lastAttackState){next=candidate;break;}
            forced=phase>=2?1:0;
        }
        lastAttackState=next;setState(next,forced);
    }
    private void setState(int next){setState(next,0);}    
    private void setState(int next,int forcedVariant){
        int previous=state;state=next;clock=0;attackHit=false;variant=forcedVariant;lineCross=false;secondaryRule=0;secondOffset=0;resonanceWave=0;verdictStartCount=verdictStrikeTick=0;verdictQuiet.clear();verdictPrevCrouch.clear();verdictBraced.clear();
        if(next==REST && (previous==DECLARE||previous==STAMP||previous==TOLL||previous==JUDGMENT||previous==UNBOUND))recoveryTicks=previous==JUDGMENT?12:9;
        else if(next!=REST)recoveryTicks=0;
        setInvulnerable(next==MANIFEST||next==COLLAPSE);
        if(next==REST||next==UNBOUND||next==COLLAPSE)motionBeat(MasterAnimationBeatPacket.CLEAR,0);
        else if(next==DECLARE||next==STAMP||next==TOLL||next==JUDGMENT)motionBeat(MasterAnimationBeatPacket.ANTICIPATE,next==JUDGMENT?1.0f:variant>=2?.88f:.68f);
        if(next==DECLARE){
            lineX=random.nextBoolean();lineOffset=random.nextInt(11)-5;
            if(variant==3){lineCross=true;lineOffset=11.5;secondOffset=-11.5;tell("CLOSING DOCKET // Four filing walls are contracting. Keep within the margin without touching the record.");sound("declare");}
            else if(variant==2){lineOffset=10;secondOffset=-10;tell("TRIBUNAL ARCH // Three witness gates are entering the court.");sound("declare");}
            else if(variant==1){lineCross=true;secondOffset=random.nextInt(9)-4;tell("CROSS-EXAMINATION // Two filing lines have been entered. Read both before they close.");sound("declare");}
            else {tell("DECLARATION // A ruled finding has been entered. Leave the seal-wall before it closes.");sound("declare");}
        }else if(next==STAMP){
            makeStamps(variant==2?2:variant==1?1:0);
            tell(variant==2?"VERDICT CASCADE // The court will stamp the floor three times.":variant==1?"COUNTERSIGNED FINDINGS // The first seals will restamp before the article closes.":"SEALED FINDINGS // Delayed seals have been entered beneath your feet.");sound("stamp");
        }else if(next==TOLL){
            if(variant==3){rule=0;secondaryRule=0;tell("APPEAL WINDOW // Near and far bell-shells will alternate. Do not trust the direction of the previous toll.");sound("toll");}
            else if(variant==2){rule=0;secondaryRule=0;tell("FUNERAL RESONANCE // Three bell-shells will pass through the court.");sound("toll");}
            else {rule=random.nextBoolean()?1:2;if(variant==1){secondaryRule=rule==1?2:1;tell(rule==1?"DUAL TOLL // Move now, then survive the stillness that follows.":"DUAL TOLL // Remain still now, then survive the motion that follows.");}else tell(rule==1?"TEMPORARY LAW // MOVEMENT IS REQUIRED.":"TEMPORARY LAW // STILLNESS IS REQUIRED.");captureRuleStart();sound("toll");}
        }else if(next==JUDGMENT){
            if(variant==3){
                verdictStartCount=2+random.nextInt(3);verdictStrikeTick=18+(verdictStartCount-1)*16+12;stamps.clear();
                for(ServerPlayer p:active()){verdictQuiet.put(p.getUUID(),0);verdictPrevCrouch.put(p.getUUID(),p.isShiftKeyDown());}
                tell("FINAL NOTICE // The court will count what remains. Brace only at the last instant; a held objection is inadmissible.");sound("judgment");
            }else{
                lineX=random.nextBoolean();lineOffset=random.nextInt(9)-4;rule=random.nextBoolean()?1:2;makeStamps(variant==2?2:variant==1?1:0);captureRuleStart();
                if(variant==2){lineCross=true;secondOffset=-lineOffset;secondaryRule=rule==1?2:1;tell("FINAL APPEAL // Gate, seal, bell and supplemental law will be filed in one article.");sound("judgment");}
                else if(variant==1){lineCross=true;secondOffset=random.nextInt(7)-3;secondaryRule=rule==1?2:1;tell("TRIPLICATE JUDGMENT // The article contains two lines, repeated seals and a second law in reserve.");sound("judgment");}
                else {tell("JOINT JUDGMENT // A gate, a seal and a temporary law have been filed together.");sound("judgment");}
            }
        }
    }
    private void captureRuleStart(){ruleStart.clear();for(ServerPlayer p:active())ruleStart.put(p.getUUID(),p.position());}
    private void makeStamps(int mode){
        stamps.clear();List<ServerPlayer> ps=active();if(ps.isEmpty())return;
        int n=mode==2?Math.max(6,Math.min(10,ps.size()+5)):Math.max(mode==1?5:4,Math.min(mode==1?9:7,ps.size()+(mode==1?4:3)));
        for(int i=0;i<n;i++){ServerPlayer p=ps.get(i%ps.size());double a=i*Math.PI*2/n+random.nextDouble()*(mode==2?.72:.4),r=(mode==2?1.6:mode==1?2.2:2.5)+random.nextDouble()*(mode==2?7.6:mode==1?6.5:5);double x=MthClamp(p.getX()-center.getX()-.5+Math.cos(a)*r,-12,12),z=MthClamp(p.getZ()-center.getZ()-.5+Math.sin(a)*r,-12,12);stamps.add(new Vec3(x,0,z));}
    }
    private static double MthClamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}

    private void tickDeclare(List<ServerPlayer> ps){
        if(variant==3){
            if(clock>=34&&clock<=78&&clock%4==0){
                double gate=Math.max(2.0,11.5-(clock-34)*.22);
                for(ServerPlayer p:ps){double dx=Math.abs(p.getX()-center.getX()-.5),dz=Math.abs(p.getZ()-center.getZ()-.5);if(Math.abs(dx-gate)<1.02||Math.abs(dz-gate)<1.02)punish(p,10);}
            }
            if(clock==50||clock==66){motionBeat(MasterAnimationBeatPacket.RELEASE,clock==66?1.14f:1.02f);sound("impact");}
            if(clock>=104)finishAttack();return;
        }
        if(variant==2){
            if(clock>=34&&clock<=72&&clock%4==0){double gate=Math.max(1.6,10.5-(clock-34)*.22);for(ServerPlayer p:ps){double axis=lineX?p.getZ()-center.getZ()-.5:p.getX()-center.getX()-.5;boolean hit=Math.abs(Math.abs(axis)-gate)<1.05;if(clock>=62)hit|=Math.abs(axis)<1.35;if(hit)punish(p,10);}}
            if(clock==50||clock==66){motionBeat(MasterAnimationBeatPacket.RELEASE,1.08f);sound("impact");}
            if(clock>=96)finishAttack();return;
        }
        if(clock>=42&&clock<=54&&clock%4==0)for(ServerPlayer p:ps){
            double local=lineX?p.getZ()-center.getZ()-.5:p.getX()-center.getX()-.5;
            boolean hit=Math.abs(local-lineOffset)<(variant==1?.96:1.05);
            if(lineCross){double local2=lineX?p.getX()-center.getX()-.5:p.getZ()-center.getZ()-.5;hit|=Math.abs(local2-secondOffset)<.96;}
            if(hit)punish(p,variant==1?9:8);
        }
        if(clock==42){motionBeat(MasterAnimationBeatPacket.RELEASE,variant==1?1.02f:.90f);sound("impact");}
        if(clock>= (variant==1?88:80))finishAttack();
    }
    private void tickStamp(List<ServerPlayer> ps){
        if(variant==2){
            if(clock==36||clock==54||clock==72){for(Vec3 s:stamps)for(ServerPlayer p:ps){double dx=p.getX()-(center.getX()+.5+s.x),dz=p.getZ()-(center.getZ()+.5+s.z);if(dx*dx+dz*dz<2.35*2.35)punish(p,10);}motionBeat(MasterAnimationBeatPacket.RELEASE,1.05f);sound("impact");resonanceWave++;}
            if(clock==37||clock==55)makeStamps(2);
            if(clock>=88)finishAttack();return;
        }
        if(clock==48|| (variant==1 && clock==60)){for(Vec3 s:stamps)for(ServerPlayer p:ps){double r=variant==1&&clock==60?2.25:2.7;double dx=p.getX()-(center.getX()+.5+s.x),dz=p.getZ()-(center.getZ()+.5+s.z);if(dx*dx+dz*dz<r*r)punish(p,variant==1&&clock==60?8:11);}motionBeat(MasterAnimationBeatPacket.RELEASE,clock==60?.92f:1.06f);sound("impact");}
        if(clock>= (variant==1?96:62))finishAttack();
    }
    private void tickToll(List<ServerPlayer> ps){
        if(variant==3){
            int[] times={34,52,70,88};double[] radii={3.8,10.8,6.6,12.8};
            for(int i=0;i<times.length;i++)if(clock==times[i]){double rr=radii[i];for(ServerPlayer p:ps){double dx=p.getX()-center.getX()-.5,dz=p.getZ()-center.getZ()-.5,dist=Math.sqrt(dx*dx+dz*dz);if(Math.abs(dist-rr)<1.40)punish(p,11);}resonanceWave=i+1;motionBeat(MasterAnimationBeatPacket.RELEASE,1.04f+i*.08f);sound("toll");}
            if(clock>=108)finishAttack();return;
        }
        if(variant==2){
            int[] times={34,52,70};double[] radii={5.0,9.0,12.5};
            for(int i=0;i<times.length;i++)if(clock==times[i]){double rr=radii[i];for(ServerPlayer p:ps){double dx=p.getX()-center.getX()-.5,dz=p.getZ()-center.getZ()-.5,dist=Math.sqrt(dx*dx+dz*dz);if(Math.abs(dist-rr)<1.45)punish(p,11);}resonanceWave=i+1;motionBeat(MasterAnimationBeatPacket.RELEASE,1.12f+i*.08f);sound("toll");}
            if(clock>=96)finishAttack();return;
        }
        if(variant==0){if(clock==62){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,.96f);sound("impact");}if(clock>=72)finishAttack();return;}
        if(clock==48){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,.92f);sound("impact");rule=secondaryRule;secondaryRule=0;captureRuleStart();tell(rule==1?"SUPPLEMENTAL LAW // Movement is now required.":"SUPPLEMENTAL LAW // Stillness is now required.");}
        if(clock==74){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,1.02f);sound("impact");}
        if(clock>=92)finishAttack();
    }
    private void tickJudgment(List<ServerPlayer> ps){
        if(variant==3){tickFinalNotice(ps);return;}
        if(variant==2){
            if(clock>=30&&clock<=46&&clock%4==0)for(ServerPlayer p:ps){double axis=lineX?p.getZ()-center.getZ()-.5:p.getX()-center.getX()-.5;if(Math.abs(Math.abs(axis)-5.2)<1.0)punish(p,9);}
            if(clock==48){for(Vec3 s:stamps)for(ServerPlayer p:ps){double dx=p.getX()-(center.getX()+.5+s.x),dz=p.getZ()-(center.getZ()+.5+s.z);if(dx*dx+dz*dz<2.35*2.35)punish(p,10);}motionBeat(MasterAnimationBeatPacket.RELEASE,1.14f);sound("stamp");}
            if(clock==62){double rr=8.4;for(ServerPlayer p:ps){double dx=p.getX()-center.getX()-.5,dz=p.getZ()-center.getZ()-.5;if(Math.abs(Math.sqrt(dx*dx+dz*dz)-rr)<1.4)punish(p,11);}motionBeat(MasterAnimationBeatPacket.RELEASE,1.24f);sound("toll");resonanceWave=1;}
            if(clock==66){rule=secondaryRule;secondaryRule=0;captureRuleStart();tell(rule==1?"FINAL SUPPLEMENT // Move before the appeal closes.":"FINAL SUPPLEMENT // Hold still while the appeal closes.");}
            if(clock==84){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,1.34f);sound("impact");}
            if(clock>=116)finishAttack();return;
        }
        if(clock>=36&&clock<=50&&clock%4==0)for(ServerPlayer p:ps){double local=lineX?p.getZ()-center.getZ()-.5:p.getX()-center.getX()-.5;boolean hit=Math.abs(local-lineOffset)<.9;if(lineCross){double local2=lineX?p.getX()-center.getX()-.5:p.getZ()-center.getZ()-.5;hit|=Math.abs(local2-secondOffset)<.9;}if(hit)punish(p,variant==1?10:9);}
        if(clock==50 || (variant==1&&clock==62)){for(Vec3 s:stamps)for(ServerPlayer p:ps){double rr=clock==50?2.35:2.1;double dx=p.getX()-(center.getX()+.5+s.x),dz=p.getZ()-(center.getZ()+.5+s.z);if(dx*dx+dz*dz<rr*rr)punish(p,clock==50?10:8);}motionBeat(MasterAnimationBeatPacket.RELEASE,clock==50?1.08f:.94f);sound("stamp");}
        if(clock==58){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,.90f);}
        if(variant==1 && clock==64){rule=secondaryRule;secondaryRule=0;captureRuleStart();tell(rule==1?"SUPPLEMENTAL ARTICLE // Motion is now compulsory.":"SUPPLEMENTAL ARTICLE // Stillness is now compulsory.");}
        if(variant==1 && clock==80){evaluateRule(ps,rule);motionBeat(MasterAnimationBeatPacket.RELEASE,.98f);}
        if(clock>= (variant==1?100:80))finishAttack();
    }
    private void tickFinalNotice(List<ServerPlayer> ps){
        updateVerdictBrace(ps);
        for(int i=0;i<verdictStartCount;i++){
            int beat=18+i*16,digit=verdictStartCount-i;
            if(clock==beat){
                stamps.clear();for(ServerPlayer p:ps){double x=MthClamp(p.getX()-center.getX()-.5,-12,12),z=MthClamp(p.getZ()-center.getZ()-.5,-12,12);stamps.add(new Vec3(x,0,z));}
                for(ServerPlayer p:ps)Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY","COUNT // "+digit,900));
                timelineSound(SoundEvents.BELL_BLOCK,.92f,.42f+(.10f*(verdictStartCount-digit)));motionBeat(MasterAnimationBeatPacket.ANTICIPATE,.62f+digit*.08f);
            }
            if(clock==beat+8){
                for(Vec3 s:stamps)for(ServerPlayer p:ps){double dx=p.getX()-(center.getX()+.5+s.x),dz=p.getZ()-(center.getZ()+.5+s.z);if(dx*dx+dz*dz<2.1*2.1)punish(p,8);}
                motionBeat(MasterAnimationBeatPacket.RELEASE,.82f);sound("stamp");stamps.clear();
            }
        }
        if(clock==verdictStrikeTick-FINAL_NOTICE_WINDOW_TICKS)for(ServerPlayer p:ps)Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY","OBJECTION WINDOW // NOW.",850));
        if(clock==verdictStrikeTick){
            for(ServerPlayer p:ps){
                LightningBolt bolt=EntityType.LIGHTNING_BOLT.create((ServerLevel)level());if(bolt!=null){bolt.moveTo(p.getX(),p.getY(),p.getZ());bolt.setVisualOnly(true);((ServerLevel)level()).addFreshEntity(bolt);}
                if(verdictBraced.contains(p.getUUID())&&p.isShiftKeyDown()){
                    Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY","OBJECTION SUSTAINED // Timing admitted.",1700));
                    p.invulnerableTime=Math.max(p.invulnerableTime,8);
                }else{
                    attackHit=true;float remaining=p.getHealth()-p.getMaxHealth()*.75f;if(remaining<=0f)p.kill();else p.setHealth(remaining);
                    Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY","JUDGMENT ENTERED // The witness objected too early, too late, or continuously.",2300));
                }
            }
            timelineSound(SoundEvents.LIGHTNING_BOLT_THUNDER,1.0f,.72f);motionBeat(MasterAnimationBeatPacket.RELEASE,1.45f);
        }
        if(clock>=verdictStrikeTick+18)finishAttack();
    }
    private void updateVerdictBrace(List<ServerPlayer> ps){
        for(ServerPlayer p:ps){UUID id=p.getUUID();boolean crouch=p.isShiftKeyDown(),prev=verdictPrevCrouch.getOrDefault(id,crouch);int quiet=verdictQuiet.getOrDefault(id,0);
            if(!crouch)quiet=Math.min(40,quiet+1);else{if(!prev&&quiet>=10&&clock>=verdictStrikeTick-FINAL_NOTICE_WINDOW_TICKS&&clock<=verdictStrikeTick)verdictBraced.add(id);quiet=0;}
            verdictQuiet.put(id,quiet);verdictPrevCrouch.put(id,crouch);
        }
    }

    private void evaluateRule(List<ServerPlayer> ps,int activeRule){for(ServerPlayer p:ps){Vec3 start=ruleStart.get(p.getUUID());if(start==null)continue;Vec3 delta=p.position().subtract(start);double moved=Math.sqrt(delta.x*delta.x+delta.z*delta.z);boolean violated=activeRule==1?moved<(variant==1?3.7:4.2):moved>(variant==1?1.1:1.35);if(violated){punish(p,12);int law=activeRule;Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE MOURNING NOTARY",law==1?"BREACH // You remained where movement was required.":"BREACH // The record shows movement where stillness was required.",3200));}}}
    private void finishAttack(){if(!attackHit)setState(UNBOUND);else setState(REST);}
    private void punish(ServerPlayer p,float amount){attackHit=true;p.hurt(level().damageSources().magic(),(float)(amount*tuning.damageMultiplier()));}

    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        ServerPlayer attacker=null;
        if(source.getEntity() instanceof ServerPlayer direct)attacker=direct;
        else if(source.getEntity() instanceof dev.marrowseal.wardbound.champion.DefeatedChampionEntity silas
                && silas.companionActive()&&silas.companionOwnerId()!=null&&level() instanceof ServerLevel sl)
            attacker=sl.getServer().getPlayerList().getPlayer(silas.companionOwnerId());
        if(level().isClientSide||collapsing||state==MANIFEST||attacker==null||!participants.contains(attacker.getUUID())
                ||!active().contains(attacker)||!Float.isFinite(amount)||amount<=0)return false;
        float cap=(float)tuning.hitCap();
        float applied=Math.min(cap,amount);
        if(state==UNBOUND)applied*=1.65f;
        // AttributeFix exposes the real 8k+ health. The vitality ledger remains only to
        // intercept lethal hits safely for the authored collapse sequence.
        float before=vitality.prepareNativeDamage(this);MasterNativeDamageBridge.arm(this);boolean hit=super.hurt(source,applied);float actual=MasterNativeDamageBridge.consume(this,Math.max(0,before-getHealth()));
        if((hit||actual>0)&&actual>0){
            int reactionKind=state==UNBOUND?MasterDamageReactionPacket.VULNERABLE:(actual>=Math.max(24,cap*.42f)?MasterDamageReactionPacket.HEAVY:MasterDamageReactionPacket.LIGHT);
            if(state==TOLL||state==JUDGMENT||state==STAMP)reactionKind=MasterDamageReactionPacket.LIGHT;
            float reactionStrength=reactionKind==MasterDamageReactionPacket.VULNERABLE?1.22f:reactionKind==MasterDamageReactionPacket.HEAVY?.86f:.38f;
            damageReaction(reactionKind,reactionStrength,getId()*457+tickCount*19+state);
            if(vitality.damage(actual)){bar.setProgress(0f);beginCollapse();}else{bar.setProgress(vitality.fraction());vitality.syncNativeHealth(this);}
        }
        return hit||actual>0;
    }
    private void beginCollapse(){if(collapsing)return;collapsing=true;state=COLLAPSE;clock=0;recoveryTicks=0;stamps.clear();ruleStart.clear();lineCross=false;secondaryRule=0;resonanceWave=0;setHealth(1);setInvulnerable(true);motionBeat(MasterAnimationBeatPacket.CLEAR,0);if(center!=null && level() instanceof ServerLevel sl)MasterArenaManager.cleanupCombatTransients(sl,center);for(ServerPlayer p:active())NotaryProgression.victory(p,practice);tell("FINAL ENTRY // The signature line remains blank. This is not an acquittal.");sound("collapse");bar.setProgress(0);}
    /** Four-stage court shutdown: wrong bell, sequential seal failure, erased signature, final hush. */
    private void playCollapseTimeline(){
        if(!(level() instanceof ServerLevel sl))return;
        if(clock==1){sl.playSound(null,blockPosition(),SoundEvents.BELL_BLOCK,SoundSource.HOSTILE,1.06f,.31f);sl.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.48f,.43f);}
        if(clock==18||clock==34||clock==50||clock==66){float p=.92f-(clock-18)*.004f;sl.playSound(null,blockPosition(),SoundEvents.GLASS_BREAK,SoundSource.HOSTILE,.52f,p);sl.playSound(null,blockPosition(),SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.HOSTILE,.38f,p*.82f);}
        if(clock==82){sl.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.46f,.44f);sl.playSound(null,blockPosition(),SoundEvents.SOUL_ESCAPE,SoundSource.HOSTILE,.34f,.52f);}
        if(clock==100){sl.playSound(null,blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,.24f,.29f);sl.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.18f,.36f);}
    }
    @Override public void remove(RemovalReason reason){if(center!=null&&(reason==RemovalReason.DISCARDED||reason==RemovalReason.KILLED)&&level() instanceof ServerLevel sl)MasterArenaManager.cleanupCombatTransients(sl,center);bar.removeAllPlayers();super.remove(reason);}
    @Override public boolean removeWhenFarAway(double d){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}

    private void syncView(){CompoundTag v=new CompoundTag();v.putInt("state",state);v.putInt("clock",clock);v.putInt("recovery",recoveryTicks);v.putInt("phase",phase);v.putInt("rule",rule);v.putInt("secondary_rule",secondaryRule);v.putInt("variant",variant);v.putInt("resonance_wave",resonanceWave);v.putInt("verdict_start",verdictStartCount);v.putInt("verdict_strike",verdictStrikeTick);v.putFloat("health",vitality.fraction());v.putBoolean("line_x",lineX);v.putBoolean("line_cross",lineCross);v.putDouble("line_offset",lineOffset);v.putDouble("second_offset",secondOffset);if(center!=null){v.putInt("cx",center.getX());v.putInt("cy",center.getY());v.putInt("cz",center.getZ());}v.putUUID("bar",bar.getId());ListTag list=new ListTag();for(Vec3 s:stamps){CompoundTag q=new CompoundTag();q.putDouble("x",s.x);q.putDouble("z",s.z);list.add(q);}v.put("stamps",list);entityData.set(VIEW,v);}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putBoolean("NotaryCombatStateV2",true);
        tag.putInt("NotaryState",state);tag.putInt("NotaryClock",clock);tag.putInt("NotaryPhase",phase);tag.putInt("NotaryCycle",cycle);tag.putInt("NotaryLastAttack",lastAttackState);
        tag.putInt("NotaryRule",rule);tag.putInt("NotarySecondaryRule",secondaryRule);tag.putInt("NotaryVariant",variant);
        tag.putInt("NotaryEmptyTicks",emptyTicks);tag.putInt("NotaryResonanceWave",resonanceWave);tag.putInt("NotaryVerdictStart",verdictStartCount);tag.putInt("NotaryVerdictStrike",verdictStrikeTick);tag.putInt("NotaryTotalTicks",totalTicks);
        tag.putBoolean("NotaryAttackHit",attackHit);tag.putBoolean("NotaryLineCross",lineCross);tag.putBoolean("NotaryLineX",lineX);
        tag.putDouble("NotaryLineOffset",lineOffset);tag.putDouble("NotarySecondOffset",secondOffset);tag.putDouble("NotaryVitalityMax",vitality.maximum());tag.putDouble("NotaryVitalityCurrent",vitality.current());
        if(center!=null)tag.putLong("NotaryCenter",center.asLong());tag.putBoolean("NotaryPractice",practice);tag.putBoolean("SilasFinalPhase",silasFinalPhaseTriggered);
        ListTag ids=new ListTag();for(UUID id:participants){CompoundTag q=new CompoundTag();q.putUUID("id",id);ids.add(q);}tag.put("NotaryParticipants",ids);
        ListTag starts=new ListTag();for(var e:ruleStart.entrySet()){CompoundTag q=new CompoundTag();q.putUUID("id",e.getKey());Vec3 v=e.getValue();q.putDouble("x",v.x);q.putDouble("y",v.y);q.putDouble("z",v.z);starts.add(q);}tag.put("NotaryRuleStarts",starts);
        ListTag misses=new ListTag();for(var e:absent.entrySet()){CompoundTag q=new CompoundTag();q.putUUID("id",e.getKey());q.putInt("ticks",Math.max(0,e.getValue()));misses.add(q);}tag.put("NotaryAbsent",misses);
        ListTag sealList=new ListTag();for(Vec3 v:stamps){CompoundTag q=new CompoundTag();q.putDouble("x",v.x);q.putDouble("z",v.z);sealList.add(q);}tag.put("NotaryStamps",sealList);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        boolean v2=tag.getBoolean("NotaryCombatStateV2");
        tuning=BossConfig.notary();
        state=Math.max(MANIFEST,Math.min(COLLAPSE,tag.getInt("NotaryState")));
        int rawClock=tag.getInt("NotaryClock");clock=rawClock>=0&&rawClock<=200?rawClock:0;
        phase=Math.max(1,Math.min(3,tag.getInt("NotaryPhase")));
        cycle=Math.max(0,Math.min(1_000_000,tag.getInt("NotaryCycle")));lastAttackState=tag.contains("NotaryLastAttack")?Math.max(-1,Math.min(JUDGMENT,tag.getInt("NotaryLastAttack"))):-1;recoveryTicks=0;
        rule=Math.max(0,Math.min(2,tag.getInt("NotaryRule")));
        secondaryRule=Math.max(0,Math.min(2,tag.getInt("NotarySecondaryRule")));
        variant=Math.max(0,Math.min(3,tag.getInt("NotaryVariant")));
        emptyTicks=Math.max(0,Math.min(220,tag.getInt("NotaryEmptyTicks")));
        resonanceWave=Math.max(0,Math.min(4,tag.getInt("NotaryResonanceWave")));
        verdictStartCount=Math.max(0,Math.min(4,tag.getInt("NotaryVerdictStart")));verdictStrikeTick=Math.max(0,Math.min(110,tag.getInt("NotaryVerdictStrike")));
        totalTicks=Math.max(0,Math.min(tuning.timeoutSeconds()*20,tag.getInt("NotaryTotalTicks")));
        attackHit=tag.getBoolean("NotaryAttackHit");lineCross=tag.getBoolean("NotaryLineCross");lineX=tag.getBoolean("NotaryLineX");
        lineOffset=tag.getDouble("NotaryLineOffset");secondOffset=tag.getDouble("NotarySecondOffset");
        if(!Double.isFinite(lineOffset))lineOffset=0;if(!Double.isFinite(secondOffset))secondOffset=0;
        lineOffset=Math.max(-16,Math.min(16,lineOffset));secondOffset=Math.max(-16,Math.min(16,secondOffset));
        center=tag.contains("NotaryCenter")?BlockPos.of(tag.getLong("NotaryCenter")):null;
        practice=tag.getBoolean("NotaryPractice");silasFinalPhaseTriggered=tag.getBoolean("SilasFinalPhase")||phase>=3;
        double vitalityMax=tag.getDouble("NotaryVitalityMax"),vitalityCurrent=tag.getDouble("NotaryVitalityCurrent");
        if(!Double.isFinite(vitalityMax)||vitalityMax<1)vitalityMax=Math.max(1,tuning.health()+tuning.healthPerAlly()*Math.max(0,tag.getList("NotaryParticipants",Tag.TAG_COMPOUND).size()-1));
        vitality=new MasterVitality(vitalityMax);vitality.restore(Double.isFinite(vitalityCurrent)?vitalityCurrent:vitalityMax);vitality.bindMaximum(this);
        participants.clear();absent.clear();ruleStart.clear();stamps.clear();verdictQuiet.clear();verdictPrevCrouch.clear();verdictBraced.clear();
        ListTag ids=tag.getList("NotaryParticipants",Tag.TAG_COMPOUND);for(int i=0;i<ids.size();i++){CompoundTag q=ids.getCompound(i);if(q.hasUUID("id"))participants.add(q.getUUID("id"));}
        if(v2){
            ListTag starts=tag.getList("NotaryRuleStarts",Tag.TAG_COMPOUND);for(int i=0;i<starts.size();i++){CompoundTag q=starts.getCompound(i);if(!q.hasUUID("id"))continue;double x=q.getDouble("x"),y=q.getDouble("y"),z=q.getDouble("z");if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z))ruleStart.put(q.getUUID("id"),new Vec3(x,y,z));}
            ListTag misses=tag.getList("NotaryAbsent",Tag.TAG_COMPOUND);for(int i=0;i<misses.size();i++){CompoundTag q=misses.getCompound(i);if(q.hasUUID("id"))absent.put(q.getUUID("id"),Math.max(0,Math.min(220,q.getInt("ticks"))));}
            ListTag sealList=tag.getList("NotaryStamps",Tag.TAG_COMPOUND);for(int i=0;i<sealList.size()&&stamps.size()<16;i++){CompoundTag q=sealList.getCompound(i);double x=q.getDouble("x"),z=q.getDouble("z");if(Double.isFinite(x)&&Double.isFinite(z))stamps.add(new Vec3(Math.max(-12,Math.min(12,x)),0,Math.max(-12,Math.min(12,z))));}
        }else if(state==DECLARE||state==STAMP||state==TOLL||state==JUDGMENT){
            // Old saves did not persist the telegraph geometry/rule snapshots. Resume safely at rest
            // instead of continuing an attack whose client cues and server hit geometry no longer agree.
            state=REST;clock=0;attackHit=false;lineCross=false;secondaryRule=0;rule=0;variant=0;
        }
        if(state==JUDGMENT&&variant==3){state=REST;clock=0;variant=0;attackHit=false;verdictStartCount=verdictStrikeTick=0;}
        collapsing=state==COLLAPSE;if(collapsing)clock=Math.min(111,clock);
        if(collapsing||vitality.current()<=0){setHealth(1);setInvulnerable(true);}else{vitality.syncNativeHealth(this);setInvulnerable(state==MANIFEST);}
        syncView();
    }
    @Override public MasterVitality wardboundVitality(){return vitality;}
    @Override public boolean wardboundVitalityActive(){return !participants.isEmpty()&&center!=null;}
    @Override public boolean wardboundVitalityLocked(){return collapsing;}
    @Override public void wardboundVitalityChanged(){bar.setProgress(vitality.fraction());}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"notary",6,s->{
            CompoundTag synced=view();
            int renderState=synced.contains("state")?synced.getInt("state"):state;
            int renderVariant=synced.contains("variant")?synced.getInt("variant"):variant;
            int renderPhase=synced.contains("phase")?Math.max(1,synced.getInt("phase")):phase;
            String clip=switch(renderState){
                case MANIFEST->"manifest_ominous";
                case DECLARE->renderVariant>=2?"tribunal_arch":renderVariant==1?"declare_article2":"declare";
                case STAMP->renderVariant==2?"verdict_cascade":renderVariant==1?"stamp_article2":"stamp";
                case TOLL->renderVariant>=2?"resonance_roar":renderVariant==1?"toll_article2":"toll";
                case JUDGMENT->renderVariant>=2?"final_appeal_roar":renderVariant==1?"judgment_article2":"judgment";
                case UNBOUND->renderPhase>=2?"unbound_recoil":"unbound";
                case COLLAPSE->"collapse";
                default->synced.getInt("recovery")>0?"recover":renderPhase>=2?"idle_article2":"idle";
            };
            RawAnimation animation=RawAnimation.begin();
            boolean hold=renderState==MANIFEST||renderState==UNBOUND||renderState==COLLAPSE;
            return s.setAndContinue(hold?animation.thenPlayAndHold("animation.mourning_notary."+clip)
                    :renderState==REST?animation.thenLoop("animation.mourning_notary."+clip)
                    :animation.thenPlay("animation.mourning_notary."+clip));
        }));
    }
}
