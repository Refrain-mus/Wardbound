package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.marrowseal.wardbound.item.WardItems;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/** Active post-Master relic logic. Intentionally separate from the ordinary card system. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID)
public final class MasterRewardEffects {
 private static final String MARGIN="WardboundLastMargin",VERDICT="WardboundUnsignedVerdict";
 private MasterRewardEffects(){}

 public static boolean openMargin(ServerPlayer p){
  if(p==null||p.getServer()==null)return false;long now=p.serverLevel().getGameTime();CompoundTag t=p.getPersistentData().getCompound(MARGIN);t.putLong("Until",now+20L*6L);t.remove("Release");t.remove("Stored");p.getPersistentData().put(MARGIN,t);fx(p.serverLevel(),p.position().add(0,1,0),WardLodestoneFxPacket.REWARD_LAST_MARGIN,.95f);WardHud.send(p,"THE LAST MARGIN // Six seconds remain blank. The next wound may be edited.",WardHud.Mood.REWARD,2200);return true;
 }

 @SubscribeEvent public static void hurt(LivingHurtEvent e){
  if(!(e.getEntity() instanceof ServerPlayer p)||p.getServer()==null||e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return;
  CompoundTag root=p.getPersistentData();if(!root.contains(MARGIN))return;CompoundTag t=root.getCompound(MARGIN);long now=p.serverLevel().getGameTime();if(t.getLong("Until")<now||e.getAmount()<4f)return;
  float original=e.getAmount();float reduced=Math.min(original*.15f,Math.max(0f,p.getHealth()-1f));float stored=Math.max(0,original-reduced);e.setAmount(reduced);t.putLong("Until",0);t.putLong("Release",now+30);t.putFloat("Stored",Math.min(60f,stored));t.putString("Dimension",p.level().dimension().location().toString());t.putDouble("OriginX",p.getX());t.putDouble("OriginY",p.getY());t.putDouble("OriginZ",p.getZ());root.put(MARGIN,t);fx(p.serverLevel(),p.position().add(0,1,0),WardLodestoneFxPacket.REWARD_LAST_MARGIN,1.25f);WardHud.send(p,"THE LAST MARGIN // The wound has been archived. A rebuttal is being prepared.",WardHud.Mood.WARD,2000);
 }

 public static boolean fileVerdict(ServerPlayer p){
  LivingEntity target=lookTarget(p,30,.94);if(target==null)return false;CompoundTag t=new CompoundTag();t.putUUID("Target",target.getUUID());t.putString("Dimension",p.level().dimension().location().toString());t.putLong("Due",p.serverLevel().getGameTime()+60);p.getPersistentData().put(VERDICT,t);fx(p.serverLevel(),target.position().add(0,target.getBbHeight()*.65,0),WardLodestoneFxPacket.REWARD_UNSIGNED_VERDICT,1.0f);p.serverLevel().playSound(null,target.blockPosition(),SoundEvents.BELL_RESONATE,SoundSource.PLAYERS,.75f,.68f);WardHud.send(p,"UNSIGNED VERDICT // A sentence has been filed without a signature.",WardHud.Mood.REWARD,1900);return true;
 }

 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.getServer()==null||!p.isAlive())return;long now=p.serverLevel().getGameTime();CompoundTag root=p.getPersistentData();
  if(root.contains(MARGIN)){CompoundTag t=root.getCompound(MARGIN);long due=t.getLong("Release");if(due>0&&now>=due){float stored=t.getFloat("Stored");CompoundTag release=t.copy();t.remove("Release");t.remove("Stored");root.put(MARGIN,t);releaseMargin(p,release,stored);}}
  if(root.contains(VERDICT)){CompoundTag t=root.getCompound(VERDICT);if(t.getLong("Due")>0&&now>=t.getLong("Due")){root.remove(VERDICT);if(!resolveVerdict(p,t))refundVerdict(p);}}
 }

 private static void releaseMargin(ServerPlayer p,CompoundTag t,float stored){
  ResourceLocation id=ResourceLocation.tryParse(t.getString("Dimension"));ServerLevel l=id==null?null:p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));if(l==null)return;double x=t.getDouble("OriginX"),y=t.getDouble("OriginY"),z=t.getDouble("OriginZ");if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;Vec3 origin=new Vec3(x,y,z);float damage=Math.min(110f,20f+stored*1.5f);AABB box=new AABB(x-8,y-8,z-8,x+8,y+8,z+8);for(LivingEntity target:l.getEntitiesOfClass(LivingEntity.class,box,q->valid(p,q)))target.hurt(p.damageSources().playerAttack(p),damage);fx(l,origin.add(0,.2,0),WardLodestoneFxPacket.REWARD_LAST_MARGIN,1.65f);l.playSound(null,BlockPos.containing(origin),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.PLAYERS,.75f,.58f);WardHud.send(p,"ASH REBUTTAL // The archived wound has been returned to the place where it was written.",WardHud.Mood.REWARD,1800);
 }
 private static boolean resolveVerdict(ServerPlayer p,CompoundTag t){
  if(!t.hasUUID("Target")||!p.level().dimension().location().toString().equals(t.getString("Dimension")))return false;Entity raw=p.serverLevel().getEntity(t.getUUID("Target"));if(!(raw instanceof LivingEntity target)||!valid(p,target)||p.distanceToSqr(target)>40*40)return false;LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(p.serverLevel());if(bolt!=null){bolt.moveTo(target.getX(),target.getY(),target.getZ());bolt.setCause(p);bolt.setVisualOnly(true);p.serverLevel().addFreshEntity(bolt);}float damage=Math.min(140f,28f+target.getMaxHealth()*.18f);target.hurt(p.damageSources().playerAttack(p),damage);fx(p.serverLevel(),target.position().add(0,target.getBbHeight()*.45,0),WardLodestoneFxPacket.REWARD_UNSIGNED_VERDICT,1.75f);p.serverLevel().playSound(null,target.blockPosition(),SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.PLAYERS,.6f,.8f);WardHud.send(p,"UNSIGNED VERDICT // Judgment entered. Signature unnecessary.",WardHud.Mood.REWARD,1800);return true;
 }
 private static void refundVerdict(ServerPlayer p){p.getCooldowns().addCooldown(WardItems.UNSIGNED_VERDICT.get(),UnsignedVerdictItem.COOLDOWN/2);WardHud.send(p,"UNSIGNED VERDICT // The filed witness left the record. Half the waiting term remains.",WardHud.Mood.WARD,1900);}
 private static boolean valid(ServerPlayer p,LivingEntity e){return e!=null&&e.isAlive()&&e!=p&&!(e instanceof Player)&&!e.isAlliedTo(p);}
 private static LivingEntity lookTarget(ServerPlayer p,double range,double dot){Vec3 eye=p.getEyePosition(),look=p.getLookAngle().normalize();List<LivingEntity> list=p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),e->valid(p,e));list.removeIf(e->{Vec3 v=e.getBoundingBox().getCenter().subtract(eye);return v.lengthSqr()>range*range||v.normalize().dot(look)<dot;});list.sort(Comparator.comparingDouble(e->e.getBoundingBox().getCenter().distanceToSqr(eye)));return list.isEmpty()?null:list.get(0);}
 private static void fx(ServerLevel l,Vec3 pos,int kind,float scale){Wardbound.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(pos.x,pos.y,pos.z,64,l.dimension())),new WardLodestoneFxPacket(pos.x,pos.y,pos.z,kind,scale));}
}
