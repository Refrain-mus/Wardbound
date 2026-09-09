package dev.marrowseal.wardbound.nhalsul;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.*;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
@OnlyIn(Dist.CLIENT)
public final class NhalSulRenderer extends GeoItemRenderer<NhalSulItem>{
    public NhalSulRenderer(){super(new DefaultedItemGeoModel<>(new ResourceLocation(NhalSulModule.MODID,"nhal_sul")));}
}
