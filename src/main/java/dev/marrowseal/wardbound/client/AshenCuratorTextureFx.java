package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.boss.*;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;
import java.util.*;

/**
 * Ashen Curator's authored world presentation. Server hazards remain authoritative;
 * this class paints their exact geometry with archive textures and Lodestone motes.
 */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class AshenCuratorTextureFx {
    private static final MultiBufferSource.BufferSource FX=MultiBufferSource.immediate(new BufferBuilder(8192));
    private static final int IVORY=0xffd8c9a8, EMBER=0xffc56d45, SAGE=0xff9fc5aa, ASH=0xff70685b, GOLD=0xffbd9d65;
    private static final Color C_IVORY=new Color(226,209,174), C_EMBER=new Color(205,101,62), C_SAGE=new Color(145,193,162), C_ASH=new Color(93,88,78), C_GOLD=new Color(201,165,103);
    private static final Map<UUID,ClientState> CLIENT=new HashMap<>();

    private static final class ClientState {
        int state=-1, kept, broken, chapter=1, clock=-1, bookState=-1;
        final Set<Integer> activated=new HashSet<>(), shotBursts=new HashSet<>();
    }

    public static ResourceLocation tex(String name){return new ResourceLocation(Wardbound.MODID,"textures/vfx/ashen_curator/"+name+".png");}

    @SubscribeEvent public static void draw(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        PoseStack p=e.getPoseStack();Vec3 camera=e.getCamera().getPosition();int owners=0;
        RenderSystem.setShaderColor(1,1,1,1);
        try{
            for(var entity:mc.level.entitiesForRendering()){
                if(!(entity instanceof AshenCuratorEntity boss)||boss.isRemoved()||boss.distanceToSqr(camera)>14400||++owners>4)continue;
                CompoundTag v=boss.view();int state=v.getInt("state"),clock=v.getInt("clock"),detail=v.getInt("detail");
                p.pushPose();
                try{
                    p.translate(boss.getX()-camera.x,boss.getY()+.045-camera.y,boss.getZ()-camera.z);
                    outline(p,0,0,20,20,.045,0xff62685f,.42f);
                    stateBase(p,e,boss,state,clock,v,detail);
                    arenaGlyphs(p,e,state,clock);
                    if(state==AshenCuratorEntity.COLLAPSE)continue;

                    ListTag hazards=v.getList("hazards",Tag.TAG_COMPOUND);
                    int refuges=v.getInt("kept")|(state==AshenCuratorEntity.ARCHIVE?1<<v.getInt("fallback"):0);
                    for(int i=0;i<Math.min(96,hazards.size());i++){
                        var h=AshenCuratorEntity.Hazard.read(hazards.getCompound(i));long age=v.getLong("time")-h.born();
                        if(age<0||age>=h.warning()+h.duration())continue;
                        boolean live=CuratorPatterns.active(age,h.warning(),h.duration());
                        float warnPulse=.58f+.16f*(float)Math.sin((age+h.id())*.22);
                        int color=live?EMBER:IVORY;
                        if(h.shape()==CuratorPatterns.RECT){
                            if(state==AshenCuratorEntity.SWEEP)ground(p,"sweep_shelf",h.x(),h.z(),h.a()*2,h.b()*2,live?.60f:.42f);
                            else ground(p,live?"archive_heat":"archive_warning",h.x(),h.z(),Math.min(6,h.a()*2),Math.min(6,h.b()*2),live?.72f:warnPulse);
                            if(live)heatRect(p,h.x()-h.a(),h.z()-h.b(),h.x()+h.a(),h.z()+h.b(),refuges,0);
                            outline(p,h.x(),h.z(),h.a(),h.b(),live?.12:.055,color,live?.95f:.78f);
                            if(live)ground(p,"impact_stamp",h.x(),h.z(),Math.min(3.2,h.a()*1.5),Math.min(3.2,h.b()*1.5),.44f);
                        }else if(h.shape()==CuratorPatterns.RING){
                            double outer=h.a()+h.b();
                            ground(p,"folio_ring",h.x(),h.z(),outer*2,outer*2,live?.70f:.52f);
                            circle(p,h.x(),h.z(),Math.max(.2,h.a()-h.b()),.05,color,.72f);
                            circle(p,h.x(),h.z(),outer,live?.12:.055,color,.92f);
                            if(live)for(int j=0;j<64;j++){
                                double a=j*Math.PI/32,x=h.x()+Math.cos(a)*h.a(),z=h.z()+Math.sin(a)*h.a();
                                if(CuratorPatterns.refuge(x,z,refuges))continue;
                                p.pushPose();p.translate(x,.006,z);p.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));flat(p,0,0,h.b()*2,h.a()*Math.PI/32+.025,EMBER,.12f);p.popPose();
                            }
                        }else{
                            ground(p,"errata_mark",h.x(),h.z(),h.a()*2.15,h.a()*2.15,live?.78f:warnPulse);
                            circle(p,h.x(),h.z(),h.a(),live?.11:.055,color,.90f);
                            if(!live){
                                double t=Math.min(1,age/(double)Math.max(1,h.warning()));
                                billboard(p,e,"page",h.x(),.5+(1-t)*2.8,h.z(),.7,.96,.76f);
                            }else{
                                ground(p,"impact_stamp",h.x(),h.z(),h.a()*1.55,h.a()*1.55,.56f);
                            }
                        }
                    }

                    // The living grimoire's pages are server-authored projectiles, not decorative guesses.
                    drawBookShots(p,e,v);

                    // Refuge/index layer is deliberately drawn after danger ink.
                    p.pushPose();p.translate(0,.018,0);
                    for(int i=0;i<4;i++){
                        double[] s=CuratorPatterns.SEALS[i];
                        boolean kept=(v.getInt("kept")&(1<<i))!=0, broken=(v.getInt("broken")&(1<<i))!=0, fallback=(state==AshenCuratorEntity.ARCHIVE&&v.getInt("fallback")==i);
                        if(kept||fallback){
                            ground(p,kept?"preserve_bloom":"archive_refuge",s[0],s[1],7,7,kept?.92f:.76f);
                            outline(p,s[0],s[1],3.5,3.5,.095,SAGE,.98f);
                        }else if(broken){
                            ground(p,"break_fracture",s[0],s[1],5.3,5.3,.84f);
                            outline(p,s[0],s[1],3.5,3.5,.07,EMBER,.76f);
                        }
                        if(state==AshenCuratorEntity.ARCHIVE&&!kept&&!broken){
                            ground(p,"index_seal",s[0],s[1],3.4,3.4,.80f);
                            int[] charges=v.getIntArray("charge");float progress=charges.length>i?Math.min(1,charges[i]/24f):0;
                            if(progress>0){
                                ground(p,"preserve_bloom",s[0],s[1],3.4+progress*2.2,3.4+progress*2.2,.18f+.46f*progress);
                                flat(p,s[0],s[1]+2.05,4.0*progress,.075,SAGE,.95f);
                            }
                        }
                    }
                    p.popPose();
                }finally{p.popPose();}
            }
        }finally{FX.endBatch();RenderSystem.setShaderColor(1,1,1,1);}
    }

    private static void stateBase(PoseStack p,RenderLevelStageEvent e,AshenCuratorEntity boss,int state,int clock,CompoundTag v,int detail){
        int vr=v.getInt("variant");
        if(state==AshenCuratorEntity.MANIFEST){
            float a=Math.min(1,clock/45f)*Math.max(.25f,1-Math.max(0,clock-78)/28f);
            ground(p,"manifest_archive",0,0,21.5,21.5,.84f*a);
            ground(p,"ash_ring",0,0,14.5,14.5,.56f*a);
            for(int i=0;i<4;i++){double ang=i*Math.PI/2+.55;billboard(p,e,"glyph_column",Math.cos(ang)*7.2,2.6,Math.sin(ang)*7.2,1.35,6.2,.66f*a);}
        }else if(state==AshenCuratorEntity.TRANSITION){
            float pulse=.58f+.18f*(float)Math.sin(clock*.16);
            float close=clock<22?1f-clock/22f:0f;
            float inhale=clock>=18&&clock<44?(clock-18)/26f:clock>=44?1f:0f;
            float wave=clock<=40?0f:Math.min(1f,(clock-40)/26f);
            ground(p,"redaction_phase",0,0,25,25,pulse+.18f*wave);
            ground(p,"ember_ring",0,0,17+wave*7,17+wave*7,.44f+.22f*wave);
            if(close>0f){
                billboard(p,e,"open_book",0,3.1,0,4.7,3.1,.70f*close);
                billboard(p,e,"open_binding_glow",0,3.45,0,3.2,3.2,.42f*close);
            }
            if(inhale>0f)for(int i=0;i<6;i++){double ang=i*Math.PI/3+clock*.014;billboard(p,e,"page",Math.cos(ang)*(7.0-inhale*2.5),1.4+(i%2)*1.2+inhale*.8,Math.sin(ang)*(7.0-inhale*2.5),.9,1.3,.30f+.32f*inhale);} 
            if(wave>0f){
                ground(p,"incineration_field",0,0,14+wave*26,14+wave*26,.28f+.36f*wave);
                for(int i=0;i<4;i++){double ang=i*Math.PI/2+clock*.06;billboard(p,e,"glyph_column",Math.cos(ang)*(4.2+wave*5.2),2.1+wave*1.4,Math.sin(ang)*(4.2+wave*5.2),1.1+wave*.35,4.8+wave*1.7,.32f+.34f*wave);} 
            }
        }else if(state==AshenCuratorEntity.ARCHIVE){
            ground(p,"index_seal",0,0,11.5,11.5,.42f);
            if(clock>=v.getInt("deadline")){
                float pulse=.68f+.20f*(float)Math.sin(clock*.34);
                ground(p,"incineration_field",0,0,39.5,39.5,pulse);
                billboard(p,e,"glyph_column",0,3.0,0,2.9,9.0,.76f);
            }
        }else if(state==AshenCuratorEntity.OPEN){
            float pulse=.72f+.20f*(float)Math.sin(clock*.24);
            ground(p,"open_binding_glow",0,0,10.5,10.5,pulse);
            billboard(p,e,"open_book",0,3.4,0,5.0,3.4,.28f);
            billboard(p,e,"open_binding_glow",0,3.7,0,3.8,3.8,.66f);
            if(boss.bookFlameActive())bookFlame(p,e,boss,.90f+.08f*(float)Math.sin(clock*.38),chapterLength(v));
        }else if(state==AshenCuratorEntity.COLLAPSE){
            float fade=Math.max(0,1-clock/120f);
            ground(p,"collapse_archive",0,0,12+clock*.06,12+clock*.06,.84f*fade);
            ground(p,"break_fracture",0,0,8.5+clock*.04,8.5+clock*.04,.38f*fade);
            if(clock>=70){float ash=Math.min(1f,(clock-70)/34f);ground(p,"ash_ring",0,0,6.5+ash*5.5,6.5+ash*5.5,.18f+.28f*ash);}
            billboard(p,e,"collapse_archive",0,2.4+clock*.025,0,4.6,4.6,.76f*fade);
            billboard(p,e,"book_flame",0,3.05,0,2.2,3.2,.22f*Math.max(0,1-clock/36f));
            if(detail>0&&clock>18)for(int i=0;i<12;i++){double a=i*Math.PI/6+clock*.02;float pageFade=Math.max(0f,1f-Math.max(0,clock-76)/34f);billboard(p,e,"page",Math.cos(a)*(2.2+clock*.03),.6+(clock-18)*.04+(i%4)*.42,Math.sin(a)*(2.2+clock*.03),.45,.62,.70f*fade*pageFade);}
        }else if(state==AshenCuratorEntity.SWEEP){
            if(vr==1) ground(p,"archive_refuge",0,0,39,7.4,.31f);
            else ground(p,"archive_refuge",0,0,7.4,39,.31f);
            if(vr==1) ground(p,"catalog",0,0,16,16,.24f+.06f*(float)Math.sin(clock*.18));
        }
        if(state==AshenCuratorEntity.RINGS && vr==1){
            ground(p,"folio_ring",-7.2,0,9.6,9.6,.38f);ground(p,"folio_ring",7.2,0,9.6,9.6,.38f);
            ground(p,"catalog",0,0,28,28,.18f+.04f*(float)Math.sin(clock*.16));
        }
        if(state==AshenCuratorEntity.ERRATA && vr==1){
            billboard(p,e,"page",0,3.3,0,2.1,2.6,.44f+.08f*(float)Math.sin(clock*.25));
            billboard(p,e,"catalog",0,2.5,0,2.9,2.9,.22f);
        }
    }


    private static double chapterLength(CompoundTag v){return v.getInt("chapter")>=2?15.8:13.8;}
    private static void bookFlame(PoseStack p,RenderLevelStageEvent e,AshenCuratorEntity boss,float alpha,double length){
        if(alpha<=0)return;
        Vec3 rel=boss.bookMuzzle().subtract(boss.position()),dir=boss.bookDirection();
        for(int i=0;i<7;i++){
            double f=(i+.35)/7.0,step=f*length;Vec3 q=rel.add(dir.scale(step));float spread=.90f+(float)f*1.55f;
            billboard(p,e,"book_flame",q.x,q.y,q.z,spread*2.0,spread*1.55,Math.max(.16f,alpha-i*.085f));
            if((i&1)==0)billboard(p,e,"book_flare",q.x,q.y,q.z,spread*2.4,spread*2.4,Math.max(.10f,alpha*.42f-i*.04f));
        }
    }

    private static void drawBookShots(PoseStack p,RenderLevelStageEvent e,CompoundTag v){
        ListTag shots=v.getList("book_shots",Tag.TAG_COMPOUND);Minecraft mc=Minecraft.getInstance();double extrap=mc.level==null?0:Math.max(0,Math.min(2.0,mc.level.getGameTime()-v.getLong("time")+mc.getFrameTime()));
        for(int i=0;i<Math.min(48,shots.size());i++){
            CompoundTag q=shots.getCompound(i);int age=q.getInt("age"),burst=q.getInt("burst"),mode=q.getInt("mode");double k=burst>0?0:extrap;double x=q.getDouble("x")+q.getDouble("vx")*k,y=q.getDouble("y")+q.getDouble("vy")*k,z=q.getDouble("z")+q.getDouble("vz")*k;
            if(burst>0){
                billboard(p,e,"impact",x,y,z,1.5,1.5,.70f);
                billboard(p,e,"ash_ribbon",x,y+.05,z,2.0,1.35,.48f);
                continue;
            }
            double vx=q.getDouble("vx"),vy=q.getDouble("vy"),vz=q.getDouble("vz");float roll=(float)(age*(mode==AshenCuratorEntity.BOOK_SPIRAL?31:18)+(q.getInt("id")%7)*17);
            flyingPage(p,e,x,y,z,.72,.96,roll,.92f);
            for(int trail=1;trail<=2;trail++){
                double trailK=trail*.55;flyingPage(p,e,x-vx*trailK,y-vy*trailK,z-vz*trailK,.58-trail*.08,.78-trail*.10,roll-trail*18,.30f-trail*.07f);
            }
        }
    }
    private static void flyingPage(PoseStack p,RenderLevelStageEvent e,double x,double y,double z,double w,double h,float roll,float alpha){
        if(alpha<=0)return;p.pushPose();p.translate(x,y,z);p.mulPose(e.getCamera().rotation());p.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));p.scale((float)w/2,(float)h/2,1);quad(p,"page",alpha,0xffffffff,true);p.popPose();
    }
    /** Subtract protected shelves from heat wash so safe cells contain no hazard fill. */
    private static void heatRect(PoseStack p,double x0,double z0,double x1,double z1,int mask,int slot){
        if(x1<=x0||z1<=z0)return;
        while(slot<4&&(mask&(1<<slot))==0)slot++;
        if(slot>=4){flat(p,(x0+x1)/2,(z0+z1)/2,x1-x0,z1-z0,EMBER,.10f);return;}
        double[] s=CuratorPatterns.SEALS[slot];double a=Math.max(x0,s[0]-3.5),b=Math.max(z0,s[1]-3.5),c=Math.min(x1,s[0]+3.5),d=Math.min(z1,s[1]+3.5);
        if(a>=c||b>=d){heatRect(p,x0,z0,x1,z1,mask,slot+1);return;}
        heatRect(p,x0,z0,a,z1,mask,slot+1);heatRect(p,c,z0,x1,z1,mask,slot+1);heatRect(p,a,z0,c,b,mask,slot+1);heatRect(p,a,d,c,z1,mask,slot+1);
    }

    private static void arenaGlyphs(PoseStack p,RenderLevelStageEvent e,int state,int clock){
        if(state==AshenCuratorEntity.COLLAPSE)return;
        boolean active=state==AshenCuratorEntity.SWEEP||state==AshenCuratorEntity.RINGS||state==AshenCuratorEntity.ERRATA||state==AshenCuratorEntity.ARCHIVE||state==AshenCuratorEntity.OPEN||state==AshenCuratorEntity.TRANSITION;
        if(!active)return;
        float blink=.22f+.16f*(float)Math.sin(clock*.45);
        for(int i=0;i<4;i++){
            double[] s=CuratorPatterns.SEALS[i];
            billboard(p,e,"glyph_column",s[0],1.8+(i%2)*.25,s[1],.85,2.4,.18f+blink);
        }
        if(state==AshenCuratorEntity.OPEN||state==AshenCuratorEntity.TRANSITION){
            billboard(p,e,"glyph_column",0,2.7,0,1.2,4.6,.24f+blink*.9f);
        }
    }

    @SubscribeEvent public static void motes(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused()){CLIENT.clear();return;}
        long now=mc.level.getGameTime();boolean reduced=WardConfig.accessibilityReduceMotion;int cadence=reduced?10:4,owners=0;
        Set<UUID> liveBosses=new HashSet<>();
        for(var ent:mc.level.entitiesForRendering())if(ent instanceof AshenCuratorEntity boss&&!boss.isRemoved()&&boss.distanceToSqr(mc.player)<10000&&++owners<=4){
            liveBosses.add(boss.getUUID());CompoundTag v=boss.view();ClientState cs=CLIENT.computeIfAbsent(boss.getUUID(),k->new ClientState());
            int state=v.getInt("state"),kept=v.getInt("kept"),broken=v.getInt("broken"),chapter=v.getInt("chapter"),clock=v.getInt("clock");
            if(cs.chapter!=chapter && chapter>=2){
                MasterCameraShake.pulse(reduced?.18f:.36f,reduced?10:20,3.1f,boss.getId()*311L+chapter,MasterAnimationBeatPacket.CURATOR);
                ashTorus(mc,boss.position().add(0,2.9,0),7.4,60,C_EMBER,C_ASH,reduced?18:64,46,boss.getId()+chapter*91);
                sphereAsh(mc,boss.position().add(0,2.7,0),6.2,C_EMBER,C_GOLD,reduced?16:54,48,boss.getId()+chapter*131);
                groundQuake(mc,boss.position().add(0,.08,0),10.2,C_EMBER,C_ASH,reduced?8:36,boss.getId()*19L+clock);
            }
            if(cs.state!=state){
                stateBurst(mc,boss,state,reduced);cs.state=state;cs.activated.clear();
                if(state==AshenCuratorEntity.COLLAPSE){
                    pageVortex(mc,boss.position().add(0,.5,0),6.0,6.4,C_IVORY,C_ASH,reduced?18:56,56,boss.getId()+2231);
                    burst(mc,boss.position().add(0,2.1,0),C_IVORY,C_ASH,reduced?16:52,3.4,60);
                }
            }
            int bookState=v.getInt("book_state");
            if(cs.bookState!=bookState){
                Vec3 muzzle=boss.bookMuzzle();
                switch(bookState){
                    case AshenCuratorEntity.BOOK_OPENING -> ringWisps(mc,muzzle,1.25,C_IVORY,C_GOLD,reduced?8:20,24);
                    case AshenCuratorEntity.BOOK_CHARGE -> {sphereAsh(mc,muzzle,1.15,C_GOLD,C_EMBER,reduced?8:24,26,boss.getId()+v.getInt("book_serial")*17);if(!reduced)MasterCameraShake.pulse(.08f,7,4.1f,boss.getId()*521L+bookState,MasterAnimationBeatPacket.CURATOR);}
                    case AshenCuratorEntity.BOOK_RELEASE -> {burst(mc,muzzle,C_EMBER,C_GOLD,reduced?8:26,1.15,30);ringWisps(mc,muzzle,1.55,C_EMBER,C_IVORY,reduced?10:28,28);}
                    case AshenCuratorEntity.BOOK_RECOIL -> burst(mc,muzzle,C_ASH,C_IVORY,reduced?5:14,.70,20);
                    case AshenCuratorEntity.BOOK_DEATH -> pageVortex(mc,boss.position().add(0,.4,0),5.4,6.2,C_IVORY,C_ASH,reduced?16:48,52,boss.getId()+1777);
                    default -> { }
                }
                cs.bookState=bookState;
            }
            if(state==AshenCuratorEntity.TRANSITION && !reduced){
                if(cs.clock<20 && clock>=20)MasterCameraShake.pulse(.24f,12,3.4f,boss.getId()*43L+20,MasterAnimationBeatPacket.CURATOR);
                if(cs.clock<42 && clock>=42){MasterCameraShake.pulse(.34f,16,4.2f,boss.getId()*43L+42,MasterAnimationBeatPacket.CURATOR);groundQuake(mc,boss.position().add(0,.06,0),12.6,C_EMBER,C_GOLD,40,boss.getId()*47L+42);} 
            }
            int newKept=kept&~cs.kept,newBroken=broken&~cs.broken;
            for(int i=0;i<4;i++){
                double[] seal=CuratorPatterns.SEALS[i];Vec3 pos=boss.position().add(seal[0],.12,seal[1]);
                if((newKept&(1<<i))!=0)burst(mc,pos,C_SAGE,C_IVORY,reduced?8:22,.9,28);
                if((newBroken&(1<<i))!=0){burst(mc,pos,C_EMBER,C_ASH,reduced?8:24,1.0,30);EncounterAftermathFx.recordCuratorSeal(new Vec3(pos.x,boss.getY(),pos.z),boss.getId()*97+i*31+clock);}
            }
            cs.kept=kept;cs.broken=broken;

            ListTag hazards=v.getList("hazards",Tag.TAG_COMPOUND);Set<Integer> present=new HashSet<>();
            for(int i=0;i<Math.min(96,hazards.size());i++){
                var h=AshenCuratorEntity.Hazard.read(hazards.getCompound(i));present.add(h.id());long age=v.getLong("time")-h.born();
                if(age>=h.warning()&&age<h.warning()+h.duration()&&cs.activated.add(h.id())){impactBurst(mc,boss,h,reduced);EncounterAftermathFx.recordCuratorHazard(boss,h,state);}
                if(now%cadence==0&&age>=0&&age<h.warning()+h.duration()){
                    if(age<h.warning() && state!=AshenCuratorEntity.COLLAPSE) hazardMote(mc,boss,h,false,i+now,reduced);
                    hazardMote(mc,boss,h,age>=h.warning(),i+now,reduced);
                }
            }
            cs.activated.retainAll(present);
            ListTag shots=v.getList("book_shots",Tag.TAG_COMPOUND);Set<Integer> presentShots=new HashSet<>();
            for(int i=0;i<Math.min(48,shots.size());i++){
                CompoundTag q=shots.getCompound(i);int id=q.getInt("id");presentShots.add(id);Vec3 pos=boss.position().add(q.getDouble("x"),q.getDouble("y"),q.getDouble("z"));
                int burstTicks=q.getInt("burst"),mode=q.getInt("mode");
                if(burstTicks>0){if(cs.shotBursts.add(id)){burst(mc,pos,mode==AshenCuratorEntity.BOOK_HOMING?C_IVORY:C_EMBER,C_ASH,reduced?5:16,.75,26);EncounterAftermathFx.recordCuratorPage(new Vec3(pos.x,boss.getY(),pos.z),boss.getId()*211+id);}}
                else if(now%Math.max(2,cadence/2)==0){Color a=mode==AshenCuratorEntity.BOOK_HOMING?C_IVORY:mode==AshenCuratorEntity.BOOK_SPIRAL?C_GOLD:C_EMBER;wisp(mc.level,pos.x,pos.y,pos.z,a,C_ASH,reduced?.055f:.085f,18,-q.getDouble("vx")*.025,.008-q.getDouble("vy")*.01,-q.getDouble("vz")*.025,.012);}
            }
            cs.shotBursts.retainAll(presentShots);
            if((state==AshenCuratorEntity.SWEEP||state==AshenCuratorEntity.RINGS||state==AshenCuratorEntity.ERRATA||state==AshenCuratorEntity.ARCHIVE||state==AshenCuratorEntity.OPEN) && now%Math.max(2,cadence)==0){
                groundQuake(mc,boss.position().add(0,.05,0),state==AshenCuratorEntity.OPEN?7.6:9.2,C_EMBER,state==AshenCuratorEntity.OPEN?C_GOLD:C_ASH,reduced?4:14,boss.getId()*61L+clock);
            }
            if(now%cadence==0&&v.getInt("detail")>0&&state!=AshenCuratorEntity.REST){
                int n=reduced?1:(v.getInt("chapter")>=2?4:2);
                for(int i=0;i<n;i++){double a=now*.022+i*Math.PI*2/n,r=3.6+(i%2)*1.1;
                    wisp(mc.level,boss.getX()+Math.cos(a)*r,boss.getY()+.7+(i%3)*.55,boss.getZ()+Math.sin(a)*r,state==AshenCuratorEntity.OPEN?C_SAGE:C_GOLD,state==AshenCuratorEntity.TRANSITION?C_EMBER:C_ASH,.08f,18,-Math.sin(a)*.012,.018,Math.cos(a)*.012,.012);
                }
                if(boss.bookFlameActive()){
                    Vec3 dir=boss.bookDirection(),origin=boss.bookMuzzle();int jets=reduced?4:10;
                    for(int i=0;i<jets;i++){
                        double step=.8+i*1.18;Vec3 q=origin.add(dir.scale(step));
                        wisp(mc.level,q.x,q.y+(i%2)*.10,q.z,C_EMBER,C_GOLD,reduced?.08f:.12f,24+i*3,dir.x*.01,dir.y*.01+.008+Math.max(0,i-1)*.002,dir.z*.01,.018);
                    }
                    if(now%2==0)vanillaFlamePillar(mc,boss,reduced?9.0:(v.getInt("chapter")>=2?15.8:13.8),reduced?18:40,now);
                    if(!reduced && now%6==0)bookCone(mc,boss,v.getInt("chapter")>=2?15.8:13.8,C_EMBER,C_GOLD,28,30,boss.getId()+(int)now);
                } else if(bookState==AshenCuratorEntity.BOOK_CHARGE && now%Math.max(2,cadence)==0){
                    Vec3 m=boss.bookMuzzle();sphereAsh(mc,m,.85,C_GOLD,C_IVORY,reduced?5:14,18,boss.getId()+(int)now);
                }
            }
            cs.chapter=chapter;cs.clock=clock;
        }
        CLIENT.keySet().retainAll(liveBosses);
    }

    private static void stateBurst(Minecraft mc,AshenCuratorEntity boss,int state,boolean reduced){
        Vec3 p=boss.position().add(0,.15,0);int n=reduced?10:32;
        if(!reduced){
            float s=switch(state){case AshenCuratorEntity.TRANSITION->.62f;case AshenCuratorEntity.COLLAPSE->.48f;case AshenCuratorEntity.ARCHIVE->.26f;case AshenCuratorEntity.OPEN->.32f;case AshenCuratorEntity.MANIFEST->.28f;default->.16f;};
            int t=switch(state){case AshenCuratorEntity.TRANSITION->30;case AshenCuratorEntity.COLLAPSE->24;case AshenCuratorEntity.MANIFEST->18;default->12;};
            MasterCameraShake.pulse(s,t,2.4f,boss.getId()*131L+state*17L,MasterAnimationBeatPacket.CURATOR);
            groundQuake(mc,boss.position().add(0,.08,0),state==AshenCuratorEntity.TRANSITION?10.5:state==AshenCuratorEntity.ARCHIVE?9.0:6.5,C_ASH,state==AshenCuratorEntity.OPEN?C_SAGE:C_EMBER,42,boss.getId()*37L+state);
        }
        switch(state){
            case AshenCuratorEntity.MANIFEST -> {ringWisps(mc,p,6.5,C_GOLD,C_ASH,n,40);ashTorus(mc,p.add(0,3.2,0),6.4,28,C_GOLD,C_ASH,reduced?18:54,42,boss.getId());sphereAsh(mc,p.add(0,3.4,0),5.0,C_IVORY,C_EMBER,reduced?16:48,40,boss.getId()+19);burst(mc,p.add(0,2.4,0),C_IVORY,C_EMBER,n,2.2,38);}
            case AshenCuratorEntity.SWEEP -> {ringWisps(mc,p,10.0,C_IVORY,C_SAGE,n,30);pageVortex(mc,boss.position().add(0,.5,0),6.4,5.2,C_IVORY,C_EMBER,reduced?12:34,34,boss.getId()+37);}
            case AshenCuratorEntity.RINGS -> {ringWisps(mc,p,5.5,C_GOLD,C_EMBER,n,34);ringWisps(mc,p,10.0,C_EMBER,C_ASH,n,38);ashTorus(mc,p.add(0,2.6,0),7.2,62,C_EMBER,C_GOLD,reduced?18:58,36,boss.getId()+53);pageVortex(mc,boss.position().add(0,1.0,0),4.8,4.6,C_GOLD,C_IVORY,reduced?12:36,38,boss.getId()+71);}
            case AshenCuratorEntity.ERRATA -> {burst(mc,p.add(0,2.0,0),C_IVORY,C_EMBER,n,1.8,34);pageVortex(mc,boss.position().add(0,1.0,0),4.6,6.0,C_IVORY,C_GOLD,reduced?10:32,36,boss.getId()+89);}
            case AshenCuratorEntity.ARCHIVE -> {ringWisps(mc,p,11.0,C_GOLD,C_SAGE,n,44);ashTorus(mc,p.add(0,3.0,0),9.2,34,C_GOLD,C_SAGE,reduced?22:72,48,boss.getId()+109);sphereAsh(mc,p.add(0,3.1,0),7.4,C_ASH,C_GOLD,reduced?20:64,48,boss.getId()+131);burst(mc,p.add(0,1.4,0),C_IVORY,C_GOLD,n,2.0,34);}
            case AshenCuratorEntity.OPEN -> {ringWisps(mc,p,2.6,C_SAGE,C_IVORY,n,38);pageVortex(mc,boss.position().add(0,.7,0),3.6,4.8,C_SAGE,C_IVORY,reduced?12:38,42,boss.getId()+167);burst(mc,boss.bookMuzzle(),C_SAGE,C_GOLD,n,1.3,42);}
            case AshenCuratorEntity.TRANSITION -> {ringWisps(mc,p,8.5,C_EMBER,C_ASH,n,46);ashTorus(mc,p.add(0,2.8,0),7.8,58,C_EMBER,C_ASH,reduced?24:78,50,boss.getId()+181);sphereAsh(mc,p.add(0,3.0,0),6.4,C_EMBER,C_GOLD,reduced?22:72,52,boss.getId()+199);burst(mc,p.add(0,2.5,0),C_EMBER,C_GOLD,n,2.8,48);}
            case AshenCuratorEntity.COLLAPSE -> {sphereAsh(mc,p.add(0,2.4,0),4.8,C_IVORY,C_ASH,reduced?18:58,52,boss.getId()+223);pageVortex(mc,boss.position().add(0,.5,0),5.2,5.5,C_IVORY,C_ASH,reduced?16:46,54,boss.getId()+239);burst(mc,p.add(0,2.2,0),C_IVORY,C_ASH,reduced?14:46,3.2,56);}
            default -> { }
        }
    }

    private static void impactBurst(Minecraft mc,AshenCuratorEntity boss,AshenCuratorEntity.Hazard h,boolean reduced){
        int n=reduced?6:16;
        if(!reduced)MasterCameraShake.pulse(h.shape()==CuratorPatterns.RECT?.18f:.14f,8,3.6f,boss.getId()*211L+h.id(),MasterAnimationBeatPacket.CURATOR);
        if(h.shape()==CuratorPatterns.RING){
            Vec3 c=boss.position().add(h.x(),.12,h.z());ringWisps(mc,c,h.a(),C_EMBER,C_GOLD,n*2,26);
        }else if(h.shape()==CuratorPatterns.RECT){
            for(int i=0;i<n;i++){double t=i/(double)Math.max(1,n-1),x=h.x()-h.a()+2*h.a()*t;
                wisp(mc.level,boss.getX()+x,boss.getY()+.12,boss.getZ()+h.z()-h.b(),C_EMBER,C_ASH,.12f,22,0,.025,0,.018);
                wisp(mc.level,boss.getX()+x,boss.getY()+.12,boss.getZ()+h.z()+h.b(),C_EMBER,C_GOLD,.12f,22,0,.025,0,.018);
            }
        }else burst(mc,boss.position().add(h.x(),.15,h.z()),C_EMBER,C_IVORY,n*2,h.a()*.65,28);
    }

    private static void hazardMote(Minecraft mc,AshenCuratorEntity boss,AshenCuratorEntity.Hazard h,boolean live,long seed,boolean reduced){
        Random r=new Random(seed*0x9E3779B97F4A7C15L+h.id()*31L);double x,z;
        if(h.shape()==CuratorPatterns.RING){double a=r.nextDouble()*Math.PI*2;x=h.x()+Math.cos(a)*h.a();z=h.z()+Math.sin(a)*h.a();}
        else if(h.shape()==CuratorPatterns.PAGE){double a=r.nextDouble()*Math.PI*2,rr=Math.sqrt(r.nextDouble())*h.a();x=h.x()+Math.cos(a)*rr;z=h.z()+Math.sin(a)*rr;}
        else {x=h.x()+(r.nextDouble()*2-1)*h.a();z=h.z()+(r.nextBoolean()?h.b():-h.b());}
        Color a=live?C_EMBER:C_IVORY,b=live?C_ASH:C_GOLD;
        wisp(mc.level,boss.getX()+x,boss.getY()+.08+r.nextDouble()*.35,boss.getZ()+z,a,b,reduced?.055f:.085f,18,0,.014,0,.012);
    }

    private static void ringWisps(Minecraft mc,Vec3 pos,double radius,Color a,Color b,int count,int life){
        for(int i=0;i<count;i++){double ang=i*Math.PI*2/count;wisp(mc.level,pos.x+Math.cos(ang)*radius,pos.y,pos.z+Math.sin(ang)*radius,a,b,.10f,life,-Math.sin(ang)*.008,.018,Math.cos(ang)*.008,.012);}
    }
    private static void burst(Minecraft mc,Vec3 pos,Color a,Color b,int count,double spread,int life){
        Random r=new Random(Double.doubleToLongBits(pos.x*17+pos.y*31+pos.z*47)+count);
        for(int i=0;i<count;i++){double ox=(r.nextDouble()-.5)*spread*2,oy=(r.nextDouble()-.3)*spread,oz=(r.nextDouble()-.5)*spread*2;
            wisp(mc.level,pos.x+ox,pos.y+oy,pos.z+oz,a,b,.08f+r.nextFloat()*.09f,life,ox*.005,.018+Math.abs(oy)*.007,oz*.005,.025);
        }
    }
    private static void ashTorus(Minecraft mc,Vec3 pos,double radius,double tiltDeg,Color a,Color b,int count,int life,int seed){
        double tilt=Math.toRadians(tiltDeg),ct=Math.cos(tilt),st=Math.sin(tilt);Random r=new Random(seed*37L+5);
        for(int i=0;i<count;i++){double u=i*Math.PI*2/Math.max(1,count)+seed*.013,v=(i*2.399963229728653+seed*.021)%(Math.PI*2),tube=.35+.18*Math.sin(v);double rr=radius+tube*Math.cos(v);double x=rr*Math.cos(u),y=tube*Math.sin(v),z=rr*Math.sin(u);double y2=y*ct-z*st,z2=y*st+z*ct;wisp(mc.level,pos.x+x,pos.y+y2,pos.z+z2,(i&1)==0?a:b,(i&1)==0?b:a,.08f+r.nextFloat()*.07f,life,0,.008,0,.016);}
    }
    private static void sphereAsh(Minecraft mc,Vec3 pos,double radius,Color a,Color b,int count,int life,int seed){
        double golden=Math.PI*(3-Math.sqrt(5));Random r=new Random(seed*43L+7);for(int i=0;i<count;i++){double y=1-2*(i+.5)/Math.max(1,count),rr=Math.sqrt(Math.max(0,1-y*y)),ang=i*golden+seed*.01,rad=radius*(.88+r.nextDouble()*.18),x=Math.cos(ang)*rr*rad,z=Math.sin(ang)*rr*rad;wisp(mc.level,pos.x+x,pos.y+y*rad,pos.z+z,(i%3==0)?b:a,(i%3==0)?a:b,.07f+r.nextFloat()*.08f,life,-x*.0012,-y*.002,-z*.0012,.014);}
    }
    private static void pageVortex(Minecraft mc,Vec3 base,double radius,double height,Color a,Color b,int count,int life,int seed){
        Random r=new Random(seed*59L+11);for(int i=0;i<count;i++){double f=i/(double)Math.max(1,count-1),ang=seed*.017+f*Math.PI*7.5+(i%4)*.42,rr=radius*(.32+.68*(1-Math.abs(f-.5)*1.18)),x=Math.cos(ang)*rr,z=Math.sin(ang)*rr,y=f*height;wisp(mc.level,base.x+x,base.y+y,base.z+z,(i&1)==0?a:b,(i&1)==0?b:a,.08f+r.nextFloat()*.09f,life,-Math.sin(ang)*.014,.015+f*.007,Math.cos(ang)*.014,.02);}
    }
    private static void bookCone(Minecraft mc,AshenCuratorEntity boss,double length,Color a,Color b,int count,int life,int seed){
        if(mc.level==null)return;Vec3 forward=boss.bookDirection(),origin=boss.bookMuzzle();Vec3 right=new Vec3(-forward.z,0,forward.x);if(right.lengthSqr()<.001)right=new Vec3(1,0,0);else right=right.normalize();Vec3 up=right.cross(forward).normalize();Random r=new Random(seed*71L+13);
        for(int i=0;i<count;i++){double t=.10+r.nextDouble()*.90,dist=t*length,spread=(r.nextDouble()*2-1)*(.22+t*.85)*1.65,vertical=(r.nextDouble()-.45)*(.55+t*1.35);Vec3 q=origin.add(forward.scale(dist)).add(right.scale(spread)).add(up.scale(vertical));wisp(mc.level,q.x,q.y,q.z,(i&1)==0?a:b,(i&1)==0?b:a,.09f+r.nextFloat()*.10f,life,forward.x*(.018+.016*t),forward.y*(.018+.016*t)+.004,forward.z*(.018+.016*t),.018);}
    }
    private static void vanillaFlamePillar(Minecraft mc,AshenCuratorEntity boss,double length,int count,long seed){
        if(mc.level==null)return;Vec3 forward=boss.bookDirection(),origin=boss.bookMuzzle();Vec3 right=new Vec3(-forward.z,0,forward.x);if(right.lengthSqr()<.001)right=new Vec3(1,0,0);else right=right.normalize();Vec3 up=right.cross(forward).normalize();Random r=new Random(seed*97L+31);
        for(int i=0;i<count;i++){
            double t=.06+r.nextDouble()*.96,dist=t*length,spread=(r.nextDouble()*2-1)*(.16+t*.52),vertical=(r.nextDouble()-.40)*(.35+t*.85);Vec3 q=origin.add(forward.scale(dist)).add(right.scale(spread)).add(up.scale(vertical));
            double mx=forward.x*(.008+.014*t),my=forward.y*(.008+.014*t)+.006+.006*t,mz=forward.z*(.008+.014*t);
            mc.level.addParticle(ParticleTypes.FLAME,q.x,q.y,q.z,mx,my,mz);
            if((i&1)==0)mc.level.addParticle(ParticleTypes.SMOKE,q.x,q.y+.04,q.z,mx*.55,my*.65,mz*.55);
            if(i%3==0)mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,q.x,q.y+.08,q.z,mx*.75,my*.9,mz*.75);
        }
    }

    private static void groundQuake(Minecraft mc,Vec3 center,double radius,Color a,Color b,int count,long seed){
        Random r=new Random(seed);for(int i=0;i<count;i++){double ang=r.nextDouble()*Math.PI*2,rr=Math.sqrt(r.nextDouble())*radius;double x=center.x+Math.cos(ang)*rr,z=center.z+Math.sin(ang)*rr;double lift=.04+r.nextDouble()*.18;wisp(mc.level,x,center.y+r.nextDouble()*.12,z,(i&1)==0?a:b,(i&1)==0?b:a,.07f+r.nextFloat()*.06f,20+(i%7),Math.cos(ang)*.008,lift,Math.sin(ang)*.008,.018);}
    }

    private static void wisp(net.minecraft.world.level.Level level,double x,double y,double z,Color start,Color end,float scale,int lifetime,double mx,double my,double mz,double randomMotion){
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(start,end).build())
                .setTransparencyData(GenericParticleData.create(.86f,0f).build())
                .setScaleData(GenericParticleData.create(scale,0f).build())
                .setLifetime(lifetime).addMotion(mx,my,mz)
                .setRandomMotion(randomMotion,randomMotion*.7,randomMotion).enableNoClip().spawn(level,x,y,z);
    }

    private static void ground(PoseStack p,String texture,double x,double z,double w,double h,float alpha){if(alpha<=0)return;p.pushPose();p.translate(x,.006,z);p.scale((float)w/2,1,(float)h/2);quad(p,texture,alpha,0xffffffff,false);p.popPose();}
    private static void billboard(PoseStack p,RenderLevelStageEvent e,String texture,double x,double y,double z,double w,double h,float alpha){if(alpha<=0)return;p.pushPose();p.translate(x,y,z);p.mulPose(e.getCamera().rotation());p.scale((float)w/2,(float)h/2,1);quad(p,texture,alpha,0xffffffff,true);p.popPose();}
    private static void flat(PoseStack p,double x,double z,double w,double h,int color,float alpha){if(alpha<=0||w<=0||h<=0)return;p.pushPose();p.translate(x,.012,z);p.scale((float)w/2,1,(float)h/2);quad(p,"boundary",alpha,color,false);p.popPose();}
    private static void outline(PoseStack p,double x,double z,double a,double b,double width,int color,float alpha){flat(p,x-a,z,width,b*2,color,alpha);flat(p,x+a,z,width,b*2,color,alpha);flat(p,x,z-b,a*2,width,color,alpha);flat(p,x,z+b,a*2,width,color,alpha);}
    private static void circle(PoseStack p,double x,double z,double radius,double width,int color,float alpha){if(radius<=0)return;for(int i=0;i<64;i++){double a=i*Math.PI/32;p.pushPose();p.translate(x+Math.cos(a)*radius,0,z+Math.sin(a)*radius);p.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));flat(p,0,0,width,radius*Math.PI/32+.025,color,alpha);p.popPose();}}
    private static void quad(PoseStack p,String texture,float alpha,int color,boolean upright){
        VertexConsumer v=FX.getBuffer(RenderType.entityTranslucent(tex(texture)));
        if(upright){vertex(v,p,-1,-1,0,0,1,alpha,color,0,0,1);vertex(v,p,1,-1,0,1,1,alpha,color,0,0,1);vertex(v,p,1,1,0,1,0,alpha,color,0,0,1);vertex(v,p,-1,1,0,0,0,alpha,color,0,0,1);}
        else{vertex(v,p,-1,0,-1,0,0,alpha,color,0,1,0);vertex(v,p,-1,0,1,0,1,alpha,color,0,1,0);vertex(v,p,1,0,1,1,1,alpha,color,0,1,0);vertex(v,p,1,0,-1,1,0,alpha,color,0,1,0);}
    }
    private static void vertex(VertexConsumer v,PoseStack p,float x,float y,float z,float u,float vv,float alpha,int c,float nx,float ny,float nz){v.vertex(p.last().pose(),x,y,z).color((c>>16)&255,(c>>8)&255,c&255,Math.max(0,Math.min(255,Math.round(alpha*255)))).uv(u,vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.last().normal(),nx,ny,nz).endVertex();}
}
