package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.fx.WardLodestoneDispatch;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/** Server-authoritative geometry helpers for the Cthulhu Head encounter. */
public final class CthulhuHeadCombat {
    public enum Pattern {
        EYE_LANCE("eye_beam_charge",170),
        DUAL_GAZE("dual_eye_burst",84),
        TRACKING_GAZE("tracking_gaze",160),
        TENTACLE_LASH("tentacle_lash",130),
        TENTACLE_FAN("tentacle_fan_attack",112),
        TENTACLE_GRASP("tentacle_grasp",146),
        PSYCHIC_ROAR("psychic_roar",120),
        HEAD_SLAM("head_slam",120),
        VOID_ERUPTION("eldritch_summon",160),
        ABYSSAL_PULSE("abyssal_pulse",140),
        SOVEREIGN_CROSS("dual_eye_burst",219);
        public final String clip; public final int ticks;
        Pattern(String clip,int ticks){this.clip=clip;this.ticks=ticks;}
    }

    private CthulhuHeadCombat() {}

    public static void fx(ServerLevel level,Vec3 pos,int kind,float scale){
        WardLodestoneDispatch.emit(level,pos,kind,scale);
    }

    public static void fxLine(ServerLevel level,Vec3 a,Vec3 b,int kind,float scale,int samples){
        int n=Math.max(2,Math.min(24,samples));
        for(int i=0;i<n;i++){
            double t=i/(double)(n-1);
            fx(level,a.lerp(b,t),kind,scale*(.78f+(float)Math.sin(t*Math.PI)*.32f));
        }
    }

    public static void fxRing(ServerLevel level,Vec3 center,double radius,int kind,float scale,int samples){
        int n=Math.max(8,Math.min(32,samples));
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2.0/n;
            fx(level,center.add(Math.cos(a)*radius,.08,Math.sin(a)*radius),kind,scale);
        }
    }

    public static boolean damageLine(CthulhuHeadEntity boss,Collection<ServerPlayer> players,Vec3 a,Vec3 b,double width,float damage,double push){
        boolean hit=false;
        for(ServerPlayer p:players){
            Vec3 q=p.position().add(0,p.getBbHeight()*.45,0);
            if(distanceToSegmentXZ(q,a,b)<=width){
                if(p.hurt(boss.damageSources().magic(),damage))hit=true;
                if(push>0){Vec3 away=q.subtract(a).multiply(1,0,1);if(away.lengthSqr()>.01){away=away.normalize().scale(push);p.setDeltaMovement(p.getDeltaMovement().add(away.x,.18,away.z));p.hurtMarked=true;}}
            }
        }
        return hit;
    }

    public static boolean damageCircle(CthulhuHeadEntity boss,Collection<ServerPlayer> players,Vec3 center,double radius,float damage,double lift){
        boolean hit=false;double r2=radius*radius;
        for(ServerPlayer p:players){
            Vec3 d=p.position().subtract(center).multiply(1,0,1);
            if(d.lengthSqr()<=r2){if(p.hurt(boss.damageSources().magic(),damage))hit=true;if(lift>0){Vec3 out=d.lengthSqr()>.01?d.normalize().scale(.45):new Vec3(0,0,.45);p.setDeltaMovement(p.getDeltaMovement().add(out.x,lift,out.z));p.hurtMarked=true;}}
        }
        return hit;
    }

    public static boolean damageAnnulus(CthulhuHeadEntity boss,Collection<ServerPlayer> players,Vec3 center,double radius,double width,float damage,boolean groundedOnly){
        boolean hit=false;
        for(ServerPlayer p:players){
            double d=Math.sqrt(p.position().subtract(center).multiply(1,0,1).lengthSqr());
            if(Math.abs(d-radius)<=width && (!groundedOnly||p.onGround())){
                if(p.hurt(boss.damageSources().magic(),damage))hit=true;
            }
        }
        return hit;
    }

    public static boolean damageCorridor(CthulhuHeadEntity boss,Collection<ServerPlayer> players,Vec3 origin,Vec3 direction,double length,double halfWidth,float damage){
        Vec3 dir=flat(direction);if(dir.lengthSqr()<.001)return false;dir=dir.normalize();Vec3 right=new Vec3(-dir.z,0,dir.x);boolean hit=false;
        for(ServerPlayer p:players){Vec3 d=p.position().subtract(origin).multiply(1,0,1);double f=d.dot(dir),s=Math.abs(d.dot(right));if(f>=0&&f<=length&&s<=halfWidth){if(p.hurt(boss.damageSources().magic(),damage))hit=true;Vec3 shove=dir.scale(.8);p.setDeltaMovement(p.getDeltaMovement().add(shove.x,.32,shove.z));p.hurtMarked=true;}}
        return hit;
    }


    public static Vec3 clampToArena(Vec3 point, Vec3 center, double maxRadius){
        Vec3 flat=point.subtract(center).multiply(1,0,1);
        double r2=flat.lengthSqr();
        if(r2<=maxRadius*maxRadius)return new Vec3(point.x,center.y,point.z);
        if(r2<1e-7)return center;
        Vec3 q=flat.normalize().scale(maxRadius);
        return new Vec3(center.x+q.x,center.y,center.z+q.z);
    }

    public static void fxCorridor(ServerLevel level,Vec3 origin,Vec3 direction,double length,double halfWidth,int kind,float scale){
        Vec3 dir=flat(direction);if(dir.lengthSqr()<.001)return;dir=dir.normalize();
        Vec3 right=new Vec3(-dir.z,0,dir.x);
        for(double f=4;f<=length;f+=4){
            Vec3 c=origin.add(dir.scale(f));
            fx(level,c,kind,scale);
            fx(level,c.add(right.scale(halfWidth*.5)),kind,scale*.94f);
            fx(level,c.add(right.scale(-halfWidth*.5)),kind,scale*.94f);
            fx(level,c.add(right.scale(halfWidth)),kind,scale*.86f);
            fx(level,c.add(right.scale(-halfWidth)),kind,scale*.86f);
        }
    }

    public static double distanceToSegmentXZ(Vec3 p,Vec3 a,Vec3 b){
        double vx=b.x-a.x,vz=b.z-a.z,wx=p.x-a.x,wz=p.z-a.z;double den=vx*vx+vz*vz;if(den<1e-7)return Math.sqrt(wx*wx+wz*wz);double t=Mth.clamp((wx*vx+wz*vz)/den,0,1);double dx=p.x-(a.x+vx*t),dz=p.z-(a.z+vz*t);return Math.sqrt(dx*dx+dz*dz);
    }

    public static Vec3 flat(Vec3 v){return new Vec3(v.x,0,v.z);}
    public static Vec3 rotate(Vec3 v,double degrees){double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a);return new Vec3(v.x*c-v.z*s,0,v.x*s+v.z*c);}
}
