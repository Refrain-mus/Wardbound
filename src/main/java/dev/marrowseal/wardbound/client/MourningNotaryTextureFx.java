package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.marrowseal.wardbound.*;
import dev.marrowseal.wardbound.boss.MourningNotaryEntity;
import dev.marrowseal.wardbound.net.NotaryRitualPacket;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * Volumetric presentation layer for the Mourning Notary.
 * Collision remains server-authoritative; these visuals deliberately avoid reducing the court
 * to flat floor lines. Gates are vertical, stamps descend as columns, and tolls occupy volume.
 */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MourningNotaryTextureFx {
    private static final MultiBufferSource.BufferSource FX=MultiBufferSource.immediate(new BufferBuilder(16384));
    private static final Color IVORY=new Color(229,217,199), GOLD=new Color(184,151,94), RED=new Color(137,57,75), VIOLET=new Color(107,82,137), ASH=new Color(83,78,82), PALE=new Color(244,234,213);
    private static final Map<UUID,BeatState> BEATS=new HashMap<>();
    private static final class BeatState{int state=-1,variant=-1,clock=-1,phase=1;}
    private MourningNotaryTextureFx(){}
    private static ResourceLocation tex(String n){return new ResourceLocation(Wardbound.MODID,"textures/vfx/mourning_notary/"+n+".png");}

    public static void ritualPulse(NotaryRitualPacket p){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;Vec3 c=new Vec3(p.x(),p.y(),p.z());int n=16+p.toll()*10;double r=1.4+p.toll()*.8;
        ringWisps(mc,c,r,p.toll()==3?RED:GOLD,p.toll()==1?IVORY:VIOLET,n,30+p.toll()*8);
        sphereShell(mc,c.add(0,1.4,0),r*.72,p.toll()==3?RED:IVORY,p.toll()==1?GOLD:VIOLET,12+p.toll()*10,32+p.toll()*5,p.toll()*71);
        burst(mc,c.add(0,.5,0),IVORY,p.toll()==3?RED:ASH,8+p.toll()*8,1.1+p.toll()*.5,28);
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;PoseStack p=e.getPoseStack();Vec3 camera=e.getCamera().getPosition();int owners=0;
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        try{
            for(var entity:mc.level.entitiesForRendering()){
                if(!(entity instanceof MourningNotaryEntity boss)||boss.isRemoved()||boss.distanceToSqr(camera)>14400||++owners>3)continue;
                CompoundTag v=boss.view();int state=v.getInt("state"),clock=v.getInt("clock"),vr=v.getInt("variant");
                p.pushPose();
                try{
                    p.translate(boss.getX()-camera.x,boss.getY()+.035-camera.y,boss.getZ()-camera.z);
                    if(state==MourningNotaryEntity.MANIFEST){
                        float a=Math.min(1,clock/30f);
                        ground(p,"manifest_seal",0,0,22,22,.72f*a);
                        ground(p,"judgment",0,0,13,13,.26f*a);
                        billboard(p,e,"consent_seal",0,4.2,0,4.8,4.8,.86f*a);
                        billboard(p,e,"witness_crown",0,7.1,0,6.2,2.8,.70f*a);
                        double close=Math.max(3.0,12.5-clock*.095);
                        gateWall(p,"tribunal_arch", close,4.0,0,90,16,8.5,.46f*a);
                        gateWall(p,"tribunal_arch",-close,4.0,0,90,16,8.5,.46f*a);
                        gateWall(p,"tribunal_arch",0,4.0, close,0,16,8.5,.42f*a);
                        gateWall(p,"tribunal_arch",0,4.0,-close,0,16,8.5,.42f*a);
                    }
                    if(state==MourningNotaryEntity.DECLARE||state==MourningNotaryEntity.JUDGMENT)drawGates(p,e,v,clock,state);
                    if(state==MourningNotaryEntity.STAMP||state==MourningNotaryEntity.JUDGMENT)drawStampColumns(p,e,v,clock,state);
                    if(state==MourningNotaryEntity.TOLL||state==MourningNotaryEntity.JUDGMENT)drawBellVolume(p,e,v,clock,state);
                    if(state==MourningNotaryEntity.JUDGMENT){billboard(p,e,"witness_crown",0,5.0,0,5.5,2.5,.60f);billboard(p,e,"judgment",0,2.6,0,3.0,3.0,.32f);}
                    if(state==MourningNotaryEntity.UNBOUND){ground(p,"unbound",0,0,8.5,8.5,.54f);billboard(p,e,"unbound",0,3.2,0,3.2,3.2,.70f);billboard(p,e,"seal_shard",0,4.8,0,2.5,2.5,.62f);}
                    if(state==MourningNotaryEntity.COLLAPSE){
                        float fade=Math.max(0,1-clock/112f);
                        ground(p,"collapse",0,0,11+clock*.045,11+clock*.045,.50f*fade);
                        billboard(p,e,"collapse",0,2.8+clock*.018,0,4.4,4.4,.68f*fade);
                        if(clock<24)billboard(p,e,"bell_resonance",0,4.0,0,5.0,5.0,.38f*(1-clock/24f));
                        billboard(p,e,"witness_crown",0,5.1+clock*.010,0,4.0,2.0,.40f*fade);
                        int[] crackAt={18,34,50,66};
                        for(int i=0;i<4;i++){
                            double ang=i*Math.PI/2,rr=3.25,x=Math.cos(ang)*rr,z=Math.sin(ang)*rr;
                            float waiting=clock<crackAt[i]?.20f:.34f;ground(p,"stamp_impact",x,z,2.8,2.8,waiting*fade);
                            if(clock>=crackAt[i]){float crack=Math.min(1f,(clock-crackAt[i])/9f);ground(p,"seal_shard",x,z,3.2,3.2,(.24f+.38f*crack)*fade);}
                        }
                        float erase=Math.max(0f,Math.min(1f,(clock-78)/18f));
                        if(erase<1f){float lineW=14f*(1f-erase);ground(p,"decree_line",0,0,Math.max(.12f,lineW),1.15,.54f*(1f-erase));}
                    }
                }finally{p.popPose();}
            }
        }finally{FX.endBatch();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableCull();}
    }

    private static void drawGates(PoseStack p,RenderLevelStageEvent e,CompoundTag v,int clock,int state){
        boolean axisZ=v.getBoolean("line_x");int vr=v.getInt("variant");float pulse=.48f+.14f*(float)Math.sin(clock*.23);
        if(vr==3 && state==MourningNotaryEntity.DECLARE){
            double gate=Math.max(2.0,11.5-Math.max(0,clock-34)*.22);
            gateWall(p,"tribunal_arch", gate,3.0,0,90,26,6.2,pulse+.16f);
            gateWall(p,"tribunal_arch",-gate,3.0,0,90,26,6.2,pulse+.16f);
            gateWall(p,"tribunal_arch",0,3.0, gate,0,26,6.2,pulse+.10f);
            gateWall(p,"tribunal_arch",0,3.0,-gate,0,26,6.2,pulse+.10f);
            ground(p,"decree_line", gate,0,1.15,24,.54f);ground(p,"decree_line",-gate,0,1.15,24,.54f);
            ground(p,"decree_line",0, gate,24,1.15,.48f);ground(p,"decree_line",0,-gate,24,1.15,.48f);
            billboard(p,e,"witness_crown",0,5.9,0,5.4,2.3,.48f);
            return;
        }
        if(vr==2){
            double gate=state==MourningNotaryEntity.JUDGMENT?5.2:Math.max(1.6,10.5-Math.max(0,clock-34)*.22);
            gateWall(p,"tribunal_arch",axisZ?0:gate,2.9,axisZ?gate:0,axisZ?0:90,26,5.8,pulse+.12f);
            gateWall(p,"tribunal_arch",axisZ?0:-gate,2.9,axisZ?-gate:0,axisZ?0:90,26,5.8,pulse+.12f);
            if(state==MourningNotaryEntity.DECLARE&&clock>=58)gateWall(p,"tribunal_arch",0,3.0,0,axisZ?0:90,26,6.2,.58f);
            billboard(p,e,"witness_crown",0,5.7,0,5.0,2.2,.42f);
            return;
        }
        double o=v.getDouble("line_offset");
        gateWall(p,"tribunal_arch",axisZ?0:o,2.9,axisZ?o:0,axisZ?0:90,26,5.5,pulse+.14f);
        if(axisZ) ground(p,"decree_line",0,o,24,1.25,.58f); else ground(p,"decree_line",o,0,1.25,24,.58f);
        if(v.getBoolean("line_cross")){
            double o2=v.getDouble("second_offset");
            gateWall(p,"tribunal_arch",axisZ?o2:0,3.05,axisZ?0:o2,axisZ?90:0,26,5.8,pulse);
        }
    }

    private static void drawStampColumns(PoseStack p,RenderLevelStageEvent e,CompoundTag v,int clock,int state){
        ListTag list=v.getList("stamps",Tag.TAG_COMPOUND);int vr=v.getInt("variant");
        int local=clock,lead=48;boolean impact;
        if(vr==2&&state==MourningNotaryEntity.STAMP){
            // Each cascade wave replaces the stamp list after the preceding impact. Using the global
            // attack clock here made waves two and three visually remain in mid-air when the server
            // applied their floor hit. Give every regenerated list its own descent clock.
            if(clock>=55){local=clock-55;lead=17;}
            else if(clock>=37){local=clock-37;lead=17;}
            else {local=clock;lead=36;}
            impact=clock==36||clock==54||clock>=72;
        }else {impact=clock>=48;}
        int hold=Math.min(4,Math.max(1,lead/5));
        float descend=Math.max(0,Math.min(1,(local-hold)/(float)Math.max(1,lead-hold)));
        for(int i=0;i<list.size();i++){
            CompoundTag q=list.getCompound(i);double x=q.getDouble("x"),z=q.getDouble("z");double y=5.8-descend*4.0+(i%3)*.18;
            billboard(p,e,impact?"stamp_impact":"verdict_column",x,y,z,vr==2?2.8:2.3,vr==2?5.4:4.5,impact?.82f:.72f);
            billboard(p,e,"seal_shard",x,1.0,z,vr==2?2.4:1.9,vr==2?2.4:1.9,impact?.52f:.34f);
            if(!impact) ground(p,"stamp_warning",x,z,vr==2?4.4:3.6,vr==2?4.4:3.6,.64f);
            if(impact||local>=28){float crack=impact?.90f:.48f;ground(p,"stamp_impact",x,z,vr==2?4.2:3.4,vr==2?4.2:3.4,crack);}
            if(vr==2&&!impact)billboard(p,e,"verdict_column",x,y+1.6,z,1.8,3.8,.34f);
        }
    }

    private static void drawBellVolume(PoseStack p,RenderLevelStageEvent e,CompoundTag v,int clock,int state){
        int vr=v.getInt("variant");int rule=v.getInt("rule");float pulse=.54f+.10f*(float)Math.sin(clock*.22);
        if(vr==3 && state==MourningNotaryEntity.TOLL){
            int wave=v.getInt("resonance_wave");double[] radii={3.8,10.8,6.6,12.8};
            for(int i=0;i<radii.length;i++){
                float a=i==Math.max(0,wave-1)?.66f:i>=wave?.27f:.13f;
                ground(p,"toll_ring",0,0,radii[i]*2.0,radii[i]*2.0,a);
                billboard(p,e,"bell_resonance",0,2.1+i*.38,0,(float)(radii[i]*.55),(float)(radii[i]*.55),a+pulse*.16f);
            }
            billboard(p,e,"witness_crown",0,5.6,0,5.0,2.2,.58f);
        }else if(vr==2){
            if(state==MourningNotaryEntity.JUDGMENT){
                ground(p,"toll_ring",0,0,17.0,17.0,.56f);
                billboard(p,e,"bell_resonance",0,2.6,0,5.2,5.2,.44f+pulse*.32f);
                if(clock>=66&&v.getInt("rule")!=0){String rr=v.getInt("rule")==1?"rule_move":"rule_still";billboard(p,e,rr,0,4.2,0,2.2,2.2,.58f);}
            }else{
                int wave=v.getInt("resonance_wave");double[] radii={5.0,9.0,12.5};
                for(int i=0;i<radii.length;i++){
                    float a=i==Math.min(2,wave)?.58f:i>wave?.24f:.14f;
                    // The damaging shell is radial, so keep a world-space ground ring at the exact
                    // server radius even when particles are reduced. The vertical resonance sprite
                    // remains decorative rather than being the only telegraph.
                    ground(p,"toll_ring",0,0,radii[i]*2.0,radii[i]*2.0,a);
                    billboard(p,e,"bell_resonance",0,2.2+i*.45,0,(float)(radii[i]*.62),(float)(radii[i]*.62),a+pulse*.18f);
                }
            }
            billboard(p,e,"witness_crown",0,5.4,0,4.6,2.1,.55f);
        }else{
            String r=rule==1?"rule_move":"rule_still";
            double ringSize=8.0+Math.min(12,clock)*.35;
            ground(p,"toll_ring",0,0,ringSize,ringSize,.52f);
            billboard(p,e,"bell_resonance",0,2.7,0,5.3,5.3,Math.min(1f,pulse+.14f));
            billboard(p,e,r,0,3.25,0,2.6,2.6,.72f);
            if(vr==1&&v.getInt("secondary_rule")!=0){String r2=v.getInt("secondary_rule")==1?"rule_move":"rule_still";billboard(p,e,r2,0,4.45,0,1.9,1.9,.40f);}
        }
    }

    @SubscribeEvent public static void motes(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused()){BEATS.clear();return;}
        long now=mc.level.getGameTime();boolean reduced=WardConfig.accessibilityReduceMotion;int cadence=reduced?8:3;boolean particleTick=now%cadence==0;int owners=0;Set<UUID> live=new HashSet<>();
        for(var ent:mc.level.entitiesForRendering())if(ent instanceof MourningNotaryEntity boss&&!boss.isRemoved()&&boss.distanceToSqr(mc.player)<6400&&++owners<=3){
            live.add(boss.getUUID());CompoundTag v=boss.view();int st=v.getInt("state"),clock=v.getInt("clock"),vr=v.getInt("variant"),phase=v.getInt("phase");BeatState bs=BEATS.computeIfAbsent(boss.getUUID(),k->new BeatState());
            if(bs.phase!=phase && phase>=2){
                MasterCameraShake.pulse(reduced?.18f:.34f,reduced?10:18,3.2f,boss.getId()*487L+phase,MasterAnimationBeatPacket.NOTARY);
                groundQuake(mc,boss.position().add(0,.08,0),10.5,ASH,GOLD,reduced?8:34,boss.getId()*47L+phase);
                ringWisps(mc,boss.position().add(0,.1,0),9.8,GOLD,RED,reduced?14:48,34);
                sphereShell(mc,boss.position().add(0,2.7,0),7.2,IVORY,VIOLET,reduced?16:52,38,boss.getId()+phase*91);
            }
            if(!reduced && (bs.state!=st || bs.variant!=vr)){
                float amp=switch(st){case MourningNotaryEntity.MANIFEST->.32f;case MourningNotaryEntity.DECLARE->vr==3?.46f:.20f;case MourningNotaryEntity.JUDGMENT->vr==2?.64f:.36f;case MourningNotaryEntity.TOLL->vr==3?.58f:vr==2?.52f:.28f;case MourningNotaryEntity.UNBOUND->.30f;case MourningNotaryEntity.COLLAPSE->.50f;default->.20f;};
                int dur=(st==MourningNotaryEntity.JUDGMENT&&vr==2)?28:st==MourningNotaryEntity.TOLL&&vr==3?24:st==MourningNotaryEntity.TOLL&&vr==2?22:14;MasterCameraShake.pulse(amp,dur,2.5f,boss.getId()*173L+st*29L+vr,MasterAnimationBeatPacket.NOTARY);
                groundQuake(mc,boss.position().add(0,.08,0),st==MourningNotaryEntity.JUDGMENT?9.0:6.5,st==MourningNotaryEntity.UNBOUND?IVORY:ASH,st==MourningNotaryEntity.TOLL?VIOLET:GOLD,reduced?0:36,boss.getId()*47L+st);
            }
            if(!reduced){
                int[] beats=st==MourningNotaryEntity.TOLL&&vr==3?new int[]{34,52,70,88}:st==MourningNotaryEntity.TOLL&&vr==2?new int[]{34,52,70}:st==MourningNotaryEntity.DECLARE&&vr==3?new int[]{50,66}:st==MourningNotaryEntity.JUDGMENT&&vr==2?new int[]{48,62,84}:st==MourningNotaryEntity.STAMP&&vr==2?new int[]{36,54,72}:new int[0];
                for(int beat:beats)if(bs.clock<beat&&clock>=beat)MasterCameraShake.pulse(st==MourningNotaryEntity.JUDGMENT?.36f:.26f,10,3.7f,boss.getId()*239L+beat,MasterAnimationBeatPacket.NOTARY);
                if(bs.phase<2 && phase>=2){
                    if(bs.clock<10 || bs.state!=st)ringWisps(mc,boss.position().add(0,.12,0),12.5,RED,GOLD,54,42);
                }
                if(st==MourningNotaryEntity.COLLAPSE){
                    if(bs.clock<18 && clock>=18)MasterCameraShake.pulse(.28f,14,3.6f,boss.getId()*353L+18,MasterAnimationBeatPacket.NOTARY);
                    if(bs.clock<42 && clock>=42)groundQuake(mc,boss.position().add(0,.08,0),8.8,RED,ASH,34,boss.getId()*359L+42);
                }
            }
            if(st==MourningNotaryEntity.STAMP){
                int[] impacts=vr==2?new int[]{36,54,72}:vr==1?new int[]{48,60}:new int[]{48};
                for(int beat:impacts)if(bs.clock<beat&&clock>=beat)persistStampScars(boss,v,beat,vr==2);
            }else if(st==MourningNotaryEntity.JUDGMENT){
                int[] impacts=vr==2?new int[]{48}:vr==1?new int[]{50,62}:new int[]{50};
                for(int beat:impacts)if(bs.clock<beat&&clock>=beat)persistStampScars(boss,v,beat,true);
            }
            bs.state=st;bs.variant=vr;bs.clock=clock;bs.phase=phase;if(!particleTick)continue;
            int n=reduced?2:6;for(int i=0;i<n;i++){double a=now*.025+i*Math.PI*2/n,r=2.8+(i%3)*.55;Color c=st==MourningNotaryEntity.UNBOUND?IVORY:st==MourningNotaryEntity.JUDGMENT?RED:GOLD;wisp(mc.level,boss.getX()+Math.cos(a)*r,boss.getY()+1.1+(i%3)*.65,boss.getZ()+Math.sin(a)*r,c,st==MourningNotaryEntity.TOLL?VIOLET:ASH,.11f,22,-Math.sin(a)*.012,.018,Math.cos(a)*.012,.016);}
            if(st==MourningNotaryEntity.MANIFEST){
                if(clock%8<cadence){
                    sphereShell(mc,boss.position().add(0,3.2,0),5.4,IVORY,GOLD,reduced?18:60,38,boss.getId()+clock);
                    sphereShell(mc,boss.position().add(0,3.8,0),8.0,VIOLET,RED,reduced?14:48,42,boss.getId()+clock*3);
                    ringWisps(mc,boss.position().add(0,.15,0),8.5,GOLD,IVORY,reduced?18:64,32);
                    courtSpiral(mc,boss.position().add(0,.2,0),reduced?18:72,clock,boss.getId());
                }
            }
            if(st==MourningNotaryEntity.DECLARE||st==MourningNotaryEntity.JUDGMENT)gateParticles(mc,boss,v,clock,reduced);
            if(st==MourningNotaryEntity.STAMP||st==MourningNotaryEntity.JUDGMENT)stampParticles(mc,boss,v,clock,reduced);
            if(st==MourningNotaryEntity.TOLL||st==MourningNotaryEntity.JUDGMENT)bellParticles(mc,boss,v,clock,reduced,st);
            if(st==MourningNotaryEntity.UNBOUND){if(clock%8<cadence)sphereShell(mc,boss.position().add(0,2.5,0),2.6,IVORY,PALE,reduced?12:32,28,boss.getId()*17+clock);}
        }
        BEATS.keySet().retainAll(live);
    }

    private static void persistStampScars(MourningNotaryEntity boss,CompoundTag v,int seed,boolean severe){
        ListTag list=v.getList("stamps",Tag.TAG_COMPOUND);int cap=Math.min(12,list.size());
        for(int i=0;i<cap;i++){CompoundTag q=list.getCompound(i);Vec3 pos=new Vec3(boss.getX()+q.getDouble("x"),boss.getY()+.018,boss.getZ()+q.getDouble("z"));EncounterAftermathFx.recordNotaryStamp(pos,boss.getId()*701+seed*31+i*17,severe);}
    }

    private static void gateParticles(Minecraft mc,MourningNotaryEntity boss,CompoundTag v,int clock,boolean reduced){
        boolean axisZ=v.getBoolean("line_x");int vr=v.getInt("variant");int state=v.getInt("state");int count=reduced?10:28;
        if(vr==3 && state==MourningNotaryEntity.DECLARE){
            double gate=Math.max(2.0,11.5-Math.max(0,clock-34)*.22);
            archWisps(mc,boss, gate,0,90,count,RED,GOLD,clock);
            archWisps(mc,boss,-gate,0,90,count,GOLD,RED,clock+29);
            archWisps(mc,boss,0, gate,0,count,IVORY,VIOLET,clock+57);
            archWisps(mc,boss,0,-gate,0,count,VIOLET,IVORY,clock+83);
            if(clock%8<3)ringWisps(mc,boss.position().add(0,.12,0),Math.max(2.2,gate),RED,GOLD,reduced?10:28,24);
        }
        else if(vr==2){
            double gate=state==MourningNotaryEntity.JUDGMENT?5.2:Math.max(1.6,10.5-Math.max(0,clock-34)*.22);
            archWisps(mc,boss,axisZ?0:gate,axisZ?gate:0,axisZ?0:90,count,RED,GOLD,clock);
            archWisps(mc,boss,axisZ?0:-gate,axisZ?-gate:0,axisZ?0:90,count,GOLD,RED,clock+41);
            if(state==MourningNotaryEntity.DECLARE&&clock>=58)archWisps(mc,boss,0,0,axisZ?0:90,count,IVORY,RED,clock+83);
        }
        else{double o=v.getDouble("line_offset");archWisps(mc,boss,axisZ?0:o,axisZ?o:0,axisZ?0:90,count,IVORY,RED,clock);if(v.getBoolean("line_cross")){double o2=v.getDouble("second_offset");archWisps(mc,boss,axisZ?o2:0,axisZ?0:o2,axisZ?90:0,count,GOLD,VIOLET,clock+37);}}
    }

    private static void stampParticles(Minecraft mc,MourningNotaryEntity boss,CompoundTag v,int clock,boolean reduced){
        ListTag list=v.getList("stamps",Tag.TAG_COMPOUND);int cap=Math.min(reduced?4:10,list.size());
        int local=clock,lead=40;
        if(v.getInt("variant")==2 && v.getInt("state")==MourningNotaryEntity.STAMP){
            if(clock>=55){local=clock-55;lead=17;}else if(clock>=37){local=clock-37;lead=17;}else{local=clock;lead=36;}
        }
        double descent=Math.max(0,Math.min(1,local/(double)Math.max(1,lead)));boolean landed=local>=lead;
        for(int i=0;i<cap;i++){CompoundTag q=list.getCompound(i);double x=boss.getX()+q.getDouble("x"),z=boss.getZ()+q.getDouble("z");double top=boss.getY()+5.6-descent*3.8;columnWisps(mc,new Vec3(x,top,z),landed?RED:GOLD,landed?ASH:IVORY,reduced?5:13,clock+i*31);if(local>=Math.max(0,lead-3)&&local<=lead+4&&clock%3==0)burst(mc,new Vec3(x,boss.getY()+.25,z),RED,GOLD,reduced?3:8,.5,22);if(landed&&local<=lead+18&&clock%6<3)groundQuake(mc,new Vec3(x,boss.getY()+.05,z),2.4,RED,GOLD,reduced?2:6,boss.getId()*71L+i*17L+clock);}
    }

    private static void bellParticles(Minecraft mc,MourningNotaryEntity boss,CompoundTag v,int clock,boolean reduced,int state){
        int vr=v.getInt("variant");Vec3 c=boss.position().add(0,2.2,0);
        if(vr==3 && state==MourningNotaryEntity.TOLL){
            int[] times={34,52,70,88};double[] radii={3.8,10.8,6.6,12.8};
            for(int i=0;i<times.length;i++){int dt=Math.abs(clock-times[i]);if(dt<13){Color a=(i&1)==0?GOLD:RED,b=(i&1)==0?IVORY:VIOLET;sphereShell(mc,c,radii[i],a,b,reduced?14:38,26,boss.getId()+clock+i*131);}}
        }
        else if(vr==2){
            if(state==MourningNotaryEntity.JUDGMENT){if(Math.abs(clock-62)<13)sphereShell(mc,c,8.4,RED,GOLD,reduced?14:40,26,boss.getId()+clock*5);}
            else {int[] times={34,52,70};double[] radii={5,9,12.5};for(int i=0;i<times.length;i++){int dt=Math.abs(clock-times[i]);if(dt<12)sphereShell(mc,c,radii[i],i==2?RED:GOLD,i==0?IVORY:VIOLET,reduced?12:34,24,boss.getId()+clock+i*101);}}
        }
        else if(clock%12<3){double radius=4.0+(clock%28)*.14;sphereShell(mc,c,radius,v.getInt("rule")==1?GOLD:VIOLET,RED,reduced?10:28,24,boss.getId()+clock*7);}
    }

    private static void archWisps(Minecraft mc,MourningNotaryEntity boss,double ox,double oz,double yawDeg,int count,Color a,Color b,int seed){
        double yaw=Math.toRadians(yawDeg),cos=Math.cos(yaw),sin=Math.sin(yaw);for(int i=0;i<count;i++){double f=i/(double)Math.max(1,count-1);double x=(f-.5)*24;double arch=2.0+Math.sin(f*Math.PI)*4.3;double wx=boss.getX()+ox+x*cos,wz=boss.getZ()+oz+x*sin;wisp(mc.level,wx,boss.getY()+arch,wz,(i&1)==0?a:b,(i&1)==0?b:a,.13f,24,0,-.006,0,.014);}
    }

    private static void courtSpiral(Minecraft mc,Vec3 base,int count,int clock,int seed){
        Random r=new Random(seed*83L+clock*17L);
        for(int i=0;i<count;i++){
            double f=i/(double)Math.max(1,count-1),ang=clock*.055+i*.72,rad=2.2+f*7.5;
            double x=base.x+Math.cos(ang)*rad,z=base.z+Math.sin(ang)*rad,y=base.y+.2+f*7.5+(r.nextDouble()-.5)*.35;
            wisp(mc.level,x,y,z,(i&1)==0?GOLD:IVORY,(i&1)==0?RED:VIOLET,.12f+r.nextFloat()*.08f,34,-Math.sin(ang)*.014,.028,Math.cos(ang)*.014,.018);
        }
    }

    private static void groundQuake(Minecraft mc,Vec3 center,double radius,Color a,Color b,int count,long seed){Random r=new Random(seed);for(int i=0;i<count;i++){double ang=r.nextDouble()*Math.PI*2,rr=Math.sqrt(r.nextDouble())*radius;double x=center.x+Math.cos(ang)*rr,z=center.z+Math.sin(ang)*rr;wisp(mc.level,x,center.y+r.nextDouble()*.1,z,(i&1)==0?a:b,(i&1)==0?b:a,.09f+r.nextFloat()*.07f,22,Math.cos(ang)*.009,.05+r.nextDouble()*.14,Math.sin(ang)*.009,.018);}}

    private static void columnWisps(Minecraft mc,Vec3 top,Color a,Color b,int count,int seed){Random r=new Random(seed*43L+7);for(int i=0;i<count;i++){double f=i/(double)Math.max(1,count-1),ang=seed*.03+i*2.1,rr=.35+r.nextDouble()*.55;wisp(mc.level,top.x+Math.cos(ang)*rr,top.y-f*4.4,top.z+Math.sin(ang)*rr,a,b,.13f+r.nextFloat()*.08f,26,0,-.028,0,.018);}}

    private static void ground(PoseStack p,String texture,double x,double z,double w,double h,float alpha){if(alpha<=0)return;p.pushPose();p.translate(x,.006,z);p.scale((float)w/2,1,(float)h/2);quad(p,texture,alpha,false);p.popPose();}
    private static void billboard(PoseStack p,RenderLevelStageEvent e,String texture,double x,double y,double z,double w,double h,float alpha){if(alpha<=0)return;p.pushPose();p.translate(x,y,z);p.mulPose(e.getCamera().rotation());p.scale((float)w/2,(float)h/2,1);quad(p,texture,alpha,true);p.popPose();}
    private static void gateWall(PoseStack p,String texture,double x,double y,double z,double yawDeg,double w,double h,float alpha){if(alpha<=0)return;p.pushPose();p.translate(x,y,z);p.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)yawDeg));p.scale((float)w/2,(float)h/2,1);quad(p,texture,alpha,true);p.popPose();}
    private static void quad(PoseStack p,String texture,float alpha,boolean upright){VertexConsumer v=FX.getBuffer(RenderType.entityTranslucent(tex(texture)));if(upright){vertex(v,p,-1,-1,0,0,1,alpha,0,0,1);vertex(v,p,1,-1,0,1,1,alpha,0,0,1);vertex(v,p,1,1,0,1,0,alpha,0,0,1);vertex(v,p,-1,1,0,0,0,alpha,0,0,1);}else{vertex(v,p,-1,0,-1,0,0,alpha,0,1,0);vertex(v,p,-1,0,1,0,1,alpha,0,1,0);vertex(v,p,1,0,1,1,1,alpha,0,1,0);vertex(v,p,1,0,-1,1,0,alpha,0,1,0);}}
    private static void vertex(VertexConsumer v,PoseStack p,float x,float y,float z,float u,float vv,float alpha,float nx,float ny,float nz){v.vertex(p.last().pose(),x,y,z).color(255,255,255,Math.max(0,Math.min(255,Math.round(alpha*255)))).uv(u,vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.last().normal(),nx,ny,nz).endVertex();}
    private static void ringWisps(Minecraft mc,Vec3 pos,double radius,Color a,Color b,int count,int life){for(int i=0;i<count;i++){double ang=i*Math.PI*2/count;wisp(mc.level,pos.x+Math.cos(ang)*radius,pos.y,pos.z+Math.sin(ang)*radius,a,b,.11f,life,-Math.sin(ang)*.008,.018,Math.cos(ang)*.008,.01);}}
    private static void sphereShell(Minecraft mc,Vec3 pos,double radius,Color a,Color b,int count,int life,int seed){double golden=Math.PI*(3-Math.sqrt(5));for(int i=0;i<count;i++){double y=1-2*(i+.5)/Math.max(1,count),rr=Math.sqrt(Math.max(0,1-y*y)),ang=i*golden+seed*.01;double x=Math.cos(ang)*rr*radius,z=Math.sin(ang)*rr*radius;wisp(mc.level,pos.x+x,pos.y+y*radius,pos.z+z,(i&1)==0?a:b,(i&1)==0?b:a,.11f+(i%3)*.025f,life,-x*.0018,-y*.004,-z*.0018,.014);}}
    private static void burst(Minecraft mc,Vec3 pos,Color a,Color b,int count,double spread,int life){Random r=new Random(Double.doubleToLongBits(pos.x*17+pos.y*31+pos.z*43)+count);for(int i=0;i<count;i++){double ox=(r.nextDouble()-.5)*spread*2,oy=(r.nextDouble()-.35)*spread,oz=(r.nextDouble()-.5)*spread*2;wisp(mc.level,pos.x+ox,pos.y+oy,pos.z+oz,a,b,.09f+r.nextFloat()*.08f,life,ox*.006,.016+Math.abs(oy)*.008,oz*.006,.022);}}
    private static void wisp(net.minecraft.world.level.Level level,double x,double y,double z,Color a,Color b,float scale,int life,double mx,double my,double mz,double random){WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE).setColorData(ColorParticleData.create(a,b).build()).setTransparencyData(GenericParticleData.create(.88f,0f).build()).setScaleData(GenericParticleData.create(scale,0f).build()).setLifetime(life).addMotion(mx,my,mz).setRandomMotion(random,random*.7,random).enableNoClip().spawn(level,x,y,z);}
}
