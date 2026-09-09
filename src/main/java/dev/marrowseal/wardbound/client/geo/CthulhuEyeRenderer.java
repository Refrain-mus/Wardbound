package dev.marrowseal.wardbound.client.geo;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.CthulhuEyeItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * GeckoLib renderer for The Savant.
 * The gaze is fixed; the item's controller only animates the eyelids.
 */
@OnlyIn(Dist.CLIENT)
public class CthulhuEyeRenderer extends GeoItemRenderer<CthulhuEyeItem> {
    public CthulhuEyeRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Wardbound.MODID, "the_savant")) {
            @Override
            public ResourceLocation getTextureResource(CthulhuEyeItem animatable) {
                return new ResourceLocation(Wardbound.MODID, "textures/item/the_savant.png");
            }
        });
    }
}
