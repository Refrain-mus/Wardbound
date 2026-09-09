package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.CthulhuHeadEntity;
import dev.marrowseal.wardbound.boss.CthulhuHeadRig;
import dev.marrowseal.wardbound.boss.WardBosses;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CthulhuHeadRenderer extends GeoEntityRenderer<CthulhuHeadEntity> {
    public CthulhuHeadRenderer(EntityRendererProvider.Context context){super(context,new Model());shadowRadius=7;addRenderLayer(new AutoGlowingGeoLayer<CthulhuHeadEntity>(this));}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(WardBosses.CTHULHU_HEAD.get(),CthulhuHeadRenderer::new);}
    public static final class Model extends GeoModel<CthulhuHeadEntity> {
        private static final String[][] SWAY_NAMES=swayNames();
        private static String[][] swayNames(){
            String[][] result=new String[CthulhuHeadRig.SEGMENTS.length][];
            for(int c=0;c<result.length;c++){
                result[c]=new String[CthulhuHeadRig.SEGMENTS[c].length];
                for(int j=0;j<result[c].length;j++)result[c][j]=CthulhuHeadRig.SEGMENTS[c][j]+"_sway";
            }
            return result;
        }
        private static ResourceLocation id(String s){return new ResourceLocation(Wardbound.MODID,s);}
        @Override public ResourceLocation getModelResource(CthulhuHeadEntity e){return id("geo/entity/cthulhu_head.geo.json");}
        @Override public ResourceLocation getTextureResource(CthulhuHeadEntity e){return id("textures/entity/cthulhu_head.png");}
        @Override public ResourceLocation getAnimationResource(CthulhuHeadEntity e){return id("animations/entity/cthulhu_head.animation.json");}
        @Override public void setCustomAnimations(CthulhuHeadEntity e,long instanceId,AnimationState<CthulhuHeadEntity> state){
            super.setCustomAnimations(e,instanceId,state);
            double t=e.tickCount+state.getPartialTick();
            float dt=(float)Math.max(0,Math.min(2,e.lastGazeTick<0?1:t-e.lastGazeTick));e.lastGazeTick=t;
            float blend=1-(float)Math.exp(-dt*.12);
            String clip=e.clip();boolean dying=clip.startsWith("death_");
            float life=clip.equals("death_start")?1-Mth.clamp(e.visualAge(state.getPartialTick())/70f,0,1):dying?0:1;
            for(int i=0;i<2;i++){
                String side=i==0?"left":"right";Entity target=e.eyeTarget(i==0);
                float yaw=0,pitch=0;
                if(target!=null&&target.isAlive()&&life>0){
                    float body=Mth.rotLerp(state.getPartialTick(),e.yBodyRotO,e.yBodyRot);
                    double[] socket=i==0?CthulhuHeadRig.EYE_LEFT:CthulhuHeadRig.EYE_RIGHT;
                    // Generated bind socket offsets; full animated beam ray origins belong to combat code.
                    Vec3 origin=e.position().add(new Vec3(socket[0],socket[1],socket[2]).yRot(-body*Mth.DEG_TO_RAD));
                    Vec3 delta=target.getEyePosition().subtract(origin);
                    yaw=Mth.clamp(Mth.wrapDegrees((float)(Mth.atan2(delta.z,delta.x)*Mth.RAD_TO_DEG)-90-body),-18,18)*life;
                    pitch=Mth.clamp((float)(-Mth.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))*Mth.RAD_TO_DEG),-24,24)*life;
                }
                e.gazeYaw[i]=Mth.lerp(blend,e.gazeYaw[i],yaw);e.gazePitch[i]=Mth.lerp(blend,e.gazePitch[i],pitch);
                CoreGeoBone aim=getAnimationProcessor().getBone("eye_"+side+"_aim");
                if(aim!=null){aim.setRotX(e.gazePitch[i]*Mth.DEG_TO_RAD);aim.setRotY(e.gazeYaw[i]*Mth.DEG_TO_RAD);aim.setRotZ(0);}
            }
            // Independent two-frequency currents, with longer phase delay along the longer chains.
            // These are absolute assignments to reserved parents, so neither frame rate nor extra render passes cause drift.
            float gain=life*(clip.equals("idle_rage")?.7f:clip.equals("idle_dormant")?.14f:.38f);
            for(int c=0;c<SWAY_NAMES.length;c++){
                int count=SWAY_NAMES[c].length,side=CthulhuHeadRig.SIDES[c],group=CthulhuHeadRig.GROUPS[c];
                for(int j=0;j<count;j++){
                    CoreGeoBone b=getAnimationProcessor().getBone(SWAY_NAMES[c][j]);if(b==null)continue;
                    float u=j/(float)(count-1),wave=(float)(t*(group>=4?.047:.029)+c*.61-u*3.3);
                    float amplitude=gain*(.55f+u*.65f)*(group>=4?1.25f:1f);
                    b.setRotX((Mth.sin(wave)+.22f*Mth.sin(wave*1.73f+c))*amplitude*Mth.DEG_TO_RAD);
                    b.setRotY(Mth.sin(wave*.81f)*amplitude*.24f*Mth.DEG_TO_RAD);
                    b.setRotZ(Mth.cos(wave+.7f)*amplitude*side*Mth.DEG_TO_RAD);
                }
            }
            CoreGeoBone breath=getAnimationProcessor().getBone("cranial_breath");
            if(breath!=null){breath.setRotX(Mth.sin((float)t*.023f)*.16f*life*Mth.DEG_TO_RAD);breath.setRotY(0);breath.setRotZ(0);}
        }
    }
}
