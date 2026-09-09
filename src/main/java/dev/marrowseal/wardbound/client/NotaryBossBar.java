package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Funeral-notarial boss plate with the currently filed law exposed to the player. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class NotaryBossBar {
    private static final ResourceLocation FRAME=new ResourceLocation(Wardbound.MODID,"textures/gui/mourning_notary_bossbar.png");
    private NotaryBossBar(){}
    @SubscribeEvent public static void draw(CustomizeGuiOverlayEvent.BossEventProgress e){
        if(!(e.getBossEvent().getName().getContents() instanceof TranslatableContents t)||!t.getKey().equals("entity.wardbound.mourning_notary"))return;
        Minecraft mc=Minecraft.getInstance();MourningNotaryEntity boss=forBar(e.getBossEvent().getId());e.setCanceled(true);e.setIncrement(58);var g=e.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2-128,y=e.getY()-2;
        g.blit(FRAME,x,y,0,0,256,32,256,32);float pr=Math.max(0,Math.min(1,e.getBossEvent().getProgress()));int fill=Math.round(226*pr);int state=boss==null?0:boss.view().getInt("state"),phase=boss==null?1:boss.view().getInt("phase");int color=state==MourningNotaryEntity.UNBOUND?0xffc7d0c1:phase>=2?0xff8e4157:0xff9e8662;
        g.fill(x+15,y+19,x+15+fill,y+24,color);g.fill(x+15,y+19,x+15+fill,y+20,0xffe7dcc8);
        g.drawString(mc.font,"MOURNING NOTARY",x+18,y+6,0xffe5dac8,false);String article=phase>=3?"ARTICLE III · FINAL APPEAL":phase>=2?"ARTICLE II · JOINT LAW":"ARTICLE I · TESTIMONY";int articleColor=phase>=3?0xffe08a9b:phase>=2?0xffc77c8d:0xffb9a47b;g.drawString(mc.font,article,x+148-mc.font.width(article)/2,y+6,articleColor,false);
        String pct=Math.round(pr*100)+"%";g.drawString(mc.font,pct,x+236-mc.font.width(pct),y+6,0xffa99d8d,false);String label=label(boss,state);g.drawCenteredString(mc.font,label,x+128,y+27,state==MourningNotaryEntity.UNBOUND?0xffe5eadf:0xffcfc2ae);
    }
    private static MourningNotaryEntity forBar(java.util.UUID barId){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return null;
        MourningNotaryEntity only=null;
        int visible=0;
        for(var e:mc.level.entitiesForRendering())if(e instanceof MourningNotaryEntity n&&!n.isRemoved()){
            visible++;
            only=n;
            var v=n.view();
            if(v.hasUUID("bar")&&v.getUUID("bar").equals(barId))return n;
        }
        // During the first sync frame the view tag may not contain the ServerBossEvent UUID yet.
        // Falling back is safe only when exactly one Notary exists; nearest-entity fallback could
        // show another encounter's article/state when multiple private cells are rendered client-side.
        return visible==1?only:null;
    }
    private static String label(MourningNotaryEntity b,int s){if(b==null)return "THE COURT IS IN SESSION";int vr=b.view().getInt("variant");return switch(s){case MourningNotaryEntity.MANIFEST->"NOTICE OF APPEARANCE // FILING";case MourningNotaryEntity.DECLARE->vr==3?"CLOSING DOCKET // FOUR WALLS CONTRACT":vr==2?"TRIBUNAL ARCH // THE GATES ARE CLOSING":vr==1?"CROSS-EXAMINATION // TWO FINDINGS ENTER":"DECLARATION // LEAVE THE SEAL-WALL";case MourningNotaryEntity.STAMP->vr==2?"VERDICT CASCADE // THREE STAMPS DESCEND":vr==1?"COUNTERSIGNED FINDINGS // THE SEALS WILL RETURN":"SEALED FINDINGS // LEAVE THE MARKED SEALS";case MourningNotaryEntity.TOLL->vr==3?"APPEAL WINDOW // NEAR · FAR · NEAR · FAR":vr==2?"FUNERAL RESONANCE // READ THE BELL-SHELLS":vr==1?"DUAL TOLL // SURVIVE TWO LAWS":"TEMPORARY LAW // "+(b.view().getInt("rule")==1?"KEEP MOVING":"REMAIN STILL");case MourningNotaryEntity.JUDGMENT->vr==3?"FINAL NOTICE // COUNT · MOVE · OBJECT AT THE LAST INSTANT":vr==2?"FINAL APPEAL // GATE · SEAL · BELL · LAW":vr==1?"TRIPLICATE JUDGMENT // TWO LINES · TWO LAWS":"JOINT JUDGMENT // THREE LAWS AT ONCE";case MourningNotaryEntity.UNBOUND->"DEFENSE SIGNATURE BROKEN // STRIKE NOW";case MourningNotaryEntity.COLLAPSE->"FINAL ENTRY // SIGNATURE WITHHELD";default->"THE RECORD OBSERVES";};}
}
