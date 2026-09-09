package dev.marrowseal.wardbound.client;

import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.WardConfig;
import dev.marrowseal.wardbound.boss.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.ParticleStatus;
import dev.marrowseal.wardbound.net.GamblerCinematicPacket;
import net.minecraft.nbt.*;
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
import java.util.*;

/** Tick-driven, client-only choreography. Decorative volume never defines damage or safe areas. */
@Mod.EventBusSubscriber(modid=Wardbound.MODID,value=Dist.CLIENT)
public final class MasterSpectacleFx {
    private static final Color GOLD=new Color(236,189,96), VIOLET=new Color(136,82,207),
            IVORY=new Color(233,216,181), EMBER=new Color(219,103,49),
            WAX=new Color(161,49,76), SILVER=new Color(176,187,204);
    private static final Map<UUID,Memory> MEMORIES=new HashMap<>();
    private static final List<Pulse> PULSES=new ArrayList<>();
    private static ClientLevel world;
    private static int budget;
    private static long time;
    private static boolean reduced;
    private static final class Memory {
        int state,clock,phase; boolean initialized;
        ListTag stamps=new ListTag();
        final Set<Integer> hazards=new HashSet<>();
    }
    private record Pulse(UUID owner,Vec3 center,int theme,int form,double radius,long born,int duration) {}
    private MasterSpectacleFx() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=world){MEMORIES.clear();PULSES.clear();world=mc.level;}
        if(world==null||mc.player==null||mc.isPaused())return;
        time=world.getGameTime();reduced=WardConfig.accessibilityReduceMotion;
        ParticleStatus quality=mc.options.particles().get();
        budget=reduced?32:quality==ParticleStatus.MINIMAL?48:quality==ParticleStatus.DECREASED?110:220;
        List<Entity> bosses=new ArrayList<>();
        for(Entity e:world.entitiesForRendering())if(!e.isRemoved()&&e.distanceToSqr(mc.player)<4096&&
                !(e instanceof AshenCuratorEntity curator&&curator.view().getInt("detail")<=0)&&
                (e instanceof PaleGamblerEntity||e instanceof AshenCuratorEntity||e instanceof MourningNotaryEntity))bosses.add(e);
        bosses.sort(Comparator.comparingDouble(e->e.distanceToSqr(mc.player)));
        if(bosses.size()>3)bosses= new ArrayList<>(bosses.subList(0,3));
        Set<UUID> visible=new HashSet<>();
        for(Entity boss:bosses){
            visible.add(boss.getUUID());Memory m=MEMORIES.computeIfAbsent(boss.getUUID(),k->new Memory());
            int theme=boss instanceof PaleGamblerEntity?0:boss instanceof AshenCuratorEntity?1:2;
            CompoundTag view=boss instanceof AshenCuratorEntity c?c.view():boss instanceof MourningNotaryEntity n?n.view():new CompoundTag();
            int state=boss instanceof PaleGamblerEntity g?g.currentHand():view.getInt("state");
            int clock=view.getInt("clock");
            int phase=boss instanceof PaleGamblerEntity g?g.currentPhase():view.getInt(theme==1?"chapter":"phase");
            boolean changed=m.initialized&&(state!=m.state||clock<m.clock);
            // Establish a baseline on arrival; joining mid-fight must not replay old impacts.
            if(changed){m.hazards.clear();enqueue(boss,boss.position().add(0,4.5,0),theme,0,4.4,30);}
            if(m.initialized&&phase>m.phase)enqueue(boss,boss.position().add(0,5,0),theme,2,8,52);
            if(theme==1&&m.initialized)curator(boss,view,m);
            if(theme==2&&m.initialized&&!changed)notary(boss,view,m);
            if(theme==1&&(state==AshenCuratorEntity.OPEN||state==AshenCuratorEntity.COLLAPSE)&&changed)
                enqueue(boss,boss.position().add(0,4.5,0),theme,2,6,42);
            if(theme==2&&(state==MourningNotaryEntity.UNBOUND||state==MourningNotaryEntity.COLLAPSE)&&changed)
                enqueue(boss,boss.position().add(0,5,0),theme,2,7,42);
            m.stamps=vStamps(view);m.state=state;m.clock=clock;m.phase=phase;m.initialized=true;
        }
        MEMORIES.keySet().retainAll(visible);
        PULSES.removeIf(p->p.center.distanceToSqr(mc.player.position())>6400||time-p.born>=p.duration||time<p.born);
        // Event accents have priority over idle ornaments under the shared particle budget.
        for(Pulse pulse:PULSES)drawPulse(pulse);
        if(time%(reduced?8:3)==0)for(Entity boss:bosses)ambient(boss);
    }

    private static ListTag vStamps(CompoundTag v){return v.getList("stamps",Tag.TAG_COMPOUND).copy();}

    /** Reuse the existing authoritative Gambler cue; no extra network traffic. */
    public static void gamblerCue(GamblerCinematicPacket cue){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.level!=world||mc.player==null)return;
        int form=switch(cue.effect()){
            case GamblerCinematicPacket.PHASE_BREAK,GamblerCinematicPacket.VICTORY_SHATTER,
                    GamblerCinematicPacket.ALL_IN,GamblerCinematicPacket.LAST_DEAL -> 2;
            case GamblerCinematicPacket.FAN_IMPACT,GamblerCinematicPacket.RING_IMPACT,
                    GamblerCinematicPacket.FLOOR_CARD_STRIKE,GamblerCinematicPacket.SWEEP_STRIKE,
                    GamblerCinematicPacket.CAGE_STRIKE,GamblerCinematicPacket.STARFALL,
                    GamblerCinematicPacket.DANGER_CARD_STRIKE,GamblerCinematicPacket.SUIT_CALL_STRIKE,
                    GamblerCinematicPacket.ROYAL_FLUSH -> 1;
            default -> -1;
        };
        if(form<0)return;
        Vec3 pos=new Vec3(cue.x(),cue.y(),cue.z());
        PaleGamblerEntity nearest=null;double distance=4096;
        for(Entity e:world.entitiesForRendering())if(e instanceof PaleGamblerEntity g&&g.distanceToSqr(pos)<distance){nearest=g;distance=g.distanceToSqr(pos);}
        if(nearest!=null)enqueue(nearest,pos.add(0,form==2?5:2.5,0),0,form,form==2?8:2,form==2?44:18);
    }

    private static void curator(Entity boss,CompoundTag v,Memory m){
        ListTag list=v.getList("hazards",Tag.TAG_COMPOUND);Set<Integer> present=new HashSet<>();
        for(int i=0;i<Math.min(96,list.size());i++){
            CompoundTag h=list.getCompound(i);int id=h.getInt("id");present.add(id);
            long age=v.getLong("time")-h.getLong("born");int warning=h.getInt("warning");
            if(age>=warning&&m.hazards.add(id)&&age-warning<=6){
                Vec3 c=boss.position().add(h.getDouble("x"),2.5,h.getDouble("z"));
                enqueue(boss,c,1,1,Math.min(2.5,Math.max(.8,h.getDouble("a"))),18);
            }
        }
        m.hazards.retainAll(present);
    }

    private static void notary(Entity boss,CompoundTag v,Memory m){
        int state=v.getInt("state"),clock=v.getInt("clock"),variant=v.getInt("variant");
        if(state==MourningNotaryEntity.JUDGMENT&&variant==3){
            int start=Math.max(2,Math.min(4,v.getInt("verdict_start"))),strike=v.getInt("verdict_strike");
            for(int i=0;i<start;i++){
                int beat=18+i*16,digit=start-i;
                if(crossed(m.clock,clock,beat))enqueue(boss,boss.position().add(0,5.4+digit*.28,0),2,0,2.7+digit*.55,24);
                if(crossed(m.clock,clock,beat+8)){
                    ListTag marks=!m.stamps.isEmpty()?m.stamps:v.getList("stamps",Tag.TAG_COMPOUND);
                    for(int j=0;j<Math.min(10,marks.size());j++){CompoundTag q=marks.getCompound(j);enqueue(boss,boss.position().add(q.getDouble("x"),2.2,q.getDouble("z")),2,1,1.55,18);}
                }
            }
            if(strike>0&&crossed(m.clock,clock,strike-6))enqueue(boss,boss.position().add(0,6.2,0),2,0,5.6,18);
            if(strike>0&&crossed(m.clock,clock,strike))enqueue(boss,boss.position().add(0,5.5,0),2,2,9.5,36);
        }
        if(state==MourningNotaryEntity.STAMP||state==MourningNotaryEntity.JUDGMENT){
            int[] beats=state==MourningNotaryEntity.STAMP?(variant==2?new int[]{36,54,72}:variant==1?new int[]{48,60}:new int[]{48})
                    :variant==3?new int[0]:variant==2?new int[]{48}:variant==1?new int[]{50,62}:new int[]{50};
            for(int beat:beats)if(crossed(m.clock,clock,beat)){
                // A cascade replaces its stamp positions on tick 37/55; use the previous
                // snapshot if a network update crossed both impact and replacement.
                ListTag stamps=state==MourningNotaryEntity.STAMP&&variant==2&&clock>beat&&!m.stamps.isEmpty()
                        ?m.stamps:v.getList("stamps",Tag.TAG_COMPOUND);
                for(int i=0;i<Math.min(10,stamps.size());i++){
                    CompoundTag q=stamps.getCompound(i);
                    enqueue(boss,boss.position().add(q.getDouble("x"),2.2,q.getDouble("z")),2,1,1.6,18);
                }
            }
        }
        int[] tolls=state==MourningNotaryEntity.TOLL?(variant==2?new int[]{34,52,70}:variant==1?new int[]{48,74}:new int[]{62})
                :state==MourningNotaryEntity.JUDGMENT?(variant==3?new int[0]:variant==2?new int[]{62,84}:variant==1?new int[]{58,80}:new int[]{58}):new int[0];
        for(int beat:tolls)if(crossed(m.clock,clock,beat))enqueue(boss,boss.position().add(0,5.2,0),2,2,8,32);
    }

    static boolean crossed(int before,int now,int beat){return MasterFxTiming.crossed(before,now,beat);}
    private static void enqueue(Entity boss,Vec3 center,int theme,int form,double radius,int life){
        if(PULSES.size()<64)PULSES.add(new Pulse(boss.getUUID(),center,theme,form,radius,time,life));
    }
    private static Color primary(int theme){return theme==0?GOLD:theme==1?IVORY:WAX;}
    private static Color secondary(int theme){return theme==0?VIOLET:theme==1?EMBER:SILVER;}

    private static void ambient(Entity boss){
        int theme=boss instanceof PaleGamblerEntity?0:boss instanceof AshenCuratorEntity?1:2;
        double angle=reduced?0:time*.035;
        Vec3 center=boss.position().add(0,theme==2?7:5.2,0);
        if(theme==0){
            // A rotating roulette constellation: twelve diamonds on an inclined celestial wheel.
            for(int i=0;i<(reduced?4:12);i++){
                double a=angle+i*Math.PI*2/(reduced?4:12),r=3.2;
                Vec3 p=center.add(Math.cos(a)*r,Math.sin(a)*.9,Math.sin(a)*r);
                line(p.add(0,.22,0),p.add(.18,0,0),GOLD,VIOLET,3);
                line(p.add(.18,0,0),p.add(0,-.22,0),GOLD,VIOLET,3);
                line(p.add(0,-.22,0),p.add(-.18,0,0),GOLD,VIOLET,3);
                line(p.add(-.18,0,0),p.add(0,.22,0),GOLD,VIOLET,3);
            }
        }else if(theme==1){
            // Opposed helical filing rails keep the archive silhouette open in the middle.
            for(int rail=0;rail<2;rail++)for(int i=0;i<(reduced?5:16);i++){
                double f=i/(double)(reduced?5:16),a=angle*(rail==0?1:-1)+f*Math.PI*2;
                point(center.add(Math.cos(a)*3.5,(f-.5)*3,Math.sin(a)*3.5),IVORY,EMBER,.085f,Vec3.ZERO,12);
            }
        }else{
            // Suspended scales: an overhead balance beam and two falling chains of wax light.
            Vec3 left=center.add(-3.3,.3,0),right=center.add(3.3,.3,0);
            line(left,right,SILVER,WAX,reduced?8:22);
            for(int side:new int[]{-1,1}){
                Vec3 end=center.add(side*3.3,-1.8,0);
                line(center.add(side*3.3,.3,0),end,SILVER,WAX,reduced?4:8);
                orbit(end,1.0,0,0,WAX,SILVER,reduced?6:16);
            }
        }
    }

    private static void drawPulse(Pulse p){
        int age=(int)(time-p.born);if(age%(reduced?4:2)!=0)return;
        double t=age/(double)p.duration;Color a=primary(p.theme),b=secondary(p.theme);
        int n=reduced?10:32;
        if(p.form==0){
            // A heraldic iris assembles above the boss, then opens into three tilted orbits.
            double r=p.radius*(.3+.7*Math.sin(t*Math.PI/2));
            for(int axis=0;axis<(reduced?1:3);axis++)orbit(p.center,r,axis,t*1.8,a,b,n);
        }else if(p.form==1){
            // Impact debris rises in separated corkscrews, leaving the floor telegraph visible.
            for(int i=0;i<(reduced?2:5);i++){
                double ang=i*Math.PI*2/(reduced?2:5)+t*3;
                Vec3 q=p.center.add(Math.cos(ang)*p.radius*(1+t),t*3,Math.sin(ang)*p.radius*(1+t));
                point(q,a,b,.13f,new Vec3(Math.cos(ang)*.025,.06,Math.sin(ang)*.025),16);
            }
        }else{
            // Funeral/phase wave: three overhead expanding latitude rings, never a damage outline.
            double radius=p.radius*(.2+.8*t);
            for(int i=0;i<(reduced?1:3);i++)orbit(p.center.add(0,i*.65,0),radius*(1-i*.12),0,0,a,b,n);
        }
    }

    private static void orbit(Vec3 c,double radius,int axis,double offset,Color a,Color b,int count){
        for(int i=0;i<count;i++){
            double ang=i*Math.PI*2/count+offset,x=Math.cos(ang)*radius,z=Math.sin(ang)*radius;
            Vec3 q=axis==0?new Vec3(x,0,z):axis==1?new Vec3(x,z*.5,z*.866):new Vec3(x*.5,z,x*.866);
            point(c.add(q),a,b,.1f,Vec3.ZERO,12);
        }
    }
    private static void line(Vec3 a,Vec3 b,Color start,Color end,int n){
        for(int i=0;i<n;i++)point(a.lerp(b,i/(double)Math.max(1,n-1)),start,end,.085f,Vec3.ZERO,12);
    }
    private static void point(Vec3 p,Color start,Color end,float size,Vec3 motion,int life){
        Minecraft mc=Minecraft.getInstance();
        if(budget<=0||world==null||mc.player==null||p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())<6.25)return;
        budget--;
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(start,end).build())
                .setTransparencyData(GenericParticleData.create(reduced?.38f:.65f,0f).build())
                .setScaleData(GenericParticleData.create(size,0f).build()).setLifetime(life)
                .addMotion(reduced?0:motion.x,reduced?0:motion.y,reduced?0:motion.z)
                .enableNoClip().spawn(world,p.x,p.y,p.z);
    }
}
