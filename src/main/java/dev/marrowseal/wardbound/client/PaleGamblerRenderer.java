package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.PaleGamblerEntity;
import dev.marrowseal.wardbound.boss.WardBosses;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@Mod.EventBusSubscriber(modid=Wardbound.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public class PaleGamblerRenderer extends GeoEntityRenderer<PaleGamblerEntity> {
    public PaleGamblerRenderer(EntityRendererProvider.Context c) {super(c,new Model());shadowRadius=1.4f;withScale(1.65f);addRenderLayer(new AutoGlowingGeoLayer<PaleGamblerEntity>(this));}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {e.registerEntityRenderer(WardBosses.PALE_GAMBLER.get(),PaleGamblerRenderer::new);}

    private static class Model extends GeoModel<PaleGamblerEntity> {
        public ResourceLocation getModelResource(PaleGamblerEntity e) {return new ResourceLocation(Wardbound.MODID,"geo/entity/pale_gambler.geo.json");}
        public ResourceLocation getTextureResource(PaleGamblerEntity e) {
            String tex=e.openTell()?"pale_gambler_exposed":e.allIn()?"pale_gambler_allin":e.currentPhase()>=2?"pale_gambler_phase2":"pale_gambler";
            return new ResourceLocation(Wardbound.MODID,"textures/entity/"+tex+".png");
        }
        public ResourceLocation getAnimationResource(PaleGamblerEntity e) {return new ResourceLocation(Wardbound.MODID,"animations/entity/pale_gambler.animation.json");}
        private void clamp(CoreGeoBone bone,float x,float y,float z){if(bone==null)return;bone.setRotX(Mth.clamp(bone.getRotX(),-x*Mth.DEG_TO_RAD,x*Mth.DEG_TO_RAD));bone.setRotY(Mth.clamp(bone.getRotY(),-y*Mth.DEG_TO_RAD,y*Mth.DEG_TO_RAD));bone.setRotZ(Mth.clamp(bone.getRotZ(),-z*Mth.DEG_TO_RAD,z*Mth.DEG_TO_RAD));}

        @Override
        public void setCustomAnimations(PaleGamblerEntity animatable,long instanceId,AnimationState<PaleGamblerEntity> state){
            super.setCustomAnimations(animatable,instanceId,state);
            if(state==null)return;
            EntityModelData data=state.getData(DataTickets.ENTITY_MODEL_DATA);
            if(data==null)return;
            CoreGeoBone body=getAnimationProcessor().getBone("body");
            CoreGeoBone root=getAnimationProcessor().getBone("root");
            // Gecko/Molang attack clips are allowed to bob, but the dealer itself is anchored to the
            // table. Hard-capping the visual root prevents successive clips/interpolation from
            // looking like the boss is slowly levitating away from the arena.
            if(root!=null){
                root.setPosY(Mth.clamp(root.getPosY(),-.35f,.55f));
                root.setPosX(Mth.clamp(root.getPosX(),-.45f,.45f));
                root.setPosZ(Mth.clamp(root.getPosZ(),-.55f,.55f));
                root.setRotX(Mth.clamp(root.getRotX(),-16f*Mth.DEG_TO_RAD,16f*Mth.DEG_TO_RAD));
                root.setRotZ(Mth.clamp(root.getRotZ(),-14f*Mth.DEG_TO_RAD,14f*Mth.DEG_TO_RAD));
            }
            MasterMotionFeel.Sample feel=body==null?null:MasterMotionFeel.sample(animatable.getId(),body.getRotX(),body.getRotY(),body.getRotZ());
            MasterDamageReactions.Sample reaction=MasterDamageReactions.sample(animatable.getId());
            boolean releaseLock=feel!=null && (feel.freezeMix()>.08f || Math.abs(feel.recoil())>.16f);
            MasterLookTracking.Look look=MasterLookTracking.sample(animatable.getId(),data.netHeadYaw(),data.headPitch(),62f,28f,8.5f,releaseLock);
            CoreGeoBone head=getAnimationProcessor().getBone("head");
            if(head!=null){
                head.setRotY(look.yaw()*Mth.DEG_TO_RAD + reaction.yaw()*.22f*Mth.DEG_TO_RAD);
                head.setRotX(look.pitch()*Mth.DEG_TO_RAD + reaction.pitch()*.18f*Mth.DEG_TO_RAD);
                head.setRotZ((-4f+reaction.roll()*.30f)*Mth.DEG_TO_RAD);
            }
            if(body!=null){
                float follow=animatable.currentHand()==0?.12f:.055f;
                body.setRotY(body.getRotY()+look.yaw()*follow*Mth.DEG_TO_RAD);
                body.setRotX(body.getRotX()+look.pitch()*.025f*Mth.DEG_TO_RAD);
            }

            // The model's face is on -Z (eyes/mouth protrude toward negative Z). For a hanging
            // arm, POSITIVE X pitch rotates the lower arm toward -Z; negative X sends it behind
            // the torso. Earlier passes had this sign inverted, which is exactly why casts still
            // lunged backwards. Clamp combat poses into the real forward hemisphere and also
            // bias the shoulder/hand chain toward the coat front so interpolation cannot cross it.
            int activeHand=animatable.currentHand();
            float shoulderFront=activeHand==0?-0.34f:-1.55f;
            float handFront=activeHand==0?-0.22f:-0.95f;
            CoreGeoBone leftArm=getAnimationProcessor().getBone("left_arm");
            CoreGeoBone rightArm=getAnimationProcessor().getBone("right_arm");
            CoreGeoBone leftHand=getAnimationProcessor().getBone("left_hand");
            CoreGeoBone rightHand=getAnimationProcessor().getBone("right_hand");
            if(leftArm!=null){
                leftArm.setPosZ(shoulderFront);
                if(activeHand!=0){float mag=Mth.clamp(Math.abs(leftArm.getRotX()),28f*Mth.DEG_TO_RAD,106f*Mth.DEG_TO_RAD);leftArm.setRotX(mag);leftArm.setRotZ(Mth.clamp(leftArm.getRotZ(),-82f*Mth.DEG_TO_RAD,82f*Mth.DEG_TO_RAD));}
            }
            if(rightArm!=null){
                rightArm.setPosZ(shoulderFront);
                if(activeHand!=0){float mag=Mth.clamp(Math.abs(rightArm.getRotX()),28f*Mth.DEG_TO_RAD,106f*Mth.DEG_TO_RAD);rightArm.setRotX(mag);rightArm.setRotZ(Mth.clamp(rightArm.getRotZ(),-82f*Mth.DEG_TO_RAD,82f*Mth.DEG_TO_RAD));}
            }
            if(leftHand!=null){
                leftHand.setPosZ(handFront);
                if(activeHand!=0){float mag=Mth.clamp(Math.abs(leftHand.getRotX()),8f*Mth.DEG_TO_RAD,44f*Mth.DEG_TO_RAD);leftHand.setRotX(mag);leftHand.setRotY(Mth.clamp(leftHand.getRotY(),-38f*Mth.DEG_TO_RAD,38f*Mth.DEG_TO_RAD));}
            }
            if(rightHand!=null){
                rightHand.setPosZ(handFront);
                if(activeHand!=0){float mag=Mth.clamp(Math.abs(rightHand.getRotX()),8f*Mth.DEG_TO_RAD,44f*Mth.DEG_TO_RAD);rightHand.setRotX(mag);rightHand.setRotY(Mth.clamp(rightHand.getRotY(),-38f*Mth.DEG_TO_RAD,38f*Mth.DEG_TO_RAD));}
            }
            // Server-authored release beat: visually hold the exact release pose for a few frames,
            // then kick into recoil. Secondary cards/hands inherit damped angular inertia.
            if(body!=null && feel!=null){
                float freeze=feel.freezeMix();
                if(freeze>0){body.setRotX(Mth.lerp(freeze,body.getRotX(),feel.freezeX()));body.setRotY(Mth.lerp(freeze,body.getRotY(),feel.freezeY()));body.setRotZ(Mth.lerp(freeze,body.getRotZ(),feel.freezeZ()));}
                body.setRotX(body.getRotX()-feel.anticipation()*7.5f*Mth.DEG_TO_RAD+feel.recoil()*11.0f*Mth.DEG_TO_RAD+reaction.pitch()*Mth.DEG_TO_RAD);
                body.setRotY(body.getRotY()+reaction.yaw()*Mth.DEG_TO_RAD);
                body.setRotZ(body.getRotZ()+feel.recoil()*5.5f*Mth.DEG_TO_RAD+feel.lagZ()*.28f+reaction.roll()*Mth.DEG_TO_RAD);
                body.setPosY(body.getPosY()+reaction.lift());
                if(leftHand!=null){leftHand.setRotX(leftHand.getRotX()+feel.lagX()*.42f-feel.recoil()*5f*Mth.DEG_TO_RAD-reaction.amount()*3.5f*Mth.DEG_TO_RAD);leftHand.setRotZ(leftHand.getRotZ()+feel.lagZ()*.55f-reaction.roll()*.22f*Mth.DEG_TO_RAD);}
                if(rightHand!=null){rightHand.setRotX(rightHand.getRotX()+feel.lagX()*.42f-feel.recoil()*5f*Mth.DEG_TO_RAD-reaction.amount()*3.5f*Mth.DEG_TO_RAD);rightHand.setRotZ(rightHand.getRotZ()+feel.lagZ()*.55f-reaction.roll()*.22f*Mth.DEG_TO_RAD);}
                MasterAfterimageTrail.emitGambler(animatable, feel);
                for(int i=0;i<4;i++){
                    CoreGeoBone card=getAnimationProcessor().getBone("card_"+i);if(card==null)continue;
                    float side=(i<2?-1f:1f),weight=1.0f+i*.16f;
                    card.setRotY(card.getRotY()+feel.lagY()*1.45f*weight+side*feel.recoil()*8f*Mth.DEG_TO_RAD);
                    card.setRotZ(card.getRotZ()+feel.lagZ()*1.85f*weight+side*feel.anticipation()*3f*Mth.DEG_TO_RAD);
                    card.setRotX(card.getRotX()+feel.lagX()*.70f);
                }
            }
            // A living combat pose may lean, but never become a horizontal slab. Death collapse is
            // applied after this clamp and therefore keeps its intentionally deeper fall.
            clamp(body,44f,52f,28f);
            clamp(head,55f,68f,24f);
            clamp(leftArm,110f,35f,88f);
            clamp(rightArm,110f,35f,88f);
            clamp(leftHand,60f,45f,55f);
            clamp(rightHand,60f,45f,55f);
            for(int i=0;i<4;i++) clamp(getAnimationProcessor().getBone("card_"+i),72f,72f,96f);
            if(animatable.deathSequence() && body!=null){
                float dc=animatable.deathClock();
                float settle=Mth.clamp((dc-48f)/46f,0f,1f);
                float hush=Mth.clamp((dc-78f)/30f,0f,1f);
                body.setRotX(body.getRotX()+settle*48f*Mth.DEG_TO_RAD);
                body.setRotZ(body.getRotZ()-settle*10f*Mth.DEG_TO_RAD);
                if(head!=null){head.setRotY(0);head.setRotX((18f+settle*24f)*Mth.DEG_TO_RAD);head.setRotZ((-4f-settle*8f)*Mth.DEG_TO_RAD);}
                if(leftArm!=null)leftArm.setRotX(leftArm.getRotX()+settle*34f*Mth.DEG_TO_RAD);
                if(rightArm!=null)rightArm.setRotX(rightArm.getRotX()+settle*29f*Mth.DEG_TO_RAD);
                if(leftHand!=null)leftHand.setRotZ(leftHand.getRotZ()-settle*18f*Mth.DEG_TO_RAD);
                if(rightHand!=null)rightHand.setRotZ(rightHand.getRotZ()+settle*18f*Mth.DEG_TO_RAD);
                // The body remains readable until the final House hush, then visually loses presence.
                float scale=1f-hush*.10f;body.setScaleX(scale);body.setScaleY(scale);body.setScaleZ(scale);
            }
        }
    }
}
