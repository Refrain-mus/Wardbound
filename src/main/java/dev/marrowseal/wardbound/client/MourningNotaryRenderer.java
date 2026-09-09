package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MourningNotaryRenderer extends GeoEntityRenderer<MourningNotaryPreviewEntity> {
    public MourningNotaryRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=1.9f;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WardBosses.MOURNING_NOTARY_PREVIEW.get(),MourningNotaryRenderer::new);}
    private static class Model extends GeoModel<MourningNotaryPreviewEntity>{
        public ResourceLocation getModelResource(MourningNotaryPreviewEntity e){return id("geo/entity/mourning_notary.geo.json");}
        public ResourceLocation getTextureResource(MourningNotaryPreviewEntity e){return id("textures/entity/mourning_notary.png");}
        public ResourceLocation getAnimationResource(MourningNotaryPreviewEntity e){return id("animations/entity/mourning_notary.animation.json");}
        private ResourceLocation id(String path){return new ResourceLocation(Wardbound.MODID,path);}
    }
}
