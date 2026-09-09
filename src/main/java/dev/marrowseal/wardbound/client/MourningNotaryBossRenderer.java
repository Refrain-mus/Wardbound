package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import dev.marrowseal.wardbound.boss.WardBosses;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Deliberately conservative combat renderer for the Mourning Notary.
 * The preview model has always been the stable visual reference, so the combat renderer now uses
 * the same transform path and leaves all authored motion to GeckoLib's animation controller.
 * Combat VFX are rendered separately by MourningNotaryTextureFx.
 */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MourningNotaryBossRenderer extends GeoEntityRenderer<MourningNotaryEntity> {
    public MourningNotaryBossRenderer(EntityRendererProvider.Context context){
        super(context,new Model());
        shadowRadius=2.4f;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(WardBosses.MOURNING_NOTARY.get(),MourningNotaryBossRenderer::new);
    }

    private static final class Model extends GeoModel<MourningNotaryEntity>{
        @Override public ResourceLocation getModelResource(MourningNotaryEntity entity){
            return id("geo/entity/mourning_notary.geo.json");
        }

        @Override public ResourceLocation getTextureResource(MourningNotaryEntity entity){
            var view=entity.view();
            int state=view.getInt("state");
            int phase=Math.max(1,view.getInt("phase"));
            String texture=state==MourningNotaryEntity.COLLAPSE?"mourning_notary_dying"
                    :state==MourningNotaryEntity.UNBOUND?"mourning_notary_unbound"
                    :(state==MourningNotaryEntity.TOLL||state==MourningNotaryEntity.JUDGMENT)?"mourning_notary_toll"
                    :phase>=2?"mourning_notary_article2":"mourning_notary";
            return id("textures/entity/"+texture+".png");
        }

        @Override public ResourceLocation getAnimationResource(MourningNotaryEntity entity){
            return id("animations/entity/mourning_notary.animation.json");
        }

        private static ResourceLocation id(String path){return new ResourceLocation(Wardbound.MODID,path);}
    }
}
