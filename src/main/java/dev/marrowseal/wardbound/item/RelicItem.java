package dev.marrowseal.wardbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import dev.marrowseal.wardbound.client.geo.ColdShimRenderer;
import dev.marrowseal.wardbound.client.geo.CyclopeanLensRenderer;
import dev.marrowseal.wardbound.client.geo.SlowEmberRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Things left over from whoever was making seals before the current houses were.
 *
 * <p>Neither is craftable and neither is a charm: they do not bind to a lock and
 * they do not buy you lives. A charm helps you survive a ward. A relic changes
 * what you know going in, which is a different kind of help and, in a mod whose
 * whole subject is locks, a more interesting one.
 */
public class RelicItem extends Item implements GeoItem {

    public enum Kind {
        /** Takes the first stage of the mechanism out of play. Three uses. */
        SHIM,
        /** Reads the ward through the lid. No durability; it is not being used up. */
        LENS,
        /**
         * Holds the ward open longer. Out of the Nether, out of a chest that
         * paid enormously, and the only thing in the mod that buys the one
         * resource every single lock spends: the clock.
         */
        EMBER,
    }

    public final Kind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public RelicItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Only the Cyclopean Lens is intentionally uncanny. Cold Shim and Slow Ember
        // use GeckoLib for their 3D geometry, but stay completely static.
        if (kind == Kind.LENS) {
            controllers.add(new AnimationController<>(this, "idle", 0, state ->
                    state.setAndContinue(RawAnimation.begin().thenLoop("animation.cyclopean_lens.idle"))));
        }
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
                    renderer = switch (kind) {
                        case SHIM -> new ColdShimRenderer();
                        case LENS -> new CyclopeanLensRenderer();
                        case EMBER -> new SlowEmberRenderer();
                    };
                }
                return renderer;
            }
        });
    }


    /**
     * Returns the exact interaction stack when it is the requested relic kind.
     * No inventory or hotbar search is performed.
     */
    public static ItemStack fromInteractionStack(ItemStack stack, Kind kind) {
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof RelicItem relic
                && relic.kind == kind) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        String key = switch (kind) {
            case SHIM -> "cold_shim";
            case LENS -> "cyclopean_lens";
            case EMBER -> "slow_ember";
        };
        tooltip.add(Component.translatable("item.wardbound." + key + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("wardbound.tooltip." + key)
                .withStyle(ChatFormatting.GRAY));
        if (stack.isDamageableItem()) {
            tooltip.add(Component.translatable("wardbound.tooltip.uses",
                            stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
