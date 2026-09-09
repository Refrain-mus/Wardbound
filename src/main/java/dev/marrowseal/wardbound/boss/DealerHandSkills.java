package dev.marrowseal.wardbound.boss;

import dev.marrowseal.wardbound.WardHud;
import dev.marrowseal.wardbound.WardSounds;
import dev.marrowseal.wardbound.Wardbound;
import dev.marrowseal.wardbound.net.WardLodestoneFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/** Six reward-only attacks. Deliberately independent from ForbiddenBargain/Card pool code. */
public final class DealerHandSkills {
    public static final int TABLE_SWEEP=0, CUT_THE_DECK=1, DEAD_MANS_DRAW=2, DOUBLE_DOWN=3, ROYAL_FLUSH=4, HOUSE_COLLAPSE=5, COUNT=6;
    private DealerHandSkills() {}

    public static String title(int id){return switch(id){
        case TABLE_SWEEP->"TABLE SWEEP";case CUT_THE_DECK->"CUT THE DECK";case DEAD_MANS_DRAW->"DEAD MAN'S DRAW";
        case DOUBLE_DOWN->"DOUBLE DOWN";case ROYAL_FLUSH->"ROYAL FLUSH";case HOUSE_COLLAPSE->"HOUSE COLLAPSE";default->"MISDEAL";};}
    public static String description(int id){return switch(id){
        case TABLE_SWEEP->"A brutal 360° clearing strike around you.";
        case CUT_THE_DECK->"Send a wide, piercing wave straight through the table.";
        case DEAD_MANS_DRAW->"The first three creatures in your gaze are dealt a killing hand.";
        case DOUBLE_DOWN->"Pay blood for an obscene single-target hit.";
        case ROYAL_FLUSH->"Five piercing lanes tear through a broad frontal fan.";
        case HOUSE_COLLAPSE->"Collapse the House at the point you are looking at.";
        default->"The card is blank.";};}

    public static boolean execute(ServerPlayer p,int id){
        if(!(p.level() instanceof ServerLevel level))return false;
        return switch(id){
            case TABLE_SWEEP->tableSweep(p,level);
            case CUT_THE_DECK->cutDeck(p,level);
            case DEAD_MANS_DRAW->deadMansDraw(p,level);
            case DOUBLE_DOWN->doubleDown(p,level);
            case ROYAL_FLUSH->royalFlush(p,level);
            case HOUSE_COLLAPSE->houseCollapse(p,level);
            default->false;
        };
    }

    private static boolean valid(ServerPlayer p,LivingEntity e){return e!=null&&e.isAlive()&&e!=p&&!(e instanceof Player)&&!e.isAlliedTo(p);}
    private static boolean hit(ServerPlayer p,LivingEntity e,float amount){return valid(p,e)&&e.hurt(p.damageSources().playerAttack(p),amount);}
    private static void fx(ServerLevel level,Vec3 pos,int kind,float scale){
        Wardbound.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(pos.x,pos.y,pos.z,64,level.dimension())),new WardLodestoneFxPacket(pos.x,pos.y,pos.z,kind,scale));
    }
    private static void sound(ServerLevel l,Vec3 p,float pitch){l.playSound(null,BlockPos.containing(p),WardSounds.GAMBLER_ACE_OATH.get(),SoundSource.PLAYERS,.74f,pitch);}

    private static boolean tableSweep(ServerPlayer p,ServerLevel l){
        List<LivingEntity> targets=l.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(8.5),e->valid(p,e));
        for(LivingEntity e:targets){hit(p,e,42f);Vec3 d=e.position().subtract(p.position());if(d.lengthSqr()>.02)e.push(d.x*.11,.32,d.z*.11);}
        fx(l,p.position().add(0,.15,0),WardLodestoneFxPacket.DEALER_TABLE_SWEEP,1.35f);sound(l,p.position(),.82f);
        WardHud.send(p,"TABLE SWEEP // Nobody keeps a seat when the table itself is cleared.",WardHud.Mood.REWARD,1800);return true;
    }

    private static boolean cutDeck(ServerPlayer p,ServerLevel l){
        Vec3 flat=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);if(flat.lengthSqr()<1e-5)return false;flat=flat.normalize();Vec3 origin=p.position().add(0,1,0);
        Set<Integer> struck=new HashSet<>();
        for(int step=2;step<=18;step+=2){Vec3 q=origin.add(flat.scale(step));for(LivingEntity e:l.getEntitiesOfClass(LivingEntity.class,new AABB(q.x-2.15,q.y-1.8,q.z-2.15,q.x+2.15,q.y+2.1,q.z+2.15),x->valid(p,x)))if(struck.add(e.getId())){hit(p,e,50f);e.push(flat.x*.8,.12,flat.z*.8);}fx(l,q,WardLodestoneFxPacket.DEALER_CUT_WAVE,1.0f+step*.018f);}
        sound(l,p.position(),.72f);WardHud.send(p,"CUT THE DECK // The line between here and there has been dealt through.",WardHud.Mood.REWARD,1800);return true;
    }

    private static List<LivingEntity> aimed(ServerPlayer p,ServerLevel l,double range,double dot,int cap){
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle().normalize();List<LivingEntity> all=l.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),e->valid(p,e));
        all.removeIf(e->{Vec3 to=e.getBoundingBox().getCenter().subtract(eye);return to.lengthSqr()>range*range||to.normalize().dot(look)<dot;});
        all.sort(Comparator.comparingDouble(e->e.getBoundingBox().getCenter().subtract(eye).lengthSqr()));if(all.size()>cap)return new ArrayList<>(all.subList(0,cap));return all;
    }
    private static boolean deadMansDraw(ServerPlayer p,ServerLevel l){
        List<LivingEntity> t=aimed(p,l,24,.86,3);if(t.isEmpty()){WardHud.send(p,"DEAD MAN'S DRAW // No name sits inside the sightline.",WardHud.Mood.WARD,1500);return false;}
        int i=0;for(LivingEntity e:t){hit(p,e,70f);fx(l,e.position().add(0,e.getBbHeight()*.55,0),WardLodestoneFxPacket.DEALER_DEAD_DRAW,1.15f+i*.08f);i++;}
        l.playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.9f,.55f);WardHud.send(p,"DEAD MAN'S DRAW // Three names at most. The House is still economical.",WardHud.Mood.REWARD,1800);return true;
    }

    private static LivingEntity primaryTarget(ServerPlayer p,ServerLevel l,double range){List<LivingEntity> t=aimed(p,l,range,.955,1);return t.isEmpty()?null:t.get(0);}
    private static boolean doubleDown(ServerPlayer p,ServerLevel l){
        LivingEntity target=primaryTarget(p,l,30);if(target==null){WardHud.send(p,"DOUBLE DOWN // The wager requires someone to lose against.",WardHud.Mood.WARD,1500);return false;}
        float cost=Math.min(p.getHealth()-1f,p.getMaxHealth()*.18f);if(cost>0)p.setHealth(Math.max(1f,p.getHealth()-cost));hit(p,target,100f);
        fx(l,target.position().add(0,target.getBbHeight()*.5,0),WardLodestoneFxPacket.DEALER_DOUBLE_DOWN,1.45f);fx(l,p.position().add(0,1,0),WardLodestoneFxPacket.DEALER_DOUBLE_DOWN,.72f);sound(l,target.position(),.54f);
        WardHud.send(p,"DOUBLE DOWN // The House accepted blood as collateral.",WardHud.Mood.DANGER,1800);return true;
    }

    private static boolean royalFlush(ServerPlayer p,ServerLevel l){
        Vec3 f=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);if(f.lengthSqr()<1e-5)return false;f=f.normalize();Vec3 r=new Vec3(-f.z,0,f.x);Map<Integer,Integer> hits=new HashMap<>();
        double[] lanes={-2.8,-1.4,0,1.4,2.8};
        for(double lane:lanes)for(int step=3;step<=20;step+=3){Vec3 q=p.position().add(0,1,0).add(f.scale(step)).add(r.scale(lane*(.55+step/40.0)));for(LivingEntity e:l.getEntitiesOfClass(LivingEntity.class,new AABB(q.x-1.25,q.y-1.7,q.z-1.25,q.x+1.25,q.y+1.9,q.z+1.25),x->valid(p,x))){int n=hits.getOrDefault(e.getId(),0);if(n<2){hits.put(e.getId(),n+1);hit(p,e,36f);}}fx(l,q,WardLodestoneFxPacket.DEALER_ROYAL_FLUSH,.85f);}
        sound(l,p.position(),1.08f);WardHud.send(p,"ROYAL FLUSH // Five lanes. No promise that a body belongs to only one.",WardHud.Mood.REWARD,1800);return true;
    }

    private static boolean houseCollapse(ServerPlayer p,ServerLevel l){
        HitResult hr=p.pick(26,1.0f,false);Vec3 q=hr.getLocation();if(!Double.isFinite(q.x+q.y+q.z))return false;
        List<LivingEntity> targets=l.getEntitiesOfClass(LivingEntity.class,new AABB(q.x-4.8,q.y-3.5,q.z-4.8,q.x+4.8,q.y+4.5,q.z+4.8),e->valid(p,e));
        for(LivingEntity e:targets)if(e.position().distanceToSqr(q)<=4.8*4.8){hit(p,e,78f);Vec3 d=e.position().subtract(q);if(d.lengthSqr()>.02)e.push(d.x*.16,.65,d.z*.16);}
        fx(l,q.add(0,.12,0),WardLodestoneFxPacket.DEALER_HOUSE_COLLAPSE,1.65f);l.playSound(null,BlockPos.containing(q),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.82f,.62f);
        WardHud.send(p,"HOUSE COLLAPSE // The architecture has remembered who owned the table.",WardHud.Mood.REWARD,1800);return true;
    }
}
