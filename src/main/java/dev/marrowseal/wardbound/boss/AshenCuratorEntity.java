package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.net.MasterLinePacket;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import dev.marrowseal.wardbound.net.MasterDamageReactionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
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

/** Server owns every deadline, seal decision and hit. No attack entities or world edits. */
public final class AshenCuratorEntity extends Monster implements GeoEntity, CinematicVitalityHost {
    public static final int MANIFEST=0, REST=1, SWEEP=2, RINGS=3, ERRATA=4, ARCHIVE=5, OPEN=6, TRANSITION=7, COLLAPSE=8;
    public static final int BOOK_CLOSED=0, BOOK_OPENING=1, BOOK_CHARGE=2, BOOK_RELEASE=3, BOOK_RECOIL=4, BOOK_CLOSING=5, BOOK_DEATH=6;
    public static final int BOOK_NONE=0, BOOK_SALVO=1, BOOK_HOMING=2, BOOK_SPIRAL=3, BOOK_FLAME=4;
    private static final EntityDataAccessor<CompoundTag> VIEW=SynchedEntityData.defineId(AshenCuratorEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.wardbound.ashen_curator"),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> participants=new LinkedHashSet<>(), contributors=new HashSet<>();
    private final Map<UUID,Integer> absent=new HashMap<>();
    private final List<Hazard> hazards=new ArrayList<>();
    private final int[] sealCharge=new int[4];
    private MasterVitality reserve=new MasterVitality(8000);
    private BossConfig.Settings tuning=BossConfig.curator();
    private BlockPos center;
    private boolean practice,retired,rewarded,silasFinalPhaseTriggered;
    private int state=MANIFEST,clock,chapter=1,turn,total,empty,kept,broken,pulse,actor=-1,hurtAt=-100,lastHit=-100,variant,archiveDeadline=-1,recoveryTicks;
    private int sealUntil,sealCooldown,openUntil,flareUntil,nextHazard,fallback;
    private int bookState=BOOK_CLOSED,bookClock,bookPattern=BOOK_NONE,bookSerial,bookNextCast,bookFlameUntil,nextPageShot;
    private float bookAimYaw,bookAimPitch;
    private UUID bookTarget;
    private final List<PageShot> pageShots=new ArrayList<>();
    private float healingBudget;
    private final UUID encounter=UUID.randomUUID();
    public record Hazard(int id,int shape,double x,double z,double a,double b,long born,int warning,int duration) {
        CompoundTag tag(){CompoundTag t=new CompoundTag();t.putInt("id",id);t.putInt("shape",shape);t.putDouble("x",x);t.putDouble("z",z);t.putDouble("a",a);t.putDouble("b",b);t.putLong("born",born);t.putInt("warning",warning);t.putInt("duration",duration);return t;}
        public static Hazard read(CompoundTag t){return new Hazard(t.getInt("id"),t.getInt("shape"),t.getDouble("x"),t.getDouble("z"),t.getDouble("a"),t.getDouble("b"),t.getLong("born"),t.getInt("warning"),t.getInt("duration"));}
    }
    private static final class PageShot {
        final int id,mode,life;
        final UUID target;
        final float spin;
        Vec3 pos,vel;
        int age,burst;
        PageShot(int id,int mode,Vec3 pos,Vec3 vel,UUID target,float spin,int life){this.id=id;this.mode=mode;this.pos=pos;this.vel=vel;this.target=target;this.spin=spin;this.life=life;}
    }
    public AshenCuratorEntity(EntityType<? extends Monster> type,Level level){super(type,level);setNoGravity(true);setPersistenceRequired();noCulling=true;}
    // AttributeFix applies after attribute-supplier registration; encounter init binds the real 8k+ native maximum.
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MasterVitality.REGISTRATION_MAX_HEALTH).add(Attributes.ARMOR,10).add(Attributes.ARMOR_TOUGHNESS,6).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.FOLLOW_RANGE,40);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(VIEW,new CompoundTag());}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    public CompoundTag view(){return entityData.get(VIEW);}
    public int bookState(){return view().getInt("book_state");}
    public int bookPattern(){return view().getInt("book_pattern");}
    public float bookAimYaw(){return view().getFloat("book_aim_yaw");}
    public float bookAimPitch(){return view().getFloat("book_aim_pitch");}
    public boolean bookFlameActive(){return view().getBoolean("book_flame");}
    public Vec3 bookDirection(){return directionFromAngles(bookAimYaw(),bookAimPitch());}
    public Vec3 bookMuzzle(){return position().add(0,3.55,0).add(bookDirection().scale(1.15));}
    public boolean includes(UUID id){return participants.contains(id);}
    public boolean silasFinalPhase(){return silasFinalPhaseTriggered || reserve.fraction()<=.333f;}
    public boolean clientParticipant(UUID id){ListTag list=view().getList("players",Tag.TAG_STRING);for(Tag t:list)if(t.getAsString().equals(id.toString()))return true;return false;}
    public void begin(ServerPlayer owner,BlockPos pos,boolean debug){
        center=pos;practice=debug;tuning=BossConfig.curator();
        retired=false;rewarded=false;silasFinalPhaseTriggered=false;state=MANIFEST;clock=0;chapter=1;turn=0;total=0;empty=0;kept=broken=pulse=0;actor=-1;variant=0;archiveDeadline=-1;
        participants.clear();contributors.clear();absent.clear();hazards.clear();pageShots.clear();Arrays.fill(sealCharge,0);
        participants.add(owner.getUUID());
        for(ServerPlayer p:owner.serverLevel().players()) if(participants.size()<8 && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(Vec3.atBottomCenterOf(pos))<=48*48)participants.add(p.getUUID());
        reserve=new MasterVitality(tuning.health()+tuning.healthPerAlly()*(participants.size()-1));reserve.syncNativeHealth(this);setInvulnerable(true);recoveryTicks=0;
        bookState=BOOK_CLOSED;bookClock=0;bookPattern=BOOK_NONE;bookTarget=owner.getUUID();bookAimYaw=getYRot();bookAimPitch=0;pageShots.clear();
        sound("manifestation",1);tell("The archive remembers your revisions. Choose what survives.");
        dev.marrowseal.wardbound.champion.SilasMasterEncounter.onMasterBegin(owner,dev.marrowseal.wardbound.champion.SilasMasterEncounter.CURATOR,this::includes);sync();
    }
    private void restForFinalMargin(){
        enter(REST);bookPattern=BOOK_NONE;if(bookState!=BOOK_CLOSED)setBookState(BOOK_CLOSING);
    }
    private List<ServerPlayer> active(){
        if(!(level() instanceof ServerLevel l)||center==null)return List.of();
        return l.players().stream().filter(p->participants.contains(p.getUUID()) && p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atBottomCenterOf(center))<=72*72).toList();
    }
    private void sound(String key,float volume){
        if(!(level() instanceof ServerLevel l))return;
        l.playSound(null,blockPosition(),CuratorSounds.get(key),SoundSource.HOSTILE,volume,1);
        float jitter=.94f+random.nextFloat()*.12f;
        switch(key){
            case "manifestation" -> {l.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.72f*volume,.58f);l.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.20f*volume,.78f);}
            case "archive_opening" -> {l.playSound(null,blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.92f*volume,.68f);l.playSound(null,blockPosition(),SoundEvents.EVOKER_PREPARE_SUMMON,SoundSource.HOSTILE,.42f*volume,.72f);}
            case "shelf_cast" -> {l.playSound(null,blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.88f*volume,.82f);l.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.48f*volume,.72f);}
            case "ring_cast" -> {l.playSound(null,blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.HOSTILE,.72f*volume,.68f);l.playSound(null,blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.HOSTILE,.52f*volume,.78f);}
            case "errata_mark" -> {l.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.62f*volume,1.12f);l.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.40f*volume,.82f);}
            case "paper_movement" -> l.playSound(null,blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.72f*volume,jitter);
            case "ash_sweep" -> {l.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.62f*volume,.64f);l.playSound(null,blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.HOSTILE,.34f*volume,.86f);}
            case "impact" -> {l.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.22f*volume,1.55f);l.playSound(null,blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.HOSTILE,.48f*volume,.72f);}
            case "phase_transition" -> {l.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.25f*volume,.62f);l.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.55f*volume,.58f);}
            case "index_burn" -> {l.playSound(null,blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.HOSTILE,.92f*volume,.56f);l.playSound(null,blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.HOSTILE,.88f*volume,.62f);l.playSound(null,blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.HOSTILE,.42f*volume,.52f);}
            case "open_binding" -> {l.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.72f*volume,1.20f);l.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.68f*volume,.76f);}
            case "preserve" -> l.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.68f*volume,1.28f);
            case "fracture" -> {l.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.28f*volume,1.72f);l.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.34f*volume,.64f);}
            case "ambient_presence" -> {if(random.nextFloat()<.65f)l.playSound(null,blockPosition(),SoundEvents.SOUL_ESCAPE,SoundSource.HOSTILE,.18f*volume,.58f+random.nextFloat()*.18f);if(random.nextFloat()<.45f)l.playSound(null,blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.22f*volume,.72f+random.nextFloat()*.20f);}
            case "whispers" -> {l.playSound(null,blockPosition(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.HOSTILE,.16f*volume,.52f+random.nextFloat()*.12f);l.playSound(null,blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.12f*volume,.48f);}
            case "victory" -> {l.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.72f*volume,.82f);l.playSound(null,blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.52f*volume,.58f);}
            case "death" -> {l.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.28f*volume,.48f);l.playSound(null,blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.HOSTILE,.56f*volume,.54f);}
            case "hurt" -> l.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.HOSTILE,.22f*volume,.78f+random.nextFloat()*.12f);
            default -> { }
        }
    }

    private void timelineSound(SoundEvent sound,float volume,float pitch){
        if(level() instanceof ServerLevel l)l.playSound(null,blockPosition(),sound,SoundSource.HOSTILE,volume,pitch);
    }
    private void playCuratorAnimationSounds(){
        switch(state){
            case SWEEP, RINGS, ERRATA, ARCHIVE -> {
                if(clock==5)timelineSound(SoundEvents.BOOK_PAGE_TURN,.55f,.72f+random.nextFloat()*.10f);
                if(clock==11)timelineSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM,.38f,1.25f);
                if(state==RINGS && clock==20)timelineSound(SoundEvents.BLAZE_SHOOT,.62f,.74f);
                if(state==SWEEP && clock==18)timelineSound(SoundEvents.FIRECHARGE_USE,.46f,.90f);
                if(state==ERRATA && (clock==18||clock==42))timelineSound(SoundEvents.ENCHANTMENT_TABLE_USE,.36f,1.08f);
                if(state==ARCHIVE && clock==24)timelineSound(SoundEvents.FIRE_AMBIENT,.36f,.82f);
            }
            case OPEN -> {
                if(clock==2)timelineSound(SoundEvents.BOOK_PAGE_TURN,.82f,.62f);
                if(clock==8)timelineSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM,.52f,1.12f);
                if(clock==14)timelineSound(SoundEvents.FIRECHARGE_USE,.88f,.68f);
                if(clock==22)timelineSound(SoundEvents.BLAZE_SHOOT,.92f,.78f);
                if(clock==34)timelineSound(SoundEvents.GENERIC_EXPLODE,.36f,.58f);
            }
            case TRANSITION -> {
                if(clock==10)timelineSound(SoundEvents.WARDEN_ROAR,.28f,.78f);
            }
            default -> {}
        }
    }
    private static Vec3 directionFromAngles(float yawDeg,float pitchDeg){
        double yaw=Math.toRadians(yawDeg+90.0),pitch=Math.toRadians(pitchDeg),cp=Math.cos(pitch);
        return new Vec3(Math.cos(yaw)*cp,-Math.sin(pitch),Math.sin(yaw)*cp).normalize();
    }
    private static float wrapDegrees(float deg){deg%=360f;if(deg>=180f)deg-=360f;if(deg<-180f)deg+=360f;return deg;}
    private static float approachAngle(float current,float target,float step){float d=wrapDegrees(target-current);if(d>step)d=step;if(d<-step)d=-step;return current+d;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static Vec3 rotateHorizontal(Vec3 v,double radians){double c=Math.cos(radians),s=Math.sin(radians);return new Vec3(v.x*c-v.z*s,v.y,v.x*s+v.z*c);}
    private Vec3 bookDirectionServer(){return directionFromAngles(bookAimYaw,bookAimPitch);}
    private Vec3 bookMuzzleServer(){return position().add(0,3.55,0).add(bookDirectionServer().scale(1.15));}
    private ServerPlayer bookTargetPlayer(List<ServerPlayer> readers){
        if(bookTarget!=null && level() instanceof ServerLevel sl){ServerPlayer p=sl.getServer().getPlayerList().getPlayer(bookTarget);if(p!=null&&readers.contains(p))return p;}
        if(readers.isEmpty())return null;
        ServerPlayer p=readers.get(Math.floorMod(bookSerial+turn,readers.size()));bookTarget=p.getUUID();return p;
    }
    private void updateBookAim(List<ServerPlayer> readers){
        ServerPlayer target=bookTargetPlayer(readers);if(target==null)return;
        Vec3 origin=position().add(0,3.55,0),d=target.getEyePosition().subtract(origin);double flat=Math.sqrt(d.x*d.x+d.z*d.z);
        float desiredYaw=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90.0),desiredPitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.max(.001,flat)));
        float yawStep=(bookState==BOOK_CHARGE||bookState==BOOK_RELEASE)?8.5f:4.5f,pitchStep=(bookState==BOOK_CHARGE||bookState==BOOK_RELEASE)?5.0f:3.0f;
        bookAimYaw=approachAngle(bookAimYaw,desiredYaw,yawStep);bookAimPitch=clamp(approachAngle(bookAimPitch,desiredPitch,pitchStep),-48f,48f);
    }
    private void setBookState(int next){if(bookState==BOOK_DEATH)return;bookState=next;bookClock=0;}
    private void beginBookCast(int pattern,List<ServerPlayer> readers){
        if(bookState==BOOK_DEATH||readers.isEmpty())return;
        bookPattern=pattern;bookSerial++;ServerPlayer target=readers.get(Math.floorMod(bookSerial+turn,readers.size()));bookTarget=target.getUUID();setBookState(BOOK_OPENING);
    }
    private int bookChargeTicks(){return switch(bookPattern){case BOOK_FLAME->15;case BOOK_SPIRAL->14;case BOOK_HOMING->12;case BOOK_SALVO->10;default->10;};}
    private void tickBook(List<ServerPlayer> readers){
        tickPageShots(readers);updateBookAim(readers);
        if(state==COLLAPSE){bookState=BOOK_DEATH;bookClock++;return;}
        if(state==OPEN && bookState==BOOK_CLOSED && total>=bookNextCast && total+38<openUntil)beginBookCast(BOOK_FLAME,readers);
        bookClock++;
        switch(bookState){
            case BOOK_CLOSED -> { }
            case BOOK_OPENING -> {if(bookClock==1){timelineSound(SoundEvents.BOOK_PAGE_TURN,.78f,.70f);timelineSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM,.30f,1.18f);}if(bookClock>=8)setBookState(BOOK_CHARGE);}
            case BOOK_CHARGE -> {if(bookClock==1){timelineSound(SoundEvents.ENCHANTMENT_TABLE_USE,.48f,.86f);timelineSound(SoundEvents.AMETHYST_BLOCK_CHIME,.34f,.62f);}if(bookClock>=bookChargeTicks())setBookState(BOOK_RELEASE);}
            case BOOK_RELEASE -> {if(bookClock==1)fireBookPattern(readers);if(bookClock>=8)setBookState(BOOK_RECOIL);}
            case BOOK_RECOIL -> {if(bookClock==1)timelineSound(SoundEvents.BOOK_PAGE_TURN,.44f,.54f);if(bookClock>=7)setBookState(BOOK_CLOSING);}
            case BOOK_CLOSING -> {if(bookClock==1)timelineSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM,.28f,.78f);if(bookClock>=9){bookState=BOOK_CLOSED;bookClock=0;bookPattern=BOOK_NONE;bookNextCast=total+14;}}
            default -> { }
        }
    }
    private void fireBookPattern(List<ServerPlayer> readers){
        if(!(level() instanceof ServerLevel sl))return;
        Vec3 origin=bookMuzzleServer(),dir=bookDirectionServer();
        motionBeat(MasterAnimationBeatPacket.RELEASE,bookPattern==BOOK_FLAME?1.30f:.78f+(chapter>=2?.12f:0));
        switch(bookPattern){
            case BOOK_SALVO -> {
                int count=chapter>=2?7:5;for(int i=0;i<count;i++){double spread=(i-(count-1)/2.0)*(chapter>=2?5.8:7.0);spawnPageShot(BOOK_SALVO,origin,rotateHorizontal(dir,Math.toRadians(spread)).scale(chapter>=2?.72:.64),null,0,52);}
                timelineSound(SoundEvents.BOOK_PAGE_TURN,.92f,1.18f);
            }
            case BOOK_HOMING -> {
                int count=chapter>=2?6:4;for(int i=0;i<count;i++){ServerPlayer target=readers.get(i%readers.size());Vec3 launch=rotateHorizontal(dir,Math.toRadians((i-(count-1)/2.0)*8.0)).scale(.58);spawnPageShot(BOOK_HOMING,origin,launch,target.getUUID(),0,64);}
                timelineSound(SoundEvents.ENCHANTMENT_TABLE_USE,.52f,1.25f);timelineSound(SoundEvents.BOOK_PAGE_TURN,.82f,1.35f);
            }
            case BOOK_SPIRAL -> {
                int count=chapter>=2?9:7;for(int i=0;i<count;i++){double spread=(i-(count-1)/2.0)*5.0;float spin=(i%2==0?1:-1)*(chapter>=2?.070f:.055f);spawnPageShot(BOOK_SPIRAL,origin,rotateHorizontal(dir,Math.toRadians(spread)).scale(.60),readers.get(i%readers.size()).getUUID(),spin,68);}
                timelineSound(SoundEvents.BOOK_PAGE_TURN,.94f,.88f);timelineSound(SoundEvents.AMETHYST_BLOCK_CHIME,.46f,1.10f);
            }
            case BOOK_FLAME -> {
                bookFlameUntil=total+(chapter>=2?18:15);timelineSound(SoundEvents.FIRECHARGE_USE,1.0f,.63f);timelineSound(SoundEvents.BLAZE_SHOOT,.94f,.74f);timelineSound(SoundEvents.GENERIC_EXPLODE,.24f,.62f);
            }
            default -> timelineSound(SoundEvents.BOOK_PAGE_TURN,.48f,.80f);
        }
    }
    private void spawnPageShot(int mode,Vec3 pos,Vec3 vel,UUID target,float spin,int life){
        if(pageShots.size()>=48)pageShots.remove(0);pageShots.add(new PageShot(++nextPageShot,mode,pos,vel,target,spin,life));
    }
    private ServerPlayer pageTarget(UUID id,List<ServerPlayer> readers){if(id==null)return null;for(ServerPlayer p:readers)if(p.getUUID().equals(id))return p;return null;}
    private void burstPageShot(ServerLevel sl,PageShot shot){
        if(shot.burst>0)return;shot.burst=6;shot.vel=Vec3.ZERO;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,shot.pos.x,shot.pos.y,shot.pos.z,10,.35,.30,.35,.035);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,shot.pos.x,shot.pos.y,shot.pos.z,4,.18,.18,.18,.02);
        sl.playSound(null,BlockPos.containing(shot.pos.x,shot.pos.y,shot.pos.z),SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,.24f,1.45f);
    }
    private void tickPageShots(List<ServerPlayer> readers){
        if(!(level() instanceof ServerLevel sl)||pageShots.isEmpty())return;
        Iterator<PageShot> it=pageShots.iterator();
        while(it.hasNext()){
            PageShot s=it.next();
            if(s.burst>0){if(--s.burst<=0)it.remove();continue;}
            s.age++;double speed=Math.max(.01,s.vel.length());
            if(s.mode==BOOK_HOMING){ServerPlayer target=pageTarget(s.target,readers);if(target!=null){Vec3 desired=target.getEyePosition().subtract(s.pos);if(desired.lengthSqr()>.01){desired=desired.normalize().scale(speed);s.vel=s.vel.scale(.86).add(desired.scale(.14));if(s.vel.lengthSqr()>.001)s.vel=s.vel.normalize().scale(speed);}}}
            else if(s.mode==BOOK_SPIRAL){s.vel=rotateHorizontal(s.vel,s.spin);s.vel=s.vel.add(0,Math.sin((s.age+s.id)*.48)*.008,0);if(s.vel.lengthSqr()>.001)s.vel=s.vel.normalize().scale(speed);}
            Vec3 next=s.pos.add(s.vel);boolean hit=false;
            for(ServerPlayer p:readers){Vec3 center=p.position().add(0,p.getBbHeight()*.52,0);if(center.distanceToSqr(next)<=1.55*1.55){float dmg=(float)((chapter>=2?10:7)*tuning.damageMultiplier());if(p.hurt(damageSources().indirectMagic(this,this),dmg))lastHit=total;hit=true;break;}}
            s.pos=next;if(hit||s.age>=s.life)burstPageShot(sl,s);
        }
    }
    private void tell(String text){if(!tuning.dialogue()||text==null||text.isBlank())return;int duration=dialogueDurationMs(text);for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new MasterLinePacket("THE ASHEN CURATOR",text,duration));}
    private int dialogueDurationMs(String text){
        int words=text.trim().split("\\s+").length,reading=1500+words*210;
        int remaining=switch(state){
            case MANIFEST->Math.max(30,100-clock);case TRANSITION->Math.max(30,95-clock);
            case SWEEP->Math.max(30,74-clock);case RINGS->Math.max(30,82-clock);case ERRATA->Math.max(30,78-clock);
            case ARCHIVE->Math.max(40,Math.min(140,(archiveDeadline>0?archiveDeadline:indexDeadline())-clock));
            case OPEN->Math.max(30,Math.min(110,openUntil-total));case COLLAPSE->Math.max(30,120-clock);default->48;
        };
        return Math.max(2200,Math.min(7600,Math.max(reading,remaining*50)));
    }
    private void motionBeat(int phase,float strength){
        MasterAnimationBeatPacket pkt=new MasterAnimationBeatPacket(getId(),MasterAnimationBeatPacket.CURATOR,phase,strength,getId()*577+total*17+state*31+phase);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    private void damageReaction(int kind,float strength,int seed){
        MasterDamageReactionPacket pkt=new MasterDamageReactionPacket(getId(),MasterDamageReactionPacket.CURATOR,kind,strength,seed);
        for(ServerPlayer p:active())Wardbound.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),pkt);
    }
    private int warning(int ticks){return CuratorPatterns.warning(tuning.attackSpeed(),ticks);}
    private int indexDeadline(){return Math.max(120,warning(180));}
    private void enter(int next){
        int previous=state;state=next;clock=0;
        if(next==REST && previous!=REST && previous!=MANIFEST && previous!=TRANSITION && previous!=COLLAPSE)recoveryTicks=previous==OPEN||previous==ARCHIVE?12:9;
        else if(next!=REST)recoveryTicks=0;
        setInvulnerable(next==MANIFEST||next==TRANSITION||next==COLLAPSE);
        if(next==REST||next==COLLAPSE)motionBeat(MasterAnimationBeatPacket.CLEAR,0);
    }
    @Override public void aiStep(){
        super.aiStep();setDeltaMovement(Vec3.ZERO);
        if(!(level() instanceof ServerLevel l))return;
        if(retired||center==null||!BossConfig.curator().enabled()){discard();return;}
        setPos(center.getX()+.5,center.getY()+0.02,center.getZ()+.5);
        if(state==COLLAPSE){clock++;tickCollapseTimeline();if(clock>=120)discard();sync();return;}
        if(state!=MANIFEST)total++;if(total>tuning.timeoutSeconds()*20){tell("The reading is closed. Your evidence remains.");discard();return;}
        List<ServerPlayer> readers=active();
        for(UUID id:new ArrayList<>(participants)){
            ServerPlayer p=l.getServer().getPlayerList().getPlayer(id);
            if(p==null || !p.isAlive() || p.level()!=level() || p.isSpectator()){participants.remove(id);continue;}
            if(!readers.contains(p)){int n=absent.getOrDefault(id,0)+1;absent.put(id,n);if(n>=180)participants.remove(id);}else absent.remove(id);
        }
        for(ServerPlayer p:new ArrayList<>(bar.getPlayers()))if(!readers.contains(p))bar.removePlayer(p);
        for(ServerPlayer p:readers)bar.addPlayer(p);
        if(readers.isEmpty())empty++;else empty=0;
        if(empty>=180){discard();return;}
        tickBook(readers);
        // The body is the carrier. During a grimoire cast it deliberately stops hard-tracking,
        // allowing the book to acquire and follow the target independently.
        if(state!=MANIFEST && !readers.isEmpty() && (bookState==BOOK_CLOSED || state==REST)){ServerPlayer p=readers.get(0);getLookControl().setLookAt(p,8,8);float desired=(float)(Math.toDegrees(Math.atan2(p.getZ()-getZ(),p.getX()-getX()))-90);setYRot(approachAngle(getYRot(),desired,3.2f));setYBodyRot(getYRot());}
        if(total>sealUntil && state!=ARCHIVE){kept=0;broken=0;}
        if(state!=MANIFEST && state!=TRANSITION && state!=OPEN && CuratorPatterns.chapter(reserve.fraction())>chapter){
            chapter=2;hazards.clear();kept=broken=0;enter(TRANSITION);bookPattern=BOOK_NONE;if(bookState!=BOOK_CLOSED)setBookState(BOOK_CLOSING);sound("phase_transition",1);tell("REDACTION. The shelves will no longer hold still.");
        }
        if(!silasFinalPhaseTriggered && state!=MANIFEST && state!=COLLAPSE && reserve.fraction()<=.333f){
            silasFinalPhaseTriggered=true;hazards.clear();kept=broken=0;restForFinalMargin();
            tell("FINAL CATALOGUE // Enough borrowed testimony. The last page will admit one living reader.");sound("phase_transition",1);
            dev.marrowseal.wardbound.champion.SilasMasterEncounter.expelAtFinalPhase(l,position(),dev.marrowseal.wardbound.champion.SilasMasterEncounter.CURATOR,this::includes);
        }
        long now=l.getGameTime();hazards.removeIf(h->now-h.born>=h.warning+h.duration);
        if(hazards.stream().anyMatch(h->now-h.born==h.warning)){
            sound(state==SWEEP?"ash_sweep":"impact",.9f);
            float beat=state==ARCHIVE?1.35f:state==RINGS?1.08f:state==SWEEP?1.02f:.94f;
            motionBeat(MasterAnimationBeatPacket.RELEASE,beat+(chapter==2?.12f:0));
        }
        // One hit at most per player per ten ticks, even when multiple zones overlap.
        if(total%10==0)for(ServerPlayer p:readers){
            double x=p.getX()-getX(),z=p.getZ()-getZ();
            if(CuratorPatterns.refuge(x,z,kept|(state==ARCHIVE?1<<fallback:0)) || Math.abs(p.getY()-getY())>4)continue;
            for(Hazard h:hazards)if(CuratorPatterns.active(now-h.born,h.warning,h.duration) && CuratorPatterns.inside(h.shape,x-h.x,z-h.z,h.a,h.b)){
                if(p.hurt(damageSources().mobAttack(this),(float)((chapter==1?16:22)*tuning.damageMultiplier())))lastHit=total;
                break;
            }
        }
        if(state!=MANIFEST && state!=TRANSITION && state!=REST && state!=COLLAPSE && total%12==0){
            for(ServerPlayer p:readers){
                double dx=p.getX()-getX(), dz=p.getZ()-getZ();
                double dist2=dx*dx+dz*dz;
                if(dist2<=6.25 && Math.abs(p.getY()-getY())<3){
                    if(p.hurt(damageSources().indirectMagic(this,this),(float)((chapter==1?8:11)*tuning.damageMultiplier())))lastHit=total;
                }
            }
        }
        if(total<bookFlameUntil && total%4==0){
            Vec3 origin=bookMuzzleServer(),dir=bookDirectionServer();
            for(ServerPlayer p:readers){
                Vec3 to=p.position().add(0,p.getBbHeight()*.55,0).subtract(origin);double along=to.dot(dir);
                if(along<.4 || along>(chapter>=2?15.8:13.8))continue;
                double radial=Math.max(0,to.lengthSqr()-along*along),radius=1.0+along*.115;
                if(radial<=radius*radius && p.hurt(damageSources().indirectMagic(this,this),(float)((chapter==1?14:20)*tuning.damageMultiplier())))lastHit=total;
            }
        }
        playCuratorAnimationSounds();
        if(total%180==0)sound("ambient_presence",.35f);
        if(tuning.dialogue() && total%420==0)sound("whispers",.45f);
        switch(state){
            case MANIFEST -> {if(clock==45)sound("archive_opening",1);if(clock>=100)enter(REST);}
            case TRANSITION -> {if(clock==20 && !readers.isEmpty())beginBookCast(BOOK_NONE,readers);if(clock>=95)enter(REST);}
            case REST -> {if(recoveryTicks>0)recoveryTicks--;else if(clock>=Math.max(35,(int)((chapter==1?65:45)/tuning.attackSpeed())))startReading(readers);}
            case SWEEP,RINGS,ERRATA -> tickReading(readers);
            case ARCHIVE -> tickArchive(readers);
            case OPEN -> {if(total>=openUntil){hazards.clear();enter(REST);}}
            default -> {}
        }
        clock++;bar.setProgress(reserve.fraction());if(total%2==0)sync();
    }
    private void add(int shape,double x,double z,double a,double b,int delay,int life){
        if(hazards.size()>=96)return;
        hazards.add(new Hazard(++nextHazard,shape,x,z,a,b,level().getGameTime(),delay,life));
    }
    private void startReading(List<ServerPlayer> readers){
        if(readers.isEmpty())return;
        int readingIndex=turn++;
        int slot=Math.floorMod(readingIndex,4),cycle=Math.floorDiv(readingIndex,4);
        int next=switch(slot){case 0->SWEEP;case 1->ERRATA;case 2->RINGS;default->ARCHIVE;};
        variant=0;archiveDeadline=-1;
        // Use the completed four-reading cycle rather than the post-increment turn parity. The old
        // parity made STACKED SHELVES mathematically unreachable and made TWIN FOLIOS nearly fixed.
        if(next==SWEEP) variant = chapter>=2 && (cycle&1)==1 ? 1 : 0;
        else if(next==ERRATA) variant = chapter>=2 ? ((cycle%3)==0?1:0) : ((cycle%3)==2?1:0);
        else if(next==RINGS) variant = chapter>=2 && (cycle&1)==0 ? 1 : 0;
        else if(next==ARCHIVE) variant = chapter>=2 && (cycle&1)==1 ? 1 : 0;
        enter(next);motionBeat(MasterAnimationBeatPacket.ANTICIPATE,next==ARCHIVE?1.0f:.72f+(chapter==2?.10f:0));sound(switch(next){case SWEEP->"shelf_cast";case RINGS->"ring_cast";case ERRATA->"errata_mark";case ARCHIVE->"archive_opening";default->"attack_charge";},.9f);
        int bookCast=switch(next){case SWEEP->BOOK_SALVO;case ERRATA->BOOK_HOMING;case RINGS->BOOK_SPIRAL;case ARCHIVE->chapter>=2?BOOK_SPIRAL:BOOK_SALVO;default->BOOK_NONE;};
        beginBookCast(bookCast,readers);
        if(next==ARCHIVE){
            kept=broken=0;fallback=(turn/4)%4;Arrays.fill(sealCharge,0);sealUntil=total+300;healingBudget=0;
            archiveDeadline = variant==1 ? Math.max(110,indexDeadline()-20) : indexDeadline();
            tell(variant==1?"HOLLOW INDEX // The archive demands a harsher filing. Preserve or break quickly before the shelves ignite.":"FINAL INDEX: one last shelf remains lit. Crouch on seals to preserve more; use your writ to break an open seal.");
        }
        if(next==SWEEP){
            if(variant==0){
                tell("ASH SHELVES // The blank aisle survives.");
                for(int i=0;i<5;i++){double x=3.8+i*3.55;add(0,x,0,2.25,19.2,warning(54)+i*12,58);add(0,-x,0,2.25,19.2,warning(54)+i*12,58);}
                add(2,0,0,3.8,0,warning(46),52);
            } else {
                tell("STACKED SHELVES // The lit aisle now runs across the record, not through it.");
                for(int i=0;i<5;i++){double z=3.8+i*3.55;add(0,0,z,19.2,2.25,warning(52)+i*11,58);add(0,0,-z,19.2,2.25,warning(52)+i*11,58);}
                add(2,0,0,3.4,0,warning(42),52);
            }
        }
        if(next==RINGS){
            if(variant==0){tell("CINDER FOLIOS // Read the expanding edge.");for(int i=0;i<6;i++)add(1,0,0,3.6+i*2.75,1.25,warning(56)+i*13,22);add(2,0,0,3.4,0,warning(44),44);} else {
                tell("TWIN FOLIOS // Two catalogues overlap before the main volume closes.");
                add(1,-7.2,0,4.8,1.3,warning(52),22);add(1,7.2,0,4.8,1.3,warning(52)+7,22);add(1,0,0,13.8,1.35,warning(52)+16,28);add(2,0,0,3.4,0,warning(38),44);
            }
        }
        if(next==ERRATA)tell(variant==1?"MARGINALIA // The archive corrects where you stand, then where you flee.":"ERRATA // The pages follow, then fix their last address.");
    }
    private void tickReading(List<ServerPlayer> readers){
        if(state==ERRATA){
            boolean trigger = variant==0 ? (clock==1 || clock==36 || (chapter==2 && clock==72)) : (clock==1 || clock==24 || clock==48 || (chapter==2 && clock==72));
            if(trigger){
                for(ServerPlayer p:readers){double x=Math.max(-17.5,Math.min(17.5,p.getX()-getX())),z=Math.max(-17.5,Math.min(17.5,p.getZ()-getZ()));add(2,x,z,variant==1?3.2:3.7,0,warning(variant==1?38:44),chapter==2?(variant==1?104:118):(variant==1?64:74));}
                sound("paper_movement",.7f);
            }
        }
        if(chapter==2 && reserve.fraction()<.25 && clock==30 && state!=SWEEP){add(0,15,0,2.8,19.5,52,78);add(0,-15,0,2.8,19.5,52,78);add(0,0,15,19.5,2.6,58,72);add(0,0,-15,19.5,2.6,58,72);}
        if(chapter==2 && state==RINGS && variant==1 && clock==38){add(1,0,0,16.2,1.35,warning(36),22);add(2,0,0,3.7,0,warning(32),38);}
        if(clock>warning(60)+60 && hazards.isEmpty()){
            if(lastHit<total-clock){open(variant==1?84:70);tell(variant==1?"The archive found no witness in its margins. A wider binding opens.":"An address without a victim. The binding opens.");}else enter(REST);
        }
    }
    private void tickArchive(List<ServerPlayer> readers){
        int deadline=archiveDeadline>0?archiveDeadline:indexDeadline();
        for(int i=0;i<4;i++){
            final int slot=i;
            if(((kept|broken)&(1<<i))!=0)continue;
            boolean holding=readers.stream().anyMatch(p->p.isShiftKeyDown() && nearSeal(p,slot,2.4));
            sealCharge[i]=holding?sealCharge[i]+1:Math.max(0,sealCharge[i]-2);
            if(sealCharge[i]>=24 && clock<deadline){kept|=1<<i;sealUntil=total+220;sound("preserve",.8f);for(ServerPlayer p:readers)if(nearSeal(p,i,2.4)){contributors.add(p.getUUID());actor=p.getId();pulse++;}}
        }
        // Seals repair at most 2% per index. Destroying two interrupts the repair entirely.
        if(chapter==2 && clock<deadline && total%20==0 && healingBudget<.02f && Integer.bitCount(broken)<2){
            double heal=reserve.maximum()*.0025*(4-Integer.bitCount(kept|broken))/4;
            reserve.restore(reserve.current()+heal);healingBudget+=.0025f;
        }
        if(clock==deadline){
            if(Integer.bitCount(kept)>=2 || Integer.bitCount(broken)>=2){open(110);tell("Two entries denied. Strike the open book.");return;}
            tell("INCINERATION // Only preserved shelves and the last lit shelf remain safe.");sound("index_burn",1);
            // This has its own full warning even at maximum attack_speed.
            add(0,0,0,16,16,45,75);
        }
        if(clock>deadline+125){hazards.clear();enter(REST);}
    }
    private boolean nearSeal(ServerPlayer p,int i,double radius){double[] s=CuratorPatterns.SEALS[i];return Math.abs(p.getY()-getY())<3 && p.position().distanceToSqr(position().add(s[0],0,s[1]))<radius*radius;}
    public boolean breakSeal(ServerPlayer p){
        if(!participants.contains(p.getUUID()) || !active().contains(p) || state!=ARCHIVE || clock>=(archiveDeadline>0?archiveDeadline:indexDeadline()) || total<sealCooldown)return false;
        for(int i=0;i<4;i++)if(nearSeal(p,i,3) && ((kept|broken)&(1<<i))==0){broken|=1<<i;contributors.add(p.getUUID());sealCooldown=total+10;actor=p.getId();pulse++;sound("fracture",1);sync();return true;}
        return false;
    }
    private void open(int ticks){hazards.clear();enter(OPEN);openUntil=total+ticks;flareUntil=total+52;bookNextCast=total;beginBookCast(BOOK_FLAME,active());motionBeat(MasterAnimationBeatPacket.ANTICIPATE,1.05f+(chapter==2?.12f:0));sound("open_binding",1);sync();}
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)){if(!level().isClientSide)discard();return true;}
        ServerPlayer p=null;
        if(source.getEntity() instanceof ServerPlayer direct)p=direct;
        else if(source.getEntity() instanceof dev.marrowseal.wardbound.champion.DefeatedChampionEntity silas && silas.companionActive() && silas.companionOwnerId()!=null && level() instanceof ServerLevel sl)
            p=sl.getServer().getPlayerList().getPlayer(silas.companionOwnerId());
        if(level().isClientSide || state==MANIFEST || state==TRANSITION || state==COLLAPSE || p==null || !active().contains(p) || !Float.isFinite(amount))return false;
        float before=reserve.prepareNativeDamage(this);MasterNativeDamageBridge.arm(this);
        boolean hit=super.hurt(source,(float)Math.min(460,Math.min(tuning.hitCap(),amount*(state==OPEN?2.05:.80))));
        float actual=MasterNativeDamageBridge.consume(this,Math.max(0,before-getHealth()));
        if((hit||actual>0) && actual>0){
            contributors.add(p.getUUID());hurtAt=total;
            int reactionKind=state==OPEN?MasterDamageReactionPacket.VULNERABLE:(actual>=Math.max(30,tuning.hitCap()*.42)?MasterDamageReactionPacket.HEAVY:MasterDamageReactionPacket.LIGHT);
            if(state!=REST&&state!=OPEN&&(bookState==BOOK_CHARGE||bookState==BOOK_RELEASE))reactionKind=MasterDamageReactionPacket.LIGHT;
            float reactionStrength=reactionKind==MasterDamageReactionPacket.VULNERABLE?1.36f:reactionKind==MasterDamageReactionPacket.HEAVY?1.02f:.44f;
            damageReaction(reactionKind,reactionStrength,getId()*421+total*23+state);
            if(total%4==0)sound("hurt",.5f);if(reserve.damage(actual))collapse();else reserve.syncNativeHealth(this);sync();
        }
        return hit||actual>0;
    }
    private void collapse(){
        if(rewarded)return;rewarded=true;hazards.clear();pageShots.clear();kept=broken=0;enter(COLLAPSE);bookState=BOOK_DEATH;bookClock=0;bookPattern=BOOK_NONE;bookFlameUntil=0;if(level() instanceof ServerLevel sl&&center!=null)MasterArenaManager.cleanupCombatTransients(sl,center);bar.removeAllPlayers();sound("death",1.05f);
        for(ServerPlayer p:active())if(contributors.contains(p.getUUID()))CuratorProgression.victory(p,practice);
        sync();
    }
    /** Multi-beat archive shutdown: pages fail first, then flame, binding, impact and ash. */
    private void tickCollapseTimeline(){
        if(clock==10){sound("paper_movement",.78f);timelineSound(SoundEvents.BOOK_PAGE_TURN,.74f,1.18f);}
        if(clock==22){sound("paper_movement",.62f);timelineSound(SoundEvents.BOOK_PAGE_TURN,.66f,.78f);}
        if(clock==35){timelineSound(SoundEvents.FIRE_EXTINGUISH,.82f,.62f);timelineSound(SoundEvents.BLAZE_HURT,.22f,.52f);}
        if(clock==52){timelineSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM,.52f,.58f);timelineSound(SoundEvents.BOOK_PAGE_TURN,.58f,.48f);}
        if(clock==66){timelineSound(SoundEvents.ANVIL_LAND,.22f,.62f);timelineSound(SoundEvents.WOOD_HIT,.46f,.72f);}
        if(clock==82){sound("ash_sweep",.52f);timelineSound(SoundEvents.SOUL_ESCAPE,.38f,.48f);}
        if(clock==104)sound("victory",.82f);
    }
    @Override public void die(DamageSource source){if(!level().isClientSide)discard();}
    @Override public void remove(RemovalReason reason){hazards.clear();pageShots.clear();if((reason==RemovalReason.DISCARDED||reason==RemovalReason.KILLED)&&level() instanceof ServerLevel sl&&center!=null)MasterArenaManager.cleanupCombatTransients(sl,center);bar.removeAllPlayers();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CuratorCombatStateV2",true);
        if(center!=null)tag.putLong("CuratorCenter",center.asLong());
        tag.putBoolean("CuratorPractice",practice);tag.putBoolean("CuratorRewarded",rewarded);tag.putBoolean("CuratorSilasFinal",silasFinalPhaseTriggered);
        tag.putInt("CuratorState",state);tag.putInt("CuratorClock",clock);tag.putInt("CuratorChapter",chapter);tag.putInt("CuratorTurn",turn);tag.putInt("CuratorTotal",total);
        tag.putInt("CuratorEmpty",empty);tag.putInt("CuratorPulse",pulse);tag.putInt("CuratorHurtAt",hurtAt);tag.putInt("CuratorLastHit",lastHit);tag.putInt("CuratorVariant",variant);
        tag.putDouble("CuratorReserveMax",reserve.maximum());tag.putDouble("CuratorReserveCurrent",reserve.current());tag.putFloat("CuratorHealingBudget",healingBudget);
        ListTag ids=new ListTag();for(UUID id:participants){CompoundTag q=new CompoundTag();q.putUUID("id",id);ids.add(q);}tag.put("CuratorParticipants",ids);
        ListTag done=new ListTag();for(UUID id:contributors){CompoundTag q=new CompoundTag();q.putUUID("id",id);done.add(q);}tag.put("CuratorContributors",done);
        ListTag misses=new ListTag();for(var e:absent.entrySet()){CompoundTag q=new CompoundTag();q.putUUID("id",e.getKey());q.putInt("ticks",Math.max(0,e.getValue()));misses.add(q);}tag.put("CuratorAbsent",misses);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        // Legacy Curators deliberately retired on load because their attack geometry was not persisted.
        // Keep that behavior for old saves; V2 checkpoints can resume safely.
        if(!tag.getBoolean("CuratorCombatStateV2")){retired=true;return;}
        retired=false;tuning=BossConfig.curator();
        if(tag.contains("CuratorCenter"))center=BlockPos.of(tag.getLong("CuratorCenter"));
        practice=tag.getBoolean("CuratorPractice");rewarded=tag.getBoolean("CuratorRewarded");silasFinalPhaseTriggered=tag.getBoolean("CuratorSilasFinal");
        chapter=Math.max(1,Math.min(2,tag.getInt("CuratorChapter")));turn=Math.max(0,Math.min(1_000_000,tag.getInt("CuratorTurn")));total=Math.max(0,Math.min(tuning.timeoutSeconds()*20,tag.getInt("CuratorTotal")));
        empty=Math.max(0,Math.min(179,tag.getInt("CuratorEmpty")));pulse=Math.max(0,Math.min(1_000_000,tag.getInt("CuratorPulse")));hurtAt=Math.max(-1000,Math.min(total,tag.getInt("CuratorHurtAt")));lastHit=Math.max(-1000,Math.min(total,tag.getInt("CuratorLastHit")));variant=Math.max(0,Math.min(1,tag.getInt("CuratorVariant")));
        double max=tag.getDouble("CuratorReserveMax"),current=tag.getDouble("CuratorReserveCurrent");
        if(!Double.isFinite(max)||max<1)max=Math.max(1,tuning.health()+tuning.healthPerAlly()*Math.max(0,tag.getList("CuratorParticipants",Tag.TAG_COMPOUND).size()-1));
        reserve=new MasterVitality(max);reserve.restore(Double.isFinite(current)?current:max);reserve.bindMaximum(this);
        healingBudget=tag.getFloat("CuratorHealingBudget");if(!Float.isFinite(healingBudget))healingBudget=0;healingBudget=Math.max(0,Math.min(.02f,healingBudget));
        participants.clear();contributors.clear();absent.clear();
        ListTag ids=tag.getList("CuratorParticipants",Tag.TAG_COMPOUND);for(int i=0;i<ids.size()&&participants.size()<8;i++){CompoundTag q=ids.getCompound(i);if(q.hasUUID("id"))participants.add(q.getUUID("id"));}
        ListTag done=tag.getList("CuratorContributors",Tag.TAG_COMPOUND);for(int i=0;i<done.size();i++){CompoundTag q=done.getCompound(i);if(q.hasUUID("id"))contributors.add(q.getUUID("id"));}
        ListTag misses=tag.getList("CuratorAbsent",Tag.TAG_COMPOUND);for(int i=0;i<misses.size();i++){CompoundTag q=misses.getCompound(i);if(q.hasUUID("id"))absent.put(q.getUUID("id"),Math.max(0,Math.min(179,q.getInt("ticks"))));}
        hazards.clear();pageShots.clear();Arrays.fill(sealCharge,0);kept=broken=0;actor=-1;archiveDeadline=-1;sealUntil=sealCooldown=openUntil=flareUntil=0;nextHazard=0;fallback=0;recoveryTicks=0;
        bookState=BOOK_CLOSED;bookClock=0;bookPattern=BOOK_NONE;bookSerial=0;bookNextCast=total;bookFlameUntil=0;nextPageShot=0;bookTarget=null;bookAimYaw=getYRot();bookAimPitch=0;
        int savedState=Math.max(MANIFEST,Math.min(COLLAPSE,tag.getInt("CuratorState")));
        if(rewarded||savedState==COLLAPSE){rewarded=true;state=COLLAPSE;clock=Math.max(0,Math.min(119,tag.getInt("CuratorClock")));bookState=BOOK_DEATH;}
        else if(savedState==MANIFEST){state=MANIFEST;clock=Math.max(0,Math.min(99,tag.getInt("CuratorClock")));}
        else if(savedState==TRANSITION){state=TRANSITION;clock=Math.max(0,Math.min(94,tag.getInt("CuratorClock")));}
        else {state=REST;clock=0;}
        if(state==COLLAPSE||reserve.current()<=0)setHealth(1);else reserve.syncNativeHealth(this);setInvulnerable(state==MANIFEST||state==TRANSITION||state==COLLAPSE);
        if(center!=null)sync();
    }
    private void sync(){
        CompoundTag t=new CompoundTag();t.putUUID("encounter",encounter);t.putUUID("bar",bar.getId());t.putInt("state",state);t.putInt("clock",clock);t.putInt("recovery",recoveryTicks);t.putInt("chapter",chapter);t.putInt("kept",kept);t.putInt("broken",broken);t.putInt("pulse",pulse);t.putInt("actor",actor);t.putInt("hurt",total-hurtAt<8?1:0);t.putFloat("health",reserve.fraction());t.putInt("detail",tuning.particles());t.putLong("time",level().getGameTime());t.putIntArray("charge",sealCharge);
        t.putBoolean("theme",tuning.themeEnabled());t.putFloat("volume",(float)tuning.themeVolume());t.putInt("fadeIn",tuning.themeFadeInTicks());t.putInt("fadeOut",tuning.themeFadeOutTicks());
        t.putInt("fallback",fallback);t.putInt("deadline",archiveDeadline>0?archiveDeadline:indexDeadline());t.putInt("variant",variant);
        t.putInt("book_state",bookState);t.putInt("book_clock",bookClock);t.putInt("book_pattern",bookPattern);t.putFloat("book_aim_yaw",bookAimYaw);t.putFloat("book_aim_pitch",bookAimPitch);t.putBoolean("book_flame",total<bookFlameUntil);t.putInt("book_serial",bookSerial);
        ListTag shots=new ListTag();for(PageShot s:pageShots){CompoundTag q=new CompoundTag();q.putInt("id",s.id);q.putInt("mode",s.mode);q.putInt("age",s.age);q.putInt("life",s.life);q.putInt("burst",s.burst);q.putFloat("spin",s.spin);q.putDouble("x",s.pos.x-getX());q.putDouble("y",s.pos.y-getY());q.putDouble("z",s.pos.z-getZ());q.putDouble("vx",s.vel.x);q.putDouble("vy",s.vel.y);q.putDouble("vz",s.vel.z);shots.add(q);}t.put("book_shots",shots);
        ListTag h=new ListTag();for(Hazard a:hazards)h.add(a.tag());t.put("hazards",h);ListTag p=new ListTag();for(UUID id:participants)p.add(StringTag.valueOf(id.toString()));t.put("players",p);entityData.set(VIEW,t);
    }
    @Override public MasterVitality wardboundVitality(){return reserve;}
    @Override public boolean wardboundVitalityActive(){return !participants.isEmpty()&&center!=null;}
    @Override public boolean wardboundVitalityLocked(){return state==COLLAPSE;}
    @Override public void wardboundVitalityChanged(){bar.setProgress(reserve.fraction());}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"archivist",6,s->{
            CompoundTag v=view();int st=v.getInt("state"),ch=v.getInt("chapter"),cl=v.getInt("clock");
            int vr=v.getInt("variant");
            String animation=switch(st){
                case MANIFEST->"manifest_loom";
                case SWEEP->vr==1?"sweep_stacks":ch>=2?"sweep_redaction":"sweep";
                case RINGS->vr==1?"rings_indexed":ch>=2?"rings_redaction":"rings";
                case ERRATA->vr==1?"pages_marginalia":ch>=2?"pages_redaction":"pages";
                case ARCHIVE->cl>=v.getInt("deadline")?"incineration_roar":(vr==1&&ch>=2?"index_redaction":"index");
                case OPEN->ch>=2?"open_predatory":"open_lean";
                case TRANSITION->"transition_roar";
                case COLLAPSE->cl>=80?"release":"collapse";
                // Damage flinch is already layered non-destructively by MasterDamageReactions in
                // AshenCuratorRenderer. Do not swap the primary GeckoLib controller to the legacy
                // head-only `hurt` clip: bones absent from that clip can resolve toward the bind pose
                // during the transition and make the Curator's limbs/book rig fold through itself.
                default->v.getInt("recovery")>0?"recover":ch>=2?"idle_embers":"idle";
            };
            boolean hold=st==MANIFEST || st==OPEN || st==TRANSITION || st==COLLAPSE;
            RawAnimation raw=RawAnimation.begin();
            return s.setAndContinue(hold?raw.thenPlayAndHold("animation.ashen_curator."+animation):raw.thenLoop("animation.ashen_curator."+animation));
        }));
        c.add(new AnimationController<>(this,"living_grimoire",1,s->{
            int bs=view().getInt("book_state");String clip=switch(bs){
                case BOOK_OPENING->"grimoire_opening";
                case BOOK_CHARGE->"grimoire_charge";
                case BOOK_RELEASE->"grimoire_release";
                case BOOK_RECOIL->"grimoire_recoil";
                case BOOK_CLOSING->"grimoire_closing";
                case BOOK_DEATH->"grimoire_death";
                default->"grimoire_closed";
            };
            RawAnimation raw=RawAnimation.begin();
            boolean loop=bs==BOOK_CLOSED||bs==BOOK_CHARGE;
            return s.setAndContinue(loop?raw.thenLoop("animation.ashen_curator."+clip):raw.thenPlayAndHold("animation.ashen_curator."+clip));
        }));
    }

}
