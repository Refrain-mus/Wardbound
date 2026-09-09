package dev.marrowseal.wardbound.maestroart.client;

import dev.marrowseal.wardbound.maestroart.MaestroArtEntity;
import dev.marrowseal.wardbound.maestroart.MaestroArtModule;
import dev.marrowseal.wardbound.maestroart.MaestroRig;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@Mod.EventBusSubscriber(modid="wardbound",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MaestroArtRenderer extends GeoEntityRenderer<MaestroArtEntity> {
    public MaestroArtRenderer(EntityRendererProvider.Context context){super(context,new Model());shadowRadius=.48f;addRenderLayer(new MaestroVfxLayer(this));addRenderLayer(new AutoGlowingGeoLayer<MaestroArtEntity>(this));}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(MaestroArtModule.type(),MaestroArtRenderer::new);}
    public static final class Model extends GeoModel<MaestroArtEntity> {
        private static ResourceLocation id(String path){return new ResourceLocation("wardbound",path);}
        @Override public ResourceLocation getModelResource(MaestroArtEntity e){return id("geo/entity/maestro_performer.geo.json");}
        @Override public ResourceLocation getTextureResource(MaestroArtEntity e){return id("textures/entity/maestro_performer.png");}
        @Override public ResourceLocation getAnimationResource(MaestroArtEntity e){return id("animations/entity/maestro_performer.animation.json");}
        /** Local pose access for a future VFX render layer. This is not a world-space position. */
        public CoreGeoBone socketBone(String name){
            if(!MaestroRig.SOCKETS.contains(name))throw new IllegalArgumentException("Unknown Maestro socket: "+name);
            return getAnimationProcessor().getBone(name);
        }
        @Override public void setCustomAnimations(MaestroArtEntity e,long instanceId,AnimationState<MaestroArtEntity> state){
            super.setCustomAnimations(e,instanceId,state);
            String clip=e.clip();float time=e.tickCount+state.getPartialTick();
            float life=clip.equals("death_start")?1-Mth.clamp(e.visualPerformanceAge(state.getPartialTick())/65,0,1):clip.startsWith("death_")?0:1;
            float energy=clip.equals("idle_rage")?1.25f:clip.startsWith("idle_")?.55f:1f;
            for(int i=0;i<MaestroRig.SECONDARY.length;i++){
                String name=MaestroRig.SECONDARY[i];CoreGeoBone b=getAnimationProcessor().getBone(name);if(b==null)continue;
                float strength=name.startsWith("coat_")?.7f:name.startsWith("baton_ribbon")?1.1f:.32f;
                float phase=time*.039f+i*.47f;
                // Reserved parent controls only, with absolute assignments: no per-frame drift.
                b.setRotX(Mth.sin(phase)*strength*energy*life*Mth.DEG_TO_RAD);
                b.setRotY(Mth.sin(phase*.71f+.8f)*strength*.3f*life*Mth.DEG_TO_RAD);
                b.setRotZ(Mth.cos(phase+.5f)*strength*.55f*energy*life*Mth.DEG_TO_RAD);
            }
            CoreGeoBone baton=getAnimationProcessor().getBone("baton");
            if(baton!=null)baton.setHidden(clip.startsWith("piano_"));
        }
    }
}
