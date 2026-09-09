package dev.marrowseal.wardbound.client.geo;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.item.WitnessLedgerItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class WitnessLedgerRenderer extends GeoItemRenderer<WitnessLedgerItem> {
    public WitnessLedgerRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Wardbound.MODID, "witness_ledger")) {
            @Override
            public ResourceLocation getTextureResource(WitnessLedgerItem animatable) {
                return new ResourceLocation(Wardbound.MODID, "textures/item/witness_ledger_geo.png");
            }
        });
    }
}
