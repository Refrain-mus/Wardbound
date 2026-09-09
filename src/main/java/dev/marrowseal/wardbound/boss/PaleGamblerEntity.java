package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.net.MasterLinePacket;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import dev.marrowseal.wardbound.net.MasterDamageReactionPacket;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import dev.marrowseal.wardbound.net.MasterCuesPacket;
import dev.marrowseal.wardbound.net.OpenGamblerChoicePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

public class PaleGamblerEntity extends Monster implements GeoEntity, CinematicVitalityHost {
    private static final EntityDataAccessor<Integer> HAND=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OPEN_TELL=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HOUSE_READ=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ALL_IN=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FINAL_TELL=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GESTURE=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    /** -1 while fighting; otherwise the staged death timeline clock. */
    private static final EntityDataAccessor<Integer> DEATH_CLOCK=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    /** Short server-authored neutralisation window after a completed hand. */
    private static final EntityDataAccessor<Integer> RECOVERY=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.INT);
    /** False while the player is being given a few seconds to take in the authored arena. */
    private static final EntityDataAccessor<Boolean> INTRO_STARTED=SynchedEntityData.defineId(PaleGamblerEntity.class,EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.pale_gambler.idle"),
            DEAL=RawAnimation.begin().thenLoop("animation.pale_gambler.deal"),
            FAN_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.fan"),
            RINGS_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.rings"),
            MARKS_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.marks"),
            SWEEP_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.sweep"),
            CAGE_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.cage"),
            ROYAL_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.royal"),
            FLOOR_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.floor_cards"),
            CHOICE_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.forced_choice"),
            TEETH_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.house_teeth"),
            STARFALL_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.starfall"),
            WALL_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.card_wall"),
            SPIRAL_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.spiral"),
            CROSS_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.dealer_cross"),
            CHIP_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.chip_storm"),
            SPLIT_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.split_pot"),
            STACKED_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.stacked_deck"),
            BURIED_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.buried_ace"),
            MIRROR_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.mirror_debt"),
            EDGE_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.house_edge"),
            DOUBLE_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.double_down"),
            DEAD_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.dead_mans_draw"),
            FALSE_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.false_tell"),
            SUIT_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.suit_call"),
            FLUSH_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.royal_flush"),
            LAST_ANIM=RawAnimation.begin().thenLoop("animation.pale_gambler.last_deal"),
            DEATH_ANIM=RawAnimation.begin().thenPlayAndHold("animation.pale_gambler.last_deal"),
            RECOVER_ANIM=RawAnimation.begin().thenPlayAndHold("animation.pale_gambler.recover"),
            LEAN_ANIM=RawAnimation.begin().thenPlayAndHold("animation.pale_gambler.predatory_lean"),
            PHASE_ROAR_ANIM=RawAnimation.begin().thenPlayAndHold("animation.pale_gambler.phase_roar"),
            ALL_IN_ROAR_ANIM=RawAnimation.begin().thenPlayAndHold("animation.pale_gambler.all_in_roar");
    private static final DustParticleOptions GOLD=dust(.95f,.72f,.25f),VIOLET=dust(.70f,.25f,.95f),RED=dust(1,.13f,.26f),SAFE=dust(.25f,1,.82f);
    private static DustParticleOptions dust(float r,float g,float b){return new DustParticleOptions(new Vector3f(r,g,b),1.65f);}
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.wardbound.pale_gambler"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    private UUID owner;
    private BlockPos table;
    private final Set<UUID> players=new LinkedHashSet<>(),contributors=new HashSet<>();
    private final List<Vec3> marks=new ArrayList<>();
    private final List<GamblerPatterns.CardZone> floorCards=new ArrayList<>();
    private final Set<UUID> pendingChoices=new LinkedHashSet<>();
    private final Map<UUID,Integer> choiceOffers=new HashMap<>();
    private final Map<UUID,Float> pendingDamage=new HashMap<>();
    private final Map<UUID,Integer> suitAssignments=new HashMap<>();
    private int choiceToken;
    private long choiceDeadline=Long.MIN_VALUE;
    private boolean debug,rewarded,collectCues,floorCardsResolved;
    private final List<MasterCuesPacket.Cue> cues=new ArrayList<>();
    private int turn,clock,rest=80,emptyTicks,totalTicks,lastSpeechTick=-10000;
    private String lastSpeechText="";
    private final ArrayDeque<Integer> recentHands=new ArrayDeque<>();
    private int handHits,exposedTicks,specialIndex=-1;
    private int cleanStreak;
    private boolean allIn,lastDealUsed;
    private double aim;
    private MasterVitality vitality=new MasterVitality(8000);
    private double damageScale=1,speed=1,hitCap=180;
    private int timeout=36000,detail=2;
    private boolean dialogue=true;
    private int summonTicks;
    private int introWaitTicks;
    private int gestureTicks;
    private int deathClock=-1;
    private boolean introPulsePlayed;

    public PaleGamblerEntity(EntityType<? extends Monster> type,Level level){super(type,level);setNoGravity(true);setPersistenceRequired();xpReward=0;}
    // AttributeFix applies after attribute-supplier registration; encounter init binds the real 8k+ native maximum.
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MasterVitality.REGISTRATION_MAX_HEALTH).add(Attributes.ARMOR,14).add(Attributes.ARMOR_TOUGHNESS,8).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.ATTACK_DAMAGE,30).add(Attributes.FOLLOW_RANGE,40);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(HAND,0);entityData.define(PHASE,1);entityData.define(OPEN_TELL,false);entityData.define(HOUSE_READ,0);entityData.define(ALL_IN,false);entityData.define(FINAL_TELL,false);entityData.define(GESTURE,0);entityData.define(DEATH_CLOCK,-1);entityData.define(RECOVERY,0);entityData.define(INTRO_STARTED,false);}
    @Override public boolean removeWhenFarAway(double d){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public MasterVitality wardboundVitality(){return vitality;}
    @Override public boolean wardboundVitalityActive(){return !players.isEmpty()&&table!=null;}
    @Override public boolean wardboundVitalityLocked(){return deathClock>=0;}
    @Override public void wardboundVitalityChanged(){bar.setProgress(vitality.fraction());}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    public int currentHand(){return entityData.get(HAND);}
    public int currentPhase(){return entityData.get(PHASE);}
    public boolean silasFinalPhase(){return entityData.get(PHASE)>=3;}
    public boolean includes(UUID id){return id!=null&&players.contains(id);}
    public boolean openTell(){return entityData.get(OPEN_TELL);}
    public int houseReadLevel(){return entityData.get(HOUSE_READ);}
    public boolean allIn(){return entityData.get(ALL_IN);}
    public boolean finalTell(){return entityData.get(FINAL_TELL);}
    public int deathClock(){return entityData.get(DEATH_CLOCK);}
    public boolean introStarted(){return entityData.get(INTRO_STARTED);}
    public boolean deathSequence(){return deathClock()>=0;}
    public float deathProgress(){return deathSequence()?Mth.clamp(deathClock()/112f,0f,1f):0f;}
    private RawAnimation animationForHand(int hand){
        return switch(hand){
            case GamblerPatterns.FAN -> FAN_ANIM;
            case GamblerPatterns.RINGS -> RINGS_ANIM;
            case GamblerPatterns.MARKS -> MARKS_ANIM;
            case GamblerPatterns.SWEEP -> SWEEP_ANIM;
            case GamblerPatterns.CAGE -> CAGE_ANIM;
            case GamblerPatterns.ROYAL -> ROYAL_ANIM;
            case GamblerPatterns.FLOOR_CARDS -> FLOOR_ANIM;
            case GamblerPatterns.FORCED_CHOICE -> CHOICE_ANIM;
            case GamblerPatterns.HOUSE_TEETH -> TEETH_ANIM;
            case GamblerPatterns.STARFALL -> STARFALL_ANIM;
            case GamblerPatterns.CARD_WALL -> WALL_ANIM;
            case GamblerPatterns.SPIRAL -> SPIRAL_ANIM;
            case GamblerPatterns.DEALER_CROSS -> CROSS_ANIM;
            case GamblerPatterns.CHIP_STORM -> CHIP_ANIM;
            case GamblerPatterns.SPLIT_POT -> SPLIT_ANIM;
            case GamblerPatterns.STACKED_DECK -> STACKED_ANIM;
            case GamblerPatterns.BURIED_ACE -> BURIED_ANIM;
            case GamblerPatterns.MIRROR_DEBT -> MIRROR_ANIM;
            case GamblerPatterns.HOUSE_EDGE -> EDGE_ANIM;
            case GamblerPatterns.DOUBLE_DOWN -> DOUBLE_ANIM;
            case GamblerPatterns.DEAD_MANS_DRAW -> DEAD_ANIM;
            case GamblerPatterns.FALSE_TELL -> FALSE_ANIM;
            case GamblerPatterns.SUIT_CALL -> SUIT_ANIM;
            case GamblerPatterns.ROYAL_FLUSH -> FLUSH_ANIM;
            case GamblerPatterns.LAST_DEAL -> LAST_ANIM;
            case 0 -> IDLE;
            default -> DEAL;
        };
    }
    private void gesture(int id,int ticks){if(id==3&&gestureTicks>0)return;gestureTicks=Math.max(1,ticks);entityData.set(GESTURE,id);}
    private void motionBeat(int phase,float strength){
        if(!(level() instanceof ServerLevel sl))return;
        MasterAnimationBeatPacket pkt=new MasterAnimationBeatPacket(getId(),MasterAnimationBeatPacket.GAMBLER,phase,strength,getId()*911+turn*37+clock*13+phase);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    private void damageReaction(int kind,float strength,int seed){
        MasterDamageReactionPacket pkt=new MasterDamageReactionPacket(getId(),MasterDamageReactionPacket.GAMBLER,kind,strength,seed);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"dealer",6,s->{int g=entityData.get(GESTURE);RawAnimation a=entityData.get(DEATH_CLOCK)>=0?DEATH_ANIM:g==1?PHASE_ROAR_ANIM:g==2?ALL_IN_ROAR_ANIM:g==3?LEAN_ANIM:entityData.get(RECOVERY)>0&&entityData.get(HAND)==0?RECOVER_ANIM:animationForHand(entityData.get(HAND));return s.setAndContinue(a);}));}
    public void begin(ServerPlayer initiator,BlockPos center,boolean practice){
        owner=initiator.getUUID();table=center;debug=practice;
        players.clear();contributors.clear();marks.clear();floorCards.clear();suitAssignments.clear();pendingDamage.clear();pendingChoices.clear();choiceOffers.clear();
        turn=0;clock=0;rest=0;totalTicks=0;emptyTicks=0;lastSpeechTick=-1000;lastSpeechText="";
        players.add(owner);
        for(ServerPlayer p:initiator.serverLevel().players()){
            if(players.size()>=4)break;
            if(!p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(Vec3.atBottomCenterOf(center))<=196)players.add(p.getUUID());
        }
        BossConfig.Settings s=BossConfig.gambler();vitality=new MasterVitality(GamblerPatterns.health(players.size()));vitality.syncNativeHealth(this);damageScale=s.damageMultiplier();speed=s.attackSpeed();hitCap=s.hitCap();timeout=s.timeoutSeconds()*20;dialogue=s.dialogue();detail=s.particles();
        cleanStreak=0;allIn=false;lastDealUsed=false;gestureTicks=0;deathClock=-1;rewarded=false;recentHands.clear();entityData.set(DEATH_CLOCK,-1);entityData.set(RECOVERY,0);entityData.set(GESTURE,0);entityData.set(HOUSE_READ,0);entityData.set(ALL_IN,false);entityData.set(FINAL_TELL,false);entityData.set(OPEN_TELL,false);
        // Arrival grace: keep the dealer below the floor and suppress the boss presentation while
        // the player gets a clean first look at the authored arena. The intro begins after a short
        // minimum pause once the owner approaches the table, with a fallback so the encounter can
        // never soft-lock if the player simply stands at the entrance.
        summonTicks=0;introWaitTicks=0;introPulsePlayed=false;entityData.set(INTRO_STARTED,false);setInvulnerable(true);setInvisible(true);
        setPos(center.getX()+.5,center.getY(),center.getZ()+.5);
        WardHud.send(initiator,"THE LAST TABLE // The House gives you a moment. Look around, then approach the table.",WardHud.Mood.WARD);
    }

    private void startIntro(ServerLevel l){
        if(introStarted() || table==null)return;
        entityData.set(INTRO_STARTED,true);summonTicks=94;introPulsePlayed=false;setInvulnerable(true);setInvisible(false);
        setPos(table.getX()+.5,table.getY()-2.6,table.getZ()+.5);
        for(ServerPlayer p:active())bar.addPlayer(p);
        tellImportant("You mistook my games for permission to survive. Come. Place your life on the table.");
        ServerPlayer initiator=owner==null?null:l.getServer().getPlayerList().getPlayer(owner);
        if(initiator!=null)dev.marrowseal.wardbound.champion.SilasMasterEncounter.onMasterBegin(initiator,dev.marrowseal.wardbound.champion.SilasMasterEncounter.GAMBLER,this::includes);
        cinematic(l,GamblerCinematicPacket.TABLE_OPEN,table.getX()+.5,table.getY()+.12,table.getZ()+.5,10f,(int)(table.asLong()^l.getGameTime()));
        cinematic(l,GamblerCinematicPacket.BOSS_INTRO,table.getX()+.5,table.getY()+.15,table.getZ()+.5,11f,getId()*131+turn);
        l.playSound(null,table,WardSounds.GAMBLER_BOSS_INTRO.get(),SoundSource.HOSTILE,1.15f,.96f);
        WardHud.Mood mood=WardHud.Mood.DANGER;
        for(ServerPlayer p:active()){
            cinematicPlayer(p,GamblerCinematicPacket.PLAYER_AWE,1.0f,getId()*137+p.getId());
            WardHud.send(p,"THE LAST TABLE // Three hands. One sovereign. The prepared boundary is now part of the wager.",mood);
        }
    }
    private List<ServerPlayer> active(){
        if(!(level() instanceof ServerLevel l) || table==null)return List.of();
        double activeRadius=GamblerPatterns.BOUNDARY_RADIUS+2.0,activeSq=activeRadius*activeRadius;
        List<ServerPlayer> out=new ArrayList<>();for(UUID id:players){ServerPlayer p=l.getServer().getPlayerList().getPlayer(id);if(p!=null && p.level()==l && p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atBottomCenterOf(table))<=activeSq)out.add(p);}return out;
    }
    private void tell(String text){
        if(!dialogue || text==null || text.isBlank())return;
        // Combat dialogue should punctuate hands, not narrate every attack. Eleven seconds is long
        // enough for the subtitle to clear and for several patterns to pass between ordinary lines.
        if(tickCount-lastSpeechTick<220)return;
        if(text.equals(lastSpeechText) && tickCount-lastSpeechTick<420)return;
        lastSpeechTick=tickCount;lastSpeechText=text;
        if(level() instanceof ServerLevel sl) speechStinger(sl,false);
        int duration=dialogueDurationMs(text,48);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE PALE GAMBLER",text,duration));
    }
    private void tellImportant(String text){
        if(!dialogue || text==null || text.isBlank())return;
        // Important beats may interrupt the normal cadence, but duplicate/adjacent subtitles are
        // still suppressed so phase changes and choice results do not form a text wall.
        if(text.equals(lastSpeechText) && tickCount-lastSpeechTick<300)return;
        lastSpeechTick=tickCount;lastSpeechText=text;
        if(level() instanceof ServerLevel sl) speechStinger(sl,true);
        int beatTicks=summonTicks>0?summonTicks:Math.max(72,gestureTicks);
        int duration=dialogueDurationMs(text,beatTicks);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE PALE GAMBLER",text,duration));
    }
    private int dialogueDurationMs(String text,int beatTicks){
        int words=text==null?0:text.trim().split("\\s+").length;
        int reading=1500+words*215;
        int authored=Math.max(0,beatTicks)*50;
        return Math.max(2200,Math.min(7600,Math.max(reading,authored)));
    }
    private void speechStinger(ServerLevel l,boolean important){
        SoundEvent s=switch(random.nextInt(3)){
            case 0 -> WardSounds.GAMBLER_SPEECH_1.get();
            case 1 -> WardSounds.GAMBLER_SPEECH_2.get();
            default -> WardSounds.GAMBLER_SPEECH_3.get();
        };
        l.playSound(null,blockPosition(),s,SoundSource.HOSTILE,important?.58f:.40f,.92f+random.nextFloat()*.10f);
    }
    private int interval(int base){return GamblerPatterns.ticks(base,speed);}
    private int windup(int hand,int phase){
        int out;
        if(hand==GamblerPatterns.ROYAL)out=Math.max(60,interval(76));
        else if(hand==GamblerPatterns.FLOOR_CARDS)out=Math.max(70,interval(84));
        else if(hand==GamblerPatterns.HOUSE_TEETH)out=Math.max(44,interval(56));
        else if(hand==GamblerPatterns.STARFALL)out=Math.max(50,interval(62));
        else if(hand==GamblerPatterns.CARD_WALL)out=Math.max(42,interval(52));
        else if(hand==GamblerPatterns.SPIRAL)out=Math.max(38,interval(48));
        else if(hand==GamblerPatterns.DEALER_CROSS)out=Math.max(40,interval(50));
        else if(hand==GamblerPatterns.CHIP_STORM)out=Math.max(48,interval(58));
        else if(hand==GamblerPatterns.SPLIT_POT)out=Math.max(46,interval(56));
        else if(hand==GamblerPatterns.STACKED_DECK)out=Math.max(50,interval(62));
        else if(hand==GamblerPatterns.BURIED_ACE)out=Math.max(54,interval(66));
        else if(hand==GamblerPatterns.MIRROR_DEBT)out=Math.max(48,interval(60));
        else if(hand==GamblerPatterns.HOUSE_EDGE)out=Math.max(50,interval(62));
        else if(hand==GamblerPatterns.DOUBLE_DOWN)out=Math.max(56,interval(68));
        else if(hand==GamblerPatterns.DEAD_MANS_DRAW)out=Math.max(54,interval(66));
        else if(hand==GamblerPatterns.FALSE_TELL)out=Math.max(62,interval(74));
        else if(hand==GamblerPatterns.SUIT_CALL)out=Math.max(64,interval(78));
        else if(hand==GamblerPatterns.ROYAL_FLUSH)out=Math.max(58,interval(70));
        else if(hand==GamblerPatterns.LAST_DEAL)out=Math.max(76,interval(92));
        else out=Math.max(22,interval(GamblerPatterns.windup(phase)));
        float f=vitality==null?1f:vitality.fraction();
        if(f<.52f)out-=3;
        if(f<.34f)out-=4;
        if(allIn)out-=8;
        int floor=hand==GamblerPatterns.ROYAL?48:hand==GamblerPatterns.FLOOR_CARDS?56:hand==GamblerPatterns.LAST_DEAL?62:20;
        return Math.max(floor,out);
    }
    private int forcedChoiceDuration(){return Math.max(120,interval(180));}
    @Override public void aiStep(){
        super.aiStep();setDeltaMovement(Vec3.ZERO);
        if(!(level() instanceof ServerLevel l))return;
        if(deathClock>=0){tickDeathSequence(l);return;}
        if(!isAlive())return;
        if(owner==null || table==null){discard();return;}
        LockData d=LockData.get(l.getServer());if(!GamblerProgression.owns(d,owner,getUUID())){discard();return;}
        if(!BossConfig.gambler().enabled()){discard();return;}
        if(tickCount%20==0)GamblerProgression.claim(d,owner,getUUID(),GamblerProgression.now(l));
        ServerPlayer ownerPlayer=l.getServer().getPlayerList().getPlayer(owner);
        if(ownerPlayer==null || !ownerPlayer.isAlive()){
            fadeOutAndLeave(l,"The chair stands empty. The House folds the table.");
            return;
        }
        if(!introStarted()){
            setInvisible(true);setPos(table.getX()+.5,table.getY(),table.getZ()+.5);
            enforceArenaBoundary(l);
            List<ServerPlayer> waiting=active();if(waiting.isEmpty())emptyTicks++;else emptyTicks=0;
            if(emptyTicks>=200){discard();return;}
            introWaitTicks++;
            double dx=ownerPlayer.getX()-(table.getX()+.5),dz=ownerPlayer.getZ()-(table.getZ()+.5);
            double dy=Math.abs(ownerPlayer.getY()-table.getY());
            boolean approached=dx*dx+dz*dz<=11.0*11.0 && dy<=4.0;
            // 3.5 s minimum viewing time, then start on approach; 11 s is the non-blocking fallback.
            if(introWaitTicks>=70 && (approached || introWaitTicks>=220))startIntro(l);
            return;
        }
        double summonLift=summonTicks>0?(-2.6+2.6*(1.0-(summonTicks/94.0))):0;
        setPos(table.getX()+.5,table.getY()+summonLift,table.getZ()+.5);
        enforceArenaBoundary(l);
        List<ServerPlayer> seated=active();if(seated.isEmpty())emptyTicks++;else emptyTicks=0;
        if(summonTicks<=0 && !seated.isEmpty())trackNearest(seated);
        if(emptyTicks>=200){tellImportant("Keep the invitation. Fear will bring you back.");discard();return;}
        if(summonTicks>0){tickSummon(l);return;}
        if(++totalTicks>timeout){tellImportant("Keep the invitation. Fear will bring you back.");discard();return;}
        if(gestureTicks>0 && --gestureTicks<=0){gestureTicks=0;entityData.set(GESTURE,0);}
        // Persistent arena ambience/outer-ring presentation is client-side Lodestone now.
        // Keeping the server out of this layer prevents old vanilla dust from fighting the authored VFX.
        bar.setProgress(vitality.fraction());int phase=GamblerPatterns.phase(vitality.fraction());
        if(!allIn && vitality.fraction()<=.08f)enterAllIn(l);
        if(tickCount%105==0 && random.nextFloat()<.72f)voiceSound(l,phase);
        if(tickCount%220==0 && random.nextFloat()<.42f)ambientDetonation(l,phase);
        if(phase!=entityData.get(PHASE)){
            if(entityData.get(HAND)==GamblerPatterns.FORCED_CHOICE)settleUnresolvedChoices(l,true);
            entityData.set(PHASE,phase);entityData.set(HAND,0);entityData.set(OPEN_TELL,false);entityData.set(FINAL_TELL,false);exposedTicks=0;cleanStreak=0;entityData.set(HOUSE_READ,0);rest=50;clock=0;marks.clear();floorCards.clear();suitAssignments.clear();entityData.set(RECOVERY,0);
            bar.setColor(phase==3?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.PURPLE);
            tellImportant(phase==2?"You have drawn blood. Now I shall deal with both hands.":"No more wagers. No more mercy. Only the last hand.");
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_PHASE_BREAK.get(),SoundSource.HOSTILE,1.05f,phase==3?.82f:1.0f);
            gesture(1,phase==3?58:46);
            cinematic(l,GamblerCinematicPacket.PHASE_BREAK,getX(),table.getY()+.15,getZ(),phase==3?8f:6f,phase*1009+turn);
            if(phase==3)dev.marrowseal.wardbound.champion.SilasMasterEncounter.expelAtFinalPhase(l,position(),dev.marrowseal.wardbound.champion.SilasMasterEncounter.GAMBLER,this::includes);
        }
        if(exposedTicks>0){if(--exposedTicks<=0){exposedTicks=0;entityData.set(OPEN_TELL,false);entityData.set(FINAL_TELL,false);}}
        if(rest>0){rest--;int r=entityData.get(RECOVERY);if(r>0)entityData.set(RECOVERY,r-1);return;}
        if(seated.isEmpty())return;
        if(entityData.get(HAND)==0)startHand(l,seated,phase);
        int hand=entityData.get(HAND);
        if(hand==GamblerPatterns.FORCED_CHOICE){tickForcedChoice(l,phase);return;}
        int wind=windup(hand,phase),fanGap=interval(22),markGap=Math.max(22,interval(28)),ringGap=interval(16),sweepGap=interval(13),gridGap=Math.max(24,interval(30)),
                teethGap=Math.max(24,interval(30)),starGap=Math.max(30,interval(38)),wallGap=Math.max(20,interval(25)),spiralGap=Math.max(16,interval(20)),
                crossGap=Math.max(20,interval(25)),chipGap=Math.max(22,interval(28)),splitGap=Math.max(26,interval(34)),mirrorGap=Math.max(28,interval(36)),stackGap=Math.max(30,interval(38)),
                edgeGap=Math.max(26,interval(34)),doubleGap=Math.max(34,interval(42)),deadGap=Math.max(28,interval(36)),flushGap=Math.max(30,interval(38)),lastGap=Math.max(30,interval(38));
        if(clock%3==0)telegraph(l,hand,phase,wind,fanGap,markGap,ringGap,sweepGap,gridGap,teethGap,starGap,wallGap,spiralGap,crossGap,chipGap,splitGap,mirrorGap,stackGap,edgeGap,doubleGap,deadGap,flushGap,lastGap);
        playHandTimelineSounds(l,hand,phase,wind);
        pendingDamage.clear();
        if(clock==wind && hand!=GamblerPatterns.FLOOR_CARDS){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_SLAM.get(),SoundSource.HOSTILE,.52f,.88f+phase*.04f);
            for(ServerPlayer p:seated)if(horizontal(p)<3.1)hit(p,32+phase*6,phase);
            cinematic(l,GamblerCinematicPacket.AMBIENT_DETONATION,getX(),table.getY()+.12,getZ(),3.4f,turn*317+hand);
        }
        int end=wind;
        switch(hand){
            case GamblerPatterns.FAN -> {int waves=phase;for(int w=0;w<waves;w++)if(clock==wind+w*fanGap)strikeFan(l,seated,phase,w);end+= (waves-1)*fanGap;}
            case GamblerPatterns.RINGS -> {for(int w=0;w<5;w++)if(clock==wind+w*ringGap)strikeRing(l,seated,GamblerPatterns.ring(w,phase),phase);end+=4*ringGap;}
            case GamblerPatterns.MARKS -> {for(int w=0;w<phase+1;w++){if(clock==wind+w*markGap)strikeMarks(l,seated,phase);if(w<phase && clock==wind+w*markGap+1){marks.clear();for(ServerPlayer p:seated)marks.add(p.position());}}end+=phase*markGap;}
            case GamblerPatterns.SWEEP -> {for(int w=0;w<8;w++)if(clock==wind+w*sweepGap){double a=aim+w*Math.PI/8;ray(l,a,RED,true);ray(l,a+Math.PI,RED,true);for(ServerPlayer p:seated)if(GamblerPatterns.onRay(p.getX()-getX(),p.getZ()-getZ(),a) || GamblerPatterns.onRay(p.getX()-getX(),p.getZ()-getZ(),a+Math.PI))hit(p,40+phase*6,phase);impact(l);cinematic(l,GamblerCinematicPacket.SWEEP_STRIKE,getX(),table.getY()+.12,getZ(),16f,Float.floatToIntBits((float)a));}end+=7*sweepGap;}
            case GamblerPatterns.CAGE -> {for(int w=0;w<phase+1;w++)if(clock==wind+w*gridGap){grid(l,w,RED,true);for(ServerPlayer p:seated)if(GamblerPatterns.onGrid(p.getX()-getX(),p.getZ()-getZ(),w))hit(p,42+phase*6,phase);impact(l);cinematic(l,GamblerCinematicPacket.CAGE_STRIKE,getX(),table.getY()+.12,getZ(),15f,turn*101+w);}end+=phase*gridGap;}
            case GamblerPatterns.ROYAL -> {int gap=Math.max(45,interval(56));int waves=phase==3?2:1;for(int w=0;w<waves;w++)if(clock==wind+w*gap){for(ServerPlayer p:seated)if(!GamblerPatterns.inSanctuary(p.getX()-getX(),p.getZ()-getZ(),w))hit(p,72+phase*8,phase);circle(l,15,RED);impact(l);cinematic(l,GamblerCinematicPacket.ROYAL,getX(),table.getY()+.12,getZ(),15f,turn*31+w);}end+=(waves-1)*gap;}
            case GamblerPatterns.HOUSE_TEETH -> {int waves=phase==1?1:2;for(int w=0;w<waves;w++){int at=wind+w*teethGap;if(clock==at-6)spawnTeeth(l,phase,w);if(clock==at)resolveTeeth(seated,phase,w);}end+=(waves-1)*teethGap+8;}
            case GamblerPatterns.STARFALL -> {int waves=phase==1?1:2;for(int w=0;w<waves;w++)if(clock==wind+w*starGap){strikeStarfall(l,seated,phase);if(w+1<waves)buildStarfallMarks(seated,phase,w+1);}end+=(waves-1)*starGap;}
            case GamblerPatterns.CARD_WALL -> {int waves=GamblerPatterns.wallWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*wallGap)strikeWall(l,seated,phase,w);end+=(waves-1)*wallGap;}
            case GamblerPatterns.SPIRAL -> {int waves=GamblerPatterns.spiralWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*spiralGap)strikeSpiral(l,seated,phase,w);end+=(waves-1)*spiralGap;}
            case GamblerPatterns.DEALER_CROSS -> {int waves=GamblerPatterns.crossWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*crossGap)strikeDealerCross(l,seated,phase,w);end+=(waves-1)*crossGap;}
            case GamblerPatterns.CHIP_STORM -> {int waves=GamblerPatterns.chipCount(phase);for(int w=0;w<waves;w++)if(clock==wind+w*chipGap)strikeChip(l,seated,phase,w);end+=(waves-1)*chipGap;}
            case GamblerPatterns.SPLIT_POT -> {int waves=GamblerPatterns.splitPotWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*splitGap)strikeSplitPot(l,seated,phase,w);end+=(waves-1)*splitGap;}
            case GamblerPatterns.STACKED_DECK -> {int waves=GamblerPatterns.stackedWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*stackGap)strikeStackedDeck(l,seated,phase,w);end+=(waves-1)*stackGap;}
            case GamblerPatterns.BURIED_ACE -> {if(clock==wind)strikeBuriedAce(l,seated,phase);}
            case GamblerPatterns.MIRROR_DEBT -> {int waves=GamblerPatterns.mirrorWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*mirrorGap)strikeMirrorDebt(l,seated,phase,w);end+=(waves-1)*mirrorGap;}
            case GamblerPatterns.HOUSE_EDGE -> {int waves=GamblerPatterns.houseEdgeWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*edgeGap)strikeHouseEdge(l,seated,phase,w);end+=(waves-1)*edgeGap;}
            case GamblerPatterns.DOUBLE_DOWN -> {int waves=GamblerPatterns.doubleDownWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*doubleGap)strikeDoubleDown(l,seated,phase,w);end+=(waves-1)*doubleGap;}
            case GamblerPatterns.DEAD_MANS_DRAW -> {int waves=GamblerPatterns.deadMansWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*deadGap)strikeDeadMansDraw(l,seated,phase,w);end+=(waves-1)*deadGap;}
            case GamblerPatterns.FALSE_TELL -> {if(clock==wind-12)cinematic(l,GamblerCinematicPacket.FALSE_TELL_REVEAL,getX(),table.getY()+.12,getZ(),15f,turn*457+phase);if(clock==wind)strikeFalseTell(l,seated,phase);}
            case GamblerPatterns.SUIT_CALL -> {if(clock==wind)strikeSuitCall(l,seated,phase);}
            case GamblerPatterns.ROYAL_FLUSH -> {int waves=GamblerPatterns.royalFlushWaves(phase);for(int w=0;w<waves;w++)if(clock==wind+w*flushGap)strikeRoyalFlush(l,seated,phase,w);end+=(waves-1)*flushGap;}
            case GamblerPatterns.LAST_DEAL -> {int stages=GamblerPatterns.lastDealStages();for(int st=0;st<stages;st++)if(clock==wind+st*lastGap)strikeLastDealStage(l,seated,phase,st);end+=(stages-1)*lastGap;}
            case GamblerPatterns.FLOOR_CARDS -> {if(clock>=wind && !floorCardsResolved)resolveFloorCards(l,seated,phase);}
        }
        for(ServerPlayer p:seated)if(pendingDamage.containsKey(p.getUUID()))applyHit(p,pendingDamage.get(p.getUUID()),phase);
        if(++clock>end+8)finishHand(l,phase,hand);
    }
    private void startHand(ServerLevel l,List<ServerPlayer> seated,int phase){
        int hand=chooseHand(phase,turn);rememberHand(hand);turn++;entityData.set(RECOVERY,0);entityData.set(HAND,hand);
        int firstRelease=windup(hand,phase);
        // The attack clip itself is authored with its first release pose at 0.55 s (11 ticks).
        // Hold the predatory anticipation until exactly 11 ticks before the gameplay strike.
        gesture(3,Math.max(1,firstRelease-11));
        motionBeat(MasterAnimationBeatPacket.ANTICIPATE,.62f+(phase-1)*.10f+(allIn?.16f:0));
        entityData.set(OPEN_TELL,false);entityData.set(FINAL_TELL,false);exposedTicks=0;clock=0;handHits=0;specialIndex=-1;suitAssignments.clear();ServerPlayer p=seated.get(Math.floorMod(turn,seated.size()));aim=Math.atan2(p.getZ()-getZ(),p.getX()-getX());setYRot((float)(Math.toDegrees(aim)-90));setYBodyRot(getYRot());
        marks.clear();for(ServerPlayer q:seated)marks.add(q.position());
        if(hand==GamblerPatterns.STARFALL)buildStarfallMarks(seated,phase,0);
        if(hand==GamblerPatterns.CHIP_STORM){marks.clear();for(double[] m:GamblerPatterns.chipMarks(turn,phase))marks.add(new Vec3(getX()+m[0],table.getY(),getZ()+m[1]));}
        if(hand==GamblerPatterns.BURIED_ACE){marks.clear();for(double[] m:GamblerPatterns.buriedAceMarks(turn))marks.add(new Vec3(getX()+m[0],table.getY(),getZ()+m[1]));specialIndex=Math.floorMod(turn+phase,Math.max(1,marks.size()));for(int i=0;i<marks.size();i++){Vec3 m=marks.get(i);cinematic(l,GamblerCinematicPacket.BURIED_ACE,m.x,table.getY()+.12,m.z,5.5f,((turn*379+i*17)<<1)|((i==specialIndex)?1:0));}}
        if(hand==GamblerPatterns.STACKED_DECK){for(GamblerPatterns.CardZone zone:GamblerPatterns.dangerCards(turn,phase)){int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);cinematic(l,GamblerCinematicPacket.DANGER_CARD_PREVIEW,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);}}
        if(hand==GamblerPatterns.MIRROR_DEBT){for(Vec3 m:marks){cinematic(l,GamblerCinematicPacket.MIRROR_DEBT,m.x,table.getY()+.12,m.z,5.0f,(turn*383)<<1);Vec3 mirror=new Vec3(getX()*2-m.x,m.y,getZ()*2-m.z);cinematic(l,GamblerCinematicPacket.MIRROR_DEBT,mirror.x,table.getY()+.12,mirror.z,5.0f,((turn*389)<<1)|1);}}
        floorCards.clear();floorCardsResolved=false;
        if(hand==GamblerPatterns.DEAD_MANS_DRAW){
            floorCards.addAll(GamblerPatterns.deadMansCards(turn));
            cinematic(l,GamblerCinematicPacket.DEAD_MANS_DRAW,getX(),table.getY()+.11,getZ(),12f,(turn<<4)|phase);
        }
        if(hand==GamblerPatterns.FALSE_TELL)cinematic(l,GamblerCinematicPacket.FALSE_TELL,getX(),table.getY()+.11,getZ(),15f,(turn<<4)|phase);
        if(hand==GamblerPatterns.SUIT_CALL)beginSuitCall(l,seated);
        if(hand==GamblerPatterns.ROYAL_FLUSH){
            floorCards.addAll(GamblerPatterns.royalFlushCards(turn));
            cinematic(l,GamblerCinematicPacket.ROYAL_FLUSH,getX(),table.getY()+.11,getZ(),14f,(turn<<4)|phase);
        }
        if(hand==GamblerPatterns.LAST_DEAL)cinematic(l,GamblerCinematicPacket.LAST_DEAL,getX(),table.getY()+.11,getZ(),16f,(turn<<4)|phase);
        if(hand==GamblerPatterns.FLOOR_CARDS){
            floorCards.addAll(GamblerPatterns.floorCards(turn,phase));
            cinematic(l,GamblerCinematicPacket.FLOOR_FIELD_PREVIEW,getX(),table.getY()+.10,getZ(),16f,turn*353+phase);
            for(GamblerPatterns.CardZone zone:floorCards){
                int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);
                cinematic(l,GamblerCinematicPacket.FLOOR_CARD_PREVIEW,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);
            }
        }
        if(hand==GamblerPatterns.FORCED_CHOICE)beginForcedChoice(l,seated,phase);
        cinematic(l,GamblerCinematicPacket.HAND_CHARGE,getX(),table.getY()+.15,getZ(),4.5f+phase,hand | (phase<<8) | (turn<<12));
        if(hand==GamblerPatterns.MARKS || hand==GamblerPatterns.STARFALL){
            for(ServerPlayer q:seated)cinematicPlayer(q,GamblerCinematicPacket.PLAYER_MARKED,.9f,hand*997+turn*17+q.getId());
        }else if(hand==GamblerPatterns.ROYAL){
            for(ServerPlayer q:seated)cinematicPlayer(q,GamblerCinematicPacket.PLAYER_BRACE,1.2f,turn*71+q.getId());
        }
        if(hand==GamblerPatterns.ROYAL || hand==GamblerPatterns.FORCED_CHOICE || hand==GamblerPatterns.STACKED_DECK || hand==GamblerPatterns.BURIED_ACE || hand==GamblerPatterns.MIRROR_DEBT || hand==GamblerPatterns.HOUSE_EDGE || hand==GamblerPatterns.DOUBLE_DOWN || hand==GamblerPatterns.DEAD_MANS_DRAW || hand==GamblerPatterns.FALSE_TELL || hand==GamblerPatterns.SUIT_CALL || hand==GamblerPatterns.ROYAL_FLUSH || hand==GamblerPatterns.LAST_DEAL || Math.floorMod(turn,4)==0)tell(handLine(hand,phase));
        playHandSound(l,hand,phase);
    }
    private int chooseHand(int phase,int turnIndex){
        float f=vitality==null?1f:vitality.fraction();
        if(phase==3 && !lastDealUsed && f<=.20f){lastDealUsed=true;return GamblerPatterns.LAST_DEAL;}

        // Every phase has a deliberately broad pool. Phase I now exposes enough of the vocabulary
        // that a healthy boss cannot feel like a three-move tutorial, while the harsher rule-heavy
        // hands remain concentrated later.
        int[] pool=phase==1
                ? new int[]{GamblerPatterns.FAN,GamblerPatterns.MARKS,GamblerPatterns.RINGS,GamblerPatterns.SWEEP,
                GamblerPatterns.FLOOR_CARDS,GamblerPatterns.HOUSE_TEETH,GamblerPatterns.DEALER_CROSS,GamblerPatterns.BURIED_ACE,
                GamblerPatterns.STARFALL,GamblerPatterns.CARD_WALL,GamblerPatterns.SPIRAL,GamblerPatterns.CHIP_STORM,GamblerPatterns.SPLIT_POT}
                : phase==2
                ? new int[]{GamblerPatterns.STARFALL,GamblerPatterns.CARD_WALL,GamblerPatterns.SPIRAL,GamblerPatterns.HOUSE_TEETH,
                GamblerPatterns.FLOOR_CARDS,GamblerPatterns.MARKS,GamblerPatterns.FORCED_CHOICE,GamblerPatterns.DEALER_CROSS,
                GamblerPatterns.CHIP_STORM,GamblerPatterns.SPLIT_POT,GamblerPatterns.STACKED_DECK,GamblerPatterns.BURIED_ACE,
                GamblerPatterns.MIRROR_DEBT,GamblerPatterns.HOUSE_EDGE,GamblerPatterns.DOUBLE_DOWN,GamblerPatterns.DEAD_MANS_DRAW,
                GamblerPatterns.FALSE_TELL,GamblerPatterns.SUIT_CALL,GamblerPatterns.ROYAL_FLUSH,GamblerPatterns.FAN,GamblerPatterns.RINGS,GamblerPatterns.CAGE}
                : allIn
                ? new int[]{GamblerPatterns.ROYAL,GamblerPatterns.FALSE_TELL,GamblerPatterns.ROYAL_FLUSH,GamblerPatterns.DEAD_MANS_DRAW,
                GamblerPatterns.HOUSE_EDGE,GamblerPatterns.DOUBLE_DOWN,GamblerPatterns.MIRROR_DEBT,GamblerPatterns.SUIT_CALL,
                GamblerPatterns.STACKED_DECK,GamblerPatterns.DEALER_CROSS,GamblerPatterns.CHIP_STORM,GamblerPatterns.SPIRAL}
                : new int[]{GamblerPatterns.ROYAL,GamblerPatterns.STARFALL,GamblerPatterns.CARD_WALL,GamblerPatterns.SPIRAL,
                GamblerPatterns.FORCED_CHOICE,GamblerPatterns.FLOOR_CARDS,GamblerPatterns.HOUSE_TEETH,GamblerPatterns.MARKS,
                GamblerPatterns.DEALER_CROSS,GamblerPatterns.CHIP_STORM,GamblerPatterns.SPLIT_POT,GamblerPatterns.STACKED_DECK,
                GamblerPatterns.BURIED_ACE,GamblerPatterns.MIRROR_DEBT,GamblerPatterns.HOUSE_EDGE,GamblerPatterns.DOUBLE_DOWN,
                GamblerPatterns.DEAD_MANS_DRAW,GamblerPatterns.FALSE_TELL,GamblerPatterns.SUIT_CALL,GamblerPatterns.ROYAL_FLUSH,
                GamblerPatterns.SWEEP,GamblerPatterns.CAGE,GamblerPatterns.RINGS};

        int scripted=GamblerPatterns.attack(turnIndex);
        boolean scriptedEligible=containsHand(pool,scripted) && !recentHands.contains(scripted);
        // Preserve a little authored ordering, but never at the cost of obvious repetition.
        if(scriptedEligible && random.nextFloat()<.46f)return scripted;

        int[] candidates=new int[pool.length];
        int count=0;
        for(int hand:pool)if(!recentHands.contains(hand))candidates[count++]=hand;
        if(count==0){for(int hand:pool)candidates[count++]=hand;}
        return candidates[Math.floorMod(turnIndex*7+random.nextInt(Math.max(1,count)),count)];
    }

    private static boolean containsHand(int[] pool,int hand){
        for(int candidate:pool)if(candidate==hand)return true;
        return false;
    }

    private void rememberHand(int hand){
        if(hand<=0)return;
        recentHands.remove(hand);
        recentHands.addLast(hand);
        while(recentHands.size()>4)recentHands.removeFirst();
    }

    private int recoveryDelay(int phase,int hand){
        int out=Math.max(5,interval(phase==1?18:phase==2?12:8)/2);
        float f=vitality==null?1f:vitality.fraction();
        if(f<.60f)out-=2;
        if(f<.34f)out-=2;
        if(allIn)out-=4;
        if(hand==GamblerPatterns.ROYAL || hand==GamblerPatterns.STARFALL || hand==GamblerPatterns.CARD_WALL || hand==GamblerPatterns.SPIRAL || hand==GamblerPatterns.DEALER_CROSS || hand==GamblerPatterns.CHIP_STORM || hand==GamblerPatterns.SPLIT_POT || hand==GamblerPatterns.STACKED_DECK || hand==GamblerPatterns.BURIED_ACE || hand==GamblerPatterns.MIRROR_DEBT || hand==GamblerPatterns.HOUSE_EDGE || hand==GamblerPatterns.DOUBLE_DOWN || hand==GamblerPatterns.DEAD_MANS_DRAW || hand==GamblerPatterns.FALSE_TELL || hand==GamblerPatterns.SUIT_CALL || hand==GamblerPatterns.ROYAL_FLUSH || hand==GamblerPatterns.LAST_DEAL)out-=1;
        return Math.max(allIn?4:5,out);
    }

    private void finishHand(ServerLevel l,int phase,int hand){
        if(hand!=0)cinematic(l,GamblerCinematicPacket.HAND_RELEASE,getX(),table.getY()+.15,getZ(),3.8f,turn*449+hand*17+phase);
        motionBeat(MasterAnimationBeatPacket.CLEAR,0);
        entityData.set(HAND,0);
        boolean eligible=hand!=GamblerPatterns.FORCED_CHOICE && hand!=0;
        boolean clean=eligible && handHits==0 && !active().isEmpty();
        int recovery=recoveryDelay(phase,hand);
        entityData.set(RECOVERY,Math.min(9,recovery));
        if(clean){
            if(hand==GamblerPatterns.LAST_DEAL){
                cleanStreak=3;
                entityData.set(HOUSE_READ,3);
                entityData.set(FINAL_TELL,true);
                exposedTicks=110;
                entityData.set(OPEN_TELL,true);
                rest=Math.max(recovery,exposedTicks);
                cinematic(l,GamblerCinematicPacket.FINAL_TELL,getX(),table.getY()+.15,getZ(),8.0f,turn*463+phase);
                l.playSound(null,blockPosition(),WardSounds.GAMBLER_PHASE_BREAK.get(),SoundSource.HOSTILE,1.0f,1.18f);
                tellImportant("You read the whole hand. Then finish it. Before the House remembers how to lie.");
            }else{
                cleanStreak=Math.min(3,cleanStreak+1);
                entityData.set(HOUSE_READ,cleanStreak);
                entityData.set(FINAL_TELL,false);
                exposedTicks=(phase==1?24:phase==2?32:40)+cleanStreak*8;
                entityData.set(OPEN_TELL,true);
                rest=Math.max(recovery,exposedTicks);
                cinematic(l,GamblerCinematicPacket.HOUSE_READ,getX(),table.getY()+.15,getZ(),5.0f+cleanStreak*.7f,(turn*401+hand*13+phase)|(cleanStreak<<24));
                l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_FLIP.get(),SoundSource.HOSTILE,.76f,1.14f+cleanStreak*.05f);
                tell(cleanStreak>=3?"Three clean hands. You are reading me now. Prove it while the tell is open.":"You found the tell. Read another hand and the House will open wider.");
            }
        }else{
            cleanStreak=0;
            entityData.set(HOUSE_READ,0);
            entityData.set(FINAL_TELL,false);
            exposedTicks=0;entityData.set(OPEN_TELL,false);rest=recovery;
        }
        marks.clear();floorCards.clear();floorCardsResolved=false;specialIndex=-1;handHits=0;suitAssignments.clear();
    }

    private void strikeStackedDeck(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        List<GamblerPatterns.CardZone> zones=GamblerPatterns.dangerCards(turn+wave*3,phase);
        impact(l);
        for(GamblerPatterns.CardZone zone:zones){
            drawFloorCard(l,zone,RED,true);
            int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);
            cinematic(l,GamblerCinematicPacket.DANGER_CARD_STRIKE,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);
        }
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            for(GamblerPatterns.CardZone zone:zones)if(GamblerPatterns.insideCard(x,z,zone)){hit(p,42+phase*7,phase);break;}
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_1.get(),SoundSource.HOSTILE,.92f,.84f+wave*.06f);
        if(wave+1<GamblerPatterns.stackedWaves(phase)){
            for(GamblerPatterns.CardZone zone:GamblerPatterns.dangerCards(turn+(wave+1)*3,phase)){int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);cinematic(l,GamblerCinematicPacket.DANGER_CARD_PREVIEW,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);}
        }
    }

    private void strikeBuriedAce(ServerLevel l,List<ServerPlayer> seated,int phase){
        if(marks.isEmpty())return;
        specialIndex=Math.floorMod(specialIndex,marks.size());
        Vec3 safe=marks.get(specialIndex);
        impact(l);
        for(int i=0;i<marks.size();i++){Vec3 m=marks.get(i);boolean isSafe=i==specialIndex;cinematic(l,GamblerCinematicPacket.BURIED_ACE,m.x,table.getY()+.12,m.z,5.5f,((turn*409+i*19)<<1)|(isSafe?1:0));}
        for(ServerPlayer p:seated){
            if(!GamblerPatterns.inBuriedAce(p.getX(),p.getZ(),safe.x,safe.z)){hit(p,48+phase*8,phase);}
            else cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,.8f,turn*419+p.getId());
        }
        l.playSound(null,BlockPos.containing(safe),WardSounds.GAMBLER_CHIP_IMPACT.get(),SoundSource.HOSTILE,.90f,1.14f);
    }

    private void strikeMirrorDebt(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        for(Vec3 base:marks){
            Vec3 q=wave==0?base:new Vec3(getX()*2-base.x,base.y,getZ()*2-base.z);
            circleAt(l,q.x,q.z,2.5,RED);
            cinematic(l,GamblerCinematicPacket.MIRROR_DEBT,q.x,table.getY()+.12,q.z,5.0f,((turn*421+wave*31)<<1)|(wave&1));
        }
        for(ServerPlayer p:seated){
            for(Vec3 base:marks){Vec3 q=wave==0?base:new Vec3(getX()*2-base.x,base.y,getZ()*2-base.z);if(Math.hypot(p.getX()-q.x,p.getZ()-q.z)<=2.5){hit(p,40+phase*7,phase);break;}}
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_2.get(),SoundSource.HOSTILE,.80f,wave==0?.92f:1.08f);
    }

    private void strikeHouseEdge(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        double radius=GamblerPatterns.houseEdgeRadius(wave,phase);
        circle(l,radius,RED);
        for(int s=0;s<GamblerPatterns.houseEdgeSpokes(phase);s++)ray(l,GamblerPatterns.houseEdgeAngle(turn,s,phase,wave),RED,true);
        cinematic(l,GamblerCinematicPacket.HOUSE_EDGE,getX(),table.getY()+.12,getZ(),(float)radius,(turn*433+wave*37)|(phase<<24));
        for(ServerPlayer p:seated)if(GamblerPatterns.houseEdgeUnsafe(p.getX()-getX(),p.getZ()-getZ(),turn,phase,wave))hit(p,44+phase*7,phase);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,.88f,.88f+wave*.07f);
    }

    private void strikeDoubleDown(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        List<double[]> safe=GamblerPatterns.doubleDownSafe(turn,wave);
        for(double[] m:safe)circleAt(l,getX()+m[0],getZ()+m[1],GamblerPatterns.doubleDownRadius(),SAFE);
        cinematic(l,GamblerCinematicPacket.DOUBLE_DOWN,getX(),table.getY()+.12,getZ(),10.0f,(turn*439+wave*41)|(phase<<24));
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();boolean ok=false;
            for(double[] m:safe)if(GamblerPatterns.inDoubleDownSafe(x,z,m[0],m[1])){ok=true;break;}
            if(!ok)hit(p,46+phase*8,phase); else cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,.55f,turn*443+wave*13+p.getId());
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_1.get(),SoundSource.HOSTILE,.94f,.82f+wave*.08f);
    }


    private void beginSuitCall(ServerLevel l,List<ServerPlayer> seated){
        suitAssignments.clear();
        int base=Math.floorMod(turn,4);
        for(int i=0;i<seated.size();i++){
            ServerPlayer p=seated.get(i);
            int suit=Math.floorMod(base+i,4);
            suitAssignments.put(p.getUUID(),suit);
            cinematicPrivate(p,GamblerCinematicPacket.SUIT_CALL,getX(),table.getY()+.12,getZ(),9.6f,(turn<<4)|suit);
            Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE PALE GAMBLER","SUIT CALL // "+suitName(suit)+". Your seal is yours alone."));
        }
    }

    private static String suitName(int suit){
        return switch(Math.floorMod(suit,4)){case 0->"SPADE";case 1->"HEART";case 2->"CLUB";default->"DIAMOND";};
    }

    private void strikeDeadMansDraw(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        if(floorCards.isEmpty())floorCards.addAll(GamblerPatterns.deadMansCards(turn));
        int[] active=GamblerPatterns.deadMansActive(turn,phase,wave);
        impact(l);
        for(int idx:active){
            if(idx<0 || idx>=floorCards.size())continue;
            GamblerPatterns.CardZone zone=floorCards.get(idx);
            drawFloorCard(l,zone,RED,true);
            int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);
            cinematic(l,GamblerCinematicPacket.DANGER_CARD_STRIKE,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);
        }
        cinematic(l,GamblerCinematicPacket.DEAD_MANS_DRAW,getX(),table.getY()+.12,getZ(),12f,(turn<<8)|(phase<<4)|wave);
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();boolean struck=false;
            for(int idx:active)if(idx>=0 && idx<floorCards.size() && GamblerPatterns.insideCard(x,z,floorCards.get(idx))){struck=true;break;}
            if(struck)hit(p,46+phase*8,phase);
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_2.get(),SoundSource.HOSTILE,.92f,.78f+wave*.07f);
    }

    private void strikeFalseTell(ServerLevel l,List<ServerPlayer> seated,int phase){
        impact(l);
        for(int spoke=0;spoke<GamblerPatterns.falseTellSpokes(phase);spoke++){
            if(!GamblerPatterns.falseTellRealSpoke(turn,phase,spoke))continue;
            double a=GamblerPatterns.falseTellAngle(aim,spoke,phase,turn);
            ray(l,a,RED,true);
            shockRay(l,a,16);
        }
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            for(int spoke:GamblerPatterns.falseTellReal(turn,phase)){
                if(GamblerPatterns.onRay(x,z,GamblerPatterns.falseTellAngle(aim,spoke,phase,turn))){hit(p,54+phase*8,phase);break;}
            }
        }
        cinematic(l,GamblerCinematicPacket.FALSE_TELL,getX(),table.getY()+.12,getZ(),15f,(turn<<8)|(phase<<4)|1);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,.96f,.90f);
    }

    private void strikeSuitCall(ServerLevel l,List<ServerPlayer> seated,int phase){
        List<double[]> seals=GamblerPatterns.suitMarks(turn);
        impact(l);
        cinematic(l,GamblerCinematicPacket.SUIT_CALL_STRIKE,getX(),table.getY()+.12,getZ(),9.6f,(turn<<4)|phase);
        for(ServerPlayer p:seated){
            int suit=suitAssignments.getOrDefault(p.getUUID(),Math.floorMod(turn+p.getId(),4));
            double[] seal=seals.get(Math.floorMod(suit,seals.size()));
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            if(Math.hypot(x-seal[0],z-seal[1])>GamblerPatterns.suitRadius()){
                hit(p,55+phase*9,phase);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,50,1));
            }else cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,.75f,turn*467+p.getId());
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_CHIP_IMPACT.get(),SoundSource.HOSTILE,1.0f,1.04f);
    }

    private void strikeRoyalFlush(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        if(floorCards.isEmpty())floorCards.addAll(GamblerPatterns.royalFlushCards(turn));
        int[] active=GamblerPatterns.royalFlushActive(turn,wave);
        impact(l);
        for(int idx:active){
            if(idx<0 || idx>=floorCards.size())continue;
            GamblerPatterns.CardZone zone=floorCards.get(idx);
            drawFloorCard(l,zone,RED,true);
            int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);
            cinematic(l,GamblerCinematicPacket.DANGER_CARD_STRIKE,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);
        }
        cinematic(l,GamblerCinematicPacket.ROYAL_FLUSH,getX(),table.getY()+.12,getZ(),14f,(turn<<8)|(phase<<4)|wave);
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            for(int idx:active)if(idx>=0 && idx<floorCards.size() && GamblerPatterns.insideCard(x,z,floorCards.get(idx))){hit(p,50+phase*8,phase);break;}
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,.98f,.86f+wave*.06f);
    }

    private void strikeLastDealStage(ServerLevel l,List<ServerPlayer> seated,int phase,int stage){
        cinematic(l,GamblerCinematicPacket.LAST_DEAL,getX(),table.getY()+.12,getZ(),16f,(turn<<8)|(stage&0xFF));
        switch(stage){
            case 0 -> strikeDealerCross(l,seated,3,0);
            case 1 -> strikeSplitPot(l,seated,3,1);
            case 2 -> strikeDoubleDown(l,seated,3,0);
            case 3 -> strikeMirrorDebt(l,seated,3,1);
            case 4 -> strikeLastRoyal(l,seated);
            default -> strikeFalseTell(l,seated,3);
        }
    }

    private void strikeLastRoyal(ServerLevel l,List<ServerPlayer> seated){
        int wave=Math.floorMod(turn,2);
        impact(l);circle(l,15,RED);
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+wave*Math.PI/4;
            circleAt(l,getX()+10*Math.cos(a),getZ()+10*Math.sin(a),3.2,SAFE);
        }
        for(ServerPlayer p:seated)if(!GamblerPatterns.inSanctuary(p.getX()-getX(),p.getZ()-getZ(),wave))hit(p,88,3);
        cinematic(l,GamblerCinematicPacket.ROYAL,getX(),table.getY()+.12,getZ(),15f,turn*479+wave);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,1.05f,.80f);
    }

    private void enterAllIn(ServerLevel l){
        allIn=true;
        entityData.set(ALL_IN,true);
        gesture(2,60);
        bar.setColor(BossEvent.BossBarColor.RED);
        cinematic(l,GamblerCinematicPacket.ALL_IN,getX(),table.getY()+.12,getZ(),16f,getId()*487+turn);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_PHASE_BREAK.get(),SoundSource.HOSTILE,1.15f,.72f);
        tellImportant("ALL IN. No reserve. No courtesy. If I fall, the House falls with me.");
    }

    private void tickSummon(ServerLevel l){
        entityData.set(HAND,0);
        setInvulnerable(true);
        int age=94-summonTicks;
        if(age==0 || !introPulsePlayed){
            introPulsePlayed=true;
            l.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,1.05f,.92f);
        }
        if(age%12==0){
            double radius=1.7+age*.085;
            cinematic(l,GamblerCinematicPacket.SUMMON_PULSE,getX(),table.getY()+.12,getZ(),(float)Math.min(10.8,radius),getId()*173+age);
        }
        if(age==28 || age==62)l.playSound(null,blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.HOSTILE,.7f,.62f+age*.004f);
        if(summonTicks==24)l.playSound(null,blockPosition(),WardSounds.GAMBLER_TABLE_OPEN.get(),SoundSource.HOSTILE,.9f,.88f);
        if(--summonTicks<=0){
            summonTicks=0;setInvulnerable(false);
            cinematic(l,GamblerCinematicPacket.SUMMON_PULSE,getX(),table.getY()+.12,getZ(),11f,getId()*181+94);
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_1.get(),SoundSource.HOSTILE,.95f,.82f);
            tellImportant("The opening deal is finished. Now survive the hand.");
        }
    }

    private void fadeOutAndLeave(ServerLevel l,String line){
        settleUnresolvedChoices(l,false);
        tellImportant(line);
        cinematic(l,GamblerCinematicPacket.AMBIENT_DETONATION,getX(),getY()+.12,getZ(),5.5f,getId()*227+turn);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_DEATH_COLLAPSE.get(),SoundSource.HOSTILE,.85f,.76f);
        for(ServerPlayer p:active())cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,1.0f,getId()*211+p.getId());
        discard();
    }

    private static int upcoming(int clock,int wind,int gap){return clock<wind?0:(clock-wind)/gap+1;}
    private void telegraph(ServerLevel l,int hand,int phase,int wind,int fg,int mg,int rg,int sg,int cg,int tg,int stg,int wg,int spg,int xg,int chg,int ptg,int mdg,int sdg,int heg,int ddg,int deadg,int flushg,int lastg){
        collectCues=true;cues.clear();
        if(clock<wind && hand!=GamblerPatterns.FLOOR_CARDS)circle(l,3.1,RED);
        switch(hand){
            case 1 -> {int w=upcoming(clock,wind,fg);if(w<phase)for(int lane=0;lane<GamblerPatterns.lanes(phase);lane++)ray(l,GamblerPatterns.laneAngle(aim,lane,phase,w),w==0?VIOLET:GOLD,false);}
            case 2 -> {int w=upcoming(clock,wind,rg);if(w<5)circle(l,GamblerPatterns.ring(w,phase),VIOLET);}
            case 3 -> {if(clock<=wind+phase*mg)for(Vec3 m:marks)circleAt(l,m.x,m.z,2.3,VIOLET);}
            case 4 -> {int w=upcoming(clock,wind,sg);if(w<8){ray(l,aim+w*Math.PI/8,VIOLET,false);ray(l,aim+w*Math.PI/8+Math.PI,VIOLET,false);}}
            case 5 -> {int w=upcoming(clock,wind,cg);if(w<phase+1)grid(l,w,VIOLET,false);}
            case GamblerPatterns.ROYAL -> {int gap=Math.max(45,interval(56)),w=upcoming(clock,wind,gap);if(w<(phase==3?2:1)){for(int i=0;i<4;i++){double a=i*Math.PI/2+w*Math.PI/4;circleAt(l,getX()+10*Math.cos(a),getZ()+10*Math.sin(a),3.2,SAFE);}circle(l,15,RED);}}
            case GamblerPatterns.HOUSE_TEETH -> {int w=upcoming(clock,wind,tg);if(w<(phase==1?1:2))for(int lane=0;lane<GamblerPatterns.teethLanes(phase);lane++)ray(l,GamblerPatterns.teethAngle(aim,lane,phase,w),VIOLET,false);}
            case GamblerPatterns.STARFALL -> {int w=upcoming(clock,wind,stg),waves=phase==1?1:2;if(w<waves)for(Vec3 m:marks)circleAt(l,m.x,m.z,2.7,clock>=wind-12?RED:VIOLET);}
            case GamblerPatterns.CARD_WALL -> {int w=upcoming(clock,wind,wg);if(w<GamblerPatterns.wallWaves(phase))wall(l,w,phase,VIOLET,false);}
            case GamblerPatterns.SPIRAL -> {int w=upcoming(clock,wind,spg);if(w<GamblerPatterns.spiralWaves(phase))for(int s=0;s<GamblerPatterns.spiralSpokes(phase);s++)ray(l,GamblerPatterns.spiralAngle(aim,s,phase,w),VIOLET,false);}
            case GamblerPatterns.DEALER_CROSS -> {int w=upcoming(clock,wind,xg);if(w<GamblerPatterns.crossWaves(phase))for(int s=0;s<GamblerPatterns.crossSpokes(phase);s++)ray(l,GamblerPatterns.crossAngle(aim,s,phase,w),w==0?GOLD:VIOLET,false);}
            case GamblerPatterns.CHIP_STORM -> {int w=upcoming(clock,wind,chg);if(w<marks.size())for(int i=0;i<marks.size();i++){Vec3 m=marks.get(i);circleAt(l,m.x,m.z,GamblerPatterns.chipRadius(phase),i==w?RED:GOLD);}}
            case GamblerPatterns.SPLIT_POT -> {int w=upcoming(clock,wind,ptg);if(w<GamblerPatterns.splitPotWaves(phase)){int mode=Math.floorMod(w,3);if(mode==0){circle(l,6.3,RED);circle(l,10.2,SAFE);}else if(mode==1){circle(l,10.2,RED);circle(l,6.3,SAFE);}else{circle(l,phase==3?5.2:5.8,RED);circle(l,phase==3?11.8:11.2,RED);}}}
            case GamblerPatterns.STACKED_DECK -> {int w=upcoming(clock,wind,sdg);if(w<GamblerPatterns.stackedWaves(phase)){for(GamblerPatterns.CardZone zone:GamblerPatterns.dangerCards(turn+w*3,phase))drawFloorCard(l,zone,clock>=wind-10?RED:VIOLET,false);}}
            case GamblerPatterns.BURIED_ACE -> {if(clock<=wind){circle(l,15,clock>=wind-12?RED:VIOLET);for(int i=0;i<marks.size();i++){Vec3 m=marks.get(i);circleAt(l,m.x,m.z,GamblerPatterns.buriedAceRadius(),i==specialIndex?SAFE:(clock>=wind-10?RED:GOLD));}}}
            case GamblerPatterns.MIRROR_DEBT -> {int w=upcoming(clock,wind,mdg);if(w<GamblerPatterns.mirrorWaves(phase))for(Vec3 m:marks){Vec3 q=w==0?m:new Vec3(getX()*2-m.x,m.y,getZ()*2-m.z);circleAt(l,q.x,q.z,2.5,w==0?(clock>=wind-10?RED:VIOLET):GOLD);}}
            case GamblerPatterns.HOUSE_EDGE -> {int w=upcoming(clock,wind,heg);if(w<GamblerPatterns.houseEdgeWaves(phase)){double rr=GamblerPatterns.houseEdgeRadius(w,phase);circle(l,rr,clock>=wind-10?RED:VIOLET);for(int s=0;s<GamblerPatterns.houseEdgeSpokes(phase);s++)ray(l,GamblerPatterns.houseEdgeAngle(turn,s,phase,w),clock>=wind-10?RED:GOLD,false);}}
            case GamblerPatterns.DOUBLE_DOWN -> {int w=upcoming(clock,wind,ddg);if(w<GamblerPatterns.doubleDownWaves(phase)){circle(l,15,clock>=wind-12?RED:VIOLET);for(double[] m:GamblerPatterns.doubleDownSafe(turn,w)){circleAt(l,getX()+m[0],getZ()+m[1],GamblerPatterns.doubleDownRadius(),SAFE);}}}
            case GamblerPatterns.DEAD_MANS_DRAW -> {
                int w=upcoming(clock,wind,deadg);
                if(w<GamblerPatterns.deadMansWaves(phase)){
                    int[] hot=GamblerPatterns.deadMansActive(turn,phase,w);
                    for(int i=0;i<floorCards.size();i++){boolean active=false;for(int h:hot)if(h==i){active=true;break;}drawFloorCard(l,floorCards.get(i),active?(clock>=wind-10?RED:VIOLET):GOLD,false);}
                }
            }
            case GamblerPatterns.FALSE_TELL -> {
                boolean reveal=clock>=wind-12;
                for(int spoke=0;spoke<GamblerPatterns.falseTellSpokes(phase);spoke++){
                    boolean real=GamblerPatterns.falseTellRealSpoke(turn,phase,spoke);
                    DustParticleOptions ink=reveal?(real?RED:SAFE):((spoke&1)==0?VIOLET:GOLD);
                    ray(l,GamblerPatterns.falseTellAngle(aim,spoke,phase,turn),ink,false);
                }
            }
            case GamblerPatterns.SUIT_CALL -> {
                circle(l,15,clock>=wind-12?RED:VIOLET);
                for(double[] m:GamblerPatterns.suitMarks(turn))circleAt(l,getX()+m[0],getZ()+m[1],GamblerPatterns.suitRadius(),GOLD);
            }
            case GamblerPatterns.ROYAL_FLUSH -> {
                int w=upcoming(clock,wind,flushg);
                if(w<GamblerPatterns.royalFlushWaves(phase)){
                    int[] hot=GamblerPatterns.royalFlushActive(turn,w);
                    for(int i=0;i<floorCards.size();i++){boolean active=false;for(int h:hot)if(h==i){active=true;break;}drawFloorCard(l,floorCards.get(i),active?(clock>=wind-10?RED:VIOLET):GOLD,false);}
                }
            }
            case GamblerPatterns.LAST_DEAL -> {int st=upcoming(clock,wind,lastg);if(st<GamblerPatterns.lastDealStages())telegraphLastDealStage(l,phase,st,wind,lastg);}
            case GamblerPatterns.FLOOR_CARDS -> {
                for(GamblerPatterns.CardZone zone:floorCards)drawFloorCard(l,zone,SAFE,false);
                
                circle(l,GamblerPatterns.RADIUS,clock >= Math.max(0,wind-12)?RED:VIOLET);
            }
        }
        collectCues=false;
        MasterCuesPacket packet=new MasterCuesPacket(getId(),table.getY()+.1,List.copyOf(cues));
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),packet);
    }

    private void telegraphLastDealStage(ServerLevel l,int phase,int stage,int wind,int gap){
        boolean hot=clock>=wind+stage*gap-10;
        switch(stage){
            case 0 -> {for(int sp=0;sp<GamblerPatterns.crossSpokes(3);sp++)ray(l,GamblerPatterns.crossAngle(aim,sp,3,0),hot?RED:VIOLET,false);}
            case 1 -> {circle(l,10.2,hot?RED:VIOLET);circle(l,6.3,SAFE);}
            case 2 -> {circle(l,15,hot?RED:VIOLET);for(double[] m:GamblerPatterns.doubleDownSafe(turn,0))circleAt(l,getX()+m[0],getZ()+m[1],GamblerPatterns.doubleDownRadius(),SAFE);}
            case 3 -> {for(Vec3 m:marks){Vec3 q=new Vec3(getX()*2-m.x,m.y,getZ()*2-m.z);circleAt(l,q.x,q.z,2.5,hot?RED:GOLD);}}
            case 4 -> {int w=Math.floorMod(turn,2);circle(l,15,hot?RED:VIOLET);for(int i=0;i<4;i++){double a=i*Math.PI/2+w*Math.PI/4;circleAt(l,getX()+10*Math.cos(a),getZ()+10*Math.sin(a),3.2,SAFE);}}
            default -> {for(int sp=0;sp<GamblerPatterns.falseTellSpokes(3);sp++){boolean real=GamblerPatterns.falseTellRealSpoke(turn,3,sp);ray(l,GamblerPatterns.falseTellAngle(aim,sp,3,turn),hot?(real?RED:SAFE):VIOLET,false);}}
        }
    }

    private String handLine(int hand,int phase){
        return switch(hand){
            case GamblerPatterns.FAN -> "Every card has an edge. Find the gaps.";
            case GamblerPatterns.RINGS -> phase==3?"The walls of my kingdom close inward.":"Five ripples. One mistake is enough.";
            case GamblerPatterns.MARKS -> "Your shadow has signed for you.";
            case GamblerPatterns.SWEEP -> "The hands of the clock are blades.";
            case GamblerPatterns.CAGE -> "Between the lines, a little mercy.";
            case GamblerPatterns.FLOOR_CARDS -> "The table has decided where your name belongs.";
            case GamblerPatterns.FORCED_CHOICE -> "Three debts in the deck. I only need to show you two.";
            case GamblerPatterns.HOUSE_TEETH -> "The table has teeth beneath the felt.";
            case GamblerPatterns.STARFALL -> "The ceiling has entered the wager.";
            case GamblerPatterns.CARD_WALL -> "The house moves the walls, not the player.";
            case GamblerPatterns.SPIRAL -> "The hand turns. Do not turn with it.";
            case GamblerPatterns.DEALER_CROSS -> "A clean cut needs no second opinion.";
            case GamblerPatterns.CHIP_STORM -> "Every chip on the felt remembers who owes it.";
            case GamblerPatterns.SPLIT_POT -> "Inside. Outside. Between. The pot changes its mind.";
            case GamblerPatterns.STACKED_DECK -> "A stacked deck is not dishonest when I own the table.";
            case GamblerPatterns.BURIED_ACE -> "One seal is true. The others are what hope looks like from my side of the table.";
            case GamblerPatterns.MIRROR_DEBT -> "Every debt has a reflection. Move before it learns your shape.";
            case GamblerPatterns.HOUSE_EDGE -> "The edge is where the House keeps what the center cannot collect.";
            case GamblerPatterns.DOUBLE_DOWN -> "Two safe bets. I will move both before you grow comfortable.";
            case GamblerPatterns.DEAD_MANS_DRAW -> "Four cards. One grave at a time. Do not stand where the House points.";
            case GamblerPatterns.FALSE_TELL -> "You wanted to read me. Then decide which warning is the lie.";
            case GamblerPatterns.SUIT_CALL -> "A suit for every guest. Stand in another player's mercy and it becomes mine.";
            case GamblerPatterns.ROYAL_FLUSH -> "Five royal cards. I only need two of them to close around you.";
            case GamblerPatterns.LAST_DEAL -> "You have seen every trick. Good. Now survive them without the pauses between.";
            default -> "Four places at my table. Reach the emerald seals, or be erased.";
        };
    }
    private void playHandSound(ServerLevel l,int hand,int phase){
        SoundEvent s=switch(hand){
            // Keep deliberate air movement for the attacks that actually sweep/turn through space.
            // Everything else leans on cards, table contacts, mechanical clicks and occult tone.
            case GamblerPatterns.FAN -> WardSounds.GAMBLER_FAN_CAST.get();
            case GamblerPatterns.RINGS -> WardSounds.GAMBLER_RING_CAST.get();
            case GamblerPatterns.MARKS -> WardSounds.GAMBLER_CHOICE_BIND.get();
            case GamblerPatterns.SWEEP -> WardSounds.GAMBLER_CROSS_CAST.get();
            case GamblerPatterns.CAGE -> WardSounds.GAMBLER_POT_CAST.get();
            case GamblerPatterns.FLOOR_CARDS -> WardSounds.GAMBLER_CARD_FLIP.get();
            case GamblerPatterns.HOUSE_TEETH -> WardSounds.GAMBLER_TEETH_RISE.get();
            case GamblerPatterns.STARFALL -> WardSounds.GAMBLER_CHIP_CAST.get();
            case GamblerPatterns.CARD_WALL -> WardSounds.GAMBLER_CARD_SLAM.get();
            case GamblerPatterns.SPIRAL -> WardSounds.GAMBLER_SPIRAL_CAST.get();
            case GamblerPatterns.ROYAL -> WardSounds.GAMBLER_ROYAL_CAST.get();
            case GamblerPatterns.FORCED_CHOICE -> WardSounds.GAMBLER_CHOICE_BIND.get();
            case GamblerPatterns.DEALER_CROSS -> WardSounds.GAMBLER_CROSS_CAST.get();
            case GamblerPatterns.CHIP_STORM -> WardSounds.GAMBLER_CHIP_CAST.get();
            case GamblerPatterns.SPLIT_POT -> WardSounds.GAMBLER_POT_CAST.get();
            case GamblerPatterns.STACKED_DECK -> WardSounds.GAMBLER_CARD_SLAM.get();
            case GamblerPatterns.BURIED_ACE -> WardSounds.GAMBLER_CHIP_CAST.get();
            case GamblerPatterns.MIRROR_DEBT -> WardSounds.GAMBLER_CHOICE_BIND.get();
            case GamblerPatterns.HOUSE_EDGE -> WardSounds.GAMBLER_CROSS_CAST.get();
            case GamblerPatterns.DOUBLE_DOWN -> WardSounds.GAMBLER_POT_CAST.get();
            case GamblerPatterns.DEAD_MANS_DRAW -> WardSounds.GAMBLER_CARD_SLAM.get();
            case GamblerPatterns.FALSE_TELL -> WardSounds.GAMBLER_CHOICE_BIND.get();
            case GamblerPatterns.SUIT_CALL -> WardSounds.GAMBLER_CHIP_CAST.get();
            case GamblerPatterns.ROYAL_FLUSH -> WardSounds.GAMBLER_ROYAL_CAST.get();
            case GamblerPatterns.LAST_DEAL -> WardSounds.GAMBLER_PHASE_BREAK.get();
            default -> WardSounds.GAMBLER_CARD_FLIP.get();
        };
        float volume=hand==GamblerPatterns.ROYAL?1.15f:hand==GamblerPatterns.STARFALL?.92f:.78f;
        float pitch=phase==3?.93f:phase==2?1.0f:1.06f;
        l.playSound(null,blockPosition(),s,SoundSource.HOSTILE,volume,pitch);
        SoundEvent vanilla=switch(hand){
            case GamblerPatterns.FAN, GamblerPatterns.SWEEP, GamblerPatterns.DEALER_CROSS, GamblerPatterns.HOUSE_EDGE -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case GamblerPatterns.RINGS, GamblerPatterns.ROYAL, GamblerPatterns.ROYAL_FLUSH, GamblerPatterns.LAST_DEAL -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
            case GamblerPatterns.STARFALL, GamblerPatterns.CHIP_STORM, GamblerPatterns.BURIED_ACE -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case GamblerPatterns.CARD_WALL, GamblerPatterns.STACKED_DECK, GamblerPatterns.DEAD_MANS_DRAW -> SoundEvents.WOODEN_TRAPDOOR_CLOSE;
            case GamblerPatterns.FORCED_CHOICE, GamblerPatterns.FALSE_TELL, GamblerPatterns.MIRROR_DEBT, GamblerPatterns.SUIT_CALL -> SoundEvents.TRIPWIRE_CLICK_ON;
            default -> SoundEvents.ITEM_FRAME_ROTATE_ITEM;
        };
        l.playSound(null,blockPosition(),vanilla,SoundSource.HOSTILE,.55f,hand==GamblerPatterns.LAST_DEAL?.70f:.96f+random.nextFloat()*.10f);
    }

    private void playHandTimelineSounds(ServerLevel l,int hand,int phase,int wind){
        int draw=Math.max(4,wind-18), snap=Math.max(8,wind-10), flick=Math.max(10,wind-4), slam=Math.max(12,wind-1);
        if(clock==draw){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_FLIP.get(),SoundSource.HOSTILE,.58f,1.06f+random.nextFloat()*.06f);
            l.playSound(null,blockPosition(),SoundEvents.ITEM_FRAME_ROTATE_ITEM,SoundSource.HOSTILE,.34f,1.18f);
        }
        if(clock==snap){
            SoundEvent cue=switch(hand){
                case GamblerPatterns.CHIP_STORM, GamblerPatterns.STARFALL, GamblerPatterns.BURIED_ACE, GamblerPatterns.SPLIT_POT -> SoundEvents.AMETHYST_BLOCK_HIT;
                case GamblerPatterns.ROYAL, GamblerPatterns.ROYAL_FLUSH, GamblerPatterns.LAST_DEAL -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
                default -> SoundEvents.TRIPWIRE_CLICK_ON;
            };
            l.playSound(null,blockPosition(),cue,SoundSource.HOSTILE,.42f,hand==GamblerPatterns.LAST_DEAL?.74f:1.06f);
        }
        if(clock==flick){
            SoundEvent cue=switch(hand){
                case GamblerPatterns.CHIP_STORM, GamblerPatterns.STARFALL, GamblerPatterns.BURIED_ACE, GamblerPatterns.SPLIT_POT -> WardSounds.GAMBLER_CHIP_CAST.get();
                case GamblerPatterns.ROYAL, GamblerPatterns.ROYAL_FLUSH, GamblerPatterns.LAST_DEAL, GamblerPatterns.CARD_WALL, GamblerPatterns.STACKED_DECK, GamblerPatterns.DEAD_MANS_DRAW -> WardSounds.GAMBLER_CARD_SLAM.get();
                default -> WardSounds.GAMBLER_FAN_CAST.get();
            };
            l.playSound(null,blockPosition(),cue,SoundSource.HOSTILE,.52f,phase==3?.92f:1.02f);
        }
        if(clock==slam && (hand==GamblerPatterns.ROYAL || hand==GamblerPatterns.ROYAL_FLUSH || hand==GamblerPatterns.LAST_DEAL || hand==GamblerPatterns.DEAD_MANS_DRAW || hand==GamblerPatterns.STACKED_DECK)){
            l.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.24f,1.52f);
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_SLAM.get(),SoundSource.HOSTILE,.60f,.88f);
        }
    }
    private void trackNearest(List<ServerPlayer> seated){
        ServerPlayer target=null;double best=Double.MAX_VALUE;
        for(ServerPlayer p:seated){double d=distanceToSqr(p);if(d<best){best=d;target=p;}}
        if(target==null)return;
        double dx=target.getX()-getX(),dz=target.getZ()-getZ();
        double dy=target.getEyeY()-getEyeY(),flat=Math.max(.001,Math.hypot(dx,dz));
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0);
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,flat));
        setYHeadRot(Mth.approachDegrees(getYHeadRot(),yaw,7.5f));
        setXRot(Mth.approachDegrees(getXRot(),Mth.clamp(pitch,-28f,24f),5.5f));
    }
    private void voiceSound(ServerLevel l,int phase){
        SoundEvent s=switch(random.nextInt(6)){
            case 0 -> WardSounds.GAMBLER_VOICE_1.get();
            case 1 -> WardSounds.GAMBLER_VOICE_2.get();
            case 2 -> WardSounds.GAMBLER_VOICE_3.get();
            case 3 -> WardSounds.GAMBLER_AMBIENCE_1.get();
            case 4 -> WardSounds.GAMBLER_AMBIENCE_2.get();
            default -> WardSounds.GAMBLER_AMBIENCE_3.get();
        };
        l.playSound(null,blockPosition(),s,SoundSource.HOSTILE,.24f+phase*.045f,.94f+random.nextFloat()*.12f);
    }
    private void ambientDetonation(ServerLevel l,int phase){
        double a=random.nextDouble()*Math.PI*2,rad=7+random.nextDouble()*7;
        double x=getX()+Math.cos(a)*rad,z=getZ()+Math.sin(a)*rad,y=table.getY()+.5;
        cinematic(l,GamblerCinematicPacket.AMBIENT_DETONATION,x,y,z,2.3f+phase*.35f,turn*233+tickCount);
        l.playSound(null,BlockPos.containing(x,y,z),WardSounds.GAMBLER_AMBIENT_DETONATION.get(),SoundSource.HOSTILE,.38f+phase*.04f,.94f+random.nextFloat()*.10f);
    }
    private void buildStarfallMarks(List<ServerPlayer> seated,int phase,int wave){
        marks.clear();
        int desired=Math.min(7,2+phase+Math.max(1,seated.size()));
        for(ServerPlayer p:seated){if(marks.size()>=desired)break;marks.add(p.position());}
        java.util.Random r=new java.util.Random(((long)turn<<24) ^ (wave*0x9E3779B9L));
        while(marks.size()<desired){double a=r.nextDouble()*Math.PI*2,rad=4+r.nextDouble()*10;marks.add(new Vec3(getX()+Math.cos(a)*rad,table.getY(),getZ()+Math.sin(a)*rad));}
    }
    private void strikeStarfall(ServerLevel l,List<ServerPlayer> seated,int phase){
        impact(l);
        int fxSeed=turn*97+clock;
        for(Vec3 m:marks){
            cinematic(l,GamblerCinematicPacket.STARFALL,m.x,table.getY()+.12,m.z,2.7f,fxSeed++);
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_STARFALL_IMPACT.get(),SoundSource.HOSTILE,1.0f,.95f);
        for(ServerPlayer p:seated)for(Vec3 m:marks)if(Math.hypot(p.getX()-m.x,p.getZ()-m.z)<=2.7){hit(p,40+phase*7,phase);break;}
    }
    private void spawnTeeth(ServerLevel l,int phase,int wave){
        for(int lane=0;lane<GamblerPatterns.teethLanes(phase);lane++){
            double a=GamblerPatterns.teethAngle(aim,lane,phase,wave);
            for(double r=3.0;r<=15.0;r+=2.0){
                double x=getX()+Math.cos(a)*r,z=getZ()+Math.sin(a)*r;
                EvokerFangs fang=new EvokerFangs(l,x,table.getY(),z,(float)a,4,this);
                fang.getPersistentData().putBoolean("WardboundMasterTransient",true);
                l.addFreshEntity(fang);
            }
        }
    }
    private void resolveTeeth(List<ServerPlayer> seated,int phase,int wave){
        if(level() instanceof ServerLevel l){impact(l);for(int lane=0;lane<GamblerPatterns.teethLanes(phase);lane++)shockRay(l,GamblerPatterns.teethAngle(aim,lane,phase,wave),16);cinematic(l,GamblerCinematicPacket.HOUSE_TEETH,getX(),table.getY()+.12,getZ(),8f,turn*41+wave);}
        for(ServerPlayer p:seated)for(int lane=0;lane<GamblerPatterns.teethLanes(phase);lane++)if(GamblerPatterns.onRay(p.getX()-getX(),p.getZ()-getZ(),GamblerPatterns.teethAngle(aim,lane,phase,wave))){hit(p,30+phase*6,phase);break;}
    }
    private void wall(ServerLevel l,int wave,int phase,DustParticleOptions c,boolean strike){
        double offset=GamblerPatterns.wallOffset(wave,phase),gap=GamblerPatterns.wallGap(wave,phase),half=Math.sqrt(Math.max(0,256-offset*offset));
        boolean vertical=GamblerPatterns.wallVertical(turn);
        double g0=gap-2.7,g1=gap+2.7;
        if(vertical){wallSegment(l,getX()+offset,getZ()-half,getX()+offset,getZ()+g0,c,strike);wallSegment(l,getX()+offset,getZ()+g1,getX()+offset,getZ()+half,c,strike);}
        else{wallSegment(l,getX()-half,getZ()+offset,getX()+g0,getZ()+offset,c,strike);wallSegment(l,getX()+g1,getZ()+offset,getX()+half,getZ()+offset,c,strike);}
    }
    private void wallSegment(ServerLevel l,double x1,double z1,double x2,double z2,DustParticleOptions c,boolean strike){
        if(collectCues)cues.add(new MasterCuesPacket.Cue(false,x1,z1,x2,z2,1.05,color(c)));
    }
    private void strikeWall(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);wall(l,wave,phase,RED,true);
        double wallOffset=GamblerPatterns.wallOffset(wave,phase);boolean vertical=GamblerPatterns.wallVertical(turn);
        cinematic(l,GamblerCinematicPacket.CARD_WALL,getX()+(vertical?wallOffset:0),table.getY()+.12,getZ()+(vertical?0:wallOffset),7f,(vertical?0:1)|(wave<<2));
        for(ServerPlayer p:seated)if(GamblerPatterns.onWall(p.getX()-getX(),p.getZ()-getZ(),wave,phase,turn))hit(p,38+phase*7,phase);
    }
    private void strikeSpiral(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);cinematic(l,GamblerCinematicPacket.SPIRAL,getX(),table.getY()+.12,getZ(),10f,turn*53+wave);
        for(int s=0;s<GamblerPatterns.spiralSpokes(phase);s++){
            double a=GamblerPatterns.spiralAngle(aim,s,phase,wave);ray(l,a,RED,true);if(s%2==0)shockRay(l,a,16);
        }
        for(ServerPlayer p:seated)for(int s=0;s<GamblerPatterns.spiralSpokes(phase);s++)if(GamblerPatterns.onRay(p.getX()-getX(),p.getZ()-getZ(),GamblerPatterns.spiralAngle(aim,s,phase,wave))){hit(p,34+phase*6,phase);break;}
    }

    private void strikeDealerCross(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        cinematic(l,GamblerCinematicPacket.DEALER_CROSS,getX(),table.getY()+.12,getZ(),15.5f,turn*293+wave);
        for(int s=0;s<GamblerPatterns.crossSpokes(phase);s++){
            double a=GamblerPatterns.crossAngle(aim,s,phase,wave);
            ray(l,a,RED,true);
        }
        for(ServerPlayer p:seated){
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            for(int s=0;s<GamblerPatterns.crossSpokes(phase);s++){
                if(GamblerPatterns.onRay(x,z,GamblerPatterns.crossAngle(aim,s,phase,wave))){hit(p,36+phase*7,phase);break;}
            }
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_CROSS_IMPACT.get(),SoundSource.HOSTILE,.86f,.96f+wave*.03f);
    }

    private void strikeChip(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        if(marks.isEmpty())return;
        int index=Math.floorMod(wave,marks.size());
        Vec3 mark=marks.get(index);
        impact(l);
        cinematic(l,GamblerCinematicPacket.CHIP_STORM,mark.x,table.getY()+.12,mark.z,(float)(GamblerPatterns.chipRadius(phase)*2.0),turn*307+wave);
        for(ServerPlayer p:seated){
            if(GamblerPatterns.chipHit(p.getX(),p.getZ(),mark.x,mark.z,phase))hit(p,40+phase*7,phase);
        }
        l.playSound(null,BlockPos.containing(mark),WardSounds.GAMBLER_CHIP_IMPACT.get(),SoundSource.HOSTILE,.82f,1.05f-wave*.025f);
    }

    private void strikeSplitPot(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        cinematic(l,GamblerCinematicPacket.SPLIT_POT,getX(),table.getY()+.12,getZ(),15.8f,turn*313+wave);
        for(ServerPlayer p:seated){
            double distance=horizontal(p);
            if(GamblerPatterns.splitPotUnsafe(distance,wave,phase))hit(p,38+phase*7,phase);
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_POT_IMPACT.get(),SoundSource.HOSTILE,.85f,.90f+wave*.07f);
    }

    private void beginForcedChoice(ServerLevel l,List<ServerPlayer> seated,int phase){
        choiceToken=(int)((l.getGameTime() ^ getUUID().getLeastSignificantBits() ^ ((long)turn<<17)) & 0x7FFFFFFF);
        if(choiceToken==0)choiceToken=1;
        choiceDeadline=l.getGameTime()+forcedChoiceDuration();pendingChoices.clear();choiceOffers.clear();
        LockData d=LockData.get(l.getServer());
        for(ServerPlayer p:seated){
            pendingChoices.add(p.getUUID());
            int a=random.nextInt(GamblerForcedChoice.CARD_COUNT);
            int b=random.nextInt(GamblerForcedChoice.CARD_COUNT-1);
            if(b>=a)b++;
            if(random.nextBoolean()){int swap=a;a=b;b=swap;}
            choiceOffers.put(p.getUUID(),(a&0xFF)|((b&0xFF)<<8));
            cinematicPlayer(p,GamblerCinematicPacket.CHOICE_REVEAL,1.8f,(a&0xFF)|((b&0xFF)<<8)|((choiceToken&0x7FFF)<<16));
            d.setUniqueInt(p.getUUID(),"gambler_forced_choice_pending",choiceToken);
            d.setUniqueLong(p.getUUID(),"gambler_forced_choice_deadline",choiceDeadline);
            cinematicPlayer(p,GamblerCinematicPacket.CHOICE_BIND,1.6f,choiceToken ^ p.getId());
            sendChoiceScreen(p,phase);
        }
    }
    private void sendChoiceScreen(ServerPlayer p,int phase){
        int remain=(int)Math.max(20,Math.min(20*30,choiceDeadline-((ServerLevel)level()).getGameTime()));
        int offer=choiceOffers.getOrDefault(p.getUUID(),GamblerForcedChoice.THIRTY_CUT|(GamblerForcedChoice.COINS_EDGE<<8));
        int left=offer&0xFF,right=(offer>>>8)&0xFF;
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenGamblerChoicePacket(getId(),choiceToken,phase,remain,left,right));
    }
    private void tickForcedChoice(ServerLevel l,int phase){
        if(clock%20==0){for(UUID id:new ArrayList<>(pendingChoices)){ServerPlayer p=l.getServer().getPlayerList().getPlayer(id);if(p!=null)sendChoiceScreen(p,phase);}}
        if(l.getGameTime()>=choiceDeadline)settleUnresolvedChoices(l,true);
        clock++;
        if(pendingChoices.isEmpty() || clock>forcedChoiceDuration()+30){entityData.set(HAND,0);rest=Math.max(8,interval(16)/2);clock=0;choiceDeadline=Long.MIN_VALUE;choiceOffers.clear();}
    }
    private void settleUnresolvedChoices(ServerLevel l,boolean punish){
        for(UUID id:new ArrayList<>(pendingChoices)){
            ServerPlayer p=l.getServer().getPlayerList().getPlayer(id);
            if(punish){
                // A disconnected player keeps the live claim so reconnecting before/after the
                // deadline still resolves the same forced hand.
                if(p!=null)GamblerEvents.forceChoiceForfeit(p,choiceToken);
            }else GamblerEvents.clearChoiceClaim(l.getServer(),id,choiceToken);
            choiceOffers.remove(id);
        }
        pendingChoices.clear();
    }

    /**
     * The entity deliberately resumes from a neutral hand after save/load. Any persistent forced-choice
     * claim belonging to that abandoned hand must therefore be cancelled as well, including for offline
     * participants, or PlayerTickEvent can collect a ghost 50% forfeit after the encounter no longer owns it.
     */
    private void clearAbandonedChoiceClaims(ServerLevel l){
        LinkedHashSet<UUID> ids=new LinkedHashSet<>(players);
        ids.addAll(pendingChoices);
        for(UUID id:ids)GamblerEvents.clearAnyChoiceClaim(l.getServer(),id);
        pendingChoices.clear();choiceOffers.clear();choiceDeadline=Long.MIN_VALUE;choiceToken=0;
    }
    public void resolveForcedChoice(ServerPlayer player,int token,int choice){
        if(!(level() instanceof ServerLevel l) || !isAlive() || entityData.get(HAND)!=GamblerPatterns.FORCED_CHOICE || token!=choiceToken || !pendingChoices.contains(player.getUUID()) || choice<0 || choice>1)return;
        Integer encoded=choiceOffers.get(player.getUUID());
        if(encoded==null)return;
        int card=choice==0?(encoded&0xFF):((encoded>>>8)&0xFF);
        if(card<0 || card>=GamblerForcedChoice.CARD_COUNT)return;
        if(!GamblerEvents.clearChoiceClaim(player,token))return;
        pendingChoices.remove(player.getUUID());choiceOffers.remove(player.getUUID());
        switch(card){
            case GamblerForcedChoice.THIRTY_CUT -> {
                exactMaxHealthLoss(player,.30f);
                cinematicPlayer(player,GamblerCinematicPacket.PLAYER_STAGGER,.95f,choiceToken ^ 0x30);
                Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MasterLinePacket("THE PALE GAMBLER","Thirty percent of everything your body can hold. The cut does not bargain with armor."));
            }
            case GamblerForcedChoice.COINS_EDGE -> {
                l.playSound(null,player.blockPosition(),WardSounds.GAMBLER_CARD_FLIP.get(),SoundSource.PLAYERS,.75f,1.15f);
                if(random.nextBoolean()){
                    cinematicPlayer(player,GamblerCinematicPacket.PLAYER_RELEASE,.75f,choiceToken ^ 0x51);
                    Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MasterLinePacket("THE PALE GAMBLER","Heads. Nothing is taken. Do not mistake that for mercy."));
                }else{
                    exactMaxHealthLoss(player,.50f);
                    cinematicPlayer(player,GamblerCinematicPacket.PLAYER_STAGGER,1.15f,choiceToken ^ 0x50);
                    Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MasterLinePacket("THE PALE GAMBLER","Tails. Half of your maximum health belongs to the House."));
                }
            }
            case GamblerForcedChoice.BLUNT_HAND -> {
                exactMaxHealthLoss(player,.20f);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,20*20,3,false,true,true));
                cinematicPlayer(player,GamblerCinematicPacket.PLAYER_MARKED,1.05f,choiceToken ^ 0xB17);
                Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MasterLinePacket("THE PALE GAMBLER","A smaller cut, then a useless hand. Weakness IV. Twenty seconds."));
            }
            default -> {}
        }
    }
    private void exactMaxHealthLoss(ServerPlayer p,float fraction){
        float amount=Math.max(1f,p.getMaxHealth()*Mth.clamp(fraction,0f,1f));
        float after=p.getHealth()-amount;
        p.invulnerableTime=0;
        if(after<=0f)p.kill(); else p.setHealth(after);
        if(level() instanceof ServerLevel sl){
            sl.playSound(null,p.blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_2.get(),SoundSource.PLAYERS,.72f,.88f);
        }
    }
    private void resolveFloorCards(ServerLevel l,List<ServerPlayer> seated,int phase){
        if(floorCardsResolved)return;
        // Defensive recovery: the authoritative pattern is deterministic, so a transient list reset
        // must never turn a visible floor-card windup into a no-op.
        if(floorCards.isEmpty())floorCards.addAll(GamblerPatterns.floorCards(turn,phase));
        floorCardsResolved=true;
        impact(l);for(GamblerPatterns.CardZone zone:floorCards){
            drawFloorCard(l,zone,SAFE,true);
            int encoded=((int)Math.round(zone.angle()*1000.0)<<4)|(zone.suit()&15);
            cinematic(l,GamblerCinematicPacket.FLOOR_CARD_STRIKE,getX()+zone.x(),table.getY()+.13,getZ()+zone.z(),(float)zone.halfHeight(),encoded);
        }
        cinematic(l,GamblerCinematicPacket.FLOOR_CARD_IMPACT,getX(),table.getY()+.12,getZ(),11f,turn*67+phase);
        for(ServerPlayer p:seated){
            boolean safe=false;double x=p.getX()-getX(),z=p.getZ()-getZ();
            for(GamblerPatterns.CardZone zone:floorCards)if(GamblerPatterns.insideCard(x,z,zone)){safe=true;break;}
            if(!safe){percentHit(p,.80f);p.addEffect(new MobEffectInstance(MobEffects.WITHER,80+phase*20,0));}
            else cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,.65f,turn*271+p.getId());
        }
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_1.get(),SoundSource.HOSTILE,1.05f,.92f);
    }
    private void percentHit(ServerPlayer p,float fraction){
        p.invulnerableTime=0;
        float amount=Math.max(1f,p.getMaxHealth()*fraction);
        if(p.hurt(damageSources().magic(),amount))handHits++;
    }
    private void drawFloorCard(ServerLevel l,GamblerPatterns.CardZone zone,DustParticleOptions c,boolean strike){
        double cos=Math.cos(zone.angle()),sin=Math.sin(zone.angle());
        double hw=zone.halfWidth(),hh=zone.halfHeight();
        double cx=getX()+zone.x(),cz=getZ()+zone.z();
        double[][] outer={{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
        double[][] world=new double[4][2];
        for(int i=0;i<4;i++){
            double lx=outer[i][0],lz=outer[i][1];
            world[i][0]=cx+lx*cos-lz*sin;
            world[i][1]=cz+lx*sin+lz*cos;
        }
        // Outer plate.
        for(int i=0;i<4;i++){
            cardLine(l,world[i][0],world[i][1],world[(i+1)%4][0],world[(i+1)%4][1],c,strike);
        }

        // Heavy inner rails so the floor telegraph reads like an actual occult card face.
        double inset=.45, inset2=.92;
        double iw=Math.max(.6,hw-inset), ih=Math.max(.95,hh-inset);
        double iw2=Math.max(.35,hw-inset2), ih2=Math.max(.55,hh-inset2);
        DustParticleOptions ink = strike ? SAFE : GOLD;
        drawCardRect(l,cx,cz,cos,sin,iw,ih,ink,false);
        drawCardRect(l,cx,cz,cos,sin,iw2,ih2,ink,false);

        // Title rail and footer rail - feels like a named eldritch card instead of a plain rectangle.
        double railY = ih - .55;
        cardLineLocal(l,cx,cz,cos,sin,-iw*.72,-railY, iw*.72,-railY,ink,false);
        cardLineLocal(l,cx,cz,cos,sin,-iw*.72, railY, iw*.72, railY,ink,false);
        cardLineLocal(l,cx,cz,cos,sin,-iw*.56,-railY+.28, iw*.56,-railY+.28,ink,false);
        cardLineLocal(l,cx,cz,cos,sin,-iw*.56, railY-.28, iw*.56, railY-.28,ink,false);

        // Interior ruling.
        for(double f:new double[]{-.52,-.18,.18,.52}) {
            cardLineLocal(l,cx,cz,cos,sin,f*iw2,-ih2*.72,f*iw2,ih2*.72,ink,false);
        }
        cardLineLocal(l,cx,cz,cos,sin,-iw2*.58,-ih2*.06, iw2*.58,-ih2*.06,ink,false);
        cardLineLocal(l,cx,cz,cos,sin,-iw2*.58, ih2*.06, iw2*.58, ih2*.06,ink,false);

        // Corner pips.
        double pipX = iw2*.73, pipY = ih2*.73;
        drawCardPip(l,cx,cz,cos,sin,-pipX,-pipY,zone.suit(),ink);
        drawCardPip(l,cx,cz,cos,sin, pipX,-pipY,zone.suit(),ink);
        drawCardPip(l,cx,cz,cos,sin,-pipX, pipY,zone.suit(),ink);
        drawCardPip(l,cx,cz,cos,sin, pipX, pipY,zone.suit(),ink);

        // Central sigil family; alternate cards feel visually different.
        drawCenterCardSigil(l,cx,cz,cos,sin,zone.suit(),ink,strike);

        // Strike animation gets a quick travelling seam so the player sees the card "turn on".
        if(strike){
            double sweep = ((tickCount + zone.suit()*7) % 16) / 15.0 * 2.0 - 1.0;
            cardLineLocal(l,cx,cz,cos,sin,sweep*iw2,-ih2*.84,sweep*iw2,ih2*.84,SAFE,true);
        }
    }
    private void drawCardRect(ServerLevel l,double cx,double cz,double cos,double sin,double hw,double hh,DustParticleOptions c,boolean strike){
        double[][] pts={{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
        for(int i=0;i<4;i++){
            double x1=cx+pts[i][0]*cos-pts[i][1]*sin, z1=cz+pts[i][0]*sin+pts[i][1]*cos;
            double x2=cx+pts[(i+1)%4][0]*cos-pts[(i+1)%4][1]*sin, z2=cz+pts[(i+1)%4][0]*sin+pts[(i+1)%4][1]*cos;
            cardLine(l,x1,z1,x2,z2,c,strike);
        }
    }

    private void cardLineLocal(ServerLevel l,double cx,double cz,double cos,double sin,double lx1,double lz1,double lx2,double lz2,DustParticleOptions c,boolean strike){
        cardLine(l,cx+lx1*cos-lz1*sin,cz+lx1*sin+lz1*cos,cx+lx2*cos-lz2*sin,cz+lx2*sin+lz2*cos,c,strike);
    }

    private void drawCardPip(ServerLevel l,double cx,double cz,double cos,double sin,double px,double pz,int suit,DustParticleOptions ink){
        if((suit & 1)==0){
            // diamond/coin-like pip
            cardLineLocal(l,cx,cz,cos,sin,px,pz-.18,px+.18,pz,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px+.18,pz,px,pz+.18,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px,pz+.18,px-.18,pz,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px-.18,pz,px,pz-.18,ink,false);
        }else{
            // cut/star-like pip
            cardLineLocal(l,cx,cz,cos,sin,px-.16,pz,px+.16,pz,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px,pz-.16,px,pz+.16,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px-.12,pz-.12,px+.12,pz+.12,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,px+.12,pz-.12,px-.12,pz+.12,ink,false);
        }
    }

    private void drawCenterCardSigil(ServerLevel l,double cx,double cz,double cos,double sin,int suit,DustParticleOptions ink,boolean strike){
        double size = strike ? 1.18 : 1.05;
        if((suit & 1)==0){
            // ornate diamond card
            cardLineLocal(l,cx,cz,cos,sin,0,-size, size*.72,0,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,size*.72,0,0,size,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,0,size,-size*.72,0,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,-size*.72,0,0,-size,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,-size*.28,0,size*.28,0,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,0,-size*.28,0,size*.28,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,-size*.86,-size*.34,size*.86,-size*.34,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,-size*.86,size*.34,size*.86,size*.34,ink,false);
        }else{
            // severed/stellar sigil
            for(int i=0;i<4;i++){
                double a=i*Math.PI/2.0;
                cardLineLocal(l,cx,cz,cos,sin,0,0,Math.cos(a)*size,Math.sin(a)*size,ink,false);
            }
            cardLineLocal(l,cx,cz,cos,sin,-size*.82,-size*.82,size*.82,size*.82,ink,false);
            cardLineLocal(l,cx,cz,cos,sin,size*.82,-size*.82,-size*.82,size*.82,ink,false);
            drawCardRect(l,cx,cz,cos,sin,size*.36,size*.36,ink,false);
        }
    }

    private void cardLine(ServerLevel l,double x1,double z1,double x2,double z2,DustParticleOptions c,boolean strike){
        if(collectCues)cues.add(new MasterCuesPacket.Cue(false,x1,z1,x2,z2,.11,color(c)));
    }


    private void shockRing(ServerLevel l,double radius){
        // Sparse vanilla fallback at the exact server hit geometry. Premium texture VFX remains the
        // primary presentation, but a strike must never become visually silent if that layer is disabled.
        int points=32;double y=table.getY()+.18;
        for(int i=0;i<points;i++){
            double a=i*Math.PI*2.0/points,x=getX()+Math.cos(a)*radius,z=getZ()+Math.sin(a)*radius;
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,x,y,z,1,.025,.025,.025,.015);
            if((i&3)==0)l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,x,y+.04,z,1,.015,.015,.015,0);
        }
    }
    private void shockRay(ServerLevel l,double angle,double length){
        double y=table.getY()+.18,cos=Math.cos(angle),sin=Math.sin(angle);
        for(double d=2.2;d<=Math.max(2.2,length);d+=1.15){
            double x=getX()+cos*d,z=getZ()+sin*d;
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,x,y,z,1,.025,.025,.025,.012);
        }
    }
    private void markRift(ServerLevel l,Vec3 mark){
        cinematic(l,GamblerCinematicPacket.MARK_RIFT,mark.x,table.getY()+.12,mark.z,2.7f,turn*277+clock);
    }

    private void cinematic(ServerLevel l,int effect,double x,double y,double z,float size,int seed){
        GamblerCinematicPacket packet=new GamblerCinematicPacket(effect,-1,x,y,z,size,seed);
        for(ServerPlayer viewer:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet);
    }
    private void cinematicPlayer(ServerPlayer target,int effect,float size,int seed){
        if(!(level() instanceof ServerLevel l))return;
        GamblerCinematicPacket packet=new GamblerCinematicPacket(effect,target.getId(),target.getX(),target.getY(),target.getZ(),size,seed);
        for(ServerPlayer viewer:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet);
    }
    private void cinematicPrivate(ServerPlayer target,int effect,double x,double y,double z,float size,int seed){
        GamblerCinematicPacket packet=new GamblerCinematicPacket(effect,target.getId(),x,y,z,size,seed);
        Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->target),packet);
    }

    private void enforceArenaBoundary(ServerLevel arena){
        if(table==null)return;
        double limit=GamblerPatterns.BOUNDARY_RADIUS+.75;double limitSq=limit*limit;
        LockData data=LockData.get(arena.getServer());
        for(UUID id:new ArrayList<>(players)){
            ServerPlayer p=arena.getServer().getPlayerList().getPlayer(id);
            if(p==null || !p.isAlive() || p.isCreative() || p.isSpectator())continue;
            boolean wrongLevel=p.level()!=arena;
            double dx=p.getX()-(table.getX()+.5),dz=p.getZ()-(table.getZ()+.5);
            double vertical=p.getY()-table.getY();
            // Give modded movement room above the table, but do not let tunnelling beneath the felt become an escape route.
            if(!wrongLevel && dx*dx+dz*dz<=limitSq && vertical<=12.0D && vertical>=-2.5D)continue;
            if(pendingChoices.remove(id))GamblerEvents.clearChoiceClaim(p,choiceToken);
            choiceOffers.remove(id);
            WardHud.message(p,Component.literal("THE PALE GAMBLER // Leave the table, leave the hand.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED,net.minecraft.ChatFormatting.BOLD),false);
            if(p.level() instanceof ServerLevel pl){
                cinematicPlayer(p,GamblerCinematicPacket.PLAYER_STAGGER,1.25f,getId()*283+p.getId());
                pl.playSound(null,p.blockPosition(),WardSounds.GAMBLER_HEAVY_HIT_3.get(),SoundSource.HOSTILE,.70f,.70f);
            }
            p.kill();
        }
    }

    private static int color(DustParticleOptions c){return c==RED?0xFF4264:c==SAFE?0x55FFCA:c==GOLD?0xEDC16B:0xC77AFF;}
    private double horizontal(ServerPlayer p){return Math.hypot(p.getX()-getX(),p.getZ()-getZ());}
    private void circle(ServerLevel l,double radius,DustParticleOptions c){circleAt(l,getX(),getZ(),radius,c);}
    private void circleAt(ServerLevel l,double x,double z,double radius,DustParticleOptions c){
        if(collectCues)cues.add(new MasterCuesPacket.Cue(true,x,z,radius,0,c==VIOLET && radius>=3?1:.08,color(c)));
    }
    private void ray(ServerLevel l,double a,DustParticleOptions c,boolean strike){
        if(collectCues)cues.add(new MasterCuesPacket.Cue(false,getX()+Math.cos(a)*2.2,getZ()+Math.sin(a)*2.2,getX()+Math.cos(a)*16,getZ()+Math.sin(a)*16,.95,color(c)));
    }
    private void grid(ServerLevel l,int wave,DustParticleOptions c,boolean strike){
        if(!collectCues)return;
        for(int i=-2;i<=2;i++){
            double k=i*5+(wave%2)*2.5;
            double h=Math.sqrt(256-k*k);
            cues.add(new MasterCuesPacket.Cue(false,getX()+k,getZ()-h,getX()+k,getZ()+h,.9,color(c)));
            cues.add(new MasterCuesPacket.Cue(false,getX()-h,getZ()+k,getX()+h,getZ()+k,.9,color(c)));
        }
    }
    private void hit(ServerPlayer p,float raw,int phase){pendingDamage.merge(p.getUUID(),raw,Math::max);}
    private void applyHit(ServerPlayer p,float raw,int phase){
        float pressure=allIn?1.18f:1.0f;
        if(p.hurt(damageSources().mobAttack(this),(float)(raw*damageScale*pressure))){
            handHits++;
            if(phase>=2)p.addEffect(new MobEffectInstance(MobEffects.WITHER,60+phase*20,0));
            cinematicPlayer(p,GamblerCinematicPacket.PLAYER_STAGGER,.8f,turn*79+p.getId());
        }
    }
    private void impact(ServerLevel l){
        int hand=entityData.get(HAND),phase=entityData.get(PHASE);
        float strength=(hand==GamblerPatterns.ROYAL||hand==GamblerPatterns.LAST_DEAL||hand==GamblerPatterns.ROYAL_FLUSH?1.35f:hand==GamblerPatterns.HOUSE_EDGE||hand==GamblerPatterns.DEAD_MANS_DRAW?1.15f:.88f)+(phase-1)*.08f;
        motionBeat(MasterAnimationBeatPacket.RELEASE,strength);
        SoundEvent hit=switch(random.nextInt(4)){
            case 0 -> WardSounds.GAMBLER_HEAVY_HIT_1.get();
            case 1 -> WardSounds.GAMBLER_HEAVY_HIT_2.get();
            case 2 -> WardSounds.GAMBLER_HEAVY_HIT_3.get();
            default -> WardSounds.GAMBLER_CARD_SLAM.get();
        };
        l.playSound(null,blockPosition(),hit,SoundSource.HOSTILE,.68f,.92f+random.nextFloat()*.10f);
        l.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.HOSTILE,.52f,.86f+random.nextFloat()*.08f);
        if(random.nextFloat()<.35f)l.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.20f,1.7f+random.nextFloat()*.2f);
    }
    private void strikeFan(ServerLevel l,List<ServerPlayer> seated,int phase,int wave){
        impact(l);
        for(int lane=0;lane<GamblerPatterns.lanes(phase);lane++){
            double a=GamblerPatterns.laneAngle(aim,lane,phase,wave);
            ray(l,a,RED,true);
            shockRay(l,a,16);
        }
        cinematic(l,GamblerCinematicPacket.FAN_IMPACT,getX(),table.getY()+.12,getZ(),8f,turn*83+wave);
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_FAN_IMPACT.get(),SoundSource.HOSTILE,.88f,1.0f);
        for(ServerPlayer p:seated)for(int lane=0;lane<GamblerPatterns.lanes(phase);lane++)if(GamblerPatterns.onRay(p.getX()-getX(),p.getZ()-getZ(),GamblerPatterns.laneAngle(aim,lane,phase,wave))){hit(p,34+phase*6,phase);break;}
    }
    private void strikeRing(ServerLevel l,List<ServerPlayer> seated,double radius,int phase){
        circle(l,radius,RED);impact(l);shockRing(l,radius);cinematic(l,GamblerCinematicPacket.RING_IMPACT,getX(),table.getY()+.12,getZ(),(float)radius,turn*89+(int)(radius*10));
        l.playSound(null,blockPosition(),WardSounds.GAMBLER_RING_IMPACT.get(),SoundSource.HOSTILE,.82f,1.0f);
        for(ServerPlayer p:seated)if(p.getY()-getY()<.9 && p.getY()-getY()>-2 && GamblerPatterns.onRing(p.getX()-getX(),p.getZ()-getZ(),radius))hit(p,36+phase*6,phase);
    }
    private void strikeMarks(ServerLevel l,List<ServerPlayer> seated,int phase){
        impact(l);for(Vec3 m:marks){markRift(l,m);}for(ServerPlayer p:seated)for(Vec3 m:marks)if(GamblerPatterns.onMark(p.getX(),p.getZ(),m.x,m.z)){hit(p,38+phase*6,phase);break;}
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        boolean open=entityData.get(OPEN_TELL) && exposedTicks>0;
        ServerPlayer p=null;
        if(source.getEntity() instanceof ServerPlayer direct)p=direct;
        else if(source.getEntity() instanceof dev.marrowseal.wardbound.champion.DefeatedChampionEntity silas && silas.companionActive() && silas.companionOwnerId()!=null && level() instanceof ServerLevel sl)
            p=sl.getServer().getPlayerList().getPlayer(silas.companionOwnerId());
        if(deathClock>=0 || !introStarted() || p==null || !players.contains(p.getUUID()) || !active().contains(p) || (rest>30 && !open) || !isAlive() || summonTicks>0)return false;
        // Clean consecutive hands teach the player to read the dealer. Each read widens the
        // vulnerability multiplier; surviving The Last Deal opens the one true final punish window.
        int read=Math.max(1,entityData.get(HOUSE_READ));
        boolean finalTell=entityData.get(FINAL_TELL);
        float tellMultiplier=finalTell?2.50f:read>=3?1.90f:read==2?1.65f:1.45f;
        double capMultiplier=finalTell?2.10:read>=3?1.70:read==2?1.50:1.35;
        float scaledAmount=open?amount*tellMultiplier:amount;
        double cap=open?Math.min(600,hitCap*capMultiplier):Math.min(400,hitCap);
        float before=vitality.prepareNativeDamage(this);MasterNativeDamageBridge.arm(this);boolean result=super.hurt(source,(float)Math.min(cap,scaledAmount));float actual=MasterNativeDamageBridge.consume(this,Math.max(0,before-getHealth()));
        if(result||actual>0){
            contributors.add(p.getUUID());
            int reactionKind=open?MasterDamageReactionPacket.VULNERABLE:(actual>=Math.max(34,cap*.46)?MasterDamageReactionPacket.HEAVY:MasterDamageReactionPacket.LIGHT);
            if(!open && currentHand()!=0 && reactionKind==MasterDamageReactionPacket.HEAVY)reactionKind=MasterDamageReactionPacket.LIGHT; // critical hand poses never visually break.
            float reactionStrength=open?1.28f:reactionKind==MasterDamageReactionPacket.HEAVY?.92f:.48f;
            damageReaction(reactionKind,reactionStrength,getId()*313+turn*17+tickCount);
            SoundEvent hurtSound=switch(random.nextInt(3)){case 0->WardSounds.GAMBLER_HURT_1.get();case 1->WardSounds.GAMBLER_HURT_2.get();default->WardSounds.GAMBLER_HURT_3.get();};
            if(random.nextFloat()<.48f && level() instanceof ServerLevel sl)sl.playSound(null,blockPosition(),hurtSound,SoundSource.HOSTILE,.42f,.95f+random.nextFloat()*.12f);
            if(vitality.damage(actual)){bar.setProgress(0f);setHealth(1);beginDeathSequence(source);}else{bar.setProgress(vitality.fraction());vitality.syncNativeHealth(this);}
        }
        return result||actual>0;
    }
    private void beginDeathSequence(DamageSource source){
        if(deathClock>=0||rewarded)return;
        rewarded=true;deathClock=0;entityData.set(DEATH_CLOCK,0);setHealth(1);setInvulnerable(true);
        entityData.set(RECOVERY,0);entityData.set(HAND,0);entityData.set(OPEN_TELL,false);entityData.set(FINAL_TELL,false);entityData.set(GESTURE,0);gestureTicks=0;exposedTicks=0;
        marks.clear();floorCards.clear();suitAssignments.clear();pendingDamage.clear();
        if(level() instanceof ServerLevel l){settleUnresolvedChoices(l,false);if(table!=null)MasterArenaManager.cleanupCombatTransients(l,table);}
        if(source.getEntity() instanceof ServerPlayer p && players.contains(p.getUUID()))contributors.add(p.getUUID());
        else if(source.getEntity() instanceof dev.marrowseal.wardbound.champion.DefeatedChampionEntity silas && silas.companionOwnerId()!=null && level() instanceof ServerLevel sl){
            ServerPlayer ownerPlayer=sl.getServer().getPlayerList().getPlayer(silas.companionOwnerId());
            if(ownerPlayer!=null&&players.contains(ownerPlayer.getUUID()))contributors.add(ownerPlayer.getUUID());
        }
        if(level() instanceof ServerLevel l && owner!=null){
            tellImportant("Impossible. The table... remembers your name.");
            if(vitality.current()<=0)for(ServerPlayer p:active())if(contributors.contains(p.getUUID()))GamblerProgression.victory(p,debug);
            motionBeat(MasterAnimationBeatPacket.CLEAR,0);
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_DEATH_COLLAPSE.get(),SoundSource.HOSTILE,.72f,.72f);
        }
        bar.setProgress(0f);bar.removeAllPlayers();
    }
    private void tickDeathSequence(ServerLevel l){
        if(table!=null)setPos(table.getX()+.5,table.getY(),table.getZ()+.5);
        setDeltaMovement(Vec3.ZERO);setHealth(1);setInvulnerable(true);
        deathClock++;entityData.set(DEATH_CLOCK,deathClock);
        if(deathClock==12){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_WALL_RIP.get(),SoundSource.HOSTILE,.82f,.58f);
            l.playSound(null,blockPosition(),SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),SoundSource.HOSTILE,.42f,.62f);
        }
        if(deathClock==28){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_FLIP.get(),SoundSource.HOSTILE,.62f,.66f);
            l.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.24f,.48f);
        }
        if(deathClock==44){
            cinematic(l,GamblerCinematicPacket.DEATH_FINAL_CARD,getX(),table!=null?table.getY()+.12:getY(),getZ(),4.8f,getId()*211+turn);
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_FLIP.get(),SoundSource.HOSTILE,.88f,.82f);
        }
        if(deathClock==58){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_CARD_SLAM.get(),SoundSource.HOSTILE,.92f,.62f);
            l.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.18f,.72f);
        }
        if(deathClock==74){
            cinematic(l,GamblerCinematicPacket.VICTORY_SHATTER,getX(),table!=null?table.getY()+.15:getY(),getZ(),9f,getId()*193+turn);
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_DEATH_COLLAPSE.get(),SoundSource.HOSTILE,1.08f,.86f);
        }
        if(deathClock==88){
            l.playSound(null,blockPosition(),WardSounds.GAMBLER_AMBIENCE_3.get(),SoundSource.HOSTILE,.18f,.48f);
            for(ServerPlayer p:active())cinematicPlayer(p,GamblerCinematicPacket.PLAYER_RELEASE,1.0f,getId()*197+p.getId());
        }
        if(deathClock>=112){
            if(owner!=null)GamblerProgression.release(LockData.get(l.getServer()),owner,getUUID());
            discard();
        }
    }
    @Override public void die(DamageSource source){
        if(level().isClientSide){super.die(source);return;}
        beginDeathSequence(source);
    }
    @Override public void remove(RemovalReason reason){
        if(level() instanceof ServerLevel l && reason!=RemovalReason.CHANGED_DIMENSION)clearAbandonedChoiceClaims(l);
        if((reason==RemovalReason.DISCARDED || reason==RemovalReason.KILLED) && level() instanceof ServerLevel l){if(table!=null)MasterArenaManager.cleanupCombatTransients(l,table);if(owner!=null)GamblerProgression.release(LockData.get(l.getServer()),owner,getUUID());}
        bar.removeAllPlayers();super.remove(reason);
    }
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);if(introStarted())bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(owner!=null)tag.putUUID("TableOwner",owner);if(table!=null)tag.putLong("TableCenter",table.asLong());
        tag.putBoolean("DebugTable",debug);tag.putInt("TableTurn",turn);tag.putInt("TableAge",totalTicks);tag.putDouble("MasterMaximum",vitality.maximum());tag.putDouble("MasterHealth",vitality.current());
        tag.putDouble("MasterDamage",damageScale);tag.putDouble("MasterSpeed",speed);tag.putDouble("MasterHitCap",hitCap);tag.putInt("MasterTimeout",timeout);tag.putInt("MasterDetail",detail);tag.putBoolean("MasterDialogue",dialogue);tag.putBoolean("IntroStarted",introStarted());tag.putInt("IntroWaitTicks",introWaitTicks);tag.putInt("SummonTicks",summonTicks);tag.putBoolean("IntroPulsePlayed",introPulsePlayed);tag.putInt("ExposedTicks",exposedTicks);tag.putInt("HandHits",handHits);tag.putInt("SpecialIndex",specialIndex);tag.putInt("CleanStreak",cleanStreak);tag.putBoolean("AllIn",allIn);tag.putBoolean("LastDealUsed",lastDealUsed);tag.putBoolean("FinalTell",entityData.get(FINAL_TELL));tag.putInt("DeathClock",deathClock);tag.putBoolean("Rewarded",rewarded);tag.putIntArray("RecentHands",recentHands.stream().mapToInt(Integer::intValue).toArray());
        ListTag list=new ListTag();for(UUID id:players){CompoundTag p=new CompoundTag();p.putUUID("Id",id);p.putBoolean("Contributed",contributors.contains(id));list.add(p);}tag.put("TablePlayers",list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);owner=tag.hasUUID("TableOwner")?tag.getUUID("TableOwner"):null;table=tag.contains("TableCenter")?BlockPos.of(tag.getLong("TableCenter")):null;debug=tag.getBoolean("DebugTable");turn=Math.max(0,Math.min(1_000_000,tag.getInt("TableTurn")));totalTicks=0;
        players.clear();contributors.clear();for(var value:tag.getList("TablePlayers",10)){CompoundTag p=(CompoundTag)value;if(p.hasUUID("Id") && players.size()<4){UUID id=p.getUUID("Id");players.add(id);if(p.getBoolean("Contributed"))contributors.add(id);}}
        // Combat hands are intentionally not resumed from NBT. Cancel the matching persisted UI/timeout
        // claim at the same boundary so a server reload cannot later punish a choice that no longer exists.
        if(level() instanceof ServerLevel sl)clearAbandonedChoiceClaims(sl);
        BossConfig.Settings s=BossConfig.gambler();
        float legacyFraction=getMaxHealth()>0?getHealth()/getMaxHealth():1f;if(!Float.isFinite(legacyFraction))legacyFraction=1f;
        double max=tag.contains("MasterMaximum")?tag.getDouble("MasterMaximum"):GamblerPatterns.health(players.size());if(!Double.isFinite(max)||max<1)max=GamblerPatterns.health(players.size());
        double current=tag.contains("MasterHealth")?tag.getDouble("MasterHealth"):max*Math.max(0,Math.min(1,legacyFraction));if(!Double.isFinite(current))current=max;
        vitality=new MasterVitality(max);vitality.restore(current);vitality.bindMaximum(this);
        damageScale=tag.contains("MasterDamage")?tag.getDouble("MasterDamage"):s.damageMultiplier();if(!Double.isFinite(damageScale)||damageScale<=0)damageScale=s.damageMultiplier();
        speed=tag.contains("MasterSpeed")?tag.getDouble("MasterSpeed"):s.attackSpeed();if(!Double.isFinite(speed)||speed<=0)speed=s.attackSpeed();
        hitCap=tag.contains("MasterHitCap")?tag.getDouble("MasterHitCap"):s.hitCap();if(!Double.isFinite(hitCap)||hitCap<=0)hitCap=s.hitCap();
        timeout=tag.contains("MasterTimeout")?tag.getInt("MasterTimeout"):s.timeoutSeconds()*20;if(timeout<20||timeout>7200*20)timeout=s.timeoutSeconds()*20;totalTicks=Math.max(0,Math.min(timeout,tag.getInt("TableAge")));
        detail=tag.contains("MasterDetail")?Math.max(0,Math.min(2,tag.getInt("MasterDetail"))):s.particles();dialogue=tag.contains("MasterDialogue")?tag.getBoolean("MasterDialogue"):s.dialogue();boolean savedIntroStarted=tag.contains("IntroStarted")?tag.getBoolean("IntroStarted"):true;entityData.set(INTRO_STARTED,savedIntroStarted);introWaitTicks=tag.contains("IntroWaitTicks")?Math.max(0,Math.min(220,tag.getInt("IntroWaitTicks"))):0;summonTicks=tag.contains("SummonTicks")?Math.max(0,Math.min(94,tag.getInt("SummonTicks"))):0;introPulsePlayed=tag.getBoolean("IntroPulsePlayed");
        exposedTicks=Math.max(0,Math.min(600,tag.getInt("ExposedTicks")));handHits=Math.max(0,Math.min(64,tag.getInt("HandHits")));specialIndex=tag.contains("SpecialIndex")?Math.max(-1,Math.min(64,tag.getInt("SpecialIndex"))):-1;cleanStreak=Math.max(0,Math.min(3,tag.getInt("CleanStreak")));allIn=tag.getBoolean("AllIn") || vitality.fraction()<=.08f;lastDealUsed=tag.getBoolean("LastDealUsed");deathClock=tag.contains("DeathClock")?Math.max(-1,Math.min(112,tag.getInt("DeathClock"))):-1;rewarded=tag.getBoolean("Rewarded")||deathClock>=0;rest=80;clock=0;pendingChoices.clear();choiceOffers.clear();floorCards.clear();suitAssignments.clear();recentHands.clear();for(int hand:tag.getIntArray("RecentHands")){if(hand>=GamblerPatterns.FAN&&hand<=GamblerPatterns.LAST_DEAL){recentHands.remove(hand);recentHands.addLast(hand);while(recentHands.size()>4)recentHands.removeFirst();}}choiceDeadline=Long.MIN_VALUE;entityData.set(RECOVERY,0);entityData.set(HAND,0);entityData.set(PHASE,GamblerPatterns.phase(vitality.fraction()));entityData.set(HOUSE_READ,cleanStreak);entityData.set(ALL_IN,allIn);entityData.set(FINAL_TELL,deathClock<0&&tag.getBoolean("FinalTell") && exposedTicks>0);entityData.set(OPEN_TELL,deathClock<0&&exposedTicks>0);gestureTicks=0;entityData.set(GESTURE,0);entityData.set(DEATH_CLOCK,deathClock);setNoGravity(true);setInvisible(!introStarted()&&deathClock<0);setInvulnerable(deathClock>=0||!introStarted()||summonTicks>0);if(deathClock>=0)setHealth(1);else if(vitality.current()>0)vitality.syncNativeHealth(this);else setHealth(0);
    }
}
