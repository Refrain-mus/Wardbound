package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.AshenCuratorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Archive folio boss plate with state, seal ledger and vulnerability presentation. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class CuratorBossBar {
    private static final ResourceLocation FRAME = new ResourceLocation(Wardbound.MODID,"textures/gui/ashen_curator_bossbar.png");

    @SubscribeEvent public static void draw(CustomizeGuiOverlayEvent.BossEventProgress e){
        if(!(e.getBossEvent().getName().getContents() instanceof TranslatableContents title)||!title.getKey().equals("entity.wardbound.ashen_curator"))return;
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null)return; AshenCuratorEntity boss=null;
        for(var ent:mc.level.entitiesForRendering()) if(ent instanceof AshenCuratorEntity b && b.view().hasUUID("bar") && b.view().getUUID("bar").equals(e.getBossEvent().getId())) {boss=b;break;}
        e.setCanceled(true); e.setIncrement(68);
        var g=e.getGuiGraphics(); int x=mc.getWindow().getGuiScaledWidth()/2-128, y=e.getY()-3;
        RenderSystem.setShaderColor(1,1,1,1); g.blit(FRAME,x,y,0,0,256,64,256,64);
        int state=boss==null?0:boss.view().getInt("state"), chapter=boss==null?1:boss.view().getInt("chapter");
        g.drawString(mc.font,"ASHEN CURATOR",x+18,y+10,0xffe7d8ba,false);
        g.drawString(mc.font,chapter>=2?"CHAPTER II · REDACTION":"CHAPTER I · ACCESSION",x+18,y+21,chapter>=2?0xffc78a66:0xffa6b6a8,false);
        int pct=Math.max(0,Math.min(100,Math.round(e.getBossEvent().getProgress()*100)));
        String hp=pct+"%"; g.drawString(mc.font,hp,x+178-mc.font.width(hp),y+21,0xff938b79,false);
        int fill=Math.max(0,Math.min(228,Math.round(228*e.getBossEvent().getProgress())));
        int barColor=state==AshenCuratorEntity.OPEN?0xff9bc4a4:state==AshenCuratorEntity.COLLAPSE?0xff6d5b4d:chapter>=2?0xffb66f4c:0xffb49d72;
        g.fill(x+14,y+32,x+14+fill,y+39,barColor); g.fill(x+14,y+32,x+14+fill,y+33,0xffead8b7);
        for(int i=1;i<5;i++) g.fill(x+14+i*45,y+33,x+15+i*45,y+39,0xff191c1a);
        String label=label(state,boss);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(label,222),x+16,y+48,state==AshenCuratorEntity.OPEN?0xffb9e0c2:0xffb6b7a7,false);
        if(boss!=null){var v=boss.view();
            for(int i=0;i<4;i++){
                int sx=x+201+i*11, color=(v.getInt("kept")&(1<<i))!=0?0xff9fc9ab:(v.getInt("broken")&(1<<i))!=0?0xffbd6b47:0xff4e554d;
                g.fill(sx,y+11,sx+8,y+19,color); g.fill(sx+1,y+12,sx+7,y+13,0x66ffffff);
            }
        }
        RenderSystem.setShaderColor(1,1,1,1);
    }

    private static String label(int state,AshenCuratorEntity boss){
        if(boss==null)return "THE ARCHIVE IS OPEN";
        var v=boss.view();
        int vr=v.getInt("variant");
        return switch(state){
            case AshenCuratorEntity.SWEEP -> vr==1?"STACKED SHELVES  ·  HOLD THE LIT CROSS-AISLE":"ASH SHELVES  ·  HOLD THE BLANK AISLE";
            case AshenCuratorEntity.RINGS -> vr==1?"TWIN FOLIOS  ·  TWO EDGES CLOSE AT ONCE":"CINDER FOLIOS  ·  READ THE EXPANDING EDGE";
            case AshenCuratorEntity.ERRATA -> vr==1?"MARGINALIA  ·  THE PAGE CORRECTS YOUR ESCAPE":"ERRATA  ·  LEAVE THE MARKED ADDRESS";
            case AshenCuratorEntity.ARCHIVE -> v.getInt("clock")>=v.getInt("deadline")
                    ? "INCINERATION  ·  PRESERVED SHELVES ONLY"
                    : (vr==1?"HOLLOW INDEX  ·  FASTER FILING  ·  ":"FINAL INDEX  ·  PRESERVE / BREAK  ·  ")+Math.max(0,(v.getInt("deadline")-v.getInt("clock")+19)/20)+"s";
            case AshenCuratorEntity.OPEN -> (vr==1?"WIDE BINDING  ·  EXTENDED DAMAGE WINDOW":"OPEN BINDING  ·  EXPOSED PAGE // DAMAGE WINDOW");
            case AshenCuratorEntity.TRANSITION -> "REDACTION  ·  THE SHELVES SHIFT";
            case AshenCuratorEntity.COLLAPSE -> "THE RECORD IS RELEASED";
            case AshenCuratorEntity.MANIFEST -> "CATALOGUE ENTRY // MANIFESTING";
            default -> "CHOOSE WHAT SURVIVES";
        };
    }
}
