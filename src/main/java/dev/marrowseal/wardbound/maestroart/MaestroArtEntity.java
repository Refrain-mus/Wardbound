package dev.marrowseal.wardbound.maestroart;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Art/performance entity. No damage, progression, arena or natural spawning. */
public class MaestroArtEntity extends PathfinderMob implements GeoEntity {
    private static final int BLEND_TICKS=6;
    private static final EntityDataAccessor<String> CLIP=SynchedEntityData.defineId(MaestroArtEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> REVISION=SynchedEntityData.defineId(MaestroArtEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(MaestroArtEntity.class,EntityDataSerializers.LONG);
    private static final List<String> SHOWCASE=List.of("intro","idle_composed","idle_observing","glide_step","pivot_turn","conduct_start","conduct_loop","conduct_strike","recovery_cast","left_hand_cast","right_hand_cast","dual_hand_cast","command_gesture","summoning_gesture","shockwave_release","note_barrage","wave_attack","slam_attack","recovery_heavy","dash_lunge","recovery_step","phase_transition","idle_rage","grand_crescendo","baton_flourish","accusation","counter_guard","counter_riposte","staccato_combo","legato_sweep","orchestra_summon","silence_field","levitation_start","levitation_loop","levitation_end","curtain_call","silence_gesture","formal_bow","piano_start","piano_loop","piano_end","stagger","death_start","death_fall","death_end");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private int remaining=150,seenRevision=-1,showcase=-1;
    private int visualClipStart;
    public float visualPerformanceAge(float partial){return Math.max(0,tickCount-visualClipStart+partial);}
    public MaestroArtEntity(EntityType<? extends PathfinderMob> type,Level level){
        super(type,level);setPersistenceRequired();setNoGravity(true);setCustomName(Component.literal("Maestro"));setCustomNameVisible(false);
    }
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(CLIP,"intro");entityData.define(REVISION,0);entityData.define(START,0L);}
    public int performanceRevision(){return entityData.get(REVISION);}
    public String clip(){return entityData.get(CLIP);}
    public float performanceAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
    public boolean playClip(String name){
        if(level().isClientSide||!MaestroRig.CLIPS.contains(name))return false;
        showcase=-1;startClip(name);return true;
    }
    private void startClip(String name){
        entityData.set(CLIP,name);entityData.set(REVISION,entityData.get(REVISION)+1);entityData.set(START,level().getGameTime());
        remaining=MaestroRig.TICKS[MaestroRig.CLIPS.indexOf(name)]+BLEND_TICKS;
    }
    public void beginShowcase(){if(level().isClientSide)return;showcase=0;startClip(SHOWCASE.get(0));}
    protected boolean previewMotionLock(){return true;}
    protected void serverPerformanceTick(){}
    @Override public void aiStep(){
        super.aiStep();
        if(previewMotionLock()){setDeltaMovement(Vec3.ZERO);setNoGravity(true);}
        if(level().isClientSide)return;
        if(tickCount==1&&entityData.get(START)==0L)entityData.set(START,level().getGameTime());
        if((showcase>=0||!MaestroRig.LOOPS.contains(clip()))&&remaining>0&&--remaining==0){
            if(showcase>=0){if(++showcase<SHOWCASE.size())startClip(SHOWCASE.get(showcase));else showcase=-1;}
            else if(!clip().equals("death_end"))startClip(MaestroRig.AFTER.getOrDefault(clip(),"idle_composed"));
        }
        serverPerformanceTick();
    }
    protected boolean hurtThroughMob(DamageSource source,float amount){return super.hurt(source,amount);}
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)){discard();return true;}
        if(!level().isClientSide&&showcase<0&&clip().startsWith("idle_"))playClip("stagger");
        return false;
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(3);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("MaestroArtClip",clip());}
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);String name=tag.getString("MaestroArtClip");
        // Controllers are recreated on reload, so restart the saved performance from its beginning.
        playClip(MaestroRig.CLIPS.contains(name)?name:"idle_composed");
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar){
        registrar.add(new AnimationController<>(this,"maestro_performance",BLEND_TICKS,state->{
            int revision=entityData.get(REVISION);
            if(seenRevision!=revision){state.getController().forceAnimationReset();seenRevision=revision;visualClipStart=tickCount;}
            String name=clip(),id="animation.maestro_performer."+name;RawAnimation animation=RawAnimation.begin();
            return state.setAndContinue(MaestroRig.LOOPS.contains(name)?animation.thenLoop(id):animation.thenPlayAndHold(id));
        }));
    }
}
