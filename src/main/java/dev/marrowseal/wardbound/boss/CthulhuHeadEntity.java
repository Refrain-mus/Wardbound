package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/** The head-only pre-final sovereign. Preview clips and the real arena encounter share one rig. */
public final class CthulhuHeadEntity extends PathfinderMob implements GeoEntity, CinematicVitalityHost {
    public static final List<String> CLIPS=CthulhuHeadRig.CLIPS;
    private static final int TRANSITION_TICKS=6;
    private static final double COMBAT_HEALTH=14000.0,PROXY_MAX_HEALTH=MasterVitality.REGISTRATION_MAX_HEALTH;
    private static final EntityDataAccessor<String> CLIP=SynchedEntityData.defineId(CthulhuHeadEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> REVISION=SynchedEntityData.defineId(CthulhuHeadEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEFT=SynchedEntityData.defineId(CthulhuHeadEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RIGHT=SynchedEntityData.defineId(CthulhuHeadEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> CLIP_START=SynchedEntityData.defineId(CthulhuHeadEntity.class,EntityDataSerializers.LONG);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.wardbound.cthulhu_head"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);

    private int remaining=226,seenRevision=-1,showcaseIndex=-1;
    private boolean manualGaze;
    private static final List<String> SHOWCASE=List.of("manifestation","idle_observing","tracking_gaze","eye_beam_charge","eye_beam_fire","dual_eye_burst","tentacle_lash","tentacle_fan_attack","tentacle_grasp","tentacle_recoil","eldritch_summon","abyssal_pulse","psychic_roar","head_slam","phase_transition","idle_rage","death_start","death_collapse","death_end");
    public final float[] gazeYaw=new float[2],gazePitch=new float[2];public double lastGazeTick=-1;

    // Combat state. The arena is intentionally single-witness for this pre-final pass.
    private boolean combat,practice,dying;
    private MasterVitality vitality=new MasterVitality(COMBAT_HEALTH);
    private UUID combatOwner;
    private BlockPos arenaCenter;
    private int manifestTicks,phase=1,phaseTransition,restTicks=40,patternTick,deathTicks;
    private CthulhuHeadCombat.Pattern pattern,lastPattern;
    private Vec3 attackPoint=Vec3.ZERO,attackPoint2=Vec3.ZERO,attackDir=new Vec3(0,0,1);
    private double attackAngle;
    private int variant;
    private final List<Vec3> zones=new ArrayList<>();
    private final Set<UUID> patternHits=new HashSet<>();

    public CthulhuHeadEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setPersistenceRequired();setNoGravity(true);noCulling=true;xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,PROXY_MAX_HEALTH).add(Attributes.ARMOR,16).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.FOLLOW_RANGE,96);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(CLIP,"manifestation");entityData.define(REVISION,0);entityData.define(LEFT,-1);entityData.define(RIGHT,-1);entityData.define(CLIP_START,0L);}
    public float visualAge(float partialTick){return Math.max(0,(level().getGameTime()-entityData.get(CLIP_START))+partialTick);}
    public String clip(){return entityData.get(CLIP);}public boolean isCombat(){return combat;}public boolean isPractice(){return practice;}public UUID combatOwner(){return combatOwner;}public int combatPhase(){return phase;}

    public void beginCombat(ServerPlayer owner,BlockPos center,boolean debug){
        if(level().isClientSide||owner==null||center==null)return;combat=true;practice=debug;dying=false;combatOwner=owner.getUUID();arenaCenter=center.immutable();phase=1;manifestTicks=220;phaseTransition=0;restTicks=40;pattern=null;lastPattern=null;deathTicks=0;zones.clear();patternHits.clear();setInvulnerable(true);vitality=new MasterVitality(COMBAT_HEALTH);vitality.syncNativeHealth(this);bar.addPlayer(owner);bar.setVisible(true);bar.setProgress(1);startClip("manifestation");CthulhuHeadCombat.fx((ServerLevel)level(),arena(),WardLodestoneFxPacket.CTHULHU_MANIFEST,2.4f);sound(SoundEvents.ENDER_DRAGON_GROWL,1.4f,.38f);
        if(!debug)WardHud.send(owner,"THE HEAD // Do not mistake proximity for arrival. My body has not crossed into this dream.",WardHud.Mood.GLITCH,5600);
    }

    public void playClip(String name){if(level().isClientSide||!CLIPS.contains(name))return;showcaseIndex=-1;startClip(name);}
    public void beginShowcase(){if(level().isClientSide||combat)return;showcaseIndex=0;startClip(SHOWCASE.get(0));}
    private void startClip(String name){entityData.set(CLIP,name);entityData.set(REVISION,entityData.get(REVISION)+1);remaining=CthulhuHeadRig.CLIP_TICKS[CLIPS.indexOf(name)]+TRANSITION_TICKS;entityData.set(CLIP_START,level().getGameTime());}
    public void setEyeTargets(LivingEntity left,LivingEntity right){if(level().isClientSide)return;manualGaze=true;entityData.set(LEFT,left==null?-1:left.getId());entityData.set(RIGHT,right==null?-1:right.getId());}
    public void automaticGaze(){manualGaze=false;}public Entity eyeTarget(boolean left){return level().getEntity(entityData.get(left?LEFT:RIGHT));}

    @Override public void aiStep(){
        super.aiStep();setDeltaMovement(Vec3.ZERO);setNoGravity(true);
        if(level().isClientSide)return;
        if(combat){combatTick();return;}
        if(tickCount==1&&entityData.get(CLIP_START)==0L)entityData.set(CLIP_START,level().getGameTime());
        if((showcaseIndex>=0||!clip().startsWith("idle_"))&&remaining>0&&--remaining==0){if(showcaseIndex>=0){if(++showcaseIndex<SHOWCASE.size())startClip(SHOWCASE.get(showcaseIndex));else showcaseIndex=-1;}else switch(clip()){case "death_start"->playClip("death_collapse");case "death_collapse"->playClip("death_end");case "death_end"->{}case "eye_beam_charge"->playClip("eye_beam_fire");default->playClip("idle_awake");}}
        if(!manualGaze&&tickCount%10==0){var p=level().getNearestPlayer(this,96);int id=p==null?-1:p.getId();entityData.set(LEFT,id);entityData.set(RIGHT,id);}
    }

    private void combatTick(){
        if(!(level() instanceof ServerLevel sl)||arenaCenter==null||combatOwner==null){discard();return;}
        Vec3 home=CthulhuHeadArena.bossPos(arenaCenter);setPos(home.x,home.y,home.z);setYRot(0);setYBodyRot(0);setYHeadRot(0);
        ServerPlayer p=sl.getServer().getPlayerList().getPlayer(combatOwner);if(p==null||!p.isAlive()||p.level()!=sl){bar.removeAllPlayers();return;}bar.addPlayer(p);bar.setProgress(vitality.fraction());
        if(manualGaze&&(pattern==CthulhuHeadCombat.Pattern.TRACKING_GAZE||pattern==CthulhuHeadCombat.Pattern.EYE_LANCE||pattern==CthulhuHeadCombat.Pattern.DUAL_GAZE)){if(eyeTarget(true)!=p||eyeTarget(false)!=p)setEyeTargets(p,p);}
        if(dying){deathTick();return;}
        if(manifestTicks>0){int age=220-manifestTicks;if(age==45||age==105||age==170)CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_MANIFEST,1.5f+age/180f);if(--manifestTicks==0){setInvulnerable(false);playIdle();sound(SoundEvents.SCULK_SHRIEKER_SHRIEK,1.0f,.42f);}return;}
        if(pattern==null&&phaseTransition==0){int wanted=vitality.fraction()<=.35f?3:vitality.fraction()<=.70f?2:1;if(wanted>phase){phase=wanted;phaseTransition=140;setInvulnerable(true);startClip("phase_transition");CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_PHASE,2.0f+phase*.25f);sound(SoundEvents.WARDEN_ROAR,1.1f,.45f);if(!practice){if(phase==2)WardHud.send(p,"THE HEAD // You strike a crown without its kingdom. The flesh you seek sleeps farther than distance.",WardHud.Mood.DANGER,5200);else WardHud.send(p,"THE HEAD // This face is only the part of me your world can survive remembering.",WardHud.Mood.GLITCH,5600);}return;}}
        if(phaseTransition>0){int age=140-phaseTransition;if(age==35||age==75||age==115)CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_PHASE,1.25f+age*.006f+phase*.12f);if(--phaseTransition==0){setInvulnerable(false);playIdle();restTicks=28;}return;}
        if(pattern==null){if(--restTicks<=0)beginPattern(p);return;}
        tickPattern(p);patternTick++;if(pattern!=null&&patternTick>=patternDuration())endPattern();
    }

    private void beginPattern(ServerPlayer p){
        CthulhuHeadCombat.Pattern[] pool=phase==1?new CthulhuHeadCombat.Pattern[]{CthulhuHeadCombat.Pattern.EYE_LANCE,CthulhuHeadCombat.Pattern.TENTACLE_LASH,CthulhuHeadCombat.Pattern.PSYCHIC_ROAR,CthulhuHeadCombat.Pattern.HEAD_SLAM}:phase==2?new CthulhuHeadCombat.Pattern[]{CthulhuHeadCombat.Pattern.EYE_LANCE,CthulhuHeadCombat.Pattern.DUAL_GAZE,CthulhuHeadCombat.Pattern.TENTACLE_LASH,CthulhuHeadCombat.Pattern.TENTACLE_FAN,CthulhuHeadCombat.Pattern.TENTACLE_GRASP,CthulhuHeadCombat.Pattern.PSYCHIC_ROAR,CthulhuHeadCombat.Pattern.HEAD_SLAM,CthulhuHeadCombat.Pattern.VOID_ERUPTION}:CthulhuHeadCombat.Pattern.values();
        CthulhuHeadCombat.Pattern pick=pool[random.nextInt(pool.length)];for(int i=0;i<8&&pick==lastPattern;i++)pick=pool[random.nextInt(pool.length)];pattern=pick;lastPattern=pick;patternTick=0;patternHits.clear();zones.clear();variant=random.nextInt(2);attackPoint=p.position();attackPoint2=attackPoint;attackDir=CthulhuHeadCombat.flat(p.position().subtract(arena()));if(attackDir.lengthSqr()<.01)attackDir=new Vec3(0,0,1);else attackDir=attackDir.normalize();attackAngle=random.nextDouble()*Math.PI;startClip(pattern.clip);
        if(pattern==CthulhuHeadCombat.Pattern.TRACKING_GAZE||pattern==CthulhuHeadCombat.Pattern.EYE_LANCE||pattern==CthulhuHeadCombat.Pattern.DUAL_GAZE)setEyeTargets(p,p);
        if(pattern==CthulhuHeadCombat.Pattern.TENTACLE_GRASP)attackPoint=CthulhuHeadCombat.clampToArena(p.position().add(p.getDeltaMovement().multiply(5,0,5)),arena(),CthulhuHeadArena.ARENA_RADIUS-5.5);
        if(pattern==CthulhuHeadCombat.Pattern.VOID_ERUPTION){for(int i=0;i<6+(phase==3?2:0);i++){double a=random.nextDouble()*Math.PI*2,r=8+random.nextDouble()*34;Vec3 q=arena().add(Math.cos(a)*r,0,Math.sin(a)*r);if(i<2)q=p.position().add(p.getDeltaMovement().multiply(7+i*3,0,7+i*3));q=CthulhuHeadCombat.clampToArena(q,arena(),CthulhuHeadArena.ARENA_RADIUS-5.0);zones.add(q);}}
    }

    private void tickPattern(ServerPlayer p){ServerLevel sl=(ServerLevel)level();int t=patternTick;Collection<ServerPlayer> players=List.of(p);
        switch(pattern){
            case EYE_LANCE->{boolean left=variant==0;Vec3 eye=eyeOrigin(left);if(t<78&&t%12==0){attackPoint=p.position().add(p.getDeltaMovement().multiply(5,0,5));CthulhuHeadCombat.fxLine(sl,eye,extendBeam(eye,attackPoint),WardLodestoneFxPacket.CTHULHU_EYE_CHARGE,.82f,9);}if(t==80){attackPoint=p.position().add(p.getDeltaMovement().multiply(8,0,8));startClip(variant==0?"eye_beam_left":"eye_beam_right");sound(SoundEvents.GUARDIAN_ATTACK,1.15f,.38f);}if(t==92){Vec3 end=extendBeam(eye,attackPoint);CthulhuHeadCombat.fxLine(sl,eye,end,WardLodestoneFxPacket.CTHULHU_EYE_BEAM,1.35f,14);CthulhuHeadCombat.damageLine(this,players,eye,end,1.7,24f,.35);}}
            case DUAL_GAZE->{Vec3 d=attackDir,dl=CthulhuHeadCombat.rotate(d,-14),dr=CthulhuHeadCombat.rotate(d,14);Vec3 le=eyeOrigin(true),re=eyeOrigin(false),lend=le.add(dl.scale(92)),rend=re.add(dr.scale(92));if(t==8||t==24||t==40){CthulhuHeadCombat.fxLine(sl,le,lend,WardLodestoneFxPacket.CTHULHU_EYE_CHARGE,.78f,10);CthulhuHeadCombat.fxLine(sl,re,rend,WardLodestoneFxPacket.CTHULHU_EYE_CHARGE,.78f,10);}if(t==52){CthulhuHeadCombat.fxLine(sl,le,lend,WardLodestoneFxPacket.CTHULHU_EYE_BEAM,1.25f,13);CthulhuHeadCombat.fxLine(sl,re,rend,WardLodestoneFxPacket.CTHULHU_EYE_BEAM,1.25f,13);CthulhuHeadCombat.damageLine(this,players,le,lend,1.5,18f,.25);CthulhuHeadCombat.damageLine(this,players,re,rend,1.5,18f,.25);sound(SoundEvents.GUARDIAN_ATTACK,1.25f,.5f);}}
            case TRACKING_GAZE->{if(t<25)attackPoint=p.position();else if(t<=142){attackPoint=attackPoint.lerp(p.position(),phase==3?.105:.082);Vec3 eye=eyeOrigin(variant==0),end=extendBeam(eye,attackPoint);if(t%5==0)CthulhuHeadCombat.fxLine(sl,eye,end,t<50?WardLodestoneFxPacket.CTHULHU_EYE_CHARGE:WardLodestoneFxPacket.CTHULHU_EYE_BEAM,t<50?.72f:1.0f,10);if(t>=50&&t%18==0)CthulhuHeadCombat.damageLine(this,players,eye,end,1.15,7f,.08);}}
            case TENTACLE_LASH->{if(t==10||t==26||t==42)for(double off:new double[]{-11,0,11})lineFx(sl,attackAngle,off,WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH,.9f);if(t==55){for(double off:new double[]{-11,0,11})lineHit(sl,players,attackAngle,off,19f);sound(SoundEvents.SCULK_CLICKING_STOP,1.2f,.45f);}if(phase<3&&t==60)startClip("tentacle_recoil");if(phase==3&&t==60)startClip("tentacle_lash");if(phase==3&&(t==70||t==86||t==102))for(double off:new double[]{-16,-5,6,17})lineFx(sl,attackAngle+Math.PI/2,off,WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH,.86f);if(phase==3&&t==115){for(double off:new double[]{-16,-5,6,17})lineHit(sl,players,attackAngle+Math.PI/2,off,15f);sound(SoundEvents.SCULK_CLICKING_STOP,1.15f,.4f);}if(phase==3&&t==120)startClip("tentacle_recoil");}
            case TENTACLE_FAN->{Vec3 o=arena();if(t==12||t==30||t==46)for(double deg:new double[]{-42,-21,0,21,42}){Vec3 end=o.add(CthulhuHeadCombat.rotate(attackDir,deg).scale(48));CthulhuHeadCombat.fxLine(sl,o,end,WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH,.82f,9);}if(t==61){for(double deg:new double[]{-42,-21,0,21,42}){Vec3 end=o.add(CthulhuHeadCombat.rotate(attackDir,deg).scale(48));CthulhuHeadCombat.fxLine(sl,o,end,WardLodestoneFxPacket.CTHULHU_TENTACLE_IMPACT,1.15f,11);CthulhuHeadCombat.damageLine(this,players,o,end,1.45,16f,.45);}sound(SoundEvents.WARDEN_SONIC_BOOM,1.0f,.62f);}}
            case TENTACLE_GRASP->{if(t==8||t==24||t==40||t==56)CthulhuHeadCombat.fxRing(sl,attackPoint,5.4-(t*.025),WardLodestoneFxPacket.CTHULHU_GRASP,1.0f,18);if(t==54){Vec3 d=attackPoint.subtract(p.position()).multiply(1,0,1);if(d.lengthSqr()<9*9&&d.lengthSqr()>.01){d=d.normalize().scale(1.05);p.setDeltaMovement(p.getDeltaMovement().add(d.x,.10,d.z));p.hurtMarked=true;}}if(t==72){CthulhuHeadCombat.fxRing(sl,attackPoint,4.4,WardLodestoneFxPacket.CTHULHU_TENTACLE_IMPACT,1.4f,24);CthulhuHeadCombat.damageCircle(this,players,attackPoint,4.4,22f,.45);sound(SoundEvents.SQUID_SQUIRT,1.0f,.42f);}if(t==76)startClip("tentacle_recoil");}
            case PSYCHIC_ROAR->{if(t==40)sound(SoundEvents.WARDEN_ROAR,1.35f,.52f);if(t>=48&&t<=102&&t%4==0){double radius=5+(t-48)*.68;CthulhuHeadCombat.fxRing(sl,arena(),radius,WardLodestoneFxPacket.CTHULHU_ROAR,1.0f,24);double d=Math.sqrt(p.position().subtract(arena()).multiply(1,0,1).lengthSqr());if(Math.abs(d-radius)<=2.0&&p.onGround()&&patternHits.add(p.getUUID())){p.hurt(damageSources().magic(),17f);Vec3 out=p.position().subtract(arena()).multiply(1,0,1);if(out.lengthSqr()>.01){out=out.normalize().scale(1.1);p.setDeltaMovement(p.getDeltaMovement().add(out.x,.35,out.z));p.hurtMarked=true;}}}}
            case HEAD_SLAM->{if(t==12||t==34||t==54)CthulhuHeadCombat.fxCorridor(sl,arena(),attackDir,43,7.2,WardLodestoneFxPacket.CTHULHU_SLAM_TELEGRAPH,1.0f);if(t==70){CthulhuHeadCombat.damageCorridor(this,players,arena(),attackDir,43,7.2,29f);CthulhuHeadCombat.fxCorridor(sl,arena(),attackDir,43,7.2,WardLodestoneFxPacket.CTHULHU_SLAM_IMPACT,1.28f);sound(SoundEvents.GENERIC_EXPLODE,1.5f,.45f);}if(t==78){CthulhuHeadCombat.fxRing(sl,arena(),15,WardLodestoneFxPacket.CTHULHU_PULSE,1.2f,24);CthulhuHeadCombat.damageAnnulus(this,players,arena(),15,2.4,11f,false);}}
            case VOID_ERUPTION->{for(int i=0;i<zones.size();i++){int due=52+i*13;Vec3 q=zones.get(i);if(t==due-28||t==due-12)CthulhuHeadCombat.fxRing(sl,q,3.7,WardLodestoneFxPacket.CTHULHU_VOID_RIFT,.9f,18);if(t==due){CthulhuHeadCombat.fx(sl,q.add(0,.2,0),WardLodestoneFxPacket.CTHULHU_VOID_RIFT,1.55f);CthulhuHeadCombat.damageCircle(this,players,q,4.2,16f,.5);sound(SoundEvents.SCULK_CATALYST_BLOOM,.72f,.48f);}}}
            case ABYSSAL_PULSE->{int[] when={45,70,95,120};double[] radii={8,18,29,40};for(int i=0;i<when.length;i++){if(t==when[i]-12)CthulhuHeadCombat.fxRing(sl,arena(),radii[i],WardLodestoneFxPacket.CTHULHU_PULSE,.85f,26);if(t==when[i]){CthulhuHeadCombat.fxRing(sl,arena(),radii[i],WardLodestoneFxPacket.CTHULHU_PULSE,1.35f,28);CthulhuHeadCombat.damageAnnulus(this,players,arena(),radii[i],2.4,18f,false);sound(SoundEvents.CONDUIT_ATTACK_TARGET,.75f,.46f+i*.06f);}}}
            case SOVEREIGN_CROSS->{double a=attackAngle;if(t==14||t==32){lineFx(sl,a,0,WardLodestoneFxPacket.CTHULHU_EYE_CHARGE,.95f);lineFx(sl,a+Math.PI/2,0,WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH,.95f);}if(t==52){lineHit(sl,players,a,0,23f);lineHit(sl,players,a+Math.PI/2,0,23f);CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_PHASE,1.5f);}if(t==84)startClip("tentacle_fan_attack");if(t==96||t==114||t==130){lineFx(sl,a+Math.PI/4,0,WardLodestoneFxPacket.CTHULHU_EYE_CHARGE,.9f);lineFx(sl,a-Math.PI/4,0,WardLodestoneFxPacket.CTHULHU_TENTACLE_TELEGRAPH,.9f);}if(t==145){lineHit(sl,players,a+Math.PI/4,0,21f);lineHit(sl,players,a-Math.PI/4,0,21f);sound(SoundEvents.WARDEN_SONIC_BOOM,1.2f,.45f);}if(t==149)startClip("tentacle_recoil");}
        }
    }

    private int patternDuration(){return pattern==CthulhuHeadCombat.Pattern.TENTACLE_LASH&&phase==3?190:pattern.ticks;}
    private void endPattern(){pattern=null;patternTick=0;zones.clear();patternHits.clear();automaticGaze();playIdle();restTicks=phase==1?48:phase==2?34:22;}
    private void playIdle(){startClip(phase==1?"idle_awake":phase==2?"idle_observing":"idle_rage");}
    private Vec3 arena(){return Vec3.atCenterOf(arenaCenter).add(0,1.0,0);}
    private Vec3 eyeOrigin(boolean left){double[] s=left?CthulhuHeadRig.EYE_LEFT:CthulhuHeadRig.EYE_RIGHT;return position().add(new Vec3(s[0],s[1],s[2]));}
    private Vec3 extendBeam(Vec3 origin,Vec3 target){Vec3 d=target.add(0,1,0).subtract(origin);if(d.lengthSqr()<.01)d=new Vec3(0,0,1);return origin.add(d.normalize().scale(92));}
    private void lineFx(ServerLevel sl,double angle,double offset,int kind,float scale){Vec3 c=arena(),d=new Vec3(Math.cos(angle),0,Math.sin(angle)),r=new Vec3(-d.z,0,d.x),a=c.add(r.scale(offset)).subtract(d.scale(48)),b=c.add(r.scale(offset)).add(d.scale(48));CthulhuHeadCombat.fxLine(sl,a,b,kind,scale,12);}
    private void lineHit(ServerLevel sl,Collection<ServerPlayer> players,double angle,double offset,float damage){Vec3 c=arena(),d=new Vec3(Math.cos(angle),0,Math.sin(angle)),r=new Vec3(-d.z,0,d.x),a=c.add(r.scale(offset)).subtract(d.scale(48)),b=c.add(r.scale(offset)).add(d.scale(48));CthulhuHeadCombat.fxLine(sl,a,b,WardLodestoneFxPacket.CTHULHU_TENTACLE_IMPACT,1.25f,14);CthulhuHeadCombat.damageLine(this,players,a,b,1.85,damage,.55);}
    private void sound(net.minecraft.sounds.SoundEvent event,float volume,float pitch){if(level() instanceof ServerLevel sl)sl.playSound(null,blockPosition(),event,SoundSource.HOSTILE,volume,pitch);}

    private void beginDeath(){if(dying)return;dying=true;pattern=null;phaseTransition=0;setHealth(1);setInvulnerable(true);deathTicks=0;automaticGaze();startClip("death_start");bar.setProgress(0);CthulhuHeadCombat.fx((ServerLevel)level(),arena(),WardLodestoneFxPacket.CTHULHU_DEATH,2.3f);sound(SoundEvents.ENDER_DRAGON_DEATH,1.2f,.35f);if(!practice&&level() instanceof ServerLevel sl&&combatOwner!=null){ServerPlayer p=sl.getServer().getPlayerList().getPlayer(combatOwner);if(p!=null)WardHud.send(p,"THE HEAD // Silence this mouth if you wish. You have not yet stood before the body that owns it.",WardHud.Mood.GLITCH,6200);}}
    private void deathTick(){ServerLevel sl=(ServerLevel)level();if(deathTicks==100){startClip("death_collapse");CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_DEATH,2.8f);}if(deathTicks==240){startClip("death_end");CthulhuHeadCombat.fx(sl,arena(),WardLodestoneFxPacket.CTHULHU_DEATH,3.2f);}if(++deathTicks>=340){CthulhuHeadArena.victory(this);bar.removeAllPlayers();discard();}}

    @Override public boolean hurt(DamageSource source,float amount){
        if(!combat){if(source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)){discard();return true;}if(!level().isClientSide&&!clip().startsWith("death_")&&clip().startsWith("idle_"))playClip("stagger");return false;}
        if(level().isClientSide||dying||isInvulnerable())return false;Entity attacker=source.getEntity();if(!(attacker instanceof ServerPlayer p)||combatOwner==null||!combatOwner.equals(p.getUUID()))return false;float applied=Math.min(85f,Math.max(0,amount));if(applied<=0)return false;
        // AttributeFix exposes the sovereign's real 14k max health. Probe at full native
        // health only while Forge calculates mitigation, then restore the authoritative value.
        float before=vitality.prepareNativeDamage(this);MasterNativeDamageBridge.arm(this);boolean hit=super.hurt(source,applied);float actual=MasterNativeDamageBridge.consume(this,Math.max(0,before-getHealth()));if((hit||actual>0)&&actual>0){if(vitality.damage(actual)){bar.setProgress(0f);beginDeath();}else{bar.setProgress(vitality.fraction());vitality.syncNativeHealth(this);}}return hit||actual>0;
    }
    @Override public boolean removeWhenFarAway(double d){return false;}@Override public boolean isPushable(){return false;}@Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(32,28,32);}@Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}@Override public void remove(RemovalReason reason){bar.removeAllPlayers();super.remove(reason);}

    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("CthulhuClip",clip());tag.putBoolean("Combat",combat);tag.putBoolean("Practice",practice);tag.putBoolean("Dying",dying);tag.putInt("Phase",phase);tag.putInt("Manifest",manifestTicks);tag.putInt("DeathTicks",deathTicks);tag.putDouble("CthulhuVitalityMax",vitality.maximum());tag.putDouble("CthulhuVitalityCurrent",vitality.current());if(combatOwner!=null)tag.putUUID("CombatOwner",combatOwner);if(arenaCenter!=null)tag.putLong("ArenaCenter",arenaCenter.asLong());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);combat=tag.getBoolean("Combat");practice=tag.getBoolean("Practice");dying=tag.getBoolean("Dying");phase=Math.max(1,Math.min(3,tag.getInt("Phase")));manifestTicks=Math.max(0,tag.getInt("Manifest"));deathTicks=Math.max(0,tag.getInt("DeathTicks"));double vitalityMax=tag.getDouble("CthulhuVitalityMax"),vitalityCurrent=tag.getDouble("CthulhuVitalityCurrent");if(!Double.isFinite(vitalityMax)||vitalityMax<1)vitalityMax=COMBAT_HEALTH;vitality=new MasterVitality(vitalityMax);vitality.restore(Double.isFinite(vitalityCurrent)?vitalityCurrent:vitalityMax);vitality.bindMaximum(this);if(tag.hasUUID("CombatOwner"))combatOwner=tag.getUUID("CombatOwner");if(tag.contains("ArenaCenter"))arenaCenter=BlockPos.of(tag.getLong("ArenaCenter"));pattern=null;phaseTransition=0;restTicks=40;zones.clear();patternHits.clear();if(!level().isClientSide){if(combat){if(dying)startClip(deathTicks<100?"death_start":deathTicks<240?"death_collapse":"death_end");else if(manifestTicks>0)startClip("manifestation");else playIdle();}else{String name=tag.getString("CthulhuClip");startClip(CLIPS.contains(name)?name:"idle_awake");}}if(combat){if(dying)setHealth(1);else vitality.syncNativeHealth(this);setInvulnerable(dying||manifestTicks>0);}}
    @Override public MasterVitality wardboundVitality(){return vitality;}
    @Override public boolean wardboundVitalityActive(){return combat;}
    @Override public boolean wardboundVitalityLocked(){return dying;}
    @Override public void wardboundVitalityChanged(){bar.setProgress(vitality.fraction());}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar){registrar.add(new AnimationController<>(this,"performance",TRANSITION_TICKS,state->{int revision=entityData.get(REVISION);if(seenRevision!=revision){state.getController().forceAnimationReset();seenRevision=revision;}String name=clip();RawAnimation a=RawAnimation.begin();String resource="animation.cthulhu_head."+name;return state.setAndContinue(name.startsWith("idle_")?a.thenLoop(resource):a.thenPlayAndHold(resource));}));}
}
