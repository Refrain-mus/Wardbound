package dev.marrowseal.wardbound.client;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class AshenCuratorRenderer extends GeoEntityRenderer<AshenCuratorEntity> {
    public AshenCuratorRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=1.9f;addRenderLayer(new AutoGlowingGeoLayer<AshenCuratorEntity>(this));}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WardBosses.ASHEN_CURATOR.get(),AshenCuratorRenderer::new);}
    private static class Model extends GeoModel<AshenCuratorEntity>{
        public ResourceLocation getModelResource(AshenCuratorEntity e){return id("geo/entity/ashen_curator.geo.json");}
        public ResourceLocation getTextureResource(AshenCuratorEntity e){
            int st=e.view().getInt("state"),ch=e.view().getInt("chapter"),bs=e.bookState();
            String tex=st==AshenCuratorEntity.COLLAPSE?"ashen_curator_dying":(st==AshenCuratorEntity.OPEN||bs==AshenCuratorEntity.BOOK_CHARGE||bs==AshenCuratorEntity.BOOK_RELEASE)?"ashen_curator_ignited":(ch>=2||st==AshenCuratorEntity.TRANSITION)?"ashen_curator_redaction":"ashen_curator";
            return id("textures/entity/"+tex+".png");
        }
        public ResourceLocation getAnimationResource(AshenCuratorEntity e){return id("animations/entity/ashen_curator.animation.json");}
        @Override public void setCustomAnimations(AshenCuratorEntity e,long id,AnimationState<AshenCuratorEntity> state){
            super.setCustomAnimations(e,id,state);
            CoreGeoBone root=getAnimationProcessor().getBone("root");
            CoreGeoBone body=getAnimationProcessor().getBone("body");
            if(root==null||body==null)return;
            MasterMotionFeel.Sample feel=MasterMotionFeel.sample(e.getId(),body.getRotX(),body.getRotY(),body.getRotZ());
            MasterDamageReactions.Sample reaction=MasterDamageReactions.sample(e.getId());
            int encounterState=e.view().getInt("state");int bookState=e.bookState();
            float targetYaw=Mth.wrapDegrees(e.bookAimYaw()-e.getYRot());float targetPitch=e.bookAimPitch();
            boolean lookLocked=bookState==AshenCuratorEntity.BOOK_RELEASE||bookState==AshenCuratorEntity.BOOK_RECOIL||encounterState==AshenCuratorEntity.TRANSITION;
            MasterLookTracking.Look look=MasterLookTracking.sample(e.getId(),targetYaw,targetPitch,34f,20f,2.9f,lookLocked);
            CoreGeoBone head=getAnimationProcessor().getBone("head");
            if(head!=null){
                head.setRotY(head.getRotY()-look.yaw()*Mth.DEG_TO_RAD);
                head.setRotX(head.getRotX()-look.pitch()*.70f*Mth.DEG_TO_RAD+reaction.pitch()*.035f*Mth.DEG_TO_RAD);
                head.setRotZ(head.getRotZ()+reaction.roll()*.035f*Mth.DEG_TO_RAD);
            }
            body.setRotY(body.getRotY()-look.yaw()*.10f*Mth.DEG_TO_RAD);

            // IMPORTANT: Curator's detached folios live under root while the torso, arms and bound
            // grimoire live under body. Applying hit recoil to body/binding/book bones separately
            // makes those two skeletal islands diverge on impact. Motion/recoil therefore belongs
            // on root as one rigid mass. This preserves every parent-child joint while still giving
            // the boss readable weight.
            float globalPitch=-feel.anticipation()*2.2f+feel.recoil()*4.0f+reaction.pitch()*.14f;
            float globalYaw=feel.lagY()*2.8f+reaction.yaw()*.14f*Mth.DEG_TO_RAD;
            float globalRoll=feel.lagZ()*2.5f+reaction.roll()*.16f*Mth.DEG_TO_RAD;
            root.setRotX(root.getRotX()+Mth.clamp(globalPitch,-7.5f,7.5f)*Mth.DEG_TO_RAD);
            root.setRotY(root.getRotY()+Mth.clamp(globalYaw,-5.0f*Mth.DEG_TO_RAD,5.0f*Mth.DEG_TO_RAD));
            root.setRotZ(root.getRotZ()+Mth.clamp(globalRoll,-4.5f*Mth.DEG_TO_RAD,4.5f*Mth.DEG_TO_RAD));
            root.setPosY(root.getPosY()+Mth.clamp(reaction.lift()*.20f,-.06f,.06f));

            MasterAfterimageTrail.emitCurator(e, feel);
            for(int i=0;i<6;i++)lagBone("hem_"+i,feel,.18f+i*.022f,(i%2==0?1:-1));
            for(int i=0;i<4;i++)lagBone("cape_"+i,feel,.28f+i*.035f,(i%2==0?1:-1));
            lagBone("stole_left",feel,.34f,-1);lagBone("stole_right",feel,.34f,1);

            CoreGeoBone binding=getAnimationProcessor().getBone("binding");
            if(binding!=null){
                // Living Grimoire aim remains independent, but damage reaction never directly
                // twists this bone. That avoids compounding the second animation controller.
                float relativeYaw=Mth.wrapDegrees(e.bookAimYaw()-e.getYRot());
                binding.setRotY(binding.getRotY()-relativeYaw*Mth.DEG_TO_RAD);
                binding.setRotX(binding.getRotX()-e.bookAimPitch()*Mth.DEG_TO_RAD);
                if(bookState==AshenCuratorEntity.BOOK_CHARGE){float tremor=(float)Math.sin(e.tickCount*2.45f);binding.setRotZ(binding.getRotZ()+tremor*2.0f*Mth.DEG_TO_RAD);}
                if(bookState==AshenCuratorEntity.BOOK_RELEASE)binding.setRotX(binding.getRotX()-3.2f*Mth.DEG_TO_RAD);
                if(bookState==AshenCuratorEntity.BOOK_RECOIL)binding.setRotX(binding.getRotX()+3.8f*Mth.DEG_TO_RAD);
            }
            CoreGeoBone left=getAnimationProcessor().getBone("book_left"),right=getAnimationProcessor().getBone("book_right");
            float bookLag=bookState==AshenCuratorEntity.BOOK_CHARGE?1.05f:bookState==AshenCuratorEntity.BOOK_RELEASE?1.15f:.70f;
            if(left!=null){left.setRotY(left.getRotY()+feel.lagY()*.44f*bookLag-feel.recoil()*3f*Mth.DEG_TO_RAD);left.setRotZ(left.getRotZ()-feel.lagZ()*.34f*bookLag);}
            if(right!=null){right.setRotY(right.getRotY()+feel.lagY()*.44f*bookLag+feel.recoil()*3f*Mth.DEG_TO_RAD);right.setRotZ(right.getRotZ()+feel.lagZ()*.34f*bookLag);}
            for(int i=0;i<4;i++){
                CoreGeoBone folio=getAnimationProcessor().getBone("folio_"+i);
                if(folio!=null){
                    float side=(i%2==0?-1:1),organ=bookState==AshenCuratorEntity.BOOK_CHARGE||bookState==AshenCuratorEntity.BOOK_RELEASE?1.25f:1f;
                    folio.setRotY(folio.getRotY()+feel.lagY()*(.46f+i*.07f)*organ);
                    folio.setRotZ(folio.getRotZ()+side*(feel.lagZ()*.68f*organ+feel.recoil()*2.8f*Mth.DEG_TO_RAD));
                    if(encounterState==AshenCuratorEntity.COLLAPSE){float dc=e.view().getInt("clock");float loss=Mth.clamp((dc-(8+i*9f))/10f,0f,1f);float scale=1f-loss;folio.setScaleX(scale);folio.setScaleY(scale);folio.setScaleZ(scale);}
                }
            }

            // Skeletal safety envelope. The source clips remain expressive, but no normal combat
            // blend is allowed to turn an upper arm/forearm/hand far enough to read as detached.
            clamp(body,32f,28f,18f);
            clamp(head,40f,36f,16f);
            clampJoint("arm_left",78f,50f,72f);clampJoint("arm_right",78f,50f,72f);
            clampJoint("forearm_left",58f,42f,46f);clampJoint("forearm_right",58f,42f,46f);
            clampJoint("hand_left",42f,34f,36f);clampJoint("hand_right",42f,34f,36f);
            clamp(binding,64f,62f,22f);
            clamp(left,112f,112f,72f);clamp(right,112f,112f,72f);
            for(int i=0;i<4;i++) clamp(getAnimationProcessor().getBone("folio_"+i),78f,92f,108f);

            if(encounterState==AshenCuratorEntity.COLLAPSE){
                float dc=e.view().getInt("clock");float drop=Mth.clamp((dc-46f)/28f,0f,1f);float ash=Mth.clamp((dc-78f)/38f,0f,1f);
                if(binding!=null){binding.setPosY(binding.getPosY()-drop*18f);binding.setRotX(binding.getRotX()+drop*54f*Mth.DEG_TO_RAD);}
                if(left!=null){left.setRotZ(left.getRotZ()+drop*42f*Mth.DEG_TO_RAD);left.setRotX(left.getRotX()+drop*24f*Mth.DEG_TO_RAD);}
                if(right!=null){right.setRotZ(right.getRotZ()-drop*42f*Mth.DEG_TO_RAD);right.setRotX(right.getRotX()+drop*24f*Mth.DEG_TO_RAD);}
                if(head!=null){head.setRotX(head.getRotX()+ash*32f*Mth.DEG_TO_RAD);head.setRotY(head.getRotY()*(1f-ash));}
                float bodyScale=1f-ash*.82f;body.setScaleX(bodyScale);body.setScaleY(bodyScale);body.setScaleZ(bodyScale);body.setPosY(body.getPosY()-ash*7f);
            }
        }
        private void clampJoint(String name,float x,float y,float z){CoreGeoBone b=getAnimationProcessor().getBone(name);if(b==null)return;clamp(b,x,y,z);b.setScaleX(1f);b.setScaleY(1f);b.setScaleZ(1f);}
        private void lagBone(String name,MasterMotionFeel.Sample f,float gain,int side){CoreGeoBone b=getAnimationProcessor().getBone(name);if(b==null)return;b.setRotX(b.getRotX()+f.lagX()*gain);b.setRotY(b.getRotY()+f.lagY()*gain*.7f);b.setRotZ(b.getRotZ()+f.lagZ()*gain*side);}
        private void clamp(CoreGeoBone bone,float x,float y,float z){if(bone==null)return;float rx=finite(bone.getRotX())?bone.getRotX():0f,ry=finite(bone.getRotY())?bone.getRotY():0f,rz=finite(bone.getRotZ())?bone.getRotZ():0f;bone.setRotX(Mth.clamp(rx,-x*Mth.DEG_TO_RAD,x*Mth.DEG_TO_RAD));bone.setRotY(Mth.clamp(ry,-y*Mth.DEG_TO_RAD,y*Mth.DEG_TO_RAD));bone.setRotZ(Mth.clamp(rz,-z*Mth.DEG_TO_RAD,z*Mth.DEG_TO_RAD));}
        private boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
        private ResourceLocation id(String path){return new ResourceLocation(Wardbound.MODID,path);}
    }
}
