package dev.marrowseal.wardbound.nhalsul;

import dev.marrowseal.wardbound.WardAdvancements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class NhalSulItem extends SwordItem implements GeoItem {
    private static final String AWAKENED="NhalSulAwakened";
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);

    /** Nhal-Sûl is a relic, not a consumable tool: no durability lifecycle/bar. */
    private static final Tier NHAL_TIER=new Tier(){
        @Override public int getUses(){return 0;}
        @Override public float getSpeed(){return Tiers.NETHERITE.getSpeed();}
        @Override public float getAttackDamageBonus(){return 4.0f;}
        @Override public int getLevel(){return Tiers.NETHERITE.getLevel();}
        @Override public int getEnchantmentValue(){return Tiers.NETHERITE.getEnchantmentValue();}
        @Override public TagKey<Block> getTag(){return Tiers.NETHERITE.getTag();}
        @Override public Ingredient getRepairIngredient(){return Tiers.NETHERITE.getRepairIngredient();}
    };

    // Relic tier contributes 4, this item contributes 35 and the player base contributes 1 = 40 total attack damage.
    public NhalSulItem(Properties p){super(NHAL_TIER,35,-2.6f,p);SingletonGeoAnimatable.registerSyncedAnimatable(this);}
    public static boolean awakened(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean(AWAKENED);}

    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        super.inventoryTick(stack,level,entity,slot,selected);
    }

    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
        return super.hurtEnemy(stack,target,attacker);
    }

    /**
     * Unwritten Cut is intentionally immediate. A click always produces the slash even if there is
     * no target in front of the player; solid blocks merely shorten the visible/damaging corridor.
     */
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(stack);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        if(level instanceof ServerLevel server && player instanceof ServerPlayer p)castUnwrittenCut(server,p,stack);
        player.swing(hand,true);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }

    private void castUnwrittenCut(ServerLevel server,ServerPlayer p,ItemStack stack){
        boolean first=!awakened(stack);
        stack.getOrCreateTag().putBoolean(AWAKENED,true);
        p.getCooldowns().addCooldown(this,NhalSulRules.COOLDOWN);

        Vec3 start=p.getEyePosition();
        Vec3 direction=p.getLookAngle().normalize();
        Vec3 end=start.add(direction.scale(NhalSulRules.RANGE));
        HitResult wall=server.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double range=wall.getType()==HitResult.Type.MISS?NhalSulRules.RANGE:Math.max(.35,wall.getLocation().distanceTo(start)-.05);
        Vec3 stop=start.add(direction.scale(range));

        List<Mob> targets=server.getEntitiesOfClass(Mob.class,new AABB(start,stop).inflate(NhalSulRules.WIDTH),m->
                m.isAlive() && !m.isAlliedTo(p) && !(m instanceof TamableAnimal t && t.isTame()) &&
                        (m instanceof Enemy || m.getType().getCategory()== MobCategory.MONSTER || m.getTarget()==p));
        targets.sort(Comparator.comparingDouble(m->m.distanceToSqr(p)));
        int struck=0;
        for(Mob target:targets){
            Vec3 point=target.getBoundingBox().getCenter();
            Vec3 offset=point.subtract(start);
            double along=offset.dot(direction);
            double side=offset.lengthSqr()-along*along;
            if(along>range||!NhalSulRules.inCorridor(along,Math.max(0,side)))continue;
            HitResult sight=server.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(sight.getType()!=HitResult.Type.MISS)continue;
            if(target.hurt(server.damageSources().playerAttack(p),NhalSulRules.DAMAGE)){
                target.knockback(.72,-direction.x,-direction.z);
                if(++struck>=NhalSulRules.MAX_TARGETS)break;
            }
        }

        triggerAnim(p,GeoItem.getOrAssignId(stack,server),"blade","release");
        server.playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1.25f,.58f);
        server.playSound(null,p.blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.PLAYERS,.72f,1.48f);
        send(p,new NhalSulFxPacket(start.x,start.y,start.z,direction.x,direction.y,direction.z,(float)range,first?2:1));
        p.getInventory().setChanged();
        if(first) {
            WardAdvancements.nhalSulAwakened(p);
            p.sendSystemMessage(Component.translatable("nhal_sul.awakened").withStyle(ChatFormatting.DARK_AQUA,ChatFormatting.ITALIC));
        }
    }

    private static void send(ServerPlayer p,NhalSulFxPacket packet){
        NhalSulModule.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.getX(),p.getY(),p.getZ(),48,p.level().dimension())),packet);
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){
        return slotChanged||oldStack.getItem()!=newStack.getItem();
    }

    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("nhal_sul.history").withStyle(ChatFormatting.GRAY));
        if(awakened(stack))lines.add(Component.translatable("nhal_sul.skill").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"blade",2,s->s.setAndContinue(RawAnimation.begin().thenLoop("animation.nhal_sul.idle")))
                .triggerableAnim("charge",RawAnimation.begin().thenPlayAndHold("animation.nhal_sul.charge"))
                .triggerableAnim("settle",RawAnimation.begin().thenPlay("animation.nhal_sul.settle"))
                .triggerableAnim("release",RawAnimation.begin().thenPlay("animation.nhal_sul.release")));
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(new IClientItemExtensions(){
        private BlockEntityWithoutLevelRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new NhalSulRenderer();return renderer;}
    });}
}
