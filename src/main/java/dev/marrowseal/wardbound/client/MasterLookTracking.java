package dev.marrowseal.wardbound.client;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Smoothed head/upper-body tracking with a release-frame hold instead of snapping to neutral. */
public final class MasterLookTracking {
    public record Look(float yaw,float pitch){}
    private static final class State{float yaw,pitch;long last=System.nanoTime();boolean init;}
    private static final Map<Integer,State> STATES=new HashMap<>();
    private MasterLookTracking(){}

    public static Look sample(int id,float targetYaw,float targetPitch,float yawLimit,float pitchLimit,float speed,boolean locked){
        State s=STATES.computeIfAbsent(id,k->new State());long now=System.nanoTime();float dt=Mth.clamp((now-s.last)/1_000_000_000f,0f,.08f);s.last=now;
        targetYaw=Mth.clamp(Mth.wrapDegrees(targetYaw),-yawLimit,yawLimit);targetPitch=Mth.clamp(targetPitch,-pitchLimit,pitchLimit);
        if(!s.init){s.yaw=targetYaw;s.pitch=targetPitch;s.init=true;}
        if(!locked){float a=1f-(float)Math.exp(-Math.max(.1f,speed)*dt);s.yaw=Mth.lerp(a,s.yaw,targetYaw);s.pitch=Mth.lerp(a,s.pitch,targetPitch);}
        return new Look(s.yaw,s.pitch);
    }
}
