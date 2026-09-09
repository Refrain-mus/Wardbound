package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, non-blocking ritual overlays for major Pale Gambler beats. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class GamblerScreenFx {
    private static String title="";
    private static long started;
    private static long duration;
    private static int mood;
    private GamblerScreenFx(){}

    public static void trigger(String text,long ms,int newMood){
        title=text==null?"":text;
        started=System.currentTimeMillis();
        duration=Math.max(250,ms);
        mood=newMood;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event){
        if(title.isEmpty())return;
        long age=System.currentTimeMillis()-started;
        if(age<0 || age>=duration){title="";return;}
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return;
        GuiGraphics g=event.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        float in=Math.min(1f,age/140f),out=Math.min(1f,(duration-age)/220f),fade=Math.max(0f,Math.min(in,out));
        if(WardConfig.accessibilityReduceMotion)fade*=.72f;
        int alpha=Math.round(150*fade),lineAlpha=Math.round(230*fade);
        int plate=(alpha<<24)|0x030208;
        int gold=(lineAlpha<<24)|0xD1B16D;
        int accent=(lineAlpha<<24)|(mood>=3?0xC95A72:mood==2?0x8A60BD:0x6DCBB7);
        int bar=Math.max(8,Math.round(15*fade));
        g.fill(0,0,w,bar,plate);
        g.fill(0,h-bar,w,h,plate);
        g.fill(w/2-90,bar,w/2+90,bar+1,gold);
        g.fill(w/2-58,bar+3,w/2+58,bar+4,accent);
        g.fill(w/2-90,h-bar-1,w/2+90,h-bar,gold);
        var sigil = mood>=3?GamblerTextureFx.SIGIL_BLOOD:mood==2?GamblerTextureFx.SIGIL_GOLD:GamblerTextureFx.SIGIL_TEAL;
        drawSigil(g,sigil,w/2,bar+9,fade,false);
        drawSigil(g,sigil,w/2,h-bar-9,fade,true);
        if(fade>.22f){
            int textAlpha=Math.round(255*fade);
            int textColor=(textAlpha<<24)|0xF2E3C1;
            g.drawCenteredString(mc.font,title,w/2,Math.max(18,bar+8),textColor);
        }
    }

    private static void drawSigil(GuiGraphics g,net.minecraft.resources.ResourceLocation texture,int cx,int cy,float fade,boolean flip){
        int size=34;
        RenderSystem.setShaderColor(1f,1f,1f,Math.max(.18f,fade));
        var pose=g.pose();
        pose.pushPose();
        pose.translate(cx,cy,0);
        if(flip)pose.scale(1f,-1f,1f);
        g.blit(texture,-size/2,-size/2,0,0,size,size,128,128);
        pose.popPose();
        RenderSystem.setShaderColor(1f,1f,1f,1f);
    }
}
