package dev.marrowseal.wardbound.client.geo;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.IchorheartItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Renders the ichorheart.
 *
 * <p>{@code DefaultedItemGeoModel} resolves all three asset paths from one name,
 * so the model, the animation and the texture live at
 * {@code geo/item/ichorheart.geo.json}, {@code animations/item/ichorheart.animation.json}
 * and {@code textures/item/ichorheart.png} respectively. The texture is
 * overridden because the sprite of the same name is still there for the
 * inventory-free cases and is a twelve frame strip, which would map into the
 * model as one long smear.
 */
@OnlyIn(Dist.CLIENT)
public class IchorheartRenderer extends GeoItemRenderer<IchorheartItem> {

    public IchorheartRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Wardbound.MODID, "ichorheart")) {
            @Override
            public ResourceLocation getTextureResource(IchorheartItem animatable) {
                return new ResourceLocation(Wardbound.MODID, "textures/item/ichorheart_geo.png");
            }
        });
    }
}
