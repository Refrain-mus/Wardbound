package dev.marrowseal.wardbound.ancientsmith;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mod.EventBusSubscriber(modid="wardbound",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SmithClientRegistration {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(AncientSmithModule.SMITH.get(),SmithRenderer::new);
        e.registerEntityRenderer(AncientSmithModule.ANVIL.get(),AnvilRenderer::new);
    }

    private static class Model<T extends GeoAnimatable> extends GeoModel<T>{
        private final String id;
        Model(String s){id=s;}
        public ResourceLocation getModelResource(T a){return AncientSmithModule.id("geo/entity/"+id+".geo.json");}
        public ResourceLocation getTextureResource(T a){return AncientSmithModule.id("textures/entity/"+id+".png");}
        public ResourceLocation getAnimationResource(T a){return AncientSmithModule.id("animations/entity/"+id+".animation.json");}
    }

    private static final class SmithModel extends Model<AncientSmithEntity>{
        SmithModel(){super("ancient_smith");}
        @Override public void setCustomAnimations(AncientSmithEntity smith,long instanceId,AnimationState<AncientSmithEntity> state){
            super.setCustomAnimations(smith,instanceId,state);
            CoreGeoBone hammer=getAnimationProcessor().getBone("hammer");
            if(hammer!=null) hammer.setHidden(smith.sceneTick()<0 && smith.state()==SmithTimeline.State.IDLE_HEAVY);
            // Do not translate or rotate arm-chain bones here. GeckoLib custom transforms can be
            // evaluated repeatedly without restoring the authored bind translation; additive
            // getPos*() edits therefore drift the limbs away from their parents over time.
            // Hammer-state arm posing is authored safely in ancient_smith.animation.json instead.
        }
    }

    public static final class SmithRenderer extends GeoEntityRenderer<AncientSmithEntity>{
        public SmithRenderer(EntityRendererProvider.Context c){super(c,new SmithModel());shadowRadius=1.6f;}
    }
    public static final class AnvilRenderer extends GeoEntityRenderer<AncientAnvilEntity>{
        public AnvilRenderer(EntityRendererProvider.Context c){super(c,new Model<>("ancient_forge_anvil"));shadowRadius=2.6f;}
    }
}
