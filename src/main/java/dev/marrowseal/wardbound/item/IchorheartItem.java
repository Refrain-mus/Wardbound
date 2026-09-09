package dev.marrowseal.wardbound.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import dev.marrowseal.wardbound.client.geo.IchorheartRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * The ichorheart, as a model rather than a picture.
 *
 * <p>It was a twelve frame sprite strip before this, which is a perfectly good
 * way to make something blink and a poor one to make something beat: a flipbook
 * has no depth, so the swelling reads as the icon changing size rather than as a
 * muscle contracting. A model made of a dozen boxes, with the two lobes on their
 * own bones and each of them a fraction out of time with the body, reads as one
 * thing moving. That is the whole reason the GeckoLib dependency is worth
 * carrying, and it is why nothing else in this mod uses it.
 *
 * <p>The animation never stops and has no controller logic. There is nothing to
 * respond to: the point of the item is that it is doing this whether or not you
 * are looking at it.
 */
public class IchorheartItem extends CharmItem implements GeoItem {

    private static final RawAnimation BEAT =
            RawAnimation.begin().thenLoop("animation.ichorheart.beat");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IchorheartItem(Properties properties) {
        super(properties, Charm.ICHORHEART);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "beat", 0, state -> state.setAndContinue(BEAT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private IchorheartRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new IchorheartRenderer();
                return renderer;
            }
        });
    }

    /** The sprite is gone, so the enchantment sheen would have nothing to sit on. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
