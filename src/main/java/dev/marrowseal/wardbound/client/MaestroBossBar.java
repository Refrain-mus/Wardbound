package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.MaestroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A compact conductor's score instead of the vanilla boss plate. */
@Mod.EventBusSubscriber(modid= Wardbound.MODID,value= Dist.CLIENT)
public final class MaestroBossBar {
    private MaestroBossBar(){}
    @SubscribeEvent public static void draw(CustomizeGuiOverlayEvent.BossEventProgress e){
        if(!(e.getBossEvent().getName().getContents() instanceof TranslatableContents t)||!t.getKey().equals("entity.wardbound.maestro"))return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;MaestroEntity boss=null;
        for(var ent:mc.level.entitiesForRendering())if(ent instanceof MaestroEntity m){boss=m;break;}
        e.setCanceled(true);e.setIncrement(54);var g=e.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2-128,y=e.getY()-2;
        g.fill(x,y,x+256,y+44,0xe60a0810);g.fill(x+1,y+1,x+255,y+43,0xff2a2232);
        for(int staff=0;staff<5;staff++)g.fill(x+13,y+25+staff*3,x+243,y+26+staff*3,0xff51445f);
        g.drawString(mc.font,"MAESTRO",x+14,y+7,0xffeee5d2,false);
        String phase=boss==null?"PERFORMANCE":boss.phase()==1?"I · TEMPO":boss.phase()==2?"II · DISSONANCE":"III · CRESCENDO";
        g.drawString(mc.font,phase,x+181-mc.font.width(phase),y+7,boss!=null&&boss.phase()>=3?0xffe89bba:0xffbca7d2,false);
        float progress=Math.max(0,Math.min(1,e.getBossEvent().getProgress()));int fill=Math.round(220*progress);
        g.fill(x+18,y+27,x+18+fill,y+36,boss!=null&&boss.phase()>=3?0xffa84773:boss!=null&&boss.phase()==2?0xff805aa0:0xff69547f);
        g.fill(x+18,y+27,x+18+fill,y+29,0xffead9f0);
        for(int i=1;i<8;i++)g.fill(x+18+i*27,y+26,x+19+i*27,y+37,0xff17121d);
        String cue=boss==null?"LISTEN":label(boss.combatState());
        g.drawString(mc.font,cue,x+14,y+15,0xffa99cb0,false);
        if(boss!=null){String beat="♩ "+boss.combatBeat();g.drawString(mc.font,beat,x+240-mc.font.width(beat),y+15,0xffd8cadc,false);}
    }
    private static String label(int state){return switch(state){
        case MaestroEntity.INTRO -> "THE HALL IS COUNTING IN";
        case MaestroEntity.CONDUCT -> "DOWNBEAT · READ THE BATON";
        case MaestroEntity.STACCATO -> "STACCATO · THREE CUTS";
        case MaestroEntity.LEGATO -> "LEGATO · FOLLOW THE SWEEP";
        case MaestroEntity.NOTE_BARRAGE -> "BARRAGE · LEAVE THE LINE";
        case MaestroEntity.SILENCE -> "SILENCE · LEAVE THE FIELD";
        case MaestroEntity.ORCHESTRA -> "ORCHESTRA · CROSSING PARTS";
        case MaestroEntity.DASH -> "LUNGE · BREAK THE SIGHTLINE";
        case MaestroEntity.COUNTER_GUARD -> "FERMATA · DO NOT STRIKE";
        case MaestroEntity.COUNTER_RIPOSTE -> "RIPOSTE · TOO LATE";
        case MaestroEntity.CRESCENDO -> "GRAND CRESCENDO · MOVE WITH THE RINGS";
        case MaestroEntity.PHASE -> "THE SCORE IS BEING REWRITTEN";
        case MaestroEntity.DYING -> "CURTAIN CALL";
        default -> "LISTEN FOR THE NEXT MEASURE";
    };}
}
