package dev.marrowseal.wardbound.ancientsmith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Visual copy only. No ItemEntity exists; proximity pickup transfers server escrow exactly once. */
@Mod.EventBusSubscriber(modid="wardbound",value=Dist.CLIENT)
public final class SmithWeaponDisplay {
 public static Vec3 position(AncientSmithEntity s,float partial){double t=s.sceneTick()+partial;Vec3 anvil=s.strikePoint().add(0,.1,0),hands=s.local(0,5.5,-2.65);
  if(t<50)return anvil;if(t<70)return anvil.lerp(hands,ease((t-50)/20));if(t<100)return hands;if(t<120)return hands.lerp(anvil,ease((t-100)/20));
  if(t<390)return anvil;if(t<424)return anvil.lerp(hands,ease((t-390)/34));
  if(t<434)return hands;if(t<460){double u=ease((t-434)/26);return hands.lerp(s.offerPoint(),u).add(0,Math.sin(u*Math.PI)*.6,0);}return s.offerPoint();
 }
 private static double ease(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;var buffers=mc.renderBuffers().bufferSource();
  for(var entity:mc.level.entitiesForRendering()){if(!(entity instanceof AncientSmithEntity s)||s.sceneSword().isEmpty()||s.sceneTick()<0||s.distanceToSqr(e.getCamera().getPosition())>9216)continue;
   Vec3 p=position(s,e.getPartialTick()).subtract(e.getCamera().getPosition());PoseStack pose=e.getPoseStack();pose.pushPose();pose.translate(p.x,p.y,p.z);pose.mulPose(Axis.YP.rotationDegrees(-s.getYRot()));pose.mulPose(Axis.ZP.rotationDegrees(90));
   int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(position(s,e.getPartialTick())));
   mc.getItemRenderer().renderStatic(s.sceneSword(),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,s.getId());pose.popPose();
  }
 }
}
