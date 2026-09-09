package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.MasterAnimationBeatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only game-feel layer shared by the three Masters.
 *
 * ANTICIPATE grows into a held physical lean. RELEASE is authored by the server on the actual
 * gameplay strike tick and produces a short 1-2 tick visual hitstop, a recoil impulse, camera kick and
 * a Lodestone volume. Nothing pauses the game simulation.
 *
 * The same state also exposes damped angular inertia used by secondary bones (cards, pages,
 * robes, chains and the bell), so they lag behind the primary rig instead of moving as one slab.
 */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MasterMotionFeel {
    private static final Color G_VIOLET=new Color(174,76,245), G_GOLD=new Color(236,192,96);
    private static final Color C_EMBER=new Color(218,100,55), C_IVORY=new Color(236,218,179);
    private static final Color N_WINE=new Color(142,75,108), N_IVORY=new Color(225,215,197);

    private static final class State {
        int style;
        int phase;
        float strength;
        long phaseStartNanos;
        long releaseStartNanos;
        long lastSeenNanos;
        boolean freezeCaptured;
        float freezeX,freezeY,freezeZ;
        float lastX,lastY,lastZ;
        float lagX,lagY,lagZ;
        boolean baseReady;
        UUID entityUuid;
    }
    public record Sample(float anticipation,float freezeMix,float freezeX,float freezeY,float freezeZ,
                         float recoil,float lagX,float lagY,float lagZ) {
        public static final Sample ZERO=new Sample(0,0,0,0,0,0,0,0,0);
    }

    private static final Map<Integer,State> STATES=new HashMap<>();
    private static Object levelIdentity;
    private MasterMotionFeel(){}

    public static void accept(MasterAnimationBeatPacket p){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        Entity owner=mc.level.getEntity(p.entityId());
        State s=STATES.get(p.entityId());
        if(s==null || (owner!=null && s.entityUuid!=null && !s.entityUuid.equals(owner.getUUID()))){s=new State();STATES.put(p.entityId(),s);}
        if(owner!=null)s.entityUuid=owner.getUUID();
        long now=System.nanoTime();s.style=p.style();s.strength=p.strength();s.phase=p.phase();s.phaseStartNanos=now;s.lastSeenNanos=now;
        if(p.phase()==MasterAnimationBeatPacket.CLEAR){s.phase=0;s.freezeCaptured=false;return;}
        if(p.phase()==MasterAnimationBeatPacket.ANTICIPATE){s.releaseStartNanos=0;s.freezeCaptured=false;return;}
        if(p.phase()==MasterAnimationBeatPacket.RELEASE){
            s.releaseStartNanos=now;s.freezeCaptured=false;
            float camera=s.style==MasterAnimationBeatPacket.CURATOR?1.15f:s.style==MasterAnimationBeatPacket.NOTARY?.92f:1.0f;
            MasterCameraShake.pulse(Math.max(.22f,p.strength()*camera),s.style==MasterAnimationBeatPacket.CURATOR?5:4,s.style==MasterAnimationBeatPacket.NOTARY?5.1f:6.8f,p.seed(),s.style);
            if(!WardConfig.accessibilityReduceMotion)spawnReleaseVolume(mc,p.entityId(),p.style(),p.strength(),p.seed());
        }
    }

    /** Called from the GeoModel after GeckoLib has evaluated the base animation for this frame. */
    public static Sample sample(int entityId,float baseX,float baseY,float baseZ){
        State s=STATES.get(entityId);if(s==null)return Sample.ZERO;
        Minecraft mc=Minecraft.getInstance();Entity owner=mc.level==null?null:mc.level.getEntity(entityId);
        if(owner==null || (s.entityUuid!=null && !s.entityUuid.equals(owner.getUUID()))){STATES.remove(entityId);return Sample.ZERO;}
        s.entityUuid=owner.getUUID();
        long now=System.nanoTime();s.lastSeenNanos=now;
        if(!s.baseReady){s.lastX=baseX;s.lastY=baseY;s.lastZ=baseZ;s.baseReady=true;}
        float dx=wrapRad(baseX-s.lastX),dy=wrapRad(baseY-s.lastY),dz=wrapRad(baseZ-s.lastZ);
        s.lastX=baseX;s.lastY=baseY;s.lastZ=baseZ;
        // Angular spring: quick input from the primary rig, slow decay on secondary pieces.
        s.lagX=s.lagX*.80f+dx*2.35f;
        s.lagY=s.lagY*.80f+dy*2.55f;
        s.lagZ=s.lagZ*.79f+dz*2.40f;
        s.lagX*=.94f;s.lagY*=.94f;s.lagZ*=.94f;

        float anticipation=0,freezeMix=0,recoil=0;
        if(s.phase==MasterAnimationBeatPacket.ANTICIPATE){
            float ms=(now-s.phaseStartNanos)/1_000_000f;
            anticipation=smooth(Math.min(1f,ms/340f))*s.strength;
        }else if(s.phase==MasterAnimationBeatPacket.RELEASE && s.releaseStartNanos>0){
            float ms=(now-s.releaseStartNanos)/1_000_000f;
            if(!s.freezeCaptured){s.freezeCaptured=true;s.freezeX=baseX;s.freezeY=baseY;s.freezeZ=baseZ;}
            // Strength-authored micro hitstop: roughly one Minecraft tick on ordinary releases,
            // rising toward two ticks only for the largest phase/final impacts. Simulation never pauses.
            float holdMs=42f+Math.min(1.45f,Math.max(0f,s.strength))*22f;
            if(s.style==MasterAnimationBeatPacket.CURATOR)holdMs+=4f;
            holdMs=Math.min(78f,holdMs);
            float fadeMs=30f;
            if(ms<holdMs)freezeMix=1f;
            else if(ms<holdMs+fadeMs)freezeMix=1f-(ms-holdMs)/fadeMs;
            // Then snap backwards and settle through the original pose.
            float recoilStart=Math.max(42f,holdMs-6f);
            if(ms>=recoilStart&&ms<recoilStart+132f){
                float t=(ms-recoilStart)/132f;
                recoil=(float)Math.sin(t*Math.PI)*s.strength;
                if(t>.58f)recoil-=smooth((t-.58f)/.42f)*s.strength*.28f;
            }
            if(ms>=225f){s.phase=0;s.freezeCaptured=false;}
        }
        if(WardConfig.accessibilityReduceMotion){anticipation=0;freezeMix=0;recoil=0;}
        return new Sample(anticipation,Math.max(0,Math.min(1,freezeMix)),s.freezeX,s.freezeY,s.freezeZ,recoil,s.lagX,s.lagY,s.lagZ);
    }

    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float wrapRad(float r){while(r>(float)Math.PI)r-=(float)(Math.PI*2);while(r<-(float)Math.PI)r+=(float)(Math.PI*2);return r;}

    private static void spawnReleaseVolume(Minecraft mc,int entityId,int style,float strength,int seed){
        Entity e=mc.level.getEntity(entityId);if(e==null)return;Vec3 c=e.position().add(0,e.getBbHeight()*.42,0);
        Color a=style==MasterAnimationBeatPacket.CURATOR?C_EMBER:style==MasterAnimationBeatPacket.NOTARY?N_WINE:G_VIOLET;
        Color b=style==MasterAnimationBeatPacket.CURATOR?C_IVORY:style==MasterAnimationBeatPacket.NOTARY?N_IVORY:G_GOLD;
        int n=Math.max(12,Math.min(54,(int)(22+strength*17)));double golden=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<n;i++){
            double y=1-2*(i+.5)/n,rr=Math.sqrt(Math.max(0,1-y*y)),ang=i*golden+seed*.017,rad=(1.3+strength*.75)*(0.88+(i%5)*.025);
            double ox=Math.cos(ang)*rr*rad,oz=Math.sin(ang)*rr*rad,oy=y*rad;
            WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                    .setColorData(ColorParticleData.create((i&1)==0?a:b,(i&1)==0?b:a).build())
                    .setTransparencyData(GenericParticleData.create(.92f,0f).build())
                    .setScaleData(GenericParticleData.create(.10f+.04f*strength,0f).build())
                    .setLifetime(18+(i%8)).addMotion(ox*.015,oy*.012+.012,oz*.015)
                    .setRandomMotion(.018,.012,.018).enableNoClip().spawn(mc.level,c.x+ox*.35,c.y+oy*.28,c.z+oz*.35);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(levelIdentity!=mc.level){STATES.clear();levelIdentity=mc.level;}
        if(mc.level==null)return;long now=System.nanoTime();
        Iterator<Map.Entry<Integer,State>> it=STATES.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<Integer,State> entry=it.next();State s=entry.getValue();Entity owner=mc.level.getEntity(entry.getKey());
            if(owner==null || (s.entityUuid!=null&&!s.entityUuid.equals(owner.getUUID())) || now-s.lastSeenNanos>8_000_000_000L)it.remove();
        }
    }
}
