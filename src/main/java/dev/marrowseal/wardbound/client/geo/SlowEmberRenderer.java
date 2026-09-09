package dev.marrowseal.wardbound.client.geo;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.RelicItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class SlowEmberRenderer extends GeoItemRenderer<RelicItem> {
    public SlowEmberRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Wardbound.MODID, "slow_ember")));
    }
}
