package dev.marrowseal.wardbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.marrowseal.wardbound.maestroart.*;
import dev.marrowseal.wardbound.boss.MaestroEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Client-only, visual effects; never applies damage. No global shared bone positions. */
public final class MaestroCombatVfxLayer extends GeoRenderLayer<MaestroEntity> {
    private final Map<MaestroEntity,State> states=new WeakHashMap<>();
    private static final class State {
        int revision=-1,tick=-1,budget;float age;
        final Map<String,Vec3> trails=new HashMap<>();
    }
    public MaestroCombatVfxLayer(GeoRenderer<MaestroEntity> renderer){super(renderer);}
    @Override public void preRender(PoseStack pose,MaestroEntity entity,BakedGeoModel model,RenderType type,MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay){
        for(String name:MaestroRig.SOCKETS){
            var bone=getGeoModel().getAnimationProcessor().getBone(name);
            if(bone instanceof GeoBone geo)geo.setTrackingMatrices(true);
        }
    }
    /** Call after this entity's geometry pass: matrices include cloth, bone and world transforms. */
    private Vec3 socket(String name){
        var bone=getGeoModel().getAnimationProcessor().getBone(name);
        if(!(bone instanceof GeoBone geo))return null;
        var p=geo.getWorldPosition();return new Vec3(p.x(),p.y(),p.z());
    }
    private void particle(MaestroEntity e,State s,SimpleParticleType type,Vec3 p,Vec3 velocity){
        if(p==null||s.budget--<=0)return;
        e.level().addParticle(type,p.x,p.y,p.z,velocity.x,velocity.y,velocity.z);
    }
    private void effect(MaestroEntity e,State s,MaestroVfxTimeline.Cue cue){
        Vec3 p=socket(cue.socket());if(p==null)return;String kind=cue.effect();
        // Floor sockets follow root animation; project only floor/stage effects to entity's ground plane.
        boolean floor=cue.socket().contains("floor")||cue.socket().contains("stage")||cue.socket().startsWith("orchestra_");
        if(floor)p=new Vec3(p.x,e.getY()+.04,p.z);
        boolean halo=kind.contains("halo")||kind.contains("score");
        boolean ring=floor||kind.contains("wave")||halo;
        boolean vertical=halo||(!floor&&kind.contains("wave"));
        if(ring){
            int count=cue.socket().startsWith("orchestra_")?12:kind.equals("grand_release")?48:24;
            for(int i=0;i<count;i++){
                double a=2*Math.PI*i/count;Vec3 dir=vertical?new Vec3(Math.cos(a),Math.sin(a),0).yRot((float)Math.toRadians(-e.getYRot())):new Vec3(Math.cos(a),0,Math.sin(a));
                particle(e,s,halo?MaestroParticles.NOTE:MaestroParticles.ARC,p.add(dir.scale(halo?.65:.18)),dir.scale(halo?.012:.10));
            }
            particle(e,s,MaestroParticles.RING,p,Vec3.ZERO);
        }else if(kind.contains("arc")||kind.equals("downbeat")){
            for(int i=0;i<16;i++){
                double a=-Math.PI*.7+i*Math.PI*1.4/15;
                Vec3 dir=new Vec3(Math.sin(a),Math.cos(a)*.45,-.65).yRot((float)Math.toRadians(-e.getYRot()));
                particle(e,s,MaestroParticles.ARC,p.add(dir.scale(.12)),dir.scale(.09));
            }
        }else if(kind.contains("note")||kind.contains("bolt")){
            Vec3 forward=e.getLookAngle().scale(.18);
            for(int i=0;i<6;i++)particle(e,s,MaestroParticles.NOTE,p.add(0,i*.035,0),forward.add((i-2.5)*.009,.012,0));
        }else{
            for(int i=0;i<12;i++){
                double a=2*Math.PI*i/12;
                particle(e,s,MaestroParticles.BURST,p,new Vec3(Math.cos(a)*.035,.02+Math.sin(a)*.025,Math.sin(a)*.035));
            }
        }
    }
    @Override public void render(PoseStack pose,MaestroEntity e,BakedGeoModel model,RenderType type,MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay){
        State s=states.computeIfAbsent(e,k->new State());
        if(s.tick==e.tickCount)return; // Glow pass, extra cameras and high FPS never multiply emission.
        s.tick=e.tickCount;
        var viewer=Minecraft.getInstance().player;
        float age=Math.max(0,e.visualPerformanceAge(partial)-6);
        if(viewer==null||viewer.distanceToSqr(e)>64*64){s.age=age;s.revision=e.performanceRevision();s.trails.clear();return;}
        s.budget=viewer.distanceToSqr(e)>24*24?48:128;
        if(s.revision!=e.performanceRevision()){
            s.revision=e.performanceRevision();s.age=age;s.trails.clear();
            // Local visual clock resets with the animation controller, including late tracking.
            if(age<1)s.age=-.01f;
        }
        String clip=e.clip();int index=MaestroRig.CLIPS.indexOf(clip);if(index<0)return;
        int duration=MaestroRig.TICKS[index];boolean loop=MaestroRig.LOOPS.contains(clip);
        float previous=s.age;s.age=age;
        // Culling or pauses discard old bursts; at most the last 2 ticks are caught up.
        previous=Math.max(previous,age-2);
        for(var cue:MaestroVfxTimeline.CUES.getOrDefault(clip,List.of())){
            double at=cue.seconds()*20;
            if(loop){double cycle=Math.floor(age/duration);at+=cycle*duration;if(at>age)at-=duration;}
            if(at>previous&&at<=age)effect(e,s,cue);
        }
        boolean active=!clip.startsWith("idle_")&&!clip.startsWith("death_")&&!clip.startsWith("recovery_");
        if(active){
            for(String name:List.of("baton_tip_socket","conducting_arc_left_socket")){
                if(name.startsWith("baton")&&clip.startsWith("piano_"))continue;
                Vec3 p=socket(name);if(p==null)continue;Vec3 old=s.trails.put(name,p);
                if(old==null||old.distanceToSqr(p)>4)continue;
                int count=Math.min(6,Math.max(1,(int)(old.distanceTo(p)/.045)));
                for(int i=1;i<=count;i++)particle(e,s,MaestroParticles.ARC,old.lerp(p,(double)i/count),Vec3.ZERO);
            }
        }else s.trails.clear();
        if(clip.startsWith("idle_")&&e.tickCount%12==0)particle(e,s,MaestroParticles.NOTE,socket("chest_socket"),new Vec3(0,.014,0));
    }
}
