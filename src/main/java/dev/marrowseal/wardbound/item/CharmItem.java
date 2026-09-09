package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import dev.marrowseal.wardbound.client.geo.SealwrightKeyRenderer;
import dev.marrowseal.wardbound.client.geo.WardKeyRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;
import dev.marrowseal.wardbound.WardSounds;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Shared behaviour for both charms: a tooltip that states the trade plainly, and
 * for the heart, the small piece of ambience that makes carrying it feel like a
 * decision rather than an inventory slot.
 */
public class CharmItem extends Item implements GeoItem {

    public final Charm charm;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public CharmItem(Properties properties, Charm charm) {
        super(properties);
        this.charm = charm;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Ward Key and Sealwright's Key are static GeckoLib models. Ichorheart
        // overrides this method and retains its original heartbeat animation.
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
                if (renderer == null) {
                    renderer = charm == Charm.SEALWRIGHT
                            ? new SealwrightKeyRenderer()
                            : new WardKeyRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        int left = stack.getMaxDamage() - stack.getDamageValue();

        if (charm == Charm.ICHORHEART) {
            tooltip.add(Component.translatable("item.wardbound.ichorheart.desc")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable("item.wardbound.ward_key.desc")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        tooltip.add(Component.translatable("wardbound.tooltip.lives", charm.bonusLives())
                .withStyle(ChatFormatting.GRAY));
        if (charm.lootBonus() > 1.001f) {
            tooltip.add(Component.translatable("wardbound.tooltip.loot",
                            Math.round((charm.lootBonus() - 1f) * 100f))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("wardbound.tooltip.uses", left, stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The heart beats in your bag. Roughly once every eight seconds, quietly,
     * and only when it still has a use left in it: a spent one has nothing left
     * to say. The interval is offset by the slot so two hearts never sync up.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (charm != Charm.ICHORHEART || level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (stack.getDamageValue() >= stack.getMaxDamage()) return;

        long tick = level.getGameTime() + slot * 37L;
        if (tick % 160L != 0L) return;

        level.playSound(null, player.blockPosition(), WardSounds.HEARTBEAT.get(),
                SoundSource.PLAYERS, 0.16f, 0.94f);
    }

    /** Charms never take enchantments; they are found things, not made ones. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return charm == Charm.ICHORHEART;
    }
}
