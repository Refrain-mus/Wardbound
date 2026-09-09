package dev.marrowseal.wardbound.ancientsmith;
import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.WardAdvancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
public final class AncientSmithEntity extends PathfinderMob implements GeoEntity {
 private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.INT),CLOCK=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<ItemStack> SWORD=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.ITEM_STACK);
 private static final EntityDataAccessor<Float> OX=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.FLOAT),OY=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.FLOAT),OZ=SynchedEntityData.defineId(AncientSmithEntity.class,EntityDataSerializers.FLOAT);
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);private int returnTicks,workTicks,previewTicks;
 public AncientSmithEntity(EntityType<? extends PathfinderMob> t,Level l){super(t,l);setPersistenceRequired();setNoGravity(true);xpReward=0;}
 public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
 @Override protected void registerGoals(){} // No targets, combat AI, random wandering, or boss bar.
 @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(STATE,SmithTimeline.State.IDLE_HEAVY.ordinal());entityData.define(CLOCK,-1);entityData.define(SWORD,ItemStack.EMPTY);entityData.define(OX,0f);entityData.define(OY,0f);entityData.define(OZ,0f);}
 public SmithTimeline.State state(){return SmithTimeline.State.values()[Math.floorMod(entityData.get(STATE),SmithTimeline.State.values().length)];}
 public int sceneTick(){return entityData.get(CLOCK);}public ItemStack sceneSword(){return entityData.get(SWORD);}
 public Vec3 local(double x,double y,double z){return position().add(new Vec3(x,y,z).yRot((float)Math.toRadians(180-getYRot())));}
 public Vec3 strikePoint(){return local(0,3.5625,-5.125);}
 public Vec3 offerPoint(){return position().add(entityData.get(OX),entityData.get(OY),entityData.get(OZ));}
 void scene(int tick,ItemStack sword,Vec3 offer){entityData.set(CLOCK,tick);entityData.set(STATE,SmithTimeline.at(tick).state().ordinal());if(sceneSword().isEmpty())entityData.set(SWORD,sword.copy());Vec3 d=offer.subtract(position());entityData.set(OX,(float)d.x);entityData.set(OY,(float)d.y);entityData.set(OZ,(float)d.z);}
 void clearScene(){entityData.set(CLOCK,-1);entityData.set(SWORD,ItemStack.EMPTY);preview(SmithTimeline.State.DISMISS_PLAYER,40);}
 public void preview(SmithTimeline.State state,int duration){if(sceneTick()>=0)return;entityData.set(STATE,state.ordinal());returnTicks=duration;previewTicks=0;}
 @Override public void tick(){
  super.tick();setDeltaMovement(Vec3.ZERO);setYBodyRot(getYRot());setYHeadRot(getYRot());
  if(level().isClientSide||sceneTick()>=0)return;
  previewTicks++;
  if(state()==SmithTimeline.State.FORGE_IDLE){if(++workTicks%96==67)SmithVfx.emit(this,SmithVfx.STRIKE);}else workTicks=0;
  if(state()==SmithTimeline.State.HAMMER_STRIKE&&previewTicks==22)SmithVfx.emit(this,SmithVfx.STRIKE);
  if(state()==SmithTimeline.State.HAMMER_STRIKE_HEAVY&&previewTicks==56)SmithVfx.emit(this,SmithVfx.HEAVY);
  if(state()==SmithTimeline.State.SINGLE_FINAL_STRIKE){if(previewTicks==48)SmithVfx.emit(this,SmithVfx.FINAL);if(previewTicks==50)SmithVfx.emit(this,SmithVfx.SILENCE);}
  if(returnTicks>0&&--returnTicks==0){
   if(state()==SmithTimeline.State.DISMISS_PLAYER)preview(SmithTimeline.State.RESUME_FORGING,50);
   else preview(SmithTimeline.State.FORGE_IDLE,0);
  }
 }
 @Override protected InteractionResult mobInteract(Player p,InteractionHand h){if(h!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(p instanceof ServerPlayer sp){WardAdvancements.ancientSmithSeen(sp);if(!AncientSmithHooks.claim(sp)&&sceneTick()<0){WardHud.send(sp,net.minecraft.network.chat.Component.translatable(p.isShiftKeyDown()?"ancient_smith.dialogue.masters":"ancient_smith.dialogue.endures").getString(),WardHud.Mood.WARD,1600);preview(SmithTimeline.State.TALK_SHORT,30);}}return InteractionResult.sidedSuccess(level().isClientSide);}
 @Override public boolean hurt(DamageSource s,float a){return s.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)&&super.hurt(s,a);}@Override public boolean isPushable(){return false;}@Override public boolean canBeLeashed(Player p){return false;}@Override public boolean removeWhenFarAway(double d){return false;}@Override public boolean canChangeDimensions(){return false;}
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);entityData.set(CLOCK,-1);entityData.set(SWORD,ItemStack.EMPTY);entityData.set(STATE,SmithTimeline.State.IDLE_HEAVY.ordinal());}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar r){r.add(new AnimationController<>(this,"body",3,s->{var a=state();String clip=a==SmithTimeline.State.TALK_SHORT&&sceneTick()>=SmithTimeline.OFFER?"talk_offer":a.clip;return s.setAndContinue(a.loop?RawAnimation.begin().thenLoop("animation.ancient_smith."+clip):RawAnimation.begin().thenPlayAndHold("animation.ancient_smith."+clip));}));}
 @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
