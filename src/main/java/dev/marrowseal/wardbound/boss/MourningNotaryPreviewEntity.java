package dev.marrowseal.wardbound.boss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Explicit art-preview entity, not an unfinished combat encounter disguised as a boss. */
public final class MourningNotaryPreviewEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> POSE=SynchedEntityData.defineId(MourningNotaryPreviewEntity.class,EntityDataSerializers.STRING);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public MourningNotaryPreviewEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setPersistenceRequired();setNoGravity(true);setCustomNameVisible(false);}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(POSE,"idle");}
    public void pose(String name){entityData.set(POSE,name);}
    @Override public void aiStep(){super.aiStep();setDeltaMovement(Vec3.ZERO);}
    @Override public boolean hurt(DamageSource s,float amount){if(s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)){discard();return true;}return false;}
    @Override public boolean removeWhenFarAway(double d){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("NotaryPreviewPose",entityData.get(POSE));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);String pose=tag.getString("NotaryPreviewPose");entityData.set(POSE,NotaryVisualCommands.POSES.contains(pose)?pose:"idle");}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"notary",5,s->{
        String pose=entityData.get(POSE);boolean hold=pose.equals("manifest")||pose.equals("unbound")||pose.equals("collapse")||pose.equals("hurt");
        String clip="animation.mourning_notary."+pose;RawAnimation a=RawAnimation.begin();return s.setAndContinue(hold?a.thenPlayAndHold(clip):a.thenLoop(clip));
    }));}
}
