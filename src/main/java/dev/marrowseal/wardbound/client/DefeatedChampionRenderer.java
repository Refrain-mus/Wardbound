package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.WardBosses;
import dev.marrowseal.wardbound.champion.DefeatedChampionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.core.animation.AnimationState;

@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class DefeatedChampionRenderer extends GeoEntityRenderer<DefeatedChampionEntity> {
    public DefeatedChampionRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=.45f;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WardBosses.DEFEATED_CHAMPION.get(),DefeatedChampionRenderer::new);}
    private static class Model extends GeoModel<DefeatedChampionEntity>{
        private ResourceLocation id(String path){return new ResourceLocation(Wardbound.MODID,path);}
        public ResourceLocation getModelResource(DefeatedChampionEntity e){return id("geo/entity/defeated_champion.geo.json");}
        public ResourceLocation getTextureResource(DefeatedChampionEntity e){return id("textures/entity/defeated_champion.png");}
        public ResourceLocation getAnimationResource(DefeatedChampionEntity e){return id("animations/entity/defeated_champion.animation.json");}
        @Override public void setCustomAnimations(DefeatedChampionEntity e,long id,AnimationState<DefeatedChampionEntity> state){
            super.setCustomAnimations(e,id,state);
            var hand=getAnimationProcessor().getBone("sword_hand");
            var hip=getAnimationProcessor().getBone("sword_hip");
            var scabbard=getAnimationProcessor().getBone("scabbard");
            var head=getAnimationProcessor().getBone("head");
            if(hand!=null)hand.setHidden(!e.drawn());
            if(hip!=null)hip.setHidden(e.drawn());
            if(scabbard!=null){
                scabbard.setHidden(false);
                scabbard.setRotX(Mth.clamp(scabbard.getRotX(),-35f*Mth.DEG_TO_RAD,45f*Mth.DEG_TO_RAD));
                scabbard.setRotZ(Mth.clamp(scabbard.getRotZ(),-55f*Mth.DEG_TO_RAD,55f*Mth.DEG_TO_RAD));
            }
            if(head!=null){
                head.setRotX(Mth.clamp(head.getRotX(),-40f*Mth.DEG_TO_RAD,55f*Mth.DEG_TO_RAD));
                head.setRotY(Mth.clamp(head.getRotY(),-65f*Mth.DEG_TO_RAD,65f*Mth.DEG_TO_RAD));
                head.setRotZ(Mth.clamp(head.getRotZ(),-20f*Mth.DEG_TO_RAD,20f*Mth.DEG_TO_RAD));
            }
        }
    }
}
