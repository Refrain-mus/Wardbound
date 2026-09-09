package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;
import dev.marrowseal.wardbound.CthulhuFreeze;
import dev.marrowseal.wardbound.CuriosCompat;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.client.geo.CthulhuIdolRenderer;
import dev.marrowseal.wardbound.net.CthulhuFieldPacket;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Reward unique to Cthulhu's Game. */
public class CthulhuIdolItem extends Item implements GeoItem {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cthulhu_idol.idle");
    private static final RawAnimation INVOKE = RawAnimation.begin().thenPlay("animation.cthulhu_idol.invoke");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public CthulhuIdolItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idol", 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim("invoke", INVOKE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new CthulhuIdolRenderer();
                return renderer;
            }
        });
    }

    /**
     * Off deliberately. On a normal flat item the glint is a small sheen over a
     * 16x16 icon, but GeckoLib routes GeoItem rendering through
     * {@code ItemRenderer.getFoilBufferDirect(..., stack.hasFoil())}, so a true
     * here lays the animated glint texture over every face of the model. On a
     * piece this size it washes the stone lavender and speckles the bronze,
     * which is what it was doing before.
     *
     * <p>The idol already reads as charged without it: the emissive eye, and
     * the sculk-soul and dust particles in {@link #inventoryTick}. Return true
     * again if the glint is wanted back.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide && entity instanceof Player player) {
            boolean activeHand = isSelected || player.getOffhandItem() == stack;
            if (activeHand && level.getGameTime() % 4 == 0) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 0.6;
                double y = player.getY() + 1.0 + level.random.nextDouble() * 0.5;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 0.6;
                level.addParticle(ParticleTypes.SCULK_SOUL, x, y, z,
                        (level.random.nextDouble() - 0.5) * 0.02, 0.012,
                        (level.random.nextDouble() - 0.5) * 0.02);
                level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.94f, 0.88f), 1.0f),
                        x, y, z, 0.0, 0.008, 0.0);
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide) return false;
        if (level.getGameTime() % 3 == 0) {
            double x = entity.getX();
            double y = entity.getY() + 0.08;
            double z = entity.getZ();
            for (int i = 0; i < 3; i++) {
                double ang = level.getGameTime() * 0.14 + i * (Math.PI * 2.0 / 3.0);
                level.addParticle(new DustParticleOptions(new Vector3f(0.52f, 0.94f, 0.86f), 0.9f),
                        x + Math.cos(ang) * 0.30, y + 0.04, z + Math.sin(ang) * 0.30,
                        0.0, 0.006, 0.0);
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        return activate(serverPlayer, stack)
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }

    /**
     * Dedicated keybind path. Prefer an equipped Curios Idol, then fall back to
     * a held copy so the key remains useful even without Curios installed.
     */
    public static boolean tryUseFromHandsOrCurios(ServerPlayer player) {
        ItemStack equipped = CuriosCompat.findFirstEquipped(player, WardItems.CTHULHU_IDOL.get());
        if (!equipped.isEmpty() && equipped.getItem() instanceof CthulhuIdolItem) {
            return activate(player, equipped);
        }
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CthulhuIdolItem) return activate(player, main);
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof CthulhuIdolItem && activate(player, off);
    }

    private static void triggerInvoke(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || !(player.level() instanceof ServerLevel level)) return;
        if (!(stack.getItem() instanceof CthulhuIdolItem idol)) return;
        idol.triggerAnim(player, GeoItem.getOrAssignId(stack, level), "idol", "invoke");
    }

    private static boolean activate(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        if (!(stack.getItem() instanceof CthulhuIdolItem item)) return false;
        if (player.getCooldowns().isOnCooldown(item)) return false;
        triggerInvoke(player, stack);

        // The field is a real 9x9 patch in front of the player, independent of
        // pitch. Using getLookAngle() shrank the horizontal offset when looking
        // up/down, which made the square slide back under the player.
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        Vec3 center = new Vec3(player.getX() + forwardX * 3.0,
                player.getY() + player.getBbHeight() * 0.45,
                player.getZ() + forwardZ * 3.0);
        AABB area = new AABB(center.x - 4.5, player.getY() - 1.0, center.z - 4.5,
                center.x + 4.5, player.getY() + 4.5, center.z + 4.5);

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && e instanceof Mob);

        int freezeTicks = Math.max(10, Math.round(WardConfig.cthulhuIdolFreezeSeconds * 20f));
        // The arrest itself is absolute, but the Idol should leave something behind when time resumes.
        // Default 4s freeze therefore becomes ~8s of Weakness III + Mining Fatigue IV total.
        int aftershockTicks = Math.max(40, freezeTicks);
        int debuffTicks = freezeTicks + aftershockTicks;
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, freezeTicks, 255,
                    false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, debuffTicks, 3,
                    false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, debuffTicks, 2,
                    false, true, true));
            target.setDeltaMovement(Vec3.ZERO);
            if (target instanceof Mob mob) CthulhuFreeze.apply(mob, freezeTicks);
            dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, target.position().add(0, target.getBbHeight() * 0.55, 0), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.CTHULHU_TARGET, 0.8f);
        }

        // Always fire, even with no target: the field itself is the action.
        dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new Vec3(player.getX(), player.getY() + 1.0, player.getZ()), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.CTHULHU_FIELD, 1.0f);
        Wardbound.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, player.getY() + 0.02, center.z, 48.0, serverLevel.dimension())),
                new CthulhuFieldPacket(center.x, player.getY() + 0.02, center.z, 4.5f, freezeTicks));
        dev.marrowseal.wardbound.fx.WardLodestoneDispatch.emit(serverLevel, new Vec3(center.x, player.getY() + 1.0, center.z), dev.marrowseal.wardbound.net.WardLodestoneFxPacket.CTHULHU_FIELD, 1.2f);

        serverLevel.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 0.8f, 0.55f);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.85f, 0.62f);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT,
                SoundSource.PLAYERS, 0.55f, 0.72f);

        if (targets.isEmpty()) {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The idol opens its gaze. The air locks in place.")
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (targets.size() == 1) {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The idol arrests one thing in its field.")
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            dev.marrowseal.wardbound.WardHud.message(player, Component.literal("The idol arrests " + targets.size() + " things in its field.")
                    .withStyle(ChatFormatting.AQUA), true);
        }

        player.getCooldowns().addCooldown(item, WardConfig.cthulhuIdolCooldownSeconds * 20);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wardbound.cthulhu_idol.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("wardbound.tooltip.cthulhu_idol_cooldown",
                        WardConfig.cthulhuIdolCooldownSeconds)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wardbound.tooltip.cthulhu_idol_fx")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
