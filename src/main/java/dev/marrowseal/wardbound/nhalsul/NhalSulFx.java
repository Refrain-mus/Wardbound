package dev.marrowseal.wardbound.nhalsul;

import dev.marrowseal.wardbound.client.MasterCameraShake;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately readable world-space sword cut: a fast travelling edge leaves a pale seam in the
 * air, a second diagonal echo crosses it, and the end-point collapses into a seal. The effect exists
 * even when the attack hits nothing, which is important for making right-click feel authored.
 */
@Mod.EventBusSubscriber(modid=NhalSulModule.MODID,value=Dist.CLIENT)
public final class NhalSulFx {
    private static final Color PALE=new Color(205,247,244);
    private static final Color CYAN=new Color(103,218,222);
    private static final Color INK=new Color(35,43,69);
    private static final Color SILVER=new Color(226,229,220);
    private static final Color DEEP=new Color(75,96,124);

    private record Cut(Vec3 origin,Vec3 direction,float reach,int phase,long born){}
    private static final List<Cut> CUTS=new ArrayList<>();
    private static ClientLevel world;
    private static int budget;

    private NhalSulFx(){}

    public static void accept(NhalSulFxPacket packet){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        if(world!=mc.level){CUTS.clear();world=mc.level;}
        if(CUTS.size()>=12)CUTS.remove(0);
        Vec3 origin=new Vec3(packet.x(),packet.y(),packet.z());
        Vec3 direction=new Vec3(packet.dx(),packet.dy(),packet.dz());
        if(!Double.isFinite(origin.lengthSqr())||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()<.01||!Float.isFinite(packet.reach()))return;
        CUTS.add(new Cut(origin,direction.normalize(),Math.max(.35f,Math.min(18f,packet.reach())),packet.phase(),world.getGameTime()));
        MasterCameraShake.pulse(packet.phase()>=2?.31f:.23f,7,7.2f,Double.doubleToLongBits(origin.x+origin.z),MasterAnimationBeatPacket.GAMBLER);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=world){CUTS.clear();world=mc.level;}
        if(world==null||mc.player==null||mc.isPaused())return;

        long now=world.getGameTime();
        CUTS.removeIf(c->now-c.born>32||now<c.born||c.origin.distanceToSqr(mc.player.position())>4096);
        ParticleStatus quality=mc.options.particles().get();
        budget=quality==ParticleStatus.MINIMAL?54:quality==ParticleStatus.DECREASED?140:300;

        for(Cut cut:CUTS){
            int age=(int)(now-cut.born);
            Vec3 d=cut.direction;
            Vec3 right=d.cross(new Vec3(0,1,0));
            if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
            right=right.normalize();
            Vec3 up=right.cross(d).normalize();

            // 0-7 ticks: the blade edge races outward, but leaves the entire travelled seam visible.
            if(age<=7){
                double progress=Math.min(1.0,(age+1)/5.0);
                double travelled=cut.reach*progress;
                int samples=quality==ParticleStatus.MINIMAL?14:quality==ParticleStatus.DECREASED?22:34;
                for(int i=0;i<samples;i++){
                    double f=i/(double)Math.max(1,samples-1);
                    Vec3 center=cut.origin.add(d.scale(travelled*f));
                    double taper=Math.sin(Math.PI*f);
                    double flare=(.16+1.30*taper)*(1.0-age/11.0);
                    double twist=Math.sin(f*Math.PI*1.35+.45)*.36;
                    Vec3 a=center.add(right.scale(flare)).add(up.scale(twist));
                    Vec3 b=center.add(right.scale(-flare*.46)).add(up.scale(-twist*.55));
                    point(a,PALE,CYAN,.105f,16);
                    point(b,SILVER,DEEP,.072f,14);
                    if((i&2)==0)point(center.add(up.scale(taper*.26)),PALE,INK,.055f,13);
                }

                // A delayed diagonal echo makes the ability read as a cut rather than a particle ray.
                if(age>=2){
                    double echoProgress=Math.min(1.0,(age-1)/5.0);
                    double echoTravel=cut.reach*echoProgress;
                    int echoSamples=quality==ParticleStatus.MINIMAL?8:quality==ParticleStatus.DECREASED?13:20;
                    for(int i=0;i<echoSamples;i++){
                        double f=i/(double)Math.max(1,echoSamples-1);
                        Vec3 center=cut.origin.add(d.scale(echoTravel*f));
                        double span=Math.sin(Math.PI*f)*.72;
                        Vec3 q=center.add(right.scale(span*.58)).add(up.scale((f-.5)*1.55));
                        point(q,SILVER,INK,.075f,14);
                    }
                }
            }

            // 6-18 ticks: the cut remains as a thin afterimage instead of vanishing instantly.
            if(age>=6&&age<=18&&age%2==0){
                int samples=quality==ParticleStatus.MINIMAL?10:quality==ParticleStatus.DECREASED?16:24;
                float fade=(18-age)/12f;
                for(int i=0;i<samples;i++){
                    double f=i/(double)Math.max(1,samples-1);
                    Vec3 q=cut.origin.add(d.scale(cut.reach*f)).add(right.scale(Math.sin(f*Math.PI*2.0)*.08));
                    point(q,PALE,INK,.045f+.025f*fade,10);
                }
            }

            // End seal/ring: readable even when no target was hit.
            if(age>=4&&age<24&&age%2==0){
                Vec3 center=cut.origin.add(d.scale(cut.reach));
                double radius=(cut.phase>=2?1.8:1.38)*(1.0-(age-4)/24.0);
                radius=Math.max(.20,radius);
                int points=quality==ParticleStatus.MINIMAL?10:quality==ParticleStatus.DECREASED?18:28;
                for(int i=0;i<points;i++){
                    double a=i*Math.PI*2.0/points+age*.11;
                    Vec3 q=center.add(right.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));
                    point(q,(i&1)==0?SILVER:CYAN,INK,.075f,12);
                }
                if(age==6||age==10){
                    for(int i=0;i<8;i++){
                        double a=i*Math.PI/4.0;
                        Vec3 q=center.add(right.scale(Math.cos(a)*radius*.7)).add(up.scale(Math.sin(a)*radius*.7));
                        Vec3 velocity=right.scale(Math.cos(a)*.025).add(up.scale(Math.sin(a)*.025));
                        pointMotion(q,SILVER,CYAN,.095f,16,velocity);
                    }
                }
            }
        }
    }

    private static void point(Vec3 pos,Color a,Color b,float size,int life){
        pointMotion(pos,a,b,size,life,Vec3.ZERO);
    }

    private static void pointMotion(Vec3 pos,Color a,Color b,float size,int life,Vec3 velocity){
        Minecraft mc=Minecraft.getInstance();
        if(budget<=0||world==null||pos.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())<.55)return;
        budget--;
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(a,b).build())
                .setTransparencyData(GenericParticleData.create(.92f,0).build())
                .setScaleData(GenericParticleData.create(size,0).build())
                .setLifetime(life)
                .addMotion(velocity.x,velocity.y,velocity.z)
                .enableNoClip()
                .spawn(world,pos.x,pos.y,pos.z);
    }
}
