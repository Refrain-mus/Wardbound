package dev.marrowseal.wardbound.ancientsmith;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
/** Placeable scene prop, not an inventory or a replacement for vanilla anvils. */
public final class AncientAnvilEntity extends Entity implements GeoEntity {
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
 public AncientAnvilEntity(EntityType<? extends AncientAnvilEntity> t,Level l){super(t,l);setNoGravity(true);}
 @Override protected void defineSynchedData(){}@Override protected void readAdditionalSaveData(CompoundTag t){}@Override protected void addAdditionalSaveData(CompoundTag t){}
 @Override public boolean canBeCollidedWith(){return true;}@Override public boolean isPickable(){return true;}
 @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar r){}@Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
