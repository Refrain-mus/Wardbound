package dev.marrowseal.wardbound.client.geo;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.CthulhuIdolItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders the Cthulhu Idol.
 *
 * <p>The sheet at {@code textures/item/cthulhu_idol_geo.png} is not a UV
 * unwrap. The model samples eight fixed 16x16 regions of it and reuses each one
 * across every face of several cubes, so each region is a material rather than
 * a place on the model. Editing it means editing those regions, not painting
 * over a silhouette.
 *
 * <p>{@link AutoGlowingGeoLayer} re-renders the model at full brightness
 * through the mask at {@code cthulhu_idol_geo_glowmask.png}, which marks only
 * the eye. GeckoLib resolves that path itself by appending {@code _glowmask}
 * to the texture name, and it erases the masked pixels from the base texture so
 * they are drawn once, by the glow pass. The mask must stay the same size as
 * the base sheet or GeckoLib throws on load.
 */
@OnlyIn(Dist.CLIENT)
public class CthulhuIdolRenderer extends GeoItemRenderer<CthulhuIdolItem> {
    public CthulhuIdolRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Wardbound.MODID, "cthulhu_idol")) {
            @Override
            public ResourceLocation getTextureResource(CthulhuIdolItem animatable) {
                return new ResourceLocation(Wardbound.MODID, "textures/item/cthulhu_idol_geo.png");
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<CthulhuIdolItem>(this));
    }
}
