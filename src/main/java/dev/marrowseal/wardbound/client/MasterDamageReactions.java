package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.net.MasterDamageReactionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.marrowseal.wardbound.Wardbound;

import java.util.HashMap;
import java.util.Map;

/** Layered, non-interrupting damage flinch/stagger state. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MasterDamageReactions {
    public record Sample(float amount,float pitch,float yaw,float roll,float lift,int kind) {
        public static final Sample NONE=new Sample(0,0,0,0,0,MasterDamageReactionPacket.LIGHT);
    }
    private static final class Reaction {
        int style,kind,seed; float strength; long started;
    }
    private static final Map<Integer,Reaction> ACTIVE=new HashMap<>();
    private static Object levelIdentity;
    private MasterDamageReactions(){}

    public static void accept(MasterDamageReactionPacket p){
        Reaction r=ACTIVE.computeIfAbsent(p.entityId(),k->new Reaction());
        r.style=p.style();r.kind=p.kind();r.strength=p.strength();r.seed=p.seed();r.started=System.nanoTime();
        if(!WardConfig.accessibilityReduceMotion && p.kind()!=MasterDamageReactionPacket.LIGHT){
            float cam=p.kind()==MasterDamageReactionPacket.VULNERABLE?.18f:.11f;
            MasterCameraShake.pulse(cam, p.kind()==MasterDamageReactionPacket.VULNERABLE?7:5, 5.2f, p.seed(), p.style());
        }
    }

    public static Sample sample(int entityId){
        Reaction r=ACTIVE.get(entityId);if(r==null)return Sample.NONE;
        double age=(System.nanoTime()-r.started)/1_000_000_000.0;
        double duration=r.kind==MasterDamageReactionPacket.VULNERABLE?.34:r.kind==MasterDamageReactionPacket.HEAVY?.26:.16;
        if(age<0||age>=duration){ACTIVE.remove(entityId);return Sample.NONE;}
        float t=(float)(age/duration);
        // Fast contact -> brief hold -> springy recovery, no simulation interruption.
        float envelope=t<.18f?Mth.clamp(t/.18f,0,1):(float)Math.pow(1-(t-.18f)/.82f,1.65);
        float wave=(float)Math.sin(t*Math.PI*(r.kind==MasterDamageReactionPacket.VULNERABLE?1.35:1.75));
        float amount=envelope*r.strength;
        float side=((r.seed&1)==0?-1f:1f);
        float pitch=(-6.5f-5.5f*(r.kind))*amount + wave*2.0f*amount;
        float yaw=side*(r.kind==MasterDamageReactionPacket.LIGHT?2.0f:4.5f)*amount;
        float roll=side*(r.kind==MasterDamageReactionPacket.VULNERABLE?8.5f:r.kind==MasterDamageReactionPacket.HEAVY?5.5f:2.5f)*amount;
        float lift=(r.kind==MasterDamageReactionPacket.VULNERABLE?.10f:.04f)*amount;
        return new Sample(amount,pitch,yaw,roll,lift,r.kind);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){ACTIVE.clear();levelIdentity=mc.level;}
    }
}
